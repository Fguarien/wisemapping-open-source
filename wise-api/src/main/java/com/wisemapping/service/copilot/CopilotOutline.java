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

import java.util.List;

/**
 * Validated tree returned by the copilot model: a central topic and its sub-topics.
 * Instances are produced by {@link CopilotOutlineParser}, which enforces the configured limits.
 */
public record CopilotOutline(@NotNull String title, @NotNull List<Node> children) {

    public record Node(@NotNull String text, @NotNull List<Node> children) {
    }

    public int countTopics() {
        return count(children);
    }

    private static int count(@NotNull List<Node> nodes) {
        int result = 0;
        for (Node node : nodes) {
            result += 1 + count(node.children());
        }
        return result;
    }
}
