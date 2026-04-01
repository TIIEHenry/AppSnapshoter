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
            if (client == null) {
                return false;
            }
            if (!client.asBinder().isBinderAlive()) {
                return false;
            }
            return client.asBinder().pingBinder();
        }
    }

    /**
     * release for {@link #fetchRemote(Context)}
     *
     * @param context
     */
    public void releaseClient(Context context) {
        I localClient = client;
        if (localClient == null) {
            return;
        }
        beforeRelease(localClient);
        try {
            localClient.asBinder().unlinkToDeath(deathRecipient, 0);
        } catch (Exception ignored) {
        }
        synchronized (clientLock) {
            client = null;
        }
        if (context != null) {
            unbindService(context);
        }
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
            if (isConnected()) {
                clientFuture = CompletableFuture.completedFuture(client);
                return clientFuture;
            }
            if (client != null) {
                client = null;
            }
        }
        if (clientFuture.isDone()) {
            clientFuture = new CompletableFuture<>();
            Log.d(getLogTag(), "reset future");
        }
//        CountDownLatch latch = countDownLatch;
//        if (latch.getCount() == 0) {
//            latch = new CountDownLatch(1);
//        }
        Log.d(getLogTag(), "bindService start");
        Context applicationContext = context.getApplicationContext();
//        unbindService(applicationContext);
        ServiceConnection bound = bindRemote(applicationContext, connection);
        if (bound == null) {
            throw new IllegalStateException("bindService failed");
//            Log.w(getLogTag(), "bindService failed");
//            return CompletableFuture.completedFuture(client);
        }
        return clientFuture;
    }

    @WorkerThread
    public synchronized I waitFetch(@NonNull Context context) {
        if (isMainThread()) {
            throw new IllegalStateException("waitFetch can not be called in main thread");
        }
        long startTime = System.currentTimeMillis();
        try {
            I i = clientFuture.get(BIND_TIMEOUT, TimeUnit.SECONDS);
            onFetched(context, i);
            return i;
        } catch (TimeoutException e) {
            Log.w(getLogTag(), "Timeout waiting for service binding, checking if connected: " + this);
            // 超时后检查服务是否已经连接成功
            // onServiceConnected 可能在超时后但返回前完成了
            I cachedClient = client;
            if (cachedClient != null && cachedClient.asBinder().isBinderAlive() && cachedClient.asBinder().pingBinder()) {
                Log.i(getLogTag(), "Service connected after timeout, returning client");
                return cachedClient;
            }
            Log.e(getLogTag(), "Service not connected after timeout");
        } catch (CancellationException e) {
            Log.e(getLogTag(), "canceled: " + e.getMessage());
        } catch (Exception e) {
            Log.e(getLogTag(), "error: " + e.getMessage());
        } finally {
            // 不要在这里重置 future，因为 onServiceConnected 可能还在使用它
            // 如果服务已经连接成功，client 不为 null，后续调用可以通过 isConnected() 检测到
            // clientFuture = new CompletableFuture<>();
        }
        long endTime = System.currentTimeMillis();
        Log.d(getLogTag(), "bindService time: " + (endTime - startTime));
        return client;
    }

    public void unbindService(@NonNull Context context) {
        doUnbind(context, connection);
    }

    public void doUnbind(@NonNull Context context, @NonNull ServiceConnection connection) {
        try {
            context.unbindService(connection);
        } catch (Exception ignored) {
        }
    }

    private final IBinder.DeathRecipient deathRecipient = new IBinder.DeathRecipient() {

        @Override
        public void binderDied() {
            try {
                I i = client;
                if (i != null) {
                    i.asBinder().unlinkToDeath(this, 0);
                }
            } catch (Exception ignored) {
            }
            synchronized (clientLock) {
                client = null;
            }
            onBinderDied();
        }
    };


    @NonNull
    public abstract I asInterface(@NonNull IBinder iBinder);

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder iBinder) {
            CompletableFuture<I> future = clientFuture;
            I i;
            try {
                if (iBinder == null) {
                    throw new IllegalStateException("iBinder is null");
                }
                i = asInterface(iBinder);
                client = i;
                onConnected(i);
                try {
                    i.asBinder().linkToDeath(deathRecipient, 0);
                } catch (RemoteException e) {
                    Log.w(getLogTag(), "linkToDeath failed: " + e.getMessage());
                }
            } catch (Exception e) {
                Log.e(getLogTag(), "asInterface failed: " + e.getMessage());
                future.completeExceptionally(e);
                return;
            }
            future.complete(i);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            I i = client;
            if (i != null) {
                try {
                    i.asBinder().unlinkToDeath(deathRecipient, 0);
                } catch (Exception e) {
                    Log.w(getLogTag(), "unlinkToDeath failed: " + e.getMessage());
                }
            }
            synchronized (clientLock) {
                client = null;
            }
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
        if (b != null) {
            return b;
        }
        return null;
    }

    /**
     * 异步绑定Service，绑定之前判断是否已经连接{@link #isConnected()}，如果已经连接则返回null
     *
     * @param context
     * @param connection
     * @return 真正的ServiceConnection 不同于输入的connection，null代表已连接或者失败
     */
    @Nullable
    public ServiceConnection bindRemote(@NonNull Context context, @NonNull ServiceConnection connection) {
        if (isConnected()) {
            return null;
        }
        Intent intent = getIntent(context);
        Log.d(getLogTag(), "bindService: " + intent);
        ServiceConnection conn = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder iBinder) {
                Log.i(getLogTag(), "onServiceConnected: " + name);
                I i;
                CompletableFuture<I> future = clientFuture;
                try {
                    i = asInterface(iBinder);
                    synchronized (clientLock) {
                        client = i;
                    }
                    onConnected(i);
                    try {
                        i.asBinder().linkToDeath(deathRecipient, 0);
                    } catch (RemoteException e) {
                        Log.w(getLogTag(), "linkToDeath failed: " + e.getMessage());
                    }
                } catch (Exception e) {
                    Log.e(getLogTag(), "asInterface failed: " + e.getMessage());

                    if (!future.isDone()) {
                        future.completeExceptionally(e);
                    }
                    return;
                }
                if (!future.isDone()) {
                    future.complete(i);
                }
                connection.onServiceConnected(name, iBinder);
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                I i = client;
                if (i != null) {
                    try {
                        i.asBinder().unlinkToDeath(deathRecipient, 0);
                    } catch (Exception e) {
                        Log.w(getLogTag(), "unlinkToDeath failed: " + e.getMessage());
                    }
                }
                synchronized (clientLock) {
                    client = null;
                }
                connection.onServiceDisconnected(name);
            }
        };
        return doBind(context, intent, conn);
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

}