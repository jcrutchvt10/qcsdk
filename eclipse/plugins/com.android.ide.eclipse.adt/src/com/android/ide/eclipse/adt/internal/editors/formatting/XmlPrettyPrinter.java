/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Eclipse Public License, Version 1.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.eclipse.org/org/documents/epl-v10.php
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.ide.eclipse.adt.internal.editors.formatting;

import static com.android.ide.eclipse.adt.internal.editors.descriptors.XmlnsAttributeDescriptor.XMLNS;
import static com.android.ide.eclipse.adt.internal.editors.resources.descriptors.ResourcesDescriptors.COLOR_ELEMENT;
import static com.android.ide.eclipse.adt.internal.editors.resources.descriptors.ResourcesDescriptors.DIMEN_ELEMENT;
import static com.android.ide.eclipse.adt.internal.editors.resources.descriptors.ResourcesDescriptors.ITEM_TAG;
import static com.android.ide.eclipse.adt.internal.editors.resources.descriptors.ResourcesDescriptors.STRING_ELEMENT;
import static com.android.ide.eclipse.adt.internal.editors.resources.descriptors.ResourcesDescriptors.STYLE_ELEMENT;

import com.android.ide.eclipse.adt.internal.editors.layout.gle2.DomUtilities;

import org.eclipse.wst.xml.core.internal.document.DocumentTypeImpl;
import org.eclipse.wst.xml.core.internal.document.ElementImpl;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Visitor which walks over the subtree of the DOM to be formatted and pretty prints
 * the DOM into the given {@link StringBuilder}
 */
@SuppressWarnings("restriction")
class XmlPrettyPrinter {
    /** The style to print the XML in */
    private final XmlFormatStyle mStyle;
    /** Formatting preferences to use when formatting the XML */
    private final XmlFormatPreferences mPrefs;
    /** Start node to start formatting at */
    private Node mStartNode;
    /** Start node to stop formatting after */
    private Node mEndNode;
    /** Whether the visitor is currently in range */
    private boolean mInRange;
    /** Output builder */
    private StringBuilder mOut;
    /** String to insert for a single indentation level */
    private String mIndentString;
    /** Line separator to use */
    private String mLineSeparator;

    /**
     * Creates a new {@link XmlPrettyPrinter}
     *
     * @param prefs the preferences to format with
     * @param style the style to format with
     * @param lineSeparator the line separator to use, such as "\n" (can be null, in which
     *     case the system default is looked up via the line.separator property)
     */
    XmlPrettyPrinter(XmlFormatPreferences prefs, XmlFormatStyle style, String lineSeparator) {
        mPrefs = prefs;
        mStyle = style;
        if (lineSeparator == null) {
            lineSeparator = System.getProperty("line.separator"); //$NON-NLS-1$
        }
        mLineSeparator = lineSeparator;
    }

    /**
     * Start pretty-printing at the given node, which must either be the
     * startNode or contain it as a descendant.
     *
     * @param rootDepth the depth of the given node, used to determine indentation
     * @param root the node to start pretty printing from (which may not itself be
     *            included in the start to end node range but should contain it)
     * @param startNode the node to start formatting at
     * @param endNode the node to end formatting at
     * @param out the {@link StringBuilder} to pretty print into
     */
    public void prettyPrint(int rootDepth, Node root, Node startNode, Node endNode,
            StringBuilder out) {
        if (startNode == null) {
            startNode = root;
        }
        if (endNode == null) {
            endNode = root;
        }

        mStartNode = startNode;
        mEndNode = endNode;
        mOut = out;
        mInRange = false;
        mIndentString = mPrefs.getOneIndentUnit();

        visitNode(rootDepth, root);
    }

