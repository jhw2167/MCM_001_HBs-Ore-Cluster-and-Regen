package com.holybuckets.orecluster;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import com.holybuckets.foundation.LoggerBase;

public class LoggerProject extends LoggerBase {


    public static final Logger LOGGER = LogUtils.getLogger();
    public static final String PREFIX = OreClustersAndRegenMain.NAME;
    public static final Boolean DEBUG_MODE = true;

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

}
//END CLASS