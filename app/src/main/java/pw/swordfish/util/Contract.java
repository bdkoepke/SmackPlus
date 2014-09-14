package pw.swordfish.util;

public final class Contract {
    public static <T> T requiresNonNull(T o, String argument) {
        if (o == null)
            throw new IllegalArgumentException(argument);
        return o;
    }
}
