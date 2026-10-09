package com.projecthero.mod.client.punisher;

import java.util.HashMap;
import java.util.Map;

import com.projecthero.mod.client.punisher.GunAnim.Kind;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;

/**
 * v0.15.16 (user: "add better poses for holding the guns ... make it make sense and look good"): third-person gun
 * handling for anyone holding a Punisher firearm, on top of vanilla's limbs.
 *
 * <ul>
 *   <li><b>Low ready</b> (holding): both hands on the gun, muzzle angled down in front; walking bobs it gently.</li>
 *   <li><b>Aim</b> (right-click): shouldered and pointed where the head looks -- the off hand on the handguard (two-hand
 *       pistol grip for the pistol).</li>
 *   <li><b>Sprint carry</b>: muzzle low, arms tucked, bouncing with the stride.</li>
 *   <li><b>Reload</b>: the gun canted in front of the chest while the off hand strips the old magazine, fetches a new one
 *       from the belt, seats it and slaps it home (the shotgun feeds one shell per cycle instead).</li>
 *   <li><b>Tactical Roll</b>: a tuck -- knees up, gun hugged (the tumble itself is a body rotation, {@link #rollAngle}).</li>
 *   <li><b>Adrenaline</b>: the off hand raises a syringe and drives it into the thigh, presses, rips it out; the head
 *       snaps back as it hits.</li>
 * </ul>
 * The barrel is kept pointing where it should with a per-frame item correction ({@link #item}) that
 * {@code ItemInHandLayerMixin} applies -- the gun models are built for a hanging arm, so a raised arm would otherwise
 * tip them skyward.
 */
public final class PunisherGunPose {
	/** id -> {barrel pitch (rad, down +), cant (deg), magazine shown, syringe shown, scale, barrel yaw (deg), grip lift factor} for this frame */
	private static final Map<Integer, float[]> ITEM = new HashMap<>();

	private PunisherGunPose() {
	}

	public static void clear() {
		ITEM.clear();
	}

	/** This frame's correction for the held gun / props, or null when nothing is posed. */
	public static float[] item(Player player) {
		return ITEM.get(player.getId());
	}

