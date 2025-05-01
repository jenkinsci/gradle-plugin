/*
 * The MIT License
 *
 * Copyright (c) 2004-2010, InfraDNA, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package hudson.plugins.gradle;

import static hudson.plugins.gradle.TimestampPrefixDetector.TimestampPattern;

import hudson.Extension;
import hudson.MarkupText;
import hudson.console.ConsoleAnnotationDescriptor;
import hudson.console.ConsoleAnnotator;
import hudson.console.ConsoleNote;
import java.util.regex.Pattern;

/**
 * Annotates the BUILD SUCCESSFUL/FAILED line of the Ant execution.
 *
 * @author ikikko
 */
public class GradleOutcomeNote extends ConsoleNote {

    private static final Pattern BUILD_RESULT_PATTERN = Pattern.compile("^(?:" + TimestampPattern + ")?(BUILD \\S*)");

    public GradleOutcomeNote() {}

    @Override
    public ConsoleAnnotator annotate(Object context, MarkupText text, int charPos) {
        MarkupText.SubText t = text.findToken(BUILD_RESULT_PATTERN);
        if (t == null) {
            return null;
        }
        int timestampPrefix = TimestampPrefixDetector.detectTimestampPrefix(text.getText());
        String buildStatus = t.group(1);
        if (text.getText().contains("FAIL"))
            text.addMarkup(
                    timestampPrefix,
                    timestampPrefix + buildStatus.length(),
                    "<span class=gradle-outcome-failure>",
                    "</span>");
        if (text.getText().contains("SUCCESS"))
            text.addMarkup(
                    timestampPrefix,
                    timestampPrefix + buildStatus.length(),
                    "<span class=gradle-outcome-success>",
                    "</span>");
        return null;
    }

    @Extension
    public static final class DescriptorImpl extends ConsoleAnnotationDescriptor {
        public String getDisplayName() {
            return "Gradle build outcome";
        }
    }
}
