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

package com.wisemapping.rest;

import com.wisemapping.exceptions.AccessDeniedSecurityException;
import com.wisemapping.exceptions.WiseMappingException;
import com.wisemapping.model.Account;
import com.wisemapping.rest.model.RestCopilotMap;
import com.wisemapping.rest.model.RestCopilotRequest;
import com.wisemapping.security.Utils;
import com.wisemapping.service.copilot.CopilotResult;
import com.wisemapping.service.copilot.CopilotService;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import jakarta.servlet.http.HttpServletResponse;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Built-in AI copilot. Only answers when {@code app.ai.copilot} is configured; otherwise every call
 * returns 404 and the frontend keeps linking to the hosted copilot.
 */
@RestController
@RequestMapping("/api/restful/ai/copilot")
public class CopilotController {

    @Autowired
    @Qualifier("copilotService")
    private CopilotService copilotService;

    @PreAuthorize("isAuthenticated() and hasRole('ROLE_USER')")
    @RequestMapping(method = RequestMethod.POST, value = "/maps", consumes = {"application/json"},
            produces = {"application/json"})
    @ResponseStatus(value = HttpStatus.CREATED)
    @RateLimiter(name = "copilotLimiter")
    public RestCopilotMap createMap(@RequestBody RestCopilotRequest request, @NotNull HttpServletResponse response)
            throws WiseMappingException {
        final Account user = Utils.getUser(true);
        if (user.isSuspended()) {
            throw new AccessDeniedSecurityException(
                    "Suspended users cannot create maps. Please contact support for assistance.",
                    "SUSPENDED_USER_CANNOT_CREATE_MAPS");
        }

        final String prompt = request != null && request.getPrompt() != null ? request.getPrompt() : "";
        final String language = request != null ? request.getLanguage() : null;
        final String model = request != null ? request.getModel() : null;
        final CopilotResult result = copilotService.createMap(prompt, language, model, user);

        final int mapId = result.mindmap().getId();
        response.setHeader("Location", "/api/restful/maps/" + mapId);
        response.setHeader("ResourceId", Integer.toString(mapId));
        return new RestCopilotMap(result);
    }
}
