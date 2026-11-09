# Stage 1: speed and extraction reliability

## Goal

Improve the existing fast-path downloader and make YouTube extraction/runtime failures visible, while keeping downloads on direct HTTP streams whenever yt-dlp has already returned usable URLs. Do not add a second extraction engine until its Android compatibility and license/build impact are verified.

## Current baseline

- `DirectDownloader` already downloads ranged files in parallel, retries failed ranges, and falls back when range requests are unsupported.
- `YouTubeDirectTransfer` already downloads separate video/audio streams through `DirectDownloader` and merges with FFmpeg stream copy.
- yt-dlp is initialized in the background and updated on maintenance/failure; downloads use the yt-dlp fallback when direct streams cannot be used.
- Diagnostics currently records download stages and errors, but not yt-dlp warning output about JS runtimes or n-challenge failures.
- There is no repeatable physical-device benchmark data yet; report this honestly rather than inventing results.
- The pinned `youtubedl-android:0.18.1` source already includes `libqjs.so` for both app ABIs and adds `--js-runtimes quickjs:<path>` on every execution.

## Implementation sequence

1. Reuse the existing bounded parallel Range downloader and direct YouTube stream merge; make media transfers use one shared HTTP/2-capable OkHttp client.
2. Enable yt-dlp's official EJS component download for YouTube and keep the runtime's cache between extractions.
3. Add unit coverage for EJS configuration and extracting/sanitizing actionable yt-dlp warnings.
4. Route preview extraction and fallback downloads through warning capture; include warnings in the Diagnostics screen and copied log.
5. Add a README benchmark table with three repeatable test cases marked “not measured” until physical-device measurements exist, plus exact steps for collecting comparable results.
6. Run focused unit tests and the debug build if the local Android SDK is available; otherwise record the local limitation and use existing CI evidence without claiming a fresh pass.

## Verification

- Unit tests cover warning matching, sanitization, and deduplication.
- Run the targeted diagnostics/yt-dlp tests and `assembleDebug`.
- Inspect the README benchmark table to ensure it contains no fabricated measurements.
- Record any unsupported runtime integration as a known limitation with manual verification steps.

## Out of scope for this stage

Notifications, Room persistence, the shared quality picker, settings redesign, Instagram/TikTok resolver expansion, icon changes, onboarding, recipes, and release automation remain in their numbered stages from the user specification.
