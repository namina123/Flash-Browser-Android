package com.namina.flashbrowser;

import android.text.TextUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

final class FeaturePanelTaskController {
    enum TaskMode {
        DAILY_DUTY,
        FULL_SWEEP,
        FUBEN_PROGRESS,
        MEDAL_REPEAT
    }

    static final class MedalBlockRequest {
        final int blockNumber;
        final int maxClaimRange;

        MedalBlockRequest(int blockNumber, int maxClaimRange) {
            this.blockNumber = blockNumber;
            this.maxClaimRange = maxClaimRange;
        }
    }

    static final class StartRequest {
        final int concurrency;
        final int requestIntervalMs;
        final int frequentRetryIntervalMs;
        final List<DutyRequestQueue.CookieTarget> targets;
        final boolean runDailyDuty;
        final boolean runFullSweep;
        final List<Integer> fubenProgressStages;
        final List<MedalBlockRequest> medalBlockRequests;
        final boolean medalRepeatInfinite;
        final Set<String> medalDailyIntegralKeys;

        StartRequest(
                int concurrency,
                int requestIntervalMs,
                int frequentRetryIntervalMs,
                List<DutyRequestQueue.CookieTarget> targets,
                boolean runDailyDuty,
                boolean runFullSweep,
                List<Integer> fubenProgressStages,
                List<MedalBlockRequest> medalBlockRequests,
                boolean medalRepeatInfinite,
                Set<String> medalDailyIntegralKeys
        ) {
            this.concurrency = concurrency;
            this.requestIntervalMs = requestIntervalMs;
            this.frequentRetryIntervalMs = frequentRetryIntervalMs;
            this.targets = targets;
            this.runDailyDuty = runDailyDuty;
            this.runFullSweep = runFullSweep;
            this.fubenProgressStages = fubenProgressStages;
            this.medalBlockRequests = medalBlockRequests;
            this.medalRepeatInfinite = medalRepeatInfinite;
            this.medalDailyIntegralKeys = medalDailyIntegralKeys;
        }
    }

    static final class BuildResult {
        final String errorMessage;
        final StartRequest request;

        BuildResult(String errorMessage, StartRequest request) {
            this.errorMessage = errorMessage;
            this.request = request;
        }
    }

    private final BrowserPreferenceStore preferenceStore;

    FeaturePanelTaskController(BrowserPreferenceStore preferenceStore) {
        this.preferenceStore = preferenceStore;
    }

    int getSavedConcurrency() {
        return preferenceStore.getPanelConcurrency(1);
    }

    int getSavedRequestInterval() {
        return preferenceStore.getPanelRequestInterval(700);
    }

    int getSavedFrequentRetryInterval() {
        return preferenceStore.getPanelFrequentRetryInterval(14000);
    }

