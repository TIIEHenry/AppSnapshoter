package tiiehenry.android.shapshotor.provider;

import android.content.Context;

import tiiehenry.android.shapshotor.app.IAppManager;

public abstract class AppManagerProvider implements IProvider<IAppManager> {
    protected Context hostContext;
    protected Context pluginContext;

    public AppManagerProvider(Context hostContext, Context pluginContext) {
        this.hostContext = hostContext;
        this.pluginContext = pluginContext;
    }

    @Override
    public abstract IAppManager provide();
}
