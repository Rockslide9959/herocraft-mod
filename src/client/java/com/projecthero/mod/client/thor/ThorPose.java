package com.projecthero.mod.client.thor;

import java.util.HashMap;
import java.util.Map;

import com.projecthero.mod.item.ModItems;
import com.projecthero.mod.power.ThorFx;
import com.projecthero.mod.power.ThorVisuals;
import com.projecthero.mod.thorarmor.ThorArmor;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;

/**
 * v0.14.4: Thor's body animations -- the same keyframe technique as {@code GreenLanternPose}: arm / body / leg / head
 * angles laid over vanilla's own animation, driven by the synced {@link ThorFx} so every viewer sees the same thing.
 * Frames are written for a hammer in the right hand and mirrored when Mjolnir is in the left.
 * <ul>
 *   <li>Lightning Strike: Mjolnir thrust to the sky as the bolt lands, then brought down. Storm Call: raised to the sky
 *   and held. Suit-up: raised to the sky while the armour forms.</li>
 *   <li>Thunderclap: both hands overhead, then a slam into the ground with the body driven down after it.</li>
 *   <li>Chain Lightning: a wound-up thrust out along the aim, the free arm flung back. Throw: wind-up and a full
 *   follow-through. Hammer Volley: the hammer flung up and away, then the empty hand directing it.</li>
 *   <li>Held: Lightning Beam (hammer braced out along the aim, the free hand gripping the wrist, legs staggered,
 *   trembling with the recoil); God of Thunder's Wrath charge (hammer straight up, the other arm thrown wide, head back,
 *   shaking harder as it builds), released as a two-handed chop down onto the target.</li>
 * </ul>
 * Frames are {@code {tick, hArmX, hArmY, hArmZ, fArmX, fArmY, fArmZ, bodyX, bodyY, rLegX, lLegX, headX, grip}}: radians,
 * "h" = the hammer arm, "f" = the free arm, and {@code grip} (0..1) re-grips the held hammer so it points straight out
 * of the fist along the arm (head to the sky when the arm is up, at the target when it is thrust out) -- applied to
 * the held item by {@code ItemInHandLayerMixin} via {@link #hammerGrip}.
 */
public final class ThorPose {
	private static final float HALF_PI = (float) (Math.PI / 2);

	private static float[] f(float tick, float... v) {
		float[] out = new float[13];
		out[0] = tick;
		System.arraycopy(v, 0, out, 1, Math.min(12, v.length));
		return out;
	}

	private static float[] rest(float tick) {
		return f(tick);
	}

	private static final float[][] STRIKE = {
			f(0, -3.0f, 0.1f, 0.15f, -0.35f, 0, -0.55f, -0.1f, 0, 0, 0, -0.35f, 1),
			f(5, -3.05f, 0.1f, 0.12f, -0.35f, 0, -0.6f, -0.12f, 0, 0.05f, -0.05f, -0.4f, 1),
			f(9, -1.25f, 0, 0, 0.2f, 0, -0.3f, 0.18f, 0, -0.35f, 0.3f, 0.05f, 1),
			f(12, -1.2f, 0, 0, 0.2f, 0, -0.3f, 0.15f, 0, -0.3f, 0.25f, 0.05f, 1),
			rest(17) };
	private static final float[][] THUNDERCLAP = {
			f(0, -2.9f, 0, -0.3f, -2.9f, 0, 0.3f, -0.15f, 0, 0.1f, -0.1f, -0.3f, 1),
			f(3, -0.55f, 0, -0.35f, -0.55f, 0, 0.35f, 0.45f, 0, -0.55f, 0.45f, 0.3f, 1),
			f(10, -0.6f, 0, -0.3f, -0.6f, 0, 0.3f, 0.42f, 0, -0.5f, 0.42f, 0.28f, 1),
			rest(17) };
	/** Aimed: the hammer arm's X / Y are offsets on the crosshair. */
	private static final float[][] CHAIN = {
			f(0, 0.35f, 0.25f, 0, 0.1f, 0, -0.2f, 0, 0.35f, 0, 0, 0, 1),
			f(2, 0, 0, 0, 0.55f, 0, -0.75f, 0.08f, -0.3f, -0.35f, 0.3f, 0, 1),
			f(9, 0, 0, 0, 0.5f, 0, -0.7f, 0.06f, -0.25f, -0.3f, 0.25f, 0, 1),
			rest(14) };
	/** Aimed. The hammer is already in the air, so no grip. */
	private static final float[][] THROW = {
			f(0, -1.3f, 0.35f, 0, 0.25f, 0, -0.3f, -0.1f, 0.45f, 0.2f, -0.2f, 0, 0),
			f(3, 0.55f, -0.2f, 0, 0.45f, 0, -0.2f, 0.25f, -0.45f, -0.4f, 0.35f, 0.05f, 0),
			f(7, 0.45f, -0.15f, 0, 0.35f, 0, -0.2f, 0.2f, -0.35f, -0.35f, 0.3f, 0.05f, 0),
			rest(12) };
	private static final float[][] VOLLEY = {
			f(0, -2.7f, 0, 0.45f, -0.3f, 0, -0.35f, -0.12f, 0, 0, 0, -0.35f, 0),
			f(4, -2.8f, 0, 0.5f, -0.3f, 0, -0.35f, -0.12f, 0, 0, 0, -0.35f, 0),
			f(8, -1.55f, -0.35f, 0, -0.2f, 0, -0.2f, 0.05f, -0.2f, -0.2f, 0.2f, 0, 0),
			f(13, -1.5f, -0.35f, 0, -0.2f, 0, -0.2f, 0.05f, -0.2f, -0.2f, 0.2f, 0, 0),
			rest(18) };
	/** Aimed: the charged hammer brought down onto the target with both hands. */
	private static final float[][] WRATH = {
			f(0, -1.5f, 0, 0, -1.5f + HALF_PI, 0, 0.1f, -0.15f, 0, 0.1f, -0.1f, -0.3f, 1),
			f(4, 0.15f, 0, 0, -1.45f, 0.55f, 0, 0.3f, 0, -0.5f, 0.45f, 0.15f, 1),
			f(12, 0.1f, 0, 0, -1.4f, 0.5f, 0, 0.28f, 0, -0.45f, 0.4f, 0.15f, 1),
			rest(18) };
	private static final float[][] STORM = {
			rest(0),
			f(3, -3.05f, 0.1f, 0.12f, -0.2f, 0, -0.9f, -0.1f, 0, 0, 0, -0.45f, 1),
			f(16, -3.05f, 0.1f, 0.12f, -0.2f, 0, -0.9f, -0.1f, 0, 0, 0, -0.45f, 1),
			rest(22) };
	/** Suit-up, on the suit clock: the hammer (or fist) raised to the sky while the armour forms. */
	private static final float[][] SUIT = {
			rest(0),
			f(4, -3.05f, 0.1f, 0.12f, -0.25f, 0, -0.6f, -0.1f, 0, 0, 0, -0.4f, 1),
			f(28, -3.05f, 0.1f, 0.12f, -0.25f, 0, -0.65f, -0.1f, 0, 0, 0, -0.4f, 1),
			rest(ThorArmor.SUIT_UP_TICKS) };

