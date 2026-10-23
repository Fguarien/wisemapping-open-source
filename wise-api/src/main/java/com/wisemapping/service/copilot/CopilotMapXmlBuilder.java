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
 * Serializes a {@link CopilotOutline} to the WiseMapping map format.
 *
 * <p>Every non-central topic carries an {@code order} and a {@code position}, as required by the
 * editor. Positions follow the same rule as the OPML importer of the frontend: first-level topics
 * alternate right (even order) and left (odd order) of the central topic, and descendants stay on
 * their parent's side. The editor lays the map out again when it is opened.</p>
 */
public final class CopilotMapXmlBuilder {

    private CopilotMapXmlBuilder() {
    }

    @NotNull
    public static String toXml(@NotNull CopilotOutline outline) {
        final StringBuilder xml = new StringBuilder();
        final int[] nextId = {1};
        xml.append("<map version=\"tango\" theme=\"prism\" layout=\"mindmap\">");
        xml.append("<topic central=\"true\" text=\"")
                .append(escape(outline.title()))
                .append("\" id=\"")
                .append(nextId[0]++)
                .append("\">");
        appendChildren(xml, outline.children(), 0, 0, true, nextId);
        xml.append("</topic></map>");
        return xml.toString();
    }

    private static void appendChildren(@NotNull StringBuilder xml, @NotNull List<CopilotOutline.Node> children,
                                       int parentX, int parentY, boolean parentIsCentral, int @NotNull [] nextId) {
        for (int order = 0; order < children.size(); order++) {
            final CopilotOutline.Node child = children.get(order);
            final int x;
            final int y;
            if (parentIsCentral) {
                final int side = order % 2 == 0 ? 1 : -1;
                x = side * 200;
                y = (order / 2) * 50;
            } else {
                final int side = parentX >= 0 ? 1 : -1;
                x = parentX + side * 150;
                y = parentY + order * 25;
            }

            xml.append("<topic position=\"")
                    .append(x).append(',').append(y)
                    .append("\" order=\"").append(order)
                    .append("\" text=\"").append(escape(child.text()))
                    .append("\" id=\"").append(nextId[0]++)
                    .append('"');
            if (child.children().isEmpty()) {
                xml.append("/>");
            } else {
                xml.append('>');
                appendChildren(xml, child.children(), x, y, false, nextId);
                xml.append("</topic>");
            }
        }
    }

    @NotNull
    static String escape(@NotNull String value) {
        final StringBuilder result = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            final char c = value.charAt(i);
            switch (c) {
                case '&' -> result.append("&amp;");
                case '<' -> result.append("&lt;");
                case '>' -> result.append("&gt;");
                case '"' -> result.append("&quot;");
                case '\'' -> result.append("&apos;");
                default -> {
                    // Characters that are not allowed in XML 1.0 are dropped.
                    if (c == 0x9 || c == 0xA || c == 0xD || (c >= 0x20 && c <= 0xD7FF)
                            || (c >= 0xE000 && c <= 0xFFFD) || Character.isSurrogate(c)) {
                        result.append(c);
                    }
                }
            }
        }
        return result.toString();
    }
}
