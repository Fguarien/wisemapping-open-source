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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.wisemapping.config.CopilotProperties;
import com.wisemapping.exceptions.CopilotInvalidResponseException;
import com.wisemapping.exceptions.CopilotUnavailableException;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Calls {@code POST {baseUrl}/chat/completions} of an OpenAI-compatible API (OpenAI, Ollama,
 * vLLM, LiteLLM...). Streaming is not used: the copilot needs the whole answer to build the map.
 */
@Component("copilotLlmClient")
public class OpenAiCompatibleCopilotLlmClient implements CopilotLlmClient {
    private static final Logger logger = LoggerFactory.getLogger(OpenAiCompatibleCopilotLlmClient.class);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);

    @Autowired
    private CopilotProperties properties;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @NotNull
    @Override
    public CopilotCompletion complete(@NotNull String model, @NotNull String systemPrompt, @NotNull String userPrompt)
            throws CopilotUnavailableException, CopilotInvalidResponseException {

        final ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model);
        body.put("temperature", properties.getTemperature());
        body.put("stream", false);
        if (properties.isDisableFallbacks()) {
            // LiteLLM: fail instead of silently serving another model under the requested name.
            body.put("disable_fallbacks", true);
        }
        final ArrayNode messages = body.putArray("messages");
        messages.addObject().put("role", "system").put("content", systemPrompt);
        messages.addObject().put("role", "user").put("content", userPrompt);

        final String answer;
        final long start = System.currentTimeMillis();
        try {
            RestClient.RequestBodySpec request = restClient().post()
                    .uri(completionsUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON);
            final String apiKey = properties.getApiKey();
            if (apiKey != null && !apiKey.isBlank()) {
                request = request.header("Authorization", "Bearer " + apiKey);
            }
            answer = request.body(objectMapper.writeValueAsString(body))
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException e) {
            logger.warn("Copilot endpoint answered HTTP {} after {} ms", e.getStatusCode().value(),
                    System.currentTimeMillis() - start);
            throw new CopilotUnavailableException("AI endpoint returned HTTP " + e.getStatusCode().value());
        } catch (RestClientException e) {
            logger.warn("Copilot endpoint could not be reached after {} ms: {}",
                    System.currentTimeMillis() - start, e.getMessage());
            throw new CopilotUnavailableException("AI endpoint could not be reached");
        } catch (Exception e) {
            throw new CopilotUnavailableException("AI request failed: " + e.getMessage());
        }
        logger.debug("Copilot completion received in {} ms", System.currentTimeMillis() - start);
        return extractContent(answer);
    }

    @NotNull
    CopilotCompletion extractContent(String answer) throws CopilotInvalidResponseException {
        try {
            final JsonNode root = objectMapper.readTree(answer == null ? "" : answer);
            final JsonNode content = root.path("choices").path(0).path("message").path("content");
            if (!content.isTextual() || content.asText().isBlank()) {
                throw new CopilotInvalidResponseException("AI answer has no message content");
            }
            final JsonNode served = root.path("model");
            return new CopilotCompletion(content.asText(), served.isTextual() ? served.asText() : null);
        } catch (CopilotInvalidResponseException e) {
            throw e;
        } catch (Exception e) {
            throw new CopilotInvalidResponseException("AI answer is not a chat completion");
        }
    }

    @NotNull
    private String completionsUrl() {
        String base = properties.getBaseUrl().trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/chat/completions";
    }

    @NotNull
    private RestClient restClient() {
        final HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
        final JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(Math.max(1, properties.getTimeoutSeconds())));
        return RestClient.builder().requestFactory(factory).build();
    }
}
