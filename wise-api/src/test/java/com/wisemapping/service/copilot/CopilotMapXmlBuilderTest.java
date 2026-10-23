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

import com.wisemapping.mindmap.utils.MindmapUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.SchemaFactory;
import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CopilotMapXmlBuilderTest {

    private static CopilotOutline sample() {
        return new CopilotOutline("Q&A <\"plan\">", List.of(
                new CopilotOutline.Node("Right", List.of(
                        new CopilotOutline.Node("Right child 1", List.of()),
                        new CopilotOutline.Node("Right child 2", List.of()))),
                new CopilotOutline.Node("Left", List.of(
                        new CopilotOutline.Node("Left child", List.of()))),
                new CopilotOutline.Node("Right again", List.of())));
    }

    @Test
    @DisplayName("Should produce a map that validates against mindmap.xsd")
    void shouldValidateAgainstSchema() {
        final String xml = CopilotMapXmlBuilder.toXml(sample());
        // Stored maps carry no namespace; the schema declares one, so it is added for validation only.
        final String namespaced = xml.replaceFirst("<map ", "<map xmlns=\"http://www.wisemapping.org/mindmap\" ");

        assertDoesNotThrow(() -> SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
                .newSchema(getClass().getResource("/mindmap.xsd"))
                .newValidator()
                .validate(new StreamSource(new StringReader(namespaced))));
        assertDoesNotThrow(() -> MindmapUtils.validateXmlContent(xml));
    }

    @Test
    @DisplayName("Should keep the tree, escape text and give unique ids, orders and sides")
    void shouldSerializeTree() throws Exception {
        final Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new ByteArrayInputStream(CopilotMapXmlBuilder.toXml(sample()).getBytes(StandardCharsets.UTF_8)));

        final Element map = doc.getDocumentElement();
        assertEquals("map", map.getTagName());
        assertEquals("mindmap", map.getAttribute("layout"));

        final NodeList topics = doc.getElementsByTagName("topic");
        assertEquals(7, topics.getLength());

        final Element central = (Element) topics.item(0);
        assertEquals("true", central.getAttribute("central"));
        assertEquals("Q&A <\"plan\">", central.getAttribute("text"));

        final Set<String> ids = new HashSet<>();
        for (int i = 0; i < topics.getLength(); i++) {
            assertTrue(ids.add(((Element) topics.item(i)).getAttribute("id")), "ids must be unique");
        }

        final Element right = (Element) topics.item(1);
        assertEquals("Right", right.getAttribute("text"));
        assertEquals("0", right.getAttribute("order"));
        assertTrue(right.getAttribute("position").startsWith("200,"));

        final Element rightChild2 = (Element) right.getElementsByTagName("topic").item(1);
        assertEquals("1", rightChild2.getAttribute("order"));
        assertTrue(rightChild2.getAttribute("position").startsWith("350,"), "descendants stay on the right");

        final Element left = (Element) topics.item(4);
        assertEquals("Left", left.getAttribute("text"));
        assertTrue(left.getAttribute("position").startsWith("-200,"));
        final Element leftChild = (Element) left.getElementsByTagName("topic").item(0);
        assertTrue(leftChild.getAttribute("position").startsWith("-350,"), "descendants stay on the left");
    }

    @Test
    @DisplayName("Should drop characters that XML 1.0 does not allow")
    void shouldDropInvalidXmlCharacters() {
        final String escaped = CopilotMapXmlBuilder.escape("a\u0000b\u0008c'd");
        assertEquals("abc&apos;d", escaped);
        assertFalse(escaped.contains("\u0000"));
    }
}
