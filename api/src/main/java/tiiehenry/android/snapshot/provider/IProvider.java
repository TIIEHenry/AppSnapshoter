package tiiehenry.android.snapshot.provider;

import android.os.IInterface;

public interface IProvider<T extends IInterface> {
    void onInstall();
    T provide();
}