	public static void apply(Player player, HumanoidModel<?> m, float limbSwing, float limbAmount) {
		float pt = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
		Kind kind = GunAnim.kind(player);
		float stab = GunAnim.stab(player, pt);
		float roll = GunAnim.rolling(player, pt);
		float melee = GunAnim.melee(player, pt);
		if (kind == null && stab < 0f && roll < 0f && melee < 0f) {
			ITEM.remove(player.getId());
			return;
		}
		boolean right = player.getMainArm() == HumanoidArm.RIGHT;
		float s = right ? 1f : -1f;
		ModelPart main = right ? m.rightArm : m.leftArm;
		ModelPart off = right ? m.leftArm : m.rightArm;
		float[] item = { 0f, 0f, 0f, 0f, 1f, 0f, 1f };
		float barrelPitch = 0f;
		float bodyYaw = Mth.rotLerp(pt, player.yBodyRotO, player.yBodyRot);
		float headYaw = Mth.rotLerp(pt, player.yHeadRotO, player.yHeadRot);
		float lookPitch = Mth.lerp(pt, player.xRotO, player.getXRot()) * Mth.DEG_TO_RAD;
		float barrelYaw = bodyYaw;

		if (kind != null) {
			float aim = GunAnim.aim(player, pt);
			float sprint = GunAnim.sprint(player, pt);
			float hx = m.head.xRot;
			float hy = m.head.yRot;
			boolean pistol = kind == Kind.PISTOL;
			float walk = Mth.cos(limbSwing * 0.6662f) * limbAmount;

			// v0.15.16 playtest: the shooting arm sets where the gun is; the support arm is then solved (reach()) so its hand
			// lands on the gun's handguard wherever the gun points -- both hands always on the gun.
			// ready: gun up at chest height, muzzle a touch low and in toward the middle
			// the forearm angles down more steeply than the barrel, as when really holding a pistol grip
			float mX = (pistol ? -1.0f : -0.7f) + hx * 0.35f + walk * 0.04f;
			float mY = (pistol ? -0.2f : -0.35f) * s + hy * 0.3f;
			float barrel = (pistol ? 0.45f : 0.3f) + hx * 0.35f;
			float yawRel = -10f * s + hy * Mth.RAD_TO_DEG * 0.3f;

			// sprint carry: across the chest, muzzle low, bouncing with the stride
			if (sprint > 0f) {
				float bounce = Mth.sin(limbSwing * 0.6662f * 2f) * 0.08f * limbAmount;
				mX = Mth.lerp(sprint, mX, (pistol ? -0.75f : -0.6f) + bounce);
				mY = Mth.lerp(sprint, mY, (pistol ? -0.3f : -0.45f) * s);
				barrel = Mth.lerp(sprint, barrel, pistol ? 0.8f : 0.7f);
				yawRel = Mth.lerp(sprint, yawRel, (pistol ? -20f : -42f) * s);
			}
			// aim down sights: shouldered, along the look
			if (aim > 0f) {
				mX = Mth.lerp(aim, mX, (pistol ? -1.5708f : -1.4708f) + hx);
				mY = Mth.lerp(aim, mY, (pistol ? -0.12f : -0.3f) * s + hy);
				barrel = Mth.lerp(aim, barrel, lookPitch);
				yawRel = Mth.lerp(aim, yawRel, hy * Mth.RAD_TO_DEG);
			}
			float kick = GunAnim.recoil(player, pt) * (kind == Kind.SNIPER || kind == Kind.SHOTGUN ? 0.35f : 0.18f);
			mX -= kick;
			barrel -= kick * 0.6f;
			float yaw = bodyYaw + yawRel;
			// aimed, the arms are up level with the gun: it sits lower on the fists
			float liftFactor = 1f - 1.8f * aim;
			item[6] = liftFactor;
			float[] o0 = reach(kind, s, mX, mY, barrel, yawRel, liftFactor);
			float oX = o0[0];
			float oY = o0[1];

			// reload
			float r = GunAnim.reload(player, pt);
			float cant = 0f;
			if (r >= 0f) {
				float w = GunAnim.seg(r, 0f, 0.12f) * (1f - GunAnim.seg(r, 0.9f, 1f));
				mX = Mth.lerp(w, mX, -0.9f);
				mY = Mth.lerp(w, mY, -0.02f * s);
				barrel = Mth.lerp(w, barrel, 0.3f);
				yaw = rotLerp(w, yaw, bodyYaw - 30f * s);
				float[] o = kind == Kind.SHOTGUN ? shellHand(r) : magHand(r);
				oX = Mth.lerp(w, oX, o[0]);
				oY = Mth.lerp(w, oY, o[1] * s);
				off.zRot = Mth.lerp(w, off.zRot, o[2] * s);
				cant = (kind == Kind.SHOTGUN ? -35f : 28f) * w;
				m.head.xRot += 0.35f * w;
				if (kind != Kind.SHOTGUN && ((r > 0.14f && r < 0.36f) || (r > 0.46f && r < 0.72f))) {
					item[2] = 1f;
				}
			}
			main.xRot = mX;
			main.yRot = mY;
			main.zRot = 0f;
			off.xRot = oX;
			off.yRot = oY;
			if (r < 0f) {
				off.zRot = 0f;
			}
			barrelPitch = barrel;
			barrelYaw = yaw;
			item[1] = cant;
			// the mesh guns (rifle, sniper) are slimmer than the voxel ones: a little bigger in the hands
			item[4] = scale(kind);
		}

		if (roll >= 0f) {
			float w = GunAnim.seg(roll, 0f, 0.15f) * (1f - GunAnim.seg(roll, 0.85f, 1f));
			main.xRot = Mth.lerp(w, main.xRot, -1.35f);
			main.yRot = Mth.lerp(w, main.yRot, -0.1f * s);
			off.xRot = Mth.lerp(w, off.xRot, -1.45f);
			off.yRot = Mth.lerp(w, off.yRot, 0.7f * s);
			m.rightLeg.xRot = Mth.lerp(w, m.rightLeg.xRot, -1.25f);
			m.leftLeg.xRot = Mth.lerp(w, m.leftLeg.xRot, -1.1f);
			m.rightLeg.yRot = Mth.lerp(w, m.rightLeg.yRot, 0f);
			m.leftLeg.yRot = Mth.lerp(w, m.leftLeg.yRot, 0f);
			m.head.xRot = Mth.lerp(w, m.head.xRot, 0.7f);
			barrelPitch = Mth.lerp(w, barrelPitch, 0.5f);
		}

		if (stab >= 0f) {
			stabPose(m, off, s, stab, item);
		}
		if (melee >= 0f) {
			float[] b = GunAnim.meleeKind(player) == com.projecthero.mod.punisher.ability.PunisherMelee.ANIM_KICK
					? kickPose(m, main, off, right, s, melee) : punchPose(m, main, off, right, s, melee);
			barrelPitch = Mth.lerp(b[0], barrelPitch, b[1]);
			barrelYaw = rotLerp(b[0], barrelYaw, bodyYaw + b[2]);
			item[6] = Mth.lerp(b[0], item[6], b[3]); // arm up level: the gun sits lower on the fist, as when aimed
		}
		item[0] = barrelPitch;
		item[5] = barrelYaw;
		m.hat.copyFrom(m.head);
		ITEM.put(player.getId(), item);
	}

