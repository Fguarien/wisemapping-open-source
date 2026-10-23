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
import com.wisemapping.rest.model.RestUser;
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

import java.util.Map;

import static com.wisemapping.test.rest.RestHelper.createTestUser;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Default configuration: the copilot is off, the frontend keeps the hosted copilot link.
 */
@SpringBootTest(classes = {AppConfig.class}, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("Copilot Controller Tests - copilot disabled")
class RestCopilotDisabledTest {

    @LocalServerPort
    private int port;

    @Test
    @DisplayName("Should report copilotEnabled false and answer 404")
    void shouldBeDisabledByDefault() {
        final TestRestTemplate restTemplate = new TestRestTemplate("http://localhost:" + port + "/");

        final ResponseEntity<String> config = restTemplate.getForEntity("/api/restful/app/config", String.class);
        assertTrue(config.getBody().contains("\"copilotEnabled\":false"));
        assertTrue(config.getBody().contains("\"copilotModels\":[]"));

        final RestUser user = createTestUser(restTemplate);
        final HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
        final ResponseEntity<String> response = restTemplate.withBasicAuth(user.getEmail(), user.getPassword())
                .exchange("/api/restful/ai/copilot/maps", HttpMethod.POST,
                        new HttpEntity<>(Map.of("prompt", "tea"), headers), String.class);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }
}
