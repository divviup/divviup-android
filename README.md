# divviup-android
An Android client library for the [Distributed Aggregation Protocol][DAP].

[DAP]: https://datatracker.ietf.org/doc/draft-ietf-ppm-dap/

## Protocol Versions and Release Branches

The following versions of the DAP protocol are supported by different branches
and releases.

| Package version | Git branch    | Protocol version                    | Conformant? | Status    |
|-----------------|---------------|-------------------------------------|-------------|-----------|
| 0.1.0           | `release/0.1` | [`draft-ietf-ppm-dap-07`][draft-07] | Yes         | Unmaintained as of June 24, 2024 |
| 0.2.0           | `main`        | [`draft-ietf-ppm-dap-09`][draft-09] | Yes         | Supported |

[draft-07]: https://datatracker.ietf.org/doc/draft-ietf-ppm-dap/07/
[draft-09]: https://datatracker.ietf.org/doc/draft-ietf-ppm-dap/09/
[draft-09-issue]: https://github.com/divviup/divviup-android/issues/101

## Usage

Ensure that Maven Central is in the list of repositories from which
dependencies are resolved. Add the library to your project as follows.

```groovy
// build.gradle

dependencies {
    implementation 'org.divviup.android:divviup-android:0.1.0'
}
```

```kotlin
// build.gradle.kts

dependencies {
    implementation("org.divviup.android:divviup-android:0.1.0")
}
```

Construct a `Client` from your DAP task's parameters, and use it to send report as follows.
(Note that this should be done off the main thread)

```java
import android.util.Log;

import org.divviup.android.Client;
import org.divviup.android.TaskId;

import java.net.URI;

public class MyRunnable implements Runnable {
    public void run() {
        try {
            URI leaderEndpoint = new URI("https://<your leader here>/");
            URI helperEndpoint = new URI("https://<your helper here>/");
            TaskId taskId = TaskId.parse("<your DAP TaskId here>");
            long timePrecisionSeconds = <your time precision here>;
            Client<Boolean> client = Client.createPrio3Count(context, leaderEndpoint, helperEndpoint, taskId, timePrecisionSeconds);
            client.sendMeasurement(<your measurement here>);
        } catch (Exception e) {
            Log.e("MyRunnable", "upload failed", e);
        }
    }
}
```
