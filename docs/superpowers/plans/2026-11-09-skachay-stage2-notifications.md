# Stage 2: one notification per download

## Goal

Route foreground-service and notification updates through one coordinator. Keep a stable ID for the same download request, update that ID in place, preserve completed/error notifications after the service exits, and show at most one group summary when multiple downloads are active.

## Current issue

`DownloadService` starts foreground notification ID 1, then also posts per-download notifications when multiple downloads are active. The group summary is updated through a separate path, and download IDs increment from process-local state.

## Implementation sequence

1. Add deterministic notification IDs based on the normalized download request, excluding reserved summary IDs.
2. Add a pure notification state machine that owns active IDs and the single foreground ID; test first start, parallel start, progress update, foreground completion with another job active, and final completion.
3. Add `DownloadNotificationCenter` as the only place that calls `startForeground`, `stopForeground`, `NotificationManager.notify`, or `cancel` for downloads and the summary.
4. Route initial, progress, success, duplicate, and error notifications through the center. Detach completed notifications before promoting another active download so their final state remains under the same ID.
5. Keep pause/cancel action wiring with the persistent queue stage so actions control real work; do not show buttons that cannot actually pause/cancel a download.
6. Run the unit tests, debug build, screenshot generation, and an Android instrumentation notification-count check where an emulator is available.

## Verification

- Unit tests cover stable-ID determinism and notification-state transitions.
- CI must run `testDebugUnitTest`, `assembleDebug`, and screenshot generation successfully.
- Instrumentation should verify one active notification per download plus no more than one group summary.
- Manual check: run one download, then two in parallel; watch progress and confirm each result replaces its active row rather than creating a duplicate.
