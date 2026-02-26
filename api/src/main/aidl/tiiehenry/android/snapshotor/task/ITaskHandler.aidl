package tiiehenry.android.snapshotor.task;

interface ITaskHandler {
    String id();
    int state();
    void start();
    void cancel();
}
