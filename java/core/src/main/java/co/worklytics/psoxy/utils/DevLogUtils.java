package co.worklytics.psoxy.utils;

import co.worklytics.psoxy.gateway.impl.EnvVarsConfigService;

import java.util.logging.Logger;

public class DevLogUtils {

    public static void warn(EnvVarsConfigService configService, Logger logger, String message, Object... args) {
        if (configService.isDevelopment()) {
            logger.warning(String.format(message, args));
        }
    }

    public static void info(EnvVarsConfigService configService, Logger logger, String message, Object... args) {
        if (configService.isDevelopment()) {
            logger.info(String.format(message, args));
        }
    }

    public static void severe(EnvVarsConfigService configService, Logger logger, String message, Object... args) {
        if (configService.isDevelopment()) {
            logger.severe(String.format(message, args));
        }
    }

    public static void fine(EnvVarsConfigService configService, Logger logger, String message, Object... args) {
        if (configService.isDevelopment()) {
            logger.fine(String.format(message, args));
        }
    }

    public static void warn(EnvVarsConfigService configService, Logger logger, String message, Throwable exception, Object... args) {
        if (configService.isDevelopment()) {
            logger.warning(String.format(message, args) + ": " + exception.getMessage());
        }
    }

    public static void severe(EnvVarsConfigService configService, Logger logger, String message, Throwable exception, Object... args) {
        if (configService.isDevelopment()) {
            logger.severe(String.format(message, args) + ": " + exception.getMessage());
        }
    }
}
