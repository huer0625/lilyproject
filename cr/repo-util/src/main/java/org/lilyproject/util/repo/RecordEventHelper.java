/*
 * Copyright 2012 NGDATA nv
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
package org.lilyproject.util.repo;

import java.util.Map;
import java.util.Set;

import org.lilyproject.repository.api.FieldType;
import org.lilyproject.repository.api.QName;
import org.lilyproject.repository.api.RepositoryException;
import org.lilyproject.repository.api.SchemaId;
import org.lilyproject.repository.api.Scope;
import org.lilyproject.repository.api.TypeManager;

import static org.lilyproject.util.repo.RecordEvent.Type.CREATE;

/**
 * Wraps a {@link RecordEvent} to add some extra utility methods and some cached calculations.
 */
public class RecordEventHelper {
    private FieldFilter fieldFilter;
    private RecordEvent event;
    private TypeManager typeManager;

    private Map<Scope, Set<FieldType>> updatedFieldsByScope;
    private Map<Scope, Set<SchemaId>> updatedFieldsSchemaIdByScope;
    private Map<Scope, Set<QName>> updatedFieldsNameByScope;

    public RecordEventHelper(RecordEvent recordEvent, FieldFilter fieldFilter, TypeManager typeManager) {
        this.event = recordEvent;
        this.fieldFilter = fieldFilter != null ? fieldFilter : FieldFilter.PASS_ALL_FIELD_FILTER;
        this.typeManager = typeManager;
    }

    public RecordEvent getEvent() {
        return event;
    }

    public Map<Scope, Set<FieldType>> getUpdatedFieldsByScope() throws RepositoryException, InterruptedException {
        if (updatedFieldsByScope == null) {
            updatedFieldsByScope = FieldTypeUtil.getFieldTypeAndScope(event.getUpdatedFields(), fieldFilter, typeManager);
        }
        return updatedFieldsByScope;
    }

    public Map<Scope, Set<SchemaId>> getUpdatedFieldTypeIdsByScope() throws RepositoryException, InterruptedException {
        if (updatedFieldsSchemaIdByScope == null) {
            updatedFieldsSchemaIdByScope = FieldTypeUtil.getFieldTypeIdsAndScope(event.getUpdatedFields(), fieldFilter, typeManager);
        }
        return updatedFieldsSchemaIdByScope;
    }

    public Map<Scope, Set<QName>> getUpdatedFieldTypeNamesByScope() throws RepositoryException, InterruptedException {
        if (updatedFieldsNameByScope == null) {
            updatedFieldsNameByScope = FieldTypeUtil.getFieldTypeNamesAndScope(event.getUpdatedFields(), fieldFilter, typeManager);
        }
        return updatedFieldsNameByScope;
    }

    public Set<SchemaId> getModifiedVTags() throws RepositoryException, InterruptedException {
        Set<SchemaId> changedVTags = VersionTag.filterVTagFields(event.getUpdatedFields(), typeManager);

        // Last vtag
        if (event.getVersionCreated() != -1 || event.getType() == CREATE) {
            changedVTags.add(typeManager.getFieldTypeByName(VersionTag.LAST).getId());
        }

        return changedVTags;
    }
}
