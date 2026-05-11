package com.namina.flashbrowser;

import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class FeaturePanelCookieController {
    private final Context context;
    private final BrowserPreferenceStore preferenceStore;
    private final CookieProfileManager cookieProfileManager;

    FeaturePanelCookieController(
            Context context,
            BrowserPreferenceStore preferenceStore,
            CookieProfileManager cookieProfileManager
    ) {
        this.context = context.getApplicationContext();
        this.preferenceStore = preferenceStore;
        this.cookieProfileManager = cookieProfileManager;
    }

    void renderChoices(
            LinearLayout container,
            TextView hintText,
            Button storageAccessButton,
            List<FeatureCookieChoice> choices,
            FeatureCookieChoice currentPageChoice,
            Runnable onChoicesChanged
    ) {
        if (container == null) {
            return;
        }

        choices.clear();
        if (currentPageChoice != null) {
            currentPageChoice.selected = preferenceStore.isCurrentPageCookieSelectedByDefault();
            choices.add(currentPageChoice);
        }

        boolean hasStorageAccess = cookieProfileManager.canAccessRootDirectory() && cookieProfileManager.ensureInitialized();
        if (hasStorageAccess) {
            List<GroupedCookieProfile> profiles = groupProfiles(cookieProfileManager.loadProfiles());
            for (GroupedCookieProfile profile : profiles) {
                FeatureCookieChoice choice = new FeatureCookieChoice();
                choice.label = profile.displayUserNames + " (" + profile.displayFileNames + ")";
                choice.pageUrl = CookieProfileManager.buildTargetUrl(profile.primaryProfile);
                choice.subtitle = choice.pageUrl;
                choice.baseUrl = CookieProfileManager.buildRootUrl(profile.primaryProfile);
                choice.cookies = profile.primaryProfile.userCookies;
                choice.selectionKey = profile.selectionKey;
                choice.selected = isPersistedCookieChoiceSelected(choice.selectionKey);
                choice.currentPage = false;
                choices.add(choice);
            }
        }

        renderChoiceViews(container, choices, onChoicesChanged);

        if (storageAccessButton != null) {
            storageAccessButton.setVisibility(hasStorageAccess ? View.GONE : View.VISIBLE);
        }
        if (hintText != null) {
            hintText.setText(hasStorageAccess
                    ? "优先显示当前页面 Cookie。每日任务会自动忽略 URL 中包含 pvzol.org 的选项。"
                    : "优先显示当前页面 Cookie。未授权时只能使用当前页面 Cookie。每日任务会自动忽略 URL 中包含 pvzol.org 的选项。");
        }
    }

    void selectAll(List<FeatureCookieChoice> choices) {
        for (FeatureCookieChoice choice : choices) {
            choice.selected = true;
            persistFeatureCookieChoiceSelection(choice, true);
        }
    }

    private void renderChoiceViews(
            LinearLayout container,
            List<FeatureCookieChoice> choices,
            Runnable onChoicesChanged
    ) {
        container.removeAllViews();
        if (choices.isEmpty()) {
            TextView emptyView = new TextView(context);
            emptyView.setText("当前没有可用 Cookie。");
            emptyView.setTextColor(0xFF4B5563);
            container.addView(emptyView);
            return;
        }

        LayoutInflater inflater = LayoutInflater.from(context);
        for (FeatureCookieChoice choice : choices) {
            View itemView = inflater.inflate(R.layout.item_panel_cookie_option, container, false);
            CheckBox checkBox = itemView.findViewById(R.id.check_cookie_option);
            TextView subtitle = itemView.findViewById(R.id.text_cookie_option_subtitle);
            checkBox.setText(choice.currentPage ? "当前页面 Cookie" : choice.label);
            checkBox.setChecked(choice.selected);
            checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                choice.selected = isChecked;
                persistFeatureCookieChoiceSelection(choice, isChecked);
                if (onChoicesChanged != null) {
                    onChoicesChanged.run();
                }
            });

            StringBuilder subtitleBuilder = new StringBuilder();
            if (!choice.currentPage) {
                subtitleBuilder.append(choice.label).append('\n');
            }
            subtitleBuilder.append(choice.subtitle == null ? "" : choice.subtitle);
            if (!TextUtils.isEmpty(choice.baseUrl)) {
                subtitleBuilder.append("\nAMF: ").append(choice.baseUrl).append("/pvz/amf/");
            }
            if (!CookieProfileManager.isDutyRewardEligibleBaseUrl(choice.pageUrl)) {
                subtitleBuilder.append("\n该项不会用于每日任务，URL 包含 pvzol.org。");
            }
            subtitle.setText(subtitleBuilder.toString().trim());
            container.addView(itemView);
        }
    }

    private boolean isPersistedCookieChoiceSelected(String selectionKey) {
        if (TextUtils.isEmpty(selectionKey)) {
            return false;
        }
        Set<String> selectedKeys = preferenceStore.getSelectedCookieKeys();
        return selectedKeys.contains(selectionKey);
    }

    private void persistFeatureCookieChoiceSelection(FeatureCookieChoice choice, boolean selected) {
        if (choice == null) {
            return;
        }
        if (choice.currentPage) {
            preferenceStore.setCurrentPageCookieSelectedByDefault(selected);
            return;
        }
        if (TextUtils.isEmpty(choice.selectionKey)) {
            return;
        }
        Set<String> selectedKeys = preferenceStore.getSelectedCookieKeys();
        if (selected) {
            selectedKeys.add(choice.selectionKey);
        } else {
            selectedKeys.remove(choice.selectionKey);
        }
        preferenceStore.setSelectedCookieKeys(selectedKeys);
    }

    private List<GroupedCookieProfile> groupProfiles(List<CookieProfileManager.CookieProfile> profiles) {
        LinkedHashMap<String, GroupedCookieProfile> grouped = new LinkedHashMap<>();
        ArrayList<GroupedCookieProfile> fallbackGroups = new ArrayList<>();
        int fallbackIndex = 1;
        for (CookieProfileManager.CookieProfile profile : profiles) {
            if (profile == null) {
                continue;
            }
            String key = CookieProfileManager.buildCookieIdentityKey(profile.userCookies);
            if (TextUtils.isEmpty(key)) {
                fallbackGroups.add(new GroupedCookieProfile("fallback_" + fallbackIndex, profile));
                fallbackIndex += 1;
                continue;
            }
            GroupedCookieProfile group = grouped.get(key);
            if (group == null) {
                group = new GroupedCookieProfile(key, profile);
                grouped.put(key, group);
            } else {
                group.add(profile);
            }
        }
        ArrayList<GroupedCookieProfile> result = new ArrayList<>(grouped.values());
        result.addAll(fallbackGroups);
        return result;
    }

    private static final class GroupedCookieProfile {
        final String selectionKey;
        final ArrayList<CookieProfileManager.CookieProfile> profiles = new ArrayList<>();
        final LinkedHashSet<String> fileNames = new LinkedHashSet<>();
        final LinkedHashSet<String> userNames = new LinkedHashSet<>();
        CookieProfileManager.CookieProfile primaryProfile;
        String displayFileNames;
        String displayUserNames;

        GroupedCookieProfile(String selectionKey, CookieProfileManager.CookieProfile profile) {
            this.selectionKey = selectionKey;
            add(profile);
        }

        void add(CookieProfileManager.CookieProfile profile) {
            if (profile == null) {
                return;
            }
            if (primaryProfile == null) {
                primaryProfile = profile;
            }
            profiles.add(profile);
            if (profile.file != null) {
                fileNames.add(profile.file.getName());
            }
            if (!TextUtils.isEmpty(profile.userName)) {
                userNames.add(profile.userName);
            }
            displayFileNames = TextUtils.join("; ", fileNames);
            displayUserNames = TextUtils.join("; ", userNames);
            if (TextUtils.isEmpty(displayUserNames)) {
                displayUserNames = "cookie_" + System.currentTimeMillis();
            }
        }
    }
}
