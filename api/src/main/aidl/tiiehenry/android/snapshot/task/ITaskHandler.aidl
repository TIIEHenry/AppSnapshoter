package tiiehenry.android.snapshot.task;

interface ITaskHandler {
    String id();
    int state();
    void start();
    void cancel();
}
