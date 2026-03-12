package tiiehenry.android.snapshot.provider;

import android.content.Context;

import tiiehenry.android.snapshot.app.IAppManager;

public abstract class AppManagerProvider implements IProvider<IAppManager> {
    protected Context context;

    public AppManagerProvider(Context context) {
        this.context = context;
    }

    @Override
    public abstract IAppManager provide();
}
