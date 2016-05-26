/**
 * Created by brandon on 5/25/16.
 */
public class Logger {

    public static LogLevel masterLogLevel = LogLevel.INFO; //default log level, don't print debug

    public static void log(LogLevel ll, String message) {
        if (ll.level >= masterLogLevel.level) { //if the loglevel is higher, dont log it out to stdout
            System.out.println(message);
        }
    }

    public static void setMasterLogLevel(LogLevel ll) {
        masterLogLevel = ll;
    }

    public enum LogLevel {
        DEBUG(0), INFO(1), WARNING(2), ERROR(3);

        int level;

        LogLevel(int _level) {
            this.level = _level;
        }
    }
}
