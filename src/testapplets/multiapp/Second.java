package testapplets.multiapp;

import javacard.framework.APDU;
import javacard.framework.Applet;
import javacard.framework.ISOException;
import javacard.security.CryptoException;

public class Second extends Applet {

	private Second(byte[] parameters, short offset, byte length) {
		register(parameters, (short) (offset + 1), parameters[offset]);
	}

	public static void install(byte[] parameters, short offset, byte length) {
		new Second(parameters, offset, length);
	}

	public void process(APDU arg0) throws ISOException {
		CryptoException.throwIt((short) 0x6666);
	}
}
