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

import io.neba.core.logviewer.LogviewerEnabled;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

/**
 * OSGi Config Admin component. Provides {@link LogviewerEnabled} only when enabled via configuration.
 * Off by default; create a configuration for PID {@value #PID} with {@code enabled=true} to enable.
 */
@Component(configurationPid = LogviewerConfig.PID, service = {})
@Designate(ocd = LogviewerConfig.Configuration.class)
public class LogviewerConfig {

    static final String PID = "io.neba.logviewer";

    private BundleContext context;
    private ServiceRegistration<LogviewerEnabled> registration;

    @Activate
    protected void activate(Configuration config, BundleContext context) {
        this.context = context;
        updateRegistration(config);
    }

    @Modified
    protected void modified(Configuration config) {
        updateRegistration(config);
    }

    @Deactivate
    protected void deactivate() {
        unregister();
    }

    private void updateRegistration(Configuration config) {
        unregister();
        if (config != null && config.enabled()) {
            registration = context.registerService(LogviewerEnabled.class, new LogviewerEnabled() {}, null);
        }
    }

    private void unregister() {
        if (registration != null) {
            registration.unregister();
            registration = null;
        }
    }

    @ObjectClassDefinition(
            name = "NEBA Logviewer",
            description = "Enable or disable the logviewer (Felix console plugin and SSE tail endpoint). Off by default."
    )
    public @interface Configuration {
        @AttributeDefinition(
                name = "Enabled",
                description = "Enable the logviewer. When disabled, the Felix console plugin and /neba/logviewer/tail endpoint are not available.",
                defaultValue = "false"
        )
        boolean enabled() default false;
    }
}
