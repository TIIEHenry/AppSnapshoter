package tiiehenry.android.snapshotor.file;

import tiiehenry.android.snapshotor.file.ICompressCallback;
import tiiehenry.android.snapshotor.task.ITaskHandler;
import android.os.ParcelFileDescriptor;

interface IFileCompressor {
    List<String> supportedAlgorithms();
    String fileExtension(String algorithm, String type, String file);
    String detectAlgorithm(String file);
    ITaskHandler compress(String algorithm, String dir, String targetFile, in List<String> excludes,  in List<String> excludeFiles, ICompressCallback callback);
    boolean checkFileValid(String algorithm, String path, long size, String md5);
    ITaskHandler decompress(String algorithm, String file, String targetDir, ICompressCallback callback);
}
