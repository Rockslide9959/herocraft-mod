package com.projecthero.mod.client.quirk.explosion;

import static com.projecthero.mod.client.quirk.QuirkKeyframes.f;
import static com.projecthero.mod.client.quirk.QuirkKeyframes.rest;

import com.projecthero.mod.client.HipBend;
import com.projecthero.mod.client.quirk.QuirkKeyframes;
import com.projecthero.mod.quirk.Quirks;
import com.projecthero.mod.quirk.data.QuirkState;
import com.projecthero.mod.quirk.explosion.Explosion;
import com.projecthero.mod.quirk.explosion.ExplosionConfig;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * v0.16.7 (revamp): Bakugo's poses on the vanilla humanoid model, set at the end of {@code setupAnim}. Two layers:
 * <ul>
 *   <li><b>Held states</b> (procedural, from the synced timers): Explosion Propulsion's flight (leaning into it, both palms
 *       thrown back blasting, legs trailing), the Howitzer (arms flung straight out, legs together, while the whole body
 *       spins -- {@link #bodyTurn}), Cool Off's arm shake, Arm Strain's limp, shaking arms.</li>
 *   <li><b>The moves</b> (keyframed, {@link QuirkKeyframes}, ids 120..139): AP Shot (the palm snapped out at the aim,
 *       recoil), the Auto-Cannon (palms pumping in turn), the Explosive Punch (cocked back, driven through with a body
 *       twist), Blast Rush (both palms raised and slammed at the ground), the Stun Grenade (palms together at the face, then
 *       flung wide in the flash), Cool Off, the Explosion Dash, the Cluster (arms swept out all round), the Gauntlet Grenade
 *       (the left hand pulls the pin on the right gauntlet, aim, then the recoil throws him back).</li>
 * </ul>
 * Aimed moves add the look pitch to the shooting arm so the palm points where he looks.
 */
public final class ExplosionPose {
	//                                        tick  rArm x y z          lArm x y z          body x y        rLeg x y z           lLeg x y z           head
	/** R: AP Shot -- the right palm snapped out at the aim, the left hand bracing the wrist, a kick of recoil. */
	static final float[][] AP_SHOT = {
			rest(0),
			f(2, -1.6f, -0.1f, 0, -1.2f, 0.55f, 0, 0.05f, -0.3f, -0.2f, 0, 0, 0.25f, 0, 0, 0),
			f(4, -2.0f, -0.1f, 0, -1.4f, 0.55f, 0, -0.1f, -0.25f, -0.2f, 0, 0, 0.25f, 0, 0, 0),
			f(7, -1.6f, -0.1f, 0, -1.2f, 0.55f, 0, 0.0f, -0.3f, -0.2f, 0, 0, 0.25f, 0, 0, 0),
			rest(11) };
	/** Shift+R: AP Shot: Auto-Cannon -- both palms out, pumping in turn as the shots go. */
	static final float[][] AUTO = autoFrames();
	/** G: Explosive Punch -- cocked back with a twist, then driven through, the blast at the knuckles. */
	static final float[][] PUNCH = {
			rest(0),
			f(2, 0.8f, 0.4f, 0.15f, -0.9f, 0, -0.2f, 0.1f, 0.5f, 0.25f, 0, 0, -0.3f, 0, 0, 0),
			f(4, -1.65f, -0.1f, 0.05f, 0.5f, 0, -0.25f, 0.35f, -0.5f, -0.45f, 0, 0, 0.45f, 0, 0, 0.05f),
			f(7, -1.6f, -0.1f, 0.05f, 0.5f, 0, -0.25f, 0.3f, -0.45f, -0.45f, 0, 0, 0.45f, 0, 0, 0.05f),
			rest(11) };
	/** Shift+G: Blast Rush -- both palms raised high, then slammed at the ground as the blasts march away. */
	static final float[][] BLAST_RUSH = {
			rest(0),
			f(3, -2.9f, 0, 0.25f, -2.9f, 0, -0.25f, -0.15f, 0, 0.1f, 0, 0.05f, -0.1f, 0, -0.05f, -0.3f),
			f(5, -0.55f, 0, 0.35f, -0.55f, 0, -0.35f, 0.75f, 0, -0.75f, 0, 0.1f, 0.35f, 0, -0.1f, 0.35f),
			f(13, -0.55f, 0, 0.35f, -0.55f, 0, -0.35f, 0.7f, 0, -0.7f, 0, 0.1f, 0.35f, 0, -0.1f, 0.3f),
			rest(17) };
	/** V: Stun Grenade -- palms pressed together before the face, then flung wide in the flash, head turned from it. */
	static final float[][] STUN = {
			rest(0),
			f(3, -1.6f, 0.6f, 0, -1.6f, -0.6f, 0, 0.1f, 0, -0.15f, 0, 0, 0.15f, 0, 0, 0.1f),
			f(5, -1.3f, -0.5f, 0.7f, -1.3f, 0.5f, -0.7f, -0.15f, 0, 0.1f, 0, 0.1f, -0.1f, 0, -0.1f, 0.3f),
			f(10, -1.2f, -0.5f, 0.75f, -1.2f, 0.5f, -0.75f, -0.1f, 0, 0.1f, 0, 0.1f, -0.1f, 0, -0.1f, 0.2f),
			rest(15) };
	/** Shift+V: Cool Off -- arms dropped and shaken out at his sides, head back, the sweat steaming off. */
	static final float[][] COOL = {
			rest(0),
			f(4, 0.2f, 0, 0.45f, 0.2f, 0, -0.45f, -0.08f, 0, 0, 0, 0.08f, 0, 0, -0.08f, -0.35f),
			f(ExplosionConfig.COOL_TICKS - 4, 0.2f, 0, 0.45f, 0.2f, 0, -0.45f, -0.08f, 0, 0, 0, 0.08f, 0, 0, -0.08f, -0.35f),
			rest(ExplosionConfig.COOL_TICKS) };
	/** X: Propulsion take-off -- palms slammed back and down. (The flight itself is procedural.) */
	static final float[][] PROPEL = {
			rest(0),
			f(2, 0.7f, 0, 0.5f, 0.7f, 0, -0.5f, 0.2f, 0, -0.6f, 0, 0, -0.5f, 0, 0, -0.2f),
			f(6, 0.9f, 0, 0.4f, 0.9f, 0, -0.4f, 0.3f, 0, 0.2f, 0, 0, 0.4f, 0, 0, -0.3f) };
	/** Shift+X: Explosion Dash -- thrown forward off one back-blast: arms flung back, leaning in, legs trailing. */
	static final float[][] DASH = {
			rest(0),
			f(2, 1.25f, 0, 0.3f, 1.25f, 0, -0.3f, 0.55f, 0, 0.5f, 0, 0, 0.2f, 0, 0, -0.45f),
			f(8, 1.15f, 0, 0.35f, 1.15f, 0, -0.35f, 0.5f, 0, 0.45f, 0, 0, 0.15f, 0, 0, -0.4f),
			rest(12) };
	/** Z: Cluster -- crouched, arms swept out wide and low all round as the rings go off. */
	static final float[][] CLUSTER = {
			rest(0),
			f(2, -0.6f, 0, 0.2f, -0.6f, 0, -0.2f, 0.35f, 0, -0.35f, 0, 0.1f, 0.2f, 0, -0.1f, 0.1f),
			f(4, 0.1f, 0, 1.3f, 0.1f, 0, -1.3f, 0.2f, 0, -0.2f, 0, 0.2f, 0.2f, 0, -0.2f, 0),
			f(8, 0.1f, 0, 1.35f, 0.1f, 0, -1.35f, 0.2f, 0, -0.2f, 0, 0.2f, 0.2f, 0, -0.2f, 0),
			rest(12) };
	/** Shift+C: Gauntlet Grenade -- the left hand pulls the pin on the right gauntlet, the arm levels at the aim and fills, then the recoil. */
	static final float[][] GAUNTLET = {
			rest(0),
			f(3, -1.2f, -0.3f, 0, -1.25f, 0.75f, 0, 0.1f, 0.25f, -0.2f, 0, 0, 0.3f, 0, 0, 0.15f),
			f(6, -1.45f, -0.05f, 0, -0.8f, 0.9f, -0.4f, 0.1f, 0.35f, -0.45f, 0, 0.05f, 0.45f, 0, -0.05f, 0),
			f(ExplosionConfig.GAUNTLET_CHARGE, -1.5f, -0.05f, 0, -1.3f, 0.6f, 0, 0.12f, 0.3f, -0.45f, 0, 0.05f, 0.45f, 0, -0.05f, 0),
			f(ExplosionConfig.GAUNTLET_CHARGE + 2, -2.5f, 0, 0.2f, -2.2f, 0.4f, 0, -0.4f, 0.2f, -0.7f, 0, 0.1f, 0.3f, 0, -0.1f, -0.25f),
			f(ExplosionConfig.GAUNTLET_CHARGE + 7, -1.9f, 0, 0.2f, -1.6f, 0.3f, 0, -0.25f, 0.1f, -0.5f, 0, 0.1f, 0.3f, 0, -0.1f, -0.15f),
			rest(ExplosionConfig.GAUNTLET_CHARGE + 13) };
	/** Arm Strain: the arms hang limp and shake, shoulders slumped (the shake is added in {@link #apply}). */
	static final float[][] STRAIN = {
			rest(0),
			f(4, 0.2f, 0, 0.08f, 0.2f, 0, -0.08f, 0.3f, 0, 0, 0, 0, 0, 0, 0, 0.4f),
			f(ExplosionConfig.STRAIN_TICKS - 5, 0.2f, 0, 0.08f, 0.2f, 0, -0.08f, 0.25f, 0, 0, 0, 0, 0, 0, 0, 0.35f),
			rest(ExplosionConfig.STRAIN_TICKS) };

	private ExplosionPose() {
	}

	private static float[][] autoFrames() {
		int shots = ExplosionConfig.AUTO_SHOTS;
		int iv = ExplosionConfig.AUTO_INTERVAL;
		float[][] out = new float[shots + 3][];
		out[0] = rest(0);
		out[1] = f(1, -1.5f, -0.15f, 0, -1.5f, 0.15f, 0, 0.1f, 0, -0.25f, 0, 0, 0.3f, 0, 0, 0);
		for (int i = 0; i < shots; i++) {
			boolean right = i % 2 == 0;
			out[2 + i] = f(2 + i * iv, right ? -1.85f : -1.45f, -0.15f, 0, right ? -1.45f : -1.85f, 0.15f, 0, 0.1f, right ? -0.12f : 0.12f, -0.25f, 0, 0, 0.3f,
					0, 0, 0);
		}
		out[shots + 2] = rest(2 + shots * iv + 5);
		return out;
	}

	static float[][] frames(int animId) {
		return switch (animId) {
			case Explosion.ANIM_AP_SHOT -> AP_SHOT;
			case Explosion.ANIM_AUTO -> AUTO;
			case Explosion.ANIM_PUNCH -> PUNCH;
			case Explosion.ANIM_BLAST_RUSH -> BLAST_RUSH;
			case Explosion.ANIM_STUN -> STUN;
			case Explosion.ANIM_COOL -> COOL;
			case Explosion.ANIM_PROPEL -> PROPEL;
			case Explosion.ANIM_DASH -> DASH;
			case Explosion.ANIM_CLUSTER -> CLUSTER;
			case Explosion.ANIM_GAUNTLET -> GAUNTLET;
			case Explosion.ANIM_STRAIN -> STRAIN;
			default -> null;
		};
	}

	/** Test / harness: the pose exists for this id (the two Howitzers are procedural + {@link #bodyTurn}). */
	public static boolean hasPose(int animId) {
		return frames(animId) != null || animId == Explosion.ANIM_HOWITZER || animId == Explosion.ANIM_HOWITZER_CLUSTER;
	}

	private static float age(Player player, QuirkState s, float partial) {
		if (s == null || s.animId < 120 || s.animId > 139) {
			return -1f;
		}
		return player.level().getGameTime() - s.animStart + partial;
	}

	public static void apply(Player player, HumanoidModel<?> model) {
		if (!Explosion.is(player)) {
			return;
		}
		float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
		float t = player.tickCount + partial;
		ModelPart r = model.rightArm, l = model.leftArm;
		float aim = model.head.xRot; // the look pitch, before any pose touches the head
		QuirkState s = Quirks.peek(player);
		float age = age(player, s, partial);
		boolean propelling = Explosion.propelling(player) && !player.onGround();
		if (propelling) {
			// Propulsion: leaning into it, both palms thrown back blasting, the legs trailing
			float jit = 0.06f * Mth.sin(t * 2.7f);
			float speed = (float) Math.min(1.0, Math.hypot(player.getX() - player.xo, player.getZ() - player.zo) / 0.8);
			HipBend.pitch(model, 0.85f, 0.15f + 0.55f * speed);
			pose(r, 1f, 0.95f + jit, 0f, 0.35f);
			pose(l, 1f, 0.95f - jit, 0f, -0.35f);
			model.rightLeg.xRot = 0.25f + 0.1f * Mth.sin(t * 0.6f);
			model.leftLeg.xRot = 0.45f - 0.1f * Mth.sin(t * 0.6f);
			model.rightLeg.yRot = 0f;
			model.leftLeg.yRot = 0f;
			model.head.xRot = Mth.lerp(0.5f, model.head.xRot, model.head.xRot - 0.45f * speed);
		}
		if (Explosion.spinning(player)) {
			// the Howitzer: arms flung straight out blasting, legs together and straight (the body spin is bodyTurn)
			float flick = 0.08f * Mth.sin(t * 2.3f);
			pose(r, 1f, -0.05f, 0f, 1.5f + flick);
			pose(l, 1f, -0.05f, 0f, -1.5f - flick);
			pose(model.rightLeg, 1f, 0.05f, 0f, 0.06f);
			pose(model.leftLeg, 1f, 0.05f, 0f, -0.06f);
			model.head.xRot = 0.15f;
			model.head.yRot = 0f;
			model.hat.copyFrom(model.head);
			return;
		}
		float[][] fr = age < 0f ? null : frames(s.animId);
		if (fr != null && !(propelling && s.animId == Explosion.ANIM_PROPEL)) {
			if (QuirkKeyframes.play(model, fr, age, 1f)) {
				// aimed moves: the shooting arm follows the look pitch
				float w = QuirkKeyframes.weight(age, QuirkKeyframes.length(fr));
				switch (s.animId) {
					case Explosion.ANIM_AP_SHOT, Explosion.ANIM_GAUNTLET -> r.xRot += aim * w;
					case Explosion.ANIM_AUTO -> {
						r.xRot += aim * w;
						l.xRot += aim * w;
					}
					case Explosion.ANIM_STRAIN, Explosion.ANIM_COOL -> {
						float shake = 0.06f * Mth.sin(t * 4.3f) * w;
						r.xRot += shake;
						l.xRot -= shake;
						r.zRot += shake * 0.5f;
						l.zRot -= shake * 0.5f;
					}
					default -> {
					}
				}
			}
		}
		model.hat.copyFrom(model.head);
	}

	// ---------------------------------------------------------------- whole-body turns (QuirkBodyTurnMixin)

	/**
	 * Howitzer Impact: {@code [yaw, pitch, roll]} -- spinning about the vertical while he rises (faster and faster), then
	 * pitched head-first along his dive (the look) and corkscrewing about his long axis. Its Cluster: the dive only.
	 */
	public static float[] bodyTurn(Player player, float partial) {
		if (!Explosion.spinning(player)) {
			return null;
		}
		QuirkState s = Quirks.peek(player);
		if (s == null) {
			return null;
		}
		float now = player.level().getGameTime() + partial;
		float since = now - s.animStart;
		long dive = s.val(Explosion.V_HOWITZER_DIVE);
		float deg = ExplosionConfig.HOWITZER_SPIN_DEGREES;
		if (dive > 0 && now < dive) {
			// the rise: a spin about the vertical that speeds up
			float k = since;
			return new float[] { -(0.5f * deg * k + 0.12f * deg * k * k), 0f, 0f };
		}
		float diveAge = dive > 0 ? now - dive : since;
		// head-first along the dive: the look pitch + 90 (level look = horizontal body), easing in over 3 ticks
		float pitch = Mth.clamp(90f + player.getViewXRot(partial), 30f, 170f) * Math.min(1f, diveAge / 3f);
		float roll = -deg * 1.4f * diveAge;
		return new float[] { 0f, pitch, roll };
	}

	private static void pose(ModelPart part, float w, float x, float y, float z) {
		part.xRot = Mth.lerp(w, part.xRot, x);
		part.yRot = Mth.lerp(w, part.yRot, y);
		part.zRot = Mth.lerp(w, part.zRot, z);
	}
}
