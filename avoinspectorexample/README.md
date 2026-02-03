# Avo Inspector Example App

Example app demonstrating the Avo Inspector SDK integration.

## Building with Different Dependency Sources

The example app supports four dependency sources for the Avo Inspector SDK.

### IDE (Android Studio)

Edit `gradle.properties` in the project root:

```properties
# Options: maven, jitpack-release, jitpack-commit, local
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

### JitPack Release

Uses JitPack for testing release tags:

```bash
./gradlew :avoinspectorexample:assembleDebug -Psource=jitpack-release -Pref=2.2.1
```

### JitPack Commit

Uses JitPack for testing commits or branches:

```bash
# Using a commit hash
./gradlew :avoinspectorexample:assembleDebug -Psource=jitpack-commit -Pref=abc1234

# Using a branch (use ~ instead of /)
./gradlew :avoinspectorexample:assembleDebug -Psource=jitpack-commit -Pref=feat~branch-name-SNAPSHOT
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
| JitPack Release | `./gradlew assembleDebug -Psource=jitpack-release -Pref=<tag>` | Test release tags |
| JitPack Commit | `./gradlew assembleDebug -Psource=jitpack-commit -Pref=<commit>` | Test commits/branches |
| Local | `./gradlew assembleDebug -Psource=local` | Development |
