package com.oxgames.rufflewrapper;

import android.os.SystemClock;
import android.text.TextUtils;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;

final class DutyRequestQueue {

    interface Listener {
        void onQueueUpdated(StateSnapshot snapshot);
    }

    static final class CookieTarget {
        final String label;
        final String baseUrl;
        final String cookies;

        CookieTarget(String label, String baseUrl, String cookies) {
            this.label = label;
            this.baseUrl = baseUrl;
            this.cookies = cookies;
        }
    }

    static final class StateSnapshot {
        final boolean running;
        final boolean paused;
        final boolean cancelling;
        final int total;
        final int queued;
        final int active;
        final int completed;
        final int failed;
        final int skipped;
        final List<String> logs;

        StateSnapshot(
                boolean running,
                boolean paused,
                boolean cancelling,
                int total,
                int queued,
                int active,
                int completed,
                int failed,
                int skipped,
                List<String> logs
        ) {
            this.running = running;
            this.paused = paused;
            this.cancelling = cancelling;
            this.total = total;
            this.queued = queued;
            this.active = active;
            this.completed = completed;
            this.failed = failed;
            this.skipped = skipped;
            this.logs = logs;
        }
    }

    private static final int REQUEST_TYPE_DISCOVER = 0;
    private static final int REQUEST_TYPE_REWARD = 1;

    private final Object lock = new Object();
    private final ArrayList<String> logs = new ArrayList<>();
    private final CopyOnWriteArrayList<ActiveConnection> activeConnections = new CopyOnWriteArrayList<>();

    private Listener listener;
    private BlockingQueue<RequestTask> queue;
    private ExecutorService executor;
    private boolean running;
    private boolean paused;
    private boolean cancelling;
    private int total;
    private int completed;
    private int failed;
    private int skipped;
    private int active;
    private int requestIntervalMs;
    private long nextDispatchUptimeMs;

    void setListener(Listener listener) {
        synchronized (lock) {
            this.listener = listener;
        }
        notifyListener();
    }

    boolean isBusy() {
        synchronized (lock) {
            return running || paused || cancelling;
        }
    }

    void startDailyDutyRewards(List<CookieTarget> targets, int concurrency, int requestIntervalMs) {
        synchronized (lock) {
            if (running || paused || cancelling) {
                appendLogLocked("Queue is already running.");
                notifyListenerLocked();
                return;
            }

            queue = new LinkedBlockingQueue<>();
            logs.clear();
            completed = 0;
            failed = 0;
            skipped = 0;
            active = 0;
            total = 0;
            this.requestIntervalMs = Math.max(0, requestIntervalMs);
            nextDispatchUptimeMs = 0L;
            cancelling = false;
            paused = false;

            for (CookieTarget target : targets) {
                if (target == null || TextUtils.isEmpty(target.baseUrl) || TextUtils.isEmpty(target.cookies)) {
                    skipped += 1;
                    continue;
                }
                if (target.baseUrl.toLowerCase(Locale.US).contains("pvzol.org")) {
                    skipped += 1;
                    appendLogLocked("Skip " + target.label + ": base URL contains pvzol.org");
                    continue;
                }
                queue.add(RequestTask.discovery(target));
                total += 1;
            }

            if (total <= 0) {
                appendLogLocked("No eligible cookie targets.");
                notifyListenerLocked();
                return;
            }

            running = true;
            appendLogLocked("Queue started. targets=" + total
                    + ", concurrency=" + Math.max(1, concurrency)
                    + ", interval=" + this.requestIntervalMs + "ms");
            executor = java.util.concurrent.Executors.newFixedThreadPool(Math.max(1, concurrency));
            for (int i = 0; i < Math.max(1, concurrency); i += 1) {
                executor.execute(this::workerLoop);
            }
            notifyListenerLocked();
        }
    }

    void pause() {
        synchronized (lock) {
            if (!running || paused) {
                return;
            }
            paused = true;
            appendLogLocked("Queue paused.");
            notifyListenerLocked();
        }
    }

