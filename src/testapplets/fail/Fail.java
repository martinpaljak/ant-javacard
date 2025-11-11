package testapplets.fail;

import javacard.framework.APDU;
import javacard.framework.Applet;
import javacard.framework.ISO7816;
import javacard.framework.ISOException;
import javacard.security.CryptoException;

public class Fail extends Applet {

    private Fail(byte[] parameters, short offset, byte length) {
        register(parameters, (short) (offset + 1), parameters[offset]);
    }

    public static void install(byte[] parameters, short offset, byte length) {
        new Fail(parameters, offset, length);
    }

    public void process(APDU apdu) throws ISOException {
        if (selectingApplet())
            return;
        byte[] buffer = apdu.getBuffer();
        short i = 5;
        if (buffer[i + buffer[ISO7816.OFFSET_CLA]] != (short) 6)
            CryptoException.throwIt((short) 0x6666);
        apdu.setIncomingAndReceive();
        apdu.setOutgoingAndSend((short) 0, (short) 11);
    }
}
