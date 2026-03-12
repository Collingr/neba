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

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.concurrent.ExecutorService;

import static java.io.File.createTempFile;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static java.util.concurrent.Executors.newSingleThreadExecutor;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for TailRunner (file tail logic).
 */
@RunWith(MockitoJUnitRunner.class)
public class TailRunnerTest extends TailTests {

    private final ExecutorService executorService = newSingleThreadExecutor();

    private TailRunner testee;

    @After
    public void tearDown() {
        if (this.testee != null) {
            this.testee.stop();
        }
        this.executorService.shutdownNow();
    }

    @Test
    public void testHandlingOfFileNotFoundExceptionDuringLogFileRotation() throws Exception {
        File logFile = createTempFile("tailsocket-test-", ".log", getTestLogfileDirectory().getParentFile());

        LogOutput output = TailTests.createLogOutputWithCallback(getRecordingOutput(), () -> {
            rotate(logFile);
            return null;
        });
        followAsynchronously(logFile, output);

        write(logFile, "first line");

        eventually(() -> assertSendTextContains("first line"));

        sleepUpTo(100, MILLISECONDS);

        createFile(logFile.getAbsolutePath());

        eventually(() -> assertErrorMessageIsSent("file rotated"));
    }

    @Test
    public void testHandlingOfFileRotation() throws Exception {
        File logFile = createTempFile("tailsocket-test-", ".log", getTestLogfileDirectory().getParentFile());

        LogOutput output = TailTests.createLogOutputWithCallback(getRecordingOutput(), () -> {
            rotate(logFile);
            createFile(logFile.getAbsolutePath());
            return null;
        });
        followAsynchronously(logFile, output);

        write(logFile, "first line");

        eventually(() -> assertSendTextContains("first line"));
        eventually(() -> assertErrorMessageIsSent("file rotated"));
    }

    @Test
    public void testHandlingOfRemovedLogFile() throws Exception {
        File logFile = createTempFile("tailsocket-test-", ".log", getTestLogfileDirectory().getParentFile());

        LogOutput output = TailTests.createLogOutputWithCallback(getRecordingOutput(), () -> {
            rotate(logFile);
            return null;
        });
        followAsynchronously(logFile, output);

        write(logFile, "first line");

        eventually(() -> assertSendTextContains("first line"));
        eventually(() -> assertErrorMessageIsSent("file not found"));
    }

    @Test
    public void testHandlingOfIoExceptionWhenSendingLineToClient() throws Exception {
        LogOutput throwingOutput = new LogOutput() {
            @Override
            public void sendBytes(java.nio.ByteBuffer buffer) throws IOException {
                throw new IOException("THIS IS AN EXPECTED TEST EXCEPTION");
            }

            @Override
            public void sendString(String message) throws IOException {
                // no-op
            }
        };
        tailSynchronously("logs/error.log", throwingOutput);
    }

    @Test
    public void testPreservationOfWhiteSpaces() throws Exception {
        tailAsynchronously("logs/error-withwhitespaces.log");

        eventually(() ->
                assertSendTextContains(
                        "06.09.2013 15:03:50.719 *ERROR* error message with stacktrace\r\n" +
                                "  at org.apache.sling.jcr.resource.internal.JcrResourceResolverFactoryImpl.getDefaultWorkspaceName(JcrResourceResolverFactoryImpl.java:398)\r\n" +
                                "        at org.apache.sling.jcr.resource.internal.JcrResourceResolver.getResource(JcrResourceResolver.java:817)"));
    }

    @Test
    public void testTailErrorLogIsFullyRead() throws Exception {
        tailAsynchronously("logs/error.log");

        eventually(() -> {
            assertSendTextStartsWith("-- test logs/error.log first line --");
            assertSendTextEndsWith("-- test logs/error.log last line --");
        });
    }

    @Test(expected = IllegalArgumentException.class)
    public void testHandlingOfNullFileArgument() {
        new TailRunner(getRecordingOutput(), null, 1000, Tail.Mode.TAIL);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testHandlingOfNullOutputArgument() {
        new TailRunner(null, new File(getTestLogfileDirectory(), "logs/error.log"), 1000, Tail.Mode.TAIL);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testHandlingOfNullModeArgument() {
        new TailRunner(getRecordingOutput(), new File(getTestLogfileDirectory(), "logs/error.log"), 1000, null);
    }

    @Test
    public void testFollowLogfile() throws Exception {
        File emptyLog = createTempFile("tailsocket-test-", ".log", getTestLogfileDirectory().getParentFile());

        followAsynchronously(emptyLog);

        sleepUpTo(1, SECONDS);

        assertNoTextWasSent();

        write(emptyLog, "test line");

        eventually(() -> assertSendTextContains("test line"));
    }

    @Test
    public void testStopFollowLogfile() throws Exception {
        File emptyLog = createTempFile("tailsocket-test-", ".log", getTestLogfileDirectory().getParentFile());

        followAsynchronously(emptyLog);

        sleepUpTo(1, SECONDS);

        assertNoTextWasSent();

        testee.stop();

        write(emptyLog, "test line");

        sleepUpTo(1, SECONDS);

        assertNoTextWasSent();
    }

    private void assertSendTextEndsWith(String s) {
        assertThat(getReceivedText().toString()).endsWith(s);
    }

    private void assertSendTextStartsWith(String s) {
        assertThat(getReceivedText().toString()).startsWith(s);
    }

    private void tailSynchronously(String fileName) {
        tailSynchronously(fileName, getRecordingOutput());
    }

    private void tailSynchronously(String fileName, LogOutput output) {
        this.testee = new TailRunner(output, new File(getTestLogfileDirectory(), fileName), 1024L * 1024L, Tail.Mode.TAIL);
        this.testee.run();
    }

    private void tailAsynchronously(String fileName) {
        this.testee = new TailRunner(getRecordingOutput(), new File(getTestLogfileDirectory(), fileName), 1024L * 1024L, Tail.Mode.TAIL);
        this.executorService.execute(this.testee);
    }

    private void followAsynchronously(File logFile) {
        followAsynchronously(logFile, getRecordingOutput());
    }

    private void followAsynchronously(File logFile, LogOutput output) {
        this.testee = new TailRunner(output, logFile, 1024, Tail.Mode.FOLLOW);
        this.executorService.execute(this.testee);
    }

    private java.nio.file.Path rotate(File file) throws IOException {
        return Files.move(file.toPath(), new File(file.getAbsolutePath() + ".rotated").toPath(), REPLACE_EXISTING);
    }

    private void createFile(String logFilePath) throws IOException {
        assertThat(new File(logFilePath).createNewFile())
                .describedAs("Re-creating the logfile was successful")
                .isTrue();
    }
}
