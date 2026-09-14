# COMP90018_Mobile_Systems_2026
COMP90018 Mobile Computing Systems Programming Sem 2 2026 Group Project

A spaced-repetition flashcard app built with Jetpack Compose, Room, Hilt, and Navigation Compose.

## Requirements

- JDK 17
- Android Studio (latest stable) or the Gradle wrapper on the command line

## Building & running

```bash
./gradlew assembleDebug   # build a debug APK
./gradlew test            # run unit tests
./gradlew check           # run tests, lint, ktlint, and detekt
```

## Code style & static analysis

The project uses [ktlint](https://github.com/pinterest/ktlint) (via the [JLLeitschuh Gradle plugin](https://github.com/JLLeitschuh/ktlint-gradle)) for formatting and [detekt](https://detekt.dev/) for static analysis. Both run automatically as part of `./gradlew check`.

| Task                  | What it does                                                  |
|------------------------|----------------------------------------------------------------|
| `./gradlew ktlintCheck` | Verifies Kotlin code follows the official style, no changes made |
| `./gradlew ktlintFormat` | Auto-formats Kotlin code to fix what it can                   |
| `./gradlew detekt`      | Runs static analysis (complexity, unused code, Compose best practices, etc.) |

Detekt's findings are written to an HTML report at `app/build/reports/detekt/detekt.html` (open it in a browser) — this is a build artifact, not committed to git, and gets regenerated on every run.

Before pushing, run:

```bash
./gradlew ktlintFormat detekt
```

### Configuration

- `.editorconfig` — formatting rules ktlint reads (indent size, max line length, etc.). Also configures Android Studio's own formatter to match. Wildcard imports are disallowed everywhere **except** `androidx.compose.foundation.layout.*`, `androidx.compose.material3.*`, and `androidx.compose.runtime.*`, matching [Compose's own API guidelines](https://github.com/androidx/androidx/blob/androidx-main/compose/docs/compose-api-guidelines.md).
- `config/detekt/detekt.yml` — detekt rule overrides. Builds on detekt's default ruleset (`buildUponDefaultConfig`), so only deviations from the defaults are listed here.
- `config/detekt/baseline.xml` — pre-existing findings from before detekt was introduced, so `check` doesn't fail on legacy code. **New code must not add entries here.** If you need to suppress a specific new finding, prefer an inline `@Suppress("RuleId")` (with a comment explaining why) over regenerating the baseline. To regenerate the baseline after intentionally accepting a batch of findings, run `./gradlew detektBaseline`.

Detekt also pulls in the [Compose rules](https://mrmans0n.github.io/compose-rules/) ruleset (`io.nlopez.compose.rules:detekt`), which checks Compose-specific conventions such as exposing a `modifier: Modifier` parameter on stateless composables.

## Continuous integration

`.github/workflows/ci.yml` runs `./gradlew check` (tests, Android Lint, ktlint, detekt) on every pull request and on every push to `main`. A red check on a PR means one of those failed — click into the run's logs to see which task and what it reported; the failure will match what you'd see running that same command locally. There's no auto-fix step in CI (it doesn't push formatting changes back) — run `./gradlew ktlintFormat detekt` locally and push the fix.

### Troubleshooting: `./gradlew` fails with `What went wrong: 26` (or similar bare number)

This project's Gradle wrapper (8.7) doesn't support very new JDKs — if your machine's default `java` is something like JDK 24+ (check with `java -version`), Gradle fails to even start, with an unhelpful error that's just the major version number.

Fix: install JDK 17 (e.g. via [Adoptium](https://adoptium.net/) or `sdk install java 17.0.x-tem` with [SDKMAN](https://sdkman.io/)), then point Gradle at it **globally on your machine** — not in this repo, since the path is machine-specific:

```properties
# ~/.gradle/gradle.properties (create if it doesn't exist)
org.gradle.java.home=/path/to/your/jdk-17
```

Find your installed JDKs' paths with `/usr/libexec/java_home -V` (macOS) or `update-alternatives --list java` (Linux). Don't add `org.gradle.java.home` to the project's own `gradle.properties` — it would hardcode your personal file path for every other contributor.
