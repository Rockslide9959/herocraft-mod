package com.projecthero.mod.doomsday;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.doomsday.block.DoomsdayBlocks;
import com.projecthero.mod.doomsday.entity.DoomsdayEntityTypes;
import com.projecthero.mod.event.EventConfig;
import com.projecthero.mod.event.EventInstance;
import com.projecthero.mod.event.EventManager;
import com.projecthero.mod.event.EventSavedData;
import com.projecthero.mod.event.EventTypes;
import com.projecthero.mod.galactus.GalactusTerrain;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;

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
 * v0.17.2: <b>Doomsday</b> -- one entry point, called from {@code ProjectHeroMod.onInitialize}: the entities, blocks and
 * items, the event type, the "a fighter died" hook and the natural impact roll. See {@link DoomsdayEvent} and
 * {@code docs/DOOMSDAY_DESIGN.md}.
 *
 * <p>Three ways in: the <b>Calatonian Casket</b> block (right-click it), {@code /projecthero raid start doomsday}, and the
 * <b>natural impact</b> -- once the world has driven off Galactus or won the Apokolips Invasion, a
 * {@link DoomsdayConfig.Trigger#chancePerDay} roll every night crashes a casket down near a random online player.
 */
public final class Doomsday {
	public enum Source { CASKET, COMMAND, IMPACT }

	private Doomsday() {
	}

	public static void initialize() {
		DoomsdayEntityTypes.initialize();
		DoomsdayBlocks.initialize();
		DoomsdayItems.initialize();
		DoomsdayRewards.initialize();
		EventTypes.register(DoomsdayEvent.TYPE_ID, DoomsdayEvent::new);
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer p) {
				for (DoomsdayEvent e : DoomsdayEvent.all(p.server)) {
					if (e.dimension() == p.level().dimension() && e.isFighter(p.getUUID())) {
						e.onFighterDied(p.serverLevel(), p);
					}
				}
			}
		});
	}

	public static void serverTick(MinecraftServer server) {
		if (server.getTickCount() % 20 == 7) {
			rollImpact(server);
		}
	}

	/** Starts Doomsday centred on {@code at}. Null when switched off, on Peaceful, or if one is already running too close. */
	public static DoomsdayEvent start(ServerLevel level, BlockPos at, ServerPlayer activator, Source source, float yaw) {
		return start(level, at, activator, source, yaw, null);
	}

	public static DoomsdayEvent start(ServerLevel level, BlockPos at, ServerPlayer activator, Source source, float yaw,
			java.util.function.Consumer<DoomsdayEvent> before) {
		if (!DoomsdayConfig.trigger().enabled || level.getDifficulty() == Difficulty.PEACEFUL) {
			return null;
		}
		if (EventManager.anyActiveNear(level, at, DoomsdayEvent.TYPE_ID, EventConfig.framework().minDistanceBetweenEvents)) {
			return null;
		}
		DoomsdayEvent e = new DoomsdayEvent(UUID.randomUUID());
		if (!EventManager.start(level, e, at)) {
			return null;
		}
		if (before != null) {
			before.accept(e);
		}
		e.activate(level, activator, source == Source.CASKET, yaw);
		return e;
	}

	/** Every night, once the world earned it: a {@code chancePerDay} roll for a casket impact. */
	private static void rollImpact(MinecraftServer server) {
		DoomsdayConfig.Trigger cfg = DoomsdayConfig.trigger();
		ServerLevel level = server.overworld();
		if (!cfg.enabled || !cfg.naturalTriggerEnabled || level.getDifficulty() == Difficulty.PEACEFUL || !level.isNight()) {
			return;
		}
		long day = level.getDayTime() / 24000L;
		DoomsdayWorldState ws = DoomsdayWorldState.get(server);
		if (day == ws.lastRollDay) {
			return;
		}
		ws.lastRollDay = day;
		ws.setDirty();
		if (!DoomsdayWorldState.unlocked(server) || !DoomsdayEvent.all(server).isEmpty() || level.random.nextDouble() >= cfg.chancePerDay) {
			return;
		}
		startImpact(level);
	}

	/** A casket crashes down near a random eligible player. Null if nobody / nowhere fits. */
	public static DoomsdayEvent startImpact(ServerLevel level) {
		List<ServerPlayer> eligible = new ArrayList<>();
		for (ServerPlayer p : level.players()) {
			if (DoomsdayEvent.isFighterPlayer(p) && EventManager.forPlayer(p) == null) {
				eligible.add(p);
			}
		}
		if (eligible.isEmpty()) {
			return null;
		}
		ServerPlayer p = eligible.get(level.random.nextInt(eligible.size()));
		DoomsdayConfig.Trigger cfg = DoomsdayConfig.trigger();
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
			BlockState below = level.getBlockState(at.below());
			if (!level.getBlockState(at).canBeReplaced() || below.liquid() || GalactusTerrain.nearBlockEntity(level, at, 6)) {
				continue;
			}
			DoomsdayEvent e = start(level, at, null, Source.IMPACT, level.random.nextFloat() * 360f);
			if (e == null) {
				return null;
			}
			for (ServerPlayer near : level.players()) {
				if (near.distanceToSqr(p) < 200 * 200) {
					near.sendSystemMessage(Component.translatable("message.projecthero.doomsday.impact").withStyle(ChatFormatting.DARK_RED));
				}
			}
			ProjectHeroMod.LOGGER.info("[ProjectHero] A Calatonian casket fell near {}", p.getGameProfile().getName());
			return e;
		}
		return null;
	}

	public static int endAll(MinecraftServer server) {
		int n = 0;
		for (EventInstance e : EventManager.active(server)) {
			ServerLevel level = e.dimension() == null ? null : server.getLevel(e.dimension());
			if (level != null && e instanceof DoomsdayEvent) {
				e.abort(level);
				EventSavedData.get(level).remove(e.id());
				n++;
			}
		}
		return n;
	}

	/**
	 * {@code /projecthero raid <verb> doomsday}: {@code start} crashes a casket down where you stand; {@code end} stops
	 * every one; {@code advancetimer} moves the nearest one on (the casket opens -> this life ends -> he revives ... -> with
	 * the Projector in your hands after the third death, he is banished).
	 */
	public static int command(CommandContext<CommandSourceStack> c, String verb) throws CommandSyntaxException {
		MinecraftServer server = c.getSource().getServer();
		switch (verb) {
			case "start" -> {
				ServerPlayer player = c.getSource().getPlayerOrException();
				ServerLevel level = player.serverLevel();
				if (level.getDifficulty() == Difficulty.PEACEFUL) {
					c.getSource().sendFailure(Component.literal("Doomsday never comes on Peaceful."));
					return 0;
				}
				if (!DoomsdayConfig.trigger().enabled) {
					c.getSource().sendFailure(Component.literal("Doomsday is switched off (config/projecthero_doomsday.json, trigger.enabled)."));
					return 0;
				}
				BlockPos at = player.blockPosition().relative(player.getDirection(), 12);
				at = new BlockPos(at.getX(), level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ()), at.getZ());
				if (start(level, at, player, Source.COMMAND, player.getYRot() + 180f) == null) {
					c.getSource().sendFailure(Component.literal("Could not start Doomsday here (one already running nearby?)."));
					return 0;
				}
				c.getSource().sendSuccess(() -> Component.literal("Doomsday: a casket is falling."), true);
				return 1;
			}
			case "end" -> {
				int n = endAll(server);
				c.getSource().sendSuccess(() -> Component.literal("Ended " + n + " Doomsday event(s)."), true);
				return n;
			}
			case "advancetimer" -> {
				ServerPlayer player = c.getSource().getPlayerOrException();
				DoomsdayEvent best = null;
				for (DoomsdayEvent e : DoomsdayEvent.all(server)) {
					if (e.isAt(player.serverLevel(), player.blockPosition(), 256)) {
						best = e;
					}
				}
				if (best == null) {
					c.getSource().sendFailure(Component.literal("No Doomsday event near you."));
					return 0;
				}
				best.debugAdvance(player.serverLevel(), player);
				DoomsdayEvent.Phase phase = best.phase();
				int deaths = best.deaths();
				c.getSource().sendSuccess(() -> Component.literal("Advanced Doomsday (now: " + phase + ", deaths " + deaths + ")."), true);
				return 1;
			}
			default -> {
				c.getSource().sendSuccess(() -> Component.literal("Doomsday has no timer to remove."), false);
				return 0;
			}
		}
	}
}
