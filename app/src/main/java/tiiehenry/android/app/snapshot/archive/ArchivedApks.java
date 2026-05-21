package tiiehenry.android.app.snapshot.archive;

public class ArchivedApks {
    public static String getArchivedApkDir(String baseDir, long versionCode) {
        return baseDir + "/apks/" + versionCode ;
    }
}
