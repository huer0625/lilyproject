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
package org.lilycms.repository.impl;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.NavigableMap;
import java.util.UUID;
import java.util.Map.Entry;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HColumnDescriptor;
import org.apache.hadoop.hbase.HConstants;
import org.apache.hadoop.hbase.HTableDescriptor;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.HBaseAdmin;
import org.apache.hadoop.hbase.client.HTable;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.util.Bytes;
import org.lilycms.repository.api.FieldType;
import org.lilycms.repository.api.FieldTypeEntry;
import org.lilycms.repository.api.FieldTypeExistsException;
import org.lilycms.repository.api.FieldTypeNotFoundException;
import org.lilycms.repository.api.FieldTypeUpdateException;
import org.lilycms.repository.api.IdGenerator;
import org.lilycms.repository.api.PrimitiveValueType;
import org.lilycms.repository.api.QName;
import org.lilycms.repository.api.RecordType;
import org.lilycms.repository.api.RecordTypeExistsException;
import org.lilycms.repository.api.RecordTypeNotFoundException;
import org.lilycms.repository.api.RepositoryException;
import org.lilycms.repository.api.Scope;
import org.lilycms.repository.api.TypeManager;
import org.lilycms.repository.api.ValueType;
import org.lilycms.util.ArgumentValidator;

public class HBaseTypeManager implements TypeManager {

    private static final String TYPE_TABLE = "typeTable";
    private static final byte[] NON_VERSIONED_COLUMN_FAMILY = Bytes.toBytes("NVCF");
    private static final byte[] FIELDTYPEENTRY_COLUMN_FAMILY = Bytes.toBytes("FTECF");
    private static final byte[] MIXIN_COLUMN_FAMILY = Bytes.toBytes("MCF");

    private static final byte[] CURRENT_VERSION_COLUMN_NAME = Bytes.toBytes("$currentVersion");

    private static final byte[] FIELDTYPE_NAME_COLUMN_NAME = Bytes.toBytes("$name");
    private static final byte[] FIELDTYPE_VALUETYPE_COLUMN_NAME = Bytes.toBytes("$valueType");
    private static final byte[] FIELDTPYE_SCOPE_COLUMN_NAME = Bytes.toBytes("$scope");

    private HTable typeTable;
    private IdGenerator idGenerator;
    private Map<QName, FieldType> fieldTypeNameCache = new HashMap<QName, FieldType>();

    public HBaseTypeManager(IdGenerator idGenerator, Configuration configuration) throws IOException {
        this.idGenerator = idGenerator;
        try {
            typeTable = new HTable(configuration, TYPE_TABLE);
        } catch (IOException e) {
            HBaseAdmin admin = new HBaseAdmin(configuration);
            HTableDescriptor tableDescriptor = new HTableDescriptor(TYPE_TABLE);
            tableDescriptor.addFamily(new HColumnDescriptor(NON_VERSIONED_COLUMN_FAMILY));
            tableDescriptor.addFamily(new HColumnDescriptor(FIELDTYPEENTRY_COLUMN_FAMILY, HConstants.ALL_VERSIONS,
                            "none", false, true, HConstants.FOREVER, false));
            tableDescriptor.addFamily(new HColumnDescriptor(MIXIN_COLUMN_FAMILY, HConstants.ALL_VERSIONS, "none",
                            false, true, HConstants.FOREVER, false));
            admin.createTable(tableDescriptor);
            typeTable = new HTable(configuration, TYPE_TABLE);
        }
        registerDefaultValueTypes();
    }

    public RecordType newRecordType(String recordTypeId) {
        ArgumentValidator.notNull(recordTypeId, "recordTypeId");
        return new RecordTypeImpl(recordTypeId);
    }

