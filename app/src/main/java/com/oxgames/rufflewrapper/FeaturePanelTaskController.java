package com.namina.flashbrowser;

import android.text.TextUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

final class FeaturePanelTaskController {
    static final class StartRequest {
        final int concurrency;
        final int requestIntervalMs;
        final int frequentRetryIntervalMs;
        final List<DutyRequestQueue.CookieTarget> targets;

        StartRequest(
                int concurrency,
                int requestIntervalMs,
                int frequentRetryIntervalMs,
                List<DutyRequestQueue.CookieTarget> targets
        ) {
            this.concurrency = concurrency;
            this.requestIntervalMs = requestIntervalMs;
            this.frequentRetryIntervalMs = frequentRetryIntervalMs;
            this.targets = targets;
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

        if (startCheckedItemsOnly && !dailyDutyChecked) {
            return new BuildResult("No task selected.", null);
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
                        new ArrayList<>(deduplicatedTargets.values())
                )
        );
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
}
