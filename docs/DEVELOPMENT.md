# Development

This document is for anyone who wants to build or change the app. To use it, the [README](../README.md) is enough.

## Hard rule

The app never changes a setting. It only reads settings, shows a notification and opens the matching settings page. The
user turns the setting off. For this reason the following are absent and are not added: `WRITE_SECURE_SETTINGS`,
`WRITE_SETTINGS`, an accessibility service, device admin, Shizuku, root and `INTERNET`. The other two goals are a high
level of security and low memory use: Nothing runs in the background all the time and there are no runtime libraries.

## Source code

The app is written in Kotlin with Android's own classes only. Package `com.cgrshn.watchdog`, minimum Android 12 (SDK 31),
target SDK 37. The sources are in `app/src/main/java/com/cgrshn/watchdog`:

| File | Contents |
|---|---|
| `MainActivity.kt` | The single screen: Status, Check button, check interval and language |
| `ChoiceDialog.kt` | The app's own choice window for the language and the check interval |
| `Checker.kt` | One check: Reads the settings, makes the decision, applies the notification and the schedule |
| `Decision.kt` | All decision rules, independent of Android (`State`, `Snapshot`, `Trigger`, `ReminderType`, `Delivery`, `Outcome`) |
| `SettingsReader.kt` | Reads the watched settings: USB and wireless debugging, Auto Blocker and Install unknown apps |
| `SettingsPages.kt` | Opens settings pages (`Page`, and `SettingsOpenerActivity` for notifications) |
| `Notifier.kt` | The app's single notification and its channel |
| `Scheduler.kt` | The check alarm and the content-triggered job |
| `Receivers.kt` | `CheckReceiver`, `BootReceiver` and `SettingChangeJob` |
| `Store.kt` | The small persistent store (`SharedPreferences`) |
| `Localized.kt` | Applies the language chosen in the app |
| `Formatting.kt` | Check intervals (`Intervals`), languages (`Language`) and interval, date and list formats (`Format`) |

The unit tests are in `app/src/test/java/com/cgrshn/watchdog` (`DecisionTest.kt`, `FormatTest.kt`). The build-time
manifest check (`ManifestCheck`) is in `app/build.gradle.kts`.

## Building

In Git Bash, in the project folder:

```bash
./tools/build.sh testDebugUnitTest assembleRelease lintDebug
```

- `tools/build.sh` runs the Gradle wrapper. If `JAVA_HOME` is not set, it uses the JBR that ships with Android Studio as
  the JDK. It also points Java's local socket files to the project's `.tmp/` folder (ignored by Git): When the user
  folder name contains non-ASCII letters, Java cannot open them in the temp folder and Gradle fails with "Unable to
  establish loopback connection".
- AGP 9.4.1, compile and target SDK 37, Java 17. There is no separate Kotlin Gradle plugin: AGP 9 compiles the Kotlin
  sources with its built-in Kotlin support. The configuration cache and the build cache are on (`gradle.properties`).
- The package that goes to the phone is `app/build/outputs/apk/release/app-release.apk`. It is minified with R8, its
  resources are shrunk and it is not debuggable.
