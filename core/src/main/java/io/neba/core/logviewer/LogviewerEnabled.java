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

/**
 * Marker service indicating the logviewer is enabled via OSGi configuration.
 * When this service is not available, the logviewer console plugin and SSE servlet do not activate.
 * <p>
 * Configure via OSGi Config Admin: create a configuration for PID {@code io.neba.logviewer}
 * and set {@code enabled=true} to enable the logviewer. Off by default.
 */
public interface LogviewerEnabled {
}
