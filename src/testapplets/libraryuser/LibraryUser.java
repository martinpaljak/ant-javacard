package testapplets.libraryuser;

import javacard.framework.APDU;
import javacard.framework.Applet;
import javacard.framework.ISOException;
import testapplets.library.SomeLibrary;

public class LibraryUser extends Applet {

    private final short value;

    private LibraryUser(boolean bvalue) {
        value = SomeLibrary.booleantest(bvalue);
    }

    public static void install(byte[] parameters, short offset, byte length) {
        new LibraryUser(true).register(parameters, (short) (offset + 1), parameters[offset]);
    }

    public void process(APDU arg0) throws ISOException {
        SomeLibrary.booleantest(true);
    }
}
