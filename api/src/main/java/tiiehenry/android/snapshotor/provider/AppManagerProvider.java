package tiiehenry.android.snapshotor.provider;

import android.content.Context;

import tiiehenry.android.snapshotor.app.IAppManager;

public abstract class AppManagerProvider implements IProvider<IAppManager> {
    protected Context context;

    public AppManagerProvider(Context context) {
        this.context = context;
    }

    @Override
    public abstract IAppManager provide();
}
