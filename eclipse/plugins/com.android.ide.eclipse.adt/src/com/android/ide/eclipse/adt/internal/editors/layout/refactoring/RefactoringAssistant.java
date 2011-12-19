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

package com.android.ide.eclipse.adt.internal.editors.layout.refactoring;

import com.android.ide.eclipse.adt.AdtPlugin;
import com.android.ide.eclipse.adt.internal.editors.AndroidXmlEditor;
import com.android.ide.eclipse.adt.internal.editors.layout.LayoutEditor;
import com.android.ide.eclipse.adt.internal.editors.layout.gle2.DomUtilities;
import com.android.ide.eclipse.adt.internal.refactorings.extractstring.ExtractStringRefactoring;
import com.android.ide.eclipse.adt.internal.refactorings.extractstring.ExtractStringWizard;

import org.eclipse.core.resources.IFile;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.text.TextSelection;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.text.contentassist.IContextInformation;
import org.eclipse.jface.text.quickassist.IQuickAssistInvocationContext;
import org.eclipse.jface.text.quickassist.IQuickAssistProcessor;
import org.eclipse.jface.text.source.Annotation;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.ltk.core.refactoring.Refactoring;
import org.eclipse.ltk.ui.refactoring.RefactoringWizard;
import org.eclipse.ltk.ui.refactoring.RefactoringWizardOpenOperation;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.wst.sse.core.internal.provisional.IStructuredModel;
import org.eclipse.wst.sse.core.internal.provisional.IndexedRegion;
import org.eclipse.wst.sse.core.internal.provisional.text.IStructuredDocument;
import org.eclipse.wst.sse.core.internal.provisional.text.IStructuredDocumentRegion;
import org.eclipse.wst.sse.core.internal.provisional.text.ITextRegion;
import org.eclipse.wst.sse.ui.StructuredTextEditor;
import org.eclipse.wst.xml.core.internal.regions.DOMRegionContext;
import org.w3c.dom.Node;

import java.util.ArrayList;
import java.util.List;

/**
 * QuickAssistProcessor which helps invoke refactoring operations on text elements.
 */
@SuppressWarnings("restriction") // XML model
public class RefactoringAssistant implements IQuickAssistProcessor {

    /**
     * Creates a new {@link RefactoringAssistant}
     */
    public RefactoringAssistant() {
    }

    public boolean canAssist(IQuickAssistInvocationContext invocationContext) {
        return true;
    }

    public boolean canFix(Annotation annotation) {
        return true;
    }

