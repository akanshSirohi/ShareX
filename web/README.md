# ShareX web portal

The portal is a JavaScript-only Next.js static export with locally installed shadcn/ui components. It works offline when served by the Android app. Source files and dependencies stay in `web/`; only a ZIP of the exported build is copied to Android assets.

## Build and package

Use Node.js 22 or later:

```sh
cd web
npm ci
npm run build
npm run copy:assets
```

`npm run build` writes `web/out/`. The separate copy script creates `app/src/main/assets/sharex_web_v4_8.zip` with the complete export, including `_next/`, and removes older portal archives and loose build folders. Fonts and other Android assets are preserved. `npm run build:assets` runs both steps. Archive entries are sorted with fixed timestamps, so unchanged exports produce identical archives.

The checked-in ZIP allows Android builds without Node.js. Rebuild and package after changing portal source. Gradle checks the archive's index and version manifest. Each APK assembly verifies that the complete ZIP is packaged with identical content.

## Folder versions

Set `assetFolder` in `web-ui.json` to a new folder such as `sharex_web_v4_8`, then run `npm run build:assets`. The folder name is the version key. Gradle uses the same value to generate `BuildConfig.WEB_INTERFACE_DIR`; Java constants use that generated value. No Java version constants need manual edits.

Browser approval and optional passwords are enforced by the Android server on every API, file, upload, and plugin request. The portal first requests approval on the phone, then asks for a password if enabled. Password sessions use an HttpOnly, SameSite=Strict cookie with a fixed expiry (24 hours by default); browsing does not extend expiry. Password or policy changes invalidate existing sessions. The remembered-browser exemption requires permanent approval of a server-issued browser secret, not a browser-supplied ID. Older remembered browsers require one fresh approval. Enable SSL in Android Settings to encrypt passwords and cookies in transit.

On release launches, a missing version directory triggers installation. Debug launches refresh the installed export every time, even when the version stays the same. Installation deletes old web UI directories first, then extracts the ZIP into the new version directory in internal storage. A completion marker is written only after extraction and manifest validation. Failed extraction removes the incomplete directory; interrupted extraction retries on the next launch. Restart the Android app after installing a rebuilt APK to refresh its portal.

## Development

Run the Android sharing server, approve this browser on your phone, and proxy requests through Next.js:

```powershell
$env:SHAREX_DEVICE_URL = 'http://192.168.1.20:6060'
npm run dev
```

Open `http://localhost:3000`. The development-only proxy forwards `/ShareX`, uploads, app thumbnails, downloads, and `/SharexApp/` requests to the device. Production uses relative URLs and needs no Next.js server, runtime proxy, CDN, or external fonts.

## Appearance

Choose **Default**, **Zen Inspired**, **2077**, **hex**, **Purple Rain**, **Light Green**, or **Claude +** in the Android app's web interface theme setting. The portal follows the device setting and checks for changes every 15 seconds. Theme selection is not available in the browser. The browser always provides a light/dark switch and remembers that preference locally.

Zen Inspired uses the light and dark tokens from [Bikash's tweakcn theme](https://tweakcn.com/themes/cmlm03etv000204lh15608kec). The default uses shadcn's neutral tokens. The additional presets use the supplied tokens from [2077](https://tweakcn.com/themes/cmlecx2br000004if3m471xxd), [hex](https://tweakcn.com/themes/cmninq0c3000604l25wvb3xgh), [Purple Rain](https://tweakcn.com/themes/cmlh0vbnd000004l112kx8a0l), [Light Green](https://tweakcn.com/themes/cmlhfpjhw000004l4f4ax3m7z), and [Claude +](https://tweakcn.com/themes/cmdght103000n04lh3e2ae93r). These themes ship locally with no network request at runtime.

## Data and operations

React builds tables from JSON. `listFiles`, `listApps`, `getInstalledPlugins`, `getState`, `listFolders`, and file-operation responses use `application/json`, `Cache-Control: no-store`, and negotiated gzip. Browsers decompress JSON automatically. File downloads, byte ranges, ZIP archives, and plugin templates retain their transfer paths.

Uploads include their destination in the request rather than relying on another browser's last visited folder. Browser approval retains the existing `fsx_auth_token` cookie and approval flow. In Android settings, **Remembered devices** opens a list of browser/platform names with individual Remove and Clear all controls. Legacy records remain approved and receive a name and connection date on their next connection; temporary and denied sessions stay separate. Connection dates use the Android device's locale and current timezone. The list scrolls inside a bounded middle area while Close and Clear all remain fixed.

Select files or use a row menu to **Copy to…** or **Move to…**. The destination picker lists device folders. Background jobs report progress through `fileOperationStatus`. Folder copies are recursive, include empty files, and preserve modification dates. Copy collisions get names such as `report (copy).pdf`; move collisions fail without overwriting. Moving a folder into itself or a descendant is rejected. Private mode and restricted modification disable both actions. Symbolic links are rejected. Moves use the filesystem's move operation; destinations on a different filesystem may fail safely.

Switch between list and grid with the view controls; the browser remembers the choice. Both views keep selection, right-click menus, and file actions. About displays the folder version as `v4.6` and the author on a separate line. The device button opens storage usage, battery/charging, connection status, and the current sharing-session duration. Android supplies storage and monotonic session time; stopping or restarting sharing resets the session.

Right-click a file or folder for the same actions. Right-clicking an already selected row keeps the selection and offers bulk actions. The selected-file toolbar stays visible while scrolling.

## Previews

Images, videos, and documents open in a dialog occupying 96% of the viewport width and 94% of its height. Close and Download remain visible. The native image viewer fits the entire image without enlargement, supports wheel/pinch zoom, drag to pan, rotate, and reset. Howler streams audio in a compact dialog; Movi plays video; Omni Doc Viewer displays PDF, DOCX, XLS/XLSX, PPTX, Markdown, CSV, JSON, HTML, text, and code. Omni keeps pagination and its thumbnail toggle. HTML previews hide the built-in toolbar. Its download control is hidden; the dialog's download button requests an attachment from Android. Unsupported files or decoder errors show a small dialog with Download and Close.

Each player is a separate lazy chunk, requested only when a matching preview opens. Playback and viewer resources are disposed on close. Build and development scripts copy Movi WASM and PDF.js workers, fonts, CMaps, and decoders into `public/players/`; these assets ship in the static export. PDF workers never fall back to a CDN. Omni's optional Node canvas dependency is excluded because previews use browser Canvas APIs. Dependency overrides follow Omni's patched ECharts and UUID versions for PPTX rendering.

## Checks

```sh
npm test
npm run build:assets
```

From the repository root:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
```

Node tests verify deterministic ZIP packaging, folder-version changes, preservation of other assets, rejection of missing or stale exports, preview format detection, and audio codec aliases. Java tests verify gzip negotiation, unchanged file/ZIP payloads, recursive copy, empty files, collisions, moves, and permission/path restrictions.

`PortalAssetsTest` is an Android instrumentation test that installs the packaged export into a temporary directory and exercises the production static-file route for the HTML and every referenced CSS/JavaScript bundle. It checks HTTP status, MIME types, and complete response content.

`FilePreviewResponseTest` checks inline previews, forced downloads, and audio byte ranges with the legacy download preference enabled. `WebAccessDialogTest` checks the approval scope and temporary, permanent, and denied decisions.
