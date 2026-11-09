package com.customtotem.playertotems;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main mod entrypoint for Player Totems (Server & Client Hybrid).
 */
public class PlayerTotems implements ModInitializer {
    public static final String MOD_ID = "playertotems";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Initializing Player Totems (Server & Client Hybrid)...");
    }
}
