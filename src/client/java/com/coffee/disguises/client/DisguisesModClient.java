package com.coffee.disguises.client;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client-side entrypoint.
 * Currently handles: action bar display scheduling (future), self-disguise rendering (future).
 */
public class DisguisesModClient implements ClientModInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger("disguises-client");

    @Override
    public void onInitializeClient() {
        LOGGER.info("Disguises client initialized.");
        // Future: register client-side render hooks for self-disguise (Phase 2)
    }
}
