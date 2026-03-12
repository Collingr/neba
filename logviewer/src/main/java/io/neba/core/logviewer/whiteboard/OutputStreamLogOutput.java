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
package io.neba.core.logviewer.whiteboard;

import io.neba.core.logviewer.common.LogOutput;

import javax.servlet.ServletOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * {@link LogOutput} adapter for SSE streaming via {@link ServletOutputStream}.
 * Writes log data as Server-Sent Events format.
 */
class OutputStreamLogOutput implements LogOutput {

    private static final byte[] SSE_DATA_PREFIX = "data: ".getBytes(StandardCharsets.UTF_8);
    private static final byte[] SSE_EOL = "\n\n".getBytes(StandardCharsets.UTF_8);
    private static final byte[] SSE_EVENT_INFO = "event: info\n".getBytes(StandardCharsets.UTF_8);

    private final ServletOutputStream outputStream;

    OutputStreamLogOutput(ServletOutputStream outputStream) {
        if (outputStream == null) {
            throw new IllegalArgumentException("outputStream must not be null");
        }
        this.outputStream = outputStream;
    }

    @Override
    public void sendBytes(ByteBuffer buffer) throws IOException {
        if (buffer == null || !buffer.hasRemaining()) {
            return;
        }
        outputStream.write(SSE_DATA_PREFIX);
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        outputStream.write(bytes);
        outputStream.write(SSE_EOL);
        outputStream.flush();
    }

    @Override
    public void sendString(String message) throws IOException {
        if (message == null) {
            return;
        }
        outputStream.write(SSE_EVENT_INFO);
        outputStream.write(SSE_DATA_PREFIX);
        outputStream.write(message.getBytes(StandardCharsets.UTF_8));
        outputStream.write(SSE_EOL);
        outputStream.flush();
    }
}
