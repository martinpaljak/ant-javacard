package testapplets.libraryuser;

import javacard.framework.APDU;
import javacard.framework.Applet;
import javacard.framework.ISOException;

import testapplets.library.SomeLibrary;

public class LibraryUser extends Applet {

	private LibraryUser(byte[] parameters, short offset, byte length, short bvalue) {
		register(parameters, (short) (offset + 1), parameters[offset]);
	}

	public static void install(byte[] parameters, short offset, byte length) {
		new LibraryUser(parameters, offset, length, SomeLibrary.FALSE);
	}

	public void process(APDU arg0) throws ISOException {
		SomeLibrary.booleantest(true);

	}

}
