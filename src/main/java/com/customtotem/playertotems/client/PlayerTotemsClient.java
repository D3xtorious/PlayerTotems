package com.customtotem.playertotems.client;

import com.customtotem.playertotems.PlayerTotems;
import net.fabricmc.api.ClientModInitializer;

/**
 * Client entrypoint for Player Totems, responsible for warming up model geometry
 * and initializing client render hooks.
 */
public class PlayerTotemsClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        PlayerTotems.LOGGER.info("Initializing Player Totems client renderer...");
        TotemModelGeometry.ensureLoaded();
    }
}
