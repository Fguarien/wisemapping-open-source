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
import com.sun.net.httpserver.HttpServer;
import com.wisemapping.config.CopilotProperties;
import com.wisemapping.exceptions.CopilotInvalidResponseException;
import com.wisemapping.exceptions.CopilotUnavailableException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the client against a local fake OpenAI-compatible server.
 */
class OpenAiCompatibleCopilotLlmClientTest {

    private HttpServer server;
    private final AtomicReference<String> path = new AtomicReference<>();
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private final AtomicReference<String> requestBody = new AtomicReference<>();
    private volatile int status = 200;
    private volatile String responseBody = "";
    private volatile long delayMillis = 0;

    private OpenAiCompatibleCopilotLlmClient client;
    private CopilotProperties properties;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            path.set(exchange.getRequestURI().getPath());
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            try {
                Thread.sleep(delayMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            final byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();

        properties = new CopilotProperties();
        properties.setEnabled(true);
        properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/v1/");
        properties.setModel("smart-model-local");
        properties.setApiKey("sk-test");
        properties.setTimeoutSeconds(5);

        client = new OpenAiCompatibleCopilotLlmClient();
        ReflectionTestUtils.setField(client, "properties", properties);
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    @DisplayName("Should post a chat completion and return the message content")
    void shouldCallChatCompletions() throws Exception {
        responseBody = "{\"model\":\"hosted_vllm/qwen3.5:9b\",\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"{\\\"title\\\":\\\"T\\\"}\"}}]}";

        final CopilotCompletion completion = client.complete("smart-model-local", "system rules", "a map about tea");

        assertEquals("{\"title\":\"T\"}", completion.content());
        assertEquals("hosted_vllm/qwen3.5:9b", completion.servedModel());
        assertEquals("/v1/chat/completions", path.get());
        assertEquals("Bearer sk-test", authorization.get());
        final JsonNode body = new ObjectMapper().readTree(requestBody.get());
        assertEquals("smart-model-local", body.get("model").asText());
        assertEquals(false, body.get("stream").asBoolean());
        assertNull(body.get("disable_fallbacks"), "not sent unless configured: OpenAI rejects unknown parameters");
        assertEquals("system", body.get("messages").get(0).get("role").asText());
        assertEquals("system rules", body.get("messages").get(0).get("content").asText());
        assertEquals("a map about tea", body.get("messages").get(1).get("content").asText());
    }

    @Test
    @DisplayName("Should send disable_fallbacks when configured")
    void shouldSendDisableFallbacks() throws Exception {
        properties.setDisableFallbacks(true);
        responseBody = "{\"choices\":[{\"message\":{\"content\":\"ok\"}}]}";

        final CopilotCompletion completion = client.complete("smart-model-local", "s", "u");

        final JsonNode body = new ObjectMapper().readTree(requestBody.get());
        assertTrue(body.get("disable_fallbacks").asBoolean());
        assertNull(completion.servedModel(), "no model field in the answer");
    }

    @Test
    @DisplayName("Should not send an Authorization header without an API key")
    void shouldSkipAuthorizationWithoutKey() throws Exception {
        properties.setApiKey("");
        responseBody = "{\"choices\":[{\"message\":{\"content\":\"ok\"}}]}";

        client.complete("smart-model-local", "s", "u");

        assertNull(authorization.get());
    }

    @Test
    @DisplayName("Should report an HTTP error as unavailable")
    void shouldReportHttpError() {
        status = 500;
        responseBody = "{\"error\":\"boom\"}";

        assertThrows(CopilotUnavailableException.class, () -> client.complete("smart-model-local", "s", "u"));
    }

    @Test
    @DisplayName("Should report a timeout as unavailable")
    void shouldReportTimeout() {
        properties.setTimeoutSeconds(1);
        delayMillis = 2500;
        responseBody = "{\"choices\":[{\"message\":{\"content\":\"late\"}}]}";

        assertThrows(CopilotUnavailableException.class, () -> client.complete("smart-model-local", "s", "u"));
    }

    @Test
    @DisplayName("Should reject an answer that is not a chat completion")
    void shouldRejectMalformedAnswer() {
        responseBody = "{\"choices\":[]}";
        assertThrows(CopilotInvalidResponseException.class, () -> client.complete("smart-model-local", "s", "u"));

        responseBody = "not json";
        assertThrows(CopilotInvalidResponseException.class, () -> client.complete("smart-model-local", "s", "u"));
    }

    @Test
    @DisplayName("Should report an unreachable endpoint as unavailable")
    void shouldReportUnreachableEndpoint() {
        properties.setBaseUrl("http://127.0.0.1:1/v1");
        assertThrows(CopilotUnavailableException.class, () -> client.complete("smart-model-local", "s", "u"));
    }
}
