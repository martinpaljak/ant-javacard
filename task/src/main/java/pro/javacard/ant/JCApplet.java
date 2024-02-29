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
