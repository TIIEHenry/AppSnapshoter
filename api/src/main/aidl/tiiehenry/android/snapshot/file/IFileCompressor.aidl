package tiiehenry.android.snapshot.file;

import tiiehenry.android.snapshot.file.ICompressCallback;
import tiiehenry.android.snapshot.task.ITaskHandler;
import android.os.ParcelFileDescriptor;

interface IFileCompressor {
    List<String> supportedAlgorithms();
    String fileExtension(String algorithm, String type, String file);
    String detectAlgorithm(String file);
    boolean checkFileValid(String algorithm, String path, long size, String md5);
    ITaskHandler compress(String algorithm, String dir, String targetFile, in List<String> excludes,  in List<String> excludeFiles, ICompressCallback callback);
    ITaskHandler compressMultiple(String algorithm, in List<String> files, String targetFile, ICompressCallback callback);
    ITaskHandler decompress(String algorithm, String file, String targetDir, ICompressCallback callback);
}