    public RecordType createRecordType(RecordType recordType) throws RecordTypeExistsException,
                    RecordTypeNotFoundException, FieldTypeNotFoundException, RepositoryException {
        ArgumentValidator.notNull(recordType, "recordType");
        RecordType newRecordType = recordType.clone();
        Long recordTypeVersion = Long.valueOf(1);
        byte[] rowId = Bytes.toBytes(recordType.getId());
        try {
            if (typeTable.exists(new Get(rowId))) {
                throw new RecordTypeExistsException(recordType);
            }

            Put put = new Put(rowId);
            put.add(NON_VERSIONED_COLUMN_FAMILY, CURRENT_VERSION_COLUMN_NAME, Bytes.toBytes(recordTypeVersion));

            Collection<FieldTypeEntry> fieldTypeEntries = recordType.getFieldTypeEntries();
            for (FieldTypeEntry fieldTypeEntry : fieldTypeEntries) {
                newRecordType.addFieldTypeEntry(putFieldTypeEntry(recordTypeVersion, put, fieldTypeEntry));
            }

            Map<String, Long> mixins = recordType.getMixins();
            for (Entry<String, Long> mixin : mixins.entrySet()) {
                newRecordType.addMixin(mixin.getKey(), putMixinOnRecordType(recordTypeVersion, put, mixin.getKey(),
                                mixin.getValue()));
            }

            typeTable.put(put);
        } catch (IOException e) {
            throw new RepositoryException("Exception occured while creating recordType <" + recordType.getId()
                            + "> on HBase", e);
        }
        newRecordType.setVersion(recordTypeVersion);
        return newRecordType;
    }

    public RecordType updateRecordType(RecordType recordType) throws RecordTypeNotFoundException,
                    FieldTypeNotFoundException, RepositoryException {
        ArgumentValidator.notNull(recordType, "recordType");
        RecordType newRecordType = recordType.clone();
        String id = recordType.getId();
        Put put = new Put(Bytes.toBytes(id));

        RecordType latestRecordType = getRecordType(id, null);
        Long latestRecordTypeVersion = latestRecordType.getVersion();
        Long newRecordTypeVersion = latestRecordTypeVersion + 1;

        boolean fieldTypeEntriesChanged = updateFieldTypeEntries(put, newRecordTypeVersion, recordType,
                        latestRecordType);

        boolean mixinsChanged = updateMixins(put, newRecordTypeVersion, recordType, latestRecordType);

        if (fieldTypeEntriesChanged || mixinsChanged) {
            put.add(NON_VERSIONED_COLUMN_FAMILY, CURRENT_VERSION_COLUMN_NAME, Bytes.toBytes(newRecordTypeVersion));
            try {
                typeTable.put(put);
            } catch (IOException e) {
                throw new RepositoryException("Exception occured while updating recordType <" + recordType.getId()
                                + "> on HBase", e);
            }
            newRecordType.setVersion(newRecordTypeVersion);
        } else {
            newRecordType.setVersion(latestRecordTypeVersion);
        }
        return newRecordType;
    }

    private Long putMixinOnRecordType(Long recordTypeVersion, Put put, String mixinId, Long mixinVersion)
                    throws RecordTypeNotFoundException, RepositoryException {
        Long newMixinVersion = getRecordType(mixinId, mixinVersion).getVersion();
        put.add(MIXIN_COLUMN_FAMILY, Bytes.toBytes(mixinId), recordTypeVersion, Bytes.toBytes(newMixinVersion));
        return newMixinVersion;
    }

    private boolean updateFieldTypeEntries(Put put, Long newRecordTypeVersion, RecordType recordType,
                    RecordType latestRecordType) throws FieldTypeNotFoundException, RepositoryException {
        boolean changed = false;
        Collection<FieldTypeEntry> latestFieldTypeEntries = latestRecordType.getFieldTypeEntries();
        // Update FieldTypeEntries
        for (FieldTypeEntry fieldTypeEntry : recordType.getFieldTypeEntries()) {
            FieldTypeEntry latestFieldTypeEntry = latestRecordType.getFieldTypeEntry(fieldTypeEntry.getFieldTypeId());
            if (!fieldTypeEntry.equals(latestFieldTypeEntry)) {
                putFieldTypeEntry(newRecordTypeVersion, put, fieldTypeEntry);
                changed = true;
            }
            latestFieldTypeEntries.remove(latestFieldTypeEntry);
        }
        // Remove remaining FieldTypeEntries
        for (FieldTypeEntry fieldTypeEntry : latestFieldTypeEntries) {
            put.add(FIELDTYPEENTRY_COLUMN_FAMILY, fieldTypeIdToBytes(fieldTypeEntry.getFieldTypeId()), newRecordTypeVersion,
                            new byte[] { EncodingUtil.DELETE_FLAG });
            changed = true;
        }
        return changed;
    }

