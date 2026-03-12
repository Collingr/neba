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

package io.neba.core.logviewer;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;
import java.util.Collection;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static io.neba.core.util.ZipFileUtil.toZipFileEntryName;
import static org.apache.commons.io.FileUtils.listFiles;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Olaf Otto
 */
@RunWith(MockitoJUnitRunner.class)
public class LogfileViewerConsolePluginTest {
    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;
    @Mock
    private ServletOutputStream outputStream;
    @Mock
    private ServletConfig config;
    @Mock
    private LogFiles logFiles;
    @Mock
    private LogviewerEnabled logviewerEnabled;

    private File testLogfileDirectory;
    private StringWriter internalWriter;
    private String responseText;
    private ByteArrayOutputStream internalOutputStream;
    private ZipInputStream zippedFiles;
    private Collection<File> availableLogFiles;

    @InjectMocks
    private LogfileViewerConsolePlugin testee;

    @Before
    public void setUp() throws Exception {
        this.internalWriter = new StringWriter();
        this.internalOutputStream = new ByteArrayOutputStream(8192);
        PrintWriter writer = new PrintWriter(this.internalWriter);

        URL testLogfileUrl = getClass().getResource("/io/neba/core/logviewer/testlogfiles/");
        this.testLogfileDirectory = new File(testLogfileUrl.getFile());
        this.availableLogFiles = listFiles(this.testLogfileDirectory, null, true);

        when(this.request.getServletPath())
                .thenReturn("/system/console");

        when(this.request.getServerName())
                .thenReturn("servername");

        when(this.response.getWriter())
                .thenReturn(writer);

        when(this.response.getOutputStream())
                .thenReturn(this.outputStream);

        Answer<Object> writeIntToByteArrayOutputStream = invocation -> {
            internalOutputStream.write((Integer) invocation.getArguments()[0]);
            return null;
        };

        Answer<Object> writeBytesToByteArrayOutputStream = invocation -> {
            byte[] b = (byte[]) invocation.getArguments()[0];
            int off = (Integer) invocation.getArguments()[1];
            int len = (Integer) invocation.getArguments()[2];
            internalOutputStream.write(b, off, len);
            return null;
        };

        doAnswer(writeIntToByteArrayOutputStream)
                .when(this.outputStream)
                .write(anyInt());

        doAnswer(writeBytesToByteArrayOutputStream)
                .when(this.outputStream)
                .write(isA(byte[].class), anyInt(), anyInt());

        when(this.logFiles.resolveLogFiles()).thenReturn(availableLogFiles);
    }

    @Test
    public void testGetResources() {
        URL resource = this.testee.getResource("/logviewer/static/testresource.txt");
        assertThat(resource).isNotNull();
    }

    @Test
    public void testRenderContentContainsDropdownValuesForTestLogfiles() throws Exception {
        renderContent();
        assertResponseContains("value=\"" + pathOf("logs/error.log") + "\"");
        assertResponseContains("value=\"" + pathOf("logs/error.log.1") + "\"");
        assertResponseContains("value=\"" + pathOf("logs/error.log.2020-01-01") + "\"");
        assertResponseContains("value=\"" + pathOf("logs/crx/error.log") + "\"");
        assertResponseContains("value=\"" + pathOf("logs/crx/error.log.0") + "\"");
        assertResponseContains("value=\"" + pathOf("logs/crx/error.log.2020-11-23") + "\"");
    }

    @Test
    public void testRenderContentsContainsDropdownWithSelectedOptionWhenFileParameterIsPresent() throws IOException {
        withFileRequestParameter(pathOf("logs/error.log"));
        renderContent();
        assertResponseContains("value=\"" + pathOf("logs/error.log") + "\" selected");
    }

    @Test
    public void testDownloadAllLogFilesAsZip() throws Exception {
        getAllLogfilesAsZip();
        verifyLogFilesAreSendAs("logfiles-servername.zip");
        for (File file : this.availableLogFiles) {
            assertNextZipEntryIs(toZipFileEntryName(file));
        }
    }

    @Test
    public void testDownloadCurrentLogfileAsZip() throws Exception {
        getLogfileAsZip(pathOf("logs/error.log"));
        verifyLogFilesAreSendAs("logfiles-servername-error.log.zip");
        assertZipResponseHasOneFile();
        assertNextZipEntryIs(toZipFileEntryName(toFile("logs/error.log")));
    }

    @Test
    public void testServerTimeRetrieval() throws ServletException, IOException {
        withRequestPath("/system/console/logviewer/serverTime");
        doGet();
        assertResponseMatches("\\{\"time\": \"[0-9]{1,2}.[0-9]{1,2}.[0-9]{4} [0-9]{1,2}:[0-9]{1,2}:[0-9]{1,2}.[0-9]+ \\(UTC \\+ [0-9]+\\)\"\\}");
    }

    private void verifyLogFilesAreSendAs(String filename) {
        verify(this.response).setHeader(eq("Content-Disposition"), eq("attachment;filename=" + filename));
    }

    private void assertNextZipEntryIs(String expected) throws IOException {
        ZipEntry nextEntry = this.zippedFiles.getNextEntry();
        assertThat(nextEntry).isNotNull();
        assertThat(nextEntry.getName()).isEqualTo(expected);
    }

    private void getAllLogfilesAsZip() throws ServletException, IOException {
        withRequestPath("/system/console/logviewer/download");
        doGet();
        this.zippedFiles = new ZipInputStream(new ByteArrayInputStream(this.internalOutputStream.toByteArray()));
    }

    private void getLogfileAsZip(String filePath) throws ServletException, IOException {
        withRequestPath("/system/console/logviewer/download");
        withFileRequestParameter(filePath);
        doGet();
        this.zippedFiles = new ZipInputStream(new ByteArrayInputStream(this.internalOutputStream.toByteArray()));
    }

    private void doGet() throws ServletException, IOException {
        this.testee.doGet(this.request, this.response);
        this.responseText = this.internalWriter.toString();
    }

    private void renderContent() throws IOException {
        this.testee.renderContent(this.request, this.response);
        this.responseText = this.internalWriter.getBuffer().toString();
    }

    private void withRequestPath(String requestPath) {
        when(this.request.getRequestURI()).thenReturn(requestPath);
    }

    private void withFileRequestParameter(String value) {
        doReturn(value).when(this.request).getParameter("file");
    }

    private void assertResponseContains(String expected) {
        assertThat(this.responseText).contains(expected);
    }

    private void assertResponseMatches(String regex) {
        assertThat(this.responseText).matches(regex);
    }

    private void assertZipResponseHasOneFile() throws IOException {
        assertThat(this.zippedFiles.available()).isEqualTo(1);
    }

    private String pathOf(String relativePath) {
        return toFile(relativePath).getAbsolutePath();
    }

    private File toFile(String relativePath) {
        return new File(this.testLogfileDirectory, relativePath);
    }
}
