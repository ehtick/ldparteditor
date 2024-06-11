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
package org.nschmidt.ldparteditor.data;

import java.io.FileNotFoundException;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.nschmidt.ldparteditor.helper.LDPartEditorException;
import org.nschmidt.ldparteditor.logger.NLogger;
import org.nschmidt.ldparteditor.shell.editor3d.Editor3DWindow;
import org.nschmidt.ldparteditor.text.LDParsingException;
import org.nschmidt.ldparteditor.text.UTF8BufferedReader;
import org.nschmidt.ldparteditor.widget.TreeItem;

enum DescriptionManager {
    INSTANCE;

    private static Queue<TreeItem> workQueue = new ConcurrentLinkedQueue<>();
    private static boolean hasNoThread = true;

    static synchronized void registerDescription(TreeItem ti) {

        if (hasNoThread) {
            hasNoThread = false;
            new Thread(DescriptionManager::scanForDescriptions).start();
        }

        workQueue.offer(ti);
    }

    private static void scanForDescriptions() {
        while (Editor3DWindow.getAlive().get()) {
            try {
                final TreeItem newEntry = workQueue.poll();
                if (newEntry != null) {
                    DatFile df = (DatFile) newEntry.getData();
                    // NLogger.debug(DescriptionManager.class, "Register description for {0}", df.getOldName()); //$NON-NLS-1$
                    final StringBuilder titleSb = new StringBuilder();
                    try (UTF8BufferedReader reader = new UTF8BufferedReader(df.getOldName())) {
                        String title = reader.readLine();
                        if (title != null) {
                            title = title.trim();
                            if (title.length() > 0) {
                                titleSb.append(" -"); //$NON-NLS-1$
                                titleSb.append(title.substring(1));
                            }
                        }
                    } catch (LDParsingException | FileNotFoundException ex) {
                        NLogger.debug(DescriptionManager.class, ex);
                    }
                    String d = titleSb.toString();
                    newEntry.setText(df.getShortName() + d);
                    df.setDescription(d);
                } else {
                    Thread.sleep(100);
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new LDPartEditorException(ie);
            } catch (Exception e) {
                NLogger.debug(DescriptionManager.class, e);
            }
        }
    }
}