- **Signing:** Official releases are signed with Watchdog's own release key (RSA 4096, certificate `CN=Watchdog,
  O=SCgrS`, SHA-256 `32:03:63:47:A0:5B:5C:D3:BA:28:58:37:D7:E4:5A:7C:51:9F:B4:15:E2:FF:62:2E:BE:5B:25:F5:2C:3D:10:6E`). The key lives outside the repository and is
  backed up in two places. `app/build.gradle.kts` reads its path, alias and password from `local.properties` (ignored by
  Git): `watchdog.release.storeFile`, `watchdog.release.keyAlias` and `watchdog.release.password`. Without them, for
  example in a fresh clone, the release package is signed with the local debug key, so the project still builds, but
  such a package cannot update an installed official release. Android installs an update only when it is signed with
  the same key as the installed app. If the release key were lost, the next release could only be installed after
  removing the app (its interval, language and battery setting would have to be set again).
- For a release, the package is copied to `dist/` (ignored by Git) and attached to the GitHub release as `Watchdog.apk`.
  The README's download link points to `releases/latest/download/Watchdog.apk`, so the file name stays the same in every
  release. The version is `versionName` (0.1.1) and `versionCode` (4) in `app/build.gradle.kts`. Every release raises
  both: `versionCode` by one, `versionName` in its last digit. GitHub and Google Play share the numbers: Play accepts
  an upload only with a `versionCode` higher than every earlier upload.
- Unit tests run only for the debug variant (`testDebugUnitTest`, the AGP 9 default).

## Google Play

The app is published on Google Play as well as on GitHub. The package name `com.cgrshn.watchdog` is permanent on Play.

- **Signing:** Play App Signing with a key that Google generated and keeps (decided 2026-11-19). Watchdog's release key
  is the upload key: The bundle uploaded to Play is signed with it, and Play signs the packages it delivers with its own
  key. The Play version and the GitHub APK therefore carry different signatures and cannot update each other. Moving
  from one to the other means removing the app first (its interval, language and battery setting have to be set again).
  The certificate of Play's key is shown in Play Console under Test and release › App integrity › App signing.
- **Bundle:** Play takes an Android App Bundle, not an APK.

  ```bash
  ./tools/build.sh testDebugUnitTest bundleRelease lintDebug
  ```

  The bundle is `app/build/outputs/bundle/release/app-release.aab`. `ManifestCheck` runs before it is packaged, and
  `tools/build.sh` stops if the bundle is not signed with the release key (Play refuses a debug signature). R8's mapping
  file goes inside the bundle, so Play can show readable stack traces. For an upload, the bundle is copied to `dist/` as
  `Watchdog-<versionName>.aab`.
- **Store listing:** The texts and pictures are in `fastlane/metadata/android/<locale>/` (`en-US` is the default
  language, `tr-TR` the Turkish one), in the layout fastlane and F-Droid read: `title.txt` (at most 30 characters),
  `short_description.txt` (80), `full_description.txt` (4000), `changelogs/<versionCode>.txt` (500, the "What's new"
  text) and `images/`. The pictures are drawn by `tools/play_graphics.py` (Python with Pillow, headless Chrome, Roboto
  from Google Fonts): `icon.png` (512 × 512, 32-bit PNG, the adaptive icon's visible 72 dp filling the square, Play adds
  the corners), `featureGraphic.png` (1024 × 500, no alpha) and `phoneScreenshots/` (1080 × 1920, a caption above a
  screenshot from `docs/`). Play accepts a long side of at most twice the short side, so the phone's own screenshots are
  cut at the bottom. After the screenshots in `docs/` change, run the script again.
- **Privacy policy:** `PRIVACY.md`, in English and Turkish. Play Console links to
  `https://github.com/SCgrS/Watchdog/blob/main/PRIVACY.md`, so the file must stay at that path.
- **Play Console declarations (App content):**

  | Section | Answer |
  |---|---|
  | Privacy policy | The `PRIVACY.md` address above |
  | App access | All functionality is available without special access |
  | Ads | No ads |
  | Content rating | Category "All other app types", every question No |
  | Target audience | 18 and over |
  | Data safety | No data collected, no data shared. No internet permission, so nothing leaves the phone |
  | Government app, financial features, health, news | No |
  | Category | Tools |

- **Testing before release:** The developer account is a personal account opened after 13 November 2023. Google Play
  makes such an account run a closed test with at least 12 testers who stay opted in for 14 days in a row before it can
  apply for production access.
