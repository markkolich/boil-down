/**
 * Copyright (c) 2016 Mark S. Kolich
 * http://mark.koli.ch
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 */

package com.kolich.boildown.strategeries;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.Deflater;

public final class BoiledZLIBOutputStream extends FilterOutputStream {
    
    /**
     * Buffer for input data.
     */
    private byte[] inBuf_ = null;

    /**
     * Buffer for compressed data to be written.
     */
    private byte[] outBuf_ = null;

    /**
     * Number of bytes in the buffer.
     */
    private int length_ = 0;

    /**
     * Deflater for compressing data.
     */
    private Deflater deflater_ = null;

    public static final OutputStream getInstance(final OutputStream os,
                                                 final int bufferSize) throws IOException {
        return new BoiledZLIBOutputStream(os, bufferSize);
    }

    public BoiledZLIBOutputStream(final OutputStream os,
                                  final int size) throws IOException {
        this(os, size, Deflater.BEST_COMPRESSION, Deflater.DEFAULT_STRATEGY);
    }

    public BoiledZLIBOutputStream(final OutputStream os,
                                  final int size,
                                  final int level,
                                  final int strategy) throws IOException {
        super(os);
        inBuf_ = new byte[size];
        outBuf_ = new byte[size + 64];
        deflater_ = new Deflater(level);
        deflater_.setStrategy(strategy);
    }

    protected void compressAndFlush() throws IOException {
        if (length_ > 0) {
            deflater_.setInput(inBuf_, 0, length_);
            deflater_.finish();
            int size = deflater_.deflate(outBuf_);

            // Write the size of the compressed data.
            out.write((size >> 24) & 0xFF);
            out.write((size >> 16) & 0xFF);
            out.write((size >>  8) & 0xFF);
            out.write((size >>  0) & 0xFF);

            // Write the size of the uncompressed data.
            out.write((length_ >> 24) & 0xFF);
            out.write((length_ >> 16) & 0xFF);
            out.write((length_ >>  8) & 0xFF);
            out.write((length_ >>  0) & 0xFF);

            out.write(outBuf_, 0, size);
            out.flush();

            length_ = 0;
            deflater_.reset();
        }
    }

    @Override
    public void write(int b) throws IOException {
        inBuf_[length_++] = (byte) b;
        if (length_ == inBuf_.length) {
            compressAndFlush();
        }
    }

    @Override
    public void write(byte[] b,
                      int offset,
                      int len) throws IOException {
        while ((length_ + len) > inBuf_.length) {
            int toCopy = inBuf_.length - length_;
            System.arraycopy(b, offset, inBuf_, length_, toCopy);
            length_ += toCopy;
            compressAndFlush();
            offset += toCopy;
            len -= toCopy;
        }
        System.arraycopy(b, offset, inBuf_, length_, len);
        length_ += len;
    }

    @Override
    public void flush() throws IOException {
        compressAndFlush();
        out.flush();
    }

    @Override
    public void close() throws IOException {
        compressAndFlush();
        out.close();
    }

}
