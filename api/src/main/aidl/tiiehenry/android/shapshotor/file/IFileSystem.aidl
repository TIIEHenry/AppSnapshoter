package tiiehenry.android.shapshotor.file;

import android.os.ParcelFileDescriptor;
import tiiehenry.android.shapshotor.file.IFileCompressor;

interface IFileSystem {
    int fileType(String path);
    List<String> listDir(String path);
    long calculateSize(String path);
    boolean mkdirs(String path);
    boolean delete(String path);
    long getLastModifiedTime(String path);
    boolean setLastModifiedTime(String path, long time);
    long getAccessTime(String path);
    boolean setAccessTime(String path, long time);
    String md5(String file);
    int getUid(String path);
    boolean setUid(String path, int uid);
    int getGid(String path);
    boolean setGid(String path, int gid);
    ParcelFileDescriptor openFile(String path, int mode);
    void diff(String oldDir, String newDir, inout List<String> addedList, inout List<String> removedList, inout List<String> changedList, inout List<String> keepedList);
    IFileCompressor getCompressor();
}