    private FieldTypeEntry putFieldTypeEntry(Long version, Put put, FieldTypeEntry fieldTypeEntry)
                    throws FieldTypeNotFoundException, RepositoryException {
        FieldTypeEntry newFieldTypeEntry = fieldTypeEntry.clone();
        byte[] idBytes = fieldTypeIdToBytes(fieldTypeEntry.getFieldTypeId());
        Get get = new Get(idBytes);
        try {
            if (!typeTable.exists(get)) {
                throw new FieldTypeNotFoundException(fieldTypeEntry.getFieldTypeId(), null);
            }
        } catch (IOException e) {
            throw new RepositoryException("Exception occured while checking existance of FieldTypeEntry <"
                            + fieldTypeEntry.getFieldTypeId() + "> on HBase", e);
        }
        put.add(FIELDTYPEENTRY_COLUMN_FAMILY, idBytes, version, encodeFieldTypeEntry(newFieldTypeEntry));
        return newFieldTypeEntry;
    }

    private boolean updateMixins(Put put, Long newRecordTypeVersion, RecordType recordType, RecordType latestRecordType) {
        boolean changed = false;
        Map<String, Long> latestMixins = latestRecordType.getMixins();
        // Update mixins
        for (Entry<String, Long> entry : recordType.getMixins().entrySet()) {
            String mixinId = entry.getKey();
            Long mixinVersion = entry.getValue();
            if (!mixinVersion.equals(latestMixins.get(mixinId))) {
                put.add(MIXIN_COLUMN_FAMILY, Bytes.toBytes(mixinId), newRecordTypeVersion, Bytes.toBytes(mixinVersion));
                changed = true;
            }
            latestMixins.remove(mixinId);
        }
        // Remove remaining mixins
        for (Entry<String, Long> entry : latestMixins.entrySet()) {
            put.add(MIXIN_COLUMN_FAMILY, Bytes.toBytes(entry.getKey()), newRecordTypeVersion,
                            new byte[] { EncodingUtil.DELETE_FLAG });
            changed = true;
        }
        return changed;
    }

    public RecordType getRecordType(String recordTypeId, Long version) throws RecordTypeNotFoundException,
                    RepositoryException {
        ArgumentValidator.notNull(recordTypeId, "recordTypeId");
        Get get = new Get(Bytes.toBytes(recordTypeId));
        if (version != null) {
            get.setMaxVersions();
        }
        Result result;
        try {
            if (!typeTable.exists(get)) {
                throw new RecordTypeNotFoundException(recordTypeId, null);
            }
            result = typeTable.get(get);
        } catch (IOException e) {
            throw new RepositoryException("Exception occured while retrieving recordType <" + recordTypeId
                            + "> from HBase table", e);
        }
        RecordType recordType = newRecordType(recordTypeId);
        Long currentVersion = Bytes.toLong(result.getValue(NON_VERSIONED_COLUMN_FAMILY, CURRENT_VERSION_COLUMN_NAME));
        if (version != null) {
            if (currentVersion < version) {
                throw new RecordTypeNotFoundException(recordTypeId, version);
            }
            recordType.setVersion(version);
        } else {
            recordType.setVersion(currentVersion);
        }
        extractFieldTypeEntries(result, version, recordType);
        extractMixins(result, version, recordType);
        return recordType;
    }