- **Open source, separate package** (the owner's decisions, 2026-11-19): The Play version has no update path to or
  from the GitHub APK (different signatures, see above), but it is the same open source app: The description links to
  this repository and the store contact's website is the repository. Play's automatic protection is off (Protected with
  Google Play › Automatic protection), so Play adds no installer check code of its own and the delivered app runs the
  same code as this repository.
- **Words:** The listing does not claim that the app protects the phone (it only reminds), does not suggest installing
  apps from outside Play, and says that Watchdog is not affiliated with Samsung. The title is plain "Watchdog": Play
  allows several apps with the same title, and other apps called Watchdog already exist there.

## Security setup

- **Permissions:** Only `POST_NOTIFICATIONS` and `RECEIVE_BOOT_COMPLETED`. The manifest's `<queries>` entries are
  package visibility, not permissions: The one for `com.samsung.android.rampart` lets the app see whether Auto Blocker
  exists on the phone, the `MAIN`/`LAUNCHER` intent lets it see the apps with an icon in the app list, to check which of
  them may install unknown apps (see Settings read). `QUERY_ALL_PACKAGES` is not used. The app list is only looked at
  during a check, nothing about it is stored or leaves the phone.
- **Manifest check:** The `manifestCheck<Variant>` task (`ManifestCheck` in `app/build.gradle.kts`) checks the merged
  manifest of every variant before `package<Variant>`, `package<Variant>Bundle` and `check`. The build stops if the
  permission list differs from the two above, if a component is guarded by a forbidden privilege (settings writing,
  accessibility, device admin, notification listener, VPN, input method, internet), if there is a content provider, an
  app-defined permission, a shared user ID or a namespaced element, if a component does not state `android:exported`, if
  an exported component is not on the known list or not guarded by the expected system permission, if backup is on, or
  if the release package is debuggable. The report is written to `app/build/manifest-check/<variant>.txt`. The check was
  tested by adding the `INTERNET` permission: The build stopped and no package was produced.
- **Exported components:** `MainActivity` (the launcher), `BootReceiver` (only the boot, app update and clock change
  broadcasts, which only the system can send. Any other action is ignored) and `SettingChangeJob` (guarded by
  `BIND_JOB_SERVICE`). `CheckReceiver` and `SettingsOpenerActivity` are not exported: Only the app's own pending intents
  reach them, and those are immutable and name their component explicitly.
- **Screen:** The main screen and the choice window ignore touches while another app's overlay covers them
  (`filterTouchesWhenObscured`). This matters for the Check button, which moves the next check one interval later, and
  for the interval choice, which makes all later reminders rarer.
- **Notification:** The notification is private (`VISIBILITY_PRIVATE`) with a public version that shows only the app
  name and "You have a reminder". When the phone is set to hide sensitive content on the lock screen, the locked phone
  shows only that version. When it is set to show all content, Android shows the full text. An app cannot override this:
  Setting the channel's `lockscreenVisibility` was tried, and Android 16 resets it to "no override" for the app's own
  channels. The Auto Blocker action button works only after unlocking (`setAuthenticationRequired`).
- **Fake change signals:** Any app can send a change signal for a settings address (`ContentResolver.notifyChange`).
  Such a signal cannot postpone a check (see Scheduling) and never raises a new alert. While the screen is open, signals
  that arrive close together are merged into one check 500 ms after the last one, so a flood of signals cannot keep the
  screen busy. In the background, `JobScheduler`'s delays and quotas limit the job.
- **Auto Blocker and Settings intents:** Auto Blocker counts only when its package is a system app (`FLAG_SYSTEM`).
  Otherwise any sideloaded app could take the package name on a phone without Auto Blocker, and Watchdog would open its
  screen. Settings pages are first sent to the system Settings app by package (`com.android.settings`), then as plain
  intents for phones whose Settings app has another package name. The intents carry only a package name (the app's
  own, or the one app shown on the Install unknown apps row) and a settings key.
- **Data:** A few values in the private `SharedPreferences` file `store`: The check interval, the language, the last and
  the next check time, whether the notification permission was asked and the pending Auto Blocker reminder. Nothing is
  backed up to the cloud or moved to a new phone (`allowBackup="false"` and `res/xml/backup_rules.xml`). The manifest
  also sets `usesCleartextTraffic="false"` and an empty `taskAffinity`.
- **Dependencies:** At run time only Android's own classes and the Kotlin standard library. JUnit 4.13.2 is used only
  by the unit tests. Google's repository serves only the `com.android`, `com.google` and `androidx` groups, everything
  else comes from Maven Central, and project-level repositories are refused (`settings.gradle.kts`). The SHA-256 of
  every file the build downloads is pinned in `gradle/verification-metadata.xml`. If one does not match, the build
  stops. After an AGP or Gradle upgrade the new checksums are added with the configuration cache off (otherwise the
  aapt2 entry is missing). Do not delete the file first: Gradle keeps the existing entries and adds the new ones. Then
  review `git diff`: Only entries of the new versions may appear, the checksums of the old ones must not change. A file
  that was tampered with during the download would otherwise be pinned silently.

  ```bash
  ./tools/build.sh --no-configuration-cache --write-verification-metadata sha256 testDebugUnitTest assembleRelease assembleDebug lintDebug
  git diff gradle/verification-metadata.xml
  ```

## Settings read

| Setting | Table | Key |
|---|---|---|
| USB debugging | `Settings.Global` | `adb_enabled` |
| Wireless debugging | `Settings.Global` | `adb_wifi_enabled` |
| Auto Blocker (Samsung) | `Settings.Secure` | `rampart_main_switch_enabled` |
| Install unknown apps | `AppOpsManager`, per app | `android:request_install_packages` |

Developer options itself (`development_settings_enabled`) is not watched: Leaving it on is not a risk by itself. Watching
it also kept the reminder up after debugging was turned off and held back the Auto Blocker reminder.

Auto Blocker's key was found on a Samsung phone with One UI 8 and Android 16. Its value was 0 while Auto Blocker was off:

```bash
adb shell settings list secure | grep rampart
```

Samsung's internal name for Auto Blocker is "rampart" (package `com.samsung.android.rampart`). Since Android 12, reading
a hidden key that is not marked `@Readable` can throw a `SecurityException`. The state is then `UNKNOWN` (see the
decision logic below). A missing value is also read as `UNKNOWN` while the Auto Blocker package exists as a system
app. If it does not exist (not a Samsung phone, or an older One UI) or is not a system app, the state is `ABSENT`: The
row shows "Not available" and there is no Auto Blocker reminder. A debugging setting that cannot be read is `UNKNOWN` and never counts as on.

**Install unknown apps:** Since Android 8 this is not one switch but a permission per app (Settings › Apps › Special
access › Install unknown apps). An app that asks for `REQUEST_INSTALL_PACKAGES` may hand APK files to the installer only
while the user allows it there. The permission is an app operation: `SettingsReader` takes the op name from
`AppOpsManager.permissionToOp` and reads each app's mode with `checkOpNoThrow` (`unsafeCheckOpNoThrow` is deprecated in
SDK 37). The setting counts as on when at least one app has `MODE_ALLOWED`, the mode the Settings switch sets. The apps
are named on the screen and in the notification, sorted by name.

