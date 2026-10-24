package tiiehenry.android.snapshot.app;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Looper;
import android.os.RemoteException;
import android.util.Log;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public abstract class IServiceClient<I extends IInterface> {

    public static ThreadPoolExecutor executor = new ThreadPoolExecutor(
            1,
            1,
            0L, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(),
            r -> {
                Thread t = new Thread(r, "IServiceClient-Bind-Executor");
                t.setDaemon(false);
                return t;
            },
            new ThreadPoolExecutor.AbortPolicy()
    );

    public static int BIND_TIMEOUT = 15;

    protected I client = null;
    protected final Object clientLock = new Object();
    private volatile CompletableFuture<I> clientFuture = new CompletableFuture<>();

    /**
     * Generation of the binding currently allowed to publish client state.
     * Increased when a new bind starts and when the saved binding is released.
     */
    private int bindGeneration = 0;
    /** Context instance passed to the successful bindService / doBind. */
    private Context boundContext;
    /** ServiceConnection returned by doBind for that bind. */
    private ServiceConnection boundConnection;
    /** Future that the active bind will complete. */
    private CompletableFuture<I> boundFuture;
    /** Death recipient registered for {@link #client}, if any. */
    private IBinder.DeathRecipient deathRecipient;

    public boolean bindImportant = false;
    protected final ArrayList<IServiceRemoteObserver> observers = new ArrayList<>();

    public interface BindCallback<I extends IInterface> {
        void onServiceConnected(@NonNull I client, @NonNull ComponentName name, @NonNull IBinder service);

        void onServiceDisconnected(ComponentName name);

        void onError(@NonNull Exception e);
    }

    @NonNull
    public String getLogTag() {
        return "IServiceClient";
    }

    /**
     * 获取缓存的service
     *
     * @return
     */
    @Nullable
    public I getClient() {
        return client;
    }

    /**
     * 是否已经连接
     * connection of {@link #fetchRemote(Context)}
     */
    public boolean isConnected() {
        synchronized (clientLock) {
            return connectedLocked();
        }
    }

    private boolean connectedLocked() {
        if (client == null) {
            return false;
        }
        if (!client.asBinder().isBinderAlive()) {
            return false;
        }
        return client.asBinder().pingBinder();
    }

    /**
     * release for {@link #fetchRemote(Context)}
     *
     * @param context
     */
    public synchronized void releaseClient(Context context) {
        I localClient;
        IBinder.DeathRecipient recipient;
        synchronized (clientLock) {
            if (boundConnection == null && client == null) {
                return;
            }
            localClient = client;
            recipient = deathRecipient;
            bindGeneration++;
        }
        if (localClient != null) {
            beforeRelease(localClient);
            unlinkDeath(localClient.asBinder(), recipient);
        }
        synchronized (clientLock) {
            if (client == localClient) {
                client = null;
            }
            if (deathRecipient == recipient) {
                deathRecipient = null;
            }
        }
        discardCurrentClientFuture();
        unbindService(context);
        onReleased();
    }


    /**
     * 连接成功，在{@link IServiceClient#fetchRemote(Context)}返回结果之前
     *
     * @param service
     */
    public void onConnected(I service) {

    }

    /**
     * 连接成功，在{@link IServiceClient#fetchRemote(Context)}返回结果之后
     *
     * @param context
     * @param service
     */
    public void onFetched(Context context, I service) throws Exception {

    }

    /**
     * 不会在客户端调用 unbindService() 时触发
     *
     * @param name
     */
    public void onDisconnected(ComponentName name) {
        synchronized (observers) {
            observers.removeIf(observer -> !observer.onDisconnected());
        }
    }

    /**
     * 这里可以处理重连，unbindService
     */
    public void onBinderDied() {
        synchronized (observers) {
            observers.removeIf(observer -> !observer.onBinderDied());
        }
    }

    /**
     * 添加连接状态回调
     *
     * @param observer
     */
    public void addConnectionObserver(IServiceRemoteObserver observer) {
        synchronized (observers) {
            if (observers.contains(observer)) {
                return;
            }
            observers.add(observer);
        }
    }

    /**
     * 移除连接状态回调
     *
     * @param observer
     */
    public void removeConnectionObserver(IServiceRemoteObserver observer) {
        synchronized (observers) {
            observers.remove(observer);
        }
    }

    public void beforeRelease(I service) {

    }

    public void onReleased() {

    }

    public static boolean isMainThread() {
        return Thread.currentThread() == Looper.getMainLooper().getThread();
    }

    /**
     * 获取Service
     * 如果已有直接返回
     * 没有绑定则绑定并阻塞等待service，缓存binder对象为client
     * 超时 {@link #BIND_TIMEOUT}
     *
     * @param context
     * @return 绑定失败会返回null 可重新尝试
     */
    @MainThread
    @NonNull
    public synchronized CompletableFuture<I> fetchRemote(@NonNull Context context) {
        if (!isMainThread()) {
            throw new IllegalStateException("fetchRemote can only be called in main thread");
        }
        synchronized (clientLock) {
            if (connectedLocked()) {
                clientFuture = CompletableFuture.completedFuture(client);
                return clientFuture;
            }
        }
        Log.d(getLogTag(), "bindService start");
        Context applicationContext = context.getApplicationContext();
        ServiceConnection bound = bindRemote(applicationContext, connection);
        if (bound == null) {
            synchronized (clientLock) {
                if (connectedLocked()) {
                    clientFuture = CompletableFuture.completedFuture(client);
                    return clientFuture;
                }
            }
            throw new IllegalStateException("bindService failed");
        }
        return clientFuture;
    }

    @WorkerThread
    public I waitFetch(@NonNull Context context) {
        if (isMainThread()) {
            throw new IllegalStateException("waitFetch can not be called in main thread");
        }
        final CompletableFuture<I> future;
        synchronized (this) {
            future = clientFuture;
        }
        long startTime = System.currentTimeMillis();
        try {
            I fetched = future.get(BIND_TIMEOUT, TimeUnit.SECONDS);
            if (!isDeliverable(fetched)) {
                return null;
            }
            onFetched(context, fetched);
            return fetched;
        } catch (TimeoutException e) {
            Log.w(getLogTag(), "Timeout waiting for service binding, checking if connected: " + this);
            I cachedClient;
            synchronized (clientLock) {
                cachedClient = client;
            }
            if (isBinderUsable(cachedClient)) {
                Log.i(getLogTag(), "Service connected after timeout, returning client");
                try {
                    onFetched(context, cachedClient);
                } catch (Exception callbackError) {
                    Log.e(getLogTag(), "error: " + callbackError.getMessage());
                    return null;
                }
                return cachedClient;
            }
            Log.e(getLogTag(), "Service not connected after timeout");
        } catch (CancellationException e) {
            Log.e(getLogTag(), "canceled: " + e.getMessage());
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            Log.e(getLogTag(), "error: " + e.getMessage());
        }
        long endTime = System.currentTimeMillis();
        Log.d(getLogTag(), "bindService time: " + (endTime - startTime));
        return null;
    }

    public synchronized void unbindService(@NonNull Context context) {
        Context ctx;
        ServiceConnection conn;
        synchronized (clientLock) {
            conn = boundConnection;
            if (conn == null) {
                return;
            }
            ctx = boundContext != null ? boundContext : context;
            boundConnection = null;
            boundContext = null;
            bindGeneration++;
        }
        if (ctx == null) {
            return;
        }
        doUnbind(ctx, conn);
    }

    public void doUnbind(@NonNull Context context, @NonNull ServiceConnection connection) {
        try {
            context.unbindService(connection);
        } catch (Exception ignored) {
        }
    }

    @NonNull
    public abstract I asInterface(@NonNull IBinder iBinder);

    /**
     * Observer connection passed into {@link #bindRemote(Context, ServiceConnection)} by
     * {@link #fetchRemote(Context)}. Client publication happens on the connection that was
     * actually registered with bindService.
     */
    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder iBinder) {
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            onDisconnected(name);
        }
    };

    /**
     * 异步绑定Service，绑定之前判断是否已经连接{@link #isConnected()}，如果已经连接则返回false
     *
     * @param context
     * @param callback
     * @return 是否开始绑定，false直接失败
     */
    public ServiceConnection bindRemote(@NonNull Context context, @NonNull BindCallback<I> callback) {
        ServiceConnection connection1 = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder iBinder) {
                I i = client;
                if (i != null) {
                    callback.onServiceConnected(i, name, iBinder);
                } else {
                    callback.onError(new RemoteException("client is null"));
                }
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                callback.onServiceDisconnected(name);
            }
        };
        ServiceConnection b = bindRemote(context, connection1);
        return b;
    }

    /**
     * 异步绑定Service，绑定之前判断是否已经连接{@link #isConnected()}，如果已经连接则返回null
     *
     * @param context
     * @param connection
     * @return 真正的ServiceConnection 不同于输入的connection，null代表已连接或者失败
     */
    @Nullable
    public synchronized ServiceConnection bindRemote(@NonNull Context context, @NonNull ServiceConnection connection) {
        BindHandoff handoff = beginBind();
        if (handoff.alreadyConnected) {
            return null;
        }
        if (handoff.previousConnection != null && handoff.previousContext != null) {
            doUnbind(handoff.previousContext, handoff.previousConnection);
        }
        return bindPrepared(context, connection, handoff.generation);
    }

    @androidx.annotation.Nullable
    public ServiceConnection doBind(@NonNull Context context, Intent intent, ServiceConnection conn) {
        int flag = getFlag();
        if (context.bindService(intent, conn, flag)) {
            return conn;
        }
        return null;
    }

    public int getFlag() {
        int flag = Context.BIND_AUTO_CREATE;
        if (Build.VERSION.SDK_INT >= 34) {
            flag |= Context.BIND_ALLOW_ACTIVITY_STARTS;
        }
        if (bindImportant) {
            flag |= Context.BIND_IMPORTANT;
        }
        return flag;
    }

    public abstract Intent getIntent(@NonNull Context context);

    /**
     * Drops a dead cached client. Caller holds {@link #clientLock} and has already
     * decided {@link #connectedLocked()} is false.
     */
    private void dropDeadClientLocked() {
        if (client == null) {
            return;
        }
        IBinder.DeathRecipient recipient = deathRecipient;
        IBinder binder = client.asBinder();
        unlinkDeath(binder, recipient);
        deathRecipient = null;
        client = null;
    }

    private void unlinkDeath(@Nullable IBinder binder, @Nullable IBinder.DeathRecipient recipient) {
        if (binder == null || recipient == null) {
            return;
        }
        try {
            binder.unlinkToDeath(recipient, 0);
        } catch (Exception ignored) {
        }
    }

    /**
     * Marks the previous binding stale, unlinks a dead client, and keeps the incomplete
     * future a waiter may already be blocked on.
     */
    private BindHandoff beginBind() {
        synchronized (clientLock) {
            if (connectedLocked()) {
                return BindHandoff.connected();
            }
            int generation = ++bindGeneration;
            BindHandoff handoff = new BindHandoff(false, generation, boundContext, boundConnection);
            boundContext = null;
            boundConnection = null;
            dropDeadClientLocked();
            if (clientFuture.isDone()) {
                clientFuture = new CompletableFuture<>();
                Log.d(getLogTag(), "reset future");
            }
            return handoff;
        }
    }

    @Nullable
    private ServiceConnection bindPrepared(@NonNull Context context,
                                           @NonNull ServiceConnection delegate,
                                           int generation) {
        final CompletableFuture<I> future;
        synchronized (clientLock) {
            future = clientFuture;
        }
        ClientServiceConnection conn = new ClientServiceConnection(delegate, future, generation);
        synchronized (clientLock) {
            if (generation != bindGeneration) {
                return null;
            }
            boundContext = context;
            boundConnection = conn;
            boundFuture = future;
        }
        Intent intent = getIntent(context);
        Log.d(getLogTag(), "bindService: " + intent);
        ServiceConnection bound = doBind(context, intent, conn);
        synchronized (clientLock) {
            if (generation != bindGeneration || boundConnection != conn) {
                return bound;
            }
            if (bound == null) {
                boundContext = null;
                boundConnection = null;
                boundFuture = null;
                return null;
            }
            boundContext = context;
            boundConnection = bound;
            boundFuture = future;
        }
        return bound;
    }

    private boolean isBinderUsable(@Nullable I service) {
        if (service == null) {
            return false;
        }
        try {
            IBinder binder = service.asBinder();
            return binder != null && binder.isBinderAlive() && binder.pingBinder();
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * A non-null wait result must still be the current client and a living binder.
     */
    private boolean isDeliverable(@Nullable I fetched) {
        if (!isBinderUsable(fetched)) {
            return false;
        }
        synchronized (clientLock) {
            if (client == null) {
                return false;
            }
            if (client == fetched) {
                return true;
            }
            IBinder currentBinder = client.asBinder();
            IBinder fetchedBinder = fetched.asBinder();
            return currentBinder != null && currentBinder == fetchedBinder;
        }
    }

    /**
     * Retires the future captured when {@code finished} was bound.
     * A newer incomplete future is left for the next bind.
     */
    private void discardFuture(@Nullable CompletableFuture<I> finished) {
        if (finished == null) {
            return;
        }
        synchronized (clientLock) {
            boolean ownedBoundFuture = boundFuture == finished;
            if (ownedBoundFuture) {
                boundFuture = null;
            }
            CompletableFuture<I> current = clientFuture;
            if (current == finished) {
                if (!finished.isDone()) {
                    finished.completeExceptionally(new CancellationException("client cleared"));
                }
                clientFuture = new CompletableFuture<>();
                return;
            }
            if (ownedBoundFuture && current != null && current.isDone()) {
                clientFuture = new CompletableFuture<>();
            }
        }
    }

    /**
     * Release path: the saved future and any completed future must stop yielding the old client.
     */
    private void discardCurrentClientFuture() {
        synchronized (clientLock) {
            CompletableFuture<I> finished = boundFuture;
            boundFuture = null;
            if (finished != null && !finished.isDone()) {
                finished.completeExceptionally(new CancellationException("client cleared"));
            }
            CompletableFuture<I> current = clientFuture;
            if (finished != null && current != finished && !current.isDone()) {
                return;
            }
            if (current == finished || current.isDone()) {
                clientFuture = new CompletableFuture<>();
            }
        }
    }

    private final class ClientServiceConnection implements ServiceConnection {
        private final ServiceConnection delegate;
        private final CompletableFuture<I> future;
        private final int generation;
        private IBinder watchedBinder;
        private IBinder.DeathRecipient watchedRecipient;
        private boolean connectedOnce;
        private boolean disconnectOnce;

        private ClientServiceConnection(@NonNull ServiceConnection delegate,
                                        @NonNull CompletableFuture<I> future,
                                        int generation) {
            this.delegate = delegate;
            this.future = future;
            this.generation = generation;
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder iBinder) {
            Log.i(getLogTag(), "onServiceConnected: " + name);
            I service = null;
            try {
                if (iBinder == null) {
                    throw new IllegalStateException("iBinder is null");
                }
                service = asInterface(iBinder);
                final IBinder watched = service.asBinder();
                watchedBinder = watched;
                synchronized (clientLock) {
                    if (generation != bindGeneration || connectedOnce) {
                        return;
                    }
                    connectedOnce = true;
                    client = service;
                }
                onConnected(service);
                linkDeath(service, watched);
                synchronized (clientLock) {
                    if (generation != bindGeneration || client != service) {
                        return;
                    }
                }
            } catch (Exception e) {
                Log.e(getLogTag(), "asInterface failed: " + e.getMessage());
                synchronized (clientLock) {
                    if (service != null && generation == bindGeneration && client == service) {
                        client = null;
                    }
                }
                if (!future.isDone()) {
                    future.completeExceptionally(e);
                }
                return;
            }
            if (!future.isDone()) {
                future.complete(service);
            }
            delegate.onServiceConnected(name, iBinder);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            boolean dropFuture = false;
            synchronized (clientLock) {
                if (generation != bindGeneration) {
                    unlinkOwn();
                    return;
                }
                if (disconnectOnce) {
                    return;
                }
                boolean ours = client == null
                        || watchedBinder == null
                        || client.asBinder() == watchedBinder;
                if (!ours) {
                    return;
                }
                disconnectOnce = true;
                unlinkOwn();
                if (client != null) {
                    client = null;
                    if (deathRecipient == watchedRecipient) {
                        deathRecipient = null;
                    }
                }
                dropFuture = true;
            }
            if (dropFuture) {
                discardFuture(future);
            }
            delegate.onServiceDisconnected(name);
        }

        private void linkDeath(I service, @Nullable IBinder watched) throws RemoteException {
            if (watched == null) {
                throw new RemoteException("binder is null");
            }
            IBinder.DeathRecipient recipient = new IBinder.DeathRecipient() {
                @Override
                public void binderDied() {
                    boolean cleared = false;
                    synchronized (clientLock) {
                        I current = client;
                        if (current == null || current.asBinder() != watched) {
                            return;
                        }
                        try {
                            watched.unlinkToDeath(this, 0);
                        } catch (Exception ignored) {
                        }
                        client = null;
                        if (deathRecipient == this) {
                            deathRecipient = null;
                        }
                        cleared = true;
                    }
                    if (cleared) {
                        discardFuture(future);
                        onBinderDied();
                    }
                }
            };
            watchedBinder = watched;
            watchedRecipient = recipient;
            try {
                watched.linkToDeath(recipient, 0);
            } catch (RemoteException e) {
                watchedRecipient = null;
                Log.w(getLogTag(), "linkToDeath failed: " + e.getMessage());
                return;
            }
            synchronized (clientLock) {
                if (generation != bindGeneration || client != service) {
                    unlinkDeath(watched, recipient);
                    if (deathRecipient == recipient) {
                        deathRecipient = null;
                    }
                    return;
                }
                deathRecipient = recipient;
            }
        }

        private void unlinkOwn() {
            IBinder binder = watchedBinder;
            IBinder.DeathRecipient recipient = watchedRecipient;
            unlinkDeath(binder, recipient);
            if (deathRecipient == recipient) {
                deathRecipient = null;
            }
        }
    }

    private static final class BindHandoff {
        final boolean alreadyConnected;
        final int generation;
        final Context previousContext;
        final ServiceConnection previousConnection;

        private BindHandoff(boolean alreadyConnected,
                            int generation,
                            Context previousContext,
                            ServiceConnection previousConnection) {
            this.alreadyConnected = alreadyConnected;
            this.generation = generation;
            this.previousContext = previousContext;
            this.previousConnection = previousConnection;
        }

        static BindHandoff connected() {
            return new BindHandoff(true, 0, null, null);
        }
    }

}
