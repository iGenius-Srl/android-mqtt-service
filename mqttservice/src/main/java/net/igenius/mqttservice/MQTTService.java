package net.igenius.mqttservice;

import android.content.Intent;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.util.UUID;

import static net.igenius.mqttservice.MQTTServiceCommand.ACTION_CONNECT;
import static net.igenius.mqttservice.MQTTServiceCommand.ACTION_DISCONNECT;
import static net.igenius.mqttservice.MQTTServiceCommand.ACTION_PUBLISH;
import static net.igenius.mqttservice.MQTTServiceCommand.ACTION_SUBSCRIBE;
import static net.igenius.mqttservice.MQTTServiceCommand.BROADCAST_CONNECTION_SUCCESS;
import static net.igenius.mqttservice.MQTTServiceCommand.BROADCAST_EXCEPTION;
import static net.igenius.mqttservice.MQTTServiceCommand.BROADCAST_MESSAGE_ARRIVED;
import static net.igenius.mqttservice.MQTTServiceCommand.BROADCAST_PUBLISH_SUCCESS;
import static net.igenius.mqttservice.MQTTServiceCommand.BROADCAST_SUBSCRIPTION_SUCCESS;
import static net.igenius.mqttservice.MQTTServiceCommand.PARAM_BROADCAST_TYPE;
import static net.igenius.mqttservice.MQTTServiceCommand.PARAM_BROKER_URL;
import static net.igenius.mqttservice.MQTTServiceCommand.PARAM_CLIENT_ID;
import static net.igenius.mqttservice.MQTTServiceCommand.PARAM_EXCEPTION;
import static net.igenius.mqttservice.MQTTServiceCommand.PARAM_PASSWORD;
import static net.igenius.mqttservice.MQTTServiceCommand.PARAM_PAYLOAD;
import static net.igenius.mqttservice.MQTTServiceCommand.PARAM_QOS;
import static net.igenius.mqttservice.MQTTServiceCommand.PARAM_REQUEST_ID;
import static net.igenius.mqttservice.MQTTServiceCommand.PARAM_TOPIC;
import static net.igenius.mqttservice.MQTTServiceCommand.PARAM_TOPICS;
import static net.igenius.mqttservice.MQTTServiceCommand.PARAM_USERNAME;
import static net.igenius.mqttservice.MQTTServiceCommand.getBroadcastAction;

public class MQTTService extends BackgroundService implements Runnable, MqttCallback {

    public static String NAMESPACE = "net.igenius.mqtt";

    private Intent mIntent;
    private MqttClient mClient;

    private String getParameter(String key) {
        return mIntent.getStringExtra(key);
    }

    private void broadcast(String type, String requestId, String... params) {
        if (params != null && params.length > 0 && params.length % 2 != 0)
            throw new IllegalArgumentException("Parameters must be passed in the form: PARAM_NAME, paramValue");

        Intent intent = new Intent();

        intent.setAction(getBroadcastAction());
        intent.putExtra(PARAM_BROADCAST_TYPE, type);
        intent.putExtra(PARAM_REQUEST_ID, requestId);

        if (params != null && params.length > 0) {
            for (int i = 0; i <= params.length - 2; i += 2) {
                intent.putExtra(params[i], params[i + 1]);
            }
        }

        sendBroadcast(intent);
    }

    private void broadcastException(String requestId, Exception exception) {
        Intent intent = new Intent();

        intent.setAction(getBroadcastAction());
        intent.putExtra(PARAM_BROADCAST_TYPE, BROADCAST_EXCEPTION);
        intent.putExtra(PARAM_REQUEST_ID, requestId);
        intent.putExtra(PARAM_EXCEPTION, exception);

        sendBroadcast(intent);
    }

    @Override
    public int onStartCommand(final Intent intent, int flags, int startId) {

        if (intent != null) {
            if (intent.getAction() == null || intent.getAction().isEmpty()) {
                MQTTServiceLogger.error("MQTTService onStartCommand",
                                        "null or empty Intent passed, ignoring it!");
            } else {
                mIntent = intent;
                post(this);
            }
        }

        return START_STICKY;
    }

    @Override
    public void run() {
        String action = mIntent.getAction();
        String requestId = getParameter(PARAM_REQUEST_ID);

        if (ACTION_CONNECT.equals(action)) {
            onConnect(requestId, getParameter(PARAM_BROKER_URL), getParameter(PARAM_CLIENT_ID),
                      getParameter(PARAM_USERNAME), getParameter(PARAM_PASSWORD));

        } else if (ACTION_DISCONNECT.equals(action)) {
            onDisconnect(requestId);

        } else if (ACTION_SUBSCRIBE.equals(action)) {
            onSubscribe(requestId, Integer.parseInt(getParameter(PARAM_QOS)),
                        mIntent.getStringArrayExtra(PARAM_TOPICS));

        } else if (ACTION_PUBLISH.equals(action)) {
            onPublish(requestId, getParameter(PARAM_TOPIC), getParameter(PARAM_PAYLOAD));
        }
    }

