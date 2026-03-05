package com.coffee.disguises.watcher;

import net.minecraft.network.syncher.SynchedEntityData;

import java.util.ArrayList;
import java.util.List;

/**
 * Holds the metadata flags for a disguise.
 * The base class covers universal Entity DataTracker flags.
 */
public class FlagWatcher {

    protected boolean onFire     = false;
    protected boolean crouching  = false;
    protected boolean sprinting  = false;
    protected boolean swimming   = false;
    protected boolean invisible  = false;
    protected boolean glowing    = false;
    protected boolean fallFlying = false;

    protected String  customName        = null;
    protected boolean customNameVisible = false;
    protected boolean silent            = false;
    protected boolean noGravity         = false;

    // ---- Setters ----
    public FlagWatcher setOnFire(boolean v)           { this.onFire = v; return this; }
    public FlagWatcher setCrouching(boolean v)         { this.crouching = v; return this; }
    public FlagWatcher setSprinting(boolean v)         { this.sprinting = v; return this; }
    public FlagWatcher setSwimming(boolean v)          { this.swimming = v; return this; }
    public FlagWatcher setInvisible(boolean v)         { this.invisible = v; return this; }
    public FlagWatcher setGlowing(boolean v)           { this.glowing = v; return this; }
    public FlagWatcher setFallFlying(boolean v)        { this.fallFlying = v; return this; }
    public FlagWatcher setCustomName(String name)      { this.customName = name; return this; }
    public FlagWatcher setCustomNameVisible(boolean v) { this.customNameVisible = v; return this; }
    public FlagWatcher setSilent(boolean v)            { this.silent = v; return this; }
    public FlagWatcher setNoGravity(boolean v)         { this.noGravity = v; return this; }

    // ---- Getters ----
    public boolean isOnFire()            { return onFire; }
    public boolean isCrouching()         { return crouching; }
    public boolean isSprinting()         { return sprinting; }
    public boolean isSwimming()          { return swimming; }
    public boolean isInvisible()         { return invisible; }
    public boolean isGlowing()           { return glowing; }
    public boolean isFallFlying()        { return fallFlying; }
    public String  getCustomName()       { return customName; }
    public boolean isCustomNameVisible() { return customNameVisible; }
    public boolean isSilent()            { return silent; }
    public boolean isNoGravity()         { return noGravity; }

    /**
     * Must be public — MetadataBuilder calls this from a different package.
     * Bit layout matches vanilla Entity.DATA_SHARED_FLAGS_ID.
     */
    public byte buildSharedFlags() {
        byte flags = 0;
        if (onFire)     flags |= 0x01;
        if (crouching)  flags |= 0x02;
        if (sprinting)  flags |= 0x08;
        if (swimming)   flags |= 0x10;
        if (invisible)  flags |= 0x20;
        if (glowing)    flags |= 0x40;
        if (fallFlying) flags |= (byte) 0x80;
        return flags;
    }

    public List<SynchedEntityData.DataValue<?>> buildMetadata() {
        return new ArrayList<>();
    }
}
