package org.lilycms.repoutil;

import static org.lilycms.repoutil.EventType.*;

import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ArrayNode;
import org.codehaus.jackson.node.JsonNodeFactory;
import org.codehaus.jackson.node.ObjectNode;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/**
 * Represents the payload of an event about a create-update-delete operation on the repository.
 *
 * <p>The actual payload is json, this class helps in parsing or constructing that json.
 */
public class RecordEvent {
    private long versionCreated = -1;
    private long versionUpdated = -1;
    private Type type;
    private Set<String> updatedFields = new HashSet<String>();

    public enum Type {CREATE, UPDATE, DELETE}

    public RecordEvent() {
    }

    /**
     * Creates a record event from the json data supplied as bytes.
     */
    public RecordEvent(String messageType, byte[] data) throws IOException {
        if (messageType.equals(EVENT_RECORD_CREATED)) {
            type = Type.CREATE;
        } else if (messageType.equals(EVENT_RECORD_DELETED)) {
            type = Type.DELETE;
        } else if (messageType.equals(EVENT_RECORD_UPDATED)) {
            type = Type.UPDATE;
        } else {
            throw new RuntimeException("Unexpected kind of message type: " + messageType);
        }

        JsonNode msgData = new ObjectMapper().readValue(data, 0, data.length, JsonNode.class);

        if (msgData.get("versionCreated") != null) {
            versionCreated = msgData.get("versionCreated").getIntValue();
        }

        if (msgData.get("versionUpdated") != null) {
            versionUpdated = msgData.get("versionUpdated").getIntValue();
        }

        JsonNode updatedFieldsNode = msgData.get("updatedFields");
        if (updatedFieldsNode != null && updatedFieldsNode.size() > 0) {
            for (int i = 0; i < updatedFieldsNode.size(); i++) {
                updatedFields.add(updatedFieldsNode.get(i).getTextValue());
            }
        }
    }

    public long getVersionCreated() {
        return versionCreated;
    }

    public void setVersionCreated(long versionCreated) {
        this.versionCreated = versionCreated;
    }

    public long getVersionUpdated() {
        return versionUpdated;
    }

    public void setVersionUpdated(long versionUpdated) {
        this.versionUpdated = versionUpdated;
    }

    public Type getType() {
        return type;
    }

    /**
     * The fields which were updated (= added, deleted or changed), identified by their FieldType ID.
     */
    public Set<String> getUpdatedFields() {
        return updatedFields;
    }

    public void addUpdatedField(String fieldTypeId) {
        updatedFields.add(fieldTypeId);
    }

    public ObjectNode toJson() {
        JsonNodeFactory factory = JsonNodeFactory.instance;
        ObjectNode object = factory.objectNode();

        ArrayNode updatedFieldsNode = object.putArray("updatedFields");
        for (String updatedField : updatedFields) {
            updatedFieldsNode.add(updatedField);
        }

        if (versionUpdated != -1) {
            object.put("versionUpdated", versionUpdated);
        }

        if (versionCreated != -1) {
            object.put("versionCreated", versionCreated);
        }

        return object;
    }

    public byte[] toJsonBytes() {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        ObjectMapper mapper = new ObjectMapper();
        try {
            mapper.writeValue(os, toJson());
        } catch (IOException e) {
            // Small chance of this happening, since we are writing to a byte array
            throw new RuntimeException(e);
        }
        return os.toByteArray();
    }
}