    private void onConnect(final String requestId,
                           final String brokerUrl,
                           final String clientId,
                           final String username,
                           final String password) {

        MQTTServiceLogger.debug(getClass().getSimpleName(), requestId + " Connect to "
                + brokerUrl + " with user: " + username + " and password: " + password);

        try {
            if (mClient == null) {
                MQTTServiceLogger.debug("onConnect", "Creating new MQTT connection");

                mClient = new MqttClient(brokerUrl, clientId, new MemoryPersistence());
                mClient.setCallback(this);

                MqttConnectOptions connectOptions = new MqttConnectOptions();
                if (username != null && password != null) {
                    connectOptions.setUserName(username);
                    connectOptions.setPassword(password.toCharArray());
                }
                connectOptions.setCleanSession(true);
                connectOptions.setAutomaticReconnect(true);

                mClient.connect(connectOptions);
                MQTTServiceLogger.debug("onConnect", "Connected");
                broadcast(BROADCAST_CONNECTION_SUCCESS, requestId);

            } else {
                if (mClient.isConnected()) {
                    MQTTServiceLogger.debug("onConnect", "Client already connected, nothing to do");
                } else {
                    MQTTServiceLogger.debug("onConnect", "Reconnecting MQTT");

                    mClient.reconnect();
                    broadcast(BROADCAST_CONNECTION_SUCCESS, requestId);
                }
            }

        } catch (MqttException exc) {
            broadcastException(requestId, new MqttException(exc));
        }
    }

    private boolean clientIsConnected() {
        return (mClient != null && mClient.isConnected());
    }

    private void onDisconnect(final String requestId) {
        if (!clientIsConnected()) {
            MQTTServiceLogger.info("onDisconnect", "No client connected, nothing to disconnect!");
            return;
        }

        try {
            MQTTServiceLogger.debug("onDisconnect", "Disconnecting MQTT");
            mClient.disconnect();

        } catch (Exception e) {
            broadcastException(requestId, new MqttException(e));

            try {
                mClient.disconnectForcibly();
            } catch (Exception ignored) { }

        } finally {
            mClient = null;
        }
    }

    private void onSubscribe(final String requestId, final int qos, final String[] topics) {
        if (topics == null || topics.length == 0) {
            broadcastException(requestId, new Exception("No topics passed to subscribe!"));
            return;
        }

        if (!clientIsConnected()) {
            broadcastException(requestId, new Exception("Can't subscribe to topics, client not connected!"));
            return;
        }

        try {
            for (String topic : topics) {
                MQTTServiceLogger.debug("onSubscribe", "Subscribing to topic: " + topic + " with QoS " + qos);
                mClient.subscribe(topic, qos);
                MQTTServiceLogger.debug("onSubscribe", "Successfully subscribed to topic: " + topic);

                broadcast(BROADCAST_SUBSCRIPTION_SUCCESS, requestId,
                        PARAM_TOPICS, topic
                );
            }

        } catch (Exception e) {
            broadcastException(requestId, new MqttException(e));
        }
    }

    private void onPublish(final String requestId, final String topic, final String payload) {
        if (!clientIsConnected()) {
            broadcastException(requestId, new Exception("Can't publish to topic: " + topic + ", client not connected!"));
            return;
        }

        try {
            MQTTServiceLogger.debug("onPublish", "Publishing to topic: " + topic + ", payload: " + payload);
            MqttMessage message = new MqttMessage(payload.getBytes("UTF-8"));
            message.setQos(0);
            mClient.publish(topic, message);
            MQTTServiceLogger.debug("onPublish", "Successfully published to topic: " + topic + ", payload: " + payload);

            broadcast(BROADCAST_PUBLISH_SUCCESS, requestId,
                    PARAM_TOPIC, topic
            );

        } catch (Exception exc) {
            broadcastException(requestId, new MqttException(exc));
        }
    }

    @Override
    public void connectionLost(Throwable cause) {
        broadcastException(UUID.randomUUID().toString(), new Exception(cause));
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) throws Exception {
        broadcast(BROADCAST_MESSAGE_ARRIVED, UUID.randomUUID().toString(),
                PARAM_TOPIC, topic,
                PARAM_PAYLOAD, new String(message.getPayload())
        );
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        //TODO: check what this does
    }
}
