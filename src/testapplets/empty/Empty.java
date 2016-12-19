package testapplets.empty;

import javacard.framework.APDU;
import javacard.framework.Applet;
import javacard.framework.ISOException;

public class Empty extends Applet {

	private Empty(byte[] parameters, short offset, byte length) {
		register(parameters, offset, length);
	}

	public static void install(byte[] parameters, short offset, byte length) {
		new Empty(parameters, offset, length);
	}

	public void process(APDU arg0) throws ISOException {

	}

}
