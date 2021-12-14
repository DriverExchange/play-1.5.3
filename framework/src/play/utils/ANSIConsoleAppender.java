package play.utils;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.appender.ConsoleAppender;
import org.apache.logging.log4j.core.net.Priority;
import org.apache.logging.log4j.core.LogEvent;

/**
 * Colour-coded console appender for Log4J.
 */

/** Replace by the pattern in the Logger.java class
 * %highlight{%d{HH:mm:ss.SSS} %-5level %logger{36}.%M() @%L - %msg%n}{FATAL=red blink, ERROR=red, WARN=yellow bold, INFO=black, DEBUG=green bold, TRACE=blue}
 * */
//public class ANSIConsoleAppender extends ConsoleAppender {
//
//    static final int NORMAL = 0;
//    static final int BRIGHT = 1;
//    static final int FOREGROUND_BLACK = 30;
//    static final int FOREGROUND_RED = 31;
//    static final int FOREGROUND_GREEN = 32;
//    static final int FOREGROUND_YELLOW = 33;
//    static final int FOREGROUND_BLUE = 34;
//    static final int FOREGROUND_MAGENTA = 35;
//    static final int FOREGROUND_CYAN = 36;
//    static final int FOREGROUND_WHITE = 37;
//    static final String PREFIX = "\u001b[";
//    static final String SUFFIX = "m";
//    static final char SEPARATOR = ';';
//    static final String END_COLOUR = PREFIX + SUFFIX;
//    static final String FATAL_COLOUR = PREFIX + BRIGHT + SEPARATOR + FOREGROUND_RED + SUFFIX;
//    static final String ERROR_COLOUR = PREFIX + NORMAL + SEPARATOR + FOREGROUND_RED + SUFFIX;
//    static final String WARN_COLOUR = PREFIX + NORMAL + SEPARATOR + FOREGROUND_YELLOW + SUFFIX;
//    static final String INFO_COLOUR = PREFIX + SUFFIX;
//    static final String DEBUG_COLOUR = PREFIX + NORMAL + SEPARATOR + FOREGROUND_CYAN + SUFFIX;
//    static final String TRACE_COLOUR = PREFIX + NORMAL + SEPARATOR + FOREGROUND_BLUE + SUFFIX;
//
//    /**
//     * Wraps the ANSI control characters around the
//     * output from the super-class Appender.
//     */
//    @Override
//    public void append(LogEvent event) {
//        this.qw.write(getColour(event.getLevel()));
//        super.append(event);
//        this.qw.write(END_COLOUR);
//        if (this.immediateFlush) {
//            this.qw.flush();
//        }
//    }
//
//    /**
//     * Get the appropriate control characters to change
//     * the colour for the specified logging level.
//     */
//    private String getColour(org.apache.logging.log4j.Level level) {
//        if(level == Level.FATAL)
//            return FATAL_COLOUR;
//        else if(level == Level.ERROR)
//            return ERROR_COLOUR;
//        else if(level == Level.WARN)
//            return WARN_COLOUR;
//        else if(level == Level.INFO)
//            return INFO_COLOUR;
//        else if(level == Level.DEBUG)
//            return DEBUG_COLOUR;
//        else
//            return TRACE_COLOUR;
//        }
//    }
//}
