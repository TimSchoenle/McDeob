package com.shanebeestudios.mcdeop.app;

import com.shanebeestudios.mcdeop.app.components.McDeobUpdateNotification;
import com.shanebeestudios.mcdeop.util.GeneratedConstant;
import com.shanebeestudios.mcdeop.util.GithubReleaseChecker;
import com.shanebeestudios.mcdeop.util.GithubReleaseChecker.UpdateCheckResult;
import com.shanebeestudios.mcdeop.util.GithubReleaseChecker.UpdateCheckStatus;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;
import javafx.concurrent.Task;
import javafx.scene.control.Button;
import lombok.extern.slf4j.Slf4j;

/**
 * Drives the release check and the notification banner it feeds.
 *
 * <p>Split out of the application class, which otherwise owned the scene graph, the processing run,
 * and this flow at once.
 */
@Slf4j
final class UpdateCheckController {
    private static final String GITHUB_RELEASES_URL = GeneratedConstant.GITHUB_REPO_URL + "/releases/latest";
    private static final DateTimeFormatter CHECKED_AT_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final GithubReleaseChecker releaseChecker = new GithubReleaseChecker();
    private final McDeobUpdateNotification notification;
    private final Button triggerButton;
    private final Consumer<String> releaseOpener;

    private String latestReleaseUrl = GITHUB_RELEASES_URL;
    private boolean checkInProgress;

    /**
     * @param notification banner to report progress and results through
     * @param triggerButton button disabled while a check is running
     * @param releaseOpener opens a release page in the user's browser
     */
    UpdateCheckController(
            final McDeobUpdateNotification notification,
            final Button triggerButton,
            final Consumer<String> releaseOpener) {
        this.notification = notification;
        this.triggerButton = triggerButton;
        this.releaseOpener = releaseOpener;
    }

    /**
     * Starts a check unless one is already running.
     *
     * @param userInitiated whether the user asked for this check; an automatic check stays silent when
     *     the app is already up to date, rather than reporting a result nobody asked for
     */
    void check(final boolean userInitiated) {
        if (this.checkInProgress) {
            return;
        }
        this.checkInProgress = true;
        this.triggerButton.setDisable(true);
        this.notification.showChecking();

        final Task<UpdateCheckResult> task = new Task<>() {
            @Override
            protected UpdateCheckResult call() {
                try {
                    return UpdateCheckController.this.releaseChecker.checkForUpdateDetailed(GeneratedConstant.VERSION);
                } catch (final Exception e) {
                    log.error("Could not check for newer GitHub releases", e);
                    return new UpdateCheckResult(
                            UpdateCheckStatus.FAILED,
                            null,
                            e.getMessage() != null
                                    ? e.getMessage()
                                    : e.getClass().getSimpleName());
                }
            }
        };

        task.setOnSucceeded(event -> this.onResult(task.getValue(), userInitiated));
        task.setOnFailed(event -> {
            final Throwable throwable = task.getException();
            final String reason = throwable != null && throwable.getMessage() != null
                    ? throwable.getMessage()
                    : "Unexpected failure while checking updates.";
            this.finish();
            this.reportFailure(reason);
        });

        final Thread thread = new Thread(task, "Release-Check-Thread");
        thread.setDaemon(true);
        thread.start();
    }

    private void onResult(final UpdateCheckResult result, final boolean userInitiated) {
        this.finish();

        if (result == null) {
            this.reportFailure("No response received from update checker.");
            return;
        }

        switch (result.status()) {
            case UPDATE_AVAILABLE -> this.reportUpdateAvailable(result);
            case UP_TO_DATE -> this.reportUpToDate(userInitiated);
            case FAILED ->
                this.reportFailure(
                        result.errorMessage() != null && !result.errorMessage().isBlank()
                                ? result.errorMessage()
                                : "Unknown error while contacting GitHub.");
        }
    }

    private void reportUpdateAvailable(final UpdateCheckResult result) {
        if (result.updateInfo() == null) {
            this.reportFailure("Update reported without any details.");
            return;
        }

        final String url = result.updateInfo().releaseUrl();
        if (url != null && !url.isBlank()) {
            this.latestReleaseUrl = url;
        }
        this.notification.showUpdateAvailable(
                GeneratedConstant.VERSION,
                result.updateInfo(),
                () -> this.releaseOpener.accept(this.latestReleaseUrl),
                this.notification::dismiss);
    }

    private void reportUpToDate(final boolean userInitiated) {
        if (!userInitiated) {
            // Nothing to say about a background check that found nothing.
            this.notification.dismiss();
            return;
        }

        final String checkedAt = "Last checked: " + CHECKED_AT_FORMAT.format(LocalDateTime.now());
        this.notification.showUpToDate(GeneratedConstant.VERSION, checkedAt, () -> this.check(true));
    }

    private void reportFailure(final String reason) {
        this.notification.showCheckFailed(reason, () -> this.check(true), this.notification::dismiss);
    }

    private void finish() {
        this.checkInProgress = false;
        this.triggerButton.setDisable(false);
    }
}
