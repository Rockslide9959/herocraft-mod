package com.projecthero.mod.andy;

import com.projecthero.mod.andy.data.AndyState;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.3 (tester: "A way people can naturally pick up the head"): while Andy is down to his head (no body), anyone can pick him
 * up -- right-click him with an empty main hand -- and carry him about in front of them, tucked under the arm. He climbs down
 * with Sneak, or by himself once his body has grown back ({@link Andy#tick}). Vanilla riding does the syncing, so it holds up in
 * multiplayer; {@code EntityAndyCarryMixin} places the seat (both sides).
 */
public final class AndyCarry {
	private AndyCarry() {
	}

	/** Andy is only a head (or a head on a regrowing stump): small enough to carry. Client-safe. */
	public static boolean carriable(Entity e) {
		return e instanceof Player p && Andy.hasPower(p) && Andy.missing(p, AndyState.BODY) && !Andy.missing(p, AndyState.HEAD);
	}

	/** From the UseEntityCallback: {@code carrier} right-clicked {@code target}. */
	public static InteractionResult tryPickUp(Player carrier, InteractionHand hand, Entity target) {
		if (hand != InteractionHand.MAIN_HAND || !carriable(target) || carrier == target || !carrier.getMainHandItem().isEmpty()
				|| carrier.isPassenger() || target.isPassenger() || carrier.isSpectator() || carriable(carrier)) {
			return InteractionResult.PASS;
		}
		if (!(carrier instanceof ServerPlayer c) || !(target instanceof ServerPlayer head)) {
			return InteractionResult.SUCCESS; // the client: the server decides
		}
		if (!head.startRiding(c, true)) {
			return InteractionResult.PASS;
		}
		c.level().playSound(null, c.getX(), c.getY() + 1.0, c.getZ(), SoundEvents.SLIME_SQUISH_SMALL, SoundSource.PLAYERS, 0.9f, 0.9f);
		return InteractionResult.SUCCESS;
	}

	/** The seat: in front of the carrier's chest, on his main-hand side, turned with his body. */
	public static Vec3 seat(Entity carrier) {
		float yaw = carrier instanceof LivingEntity l ? l.yBodyRot : carrier.getYRot();
		double side = carrier instanceof Player p && p.getMainArm() == net.minecraft.world.entity.HumanoidArm.LEFT ? 1.0 : -1.0;
		return new Vec3(0.32 * side, 0.82, 0.42).yRot(-yaw * Mth.DEG_TO_RAD);
	}
}
