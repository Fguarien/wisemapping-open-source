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

import com.wisemapping.exceptions.CopilotInvalidResponseException;
import com.wisemapping.exceptions.CopilotUnavailableException;
import org.jetbrains.annotations.NotNull;

/**
 * Chat completion call used by the copilot. The default implementation talks to any
 * OpenAI-compatible endpoint; tests replace it with a stub.
 */
public interface CopilotLlmClient {

    /**
     * @param model model name sent to the endpoint
     * @return the content of the first choice and the model that served it.
     */
    @NotNull
    CopilotCompletion complete(@NotNull String model, @NotNull String systemPrompt, @NotNull String userPrompt)
            throws CopilotUnavailableException, CopilotInvalidResponseException;
}
