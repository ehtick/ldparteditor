/* MIT - License

Copyright (c) 2012 - this year, Nils Schmidt

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"),
to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense,
and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED,
INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR
PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE
FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE. */
package org.nschmidt.ldparteditor.helper.compositetext;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.text.MessageFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Shell;
import org.nschmidt.ldparteditor.composite.Composite3D;
import org.nschmidt.ldparteditor.composite.compositetab.CompositeTab;
import org.nschmidt.ldparteditor.data.DatFile;
import org.nschmidt.ldparteditor.data.DatType;
import org.nschmidt.ldparteditor.data.GColour;
import org.nschmidt.ldparteditor.data.GData;
import org.nschmidt.ldparteditor.data.GData2;
import org.nschmidt.ldparteditor.data.GData3;
import org.nschmidt.ldparteditor.data.GData4;
import org.nschmidt.ldparteditor.data.GData5;
import org.nschmidt.ldparteditor.data.GDataCSG;
import org.nschmidt.ldparteditor.data.Matrix;
import org.nschmidt.ldparteditor.data.Vertex;
import org.nschmidt.ldparteditor.data.VertexManager;
import org.nschmidt.ldparteditor.enumtype.MyLanguage;
import org.nschmidt.ldparteditor.enumtype.View;
import org.nschmidt.ldparteditor.helper.LDPartEditorException;
import org.nschmidt.ldparteditor.helper.math.HashBiMap;
import org.nschmidt.ldparteditor.helper.math.MathHelper;
import org.nschmidt.ldparteditor.i18n.I18n;
import org.nschmidt.ldparteditor.logger.NLogger;
import org.nschmidt.ldparteditor.opengl.OpenGLRenderer;
import org.nschmidt.ldparteditor.project.Project;
import org.nschmidt.ldparteditor.shell.editor3d.Editor3DWindow;
import org.nschmidt.ldparteditor.shell.editortext.EditorTextWindow;
import org.nschmidt.ldparteditor.text.DatParser;
import org.nschmidt.ldparteditor.text.StringHelper;
import org.nschmidt.ldparteditor.widget.TreeItem;

/**
 * Compiles outlined subfiles (reverse inlining)
 */
public enum SubfileCompiler {
    INSTANCE;

    private static final Deque<Matrix> matrixInvStack = new ArrayDeque<>();
    private static final Deque<Matrix> matrixProdStack = new ArrayDeque<>();
    private static final Deque<String> nameStack = new ArrayDeque<>();
    private static final Deque<String> colourStack = new ArrayDeque<>();
    private static final Deque<StringBuilder> builderStack = new ArrayDeque<>();
    private static final Deque<Boolean> toFolderStack = new ArrayDeque<>();
    private static boolean skipCompile = true;

    private static String colour = "16"; //$NON-NLS-1$
    private static String name = ""; //$NON-NLS-1$
    private static Matrix matrixInv = View.ACCURATE_ID;
    private static Matrix matrixProd = View.ACCURATE_ID;
    private static StringBuilder builder = new StringBuilder();
    private static boolean writeToPartFolder = true;

