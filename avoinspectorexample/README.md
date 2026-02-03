# Avo Inspector Example App

Example app demonstrating the Avo Inspector SDK integration.

## Building with Different Dependency Sources

The example app supports three dependency sources for the Avo Inspector SDK.

### IDE (Android Studio)

Edit `gradle.properties` in the project root:

```properties
# Options: maven, jitpack, local
source=maven
```

Change the value and sync Gradle to switch sources.

### Command Line

### Maven Central (default)

Uses the released version from Maven Central:

```bash
./gradlew :avoinspectorexample:assembleDebug
./gradlew :avoinspectorexample:assembleRelease
```

### JitPack

Uses JitPack for testing pre-release builds. Specify a branch, tag, or commit with `-Pref`:

```bash
# Using a tag
./gradlew :avoinspectorexample:assembleDebug -Psource=jitpack -Pref=2.2.1

# Using a branch
./gradlew :avoinspectorexample:assembleDebug -Psource=jitpack -Pref=master

# Using a commit hash
./gradlew :avoinspectorexample:assembleDebug -Psource=jitpack -Pref=abc1234
```

### Local Module

Uses the local `avoinspector` module for development:

```bash
./gradlew :avoinspectorexample:assembleDebug -Psource=local
./gradlew :avoinspectorexample:assembleRelease -Psource=local
```

## Summary

| Source | Command | Use Case |
|--------|---------|----------|
| Maven | `./gradlew assembleDebug` | Test released version |
| JitPack | `./gradlew assembleDebug -Psource=jitpack -Pref=<ref>` | Test pre-release builds |
| Local | `./gradlew assembleDebug -Psource=local` | Development |
