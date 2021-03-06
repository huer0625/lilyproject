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
package org.lilyproject.tools.import_.cli;

public interface ImportListener {
    /**
     * An exception occurring during import. This method should itself not throw exceptions, it should log
     * it or collect it for later reporting.
     */
    void exception(Throwable throwable);

    void recordImportException(Throwable throwable, String json, int lineNumber);

    void tooManyRecordImportErrors(long count);

    void conflict(EntityType entityType, String entityName, String propName, Object oldValue, Object newValue)
            throws ImportConflictException;

    void existsAndEqual(EntityType entityType, String entityName, String entityId);

    void updated(EntityType entityType, String entityName, String entityId, Long version);

    void created(EntityType entityType, String entityName, String entityId);

    void allowedFailure(EntityType entityType, String entityName, String entityId, String reason);
}