	/** How far (px) the gun slides back along its barrel in third person so its grip sits in the fist. */
	public static float gripShift(net.minecraft.world.item.ItemStack stack) {
		Kind k = GunAnim.kind(stack);
		if (k == null) {
			return 0f;
		}
		return switch (k) {
			case RIFLE, SNIPER -> 4.5f;
			case SHOTGUN -> 3f;
			default -> 1.5f;
		};
	}

	/** How far (px) the gun rides above the fist in third person. */
	public static float gripLift(net.minecraft.world.item.ItemStack stack) {
		Kind k = GunAnim.kind(stack);
		if (k == null) {
			return 0f;
		}
		return switch (k) {
			case RIFLE, SNIPER -> 2.4f;
			case SHOTGUN -> 2f;
			default -> 0.8f;
		};
	}

	/** Third-person size of each gun on top of its model's own display scale (the mesh guns are slim: bigger). */
	private static float scale(Kind k) {
		return switch (k) {
			case RIFLE, SNIPER -> 1.45f;
			case SHOTGUN -> 1.15f;
			default -> 1.05f;
		};
	}

	/**
	 * Support-arm angles {xRot, yRot} that put its hand on the gun: the shooting hand is found from its arm angles, the
	 * handguard sits along the barrel from there (the grip-to-handguard length of that gun at its third-person size), and
	 * the support arm is pointed at it. Model space: px, +Y down, -Z ahead, the body's right is -X; arm pivots at
	 * (-5, 2, 0) / (5, 2, 0), the fist's middle ~9.5 px down the arm.
	 */
	private static float[] reach(Kind kind, float s, float mX, float mY, float barrel, float yawRelDeg, float liftFactor) {
		// shooting hand
		float[] hand = rotate(-1f * s, 9.5f, 0f, mX, mY);
		hand[0] += -5f * s;
		hand[1] += 2f;
		// along the barrel to the handguard (pistol: the support hand cups the shooting hand)
		float len = switch (kind) {
			case RIFLE -> 7.1f * 0.55f;
			case SHOTGUN -> 8.4f * 0.55f;
			case SNIPER -> 9.6f * 0.52f;
			default -> 0f;
		} * scale(kind);
		float yr = yawRelDeg * Mth.DEG_TO_RAD;
		float dx = -Mth.sin(yr) * Mth.cos(barrel);
		float dy = Mth.sin(barrel);
		float dz = -Mth.cos(yr) * Mth.cos(barrel);
		float tx = hand[0] + dx * len + (kind == Kind.PISTOL ? 1.6f * s : 0f);
		float lift = switch (kind) {
			case RIFLE, SNIPER -> 2.4f;
			case SHOTGUN -> 2f;
			default -> 0.8f;
		};
		float ty = hand[1] + dy * len + 1.6f - lift * liftFactor; // under the handguard, which rides lift px above the shooting fist
		float tz = hand[2] + dz * len;
		// point the support arm at it
		float px = tx - 5f * s;
		float py = ty - 2f;
		float pz = tz;
		float l = Mth.sqrt(px * px + py * py + pz * pz);
		if (l < 1.0e-3f) {
			return new float[] { -1f, 0.6f * s };
		}
		px /= l;
		py /= l;
		pz /= l;
		float x = -(float) Math.acos(Mth.clamp(py, -1f, 1f));
		float y = (float) Math.atan2(-px, -pz);
		return new float[] { x, y };
	}

