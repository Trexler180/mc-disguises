package com.coffee.disguises.mixin;

import com.coffee.disguises.core.DisguiseNameResolver;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.OutgoingChatMessage;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.function.Predicate;

/** Substitutes only the per-recipient presentation name attached to normal signed chat. */
@Mixin(PlayerList.class)
public abstract class PlayerListChatMixin {

    @WrapOperation(
            method = "broadcastChatMessage(Lnet/minecraft/network/chat/PlayerChatMessage;Ljava/util/function/Predicate;Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/network/chat/ChatType$Bound;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerPlayer;sendChatMessage(Lnet/minecraft/network/chat/OutgoingChatMessage;ZLnet/minecraft/network/chat/ChatType$Bound;)V"
            )
    )
    private void disguises$decorateChatNameForObserver(
            ServerPlayer observer,
            OutgoingChatMessage message,
            boolean filtered,
            ChatType.Bound chatType,
            Operation<Void> original,
            PlayerChatMessage signedMessage,
            Predicate<ServerPlayer> filter,
            ServerPlayer sender,
            ChatType.Bound originalChatType) {
        ChatType.Bound decorated = DisguiseNameResolver.decorateChatName(chatType, sender, observer);
        original.call(observer, message, filtered, decorated);
    }
}
