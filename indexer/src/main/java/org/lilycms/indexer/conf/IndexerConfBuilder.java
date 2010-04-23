package org.lilycms.indexer.conf;

import org.lilycms.repository.api.QName;
import org.lilycms.util.location.LocationAttributes;
import org.lilycms.util.xml.DocumentHelper;
import org.lilycms.util.xml.LocalXPathExpression;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.InputStream;
import java.util.*;

// TODO: add some validation of the XML?

// Terminology: the word "field" is usually used for a field from a repository record, while
// the term "index field" is usually used for a field in the index, though sometimes these
// are also just called field.
public class IndexerConfBuilder {
    private static LocalXPathExpression INDEX_CASES =
            new LocalXPathExpression("/indexer/cases/case");

    private static LocalXPathExpression INDEX_FIELDS =
            new LocalXPathExpression("/indexer/indexFields/indexField");

    private Document doc;

    private IndexerConf conf;

    private IndexerConfBuilder() {
        // prevents instantiation
    }

    public static IndexerConf build(InputStream is) throws IndexerConfException {
        Document doc;
        try {
            doc = DocumentHelper.parse(is);
        } catch (Exception e) {
            throw new IndexerConfException("Error parsing supplied indexer configuration.", e);
        }
        return new IndexerConfBuilder().build(doc);
    }

    private IndexerConf build(Document doc) throws IndexerConfException {
        this.doc = doc;
        this.conf = new IndexerConf();

        try {
            buildCases();
            buildIndexFields();
        } catch (Exception e) {
            throw new IndexerConfException("Error in the indexer configuration.", e);
        }

        return conf;
    }

    private void buildCases() throws Exception {
        List<Element> cases = INDEX_CASES.get().evalAsNativeElementList(doc);
        for (Element caseEl : cases) {
            // TODO will need resolving of the QName
            String recordType = DocumentHelper.getAttribute(caseEl, "recordType", true);
            String vtagsSpec = DocumentHelper.getAttribute(caseEl, "vtags", false);
            boolean indexVersionLess = DocumentHelper.getBooleanAttribute(caseEl, "indexVersionLess", false);

            Map<String, String> varPropsPattern = parseVariantPropertiesPattern(caseEl);
            Set<String> vtags = parseVersionTags(vtagsSpec);

            IndexCase indexCase = new IndexCase(recordType, varPropsPattern, vtags, indexVersionLess);
            conf.indexCases.add(indexCase);
        }
    }

    private Map<String, String> parseVariantPropertiesPattern(Element caseEl) throws Exception {
        String variant = DocumentHelper.getAttribute(caseEl, "variant", false);

        Map<String, String> varPropsPattern = new HashMap<String, String>();

        if (variant == null)
            return varPropsPattern;

        String[] props = variant.split(",");
        for (String prop : props) {
            prop = prop.trim();
            if (prop.length() > 0) {
                int eqPos = prop.indexOf("=");
                if (eqPos != -1) {
                    String propName = prop.substring(0, eqPos);
                    String propValue = prop.substring(eqPos + 1);
                    if (propName.equals("*")) {
                        throw new IndexerConfException(String.format("Error in variant attribute: the character '*' " +
                                "can only be used as wildcard, not as variant dimension name, attribute = %1$s, at: %2$s",
                                variant, LocationAttributes.getLocation(caseEl)));
                    }
                    varPropsPattern.put(propName, propValue);
                } else {
                    varPropsPattern.put(prop, null);
                }
            }
        }

        return varPropsPattern;
    }

    private Set<String> parseVersionTags(String vtagsSpec) {
        Set<String> vtags = new HashSet<String>();

        if (vtagsSpec == null)
            return vtags;

        String[] tags = vtagsSpec.split(",");
        for (String tag : tags) {
            tag = tag.trim();
            if (tag.length() > 0)
                vtags.add(tag);
        }

        return vtags;
    }

    private void buildIndexFields() throws Exception {
        List<Element> indexFields = INDEX_FIELDS.get().evalAsNativeElementList(doc);
        for (Element indexFieldEl : indexFields) {
            String name = DocumentHelper.getAttribute(indexFieldEl, "name", true);
            validateName(name);
            Element valueEl = DocumentHelper.getElementChild(indexFieldEl, "value", true);
            Value value = buildValue(valueEl);

            IndexField indexField = new IndexField(name, value);
            conf.addIndexField(indexField);
        }
    }

    private void validateName(String name) throws IndexerConfException {
        if (name.startsWith("@@")) {
            throw new IndexerConfException("Indexer configuration: names starting with @@ are reserved for internal uses. Name: " + name);
        }
    }

    private Value buildValue(Element valueEl) throws Exception {
        Element fieldEl = DocumentHelper.getElementChild(valueEl, "field", true);
        QName name = parseQName(DocumentHelper.getAttribute(fieldEl, "name", true), fieldEl);
        return new Value(name);
    }

    private QName parseQName(String qname, Element contextEl) throws IndexerConfException {
        int colonPos = qname.indexOf(":");
        if (colonPos == -1) {
            throw new IndexerConfException("Field name is not a qualified name, it should include a namespace prefix: " + qname);
        }

        String prefix = qname.substring(0, colonPos);
        String localName = qname.substring(colonPos + 1);

        String uri = contextEl.lookupNamespaceURI(prefix);
        if (uri == null) {
            throw new IndexerConfException("Prefix does not resolve to a namespace: " + qname);
        }

        return new QName(uri, localName);
    }

}
