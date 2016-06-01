/**
 * Static logger class with configurable log levels and colors
 */
public class Logger {

    //ANSI colors for supported terminals (most unix based terminals).
    private static final String ANSI_RESET = "\u001B[0m";
    private static final String ANSI_RED = "\u001B[31m";
    private static final String ANSI_GREEN = "\u001B[32m";
    private static final String ANSI_YELLOW = "\u001B[33m";
    private static final String ANSI_BLUE = "\u001B[34m";
    private static final String ANSI_PURPLE = "\u001B[35m";
    private static LogLevel masterLogLevel = LogLevel.INFO; //default log level, don't print debug

    private static void log(LogLevel ll, String message) { //static implmentation means we don't need instances of logger
        //it keeps no state, so this is safe.
        if (ll.level >= masterLogLevel.level) { //if the loglevel is higher, dont log it out to stdout
            System.out.println(ll.color + String.format("[%s]: %s", ll, message) + ANSI_RESET);
        }
    }

    static void error(String message) {
        log(LogLevel.ERROR, message);
    }

    static void warning(String message) {
        log(LogLevel.WARNING, message);
    }

    static void info(String message) {
        log(LogLevel.INFO, message);
    }

    static void debug(String message) {
        log(LogLevel.DEBUG, message);
    }

    static void trace(String message) {
        log(LogLevel.TRACE, message);
    }

    static void setMasterLogLevel(LogLevel ll) {
        masterLogLevel = ll;
    }

    enum LogLevel { //each level has an integer and a color for testing loglevel and pretty printing respectively.
        TRACE(-1, ANSI_BLUE), DEBUG(0, ANSI_GREEN), INFO(1, ANSI_PURPLE), WARNING(2, ANSI_YELLOW), ERROR(3, ANSI_RED);

        int level;
        String color;

        LogLevel(int _level, String _color) {
            this.level = _level;
            this.color = _color;
        }
    }
}
