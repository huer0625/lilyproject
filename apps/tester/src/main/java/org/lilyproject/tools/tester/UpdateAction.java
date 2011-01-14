package org.lilyproject.tools.tester;

import java.util.ArrayList;
import java.util.List;

import org.codehaus.jackson.JsonNode;
import org.lilyproject.repository.api.QName;
import org.lilyproject.repository.api.Record;
import org.lilyproject.repository.api.RecordException;
import org.lilyproject.repository.api.RecordId;
import org.lilyproject.util.json.JsonUtil;

public class UpdateAction extends AbstractTestAction implements TestAction {
    private String pattern;
    private String patternDetail;

    public UpdateAction(JsonNode actionNode, TestActionContext testActionContext) {
        super(actionNode, testActionContext);
        pattern = JsonUtil.getString(actionNode, "pattern", "all");
        patternDetail = JsonUtil.getString(actionNode, "patternDetail", null);
    }

    public int run() {
        for (int i = 0; i < count; i++) {
            TestRecord testRecord = testActionContext.records.getRecord(source);

            if (testRecord == null)
                continue;

            TestRecordType recordTypeToUpdate = testRecord.getRecordType();
            RecordId recordId = testRecord.getRecordId();
            ActionResult result = updateRecord(recordTypeToUpdate, recordId);
            report(result.success, result.duration);
            if (result.success)
                testActionContext.records.addRecord(destination, new TestRecord(((Record)result.object).getId(), recordTypeToUpdate));
        }
        return failureCount;
    }
    
    private ActionResult updateRecord(TestRecordType recordTypeToUpdate, RecordId recordId) {
        double duration = 0;
        long before = 0;
        List<TestFieldType> fieldsToUpdate = new ArrayList<TestFieldType>();
        // Select x random fields to update
        if ("random".equals(pattern)) {
            List<TestFieldType> recordFields = recordTypeToUpdate.getFieldTypes();
            for (int i = 0; i < Integer.valueOf(patternDetail); i++) {
                int selectedField = (int) Math.floor(Math.random() * recordFields.size());
                fieldsToUpdate.add(recordFields.get(selectedField));
            }
        // Select specified fields to update
        } else if ("fields".equals(pattern)) {
            String[] fieldNames = patternDetail.split(",");
            List<QName> fieldQNames = new ArrayList<QName>(fieldNames.length);
            for (String fieldName: fieldNames) {
                fieldQNames.add(testActionContext.fieldTypes.get(fieldName).getFieldType().getName());
            }
            for (TestFieldType testFieldType : recordTypeToUpdate.getFieldTypes()) {
                if (fieldQNames.contains(testFieldType.getFieldType().getName())) {
                    fieldsToUpdate.add(testFieldType);
                }
            }
        }
        // Update all fields
        else
            fieldsToUpdate.addAll(recordTypeToUpdate.getFieldTypes());
        
        Record record = null;
        try {
            record = testActionContext.repository.newRecord(recordId);
        } catch (RecordException e) {
            reportError("Error preparing update record.", e);
            return new ActionResult(false, null, 0);
        }
        
        // If there is a Link-field that links to specified record type we need to read the field
        // in order to update the record that is linked to
        boolean readRecord = false;
        for (TestFieldType field: fieldsToUpdate) {
            if (field.getLinkedRecordTypeName() != null) {
                readRecord = true;
                break;
            }
        }
        Record originalRecord = null;
        if (readRecord == true) {
            before = System.nanoTime();
            try {
                originalRecord = testActionContext.repository.read(record.getId());
                long readDuration = System.nanoTime() - before;
                report(true, readDuration, "repoRead");
                duration += readDuration; 
            } catch (Throwable t) {
                long readDuration = System.nanoTime() - before;
                report(false, readDuration, "readLinkFields");
                reportError("Error updating subrecord.", t);
                duration += readDuration;
                return new ActionResult(false, null, duration);
            }
        }
        
        // Prepare the record with updated field values
        for (TestFieldType testFieldType : fieldsToUpdate) {
            ActionResult result = testFieldType.updateValue(this, originalRecord);
            duration += result.duration;
            if (!result.success)
                return new ActionResult(false, null, duration);
            // In case of a link field to a specific recordType we only update that record, but not the link itself
            if (result.object != null) 
                record.setField(testFieldType.fieldType.getName(), result.object);
        }

        boolean success;
        before = System.nanoTime();
        long updateDuration = 0;
        try {
            record = testActionContext.repository.update(record);
            updateDuration = System.nanoTime() - before;
            success = true;
        } catch (Throwable t) {
            updateDuration = System.nanoTime() - before;
            success = false;
            reportError("Error updating record.", t);
        }
        duration += updateDuration;
        report(success, updateDuration, "repoUpdate."+recordTypeToUpdate.getRecordType().getName().getName());
        return new ActionResult(success, record, duration);
    }
    
    public ActionResult linkFieldAction(TestFieldType testFieldType, RecordId recordId) {
        double duration = 0;
        String linkedRecordTypeName = testFieldType.getLinkedRecordTypeName();
        if (linkedRecordTypeName != null) {
            TestRecordType linkedRecordType = testActionContext.recordTypes.get(linkedRecordTypeName);
            ActionResult result = updateRecord(linkedRecordType, recordId);
            report(result.success, result.duration, "linkUpdate."+linkedRecordType.getRecordType().getName().getName());
            duration += result.duration;
            if (!result.success)
                return new ActionResult(false, null, duration);
            // We updated the record that was linked to but not the linkfield itself. So we return null in the ActionResult.
            return new ActionResult(true, null, duration);
        } else {
            return new ActionResult(true, testFieldType.generateLink(), 0);
        }
    }
}
