/*
  Copyright 2013 the original author or authors.
  <p>
  Licensed under the Apache License, Version 2.0 the "License";
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at
  <p>
  http://www.apache.org/licenses/LICENSE-2.0
  <p>
  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
 */
package io.neba.core.logviewer.java8;

import io.neba.core.logviewer.LogFiles;
import io.neba.core.logviewer.common.TailCommand;
import io.neba.core.logviewer.common.TailRunner;
import org.eclipse.jetty.websocket.api.WebSocketAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutorService;

import static java.util.concurrent.Executors.newSingleThreadExecutor;

/**
 * Jetty 9 WebSocket adapter. Uses {@link TailCommand} and {@link TailRunner} from logviewer-common.
 */
public class TailSocket extends WebSocketAdapter {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final ExecutorService executorService = newSingleThreadExecutor();

    private final LogFiles logFiles;
    private TailRunner tailRunner;

    TailSocket(LogFiles logFiles) {
        if (logFiles == null) {
            throw new IllegalArgumentException("Method argument logFiles must not be null.");
        }
        this.logFiles = logFiles;
    }

    @Override
    public void onWebSocketClose(int statusCode, String reason) {
        stopTail();
        this.executorService.shutdownNow();
        super.onWebSocketClose(statusCode, reason);
    }

    @Override
    public void onWebSocketText(String message) {
        if (TailCommand.isPing(message)) {
            getRemote().sendStringByFuture("pong");
            return;
        }

        if (TailCommand.isStop(message)) {
            stopTail();
            return;
        }

        TailCommand.Parsed parsed = TailCommand.parse(message);
        if (parsed == null) {
            logger.warn("Unsupported command format '" + message + "', must match " + TailCommand.getPattern().pattern() + ", ignoring the command.");
            return;
        }

        try {
            File file = resolveLogFile(parsed.path);
            if (file == null) {
                return;
            }

            stopTail();
            this.tailRunner = new TailRunner(new RemoteEndpointLogOutput(getRemote()), file, parsed.bytesToTail, parsed.mode);
            this.executorService.execute(this.tailRunner);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void stopTail() {
        if (this.tailRunner != null) {
            synchronized (this.tailRunner) {
                this.tailRunner.stop();
                this.tailRunner = null;
            }
        }
    }

    private File resolveLogFile(String path) throws IOException {
        return this.logFiles.resolveLogFiles()
                .stream()
                .filter(f -> f.getAbsolutePath().equals(path))
                .findFirst()
                .orElse(null);
    }
}
