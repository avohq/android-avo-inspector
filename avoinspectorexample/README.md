# Avo Inspector Example App

Example app demonstrating the Avo Inspector SDK integration.

## Building with Different Dependency Sources

The example app supports four dependency sources for the Avo Inspector SDK.

### IDE (Android Studio)

Edit `gradle.properties` in the project root:

```properties
# Options: maven, jitpack, local
source=maven
ref=2.2.1
```

Change the value and sync Gradle to switch sources.

### Command Line

### Maven Central (default)

Uses the released version from Maven Central:

```bash
./gradlew :avoinspectorexample:assembleDebug
```

### JitPack

Uses JitPack for testing release tags, commits, or branches:

```bash
# Using a release tag
./gradlew :avoinspectorexample:assembleDebug -Psource=jitpack -Pref=2.2.1

# Using a commit hash
./gradlew :avoinspectorexample:assembleDebug -Psource=jitpack -Pref=abc1234

# Using a branch (use ~ instead of /)
./gradlew :avoinspectorexample:assembleDebug -Psource=jitpack -Pref=feat~branch-name-SNAPSHOT
```

### Local Module

Uses the local `avoinspector` module for development:

```bash
./gradlew :avoinspectorexample:assembleDebug -Psource=local
```

## Summary

| Source | Command | Use Case |
|--------|---------|----------|
| Maven | `./gradlew assembleDebug` | Test released version |
| JitPack | `./gradlew assembleDebug -Psource=jitpack -Pref=<ref>` | Test tags/commits/branches |
| Local | `./gradlew assembleDebug -Psource=local` | Development |
