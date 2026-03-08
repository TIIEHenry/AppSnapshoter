package tiiehenry.android.snapshotor.provider;

import android.content.Context;

import tiiehenry.android.snapshotor.file.IFileSystem;

public abstract class FileSystemProvider implements IProvider<IFileSystem> {
    protected Context context;

    public FileSystemProvider(Context context) {
        this.context = context;
    }

    @Override
    public abstract IFileSystem provide();
}
