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

import io.neba.core.logviewer.LogFiles;
import io.neba.core.logviewer.TailServlet;
import org.eclipse.jetty.websocket.servlet.WebSocketServlet;
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Java 11 WebSocket implementation of the log tail servlet (Jetty 9).
 * Used for AEM 6.5 (Java 11) and AEM 6.6 (Java 17/21). The NEBA delivery embeds
 * the Jetty 9.4 stack so this bundle resolves on both AEM versions.
 */
@Component(service = TailServlet.class)
public class TailServletJava11 extends WebSocketServlet implements TailServlet {
    private static final long serialVersionUID = 1326193543519605309L;

    @Reference
    private LogFiles logFiles;

    @Override
    public void configure(WebSocketServletFactory factory) {
        factory.getPolicy().setIdleTimeout(SECONDS.toMillis(30));
        factory.setCreator((servletUpgradeRequest, servletUpgradeResponse) -> new TailSocketJava11(logFiles));
    }
}
