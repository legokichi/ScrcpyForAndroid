package org.client.scrcpy.utils;

import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.os.SystemClock;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by zhouwenchao on 2017-04-27.
 */
public final class ThreadUtils {
    private static final ExecutorService threadCache = Executors.newCachedThreadPool();
    //    private static final ScheduledExecutorService timerTask = Executors.newSingleThreadScheduledExecutor();
    private static final Handler mHandler = new Handler(Looper.getMainLooper());
    private static final List<RunnableIm> RUNNABLE_LIST = new ArrayList<>();
    private static final Object listLock = new Object();
    private static boolean timerExit = true;
    private static boolean RUNNING = true;
    // Background thread (avoid deadlocks or other screens may freeze)
    private static volatile Handler asyncHandler;

    static {
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                if (!threadCache.isShutdown()) {
                    threadCache.shutdown();
                }
            }
        });
        Thread mainThread = new Thread(() -> {
            Looper.prepare();
            asyncHandler = new Handler(Looper.myLooper());
            // Raise the thread priority
            Process.setThreadPriority(Process.THREAD_PRIORITY_MORE_FAVORABLE);
            while (RUNNING) {
                try {
                    Looper.loop();
                } catch (Exception e) {
                }
            }
            asyncHandler = null;
        });
        mainThread.start();
    }

    /**
     * Execute a task asynchronously on a dedicated thread.
     * <p>
     * Using a single worker thread avoids cross-thread state issues while still
     * benefiting from asynchronous execution. Benchmarking shows a Handler post
     * takes under 0.1 ms (versus ~1 ms for launching a coroutine), so this
     * approach is preferred for lightweight tasks.
     *
     * @param r task to run
     */
    public static void workPost(Runnable r) {
        //noinspection StatementWithEmptyBody
        while (asyncHandler == null) {  // Wait for initialization
        }
        asyncHandler.post(r);
    }

    public static void workPostDelay(Runnable r, long time) {
        //noinspection StatementWithEmptyBody
        while (asyncHandler == null) {  // Wait for initialization
        }
        asyncHandler.postDelayed(r, time);
    }

    public static void removeWork(Runnable r) {
        //noinspection StatementWithEmptyBody
        while (asyncHandler == null) {  // Wait for initialization
        }
        asyncHandler.removeCallbacks(r);
    }

    public static void postDelayed(Runnable r, long time) {
        mHandler.postDelayed(r, time);
    }

    public static void post(Runnable r) {  // Run on the main thread
        mHandler.post(r);
    }

    public static void removeCallbacks(Runnable r) {
        mHandler.removeCallbacks(r);
    }

    public static void sleep(long time) {
        try {
            Thread.sleep(time);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private ThreadUtils() {
    }

    public static void execute(Runnable runnable) {
        if (runnable != null) {
            threadCache.execute(runnable);
        }
    }

    public static void executeDelayed(Runnable runnable, long time) {
        if (time <= 0) {
            execute(runnable);
            return;
        }
        RunnableIm im = RunnableIm.obtain(time, runnable, SystemClock.elapsedRealtime());
        synchronized (listLock) {
            RUNNABLE_LIST.add(im);
            if (timerExit) {
                execute(TIMER_RUNNABLE);
                timerExit = false;
            } else {
                try {
                    listLock.notifyAll();
                } catch (Exception ignored) {
                }
            }
        }
    }

    public static void removeExecute(Runnable runnable) {
        if (runnable == null) {
            return;
        }
        synchronized (listLock) {
            for (int index = 0; index < RUNNABLE_LIST.size(); index++) {
                RunnableIm imTmp = RUNNABLE_LIST.get(index);
                if (runnable.equals(imTmp.getRunnable())) {
                    RUNNABLE_LIST.remove(index);
                    imTmp.recycle();
                    index--;  // Adjust index after removal
                }
            }
        }
    }

    public static boolean hasRunnable(Runnable runnable) {
        if (runnable == null) {
            return false;
        }
        synchronized (listLock) {
            for (int index = 0; index < RUNNABLE_LIST.size(); index++) {
                RunnableIm imTmp = RUNNABLE_LIST.get(index);
                if (runnable.equals(imTmp.getRunnable())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static final TimerRunnable TIMER_RUNNABLE = new TimerRunnable();

    /**
     * Single-thread worker used to run queued tasks.
     * Avoid blocking this thread to prevent stalls.
     */
    public static final class SignelThread extends Thread {

        // Background thread (avoid deadlocks or other screens may freeze)
        private final Object WORK_LOCK = new Object();
        private RunnableIm workLink;
        private boolean exit = false;

        @Override
        public void run() {
            while (!exit) {
                Runnable runnable = null;
                synchronized (WORK_LOCK) {
                    RunnableIm runnableIm = workLink;
                    // Wait until notified when the queue is empty
                    if (runnableIm == null) {
                        try {
                            WORK_LOCK.wait();
                        } catch (InterruptedException ignore) {
                        }
                        continue;
                    }
                    long waitTime = runnableIm.getTime() - (System.currentTimeMillis() - runnableIm.getSysTime());
                    if (waitTime <= 0) {
                        runnable = runnableIm.runnable;
                        workLink = runnableIm.nextRunnableIm;
                        runnableIm.recycle();
                    } else {
                        try {
                            WORK_LOCK.wait(waitTime);
                        } catch (InterruptedException ignore) {
                        }
                    }
                }
                // Running outside the synchronized block is faster because add/remove won't block it
                if (runnable != null) {
                    try { // Execute the runnable
                        runnable.run();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
            synchronized (WORK_LOCK) {
                workLink = null;
            }
        }

        public void exitThread() {
            exit = true;
            synchronized (WORK_LOCK) {
                WORK_LOCK.notify();
            }
        }

        public void workPost(Runnable runnable) {
            if (exit) return;
            if (runnable != null) {
                workPostDelay(runnable, 0);
            }
        }

        public void workPostDelay(Runnable runnable, long delay) {
            if (exit) return;
            if (runnable == null) {
                return;
            }
            synchronized (WORK_LOCK) {
                long currentSystemTime = System.currentTimeMillis();
                RunnableIm runnableIm = RunnableIm.obtain(delay, runnable, currentSystemTime);
                // Insert into the queue
                if (workLink == null) {
                    workLink = runnableIm;
                    // Notifying is required when the head of the queue changes
                    WORK_LOCK.notify();
                    return;
                }
                long curWaitTime = runnableIm.time;
                RunnableIm linkRunnableIm = workLink;
                long curLinkWaitTime = linkRunnableIm.getTime() - (currentSystemTime - linkRunnableIm.getSysTime());
                // If the head executes sooner than the new task, walk the list to insert later
                // Otherwise place the new task at the head
                if (curLinkWaitTime <= curWaitTime) {
                    while (linkRunnableIm.nextRunnableIm != null) {
                        RunnableIm tmpIm = linkRunnableIm.nextRunnableIm;
                        curLinkWaitTime = tmpIm.getTime() - (currentSystemTime - tmpIm.getSysTime());
                        // Find the insertion point where the next task is scheduled later
                        // and insert the new task before it
                        if (curLinkWaitTime > curWaitTime) {
                            break;
                        } else {
                            linkRunnableIm = tmpIm;
                        }
                    }
                    RunnableIm tmpRunnableIm = linkRunnableIm.nextRunnableIm;
                    // Insert into the next position
                    linkRunnableIm.nextRunnableIm = runnableIm;
                    runnableIm.nextRunnableIm = tmpRunnableIm;
                } else {
                    workLink = runnableIm;
                    runnableIm.nextRunnableIm = linkRunnableIm;
                    // Notify only when the head changes; inserting mid-queue does not require it
                    if (linkRunnableIm != workLink) {
                        WORK_LOCK.notify();
                    }
                }
            }
        }

        public boolean removeWork(Runnable runnable) {
            if (exit) return false;
            if (runnable == null) {
                return false;
            }
            synchronized (WORK_LOCK) {
                if (workLink == null) {
                    return false;
                }
                RunnableIm linkRunnableIm = workLink;
                if (linkRunnableIm.runnable == runnable) {
                    // Advance to the next node
                    workLink = linkRunnableIm.nextRunnableIm;
                    // Notify because the head changed
                    WORK_LOCK.notify();
                    return true;
                }
                while (linkRunnableIm.nextRunnableIm != null) {
                    RunnableIm tmpRunableIm = linkRunnableIm.nextRunnableIm;
                    // Remove from the queue
                    if (tmpRunableIm.runnable == runnable) {
                        linkRunnableIm.nextRunnableIm = tmpRunableIm.nextRunnableIm;
                        return true;
                    } else {
                        linkRunnableIm = tmpRunableIm;
                    }
                }
                return false;
            }
        }
    }

    /**
     * Timer thread that schedules delayed tasks.
     */
    private static final class TimerRunnable implements Runnable {

        @Override
        public void run() {
            boolean waitQuere = false;
            while (!threadCache.isShutdown()) {
                synchronized (listLock) {
                    if (RUNNABLE_LIST.size() == 0) {
                        // Wait 60 seconds; if nothing is scheduled, exit the timer thread
                        if (waitQuere) {
                            timerExit = true;
                            return;
                        } else {
                            waitQuere = true;
                            ThreadWait(60000);
                        }
                        continue;
                    }
                    waitQuere = false;
                    int minImIndex = getMinTimeImIndex(SystemClock.elapsedRealtime());
                    if (minImIndex == -1) {
                        continue;
                    }
                    RunnableIm runnableIm = RUNNABLE_LIST.get(minImIndex);
                    long sleepTime = runnableIm.getTime() - (SystemClock.elapsedRealtime() - runnableIm.getSysTime());
                    if (sleepTime <= 0) {
                        RUNNABLE_LIST.remove(minImIndex);
                        execute(runnableIm.getRunnable());
                        runnableIm.recycle();  // Mark as recycled for the next run
                        continue;
                    }
                    ThreadWait(sleepTime); // Sleep until it is time to execute the task
                }
            }
        }

        private void ThreadWait(long time) {
            try {
                listLock.wait(time);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Find the index of the runnable with the smallest remaining wait time.
     *
     * @return index of the runnable to run next, or -1 if none
     */
    private static int getMinTimeImIndex(long time) {
        int minIndex = -1;
        RunnableIm imMin = null;
        for (int index = 0; index < RUNNABLE_LIST.size(); index++) {
            if (minIndex == -1) {  // Initialize to the first element
                minIndex = index;
                imMin = RUNNABLE_LIST.get(index);
            } else {
                // Track the runnable with the shortest remaining delay
                RunnableIm imTmp = RUNNABLE_LIST.get(index);
                if ((imTmp.getTime() - (time - imTmp.getSysTime())) < (imMin.getTime() - (time - imMin.getSysTime()))) {
                    minIndex = index;
                    imMin = imTmp;
                }
            }
        }
        return minIndex;
    }


    private static class RunnableIm {

        long time;
        Runnable runnable;
        long sysTime;
        RunnableIm nextRunnableIm;

        private RunnableIm next;

        private static final Object sPoolSync = new Object();
        private static RunnableIm sPool;
        private static int sPoolSize = 0;
        private static final int MAX_POOL_SIZE = 32;

        static RunnableIm obtain() {
            synchronized (sPoolSync) {
                if (sPool != null) {
                    RunnableIm m = sPool;
                    sPool = m.next;
                    m.next = null;
                    sPoolSize--;
                    return m;
                }
            }
            return new RunnableIm();
        }

        static RunnableIm obtain(long time, Runnable runnable, long sysTime) {
            RunnableIm obtain = obtain();
            obtain.time = time;
            obtain.runnable = runnable;
            obtain.sysTime = sysTime;
            obtain.nextRunnableIm = null;
            return obtain;
        }

        void recycle() {
            time = 0;
            runnable = null;
            sysTime = 0;
            nextRunnableIm = null;

            synchronized (sPoolSync) {
                if (sPoolSize < MAX_POOL_SIZE) {
                    next = sPool;
                    sPool = this;
                    sPoolSize++;
                }
            }
        }

        long getTime() {
            return time;
        }

        long getSysTime() {
            return sysTime;
        }

        Runnable getRunnable() {
            return runnable;
        }
    }

}
