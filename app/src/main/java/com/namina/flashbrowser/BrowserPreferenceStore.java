package com.namina.flashbrowser;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
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
    private static final String PREF_PANEL_REPOSITORY_RECORDS = "panel_repository_records";

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
}