	/** Per player (entity id): {grip, arm override weight} from the last pose, read by the held-item layer. */
	private static final Map<Integer, float[]> GRIP = new HashMap<>();

	private ThorPose() {
	}

	/** How far (0..1) the hammer should be re-gripped along the arm for {@code player}'s current Thor pose. */
	public static float hammerGrip(Player player) {
		float[] g = GRIP.get(player.getId());
		return g == null ? 0f : g[0];
	}

	/** How much (0..1) a Thor pose is overriding {@code player}'s arms right now. */
	public static float armWeight(Player player) {
		float[] g = GRIP.get(player.getId());
		return g == null ? 0f : g[1];
	}

	public static void apply(Player player, HumanoidModel<?> m) {
		ThorFx fx = player.getAttachedOrElse(com.projecthero.mod.attachment.ModAttachments.THOR_FX, null);
		if (fx == null || fx.equals(ThorFx.EMPTY)) {
			GRIP.remove(player.getId());
			return;
		}
		float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
		long now = player.level().getGameTime();
		float age = player.tickCount + partial;
		boolean left = hammerOnLeft(player);
		ModelPart hArm = left ? m.leftArm : m.rightArm;
		ModelPart fArm = left ? m.rightArm : m.leftArm;
		float s = left ? -1f : 1f;

		if (fx.has(ThorFx.CH_WRATH)) {
			float held = now - fx.chargeStart() + partial;
			float in = Math.min(1f, held / 5f);
			float build = Math.min(1f, held / 100f);
			float tremble = Mth.sin(age * 2.7f) * (0.02f + 0.06f * build);
			lerp(hArm, in, -3.1f + tremble, 0.1f * s, 0.12f * s);
			lerp(fArm, in, -0.45f - tremble, 0f, -1.15f * s);
			m.body.xRot = Mth.lerp(in, m.body.xRot, -0.08f);
			m.rightLeg.zRot = Mth.lerp(in, m.rightLeg.zRot, 0.14f);
			m.leftLeg.zRot = Mth.lerp(in, m.leftLeg.zRot, -0.14f);
			m.rightLeg.xRot *= 1f - in * 0.7f;
			m.leftLeg.xRot *= 1f - in * 0.7f;
			m.head.xRot = Mth.lerp(in, m.head.xRot, -0.55f);
			m.hat.copyFrom(m.head);
			store(player, in, in);
			return;
		}
		if (fx.has(ThorFx.CH_BEAM)) {
			float tremble = Mth.sin(age * 3.3f) * 0.02f;
			hArm.xRot = -HALF_PI + m.head.xRot + tremble;
			hArm.yRot = -0.1f * s + m.head.yRot;
			hArm.zRot = 0f;
			fArm.xRot = -HALF_PI + m.head.xRot + 0.14f - tremble;
			fArm.yRot = m.head.yRot + 0.62f * s;
			fArm.zRot = 0f;
			m.rightLeg.xRot = m.rightLeg.xRot * 0.3f - 0.32f * s;
			m.leftLeg.xRot = m.leftLeg.xRot * 0.3f + 0.3f * s;
			store(player, 1f, 1f);
			return;
		}
		if (fx.anim() != ThorFx.ANIM_NONE) {
			float tick = now - fx.animStart() + partial;
			float[][] frames = switch (fx.anim()) {
				case ThorFx.ANIM_STRIKE -> STRIKE;
				case ThorFx.ANIM_THUNDERCLAP -> THUNDERCLAP;
				case ThorFx.ANIM_CHAIN -> CHAIN;
				case ThorFx.ANIM_THROW -> THROW;
				case ThorFx.ANIM_VOLLEY -> VOLLEY;
				case ThorFx.ANIM_WRATH -> WRATH;
				case ThorFx.ANIM_STORM -> STORM;
				default -> null;
			};
			boolean aimed = frames == CHAIN || frames == THROW || frames == WRATH;
			if (frames != null && tick >= 0f && tick <= frames[frames.length - 1][0]) {
				blend(player, m, frames, tick, aimed, left);
				return;
			}
		}
		if (fx.suitDir() == ThorFx.SUIT_UP) {
			float tick = now - fx.suitStart() + partial;
			if (tick >= 0f && tick <= ThorArmor.SUIT_UP_TICKS) {
				blend(player, m, SUIT, tick, false, left);
				return;
			}
		}
		GRIP.remove(player.getId());
	}

