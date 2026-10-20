package pdx.dev.fbactors.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public final class Watermark {


    public static final String LICENCE = "MIT";


    public static final String SOURCE = "https://github.com/PDX122/Actors";


    public static final String NOTICE = "Flashback Actors is MIT-licensed. "
        + "Source: https://github.com/PDX122/Actors. Keep the copyright notice; "
        + "redistribution as your own work is not permitted.";

    private static final Logger LOGGER = LoggerFactory.getLogger("fbactors");

    private Watermark() {
    }


    public static void log() {
        LOGGER.info("Flashback Actors ({}) - {}", LICENCE, NOTICE);
    }
}

