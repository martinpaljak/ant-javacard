package testapplets.integer;

import javacard.framework.APDU;
import javacard.framework.Applet;
import javacard.framework.ISOException;

public class EmptyInt extends Applet {

	private EmptyInt(byte[] parameters, short offset, byte length) {
		int everything = 42;
		register(parameters, offset, length);
	}

	public static void install(byte[] parameters, short offset, byte length) {
		new EmptyInt(parameters, offset, length);
	}

	public void process(APDU arg0) throws ISOException {

	}

}
