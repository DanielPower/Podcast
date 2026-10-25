# Podcast

A simple Android podcast player built around a single queue.

- **Queue**: every episode waiting to be played. Drag the handle to reorder, swipe right to remove, tap to open the episode.
- **Episode / Now Playing**: one screen for both. It shows chapters, show notes and details; the bottom pane has the player controls and a seek bar split by chapter when the episode is playing, or Play now / Play next when it isn't. Tap the mini player to open it.
- **Podcasts**: a grid of subscriptions. "Add podcast" searches the Apple Podcasts directory.
- New episodes are downloaded automatically (checked hourly, and when the app opens). Each takes its place at the end of the queue as soon as its download starts, showing progress, and becomes playable once downloaded.
- **Android Auto**: the queue is browsable in the car, and "next" skips the current episode (removes it from the queue).
- When an episode finishes it is removed from the queue and the next episode starts.
- Downloads are kept until an episode has been out of the queue for a week, then deleted.
- From a podcast's page, older episodes can be downloaded; they join the end of the queue while downloading.
- **Settings** (the gear on Queue and Podcasts): colour scheme, Auto/Light/Dark, skip lengths, whether new episodes download automatically, Wi-Fi only, and how long removed episodes are kept.

## Design

The look follows the "Washi" design of [Jiwa Jiwa](https://github.com/DanielPower/JiwaJiwa): paper and ink with one accent colour, Zen Kaku Gothic New (OFL, licence in `app/src/main/assets/licenses`), 4 dp corners, and one job per colour. In every scheme the accent marks what's playing and the main control, ochre (tertiary) means downloading, crimson (error) means remove or failed, the link colour (secondary) is for quiet links, and ink is for everything that only needs reading. The schemes are Washi (Jiwa Jiwa's palette), Ai (indigo), Matcha (green tea) and Sumi (black ink only), each with a light and dark version, in `ui/Theme.kt`. Shared components (headers, buttons, chips, switch, nav bar) are in `ui/Washi.kt`; use them for new screens rather than stock Material ones.

## Building

Kotlin + Jetpack Compose, Room, WorkManager and Media3 (ExoPlayer). Requires JDK 17+ and the Android SDK.

```sh
./gradlew assembleDebug
```

CI builds a debug APK on every pull request; download it from the workflow run's artifacts.
