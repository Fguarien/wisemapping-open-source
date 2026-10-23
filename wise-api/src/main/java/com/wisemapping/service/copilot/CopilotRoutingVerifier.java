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
import com.wisemapping.exceptions.CopilotRoutingMismatchException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Checks which model actually served a completion against the locality the administrator declared.
 *
 * <p>Proxies such as LiteLLM may serve a fallback under the requested name: a model declared local can
 * be answered by a cloud provider, and the other way round. The {@code model} field of the completion
 * names the deployment that answered; it must fully match the model's {@code served-match} expression.</p>
 *
 * <ul>
 *     <li>match: the declared locality is confirmed;</li>
 *     <li>model declared <b>local</b> and no match (or no {@code model} field): rejected with
 *     {@link CopilotRoutingMismatchException}, no map is created — the prompt may have left the network
 *     and the user must be told, not handed a map that pretends otherwise;</li>
 *     <li>model declared <b>external</b> and no match: accepted, reported as unverified with a warning —
 *     the data left the network either way, as the user was told before generating;</li>
 *     <li>no {@code served-match} configured: reported as unverified.</li>
 * </ul>
 *
 * <p>This is detection, not prevention: when the answer comes back the prompt has already been sent.
 * Prevention is {@code disable-fallbacks}.</p>
 */
public final class CopilotRoutingVerifier {
    private static final Logger logger = LoggerFactory.getLogger(CopilotRoutingVerifier.class);

    /**
     * Locality observed after the call.
     */
    public enum ActualLocality {
        LOCAL, EXTERNAL, UNVERIFIED
    }

    /**
     * @param warning true when the user should be told that the answer did not come from where the
     *                model was declared to run, or that this could not be checked for a local model.
     */
    public record Result(@NotNull ActualLocality actualLocality, @Nullable String servedModel, boolean warning) {
    }

    private CopilotRoutingVerifier() {
    }

    @NotNull
    public static Result verify(@NotNull CopilotProperties.Model model, @Nullable String servedModel)
            throws CopilotRoutingMismatchException {
        final boolean declaredLocal = model.getLocality() == CopilotProperties.Locality.LOCAL;
        final Pattern expected = compile(model);

        if (expected == null) {
            // Nothing to compare with: say so instead of assuming.
            return new Result(ActualLocality.UNVERIFIED, servedModel, declaredLocal);
        }

        if (servedModel != null && expected.matcher(servedModel).matches()) {
            return new Result(declaredLocal ? ActualLocality.LOCAL : ActualLocality.EXTERNAL, servedModel, false);
        }

        if (declaredLocal) {
            logger.warn("Copilot model '{}' is declared local but was served by '{}' (expected /{}/): map not created",
                    model.getId(), servedModel, model.getServedMatch());
            throw new CopilotRoutingMismatchException("Model '" + model.getId()
                    + "' is declared local but was served by '" + servedModel + "'");
        }

        logger.warn("Copilot model '{}' was served by '{}' instead of /{}/", model.getId(), servedModel,
                model.getServedMatch());
        return new Result(ActualLocality.UNVERIFIED, servedModel, true);
    }

    @Nullable
    private static Pattern compile(@NotNull CopilotProperties.Model model) {
        final String expression = model.getServedMatch();
        if (expression.isBlank()) {
            return null;
        }
        try {
            return Pattern.compile(expression);
        } catch (PatternSyntaxException e) {
            logger.warn("Invalid served-match expression for copilot model '{}': {}", model.getId(), e.getMessage());
            return null;
        }
    }
}
