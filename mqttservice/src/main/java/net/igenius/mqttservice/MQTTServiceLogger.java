package net.igenius.mqttservice;

/**
 * MQTT Service library logger.
 * You can provide your own logger delegate implementation, to be able to log in a different way.
 * By default the log level is set to DEBUG when the build type is debug, and OFF in release.
 * The default logger implementation logs in Android's LogCat.
 * @author gotev (Aleksandar Gotev)
 */
public class MQTTServiceLogger {

    public enum LogLevel {
        DEBUG,
        INFO,
        ERROR,
        OFF
    }

    public interface LoggerDelegate {
        void error(String tag, String message);
        void error(String tag, String message, Throwable exception);
        void debug(String tag, String message);
        void info(String tag, String message);
    }

    private LogLevel mLogLevel = BuildConfig.DEBUG ? LogLevel.DEBUG : LogLevel.OFF;

    private LoggerDelegate mDelegate = new MQTTServiceDefaultLoggerDelegate();

    private MQTTServiceLogger() { }

    private static class SingletonHolder {
        private static final MQTTServiceLogger instance = new MQTTServiceLogger();
    }

    public static void resetLoggerDelegate() {
        synchronized (MQTTServiceLogger.class) {
            SingletonHolder.instance.mDelegate = new MQTTServiceDefaultLoggerDelegate();
        }
    }

    public static void setLoggerDelegate(LoggerDelegate delegate) {
        if (delegate == null)
            throw new IllegalArgumentException("delegate MUST not be null!");

        synchronized (MQTTServiceLogger.class) {
            SingletonHolder.instance.mDelegate = delegate;
        }
    }

    public static void setLogLevel(LogLevel level) {
        synchronized (MQTTServiceLogger.class) {
            SingletonHolder.instance.mLogLevel = level;
        }
    }

    public static void error(String tag, String message) {
        if (SingletonHolder.instance.mLogLevel.compareTo(LogLevel.ERROR) <= 0) {
            SingletonHolder.instance.mDelegate.error(tag, message);
        }
    }

    public static void error(String tag, String message, Throwable exception) {
        if (SingletonHolder.instance.mLogLevel.compareTo(LogLevel.ERROR) <= 0) {
            SingletonHolder.instance.mDelegate.error(tag, message, exception);
        }
    }

    public static void info(String tag, String message) {
        if (SingletonHolder.instance.mLogLevel.compareTo(LogLevel.INFO) <= 0) {
            SingletonHolder.instance.mDelegate.info(tag, message);
        }
    }

    public static void debug(String tag, String message) {
        if (SingletonHolder.instance.mLogLevel.compareTo(LogLevel.DEBUG) <= 0) {
            SingletonHolder.instance.mDelegate.debug(tag, message);
        }
    }
}
