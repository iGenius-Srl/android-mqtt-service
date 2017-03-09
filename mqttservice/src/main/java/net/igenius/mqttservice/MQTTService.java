package net.igenius.mqttservice;

import android.content.Intent;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static net.igenius.mqttservice.MQTTServiceCommand.ACTION_CONNECT;
import static net.igenius.mqttservice.MQTTServiceCommand.ACTION_CONNECT_AND_SUBSCRIBE;
import static net.igenius.mqttservice.MQTTServiceCommand.ACTION_DISCONNECT;
import static net.igenius.mqttservice.MQTTServiceCommand.ACTION_PUBLISH;
import static net.igenius.mqttservice.MQTTServiceCommand.ACTION_SUBSCRIBE;
import static net.igenius.mqttservice.MQTTServiceCommand.BROADCAST_CONNECTION_SUCCESS;
import static net.igenius.mqttservice.MQTTServiceCommand.BROADCAST_EXCEPTION;
import static net.igenius.mqttservice.MQTTServiceCommand.BROADCAST_MESSAGE_ARRIVED;
import static net.igenius.mqttservice.MQTTServiceCommand.BROADCAST_PUBLISH_SUCCESS;
import static net.igenius.mqttservice.MQTTServiceCommand.BROADCAST_SUBSCRIPTION_ERROR;
import static net.igenius.mqttservice.MQTTServiceCommand.BROADCAST_SUBSCRIPTION_SUCCESS;
import static net.igenius.mqttservice.MQTTServiceCommand.PARAM_AUTO_RESUBSCRIBE_ON_RECONNECT;
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

public class MQTTService extends BackgroundService implements Runnable, MqttCallbackExtended {

    public static String NAMESPACE = "net.igenius.mqtt";
    public static int KEEP_ALIVE_INTERVAL = 60; //measured in seconds
    public static int CONNECT_TIMEOUT = 30; //measured in seconds

    private Intent mIntent;
    private MqttClient mClient;
    private boolean mShutdown = false;
    private String mConnectionRequestId = null;
    private HashMap<String, Integer> mTopicsToAutoResubscribe = new LinkedHashMap<>();

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

    private void broadcastException(String type, String requestId, Exception exception, String... params) {
        Intent intent = new Intent();

        intent.setAction(getBroadcastAction());
        intent.putExtra(PARAM_BROADCAST_TYPE, type);
        intent.putExtra(PARAM_REQUEST_ID, requestId);
        intent.putExtra(PARAM_EXCEPTION, exception);

        if (params != null && params.length > 0) {
            for (int i = 0; i <= params.length - 2; i += 2) {
                intent.putExtra(params[i], params[i + 1]);
            }
        }

        sendBroadcast(intent);
    }

    @Override
    public int onStartCommand(final Intent intent, int flags, int startId) {

        if (intent != null) {
            if (intent.getAction() == null || intent.getAction().isEmpty()) {
                MQTTServiceLogger.error("MQTTService onStartCommand",
                                        "null or empty Intent passed, ignoring it!");
            } else {
                mShutdown = false;
                mIntent = intent;
                post(this);
            }
        }

        if (mShutdown) {
            MQTTServiceLogger.debug(getClass().getSimpleName(), "Shutting down service");
            stopSelf();
            return START_NOT_STICKY;
        }

        return START_STICKY;
    }

    @Override
    public void run() {
        String action = mIntent.getAction();
        String requestId = getParameter(PARAM_REQUEST_ID);

        if (ACTION_CONNECT.equals(action) || ACTION_CONNECT_AND_SUBSCRIBE.equals(action)) {
            boolean connected = onConnect(requestId, getParameter(PARAM_BROKER_URL),
                    getParameter(PARAM_CLIENT_ID), getParameter(PARAM_USERNAME),
                    getParameter(PARAM_PASSWORD));

            if (ACTION_CONNECT_AND_SUBSCRIBE.equals(action) && connected) {
                int qos = getInt(getParameter(PARAM_QOS));
                String[] topics = mIntent.getStringArrayExtra(PARAM_TOPICS);
                boolean autoResubscribe = mIntent.getBooleanExtra(PARAM_AUTO_RESUBSCRIBE_ON_RECONNECT, false);
                onSubscribe(requestId, qos, autoResubscribe, topics);
            }

        } else if (ACTION_DISCONNECT.equals(action)) {
            onDisconnect(requestId);

        } else if (ACTION_SUBSCRIBE.equals(action)) {
            onSubscribe(requestId, getInt(getParameter(PARAM_QOS)),
                        mIntent.getBooleanExtra(PARAM_AUTO_RESUBSCRIBE_ON_RECONNECT, false),
                        mIntent.getStringArrayExtra(PARAM_TOPICS));

        } else if (ACTION_PUBLISH.equals(action)) {
            onPublish(requestId, getParameter(PARAM_TOPIC), getParameter(PARAM_PAYLOAD));
        }
    }