	/** A ModelPart's rotation (x then y; z = 0) applied to a point. */
	private static float[] rotate(float vx, float vy, float vz, float xRot, float yRot) {
		float cx = Mth.cos(xRot), sx = Mth.sin(xRot);
		float y1 = vy * cx - vz * sx;
		float z1 = vy * sx + vz * cx;
		float cy = Mth.cos(yRot), sy = Mth.sin(yRot);
		return new float[] { vx * cy + z1 * sy, y1, -vx * sy + z1 * cy };
	}

	private static float rotLerp(float t, float a, float b) {
		return a + t * Mth.wrapDegrees(b - a);
	}

	/** Off hand through a magazine change: {xRot, yRot, zRot} (yRot / zRot for a right-handed shooter). */
	private static float[] magHand(float r) {
		// at the magwell, pull down + away, to the belt, back to the magwell, slap up, back on the handguard
		float[][] keys = {
				{ 0.00f, -0.95f, 0.78f, 0f },
				{ 0.12f, -0.72f, 0.62f, 0f },
				{ 0.36f, -0.15f, 0.30f, -0.2f },
				{ 0.46f, 0.12f, 0.05f, -0.15f },
				{ 0.58f, -0.35f, 0.45f, -0.05f },
				{ 0.72f, -0.74f, 0.62f, 0f },
				{ 0.82f, -1.05f, 0.5f, 0f },
				{ 1.00f, -0.95f, 0.78f, 0f } };
		return keyed(keys, r);
	}

	/** Off hand feeding one shotgun shell: belt, up to the loading port, push it in, back. */
	private static float[] shellHand(float r) {
		float[][] keys = {
				{ 0.00f, -0.9f, 0.7f, 0f },
				{ 0.25f, 0.12f, 0.1f, -0.15f },
				{ 0.55f, -0.7f, 0.55f, 0f },
				{ 0.70f, -0.82f, 0.5f, 0f },
				{ 1.00f, -0.9f, 0.7f, 0f } };
		return keyed(keys, r);
	}

	private static float[] keyed(float[][] keys, float t) {
		for (int i = 1; i < keys.length; i++) {
			if (t <= keys[i][0]) {
				float[] a = keys[i - 1];
				float[] b = keys[i];
				float f = GunAnim.smooth((t - a[0]) / Math.max(1.0e-4f, b[0] - a[0]));
				return new float[] { Mth.lerp(f, a[1], b[1]), Mth.lerp(f, a[2], b[2]), Mth.lerp(f, a[3], b[3]) };
			}
		}
		float[] l = keys[keys.length - 1];
		return new float[] { l[1], l[2], l[3] };
	}