    private void extractFieldTypeEntries(Result result, Long version, RecordType recordType) {
        if (version != null) {
            NavigableMap<byte[], NavigableMap<byte[], NavigableMap<Long, byte[]>>> allVersionsMap = result.getMap();
            NavigableMap<byte[], NavigableMap<Long, byte[]>> fieldTypeEntriesVersionsMap = allVersionsMap
                            .get(FIELDTYPEENTRY_COLUMN_FAMILY);
            if (fieldTypeEntriesVersionsMap != null) {
                for (Entry<byte[], NavigableMap<Long, byte[]>> entry : fieldTypeEntriesVersionsMap.entrySet()) {
                    String fieldTypeId = fieldTypeIdFromBytes(entry.getKey());
                    Entry<Long, byte[]> ceilingEntry = entry.getValue().ceilingEntry(version);
                    if (ceilingEntry != null) {
                        FieldTypeEntry fieldTypeEntry = decodeFieldTypeEntry(ceilingEntry.getValue(), fieldTypeId);
                        if (fieldTypeEntry != null) {
                            recordType.addFieldTypeEntry(fieldTypeEntry);
                        }
                    }
                }
            }
        } else {
            NavigableMap<byte[], byte[]> versionableMap = result.getFamilyMap(FIELDTYPEENTRY_COLUMN_FAMILY);
            if (versionableMap != null) {
                for (Entry<byte[], byte[]> entry : versionableMap.entrySet()) {
                    String fieldTypeId = fieldTypeIdFromBytes(entry.getKey());
                    FieldTypeEntry fieldTypeEntry = decodeFieldTypeEntry(entry.getValue(), fieldTypeId);
                    if (fieldTypeEntry != null) {
                        recordType.addFieldTypeEntry(fieldTypeEntry);
                    }
                }
            }
        }
    }

    private void extractMixins(Result result, Long version, RecordType recordType) {
        if (version != null) {
            NavigableMap<byte[], NavigableMap<byte[], NavigableMap<Long, byte[]>>> allVersionsMap = result.getMap();
            NavigableMap<byte[], NavigableMap<Long, byte[]>> mixinVersionsMap = allVersionsMap.get(MIXIN_COLUMN_FAMILY);
            if (mixinVersionsMap != null) {
                for (Entry<byte[], NavigableMap<Long, byte[]>> entry : mixinVersionsMap.entrySet()) {
                    String mixinId = Bytes.toString(entry.getKey());
                    Entry<Long, byte[]> ceilingEntry = entry.getValue().ceilingEntry(version);
                    if (ceilingEntry != null) {
                        if (!EncodingUtil.isDeletedField(ceilingEntry.getValue())) {
                            recordType.addMixin(mixinId, Bytes.toLong(ceilingEntry.getValue()));
                        }
                    }
                }
            }
        } else {
            NavigableMap<byte[], byte[]> mixinMap = result.getFamilyMap(MIXIN_COLUMN_FAMILY);
            if (mixinMap != null) {
                for (Entry<byte[], byte[]> entry : mixinMap.entrySet()) {
                    if (!EncodingUtil.isDeletedField(entry.getValue())) {
                        recordType.addMixin(Bytes.toString(entry.getKey()), Bytes.toLong(entry.getValue()));
                    }
                }
            }
        }
    }

    public FieldTypeEntry newFieldTypeEntry(String fieldTypeId, boolean mandatory) {
        ArgumentValidator.notNull(fieldTypeId, "fieldTypeId");
        ArgumentValidator.notNull(mandatory, "mandatory");
        return new FieldTypeEntryImpl(fieldTypeId, mandatory);
    }

    // TODO move to some encoder/decoder
    /**
     * Encoding the fields: FD-version, mandatory, alias
     */
    private byte[] encodeFieldTypeEntry(FieldTypeEntry fieldTypeEntry) {
        // TODO check if we can use nio instead
        byte[] bytes = new byte[0];
        bytes = Bytes.add(bytes, Bytes.toBytes(fieldTypeEntry.isMandatory()));
        return EncodingUtil.prefixValue(bytes, EncodingUtil.EXISTS_FLAG);
    }

    private FieldTypeEntry decodeFieldTypeEntry(byte[] bytes, String fieldTypeId) {
        if (EncodingUtil.isDeletedField(bytes)) {
            return null;
        }
        byte[] encodedBytes = EncodingUtil.stripPrefix(bytes);
        boolean mandatory = Bytes.toBoolean(encodedBytes);
        return new FieldTypeEntryImpl(fieldTypeId, mandatory);
    }

    public FieldType newFieldType(ValueType valueType, QName name, Scope scope) {
        return newFieldType(null, valueType, name, scope);
    }

