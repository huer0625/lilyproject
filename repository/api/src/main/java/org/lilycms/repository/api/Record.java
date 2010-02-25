package org.lilycms.repository.api;

import java.util.Set;

public interface Record {
    void setRecordType(String recordTypeName, long recordTypeVersion);
    String getRecordTypeName();
    long getRecordTypeVersion();
    void addField(Field field);
    Field getField(String fieldName) throws FieldNotFoundException;
    Set<Field> getFields();
    void setRecordId(String recordId);
    String getRecordId();
    void deleteField(String fieldName);
    Set<String> getDeleteFields();
}
