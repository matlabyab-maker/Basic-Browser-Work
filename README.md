# Basic Browser Work

Android browser project.

## Build
- JDK 17
- Android SDK platform 35
- Gradle 8.9
- Run `gradle --no-daemon assembleDebug`

GitHub Actions runs on pushes to every branch and supports manual dispatch.
The main Kotlin activity is in the shallow `browser-core/` directory.


## Source layout
The main browser activity is stored in the shallow `browser-core/` directory, registered explicitly as an Android source directory.
