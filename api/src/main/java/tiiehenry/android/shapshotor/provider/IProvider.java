package tiiehenry.android.shapshotor.provider;

import android.os.IInterface;

public interface IProvider<T extends IInterface> {
    T provide();
}
