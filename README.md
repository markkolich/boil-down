# boildown

http://mark.koli.ch/introducing-boildown

Compress and decompress arbitrary network streams.

Boildown listens on a local port, compresses (or decompresses) incoming traffic, and forwards the result to its destination.  It's like SSH port-forwarding, but the bidirectional network traffic flowing through Boildown is automatically compressed or decompressed, depending on how it's configured.  In essence, Boildown provides a compressed "pipe" connecting two nodes on a network.

Boildown is entirely protocol agnostic &mdash; it knows nothing about the protocol of the data flowing through it, and works transparently with any protocol that can be expressed over TCP/IP.  The most common being HTTP (port 80), HTTPS (port 443), and SSH (port 22).

Boildown currently supports the following framed or "block" codecs:

* [ZLIB](https://www.ietf.org/rfc/rfc1950.txt)
* [LZF](https://github.com/ning/compress/wiki/LZFFormat) &mdash; provided by [ning/compress](https://github.com/ning/compress)
* [Google Snappy](http://google.github.io/snappy/) &mdash; provided by [xerial/snappy-java](https://github.com/xerial/snappy-java)

## Why?

From a remote location, I SSH home quite regularly and port-forward to several services behind NAT on my home network: SSH, remote desktop, web-cams, etc.  I was curious to see if I could write something general that compresses traffic over a socket in an attempt to improve the overall "remote experience".

And, I just wanted an excuse to play with LZF and Snappy.

## Usage

There's two sides (or "modes") to Boildown:

* Compressor &mdash; listens on a local port, compresses outgoing traffic, and forwards the compressed data to another host.
* Decompressor &mdash; listens on a local port, decompresses incoming traffic, and forwards the original (uncompressed) result to its destination.

Here's how you'd create a compressed pipe using Boildown for an SSH session between `localhost:10022` and `remote:22`:

```
+--------- [localhost] ---------+                               +----------- [remote] ------------+
| --compress 10022:remote:10022 | <---- (compressed pipe) ----> | --decompress 10022:localhost:22 |
+-------------------------------+                               +---------------------------------+
```

A Boildown compressor listens at `localhost:10022` and forwards compressed traffic to the decompressor listening at `remote:10022`.  Any bytes received by the decompressor at `remote:10022` are decompressed and forwarded to the SSH server daemon listening locally on `localhost:22`.  Of course, traffic flowing the other way, `remote:22` back to `localhost:10022`, is compressed and decompressed in the same way.

Hence, a bidirectional, compressed network pipe.

### On `localhost`

Start a compressor on `localhost:10022`, forwarding compressed traffic to `remote:10022`:

```
java -jar boildown-0.1-SNAPSHOT-runnable.jar --compress 10022:remote:10022 --zlib
```

### On `remote`

Start a decompressor on `remote:10022`, forwarding decompressed traffic to `localhost:22`:

```
java -jar boildown-0.1-SNAPSHOT-runnable.jar --decompress 10022:localhost:22 --zlib
```

### Connect the dots

On `localhost`, start a new SSH session, funneling traffic through the Boildown managed compressed pipe:

```
ssh -p 10022 localhost
```

### Compression codecs

Specify `--zlib`, `--snappy`, or `--lzf` on the command line to use any of the 3 supported compression codecs.

Note, both sides of the pipe need to be using the same codec (obviously).

### Thread pool

The compressor and decompressor implementations run within threads.  The size of the internal thread pool used by Boildown can be controlled with the `--poolSize` argument.

By default, if `--poolSize` is omitted, the internal thread pool is sized to match the number of available cores. 

## Building

Boildown is built and packaged using Maven.

To build, clone the repository:

```
#~> git clone https://github.com/markkolich/boildown.git
```

Run `mvn package` to compile and build a runnable JAR:

```
#~> cd boildown
#~> mvn package
```

The resulting runnable JAR will be placed in the `dist` directory.

## License

Copyright (c) 2016 <a href="http://mark.koli.ch">Mark S. Kolich</a>

All code in this project is freely available for use and redistribution under the MIT License.

See <a href="https://github.com/markkolich/boildown/blob/master/LICENSE">LICENSE</a> for details.
