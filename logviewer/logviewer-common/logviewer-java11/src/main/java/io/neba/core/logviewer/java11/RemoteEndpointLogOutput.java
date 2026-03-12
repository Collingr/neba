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
package io.neba.core.logviewer.java11;

import io.neba.core.logviewer.common.LogOutput;
import org.eclipse.jetty.websocket.api.RemoteEndpoint;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * {@link LogOutput} adapter for Jetty 9 {@link RemoteEndpoint}.
 */
class RemoteEndpointLogOutput implements LogOutput {

    private final RemoteEndpoint remoteEndpoint;

    RemoteEndpointLogOutput(RemoteEndpoint remoteEndpoint) {
        this.remoteEndpoint = remoteEndpoint;
    }

    @Override
    public void sendBytes(ByteBuffer buffer) throws IOException {
        this.remoteEndpoint.sendBytes(buffer);
    }

    @Override
    public void sendString(String message) throws IOException {
        this.remoteEndpoint.sendString(message);
    }
}
