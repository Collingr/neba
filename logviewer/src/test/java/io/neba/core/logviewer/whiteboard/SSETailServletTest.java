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

import io.neba.core.logviewer.LogFiles;
import io.neba.core.logviewer.LogviewerEnabled;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Collections;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

/**
 * Tests for SSETailServlet (parameter validation, file resolution).
 */
@RunWith(MockitoJUnitRunner.class)
public class SSETailServletTest {

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    private StubLogFiles logFiles;
    private SSETailServlet servlet;

    @Before
    public void setUp() throws Exception {
        servlet = new SSETailServlet();
        logFiles = new StubLogFiles();
        injectLogFiles(servlet, logFiles);
        injectLogviewerEnabled(servlet);
    }

    /** Stub for LogFiles since Mockito cannot mock the concrete class. */
    private static class StubLogFiles extends LogFiles {
        private Collection<File> files = Collections.emptyList();
        private IOException toThrow;

        void setFiles(Collection<File> files) {
            this.files = files;
        }

        void setThrow(IOException e) {
            this.toThrow = e;
        }

        @Override
        public Collection<File> resolveLogFiles() throws IOException {
            if (toThrow != null) {
                throw toThrow;
            }
            return files;
        }
    }

    @Test
    public void testMissingFileParameter() throws Exception {
        doReturn(null).when(request).getParameter("file");
        doReturn("0.1").when(request).getParameter("amount");

        servlet.doGet(request, response);

        verify(response).sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing parameter: file");
    }

    @Test
    public void testEmptyFileParameter() throws Exception {
        doReturn("").when(request).getParameter("file");
        doReturn("0.1").when(request).getParameter("amount");

        servlet.doGet(request, response);

        verify(response).sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing parameter: file");
    }

    @Test
    public void testMissingAmountParameter() throws Exception {
        doReturn("/path/to/error.log").when(request).getParameter("file");
        doReturn(null).when(request).getParameter("amount");

        servlet.doGet(request, response);

        verify(response).sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing parameter: amount");
    }

    @Test
    public void testInvalidAmountParameter() throws Exception {
        doReturn("/path/to/error.log").when(request).getParameter("file");
        doReturn("0").when(request).getParameter("amount");

        servlet.doGet(request, response);

        verify(response).sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid amount, must be > 0");
    }

    @Test
    public void testUnregisteredLogFile() throws Exception {
        File knownFile = new File("/known/error.log");
        logFiles.setFiles(Collections.singletonList(knownFile));
        doReturn("/unknown/path.log").when(request).getParameter("file");
        doReturn("0.1").when(request).getParameter("amount");

        servlet.doGet(request, response);

        verify(response).sendError(HttpServletResponse.SC_BAD_REQUEST, "Log file not found or not accessible: /unknown/path.log");
    }

    @Test(expected = IOException.class)
    public void testHandlingOfIoExceptionFromLogFiles() throws Exception {
        logFiles.setThrow(new IOException("THIS IS AN EXPECTED TEST EXCEPTION"));
        doReturn("/path.log").when(request).getParameter("file");
        doReturn("0.1").when(request).getParameter("amount");

        servlet.doGet(request, response);
    }

    private static void injectLogFiles(SSETailServlet servlet, LogFiles logFiles) throws Exception {
        Field field = SSETailServlet.class.getDeclaredField("logFiles");
        field.setAccessible(true);
        field.set(servlet, logFiles);
    }

    private static void injectLogviewerEnabled(SSETailServlet servlet) throws Exception {
        Field field = SSETailServlet.class.getDeclaredField("logviewerEnabled");
        field.setAccessible(true);
        field.set(servlet, new LogviewerEnabled() {});
    }
}
