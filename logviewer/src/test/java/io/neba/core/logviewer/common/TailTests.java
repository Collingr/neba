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

import org.junit.Before;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URL;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import static java.lang.Thread.sleep;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Base class for TailRunner tests.
 */
@RunWith(MockitoJUnitRunner.class)
public abstract class TailTests {

    private File testLogfileDirectory;
    private RecordingLogOutput recordingOutput;

    @Before
    public final void setUp() throws Exception {
        URL testLogfileUrl = getClass().getResource("/io/neba/core/logviewer/testlogfiles/");
        this.testLogfileDirectory = new File(testLogfileUrl.getFile());
        this.recordingOutput = new RecordingLogOutput();
    }

    public File getTestLogfileDirectory() {
        return testLogfileDirectory;
    }

    public RecordingLogOutput getRecordingOutput() {
        return recordingOutput;
    }

    public StringBuilder getReceivedText() {
        return recordingOutput.getReceivedBytes();
    }

    public Object assertSendTextContains(String text) {
        return assertThat(normalizeLineBreaks(getReceivedText().toString()))
                .contains(normalizeLineBreaks(text));
    }

    public String pathOf(String relativePath) {
        return new File(getTestLogfileDirectory(), relativePath).getAbsolutePath();
    }

    public void assertNoTextWasSent() {
        assertThat(getReceivedText().length()).isZero();
    }

    public void assertErrorMessageIsSent(String message) {
        assertThat(recordingOutput.getReceivedStrings()).contains(message);
    }

    public void write(File file, String line) throws IOException {
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(line);
        }
    }

    public void sleepUpTo(long amount, TimeUnit unit) {
        Thread.yield();
        try {
            sleep(unit.toMillis(amount));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public void eventually(Runnable assertion) throws InterruptedException {
        long max = TimeUnit.SECONDS.toMillis(10);
        long waited = 0;
        long interval = 100;
        AssertionError last = null;
        while (waited < max) {
            try {
                assertion.run();
                return;
            } catch (AssertionError e) {
                last = e;
                sleep(interval);
                waited += interval;
            }
        }
        throw new AssertionError("Unable to satisfy within 10 seconds", last);
    }

    /**
     * LogOutput that invokes a callback when sendBytes is called.
     * Used to trigger file rotation during tail/follow.
     */
    public static LogOutput createLogOutputWithCallback(RecordingLogOutput recorder, Callable<?> onSendBytes) {
        return new LogOutput() {
            @Override
            public void sendBytes(java.nio.ByteBuffer buffer) throws IOException {
                recorder.sendBytes(buffer);
                try {
                    onSendBytes.call();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public void sendString(String message) throws IOException {
                recorder.sendString(message);
            }
        };
    }

    public static String normalizeLineBreaks(String s) {
        return s.replaceAll("[\r\n]+", "\n");
    }
}