- Only the apps the app can see are checked (package visibility since Android 11): The ones with an icon in the app
  list, through the manifest's `MAIN`/`LAUNCHER` query. These are the apps people allow in practice (browsers, file
  managers, messaging and mail apps). An app without such an icon is not seen.
- Apps that hold `INSTALL_PACKAGES` are skipped: The phone's own stores and installers install without asking, so their
  switch changes nothing and a reminder about them could never be cleared. Watchdog itself does not ask for the
  permission.
- If the installed apps cannot be listed, the state is `UNKNOWN` and never counts as on.
- The setting has no settings address, so `SettingChangeJob` and the screen's `ContentObserver` do not see it change.
  After it is turned off, the notification goes away when Watchdog's screen is opened again (coming back from the
  Settings page is enough) or at the next check. Watching app operations of other apps would need `WATCH_APPOPS`, a
  system permission.
- The row opens the switch of the one app it is allowed for (`ACTION_MANAGE_UNKNOWN_APP_SOURCES` with a `package:` URI),
  or the list when there are several or none.

**Opening a setting scrolled into view:** When Developer options is opened with the `:settings:fragment_args_key` extra
(also inside a `:settings:show_fragment_args` bundle), the Settings app scrolls the page to the setting with that key and
highlights it. This is the path of the Settings app's own search. The keys come from `res/xml/development_settings.xml`
in Samsung's `SecSettings.apk` and are the same as in AOSP: `enable_adb` for USB debugging and `toggle_adb_wireless` for
wireless debugging. On phones that do not support it, the page simply opens (`Page.USB`, `Page.WIRELESS`). If Developer
options cannot be opened, the Settings app opens. This path is used only by the rows on the main screen.

Tapping the debugging notification opens the app, not the settings page: The screen shows the debugging settings and
Auto Blocker together, so Auto Blocker is not forgotten on the way. The Auto Blocker notifications and the Auto Blocker
button in the debugging notification open Auto Blocker's page directly, through the non-exported
`SettingsOpenerActivity`.

Auto Blocker's page is `com.samsung.android.rampart/.ui.MainSettingActivity`, action
`com.samsung.android.rampart.action.MAIN_SETTING_ACTIVITY` (exported, needs no permission). It is opened only when the
package is a system app. If it cannot be opened,
Security and privacy (`ACTION_SECURITY_SETTINGS`) opens, and if that fails, the Settings app. The other pages are the
app's notification settings, App info (battery) and the "Manage app if unused" page (`ACTION_AUTO_REVOKE_PERMISSIONS`),
each with App info as the fallback.

## Decision logic

