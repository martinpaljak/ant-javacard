// SPDX-FileCopyrightText: 2015 Martin Paljak <martin@martinpaljak.net>
// SPDX-License-Identifier: MIT

package pro.javacard.ant;

// Just for Ant: <import exps="" jar=""/>
public class JCImport {
    String exps = null;
    String jar = null;

    @SuppressWarnings("unused")
    public void setExps(String msg) {
        exps = msg;
    }

    @SuppressWarnings("unused")
    public void setJar(String msg) {
        jar = msg;
    }
}
