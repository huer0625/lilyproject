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
package org.lilycms.repository.api;

import java.util.Collection;
import java.util.Map;



/**
 * The RecordType describes the schema to be followed by a {@link Record}
 * 
 * <p>
 * Multiple versions of a RecordType can exist
 * 
 * <p>
 * A collection of {@link FieldType}s describe which fields can or must be
 * part of a {@link Record} of this {@link RecordType}. A
 * {@link FieldType} is always part of a {@link RecordType} and cannot
 * exist on its own.
 */
public interface RecordType {
    String getId();

    void setVersion(Long version);
    
    Long getVersion();
    
    void addFieldTypeEntry(FieldTypeEntry fieldTypeEntry);

    FieldTypeEntry getFieldTypeEntry(String fieldTypeId);
    
    void removeFieldTypeEntry(String fieldTypeId);
    
    Collection<FieldTypeEntry> getFieldTypeEntries();
    
    void addMixin(String recordTypeId, Long recordTypeVersion);
    
    void removeMixin(String recordTypeId);
    
    Map<String, Long> getMixins();
    
    RecordType clone();
    
    boolean equals(Object obj);
}
