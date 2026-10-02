package com.projecthero.mod.client.thor;

import java.util.HashMap;
import java.util.Map;

import com.projecthero.mod.power.WeaponCombo;
import com.projecthero.mod.power.WeaponComboState;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.fabricmc.fabric.api.event.player.AttackEntityCallback;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * v0.14.20: the Mjolnir / Stormbreaker 3-hit melee combo's swings ({@link WeaponCombo}), replacing vanilla's arm swing
 * for every counted step:
 * <ul>
 *   <li>third person -- keyframed arms / body / legs / head on the vanilla model (the GeckoLib armour copies it), the
 *       same technique as {@link ThorPose}, plus a re-grip of the held weapon so it points out of the fist along the
 *       arm ({@link #weaponGrip}, applied by {@code ItemInHandLayerMixin}). Mjolnir swings one-handed; Stormbreaker
 *       two-handed, the free hand on the haft;</li>
 *   <li>first person -- the held weapon sweeps / backhands / slams about the hand ({@link #firstPerson}, from
 *       {@code ItemInHandRendererComboMixin}, which also holds off vanilla's swing and post-hit lowering meanwhile).</li>
 * </ul>
 * Driven by the synced {@link WeaponComboState} so every viewer sees the right swing. The local player's own swing is
 * predicted the moment they click ({@link #predict}) so it starts with the click instead of a round trip later; the
 * server's stamp of the same step takes over without a jump.
 * <p>Body frames are {@code {tick, wArmX, wArmY, wArmZ, fArmX, fArmY, fArmZ, bodyX, bodyY, frontLegX, backLegX, headX,
 * grip}} in radians for a weapon in the right hand (mirrored for the left): "w" = the weapon arm, "f" = the free arm,
 * arm X negative = forward / up, arm Y positive = toward the player's right, body Y positive = torso turned right.
 */
public final class WeaponComboPose {
	private static float[] f(float tick, float... v) {
		float[] out = new float[13];
		out[0] = tick;
		System.arraycopy(v, 0, out, 1, Math.min(12, v.length));
		return out;
	}

	private static float[] rest(float tick) {
		return f(tick);
	}

	private static final int SW = WeaponCombo.SWING_TICKS;
	private static final int FIN = WeaponCombo.FINISHER_TICKS;

	// ---------------- Mjolnir: one-handed ----------------
	/** Hit 1: a flat swing from out on the right across to the left. */
	private static final float[][] MJOLNIR_1 = {
			f(0, -1.45f, 0.95f, 0.25f, -0.25f, 0, -0.15f, 0, 0.55f, 0, 0, 0, 1),
			f(3, -1.5f, -0.9f, 0, 0.3f, 0, -0.1f, 0.08f, -0.55f, -0.15f, 0.1f, 0.05f, 1),
			f(6, -1.3f, -1.05f, 0, 0.25f, 0, -0.1f, 0.05f, -0.5f, -0.1f, 0.1f, 0, 1),
			rest(SW) };
	/** Hit 2: the backhand, on the other diagonal -- from high on the left down and out to the right. */
	private static final float[][] MJOLNIR_2 = {
			f(0, -2.5f, -0.75f, 0, 0.1f, 0, -0.1f, -0.05f, -0.45f, 0, 0, -0.1f, 1),
			f(3, -0.75f, 0.85f, 0.35f, -0.35f, 0, -0.2f, 0.12f, 0.5f, -0.2f, 0.15f, 0.08f, 1),
			f(6, -0.65f, 0.9f, 0.3f, -0.3f, 0, -0.2f, 0.1f, 0.45f, -0.15f, 0.1f, 0.05f, 1),
			rest(SW) };
	/** Hit 3: the finisher -- hammer high overhead, the free arm thrown wide, then slammed down with a lunge. */
	private static final float[][] MJOLNIR_3 = {
			f(0, -2.95f, -0.1f, 0.1f, -0.4f, 0, -0.9f, -0.15f, 0, 0, 0, -0.2f, 1),
			f(3, -3.05f, -0.1f, 0.1f, -0.45f, 0, -0.95f, -0.2f, 0, 0.05f, -0.05f, -0.25f, 1),
			f(6, -0.45f, -0.1f, 0, 0.3f, 0, -0.7f, 0.4f, 0, -0.55f, 0.45f, 0.3f, 1),
			f(10, -0.45f, -0.1f, 0, 0.25f, 0, -0.7f, 0.38f, 0, -0.5f, 0.42f, 0.28f, 1),
			rest(FIN) };

	// ---------------- Stormbreaker: two-handed ----------------
	/** Hit 1: a flat cleave from the right across to the left, both hands on the haft. */
	private static final float[][] STORM_1 = {
			f(0, -1.3f, 1.0f, 0.2f, -1.25f, 1.25f, 0, 0, 0.6f, 0, 0, 0, 1),
			f(3, -1.45f, -0.85f, 0, -1.35f, -0.45f, 0, 0.1f, -0.65f, -0.2f, 0.15f, 0.05f, 1),
			f(6, -1.3f, -1.0f, 0, -1.2f, -0.6f, 0, 0.08f, -0.6f, -0.15f, 0.1f, 0, 1),
			rest(SW) };
	/** Hit 2: the rising backhand on the other diagonal -- from low on the left up and out to the right. */
	private static final float[][] STORM_2 = {
			f(0, -0.55f, -0.85f, 0, -0.6f, -0.4f, 0, 0.1f, -0.5f, -0.1f, 0.1f, 0.05f, 1),
			f(3, -2.2f, 0.8f, 0.45f, -2.0f, 1.05f, 0, -0.1f, 0.5f, 0.05f, -0.05f, -0.12f, 1),
			f(6, -2.1f, 0.85f, 0.4f, -1.9f, 1.05f, 0, -0.08f, 0.45f, 0, 0, -0.1f, 1),
			rest(SW) };
	/** Hit 3: the finisher -- raised high in both hands, then a full overhead chop with a lunge. */
	private static final float[][] STORM_3 = {
			f(0, -2.95f, -0.15f, 0, -2.85f, 0.35f, 0, -0.15f, 0.05f, 0, 0, -0.2f, 1),
			f(3, -3.1f, -0.15f, 0, -2.95f, 0.35f, 0, -0.22f, 0.05f, 0.05f, -0.05f, -0.25f, 1),
			f(6, -0.55f, -0.15f, 0, -0.65f, 0.45f, 0, 0.45f, 0, -0.6f, 0.5f, 0.3f, 1),
			f(10, -0.5f, -0.15f, 0, -0.6f, 0.45f, 0, 0.42f, 0, -0.55f, 0.45f, 0.28f, 1),
			rest(FIN) };

	// ---------------- first person (both weapons) ----------------
	/**
	 * {@code {tick, x, y, z, xDeg, yDeg, zDeg}} in the hand's space (after vanilla's arm transform, before the item's
	 * own display transform; the pivot is about the grip). For a weapon held head-up: Z positive tips the head left,
	 * X positive tips it back toward the camera, negative forward / down.
	 */
	private static final float[][] FP_1 = {
			{0, 0, 0, 0, 0, 0, 0},
			{2, 0.12f, 0.05f, 0, -10, 30, -45},
			{4, -0.42f, 0.1f, -0.1f, -20, -35, 75},
			{6, -0.48f, 0.06f, -0.1f, -15, -40, 80},
			{SW, 0, 0, 0, 0, 0, 0} };
	private static final float[][] FP_2 = {
			{0, 0, 0, 0, 0, 0, 0},
			{2, -0.32f, 0.18f, 0, 10, -25, 55},
			{4, 0.14f, -0.18f, -0.12f, -35, 30, -60},
			{6, 0.14f, -0.2f, -0.1f, -30, 28, -55},
			{SW, 0, 0, 0, 0, 0, 0} };
	private static final float[][] FP_3 = {
			{0, 0, 0, 0, 0, 0, 0},
			{2, -0.12f, 0.28f, -0.12f, 12, 0, 8},
			{3, -0.12f, 0.3f, -0.12f, 14, 0, 8},
			{6, -0.25f, -0.04f, -0.25f, -75, 0, 0},
			{10, -0.25f, -0.03f, -0.22f, -70, 0, 0},
			{FIN, 0, 0, 0, 0, 0, 0} };

	/** Per player (entity id): {grip, arm override weight} from the last pose, read by the held-item layer. */
	private static final Map<Integer, float[]> GRIP = new HashMap<>();
	/** The local player's predicted step (shown until the server's stamp of the same step arrives). */
	private static WeaponComboState predicted;

	private WeaponComboPose() {
	}

	/** Client init: predict the local player's swing the moment they click a target. */
	public static void init() {
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (level.isClientSide()) {
				predict(player, entity);
			}
			return InteractionResult.PASS;
		});
		net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());
	}

	private static void predict(Player player, Entity target) {
		Minecraft mc = Minecraft.getInstance();
		if (player != mc.player || !(target instanceof LivingEntity) || !target.isAlive() || player.isSpectator()) {
			return;
		}
		int weapon = WeaponCombo.weaponOf(player.getMainHandItem());
		if (weapon == WeaponComboState.WEAPON_NONE || player.getAttackStrengthScale(0.5f) < WeaponCombo.MIN_STRENGTH) {
			return;
		}
		long now = player.level().getGameTime();
		WeaponComboState last = latest(player, now);
		predicted = new WeaponComboState(WeaponCombo.nextStep(last, weapon, now, WeaponCombo.windowTicks(player)), weapon, now);
	}

	private static int length(int step) {
		return step >= WeaponCombo.HITS ? FIN : SW;
	}

	/** The newest of the server's state and the local prediction (for chaining the next prediction). */
	private static WeaponComboState latest(Player player, long now) {
		WeaponComboState s = WeaponCombo.state(player);
		return predicted != null && predicted.start() > s.start() ? predicted : s;
	}

	/** The step whose swing {@code player} is playing, or null. */
	private static WeaponComboState playing(Player player, long now) {
		WeaponComboState s = WeaponCombo.state(player);
		WeaponComboState use = s;
		if (predicted != null && player == Minecraft.getInstance().player) {
			if (now - predicted.start() > FIN + 2) {
				predicted = null;
			} else if (s.start() < predicted.start()
					|| (s.step() == predicted.step() && s.start() - predicted.start() <= 8)) {
				use = predicted;
			}
		}
		if (use.step() <= 0 || use.weapon() != WeaponCombo.weaponOf(player.getMainHandItem())) {
			return null;
		}
		long age = now - use.start();
		return age < 0 || age > length(use.step()) ? null : use;
	}

	/** How far (0..1) the held weapon should be re-gripped along the arm for {@code player}'s current swing. */
	public static float weaponGrip(Player player) {
		float[] g = GRIP.get(player.getId());
		return g == null ? 0f : g[0];
	}

	/** How much (0..1) a combo swing is overriding {@code player}'s arms right now. */
	public static float armWeight(Player player) {
		float[] g = GRIP.get(player.getId());
		return g == null ? 0f : g[1];
	}

	private static float[][] frames(WeaponComboState s) {
		boolean storm = s.weapon() == WeaponComboState.WEAPON_STORMBREAKER;
		return switch (s.step()) {
			case 1 -> storm ? STORM_1 : MJOLNIR_1;
			case 2 -> storm ? STORM_2 : MJOLNIR_2;
			default -> storm ? STORM_3 : MJOLNIR_3;
		};
	}

	/** From {@code HumanoidModelMixin}: blend the playing swing over the model (Thor's own move poses go on top). */
	public static void apply(Player player, HumanoidModel<?> m) {
		float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
		WeaponComboState s = playing(player, player.level().getGameTime());
		if (s == null) {
			GRIP.remove(player.getId());
			return;
		}
		float tick = player.level().getGameTime() - s.start() + partial;
		float[][] fr = frames(s);
		float[] p = sample(fr, tick);
		float end = fr[fr.length - 1][0];
		float w = Mth.clamp(Math.min(tick / 1.0f + 0.5f, (end - tick) / 3f), 0f, 1f);
		boolean left = player.getMainArm() == HumanoidArm.LEFT;
		float sgn = left ? -1f : 1f;
		ModelPart wArm = left ? m.leftArm : m.rightArm;
		ModelPart fArm = left ? m.rightArm : m.leftArm;
		lerp(wArm, w, p[1], p[2] * sgn, p[3] * sgn);
		lerp(fArm, w, p[4], p[5] * sgn, p[6] * sgn);
		m.body.xRot = Mth.lerp(w, m.body.xRot, p[7]);
		m.body.yRot = Mth.lerp(w, m.body.yRot, p[8] * sgn);
		ModelPart frontLeg = left ? m.leftLeg : m.rightLeg;
		ModelPart backLeg = left ? m.rightLeg : m.leftLeg;
		frontLeg.xRot = Mth.lerp(w, frontLeg.xRot, frontLeg.xRot * 0.3f + p[9]);
		backLeg.xRot = Mth.lerp(w, backLeg.xRot, backLeg.xRot * 0.3f + p[10]);
		m.head.xRot = Mth.lerp(w, m.head.xRot, m.head.xRot + p[11]);
		m.hat.copyFrom(m.head);
		// the shoulders turn with the torso (as vanilla's own attack swing does), so a twisted body keeps its arms on
		float bodyY = m.body.yRot;
		m.rightArm.z = Mth.sin(bodyY) * 5.0f;
		m.rightArm.x = -Mth.cos(bodyY) * 5.0f;
		m.leftArm.z = -Mth.sin(bodyY) * 5.0f;
		m.leftArm.x = Mth.cos(bodyY) * 5.0f;
		GRIP.put(player.getId(), new float[] { p[12] * w, w });
	}

	/**
	 * How much (0..1) {@code player}'s main-hand weapon is playing a combo swing in first person: vanilla's swing and
	 * post-hit lowering are held off by this much (1 through the swing, easing out over its last 3 ticks).
	 */
	public static float firstPersonWeight(Player player, float partial) {
		WeaponComboState s = playing(player, player.level().getGameTime());
		if (s == null) {
			return 0f;
		}
		float tick = player.level().getGameTime() - s.start() + partial;
		return Mth.clamp((length(s.step()) - tick) / 3f, 0f, 1f);
	}

	/** First person, just before the held weapon is drawn (in the hand's space): sweep / backhand / slam it. */
	public static void firstPerson(Player player, HumanoidArm arm, float partial, PoseStack pose) {
		WeaponComboState s = playing(player, player.level().getGameTime());
		if (s == null) {
			return;
		}
		float[][] fr = s.step() == 1 ? FP_1 : s.step() == 2 ? FP_2 : FP_3;
		float tick = player.level().getGameTime() - s.start() + partial;
		float[] v = null;
		for (int i = 1; i < fr.length; i++) {
			if (tick <= fr[i][0]) {
				float[] a = fr[i - 1];
				float[] b = fr[i];
				float t = Mth.clamp((tick - a[0]) / Math.max(1e-4f, b[0] - a[0]), 0f, 1f);
				t = t * t * (3f - 2f * t);
				v = new float[7];
				for (int k = 1; k < 7; k++) {
					v[k] = Mth.lerp(t, a[k], b[k]);
				}
				break;
			}
		}
		if (v == null) {
			return;
		}
		float sg = arm == HumanoidArm.RIGHT ? 1f : -1f;
		pose.translate(sg * v[1], v[2], v[3]);
		pose.mulPose(Axis.YP.rotationDegrees(sg * v[5]));
		pose.mulPose(Axis.XP.rotationDegrees(v[4]));
		pose.mulPose(Axis.ZP.rotationDegrees(sg * v[6]));
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
				float t = Mth.clamp((tick - a[0]) / Math.max(1e-4f, b[0] - a[0]), 0f, 1f);
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

	/** World unload: forget the per-player grips and the prediction. */
	public static void clear() {
		GRIP.clear();
		predicted = null;
	}
}
