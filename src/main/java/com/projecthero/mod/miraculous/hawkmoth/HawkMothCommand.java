package com.projecthero.mod.miraculous.hawkmoth;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.projecthero.mod.miraculous.Kwami;
import com.projecthero.mod.miraculous.Miraculous;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * v0.16.6: {@code /hawkmoth} -- what the offer's buttons run, open to every player:
 * <ul>
 *   <li>{@code accept} / {@code refuse}: answer Hawk Moth's offer (only the player it was sent to has one);</li>
 *   <li>{@code link <message>}: Hawk Moth speaks straight into his Champion's mind (only a transformed Hawk Moth with a
 *       Champion player; nobody else hears it). Akuma Link (Shift+C) opens the chat box with it ready.</li>
 * </ul>
 */
public final class HawkMothCommand {
	private HawkMothCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("hawkmoth")
				.then(Commands.literal("accept").executes(ctx -> {
					ServerPlayer p = ctx.getSource().getPlayerOrException();
					return HawkMothAkumas.accept(p) ? 1 : 0;
				}))
				.then(Commands.literal("refuse").executes(ctx -> {
					ServerPlayer p = ctx.getSource().getPlayerOrException();
					HawkMothAkumas.refuse(p, false);
					return 1;
				}))
				.then(Commands.literal("link").then(Commands.argument("message", StringArgumentType.greedyString()).executes(ctx -> {
					ServerPlayer p = ctx.getSource().getPlayerOrException();
					return link(p, StringArgumentType.getString(ctx, "message")) ? 1 : 0;
				}))));
	}

	/** Hawk Moth's words to his Champion player, and to nobody else. */
	public static boolean link(ServerPlayer owner, String message) {
		ServerPlayer champion = Miraculous.transformedAs(owner, Kwami.NOOROO) ? HawkMothAkumas.championPlayerOf(owner) : null;
		if (champion == null) {
			owner.sendSystemMessage(Component.translatable("message.projecthero.hawk_moth.link_nobody").withStyle(ChatFormatting.GRAY));
			return false;
		}
		champion.sendSystemMessage(Component.translatable("message.projecthero.hawk_moth.link_heard", message)
				.withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC));
		owner.sendSystemMessage(Component.translatable("message.projecthero.hawk_moth.link_said", champion.getDisplayName(), message)
				.withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC));
		return true;
	}
}
