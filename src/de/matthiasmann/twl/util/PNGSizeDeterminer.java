/*
 * Copyright (c) 2008-2010, Matthias Mann
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *     * Redistributions of source code must retain the above copyright notice,
 *       this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     * Neither the name of Matthias Mann nor the names of its contributors may
 *       be used to endorse or promote products derived from this software
 *       without specific prior written permission.
 *     * Redistributions in source or binary form must keep the original package
 *       and class name.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package de.matthiasmann.twl.util;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.zip.CRC32;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * A PNGSizeDeterminer written by Nils Schmidt. Based on PNGDecoder from Matthias Mann.
 */
public class PNGSizeDeterminer {

    private static final String UNSUPPORTED_BIT_DEPTH = "Unsupported bit depth: "; //$NON-NLS-1$

    public enum Format {
        ALPHA(1, true), LUMINANCE(1, false), LUMINANCE_ALPHA(2, true), RGB(3, false), RGBA(4, true), BGRA(4, true), ABGR(4, true);

        final int numComponents;
        final boolean hasAlpha;

        private Format(int numComponents, boolean hasAlpha) {
            this.numComponents = numComponents;
            this.hasAlpha = hasAlpha;
        }

        public int getNumComponents() {
            return numComponents;
        }

        public boolean isHasAlpha() {
            return hasAlpha;
        }
    }

    private static final byte[] SIGNATURE = { (byte) 137, 80, 78, 71, 13, 10, 26, 10 };

    private static final int IHDR = 0x49484452;
    private static final int PLTE = 0x504C5445;
    /** tRNS */
    private static final int TRNS = 0x74524E53;
    private static final int IDAT = 0x49444154;

    private static final byte COLOR_GREYSCALE = 0;
    private static final byte COLOR_TRUECOLOR = 2;
    private static final byte COLOR_INDEXED = 3;
    private static final byte COLOR_GREYALPHA = 4;
    private static final byte COLOR_TRUEALPHA = 6;

    private final InputStream input;
    private final CRC32 crc;
    private final byte[] buffer;

    private int chunkLength;
    private int chunkType;
    private int chunkRemaining;

    private int width;
    private int height;
    private int bitdepth;
    private int colorType;
    private int bytesPerPixel;
    private byte[] palette;
    private byte[] paletteA;
    private byte[] transPixel;
    
    private int size = 0;

    public PNGSizeDeterminer(InputStream input) throws IOException {
        this.input = input;
        this.crc = new CRC32();
        this.buffer = new byte[4096];

        readFully(buffer, SIGNATURE.length);
        if (!checkSignature(buffer)) {
            throw new IOException("Not a valid PNG file"); //$NON-NLS-1$
        }

        openChunk(IHDR);
        readIHDR();
        closeChunk();

        while (true) {
            openChunk();
            switch (chunkType) {
            case IDAT:
                if (colorType == COLOR_INDEXED && palette == null) {
                    throw new IOException("Missing PLTE chunk"); //$NON-NLS-1$
                }

                return;
            case PLTE:
                readPLTE();
                break;
            case TRNS:
                readtRNS();
                break;
            default:
                break;
            }

            closeChunk();
        }
    }

