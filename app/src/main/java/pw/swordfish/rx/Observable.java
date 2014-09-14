package pw.swordfish.rx;

public interface Observable<T> {
    AutoCloseable subscribe(final Observer<T> observer);
}
