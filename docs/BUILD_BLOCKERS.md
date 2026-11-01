# Build & Environment Blockers

The following issues prevent compilation / APK build / test execution in the current environment. They are **environment limitations, not code defects**, and are disclosed honestly per the master prompt rule #10.

## B-01: No Android SDK

- `ANDROID_HOME` / `ANDROID_SDK_ROOT` not set.
- No `platform-tools`, `build-tools`, `cmdline-tools`, or Android platforms installed.
- Effect: `./gradlew :app:assembleDebug` cannot compile Kotlin, link the NDK, or package an APK.
- All Android-facing feature statuses are **UNVERIFIED**; instrumented/Espresso tests cannot run.

## B-02: Invalid `org.gradle.java.home`

- `gradle.properties` sets `org.gradle.java.home=D:/jdk-21.0.5`, which does not exist.
- Installed Java: JDK 27 at `D:\jdk-27` (`java.home = D:\jdk-27`), Java version 27.
- Gradle fails at startup: `Value 'D:/jdk-21.0.5' given for org.gradle.java.home ... is invalid`.
- Workaround: set `org.gradle.java.home` to the installed JDK (21 or later) or `GRADLE_JAVA_HOME`, then re-run Gradle.

## B-03: TFLite interpreter unavailable for ML evaluation

- `tensorflow` / `tflite-runtime` installation is blocked by application-control policy (`_pywrap_debug_events_writer` DLL load failed).
- `pip install tflite-runtime` has no matching distribution for this Python/platform.
- Effect: the production TFLite model (`phishing_model.tflite`, 3.6 MB) used by the former UI scanner path **cannot be executed or evaluated here**.
- Only the ONNX model (`neuroshield_model.onnx`) was evaluated (onnxruntime available).
- TFLite metrics: **NOT VERIFIED / BLOCKED**.

## B-04: Release signing keys not present

- `protctus-release-key.jks` exists in the repo root but no `RELEASE_KEYSTORE_PATH`/passwords are configured.
- Release build falls back to debug signing.
- Do not commit keystore passwords; provide via `local.properties` / CI secrets.

## Recommended actions

1. Install Android SDK and set `ANDROID_HOME`.
2. Fix `gradle.properties` `org.gradle.java.home` to a valid JDK (21+).
3. Run `./gradlew :app:assembleDebug` and `:app:testDebugUnitTest`.
4. Resolve TFLite evaluation in an environment with a working TFL interpreter (or certify the ONNX path as the single production model and retire TFLite).
5. Set the real `SignatureVerifier` expected hash for release builds.
