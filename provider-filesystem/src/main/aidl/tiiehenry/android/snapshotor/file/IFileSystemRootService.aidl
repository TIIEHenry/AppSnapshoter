package tiiehenry.android.snapshotor.file;

import tiiehenry.android.snapshotor.file.BytesParcelable;
import tiiehenry.android.snapshotor.file.StatFsParcelable;
import tiiehenry.android.snapshotor.file.FilePathParcelable;
import tiiehenry.android.snapshotor.file.IBinaryCallback;
import android.os.ParcelFileDescriptor;

interface IFileSystemRootService {
    StatFsParcelable readStatFs(String path);
    List<FilePathParcelable> listFilePaths(String path, boolean listFiles, boolean listDirs);
    ParcelFileDescriptor readText(String path);
    void writeText(String path, in ParcelFileDescriptor pfd);
    long calculateTreeSize(String path);
    int callTarCli(String stdOut, String stdErr, in String[] argv);
    List<String> getPackageSourceDir(String packageName, int userId);
    String compress(int level, String inputPath, String outputPath, IBinaryCallback callback);
    boolean mkdirs(String path);
    boolean exists(String path);
    int fileType(String path);
    boolean deleteRecursively(String path);
    boolean copyRecursively(String source, String target, boolean overwrite);
    long getLastModifiedTime(String path);
    boolean setLastModifiedTime(String path, long time);

    int getUid(String path);
    boolean setUid(String path, int uid);
    int getGid(String path);
    boolean setGid(String path, int gid);
    ParcelFileDescriptor openFile(String path, int mode);
    String md5(String file);
}