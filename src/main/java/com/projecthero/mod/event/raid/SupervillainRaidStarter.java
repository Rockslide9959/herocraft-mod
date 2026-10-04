package com.projecthero.mod.event.raid;

import java.util.UUID;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.event.EventInstance;
import com.projecthero.mod.event.EventManager;
import com.projecthero.mod.event.entity.PillagerSpy;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Turning a Pillager Spy's work into a running {@link SupervillainRaid}.
 *
 * <p>v0.14.21: the Spy marks the <em>player</em>, not the village. Its hit (anywhere) or its death at a player's
 * hands gives that player the Supervillain's Mark ({@link SupervillainMark}), the way a raid captain gave Bad Omen.
 * Carrying the mark into a village turns it into a 30-second omen, after which the raid starts there through
 * {@link #startFromOmen}. A missed bolt does nothing; an ordinary Pillager does nothing.
 */
public final class SupervillainRaidStarter {
	private SupervillainRaidStarter() {
	}

	/**
	 * Called from the shared damage listener when {@code player} is hurt. If the source is a Pillager Spy (or its
	 * bolt), the player is marked.
	 */
	public static void onPlayerDamaged(ServerPlayer player, Entity directSource, Entity attacker) {
		PillagerSpy spy = asSpy(directSource);
		if (spy == null) {
			spy = asSpy(attacker);
		}
		if (spy == null) {
			return;
		}
		if (!(player.level() instanceof ServerLevel level)) {
			return;
		}
		onSpyHitPlayer(level, spy, player);
	}

	private static PillagerSpy asSpy(Entity entity) {
		if (entity instanceof PillagerSpy spy) {
			return spy;
		}
		if (entity instanceof Projectile projectile && projectile.getOwner() instanceof PillagerSpy spy) {
			return spy;
		}
		return null;
	}

	/** v0.14.21: the spy has hit {@code player} -- mark the player, wherever they are standing. */
	public static void onSpyHitPlayer(ServerLevel level, PillagerSpy spy, ServerPlayer player) {
		if (SupervillainMark.mark(player)) {
			ProjectHeroMod.LOGGER.info("[SupervillainRaid] Pillager Spy {} marked {}", spy.getUUID(),
					player.getGameProfile().getName());
			// The spy has done its job: it stops chasing this player (its target filter skips marked players).
			if (spy.getTarget() == player) {
				spy.setTarget(null);
				spy.getNavigation().stop();
			}
		}
	}

	/** v0.14.21: a player killed a Pillager Spy -- like a raid captain's Bad Omen, the killer is marked. */
	public static void onSpyKilled(PillagerSpy spy, ServerPlayer killer) {
		if (SupervillainMark.mark(killer)) {
			ProjectHeroMod.LOGGER.info("[SupervillainRaid] {} killed Pillager Spy {} and was marked",
					killer.getGameProfile().getName(), spy.getUUID());
		}
	}

	/**
	 * v0.14.21: a marked player's omen ran out at {@code pos} -- start the Supervillain Raid there (the village is
	 * Marked for Attack and the raid's own preparation timer runs). Same start path the old village mark used, so the
	 * one-raid-per-area rule ({@code minDistanceBetweenEvents}) still applies.
	 *
	 * @return true if a raid started
	 */
	public static boolean startFromOmen(ServerLevel level, BlockPos pos) {
		BlockPos center = raidCenter(level, pos);
		if (SupervillainMark.raidBlocked(level, center)) {
			return false;
		}
		return markVillage(level, center);
	}

	/** @return true if a new raid record was created. */
	public static boolean markVillage(ServerLevel level, BlockPos center) {
		SupervillainRaid raid = new SupervillainRaid(UUID.randomUUID());
		return EventManager.start(level, raid, center);
	}

	/** Join an already-running raid, or start one, at the nearest village to {@code near} (debug command). */
	public static boolean startAt(ServerLevel level, BlockPos near) {
		BlockPos center = raidCenter(level, near);
		EventInstance existing = EventManager.at(level, center);
		if (existing instanceof SupervillainRaid) {
			return true;
		}
		return markVillage(level, center);
	}

	private static BlockPos raidCenter(ServerLevel level, BlockPos near) {
		int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, near.getX(), near.getZ());
		if (surface - near.getY() > com.projecthero.mod.event.EventConfig.framework().maxSpawnDistance) {
			return new BlockPos(near.getX(), surface, near.getZ());
		}
		return near;
	}
}
