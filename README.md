# Slate: a clean home screen for Fire TV

Slate is an ad-free home screen for your Fire TV with built-in EveryTV search.

- **Slick home screen.** It shows a big clock and greeting, a Favorites row and a grid of all your apps. The background glows softly and shifts to the colour of the app you're on. Focused tiles zoom in with a light sweep, a glow ring and the app's name underneath.
- **EveryTV search on the main screen.** Select **Search EveryTV**, type or use the keyboard mic, and EveryTV opens with your search. Slate also lists matching apps and remembers your recent searches on the device.
- **No ads and no data sent to Amazon.** Slate doesn't request the internet permission, so Android blocks it from going online at all. It has no ad SDKs, no analytics and no third-party libraries.
- **Your layout.** Hold OK or press ☰ on any tile to add it to Favorites, move it, hide it or open App info. Pick from 5 accent colours in Slate Settings, or switch off the ambient motion.
- **The Home button opens Slate.** It also takes over on boot (see below).

## 1. Build the APK (GitHub Actions, the same way EveryTV builds)

1. Create a new **private** repo on GitHub, e.g. `slate-launcher`.
2. Upload everything in this folder, including the hidden `.github` folder and `app/slate.jks`.
   The easiest route is GitHub Desktop or `git push`. The web uploader skips the `.github` folder.
3. Go to the **Actions** tab, where *Build Slate APK* runs automatically (about 3 minutes).
4. Download `slate.apk` from the run's artifacts or from **Releases → latest**.

To sideload with the Downloader app, copy the APK to your public `notv-apk` repo just like EveryTV.
Downloader can then fetch it from a public URL.

## 2. Install on the Fire TV and claim the Home button

Fire OS won't let you set another launcher as the default. Slate gets around this with a tiny accessibility
service. Whenever Amazon's home screen appears (Home button, boot, closing an app), it opens Slate instead.
It only notices *which app* is in front. It can't read the screen, and it has no internet access.

Run the setup script once from your Windows PC:

1. On the Fire TV: **Settings → My Fire TV → Developer options** → turn on **ADB debugging**.
2. Put `adb.exe` (Google's *platform-tools* for Windows) and `slate.apk` in the `tools` folder.
3. Double-click `tools/setup-firetv.bat` and enter the Fire TV's IP address.
   The script installs Slate and turns on the Home-button takeover. If you choose to, it also applies the privacy tweaks.

To reach Amazon's stock home, use **System → Amazon Home**. Slate stays out of the way until you open another app.

## 3. Privacy: what the setup script can and can't do

| | |
|---|---|
| Slate itself | Can't send anything. It has no network permission, and everything is stored on the device. |
| Advertising ID | Turned off (`limit_ad_tracking = 1`). |
| Amazon crash reporting and metrics | Disabled where your Fire OS version allows it. Newer Fire OS blocks disabling some system packages, and the script just skips those. |
| Fire OS itself | **Still talks to Amazon** for sign-in, updates, the Appstore and Alexa. No launcher can change that. |

Also turn these off in **Settings → Preferences → Privacy Settings**: *Device Usage Data*, *Collect App Usage Data*
and *Interest-based Ads*. Turn off **Settings → Preferences → Featured Content** autoplay too.

`tools/undo-privacy.bat` reverses all of the above.

## 4. Make EveryTV open straight into its results

Slate sends searches with Android's standard search intent: `ACTION_SEARCH` with the query in
`SearchManager.QUERY` (and also in a `"query"` extra). If EveryTV has no search screen for that intent, Slate
opens EveryTV's main screen with the same extras attached.

Add this to EveryTV's main activity in the `notv-iptv` repo:

```kotlin
// MainActivity.kt (EveryTV)
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    // ...existing setup...
    handleSearch(intent)
}

override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    handleSearch(intent)
}

private fun handleSearch(i: Intent?) {
    val q = i?.getStringExtra(SearchManager.QUERY) ?: i?.getStringExtra("query") ?: return
    openSearchScreen(q)   // ← EveryTV's existing search, pre-filled with q
}
```

Then add this intent filter to that activity in EveryTV's `AndroidManifest.xml`:

```xml
<intent-filter>
    <action android:name="android.intent.action.SEARCH" />
    <category android:name="android.intent.category.DEFAULT" />
</intent-filter>
```

If EveryTV's UI is a WebView, pass the query into the page instead, e.g.
`webView.evaluateJavascript("window.everyTvSearch(${JSONObject.quote(q)})", null)`.

Slate finds EveryTV automatically by its name. You can point search at a different app in **Slate Settings → Search app**.

## Remote controls

| Key | Action |
|---|---|
| D-pad / OK | Move around and open an app |
| Hold OK or ☰ (menu) | Open the app's options: favorite, move, hide or app info |
| Back | Close a panel or jump back to the top |
| Home | Always returns to Slate |

## Project layout

```
app/src/main/java/com/ayush/slate/
  HomeActivity.kt   home screen, rows, focus, menus, settings
  Ui.kt             cards, pills, ambient background, code-drawn icons
  Overlays.kt       glass modals and the search screen
  Apps.kt           app list, banners/icons, colour extraction, EveryTV search hand-off
  HomeWatcher.kt    Home-button takeover (accessibility service)
  Prefs.kt          on-device settings
tools/              setup-firetv.bat / .sh, undo-privacy.bat
```