    public FieldType newFieldType(String id, ValueType valueType, QName name, Scope scope) {
        ArgumentValidator.notNull(valueType, "valueType");
        ArgumentValidator.notNull(name, "name");
        ArgumentValidator.notNull(scope, "scope");
        return new FieldTypeImpl(id, valueType, name, scope);
    }

    public FieldType createFieldType(FieldType fieldType) throws FieldTypeExistsException, RepositoryException {
        ArgumentValidator.notNull(fieldType, "fieldType");
        
        if (getFieldTypeByName(fieldType.getName()) != null) {
            throw new FieldTypeExistsException(fieldType);
        }
        
        FieldType newFieldType;
        // TODO use IdGenerator
        UUID uuid = UUID.randomUUID();
        byte[] rowId;
        rowId = fieldTypeIdToBytes(uuid);
        Long version = Long.valueOf(1);
        try {
            Put put = new Put(rowId);
            put.add(NON_VERSIONED_COLUMN_FAMILY, FIELDTYPE_VALUETYPE_COLUMN_NAME, fieldType.getValueType().toBytes());
            put.add(NON_VERSIONED_COLUMN_FAMILY, FIELDTPYE_SCOPE_COLUMN_NAME, Bytes.toBytes(fieldType.getScope()
                            .name()));
            put.add(NON_VERSIONED_COLUMN_FAMILY, FIELDTYPE_NAME_COLUMN_NAME, version, encodeName(fieldType.getName()));
            typeTable.put(put);
        } catch (IOException e) {
            throw new RepositoryException("Exception occured while creating fieldType <" + fieldType.getId()
                            + "> version: <" + version + "> on HBase", e);
        }
        newFieldType = fieldType.clone();
        newFieldType.setId(uuid.toString());
        updateFieldTypeNameCache(newFieldType, null);
        return newFieldType;
    }

    private byte[] fieldTypeIdToBytes(UUID id) {
        byte[] rowId;
        rowId = new byte[16];
        Bytes.putLong(rowId, 0, id.getMostSignificantBits());
        Bytes.putLong(rowId, 8, id.getLeastSignificantBits());
        return rowId;
    }

    private byte[] fieldTypeIdToBytes(String id) {
        UUID uuid = UUID.fromString(id);
        byte[] rowId;
        rowId = new byte[16];
        Bytes.putLong(rowId, 0, uuid.getMostSignificantBits());
        Bytes.putLong(rowId, 8, uuid.getLeastSignificantBits());
        return rowId;
    }
    
    private String fieldTypeIdFromBytes(byte[] bytes) {
        UUID uuid = new UUID(Bytes.toLong(bytes, 0, 8), Bytes.toLong(bytes, 8, 8));
        return uuid.toString();
    }

    public FieldType updateFieldType(FieldType fieldType) throws FieldTypeNotFoundException, FieldTypeUpdateException,
                    RepositoryException {
        FieldType latestFieldType = getFieldTypeById(fieldType.getId());
        if (!fieldType.getValueType().equals(latestFieldType.getValueType())) {
            throw new FieldTypeUpdateException("Changing the valueType of a fieldType <" + fieldType.getId()
                            + "> is not allowed; old<" + latestFieldType.getValueType() + "> new<"
                            + fieldType.getValueType() + ">");
        }
        if (!fieldType.getScope().equals(latestFieldType.getScope())) {
            throw new FieldTypeUpdateException("Changing the scope of a fieldType <" + fieldType.getId()
                            + "> is not allowed; old<" + latestFieldType.getScope() + "> new<" + fieldType.getScope()
                            + ">");
        }
        if (!fieldType.getName().equals(latestFieldType.getName())) {
            Put put = new Put(fieldTypeIdToBytes(fieldType.getId()));
            put.add(NON_VERSIONED_COLUMN_FAMILY, FIELDTYPE_NAME_COLUMN_NAME, encodeName(fieldType.getName()));
            try {
                typeTable.put(put);
            } catch (IOException e) {
                throw new RepositoryException("Exception occured while updating fieldType <" + fieldType.getId()
                                + "> on HBase", e);
            }
        }
        updateFieldTypeNameCache(fieldType, latestFieldType.getName());
        return fieldType.clone();
    }

