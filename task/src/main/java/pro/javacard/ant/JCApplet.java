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

import org.apache.tools.ant.BuildException;

// Just for Ant
public class JCApplet {
    String klass = null;
    byte[] aid = null;

    public JCApplet() {
    }

    public void setClass(String msg) {
        klass = msg;
    }

    public void setAID(String msg) {
        try {
            aid = Misc.stringToBin(msg);
            if (aid.length < 5 || aid.length > 16) {
                throw new BuildException("Applet AID must be between 5 and 16 bytes: " + aid.length);
            }
        } catch (IllegalArgumentException e) {
            throw new BuildException("Not a valid applet AID: " + e.getMessage());
        }
    }
}
