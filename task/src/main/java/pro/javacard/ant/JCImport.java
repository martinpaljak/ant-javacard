// SPDX-FileCopyrightText: 2015 Martin Paljak <martin@martinpaljak.net>
// SPDX-License-Identifier: MIT

package pro.javacard.ant;

// Just for Ant: <import exps="" jar=""/>
public class JCImport {
    String exps = null;
    String jar = null;

    public void setExps(String msg) {
        exps = msg;
    }

    public void setJar(String msg) {
        jar = msg;
    }
}