    void resume() {
        synchronized (lock) {
            if (!running || !paused) {
                return;
            }
            paused = false;
            lock.notifyAll();
            appendLogLocked("Queue resumed.");
            notifyListenerLocked();
        }
    }

    void cancel() {
        synchronized (lock) {
            if (!running && !paused && !cancelling) {
                return;
            }
            cancelling = true;
            paused = false;
            if (queue != null) {
                queue.clear();
            }
            lock.notifyAll();
            appendLogLocked("Queue cancelling...");
        }

        for (ActiveConnection activeConnection : activeConnections) {
            activeConnection.cancel();
        }
        ExecutorService currentExecutor;
        synchronized (lock) {
            currentExecutor = executor;
        }
        if (currentExecutor != null) {
            currentExecutor.shutdownNow();
        }
        notifyListener();
    }

    StateSnapshot snapshot() {
        synchronized (lock) {
            return buildSnapshotLocked();
        }
    }

    private void workerLoop() {
        while (true) {
            RequestTask task;
            synchronized (lock) {
                while (paused && !cancelling) {
                    try {
                        lock.wait();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
                if (cancelling) {
                    break;
                }

                task = queue == null ? null : queue.poll();
                if (task == null) {
                    if (active == 0) {
                        finishLocked("Queue finished.");
                        return;
                    }
                } else {
                    active += 1;
                    notifyListenerLocked();
                }
            }

            if (task == null) {
                try {
                    Thread.sleep(120L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                continue;
            }

            if (!awaitDispatchWindow()) {
                break;
            }

            try {
                if (task.type == REQUEST_TYPE_DISCOVER) {
                    processDiscoverTask(task);
                } else {
                    processRewardTask(task);
                }
            } finally {
                synchronized (lock) {
                    active -= 1;
                    completed += 1;
                    if (cancelling && active == 0) {
                        finishLocked("Queue cancelled.");
                    } else {
                        notifyListenerLocked();
                    }
                }
            }
        }

        synchronized (lock) {
            if (active == 0) {
                finishLocked("Queue cancelled.");
            }
        }
    }

    private void processDiscoverTask(RequestTask task) {
        ActiveConnection activeConnection = new ActiveConnection();
        activeConnections.add(activeConnection);
        try {
            PvzolAmfClient.Response response =
                    PvzolAmfClient.postDutyGetAll(task.target.baseUrl, task.target.cookies, activeConnection);
            boolean success = response.httpStatusCode >= 200
                    && response.httpStatusCode < 300
                    && response.isApplicationSuccess();
            if (!success) {
                synchronized (lock) {
                    failed += 1;
                    appendLogLocked("Failed to fetch task list for " + task.target.label
                            + " (HTTP=" + response.httpStatusCode
                            + (TextUtils.isEmpty(response.description) ? "" : ", " + response.description)
                            + ")");
                }
                return;
            }

            PvzolAmfClient.DutyTaskPlan plan = PvzolAmfClient.planDutyRewardRequests(response.decodedValue);
            if (!plan.hasMainTask) {
                synchronized (lock) {
                    skipped += 1;
                    appendLogLocked("No mainTask found for " + task.target.label + ", skip rewards.");
                }
                return;
            }

            synchronized (lock) {
                if (plan.rewardRequests.isEmpty()) {
                    appendLogLocked("No reward request generated for " + task.target.label + ".");
                } else {
                    for (PvzolAmfClient.RewardRequest rewardRequest : plan.rewardRequests) {
                        queue.add(RequestTask.reward(task.target, rewardRequest.rewardId, rewardRequest.category));
                        total += 1;
                    }
                    appendLogLocked("Loaded task list for " + task.target.label
                            + ", queued " + plan.rewardRequests.size() + " reward requests.");
                    notifyListenerLocked();
                }
            }
        } catch (IOException e) {
            synchronized (lock) {
                failed += 1;
                appendLogLocked("Failed to fetch task list for " + task.target.label + ": " + e.getMessage());
            }
        } finally {
            activeConnections.remove(activeConnection);
        }
    }

    private void processRewardTask(RequestTask task) {
        ActiveConnection activeConnection = new ActiveConnection();
        activeConnections.add(activeConnection);
        try {
            PvzolAmfClient.Response response = PvzolAmfClient.postDutyReward(
                    task.target.baseUrl,
                    task.target.cookies,
                    task.rewardId,
                    task.category,
                    activeConnection
            );
            boolean success = response.httpStatusCode >= 200
                    && response.httpStatusCode < 300
                    && response.isApplicationSuccess();
            synchronized (lock) {
                if (success) {
                    appendLogLocked(task.target.label + " [" + task.rewardId + "," + task.category + "] success"
                            + (TextUtils.isEmpty(response.description) ? "" : ": " + response.description));
                } else {
                    failed += 1;
                    appendLogLocked(task.target.label + " [" + task.rewardId + "," + task.category + "] failed"
                            + " (HTTP=" + response.httpStatusCode
                            + (TextUtils.isEmpty(response.description) ? "" : ", " + response.description)
                            + ")");
                }
            }
        } catch (IOException e) {
            synchronized (lock) {
                failed += 1;
                appendLogLocked(task.target.label + " [" + task.rewardId + "," + task.category + "] error: "
                        + e.getMessage());
            }
        } finally {
            activeConnections.remove(activeConnection);
        }
    }

    private boolean awaitDispatchWindow() {
        while (true) {
            long delayMs;
            synchronized (lock) {
                while (paused && !cancelling) {
                    try {
                        lock.wait();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return false;
                    }
                }
                if (cancelling) {
                    return false;
                }
                long now = SystemClock.uptimeMillis();
                long earliest = Math.max(now, nextDispatchUptimeMs);
                delayMs = earliest - now;
                if (delayMs <= 0L) {
                    nextDispatchUptimeMs = now + requestIntervalMs;
                    return true;
                }
            }
            try {
                Thread.sleep(Math.min(delayMs, 250L));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
    }

    private void finishLocked(String message) {
        running = false;
        paused = false;
        cancelling = false;
        appendLogLocked(message);
        if (executor != null) {
            executor.shutdown();
            executor = null;
        }
        notifyListenerLocked();
    }

    private void appendLogLocked(String message) {
        if (logs.size() >= 160) {
            logs.remove(0);
        }
        logs.add(message);
    }

    private StateSnapshot buildSnapshotLocked() {
        int queued = queue == null ? 0 : queue.size();
        return new StateSnapshot(
                running,
                paused,
                cancelling,
                total,
                queued,
                active,
                completed,
                failed,
                skipped,
                Collections.unmodifiableList(new ArrayList<>(logs))
        );
    }

    private void notifyListener() {
        Listener currentListener;
        StateSnapshot snapshot;
        synchronized (lock) {
            currentListener = listener;
            snapshot = buildSnapshotLocked();
        }
        if (currentListener != null) {
            currentListener.onQueueUpdated(snapshot);
        }
    }

    private void notifyListenerLocked() {
        Listener currentListener = listener;
        if (currentListener != null) {
            currentListener.onQueueUpdated(buildSnapshotLocked());
        }
    }

    private static final class RequestTask {
        final int type;
        final CookieTarget target;
        final int rewardId;
        final int category;

        private RequestTask(int type, CookieTarget target, int rewardId, int category) {
            this.type = type;
            this.target = target;
            this.rewardId = rewardId;
            this.category = category;
        }

        static RequestTask discovery(CookieTarget target) {
            return new RequestTask(REQUEST_TYPE_DISCOVER, target, 0, 0);
        }

        static RequestTask reward(CookieTarget target, int rewardId, int category) {
            return new RequestTask(REQUEST_TYPE_REWARD, target, rewardId, category);
        }
    }

    private static final class ActiveConnection implements PvzolAmfClient.ActiveCall {
        private volatile HttpURLConnection connection;
        private volatile boolean cancelled;

        @Override
        public void bind(HttpURLConnection connection) {
            this.connection = connection;
            if (cancelled && connection != null) {
                connection.disconnect();
            }
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }

        void cancel() {
            cancelled = true;
            HttpURLConnection currentConnection = connection;
            if (currentConnection != null) {
                currentConnection.disconnect();
            }
        }
    }
}