	/** Adrenaline: raise the syringe, drive it into the thigh, press, rip it out; head snaps back on the hit. */
	private static void stabPose(HumanoidModel<?> m, ModelPart off, float s, float t, float[] item) {
		int hit = com.projecthero.mod.punisher.PunisherConfig.ADRENALINE_STAB_TICKS;
		float[][] keys = {
				{ 0f, off.xRot, off.yRot / s, off.zRot / s },
				{ 5f, -2.5f, 0.15f, -0.35f },
				{ 7.5f, -2.6f, 0.15f, -0.3f },
				{ 9.5f, -0.12f, 0.0f, -0.16f },
				{ hit, -0.08f, 0.0f, -0.14f },
				{ hit + 4f, -0.55f, -0.1f, -0.7f },
				{ GunAnim.STAB_ANIM_TICKS, off.xRot, off.yRot / s, off.zRot / s } };
		float[] o = keyed(keys, t);
		// the press: a slight tremble while the plunger goes down
		if (t > 9.5f && t < hit) {
			o[0] += Mth.sin(t * 9f) * 0.03f;
		}
		off.xRot = o[0];
		off.yRot = o[1] * s;
		off.zRot = o[2] * s;
		float snap = GunAnim.seg(t, hit, hit + 2f) * (1f - GunAnim.seg(t, hit + 5f, hit + 9f));
		m.head.xRot = Mth.lerp(snap, m.head.xRot, -0.45f);
		if (t < 9.5f) {
			m.head.xRot += 0.25f * GunAnim.seg(t, 5f, 9f); // watching the needle go in
		}
		if (t < hit + 5f) {
			item[3] = 1f;
		}
	}

	/**
	 * v0.15.18 Brutal Strike: a right-hand jab from wherever the arms were -- a short wind-up (shoulder back), the fist
	 * driven straight out at {@code PunisherMelee.PUNCH_IMPACT_TICK} with the shoulders turning into it and the lead foot
	 * forward, the other hand up in a guard, then back. Twisting the body moves the shoulder pivots the way vanilla's own
	 * attack swing does. Returns {weight, barrel pitch (rad), barrel yaw relative to the body (deg), grip lift factor}.
	 */
	private static float[] punchPose(HumanoidModel<?> m, ModelPart main, ModelPart off, boolean right, float s, float t) {
		float w = GunAnim.seg(t, 0f, 1.2f) * (1f - GunAnim.seg(t, 4.5f, 8f));
		float twist = GunAnim.keys(t, 0f, 0f, 1.2f, 0.3f, 2.2f, -0.5f, 4f, -0.45f, 8f, 0f) * s;
		float armX = GunAnim.keys(t, 0f, -0.5f, 1.2f, -0.45f, 2.2f, -1.66f, 4f, -1.6f, 8f, -0.6f);
		float armY = GunAnim.keys(t, 0f, 0f, 1.2f, 0.12f, 2.2f, -0.08f, 8f, 0f) * s;
		m.body.yRot = twist;
		main.xRot = Mth.lerp(w, main.xRot, armX);
		main.yRot = Mth.lerp(w, main.yRot, armY + twist);
		main.zRot = Mth.lerp(w, main.zRot, 0f);
		off.xRot = Mth.lerp(w, off.xRot, -1.35f);
		off.yRot = Mth.lerp(w, off.yRot, 0.6f * s + twist);
		off.zRot = Mth.lerp(w, off.zRot, 0f);
		shoulders(m, right, twist);
		ModelPart lead = right ? m.leftLeg : m.rightLeg;
		ModelPart rear = right ? m.rightLeg : m.leftLeg;
		lead.xRot = Mth.lerp(w, lead.xRot, -0.3f);
		rear.xRot = Mth.lerp(w, rear.xRot, 0.32f);
		return new float[] { w, 0.1f, (armY + twist) * Mth.RAD_TO_DEG, -0.8f };
	}

