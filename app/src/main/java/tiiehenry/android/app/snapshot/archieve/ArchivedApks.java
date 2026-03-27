package tiiehenry.android.app.snapshot.archieve;

public class ArchivedApks {
    public static String getArchivedApkDir(String baseDir, long versionCode) {
        return baseDir + "/apks/" + versionCode ;
    }
}
