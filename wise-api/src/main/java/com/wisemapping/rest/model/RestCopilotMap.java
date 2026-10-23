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

import com.wisemapping.service.copilot.CopilotResult;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/**
 * Answer of {@code POST /api/restful/ai/copilot/maps}: the map that was created, the model that was
 * asked and where the answer was observed to come from.
 */
public class RestCopilotMap {
    private int id;
    private String title;
    private RestCopilotModel model;
    /**
     * {@code model} field of the completion: the deployment that actually answered.
     */
    private String servedModel;
    /**
     * {@code local}, {@code external} or {@code unverified}.
     */
    private String actualLocality;
    /**
     * True when the answer did not come from where the model was declared to run, or when this
     * could not be checked for a local model.
     */
    private boolean routingWarning;

    public RestCopilotMap() {
    }

    public RestCopilotMap(@NotNull CopilotResult result) {
        this.id = result.mindmap().getId();
        this.title = result.mindmap().getTitle();
        this.model = new RestCopilotModel(result.model());
        this.servedModel = result.routing().servedModel();
        this.actualLocality = result.routing().actualLocality().name().toLowerCase(Locale.ROOT);
        this.routingWarning = result.routing().warning();
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public RestCopilotModel getModel() {
        return model;
    }

    public void setModel(RestCopilotModel model) {
        this.model = model;
    }

    public String getServedModel() {
        return servedModel;
    }

    public void setServedModel(String servedModel) {
        this.servedModel = servedModel;
    }

    public String getActualLocality() {
        return actualLocality;
    }

    public void setActualLocality(String actualLocality) {
        this.actualLocality = actualLocality;
    }

    public boolean isRoutingWarning() {
        return routingWarning;
    }

    public void setRoutingWarning(boolean routingWarning) {
        this.routingWarning = routingWarning;
    }
}
