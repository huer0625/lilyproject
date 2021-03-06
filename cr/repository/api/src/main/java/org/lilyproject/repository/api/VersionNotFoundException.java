/*
 * Copyright 2010 Outerthought bvba
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.lilyproject.repository.api;

import java.util.HashMap;
import java.util.Map;

public class VersionNotFoundException extends RecordException {

    private String recordId;
    private Long version;

    public VersionNotFoundException(String message, Map<String, String> state) {
        this.recordId = state.get("recordId");
        String version = state.get("version");
        this.version = version != null ? Long.valueOf(version) : null;
    }

    @Override
    public Map<String, String> getState() {
        Map<String, String> state = new HashMap<String, String>();
        state.put("recordId", recordId);
        state.put("version", version != null ? version.toString() : null);
        return state;
    }

    public VersionNotFoundException(RecordId recordId, long version) {
        this.recordId = recordId != null ? recordId.toString() : null;
        this.version = version;
    }

    @Override
    public String getMessage() {
        return "Record '" + recordId + "', version '" + version + "' not found.";
    }
}

