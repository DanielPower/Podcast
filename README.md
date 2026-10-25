# Podcast

A simple Android podcast player built around a single queue.

- **Queue**: every episode waiting to be played. Drag the handle to reorder, swipe right to remove, tap to play now.
- **Podcasts**: a grid of subscriptions. "Add podcast" searches the Apple Podcasts directory.
- New episodes are downloaded automatically (checked hourly, and when the app opens). Once a download finishes, the episode is appended to the queue.
- When an episode finishes it is removed from the queue, its file is deleted, and the next episode starts.

## Building

Kotlin + Jetpack Compose, Room, WorkManager and Media3 (ExoPlayer). Requires JDK 17+ and the Android SDK.

```sh
./gradlew assembleDebug
```

CI builds a debug APK on every pull request; download it from the workflow run's artifacts.
