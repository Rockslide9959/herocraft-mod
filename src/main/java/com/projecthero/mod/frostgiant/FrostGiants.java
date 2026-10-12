package com.projecthero.mod.frostgiant;

import java.util.UUID;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.projecthero.mod.event.EventConfig;
import com.projecthero.mod.event.EventInstance;
import com.projecthero.mod.event.EventManager;
import com.projecthero.mod.event.EventSavedData;
import com.projecthero.mod.event.EventTypes;
import com.projecthero.mod.frostgiant.block.FrostGiantBlocks;
import com.projecthero.mod.frostgiant.entity.IcePrisonEntity;
import com.projecthero.mod.frostgiant.item.FrostGear;
import com.projecthero.mod.frostgiant.item.FrostGiantItems;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.LivingEntity;

/**
 * v0.16.1: <b>The Frost Giant Assault</b> -- one entry point, called from {@code ProjectHeroMod.onInitialize}: the giants,
 * the blocks and items, the Rime smithing recipe, the event type, the blizzard packet, the "a fighter died" hook, the
 * prisoner rule (no attacking from inside the ice) and the Thor tie-ins. See {@link FrostGiantAssault}.
 *
 * <p>Three ways in: the Jotunheim Rune Stone (right-click it at night), {@code /projecthero raid start frost_giants},
 * and the natural trigger ({@link FrostGiantSpawner}, off by default -- Heimdall's warning to a worthy Thor in a snowy
 * thunderstorm).
 */
public final class FrostGiants {
	private FrostGiants() {
	}

	public static void initialize() {
		FrostGiantEntityTypes.initialize();
		FrostGiantBlocks.initialize();
		FrostGiantItems.initialize();
		FrostGear.initialize();
		FrostGiantRewards.initialize();
		FrostBestiary.initialize();
		PayloadTypeRegistry.playS2C().register(FrostBlizzardPayload.TYPE, FrostBlizzardPayload.CODEC);
		EventTypes.register(FrostGiantAssault.TYPE_ID, FrostGiantAssault::new);
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer p) {
				for (FrostGiantAssault a : FrostGiantAssault.all(p.server)) {
					if (a.dimension() == p.level().dimension() && a.isFighter(p.getUUID())) {
						a.onFighterDied(p.serverLevel(), p);
					}
				}
			}
		});
		// a prisoner in Laufey's ice (or the Casket's) can't attack anything but the ice round them
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
				!(source.getEntity() instanceof LivingEntity attacker && source.getDirectEntity() == attacker && IcePrisonEntity.isImprisoned(attacker)));
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseAmount, dealt, blocked) -> {
			if (!blocked && dealt > 0f) {
				FrostGear.afterDamage(entity, source);
			}
		});
	}

	/** Every server tick: the cold and Ymir for each running assault, the natural trigger, the orphan sweep. */
	public static void serverTick(MinecraftServer server) {
		for (FrostGiantAssault a : FrostGiantAssault.all(server)) {
			ServerLevel level = a.dimension() == null ? null : server.getLevel(a.dimension());
			if (level != null) {
				a.serverTick(level);
			}
		}
		FrostGiantSpawner.tick(server);
		FrostCleanup.tick(server);
	}

	/**
	 * Starts an assault centred on {@code at} (normally a Jotunheim Rune Stone), triggered by {@code activator} (may be
	 * null). Null on Peaceful, or if another assault is already running too close.
	 */
	public static FrostGiantAssault start(ServerLevel level, BlockPos at, ServerPlayer activator) {
		return start(level, at, activator, null);
	}

	/** {@link #start} with a hook that runs on the new assault before it activates (the gametests pin its braziers / arrivals). */
	public static FrostGiantAssault start(ServerLevel level, BlockPos at, ServerPlayer activator, java.util.function.Consumer<FrostGiantAssault> before) {
		if (level.getDifficulty() == Difficulty.PEACEFUL) {
			return null;
		}
		if (EventManager.anyActiveNear(level, at, FrostGiantAssault.TYPE_ID, EventConfig.framework().minDistanceBetweenEvents)) {
			return null;
		}
		FrostGiantAssault a = new FrostGiantAssault(UUID.randomUUID());
		if (!EventManager.start(level, a, at)) {
			return null;
		}
		if (before != null) {
			before.accept(a);
		}
		a.activate(level, activator);
		return a;
	}

	/** Ends (aborts and forgets) every running assault. */
	public static int endAll(MinecraftServer server) {
		int n = 0;
		for (EventInstance e : EventManager.active(server)) {
			ServerLevel level = e.dimension() == null ? null : server.getLevel(e.dimension());
			if (level != null && e instanceof FrostGiantAssault) {
				e.abort(level);
				EventSavedData.get(level).remove(e.id());
				n++;
			}
		}
		return n;
	}

	/**
	 * {@code /projecthero raid <verb> frost_giants}: {@code start} begins an assault where you stand (a Rune Stone appears
	 * at your feet; any time of day); {@code end} stops every assault; {@code advancetimer} jumps the nearest one to its
	 * next stage (countdown -> wave 1, a wave -> cleared, breather -> next, Laufey -> his next phase -> the Casket breaks ->
	 * he falls, victory -> the chest); {@code removetimer} has nothing to do.
	 */
	public static int command(CommandContext<CommandSourceStack> c, String verb) throws CommandSyntaxException {
		MinecraftServer server = c.getSource().getServer();
		switch (verb) {
			case "start" -> {
				ServerPlayer player = c.getSource().getPlayerOrException();
				ServerLevel level = player.serverLevel();
				if (level.getDifficulty() == Difficulty.PEACEFUL) {
					c.getSource().sendFailure(Component.literal("The Frost Giant Assault never runs on Peaceful."));
					return 0;
				}
				BlockPos at = player.blockPosition();
				if (EventManager.anyActiveNear(level, at, FrostGiantAssault.TYPE_ID, EventConfig.framework().minDistanceBetweenEvents)) {
					c.getSource().sendFailure(Component.literal("A Frost Giant Assault is already running nearby."));
					return 0;
				}
				level.setBlockAndUpdate(at, FrostGiantBlocks.RUNE_STONE.defaultBlockState());
				if (start(level, at, player) == null) {
					c.getSource().sendFailure(Component.literal("Could not start the Frost Giant Assault here."));
					return 0;
				}
				player.teleportTo(at.getX() + 0.5, at.getY() + 1.0, at.getZ() + 2.5);
				c.getSource().sendSuccess(() -> Component.literal("Frost Giant Assault started here."), true);
				return 1;
			}
			case "end" -> {
				int n = endAll(server);
				c.getSource().sendSuccess(() -> Component.literal("Ended " + n + " Frost Giant Assault(s)."), true);
				return n;
			}
			case "advancetimer" -> {
				ServerPlayer player = c.getSource().getPlayerOrException();
				FrostGiantAssault best = null;
				for (FrostGiantAssault a : FrostGiantAssault.all(server)) {
					if (a.isAt(player.serverLevel(), player.blockPosition(), 256)) {
						best = a;
					}
				}
				if (best == null) {
					c.getSource().sendFailure(Component.literal("No Frost Giant Assault near you."));
					return 0;
				}
				best.debugAdvance(player.serverLevel());
				FrostGiantAssault.Phase phase = best.phase();
				c.getSource().sendSuccess(() -> Component.literal("Advanced the Frost Giant Assault (now: " + phase + ")."), true);
				return 1;
			}
			default -> {
				c.getSource().sendSuccess(() -> Component.literal("The Frost Giant Assault has no timer to remove."), false);
				return 0;
			}
		}
	}
}
