/*
 * Copyright (c) 2019, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.ballerina.runtime.internal.values;

import io.ballerina.runtime.api.constants.RuntimeConstants;
import io.ballerina.runtime.api.creators.ErrorCreator;
import io.ballerina.runtime.api.types.PredefinedTypes;
import io.ballerina.runtime.api.types.Type;
import io.ballerina.runtime.api.types.TypeTags;
import io.ballerina.runtime.api.types.XmlNodeType;
import io.ballerina.runtime.api.utils.StringUtils;
import io.ballerina.runtime.api.utils.TypeUtils;
import io.ballerina.runtime.api.values.BLink;
import io.ballerina.runtime.api.values.BMap;
import io.ballerina.runtime.api.values.BString;
import io.ballerina.runtime.api.values.BXml;
import io.ballerina.runtime.api.values.BXmlSequence;
import io.ballerina.runtime.internal.errors.ErrorCodes;
import io.ballerina.runtime.internal.errors.ErrorHelper;
import io.ballerina.runtime.internal.types.BArrayType;
import io.ballerina.runtime.internal.types.BUnionType;
import io.ballerina.runtime.internal.utils.CycleUtils;
import io.ballerina.runtime.internal.utils.IteratorUtils;
import io.ballerina.runtime.internal.xml.XmlFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static io.ballerina.runtime.api.constants.RuntimeConstants.STRING_EMPTY_VALUE;
import static io.ballerina.runtime.api.constants.RuntimeConstants.XML_LANG_LIB;
import static io.ballerina.runtime.internal.TypeChecker.isEqual;

/**
 * <p>
 * {@code BXMLSequence} represents a sequence of {@link XmlItem}s in Ballerina.
 * </p>
 * <p>
 * <i>Note: This is an internal API and may change in future versions.</i>
 * </p>
 * 
 * @since 0.995.0
 */
public final class XmlSequence extends XmlValue implements BXmlSequence {

    List<BXml> children;

    /**
     * Create an empty xml sequence.
     */
    public XmlSequence() {
        children = new ArrayList<>();
        this.type = PredefinedTypes.TYPE_XML_NEVER;
    }

    public XmlSequence(List<BXml> values) {
        if (values.isEmpty()) {
            this.children = values;
            return;
        }
        setSequenceMembersConcatenatingAdjacentTextItems(values);
    }

    public XmlSequence(BXml child) {
        this.children = new ArrayList<>();
        if (!child.isEmpty()) {
            this.children.add(child);
        }
    }