    public ICompletionProposal[] computeQuickAssistProposals(
            IQuickAssistInvocationContext invocationContext) {

        ISourceViewer sourceViewer = invocationContext.getSourceViewer();
        AndroidXmlEditor xmlEditor = AndroidXmlEditor.getAndroidXmlEditor(sourceViewer);
        if (xmlEditor == null) {
            return null;
        }

        IFile file = xmlEditor.getInputFile();
        int offset = invocationContext.getOffset();

        // Ensure that we are over a tag name (for element-based refactoring
        // operations) or a value (for the extract include refactoring)

        boolean isValue = false;
        boolean isReferenceValue = false;
        boolean isTagName = false;
        boolean isAttributeName = false;
        boolean isStylableAttribute = false;
        IStructuredModel model = null;
        try {
            model = xmlEditor.getModelForRead();
            IStructuredDocument doc = model.getStructuredDocument();
            IStructuredDocumentRegion region = doc.getRegionAtCharacterOffset(offset);
            ITextRegion subRegion = region.getRegionAtCharacterOffset(offset);
            if (subRegion != null) {
                String type = subRegion.getType();
                if (type.equals(DOMRegionContext.XML_TAG_ATTRIBUTE_VALUE)) {
                    String value = region.getText(subRegion);
                    // Only extract values that aren't already resources
                    // (and value includes leading ' or ")
                    isValue = true;
                    if (value.startsWith("'@") || value.startsWith("\"@")) { //$NON-NLS-1$ //$NON-NLS-2$
                        isReferenceValue = true;
                    }
                } else if (type.equals(DOMRegionContext.XML_TAG_NAME)
                        || type.equals(DOMRegionContext.XML_TAG_OPEN)
                        || type.equals(DOMRegionContext.XML_TAG_CLOSE)) {
                    isTagName = true;
                } else if (type.equals(DOMRegionContext.XML_TAG_ATTRIBUTE_NAME) ) {
                    isAttributeName = true;
                    String name = region.getText(subRegion);
                    int index = name.indexOf(':');
                    if (index != -1) {
                        name = name.substring(index + 1);
                    }
                    isStylableAttribute = ExtractStyleRefactoring.isStylableAttribute(name);
                } else if (type.equals(DOMRegionContext.XML_TAG_ATTRIBUTE_EQUALS)) {
                    // On the edge of an attribute name and an attribute value
                    isAttributeName = true;
                    isStylableAttribute = true;
                }
            }
        } finally {
            if (model != null) {
                model.releaseFromRead();
            }
        }

        List<ICompletionProposal> proposals = new ArrayList<ICompletionProposal>();
        if (isTagName || isAttributeName || isValue) {
            StructuredTextEditor structuredEditor = xmlEditor.getStructuredTextEditor();
            ISelectionProvider provider = structuredEditor.getSelectionProvider();
            ISelection selection = provider.getSelection();
            if (selection instanceof ITextSelection) {
                ITextSelection textSelection = (ITextSelection) selection;

                ITextSelection originalSelection = textSelection;

                // Most of the visual refactorings do not work on text ranges
                // ...except for Extract Style where the actual attributes overlapping
                // the selection is going to be the set of eligible attributes
                boolean selectionOkay = false;

                if (textSelection.getLength() == 0 && !isValue) {
                    selectionOkay = true;
                    ISourceViewer textViewer = xmlEditor.getStructuredSourceViewer();
                    int caretOffset = textViewer.getTextWidget().getCaretOffset();
                    if (caretOffset >= 0) {
                        Node node = DomUtilities.getNode(textViewer.getDocument(), caretOffset);
                        if (node instanceof IndexedRegion) {
                            IndexedRegion region = (IndexedRegion) node;
                            int startOffset = region.getStartOffset();
                            int length = region.getEndOffset() - region.getStartOffset();
                            textSelection = new TextSelection(startOffset, length);
                        }
                    }
                }

                if (isValue && !isReferenceValue) {
                    proposals.add(new RefactoringProposal(xmlEditor,
                            new ExtractStringRefactoring(file, xmlEditor, textSelection)));
                }

                if (xmlEditor instanceof LayoutEditor) {
                    LayoutEditor editor = (LayoutEditor) xmlEditor;

                    boolean showStyleFirst = isValue || (isAttributeName && isStylableAttribute);
                    if (showStyleFirst) {
                        proposals.add(new RefactoringProposal(editor,
                                new ExtractStyleRefactoring(file, editor, originalSelection,
                                        null)));
                    }

                    if (selectionOkay) {
                        proposals.add(new RefactoringProposal(editor,
                                new WrapInRefactoring(file, editor, textSelection, null)));
                        proposals.add(new RefactoringProposal(editor,
                                new UnwrapRefactoring(file, editor, textSelection, null)));
                        proposals.add(new RefactoringProposal(editor,
                                new ChangeViewRefactoring(file, editor, textSelection, null)));
                        proposals.add(new RefactoringProposal(editor,
                                new ChangeLayoutRefactoring(file, editor, textSelection, null)));
                    }

                    // Extract Include must always have an actual block to be extracted
                    if (textSelection.getLength() > 0) {
                        proposals.add(new RefactoringProposal(editor,
                                new ExtractIncludeRefactoring(file, editor, textSelection, null)));
                    }

                    // If it's not a value or attribute name, don't place it on top
                    if (!showStyleFirst) {
                        proposals.add(new RefactoringProposal(editor,
                                new ExtractStyleRefactoring(file, editor, originalSelection,
                                        null)));
                    }

                }
            }
        }

        if (proposals.size() == 0) {
            return null;
        } else {
            return proposals.toArray(new ICompletionProposal[proposals.size()]);
        }
    }

    public String getErrorMessage() {
        return null;
    }

    private static class RefactoringProposal
            implements ICompletionProposal {
        private final AndroidXmlEditor mEditor;
        private final Refactoring mRefactoring;

        RefactoringProposal(AndroidXmlEditor editor, Refactoring refactoring) {
            super();
            mEditor = editor;
            mRefactoring = refactoring;
        }

        public void apply(IDocument document) {
            RefactoringWizard wizard = null;
            if (mRefactoring instanceof VisualRefactoring) {
                wizard = ((VisualRefactoring) mRefactoring).createWizard();
            } else if (mRefactoring instanceof ExtractStringRefactoring) {
                wizard = new ExtractStringWizard((ExtractStringRefactoring) mRefactoring,
                        mEditor.getProject());
            } else {
                throw new IllegalArgumentException();
            }

            RefactoringWizardOpenOperation op = new RefactoringWizardOpenOperation(wizard);
            try {
                IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
                op.run(window.getShell(), wizard.getDefaultPageTitle());
            } catch (InterruptedException e) {
            }
        }

        public String getAdditionalProposalInfo() {
            return String.format("Initiates the \"%1$s\" refactoring", mRefactoring.getName());
        }

        public IContextInformation getContextInformation() {
            return null;
        }

        public String getDisplayString() {
            return mRefactoring.getName();
        }

        public Image getImage() {
            return AdtPlugin.getAndroidLogo();
        }

        public Point getSelection(IDocument document) {
            return null;
        }
    }
}
