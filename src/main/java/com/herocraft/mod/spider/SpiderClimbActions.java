package com.herocraft.mod.spider;

import com.herocraft.mod.attachment.ModAttachments;
import com.herocraft.mod.spider.data.SpiderClimbLocal;
import com.herocraft.mod.spider.data.SpiderManState;

import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Server-side actions taken <em>against</em> an existing surface attachment. Kept out of
 * {@link SpiderClimb} so that class stays purely about detecting and holding a surface.
 */
public final class SpiderClimbActions {
	/** Ticks the adhesion engine refuses to re-grab after a deliberate leap. */
	private static final int LEAP_LOCKOUT = 7;

	private SpiderClimbActions() {
	}

	/**
	 * Push off the held surface. Available to Spider Adhesion as well as Spider-Man -- launching away
	 * from a wall is part of what makes adhesion a traversal power rather than a way to hang around.
	 *
	 * <p>The brief lock-out afterwards is the point: without it the engine would simply re-attach on
	 * the very next tick, and the leap would look like a twitch.
	 */
	public static boolean leap(ServerPlayer player) {
		SpiderClimb.Profile profile = SpiderClimb.profile(player);
		if (profile == null) {
			return false;
		}
		SpiderClimbLocal local = player.getAttachedOrCreate(ModAttachments.SPIDER_CLIMB_LOCAL);
		Direction face = local.face();
		if (face == null) {
			return false;
		}
		Vec3 normal = Vec3.atLowerCornerOf(face.getOpposite().getNormal());
		local.lockOut(LEAP_LOCKOUT);
		// A deliberate leap ends the grab -- coming back onto the wall means a fresh double-tap-jump.
		local.setGrabIntent(false);

		SpiderManState s = player.getAttachedOrElse(ModAttachments.SPIDER_MAN_STATE, null);
		if (s != null && s.climbState != 0) {
			SpiderManState c = s.copy();
			c.climbState = 0;
			SpiderMan.save(player, c);
		}
		SpiderAbilities.leapFromSurface(player, normal);
		return true;
	}
}