    /**
     * Compiles outlined subfiles (reverse inlining, clears the selection)
     *
     * @param datFile
     * @param preserveSelection
     * @param forceParsing
     */
    public static void compile(final DatFile datFile, boolean preserveSelection, boolean forceParsing) {
        final VertexManager vm = datFile.getVertexManager();
        if (!vm.isUpdated()) {
            final boolean[] doNotWait = new boolean[]{false};
            try
            {
                new ProgressMonitorDialog(Editor3DWindow.getWindow().getShell()).run(true, true, new IRunnableWithProgress()
                {
                    @Override
                    public void run(final IProgressMonitor monitor) throws InvocationTargetException, InterruptedException
                    {
                        monitor.beginTask(I18n.E3D_WAIT_FOR_UPDATE, IProgressMonitor.UNKNOWN);
                        while (!vm.isUpdated()) {
                            if (monitor.isCanceled()) break;
                            try {
                                Thread.sleep(100);
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                                throw new LDPartEditorException(ie);
                            }
                        }
                        doNotWait[0] = monitor.isCanceled();
                    }
                });
            } catch (InvocationTargetException consumed) {
                return;
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new LDPartEditorException(ie);
            }
            if (doNotWait[0]) {
                return;
            }
        }
        Set<Integer> selectedDataIndices = new HashSet<>();
        Set<Vertex> selectedVertices = new HashSet<>();
        if (preserveSelection) {
            selectedVertices.addAll(vm.getSelectedVertices());
            HashBiMap<Integer, GData> dpl = datFile.getDrawPerLineNoClone();
            Set<Integer> keys = dpl.keySet();
            List<Integer> lineNumbers = new ArrayList<>();
            lineNumbers.addAll(keys);
            for (Integer l : dpl.keySet()) {
                GData gd = datFile.getDrawPerLineNoClone().getValue(l);
                int type = gd.type();
                if (type != 1 && vm.getSelectedData().contains(gd)) {
                    selectedDataIndices.add(l);
                }
            }
        }
        vm.clearSelection();
        GDataCSG.resetCSG(datFile, false);
        GDataCSG.forceRecompile(datFile);
        skipCompile = true;
        matrixInv = View.ACCURATE_ID;
        matrixProd = View.ACCURATE_ID;

        if (datFile.getVertexManager().isModified() || forceParsing) {
            if (!forceParsing) datFile.setText(datFile.getText());
            datFile.parseForData(false);
        }

        HashBiMap<Integer, GData> dpl = datFile.getDrawPerLineNoClone();

        Set<Integer> keys = dpl.keySet();
        List<Integer> lineNumbers = new ArrayList<>();
        lineNumbers.addAll(keys);
        Collections.sort(lineNumbers);

        for (Integer l : lineNumbers) {
            SubfileCompiler.compile(l, datFile);
        }

        matrixInvStack.clear();
        matrixProdStack.clear();
        nameStack.clear();
        colourStack.clear();
        builderStack.clear();
        toFolderStack.clear();

        builder = null;
        Editor3DWindow.getWindow().updateTreeUnsavedEntries();
        datFile.getVertexManager().clear();
        datFile.parseForData(false);

        // Link last line
        NLogger.debug(SubfileCompiler.class, "Last line is {0}", datFile.getDrawPerLineNoClone().getValue(lineNumbers.size())); //$NON-NLS-1$

        for (EditorTextWindow w : Project.getOpenTextWindows()) {
            for (CTabItem t : w.getTabFolder().getItems()) {
                if (datFile.equals(((CompositeTab) t).getState().getFileNameObj())) {
                    ((CompositeTab) t).parseForErrorAndHints();
                    ((CompositeTab) t).getTextComposite().redraw();
                    break;
                }
            }
        }

        if (preserveSelection) {
            for (Integer l : lineNumbers) {
                GData gd = dpl.getValue(l);
                if (selectedDataIndices.contains(l)) {
                    vm.getSelectedData().add(gd);
                    switch (gd.type()) {
                    case 2:
                        vm.getSelectedLines().add((GData2) gd);
                        break;
                    case 3:
                        vm.getSelectedTriangles().add((GData3) gd);
                        break;
                    case 4:
                        vm.getSelectedQuads().add((GData4) gd);
                        break;
                    case 5:
                        vm.getSelectedCondlines().add((GData5) gd);
                        break;
                    default:
                        break;
                    }
                }
            }
            selectedVertices.retainAll(vm.getVertices());
            vm.getSelectedVertices().addAll(selectedVertices);
        }
    }