    private int getInt(String string) {
        try {
            return Integer.parseInt(string, 10);
        } catch (Throwable exc) {
            MQTTServiceLogger.error(getClass().getSimpleName(), "Unparsable string: " + string + ", returning 0");
            return 0;
        }
    }

    private boolean onConnect(final String requestId,
                              final String brokerUrl,
                              final String clientId,
                              final String username,
                              final String password) {

        MQTTServiceLogger.debug(getClass().getSimpleName(), requestId + " Connect to "
                + brokerUrl + " with user: " + username + " and password: " + password);

        mConnectionRequestId = requestId;

        try {
            if (mClient == null) {
                MQTTServiceLogger.debug("onConnect", "Creating new MQTT connection");

                mTopicsToAutoResubscribe.clear();
                mClient = new MqttClient(brokerUrl, clientId, new MemoryPersistence());
                mClient.setCallback(this);

                MqttConnectOptions connectOptions = new MqttConnectOptions();
                if (username != null && password != null) {
                    connectOptions.setUserName(username);
                    connectOptions.setPassword(password.toCharArray());
                }
                connectOptions.setCleanSession(true);
                connectOptions.setAutomaticReconnect(true);
                connectOptions.setKeepAliveInterval(KEEP_ALIVE_INTERVAL);
                connectOptions.setConnectionTimeout(CONNECT_TIMEOUT);

                mClient.connect(connectOptions);
                MQTTServiceLogger.debug("onConnect", "Connected");

            } else {
                reconnect(requestId);
            }

            return true;

        } catch (Exception exc) {
            broadcastException(BROADCAST_EXCEPTION, requestId, new MqttException(exc));
            return false;
        }
    }

    private void reconnect(String requestId) throws MqttException {
        if (mClient == null)
            return;

        if (mClient.isConnected()) {
            MQTTServiceLogger.debug("reconnect", "Client already connected, nothing to do");
        } else {
            MQTTServiceLogger.debug("reconnect", "Reconnecting MQTT");

            mClient.reconnect();
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
            MQTTServiceLogger.error("onDisconnect",
                    "Error while disconnecting from MQTT. Request Id: " + requestId, e);

            try {
                mClient.disconnectForcibly();
            } catch (Exception exc) {
                MQTTServiceLogger.error("onDisconnect", "Error while disconnect forcibly", exc);
            }

        } finally {
            mClient = null;
            mTopicsToAutoResubscribe.clear();
            mShutdown = true;
        }
    }

    private void onSubscribe(final String requestId, final int qos,
                             final boolean autoResubscribeOnConnect,
                             final String... topics) {
        if (topics == null || topics.length == 0) {
            broadcastException(BROADCAST_SUBSCRIPTION_ERROR, requestId,
                    new Exception("No topics passed to subscribe!"),
                    PARAM_TOPIC, ""
            );
            return;
        }

        if (!clientIsConnected()) {
            for (String topic : topics) {
                broadcastException(BROADCAST_SUBSCRIPTION_ERROR, requestId,
                        new Exception("Can't subscribe to topics, client not connected!"),
                        PARAM_TOPIC, topic
                );
            }
            return;
        }

        for (String topic : topics) {
            try {
                MQTTServiceLogger.debug("onSubscribe", "Subscribing to topic: " + topic + " with QoS " + qos);
                mClient.subscribe(topic, qos);

                if (autoResubscribeOnConnect) {
                    mTopicsToAutoResubscribe.put(topic, qos);
                }

                MQTTServiceLogger.debug("onSubscribe", "Successfully subscribed to topic: " + topic);

                broadcast(BROADCAST_SUBSCRIPTION_SUCCESS, requestId,
                        PARAM_TOPIC, topic
                );
            } catch (Exception e) {
                broadcastException(BROADCAST_SUBSCRIPTION_ERROR, requestId, new MqttException(e),
                        PARAM_TOPIC, topic
                );
            }
        }
    }

    private void onPublish(final String requestId, final String topic, final String payload) {
        if (!clientIsConnected()) {
            broadcastException(BROADCAST_EXCEPTION, requestId,
                               new Exception("Can't publish to topic: " + topic + ", client not connected!"));
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
            broadcastException(BROADCAST_EXCEPTION, requestId, new MqttException(exc));
        }
    }

    @Override
    public void connectionLost(Throwable cause) {
        broadcastException(BROADCAST_EXCEPTION, UUID.randomUUID().toString(), new Exception(cause));
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

    @Override
    public void connectComplete(boolean reconnect, String serverURI) {
        String requestId = reconnect ? UUID.randomUUID().toString() : mConnectionRequestId;

        if (reconnect) {
            MQTTServiceLogger.debug("reconnect", "Reconnected to " + serverURI);

            if (!mTopicsToAutoResubscribe.isEmpty()) {
                MQTTServiceLogger.debug("reconnect", "auto resubscribing to topics");
                for (Map.Entry<String, Integer> entry : mTopicsToAutoResubscribe.entrySet()) {
                    onSubscribe(requestId, entry.getValue(), true, entry.getKey());
                }
            }
        }

        broadcast(BROADCAST_CONNECTION_SUCCESS, requestId);
    }
}
