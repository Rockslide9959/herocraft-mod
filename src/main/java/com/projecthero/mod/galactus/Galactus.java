package com.projecthero.mod.galactus;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.event.EventConfig;
import com.projecthero.mod.event.EventInstance;
import com.projecthero.mod.event.EventManager;
import com.projecthero.mod.event.EventSavedData;
import com.projecthero.mod.event.EventTypes;
import com.projecthero.mod.galactus.block.GalactusBlocks;
import com.projecthero.mod.galactus.entity.GalactusEntityTypes;
import com.projecthero.mod.galactus.item.GalactusItems;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * v0.16.1: <b>Galactus: The Devourer</b> -- one entry point, called from {@code ProjectHeroMod.onInitialize}: the entities,
 * blocks and items, the event type, the sky packet, the "a fighter died" hook, the cosmic-signal roll and the orphan
 * revert. See {@link GalactusEvent}.
 *
 * <p>Three ways in: the <b>Herald's Beacon</b> (right-click it at night), {@code /projecthero raid start galactus}, and
 * the <b>cosmic signal</b> -- once the world has beaten the Ultron Contagion finale or a Frost Giant Assault, a
 * {@link GalactusConfig.Trigger#chancePerDay} roll once per in-game day starts it near a random online player.
 */
public final class Galactus {
	public enum Source { BEACON, COMMAND, SIGNAL }

	private Galactus() {
	}

	public static void initialize() {
		GalactusEntityTypes.initialize();
		GalactusBlocks.initialize();
		GalactusItems.initialize();
		GalactusRewards.initialize();
		PayloadTypeRegistry.playS2C().register(GalactusSkyPayload.TYPE, GalactusSkyPayload.CODEC);
		EventTypes.register(GalactusEvent.TYPE_ID, GalactusEvent::new);
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer p) {
				for (GalactusEvent e : GalactusEvent.all(p.server)) {
					if (e.dimension() == p.level().dimension() && e.isFighter(p.getUUID())) {
						e.onFighterDied(p.serverLevel(), p);
					}
				}
			}
		});
	}

	/** Every server tick: each running event's per-tick work, the terrain revert, the cosmic signal. */
	public static void serverTick(MinecraftServer server) {
		for (GalactusEvent e : GalactusEvent.all(server)) {
			ServerLevel level = e.dimension() == null ? null : server.getLevel(e.dimension());
			if (level != null) {
				e.serverTick(level);
			}
		}
		GalactusTerrain.get(server).tickRevert(server, Math.max(1, GalactusConfig.converters().revertPerTick));
		if (server.getTickCount() % 20 == 3) {
			rollSignal(server);
		}
	}

	/**
	 * Starts Galactus centred on {@code at} (a Herald's Beacon, or the spot the signal / command picked), triggered by
	 * {@code activator} (may be null). Null when switched off, on Peaceful, or if another one is running too close.
	 */
	public static GalactusEvent start(ServerLevel level, BlockPos at, ServerPlayer activator, Source source) {
		return start(level, at, activator, source, null);
	}

	public static GalactusEvent start(ServerLevel level, BlockPos at, ServerPlayer activator, Source source,
			java.util.function.Consumer<GalactusEvent> before) {
		if (!GalactusConfig.trigger().enabled || level.getDifficulty() == Difficulty.PEACEFUL) {
			return null;
		}
		if (EventManager.anyActiveNear(level, at, GalactusEvent.TYPE_ID, EventConfig.framework().minDistanceBetweenEvents)) {
			return null;
		}
		GalactusEvent e = new GalactusEvent(UUID.randomUUID());
		if (!EventManager.start(level, e, at)) {
			return null;
		}
		if (before != null) {
			before.accept(e);
		}
		e.activate(level, activator, true);
		return e;
	}

	/** The cosmic signal: once per in-game day, after the world earned it, a {@code chancePerDay} roll. */
	private static void rollSignal(MinecraftServer server) {
		GalactusConfig.Trigger cfg = GalactusConfig.trigger();
		ServerLevel level = server.overworld();
		if (!cfg.enabled || !cfg.naturalTriggerEnabled || level.getDifficulty() == Difficulty.PEACEFUL) {
			return;
		}
		long day = level.getDayTime() / 24000L;
		GalactusWorldState ws = GalactusWorldState.get(server);
		if (day == ws.lastRollDay) {
			return;
		}
		ws.lastRollDay = day;
		ws.setDirty();
		if (!GalactusWorldState.unlocked(server) || !GalactusEvent.all(server).isEmpty() || level.random.nextDouble() >= cfg.chancePerDay) {
			return;
		}
		startSignal(level);
	}

	/** Picks a random eligible player and starts it near them. Null if nobody / nowhere fits. */
	public static GalactusEvent startSignal(ServerLevel level) {
		List<ServerPlayer> eligible = new ArrayList<>();
		for (ServerPlayer p : level.players()) {
			if (GalactusCombat.isFighter(p) && EventManager.forPlayer(p) == null) {
				eligible.add(p);
			}
		}
		if (eligible.isEmpty()) {
			return null;
		}
		ServerPlayer p = eligible.get(level.random.nextInt(eligible.size()));
		GalactusConfig.Trigger cfg = GalactusConfig.trigger();
		for (int attempt = 0; attempt < 8; attempt++) {
			double a = level.random.nextDouble() * Math.PI * 2;
			double r = cfg.naturalOffsetMin + level.random.nextDouble() * Math.max(0, cfg.naturalOffsetMax - cfg.naturalOffsetMin);
			int x = p.getBlockX() + (int) Math.round(Math.cos(a) * r);
			int z = p.getBlockZ() + (int) Math.round(Math.sin(a) * r);
			if (level.getChunkSource().getChunkNow(x >> 4, z >> 4) == null) {
				continue;
			}
			int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
			BlockPos at = new BlockPos(x, y, z);
			BlockState there = level.getBlockState(at);
			BlockState below = level.getBlockState(at.below());
			if (!there.isAir() || !below.is(GalactusBlocks.CONSUMABLE) || GalactusTerrain.nearBlockEntity(level, at, 6)) {
				continue;
			}
			level.setBlockAndUpdate(at, GalactusBlocks.HERALDS_BEACON.defaultBlockState());
			GalactusEvent e = start(level, at, null, Source.SIGNAL);
			if (e == null) {
				level.removeBlock(at, false);
				return null;
			}
			for (ServerPlayer near : level.players()) {
				if (near.distanceToSqr(p) < 200 * 200) {
					near.sendSystemMessage(Component.translatable("message.projecthero.galactus.signal").withStyle(ChatFormatting.LIGHT_PURPLE));
				}
			}
			ProjectHeroMod.LOGGER.info("[ProjectHero] The cosmic signal reached Galactus near {}", p.getGameProfile().getName());
			return e;
		}
		return null;
	}

	public static int endAll(MinecraftServer server) {
		int n = 0;
		for (EventInstance e : EventManager.active(server)) {
			ServerLevel level = e.dimension() == null ? null : server.getLevel(e.dimension());
			if (level != null && e instanceof GalactusEvent) {
				e.abort(level);
				EventSavedData.get(level).remove(e.id());
				n++;
			}
		}
		return n;
	}

	/**
	 * {@code /projecthero raid <verb> galactus}: {@code start} begins it where you stand (a Herald's Beacon appears at your
	 * feet; any time of day); {@code end} stops every one (everything it changed is put back); {@code advancetimer} jumps
	 * the nearest one to its next stage (arrival -> the Herald -> he turns -> the Worldship -> one converter falls ... ->
	 * Galactus -> the pylons fall -> the weak points break -> the Nullifier is in your hands -> it is used -> the chest);
	 * {@code removetimer} has nothing to do.
	 */
	public static int command(CommandContext<CommandSourceStack> c, String verb) throws CommandSyntaxException {
		MinecraftServer server = c.getSource().getServer();
		switch (verb) {
			case "start" -> {
				ServerPlayer player = c.getSource().getPlayerOrException();
				ServerLevel level = player.serverLevel();
				if (level.getDifficulty() == Difficulty.PEACEFUL) {
					c.getSource().sendFailure(Component.literal("Galactus never comes on Peaceful."));
					return 0;
				}
				if (!GalactusConfig.trigger().enabled) {
					c.getSource().sendFailure(Component.literal("Galactus is switched off (config/projecthero_galactus.json, trigger.enabled)."));
					return 0;
				}
				BlockPos at = player.blockPosition();
				if (EventManager.anyActiveNear(level, at, GalactusEvent.TYPE_ID, EventConfig.framework().minDistanceBetweenEvents)) {
					c.getSource().sendFailure(Component.literal("Galactus is already here."));
					return 0;
				}
				level.setBlockAndUpdate(at, GalactusBlocks.HERALDS_BEACON.defaultBlockState());
				if (start(level, at, player, Source.COMMAND) == null) {
					c.getSource().sendFailure(Component.literal("Could not start Galactus here."));
					return 0;
				}
				player.teleportTo(at.getX() + 0.5, at.getY() + 1.0, at.getZ() + 2.5);
				c.getSource().sendSuccess(() -> Component.literal("Galactus: The Devourer started here."), true);
				return 1;
			}
			case "end" -> {
				int n = endAll(server);
				c.getSource().sendSuccess(() -> Component.literal("Ended " + n + " Galactus event(s)."), true);
				return n;
			}
			case "advancetimer" -> {
				ServerPlayer player = c.getSource().getPlayerOrException();
				GalactusEvent best = null;
				for (GalactusEvent e : GalactusEvent.all(server)) {
					if (e.isAt(player.serverLevel(), player.blockPosition(), 256)) {
						best = e;
					}
				}
				if (best == null) {
					c.getSource().sendFailure(Component.literal("No Galactus event near you."));
					return 0;
				}
				best.debugAdvance(player.serverLevel(), player);
				GalactusEvent.Phase phase = best.phase();
				c.getSource().sendSuccess(() -> Component.literal("Advanced Galactus (now: " + phase + ")."), true);
				return 1;
			}
			default -> {
				c.getSource().sendSuccess(() -> Component.literal("Galactus has no timer to remove."), false);
				return 0;
			}
		}
	}
}
