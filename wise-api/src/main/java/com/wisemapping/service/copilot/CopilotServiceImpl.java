/*
 *    Copyright [2007-2025] [wisemapping]
 *
 *   Licensed under WiseMapping Public License, Version 1.0 (the "License").
 *   It is basically the Apache License, Version 2.0 (the "License") plus the
 *   "powered by wisemapping" text requirement on every single page;
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the license at
 *
 *       https://github.com/wisemapping/wisemapping-open-source/blob/main/LICENSE.md
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package com.wisemapping.service.copilot;

import com.wisemapping.config.CopilotProperties;
import com.wisemapping.exceptions.CopilotDisabledException;
import com.wisemapping.exceptions.CopilotInvalidPromptException;
import com.wisemapping.exceptions.CopilotInvalidResponseException;
import com.wisemapping.exceptions.CopilotUnknownModelException;
import com.wisemapping.exceptions.WiseMappingException;
import com.wisemapping.mindmap.utils.MindmapUtils;
import com.wisemapping.mindmap.utils.MindmapValidationException;
import com.wisemapping.model.Account;
import com.wisemapping.model.Constants;
import com.wisemapping.model.Mindmap;
import com.wisemapping.service.MetricsService;
import com.wisemapping.service.MindmapService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Pattern;

/**
 * No {@code @Transactional} here on purpose: the model call can take a minute and must not hold a
 * database transaction. The map is stored through {@link MindmapService#addMindmap}, which runs in
 * its own transaction.
 */
@Service("copilotService")
public class CopilotServiceImpl implements CopilotService {
    private static final Logger logger = LoggerFactory.getLogger(CopilotServiceImpl.class);
    private static final Pattern LANGUAGE_CODE = Pattern.compile("^[a-zA-Z]{2,3}([-_][a-zA-Z0-9]{2,8})?$");
    private static final int MAX_ATTEMPTS = 2;

    @Autowired
    private CopilotProperties properties;

    @Autowired
    @Qualifier("copilotLlmClient")
    private CopilotLlmClient llmClient;

    @Autowired
    @Qualifier("mindmapService")
    private MindmapService mindmapService;

    @Autowired
    private MetricsService metricsService;

    @Override
    public boolean isActive() {
        return properties.isActive();
    }

    @NotNull
    @Override
    public List<CopilotProperties.Model> getModels() {
        return properties.getEffectiveModels();
    }

    @NotNull
    @Override
    public CopilotResult createMap(@NotNull String prompt, @Nullable String language, @Nullable String modelId,
                                   @NotNull Account user) throws WiseMappingException {
        if (!isActive()) {
            throw new CopilotDisabledException("The AI copilot is not configured");
        }

        final String request = prompt.trim();
        if (request.isEmpty() || request.length() > properties.getMaxPromptLength()) {
            throw new CopilotInvalidPromptException(
                    "Prompt must contain between 1 and " + properties.getMaxPromptLength() + " characters");
        }

        final CopilotProperties.Model model = properties.findModel(modelId);
        if (model == null) {
            throw new CopilotUnknownModelException("Unknown copilot model: " + modelId);
        }

        final Generation generation = askForOutline(request, language, model);
        final CopilotOutline outline = generation.outline();
        final String xml = CopilotMapXmlBuilder.toXml(outline);
        try {
            MindmapUtils.validateXmlContent(xml);
        } catch (MindmapValidationException e) {
            throw new CopilotInvalidResponseException("Generated map is not valid: " + e.getMessage());
        }

        final Mindmap mindmap = new Mindmap();
        mindmap.setTitle(uniqueTitle(outline.title(), user));
        mindmap.setXmlStr(xml);
        mindmapService.addMindmap(mindmap, user);
        metricsService.trackMindmapCreation(mindmap, user, "copilot");

        logger.info("Copilot created map {} with {} topics using '{}' (served by '{}', locality {})",
                mindmap.getId(), outline.countTopics(), model.getId(), generation.routing().servedModel(),
                generation.routing().actualLocality());
        return new CopilotResult(mindmap, model, generation.routing());
    }

    private record Generation(@NotNull CopilotOutline outline, @NotNull CopilotRoutingVerifier.Result routing) {
    }

    @NotNull
    private Generation askForOutline(@NotNull String request, @Nullable String language,
                                     @NotNull CopilotProperties.Model model) throws WiseMappingException {
        final CopilotOutlineParser parser = new CopilotOutlineParser(properties.getMaxTopics(), properties.getMaxDepth());
        final String systemPrompt = CopilotPrompts.systemPrompt(properties.getMaxDepth(), properties.getMaxTopics(),
                normalizeLanguage(language));

        CopilotOutlineParser.CopilotOutlineException lastError = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            final String userPrompt = attempt == 1 ? request : CopilotPrompts.retryPrompt(request);
            final CopilotCompletion completion = llmClient.complete(model.getId(), systemPrompt, userPrompt);
            // Checked before the answer is used: a local model served elsewhere must not produce a map.
            final CopilotRoutingVerifier.Result routing = CopilotRoutingVerifier.verify(model, completion.servedModel());
            try {
                return new Generation(parser.parse(completion.content()), routing);
            } catch (CopilotOutlineParser.CopilotOutlineException e) {
                lastError = e;
                logger.warn("Copilot answer rejected (attempt {}/{}): {}", attempt, MAX_ATTEMPTS, e.getMessage());
            }
        }
        throw new CopilotInvalidResponseException("AI answer could not be used: "
                + (lastError != null ? lastError.getMessage() : "unknown"));
    }

    @NotNull
    private String uniqueTitle(@NotNull String title, @NotNull Account user) {
        String candidate = title;
        int suffix = 2;
        while (mindmapService.getMindmapByTitle(candidate, user) != null) {
            final String ending = " (" + suffix++ + ")";
            final String base = title.length() + ending.length() > Constants.MAX_MAP_NAME_LENGTH
                    ? title.substring(0, Constants.MAX_MAP_NAME_LENGTH - ending.length())
                    : title;
            candidate = base + ending;
        }
        return candidate;
    }

    @Nullable
    static String normalizeLanguage(@Nullable String language) {
        if (language == null || !LANGUAGE_CODE.matcher(language.trim()).matches()) {
            return null;
        }
        return language.trim();
    }
}
