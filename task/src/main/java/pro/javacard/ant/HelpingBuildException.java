// SPDX-FileCopyrightText: 2015 Martin Paljak <martin@martinpaljak.net>
// SPDX-License-Identifier: MIT

package pro.javacard.ant;

import org.apache.tools.ant.BuildException;

public class HelpingBuildException extends BuildException {
    private static final long serialVersionUID = -2365126253968479314L;

    // The SDK, target and JDK combinations that work
    public static final String COMPATIBILITY = "https://github.com/martinpaljak/ant-javacard/wiki/JavaCard-SDK-and-JDK-version-compatibility";

    private static final String README = "https://github.com/martinpaljak/ant-javacard#readme";

    public HelpingBuildException(String msg) {
        this(msg, README);
    }

    public HelpingBuildException(String msg, String url) {
        super(msg + "\n\nPLEASE READ " + url);
    }
}
