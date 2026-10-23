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
import com.wisemapping.exceptions.WiseMappingException;
import com.wisemapping.model.Account;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Built-in AI copilot: turns a prompt into a new mind map owned by the user.
 */
public interface CopilotService {

    /**
     * @return true when the copilot is enabled and has an endpoint and at least one model.
     */
    boolean isActive();

    /**
     * @return the models users can pick, the default first.
     */
    @NotNull
    List<CopilotProperties.Model> getModels();

    /**
     * Asks the model for an outline and stores it as a new private map of {@code user}.
     *
     * @param prompt   what the map should be about
     * @param language ISO 639-1 code of the language the topics should be written in, or null to
     *                 follow the language of the prompt
     * @param modelId  one of {@link #getModels()}, or null for the default model
     */
    @NotNull
    CopilotResult createMap(@NotNull String prompt, @Nullable String language, @Nullable String modelId,
                            @NotNull Account user) throws WiseMappingException;
}