    /**
     * Decodes the image and returns the size in bytes
     * @throws IOException
     *             if a read or data error occurred
     * @throws IllegalArgumentException
     *             if the start position of a line falls outside the buffer
     * @throws UnsupportedOperationException
     *             if the image can't be decoded into the desired format
     */
    public int size() throws IOException {
        final int lineSize = (width * bitdepth + 7) / 8 * bytesPerPixel;
        final int lsp1 = lineSize + 1;
        byte[] curLine = new byte[lsp1];
        byte[] prevLine = new byte[lsp1];
        byte[] palLine = bitdepth < 8 ? new byte[width + 1] : null;
        final Inflater inflater = new Inflater();
        try {
            for (int y = 0; y < height; y++) {
                final int cll = curLine.length;
                readChunkUnzip(inflater, curLine, 0, cll);
                unfilter(curLine, prevLine);
                switch (colorType) {
                case COLOR_TRUECOLOR:
                    break;
                case COLOR_TRUEALPHA:
                    break;
                case COLOR_GREYSCALE:
                    break;
                case COLOR_GREYALPHA:
                    break;
                case COLOR_INDEXED:
                    switch (bitdepth) {
                    case 8:
                        palLine = curLine;
                        break;
                    case 4:
                        expand4(curLine, palLine);
                        break;
                    case 2:
                        expand2(curLine, palLine);
                        break;
                    case 1:
                        expand1(curLine, palLine);
                        break;
                    default:
                        throw new UnsupportedOperationException("Unsupported bitdepth for this image"); //$NON-NLS-1$
                    }
                    break;
                default:
                    throw new UnsupportedOperationException("Not yet implemented"); //$NON-NLS-1$
                }

                byte[] tmp = curLine;
                curLine = prevLine;
                prevLine = tmp;
            }
        } finally {
            inflater.end();
        }
        
        // 4 Bytes of CRC at the end
        closeChunk();
        return this.size;
    }

    private void expand4(byte[] src, byte[] dst) {
        for (int i = 1, n = dst.length; i < n; i += 2) {
            int val = src[1 + (i >> 1)] & 255;
            if ((n - i) == 1) {
                dst[i] = (byte) (val >> 4);
            } else {
                dst[i + 1] = (byte) (val & 15);
                dst[i] = (byte) (val >> 4);
            }
        }
    }

    private void expand2(byte[] src, byte[] dst) {
        for (int i = 1, n = dst.length; i < n; i += 4) {
            int val = src[1 + (i >> 2)] & 255;
            switch (n - i) {
            default:
                dst[i + 3] = (byte) (val & 3);
            case 3:
                dst[i + 2] = (byte) (val >> 2 & 3);
            case 2:
                dst[i + 1] = (byte) (val >> 4 & 3);
            case 1:
                dst[i] = (byte) (val >> 6);
            }
        }
    }

    private void expand1(byte[] src, byte[] dst) {
        for (int i = 1, n = dst.length; i < n; i += 8) {
            int val = src[1 + (i >> 3)] & 255;
            switch (n - i) {
            default:
                dst[i + 7] = (byte) (val & 1);
            case 7:
                dst[i + 6] = (byte) (val >> 1 & 1);
            case 6:
                dst[i + 5] = (byte) (val >> 2 & 1);
            case 5:
                dst[i + 4] = (byte) (val >> 3 & 1);
            case 4:
                dst[i + 3] = (byte) (val >> 4 & 1);
            case 3:
                dst[i + 2] = (byte) (val >> 5 & 1);
            case 2:
                dst[i + 1] = (byte) (val >> 6 & 1);
            case 1:
                dst[i] = (byte) (val >> 7);
            }
        }
    }

    private void unfilter(byte[] curLine, byte[] prevLine) throws IOException {
        switch (curLine[0]) {
        case 0: // none
            break;
        case 1:
            unfilterSub(curLine);
            break;
        case 2:
            unfilterUp(curLine, prevLine);
            break;
        case 3:
            unfilterAverage(curLine, prevLine);
            break;
        case 4:
            unfilterPaeth(curLine, prevLine);
            break;
        default:
            throw new IOException("invalide filter type in scanline: " + curLine[0]); //$NON-NLS-1$
        }
    }

    private void unfilterSub(byte[] curLine) {
        final int bpp = this.bytesPerPixel;
        for (int i = bpp + 1, n = curLine.length; i < n; ++i) {
            curLine[i] += curLine[i - bpp];
        }
    }

    private void unfilterUp(byte[] curLine, byte[] prevLine) {
        for (int i = 1, n = curLine.length; i < n; ++i) {
            curLine[i] += prevLine[i];
        }
    }

