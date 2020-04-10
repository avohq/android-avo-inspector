# AvoInspector

[![](https://jitpack.io/v/avohq/android-avo-inspector.svg)](https://jitpack.io/#avohq/android-avo-inspector)

# Avo documentation

This is a quick start guide.
For more information about the Inspector project please read [Avo documentation](https://www.avo.app/docs/inspector/sdk/android).

# Installation

We host the library on JitPack.io, so

add the following to the root build.gradle:

```
    allprojects {
        repositories {
          ...
          maven { url 'https://jitpack.io' }
        }
    }
```

and in your module build.gradle:

```
    dependencies {
        implementation 'com.github.avohq:android-avo-inspector:x.x.x'
    }
```

Use the latest github release tag to get the latest version of the library.

# Initialization

Obtain the API key at [Avo.app](https://www.avo.app/welcome)

Java
```java
AvoInspector avoInspector = new AvoInspector("MY_API_KEY", getApplication(), AvoInspectorEnv.Dev, activity);
```

Kotlin
```kotlin
val avoInspector = AvoInspector("MY_API_KEY", application, AvoInspectorEnv.Dev, activity)
```

Activity is optional, if you provide it in development or staging mode the library will show the visual inspector.

# Enabling logs

Logs are enabled by default in the dev mode and disabled in prod mode based on the init flag.

Java
```java
AvoInspector.enableLogging(true);
```

Kotlin
```kotlin
AvoInspector.enableLogging(true)
```

# Sending event schemas

Whenever you send tracking event call one of the following methods:
Read more in the [Avo documentation](https://www.avo.app/docs/inspector/sdk/android#event-tracking)

### 1.

This methods get actual tracking event parameters, extract schema automatically and send it to the Avo Inspector backend.
It is the easiest way to use the library, just call this method at the same place you call your analytics tools' track methods with the same parameters.

Java
```java
avoInspector.trackSchemaFromEvent("Event name", new HashMap<String, Object>() {{
                        put("String Prop", "Prop Value");
                        put("Float Name", 1.0);
                        put("Bool Name", true);
                    }});
```
Second parameter can also be a `JSONObject`.

Kotlin
```kotlin
avoInspector.trackSchemaFromEvent("Event name",
        mapOf("String Prop" to "Prop Value",
               "Float Name" to 1.0,
                "Bool Name" to true))
```
Second parameter can also be a `JSONObject`.

### 2.

If you prefer to extract data schema manually you would use this method.

Java
```java
avoInspector.trackSchema("Event name", new HashMap<String, AvoEventSchemaType>() {{
            put("String Prop", new AvoEventSchemaType.AvoString());
            put("Float Name", new AvoEventSchemaType.AvoFloat());
            put("Bool Name", new AvoEventSchemaType.AvoBoolean());
        }});
```

Kotlin
```kotlin
avoInspector.trackSchema("Event name",
        mapOf("String Prop" to AvoString(),
               "Float Name" to AvoFloat(),
                "Bool Name" to AvoBoolean()))
```

# Extracting event schema manually

Java
```java
Map<String, AvoEventSchemaType> schema = avoInspector.extractSchema(new HashMap<String, Object>() {{
            put("String Prop", "Prop Value");
            put("Float Name", 1.0);
            put("Bool Name", true);
        }});
```

Kotlin
```kotlin
val schema = avoInspector.extractSchema(mapOf("String Prop" to "Prop Value",
                "Float Name" to 1.0,
                "Bool Name" to true))
```

# Using the Visual Inspector

Visual Inspector is enabled in development and staging environments by default.

## Show

Java
```java
avoInspector.showVisualInspector(activity, DebuggerMode.bubble); // or DebuggerMode.bar
```

Kotlin
```kotlin
avoInspector.showVisualInspector(activity, DebuggerMode.bubble) // or DebuggerMode.bar
```

## Hide

Java
```java
avoInspector.hideVisualInspector(activity);
```

Kotlin
```kotlin
avoInspector.hideVisualInspector(activity)
```

## Advanced usage

You can get an instance of `DebuggerManager` with the following method.

Java
```java
DebuggerManager visualInspector = avoInspector.getVisualInspector();
```

Kotlin
```kotlin
val visualInspector = avoInspector.visualInspector
```

See more about the `DebuggerManager` in [GitHub repo](https://github.com/avohq/android-analytics-debugger)

# Batching control

In order to ensure our SDK doesn't have a large impact on performance or battery life it supports event schemas batching.

Default batch size is 30 and default batch flush timeout is 30 seconds.
In debug mode default batch flush timeout is 1 second, i.e. the SDK batches schemas of events sent withing one second.

Java
```java
AvoInspector.setBatchSize(15);
AvoInspector.setBatchFlushSeconds(10);
```

Kotlin
```kotlin
AvoInspector.setBatchSize(15)
AvoInspector.setBatchFlushSeconds(10)
```

## Author

Avo (https://www.avo.app), friends@avo.app

## License

AvoInspector is available under the MIT license.