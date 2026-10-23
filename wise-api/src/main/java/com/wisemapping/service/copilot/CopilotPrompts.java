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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Prompts sent to the copilot model. They ask for one JSON object and nothing else, which small
 * local models follow reliably when the shape is shown explicitly.
 */
final class CopilotPrompts {

    private CopilotPrompts() {
    }

    @NotNull
    static String systemPrompt(int maxDepth, int maxTopics, @Nullable String language) {
        final String languageRule = language != null
                ? "Write the title and every topic in the language whose ISO 639-1 code is \"" + language + "\"."
                : "Write the title and every topic in the language of the user's request.";

        return """
                You are a mind map assistant. Turn the user's request into a structured mind map.

                Answer with ONE JSON object and nothing else: no explanation, no Markdown, no code fence.
                The object has exactly this shape:
                {"title": "central idea", "topics": [{"text": "main branch", "children": [{"text": "detail", "children": []}]}]}

                Rules:
                - %s
                - 3 to 8 main branches; at most %d levels below the title; at most %d topics in total.
                - Each topic is a short phrase (2 to 8 words), not a sentence. No numbering, no bullets, no emoji.
                - Group related ideas under the same branch; do not repeat a topic.
                """.formatted(languageRule, maxDepth, maxTopics);
    }

    @NotNull
    static String retryPrompt(@NotNull String request) {
        return request + "\n\nYour previous answer could not be parsed. Reply with the JSON object only, "
                + "starting with { and ending with }.";
    }
}
