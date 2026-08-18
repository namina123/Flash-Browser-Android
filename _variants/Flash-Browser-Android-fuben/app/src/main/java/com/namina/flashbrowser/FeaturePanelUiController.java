package com.namina.flashbrowser;

import android.content.res.ColorStateList;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

final class FeaturePanelUiController {
    private Button tabCookieButton;
    private Button tabBasicButton;
    private Button tabRepositoryButton;
    private Button tabLogButton;
    private View cookiePage;
    private View basicPage;
    private View repositoryPage;
    private View logPage;
    private TextView queueStatusText;
    private TextView queueLogText;
    private Button pauseResumeButton;
    private Button cancelButton;
    private Button dailyDutyRunButton;
    private Button fullSweepRunButton;
    private Button fubenProgressRunButton;
    private Button medalRepeatRunButton;
    private Button startSelectedButton;

    void bind(
            Button tabCookieButton,
            Button tabBasicButton,
            Button tabRepositoryButton,
            Button tabLogButton,
            View cookiePage,
            View basicPage,
            View repositoryPage,
            View logPage,
            TextView queueStatusText,
            TextView queueLogText,
            Button pauseResumeButton,
            Button cancelButton,
            Button dailyDutyRunButton,
            Button fullSweepRunButton,
            Button fubenProgressRunButton,
            Button medalRepeatRunButton,
            Button startSelectedButton
    ) {
        this.tabCookieButton = tabCookieButton;
        this.tabBasicButton = tabBasicButton;
        this.tabRepositoryButton = tabRepositoryButton;
        this.tabLogButton = tabLogButton;
        this.cookiePage = cookiePage;
        this.basicPage = basicPage;
        this.repositoryPage = repositoryPage;
        this.logPage = logPage;
        this.queueStatusText = queueStatusText;
        this.queueLogText = queueLogText;
        this.pauseResumeButton = pauseResumeButton;
        this.cancelButton = cancelButton;
        this.dailyDutyRunButton = dailyDutyRunButton;
        this.fullSweepRunButton = fullSweepRunButton;
        this.fubenProgressRunButton = fubenProgressRunButton;
        this.medalRepeatRunButton = medalRepeatRunButton;
        this.startSelectedButton = startSelectedButton;
    }

    void clear() {
        bind(null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    void switchTab(int tab, int cookieTab, int basicTab, int repositoryTab, int logTab) {
        if (cookiePage != null) {
            cookiePage.setVisibility(tab == cookieTab ? View.VISIBLE : View.GONE);
        }
        if (basicPage != null) {
            basicPage.setVisibility(tab == basicTab ? View.VISIBLE : View.GONE);
        }
        if (repositoryPage != null) {
            repositoryPage.setVisibility(tab == repositoryTab ? View.VISIBLE : View.GONE);
        }
        if (logPage != null) {
            logPage.setVisibility(tab == logTab ? View.VISIBLE : View.GONE);
        }

        updateTabButtonState(tabCookieButton, tab == cookieTab);
        updateTabButtonState(tabBasicButton, tab == basicTab);
        updateTabButtonState(tabRepositoryButton, tab == repositoryTab);
        updateTabButtonState(tabLogButton, tab == logTab);
    }

    void renderQueueState(DutyRequestQueue.StateSnapshot snapshot) {
        if (snapshot == null) {
            return;
        }

        if (queueStatusText != null) {
            String state;
            if (snapshot.cancelling) {
                state = "正在终止";
            } else if (snapshot.running && snapshot.paused) {
                state = "已暂停";
            } else if (snapshot.running) {
                state = "运行中";
            } else {
                state = "空闲";
            }
            queueStatusText.setText(
                    "状态：" + state
                            + "\n总数：" + snapshot.total
                            + "  排队：" + snapshot.queued
                            + "  进行中：" + snapshot.active
                            + "\n完成：" + snapshot.completed
                            + "  失败：" + snapshot.failed
                            + "  跳过：" + snapshot.skipped
            );
        }

        if (queueLogText != null) {
            if (snapshot.logs.isEmpty()) {
                queueLogText.setText("暂无日志");
            } else {
                StringBuilder builder = new StringBuilder();
                for (String line : snapshot.logs) {
                    if (builder.length() > 0) {
                        builder.append('\n');
                    }
                    builder.append(line);
                }
                queueLogText.setText(builder.toString());
            }
        }

        if (pauseResumeButton != null) {
            pauseResumeButton.setText(snapshot.running && snapshot.paused ? "继续" : "暂停");
            pauseResumeButton.setEnabled(snapshot.running && !snapshot.cancelling);
        }
        if (cancelButton != null) {
            cancelButton.setEnabled(snapshot.running || snapshot.paused || snapshot.cancelling);
        }

        boolean idle = !snapshot.running && !snapshot.paused && !snapshot.cancelling;
        if (dailyDutyRunButton != null) {
            dailyDutyRunButton.setEnabled(idle);
        }
        if (fullSweepRunButton != null) {
            fullSweepRunButton.setEnabled(idle);
        }
        if (fubenProgressRunButton != null) {
            fubenProgressRunButton.setEnabled(idle);
        }
        if (medalRepeatRunButton != null) {
            medalRepeatRunButton.setEnabled(idle);
        }
        if (startSelectedButton != null) {
            startSelectedButton.setEnabled(idle);
        }
    }

    private void updateTabButtonState(Button button, boolean selected) {
        if (button == null) {
            return;
        }
        button.setEnabled(true);
        button.setSelected(selected);
        button.setAlpha(1.0f);
        button.setBackgroundTintList(ColorStateList.valueOf(selected ? 0xFF2563EB : 0xFF9CA3AF));
        button.setTextColor(0xFFFFFFFF);
    }
}
