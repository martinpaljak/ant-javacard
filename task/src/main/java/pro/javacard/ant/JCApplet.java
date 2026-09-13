// SPDX-FileCopyrightText: 2015 Martin Paljak <martin@martinpaljak.net>
// SPDX-License-Identifier: MIT

package pro.javacard.ant;

import org.apache.tools.ant.BuildException;
import pro.javacard.capfile.HexUtils;

// Just for Ant
public class JCApplet {
    String klass = null;
    byte[] aid = null;

    public JCApplet() {
    }

    @SuppressWarnings("unused")
    public void setClass(String msg) {
        klass = msg;
    }

    @SuppressWarnings("unused")
    public void setAID(String msg) {
        try {
            aid = HexUtils.stringToBin(msg);
            if (aid.length < 5 || aid.length > 16) {
                throw new BuildException("Applet AID must be between 5 and 16 bytes: " + aid.length);
            }
        } catch (IllegalArgumentException e) {
            throw new BuildException("Not a valid applet AID: " + e.getMessage());
        }
    }
}