	/** Which hand the pose treats as the hammer hand: wherever Mjolnir is, else the main hand. */
	private static boolean hammerOnLeft(Player player) {
		HumanoidArm arm = player.getMainArm();
		if (!player.getMainHandItem().is(ModItems.MJOLNIR) && player.getOffhandItem().is(ModItems.MJOLNIR)) {
			arm = arm.getOpposite();
		}
		return arm == HumanoidArm.LEFT;
	}

	private static void store(Player player, float grip, float weight) {
		GRIP.put(player.getId(), new float[] { grip, weight });
	}

	private static void lerp(ModelPart part, float w, float x, float y, float z) {
		part.xRot = Mth.lerp(w, part.xRot, x);
		part.yRot = Mth.lerp(w, part.yRot, y);
		part.zRot = Mth.lerp(w, part.zRot, z);
	}

	private static float[] sample(float[][] f, float tick) {
		for (int i = 1; i < f.length; i++) {
			if (tick <= f[i][0]) {
				float[] a = f[i - 1];
				float[] b = f[i];
				float t = (tick - a[0]) / Math.max(1e-4f, b[0] - a[0]);
				t = Math.max(0f, Math.min(1f, t));
				t = t * t * (3f - 2f * t);
				float[] out = new float[13];
				for (int k = 1; k < 13; k++) {
					out[k] = Mth.lerp(t, a[k], b[k]);
				}
				return out;
			}
		}
		return f[f.length - 1];
	}

	/** Applies a keyframe table; {@code aimed}: the hammer arm's X / Y are offsets on the crosshair aim. */
	private static void blend(Player player, HumanoidModel<?> m, float[][] f, float tick, boolean aimed, boolean left) {
		float[] p = sample(f, tick);
		float end = f[f.length - 1][0];
		float w = Math.max(0f, Math.min(1f, (end - tick) / 4f));
		if (!aimed) {
			w = Math.min(w, Math.max(0f, Math.min(1f, tick / 2f + 0.35f)));
		}
		float s = left ? -1f : 1f;
		ModelPart hArm = left ? m.leftArm : m.rightArm;
		ModelPart fArm = left ? m.rightArm : m.leftArm;
		float headX = m.head.xRot;
		float headY = m.head.yRot;
		lerp(hArm, w, p[1] + (aimed ? -HALF_PI + headX : 0f), (p[2] + (aimed ? -0.1f : 0f)) * s + (aimed ? headY : 0f), p[3] * s);
		lerp(fArm, w, p[4], p[5] * s, p[6] * s);
		m.body.xRot = Mth.lerp(w, m.body.xRot, p[7]);
		m.body.yRot = Mth.lerp(w, m.body.yRot, p[8] * s);
		ModelPart frontLeg = left ? m.leftLeg : m.rightLeg;
		ModelPart backLeg = left ? m.rightLeg : m.leftLeg;
		frontLeg.xRot = Mth.lerp(w, frontLeg.xRot, frontLeg.xRot * 0.3f + p[9]);
		backLeg.xRot = Mth.lerp(w, backLeg.xRot, backLeg.xRot * 0.3f + p[10]);
		m.head.xRot = Mth.lerp(w, m.head.xRot, m.head.xRot + p[11]);
		m.hat.copyFrom(m.head);
		store(player, p[12] * w, w);
	}
}
