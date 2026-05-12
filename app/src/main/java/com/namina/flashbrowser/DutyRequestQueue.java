package com.namina.flashbrowser;

import android.os.SystemClock;
import android.text.TextUtils;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;

final class DutyRequestQueue {
    private static final int CLAIM_BLOCK_INTERVAL_MS = 14000;

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

        String uniqueKey() {
            return baseUrl + "\n" + cookies;
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
    private final ArrayList<RequestTask> pendingTasks = new ArrayList<>();
    private final CopyOnWriteArrayList<ActiveConnection> activeConnections = new CopyOnWriteArrayList<>();
    private final HashMap<String, Long> cookieCooldownUntilMs = new HashMap<>();
    private final HashSet<String> inFlightCookieKeys = new HashSet<>();

    private Listener listener;
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
    private int frequentRetryIntervalMs;
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

    void startDailyDutyRewards(
            List<CookieTarget> targets,
            int concurrency,
            int requestIntervalMs,
            int frequentRetryIntervalMs
    ) {
        synchronized (lock) {
            if (running || paused || cancelling) {
                appendLogLocked("Queue is already running.");
                notifyListenerLocked();
                return;
            }

            pendingTasks.clear();
            logs.clear();
            cookieCooldownUntilMs.clear();
            inFlightCookieKeys.clear();
            completed = 0;
            failed = 0;
            skipped = 0;
            active = 0;
            total = 0;
            this.requestIntervalMs = Math.max(0, requestIntervalMs);
            this.frequentRetryIntervalMs = Math.max(0, frequentRetryIntervalMs);
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
                pendingTasks.add(RequestTask.discovery(target));
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
                    + ", interval=" + this.requestIntervalMs + "ms"
                    + ", frequentInterval=" + this.frequentRetryIntervalMs + "ms");
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
            pendingTasks.clear();
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
            RequestTask task = awaitNextTask();
            if (task == null) {
                return;
            }

            try {
                if (task.type == REQUEST_TYPE_DISCOVER) {
                    processDiscoverTask(task);
                } else {
                    processRewardTask(task);
                }
            } finally {
                synchronized (lock) {
                    inFlightCookieKeys.remove(task.cookieKey());
                    active -= 1;
                    completed += 1;
                    lock.notifyAll();
                    if (cancelling && active == 0) {
                        finishLocked("Queue cancelled.");
                    } else {
                        notifyListenerLocked();
                    }
                }
            }
        }
    }

    private RequestTask awaitNextTask() {
        while (true) {
            synchronized (lock) {
                while (paused && !cancelling) {
                    try {
                        lock.wait();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return null;
                    }
                }
                if (cancelling) {
                    return null;
                }

                if (pendingTasks.isEmpty()) {
                    if (active == 0) {
                        finishLocked("Queue finished.");
                        return null;
                    }
                    waitLocked(120L);
                    continue;
                }

                long now = SystemClock.uptimeMillis();
                long earliestWakeUptimeMs = nextDispatchUptimeMs > now ? nextDispatchUptimeMs : Long.MAX_VALUE;
                for (int i = 0; i < pendingTasks.size(); i += 1) {
                    RequestTask task = pendingTasks.get(i);
                    String cookieKey = task.cookieKey();
                    if (inFlightCookieKeys.contains(cookieKey)) {
                        continue;
                    }
                    long cooldownUntilMs = cookieCooldownUntilMs.containsKey(cookieKey)
                            ? cookieCooldownUntilMs.get(cookieKey).longValue()
                            : 0L;
                    if (cooldownUntilMs > now) {
                        earliestWakeUptimeMs = Math.min(earliestWakeUptimeMs, cooldownUntilMs);
                        continue;
                    }
                    if (nextDispatchUptimeMs > now) {
                        continue;
                    }

                    pendingTasks.remove(i);
                    active += 1;
                    inFlightCookieKeys.add(cookieKey);
                    nextDispatchUptimeMs = now + requestIntervalMs;
                    notifyListenerLocked();
                    return task;
                }

                long waitMs = 120L;
                if (earliestWakeUptimeMs != Long.MAX_VALUE) {
                    waitMs = Math.max(1L, earliestWakeUptimeMs - now);
                }
                waitLocked(Math.min(waitMs, 500L));
            }
        }
    }

