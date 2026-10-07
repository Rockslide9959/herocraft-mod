package com.projecthero.mod.ultron;

import java.util.UUID;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.projecthero.mod.event.EventConfig;
import com.projecthero.mod.event.EventInstance;
import com.projecthero.mod.event.EventManager;
import com.projecthero.mod.event.EventSavedData;
import com.projecthero.mod.event.EventTypes;
import com.projecthero.mod.ultron.block.UltronBlocks;
import com.projecthero.mod.ultron.item.UltronItems;
import com.projecthero.mod.ultron.item.VibraniumPlating;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;

/**
 * v0.15.12: <b>The Ultron Uprising</b> -- one entry point, called from {@code ProjectHeroMod.onInitialize}: the robots,
 * the blocks and items, the Vibranium smithing recipe, the event type, the beam packet and the "a fighter died" hook.
 * See {@link UltronUprising}.
 *
 * <p>Three ways in: the Ultron Beacon (right-click it), {@code /projecthero raid start ultron}, and the natural JARVIS
 * trigger ({@link UltronSpawner}, off by default).
 */
public final class Ultron {
	private Ultron() {
	}

	public static void initialize() {
		UltronEntityTypes.initialize();
		UltronBlocks.initialize();
		UltronItems.initialize();
		VibraniumPlating.initialize();
		UltronRewards.initialize();
		PayloadTypeRegistry.playS2C().register(UltronBeamPayload.TYPE, UltronBeamPayload.CODEC);
		EventTypes.register(UltronUprising.TYPE_ID, UltronUprising::new);
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer p) {
				for (UltronUprising u : UltronUprising.all(p.server)) {
					if (u.dimension() == p.level().dimension() && u.isFighter(p.getUUID())) {
						u.onFighterDied(p.serverLevel(), p);
					}
				}
			}
		});
	}

	/**
	 * Starts an uprising centred on {@code at} (normally an Ultron Beacon), triggered by {@code activator} (may be null).
	 * Null on Peaceful, or if another uprising is already running too close.
	 */
	public static UltronUprising start(ServerLevel level, BlockPos at, ServerPlayer activator) {
		return start(level, at, activator, null);
	}

	/** {@link #start} with a hook that runs on the new uprising before it activates (the gametests pin its pylons / arrivals). */
	public static UltronUprising start(ServerLevel level, BlockPos at, ServerPlayer activator, java.util.function.Consumer<UltronUprising> before) {
		if (level.getDifficulty() == Difficulty.PEACEFUL) {
			return null;
		}
		if (EventManager.anyActiveNear(level, at, UltronUprising.TYPE_ID, EventConfig.framework().minDistanceBetweenEvents)) {
			return null;
		}
		UltronUprising u = new UltronUprising(UUID.randomUUID());
		if (!EventManager.start(level, u, at)) {
			return null;
		}
		if (before != null) {
			before.accept(u);
		}
		u.activate(level, activator);
		return u;
	}

	/** Ends (aborts and forgets) every running uprising. */
	public static int endAll(MinecraftServer server) {
		int n = 0;
		for (EventInstance e : EventManager.active(server)) {
			ServerLevel level = e.dimension() == null ? null : server.getLevel(e.dimension());
			if (level != null && e instanceof UltronUprising) {
				e.abort(level);
				EventSavedData.get(level).remove(e.id());
				n++;
			}
		}
		return n;
	}

	/**
	 * {@code /projecthero raid <verb> ultron}: {@code start} begins an uprising where you stand (an uplink appears at your
	 * feet); {@code end} stops every uprising; {@code advancetimer} jumps the nearest one to its next stage (countdown ->
	 * wave 1, a wave -> cleared, the pylon gate -> pylons destroyed, Prime -> falls, the Sentry -> purged);
	 * {@code removetimer} has nothing to do.
	 */
	public static int command(CommandContext<CommandSourceStack> c, String verb) throws CommandSyntaxException {
		MinecraftServer server = c.getSource().getServer();
		switch (verb) {
			case "start" -> {
				ServerPlayer player = c.getSource().getPlayerOrException();
				ServerLevel level = player.serverLevel();
				if (level.getDifficulty() == Difficulty.PEACEFUL) {
					c.getSource().sendFailure(Component.literal("The Ultron Uprising never runs on Peaceful."));
					return 0;
				}
				BlockPos at = player.blockPosition();
				if (EventManager.anyActiveNear(level, at, UltronUprising.TYPE_ID, EventConfig.framework().minDistanceBetweenEvents)) {
					c.getSource().sendFailure(Component.literal("An Ultron Uprising is already running nearby."));
					return 0;
				}
				level.setBlockAndUpdate(at, UltronBlocks.ULTRON_BEACON.defaultBlockState());
				if (start(level, at, player) == null) {
					c.getSource().sendFailure(Component.literal("Could not start the Ultron Uprising here."));
					return 0;
				}
				player.teleportTo(at.getX() + 0.5, at.getY() + 1.0, at.getZ() + 2.5);
				c.getSource().sendSuccess(() -> Component.literal("Ultron Uprising started here."), true);
				return 1;
			}
			case "end" -> {
				int n = endAll(server);
				c.getSource().sendSuccess(() -> Component.literal("Ended " + n + " Ultron Uprising(s)."), true);
				return n;
			}
			case "advancetimer" -> {
				ServerPlayer player = c.getSource().getPlayerOrException();
				UltronUprising best = null;
				for (UltronUprising u : UltronUprising.all(server)) {
					if (u.isAt(player.serverLevel(), player.blockPosition(), 256)) {
						best = u;
					}
				}
				if (best == null) {
					c.getSource().sendFailure(Component.literal("No Ultron Uprising near you."));
					return 0;
				}
				best.debugAdvance(player.serverLevel());
				UltronUprising.Phase phase = best.phase();
				c.getSource().sendSuccess(() -> Component.literal("Advanced the Ultron Uprising (now: " + phase + ")."), true);
				return 1;
			}
			default -> {
				c.getSource().sendSuccess(() -> Component.literal("The Ultron Uprising has no timer to remove."), false);
				return 0;
			}
		}
	}
}