    private void unfilterAverage(byte[] curLine, byte[] prevLine) {
        final int bpp = this.bytesPerPixel;

        int i;
        for (i = 1; i <= bpp; ++i) {
            curLine[i] += (byte) ((prevLine[i] & 0xFF) >>> 1);
        }
        for (int n = curLine.length; i < n; ++i) {
            curLine[i] += (byte) ((prevLine[i] & 0xFF) + (curLine[i - bpp] & 0xFF) >>> 1);
        }
    }

    private void unfilterPaeth(byte[] curLine, byte[] prevLine) {
        final int bpp = this.bytesPerPixel;

        int i;
        for (i = 1; i <= bpp; ++i) {
            curLine[i] += prevLine[i];
        }
        for (int n = curLine.length; i < n; ++i) {
            int a = curLine[i - bpp] & 255;
            int b = prevLine[i] & 255;
            int c = prevLine[i - bpp] & 255;
            int p = a + b - c;
            int pa = p - a;
            if (pa < 0)
                pa = -pa;
            int pb = p - b;
            if (pb < 0)
                pb = -pb;
            int pc = p - c;
            if (pc < 0)
                pc = -pc;
            if (pa <= pb && pa <= pc)
                c = a;
            else if (pb <= pc)
                c = b;
            curLine[i] += (byte) c;
        }
    }

    private void readIHDR() throws IOException {
        checkChunkLength(13);
        readChunk(buffer, 13);
        width = readInt(buffer, 0);
        height = readInt(buffer, 4);
        bitdepth = buffer[8] & 255;
        colorType = buffer[9] & 255;

        switch (colorType) {
        case COLOR_GREYSCALE:
            if (bitdepth != 8) {
                throw new IOException(UNSUPPORTED_BIT_DEPTH + bitdepth);
            }
            bytesPerPixel = 1;
            break;
        case COLOR_GREYALPHA:
            if (bitdepth != 8) {
                throw new IOException(UNSUPPORTED_BIT_DEPTH + bitdepth);
            }
            bytesPerPixel = 2;
            break;
        case COLOR_TRUECOLOR:
            if (bitdepth != 8) {
                throw new IOException(UNSUPPORTED_BIT_DEPTH + bitdepth);
            }
            bytesPerPixel = 3;
            break;
        case COLOR_TRUEALPHA:
            if (bitdepth != 8) {
                throw new IOException(UNSUPPORTED_BIT_DEPTH + bitdepth);
            }
            bytesPerPixel = 4;
            break;
        case COLOR_INDEXED:
            switch (bitdepth) {
            case 1, 2, 4 ,8:
                bytesPerPixel = 1;
                break;
            default:
                throw new IOException(UNSUPPORTED_BIT_DEPTH + bitdepth);
            }
            break;
        default:
            throw new IOException("unsupported color format: " + colorType); //$NON-NLS-1$
        }

        if (buffer[10] != 0) {
            throw new IOException("unsupported compression method"); //$NON-NLS-1$
        }
        if (buffer[11] != 0) {
            throw new IOException("unsupported filtering method"); //$NON-NLS-1$
        }
        if (buffer[12] != 0) {
            throw new IOException("unsupported interlace method"); //$NON-NLS-1$
        }
    }

    private void readPLTE() throws IOException {
        int paletteEntries = chunkLength / 3;
        if (paletteEntries < 1 || paletteEntries > 256 || chunkLength % 3 != 0) {
            throw new IOException("PLTE chunk has wrong length"); //$NON-NLS-1$
        }
        palette = new byte[paletteEntries * 3];
        readChunk(palette, palette.length);
    }

