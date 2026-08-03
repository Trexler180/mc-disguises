package com.coffee.disguises.core;

import com.coffee.disguises.DisguisesMod;
import com.coffee.disguises.disguise.Disguise;
import com.coffee.disguises.disguise.PlayerDisguise;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;

/** Resolves disguise names only for explicitly supported presentation contexts. */
public final class DisguiseNameResolver {

    private DisguiseNameResolver() {}

    /**
     * Rebinds a signed chat message for one recipient. The signed message itself is
     * deliberately left alone so chat validation and reporting retain the real sender.
     */
    public static ChatType.Bound decorateChatName(ChatType.Bound original,
                                                   ServerPlayer sender,
                                                   ServerPlayer observer) {
        if (DisguisesMod.CONFIG == null || !DisguisesMod.CONFIG.disguiseNamesInChat) {
            return original;
        }

        Disguise disguise = DisguiseManager.INSTANCE.getDisguiseForObserver(sender, observer.getUUID());
        if (disguise == null) {
            disguise = DisguiseManager.INSTANCE.getDisguise(sender);
        }

        Component name = resolve(sender, disguise);
        if (name == null) return original;
        return new ChatType.Bound(original.chatType(), name, original.targetName());
    }

    /**
     * Resolves the global disguise name for vanilla system-message presentation,
     * including death and advancement announcements. These messages are normally
     * constructed once and broadcast, so observer-specific overrides cannot be used.
     */
    public static Component resolveSystemMessageName(ServerPlayer player) {
        if (DisguisesMod.CONFIG == null || !DisguisesMod.CONFIG.disguiseNamesInDeathMessages) {
            return null;
        }
        return resolve(player, DisguiseManager.INSTANCE.getDisguise(player));
    }

    /** Returns null when no safe substitute is available. */
    private static Component resolve(ServerPlayer player, Disguise disguise) {
        if (disguise == null) return null;

        Component rawName;
        if (disguise instanceof PlayerDisguise playerDisguise) {
            String disguiseName = playerDisguise.getDisguiseName();
            if (disguiseName == null || disguiseName.isBlank()) return null;
            rawName = Component.literal(disguiseName);
        } else {
            rawName = disguise.getType().getEntityType().getDescription();
        }

        MutableComponent formatted = PlayerTeam.formatNameForTeam(player.getTeam(), rawName);
        if (DisguisesMod.CONFIG.revealRealNameOnHover) {
            String realName = player.getGameProfile().name();
            formatted.withStyle(style -> style.withHoverEvent(new HoverEvent.ShowText(
                    Component.literal("Real player: " + realName))));
        }
        return formatted;
    }
}