    @SuppressWarnings("unchecked")
    private static void compile(Integer lineNumber, DatFile datFile) {
        GData gd = datFile.getDrawPerLineNoClone().getValue(lineNumber);
        int type = gd.type();

        if (skipCompile && type != 0)
            return;

        DatParser.clearConstants();

        if (gd.getNext() == null && type != 0 && !toFolderStack.isEmpty() && !skipCompile) {
            builder.append(gd.transformAndColourReplace(colour, matrixInv));
            builder.append(StringHelper.getLineDelimiter());
            type = 0;
        }
        switch (type) {
        case 0:
            String line = gd.toString();
            String[] dataSegments = line.trim().split("\\s+"); //$NON-NLS-1$
            // Check for 0 !LPE INLINE 1 Colour m1 m2 m3 m4 m5 m6 m7
            // m8 m9 m10 m11 m12 path
            if (dataSegments.length >= 18 && dataSegments[2].equals("INLINE") && //$NON-NLS-1$
                    dataSegments[3].equals("1") && //$NON-NLS-1$
                    dataSegments[1].equals("!LPE")) { //$NON-NLS-1$
                GColour c = DatParser.validateColour(dataSegments[4], 0f, 0f, 0f, 1f);
                if (c != null) {
                    Matrix theMatrix = DatParser.matrixFromStringsPrecise(dataSegments[5], dataSegments[6], dataSegments[7], dataSegments[8], dataSegments[9], dataSegments[10],
                            dataSegments[11], dataSegments[12], dataSegments[13], dataSegments[14], dataSegments[15], dataSegments[16]);
                    if (theMatrix != null) {
                        StringBuilder sb2 = new StringBuilder();
                        for (int s = 17; s < dataSegments.length - 1; s++) {
                            sb2.append(dataSegments[s]);
                            sb2.append(" "); //$NON-NLS-1$
                        }
                        sb2.append(dataSegments[dataSegments.length - 1]);
                        String realFilename = sb2.toString();
                        realFilename = realFilename.replace("\\", File.separator); //$NON-NLS-1$
                        String shortFilename = realFilename;
                        shortFilename = shortFilename.toLowerCase(Locale.ENGLISH);
                        String shortFilename2 = realFilename;
                        shortFilename = shortFilename.replace("s\\", "S" + File.separator).replace("\\", File.separator); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                        if (isValidName(Project.getProjectPath() + File.separator + "PARTS" + File.separator + shortFilename + ".tmp") || //$NON-NLS-1$ //$NON-NLS-2$ Neat trick to protect user data
                                isValidName(Project.getProjectPath() + File.separator + "parts" + File.separator + shortFilename + ".tmp") || //$NON-NLS-1$ //$NON-NLS-2$
                                isValidName(Project.getProjectPath() + File.separator + "P" + File.separator + shortFilename + ".tmp") || //$NON-NLS-1$ //$NON-NLS-2$
                                isValidName(Project.getProjectPath() + File.separator + "p" + File.separator + shortFilename + ".tmp") ||  //$NON-NLS-1$ //$NON-NLS-2$
                                (shortFilename = shortFilename2).startsWith("s" + File.separator ) && //$NON-NLS-1$
                                (isValidName(Project.getProjectPath() + File.separator + "PARTS" + File.separator + shortFilename + ".tmp") || //$NON-NLS-1$ //$NON-NLS-2$ Neat trick to protect user data
                                        isValidName(Project.getProjectPath() + File.separator + "parts" + File.separator + shortFilename + ".tmp") || //$NON-NLS-1$ //$NON-NLS-2$
                                        isValidName(Project.getProjectPath() + File.separator + "P" + File.separator + shortFilename + ".tmp") || //$NON-NLS-1$ //$NON-NLS-2$
                                        isValidName(Project.getProjectPath() + File.separator + "p" + File.separator + shortFilename + ".tmp") //$NON-NLS-1$ //$NON-NLS-2$
                                        )) {

                            if (matrixInvStack.isEmpty()) {
                                matrixProd = new Matrix(theMatrix);
                                matrixProdStack.push(matrixProd);
                                matrixInv = theMatrix.invert();
                            } else {

                                Matrix last = matrixProdStack.peek();

                                matrixProd = new Matrix(theMatrix);
                                matrixProdStack.push(matrixProd);
                                matrixInv = theMatrix.invert();

                                Matrix lastInv = last.invert();
                                Matrix original = Matrix.mul(lastInv, theMatrix);

                                String col = dataSegments[4];
                                if (col.equals(colour))
                                    col = "16"; //$NON-NLS-1$
                                 // Replace slashes with a backslash
                                builder.append("1 " + col + " " + MathHelper.matrixToStringPrecise(original) + " " + realFilename.replace('/', '\\')); //$NON-NLS-1$//$NON-NLS-2$ //$NON-NLS-3$
                                builder.append(StringHelper.getLineDelimiter());
                            }
                            colour = dataSegments[4];
                            name = shortFilename;
                            builder = new StringBuilder();
                            writeToPartFolder = true;

                            // Push stack content

                            matrixInvStack.push(matrixInv);
                            colourStack.push(colour);
                            nameStack.push(name);
                            builderStack.push(builder);
                            toFolderStack.push(true);

                            skipCompile = false;
                        } else {
                            Object[] messageArguments = {lineNumber, line};
                            MessageFormat formatter = new MessageFormat(""); //$NON-NLS-1$
                            formatter.setLocale(MyLanguage.getLocale());
                            formatter.applyPattern(I18n.E3D_INVALID_FILENAME);
                            MessageBox messageBox = new MessageBox(new Shell(), SWT.ICON_ERROR);
                            messageBox.setText(I18n.DIALOG_ERROR);
                            messageBox.setMessage(formatter.format(messageArguments));
                            messageBox.open();
                        }
                    } else {
                        Object[] messageArguments = {lineNumber, line};
                        MessageFormat formatter = new MessageFormat(""); //$NON-NLS-1$
                        formatter.setLocale(MyLanguage.getLocale());
                        formatter.applyPattern(I18n.E3D_INVALID_MATRIX);
                        MessageBox messageBox = new MessageBox(new Shell(), SWT.ICON_ERROR);
                        messageBox.setText(I18n.DIALOG_ERROR);
                        messageBox.setMessage(formatter.format(messageArguments));
                        messageBox.open();
                    }
                } else {
                    Object[] messageArguments = {lineNumber, line};
                    MessageFormat formatter = new MessageFormat(""); //$NON-NLS-1$
                    formatter.setLocale(MyLanguage.getLocale());
                    formatter.applyPattern(I18n.E3D_INVALID_COLOUR);
                    MessageBox messageBox = new MessageBox(new Shell(), SWT.ICON_ERROR);
                    messageBox.setText(I18n.DIALOG_ERROR);
                    messageBox.setMessage(formatter.format(messageArguments));
                    messageBox.open();
                }
            } else if ( // Check for INLINE_END
                    (gd.getNext() == null ||
                    dataSegments.length == 3 && dataSegments[2].equals("INLINE_END") && //$NON-NLS-1$
                    dataSegments[1].equals("!LPE")) && !toFolderStack.isEmpty() && !skipCompile) { //$NON-NLS-1$

                String targetPath = calculateTargetPath(datFile);
                DatFile df = new DatFile(targetPath);
                if (!datFile.isProjectFile()) {
                    df.setProjectFile(false);
                }

                // Remove the last line break at the end of the file
                final int indexOfLastLineBreak = builder.lastIndexOf(StringHelper.getLineDelimiter());
                if (indexOfLastLineBreak == 0 && builder.length() == StringHelper.getLineDelimiter().length() || (builder.length() - StringHelper.getLineDelimiter().length()) == indexOfLastLineBreak && indexOfLastLineBreak > 0) {
                    builder.setLength(indexOfLastLineBreak);
                }

                df.setText(builder.toString());
                TreeItem treeToSearch;
                if (name.startsWith("s" + File.separator) || name.startsWith("S" + File.separator)) { //$NON-NLS-1$ //$NON-NLS-2$
                    df.setType(DatType.SUBPART);
                    treeToSearch = Editor3DWindow.getWindow().getProjectSubparts();
                } else if (name.startsWith("48" + File.separator)) { //$NON-NLS-1$
                    df.setType(DatType.PRIMITIVE48);
                    treeToSearch = Editor3DWindow.getWindow().getProjectPrimitives48();
                } else if (name.startsWith("8" + File.separator)) { //$NON-NLS-1$
                    df.setType(DatType.PRIMITIVE8);
                    treeToSearch = Editor3DWindow.getWindow().getProjectPrimitives8();
                } else if (writeToPartFolder) {
                    df.setType(DatType.PART);
                    treeToSearch = Editor3DWindow.getWindow().getProjectParts();
                } else {
                    df.setType(DatType.PRIMITIVE);
                    treeToSearch = Editor3DWindow.getWindow().getProjectPrimitives();
                }

                // Get search criteria
                String criteria = Editor3DWindow.getWindow().getSearchCriteria();

                // Check for old data
                DatFile old = null;
                {
                    int odi = 0;
                    List<DatFile> od = (List<DatFile>) treeToSearch.getData();
                    for (DatFile odf : od) {
                        if (odf.equals(df) || targetPath.toUpperCase(MyLanguage.getLocale()).equals(odf.getOldName().toUpperCase(MyLanguage.getLocale()))) {
                            old = odf;
                            odf.disposeData();
                            Project.removeUnsavedFile(odf);
                            break;
                        }
                        odi++;
                    }
                    if (odi < od.size()) od.remove(odi);
                    od.add(df);
                }

                DatFile dfe = Project.getFileToEdit();
                if (dfe.equals(df)) {
                    Project.setFileToEdit(df);
                }

                df.getVertexManager().setModifiedNoSync();

                Project.addUnsavedFile(df);

                df.parseForData(false);

                df.setText(df.getText());

                // Update open 3D and text editors
                if (old != null) {
                    for (EditorTextWindow w : Project.getOpenTextWindows()) {
                        for (CTabItem t : w.getTabFolder().getItems()) {
                            if (old.equals(((CompositeTab) t).getState().getFileNameObj())) {
                                ((CompositeTab) t).getState().setFileNameObj(df);
                            }
                        }
                    }
                    List<OpenGLRenderer> renders = Editor3DWindow.renders;
                    for (OpenGLRenderer renderer : renders) {
                        Composite3D c3d = renderer.getC3D();
                        if (!c3d.isDatFileLockedOnDisplay() && old.equals(c3d.getLockableDatFileReference())) {
                            c3d.setLockableDatFileReference(df);
                        }
                    }
                }

                // Restore search
                Editor3DWindow.getWindow().search(criteria);

                Editor3DWindow.getWindow().cleanupClosedData();

                // Pop stack content

                colourStack.pop();
                nameStack.pop();
                builderStack.pop();
                matrixInvStack.pop();
                matrixProdStack.pop();
                toFolderStack.pop();

                if (!colourStack.isEmpty()) {
                    colour = colourStack.peek();
                    name = nameStack.peek();
                    builder = builderStack.peek();
                    matrixInv = matrixInvStack.peek();
                    matrixProd = matrixProdStack.peek();
                    writeToPartFolder = toFolderStack.peek();
                }

                skipCompile = matrixInvStack.isEmpty();

                df.updateLastModified();

            } else if (!skipCompile) {
                if ((dataSegments.length > 2 && dataSegments[1].equals("!LDRAW_ORG")) && (dataSegments[2].equals("Primitive") || dataSegments[2].equals("48_Primitive") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                            || dataSegments[2].equals("Unofficial_Primitive") || dataSegments[2].equals("Unofficial_48_Primitive"))) { //$NON-NLS-1$ //$NON-NLS-2$
                    toFolderStack.pop();
                    toFolderStack.push(false);
                    writeToPartFolder = false;
                }
                builder.append(gd.transformAndColourReplace(colour, matrixInv));
                builder.append(StringHelper.getLineDelimiter());
            }
            break;
        case 1, 2, 3, 4, 5, 6 /* BFC */, 8 /* CSG */:
            builder.append(gd.transformAndColourReplace(colour, matrixInv));
            builder.append(StringHelper.getLineDelimiter());
            break;
        default:
            break;
        }

    }

