package net.igenius.mqttservice;

import android.content.Context;
import android.content.Intent;

import java.util.UUID;

import static net.igenius.mqttservice.MQTTService.NAMESPACE;

/**
 * Created by Aleksandar Gotev (aleksandar@igenius.net) on 16/01/17.
 */

public class MQTTServiceCommand {

    private static final String BROADCAST_ACTION_SUFFIX = ".mqtt.broadcast";

    static final String ACTION_CONNECT = ".mqtt.connect";
    static final String ACTION_DISCONNECT = ".mqtt.disconnect";
    static final String ACTION_PUBLISH = ".mqtt.publish";
    static final String ACTION_SUBSCRIBE = ".mqtt.subscribe";
    static final String ACTION_CONNECT_AND_SUBSCRIBE = ".mqtt.connect-and-subscribe";
    static final String ACTION_CHECK_CONNECTION = ".mqtt.check-connection";

    public static final String PARAM_BROKER_URL = "brokerUrl";
    public static final String PARAM_CLIENT_ID = "clientId";
    public static final String PARAM_USERNAME = "username";
    public static final String PARAM_PASSWORD = "password";
    public static final String PARAM_TOPIC = "topic";
    public static final String PARAM_TOPICS = "topics";
    public static final String PARAM_PAYLOAD = "payload";
    public static final String PARAM_CONNECTED = "connected";
    public static final String PARAM_QOS = "qos";
    public static final String PARAM_REQUEST_ID = "reqId";
    public static final String PARAM_BROADCAST_TYPE = "broadcastType";
    public static final String PARAM_EXCEPTION = "exception";
    public static final String PARAM_AUTO_RESUBSCRIBE_ON_RECONNECT = "autoResubscribeOnReconnect";

    public static final String BROADCAST_EXCEPTION = "exception";
    public static final String BROADCAST_CONNECTION_SUCCESS = "connectionSuccess";
    public static final String BROADCAST_MESSAGE_ARRIVED = "messageArrived";
    public static final String BROADCAST_SUBSCRIPTION_SUCCESS = "subscriptionSuccess";
    public static final String BROADCAST_SUBSCRIPTION_ERROR = "subscriptionError";
    public static final String BROADCAST_PUBLISH_SUCCESS = "publishSuccess";
    public static final String BROADCAST_CONNECTION_STATUS = "connectionStatus";

    /**
     * Connects to an MQTT broker.
     * @param context application context
     * @param brokerUrl Url to which to connect. Example: ssl://mqtt.server.com:1234 or tcp://mqtt.server.com:1234
     * @param clientId client ID to give to this client
     * @param username username
     * @param password password
     * @return request Id, to be used in receiver to track events associated to this request
     */
    public static String connect(final Context context, final String brokerUrl,
                                 final String clientId, final String username,
                                 final String password) {
        return startService(context, ACTION_CONNECT, null,
                PARAM_BROKER_URL, brokerUrl,
                PARAM_CLIENT_ID, clientId,
                PARAM_USERNAME, username,
                PARAM_PASSWORD, password
        );
    }

    /**
     * Disconnects from the MQTT broker and shuts down the service
     * @param context application context
     * @return request Id, to be used in receiver to track events associated to this request
     */
    public static String disconnect(final Context context) {
        return startService(context, ACTION_DISCONNECT, null);
    }

    public static String checkConnectionStatus(final Context context) {
        return startService(context, ACTION_CHECK_CONNECTION, null);
    }

    /**
     * Subscribes to one or many topics at once.
     * @param context application context
     * @param qos QoS to use (0, 1 or 2)
     * @param autoResubscribeOnReconnect if you want the topics passed as parameters to be
     *                                   automatically resubscribed after each one automatic
     *                                   reconnection
     * @param topics topics on which to subscribe
     * @return request Id, to be used in receiver to track events associated to this request
     */
    public static String subscribe(final Context context, final int qos,
                                   final boolean autoResubscribeOnReconnect,
                                   final String... topics) {
        Intent intent = new Intent(context, MQTTService.class);
        intent.setAction(ACTION_SUBSCRIBE);

        intent.putExtra(PARAM_QOS, Integer.toString(qos));
        intent.putExtra(PARAM_AUTO_RESUBSCRIBE_ON_RECONNECT, autoResubscribeOnReconnect);
        intent.putExtra(PARAM_TOPICS, topics);

        String uuid = UUID.randomUUID().toString();
        intent.putExtra(PARAM_REQUEST_ID, ACTION_SUBSCRIBE + "/" + uuid);

        context.startService(intent);

        return uuid;
    }

