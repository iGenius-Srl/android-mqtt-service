# Android MQTT Service [![Build Status](https://travis-ci.org/iGenius-Srl/android-mqtt-service.svg?branch=master)](https://travis-ci.org/iGenius-Srl/android-mqtt-service) [![Download](https://api.bintray.com/packages/igenius-code/maven/android-mqtt-service/images/download.svg) ](https://bintray.com/igenius-code/maven/android-mqtt-service/_latestVersion)

A tiny wrapper around [Eclipse Paho MQTT Java library](https://github.com/iGenius-Srl/paho.mqtt.java), to have a lightweight background Android service, which handles all the following operations in a background thread:

* connect to broker
* disconnect from broker
* publish on topics
* subscribe to topics
* receive messages

## Setup
```groovy
compile 'net.igenius:mqttservice:1.5'
```

Before using the library, you have to initialize it. The ideal place to do that is in your [Application subclass](http://developer.android.com/reference/android/app/Application.html):
```java
public class Initializer extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        MQTTService.NAMESPACE = "com.yourcompany.yourapp"; //or BuildConfig.APPLICATION_ID;
        MQTTService.KEEP_ALIVE_INTERVAL = 60; //in seconds
        MQTTService.CONNECT_TIMEOUT = 30; //in seconds
    }
}
```

## Send commands to the MQTT service
All the commands supported by the service are implemented in the `MQTTServiceCommand` class. Some of them are:
```java
// connect to the broker
MQTTServiceCommand.connect(final Context context, final String brokerUrl,
                           final String clientId, final String username,
                           final String password)

// disconnect from the broker
MQTTServiceCommand.disconnect(final Context context)

// subscribe to one or more topics
MQTTServiceCommand.subscribe(final Context context, final int qos,
                             final boolean autoResubscribeOnReconnect,
                             final String... topics)

// connect to a broker and subscribe to one or more topics with a QoS
MQTTServiceCommand.connectAndSubscribe(final Context context,
                                       final String brokerUrl,
                                       final String clientId,
                                       final String username,
                                       final String password,
                                       final int qos,
                                       final boolean autoResubscribeOnReconnect,
                                       final String... topics)

// publish a payload to a topic, with a specific QoS
MQTTServiceCommand.publish(final Context context, final String topic,
                           final byte[] payload, final int qos)

// publish a payload to a topic, with QoS 0
MQTTServiceCommand.publish(final Context context, final String topic,
                           final byte[] payload)
```
Explore the class for complete JavaDocs and all the available options.

## Receive MQTT events
### Globally in the app
To receive events globally in the app, even if it's in background, create a new class in your project:
```java
import android.content.Context;

import net.igenius.mqttservice.MQTTServiceReceiver;

public class MQTTReceiver extends MQTTServiceReceiver {

    @Override
    public void onPublishSuccessful(Context context, String requestId,
                                    String topic) {
        // called when a message has been successfully published
    }

    @Override
    public void onSubscriptionSuccessful(Context context, String requestId,
                                         String topic) {
        // called when a subscription is successful
    }

    @Override
    public void onSubscriptionError(Context context, String requestId,
                                    String topic, Exception exception) {
        // called when a subscription is not successful.
        // This usually happens when the broker does not give permissions
        // for the requested topic
    }

    @Override
    public void onMessageArrived(Context context, String topic,
                                 byte[] payload) {
        // called when a new message arrives on any topic
    }

    @Override
    public void onConnectionSuccessful(Context context, String requestId) {
        // called when the connection is successful
    }

    @Override
    public void onException(Context context, String requestId,
                            Exception exception) {
        // called when an error happens
    }

    @Override
    public void onConnectionStatus(Context context, boolean connected) {
        // called when connection status is requested or changes
    }
}
```
and then register it into the manifest (before `</application>` tag):
```xml
<receiver
    android:name=".MQTTReceiver"
    android:enabled="true"
    android:exported="false">
    <intent-filter>
        <action android:name="com.yourcompany.yourapp.mqtt.broadcast" />
    </intent-filter>
</receiver>
```
Bear in mind that `com.yourcompany.yourapp` MUST be the same you defined in `MQTTService.NAMESPACE` setting.

### Inside an Activity
```java
public class YourActivity extends AppCompatActivity {
    private MQTTServiceReceiver receiver = new MQTTServiceReceiver() {
        @Override
        public void onSubscriptionSuccessful(Context context,
                                             String requestId, String topic) {
            // called when a message has been successfully published
        }

        @Override
        public void onSubscriptionError(Context context, String requestId,
                                        String topic, Exception exception) {
            // called when a subscription is not successful.
            // This usually happens when the broker does not give permissions
            // for the requested topic
        }

        @Override
        public void onPublishSuccessful(Context context, String requestId, String topic) {
            // called when a subscription is successful
        }

        @Override
        public void onMessageArrived(Context context, String topic,
                                     byte[] payload) {
            // called when a new message arrives on any topic
        }

        @Override
        public void onConnectionSuccessful(Context context, String requestId) {
            // called when the connection is successful
        }

        @Override
        public void onException(Context context, String requestId,
                                Exception exception) {
            // called when an error happens
        }

        @Override
        public void onConnectionStatus(Context context, boolean connected) {
            // called when connection status is requested or changes
        }
    };

    @Override
    protected void onResume() {
        super.onResume();
        receiver.register(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        receiver.unregister(this);
    }
}
```

## Logging
By default the library logging is disabled. You can enable debug log by invoking:
```java
MQTTServiceLogger.setLogLevel(LogLevel.DEBUG);
```
wherever you want in your code. You can adjust the level of detail from DEBUG to OFF.

The library logger uses `android.util.Log` by default, so you will get the output in `LogCat`. If you want to redirect logs to different output or use a different logger, you can provide your own delegate implementation like this:
```java
MQTTServiceLogger.setLoggerDelegate(new MQTTServiceLogger.LoggerDelegate() {
    @Override
    public void error(String tag, String message) {
        //your own implementation here
    }

    @Override
    public void error(String tag, String message, Throwable exception) {
        //your own implementation here
    }

    @Override
    public void debug(String tag, String message) {
        //your own implementation here
    }

    @Override
    public void info(String tag, String message) {
        //your own implementation here
    }
});
```

## Example
You can find a fully working demo app which uses this library in the `example-app` directory. Just checkout the project and give it a try.