    private static String calculateTargetPath(final DatFile df) {
        File fileParentFolder = new File(df.getOldName()).getParentFile();
        String parentFolder = new File(df.getOldName()).getParent();
        parentFolder = trimParentFolder(parentFolder);
        String targetPath;
        if (Boolean.TRUE.equals(toFolderStack.peek())) {
            String partsFolderUppercase = Project.getProjectPath() + File.separator + "PARTS"; //$NON-NLS-1$
            String partsFolderLowercase = Project.getProjectPath() + File.separator + "parts"; //$NON-NLS-1$
            File filePartsFolderUppercase = new File(partsFolderUppercase);
            if (!df.isProjectFile() && fileParentFolder.exists()) {
                targetPath = parentFolder + File.separator + name;
            } else if (filePartsFolderUppercase.exists())
                targetPath = partsFolderUppercase + File.separator + name;
            else
                targetPath = partsFolderLowercase + File.separator + name;
        } else {
            String folder = Project.getProjectPath() + File.separator + "P"; //$NON-NLS-1$
            String folder2 = Project.getProjectPath() + File.separator + "p"; //$NON-NLS-1$
            File file = new File(folder);
            if (!df.isProjectFile() && fileParentFolder.exists()) {
                targetPath = parentFolder + File.separator + name;
            } else if (file.exists())
                targetPath = folder + File.separator + name;
            else
                targetPath = folder2 + File.separator + name;
        }
        return targetPath;
    }

    private static String trimParentFolder(String parentFolder) {
        parentFolder = trimParentFolder(parentFolder, File.separator + "S");  //$NON-NLS-1$
        parentFolder = trimParentFolder(parentFolder, File.separator + "s");  //$NON-NLS-1$
        parentFolder = trimParentFolder(parentFolder, File.separator + "8");  //$NON-NLS-1$
        parentFolder = trimParentFolder(parentFolder, File.separator + "48");  //$NON-NLS-1$
        return parentFolder;
    }

    private static String trimParentFolder(String parentFolder, String suffix) {
        if (parentFolder.endsWith(suffix)) {
            parentFolder = parentFolder.substring(0, parentFolder.length() - suffix.length());
        }
        return parentFolder;
    }

    private static boolean isValidName(String text) {
        try {
            File file = new File(text);
            if (file.getParentFile() != null && (!file.getParentFile().exists() || !file.getParentFile().isDirectory())) {
                return false;
            }

            return file.createNewFile() && Files.deleteIfExists(file.toPath());
        } catch (Exception ex) {
            return false;
        }
    }
}
