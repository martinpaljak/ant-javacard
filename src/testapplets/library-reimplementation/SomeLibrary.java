package testapplets.library;

// Just to have an import
import javacard.security.RandomData;

public class SomeLibrary {
	public static final short TRUE = (short) 0x5AA5;
	public static final short FALSE = (short) 0xA55A;

	public static short booleantest(boolean b) {
		return (short) 0;
	}

	public static RandomData getRandom() {
		return null;
	}
}
