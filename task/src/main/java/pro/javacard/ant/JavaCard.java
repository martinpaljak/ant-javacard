/*
 * Copyright (c) 2015-2024 Martin Paljak
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
package pro.javacard.ant;

import org.apache.tools.ant.Project;
import org.apache.tools.ant.Task;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

// <javacard jckit="${env.JCKIT}">...</javacard>
// This is a wrapper task that can contain one or more <cap> subtasks for building capfiles.
public final class JavaCard extends Task {
    static List<Path> temporary = new ArrayList<>();

    private String master_jckit_path = null;
    private Vector<JCCap> packages = new Vector<>();

    public void setJCKit(String msg) {
        master_jckit_path = msg;
    }

    public JCCap createCap() {
        JCCap pkg = new JCCap(master_jckit_path);
        packages.add(pkg);
        return pkg;
    }

    @Override
    public void execute() {
        Thread cleanup = new Thread(() -> {
            log("Ctrl-C, cleaning up", Project.MSG_INFO);
            cleanTemp();
        });
        Runtime.getRuntime().addShutdownHook(cleanup);
        try {
            for (JCCap p : packages) {
                p.execute();
            }
        } finally {
            Runtime.getRuntime().removeShutdownHook(cleanup);
        }
    }

    static void cleanTemp() {
        // Do not clean temporary files if manually set temporary path is set. This is useful for debugging.
        if (System.getenv("ANT_JAVACARD_TMP") != null)
            return;

        // Clean temporary files.
        for (Path f : temporary) {
            if (Files.exists(f)) {
                Misc.rmminusrf(f);
            }
        }
    }
}
