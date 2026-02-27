package tiiehenry.android.snapshotor.file;

import android.os.ParcelFileDescriptor;
import tiiehenry.android.snapshotor.file.IFileCompressor;

interface IFileSystem {
    /**
     * @param path
     * @return 0:file 1:dir 2:unknown
     */
    int fileType(String path);
    List<String> listDir(String path);
    long calculateSize(String path);
    boolean mkdirs(String path);
    boolean delete(String path);
    boolean exists(String path);
    String getParent(String path);
    long length(String path);
    long getLastModifiedTime(String path);
    boolean setLastModifiedTime(String path, long time);

    String md5(String file);
    int getUid(String path);
    boolean setUid(String path, int uid);
    int getGid(String path);
    boolean setGid(String path, int gid);
    ParcelFileDescriptor openFile(String path, int mode);
    ParcelFileDescriptor openInputStream(String path);
    ParcelFileDescriptor openOutputStream(String path);
    String createTempFile(String prefix, String suffix);
    void createTarArchive(String sourceDir, String targetFile,in List<String> excludes,in List<String> excludeFiles,String stdErr,String stdOut);
    void diff(String oldDir, String newDir, inout List<String> addedList, inout List<String> removedList, inout List<String> changedList, inout List<String> keepedList);
    IFileCompressor getCompressor();
}