    /**
     * Subscribe to one or many topics at once with QoS 0.
     * @param context application context
     * @param autoResubscribeOnReconnect if you want the topics passed as parameters to be
     *                                   automatically resubscribed after each one automatic
     *                                   reconnection
     * @param topics topics on which to subscribe
     * @return request Id, to be used in receiver to track events associated to this request
     */
    public static String subscribe(final Context context, final boolean autoResubscribeOnReconnect,
                                   final String... topics) {
        return subscribe(context, 0, autoResubscribeOnReconnect, topics);
    }

    /**
     * Connects to an MQTT broker and subscribes to one or more topics if the connection is
     * successful.
     * @param context application context
     * @param brokerUrl Url to which to connect. Example: ssl://mqtt.server.com:1234 or tcp://mqtt.server.com:1234
     * @param clientId client ID to give to this client
     * @param username username
     * @param password password
     * @param qos QoS to use (0, 1 or 2)
     * @param autoResubscribeOnReconnect if you want the topics passed as parameters to be
     *                                   automatically resubscribed after each one automatic
     *                                   reconnection
     * @param topics topics on which to subscribe
     * @return request Id, to be used in receiver to track events associated to this request
     */
    public static String connectAndSubscribe(final Context context, final String brokerUrl,
                                             final String clientId, final String username,
                                             final String password, final int qos,
                                             final boolean autoResubscribeOnReconnect,
                                             final String... topics) {
        Intent intent = new Intent(context, MQTTService.class);
        intent.setAction(ACTION_CONNECT_AND_SUBSCRIBE);

        intent.putExtra(PARAM_BROKER_URL, brokerUrl);
        intent.putExtra(PARAM_CLIENT_ID, clientId);
        intent.putExtra(PARAM_USERNAME, username);
        intent.putExtra(PARAM_PASSWORD, password);

        intent.putExtra(PARAM_QOS, Integer.toString(qos));
        intent.putExtra(PARAM_AUTO_RESUBSCRIBE_ON_RECONNECT, autoResubscribeOnReconnect);
        intent.putExtra(PARAM_TOPICS, topics);

        String uuid = UUID.randomUUID().toString();
        intent.putExtra(PARAM_REQUEST_ID, ACTION_CONNECT_AND_SUBSCRIBE + "/" + uuid);

        context.startService(intent);

        return uuid;
    }

    /**
     * Publish some content on a topic.
     * @param context application context
     * @param topic topic on which to publish
     * @param payload payload to publish
     * @param qos QoS to use (0, 1 or 2)
     * @return request Id, to be used in receiver to track events associated to this request
     */
    public static String publish(final Context context, final String topic, final byte[] payload,
                                 final int qos) {
        return startService(context, ACTION_PUBLISH, payload,
                PARAM_TOPIC, topic,
                PARAM_QOS, Integer.toString(qos)
        );
    }

    /**
     * Publish some content on a topic with QoS 0.
     * @param context application context
     * @param topic topic on which to publish
     * @param payload payload to publish
     * @return request Id, to be used in receiver to track events associated to this request
     */
    public static String publish(final Context context, final String topic, final byte[] payload) {
        return publish(context, topic, payload, 0);
    }

    public static String getBroadcastAction() {
        return NAMESPACE + BROADCAST_ACTION_SUFFIX;
    }

    private static String startService(final Context context,
                                       final String action,
                                       final byte[] payload,
                                       String... params) {
        if (params != null && params.length > 0 && params.length % 2 != 0)
            throw new IllegalArgumentException("Parameters must be passed in the form: PARAM_NAME, paramValue");

        Intent intent = new Intent(context, MQTTService.class);
        intent.setAction(action);

        if (params != null && params.length > 0) {
            for (int i = 0; i <= params.length - 2; i += 2) {
                intent.putExtra(params[i], params[i + 1]);
            }
        }

        String uuid = UUID.randomUUID().toString();
        intent.putExtra(PARAM_REQUEST_ID, action + "/" + uuid);

        if (payload != null) {
            intent.putExtra(PARAM_PAYLOAD, payload);
        }

        context.startService(intent);

        return uuid;
    }
}
