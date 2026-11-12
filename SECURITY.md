# Security notes

## Trust boundaries

Shared text and Android intents are treated as untrusted. The app accepts bounded HTTP(S) URLs only for supported providers, rejects user information and unusual ports, upgrades plain HTTP to HTTPS, and caps each URL at 2,048 characters. Provider matching uses a dot-boundary suffix check.

OkHttp requests use the platform TLS stack, public-address DNS checks, HTTPS on port 443, explicit timeouts, and at most five redirects. Resolver, recipe, release, and media requests restrict redirects to their expected provider or CDN suffixes. Parsed HTML and JSON are size-bounded; direct media is streamed to private temporary files and then published through MediaStore or the user-selected Storage Access Framework folder.

YouTube format metadata accepts direct streams only from `googlevideo.com` and thumbnails only from YouTube image hosts. Instagram and TikTok media must match their provider CDN suffix lists before preview caching or download. Extractor-supplied request headers are allow-listed. Cached social previews never retain Cookie headers.

yt-dlp is launched with argument lists, ignores external config, and uses the native downloader through a loopback-only HTTPS CONNECT proxy. The proxy accepts only port 443 and source-specific provider/CDN suffixes, resolves each host, rejects private or special IPs, and connects directly to one of the validated public IPs to prevent DNS rebinding. TLS remains end-to-end between yt-dlp and the remote host. The proxy limits concurrent connections and applies connect/read timeouts.

## Files, diagnostics, and Android components

Output names remove path/control/format characters, cap UTF-8 length, protect reserved device names, and avoid existing names. The app checks available space where it can, writes MediaStore entries as pending until complete, and deletes partial temporary and pending files on failure or cancellation.

Diagnostics redact URLs and secret-bearing headers/fields and cap copied logs. The global crash record stores only a timestamp, sanitized thread name, and exception class. Cookies are not persisted: the app has no Instagram login or WebView cookie feature, and temporary TikTok resolver cookies remain in memory only.

Cleartext is disabled. Backup is disabled and extraction rules exclude app data. Android components declare `android:exported`; the launcher remains exported so Android can show and launch the app, the share activity and Quick Settings tile are exported for their system integrations, and the download service and FileProvider are private. PendingIntents are immutable. Notification denial is handled, the data-sync foreground service implements Android's timeout callback, and the Room queue exports its version 1 schema and marks interrupted work after process restart.

## Recipes and updates

Remote recipes are fetched from one fixed HTTPS GitHub raw URL with a 64 KiB cap. The parser validates the schema and fixed host/path templates; regular expressions use RE2/J and are length-bounded. Recipe signatures are not implemented yet, so HTTPS and the pinned repository endpoint are the current authenticity boundary.

The updater reads the latest release from the fixed GitHub API endpoint, accepts only the expected release APK URL and SHA-256 digest, checks the package/version/signing certificate against the installed app, rejects downgrades, and opens Android's installer only after the user taps the update action.

The bundled extractor previously requested Jackson 2.11.1, Commons Compress 1.12, and Commons IO 2.5. The app now constrains those transitive dependencies to maintained, patched releases; the Gradle lockfile is scanned by OSV-Scanner in CI.

## Remaining limits

- The proxy validates each new HTTPS connection, so a redirect to a different host is checked against the same provider/CDN allowlist and public-IP policy. Because the HTTPS request is encrypted inside CONNECT, the app cannot count redirects that remain on the same host; the five-hop redirect limit applies to OkHttp requests, not to same-host redirects inside yt-dlp's TLS tunnel. yt-dlp also has its own socket timeout.
- Remote recipes are not digitally signed. Add an Ed25519 signature and rotate its private signing key through a protected release process before treating recipe updates as independently authenticated.
- Network switching, airplane mode, storage exhaustion, process death, cancellation, large media, and update installation still need checks on physical Android devices. CI covers unit tests, Compose screenshot generation, and the queue/notification emulator checks, but cannot model every carrier or OEM behavior.

Report security issues privately to the repository owner rather than opening a public issue with a working exploit.
