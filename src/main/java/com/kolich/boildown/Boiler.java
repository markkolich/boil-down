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

import com.kolich.boildown.strategeries.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

import static com.google.common.base.Preconditions.checkNotNull;

public final class Boiler implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(Boiler.class);

    public enum CompressionMethod {
        COMPRESS, DECOMPRESS
    }

    public enum Strategery {
        ZLIB, LZF, SNAPPY
    }

    private final Socket client_;

    private final CompressionMethod method_;

    private final Strategery strategery_;

    private final String forwardHost_;
    private final int forwardPort_;

    private final int bufferSize_;

    @ParametersAreNonnullByDefault
    public Boiler(final Socket client,
                  final CompressionMethod method,
                  final Strategery strategery,
                  final String forwardHost,
                  final int forwardPort,
                  final int bufferSize) {
        client_ = checkNotNull(client, "Client socket cannot be null.");
        method_ = checkNotNull(method, "Compression method cannot be null.");
        strategery_ = checkNotNull(strategery, "Strategery cannot be null.");
        forwardHost_ = checkNotNull(forwardHost, "Forwarding host cannot be null.");
        forwardPort_ = forwardPort;
        bufferSize_ = bufferSize;
    }

    @Override
    public final void run() {
        try (final InputStream clientIn = client_.getInputStream();
             final OutputStream clientOut = client_.getOutputStream();

             final Socket forward = new Socket(forwardHost_, forwardPort_);
             final InputStream forwardIn = forward.getInputStream();
             final OutputStream forwardOut = forward.getOutputStream()) {

            final Compressor compressor;
            final Decompressor decompressor;

            if (CompressionMethod.COMPRESS.equals(method_)) {
                // Compress...
                compressor = new Compressor(clientIn, boil(forwardOut), bufferSize_);
                decompressor = new Decompressor(boil(forwardIn), clientOut, bufferSize_);
            } else {
                // Decompress...
                compressor = new Compressor(forwardIn, boil(clientOut), bufferSize_);
                decompressor = new Decompressor(boil(clientIn), forwardOut, bufferSize_);
            }

            // Start the boilers.
            compressor.start();
            decompressor.start();

            // Join on the boilers (block until finished).
            compressor.join();
            decompressor.join();
        } catch (Exception e) {
            log.error("Exception in boiler.", e);
        }
    }

    @Nullable
    private final InputStream boil(final InputStream is) throws IOException {
        InputStream boiled = null;
        switch (strategery_) {
            case ZLIB:
                boiled = BoiledZLIBInputStream.getInstance(is);
                break;
            case LZF:
                boiled = BoiledLZFInputStream.getInstance(is);
                break;
            case SNAPPY:
                boiled = BoiledSnappyFramedInputStream.getInstance(is);
                break;
        }
        return boiled;
    }

    @Nullable
    private final OutputStream boil(final OutputStream os) throws IOException {
        OutputStream boiled = null;
        switch (strategery_) {
            case ZLIB:
                boiled = BoiledZLIBOutputStream.getInstance(os, bufferSize_);
                break;
            case LZF:
                boiled = BoiledLZFOutputStream.getInstance(os);
                break;
            case SNAPPY:
                boiled = BoiledSnappyFramedOutputStream.getInstance(os);
                break;
        }
        return boiled;
    }

}
