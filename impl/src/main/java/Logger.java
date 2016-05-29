/**
 * Created by brandon on 5/25/16.
 */
public class Logger {

    public static final String ANSI_RESET = "\u001B[0m";
    public static final String ANSI_BLACK = "\u001B[30m";
    public static final String ANSI_RED = "\u001B[31m";
    public static final String ANSI_GREEN = "\u001B[32m";
    public static final String ANSI_YELLOW = "\u001B[33m";
    public static final String ANSI_BLUE = "\u001B[34m";
    public static final String ANSI_PURPLE = "\u001B[35m";
    public static final String ANSI_CYAN = "\u001B[36m";
    public static final String ANSI_WHITE = "\u001B[37m";
    public static LogLevel masterLogLevel = LogLevel.INFO; //default log level, don't print debug

    private static void log(LogLevel ll, String message) {
        if (ll.level >= masterLogLevel.level) { //if the loglevel is higher, dont log it out to stdout
            System.out.println(ll.color + String.format("[%s]: %s", ll, message) + ANSI_RESET);
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
        TRACE(-1, ANSI_BLUE), DEBUG(0, ANSI_GREEN), INFO(1, ANSI_PURPLE), WARNING(2, ANSI_YELLOW), ERROR(3, ANSI_RED);

        int level;
        String color;

        LogLevel(int _level, String _color) {
            this.level = _level;
            this.color = _color;
        }
    }
}
