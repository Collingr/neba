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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Test double for LogOutput that records all sent bytes and strings.
 */
public class RecordingLogOutput implements LogOutput {

    private final StringBuilder receivedBytes = new StringBuilder(4096);
    private final List<String> receivedStrings = new ArrayList<>();

    @Override
    public void sendBytes(ByteBuffer buffer) throws IOException {
        byte[] contents = new byte[buffer.remaining()];
        buffer.get(contents);
        receivedBytes.append(new String(contents, StandardCharsets.UTF_8));
    }

    @Override
    public void sendString(String message) throws IOException {
        receivedStrings.add(message);
    }

    public StringBuilder getReceivedBytes() {
        return receivedBytes;
    }

    public List<String> getReceivedStrings() {
        return receivedStrings;
    }

    public String getReceivedText() {
        return receivedBytes.toString();
    }
}
