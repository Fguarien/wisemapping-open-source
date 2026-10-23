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
import com.wisemapping.config.CopilotProperties.Locality;
import com.wisemapping.exceptions.CopilotRoutingMismatchException;
import com.wisemapping.service.copilot.CopilotRoutingVerifier.ActualLocality;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CopilotRoutingVerifierTest {

    private static CopilotProperties.Model local(String servedMatch) {
        return new CopilotProperties.Model("smart-model-local", "Qwen", Locality.LOCAL, "", servedMatch);
    }

    private static CopilotProperties.Model external(String servedMatch) {
        return new CopilotProperties.Model("infomaniak-mistral-small-4", "Mistral", Locality.EXTERNAL,
                "Infomaniak (Switzerland)", servedMatch);
    }

    @Test
    @DisplayName("Local model served by the expected deployment: confirmed local")
    void shouldConfirmLocal() throws Exception {
        final CopilotRoutingVerifier.Result result =
                CopilotRoutingVerifier.verify(local("(hosted_vllm/)?qwen3\\.5:9b"), "hosted_vllm/qwen3.5:9b");

        assertEquals(ActualLocality.LOCAL, result.actualLocality());
        assertEquals("hosted_vllm/qwen3.5:9b", result.servedModel());
        assertFalse(result.warning());
    }

    @Test
    @DisplayName("Local model served by a cloud model: rejected")
    void shouldRejectLocalServedExternally() {
        assertThrows(CopilotRoutingMismatchException.class,
                () -> CopilotRoutingVerifier.verify(local("(hosted_vllm/)?qwen3\\.5:9b"), "mistral-small-2506"));
    }

    @Test
    @DisplayName("Served-match must cover the whole value, not a fragment")
    void shouldRequireFullMatch() {
        assertThrows(CopilotRoutingMismatchException.class,
                () -> CopilotRoutingVerifier.verify(local("qwen3\\.5:9b"), "openrouter/qwen3.5:9b-cloud"));
    }

    @Test
    @DisplayName("Local model without a model field in the answer: rejected, it cannot be proven local")
    void shouldRejectLocalWithoutServedModel() {
        assertThrows(CopilotRoutingMismatchException.class,
                () -> CopilotRoutingVerifier.verify(local("qwen3\\.5:9b"), null));
    }

    @Test
    @DisplayName("External model served by the expected provider: confirmed external")
    void shouldConfirmExternal() throws Exception {
        final CopilotRoutingVerifier.Result result =
                CopilotRoutingVerifier.verify(external("mistral-small.*"), "mistral-small-2506");

        assertEquals(ActualLocality.EXTERNAL, result.actualLocality());
        assertFalse(result.warning());
    }

    @Test
    @DisplayName("External model served by something else (the measured LiteLLM fallback): kept, flagged unverified")
    void shouldFlagExternalServedElsewhere() throws Exception {
        final CopilotRoutingVerifier.Result result =
                CopilotRoutingVerifier.verify(external("mistral-small.*"), "ollama_chat/qwen3.5:9b");

        assertEquals(ActualLocality.UNVERIFIED, result.actualLocality());
        assertEquals("ollama_chat/qwen3.5:9b", result.servedModel());
        assertTrue(result.warning());
    }

    @Test
    @DisplayName("No served-match: unverified, with a warning only for a model declared local")
    void shouldReportUnverifiedWithoutExpression() throws Exception {
        final CopilotRoutingVerifier.Result localResult = CopilotRoutingVerifier.verify(local(""), "anything");
        assertEquals(ActualLocality.UNVERIFIED, localResult.actualLocality());
        assertTrue(localResult.warning());

        final CopilotRoutingVerifier.Result externalResult = CopilotRoutingVerifier.verify(external(""), "anything");
        assertEquals(ActualLocality.UNVERIFIED, externalResult.actualLocality());
        assertFalse(externalResult.warning());
    }

    @Test
    @DisplayName("An invalid expression is treated as absent, never as a match")
    void shouldIgnoreInvalidExpression() throws Exception {
        final CopilotRoutingVerifier.Result result = CopilotRoutingVerifier.verify(local("(unclosed"), "(unclosed");
        assertEquals(ActualLocality.UNVERIFIED, result.actualLocality());
        assertTrue(result.warning());
    }
}
