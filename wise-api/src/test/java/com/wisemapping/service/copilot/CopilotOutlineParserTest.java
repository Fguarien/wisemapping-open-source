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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CopilotOutlineParserTest {

    private final CopilotOutlineParser parser = new CopilotOutlineParser(60, 3);

    @Test
    @DisplayName("Should parse the documented shape")
    void shouldParseDocumentedShape() throws Exception {
        final CopilotOutline outline = parser.parse("""
                {"title": "Trip to Japan", "topics": [
                  {"text": "Budget", "children": [{"text": "Flights", "children": []}, {"text": "Hotels"}]},
                  {"text": "Itinerary", "children": []}
                ]}""");

        assertEquals("Trip to Japan", outline.title());
        assertEquals(2, outline.children().size());
        assertEquals("Budget", outline.children().get(0).text());
        assertEquals(2, outline.children().get(0).children().size());
        assertEquals(4, outline.countTopics());
    }

    @Test
    @DisplayName("Should unwrap a Markdown fence and drop a reasoning block")
    void shouldUnwrapFenceAndThinking() throws Exception {
        final CopilotOutline outline = parser.parse("""
                <think>The user wants a map about tea. {not json}</think>
                Here is your map:
                ```json
                {"title": "Tea", "topics": ["Green", "Black"]}
                ```
                Enjoy!""");

        assertEquals("Tea", outline.title());
        assertEquals(2, outline.children().size());
        assertEquals("Black", outline.children().get(1).text());
    }

    @Test
    @DisplayName("Should accept alternative key names")
    void shouldAcceptAlternativeKeys() throws Exception {
        final CopilotOutline outline = parser.parse(
                "{\"name\": \"Project\", \"children\": [{\"title\": \"Scope\", \"subtopics\": [{\"name\": \"In\"}]}]}");

        assertEquals("Project", outline.title());
        assertEquals("Scope", outline.children().get(0).text());
        assertEquals("In", outline.children().get(0).children().get(0).text());
    }

    @Test
    @DisplayName("Should clean list markers, emphasis and whitespace but keep leading numbers")
    void shouldSanitizeText() throws Exception {
        final CopilotOutline outline = parser.parse(
                "{\"title\": \"  **Plan**\\n\", \"topics\": [\"- First   step\", \"2. Second\", \"2026 goals\", \"   \"]}");

        assertEquals("Plan", outline.title());
        assertEquals(3, outline.children().size());
        assertEquals("First step", outline.children().get(0).text());
        assertEquals("Second", outline.children().get(1).text());
        assertEquals("2026 goals", outline.children().get(2).text());
    }

    @Test
    @DisplayName("Should bound topic length")
    void shouldBoundTopicLength() throws Exception {
        final String longText = "x".repeat(500);
        final CopilotOutline outline = parser.parse("{\"title\": \"T\", \"topics\": [\"" + longText + "\"]}");

        final String text = outline.children().get(0).text();
        assertEquals(CopilotOutlineParser.MAX_TOPIC_LENGTH, text.length());
        assertTrue(text.endsWith("…"));
    }

    @Test
    @DisplayName("Should cut levels deeper than the maximum depth")
    void shouldBoundDepth() throws Exception {
        final CopilotOutlineParser shallow = new CopilotOutlineParser(60, 2);
        final CopilotOutline outline = shallow.parse("""
                {"title": "T", "topics": [{"text": "L1", "children": [{"text": "L2", "children": [{"text": "L3"}]}]}]}""");

        final CopilotOutline.Node level2 = outline.children().get(0).children().get(0);
        assertEquals("L2", level2.text());
        assertTrue(level2.children().isEmpty());
    }

    @Test
    @DisplayName("Should keep upper levels first when the topic budget is exceeded")
    void shouldBoundTopicCountBreadthFirst() throws Exception {
        final CopilotOutlineParser small = new CopilotOutlineParser(3, 3);
        final CopilotOutline outline = small.parse("""
                {"title": "T", "topics": [
                  {"text": "A", "children": [{"text": "A1"}, {"text": "A2"}]},
                  {"text": "B", "children": [{"text": "B1"}]}
                ]}""");

        assertEquals(3, outline.countTopics());
        assertEquals("A", outline.children().get(0).text());
        assertEquals("B", outline.children().get(1).text());
        assertEquals(1, outline.children().get(0).children().size());
        assertEquals("A1", outline.children().get(0).children().get(0).text());
        assertTrue(outline.children().get(1).children().isEmpty());
    }

    @Test
    @DisplayName("Should reject answers without JSON, title or topics")
    void shouldRejectUnusableAnswers() {
        assertThrows(CopilotOutlineParser.CopilotOutlineException.class, () -> parser.parse(null));
        assertThrows(CopilotOutlineParser.CopilotOutlineException.class, () -> parser.parse("   "));
        assertThrows(CopilotOutlineParser.CopilotOutlineException.class, () -> parser.parse("Sure! Here is a map about tea."));
        assertThrows(CopilotOutlineParser.CopilotOutlineException.class, () -> parser.parse("[\"a\", \"b\"]"));
        assertThrows(CopilotOutlineParser.CopilotOutlineException.class, () -> parser.parse("{\"topics\": [\"a\"]}"));
        assertThrows(CopilotOutlineParser.CopilotOutlineException.class, () -> parser.parse("{\"title\": \"T\", \"topics\": []}"));
        assertThrows(CopilotOutlineParser.CopilotOutlineException.class, () -> parser.parse("{\"title\": \"T\", \"topics\": [1, true]}"));
    }
}