    private void readtRNS() throws IOException {
        switch (colorType) {
        case COLOR_GREYSCALE:
            checkChunkLength(2);
            transPixel = new byte[2];
            readChunk(transPixel, 2);
            break;
        case COLOR_TRUECOLOR:
            checkChunkLength(6);
            transPixel = new byte[6];
            readChunk(transPixel, 6);
            break;
        case COLOR_INDEXED:
            if (palette == null) {
                throw new IOException("tRNS chunk without PLTE chunk"); //$NON-NLS-1$
            }
            paletteA = new byte[palette.length / 3];
            Arrays.fill(paletteA, (byte) 0xFF);
            readChunk(paletteA, paletteA.length);
            break;
        default:
            // just ignore it
        }
    }

    private void closeChunk() throws IOException {
        if (chunkRemaining > 0) {
            // just skip the rest and the CRC
            skip(chunkRemaining + 4l);
        } else {
            readFully(buffer, 4);
            int expectedCrc = readInt(buffer, 0);
            int computedCrc = (int) crc.getValue();
            if (computedCrc != expectedCrc) {
                throw new IOException("Invalid CRC"); //$NON-NLS-1$
            }
        }
        chunkRemaining = 0;
        chunkLength = 0;
        chunkType = 0;
    }

    private void openChunk() throws IOException {
        readFully(buffer, 8);
        chunkLength = readInt(buffer, 0);
        chunkType = readInt(buffer, 4);
        chunkRemaining = chunkLength;
        crc.reset();
        crc.update(buffer, 4, 4); // only chunkType
    }

    private void openChunk(int expected) throws IOException {
        openChunk();
        if (chunkType != expected) {
            throw new IOException("Expected chunk: " + Integer.toHexString(expected)); //$NON-NLS-1$
        }
    }

    private void checkChunkLength(int expected) throws IOException {
        if (chunkLength != expected) {
            throw new IOException("Chunk has wrong size"); //$NON-NLS-1$
        }
    }

    private int readChunk(byte[] buffer, int length) throws IOException {
        if (length > chunkRemaining) {
            length = chunkRemaining;
        }
        readFully(buffer, length);
        crc.update(buffer, 0, length);
        chunkRemaining -= length;
        return length;
    }

    private void refillInflater(Inflater inflater) throws IOException {
        while (chunkRemaining == 0) {
            closeChunk();
            openChunk(IDAT);
        }
        int read = readChunk(buffer, buffer.length);
        inflater.setInput(buffer, 0, read);
    }

    private void readChunkUnzip(Inflater inflater, byte[] buffer, int offset, int length) throws IOException {
        assert buffer != this.buffer;
        try {
            do {
                int read = inflater.inflate(buffer, offset, length);
                if (read <= 0) {
                    if (inflater.finished()) {
                        throw new EOFException();
                    }
                    if (inflater.needsInput()) {
                        refillInflater(inflater);
                    } else {
                        throw new IOException("Can't inflate " + length + " bytes"); //$NON-NLS-1$ //$NON-NLS-2$
                    }
                } else {
                    offset += read;
                    length -= read;
                }
            } while (length > 0);
        } catch (DataFormatException ex) {
            throw (IOException) new IOException("inflate error").initCause(ex); //$NON-NLS-1$
        }
    }

    private void readFully(byte[] buffer, int length) throws IOException {
        int offset = 0;
        do {
            int read = input.read(buffer, offset, length);
            if (read < 0) {
                throw new EOFException();
            }
            offset += read;
            length -= read;
            size += read;
        } while (length > 0);
    }

    private int readInt(byte[] buffer, int offset) {
        return buffer[offset] << 24 | (buffer[offset + 1] & 255) << 16 | (buffer[offset + 2] & 255) << 8 | buffer[offset + 3] & 255;
    }

    private void skip(long amount) throws IOException {
        while (amount > 0) {
            long skipped = input.skip(amount);
            if (skipped < 0) {
                throw new EOFException();
            }
            amount -= skipped;
            size += skipped;
        }
    }

    private static boolean checkSignature(byte[] buffer) {
        for (int i = 0; i < SIGNATURE.length; i++) {
            if (buffer[i] != SIGNATURE[i]) {
                return false;
            }
        }
        return true;
    }
}