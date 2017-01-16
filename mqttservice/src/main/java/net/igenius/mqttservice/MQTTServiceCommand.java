package net.igenius.mqttservice;

import android.content.Context;
import android.content.Intent;

import com.google.gson.Gson;

import java.util.UUID;

import static net.igenius.mqttservice.MQTTService.NAMESPACE;

/**
 * Created by Aleksandar Gotev (aleksandar@igenius.net) on 16/01/17.
 */

public class MQTTServiceCommand {

    static final String BROADCAST_ACTION = ".mqtt.broadcast";

    static final String ACTION_CONNECT = ".mqtt.connect";
    static final String ACTION_DISCONNECT = ".mqtt.disconnect";
    static final String ACTION_PUBLISH = ".mqtt.publish";
    static final String ACTION_SUBSCRIBE = ".mqtt.subscribe";

    static final String PARAM_BROKER_URL = "brokerUrl";
    static final String PARAM_CLIENT_ID = "clientId";
    static final String PARAM_USERNAME = "username";
    static final String PARAM_PASSWORD = "password";
    static final String PARAM_TOPIC = "topic";
    static final String PARAM_PAYLOAD = "payload";
    static final String PARAM_QOS = "qos";
    static final String PARAM_REQUEST_ID = "reqId";
    static final String PARAM_BROADCAST_TYPE = "broadcastType";
    static final String PARAM_EXCEPTION = "exception";

    static final String BROADCAST_EXCEPTION = "exception";
    static final String BROADCAST_CONNECTION_SUCCESS = "connectionSuccess";
    static final String BROADCAST_MESSAGE_ARRIVED = "messageArrived";
    static final String BROADCAST_SUBSCRIPTION_SUCCESS = "subscriptionSuccess";

    public static String connect(final Context context, final String brokerUrl,
                                 final String clientId, final String username,
                                 final String password) {
        return startService(context, ACTION_CONNECT,
                PARAM_BROKER_URL, brokerUrl,
                PARAM_CLIENT_ID, clientId,
                PARAM_USERNAME, username,
                PARAM_PASSWORD, password
        );
    }

    public static String disconnect(final Context context) {
        return startService(context, ACTION_DISCONNECT);
    }

    public static String subscribe(final Context context, final String topic, final int qos) {
        return startService(context, ACTION_SUBSCRIBE,
                PARAM_TOPIC, topic,
                PARAM_QOS, Integer.toString(qos)
        );
    }

    public static String subscribe(final Context context, final String topic) {
        return subscribe(context, topic, 0);
    }

    public static String publish(final Context context, final String topic, final String payload,
                                 final int qos) {
        return startService(context, ACTION_PUBLISH,
                PARAM_TOPIC, topic,
                PARAM_PAYLOAD, payload,
                PARAM_QOS, Integer.toString(qos)
        );
    }

    public static String publish(final Context context, final String topic, final String payload) {
        return publish(context, topic, payload, 0);
    }

    public static String publish(final Context context, final String topic, final Object payload,
                                 final int qos) {
        return publish(context, topic, new Gson().toJson(payload), qos);
    }

    public static String publish(final Context context, final String topic, final Object payload) {
        return publish(context, topic, payload, 0);
    }

    protected static String getBroadcastAction() {
        return NAMESPACE + BROADCAST_ACTION;
    }

    private static String startService(final Context context, final String action, String... params) {
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

        context.startService(intent);

        return uuid;
    }
}
