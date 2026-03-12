package tiiehenry.android.snapshot.provider;

import android.content.Context;

import tiiehenry.android.snapshot.file.IFileSystem;

public abstract class FileSystemProvider implements IProvider<IFileSystem> {
    protected Context context;

    public FileSystemProvider(Context context) {
        this.context = context;
    }

    @Override
    public abstract IFileSystem provide();
}
