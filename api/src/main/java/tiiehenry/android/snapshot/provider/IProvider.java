package tiiehenry.android.snapshot.provider;

import android.os.IInterface;

public interface IProvider<T> {
    void onInstall();
    T provide();
}
