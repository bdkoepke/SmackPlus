package pw.swordfish.util.function;

public class Suppliers {
    public static <T> Supplier<T> memoize(final Supplier<T> supplier) {
        return new Supplier<T>() {
            T value;
            @Override
            public T get() {
                return value == null ? (value = supplier.get()) : value;
            }
        };
    }
}
