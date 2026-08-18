package com.namina.flashbrowser;

import android.os.SystemClock;
import android.text.TextUtils;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.text.SimpleDateFormat;
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
    private static final List<PvzolAmfClient.RewardRequest> FULL_SWEEP_REWARD_REQUESTS =
            PvzolAmfClient.buildFullSweepRewardRequests();
    private static final SimpleDateFormat DAY_FORMAT =
            new SimpleDateFormat("yyyyMMdd", Locale.US);

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
    private static final int REQUEST_TYPE_CUSTOM = 2;

    private final Object lock = new Object();
    private final ArrayList<String> logs = new ArrayList<>();
    private final ArrayList<RequestTask> pendingTasks = new ArrayList<>();
    private final CopyOnWriteArrayList<ActiveConnection> activeConnections = new CopyOnWriteArrayList<>();
    private final HashMap<String, Long> cookieCooldownUntilMs = new HashMap<>();
    private final HashSet<String> inFlightCookieKeys = new HashSet<>();
    private final HashMap<String, String> medalIntegralDayByKey = new HashMap<>();

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
    private boolean medalRepeatInfinite;
    private List<CookieTarget> medalRepeatTargets = Collections.emptyList();
    private List<FeaturePanelTaskController.MedalBlockRequest> medalRepeatBlocks = Collections.emptyList();

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

    void startRequests(FeaturePanelTaskController.StartRequest request) {
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
            medalIntegralDayByKey.clear();
            completed = 0;
            failed = 0;
            skipped = 0;
            active = 0;
            total = 0;
            this.requestIntervalMs = Math.max(0, request == null ? 0 : request.requestIntervalMs);
            this.frequentRetryIntervalMs = Math.max(0, request == null ? 0 : request.frequentRetryIntervalMs);
            nextDispatchUptimeMs = 0L;
            cancelling = false;
            paused = false;
            medalRepeatInfinite = request != null && request.medalRepeatInfinite;
            medalRepeatTargets = new ArrayList<>();
            medalRepeatBlocks = request == null || request.medalBlockRequests == null
                    ? Collections.emptyList()
                    : new ArrayList<>(request.medalBlockRequests);

            int eligibleTargetCount = 0;
            ArrayList<String> modeLabels = new ArrayList<>();
            if (request != null) {
                if (request.runFullSweep) {
                    modeLabels.add("全任务直领");
                } else if (request.runDailyDuty) {
                    modeLabels.add("领取每日任务");
                }
                if (request.fubenProgressStages != null && !request.fubenProgressStages.isEmpty()) {
                    modeLabels.add("领取副本进度奖励");
                }
                if (request.medalBlockRequests != null && !request.medalBlockRequests.isEmpty()) {
                    modeLabels.add("重复领取勋章奖励");
                }
            }

            List<CookieTarget> targets = request == null ? Collections.emptyList() : request.targets;
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
                eligibleTargetCount += 1;
                if (request != null && request.medalBlockRequests != null && !request.medalBlockRequests.isEmpty()) {
                    medalRepeatTargets.add(target);
                    initializeMedalIntegralDayState(target, request.medalBlockRequests, request.medalDailyIntegralKeys);
                }
                if (request != null && request.runFullSweep) {
                    for (PvzolAmfClient.RewardRequest rewardRequest : FULL_SWEEP_REWARD_REQUESTS) {
                        pendingTasks.add(RequestTask.reward(target, rewardRequest.rewardId, rewardRequest.category));
                        total += 1;
                    }
                } else if (request != null && request.runDailyDuty) {
                    pendingTasks.add(RequestTask.discovery(target));
                    total += 1;
                }
                enqueueFubenProgressTasks(target, request == null ? null : request.fubenProgressStages);
                enqueueMedalRepeatTasks(
                        target,
                        request == null ? null : request.medalBlockRequests,
                        request != null ? request.medalDailyIntegralKeys : null,
                        true
                );
            }

            if (total <= 0) {
                appendLogLocked("No eligible cookie targets.");
                notifyListenerLocked();
                return;
            }

            running = true;
            appendLogLocked("Queue started. mode=" + TextUtils.join(" + ", modeLabels)
                    + ", targets=" + eligibleTargetCount
                    + ", requests=" + total
                    + ", concurrency=" + Math.max(1, request == null ? 1 : request.concurrency)
                    + ", interval=" + this.requestIntervalMs + "ms"
                    + ", frequentInterval=" + this.frequentRetryIntervalMs + "ms");
            int concurrency = Math.max(1, request == null ? 1 : request.concurrency);
            executor = java.util.concurrent.Executors.newFixedThreadPool(concurrency);
            for (int i = 0; i < concurrency; i += 1) {
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
                } else if (task.type == REQUEST_TYPE_REWARD) {
                    processRewardTask(task);
                } else {
                    processCustomTask(task);
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
                        if (medalRepeatInfinite && !medalRepeatTargets.isEmpty() && !medalRepeatBlocks.isEmpty()) {
                            enqueueNextMedalRepeatRoundLocked();
                            lock.notifyAll();
                            notifyListenerLocked();
                            continue;
                        }
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
            if (!plan.hasAnyTaskSection) {
                synchronized (lock) {
                    failed += 1;
                    appendLogLocked("Failed to fetch task list for " + task.target.label
                            + ": no task sections found.");
                }
                return;
            }
            if (!plan.hasMainTask && plan.rewardRequests.isEmpty()) {
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

    private void processCustomTask(RequestTask task) {
        ActiveConnection activeConnection = new ActiveConnection();
        activeConnections.add(activeConnection);
        try {
            PvzolAmfClient.Response response = PvzolAmfClient.postRawRequest(
                    task.target.baseUrl,
                    task.target.cookies,
                    task.rawPayload,
                    activeConnection
            );
            if (response.containsFrequentMessage()) {
                handleFrequentResponse(task, response, task.customOperationLabel);
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
                    appendLogLocked(task.target.label + " " + task.customOperationLabel + " success"
                            + (TextUtils.isEmpty(response.description) ? "" : ": " + response.description));
                } else {
                    failed += 1;
                    appendLogLocked(task.target.label + " " + task.customOperationLabel + " failed"
                            + " (HTTP=" + response.httpStatusCode
                            + (TextUtils.isEmpty(response.description) ? "" : ", " + response.description)
                            + ")");
                }
            }
        } catch (IOException e) {
            synchronized (lock) {
                failed += 1;
                appendLogLocked(task.target.label + " " + task.customOperationLabel + " error: "
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
            appendLogLocked(task.target.label + " " + task.describeOperation()
                    + " failed: response contains 不能领取, cooldown "
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

    private void enqueueFubenProgressTasks(CookieTarget target, List<Integer> stages) {
        if (stages == null || stages.isEmpty()) {
            return;
        }
        ArrayList<Integer> bundledIntegralValues = new ArrayList<>();
        for (Integer stage : stages) {
            if (stage == null) {
                continue;
            }
            for (int i = 0; i < 4; i += 1) {
                bundledIntegralValues.add(Integer.valueOf(stage.intValue()));
            }
        }
        enqueueBundledAwardPacket(target, "integral", bundledIntegralValues, "副本进度 integral 合包");
    }

    private void enqueueMedalRepeatTasks(
            CookieTarget target,
            List<FeaturePanelTaskController.MedalBlockRequest> medalBlocks,
            java.util.Set<String> medalDailyIntegralKeys,
            boolean allowIntegral
    ) {
        if (medalBlocks == null || medalBlocks.isEmpty()) {
            return;
        }
        ArrayList<Integer> bundledIntegralValues = new ArrayList<>();
        ArrayList<String> bundledRewardKeys = new ArrayList<>();
        ArrayList<Integer> bundledMedalValues = new ArrayList<>();
        for (FeaturePanelTaskController.MedalBlockRequest block : medalBlocks) {
            if (block == null) {
                continue;
            }
            int blockNumber = block.blockNumber;
            String dailyIntegralKey = buildMedalDailyIntegralKey(target, blockNumber);
            boolean shouldRunIntegral = allowIntegral
                    && shouldRunMedalIntegral(dailyIntegralKey, medalDailyIntegralKeys);
            if (shouldRunIntegral) {
                for (int i = 0; i < 4; i += 1) {
                    bundledIntegralValues.add(Integer.valueOf(blockNumber));
                }
            }
            bundledRewardKeys.add(blockNumber + ".1");
            for (int rangeIndex = 0; rangeIndex < block.maxClaimRange; rangeIndex += 1) {
                bundledMedalValues.add(Integer.valueOf(blockNumber));
            }
        }
        enqueueBundledAwardPacket(target, "integral", bundledIntegralValues, "勋章 integral 合包");
        enqueueBundledRewardPacket(target, bundledRewardKeys, "勋章 reward 合包");
        enqueueBundledAwardPacket(target, "medal", bundledMedalValues, "勋章 medal 合包");
    }

    private void enqueueNextMedalRepeatRoundLocked() {
        int added = 0;
        for (CookieTarget target : medalRepeatTargets) {
            int before = total;
            enqueueMedalRepeatTasks(target, medalRepeatBlocks, Collections.emptySet(), true);
            added += total - before;
        }
        if (added > 0) {
            appendLogLocked("重复领取勋章奖励开始下一轮。新增请求=" + added);
        }
    }

    private void enqueueBundledAwardPacket(
            CookieTarget target,
            String awardType,
            List<Integer> values,
            String label
    ) {
        if (values == null || values.isEmpty()) {
            return;
        }
        try {
            pendingTasks.add(RequestTask.custom(
                    target,
                    PvzolAmfClient.buildBundledFubenAwardPayload(awardType, values),
                    label + " x" + values.size()
            ));
            total += 1;
        } catch (IOException e) {
            failed += 1;
            appendLogLocked(target.label + " " + label + " payload error: " + e.getMessage());
        }
    }

    private void enqueueBundledRewardPacket(
            CookieTarget target,
            List<String> rewardKeys,
            String label
    ) {
        if (rewardKeys == null || rewardKeys.isEmpty()) {
            return;
        }
        try {
            pendingTasks.add(RequestTask.custom(
                    target,
                    PvzolAmfClient.buildBundledFubenRewardPayload(rewardKeys),
                    label + " x" + rewardKeys.size()
            ));
            total += 1;
        } catch (IOException e) {
            failed += 1;
            appendLogLocked(target.label + " " + label + " payload error: " + e.getMessage());
        }
    }

    private void initializeMedalIntegralDayState(
            CookieTarget target,
            List<FeaturePanelTaskController.MedalBlockRequest> medalBlocks,
            java.util.Set<String> medalDailyIntegralKeys
    ) {
        for (FeaturePanelTaskController.MedalBlockRequest block : medalBlocks) {
            if (block == null) {
                continue;
            }
            String dailyIntegralKey = buildMedalDailyIntegralKey(target, block.blockNumber);
            if (medalDailyIntegralKeys != null && medalDailyIntegralKeys.contains(dailyIntegralKey)) {
                medalIntegralDayByKey.put(dailyIntegralKey, "");
            } else if (!medalIntegralDayByKey.containsKey(dailyIntegralKey)) {
                medalIntegralDayByKey.put(dailyIntegralKey, currentDayString());
            }
        }
    }

    private boolean shouldRunMedalIntegral(String dailyIntegralKey, java.util.Set<String> startPhaseKeys) {
        String today = currentDayString();
        String lastDay = medalIntegralDayByKey.get(dailyIntegralKey);
        if (startPhaseKeys != null && startPhaseKeys.contains(dailyIntegralKey)) {
            medalIntegralDayByKey.put(dailyIntegralKey, today);
            return true;
        }
        if (!today.equals(lastDay)) {
            medalIntegralDayByKey.put(dailyIntegralKey, today);
            return true;
        }
        return false;
    }

    private String currentDayString() {
        synchronized (DAY_FORMAT) {
            return DAY_FORMAT.format(new java.util.Date());
        }
    }

    private String buildMedalDailyIntegralKey(CookieTarget target, int blockNumber) {
        String targetKey = target == null ? "" : target.uniqueKey();
        return targetKey + "#medalIntegral#" + blockNumber;
    }

    private void finishLocked(String message) {
        running = false;
        paused = false;
        cancelling = false;
        appendLogLocked(message);
        pendingTasks.clear();
        inFlightCookieKeys.clear();
        cookieCooldownUntilMs.clear();
        medalIntegralDayByKey.clear();
        medalRepeatInfinite = false;
        medalRepeatTargets = Collections.emptyList();
        medalRepeatBlocks = Collections.emptyList();
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
        final byte[] rawPayload;
        final String customOperationLabel;

        private RequestTask(
                int type,
                CookieTarget target,
                int rewardId,
                int category,
                byte[] rawPayload,
                String customOperationLabel
        ) {
            this.type = type;
            this.target = target;
            this.rewardId = rewardId;
            this.category = category;
            this.rawPayload = rawPayload;
            this.customOperationLabel = customOperationLabel;
        }

        String cookieKey() {
            return target.uniqueKey();
        }

        String describeOperation() {
            if (type == REQUEST_TYPE_REWARD) {
                return "[" + rewardId + "," + category + "]";
            }
            return customOperationLabel == null ? "request" : customOperationLabel;
        }

        static RequestTask discovery(CookieTarget target) {
            return new RequestTask(REQUEST_TYPE_DISCOVER, target, 0, 0, null, null);
        }

        static RequestTask reward(CookieTarget target, int rewardId, int category) {
            return new RequestTask(REQUEST_TYPE_REWARD, target, rewardId, category, null, null);
        }

        static RequestTask custom(CookieTarget target, byte[] rawPayload, String customOperationLabel) {
            return new RequestTask(REQUEST_TYPE_CUSTOM, target, 0, 0, rawPayload, customOperationLabel);
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