`Decision.kt` does not depend on Android. All rules are there and covered by `DecisionTest`. `Checker` reads the
settings, passes them to `Decision.decide` with the type of the visible notification and the trigger, and applies the
outcome. The app has a single notification. Its type is stored in the notification's `extras`, no separate record is
kept.

The notification type, in order of priority:

1. `LEFT_ON`: USB or wireless debugging is on, or an app may install unknown apps. The title names the setting that is
   on (both debugging settings together), or says how many are on when Install unknown apps is on with debugging. Called
   `DEBUGGING` up to 0.1.1: `Notifier.visibleType` reads the old name from a notification posted before an update.
2. `AUTO_BLOCKER_OFF`: Auto Blocker was read as off (on phones that let apps read it).
3. `AUTO_BLOCKER_REMINDER`: Auto Blocker cannot be read, nothing is left on any more, and something was seen on before
   (the left-on notification or the reminder is visible, or the pending flag is set). This is the one-time "Turn on Auto
   Blocker now" reminder. Install unknown apps counts here too: On a Samsung phone an app from outside the Play Store
   can be installed only while Auto Blocker is off.
4. None: The notification is removed.

How it is delivered depends on what started the check:

| Trigger | Started by | Delivery |
|---|---|---|
| `ALARM` | The chosen interval is over (`CheckReceiver`) | `ALERT`: Shown with sound and vibration, again at every interval while the setting stays on |
| `BOOT` | Phone start, app update or clock change (`BootReceiver`) | `SHOW_SILENTLY`: Puts back the notification a restart removed |
| `SOFT` | A setting change (`SettingChangeJob`), the screen opening or a setting changing while it is open, a language change | `UPDATE_SILENTLY`: A visible notification is refreshed or removed, no new one appears |
| `MANUAL` | The Check button | `SHOW_SILENTLY`, and the result is told in a `Toast` |

- When no type applies, the delivery is `REMOVE`. An Auto Blocker reminder that is already visible is left alone
  (`LEAVE`): It never sounds again. A new Auto Blocker reminder appears silently even on a `SOFT` trigger, as soon as the
  settings left on go off.
- A check whose stored time has passed (also when it passed while the phone was off) is a full `ALARM` check, whatever
  started it (`Decision.effectiveTrigger`).
- **Pending Auto Blocker reminder:** When a check sees a setting left on while Auto Blocker cannot be read, the
  `auto_blocker_pending` flag is stored. When the settings go off, the reminder appears silently even if the debugging
  notification was never seen, and the flag is cleared once the reminder is posted. It is also cleared as soon as Auto
  Blocker becomes readable. The reminder closes on tap (which opens Auto Blocker's page) or swipe.
- **Notification text:** "Still on:" lists the settings that are on. Install unknown apps carries the apps it is
  allowed for: "Install unknown apps (Chrome and My Files)". If Auto Blocker exists and is not known to be on,
  the text adds "Afterwards, turn Auto Blocker back on too." and the notification gets an Auto Blocker button.
- **Silent posting:** Android's own `Notification.Builder` has no `setSilent`. As AndroidX does it, the notification is
  put in a group (`com.cgrshn.watchdog.SILENT`) with `GROUP_ALERT_SUMMARY`. There is no group summary, so no sound or
  vibration comes.
- A notification is posted only if it can reach the user (notifications allowed and the channel `reminders` not turned
  off). The channel has high importance.
- **Check message:** It names the setting that is on, or the number of settings on. While Auto Blocker cannot be read,
  it says "Nothing left on" rather than "All clear".

## Scheduling

Nothing runs in the background all the time. The process lives only for a short moment during a check.

- **Alarm:** `setWindow(RTC_WAKEUP, time, 10 min)`. There is no exact alarm permission. While the phone is in use the
  check is late by at most 10 minutes. A sleeping phone (Doze) checks at its first wake-up. The next check time is kept
  in the store.
- **Setting the time again:** Restart, force stop and update clear alarms. The stored time is set again when the app
  opens, when it is updated, when the phone starts, when the clock changes and when a setting changes. On these paths
  the time is never pushed later, so another app cannot postpone the check by sending a fake change signal for a setting
  address. It is only capped at now + interval (when the clock is set back). On first start the first check is one
  interval later. When the interval changes, the next check can only move earlier. The rule is
  `Decision.rescheduleTime` and is covered by unit tests.
- **Moving the time later:** Only the end of an interval and the Check button start the countdown again
  (`Decision.nextCheck`). The Check button works only on the app's own screen while it is not covered.
- **Intervals:** Every hour, every 6 hours, every day, every 2 days, every 3 days and every week (the default). A stored
  value that is not on this list falls back to the default.
- **Content-triggered job:** A one-time `JobScheduler` job bound to the three settings' addresses (500 ms
  update delay, at most 3 s). When a setting changes, the system runs `SettingChangeJob`, and a new job is set up after
  it ends. This way the notification goes away as soon as the setting is turned off. If the waiting job watches other
  addresses (left over from an earlier version), it is set up again with the same ID. If the job cannot be set up, the
  scheduled check still runs and the notification only goes away later. While the screen is open, a `ContentObserver` on
  the same addresses refreshes it, merging signals that arrive within 500 ms.
