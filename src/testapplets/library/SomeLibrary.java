package testapplets.library;

public class SomeLibrary {
	public static final short TRUE = (short) 0x5AA5;
	public static final short FALSE = (short) 0xA55A;

	public static short booleantest(boolean b) {
		return b ? TRUE : FALSE;
	}
}
