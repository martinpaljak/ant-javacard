// SPDX-FileCopyrightText: 2015 Martin Paljak <martin@martinpaljak.net>
// SPDX-License-Identifier: MIT

package pro.javacard.ant;

import org.apache.tools.ant.Project;
import org.apache.tools.ant.Task;

import java.util.Arrays;
import java.util.Vector;
import java.util.stream.IntStream;

// <javacard jckit="${env.JCKIT}">...</javacard>
// This is a wrapper task that can contain one or more <cap> subtasks for building capfiles.
public final class JavaCard extends Task {

    private final int[] lts = new int[]{8, 11, 17, 21};
    private String master_jckit_path = null;
    private final Vector<JCCap> packages = new Vector<>();

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
            Misc.cleanTemp();
        });
        Runtime.getRuntime().addShutdownHook(cleanup);
        String ver = JavaCard.class.getPackage().getImplementationVersion();
        log("ant-javacard " + (ver == null ? "development" : ver), Project.MSG_INFO);
        if (IntStream.of(lts).noneMatch(x -> x == Misc.getCurrentJDKVersion())) {
            log("Please consider using a LTS JDK version: " + Arrays.toString(lts), Project.MSG_WARN);
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
