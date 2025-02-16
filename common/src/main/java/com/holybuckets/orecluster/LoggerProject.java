package com.holybuckets.orecluster;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import com.holybuckets.foundation.LoggerBase;

import java.util.HashMap;

public class LoggerProject extends LoggerBase {

    public static final Logger LOGGER = LogUtils.getLogger();
    public static final String PREFIX = Constants.MOD_NAME;
    public static final Boolean DEBUG_MODE = false;

    public static void logInfo(String logId, String message) {
        LoggerBase.logInfo(PREFIX, logId, message);
    }

    public static void logWarning(String logId, String string) {
        LoggerBase.logWarning(PREFIX, logId, string);
    }

    public static void logError(String logId, String string) {
        LoggerBase.logError(PREFIX, logId, string);
    }

    public static void logDebug(String logId, String string) {
        if (DEBUG_MODE)
            LoggerBase.logDebug(PREFIX, logId, string);
    }

    public static void logInit(String logId, String string) {
        logDebug(logId, "--------" + string.toUpperCase() + " INITIALIZED --------");
    }


    //Client side logging
    public static void logClientInfo(String message) {
        LoggerBase.logClientInfo(message);
    }


    public static void logClientDisplay(String message) {
        String msg = buildClientDisplayMessage("", message);
    }

    /**
     * Returns time in milliseconds
     *
     * @param t1
     * @param t2
     */
    public static float getTime(long t1, long t2) {
        return (t2 - t1) / 1000_000L;
    }

    public static void threadExited(String logId, Object threadContainer, Throwable thrown) {
        StringBuilder sb = new StringBuilder();
        sb.append("Thread " + Thread.currentThread().getName() + " exited");

        if (thrown == null)
        {
            logDebug(logId, sb + " gracefully");
        } else
        {
            sb.append(" with exception: " + thrown.getMessage());

            //get the stack trace of the exception into a string to load into sb
            StackTraceElement[] stackTrace = thrown.getStackTrace();
            for (StackTraceElement ste : stackTrace) {
                sb.append("\n" + ste.toString() );
            }
            sb.append("\n\n");

            logError(logId, sb.toString());

        }
    }


    //create a statatic final hashmap called FILTER_RULES that holds log entries
    private static final HashMap<String, LogEntry> FILTER_RULES = new HashMap<>();
    /*
    static {
        //FILTER_RULES.put("INFO", new LogEntry("INFO", "000", PREFIX, "This is an info message"));
        FILTER_RULES.put("003001", new LogEntry(null, null, null, null, 0.1f));
        FILTER_RULES.put("003002", new LogEntry(null, null, null, null, 0.1f));
        FILTER_RULES.put("003007", new LogEntry(null, null, null, "minecraft:", null));
        FILTER_RULES.put("007002", new LogEntry(null, null, null, "1", null));

        //FILTER_RULES.put("003005", new LogEntry(null, null, null, null, null));
        //FILTER_RULES.put("003006", new LogEntry(null, null, null, "minecraft", null));
        FILTER_RULES.put("002020", new LogEntry(null, null, null, null, null));
        FILTER_RULES.put("002004", new LogEntry(null, null, null, null, null));
        FILTER_RULES.put("002032", new LogEntry(null, null, null, null, null));
        //FILTER_RULES.put("002028", new LogEntry(null, null, null, null, 0.01f));
        FILTER_RULES.put("002028", new LogEntry(null, null, null, null, 0.001f));
        FILTER_RULES.put("002025", new LogEntry(null, null, null, null, 0.01f));
        FILTER_RULES.put("002026", new LogEntry(null, null, null, null, 0.01f));
        FILTER_RULES.put("002027", new LogEntry(null, null, null, null, 0.01f));
        FILTER_RULES.put("002015", new LogEntry(null, null, null, null, 0.01f));
        FILTER_RULES.put("002033", new LogEntry(null, null, null, null, 0.01f));

    }


    private static LogEntry applySamplingRate(LogEntry entry)
    {
        boolean containsFilterableType = FILTER_RULES.containsKey(entry.type);
        boolean containsFilterableId = FILTER_RULES.containsKey(entry.id);

        if (containsFilterableType) {
            return FILTER_RULES.get(entry.type);
        }

        if (containsFilterableId) {
            return FILTER_RULES.get(entry.id);
        }
        // Apply sampling rate
        //return Math.random() < SAMPLE_RATE;
        return null;
    }
    */


}
//END CLASS