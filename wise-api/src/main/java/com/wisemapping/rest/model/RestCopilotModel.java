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

package com.wisemapping.rest.model;

import com.wisemapping.config.CopilotProperties;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/**
 * A copilot model as shown to users: no endpoint, no key, no served-match expression.
 */
public class RestCopilotModel {
    private String id;
    private String label;
    /**
     * {@code local} or {@code external}.
     */
    private String locality;
    private String provider;

    public RestCopilotModel() {
    }

    public RestCopilotModel(@NotNull CopilotProperties.Model model) {
        this.id = model.getId();
        this.label = model.getLabel();
        this.locality = model.getLocality().name().toLowerCase(Locale.ROOT);
        this.provider = model.getProvider();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getLocality() {
        return locality;
    }

    public void setLocality(String locality) {
        this.locality = locality;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }
}
