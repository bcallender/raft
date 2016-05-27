/**
 * Created by brandon on 5/25/16.
 */
public class Logger {

    public static LogLevel masterLogLevel = LogLevel.INFO; //default log level, don't print debug

    private static void log(LogLevel ll, String message) {
        if (ll.level >= masterLogLevel.level) { //if the loglevel is higher, dont log it out to stdout
            System.out.println(String.format("[%s]: %s", ll, message));
        }
    }

    public static void error(String message) {
        log(LogLevel.ERROR, message);
    }

    public static void warning(String message) {
        log(LogLevel.WARNING, message);
    }

    public static void info(String message) {
        log(LogLevel.INFO, message);
    }

    public static void debug(String message) {
        log(LogLevel.DEBUG, message);
    }

    public static void trace(String message) {
        log(LogLevel.TRACE, message);
    }

    public static void setMasterLogLevel(LogLevel ll) {
        masterLogLevel = ll;
    }

    public enum LogLevel {
        TRACE(-1), DEBUG(0), INFO(1), WARNING(2), ERROR(3);

        int level;

        LogLevel(int _level) {
            this.level = _level;
        }
    }
}
