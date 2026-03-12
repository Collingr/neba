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

package io.neba.core.logviewer;

import org.apache.commons.lang3.time.FastDateFormat;
import org.apache.felix.webconsole.AbstractWebConsolePlugin;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URL;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static io.neba.core.util.ZipFileUtil.toZipFileEntryName;
import static java.lang.System.currentTimeMillis;
import static org.apache.commons.io.IOUtils.closeQuietly;
import static org.apache.commons.io.IOUtils.copy;
import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.apache.commons.lang3.StringUtils.startsWith;
import static org.apache.commons.lang3.StringUtils.substringAfter;
import static org.apache.commons.lang3.StringUtils.substringAfterLast;
import static org.osgi.framework.Constants.SERVICE_VENDOR;

/**
 * A web console plugin for tailing and downloading the Sling log files placed within the sling log directory as configured in the
 * Apache Sling Logging Configuration.
 *
 * @author Olaf Otto
 */
@Component(
        service = Servlet.class,
        property = {
                "felix.webconsole.label=" + LogfileViewerConsolePlugin.LABEL,
                "service.description=Provides a Felix console plugin for monitoring and downloading logfiles.",
                SERVICE_VENDOR + "=neba.io"
        }
)
public class LogfileViewerConsolePlugin extends AbstractWebConsolePlugin {
    static final String LABEL = "logviewer";

    private static final long serialVersionUID = 5963934292569659695L;
    private static final String RESOURCES_ROOT = "/META-INF/consoleplugin/logviewer";
    private static final FastDateFormat DATETIME_FORMAT = FastDateFormat.getInstance("dd.MM.yyyy HH:mm:ss.S", TimeZone.getDefault());

    @Reference
    private LogFiles logFiles;

    @Reference
    private LogviewerEnabled logviewerEnabled;

    @SuppressWarnings("unused")
    public String getCategory() {
        return "NEBA";
    }

    /**
     * This method follows a felix naming convention and is automatically used
     * by felix to retrieve resources for this plugin, e.g. when retrieving script resources.
     *
     * @param path must not be <code>null</code>.
     * @return the corresponding resource, or <code>null</code>.
     */
    public URL getResource(String path) {
        URL url = null;
        String internalPath = substringAfter(path, "/" + getLabel());
        if (startsWith(internalPath, "/static/")) {
            url = getClass().getResource(RESOURCES_ROOT + internalPath);
        }
        return url;
    }

    @Override
    protected void renderContent(HttpServletRequest req, HttpServletResponse res) throws IOException {
        writeScriptIncludes(res);
        writeHead(req, res);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        String suffix = substringAfter(req.getRequestURI(), req.getServletPath() + "/" + getLabel());

        if ("/download".equals(suffix)) {
            download(res, req);
            return;
        }

        if ("/serverTime".equals(suffix)) {
            serverTime(res);
            return;
        }

        super.doGet(req, res);
    }

    /**
     * Prints the current server time and UTC offset, formatted using the servers time zone.
     */
    private void serverTime(HttpServletResponse res) throws IOException {
        final long now = currentTimeMillis();
        // Including current DST, if any.
        long utcOffsetInHours = TimeUnit.MILLISECONDS.toHours(TimeZone.getDefault().getOffset(now));
        res.setContentType("application/json");
        res.setCharacterEncoding("UTF-8");
        res.setHeader("Cache-Control", "no-store");
        res.getWriter().write('{');
        res.getWriter().write("\"time\": \"" + DATETIME_FORMAT.format(currentTimeMillis()) + " (UTC " + (utcOffsetInHours < 0 ? "-" : "+") + " " + utcOffsetInHours + ")\"");
        res.getWriter().write('}');
    }

    private void writeHead(HttpServletRequest req, HttpServletResponse res) throws IOException {
        String requestedFile = req.getParameter("file");
        StringBuilder options = new StringBuilder(1024);
        this.logFiles.resolveLogFiles().forEach(file ->
                options.append("<option value=\"").append(file.getAbsolutePath()).append("\" ")
                        .append(file.getAbsolutePath().equals(requestedFile) ? "selected " : "")
                        .append("title=\"").append(file.getAbsolutePath()).append("\">")
                        .append(file.getParentFile().getName()).append('/').append(file.getName())
                        .append("</option>"));
        writeHeadFromTemplate(res, options.toString());
        writeBodyFromTemplate(res);
    }

    /**
     * Streams the contents of the log directory as a zip file.
     */
    private void download(HttpServletResponse res, HttpServletRequest req) throws IOException {
        final String selectedLogfile = req.getParameter("file");
        final String filenameSuffix = isEmpty(selectedLogfile) ? "" : "-" + substringAfterLast(selectedLogfile, File.separator);

        res.setContentType("application/zip");
        res.setHeader("Content-Disposition", "attachment;filename=logfiles-" + req.getServerName() + filenameSuffix + ".zip");
        ZipOutputStream zos = new ZipOutputStream(res.getOutputStream());
        try {
            for (File file : this.logFiles.resolveLogFiles()) {
                if (selectedLogfile != null && !file.getAbsolutePath().equals(selectedLogfile)) {
                    continue;
                }

                ZipEntry ze = new ZipEntry(toZipFileEntryName(file));
                zos.putNextEntry(ze);
                FileInputStream in = new FileInputStream(file);
                try {
                    copy(in, zos);
                    zos.closeEntry();
                } finally {
                    closeQuietly(in);
                }
            }
            zos.finish();
        } finally {
            closeQuietly(zos);
        }
    }

    private void writeHeadFromTemplate(HttpServletResponse response, Object... templateArgs) throws IOException {
        String template = readTemplate("head.html");
        response.getWriter().printf(template, templateArgs);
    }

    private void writeBodyFromTemplate(HttpServletResponse response) throws IOException {
        String template = readTemplate("body.html");
        response.getWriter().write(template);
    }

    private String readTemplate(String templateName) {
        return readTemplateFile(RESOURCES_ROOT + "/templates/" + templateName);
    }

    private void writeScriptIncludes(HttpServletResponse response) throws IOException {
        response.getWriter().write("<script src=\"" + getLabel() + "/static/chosen.jquery.min.js\"></script>");
        response.getWriter().write("<script src=\"" + getLabel() + "/static/encoding-indexes.js\"></script>");
        response.getWriter().write("<script src=\"" + getLabel() + "/static/encoding.js\"></script>");
        response.getWriter().write("<script src=\"" + getLabel() + "/static/script.js\"></script>");
    }

    @Override
    public String getTitle() {
        return "View logfiles";
    }

    @Override
    public String getLabel() {
        return LABEL;
    }
}
