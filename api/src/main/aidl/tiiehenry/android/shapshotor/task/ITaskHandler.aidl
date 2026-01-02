package tiiehenry.android.shapshotor.task;

interface ITaskHandler {
    String id();
    int state();
    void start();
    void cancel();
}
