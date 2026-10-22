package com.projecthero.mod.client.quirk.darkshadow;

import com.projecthero.mod.quirk.Quirks;
import com.projecthero.mod.quirk.darkshadow.DarkShadow;
import com.projecthero.mod.quirk.data.QuirkState;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.7 (user) Quirk revamp: Tokoyami's keyframed poses (synced {@code animId / animStart}, so every viewer sees the
 * same), applied at the END of setupAnim. Each move is a short keyframe track per limb ({@code {tick, xRot, yRot, zRot}},
 * smoothstep between keys; {@link #AIM} in a key means "at the target / along the look"), faded in over 2 ticks and out
 * over the last 3:
 * <ul>
 *   <li>Strike: wound back across the body, then the arm thrown out at the target ("Dark Shadow!"). Black Ankh dash: a
 *       low lunge, arms swept back, then a claw rake.</li>
 *   <li>Dark Fang: both clawed hands forward, juddering with each bite. Grab: open hand out, clenched and hauled back;
 *       Slam: the fist raised and brought down.</li>
 *   <li>Shield (held): arms crossed before the face. Black Ankh: arms flung wide, then fists clenched in a hunch; while it
 *       lasts, a low predatory stance. Abyssal: arms thrown up as the wings beat; gliding, arms swept back like wings.</li>
 *   <li>Fetch / Scout: an arm sent out at the point / up and away. Nova: curled in, then flung wide, head back.</li>
 *   <li>Recall: a hand to the chest; Summon: an arm swept out. Flare: a light held high, the face turned from it.</li>
 *   <li>Berserk (held): hunched, clutching his head, shaking. Calmed: slumped, head bowed.</li>
 * </ul>
 * Negative {@code xRot} swings a limb forward / up.
 */
public final class DarkShadowPose {
	/** In a key: use the aim angle instead (x: the arm pitch to the target, y: its yaw). */
	private static final float AIM = 99f;

	private DarkShadowPose() {
	}

	private static float[] k(float t, float x, float y, float z) {
		return new float[] { t, x, y, z };
	}

	private static void pose(ModelPart part, float w, float x, float y, float z) {
		part.xRot = Mth.lerp(w, part.xRot, x);
		part.yRot = Mth.lerp(w, part.yRot, y);
		part.zRot = Mth.lerp(w, part.zRot, z);
	}

	private static float ease(float t) {
		t = Mth.clamp(t, 0f, 1f);
		return t * t * (3f - 2f * t);
	}

	/** Samples a track at {@code age} onto {@code part} with weight {@code w}. */
	private static void track(ModelPart part, float w, float age, float aimX, float aimY, float[]... keys) {
		float[] a = keys[0], b = keys[keys.length - 1];
		float u = 0f;
		if (age <= keys[0][0]) {
			b = a;
		} else if (age >= b[0]) {
			a = b;
		} else {
			for (int i = 0; i < keys.length - 1; i++) {
				if (age <= keys[i + 1][0]) {
					a = keys[i];
					b = keys[i + 1];
					u = ease((age - a[0]) / Math.max(0.001f, b[0] - a[0]));
					break;
				}
			}
		}
		float[] v = new float[3];
		for (int j = 0; j < 3; j++) {
			float av = a[j + 1] == AIM ? (j == 0 ? aimX : aimY) : a[j + 1];
			float bv = b[j + 1] == AIM ? (j == 0 ? aimX : aimY) : b[j + 1];
			v[j] = Mth.lerp(u, av, bv);
		}
		pose(part, w, v[0], v[1], v[2]);
	}

	static int length(int id) {
		return switch (id) {
			case DarkShadow.ANIM_STRIKE, DarkShadow.ANIM_GRAB, DarkShadow.ANIM_SCOUT, DarkShadow.ANIM_RECALL -> 12;
			case DarkShadow.ANIM_ANKH_DASH -> 9;
			case DarkShadow.ANIM_FANG -> 20;
			case DarkShadow.ANIM_SLAM -> 11;
			case DarkShadow.ANIM_ANKH, DarkShadow.ANIM_ABYSSAL, DarkShadow.ANIM_FETCH, DarkShadow.ANIM_SUMMON -> 14;
			case DarkShadow.ANIM_NOVA -> 18;
			case DarkShadow.ANIM_FLARE, DarkShadow.ANIM_CALM -> 28;
			default -> 0;
		};
	}

	public static void apply(Player player, HumanoidModel<?> model) {
		if (!DarkShadow.is(player) || player.level() == null) {
			return;
		}
		QuirkState s = Quirks.peek(player);
		if (s == null) {
			return;
		}
		float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
		float t = player.tickCount + partial;
		ModelPart r = model.rightArm, l = model.leftArm;
		// the look (or the target) as an arm angle
		float aimX = model.head.xRot - Mth.HALF_PI, aimY = model.head.yRot;
		Vec3 reach = DarkShadow.reach(player);
		if (reach != null && s.animId != 0) {
			Vec3 shoulder = player.getPosition(partial).add(0, player.getBbHeight() * 0.78, 0);
			Vec3 d = reach.subtract(shoulder);
			double horiz = Math.sqrt(d.x * d.x + d.z * d.z);
			float body = Mth.rotLerp(partial, player.yBodyRotO, player.yBodyRot);
			float dirYaw = (float) (Mth.atan2(-d.x, d.z) * Mth.RAD_TO_DEG);
			aimX = -Mth.HALF_PI - (float) Math.atan2(d.y, horiz);
			aimY = Mth.clamp(Mth.wrapDegrees(dirYaw - body) * Mth.DEG_TO_RAD, -1.2f, 1.2f);
		}

		// ---- held states
		if (DarkShadow.berserk(player)) {
			// losing control: hunched, clutching his head, shaking
			float shake = 0.07f * Mth.sin(t * 1.9f);
			model.body.xRot = Mth.lerp(0.8f, model.body.xRot, 0.38f);
			pose(r, 1f, -2.55f + shake, -0.55f, 0.15f);
			pose(l, 1f, -2.55f - shake, 0.55f, -0.15f);
			model.head.xRot = Mth.lerp(0.8f, model.head.xRot, 0.35f + shake);
			model.hat.copyFrom(model.head);
			model.rightLeg.z += 2.5f;
			model.leftLeg.z += 2.5f;
			model.rightLeg.y -= 0.6f;
			model.leftLeg.y -= 0.6f;
			return;
		}
		if (DarkShadow.shielding(player)) {
			pose(r, 1f, -1.65f, -0.85f, 0f);
			pose(l, 1f, -1.5f, 0.85f, 0f);
			model.head.xRot = Mth.lerp(0.7f, model.head.xRot, 0.25f);
			model.hat.copyFrom(model.head);
		}
		if (DarkShadow.ankh(player) && !player.isFallFlying()) {
			// a low, predatory stance: claws forward
			pose(r, 0.55f, -0.45f + 0.04f * Mth.sin(t * 0.1f), 0.1f, 0.25f);
			pose(l, 0.55f, -0.45f - 0.04f * Mth.sin(t * 0.1f), -0.1f, -0.25f);
		}
		if (DarkShadow.abyssal(player) && player.isFallFlying()) {
			// gliding: arms swept back along the wings
			pose(r, 1f, 0.2f, 0f, 1.15f + 0.05f * Mth.sin(t * 0.3f));
			pose(l, 1f, 0.2f, 0f, -1.15f - 0.05f * Mth.sin(t * 0.3f));
		}
		if (DarkShadow.val(player, DarkShadow.V_FETCH) == 1L) {
			pose(r, 1f, aimX, aimY, 0f); // being carried along: the arm still out at the point
		}

		// ---- the moves' keyframes
		float age = player.level().getGameTime() - s.animStart + partial;
		int len = length(s.animId);
		if (len == 0 || age < 0f || age >= len) {
			return;
		}
		float w = Math.min(1f, age / 2f + 0.2f) * Math.min(1f, (len - age) / 3f);
		switch (s.animId) {
			case DarkShadow.ANIM_STRIKE -> {
				track(r, w, age, aimX, aimY, k(0, -0.6f, 0.9f, 0.3f), k(2.5f, -1.9f, 0.9f, 0.5f), k(4.5f, AIM, AIM, 0f), k(9, AIM, AIM, 0f), k(12, -0.4f, 0, 0.1f));
				track(l, w, age, aimX, aimY, k(0, -0.3f, 0, -0.2f), k(4.5f, 0.6f, 0, -0.35f), k(12, 0.1f, 0, -0.1f));
				track(model.body, w, age, aimX, aimY, k(0, 0, 0.3f, 0), k(2.5f, 0, 0.4f, 0), k(4.5f, 0, -0.25f, 0), k(12, 0, 0, 0));
			}
			case DarkShadow.ANIM_ANKH_DASH -> {
				track(model.body, w, age, aimX, aimY, k(0, 0.15f, 0, 0), k(2, 0.55f, 0, 0), k(9, 0.3f, 0, 0));
				track(r, w, age, aimX, aimY, k(0, 0.9f, 0, 0.45f), k(3, 0.9f, 0, 0.45f), k(5, -1.7f, -0.35f, -0.1f), k(9, -0.9f, -0.2f, 0));
				track(l, w, age, aimX, aimY, k(0, 0.9f, 0, -0.45f), k(6, 0.9f, 0, -0.45f), k(9, 0.3f, 0, -0.2f));
			}
			case DarkShadow.ANIM_FANG -> {
				float jud = 0.12f * Mth.sin(age * 1.6f);
				track(r, w, age, aimX, aimY, k(0, -1.0f, -0.2f, 0), k(3, -1.55f + jud, -0.3f, 0.1f), k(20, -1.55f, -0.3f, 0.1f));
				track(l, w, age, aimX, aimY, k(0, -1.0f, 0.2f, 0), k(3, -1.5f - jud, 0.3f, -0.1f), k(20, -1.5f, 0.3f, -0.1f));
				track(model.body, w, age, aimX, aimY, k(0, 0, 0, 0), k(3, 0.22f, 0, 0), k(20, 0.22f, 0, 0));
			}
			case DarkShadow.ANIM_GRAB -> {
				track(r, w, age, aimX, aimY, k(0, -0.8f, 0, 0.3f), k(3, AIM, AIM, 0f), k(6, AIM, AIM, 0f), k(9, -1.1f, 0.2f, 0.15f), k(12, -1.0f, 0.2f, 0.15f));
				track(model.body, w, age, aimX, aimY, k(0, 0.1f, 0, 0), k(6, 0.1f, 0, 0), k(9, -0.12f, 0, 0), k(12, -0.12f, 0, 0));
			}
			case DarkShadow.ANIM_SLAM -> {
				track(r, w, age, aimX, aimY, k(0, -1.1f, 0.2f, 0.15f), k(3.5f, -2.95f, 0f, 0.1f), k(6, -0.35f, 0f, 0.1f), k(11, -0.3f, 0, 0.1f));
				track(l, w, age, aimX, aimY, k(0, -0.3f, 0, -0.2f), k(3.5f, -2.6f, 0f, -0.1f), k(6, -0.3f, 0, -0.1f), k(11, 0, 0, 0));
				track(model.body, w, age, aimX, aimY, k(0, -0.1f, 0, 0), k(3.5f, -0.2f, 0, 0), k(6, 0.4f, 0, 0), k(11, 0.1f, 0, 0));
			}
			case DarkShadow.ANIM_ANKH -> {
				track(r, w, age, aimX, aimY, k(0, -0.2f, 0, 0.3f), k(4, -0.3f, 0, 1.35f), k(7, -0.3f, 0, 1.35f), k(10, -0.55f, 0, 0.3f), k(14, -0.45f, 0.1f, 0.25f));
				track(l, w, age, aimX, aimY, k(0, -0.2f, 0, -0.3f), k(4, -0.3f, 0, -1.35f), k(7, -0.3f, 0, -1.35f), k(10, -0.55f, 0, -0.3f), k(14, -0.45f, -0.1f, -0.25f));
				track(model.head, w, age, aimX, aimY, k(0, 0, 0, 0), k(4, -0.45f, 0, 0), k(7, -0.45f, 0, 0), k(10, 0.2f, 0, 0), k(14, 0.1f, 0, 0));
				track(model.body, w, age, aimX, aimY, k(0, 0, 0, 0), k(7, -0.1f, 0, 0), k(10, 0.18f, 0, 0), k(14, 0.12f, 0, 0));
				model.hat.copyFrom(model.head);
			}
			case DarkShadow.ANIM_ABYSSAL -> {
				track(r, w, age, aimX, aimY, k(0, -0.4f, 0, 0.2f), k(3, -2.9f, 0, 0.4f), k(8, -2.4f, 0, 1.0f), k(14, 0.2f, 0, 1.1f));
				track(l, w, age, aimX, aimY, k(0, -0.4f, 0, -0.2f), k(3, -2.9f, 0, -0.4f), k(8, -2.4f, 0, -1.0f), k(14, 0.2f, 0, -1.1f));
				track(model.head, w, age, aimX, aimY, k(0, 0, 0, 0), k(3, -0.5f, 0, 0), k(14, -0.1f, 0, 0));
				model.hat.copyFrom(model.head);
			}
			case DarkShadow.ANIM_FETCH -> track(r, w, age, aimX, aimY, k(0, -0.7f, 0.4f, 0.3f), k(3, AIM, AIM, 0f), k(8, AIM, AIM, 0f), k(14, -0.9f, 0.2f, 0.1f));
			case DarkShadow.ANIM_SCOUT -> {
				track(r, w, age, aimX, aimY, k(0, -0.8f, 0.3f, 0.2f), k(4, -2.5f, -0.1f, 0f), k(12, -2.3f, -0.1f, 0f));
				track(model.head, w, age, aimX, aimY, k(0, 0, 0, 0), k(4, -0.3f, 0, 0), k(12, -0.2f, 0, 0));
				model.hat.copyFrom(model.head);
			}
			case DarkShadow.ANIM_NOVA -> {
				track(r, w, age, aimX, aimY, k(0, -0.5f, 0, 0), k(5, -1.25f, 0.95f, 0f), k(7.5f, -2.5f, 0f, 1.1f), k(12, -2.2f, 0, 0.9f), k(18, -0.3f, 0, 0.1f));
				track(l, w, age, aimX, aimY, k(0, -0.5f, 0, 0), k(5, -1.2f, -0.95f, 0f), k(7.5f, -2.5f, 0f, -1.1f), k(12, -2.2f, 0, -0.9f), k(18, -0.3f, 0, -0.1f));
				track(model.body, w, age, aimX, aimY, k(0, 0, 0, 0), k(5, 0.35f, 0, 0), k(7.5f, -0.18f, 0, 0), k(18, 0, 0, 0));
				track(model.head, w, age, aimX, aimY, k(0, 0, 0, 0), k(5, 0.4f, 0, 0), k(7.5f, -0.55f, 0, 0), k(18, 0, 0, 0));
				model.hat.copyFrom(model.head);
			}
			case DarkShadow.ANIM_RECALL -> {
				track(r, w, age, aimX, aimY, k(0, -0.4f, 0, 0), k(4, -1.25f, 0.75f, 0f), k(12, -1.2f, 0.7f, 0f));
				track(model.head, w, age, aimX, aimY, k(0, 0, 0, 0), k(4, 0.35f, 0, 0), k(12, 0.3f, 0, 0));
				model.hat.copyFrom(model.head);
			}
			case DarkShadow.ANIM_SUMMON -> {
				track(r, w, age, aimX, aimY, k(0, -1.2f, 0.7f, 0f), k(5, -1.45f, -0.2f, 1.0f), k(14, -0.3f, 0, 0.2f));
				track(model.body, w, age, aimX, aimY, k(0, 0.15f, 0, 0), k(5, -0.15f, 0, 0), k(14, 0, 0, 0));
			}
			case DarkShadow.ANIM_FLARE -> {
				track(l, w, age, aimX, aimY, k(0, -1.0f, 0, -0.2f), k(4, -3.0f, 0.1f, -0.15f), k(22, -3.0f, 0.1f, -0.15f), k(28, -0.4f, 0, 0));
				track(r, w, age, aimX, aimY, k(0, -0.3f, 0, 0), k(5, -2.0f, 0.65f, 0f), k(20, -2.0f, 0.65f, 0f), k(28, 0, 0, 0));
				track(model.head, w, age, aimX, aimY, k(0, 0, 0, 0), k(5, 0.25f, 0.45f, 0f), k(22, 0.25f, 0.45f, 0f), k(28, 0, 0, 0));
				model.hat.copyFrom(model.head);
			}
			case DarkShadow.ANIM_CALM -> {
				track(model.head, w, age, aimX, aimY, k(0, 0, 0, 0), k(6, 0.55f, 0, 0), k(28, 0.4f, 0, 0));
				track(model.body, w, age, aimX, aimY, k(0, 0, 0, 0), k(6, 0.2f, 0, 0), k(28, 0.12f, 0, 0));
				track(r, w, age, aimX, aimY, k(0, -0.4f, 0, 0), k(6, -1.8f, 0.7f, 0f), k(16, -1.7f, 0.7f, 0f), k(28, 0.1f, 0, 0.05f));
				track(l, w, age, aimX, aimY, k(0, 0, 0, 0), k(6, 0.15f, 0, -0.1f), k(28, 0.1f, 0, -0.05f));
				model.hat.copyFrom(model.head);
			}
			default -> {
			}
		}
	}
}
