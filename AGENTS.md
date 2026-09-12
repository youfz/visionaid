# Repository Guidelines

## Project Structure & Module Organization

VisionAid is a single-module Android application. Kotlin production code lives in `app/src/main/java/com/you/visionaid`, organized by responsibility: `ui/` contains fragments and the activity, `viewmodel/` owns screen state, `domain/` defines core contracts and models, `data/` provides implementations, and `core/di/` wires dependencies. XML layouts, navigation, strings, colors, dimensions, and drawables are under `app/src/main/res`. Local JVM tests belong in `app/src/test`; device or emulator tests belong in `app/src/androidTest`. Treat `specs/` as the source of truth for screen references, icons, and design tokens.

## Build, Test, and Development Commands

Run commands from the repository root. On Windows PowerShell, use `./gradlew.bat`; on macOS or Linux, substitute `./gradlew`.

- `./gradlew.bat assembleDebug` builds a debug APK.
- `./gradlew.bat testDebugUnitTest` runs local JUnit tests.
- `./gradlew.bat connectedDebugAndroidTest` runs Espresso/instrumentation tests on a connected device or emulator.
- `./gradlew.bat lintDebug` performs Android static analysis.
- `./gradlew.bat clean` removes generated build output when troubleshooting stale artifacts.

Use Android Studio to deploy the `app` configuration for interactive development.

## Coding Style & Naming Conventions

Follow standard Kotlin and Android conventions: four-space indentation, trailing commas in multiline declarations, and small single-purpose functions. Use `PascalCase` for classes, fragments, and view models; `camelCase` for functions and properties; and `UPPER_SNAKE_CASE` for constants. Resource files and IDs use lowercase `snake_case`, such as `fragment_ocr_result.xml`. Keep package names aligned with `com.you.visionaid`. No dedicated formatter is configured, so apply Android Studio formatting and optimize imports before committing. Put user-visible text in `res/values/strings.xml`.

## Testing Guidelines

JUnit 4 and `kotlinx-coroutines-test` cover local logic; AndroidX JUnit and Espresso cover device behavior. Name test classes after their subject (`ReadingViewModelTest`) and use descriptive test methods, including Kotlin backtick names. Add unit tests for state and domain changes, and instrumentation tests for navigation or Android framework integration. There is no configured coverage threshold; prioritize meaningful regression coverage.

## Commit & Pull Request Guidelines

History currently contains only an initial commit, so no formal message convention is established. Use short, imperative subjects such as `Add OCR loading state`, and keep each commit focused. Pull requests should explain the behavior change, list verification commands, link relevant issues, and include screenshots or recordings for UI changes. Note any deviations from `specs/` and accessibility impacts.

## Security & Configuration

Do not commit `local.properties`, SDK paths, credentials, or generated build directories. Keep environment-specific configuration local and update `.gitignore` when introducing new generated or sensitive files.
