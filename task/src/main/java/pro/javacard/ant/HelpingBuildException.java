// SPDX-FileCopyrightText: 2015 Martin Paljak <martin@martinpaljak.net>
// SPDX-License-Identifier: MIT

package pro.javacard.ant;

import org.apache.tools.ant.BuildException;

public class HelpingBuildException extends BuildException {
    private static final long serialVersionUID = -2365126253968479314L;

    // The kit, target and JDK combinations that work, in one table
    public static final String COMPATIBILITY = "https://github.com/martinpaljak/ant-javacard/wiki/JavaCard-SDK-and-JDK-version-compatibility";

    public HelpingBuildException(String msg) {
        super(msg + "\n\nPLEASE READ https://github.com/martinpaljak/ant-javacard#readme");
    }

    public HelpingBuildException(String msg, String url) {
        super(msg + "\n\nPLEASE READ " + url);
    }
}
