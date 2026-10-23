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

package com.wisemapping.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Environment variable names are derived from the property names by Spring Boot relaxed binding.
 * A wrong guess is accepted silently and the value is never read, so the names documented in
 * {@link CopilotProperties} are pinned here.
 */
class CopilotPropertiesBindingTest {

    private static CopilotProperties bindFromEnvironment(Map<String, Object> variables) {
        final StandardEnvironment environment = new StandardEnvironment();
        final MutablePropertySources sources = environment.getPropertySources();
        sources.remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        sources.remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        sources.addFirst(new SystemEnvironmentPropertySource(
                StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, variables));
        return new Binder(ConfigurationPropertySources.get(environment))
                .bindOrCreate("app.ai.copilot", Bindable.of(CopilotProperties.class));
    }

    @Test
    @DisplayName("Should bind every documented environment variable, model list included")
    void shouldBindDocumentedVariables() {
        final Map<String, Object> variables = new java.util.HashMap<>();
        variables.put("APP_AI_COPILOT_ENABLED", "true");
        variables.put("APP_AI_COPILOT_BASEURL", "http://litellm:4000/v1");
        variables.put("APP_AI_COPILOT_APIKEY", "sk-test");
        variables.put("APP_AI_COPILOT_DISABLEFALLBACKS", "true");
        variables.put("APP_AI_COPILOT_TIMEOUTSECONDS", "120");
        variables.put("APP_AI_COPILOT_MAXPROMPTLENGTH", "500");
        variables.put("APP_AI_COPILOT_MAXTOPICS", "40");
        variables.put("APP_AI_COPILOT_MAXDEPTH", "2");
        variables.put("APP_AI_COPILOT_MODELS_0_ID", "smart-model-local");
        variables.put("APP_AI_COPILOT_MODELS_0_LABEL", "Qwen (local)");
        variables.put("APP_AI_COPILOT_MODELS_0_LOCALITY", "local");
        variables.put("APP_AI_COPILOT_MODELS_0_SERVEDMATCH", "(hosted_vllm/)?qwen3\\.5:9b");
        variables.put("APP_AI_COPILOT_MODELS_1_ID", "infomaniak-mistral-small-4");
        variables.put("APP_AI_COPILOT_MODELS_1_LOCALITY", "external");
        variables.put("APP_AI_COPILOT_MODELS_1_PROVIDER", "Infomaniak (Switzerland)");

        final CopilotProperties properties = bindFromEnvironment(variables);

        assertTrue(properties.isEnabled());
        assertEquals("http://litellm:4000/v1", properties.getBaseUrl());
        assertEquals("sk-test", properties.getApiKey());
        assertTrue(properties.isDisableFallbacks());
        assertEquals(120, properties.getTimeoutSeconds());
        assertEquals(500, properties.getMaxPromptLength());
        assertEquals(40, properties.getMaxTopics());
        assertEquals(2, properties.getMaxDepth());
        assertTrue(properties.isActive());

        final java.util.List<CopilotProperties.Model> models = properties.getEffectiveModels();
        assertEquals(2, models.size());
        assertEquals("smart-model-local", models.get(0).getId());
        assertEquals("Qwen (local)", models.get(0).getLabel());
        assertEquals(CopilotProperties.Locality.LOCAL, models.get(0).getLocality());
        assertEquals("(hosted_vllm/)?qwen3\\.5:9b", models.get(0).getServedMatch());
        assertEquals(CopilotProperties.Locality.EXTERNAL, models.get(1).getLocality());
        assertEquals("Infomaniak (Switzerland)", models.get(1).getProvider());
        assertEquals("infomaniak-mistral-small-4", models.get(1).getLabel(), "label defaults to the id");
        assertEquals("smart-model-local", properties.findModel(null).getId(), "first model is the default");
        assertEquals("infomaniak-mistral-small-4", properties.findModel("infomaniak-mistral-small-4").getId());
        assertNull(properties.findModel("unknown"));
    }

    @Test
    @DisplayName("Should still accept the single-model configuration")
    void shouldBindSingleModel() {
        final CopilotProperties properties = bindFromEnvironment(Map.of(
                "APP_AI_COPILOT_ENABLED", "true",
                "APP_AI_COPILOT_BASEURL", "https://api.openai.com/v1",
                "APP_AI_COPILOT_MODEL", "gpt-4o-mini",
                "APP_AI_COPILOT_PROVIDER", "OpenAI"));

        assertTrue(properties.isActive());
        final java.util.List<CopilotProperties.Model> models = properties.getEffectiveModels();
        assertEquals(1, models.size());
        assertEquals("gpt-4o-mini", models.get(0).getId());
        assertEquals(CopilotProperties.Locality.EXTERNAL, models.get(0).getLocality(), "external unless declared local");
        assertEquals("OpenAI", models.get(0).getProvider());
    }

    @Test
    @DisplayName("Should also bind the legacy form where the dash becomes an underscore")
    void shouldBindLegacyUnderscoreVariant() {
        // Spring Boot accepts both APP_AI_COPILOT_APIKEY (canonical) and APP_AI_COPILOT_API_KEY
        // (legacy). The canonical form is the one documented; this pins the measured behaviour.
        final CopilotProperties properties = bindFromEnvironment(Map.of(
                "APP_AI_COPILOT_API_KEY", "sk-test",
                "APP_AI_COPILOT_BASE_URL", "http://litellm:4000/v1"));

        assertEquals("sk-test", properties.getApiKey());
        assertEquals("http://litellm:4000/v1", properties.getBaseUrl());
    }

    @Test
    @DisplayName("Should NOT bind a variable without the APP_ prefix")
    void shouldNotBindWithoutPrefix() {
        final CopilotProperties properties = bindFromEnvironment(Map.of("AI_COPILOT_APIKEY", "sk-test"));
        assertEquals("", properties.getApiKey());
    }

    @Test
    @DisplayName("Should be inactive until enabled, endpoint and model are all set")
    void shouldRequireEndpointAndModel() {
        assertFalse(bindFromEnvironment(Map.of()).isActive());
        assertFalse(bindFromEnvironment(Map.of("APP_AI_COPILOT_ENABLED", "true",
                "APP_AI_COPILOT_MODEL", "m")).isActive());
        assertFalse(bindFromEnvironment(Map.of("APP_AI_COPILOT_ENABLED", "true",
                "APP_AI_COPILOT_BASEURL", "http://x/v1")).isActive());
        assertFalse(bindFromEnvironment(Map.of("APP_AI_COPILOT_BASEURL", "http://x/v1",
                "APP_AI_COPILOT_MODEL", "m")).isActive());
    }

    @Test
    @DisplayName("Should never print the API key")
    void shouldHideApiKey() {
        final CopilotProperties properties = bindFromEnvironment(Map.of("APP_AI_COPILOT_APIKEY", "sk-secret-value"));
        assertFalse(properties.toString().contains("sk-secret-value"));
    }
}
