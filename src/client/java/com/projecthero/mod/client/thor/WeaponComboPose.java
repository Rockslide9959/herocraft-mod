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

	// ---------------- v0.16.1: Mjolnir + Stormbreaker dual wield ----------------
	// Third person, written for Mjolnir in the RIGHT hand and Stormbreaker in the LEFT (mirrored the other way round):
	// {tick, mArmX, mArmY, mArmZ, sArmX, sArmY, sArmZ, hipBend, bodyY, rightLegX, leftLegX, headX, grip}. Anticipation
	// (ticks 0-2: wound up and drawn further back), the snap (by tick 4), follow-through (to 7), then recovery. The hips
	// twist into every blow (bodyY) and the torso bends from the hips (HipBend), the weapon leading the body.
	private static final int DS = WeaponCombo.DUAL_SWING_TICKS;
	private static final int DSL = WeaponCombo.DUAL_SLAM_TICKS;

	/** Hit 1: Mjolnir's overhead hammer blow -- raised high behind the head, then brought straight down. */
	private static final float[][] DUAL_1 = {
			f(0, -2.85f, 0.15f, 0.2f, -0.8f, 0.3f, -0.1f, -0.1f, 0.3f, 0.15f, -0.1f, -0.1f, 1),
			f(2, -3.2f, 0.1f, 0.15f, -0.85f, 0.35f, -0.12f, -0.2f, 0.4f, 0.25f, -0.15f, -0.18f, 1),
			f(4, -0.6f, -0.1f, 0f, 0.2f, 0.1f, -0.35f, 0.38f, -0.32f, 0.35f, -0.5f, 0.22f, 1),
			f(7, -0.4f, -0.15f, 0f, 0.25f, 0.1f, -0.35f, 0.42f, -0.38f, 0.32f, -0.45f, 0.25f, 1),
			rest(DS) };
	/** Hit 2: Stormbreaker's flat axe sweep -- cocked out wide on the left, then carved right across the body. */
	private static final float[][] DUAL_2 = {
			f(0, -0.7f, 0.35f, 0.15f, -1.45f, -1.05f, -0.25f, 0f, -0.55f, 0.1f, -0.1f, 0f, 1),
			f(2, -0.65f, 0.4f, 0.2f, -1.55f, -1.25f, -0.3f, -0.05f, -0.7f, 0.12f, -0.12f, -0.05f, 1),
			f(4, -0.4f, 0.25f, 0.4f, -1.5f, 0.95f, 0f, 0.12f, 0.6f, -0.4f, 0.35f, 0.05f, 1),
			f(7, -0.35f, 0.2f, 0.4f, -1.35f, 1.15f, 0f, 0.15f, 0.72f, -0.38f, 0.32f, 0.05f, 1),
			rest(DS) };
	/** Hit 3: the cross-body double swing -- both weapons high and wide, then cut down across each other in an X. */
	private static final float[][] DUAL_3 = {
			f(0, -2.5f, 0.45f, 0.7f, -2.5f, -0.45f, -0.7f, -0.12f, 0f, 0.1f, 0.1f, -0.12f, 1),
			f(2, -2.75f, 0.55f, 0.8f, -2.75f, -0.55f, -0.8f, -0.2f, 0f, 0.15f, 0.15f, -0.2f, 1),
			f(4, -0.85f, -0.8f, -0.15f, -0.75f, 0.8f, 0.15f, 0.36f, 0.12f, -0.35f, 0.3f, 0.2f, 1),
			f(7, -0.7f, -0.9f, -0.15f, -0.6f, 0.9f, 0.15f, 0.4f, 0.12f, -0.32f, 0.28f, 0.22f, 1),
			rest(DS) };
	/**
	 * Hit 4: the leaping double slam -- both weapons swung low behind as he crouches, flung up overhead as he leaps
	 * (the server hops him), then both driven down together as he lands.
	 */
	private static final float[][] DUAL_4 = {
			f(0, 0.55f, 0.1f, 0.25f, 0.55f, -0.1f, -0.25f, 0.4f, 0f, -0.15f, 0.2f, 0.15f, 1),
			f(3, -2.95f, 0.05f, 0.18f, -2.95f, -0.05f, -0.18f, -0.22f, 0f, -0.55f, -0.45f, -0.25f, 1),
			f(6, -3.3f, 0.05f, 0.15f, -3.3f, -0.05f, -0.15f, -0.32f, 0f, -0.6f, -0.5f, -0.3f, 1),
			f(9, -0.4f, -0.12f, 0.05f, -0.4f, 0.12f, -0.05f, 0.55f, 0f, -0.55f, 0.45f, 0.35f, 1),
			f(12, -0.35f, -0.12f, 0.05f, -0.35f, 0.12f, -0.05f, 0.52f, 0f, -0.5f, 0.42f, 0.32f, 1),
			rest(DSL) };

	// First person, dual wield: {tick, x, y, z, xDeg, yDeg, zDeg} per hand, written as for the right hand.
	/** Hit 1, Mjolnir's hand: up and tipped back, then slammed down and forward. */
	private static final float[][] FP_D1_M = {
			{0, 0, 0, 0, 0, 0, 0},
			{2, 0.0f, 0.42f, 0.05f, 18, 0, -6},
			{4, -0.12f, -0.16f, -0.22f, -80, 0, 6},
			{7, -0.12f, -0.18f, -0.2f, -74, 0, 6},
			{DS, 0, 0, 0, 0, 0, 0} };
	/** Hit 1, Stormbreaker's hand: pulled back and down out of the way. */
	private static final float[][] FP_D1_S = {
			{0, 0, 0, 0, 0, 0, 0},
			{2, 0.04f, -0.04f, 0.06f, 8, 0, 0},
			{4, 0.08f, -0.14f, 0.12f, 18, 10, -8},
			{7, 0.08f, -0.14f, 0.12f, 18, 10, -8},
			{DS, 0, 0, 0, 0, 0, 0} };
	/** Hit 2, Stormbreaker's hand: cocked out wide, then swept flat across the view. */
	private static final float[][] FP_D2_S = {
			{0, 0, 0, 0, 0, 0, 0},
			{2, 0.16f, 0.06f, 0, -10, 35, -50},
			{4, -0.5f, 0.1f, -0.12f, -20, -40, 80},
			{7, -0.56f, 0.06f, -0.12f, -15, -45, 85},
			{DS, 0, 0, 0, 0, 0, 0} };
	/** Hit 2, Mjolnir's hand: drawn back as the body turns away. */
	private static final float[][] FP_D2_M = {
			{0, 0, 0, 0, 0, 0, 0},
			{2, 0.05f, -0.05f, 0.05f, 6, 0, 0},
			{4, 0.1f, -0.16f, 0.12f, 14, -12, 10},
			{7, 0.1f, -0.16f, 0.12f, 14, -12, 10},
			{DS, 0, 0, 0, 0, 0, 0} };
	/** Hit 3, both hands (mirrored): up and out, then cut down and across to cross in the middle. */
	private static final float[][] FP_D3 = {
			{0, 0, 0, 0, 0, 0, 0},
			{2, 0.1f, 0.38f, 0.05f, 15, 0, -22},
			{4, -0.34f, -0.12f, -0.16f, -60, -20, 52},
			{7, -0.36f, -0.14f, -0.16f, -58, -22, 55},
			{DS, 0, 0, 0, 0, 0, 0} };
	/** Hit 4, both hands: low, then flung high (the leap), then driven down together (the landing). */
	private static final float[][] FP_D4 = {
			{0, 0, -0.12f, 0.1f, 22, 0, 0},
			{3, 0.0f, 0.5f, 0.05f, 20, 0, -8},
			{6, 0.0f, 0.6f, 0.05f, 25, 0, -8},
			{9, -0.1f, -0.22f, -0.26f, -86, 0, 6},
			{12, -0.1f, -0.2f, -0.24f, -82, 0, 6},
			{DSL, 0, 0, 0, 0, 0, 0} };

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
		int weapon = WeaponCombo.comboWeapon(player);
		if (weapon == WeaponComboState.WEAPON_NONE || player.getAttackStrengthScale(0.5f) < WeaponCombo.MIN_STRENGTH) {
			return;
		}
		long now = player.level().getGameTime();
		WeaponComboState last = latest(player, now);
		predicted = new WeaponComboState(WeaponCombo.nextStep(last, weapon, now, WeaponCombo.windowTicks(player)), weapon, now);
	}

	private static int length(WeaponComboState s) {
		return WeaponCombo.swingTicks(s.weapon(), s.step());
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
			if (now - predicted.start() > WeaponCombo.DUAL_SLAM_TICKS + 2) {
				predicted = null;
			} else if (s.start() < predicted.start()
					|| (s.step() == predicted.step() && s.start() - predicted.start() <= 8)) {
				use = predicted;
			}
		}
		if (use.step() <= 0 || use.weapon() != WeaponCombo.comboWeapon(player)) {
			return null;
		}
		long age = now - use.start();
		return age < 0 || age > length(use) ? null : use;
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
		if (s.weapon() == WeaponComboState.WEAPON_DUAL) {
			return switch (s.step()) {
				case 1 -> DUAL_1;
				case 2 -> DUAL_2;
				case 3 -> DUAL_3;
				default -> DUAL_4;
			};
		}
		boolean storm = s.weapon() == WeaponComboState.WEAPON_STORMBREAKER;
		return switch (s.step()) {
			case 1 -> storm ? STORM_1 : MJOLNIR_1;
			case 2 -> storm ? STORM_2 : MJOLNIR_2;
			default -> storm ? STORM_3 : MJOLNIR_3;
		};
	}

	/** v0.16.1: is Mjolnir on {@code player}'s left (dual wield: wherever the hammer is)? */
	private static boolean mjolnirOnLeft(Player player) {
		HumanoidArm arm = player.getMainArm();
		if (!player.getMainHandItem().is(com.projecthero.mod.item.ModItems.MJOLNIR)) {
			arm = arm.getOpposite();
		}
		return arm == HumanoidArm.LEFT;
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
		boolean dual = s.weapon() == WeaponComboState.WEAPON_DUAL;
		// single weapon: the frames are for a main hand on the right; dual: for Mjolnir on the right, Stormbreaker left
		boolean left = dual ? mjolnirOnLeft(player) : player.getMainArm() == HumanoidArm.LEFT;
		float sgn = left ? -1f : 1f;
		ModelPart wArm = left ? m.leftArm : m.rightArm;
		ModelPart fArm = left ? m.rightArm : m.leftArm;
		lerp(wArm, w, p[1], p[2] * sgn, p[3] * sgn);
		lerp(fArm, w, p[4], p[5] * sgn, p[6] * sgn);
		m.body.yRot = Mth.lerp(w, m.body.yRot, p[8] * sgn);
		ModelPart frontLeg = left ? m.leftLeg : m.rightLeg;
		ModelPart backLeg = left ? m.rightLeg : m.leftLeg;
		frontLeg.xRot = Mth.lerp(w, frontLeg.xRot, frontLeg.xRot * 0.3f + p[9]);
		backLeg.xRot = Mth.lerp(w, backLeg.xRot, backLeg.xRot * 0.3f + p[10]);
		if (dual) {
			// a wider, planted stance for the two-weapon swings
			m.rightLeg.zRot = Mth.lerp(w, m.rightLeg.zRot, 0.08f);
			m.leftLeg.zRot = Mth.lerp(w, m.leftLeg.zRot, -0.08f);
		}
		m.head.xRot = Mth.lerp(w, m.head.xRot, m.head.xRot + p[11]);
		m.hat.copyFrom(m.head);
		// the shoulders turn with the torso (as vanilla's own attack swing does), so a twisted body keeps its arms on
		float bodyY = m.body.yRot;
		m.rightArm.z = Mth.sin(bodyY) * 5.0f;
		m.rightArm.x = -Mth.cos(bodyY) * 5.0f;
		m.leftArm.z = -Mth.sin(bodyY) * 5.0f;
		m.leftArm.x = Mth.cos(bodyY) * 5.0f;
		// v0.16.1: bend from the hips (after the shoulder twist, which places the arms absolutely), so the bottom of the
		// torso stays on the legs
		com.projecthero.mod.client.HipBend.pitch(m, w, p[7]);
		GRIP.put(player.getId(), new float[] { p[12] * w, w });
	}

	/**
	 * How much (0..1) {@code player}'s main-hand weapon is playing a combo swing in first person: vanilla's swing and
	 * post-hit lowering are held off by this much (1 through the swing, easing out over its last 3 ticks). v0.16.1:
	 * the Twin Thunder Strike holds them off too.
	 */
	public static float firstPersonWeight(Player player, float partial) {
		float twin = twinTick(player, partial);
		if (twin >= 0f) {
			return Mth.clamp((ThorTwinFrames.END - twin) / 3f, 0f, 1f);
		}
		WeaponComboState s = playing(player, player.level().getGameTime());
		if (s == null) {
			return 0f;
		}
		float tick = player.level().getGameTime() - s.start() + partial;
		return Mth.clamp((length(s) - tick) / 3f, 0f, 1f);
	}

	/** v0.16.1: ticks into {@code player}'s Twin Thunder Strike, or -1 if none is playing. */
	private static float twinTick(Player player, float partial) {
		com.projecthero.mod.power.ThorFx fx = player.getAttachedOrElse(
				com.projecthero.mod.attachment.ModAttachments.THOR_FX, null);
		if (fx == null || fx.anim() != com.projecthero.mod.power.ThorFx.ANIM_TWIN_THUNDER) {
			return -1f;
		}
		float t = player.level().getGameTime() - fx.animStart() + partial;
		return t < 0f || t > ThorTwinFrames.END ? -1f : t;
	}

	/**
	 * First person, just before a held weapon is drawn (in the hand's space): sweep / backhand / slam it. v0.16.1:
	 * called for both hands -- with Mjolnir and Stormbreaker in hand the dual-wield swings move each weapon (the off
	 * hand's too), and the Twin Thunder Strike raises both and slams them down.
	 */
	public static void firstPerson(Player player, HumanoidArm arm, boolean mainHand, float partial, PoseStack pose) {
		float twin = twinTick(player, partial);
		float[][] fr;
		float tick;
		boolean dual = WeaponCombo.isDualWielding(player);
		if (twin >= 0f && dual) {
			fr = ThorTwinFrames.FP;
			tick = twin;
		} else {
			WeaponComboState s = playing(player, player.level().getGameTime());
			if (s == null) {
				return;
			}
			tick = player.level().getGameTime() - s.start() + partial;
			if (s.weapon() == WeaponComboState.WEAPON_DUAL) {
				// which weapon is in this hand decides its part of the swing
				boolean mjolnirHere = (mainHand ? player.getMainHandItem() : player.getOffhandItem())
						.is(com.projecthero.mod.item.ModItems.MJOLNIR);
				fr = switch (s.step()) {
					case 1 -> mjolnirHere ? FP_D1_M : FP_D1_S;
					case 2 -> mjolnirHere ? FP_D2_M : FP_D2_S;
					case 3 -> FP_D3;
					default -> FP_D4;
				};
			} else {
				if (!mainHand) {
					return;
				}
				fr = s.step() == 1 ? FP_1 : s.step() == 2 ? FP_2 : FP_3;
			}
		}
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

	/** Back-compat: the main hand only. */
	public static void firstPerson(Player player, HumanoidArm arm, float partial, PoseStack pose) {
		firstPerson(player, arm, true, partial, pose);
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
