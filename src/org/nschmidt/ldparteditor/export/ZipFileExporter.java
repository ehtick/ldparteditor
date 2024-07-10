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
package org.nschmidt.ldparteditor.export;

import java.io.IOException;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Shell;
import org.nschmidt.ldparteditor.data.DatFile;
import org.nschmidt.ldparteditor.i18n.I18n;
import org.nschmidt.ldparteditor.logger.NLogger;

public enum ZipFileExporter {
    INSTANCE;

    public static void export(String path, DatFile df) {
        // FIXME take all files needed by the current file (recursive) and make a zip file with the correct folder structure.
        // Only if the files are not referenced from official or unofficial library.
        // TEXMAP png images should be considered, too.

        try {
            if (true) throw new IOException();
        } catch (IOException ioe) {
            NLogger.error(ZipFileExporter.class, ioe);
            MessageBox messageBox = new MessageBox(new Shell(), SWT.ICON_ERROR);
            messageBox.setText(I18n.DIALOG_ERROR);
            messageBox.setMessage(I18n.E3D_ZIP_ERROR);
            messageBox.open();
            return;
        }

        MessageBox messageBox = new MessageBox(new Shell(), SWT.ICON_INFORMATION);
        messageBox.setText(I18n.DIALOG_INFO);
        messageBox.setMessage(I18n.E3D_ZIP_CREATED);
        messageBox.open();
    }
}
