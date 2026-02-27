package tiiehenry.android.snapshotor.provider;

import android.os.IInterface;

public interface IProvider<T extends IInterface> {
    void onInstall();
    T provide();
}
