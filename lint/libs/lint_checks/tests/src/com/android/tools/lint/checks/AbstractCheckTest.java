/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.tools.lint.checks;

import com.android.tools.lint.PositionXmlParser;
import com.android.tools.lint.api.DetectorRegistry;
import com.android.tools.lint.api.IDomParser;
import com.android.tools.lint.api.Lint;
import com.android.tools.lint.api.ToolContext;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Position;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

import junit.framework.TestCase;

/** Common utility methods for the various lint check tests */
abstract class AbstractCheckTest extends TestCase {
    protected abstract Detector getDetector();

    private class CustomDetectorRegistry extends DetectorRegistry {
        @Override
        public List<? extends Detector> getDetectors() {
            List<Detector> detectors = new ArrayList<Detector>(1);
            detectors.add(AbstractCheckTest.this.getDetector());
            return detectors;
        }
    }

    protected String lint(String... relativePaths) throws Exception {
        List<File> files = new ArrayList<File>();
        for (String relativePath : relativePaths) {
            File file = getTestfile(relativePath);
            assertNotNull(file);
            files.add(file);
        }

        return checkLint(files);
    }

    protected String checkLint(List<File> files) throws Exception {
        mOutput = new StringBuilder();
        TestToolContext toolContext = new TestToolContext();
        Lint analyzer = new Lint(new CustomDetectorRegistry(), toolContext,
                Scope.PROJECT);
        analyzer.analyze(files);

        List<String> errors = toolContext.getErrors();
        Collections.sort(errors);
        for (String error : errors) {
            if (mOutput.length() > 0) {
                mOutput.append('\n');
            }
            mOutput.append(error);
        }

        if (mOutput.length() == 0) {
            mOutput.append("No warnings.");
        }

        return mOutput.toString();
    }

    /** Run lint on the given files when constructed as a separate project */
    protected String lintProject(String... relativePaths) throws Exception {
        assertFalse("getTargetDir must be overridden to make a unique directory",
                getTargetDir().equals(getTempDir()));

        File projectDir = getTargetDir();

        List<File> files = new ArrayList<File>();
        for (String relativePath : relativePaths) {
            File file = getTestfile(relativePath);
            assertNotNull(file);
            files.add(file);
        }

        return checkLint(Collections.singletonList(projectDir));
    }

    private StringBuilder mOutput = null;

    private static File sTempDir = null;
    private File getTempDir() {
        if (sTempDir == null) {
            File base = new File(System.getProperty("java.io.tmpdir"));     //$NON-NLS-1$
            String os = System.getProperty("os.name");          //$NON-NLS-1$
            if (os.startsWith("Mac OS")) {                      //$NON-NLS-1$
                base = new File("/tmp");
            }
            Calendar c = Calendar.getInstance();
            String name = String.format("lintTests_%1$tF_%1$tT", c).replace(':', '-'); //$NON-NLS-1$
            File tmpDir = new File(base, name);
            if (!tmpDir.exists() && tmpDir.mkdir()) {
                sTempDir = tmpDir;
            } else {
                sTempDir = base;
            }
        }

        return sTempDir;
    }

    protected File getTargetDir() {
        return new File(getTempDir(), getClass().getSimpleName());
    }

    private File makeTestFile(String name, String relative,
            String contents) throws IOException {
        File dir = getTargetDir();
        if (relative != null) {
            dir = new File(dir, relative);
            if (!dir.exists()) {
                boolean mkdir = dir.mkdirs();
                assertTrue(dir.getPath(), mkdir);
            }
        } else if (!dir.exists()) {
            boolean mkdir = dir.mkdirs();
            assertTrue(dir.getPath(), mkdir);
        }
        File tempFile = new File(dir, name);
        if (tempFile.exists()) {
            tempFile.delete();
        }

        Writer writer = new BufferedWriter(new FileWriter(tempFile));
        writer.write(contents);
        writer.close();

        return tempFile;
    }

    private File getTestfile(String relativePath) throws IOException {
        // Support replacing filenames and paths with a => syntax, e.g.
        //   dir/file.txt=>dir2/dir3/file2.java
        // will read dir/file.txt from the test data and write it into the target
        // directory as dir2/dir3/file2.java

        String targetPath = relativePath;
        int replaceIndex = relativePath.indexOf("=>"); //$NON-NLS-1$
        if (replaceIndex != -1) {
            // foo=>bar
            targetPath = relativePath.substring(replaceIndex + "=>".length());
            relativePath = relativePath.substring(0, replaceIndex);
        }

        String path = "data" + File.separator + relativePath; //$NON-NLS-1$
        InputStream stream =
            AbstractCheckTest.class.getResourceAsStream(path);
        assertNotNull(relativePath + " does not exist", stream);
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
        String xml = readFile(reader);
        assertNotNull(xml);
        assertTrue(xml.length() > 0);
        int index = targetPath.lastIndexOf('/');
        String relative = null;
        String name = targetPath;
        if (index != -1) {
            name = targetPath.substring(index + 1);
            relative = targetPath.substring(0, index);
        }

        return makeTestFile(name, relative, xml);
    }

    private static String readFile(Reader reader) throws IOException {
        try {
            StringBuilder sb = new StringBuilder();
            while (true) {
                int c = reader.read();
                if (c == -1) {
                    return sb.toString();
                } else {
                    sb.append((char)c);
                }
            }
        } finally {
            reader.close();
        }
    }


    private class TestToolContext extends ToolContext {
        private List<String> mErrors = new ArrayList<String>();

        public List<String> getErrors() {
            return mErrors;
        }

        @Override
        public void report(Context context, Issue issue, Location location, String message,
                Object data) {
            StringBuilder sb = new StringBuilder();

            if (location != null && location.getFile() != null) {
                sb.append(location.getFile().getName());
                sb.append(':');

                Position startPosition = location.getStart();
                if (startPosition != null) {
                    int line = startPosition.getLine();
                    if (line >= 0) {
                        // line is 0-based, should display 1-based
                        sb.append(Integer.toString(line + 1));
                        sb.append(':');
                    }
                }

                sb.append(' ');
            }

            Severity severity = getSeverity(issue);
            sb.append(severity.getDescription());
            sb.append(": ");

            sb.append(message);
            mErrors.add(sb.toString());
        }

        @Override
        public void log(Throwable exception, String format, Object... args) {
            exception.printStackTrace();
            fail(exception.toString());
        }

        @Override
        public IDomParser getParser() {
            return new PositionXmlParser();
        }

        @Override
        public boolean isEnabled(Issue issue) {
            for (Issue detectorIssue : getDetector().getIssues()) {
                if (issue == detectorIssue) {
                    return true;
                }
            }

            return false;
        }

        @Override
        public boolean isSuppressed(Context context, Issue issue, Location range, String message,
                Severity severity, Object data) {
            return false;
        }

        @Override
        public Severity getSeverity(Issue issue) {
            return issue.getDefaultSeverity();
        }

        @Override
        public String readFile(File file) {
            try {
                return AbstractCheckTest.readFile(new FileReader(file));
            } catch (Throwable e) {
                fail(e.toString());
            }
            return null;
        }
    }
}
