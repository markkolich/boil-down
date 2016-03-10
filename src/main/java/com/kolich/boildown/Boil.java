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

import com.google.common.base.Splitter;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.apache.commons.io.IOUtils;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.kohsuke.args4j.ParserProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

public final class Boil {

    private static final Logger log = LoggerFactory.getLogger(Boil.class);

    private static final Splitter colonSplitter = Splitter.on(":").omitEmptyStrings().limit(3);

    @Option(name="--compress", usage="Listen on port X and forward compressed traffic to Y:Z [X]:[Y]:[Z]")
    private String compress_ = null;

    @Option(name="--decompress", usage="Listen on port X and forward decompressed traffic to Y:Z [X]:[Y]:[Z]")
    private String decompress_ = null;

    @Option(name="--bufferSize", usage="Internal read/write buffer size, in bytes, used for compression " +
        "and decompression. Defaults to 4K.")
    private Integer bufferSize_ = 4096;

    @Option(name="--poolSize", usage="Maximum number of boilers (worker threads) to allow. Defaults to # " +
        "of available cores.")
    private Integer poolSize_ = Runtime.getRuntime().availableProcessors();

    @Option(name="--zlib", usage="Use ZLIB compression.")
    private Boolean zlib_ = false;

    @Option(name="--lzf", usage="Use LZF compression.")
    private Boolean lzf_ = false;

    @Option(name="--snappy", usage="Use Snappy compression.")
    private Boolean snappy_ = false;

    public static void main(String... args) throws Exception {
        new Boil().doMain(args);
    }

    private final void doMain(String... args) throws Exception {
        final ParserProperties properties = ParserProperties.defaults()
            .withUsageWidth(80)
            .withShowDefaults(true);
        final CmdLineParser parser = new CmdLineParser(this, properties);
        try {
            parser.parseArgument(args);
            final int enabledBoilers = (zlib_ ? 1:0) + (lzf_ ? 1:0) + (snappy_ ? 1:0);
            if (compress_ == null && decompress_ == null) {
                throw new IllegalArgumentException("Missing '--compress' or '--decompress' " +
                    "argument.");
            } else if (compress_ != null && decompress_ != null) {
                throw new IllegalArgumentException("Must specify only one of '--compress' or " +
                    "'--decompress' arguments.");
            } else if (enabledBoilers > 1) {
                throw new IllegalArgumentException("Can only specify one of --zlib, --lzf, or --snappy.");
            } else if (enabledBoilers == 0) {
                throw new IllegalArgumentException("Must specify at least one of --zlib, --lzf, or --snappy.");
            }
            run(); // Go!
        } catch (Exception e) {
            log.debug("Failed to start; see usage.", e);
            parser.printUsage(System.err);
        }
    }

    private final void run() throws Exception {
        final Boiler.CompressionMethod method;
        final List<String> arguments;
        if (compress_ != null) {
            arguments = colonSplitter.splitToList(compress_);
            method = Boiler.CompressionMethod.COMPRESS;
        } else {
            arguments = colonSplitter.splitToList(decompress_);
            method = Boiler.CompressionMethod.DECOMPRESS;
        }

        if (arguments.size() != 3) {
            throw new IllegalArgumentException("Forwarder must be in the format of [port]:[host]:[port]");
        }

        // Parse the arguments.
        final int listenPort = Integer.parseInt(arguments.get(0));
        final String forwardHost = arguments.get(1);
        final int forwardPort = Integer.parseInt(arguments.get(2));

        final Boiler.Strategery strategery = getStrategery();

        final ThreadFactoryBuilder factoryBuilder = new ThreadFactoryBuilder()
            .setDaemon(true)
            .setNameFormat("boiler-%d (" + listenPort + ":" + forwardHost + ":" + forwardPort + ")");

        final ThreadPoolExecutor threadPool = (ThreadPoolExecutor)Executors.newFixedThreadPool(poolSize_,
            factoryBuilder.build());

        try (final ServerSocket listener = new ServerSocket(listenPort)) {
            // Run loop!
            while (true) {
                // Blocks, waiting for new connections.
                final Socket client = listener.accept();
                if (threadPool.getActiveCount() >= poolSize_) {
                    // All boilers busy, forcibly hang up.
                    IOUtils.closeQuietly(client);
                } else {
                    // Submit the boiler to the pool, only if there's space to safely do so.
                    threadPool.submit(new Boiler(client, method, strategery, forwardHost, forwardPort, bufferSize_));
                }
            }
        } catch (Exception e) {
            log.error("Exception in main run-loop.", e);
        } finally {
            threadPool.shutdown();
        }
    }

    @Nullable
    private final Boiler.Strategery getStrategery() {
        if (zlib_) {
            return Boiler.Strategery.ZLIB;
        } else if (lzf_) {
            return Boiler.Strategery.LZF;
        } else if (snappy_) {
            return Boiler.Strategery.SNAPPY;
        }
        return null;
    }

}
