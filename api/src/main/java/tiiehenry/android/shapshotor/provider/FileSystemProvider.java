package tiiehenry.android.shapshotor.provider;

import android.content.Context;

import tiiehenry.android.shapshotor.file.IFileSystem;

public abstract class FileSystemProvider implements IProvider<IFileSystem> {
    protected Context hostContext;
    protected Context pluginContext;

    public FileSystemProvider(Context hostContext, Context pluginContext) {
        this.hostContext = hostContext;
        this.pluginContext = pluginContext;
    }

    @Override
    public abstract IFileSystem provide();
}
