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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration of the built-in AI copilot, which turns a prompt into a new mind map by calling
 * an OpenAI-compatible chat completions endpoint (OpenAI, a self-hosted LLM served by Ollama or
 * vLLM, a LiteLLM proxy...).
 *
 * <p>Bound from {@code app.ai.copilot.*}. Spring Boot relaxed binding drops the dashes of property
 * names, it does not turn them into underscores, and list entries are indexed. The environment
 * variables are therefore:</p>
 * <pre>
 * APP_AI_COPILOT_ENABLED            APP_AI_COPILOT_BASEURL          APP_AI_COPILOT_APIKEY
 * APP_AI_COPILOT_DISABLEFALLBACKS   APP_AI_COPILOT_TIMEOUTSECONDS   APP_AI_COPILOT_MAXPROMPTLENGTH
 * APP_AI_COPILOT_MAXTOPICS          APP_AI_COPILOT_MAXDEPTH         APP_AI_COPILOT_TEMPERATURE
 * APP_AI_COPILOT_MODELS_0_ID        APP_AI_COPILOT_MODELS_0_LABEL   APP_AI_COPILOT_MODELS_0_LOCALITY
 * APP_AI_COPILOT_MODELS_0_PROVIDER  APP_AI_COPILOT_MODELS_0_SERVEDMATCH   (then _1_, _2_...)
 * </pre>
 * <p>A single-model configuration ({@code APP_AI_COPILOT_MODEL}, {@code APP_AI_COPILOT_LOCALITY},
 * {@code APP_AI_COPILOT_PROVIDER}, {@code APP_AI_COPILOT_SERVEDMATCH}) is still accepted when no
 * list is given.</p>
 *
 * <p>The copilot is active only when {@link #isActive()} is true. Otherwise the frontend keeps the
 * default behaviour (link to the hosted ChatGPT copilot).</p>
 */
@Configuration
@ConfigurationProperties(prefix = "app.ai.copilot")
public class CopilotProperties {

    /**
     * Where a model runs, as declared by the administrator and shown to users.
     */
    public enum Locality {
        /**
         * The model runs on infrastructure controlled by the organization: prompts stay in the network.
         */
        LOCAL,
        /**
         * The model runs at a third-party provider: prompts leave the network.
         */
        EXTERNAL
    }

    /**
     * One model the user can pick in the copilot dialog.
     */
    public static class Model {
        /**
         * Model name sent to the endpoint (for LiteLLM, the model alias).
         */
        private String id = "";
        /**
         * Name shown to users. Defaults to the id.
         */
        private String label = "";
        private Locality locality = Locality.EXTERNAL;
        /**
         * Who runs the model, shown to users for external models, e.g. "OpenAI" or "Infomaniak (Switzerland)".
         */
        private String provider = "";
        /**
         * Regular expression the {@code model} field of the completion must match for the answer to be
         * considered served by this model. Proxies such as LiteLLM can silently serve a fallback under
         * the requested name; this is how it is detected. Empty: the locality is reported as unverified.
         */
        private String servedMatch = "";

        public Model() {
        }

        public Model(String id, String label, Locality locality, String provider, String servedMatch) {
            this.id = id;
            this.label = label;
            this.locality = locality;
            this.provider = provider;
            this.servedMatch = servedMatch;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        @NotNull
        public String getLabel() {
            return hasText(label) ? label : (id == null ? "" : id);
        }

        public void setLabel(String label) {
            this.label = label;
        }

        public Locality getLocality() {
            return locality == null ? Locality.EXTERNAL : locality;
        }

        public void setLocality(Locality locality) {
            this.locality = locality;
        }

        public String getProvider() {
            return provider == null ? "" : provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public String getServedMatch() {
            return servedMatch == null ? "" : servedMatch;
        }

        public void setServedMatch(String servedMatch) {
            this.servedMatch = servedMatch;
        }
    }

    /**
     * Turns the built-in copilot on.
     */
    private boolean enabled = false;

    /**
     * Base URL of the OpenAI-compatible API, up to and including the version segment,
     * e.g. {@code https://api.openai.com/v1} or {@code http://litellm:4000/v1}.
     */
    private String baseUrl = "";

    /**
     * Bearer token sent to the endpoint. Optional for endpoints without authentication.
     */
    private String apiKey = "";

    /**
     * Models offered to users. The first one is the default.
     */
    private List<Model> models = new ArrayList<>();

    /**
     * Single-model configuration, used only when {@link #models} is empty.
     */
    private String model = "";
    private Locality locality = Locality.EXTERNAL;
    private String provider = "";
    private String servedMatch = "";

    /**
     * Sends {@code "disable_fallbacks": true} with every completion. LiteLLM then fails instead of
     * serving another model under the requested name. Leave it off for endpoints that reject unknown
     * parameters (OpenAI does).
     */
    private boolean disableFallbacks = false;

    /**
     * Read timeout of the completion call. Local models can be slow: keep it generous.
     */
    private int timeoutSeconds = 90;

    /**
     * Maximum number of characters accepted in the user prompt.
     */
    private int maxPromptLength = 2000;

    /**
     * Maximum number of topics (central topic excluded) kept from the model answer.
     */
    private int maxTopics = 60;

    /**
     * Maximum depth kept below the central topic.
     */
    private int maxDepth = 3;

    /**
     * Sampling temperature sent to the model.
     */
    private double temperature = 0.3;

    /**
     * Models that can actually be used: the configured list without blank ids, or the single-model
     * configuration when there is no list.
     */
    @NotNull
    public List<Model> getEffectiveModels() {
        final List<Model> result = new ArrayList<>();
        if (models != null) {
            for (Model candidate : models) {
                if (candidate != null && hasText(candidate.getId())) {
                    result.add(candidate);
                }
            }
        }
        if (result.isEmpty() && hasText(model)) {
            result.add(new Model(model, model, locality, provider, servedMatch));
        }
        return List.copyOf(result);
    }

    /**
     * @return the model with this id, the default (first) model when {@code id} is null or blank,
     * or null when the id is unknown.
     */
    @Nullable
    public Model findModel(@Nullable String id) {
        final List<Model> available = getEffectiveModels();
        if (available.isEmpty()) {
            return null;
        }
        if (!hasText(id)) {
            return available.get(0);
        }
        return available.stream().filter(m -> m.getId().equals(id.trim())).findFirst().orElse(null);
    }

    public boolean isActive() {
        return enabled && hasText(baseUrl) && !getEffectiveModels().isEmpty();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public List<Model> getModels() {
        return models;
    }

    public void setModels(List<Model> models) {
        this.models = models;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public Locality getLocality() {
        return locality;
    }

    public void setLocality(Locality locality) {
        this.locality = locality;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getServedMatch() {
        return servedMatch;
    }

    public void setServedMatch(String servedMatch) {
        this.servedMatch = servedMatch;
    }

    public boolean isDisableFallbacks() {
        return disableFallbacks;
    }

    public void setDisableFallbacks(boolean disableFallbacks) {
        this.disableFallbacks = disableFallbacks;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public int getMaxPromptLength() {
        return maxPromptLength;
    }

    public void setMaxPromptLength(int maxPromptLength) {
        this.maxPromptLength = maxPromptLength;
    }

    public int getMaxTopics() {
        return maxTopics;
    }

    public void setMaxTopics(int maxTopics) {
        this.maxTopics = maxTopics;
    }

    public int getMaxDepth() {
        return maxDepth;
    }

    public void setMaxDepth(int maxDepth) {
        this.maxDepth = maxDepth;
    }

    public double getTemperature() {
        return temperature;
    }

    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    @Override
    public String toString() {
        // Never print the API key.
        return "CopilotProperties{" +
                "enabled=" + enabled +
                ", baseUrl='" + baseUrl + '\'' +
                ", models=" + getEffectiveModels().stream().map(Model::getId).toList() +
                ", apiKey=" + (hasText(apiKey) ? "***" : "<none>") +
                ", disableFallbacks=" + disableFallbacks +
                ", timeoutSeconds=" + timeoutSeconds +
                ", maxPromptLength=" + maxPromptLength +
                ", maxTopics=" + maxTopics +
                ", maxDepth=" + maxDepth +
                '}';
    }
}
