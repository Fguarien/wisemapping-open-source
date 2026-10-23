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

package com.wisemapping.test.rest;

import com.wisemapping.config.AppConfig;
import com.wisemapping.exceptions.CopilotUnavailableException;
import com.wisemapping.rest.model.RestCopilotMap;
import com.wisemapping.rest.model.RestMindmapList;
import com.wisemapping.rest.model.RestUser;
import com.wisemapping.service.copilot.CopilotCompletion;
import com.wisemapping.service.copilot.CopilotLlmClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.Map;

import static com.wisemapping.test.rest.RestHelper.createTestUser;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(
        classes = {AppConfig.class},
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "app.ai.copilot.enabled=true",
                "app.ai.copilot.base-url=http://llm.example.com/v1",
                "app.ai.copilot.max-prompt-length=100",
                "resilience4j.ratelimiter.instances.copilotLimiter.limit-for-period=1000",
                "app.ai.copilot.models[0].id=local-alias",
                "app.ai.copilot.models[0].label=On-prem model",
                "app.ai.copilot.models[0].locality=local",
                "app.ai.copilot.models[0].served-match=local-model-v1",
                "app.ai.copilot.models[1].id=cloud-alias",
                "app.ai.copilot.models[1].label=Cloud model",
                "app.ai.copilot.models[1].locality=external",
                "app.ai.copilot.models[1].provider=Cloud Inc",
                "app.ai.copilot.models[1].served-match=cloud-.*"
        })
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("Copilot Controller Tests - copilot enabled")
class RestCopilotControllerTest {

    private static final String TEA_ANSWER = """
            ```json
            {"title": "Tea", "topics": [{"text": "Green", "children": [{"text": "Sencha"}]}, {"text": "Black"}]}
            ```""";

    @LocalServerPort
    private int port;

    @MockitoBean(name = "copilotLlmClient")
    private CopilotLlmClient llmClient;

    private TestRestTemplate restTemplate;
    private RestUser user;

    @BeforeEach
    void createUser() {
        this.restTemplate = new TestRestTemplate("http://localhost:" + port + "/");
        this.user = createTestUser(restTemplate);
    }

    private TestRestTemplate authenticated() {
        return restTemplate.withBasicAuth(user.getEmail(), user.getPassword());
    }

    private static HttpHeaders jsonHeaders() {
        final HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        return headers;
    }

    private ResponseEntity<String> post(TestRestTemplate template, Map<String, String> body) {
        return template.exchange("/api/restful/ai/copilot/maps", HttpMethod.POST, new HttpEntity<>(body, jsonHeaders()),
                String.class);
    }

    private ResponseEntity<RestCopilotMap> create(Map<String, String> body) {
        return authenticated().exchange("/api/restful/ai/copilot/maps", HttpMethod.POST,
                new HttpEntity<>(body, jsonHeaders()), RestCopilotMap.class);
    }

    private int countMaps() {
        final ResponseEntity<RestMindmapList> maps = authenticated().exchange("/api/restful/maps/", HttpMethod.GET,
                new HttpEntity<>(jsonHeaders()), RestMindmapList.class);
        return maps.getBody().getMindmapsInfo().size();
    }

