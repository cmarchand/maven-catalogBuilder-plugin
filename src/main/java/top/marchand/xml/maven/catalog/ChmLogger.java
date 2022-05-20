package top.marchand.xml.maven.catalog;

import org.apache.maven.plugin.logging.Log;

import java.util.HashMap;
import java.util.Map;

public class ChmLogger {
    private final Log log;
    private Map<LogReason, Boolean> enabled = new HashMap<>();

    public ChmLogger(Log log) {
        this.log = log;
    }
    public void log(LogReason reason, LogLevel level, String message) {
        if(isEnabled(reason)) {
            level.log(log, reason.toString()+": "+message);
        }
    }
    public void enableReason(LogReason reason, boolean value) {
        enabled.put(reason, value);
    }

    private boolean isEnabled(LogReason reason) {
        Boolean ret = enabled.get(reason);
        return ret!=null ? ret.booleanValue() : false;
    }

    public enum LogReason {
        VISITING, EXCLUSION, MATCHES, PARAMETERS, CLASSPATH, DEPENDENCY;

        @Override
        public String toString() {
            return name();
        }
    }

    public enum LogLevel {
        DEBUG("DEBUG", new BiConsumer<Log, String>() {
            @Override
            public void accept(Log log, String s) {
                log.debug(s);
            }
        }),
        INFO("INFO", new BiConsumer<Log, String>() {
            @Override
            public void accept(Log log, String s) {
                log.info(s);
            }
        }),
        WARN("WARN", new BiConsumer<Log, String>() {
            @Override
            public void accept(Log log, String s) {
                log.warn(s);
            }
        }),
        ERROR("ERROR", new BiConsumer<Log, String>() {
            @Override
            public void accept(Log log, String s) {
                log.error(s);
            }
        });

        private final BiConsumer<Log, String> logger;
        private final String name;

        LogLevel(String name, BiConsumer<Log, String> logger) {
            this.name = name;
            this.logger = logger;
        }

        private void log(Log log, String message) {
            logger.accept(log, message);
        }

        private interface BiConsumer<T,U> {
            void accept(T t, U u);
        }
    }
}
