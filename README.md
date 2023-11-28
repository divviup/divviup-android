# divviup-android
An Android client library for the [Distributed Aggregation Protocol][DAP].

[DAP]: https://datatracker.ietf.org/doc/draft-ietf-ppm-dap/

## Protocol Versions and Release Branches

The following versions of the DAP protocol are supported by different branches
and releases.

| Package version     | Git branch | Protocol version                    | Conformant? | Status    |
|---------------------|------------|-------------------------------------|-------------|-----------|
| 0.1.0 (forthcoming) | `main`     | [`draft-ietf-ppm-dap-07`][draft-07] | Yes         | Supported |

[draft-07]: https://datatracker.ietf.org/doc/draft-ietf-ppm-dap/07/

## Usage

Note that no published releases are available yet, so you must build this
library from source for now. Add the AAR file to your project. Construct a
`Client` from your DAP task's parameters, and use it to send report as follows.
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
            Client<Boolean> client = Client.createPrio3Count(leaderEndpoint, helperEndpoint, taskId, timePrecisionSeconds);
            client.sendMeasurement(<your measurement here>);
        } catch (Exception e) {
            Log.e("MyRunnable", "upload failed", e);
        }
    }
}
```
