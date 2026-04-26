// SPDX-FileCopyrightText: 2026 Martin Paljak <martin@martinpaljak.net>
// SPDX-License-Identifier: MIT

package pro.javacard.ant;

// Just for Ant: <sources path="" includes="" excludes=""/>
public class JCSources {
    String path = null;
    String includes = null;
    String excludes = null;

    public void setPath(String msg) {
        path = msg;
    }

    public void setIncludes(String msg) {
        includes = msg;
    }

    public void setExcludes(String msg) {
        excludes = msg;
    }
}
