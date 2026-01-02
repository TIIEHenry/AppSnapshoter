package tiiehenry.android.shapshotor.provider;

import android.content.Context;

import tiiehenry.android.shapshotor.sync.IDataSyncer;

public abstract class DataSyncerProvider implements IProvider<IDataSyncer> {
    protected Context hostContext;
    protected Context pluginContext;

    public DataSyncerProvider(Context hostContext, Context pluginContext) {
        this.hostContext = hostContext;
        this.pluginContext = pluginContext;
    }

    @Override
    public abstract IDataSyncer provide();
}
