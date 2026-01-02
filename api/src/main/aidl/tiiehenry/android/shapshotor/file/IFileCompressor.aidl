package tiiehenry.android.shapshotor.file;

import tiiehenry.android.shapshotor.file.ICompressCallback;
import tiiehenry.android.shapshotor.task.ITaskHandler;
import android.os.ParcelFileDescriptor;

interface IFileCompressor {
    List<String> supportedAlgorithms();
    String fileExtension(String algorithm, String type, String file);
    String detectAlgorithm(String file);
    ITaskHandler compress(String algorithm, String dir, String targetFile, in List<String> excludes, ICompressCallback callback);
    boolean checkFileValid(String algorithm, String path, long size, String md5);
    ITaskHandler decompress(String algorithm, String file, String targetDir, ICompressCallback callback);
}
