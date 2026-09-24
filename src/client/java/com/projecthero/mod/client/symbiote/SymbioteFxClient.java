package com.projecthero.mod.client.symbiote;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.symbiote.SymbioteState;
import com.projecthero.mod.symbiote.SymbioteTransform;

import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * The Symbiote transformation effect, entirely client-side. Reads the same synced
 * {@link SymbioteState} / {@link SymbioteTransform} clock {@code SymbioteReveal} uses for the bone
 * reveal, so the particles always agree with what is actually about to appear: dark tendrils creep
 * over the <b>chest</b> first, then <b>both arms and both legs</b> together, then finally the
 * <b>head</b> -- each region gets its own dense, localised cloud rather than one uniform sweep, so it
 * reads as the Symbiote spreading limb by limb rather than a generic particle wash.
 *
 * <p>Purely cosmetic -- the suit itself is already equipped server-side by the time this runs; this
 * only decides which bones are hidden ({@code SymbioteReveal}) and what it looks like while they are.
 * Vanilla particles only ({@link ParticleTypes#SQUID_INK}, {@code SMOKE}, {@code LARGE_SMOKE},
 * {@code REVERSE_PORTAL}), a small fixed per-region budget, and only for players within ~24 blocks.
 */
public final class SymbioteFxClient {
	private static final java.util.Random RNG = new java.util.Random();

	/** Effective-progress boundaries between the three visual stages (matches {@code SymbioteReveal}'s
	 *  own thresholds closely enough that the particles are covering the part that is about to appear,
	 *  with a little deliberate overlap/anticipation into the next stage). */
	private static final float STAGE_CHEST_END = 0.32f;
	private static final float STAGE_LIMBS_END = 0.72f;

	/**
	 * v0.11.16: N toggles the Symbiote host's Predator Vision (the mob / player outline) on and off. Purely a
	 * client-side view preference -- the outline is this viewer's own render, nothing is sent to the server.
	 */
	private static boolean predatorVision = true;

	private SymbioteFxClient() {
	}

	public static boolean predatorVisionOn() {
		return predatorVision;
	}

	public static boolean togglePredatorVision() {
		predatorVision = !predatorVision;
		return predatorVision;
	}

	/**
	 * v0.11.9: the idle "shoulder wisp" ambient particle loop that used to play the whole time the suit
	 * was worn is gone -- explicit user request ("since symbiote armour has a model now remove the
	 * particles that spawn with the armour"). It existed only because the Normal Symbiote Host's suit
	 * textures were fully transparent back in v0.9.19 and needed *some* visual signal that the suit was
	 * actually on; a real GeckoLib model has carried that job since. The transformation sequence's own
	 * particles ({@link #emit}, while {@link SymbioteTransform#isAnimating} is true) are unrelated and
	 * unchanged -- those play the tendrils-creeping-over-the-body effect during suit-up/suit-down itself,
	 * not a persistent effect "with the armour".
	 */
	public static void clientTick(Minecraft client) {
		if (client.level == null || client.player == null) {
			return;
		}
		Vec3 eye = client.player.getEyePosition();
		for (Player player : client.level.players()) {
			SymbioteState s = player.getAttachedOrElse(ModAttachments.SYMBIOTE_STATE, null);
			if (s == null || !SymbioteTransform.isAnimating(s) || player.distanceToSqr(eye) > 24 * 24) {
				continue;
			}
			float effective = SymbioteTransform.effectiveProgress(s, player.level().getGameTime());
			emit(client, player, effective);
		}
	}

	private static void emit(Minecraft client, Player player, float effective) {
		Vec3 feet = player.position();
		double h = player.getBbHeight();
		double w = player.getBbWidth();
		Vec3 look = player.getLookAngle();
		Vec3 right = new Vec3(-look.z, 0, look.x).normalize();

		// A faint cloud tracing the whole silhouette every tick, so the suit reads as creeping over the
		// entire body rather than only the one region currently being sealed -- the dense stage cloud
		// below is layered on top of this, not instead of it.
		cloud(client, feet.add(0, h * 0.55, 0), w * 0.60, h * 0.5, 6);

		if (effective < STAGE_CHEST_END) {
			cloud(client, feet.add(0, h * 0.62, 0), w * 0.70, h * 0.26, 16);
		} else if (effective < STAGE_LIMBS_END) {
			cloud(client, feet.add(right.scale(w * 0.55)).add(0, h * 0.68, 0), w * 0.32, h * 0.22, 8);
			cloud(client, feet.add(right.scale(-w * 0.55)).add(0, h * 0.68, 0), w * 0.32, h * 0.22, 8);
			cloud(client, feet.add(right.scale(w * 0.24)).add(0, h * 0.24, 0), w * 0.32, h * 0.26, 8);
			cloud(client, feet.add(right.scale(-w * 0.24)).add(0, h * 0.24, 0), w * 0.32, h * 0.26, 8);
		} else {
			cloud(client, feet.add(0, h * 0.94, 0), w * 0.55, h * 0.20, 14);
		}
	}

	private static void cloud(Minecraft client, Vec3 center, double radius, double halfHeight, int count) {
		var level = client.level;
		for (int i = 0; i < count; i++) {
			double ang = RNG.nextDouble() * Math.PI * 2;
			double r = RNG.nextDouble() * radius;
			double px = center.x + Math.cos(ang) * r;
			double pz = center.z + Math.sin(ang) * r;
			double py = center.y + (RNG.nextDouble() * 2 - 1) * halfHeight;
			ParticleOptions type = switch (i % 3) {
				case 0 -> ParticleTypes.SQUID_INK;
				case 1 -> ParticleTypes.SMOKE;
				default -> ParticleTypes.REVERSE_PORTAL;
			};
			level.addParticle(type, px, py, pz,
					(RNG.nextDouble() - 0.5) * 0.02, 0.01, (RNG.nextDouble() - 0.5) * 0.02);
		}
	}
}
