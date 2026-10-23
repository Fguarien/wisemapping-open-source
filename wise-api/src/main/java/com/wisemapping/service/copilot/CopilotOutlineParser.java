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
import com.wisemapping.model.Constants;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns the raw answer of the copilot model into a bounded {@link CopilotOutline}.
 *
 * <p>Models do not always follow instructions to the letter, so the parser is lenient on the
 * envelope and strict on the content:</p>
 * <ul>
 *     <li>a reasoning block ({@code <think>...</think>}) is removed;</li>
 *     <li>a Markdown code fence ({@code ```json ... ```}) is unwrapped;</li>
 *     <li>otherwise the outermost JSON object of the answer is used;</li>
 *     <li>topic children may be named {@code children}, {@code topics} or {@code subtopics}, topic
 *     text {@code text}, {@code title} or {@code name}, and a topic may be a plain string;</li>
 *     <li>texts are trimmed, control characters removed, whitespace collapsed and length bounded;</li>
 *     <li>depth and total number of topics are bounded, keeping the upper levels first.</li>
 * </ul>
 * Anything that does not yield a title and at least one topic is rejected.
 */
public class CopilotOutlineParser {

    static final int MAX_TOPIC_LENGTH = 120;

    private static final Pattern THINK_BLOCK = Pattern.compile("(?is)<think>.*?</think>");
    private static final Pattern CODE_FENCE = Pattern.compile("(?s)```[a-zA-Z]*\\s*(.*?)```");
    private static final String[] CHILDREN_KEYS = {"children", "topics", "subtopics"};
    private static final String[] TEXT_KEYS = {"text", "title", "name", "topic"};

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final int maxTopics;
    private final int maxDepth;

    public CopilotOutlineParser(int maxTopics, int maxDepth) {
        if (maxTopics < 1 || maxDepth < 1) {
            throw new IllegalArgumentException("maxTopics and maxDepth must be positive");
        }
        this.maxTopics = maxTopics;
        this.maxDepth = maxDepth;
    }

    @NotNull
    public CopilotOutline parse(@Nullable String answer) throws CopilotOutlineException {
        if (answer == null || answer.isBlank()) {
            throw new CopilotOutlineException("Empty answer");
        }

        final JsonNode root;
        try {
            root = objectMapper.readTree(extractJson(answer));
        } catch (Exception e) {
            throw new CopilotOutlineException("Answer is not valid JSON: " + e.getMessage());
        }
        if (root == null || !root.isObject()) {
            throw new CopilotOutlineException("Answer is not a JSON object");
        }

        final String title = sanitize(firstText(root), Constants.MAX_MAP_NAME_LENGTH);
        if (title.isEmpty()) {
            throw new CopilotOutlineException("Answer has no title");
        }

        final List<MutableNode> topics = readChildren(root, 1);
        final List<CopilotOutline.Node> children = keepWithinBudget(topics);
        if (children.isEmpty()) {
            throw new CopilotOutlineException("Answer has no topic");
        }
        return new CopilotOutline(title, children);
    }

    @NotNull
    static String extractJson(@NotNull String answer) {
        String text = THINK_BLOCK.matcher(answer).replaceAll("").trim();

        final Matcher fence = CODE_FENCE.matcher(text);
        if (fence.find()) {
            text = fence.group(1).trim();
        }

        final int start = text.indexOf('{');
        final int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return text;
    }

    @NotNull
    private List<MutableNode> readChildren(@NotNull JsonNode node, int depth) {
        final List<MutableNode> result = new ArrayList<>();
        if (depth > maxDepth) {
            return result;
        }
        final JsonNode children = firstArray(node);
        if (children == null) {
            return result;
        }
        for (JsonNode child : children) {
            final String text;
            final List<MutableNode> grandChildren;
            if (child.isTextual()) {
                text = sanitize(child.asText(), MAX_TOPIC_LENGTH);
                grandChildren = new ArrayList<>();
            } else if (child.isObject()) {
                text = sanitize(firstText(child), MAX_TOPIC_LENGTH);
                grandChildren = readChildren(child, depth + 1);
            } else {
                continue;
            }
            if (!text.isEmpty()) {
                result.add(new MutableNode(text, grandChildren));
            }
        }
        return result;
    }

    /**
     * Keeps at most {@code maxTopics} topics, level by level, so that a long answer loses its
     * deepest details rather than whole branches.
     */
    @NotNull
    private List<CopilotOutline.Node> keepWithinBudget(@NotNull List<MutableNode> topics) {
        int budget = maxTopics;
        final Deque<MutableNode> queue = new ArrayDeque<>(topics);
        while (!queue.isEmpty()) {
            final MutableNode current = queue.poll();
            if (budget > 0) {
                current.kept = true;
                budget--;
                queue.addAll(current.children);
            }
        }
        return freeze(topics);
    }

    @NotNull
    private static List<CopilotOutline.Node> freeze(@NotNull List<MutableNode> nodes) {
        final List<CopilotOutline.Node> result = new ArrayList<>();
        for (MutableNode node : nodes) {
            if (node.kept) {
                result.add(new CopilotOutline.Node(node.text, freeze(node.children)));
            }
        }
        return List.copyOf(result);
    }

    @Nullable
    private static JsonNode firstArray(@NotNull JsonNode node) {
        for (String key : CHILDREN_KEYS) {
            final JsonNode value = node.get(key);
            if (value != null && value.isArray()) {
                return value;
            }
        }
        return null;
    }

    @Nullable
    private static String firstText(@NotNull JsonNode node) {
        for (String key : TEXT_KEYS) {
            final JsonNode value = node.get(key);
            if (value != null && value.isTextual()) {
                return value.asText();
            }
        }
        return null;
    }

    @NotNull
    static String sanitize(@Nullable String value, int maxLength) {
        if (value == null) {
            return "";
        }
        String result = value.replaceAll("\\p{Cntrl}", " ")
                .replaceAll("\\s+", " ")
                .trim();
        // Markdown emphasis and list markers are noise in a topic.
        result = result.replaceAll("^(?:[-*#>]+|\\d+[.)])\\s+", "").replace("**", "").trim();
        if (result.length() > maxLength) {
            result = result.substring(0, maxLength - 1).trim() + "…";
        }
        return result;
    }

    private static final class MutableNode {
        private final String text;
        private final List<MutableNode> children;
        private boolean kept;

        private MutableNode(String text, List<MutableNode> children) {
            this.text = text;
            this.children = children;
        }
    }

    /**
     * Raised when the model answer cannot be turned into an outline.
     */
    public static class CopilotOutlineException extends Exception {
        public CopilotOutlineException(String message) {
            super(message);
        }
    }
}
