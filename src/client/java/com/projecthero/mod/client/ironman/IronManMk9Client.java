package com.projecthero.mod.client.ironman;

import com.projecthero.mod.ironman.IronManArmor;
import com.projecthero.mod.ironman.ability.IronManMark9;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.7 (user) Era II stage 3: the Mk IX Inferno's client side.
 * <ul>
 *   <li><b>Lava swimming</b>: with all four pieces on (the suit is fire / lava proof since stage 1), the local player
 *       swims through lava at about {@link #LAVA_SWIM_SPEED} blocks a tick (~8 blocks/s) along the look direction and the
 *       movement keys, Space up and Shift down; with no keys held it hangs in the lava (slight lift, heavy damping).
 *       Movement is client-authoritative, so this runs at the start of the client tick, before vanilla's lava travel
 *       (which adds its own 0.02 and halves the velocity at the end of the tick).</li>
 *   <li><b>Lava visor</b>: {@link #lavaVisor} -- the lava fog pushed out to 24 blocks ({@code FogRendererMixin}).</li>
 * </ul>
 */
public final class IronManMk9Client {
	/** Blocks per tick through lava (the vanilla swim is ~0.02 per tick of input, halved every tick). */
	public static final double LAVA_SWIM_SPEED = 0.42;
	public static final float LAVA_FOG_END = 24.0f;

	private IronManMk9Client() {
	}

	public static void initialize() {
		ClientTickEvents.START_CLIENT_TICK.register(IronManMk9Client::tick);
	}

	/** True when {@code player} wears the whole Mk IX (its lava immunity, and so its lava swim / visor, needs all four). */
	public static boolean fullInferno(Player player) {
		return player != null && IronManMark9.SUIT.equals(IronManArmor.wornSuitId(player))
				&& IronManArmor.wearingFullSuit(player, IronManMark9.SUIT);
	}

	/** The lava fog end for the camera's player, or 0 when the visor is not on. */
	public static float lavaVisor(Player viewer) {
		return fullInferno(viewer) ? LAVA_FOG_END : 0f;
	}

	private static void tick(Minecraft mc) {
		LocalPlayer pl = mc.player;
		if (pl == null || mc.isPaused() || pl.isSpectator() || pl.getAbilities().flying || !pl.isInLava() || !fullInferno(pl)) {
			return;
		}
		Vec3 v = pl.getDeltaMovement();
		if (v.y > LAVA_SWIM_SPEED * 1.4) {
			// v0.16.7 (user): something is throwing us up faster than any swim (the Lava Geyser drive) -- don't damp it
			pl.fallDistance = 0f;
			return;
		}
		float fwd = pl.input.forwardImpulse;
		float left = pl.input.leftImpulse;
		double up = (pl.input.jumping ? 1.0 : 0.0) - (pl.input.shiftKeyDown ? 1.0 : 0.0);
		float yaw = pl.getYRot() * Mth.DEG_TO_RAD;
		Vec3 look = pl.getLookAngle();
		Vec3 leftDir = new Vec3(Mth.cos(yaw), 0, Mth.sin(yaw));
		Vec3 wish = look.scale(fwd).add(leftDir.scale(left)).add(0, up, 0);
		if (wish.lengthSqr() < 0.0025) {
			// no input: hang in the lava (the vanilla sink is cancelled, motion damped)
			pl.setDeltaMovement(v.x * 0.5, v.y * 0.5 + 0.01, v.z * 0.5);
			return;
		}
		Vec3 target = wish.normalize().scale(LAVA_SWIM_SPEED * (pl.isSprinting() ? 1.25 : 1.0));
		pl.setDeltaMovement(v.lerp(target, 0.55));
		pl.fallDistance = 0f;
		if (pl.tickCount % 3 == 0 && pl.level() != null) {
			// a wake of heat shimmer / embers behind the suit
			Vec3 back = pl.position().add(0, 0.5, 0).subtract(target.normalize().scale(0.6));
			pl.level().addParticle(ParticleTypes.LAVA, back.x, back.y, back.z, 0, 0, 0);
			pl.level().addParticle(ParticleTypes.FLAME, back.x, back.y + 0.3, back.z, 0, 0.02, 0);
		}
	}
}
