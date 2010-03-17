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

import java.util.Arrays;

import org.apache.hadoop.hbase.util.Bytes;

public class EncodingUtil {
    
    public static final byte EXISTS_FLAG = (byte) 0;
    public static final byte DELETE_FLAG = (byte) 1;

    public static boolean isDeletedField(byte[] value) {
        return value[0] == DELETE_FLAG;
    }

    public static byte[] prefixValue(byte[] fieldValue, byte prefix) {
        byte[] prefixedValue;
        prefixedValue = new byte[fieldValue.length + 1];
        prefixedValue[0] = prefix;
        System.arraycopy(fieldValue, 0, prefixedValue, 1, fieldValue.length);
        return prefixedValue;
    }

    public static byte[] stripPrefix(byte[] prefixedValue) {
        return Arrays.copyOfRange(prefixedValue, 1, prefixedValue.length);
    }
    
    /**
     * Generates a new HBase rowkey based on the recordTypeId.
     */
    public static byte[] generateRecordTypeRowKey(String recordTypeId) {
        return Bytes.toBytes(recordTypeId);
    }
    
    /**
     * Generates a new HBase rowkey based on the recordTypeId and fieldDescriptorId.
     */
    public static byte[] generateFieldDescriptorRowKey(String recordTypeId, String fieldDescriptorId) {
        StringBuilder rowKey = new StringBuilder();
        rowKey.append(recordTypeId);
        rowKey.append('|');
        rowKey.append(fieldDescriptorId);
        return Bytes.toBytes(rowKey.toString());
    }
}
