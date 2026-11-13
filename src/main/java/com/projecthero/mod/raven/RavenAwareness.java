package com.projecthero.mod.raven;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;

/**
 * v0.17.3 Raven, passive <b>Mystical Awareness</b>: every 8 s, for 2 s, every hostile creature within 24 blocks of her glows
 * through walls -- on her screen only (the client's {@code RavenAwarenessGlowMixin} asks {@link #glows} for the local player, so
 * nobody else sees the outlines and nothing is sent). Pure rules on the game clock, so the gametests can check them too.
 */
public final class RavenAwareness {
	private RavenAwareness() {
	}

	/** The pulse is showing at {@code gameTime} (the first 2 s of every 8). */
	public static boolean pulse(long gameTime) {
		return Math.floorMod(gameTime, RavenConfig.AWARE_PERIOD) < RavenConfig.AWARE_TICKS;
	}

	/** {@code e} is a creature Raven {@code viewer} senses right now. */
	public static boolean glows(Player viewer, Entity e) {
		if (viewer == null || e == viewer || !(e instanceof LivingEntity le) || !le.isAlive() || !(e instanceof Enemy)
				|| !Raven.hasPower(viewer) || !pulse(viewer.level().getGameTime())) {
			return false;
		}
		return e.distanceToSqr(viewer) <= RavenConfig.AWARE_RADIUS * RavenConfig.AWARE_RADIUS;
	}
}