- **Sleeping and paused apps:** Samsung can put apps to sleep, and Android pauses apps that are not opened for a long
  time. Both can stop the checks. The main screen shows a warning if the app is restricted in battery settings
  (`isBackgroundRestricted`), is not exempt from battery optimization (`isIgnoringBatteryOptimizations`) or can be paused
  (`isAutoRevokeWhitelisted`), and opens the matching settings page.

## Language

The app speaks English and Turkish. The language is chosen in the app (App › Language), independent of the phone's
language. The default is English.

- English texts are in `values/` (the default), Turkish ones in `values-tr/`. The app's name is "Watchdog" in both
  languages (`translatable="false"`). Setting names follow Samsung's One UI in each language (Developer options, Auto
  Blocker, Geliştirici seçenekleri, Otomatik Engelleyici).
- The choice is `Store.language` (`en` or `tr`, anything else falls back to English). `Localized.context` returns a
  `createConfigurationContext` for the chosen language. The screen uses it in `attachBaseContext` and `Notifier` uses it
  for every notification, so notifications from the receivers and the job are in the chosen language too. The override
  carries only the language (`Configuration()`), so the screen metrics do not freeze (for example when a split-screen
  window is resized). The localized application context is kept for the background checks, so a new context is not
  built at every check. Contexts of a screen are built fresh and never kept.
- When the language changes, the visible notification is rewritten silently in the new language, whatever its type
  (`Notifier.rewrite`), the channel name is renewed and the screen is rebuilt.
- Android's per-app language setting (`localeConfig`) is not used on purpose: The app's language must not follow the
  phone's language, and Android's per-app language is only available from Android 13. AndroidX is not used, so the
  language is applied by hand.
- Interval, date and list formats are in `Format`, per language: English "Sunday, 15 November at 14:30", "Every 6
  hours", "A, B and C". Turkish "15 Kasım Pazar 14.30" (Turkish spelling rules separate hours and minutes with a dot), "6
  saatte bir", "A, B ve C".
- The App Bundle is not split by language (`bundle.language.enableSplit = false`): The texts of the language chosen in
  the app must be present on every install.

## Interface

A single screen built with Android's own `View`s, dark theme only (`Theme.DeviceDefault`, so system windows look like
One UI on Samsung phones). The direction is an instrument panel on a night shift: Surfaces and text are graphite and
colorless, color appears only in the status lamps (green fine, amber left on, grey unknown). The status card is the one
bright element of the screen.

- From top to bottom: The app's name and tagline, the warnings (notifications off, battery restriction or pausing), the
  "Status" title with the time of the last check on its right, the status card, the Check button, the "Checks" section
  (check interval, next check) and the "App" section (language), and a line saying the app only reads settings.
- Each status row has a lamp, the setting's name, where the setting lives on the phone under the name, and the value.
  While apps are allowed to install unknown apps, their names take the place of the location line, in the amber of a
  setting left on. There is no big summary headline above the card: It would claim "all clear" while Auto Blocker is
  unknown.
- Every row that can be tapped has a chevron on the right (`ic_chevron`, `text_secondary`). "Next check" cannot be
  tapped and has none. Screen readers read each status row as name, value and location.
- Since Android 15 the screen is drawn edge to edge. Scrolled content is cut below the status bar and the last row ends
  above the navigation bar.
