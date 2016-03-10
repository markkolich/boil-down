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

import java.io.EOFException;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

public final class BoiledZLIBInputStream extends FilterInputStream {

    /**
     * Buffer of compressed data read from the stream.
     */
    private byte[] inBuf_ = null;

    /**
     * Buffer of uncompressed data.
     */
    private byte[] outBuf_ = null;

    /**
     * Offset and length of uncompressed data.
     */
    private int outOffs_ = 0;
    private int outLength_ = 0;

    /**
     * Inflater for decompressing.
     */
    private Inflater inflater_ = null;

    public static final InputStream getInstance(final InputStream is) throws IOException {
        return new BoiledZLIBInputStream(is);
    }

    private BoiledZLIBInputStream(InputStream is) throws IOException {
        super(is);
        inflater_ = new Inflater();
    }

    private void readAndDecompress() throws IOException {
        int inLength;

        // Read the length of the compressed block
        int ch1 = in.read();
        int ch2 = in.read();
        int ch3 = in.read();
        int ch4 = in.read();
        if ((ch1 | ch2 | ch3 | ch4) < 0) {
            throw new EOFException();
        }

        inLength = ((ch1 << 24) + (ch2 << 16) + (ch3 << 8) + (ch4 << 0));

        ch1 = in.read();
        ch2 = in.read();
        ch3 = in.read();
        ch4 = in.read();
        if ((ch1 | ch2 | ch3 | ch4) < 0) {
            throw new EOFException();
        }

        outLength_ = ((ch1 << 24) + (ch2 << 16) + (ch3 << 8) + (ch4 << 0));

        // Make sure we've got enough space to read the block.
        if ((inBuf_ == null) || (inLength > inBuf_.length)) {
            inBuf_ = new byte[inLength];
        }
        if ((outBuf_ == null) || (outLength_ > outBuf_.length)) {
            outBuf_ = new byte[outLength_];
        }

        // Read until we're got the entire compressed buffer.
        int inOffs = 0;
        while (inOffs < inLength) {
            int n = in.read(inBuf_, inOffs, inLength - inOffs);
            if (n == -1) {
                throw new EOFException();
            }
            inOffs += n;
        }

        inflater_.setInput(inBuf_, 0, inLength);
        try {
            inflater_.inflate(outBuf_);
        } catch(DataFormatException dfe) {
            throw new IOException("Data format exception.", dfe);
        }

        // Reset the inflater so we can re-use it for the next block.
        inflater_.reset();

        outOffs_ = 0;
    }

    @Override
    public int read() throws IOException {
        if (outOffs_ >= outLength_) {
            try {
                readAndDecompress();
            } catch(EOFException eof) {
                return -1;
            }
        }

        return outBuf_[outOffs_++] & 0xff;
    }

    @Override
    public int read(byte[] b,
                    int off,
                    int len) throws IOException {
        int count = 0;

        while (count < len) {
            if (outOffs_ >= outLength_) {
                try {
                    // If we've read at least one decompressed byte and further decompression
                    // would require blocking, return the count.
                    if ((count > 0) && (in.available() == 0)) {
                        return count;
                    } else {
                        readAndDecompress();
                    }
                } catch(EOFException eof) {
                    if (count == 0) {
                        count = -1;
                    }
                    return count;
                }
            }
            int toCopy = Math.min(outLength_ - outOffs_, len - count);
            System.arraycopy(outBuf_, outOffs_, b, off + count, toCopy);
            outOffs_ += toCopy;
            count += toCopy;
        }

        return count;
    }

    @Override
    public int available() throws IOException {
        // This isn't precise, but should be an adequate lower bound on the actual
        // amount of available data.
        return (outLength_ - outOffs_) + in.available();
    }

}
