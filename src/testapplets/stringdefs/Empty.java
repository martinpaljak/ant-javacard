package testapplets.stringdefs;

import javacard.framework.APDU;
import javacard.framework.Applet;
import javacard.framework.ISOException;
import javacard.security.CryptoException;
import javacardx.annotations.StringDef;
import javacardx.annotations.StringPool;

@StringPool(value = {
		@StringDef(name = "hello", value = "Hello World!"),
},
		name = "HelloWorldString")

public class Empty extends Applet {

	private Empty(byte[] parameters, short offset, byte length) {
		register(parameters, (short) (offset + 1), parameters[offset]);
	}

	public static void install(byte[] parameters, short offset, byte length) {
		new Empty(parameters, offset, length);
	}

	public void process(APDU arg0) throws ISOException {
		CryptoException.throwIt((short) 0x6666);
	}
}
