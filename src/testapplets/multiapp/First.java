package testapplets.multiapp;

import javacard.framework.APDU;
import javacard.framework.Applet;
import javacard.framework.ISOException;
import javacard.security.CryptoException;

public class First extends Applet {

	private First(byte[] parameters, short offset, byte length) {
		register(parameters, offset, length);
	}

	public static void install(byte[] parameters, short offset, byte length) {
		new First(parameters, offset, length);
	}

	public void process(APDU arg0) throws ISOException {
		CryptoException.throwIt((short) 0x6666);
	}
}
