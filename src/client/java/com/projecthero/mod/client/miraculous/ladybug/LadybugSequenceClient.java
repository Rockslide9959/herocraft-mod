package com.projecthero.mod.client.miraculous.ladybug;

import org.joml.Vector3f;

import com.projecthero.mod.miraculous.Miraculous;
import com.projecthero.mod.miraculous.data.MiraculousState;
import com.projecthero.mod.miraculous.ladybug.LadybugSequence;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1: the client half of Ladybug's long transformation ({@link LadybugSequence}) -- every client plays it from the
 * synced {@code formChangeAt}:
 * <ul>
 *   <li>{@link #applyPose} -- hands to the earrings, arms flung out for the twirl, the swipe across the eyes, the yo-yo
 *   arm, the hero pose;</li>
 *   <li>{@link #tick} -- the body turns through the twirl, and the light show: Tikki's dive, the earrings' flash, the red
 *   ribbon with its black spots climbing her as the suit forms, the sparkle of the mask, the spinning yo-yo, the burst of
 *   pink and spots behind the hero pose;</li>
 *   <li>{@link #camera} -- the cinematic camera path for the holder (read by {@code LadybugSequenceCameraMixin}).</li>
 * </ul>
 */
public final class LadybugSequenceClient {
	private static final Vector3f RED = new Vector3f(0.92f, 0.08f, 0.12f);
	private static final Vector3f PINK = new Vector3f(1.0f, 0.55f, 0.72f);
	private static final Vector3f PALE = new Vector3f(1.0f, 0.86f, 0.9f);
	private static final Vector3f BLACK = new Vector3f(0.04f, 0.03f, 0.05f);

	/** Pose keys: head x, right arm x / z, left arm x / z, right leg x / z, left leg x / z, body x. */
	private static final float[] STAND = { 0f, 0f, 0.1f, 0f, -0.1f, 0f, 0.05f, 0f, -0.05f, 0f };
	private static final float[] EARS = { -0.2f, -2.85f, 0.42f, -2.85f, -0.42f, 0f, 0.04f, 0f, -0.04f, 0f };
	private static final float[] TWIRL = { -0.25f, -0.2f, 1.75f, -0.2f, -1.75f, -0.55f, 0.1f, 0.05f, -0.08f, 0f };
	private static final float[] SWIPE_A = { 0.05f, -1.8f, 0.95f, -0.3f, -0.75f, 0f, 0.06f, 0f, -0.06f, 0f };
	private static final float[] SWIPE_B = { 0.1f, -1.75f, -0.55f, -0.3f, -0.75f, 0f, 0.06f, 0f, -0.06f, 0f };
	private static final float[] YOYO = { 0f, -1.45f, 0.95f, -0.35f, -0.55f, -0.15f, 0.1f, 0.15f, -0.1f, 0.05f };
	private static final float[] HERO = { -0.2f, -2.95f, 0.22f, -0.35f, -0.95f, -0.3f, 0.18f, 0.25f, -0.15f, -0.05f };
	private static final int EASE_OUT = 12;
	/** The HUD was hidden for the holder's sequence (and whether it was hidden before). */
	private static boolean hidHud;
	private static boolean hudWasHidden;

	private LadybugSequenceClient() {
	}

	private static float age(Player p, MiraculousState s, float partial) {
		return (float) (p.level().getGameTime() - s.formChangeAt) + partial;
	}

	private static boolean playing(MiraculousState s, float t) {
		return s != null && s.transformed && s.animId == LadybugSequence.ANIM && t >= 0f && t < LadybugSequence.TOTAL + EASE_OUT;
	}

	private static float startYaw(MiraculousState s) {
		return Float.intBitsToFloat((int) s.val(LadybugSequence.V_YAW));
	}

	/** The way she faces at {@code t}: where she began, turned through the twirl. */
	public static float yaw(MiraculousState s, float t) {
		return startYaw(s) + LadybugSequence.spin(Math.min(t, LadybugSequence.TOTAL));
	}

	// ---------------------------------------------------------------- the pose

	/** From {@code MiraculousClient.applyPose}, after the shared pose (it wins while the long sequence plays). */
	public static void applyPose(Player player, HumanoidModel<?> model) {
		MiraculousState s = Miraculous.peek(player);
		float t = s == null ? -1f : age(player, s, Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false));
		if (!playing(s, t)) {
			return;
		}
		float[] pose;
		float w = 1f;
		if (t < LadybugSequence.EARRINGS - 4) {
			pose = blend(STAND, EARS, smooth(t / (LadybugSequence.EARRINGS - 4f)));
		} else if (t < LadybugSequence.SPIN) {
			pose = EARS;
		} else if (t < LadybugSequence.SPIN + 6) {
			pose = blend(EARS, TWIRL, smooth((t - LadybugSequence.SPIN) / 6f));
		} else if (t < LadybugSequence.SPIN_END) {
			pose = TWIRL;
		} else if (t < LadybugSequence.MASK) {
			pose = blend(TWIRL, SWIPE_A, smooth((t - LadybugSequence.SPIN_END) / (float) (LadybugSequence.MASK - LadybugSequence.SPIN_END)));
		} else if (t < LadybugSequence.MASK_END) {
			pose = blend(SWIPE_A, SWIPE_B, smooth((t - LadybugSequence.MASK) / (float) (LadybugSequence.MASK_END - LadybugSequence.MASK)));
		} else if (t < LadybugSequence.YOYO + 4) {
			pose = blend(SWIPE_B, YOYO, smooth((t - LadybugSequence.MASK_END) / 6f));
		} else if (t < LadybugSequence.POSE) {
			pose = YOYO.clone();
			// the wrist works the yo-yo round in little circles
			pose[1] += 0.18f * Mth.sin(t * 1.6f);
			pose[2] += 0.12f * Mth.cos(t * 1.6f);
		} else if (t < LadybugSequence.POSE + 6) {
			pose = blend(YOYO, HERO, smooth((t - LadybugSequence.POSE) / 6f));
		} else if (t < LadybugSequence.TOTAL) {
			pose = HERO;
		} else {
			pose = HERO;
			w = 1f - smooth((t - LadybugSequence.TOTAL) / EASE_OUT);
		}
		set(model.head, pose[0], 0f, w, true);
		set(model.rightArm, pose[1], pose[2], w, false);
		set(model.leftArm, pose[3], pose[4], w, false);
		set(model.rightLeg, pose[5], pose[6], w, false);
		set(model.leftLeg, pose[7], pose[8], w, false);
		model.body.xRot = Mth.lerp(w, model.body.xRot, pose[9]);
		model.head.yRot = Mth.lerp(w, model.head.yRot, 0f);
		model.hat.copyFrom(model.head);
	}

	private static void set(ModelPart part, float x, float z, float w, boolean head) {
		part.xRot = Mth.lerp(w, part.xRot, x);
		if (!head) {
			part.zRot = Mth.lerp(w, part.zRot, z);
			part.yRot = Mth.lerp(w, part.yRot, 0f);
		}
	}

	private static float[] blend(float[] a, float[] b, float f) {
		float[] o = new float[a.length];
		for (int i = 0; i < a.length; i++) {
			o[i] = Mth.lerp(f, a[i], b[i]);
		}
		return o;
	}

	private static float smooth(float x) {
		return LadybugSequence.smooth(x);
	}

	// ---------------------------------------------------------------- the turn and the light show

	/** Every client tick (END): turn every player in the sequence, and draw its light. */
	public static void tick(Minecraft mc) {
		ClientLevel level = mc.level;
		if (level == null || mc.player == null || mc.isPaused()) {
			return;
		}
		// the holder watches it like a scene: no HUD and no hand in the way, given back the moment it ends
		boolean mine = camera(mc.player, 0f) != null;
		if (mine && !hidHud) {
			hudWasHidden = mc.options.hideGui;
			mc.options.hideGui = true;
			hidHud = true;
		} else if (!mine && hidHud) {
			mc.options.hideGui = hudWasHidden;
			hidHud = false;
		}
		for (Player p : level.players()) {
			MiraculousState s = Miraculous.peek(p);
			if (s == null || !s.transformed || s.animId != LadybugSequence.ANIM || p.distanceToSqr(mc.player) > 64 * 64) {
				continue;
			}
			float t = age(p, s, 0f);
			if (t < 0f || t >= LadybugSequence.TOTAL) {
				continue;
			}
			float now = yaw(s, t);
			float before = yaw(s, Math.max(0f, t - 1f));
			p.yBodyRotO = before;
			p.yBodyRot = now;
			p.yHeadRotO = before;
			p.yHeadRot = now;
			fx(level, p, s, (int) t, now);
		}
	}

	private static void fx(ClientLevel level, Player p, MiraculousState s, int t, float yaw) {
		RandomSource rnd = level.random;
		double h = p.getBbHeight();
		Vec3 feet = p.position();
		float rad = yaw * Mth.DEG_TO_RAD;
		Vec3 fwd = new Vec3(-Mth.sin(rad), 0, Mth.cos(rad));
		Vec3 right = new Vec3(-fwd.z, 0, fwd.x);
		Vec3 head = feet.add(0, h * 0.86, 0);
		DustParticleOptions red = new DustParticleOptions(RED, 1.2f);
		DustParticleOptions pink = new DustParticleOptions(PINK, 1.0f);
		DustParticleOptions pale = new DustParticleOptions(PALE, 0.8f);
		DustParticleOptions spot = new DustParticleOptions(BLACK, 1.3f);
		if (t < LadybugSequence.EARRINGS) {
			// Tikki's dive: a streak of red light curling into the earrings
			float k = t / (float) LadybugSequence.EARRINGS;
			Vec3 from = head.add(fwd.scale(1.2 * (1 - k))).add(right.scale(0.6 * Math.cos(k * 6))).add(0, 0.5 * (1 - k), 0);
			level.addParticle(red, from.x, from.y, from.z, 0, 0, 0);
			level.addParticle(pale, from.x, from.y, from.z, 0, 0, 0);
		} else if (t < LadybugSequence.SPIN) {
			// the earrings flash, their spots lighting up
			for (int side = -1; side <= 1; side += 2) {
				Vec3 ear = head.add(right.scale(0.28 * side)).add(0, -0.05, 0);
				if (t == LadybugSequence.EARRINGS) {
					level.addParticle(ParticleTypes.FLASH, ear.x, ear.y, ear.z, 0, 0, 0);
				}
				for (int i = 0; i < 3; i++) {
					level.addParticle(i == 0 ? spot : red, ear.x, ear.y, ear.z, (rnd.nextDouble() - 0.5) * 0.12, (rnd.nextDouble() - 0.5) * 0.12,
							(rnd.nextDouble() - 0.5) * 0.12);
				}
				if (t % 3 == 0) {
					level.addParticle(ParticleTypes.END_ROD, ear.x, ear.y, ear.z, (rnd.nextDouble() - 0.5) * 0.08, 0.04, (rnd.nextDouble() - 0.5) * 0.08);
				}
			}
		} else if (t < LadybugSequence.SPIN_END + 2) {
			// the ribbon: two strands of red light with black spots climbing her as the suit forms beneath them
			float top = Math.max(0.1f, LadybugSequence.cover(t) / 0.84f) * (float) h;
			for (int strand = 0; strand < 2; strand++) {
				for (int sub = 0; sub < 4; sub++) {
					double a = (t + sub / 4.0) * 0.55 + strand * Math.PI;
					double y = Math.max(0.0, top - 0.25 + 0.25 * Math.sin(a * 1.3));
					double r = 0.75 - 0.25 * (y / h);
					boolean isSpot = (t * 4 + sub) % 7 == 0;
					level.addParticle(isSpot ? spot : sub % 2 == 0 ? red : pink, feet.x + Math.cos(a) * r, feet.y + y, feet.z + Math.sin(a) * r, 0, 0.01, 0);
				}
			}
			if (t % 2 == 0) {
				level.addParticle(ParticleTypes.END_ROD, feet.x + (rnd.nextDouble() - 0.5) * 1.2, feet.y + rnd.nextDouble() * top,
						feet.z + (rnd.nextDouble() - 0.5) * 1.2, 0, 0.02, 0);
			}
		} else if (t >= LadybugSequence.MASK && t < LadybugSequence.MASK_END) {
			// the mask: sparkles along the swipe across her eyes
			float k = (t - LadybugSequence.MASK) / (float) (LadybugSequence.MASK_END - LadybugSequence.MASK);
			Vec3 eyes = head.add(fwd.scale(0.32)).add(0, 0.02, 0).add(right.scale(0.35 - 0.7 * k));
			// light, so the face stays visible as the mask forms
			if (t % 2 == 0) {
				level.addParticle(pale, eyes.x, eyes.y, eyes.z, 0, 0.01, 0);
			}
			if (t % 4 == 0) {
				level.addParticle(ParticleTypes.END_ROD, eyes.x, eyes.y + 0.1, eyes.z, 0, 0.03, 0);
			}
		} else if (t >= LadybugSequence.YOYO && t < LadybugSequence.POSE) {
			// the yo-yo: a spinning disc of red with black spots, out from her right hand
			Vec3 c = feet.add(0, h * 0.62, 0).add(right.scale(0.75)).add(fwd.scale(0.2));
			for (int i = 0; i < 6; i++) {
				double a = t * 0.9 + i * Math.PI / 3;
				Vec3 q = c.add(fwd.scale(Math.cos(a) * 0.2)).add(0, Math.sin(a) * 0.2, 0);
				level.addParticle(i % 3 == 0 ? spot : red, q.x, q.y, q.z, 0, 0, 0);
			}
		} else if (t == LadybugSequence.POSE) {
			// the hero pose: a burst of pink and spots behind her
			Vec3 c = feet.add(0, h * 0.6, 0).subtract(fwd.scale(0.6));
			level.addParticle(ParticleTypes.FLASH, c.x, c.y, c.z, 0, 0, 0);
			for (int i = 0; i < 90; i++) {
				double a = rnd.nextDouble() * Math.PI * 2;
				double r = 0.3 + rnd.nextDouble() * 0.4;
				Vec3 dir = right.scale(Math.cos(a)).add(0, Math.sin(a), 0);
				level.addParticle(i % 4 == 0 ? spot : i % 3 == 0 ? pale : pink, c.x + dir.x * r, c.y + dir.y * r, c.z + dir.z * r,
						dir.x * 0.25, dir.y * 0.25, dir.z * 0.25);
			}
			for (int i = 0; i < 16; i++) {
				level.addParticle(ParticleTypes.END_ROD, c.x, c.y, c.z, (rnd.nextDouble() - 0.5) * 0.35, (rnd.nextDouble() - 0.3) * 0.3,
						(rnd.nextDouble() - 0.5) * 0.35);
			}
		}
	}

	// ---------------------------------------------------------------- the camera

	/** Camera keys: tick, distance, angle round her from the front (deg), camera height, height looked at. */
	private static final float[][] CAM = {
			{ 0, 2.4f, 0, 1.6f, 1.55f },
			{ 14, 1.5f, 25, 1.7f, 1.62f },
			{ 32, 1.7f, 35, 1.7f, 1.6f },
			{ 40, 3.8f, 60, 1.0f, 1.0f },
			{ 66, 3.6f, 220, 1.3f, 1.1f },
			{ 92, 3.3f, 360, 1.6f, 1.3f },
			{ 98, 1.45f, 360, 1.72f, 1.68f },
			{ 116, 1.55f, 342, 1.72f, 1.68f },
			{ 122, 2.6f, 300, 1.2f, 1.1f },
			{ 146, 2.9f, 318, 1.1f, 1.1f },
			{ 152, 4.6f, 350, 0.55f, 1.25f },
			{ 170, 4.9f, 360, 0.55f, 1.3f },
	};

	/** Where the holder's camera is at {@code t}, and where it looks: {pos x, y, z, yaw, pitch}; null when not playing. */
	public static double[] camera(Player p, float partial) {
		MiraculousState s = Miraculous.peek(p);
		if (s == null) {
			return null;
		}
		float t = age(p, s, partial);
		if (!(s.transformed && s.animId == LadybugSequence.ANIM && t >= 0f && t < LadybugSequence.TOTAL)) {
			return null;
		}
		int i = 0;
		while (i < CAM.length - 2 && t > CAM[i + 1][0]) {
			i++;
		}
		float[] a = CAM[i];
		float[] b = CAM[i + 1];
		float f = smooth((t - a[0]) / (b[0] - a[0]));
		float dist = Mth.lerp(f, a[1], b[1]);
		float ang = Mth.lerp(f, a[2], b[2]);
		float camH = Mth.lerp(f, a[3], b[3]);
		float lookH = Mth.lerp(f, a[4], b[4]);
		double cx = Double.longBitsToDouble(s.val(LadybugSequence.V_X));
		double cy = Double.longBitsToDouble(s.val(LadybugSequence.V_Y)) + LadybugSequence.lift(t);
		double cz = Double.longBitsToDouble(s.val(LadybugSequence.V_Z));
		float yaw0 = startYaw(s) * Mth.DEG_TO_RAD;
		// "in front of her" is along her starting facing; the angle walks the camera round her
		double around = yaw0 + ang * Mth.DEG_TO_RAD;
		double px = cx - Math.sin(around) * dist;
		double pz = cz + Math.cos(around) * dist;
		double py = cy + camH;
		double dx = cx - px;
		double dy = cy + lookH - py;
		double dz = cz - pz;
		double horiz = Math.sqrt(dx * dx + dz * dz);
		float camYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
		float camPitch = (float) -Math.toDegrees(Math.atan2(dy, horiz));
		return new double[] { px, py, pz, camYaw, camPitch };
	}
}