	/**
	 * v0.15.18 Breach Kick: a high front kick -- the leg chambers, snaps out forward and up at
	 * {@code PunisherMelee.KICK_IMPACT_TICK}, holds, and comes down; the arms swing out for balance and the body leans back
	 * ({@link #meleeLean}). Returns {weight, barrel pitch (rad), barrel yaw relative to the body (deg), grip lift factor}.
	 */
	private static float[] kickPose(HumanoidModel<?> m, ModelPart main, ModelPart off, boolean right, float s, float t) {
		float w = GunAnim.seg(t, 0f, 1.5f) * (1f - GunAnim.seg(t, 7f, 11f));
		ModelPart kick = right ? m.rightLeg : m.leftLeg;
		ModelPart stand = right ? m.leftLeg : m.rightLeg;
		float legX = GunAnim.keys(t, 0f, 0f, 2f, -1.25f, 3f, -2.05f, 5.5f, -1.95f, 8.5f, -0.6f, 11f, 0f);
		kick.xRot = Mth.lerp(GunAnim.seg(t, 0f, 0.6f) * (1f - GunAnim.seg(t, 10f, 11f)), kick.xRot, legX);
		kick.yRot = 0f;
		kick.zRot = Mth.lerp(w, kick.zRot, 0.06f * s);
		stand.xRot = Mth.lerp(w, stand.xRot, 0.18f);
		stand.yRot = Mth.lerp(w, stand.yRot, 0f);
		main.xRot = Mth.lerp(w, main.xRot, -0.3f);
		main.yRot = Mth.lerp(w, main.yRot, 0f);
		main.zRot = Mth.lerp(w, main.zRot, 1.4f * s);
		off.xRot = Mth.lerp(w, off.xRot, -0.45f);
		off.yRot = Mth.lerp(w, off.yRot, 0f);
		off.zRot = Mth.lerp(w, off.zRot, -1.3f * s);
		m.head.xRot = Mth.lerp(w * 0.5f, m.head.xRot, 0.15f);
		return new float[] { w, 0.9f, 55f * s, 1f };
	}

	/** Swing the shoulder pivots round with a body twist, as {@code HumanoidModel#setupAttackAnimation} does. */
	private static void shoulders(HumanoidModel<?> m, boolean right, float twist) {
		m.rightArm.z = Mth.sin(twist) * 5f;
		m.rightArm.x = -Mth.cos(twist) * 5f;
		m.leftArm.z = -Mth.sin(twist) * 5f;
		m.leftArm.x = Mth.cos(twist) * 5f;
	}

	/**
	 * v0.15.18: whole-body lean for {@code PlayerRendererMixin} during the punch (forward into it) / kick (back, away from
	 * the kicking leg), in degrees about the feet, positive = back. 0 when neither plays.
	 */
	public static float meleeLean(Player player, float partialTick) {
		float t = GunAnim.melee(player, partialTick);
		if (t < 0f) {
			return 0f;
		}
		if (GunAnim.meleeKind(player) == com.projecthero.mod.punisher.ability.PunisherMelee.ANIM_KICK) {
			return GunAnim.keys(t, 0f, 0f, 2f, 5f, 3f, 11f, 5.5f, 10f, 11f, 0f);
		}
		return GunAnim.keys(t, 0f, 0f, 1.2f, 2f, 2.2f, -9f, 4f, -8f, 8f, 0f);
	}

	/**
	 * Tactical Roll body tumble for {@code PlayerRendererMixin}: {axis (0 = pitch, 1 = bank), degrees, drop}. Null when
	 * not rolling.
	 */
	public static float[] rollAngle(Player player, float partialTick) {
		float r = GunAnim.rolling(player, partialTick);
		if (r < 0f) {
			return null;
		}
		float e = GunAnim.smooth(r);
		int dir = GunAnim.rollDirectionOf(player);
		float deg = 360f * e;
		float drop = Mth.sin(r * (float) Math.PI) * 0.55f;
		return switch (dir) {
			case 1 -> new float[] { 0f, deg, drop };
			case 2 -> new float[] { 1f, -deg, drop };
			case 3 -> new float[] { 1f, deg, drop };
			default -> new float[] { 0f, -deg, drop };
		};
	}
}
