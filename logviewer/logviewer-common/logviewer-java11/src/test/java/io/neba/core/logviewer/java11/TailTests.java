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

import org.eclipse.jetty.websocket.api.RemoteEndpoint;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import static java.lang.Thread.sleep;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.isA;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Base class for logviewer tests (Java 11 / Jetty 9). Mirrors io.neba.core.logviewer.java8.TailTests.
 */
@RunWith(MockitoJUnitRunner.class)
public abstract class TailTests {

    Collection<File> availableLogFiles;

    private File testLogfileDirectory;
    private StringBuilder receivedText;
    Answer<?> recordText;

    @Mock
    private RemoteEndpoint remote;

    @Before
    public final void setUp() throws Exception {
        URL testLogfileUrl = getClass().getResource("/io/neba/core/logviewer/testlogfiles/");
        this.testLogfileDirectory = new File(testLogfileUrl.getFile());
        this.receivedText = new StringBuilder(4096);

        this.recordText = invocation -> {
            ByteBuffer buffer = invocation.getArgument(0);
            byte[] contents = new byte[buffer.limit()];
            buffer.get(contents, 0, contents.length);
            receivedText.append(new String(contents, StandardCharsets.UTF_8));
            return null;
        };

        doAnswer(recordText).when(remote).sendBytes(any());
    }

    public File getTestLogfileDirectory() {
        return testLogfileDirectory;
    }

    public StringBuilder getReceivedText() {
        return receivedText;
    }

    public RemoteEndpoint getRemote() {
        return remote;
    }

    public Object assertSendTextContains(String text) {
        return assertThat(normalizeLineBreaks(getReceivedText().toString()))
                .contains(normalizeLineBreaks(text));
    }

    public String pathOf(String relativePath) {
        return new File(getTestLogfileDirectory(), relativePath).getAbsolutePath();
    }

    public void verifyNoTextWasSent() throws IOException {
        verify(getRemote(), never()).sendBytes(any());
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
            // Continue.
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
     * Executes the callback as soon as the mocked remote receives bytes.
     * Allows reacting when TailRunner is picking up data from a log file.
     */
    public void uponWriteToRemoteDo(Callable<?> c) throws Exception {
        doAnswer(invocation -> {
            recordText.answer(invocation);
            c.call();
            return null;
        }).when(getRemote()).sendBytes(isA(ByteBuffer.class));
    }

    public static String normalizeLineBreaks(String s) {
        return s.replaceAll("[\r\n]+", "\n");
    }
}