    @Test
    @DisplayName("Should expose the models and their locality, without endpoint or match expression")
    void shouldExposeModels() {
        final ResponseEntity<String> response = restTemplate.getForEntity("/api/restful/app/config", String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        final String body = response.getBody();
        assertTrue(body.contains("\"copilotEnabled\":true"));
        assertTrue(body.contains("{\"id\":\"local-alias\",\"label\":\"On-prem model\",\"locality\":\"local\",\"provider\":\"\"}"), body);
        assertTrue(body.contains("{\"id\":\"cloud-alias\",\"label\":\"Cloud model\",\"locality\":\"external\",\"provider\":\"Cloud Inc\"}"), body);
        assertFalse(body.contains("llm.example.com"), "the endpoint must not leak");
        assertFalse(body.contains("local-model-v1"), "the match expression must not leak");
    }

    @Test
    @DisplayName("Should create a map with the default model and report it as local")
    void shouldCreateMapLocally() throws Exception {
        when(llmClient.complete(anyString(), anyString(), anyString()))
                .thenReturn(new CopilotCompletion(TEA_ANSWER, "local-model-v1"));

        final ResponseEntity<RestCopilotMap> response = create(Map.of("prompt", "A map about tea", "language", "fr"));

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        final RestCopilotMap map = response.getBody();
        assertNotNull(map);
        assertEquals("Tea", map.getTitle());
        assertEquals("local-alias", map.getModel().getId());
        assertEquals("local", map.getModel().getLocality());
        assertEquals("local", map.getActualLocality());
        assertEquals("local-model-v1", map.getServedModel());
        assertFalse(map.isRoutingWarning());
        assertEquals("/api/restful/maps/" + map.getId(), response.getHeaders().getFirst("Location"));
        verify(llmClient).complete(eq("local-alias"), argThat(system -> system.contains("\"fr\"")), eq("A map about tea"));

        final HttpHeaders textHeaders = new HttpHeaders();
        textHeaders.setContentType(MediaType.TEXT_PLAIN);
        final ResponseEntity<String> xml = authenticated().exchange("/api/restful/maps/" + map.getId() + "/document/xml",
                HttpMethod.GET, new HttpEntity<>(textHeaders), String.class);
        assertTrue(xml.getStatusCode().is2xxSuccessful());
        assertTrue(xml.getBody().contains("text=\"Green\""));
        assertTrue(xml.getBody().contains("text=\"Sencha\""));
        assertTrue(xml.getBody().contains("text=\"Black\""));
    }

    @Test
    @DisplayName("Should use the chosen external model and report it as external")
    void shouldUseChosenModel() throws Exception {
        when(llmClient.complete(eq("cloud-alias"), anyString(), anyString()))
                .thenReturn(new CopilotCompletion(TEA_ANSWER, "cloud-large-2"));

        final ResponseEntity<RestCopilotMap> response = create(Map.of("prompt", "tea", "model", "cloud-alias"));

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals("cloud-alias", response.getBody().getModel().getId());
        assertEquals("Cloud Inc", response.getBody().getModel().getProvider());
        assertEquals("external", response.getBody().getActualLocality());
        assertFalse(response.getBody().isRoutingWarning());
    }

    @Test
    @DisplayName("Should refuse, and create no map, when a local model was served by something else")
    void shouldRefuseLocalModelServedElsewhere() throws Exception {
        when(llmClient.complete(anyString(), anyString(), anyString()))
                .thenReturn(new CopilotCompletion(TEA_ANSWER, "cloud-large-2"));
        final int before = countMaps();

        final ResponseEntity<String> response = post(authenticated(), Map.of("prompt", "tea"));

        assertEquals(HttpStatus.BAD_GATEWAY, response.getStatusCode());
        assertEquals(before, countMaps(), "no map must be created");
    }

    @Test
    @DisplayName("Should keep the map but flag it when an external model was served by something else")
    void shouldFlagExternalModelServedElsewhere() throws Exception {
        when(llmClient.complete(anyString(), anyString(), anyString()))
                .thenReturn(new CopilotCompletion(TEA_ANSWER, "ollama_chat/qwen3.5:9b"));

        final ResponseEntity<RestCopilotMap> response = create(Map.of("prompt", "tea", "model", "cloud-alias"));

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals("unverified", response.getBody().getActualLocality());
        assertEquals("ollama_chat/qwen3.5:9b", response.getBody().getServedModel());
        assertTrue(response.getBody().isRoutingWarning());
    }

    @Test
    @DisplayName("Should reject an unknown model without calling the endpoint")
    void shouldRejectUnknownModel() throws Exception {
        assertEquals(HttpStatus.BAD_REQUEST, post(authenticated(), Map.of("prompt", "tea", "model", "gpt-5")).getStatusCode());
        verify(llmClient, times(0)).complete(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Should give a unique title when the user already has a map with that title")
    void shouldMakeTitleUnique() throws Exception {
        when(llmClient.complete(anyString(), anyString(), anyString()))
                .thenReturn(new CopilotCompletion(TEA_ANSWER, "local-model-v1"));

        create(Map.of("prompt", "tea"));
        final ResponseEntity<RestCopilotMap> second = create(Map.of("prompt", "tea again"));

        assertEquals(HttpStatus.CREATED, second.getStatusCode());
        assertEquals("Tea (2)", second.getBody().getTitle());
    }

    @Test
    @DisplayName("Should reject an empty or too long prompt without calling the endpoint")
    void shouldRejectInvalidPrompt() throws Exception {
        assertEquals(HttpStatus.BAD_REQUEST, post(authenticated(), Map.of("prompt", "   ")).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, post(authenticated(), Map.of("prompt", "x".repeat(101))).getStatusCode());
        verify(llmClient, times(0)).complete(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Should answer 503 when the AI endpoint is unavailable")
    void shouldReportUnavailableEndpoint() throws Exception {
        when(llmClient.complete(anyString(), anyString(), anyString()))
                .thenThrow(new CopilotUnavailableException("AI endpoint could not be reached"));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, post(authenticated(), Map.of("prompt", "tea")).getStatusCode());
    }

    @Test
    @DisplayName("Should retry once, then answer 502 when the model answer is unusable")
    void shouldReportUnusableAnswer() throws Exception {
        when(llmClient.complete(anyString(), anyString(), anyString()))
                .thenReturn(new CopilotCompletion("I cannot help with that.", "local-model-v1"));

        assertEquals(HttpStatus.BAD_GATEWAY, post(authenticated(), Map.of("prompt", "tea")).getStatusCode());
        verify(llmClient, times(2)).complete(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Should recover when the second answer is usable")
    void shouldRecoverOnRetry() throws Exception {
        when(llmClient.complete(anyString(), anyString(), anyString())).thenReturn(
                new CopilotCompletion("Sure! Here it is:", "local-model-v1"),
                new CopilotCompletion(TEA_ANSWER, "local-model-v1"));

        assertEquals(HttpStatus.CREATED, post(authenticated(), Map.of("prompt", "tea")).getStatusCode());
    }

    @Test
    @DisplayName("Should require authentication")
    void shouldRequireAuthentication() {
        final ResponseEntity<String> response = post(restTemplate, Map.of("prompt", "tea"));
        assertTrue(response.getStatusCode().is4xxClientError());
    }
}
