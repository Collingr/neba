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
import io.neba.core.logviewer.common.Tail;
import io.neba.core.logviewer.common.TailRunner;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.AsyncContext;
import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static java.lang.Math.round;
import static org.apache.commons.lang3.math.NumberUtils.toFloat;

/**
 * SSE-based log tail servlet registered via OSGi Http Whiteboard.
 * <p>
 * Query params: file (path), amount (MB, e.g. 0.1), mode (tail|follow)
 */
@Component(
        service = Servlet.class,
        property = {
                "osgi.http.whiteboard.servlet.pattern=/neba/logviewer/tail",
                "osgi.http.whiteboard.servlet.asyncSupported=true"
        }
)
public class SSETailServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final Logger logger = LoggerFactory.getLogger(SSETailServlet.class);

    private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "logviewer-tail");
        t.setDaemon(true);
        return t;
    });

    @Reference
    private LogFiles logFiles;

    @Reference
    private LogviewerEnabled logviewerEnabled;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String fileParam = req.getParameter("file");
        String amountParam = req.getParameter("amount");
        String modeParam = req.getParameter("mode");

        if (fileParam == null || fileParam.isEmpty()) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing parameter: file");
            return;
        }
        if (amountParam == null || amountParam.isEmpty()) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing parameter: amount");
            return;
        }

        float amountMb = toFloat(amountParam, 0.1f);
        if (amountMb <= 0) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid amount, must be > 0");
            return;
        }
        long bytesToTail = round(amountMb * 1024L * 1024L);

        Tail.Mode mode = "follow".equalsIgnoreCase(modeParam) ? Tail.Mode.FOLLOW : Tail.Mode.TAIL;

        File file = resolveLogFile(fileParam);
        if (file == null) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Log file not found or not accessible: " + fileParam);
            return;
        }

        resp.setContentType("text/event-stream");
        resp.setCharacterEncoding("UTF-8");
        resp.setHeader("Cache-Control", "no-cache");
        resp.setHeader("Connection", "keep-alive");

        AsyncContext asyncContext = req.startAsync(req, resp);
        asyncContext.setTimeout(0);

        executor.execute(() -> {
            try {
                HttpServletResponse response = (HttpServletResponse) asyncContext.getResponse();
                OutputStreamLogOutput output = new OutputStreamLogOutput(response.getOutputStream());
                TailRunner tailRunner = new TailRunner(output, file, bytesToTail, mode);
                tailRunner.run();
            } catch (Exception e) {
                logger.debug("Tail stream ended for {}: {}", file.getAbsolutePath(), e.getMessage());
            } finally {
                asyncContext.complete();
            }
        });
    }

    private File resolveLogFile(String path) throws IOException {
        return logFiles.resolveLogFiles().stream()
                .filter(f -> f.getAbsolutePath().equals(path))
                .findFirst()
                .orElse(null);
    }
}
