# Stage 3: home hub and quality entry point

> **For agentic workers:** Execute this plan inline, in order. Each step is independently reviewable and verified before continuing.

**Goal:** Make the home screen the one place to paste or receive links and continue into the existing quality chooser.

**Architecture:** Keep metadata extraction and the detailed selection state in `ChoiceActivity`; the home screen validates links, previews cached metadata when available, and launches that same chooser. Clipboard intake and multi-link routing use the existing `SharedUrl` and batch-download functions.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, DataStore, existing `PreviewStore`, GitHub Actions screenshot tests.

**Spec:** `docs/superpowers/specs/2026-11-09-skachay-v1-spec.txt`, sections 4–6 and 15, Stage 3.

## Global Constraints

- Keep the app UI Russian-only and preserve Android 8 (API 26) support.
- Keep one primary action per screen and avoid raw yt-dlp format labels in the regular flow.
- Do not add backend, analytics, account sign-in, ads, or paid services.
- Release only after all eight requested stages pass the final QA checklist.

---

### Task 1: Link field and clipboard intake

**Files:** Modify `app/src/main/java/ru/skachay/app/MainActivity.kt`; test through `app/src/screenshotTest/kotlin/ru/skachay/app/ScreenScreenshotTests.kt`.

- [ ] Add an editable link field with paste and clear actions.
- [ ] Prefill a supported clipboard link when the home screen opens and show `Ссылка из буфера`.
- [ ] Route one valid URL to `ChoiceActivity`; route multiple valid URLs through the existing batch helper.
- [ ] Verify the home light, dark, and 150% font-scale screenshot previews.

### Task 2: Cached home preview

**Files:** Modify `app/src/main/java/ru/skachay/app/MainActivity.kt`; reuse `app/src/main/java/ru/skachay/app/choice/PreviewStore.kt` and `QualityChoiceUi.kt` preview primitives.

- [ ] Debounce edits before loading YouTube metadata on an IO dispatcher.
- [ ] Show a thumbnail/title/source preview when cached metadata is ready and a skeleton while metadata is loading.
- [ ] Keep the `Выбрать качество` action routed through the existing chooser so preview extraction is reused.
- [ ] Verify long title and 150% screenshot previews for clipping.

### Task 3: Screenshot verification

**Files:** Modify `app/src/screenshotTest/kotlin/ru/skachay/app/ScreenScreenshotTests.kt` only if an additional home state is needed.

- [ ] Run `testDebugUnitTest`, `assembleDebug`, and `updateDebugScreenshotTest` in CI.
- [ ] Download and inspect the generated screenshots for clipped copy, overlap, and spacing.
- [ ] Commit and push the verified stage to the preview branch without creating a release.
