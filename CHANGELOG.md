## 2.0.3

Disabled sentry logging

## 2.0.2

Fixed a race condition that caused some of the early events in the app lifecycle to be not reported.

## 2.0.1

Disabled batching in the dev mode by default.

## 2.0.0

Splitting the lib into 2 flavours.

`com.github.avohq.android-avo-inspector:dev:TAG` includes the visual debugger and a SYSTEM_ALERT_WINDOW permission

`com.github.avohq.android-avo-inspector:prod:TAG` does not include the visual debugger and a SYSTEM_ALERT_WINDOW permission

Suggested usage is:

```
releaseImplementation 'com.github.avohq.android-avo-inspector:prod:TAG'
debugImplementation 'com.github.avohq.android-avo-inspector:dev:TAG'
```

### Breaking change

- `Inspector.getVisualInspector()` now returns a nullable `Object` instead of nullable `DebuggerManager`.
You can safely cast it to a nullable `DebuggerManager` in the `dev` build and it will always be `null` in the prod build.

- Dependency reference is changed to  `com.github.avohq.android-avo-inspector:dev:TAG` and `com.github.avohq.android-avo-inspector:prod:TAG`
(used to be from `com.github.avohq:android-avo-inspector:TAG`)