    BuildResult buildStartRequest(
            List<FeatureCookieChoice> choices,
            String concurrencyValue,
            String requestIntervalValue,
            String frequentRetryIntervalValue,
            boolean dailyDutyChecked,
            boolean fullSweepChecked,
            boolean fubenProgressChecked,
            boolean[] fubenProgressSelections,
            boolean medalRepeatChecked,
            boolean[] medalBlockSelections,
            String[] medalBlockRangeValues,
            boolean medalRepeatInfinite,
            boolean startCheckedItemsOnly
    ) {
        int concurrency = parsePositiveInt(concurrencyValue, getSavedConcurrency());
        int requestIntervalMs = parseNonNegativeInt(requestIntervalValue, getSavedRequestInterval());
        int frequentRetryIntervalMs = parseNonNegativeInt(
                frequentRetryIntervalValue,
                getSavedFrequentRetryInterval()
        );

        preferenceStore.setPanelConcurrency(concurrency);
        preferenceStore.setPanelRequestInterval(requestIntervalMs);
        preferenceStore.setPanelFrequentRetryInterval(frequentRetryIntervalMs);

        boolean runFullSweep = fullSweepChecked;
        boolean runDailyDuty = dailyDutyChecked && !runFullSweep;
        List<Integer> fubenProgressStages = collectSelectedStages(fubenProgressSelections);
        List<MedalBlockRequest> medalBlockRequests = collectMedalBlockRequests(
                medalBlockSelections,
                medalBlockRangeValues
        );

        if (startCheckedItemsOnly
                && !runDailyDuty
                && !runFullSweep
                && !fubenProgressChecked
                && !medalRepeatChecked) {
            return new BuildResult("No task selected.", null);
        }
        if (fubenProgressChecked && fubenProgressStages.isEmpty()) {
            return new BuildResult("请先为副本进度奖励勾选至少一个数字。", null);
        }
        if (medalRepeatChecked && medalBlockRequests.isEmpty()) {
            return new BuildResult("请先为勋章奖励勾选至少一个块。", null);
        }

        LinkedHashMap<String, DutyRequestQueue.CookieTarget> deduplicatedTargets = new LinkedHashMap<>();
        for (FeatureCookieChoice choice : choices) {
            if (!choice.selected || TextUtils.isEmpty(choice.baseUrl) || TextUtils.isEmpty(choice.cookies)) {
                continue;
            }
            String key = choice.baseUrl + "\n" + choice.cookies;
            if (!deduplicatedTargets.containsKey(key)) {
                deduplicatedTargets.put(key, new DutyRequestQueue.CookieTarget(
                        choice.currentPage ? "当前页面 Cookie" : choice.label,
                        choice.baseUrl,
                        choice.cookies
                ));
            }
        }

        if (deduplicatedTargets.isEmpty()) {
            return new BuildResult("请先勾选至少一个可用 Cookie。", null);
        }

        return new BuildResult(
                null,
                new StartRequest(
                        concurrency,
                        requestIntervalMs,
                        frequentRetryIntervalMs,
                        new ArrayList<>(deduplicatedTargets.values()),
                        runDailyDuty,
                        runFullSweep,
                        fubenProgressChecked ? fubenProgressStages : new ArrayList<>(),
                        medalRepeatChecked ? medalBlockRequests : new ArrayList<>(),
                        medalRepeatChecked && medalRepeatInfinite,
                        new HashSet<>()
                )
        );
    }

    private List<Integer> collectSelectedStages(boolean[] selections) {
        ArrayList<Integer> result = new ArrayList<>();
        if (selections == null) {
            return result;
        }
        for (int i = 0; i < selections.length; i += 1) {
            if (selections[i]) {
                result.add(Integer.valueOf(i + 1));
            }
        }
        return result;
    }

    private List<MedalBlockRequest> collectMedalBlockRequests(
            boolean[] blockSelections,
            String[] rangeValues
    ) {
        ArrayList<MedalBlockRequest> result = new ArrayList<>();
        if (blockSelections == null) {
            return result;
        }
        for (int i = 0; i < blockSelections.length; i += 1) {
            if (!blockSelections[i]) {
                continue;
            }
            String rangeValue = rangeValues != null && i < rangeValues.length ? rangeValues[i] : null;
            int maxClaimRange = parseRangeInt(rangeValue, 4, 1, 4);
            result.add(new MedalBlockRequest(i + 1, maxClaimRange));
        }
        return result;
    }

    private int parsePositiveInt(String value, int fallback) {
        if (TextUtils.isEmpty(value)) {
            return Math.max(1, fallback);
        }
        try {
            return Math.max(1, Integer.parseInt(value.trim()));
        } catch (Exception e) {
            return Math.max(1, fallback);
        }
    }

    private int parseNonNegativeInt(String value, int fallback) {
        if (TextUtils.isEmpty(value)) {
            return Math.max(0, fallback);
        }
        try {
            return Math.max(0, Integer.parseInt(value.trim()));
        } catch (Exception e) {
            return Math.max(0, fallback);
        }
    }

    private int parseRangeInt(String value, int fallback, int min, int max) {
        int parsed = fallback;
        if (!TextUtils.isEmpty(value)) {
            try {
                parsed = Integer.parseInt(value.trim());
            } catch (Exception ignored) {
                parsed = fallback;
            }
        }
        if (parsed < min) {
            return min;
        }
        if (parsed > max) {
            return max;
        }
        return parsed;
    }
}
