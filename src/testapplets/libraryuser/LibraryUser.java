// SPDX-FileCopyrightText: 2024 Martin Paljak <martin@martinpaljak.net>
// SPDX-License-Identifier: MIT

package testapplets.libraryuser;

import javacard.framework.AID;
import javacard.framework.APDU;
import javacard.framework.Applet;
import javacard.framework.ISOException;
import javacard.framework.JCSystem;
import testapplets.library.SomeLibrary;
import testapplets.library.SomeService;

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
        byte[] buffer = arg0.getBuffer();
        AID provider = JCSystem.lookupAID(buffer, (short) 0, (byte) 5);
        if (provider != null) {
            SomeService service = (SomeService) JCSystem.getAppletShareableInterfaceObject(provider, (byte) 0);
            if (service != null) {
                buffer[0] = (byte) service.ping(value);
            }
        }
    }
}
