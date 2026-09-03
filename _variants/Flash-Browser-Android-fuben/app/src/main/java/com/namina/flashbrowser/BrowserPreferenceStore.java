package com.namina.flashbrowser;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class BrowserPreferenceStore {
    private static final String PREFS_NAME = "browser_prefs";
    private static final String PREF_ORIENTATION = "orientation_mode";
    private static final String PREF_RUFFLE_FONT_MODE = "ruffle_font_mode";
    private static final String PREF_PANEL_CONCURRENCY = "panel_concurrency";
    private static final String PREF_PANEL_REQUEST_INTERVAL = "panel_request_interval";
    private static final String PREF_PANEL_FREQUENT_RETRY_INTERVAL = "panel_frequent_retry_interval";
    private static final String PREF_PANEL_SELECTED_COOKIE_KEYS = "panel_selected_cookie_keys";
    private static final String PREF_PANEL_SELECT_CURRENT_PAGE_COOKIE = "panel_select_current_page_cookie";
    private static final String PREF_PANEL_TASK_DAILY_DUTY = "panel_task_daily_duty";
    private static final String PREF_PANEL_TASK_DUTY_FULL_SWEEP = "panel_task_duty_full_sweep";
    private static final String PREF_PANEL_TASK_FUBEN_PROGRESS = "panel_task_fuben_progress";
    private static final String PREF_PANEL_TASK_MEDAL_REPEAT = "panel_task_medal_repeat";
    private static final String PREF_PANEL_FUBEN_PROGRESS_STAGE_PREFIX = "panel_fuben_progress_stage_";
    private static final String PREF_PANEL_MEDAL_BLOCK_ENABLED_PREFIX = "panel_medal_block_enabled_";
    private static final String PREF_PANEL_MEDAL_BLOCK_RANGE_PREFIX = "panel_medal_block_range_";
    private static final String PREF_PANEL_FUBEN_SETTINGS_EXPANDED = "panel_fuben_settings_expanded";
    private static final String PREF_PANEL_MEDAL_SETTINGS_EXPANDED = "panel_medal_settings_expanded";
    private static final String PREF_PANEL_MEDAL_REPEAT_INFINITE = "panel_medal_repeat_infinite";
    private static final String PREF_PANEL_MEDAL_DAILY_INTEGRAL_PREFIX = "panel_medal_daily_integral_";
    private static final String PREF_PANEL_REPOSITORY_RECORDS = "panel_repository_records";
    private static final String PREF_WELCOME_DIALOG_DISMISSED = "welcome_dialog_dismissed";
    private static final SimpleDateFormat DAY_FORMAT =
            new SimpleDateFormat("yyyyMMdd", Locale.US);

    private final SharedPreferences preferences;

    BrowserPreferenceStore(Context context) {
        this.preferences = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    int getOrientationMode(int defaultValue) {
        return preferences.getInt(PREF_ORIENTATION, defaultValue);
    }

    void setOrientationMode(int mode) {
        preferences.edit().putInt(PREF_ORIENTATION, mode).apply();
    }

    int getFontMode(int defaultValue) {
        return preferences.getInt(PREF_RUFFLE_FONT_MODE, defaultValue);
    }

    void setFontMode(int mode) {
        preferences.edit().putInt(PREF_RUFFLE_FONT_MODE, mode).apply();
    }

    int getPanelConcurrency(int defaultValue) {
        return Math.max(1, preferences.getInt(PREF_PANEL_CONCURRENCY, defaultValue));
    }

    void setPanelConcurrency(int concurrency) {
        preferences.edit().putInt(PREF_PANEL_CONCURRENCY, Math.max(1, concurrency)).apply();
    }

    int getPanelRequestInterval(int defaultValue) {
        return Math.max(0, preferences.getInt(PREF_PANEL_REQUEST_INTERVAL, defaultValue));
    }

    void setPanelRequestInterval(int intervalMs) {
        preferences.edit().putInt(PREF_PANEL_REQUEST_INTERVAL, Math.max(0, intervalMs)).apply();
    }

    int getPanelFrequentRetryInterval(int defaultValue) {
        return Math.max(0, preferences.getInt(PREF_PANEL_FREQUENT_RETRY_INTERVAL, defaultValue));
    }

    void setPanelFrequentRetryInterval(int intervalMs) {
        preferences.edit().putInt(PREF_PANEL_FREQUENT_RETRY_INTERVAL, Math.max(0, intervalMs)).apply();
    }

    boolean isPanelDailyDutyEnabled() {
        return preferences.getBoolean(PREF_PANEL_TASK_DAILY_DUTY, false);
    }

    void setPanelDailyDutyEnabled(boolean enabled) {
        preferences.edit().putBoolean(PREF_PANEL_TASK_DAILY_DUTY, enabled).apply();
    }

    boolean isPanelDutyFullSweepEnabled() {
        return preferences.getBoolean(PREF_PANEL_TASK_DUTY_FULL_SWEEP, false);
    }

    void setPanelDutyFullSweepEnabled(boolean enabled) {
        preferences.edit().putBoolean(PREF_PANEL_TASK_DUTY_FULL_SWEEP, enabled).apply();
    }

    boolean isPanelFubenProgressEnabled() {
        return preferences.getBoolean(PREF_PANEL_TASK_FUBEN_PROGRESS, false);
    }

    void setPanelFubenProgressEnabled(boolean enabled) {
        preferences.edit().putBoolean(PREF_PANEL_TASK_FUBEN_PROGRESS, enabled).apply();
    }

    boolean isPanelMedalRepeatEnabled() {
        return preferences.getBoolean(PREF_PANEL_TASK_MEDAL_REPEAT, false);
    }

    void setPanelMedalRepeatEnabled(boolean enabled) {
        preferences.edit().putBoolean(PREF_PANEL_TASK_MEDAL_REPEAT, enabled).apply();
    }

    boolean isFubenProgressStageSelected(int stageNumber) {
        return preferences.getBoolean(PREF_PANEL_FUBEN_PROGRESS_STAGE_PREFIX + stageNumber, true);
    }

    void setFubenProgressStageSelected(int stageNumber, boolean selected) {
        preferences.edit().putBoolean(PREF_PANEL_FUBEN_PROGRESS_STAGE_PREFIX + stageNumber, selected).apply();
    }

    boolean isMedalBlockEnabled(int blockNumber) {
        return preferences.getBoolean(PREF_PANEL_MEDAL_BLOCK_ENABLED_PREFIX + blockNumber, false);
    }

    void setMedalBlockEnabled(int blockNumber, boolean enabled) {
        preferences.edit().putBoolean(PREF_PANEL_MEDAL_BLOCK_ENABLED_PREFIX + blockNumber, enabled).apply();
    }

    int getMedalBlockRange(int blockNumber) {
        int stored = preferences.getInt(PREF_PANEL_MEDAL_BLOCK_RANGE_PREFIX + blockNumber, 4);
        if (stored < 1) {
            return 1;
        }
        if (stored > 4) {
            return 4;
        }
        return stored;
    }

    void setMedalBlockRange(int blockNumber, int range) {
        int safeRange = range;
        if (safeRange < 1) {
            safeRange = 1;
        }
        if (safeRange > 4) {
            safeRange = 4;
        }
        preferences.edit().putInt(PREF_PANEL_MEDAL_BLOCK_RANGE_PREFIX + blockNumber, safeRange).apply();
    }

    boolean isMedalRepeatInfiniteEnabled() {
        return preferences.getBoolean(PREF_PANEL_MEDAL_REPEAT_INFINITE, false);
    }

    void setMedalRepeatInfiniteEnabled(boolean enabled) {
        preferences.edit().putBoolean(PREF_PANEL_MEDAL_REPEAT_INFINITE, enabled).apply();
    }

    boolean shouldRunDailyMedalIntegral(String cookieKey, int blockNumber) {
        String key = buildDailyIntegralPreferenceKey(cookieKey, blockNumber);
        String today = DAY_FORMAT.format(new java.util.Date());
        return !today.equals(preferences.getString(key, ""));
    }

    void markDailyMedalIntegralDone(String cookieKey, int blockNumber) {
        String key = buildDailyIntegralPreferenceKey(cookieKey, blockNumber);
        String today = DAY_FORMAT.format(new java.util.Date());
        preferences.edit().putString(key, today).apply();
    }

    private String buildDailyIntegralPreferenceKey(String cookieKey, int blockNumber) {
        String safeCookieKey = cookieKey == null ? "" : Integer.toHexString(cookieKey.hashCode());
        return PREF_PANEL_MEDAL_DAILY_INTEGRAL_PREFIX + safeCookieKey + "_" + blockNumber;
    }

    boolean isFubenSettingsExpanded() {
        return preferences.getBoolean(PREF_PANEL_FUBEN_SETTINGS_EXPANDED, false);
    }

    void setFubenSettingsExpanded(boolean expanded) {
        preferences.edit().putBoolean(PREF_PANEL_FUBEN_SETTINGS_EXPANDED, expanded).apply();
    }

    boolean isMedalSettingsExpanded() {
        return preferences.getBoolean(PREF_PANEL_MEDAL_SETTINGS_EXPANDED, false);
    }

    void setMedalSettingsExpanded(boolean expanded) {
        preferences.edit().putBoolean(PREF_PANEL_MEDAL_SETTINGS_EXPANDED, expanded).apply();
    }

    boolean isCurrentPageCookieSelectedByDefault() {
        return preferences.getBoolean(PREF_PANEL_SELECT_CURRENT_PAGE_COOKIE, true);
    }

    void setCurrentPageCookieSelectedByDefault(boolean selected) {
        preferences.edit().putBoolean(PREF_PANEL_SELECT_CURRENT_PAGE_COOKIE, selected).apply();
    }

    Set<String> getSelectedCookieKeys() {
        return new HashSet<>(preferences.getStringSet(PREF_PANEL_SELECTED_COOKIE_KEYS, new HashSet<>()));
    }

    void setSelectedCookieKeys(Set<String> selectedKeys) {
        preferences.edit().putStringSet(PREF_PANEL_SELECTED_COOKIE_KEYS, new HashSet<>(selectedKeys)).apply();
    }

    String getRepositoryRecordsJson() {
        return preferences.getString(PREF_PANEL_REPOSITORY_RECORDS, "{}");
    }

    void setRepositoryRecordsJson(String value) {
        preferences.edit().putString(PREF_PANEL_REPOSITORY_RECORDS, value == null ? "{}" : value).apply();
    }

    boolean shouldShowWelcomeDialog() {
        return !preferences.getBoolean(PREF_WELCOME_DIALOG_DISMISSED, false);
    }

    void setWelcomeDialogDismissed(boolean dismissed) {
        preferences.edit().putBoolean(PREF_WELCOME_DIALOG_DISMISSED, dismissed).apply();
    }
}
