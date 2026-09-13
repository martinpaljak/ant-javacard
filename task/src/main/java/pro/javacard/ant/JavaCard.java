// SPDX-FileCopyrightText: 2015 Martin Paljak <martin@martinpaljak.net>
// SPDX-License-Identifier: MIT

package pro.javacard.ant;

import org.apache.tools.ant.Project;
import org.apache.tools.ant.Task;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Vector;

// <javacard jckit="${env.JCKIT}">...</javacard>
// This is a wrapper task that can contain one or more <cap> subtasks for building capfiles.
public final class JavaCard extends Task {

    // The JDK versions this task is tested on
    public static final List<Integer> LTS = Collections.unmodifiableList(Arrays.asList(8, 11, 17, 21, 25));

    private String master_jckit_path = null;
    private final Vector<JCCap> packages = new Vector<>();

    @SuppressWarnings("unused")
    public void setJCKit(String msg) {
        master_jckit_path = msg;
    }

    @SuppressWarnings("unused")
    public JCCap createCap() {
        JCCap pkg = new JCCap(master_jckit_path);
        packages.add(pkg);
        return pkg;
    }

    @Override
    public void execute() {
        Thread cleanup = new Thread(() -> {
            log("Ctrl-C, cleaning up", Project.MSG_INFO);
            packages.forEach(JCCap::cleanTemp);
        });
        Runtime.getRuntime().addShutdownHook(cleanup);
        String ver = JavaCard.class.getPackage().getImplementationVersion();
        log("ant-javacard " + (ver == null ? "development" : ver), Project.MSG_INFO);
        if (!LTS.contains(Misc.getCurrentJDKVersion())) {
            log("Use one of the LTS JDK versions: " + LTS, Project.MSG_WARN);
        }
        try {
            for (JCCap p : packages) {
                p.execute();
            }
        } finally {
            Runtime.getRuntime().removeShutdownHook(cleanup);
        }
    }

}
