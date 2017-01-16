package net.igenius.mqttdemo;

import android.content.Context;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import com.google.gson.JsonObject;

import net.igenius.mqttservice.MQTTService;
import net.igenius.mqttservice.MQTTServiceCommand;
import net.igenius.mqttservice.MQTTServiceLogger;
import net.igenius.mqttservice.MQTTServiceReceiver;

import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    private MQTTServiceReceiver receiver = new MQTTServiceReceiver() {
        @Override
        public void onSubscriptionSuccessful(Context context, String requestId, String topic) {
            Log.e("Receiver", "Subscribed to " + topic);

            JsonObject request = new JsonObject();
            request.addProperty("question", "best time to post");
            request.addProperty("lang", "en");

            MQTTServiceCommand.publish(context, publishTopic, request);
        }

        @Override
        public void onPublishSuccessful(Context context, String requestId, String topic) {
            Log.e("Receiver", "Successfully published on topic: " + topic);
        }

        @Override
        public void onMessageArrived(Context context, String topic, String payload) {
            Log.e("Receiver", "New message on " + topic + ":  " + payload);
        }

        @Override
        public void onConnectionSuccessful(Context context, String requestId) {
            Log.e("Receiver", "Connected!");
            MQTTServiceCommand.subscribe(context, subscribeTopic);
        }

        @Override
        public void onException(Context context, String requestId, Exception exception) {
            Log.e("Receiver", requestId + " exception", exception);
        }
    };

    String server = "ssl://yourserver.com:port";
    String username = "username";
    String password = "password";
    String clientId = UUID.randomUUID().toString();
    String publishTopic = "/some/publish/topic";
    String subscribeTopic = "/some/subscribe/topic";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        MQTTService.NAMESPACE = "net.igenius.mqttdemo";
        MQTTServiceLogger.setLogLevel(MQTTServiceLogger.LogLevel.DEBUG);
        MQTTServiceCommand.connect(this, server, clientId, username, password);
    }

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

