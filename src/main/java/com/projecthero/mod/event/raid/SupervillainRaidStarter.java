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
 * Turning "a Pillager Spy landed a hit on a player standing in a village" into a marked village and a
 * running {@link SupervillainRaid}.
 *
 * <p>The trigger is strict (spec section 5): the spy must have <em>dealt damage</em> to a player who
 * is <em>inside a village</em>. A missed crossbow bolt does nothing; a normal Pillager does nothing;
 * a hit on a player standing outside the village does nothing.
 */
public final class SupervillainRaidStarter {
	private SupervillainRaidStarter() {
	}

	/**
	 * Called from the shared damage listener when {@code player} takes damage. Checks whether the
	 * source is a Pillager Spy and, if the conditions are met, marks the village.
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

	/** The spy has successfully damaged {@code player}. Mark the village if it is eligible. */
	public static void onSpyHitPlayer(ServerLevel level, PillagerSpy spy, ServerPlayer player) {
		BlockPos playerPos = player.blockPosition();
		if (!PillagerSpy.insideVillage(level, playerPos)) {
			return;
		}
		BlockPos center = raidCenter(level, playerPos);

		// Already marked / counting down / being raided here? A spy cannot stack a second timer.
		if (EventManager.anyActiveNear(level, center, SupervillainRaid.TYPE_ID,
				com.projecthero.mod.event.EventConfig.framework().minDistanceBetweenEvents)) {
			return;
		}
		// v0.12.23: no post-raid cooldown any more -- a village that has been raided before can be marked again.

		if (markVillage(level, center)) {
			ProjectHeroMod.LOGGER.info("[SupervillainRaid] Pillager Spy {} marked the village at {} (hit {})",
					spy.getUUID(), center, player.getGameProfile().getName());
			// The spy has done its job -- it flees.
			spy.getNavigation().stop();
			spy.setTarget(null);
		}
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