    /** Visit the given node at the given depth */
    private void visitNode(int depth, Node node) {
        if (node == mStartNode) {
            mInRange = true;
        }

        if (mInRange) {
            visitBeforeChildren(depth, node);
        }

        NodeList children = node.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);
            visitNode(depth + 1, child);
        }

        if (mInRange) {
            visitAfterChildren(depth, node);
        }

        if (node == mEndNode) {
            mInRange = false;
        }
    }

    private void visitBeforeChildren(int depth, Node node) {
        short type = node.getNodeType();
        switch (type) {
            case Node.DOCUMENT_NODE:
            case Node.DOCUMENT_FRAGMENT_NODE:
                // Nothing to do
                break;

            case Node.ATTRIBUTE_NODE:
                // Handled as part of processing elements
                break;

            case Node.ELEMENT_NODE: {
                printOpenElementTag(depth, node);
                break;
            }

            case Node.TEXT_NODE: {
                printText(node);
                break;
            }

            case Node.CDATA_SECTION_NODE:
                printCharacterData(depth, node);
                break;

            case Node.PROCESSING_INSTRUCTION_NODE:
                printProcessingInstruction(node);
                break;

            case Node.COMMENT_NODE: {
                printComment(depth, node);
                break;
            }

            case Node.DOCUMENT_TYPE_NODE:
                printDocType(node);
                break;

            case Node.ENTITY_REFERENCE_NODE:
            case Node.ENTITY_NODE:
            case Node.NOTATION_NODE:
                break;
            default:
                assert false : type;
        }
    }

    private void visitAfterChildren(int depth, Node node) {
        short type = node.getNodeType();
        switch (type) {
            case Node.ATTRIBUTE_NODE:
                // Handled as part of processing elements
                break;
            case Node.ELEMENT_NODE: {
                printCloseElementTag(depth, node);
                break;
            }
        }
    }

    private void printProcessingInstruction(Node node) {
        mOut.append("<?xml "); //$NON-NLS-1$
        mOut.append(node.getNodeValue().trim());
        mOut.append('?').append('>').append(mLineSeparator);
    }

    private void printDocType(Node node) {
        // In Eclipse, org.w3c.dom.DocumentType.getTextContent() returns null
        if (node instanceof DocumentTypeImpl) {
            String content = ((DocumentTypeImpl) node).getSource();
            mOut.append(content);
            mOut.append(mLineSeparator);
        }
    }

    private void printCharacterData(int depth, Node node) {
        indent(depth);
        mOut.append("<![CDATA["); //$NON-NLS-1$
        mOut.append(node.getNodeValue());
        mOut.append("]]>");     //$NON-NLS-1$
        mOut.append(mLineSeparator);
    }

    private void printText(Node node) {
        // Most text nodes are just whitespace for formatting (which we're replacing)
        // so look for actual text content and extract that part out
        String text = node.getNodeValue();
        String trimmed = text.trim();
        if (trimmed.length() > 0) {
            // TODO: Reformat the contents if it is too wide?

            // Note that we append the actual text content, NOT the trimmed content,
            // since the whitespace may be significant, e.g.
            // <string name="toast_sync_error">Sync error: <xliff:g id="error">%1$s</xliff:g>...
            DomUtilities.appendXmlTextValue(mOut, text);

            if (mStyle != XmlFormatStyle.RESOURCE) {
                mOut.append(mLineSeparator);
            }
        }
    }

    private void printComment(int depth, Node node) {
        String comment = node.getNodeValue();
        boolean multiLine = comment.indexOf('\n') != -1;
        String trimmed = comment.trim();

        // See if this is an "end-of-the-line" comment, e.g. it is not a multi-line
        // comment and it appears on the same line as an opening or closing element tag;
        // if so, continue to place it as a suffix comment
        boolean isSuffixComment = false;
        if (!multiLine) {
            Node previous = node.getPreviousSibling();
            isSuffixComment = true;
            while (previous != null) {
                short type = previous.getNodeType();
                if (type == Node.TEXT_NODE || type == Node.COMMENT_NODE) {
                    if (previous.getNodeValue().indexOf('\n') != -1) {
                        isSuffixComment = false;
                        break;
                    }
                } else {
                    break;
                }
                previous = previous.getPreviousSibling();
            }
            if (isSuffixComment) {
                // Remove newline added by element open tag or element close tag
                if (endsWithLineSeparator()) {
                    removeLastLineSeparator();
                }
                mOut.append(' ');
            }
        }

        // Put the comment on a line on its own? Only if it does not follow some other comment
        // (e.g. is the first child in an element or follows some other element only separated
        // by whitespace)
        if (!mPrefs.removeEmptyLines && depth > 0 && !isSuffixComment) {
            Node curr = node.getPreviousSibling();
            if (curr == null
                    || curr.getNodeType() == Node.ELEMENT_NODE
                    || (curr.getNodeType() == Node.TEXT_NODE
                            && curr.getNodeValue().trim().length() == 0
                            && (curr.getPreviousSibling() == null
                               || curr.getPreviousSibling().getNodeType() == Node.ELEMENT_NODE))) {
                mOut.append(mLineSeparator);
            }
        }

        // TODO: Reformat the comment text?
        if (!multiLine && trimmed.length() < 70) {
            if (!isSuffixComment) {
                indent(depth);
            }
            mOut.append("<!-- ");  //$NON-NLS-1$
            mOut.append(trimmed);
            mOut.append(" -->"); //$NON-NLS-1$
            mOut.append(mLineSeparator);
        } else {
            indent(depth);
            mOut.append("<!--"); //$NON-NLS-1$
            mOut.append(mLineSeparator);
            int index = 0;
            int end = comment.length();
            int recentNewline = 0;
            while (index < end) {
                char c = comment.charAt(index);
                if (c == '\n') {
                    recentNewline = index;
                }
                if (!Character.isWhitespace(c)) {
                    break;
                }
                index++;
            }

            int start = recentNewline + 1;

            index = end - 1;
            recentNewline = -1;
            while (index > start) {
                char c = comment.charAt(index);
                if (c == '\n') {
                    recentNewline = index;
                }
                if (!Character.isWhitespace(c)) {
                    break;
                }
                index--;
            }
            end = recentNewline == -1 ? index : recentNewline;
            if (start < end) {
                mOut.append(comment.substring(start, end));
            }
            mOut.append(mLineSeparator);
            indent(depth);
            mOut.append("-->"); //$NON-NLS-1$
            mOut.append(mLineSeparator);
        }

        // Preserve whitespace after comment: See if the original document had two or
        // more newlines after the comment, and if so have a blank line between this
        // comment and the next
        Node next = node.getNextSibling();
        if (!mPrefs.removeEmptyLines && next != null && next.getNodeType() == Node.TEXT_NODE) {
            String text = next.getNodeValue();
            int newLinesBeforeText = 0;
            for (int i = 0, n = text.length(); i < n; i++) {
                char c = text.charAt(i);
                if (c == '\n') {
                    newLinesBeforeText++;
                    if (newLinesBeforeText == 2) {
                        // Yes
                        mOut.append(mLineSeparator);
                        break;
                    }
                } else if (!Character.isWhitespace(c)) {
                    break;
                }
            }
        }
    }

    private boolean endsWithLineSeparator() {
        int separatorLength = mLineSeparator.length();
        if (mOut.length() >= separatorLength) {
            for (int i = 0, j = mOut.length() - separatorLength; i < separatorLength; i++) {
               if (mOut.charAt(j) != mLineSeparator.charAt(i)) {
                   return false;
               }
            }
        }

        return true;
    }

    private void removeLastLineSeparator() {
        mOut.setLength(mOut.length() - mLineSeparator.length());
    }

    private void printOpenElementTag(int depth, Node node) {
        Element element = (Element) node;
        if (newlineBeforeElementOpen(element, depth)) {
            mOut.append(mLineSeparator);
        }
        if (indentBeforeElementOpen(element, depth)) {
            indent(depth);
        }
        mOut.append('<').append(element.getTagName());

        NamedNodeMap attributes = element.getAttributes();
        int attributeCount = attributes.getLength();
        if (attributeCount > 0) {
            // Sort the attributes
            List<Attr> attributeList = new ArrayList<Attr>();
            for (int i = 0, n = attributeCount; i < n; i++) {
                attributeList.add((Attr) attributes.item(i));
            }
            Comparator<Attr> comparator = mPrefs.sortAttributes.getAttributeComparator();
            Collections.sort(attributeList, comparator);

            // Put the single attribute on the same line as the element tag?
            boolean singleLine = mPrefs.oneAttributeOnFirstLine && attributeCount == 1
                    // In resource files we always put all the attributes (which is
                    // usually just zero, one or two) on the same line
                    || mStyle == XmlFormatStyle.RESOURCE;

            // We also place the namespace declaration on the same line as the root element,
            // but this doesn't also imply singleLine handling; subsequent attributes end up
            // on their own lines
            boolean indentNextAttribute;
            if (singleLine || (depth == 0 && XMLNS.equals(attributeList.get(0).getPrefix()))) {
                mOut.append(' ');
                indentNextAttribute = false;
            } else {
                mOut.append(mLineSeparator);
                indentNextAttribute = true;
            }

            Attr last = attributeList.get(attributeCount - 1);
            for (Attr attribute : attributeList) {
                if (indentNextAttribute) {
                    indent(depth + 1);
                }
                mOut.append(attribute.getName());
                mOut.append('=').append('"');
                DomUtilities.appendXmlAttributeValue(mOut, attribute.getValue());
                mOut.append('"');

                // Don't add a newline at the last attribute line; the > should
                // immediately follow the last attribute
                if (attribute != last) {
                    mOut.append(singleLine ? " " : mLineSeparator); //$NON-NLS-1$
                    indentNextAttribute = true;
                }
            }
        }

        boolean isClosed = isEmptyTag(element);

        // Add a space before the > or /> ? In resource files, only do this when closing the
        // element
        if (mPrefs.spaceBeforeClose && (mStyle != XmlFormatStyle.RESOURCE || isClosed)
                // in <selector> files etc still treat the <item> entries as in resource files
                && !ITEM_TAG.equals(element.getTagName())) {
            mOut.append(' ');
        }

        if (isClosed) {
            mOut.append('/');
        }

        mOut.append('>');

        if (newlineAfterElementOpen(element, depth, isClosed)) {
            mOut.append(mLineSeparator);
        }
    }

    private void printCloseElementTag(int depth, Node node) {
        Element element = (Element) node;
        if (isEmptyTag(element)) {
            // Empty tag: Already handled as part of opening tag
            return;
        }

        // Put the closing declaration on its own line - unless it's a compact
        // resource file format
        // If the element had element children, separate the end tag from them
        if (newlineBeforeElementClose(element, depth)) {
            mOut.append(mLineSeparator);
        }
        if (indentBeforeElementClose(element, depth)) {
            indent(depth);
        }
        mOut.append('<').append('/');
        mOut.append(node.getNodeName());
        mOut.append('>');

        if (newlineAfterElementClose(element, depth)) {
            mOut.append(mLineSeparator);
        }
    }

    private boolean newlineBeforeElementOpen(Element element, int depth) {
        if (mPrefs.removeEmptyLines || depth <= 0) {
            return false;
        }

        // See if this element should be separated from the previous element.
        // This is the case if we are not compressing whitespace (checked above),
        // or if we are not immediately following a comment (in which case the
        // newline would have been added above it), or if we are not in a formatting
        // style where
        if (mStyle == XmlFormatStyle.LAYOUT) {
            // In layouts we always separate elements
            return true;
        }

        if (mStyle == XmlFormatStyle.MANIFEST || mStyle == XmlFormatStyle.RESOURCE) {
            Node curr = element.getPreviousSibling();

            // <style> elements are traditionally separated unless it follows a comment
            if (STYLE_ELEMENT.equals(element.getTagName())) {
                if (curr == null
                        || curr.getNodeType() == Node.ELEMENT_NODE
                        || (curr.getNodeType() == Node.TEXT_NODE
                                && curr.getNodeValue().trim().length() == 0
                                && (curr.getPreviousSibling() == null
                                || curr.getPreviousSibling().getNodeType()
                                        == Node.ELEMENT_NODE))) {
                    return true;
                }
            }

            // In all other styles, we separate elements if they have a different tag than
            // the previous one (but we don't insert a newline inside tags)
            while (curr != null) {
                short nodeType = curr.getNodeType();
                if (nodeType == Node.ELEMENT_NODE) {
                    Element sibling = (Element) curr;
                    if (!element.getTagName().equals(sibling.getTagName())) {
                        return true;
                    }
                    break;
                } else if (nodeType == Node.TEXT_NODE) {
                    String text = curr.getNodeValue();
                    if (text.trim().length() > 0) {
                        break;
                    }
                    // If there is just whitespace, continue looking for a previous sibling
                } else {
                    // Any other previous node type, such as a comment, means we don't
                    // continue looking: this element should not be separated
                    break;
                }
                curr = curr.getPreviousSibling();
            }
            if (curr == null && depth <= 1) {
                // Insert new line inside tag if it's the first element inside the root tag
                return true;
            }

            return false;
        }

        return false;
    }

    private boolean indentBeforeElementOpen(Element element, int depth) {
        if (element.getParentNode().getNodeType() == Node.ELEMENT_NODE
                && keepElementAsSingleLine(depth - 1, (Element) element.getParentNode())) {
            return false;
        }

        return true;
    }

    private boolean indentBeforeElementClose(Element element, int depth) {
        char lastOutChar = mOut.charAt(mOut.length() - 1);
        char lastDelimiterChar = mLineSeparator.charAt(mLineSeparator.length() - 1);
        return lastOutChar == lastDelimiterChar;
    }

    private boolean newlineAfterElementOpen(Element element, int depth, boolean isClosed) {
        // In resource files we keep the child content directly on the same
        // line as the element (unless it has children). in other files, separate them
        return isClosed || !keepElementAsSingleLine(depth, element);
    }

    private boolean newlineBeforeElementClose(Element element, int depth) {
        return depth == 0 && !mPrefs.removeEmptyLines;
    }

    private boolean newlineAfterElementClose(Element element, int depth) {
        return element.getParentNode().getNodeType() == Node.ELEMENT_NODE
                && !keepElementAsSingleLine(depth - 1, (Element) element.getParentNode());
    }

    /**
     * TODO: Explain why we need to do per-tag decisions on whether to keep them on the
     * same line or not. Show that we can't just do it by depth, or by file type.
     * (style versus plurals example)
     * @param tag
     * @return
     */
    private boolean isSingleLineTag(Element element) {
        String tag = element.getTagName();

        return tag.equals(ITEM_TAG)
                || tag.equals(STRING_ELEMENT)
                || tag.equals(DIMEN_ELEMENT)
                || tag.equals(COLOR_ELEMENT);
    }

    private boolean keepElementAsSingleLine(int depth, Element element) {
        if (depth == 0) {
            return false;
        }

        return isSingleLineTag(element)
                || (mStyle == XmlFormatStyle.RESOURCE
                    && !DomUtilities.hasElementChildren(element));
    }

    private void indent(int depth) {
        for (int i = 0; i < depth; i++) {
            mOut.append(mIndentString);
        }
    }

    private boolean isEmptyTag(Element element) {
        boolean isClosed = false;
        if (element instanceof ElementImpl) {
            ElementImpl elementImpl = (ElementImpl) element;
            if (elementImpl.isEmptyTag()) {
                isClosed = true;
            }
        }
        return isClosed;
    }
}