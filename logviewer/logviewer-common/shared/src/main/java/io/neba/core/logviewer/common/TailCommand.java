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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.neba.core.logviewer.common.Tail.Mode.FOLLOW;
import static io.neba.core.logviewer.common.Tail.Mode.TAIL;
import static java.lang.Math.round;
import static org.apache.commons.lang3.math.NumberUtils.toFloat;

/**
 * Parses tail commands: {@code tail:0.1mb:/path/to/file} or {@code follow:0.1mb:/path/to/file}.
 */
public final class TailCommand {

    private static final Pattern TAIL_COMMAND = Pattern.compile("((?<mode>tail|follow):(?<amount>([0-9]+\\.)?[0-9]+)mb:(?<path>.+))");

    private TailCommand() {
    }

    /**
     * @param message raw WebSocket message
     * @return parsed command, or null if message does not match
     */
    public static Parsed parse(String message) {
        Matcher m = TAIL_COMMAND.matcher(message);
        if (!m.matches()) {
            return null;
        }
        float fractionOfMegabyteToRead = toFloat(m.group("amount"));
        long bytesToTail = round(fractionOfMegabyteToRead * 1024L * 1024L);
        Tail.Mode mode = "tail".equals(m.group("mode")) ? TAIL : FOLLOW;
        String path = m.group("path");
        return new Parsed(path, bytesToTail, mode);
    }

    public static boolean isPing(String message) {
        return "ping".equals(message);
    }

    public static boolean isStop(String message) {
        return "stop".equals(message);
    }

    public static Pattern getPattern() {
        return TAIL_COMMAND;
    }

    public static final class Parsed {
        public final String path;
        public final long bytesToTail;
        public final Tail.Mode mode;

        Parsed(String path, long bytesToTail, Tail.Mode mode) {
            this.path = path;
            this.bytesToTail = bytesToTail;
            this.mode = mode;
        }
    }
}