    private void processDiscoverTask(RequestTask task) {
        ActiveConnection activeConnection = new ActiveConnection();
        activeConnections.add(activeConnection);
        try {
            PvzolAmfClient.Response response =
                    PvzolAmfClient.postDutyGetAll(task.target.baseUrl, task.target.cookies, activeConnection);
            if (response.containsFrequentMessage()) {
                handleFrequentResponse(task, response, "api.duty.getAll");
                return;
            }

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
                        pendingTasks.add(RequestTask.reward(task.target, rewardRequest.rewardId, rewardRequest.category));
                        total += 1;
                    }
                    appendLogLocked("Loaded task list for " + task.target.label
                            + ", queued " + plan.rewardRequests.size() + " reward requests.");
                    lock.notifyAll();
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
            if (response.containsFrequentMessage()) {
                handleFrequentResponse(task, response, "api.duty.reward [" + task.rewardId + "," + task.category + "]");
                return;
            }
            if (response.containsCannotClaimMessage()) {
                handleCannotClaimResponse(task, response);
                return;
            }

            boolean success = response.httpStatusCode >= 200
                    && response.httpStatusCode < 300
                    && response.isApplicationSuccess();
            synchronized (lock) {
                if (success) {
                    String rewardSummary = PvzolAmfClient.getRewardSummary(task.rewardId);
                    appendLogLocked(task.target.label + " [" + task.rewardId + "," + task.category + "] success"
                            + (TextUtils.isEmpty(rewardSummary) ? "" : " reward=" + rewardSummary)
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

    private void handleCannotClaimResponse(RequestTask task, PvzolAmfClient.Response response) {
        synchronized (lock) {
            failed += 1;
            long resumeUptimeMs = SystemClock.uptimeMillis() + CLAIM_BLOCK_INTERVAL_MS;
            cookieCooldownUntilMs.put(task.cookieKey(), Long.valueOf(resumeUptimeMs));
            appendLogLocked(task.target.label + " ["
                    + task.rewardId + "," + task.category + "] failed: response contains 不能领取, cooldown "
                    + CLAIM_BLOCK_INTERVAL_MS + "ms"
                    + (TextUtils.isEmpty(response.description) ? "" : ", " + response.description));
            lock.notifyAll();
            notifyListenerLocked();
        }
    }

    private void handleFrequentResponse(RequestTask task, PvzolAmfClient.Response response, String operationLabel) {
        synchronized (lock) {
            failed += 1;
            long resumeUptimeMs = SystemClock.uptimeMillis() + frequentRetryIntervalMs;
            cookieCooldownUntilMs.put(task.cookieKey(), Long.valueOf(resumeUptimeMs));
            if (!cancelling) {
                pendingTasks.add(task);
                total += 1;
            }
            appendLogLocked(task.target.label + " " + operationLabel
                    + " failed: response contains 频繁, cooldown "
                    + frequentRetryIntervalMs + "ms"
                    + (TextUtils.isEmpty(response.description) ? "" : ", " + response.description));
            lock.notifyAll();
            notifyListenerLocked();
        }
    }

    private void finishLocked(String message) {
        running = false;
        paused = false;
        cancelling = false;
        appendLogLocked(message);
        pendingTasks.clear();
        inFlightCookieKeys.clear();
        cookieCooldownUntilMs.clear();
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

    private void waitLocked(long waitMs) {
        try {
            lock.wait(waitMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private StateSnapshot buildSnapshotLocked() {
        return new StateSnapshot(
                running,
                paused,
                cancelling,
                total,
                pendingTasks.size(),
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

        String cookieKey() {
            return target.uniqueKey();
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
