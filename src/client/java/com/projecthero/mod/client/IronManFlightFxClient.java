package com.projecthero.mod.client;

import com.projecthero.mod.attachment.ModAttachments;

import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * The hand-repulsor exhaust while an Iron Man player is <b>sprint</b>-flying -- a visible "going
 * faster" cue. Client-side on purpose: the particles are placed at the actual rendered hand position,
 * which is a function of the same flight lean {@link FlightPoseHelper} drives.
 *
 * <h2>Where the hand actually is ("changes 17" -- derived end to end, not guessed)</h2>
 * During Iron Man sprint flight the arms are pinned straight down at the sides
 * ({@code HumanoidModelMixin}'s hero-flight branch) and the whole body is then tilted forward by
 * {@code PlayerRendererMixin}'s {@code Axis.XP.rotationDegrees(-lean)} about the model origin (the
 * entity's feet). Pushing a standing hand -- model-local {@code (±5, 12, 0)} px, i.e. {@code ±0.31}
 * blocks out and {@code 0.75} blocks below the shoulder pivot, which sits at the feet frame after the
 * {@code scale(-1,-1,1)} / {@code translate(0,-1.501,0)} the renderer applies -- through that same X
 * rotation gives, relative to the feet:
 * <pre>
 *   hand = feet  +  right * (±halfWidth)
 *               +  up      * (armDrop * cos(lean))
 *               +  forward * (armDrop * sin(lean))
 * </pre>
 * so the hands hang straight down while hovering and swing forward to feet-height, ~0.75 blocks
 * ahead, as the body lays out flat into the superman pose. Scaled by {@link Player#getScale()} so a
 * bigger suit (Mark 1) still lines up.
 */
public final class IronManFlightFxClient {
	/** Shoulder half-width, blocks (model x = 5 px). */
	private static final double HALF_WIDTH = 0.32;
	/** Hand drop below the shoulder with the arm straight down, blocks (model 12 px). */
	private static final double ARM_DROP = 0.74;

	private IronManFlightFxClient() {
	}

	public static void clientTick(Minecraft client) {
		if (client.level == null || client.player == null) {
			return;
		}
		for (Player player : client.level.players()) {
			if (!player.getAttachedOrElse(ModAttachments.IRON_MAN_FLYING, false) || !player.isSprinting()) {
				continue;
			}
			emitHandExhaust(client, player);
		}
	}

	private static void emitHandExhaust(Minecraft client, Player player) {
		float pt = client.getTimer().getGameTimeDeltaPartialTick(false);
		double lean = Math.toRadians(FlightPoseHelper.lean(player, pt));
		double cos = Math.cos(lean);
		double sin = Math.sin(lean);

		// Frame that PlayerRendererMixin's lean rotation acts in: origin at the feet, -Z the facing
		// direction. forward / right / up in world space from the interpolated body yaw.
		double phi = Math.toRadians(Mth.rotLerp(pt, player.yBodyRotO, player.yBodyRot));
		Vec3 forward = new Vec3(-Math.sin(phi), 0, Math.cos(phi));
		Vec3 right = new Vec3(-Math.cos(phi), 0, -Math.sin(phi));

		double scale = player.getScale();
		double halfWidth = HALF_WIDTH * scale;
		double armDrop = ARM_DROP * scale;

		double fx = Mth.lerp((double) pt, player.xo, player.getX());
		double fy = Mth.lerp((double) pt, player.yo, player.getY());
		double fz = Mth.lerp((double) pt, player.zo, player.getZ());

		Vec3 washV = forward.scale(-0.05);
		for (int s = -1; s <= 1; s += 2) {
			double hx = fx + right.x * halfWidth * s + forward.x * (armDrop * sin);
			double hy = fy + armDrop * cos;
			double hz = fz + right.z * halfWidth * s + forward.z * (armDrop * sin);
			client.level.addParticle(ParticleTypes.FLAME, hx, hy, hz, washV.x, washV.y - 0.02, washV.z);
			if (client.level.random.nextBoolean()) {
				client.level.addParticle(ParticleTypes.END_ROD, hx, hy, hz, washV.x, washV.y, washV.z);
			}
			client.level.addParticle(ParticleTypes.SMOKE, hx - forward.x * 0.12, hy, hz - forward.z * 0.12,
					washV.x, washV.y, washV.z);
		}
	}
}
