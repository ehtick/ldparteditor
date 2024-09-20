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

import java.util.Set;

import org.eclipse.swt.custom.StyledText;
import org.nschmidt.ldparteditor.data.DatFile;
import org.nschmidt.ldparteditor.data.VertexManager;
import org.nschmidt.ldparteditor.widget.TreeItem;

/**
 * Selects/inspects detected problems
 */
public enum Inspector {
    INSTANCE;

    /**
     * Selects/inspects syntax and logic errors
     *
     * @param cText
     *            the selected CompositeText
     * @param issues
     *            the selected Issues
     * @param datFile
     */
    public static void inspectTextIssues(StyledText cText, Set<TreeItem> issues, DatFile datFile) {

        if (issues.isEmpty())
            return;

        if (issues.size() == 1) {
            final TreeItem singleIssue = issues.iterator().next();
            scrollToIssue(singleIssue, cText);
        }


        final VertexManager vm = datFile.getVertexManager();

        vm.clearSelection();

        final int lc = cText.getLineCount();
        for (TreeItem ti : issues) {
            if (ti.getData() != null) {
                final int offset = (int) ti.getData();
                if (offset >= 0) {
                    final int line = cText.getLineAtOffset(offset) + 1;
                    if (line >= 1 && line <= lc) {
                        vm.addTextLineToSelection(line);
                    }
                }
            }
        }

        vm.setModifiedNoSync();
        vm.syncWithTextEditors(true);
        vm.updateUnsavedStatus();
    }

    private static void scrollToIssue(TreeItem ti, StyledText cText) {
        if (ti.getData() != null) {
            final int offset = (int) ti.getData();
            if (offset >= 0) {
                final int line = cText.getLineAtOffset(offset);
                cText.setTopIndex(line);
            }
        }
    }
}
