package com.coffee.disguises.disguise;

import com.coffee.disguises.watcher.FlagWatcher;
import com.mojang.authlib.GameProfile;

/**
 * A disguise that uses a player skin.
 * Requires a resolved GameProfile (with skin Property) before it looks correct.
 * While skin is loading, a default Steve skin is used.
 */
public class PlayerDisguise extends Disguise {

    /** The fake name shown above the disguise. */
    private final String disguiseName;

    /**
     * The full GameProfile including skin/cape Properties.
     * May be null while skin is still being fetched asynchronously.
     */
    private volatile GameProfile skinProfile;

    private PlayerDisguise(PlayerBuilder builder) {
        super(builder);
        this.disguiseName = builder.disguiseName;
        this.skinProfile = builder.skinProfile;
    }

    public String getDisguiseName() { return disguiseName; }

    public GameProfile getSkinProfile() { return skinProfile; }

    /** Called once the async skin fetch completes. */
    public void setSkinProfile(GameProfile profile) { this.skinProfile = profile; }

    public boolean hasSkin() { return skinProfile != null && !skinProfile.properties().isEmpty(); }

    // ---- Builder ----

    public static PlayerBuilder builder(String disguiseName) {
        return new PlayerBuilder(disguiseName);
    }

    public static class PlayerBuilder extends Builder {
        private final String disguiseName;
        private GameProfile skinProfile;

        public PlayerBuilder(String disguiseName) {
            super(DisguiseType.PLAYER);
            this.disguiseName = disguiseName;
        }

        public PlayerBuilder skinProfile(GameProfile profile) {
            this.skinProfile = profile;
            return this;
        }

        @Override
        public PlayerDisguise build() { return new PlayerDisguise(this); }
    }
}
