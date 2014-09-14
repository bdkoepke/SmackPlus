package pw.swordfish.rx;

public interface Observer<T> {
    void onCompleted();
    void onError(Exception error);
    void onNext(T value);
}
