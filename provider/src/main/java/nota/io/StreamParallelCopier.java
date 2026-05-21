package nota.io;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;

/**
 * 一个线程读一个线程写，速度超快
 */
public class StreamParallelCopier {
    private final InputStream inputStream;
    private final OutputStream outputStream;
    private boolean finished;
    private Exception exception;
    private CountDownLatch countDownLatch;
    private int dataItemCount;
    private Thread writeThread;
    private Thread readThread;
    private boolean canceled = false;
    private ArrayBlockingQueue<DataItem> arrayBlockingQueue;

    public StreamParallelCopier(final InputStream inputStream, final OutputStream outputStream) {
        this(inputStream, outputStream, 5);
    }

    public StreamParallelCopier(final InputStream inputStream, final OutputStream outputStream, int dataItemCount) {
        this.finished = false;
        this.exception = null;
        this.dataItemCount = dataItemCount;
        this.inputStream = inputStream;
        this.outputStream = outputStream;
    }

    private void startWrite(final ArrayBlockingQueue<DataItem> arrayBlockingQueue) {
        writeThread = new Thread() {
            @Override
            public void run() {
                try {
                    while (!finished) {
                        DataItem dataItem = arrayBlockingQueue.take();
                        if (dataItem.len == -1) {
                            break;
                        }
                        if (canceled) {
                            throw new InterruptedException();
                        }
                        outputStream.write(dataItem.array, 0, dataItem.len);
                        onBytesWritten(dataItem.len);
                    }
                } catch (IOException | InterruptedException e) {
                    onException(e);
                } finally {
                    countDownLatch.countDown();
                }
            }
        };
        writeThread.setPriority(Thread.MAX_PRIORITY);
        writeThread.start();
    }

    private void startRead(final ArrayBlockingQueue<DataItem> arrayBlockingQueue, final DataItem[] dataItems) {
        readThread = new Thread() {
            @Override
            public void run() {
                try {
                    int length = dataItems.length;
                    int i = 0;
                    while (!finished) {
                        DataItem dataItem = dataItems[i];
                        if (canceled) {
                            throw new InterruptedException();
                        }
                        int read = inputStream.read(dataItems[i].array);
                        dataItem.len = read;
                        if (read == -1) {
                            break;
                        }
                        arrayBlockingQueue.put(dataItems[i]);
                        i = (i + 1) % length;
                    }
                    if (!finished) {
                        //eof
                        arrayBlockingQueue.put(new DataItem(null, -1));
                    }
                } catch (IOException | InterruptedException e) {
                    onException(e);
                } finally {
                    countDownLatch.countDown();
                }
            }
        };
        readThread.setPriority(Thread.MAX_PRIORITY);
        readThread.start();
    }

    public void startAndWait() throws Exception {
        arrayBlockingQueue = new ArrayBlockingQueue<>(dataItemCount - 2);
        DataItem[] dataItems = new DataItem[dataItemCount];
        for (int i = 0; i < dataItems.length; i++) {
            dataItems[i] = new DataItem(new byte[131072], 0);
        }
        finished = false;
        canceled = false;
        countDownLatch = new CountDownLatch(2);
        synchronized (this) {
            startWrite(arrayBlockingQueue);
            startRead(arrayBlockingQueue, dataItems);
        }
        countDownLatch.await();
        finished = true;
        final Exception exception1 = exception;
        if (exception1 != null) {
            throw exception1;
        }
    }

    protected void onBytesWritten(int len) {
        // Hook for subclasses to track progress
    }

    void onException(final Exception e) {
        synchronized (this) {
            if (!finished) {
                exception = e;
                finished = true;
                if (writeThread != null) {
                    writeThread.interrupt();
                    writeThread = null;
                }
                if (readThread != null) {
                    readThread.interrupt();
                    readThread = null;
                }
            } else {
                e.printStackTrace();
            }
        }
    }

    public void cancel() {
        canceled = true;
        if (arrayBlockingQueue != null) {
            arrayBlockingQueue.clear();
        }
        if (writeThread != null) {
            writeThread.interrupt();
            writeThread = null;
        }
        if (readThread != null) {
            readThread.interrupt();
            readThread = null;
        }
    }

    protected static class DataItem {
        byte[] array;
        int len;

        DataItem(final byte[] array, final int len) {
            this.array = array;
            this.len = len;
        }
    }
}
