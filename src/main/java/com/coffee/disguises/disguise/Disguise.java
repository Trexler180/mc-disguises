package com.coffee.disguises.disguise;

import com.coffee.disguises.watcher.FlagWatcher;

/**
 * Immutable-ish data object describing a single active disguise.
 * Treat as a value type — use the Builder to construct, and create a new
 * instance when modifying rather than mutating in-place.
 */
public class Disguise {

    protected final DisguiseType type;
    protected final FlagWatcher watcher;
    protected boolean selfDisguise;
    protected boolean showName;

    protected Disguise(Builder builder) {
        this.type = builder.type;
        this.watcher = builder.watcher;
        this.selfDisguise = builder.selfDisguise;
        this.showName = builder.showName;
    }

    public DisguiseType getType() { return type; }
    public FlagWatcher getWatcher() { return watcher; }
    public boolean isSelfDisguise() { return selfDisguise; }
    public boolean isShowName() { return showName; }

    public void setSelfDisguise(boolean selfDisguise) { this.selfDisguise = selfDisguise; }
    public void setShowName(boolean showName) { this.showName = showName; }

    @Override
    public String toString() {
        return "Disguise{type=" + type.getId() + ", selfDisguise=" + selfDisguise + "}";
    }

    // ---- Builder ----

    public static Builder builder(DisguiseType type) {
        return new Builder(type);
    }

    public static class Builder {
        private final DisguiseType type;
        private FlagWatcher watcher;
        private boolean selfDisguise = false;
        private boolean showName = false;

        public Builder(DisguiseType type) {
            this.type = type;
            this.watcher = type.createDefaultWatcher();
        }

        public Builder watcher(FlagWatcher watcher) { this.watcher = watcher; return this; }
        public Builder selfDisguise(boolean v) { this.selfDisguise = v; return this; }
        public Builder showName(boolean v) { this.showName = v; return this; }

        public Disguise build() { return new Disguise(this); }
    }
}
