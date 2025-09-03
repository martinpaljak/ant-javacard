package pro.javacard.ant;

import org.apache.tools.ant.BuildException;

import java.util.regex.Pattern;

public class BuildProp {
    static Pattern regex;

    String key;
    String value;

    public BuildProp() {

    }

    public void setKey(String key) {
        if (!regex.matcher(key).find()) {
            throw new BuildException("The buildprop key contains illegal characters: " + key);
        }

        this.key = key;
    }

    public void setValue(String value) {
        this.value = value;
    }

    static {
        regex = Pattern.compile("^[a-zA-Z_][0-9a-zA-Z_]*$");
    }
}
