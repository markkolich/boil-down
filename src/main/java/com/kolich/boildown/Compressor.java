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

package com.kolich.boildown;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.ParametersAreNonnullByDefault;
import java.io.InputStream;
import java.io.OutputStream;

import static com.google.common.base.Preconditions.checkNotNull;

public final class Compressor extends Thread {

    private static final Logger log = LoggerFactory.getLogger(Compressor.class);

    private final InputStream in_;
    private final OutputStream boiled_;
    private final int bufferSize_;

    @ParametersAreNonnullByDefault
    public Compressor(final InputStream in,
                      final OutputStream boiled,
                      final int bufferSize) throws Exception {
        super("boildown-compressor");
        setDaemon(true);
        in_ = checkNotNull(in, "Input stream cannot be null.");
        boiled_ = checkNotNull(boiled, "Boiled output stream cannot be null.");
        bufferSize_ = bufferSize;
    }

    @Override
    public final void run() {
        try {
            byte[] buffer = new byte[bufferSize_];
            int n = 0;
            while (-1 != (n = in_.read(buffer))) {
                boiled_.write(buffer, 0, n);
                boiled_.flush();
            }
        } catch (Exception e) {
            final String message = e.getMessage();
            // Only log an ERROR if the exception is unrelated to sockets closing.
            if (message != null && !message.contains("Socket closed")) {
                log.error("Compressor failure.", e);
            }
        } finally {
            IOUtils.closeQuietly(in_);
            IOUtils.closeQuietly(boiled_);
        }
    }

}
