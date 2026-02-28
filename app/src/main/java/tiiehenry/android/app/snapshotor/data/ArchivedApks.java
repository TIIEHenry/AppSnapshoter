package tiiehenry.android.app.snapshotor.data;

public class ArchivedApks {
    public static String getArchivedApkDir(String baseDir, long versionCode) {
        return baseDir + "/apks/" + versionCode ;
    }
}