    public FieldType getFieldTypeById(String id) throws FieldTypeNotFoundException, RepositoryException {
        ArgumentValidator.notNull(id, "id");
        Result result;
        Get get = new Get(fieldTypeIdToBytes(id));
        try {
            if (!typeTable.exists(get)) {
                throw new FieldTypeNotFoundException(id, null);
            }
            result = typeTable.get(get);
        } catch (IOException e) {
            throw new RepositoryException("Exception occured while retrieving fieldType <" + id + "> from HBase", e);
        }
        NavigableMap<byte[], byte[]> nonVersionableColumnFamily = result.getFamilyMap(NON_VERSIONED_COLUMN_FAMILY);
        QName name;
        name = decodeName(nonVersionableColumnFamily.get(FIELDTYPE_NAME_COLUMN_NAME));
        ValueType valueType = ValueTypeImpl.fromBytes(nonVersionableColumnFamily.get(FIELDTYPE_VALUETYPE_COLUMN_NAME),
                        this);
        Scope scope = Scope.valueOf(Bytes.toString(nonVersionableColumnFamily.get(FIELDTPYE_SCOPE_COLUMN_NAME)));
        return new FieldTypeImpl(id, valueType, name, scope);
    }
    
    // TODO centralize encoding
    private byte[] encodeName(QName qname) {
        byte[] encodedName = new byte[0];
        String name = qname.getName();
        String namespace = qname.getNamespace();
        
        if (namespace == null) {
            encodedName = Bytes.add(encodedName, Bytes.toBytes(0));
        } else {
            encodedName = Bytes.add(encodedName, Bytes.toBytes(namespace.length()));
            encodedName = Bytes.add(encodedName, Bytes.toBytes(namespace));
        }
        encodedName = Bytes.add(encodedName, Bytes.toBytes(name.length()));
        encodedName = Bytes.add(encodedName, Bytes.toBytes(name));
        return encodedName;
    }
    
    private QName decodeName(byte[] bytes) {
        int offset = 0;
        String namespace = null;
        int namespaceLength = Bytes.toInt(bytes);
        offset = offset + Bytes.SIZEOF_INT;
        if (namespaceLength > 0) {
            namespace = Bytes.toString(bytes,offset,namespaceLength);
        }
        offset = offset + namespaceLength;
        int nameLength = Bytes.toInt(bytes, offset, Bytes.SIZEOF_INT);
        offset = offset + Bytes.SIZEOF_INT;
        String name = Bytes.toString(bytes, offset, nameLength);
        return new QName(namespace, name);
    }

    // FieldType name cache
    private void updateFieldTypeNameCache(FieldType fieldType, QName oldName) {
        fieldTypeNameCache .remove(oldName);
        fieldTypeNameCache.put(fieldType.getName(), fieldType);
    }
    
    private FieldType getFieldTypeFromCache(QName name) {
        FieldType fieldType = fieldTypeNameCache.get(name);
        if (fieldType == null) {
            // TODO retrieve from table
        }
        return fieldType;
    }
    
    // Value Types

    // TODO move to a primitiveValueType registry
    private Map<String, PrimitiveValueType> primitiveValueTypes = new HashMap<String, PrimitiveValueType>();

    // TODO get this from some configuration file
    private void registerDefaultValueTypes() {
        registerPrimitiveValueType(new StringValueType());
        registerPrimitiveValueType(new IntegerValueType());
        registerPrimitiveValueType(new LongValueType());
        registerPrimitiveValueType(new BooleanValueType());
        registerPrimitiveValueType(new DateValueType());
        registerPrimitiveValueType(new LinkValueType(idGenerator));
    }

    public void registerPrimitiveValueType(PrimitiveValueType primitiveValueType) {
        primitiveValueTypes.put(primitiveValueType.getName(), primitiveValueType);
    }

    public ValueType getValueType(String primitiveValueTypeName, boolean multivalue, boolean hierarchy) {
        return new ValueTypeImpl(primitiveValueTypes.get(primitiveValueTypeName), multivalue, hierarchy);
    }

    // TODO cache these things on the RecordType itself?
    // TODO Should not rely on the recordType ?
    public FieldType getFieldTypeByName(QName name) {
        ArgumentValidator.notNull(name, "name");
        return getFieldTypeFromCache(name);
    }

}