- **Choice window** (`ChoiceDialog`): The language and the check interval are chosen in the app's own window, not in a
  system `AlertDialog`, so it reads as part of the app. It is a raised card in the screen's colors (`card_raised`, 24 dp
  corners, a hairline border), with a title, a short line about what the choice changes, the options and a quiet Cancel.
  The chosen option has a green ring with a dot, in the same visual language as the status lamps. It follows the row's
  selected state, which screen readers announce. Tapping the current option or Cancel only closes the window. The
  window is at most 420 dp wide, with 24 dp side margins on narrow screens.
- The final visual check is done on a phone, not only in the emulator: Samsung's font is wider. "Otomatik Engelleyici"
  fit on one line in the emulator but wrapped on the phone. The values are therefore short ("Unknown", "Bilinmiyor"),
  the lamp gap is 8 dp and rows with a chevron have 12 dp end padding.

## Icon

The symbol is an upward USB plug whose body is a shield, white on a vermilion background. The icon follows the usual
adaptive icon rules: A 108 dp layer, the symbol inside the 66 dp safe zone, no plate of its own, a single filled white
symbol and a notification icon drawn separately at 24 dp.

- Launcher: `res/mipmap-anydpi/ic_launcher.xml`. Background `@color/icon_background` (#E0502A, contrast 3.9:1 with
  white), foreground `res/drawable/ic_launcher_foreground.xml`, monochrome layer for themed icons
  `res/drawable/ic_launcher_monochrome.xml` (the same silhouette, the prongs are holes).
- Notification: `res/drawable/ic_notification.xml` (24 dp, thinnest part and gap 2 dp, whole-number coordinates). The
  notification color is `@color/brand`, the same vermilion.

## Testing on a phone

Tested on a Samsung phone with One UI 8 and Android 16. While Auto Blocker is on, USB commands are blocked, so adb works only
while it is off. To install the release package over an earlier build from the same PC:

```bash
adb install -r app/build/outputs/apk/release/app-release.apk
```

The schedule can be seen in the system's state:

```bash
adb shell dumpsys alarm | grep -A 3 com.cgrshn.watchdog
adb shell dumpsys jobscheduler com.cgrshn.watchdog
```

What was checked:

- USB and wireless debugging are read correctly.
- Auto Blocker cannot be read: Android does not let a third-party app read Samsung's `rampart_main_switch_enabled`
  (target SDK 37). Its page opens directly.
- The alarm (10-minute window) and the content-triggered job on the three setting addresses appear in `dumpsys alarm`
  and `dumpsys jobscheduler`. After an update, the alarm was set again from the stored time. Opening the app does not
  change the alarm. An alarm ran at the end of its interval and set the next check one interval later.
- The Check button: The message "Checked: USB debugging on" appears, the last check time is renewed, the notification is
  posted again in the silent group, and the alarm moves one interval later.
- Developer options scrolls to USB debugging and to wireless debugging, both with `am start` and from the app's rows:
  The page opens with the setting brought into view.
- The tap target of the debugging notification is `MainActivity` (request code 1) in `dumpsys activity intents`. It was
  not tested by tapping the notification.
- Both languages: The screen, the Check message, the visible notification and the channel name switch together.
- The names in the battery and pausing warnings match the phone's Turkish One UI (Uygulama bilgileri › Pil ›
  Kısıtlanmamış, Kullanılmayan uygulamayı yönet). The English ones follow Samsung's English One UI.

- Install unknown apps (2026-11-20, with a test copy whose package name has the suffix `.deneme`, installed beside the
  real app through a Gradle init script that sets `applicationIdSuffix` for the debug build): The app found the same two
  allowed apps as `adb shell appops query-op REQUEST_INSTALL_PACKAGES allow` (My Files and WhatsApp), named on the row
  and in the notification ("2 settings are still on", "Still on: USB debugging and Install unknown apps (Dosyalarım and
  WhatsApp)") in both languages. The app names come from the phone, so they follow the phone's language. With several
  apps the row opens the list (`Settings$ManageExternalSourcesActivity`). With one app it opens that app's switch
  (`Settings$ManageAppExternalSourcesActivity`), tested with a temporary build that opened the first app. One UI 8 names
  the place Apps › ⋮ › Special access (Uygulamalar › Özel erişim). The long Turkish name wraps to two lines on the row.

Not yet tested on the phone: Turning Install unknown apps off and the notification going away (the phone's settings were
not changed during the test). The debugging notification turning into "Turn on Auto Blocker now" when the settings go
off, and the notification coming back after a restart. With Auto Blocker on, adb cannot be used for these, so they are
checked in real use.