    @Override
    public List<BXml> getChildrenList() {
        return children;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public XmlNodeType getNodeType() {
        return XmlNodeType.SEQUENCE;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isEmpty() {
        return children.isEmpty();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isSingleton() {
        return children.size() == 1 && children.get(0).isSingleton();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getItemType() {
        if (isSingleton()) {
            return children.get(0).getItemType();
        }

        return XmlNodeType.SEQUENCE.value();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getElementName() {
        if (isSingleton()) {
            return children.get(0).getElementName();
        }
        return STRING_EMPTY_VALUE.getValue();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getTextValue() {
        StringBuilder seqTextBuilder = new StringBuilder();
        for (BXml x : children) {
            if (x.getNodeType() == XmlNodeType.ELEMENT || x.getNodeType() == XmlNodeType.TEXT) {
                seqTextBuilder.append(x.getTextValue());
            }
        }
        return seqTextBuilder.toString();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BString getAttribute(String localName, String namespace) {
        if (isSingleton()) {
            return children.get(0).getAttribute(localName, namespace);
        }

        return RuntimeConstants.BSTRING_NULL_VALUE;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BString getAttribute(String localName, String namespace, String prefix) {
        if (isSingleton()) {
            return children.get(0).getAttribute(localName, namespace, prefix);
        }

        return RuntimeConstants.BSTRING_NULL_VALUE;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setAttribute(String localName, String namespace, String prefix, String value) {
        if (this.isFrozen()) {
            ReadOnlyUtils.handleInvalidUpdate(XML_LANG_LIB);
        }

        if (isSingleton()) {
            children.get(0).setAttribute(localName, namespace, prefix, value);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public MapValue<BString, BString> getAttributesMap() {
        if (isSingleton()) {
            return (MapValue<BString, BString>) children.get(0).getAttributesMap();
        }

        return null;
    }

    @Override
    @Deprecated
    public void setAttributes(BMap<BString, BString> attributes) {
        if (this.isFrozen()) {
            ReadOnlyUtils.handleInvalidUpdate(XML_LANG_LIB);
        }

        if (isSingleton()) {
            children.get(0).setAttributes(attributes);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public XmlValue elements() {
        List<BXml> elementsSeq = new ArrayList<>();
        for (BXml child : children) {
            if (child.getNodeType() == XmlNodeType.ELEMENT) {
                elementsSeq.add(child);
            }
        }
        return new XmlSequence(elementsSeq);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public XmlValue elements(String qname) {
        List<BXml> elementsSeq = new ArrayList<>();
        String qnameStr = getQname(qname).toString();
        for (BXml child : children) {
            if (child.getNodeType() == XmlNodeType.ELEMENT && child.getElementName().equals(qnameStr)) {
                elementsSeq.add(child);
            }
        }
        return new XmlSequence(elementsSeq);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public XmlValue children() {
        if (children.size() == 1) {
            return (XmlValue) children.get(0).children();
        }
        return new XmlSequence(new ArrayList<>(children));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public XmlValue children(String qname) {
        List<BXml> selected = new ArrayList<>();
        if (this.children.size() == 1) {
            BXml bxml = this.children.get(0);
            return (XmlValue) bxml.children(qname);
        }

        for (BXml elem : this.children) {
            XmlSequence elements = (XmlSequence) elem.children().elements(qname);
            List<BXml> childrenList = elements.getChildrenList();
            if (childrenList.size() == 1) {
                selected.add(childrenList.get(0));
            } else if (childrenList.size() > 1) {
                selected.addAll(childrenList);
            }
        }

        if (selected.size() == 1) {
            return (XmlValue) selected.get(0);
        }

        return new XmlSequence(selected);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setChildren(BXml seq) {
        if (this.isFrozen()) {
            ReadOnlyUtils.handleInvalidUpdate(XML_LANG_LIB);
        }

        if (children.size() != 1) {
            throw ErrorCreator.createError(StringUtils.fromString(("not an " + XmlNodeType.ELEMENT)));
        }

        children.get(0).setChildren(seq);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Deprecated
    public void addChildren(BXml xmlItem) {
        children.add(xmlItem);

        // If sequence contains children of same type
        // the sequence type should be changed to that corresponding xml type
        boolean isSameType = true;

        Type tempExprType = children.get(0).getType();

        for (int i = 1; i < children.size(); i++) {
             if (tempExprType != children.get(i).getType()) {
                isSameType = false;
                break;
            }
        }
        if (isSameType) {
            this.type = getSequenceType(tempExprType);
            return;
        }
        this.type = PredefinedTypes.TYPE_XML;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public XmlValue strip() {
        List<BXml> elementsSeq = new ArrayList<>();
        boolean prevChildWasATextNode = false;
        String prevConsecutiveText = null;

        for (BXml x : children) {
            XmlValue item = (XmlValue) x;
            if (item.getNodeType() == XmlNodeType.TEXT) {
                if (prevChildWasATextNode) {
                    prevConsecutiveText += x.getTextValue();
                } else {
                    prevConsecutiveText = x.getTextValue();
                }
                prevChildWasATextNode = true;
            } else if (item.getNodeType() == XmlNodeType.ELEMENT) {
                // Previous child was a text node and now we see a element node, we need to add last child to the list
                if (prevChildWasATextNode && !prevConsecutiveText.trim().isEmpty()) {
                    elementsSeq.add(new XmlText(prevConsecutiveText));
                    prevConsecutiveText = null;
                }
                prevChildWasATextNode = false;
                elementsSeq.add(x);
            }
        }
        if (prevChildWasATextNode && !prevConsecutiveText.trim().isEmpty()) {
            elementsSeq.add(new XmlText(prevConsecutiveText));
        }
        return new XmlSequence(elementsSeq);
    }

    @Override
    public int hashCode() {
        return Objects.hash(children);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public XmlValue slice(long startIndex, long endIndex) {
        if (startIndex > this.children.size() || endIndex > this.children.size() || startIndex < -1 || endIndex < -1) {
            throw ErrorCreator
                    .createError(
                            StringUtils.fromString(("index out of range: [" + startIndex + "," + endIndex + "]")));
        }

        if (startIndex == -1) {
            startIndex = 0;
        }

        if (endIndex == -1) {
            endIndex = children.size();
        }

        if (startIndex == endIndex) {
            return new XmlSequence();
        }

        if (startIndex > endIndex) {
            throw ErrorCreator
                    .createError(StringUtils.fromString(("invalid indices: " + startIndex + " < " + endIndex)));
        }

        int j = 0;
        List<BXml> elementsSeq = new ArrayList<>();
        for (int i = (int) startIndex; i < endIndex; i++) {
            elementsSeq.add(j++, children.get(i));
        }

        return new XmlSequence(elementsSeq);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public XmlValue descendants(List<String> qnames) {
        List<BXml> descendants = new ArrayList<>();
        for (BXml child : children) {
            if (child.getNodeType() == XmlNodeType.ELEMENT) {
                XmlItem element = (XmlItem) child;
                String name = element.getQName().toString();
                if (qnames.contains(name)) {
                    descendants.add(element);
                }
                addDescendants(descendants, element, qnames);
            }
        }

        return new XmlSequence(descendants);
    }

    @Override
    public XmlValue descendants() {
        List<BXml> descendants = new ArrayList<>();
        if (children.size() == 1) {
            XmlItem element = (XmlItem) children.get(0);
            addDescendants(descendants, element);
        }
        return new XmlSequence(descendants);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object value() {
        BArrayType bArrayType = new BArrayType(PredefinedTypes.TYPE_XML);
        return new ArrayValueImpl(children.toArray(), bArrayType);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        try {
            return stringValue(null);
        } catch (Throwable t) {
            handleXmlException("failed to get xml as string: ", t);
        }
        return RuntimeConstants.STRING_NULL_VALUE;
    }

    /**
     * {@inheritDoc}
     * @param parent The link to the parent node
     */
    @Override
    public String stringValue(BLink parent) {
        try {
            StringBuilder sb = new StringBuilder();
            for (BXml child : children) {
                sb.append(child.stringValue(new CycleUtils.Node(this, parent)));
            }
            return sb.toString();
        } catch (Throwable t) {
            handleXmlException("failed to get xml as string: ", t);
        }
        return RuntimeConstants.STRING_NULL_VALUE;
    }

    @Override
    public String informalStringValue(BLink parent) {
        return "`" + stringValue(parent) + "`";
    }

    @Override
    public String expressionStringValue(BLink parent) {
        return "xml`" + toString() + "`";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object copy(Map<Object, Object> refs) {
        if (isFrozen()) {
            return this;
        }

        if (refs.containsKey(this)) {
            return refs.get(this);
        }

        ArrayList<BXml> copiedChildrenList = new ArrayList<>(children.size());
        XmlSequence copiedSeq = new XmlSequence(copiedChildrenList);
        refs.put(this, copiedSeq);
        for (BXml child : children) {
            copiedChildrenList.add((XmlValue) child.copy(refs));
        }

        return copiedSeq;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object frozenCopy(Map<Object, Object> refs) {
        XmlSequence copy = (XmlSequence) copy(refs);
        if (!copy.isFrozen()) {
            copy.freezeDirect();
        }
        return copy;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public XmlValue getItem(int index) {
        try {
            if (index >= this.children.size()) {
                return new XmlSequence();
            }
            return (XmlValue) this.children.get(index);
        } catch (Exception e) {
            throw ErrorHelper.getRuntimeException(
                    ErrorCodes.XML_SEQUENCE_INDEX_OUT_OF_RANGE, this.children.size(), index);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int size() {
        return this.children.size();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void build() {
        for (BXml child : children) {
            child.build();
        }
    }

    @Override
    protected void setAttributesOnInitialization(BMap<BString, BString> attributes) {
        if (isSingleton()) {
            ((XmlValue) children.get(0)).setAttributesOnInitialization(attributes);
        }
    }

    @Override
    protected void setAttributeOnInitialization(String localName, String namespace, String prefix, String value) {
        ((XmlValue) children.get(0)).setAttributeOnInitialization(localName, namespace, prefix, value);
    }

    @Override
    public void removeAttribute(String qname) {
        if (this.isFrozen()) {
            ReadOnlyUtils.handleInvalidUpdate(XML_LANG_LIB);
        }

        if (children.size() != 1) {
            throw ErrorCreator.createError(StringUtils.fromString(("not an " + XmlNodeType.ELEMENT)));
        }

        children.get(0).removeAttribute(qname);
    }

    @Override
    @Deprecated
    public void removeChildren(String qname) {
        if (this.isFrozen()) {
            ReadOnlyUtils.handleInvalidUpdate(XML_LANG_LIB);
        }

        if (children.size() != 1) {
            throw ErrorCreator.createError(StringUtils.fromString(("not an " + XmlNodeType.ELEMENT)));
        }

        children.get(0).removeChildren(qname);
    }

    @Override
    public void freezeDirect() {
        this.type = ReadOnlyUtils.setImmutableTypeAndGetEffectiveType(this.type);
        for (BXml elem : children) {
            elem.freezeDirect();
        }
        this.typedesc = null;
    }

    @Override
    public boolean isFrozen() {
        if (this.type.isReadOnly()) {
            return true;
        }

        for (BXml child : this.children) {
            if (!child.isFrozen()) {
                return false;
            }
        }
        freezeDirect();
        return true;
    }

    @Override
    public IteratorValue<BXml> getIterator() {
        return new IteratorValue<>() {
            final Iterator<BXml> iterator = children.iterator();

            @Override
            public boolean hasNext() {
                return iterator.hasNext();
            }

            @Override
            public BXml next() {
                return iterator.next();
            }
        };
    }

    private void setSequenceMembersConcatenatingAdjacentTextItems(List<BXml> values) {
        ArrayList<BXml> members = new ArrayList<>();
        boolean isPreviousValueText = false;
        StringBuilder text = new StringBuilder();
        for (BXml value : values) {
            if (value.getNodeType() == XmlNodeType.TEXT) {
                isPreviousValueText = true;
                text.append(value.getTextValue());
                continue;
            }
            if (isPreviousValueText) {
                members.add(XmlFactory.createXMLText(StringUtils.fromString(text.toString())));
                isPreviousValueText = false;
                text.setLength(0);
            }
            members.add(value);
        }
        if (!text.isEmpty()) {
            members.add(XmlFactory.createXMLText(StringUtils.fromString(text.toString())));
        }
        this.children = members;
    }

    private Type getSequenceType(Type tempExprType) {
        return switch (tempExprType.getTag()) {
            case TypeTags.XML_ELEMENT_TAG -> PredefinedTypes.TYPE_XML_ELEMENT_SEQUENCE;
            case TypeTags.XML_COMMENT_TAG -> PredefinedTypes.TYPE_XML_COMMENT_SEQUENCE;
            case TypeTags.XML_PI_TAG -> PredefinedTypes.TYPE_XML_PI_SEQUENCE;
            default -> PredefinedTypes.TYPE_XML_TEXT_SEQUENCE;
        };
    }

    private void initializeIteratorNextReturnType() {
        Type childrenType;
        if (children.size() == 1) {
            childrenType = children.get(0).getType();
        } else {
            Set<Type> types = new HashSet<>();
            for (BXml child : children) {
                types.add(child.getType());
            }
            childrenType = new BUnionType(new ArrayList<>(types));
        }
        iteratorNextReturnType = IteratorUtils.createIteratorNextReturnType(childrenType);
    }

    @Override
    public Type getIteratorNextReturnType() {
        if (iteratorNextReturnType == null) {
            initializeIteratorNextReturnType();
        }
        return iteratorNextReturnType;
    }

    /**
     * Deep equality check for XML Sequence.
     *
     * @param o The XML Sequence to be compared
     * @param visitedValues Visited values in previous recursive calls
     * @return True if the XML Sequences are equal; False otherwise
     */
    @Override
    public boolean equals(Object o, Set<ValuePair> visitedValues) {
        if (o instanceof XmlSequence rhsXMLSequence) {
            return isXMLSequenceChildrenEqual(this.getChildrenList(), rhsXMLSequence.getChildrenList());
        }
        if (this.isSingleton() && (o instanceof XmlValue)) {
            return isEqual(this.getChildrenList().get(0), o);
        }
        return this.getChildrenList().isEmpty() && TypeUtils.getType(o) == PredefinedTypes.TYPE_XML_NEVER;
    }

    private static boolean isXMLSequenceChildrenEqual(List<BXml> lhsList, List<BXml> rhsList) {
        if (lhsList.size() != rhsList.size()) {
            return false;
        }
        for (int i = 0; i < lhsList.size(); i++) {
            if (!isEqual(lhsList.get(i), rhsList.get(i))) {
                return false;
            }
        }
        return true;
    }
}
