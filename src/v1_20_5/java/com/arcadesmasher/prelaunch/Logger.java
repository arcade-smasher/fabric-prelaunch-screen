package com.arcadesmasher.prelaunch;

import org.slf4j.LoggerFactory;

public class Logger {
    private final org.slf4j.Logger LOGGER;

    Logger(String name) {
        LOGGER = LoggerFactory.getLogger(name);
    }

    void info(String msg, Throwable t) {
        LOGGER.info(msg, t);
    }
    void info(String msg) {
        LOGGER.info(msg);
    }

    void warn(String msg, Throwable t) {
        LOGGER.warn(msg, t);
    }
    void warn(String msg) {
        LOGGER.warn(msg);
    }

    void error(String msg, Throwable t) {
        LOGGER.error(msg, t);
    }
    void error(String msg) {
        LOGGER.error(msg);
    }
}