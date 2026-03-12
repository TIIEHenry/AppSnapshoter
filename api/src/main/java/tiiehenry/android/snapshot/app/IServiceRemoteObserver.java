package tiiehenry.android.snapshot.app;

public interface IServiceRemoteObserver {

    /**
     * @return 是否保留observer
     */
    boolean onBinderDied();

    /**
     * @return 是否保留observer
     */
    boolean onDisconnected();

}
