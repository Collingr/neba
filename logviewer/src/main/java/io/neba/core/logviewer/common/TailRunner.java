/*
  Copyright 2013 the original author or authors.

  Licensed under the Apache License, Version 2.0 the "License";
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
 */
package io.neba.core.logviewer.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;

import static io.neba.core.logviewer.common.Tail.Mode.TAIL;
import static java.lang.Math.max;
import static java.lang.Thread.sleep;
import static java.nio.ByteBuffer.allocate;
import static java.nio.file.Files.newByteChannel;
import static java.nio.file.StandardOpenOption.READ;

/**
 * File tail implementation. Reads from the end of a file and optionally follows changes.
 * Uses {@link LogOutput} to send data (e.g. SSE via OutputStreamLogOutput).
 */
public class TailRunner implements Runnable {

    private static final int AWAIT_FILE_ROTATION_MILLIS = 1000;
    private static final int TAIL_CHECK_INTERVAL_MILLIS = 500;

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final LogOutput output;
    private final File file;
    private final long bytesToTail;
    private final Tail.Mode mode;

    private volatile boolean stopped = false;

    public TailRunner(LogOutput output, File file, long bytesToTail, Tail.Mode mode) {
        if (output == null) {
            throw new IllegalArgumentException("constructor parameter output must not be null");
        }
        if (file == null) {
            throw new IllegalArgumentException("constructor parameter file must not be null");
        }
        if (mode == null) {
            throw new IllegalArgumentException("constructor parameter mode must not be null");
        }
        this.output = output;
        this.file = file;
        this.bytesToTail = bytesToTail;
        this.mode = mode;
    }

    @Override
    public void run() {
        SeekableByteChannel channel = null;

        try {
            channel = newByteChannel(this.file.toPath(), READ);

            long availableInByte = this.file.length();
            long startingFromInByte = max(availableInByte - this.bytesToTail, 0);

            channel.position(startingFromInByte);

            long position = startingFromInByte;
            long totalBytesRead = 0L;

            ByteBuffer readBuffer = allocate(4096);
            while (!this.stopped) {

                if (!this.file.exists()) {
                    sleep(AWAIT_FILE_ROTATION_MILLIS);
                }
                if (!this.file.exists()) {
                    this.output.sendString("file not found");
                    return;
                }

                if (position > this.file.length()) {
                    this.output.sendString("file rotated");
                    position = 0;
                    closeQuietly(channel);
                    channel = newByteChannel(this.file.toPath(), READ);
                }

                int read = channel.read(readBuffer);

                if (read == -1) {
                    if (mode == TAIL) {
                        return;
                    }
                    sleep(TAIL_CHECK_INTERVAL_MILLIS);
                    continue;
                }

                totalBytesRead += read;

                position = channel.position();
                readBuffer.flip();
                this.output.sendBytes(readBuffer);
                readBuffer.clear();

                if (mode == TAIL && totalBytesRead >= this.bytesToTail) {
                    return;
                }
            }
        } catch (IOException e) {
            this.logger.error("Unable to tail " + this.file.getAbsolutePath() + ".", e);
        } catch (InterruptedException e) {
            if (!this.stopped) {
                this.logger.error("Stopped tailing " + this.file.getAbsolutePath() + ", got interrupted.", e);
            }
        } finally {
            closeQuietly(channel);
        }
    }

    public void stop() {
        this.stopped = true;
    }

    private static void closeQuietly(SeekableByteChannel channel) {
        if (channel != null) {
            try {
                channel.close();
            } catch (IOException ignored) {
                // quiet
            }
        }
    }
}
