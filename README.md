# Glance Kiosk

A tiny Android app that shows one web dashboard full screen, with no
browser chrome and no system bars. Built for a
[Glance](https://github.com/glanceapp/glance) dashboard on a wall tablet,
but it works for any site.

Why not just "Add to Home screen" in Chrome? On Android 16, Chrome's
home-screen web apps keep a status-bar-high strip at the top of the page
even when the site asks for `"display": "fullscreen"`. This app is a single
WebView that hides the system bars itself.

Not affiliated with the Glance project.

## What it does

- Full screen (immersive): status and navigation bars hidden. Swipe from an
  edge to show them briefly.
- Keeps the screen on while it is in front.
- Keeps cookies, so a login survives restarts.
- Links to other sites open in your normal browser; the dashboard stays.
- After a failed load it retries every 15 s. Every 10 minutes it reloads the
  page, but not within 2 minutes of a touch.
- Page zoom (default 0.9): the page is laid out wider than the screen and
  scaled down, the way browser zoom works. At 100 % on an 8" tablet in
  landscape, Glance falls back to its one-column phone layout; at 90 % it
  keeps three columns.
- Back goes back in the page history.

Android 8.0 (API 26) or newer. No Google services, no permissions beyond
network access. Single activity, about 220 lines of Java.

## Build

Needs a JDK (17+) and the Android SDK with `build-tools;36.0.0` and
`platforms;android-36`. There is no Gradle: `build.sh` calls aapt2, javac,
d8, zipalign and apksigner directly.

```sh
KIOSK_URL=https://glance.example.com/ ./build.sh
adb install -r glance-kiosk.apk
```

Settings, from the environment or a `kiosk.env` file next to `build.sh`
(git-ignored):

| Variable | Default | |
|---|---|---|
| `KIOSK_URL` | (required) | the page to show; links to other hosts open in the browser |
| `KIOSK_ZOOM` | `0.9` | page zoom; lower shows more |
| `KIOSK_DEBUG` | `0` | `1` enables WebView remote debugging over adb (`chrome://inspect`) |

The first build creates a signing key, `glance.keystore`, next to the
script. Keep it: an APK signed with another key cannot be installed over
the old one without uninstalling it, which also loses the login.

## Use

Open **Glance Kiosk** and log in once. To make it start on boot or stay
pinned, use Android's own settings (for example *App pinning*, or set a
launcher that starts it).

## Disclosure

The code and this README were written by Claude (Anthropic's AI model) in a
Claude Code session. I tested it on an onn 8" tablet running Android 16.

## License

MIT
