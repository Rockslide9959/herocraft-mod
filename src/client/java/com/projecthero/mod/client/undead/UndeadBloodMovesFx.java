package com.projecthero.mod.client.undead;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.undead.Undead;
import com.projecthero.mod.undead.UndeadBloodMoves;
import com.projecthero.mod.undead.UndeadEjectConfig;
import com.projecthero.mod.undead.data.UndeadState;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import org.joml.Matrix4f;

/**
 * v0.17.3: the streams of Undead's blood moves, for every viewer, from the synced state:
 * <ul>
 *   <li><b>Blood Spray</b>: two pulsing ropes of blood out of the forearm stumps that fan out into a sheet two blocks wide along
 *       the aim, stopped by the first wall (where it splashes back and runs down); the spray off it is {@link BloodFx} drops
 *       flung down the stream, which splat where they land.</li>
 *   <li><b>Blood Whirlwind</b>: as he spins, the two arm streams wind out into a spiral (each parcel leaves along the arm as it
 *       was when it left), throwing drops out to the whole 10 blocks.</li>
 *   <li><b>Combustion Punch</b>: the burst of blood on the punch.</li>
 * </ul>
 * In his own first-person view the streams start at the bottom corners of the screen, thin there, so they never cover it.
 */
public final class UndeadBloodMovesFx {
	private static final RandomSource RND = RandomSource.create();
	/** Per player: the punch's animStart already burst. */
	private static final Map<UUID, Long> SEEN_PUNCH = new HashMap<>();

	private static final int TOP = 0xB0181C, SIDE = 0x720A0F, BOTTOM = 0x3A040A;

	private UndeadBloodMovesFx() {
	}

	static void reset() {
		SEEN_PUNCH.clear();
	}

	// ---------------------------------------------------------------- where the streams start

	/** The player is this client's own player, seen from his own eyes. */
	static boolean firstPersonSelf(Player player) {
		Minecraft mc = Minecraft.getInstance();
		return player == mc.player && mc.options.getCameraType().isFirstPerson();
	}

	/** The forearm stump a stream leaves: on his body for others; low in the corners of the screen in his own view. */
	static Vec3 stump(Player player, int arm, Vec3 eye, Vec3 look) {
		if (firstPersonSelf(player)) {
			Vec3 right = new Vec3(-look.z, 0, look.x);
			right = right.lengthSqr() < 1.0e-6 ? new Vec3(1, 0, 0) : right.normalize();
			Vec3 up = right.cross(look).normalize();
			double sd = arm == UndeadState.RIGHT_ARM ? 1 : -1;
			return eye.add(look.scale(0.72)).add(right.scale(0.3 * sd)).add(up.scale(-0.36)); // (right: his right, -x facing +z)
		}
		return UndeadBloodMoves.stumpOrigin(player, arm, eye, look);
	}

	// ---------------------------------------------------------------- tick: the drops

	static void tick(Player player, UndeadState s, long now) {
		if (player.level() == null) {
			return;
		}
		if (s.flag(UndeadState.FLAG_BLOOD_SPRAY)) {
			spraySpray(player, s, now);
		}
		float age = now - s.animStart;
		if (s.animId == UndeadState.ANIM_BLOOD_WHIRL && age >= 0f && age < UndeadEjectConfig.WHIRL_TICKS) {
			whirlSpray(player, s, age);
		}
		if (s.animId == UndeadState.ANIM_COMBUSTION_PUNCH && age >= 0f && age < 3f) {
			Long seen = SEEN_PUNCH.put(player.getUUID(), s.animStart);
			if (seen == null || seen != s.animStart) {
				Vec3 look = player.getLookAngle();
				Vec3 fist = player.position().add(0, Math.min(player.getBbHeight() * 0.5, 0.9), 0).add(look.scale(0.9));
				BloodFx.eruption(fist, look.scale(-1).add(0, 0.5, 0), Vec3.ZERO, firstPersonSelf(player) ? 0.5f : 1.6f);
				BloodFx.spurt(fist, look, 26, 0.45, 0.6, Vec3.ZERO);
				BloodFx.mist(fist, 6, 0.45f, Vec3.ZERO);
			}
		}
	}

	private static void spraySpray(Player player, UndeadState s, long now) {
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		Vec3[] axis = UndeadBloodMoves.sprayAxis(player, eye, look);
		double len = axis[0].distanceTo(axis[1]);
		boolean wall = len < UndeadEjectConfig.SPRAY_RANGE - 0.05;
		Vec3 right = new Vec3(-look.z, 0, look.x);
		right = right.lengthSqr() < 1.0e-6 ? new Vec3(1, 0, 0) : right.normalize();
		Vec3 up = right.cross(look).normalize();
		float p = UndeadJets.pulse(now, true);
		double rEnd = UndeadBloodMoves.sprayRadius(len / UndeadEjectConfig.SPRAY_RANGE);
		Vec3 carry = UndeadJets.travel(player);
		boolean fp = firstPersonSelf(player);
		for (int arm = UndeadState.RIGHT_ARM; arm <= UndeadState.LEFT_ARM; arm++) {
			if (s.broken(arm)) {
				continue;
			}
			Vec3 from = stump(player, arm, eye, look);
			int n = (fp ? 4 : 6) + (int) (5 * p);
			for (int i = 0; i < n; i++) {
				// at a random point across the far end of the stream, so the drops fill the 2-block cone
				double a = RND.nextDouble() * Math.PI * 2, r = Math.sqrt(RND.nextDouble()) * rEnd;
				Vec3 target = axis[1].add(right.scale(Math.cos(a) * r)).add(up.scale(Math.sin(a) * r * 0.75));
				Vec3 dir = target.subtract(from);
				double d = dir.length();
				if (d < 0.2) {
					continue;
				}
				// fast enough to reach the end in ~8 ticks against the drops' drag; a little gravity sag on the way
				double speed = Math.min(1.9, d / 6.2) * (0.85 + RND.nextDouble() * 0.3);
				Vec3 v = dir.scale(1.0 / d).scale(speed).add(0, 0.02 * d / 10.0, 0);
				int shade = BloodFx.shade(RND.nextFloat());
				BloodFx.drop(from.x, from.y, from.z, v.x + carry.x, v.y + carry.y, v.z + carry.z, 0.03f + RND.nextFloat() * 0.04f, shade, 9 + RND.nextInt(6), 0);
			}
		}
		// where it ends: splashing back off a wall (and running down it), or raining out of the air at full range
		Vec3 end = axis[1];
		if (wall) {
			BloodFx.spurt(end.subtract(look.scale(0.15)), look.scale(-1).add(up.scale(0.6)), 4 + (int) (4 * p), 0.22, 0.75, Vec3.ZERO);
			if (now % 2 == 0) {
				BloodFx.mist(end.subtract(look.scale(0.3)), 1, 0.35f + 0.2f * p, Vec3.ZERO);
			}
		} else if (now % 2 == 0) {
			BloodFx.spurt(end, look.add(0, -0.4, 0), 3, 0.18, 0.5, Vec3.ZERO);
		}
	}

	/** The world yaw (degrees) his arms' line is at during the whirl, {@code age} ticks in (the body's yaw plus the spin). */
	static float whirlYaw(Player player, float age, float partial) {
		float body = Mth.rotLerp(partial, player.yBodyRotO, player.yBodyRot);
		return body + (float) Math.toDegrees(UndeadBloodMoves.whirlAngle(age));
	}

	/** Where an arm's stump is during the whirl (arms straight out to the sides at shoulder height). */
	static Vec3 whirlStump(Vec3 pos, float yawDeg, int arm) {
		float a = (yawDeg + (arm == UndeadState.RIGHT_ARM ? 90f : -90f)) * Mth.DEG_TO_RAD;
		Vec3 out = new Vec3(-Mth.sin(a), 0, Mth.cos(a));
		return pos.add(0, 1.3, 0).add(out.scale(0.32 + UndeadEjectConfig.EJECT_ROWS / 16.0 * 0.9375));
	}

	static Vec3 whirlOut(float yawDeg, int arm) {
		float a = (yawDeg + (arm == UndeadState.RIGHT_ARM ? 90f : -90f)) * Mth.DEG_TO_RAD;
		return new Vec3(-Mth.sin(a), 0, Mth.cos(a));
	}

	private static void whirlSpray(Player player, UndeadState s, float age) {
		if (age < UndeadEjectConfig.WHIRL_RISE_TICKS * 0.5f) {
			return;
		}
		float yaw = whirlYaw(player, age, 0f);
		Vec3 pos = player.position();
		float k = Mth.clamp((age - UndeadEjectConfig.WHIRL_RISE_TICKS * 0.5f) / 4f, 0f, 1f);
		for (int arm = UndeadState.RIGHT_ARM; arm <= UndeadState.LEFT_ARM; arm++) {
			if (s.broken(arm)) {
				continue;
			}
			Vec3 at = whirlStump(pos, yaw, arm);
			// several sub-steps of the turn this tick, so the stream winds out as a continuous spiral, not spokes
			for (int sub = 0; sub < 4; sub++) {
				float y = whirlYaw(player, age - sub * 0.25f, 0f);
				Vec3 out = whirlOut(y, arm);
				int n = (int) ((2 + RND.nextInt(2)) * k);
				for (int i = 0; i < n; i++) {
					double speed = 0.75 + RND.nextDouble() * 0.45;
					Vec3 v = out.scale(speed).add((RND.nextDouble() - 0.5) * 0.08, -0.02 + RND.nextDouble() * 0.06, (RND.nextDouble() - 0.5) * 0.08);
					BloodFx.drop(at.x, at.y, at.z, v.x, v.y, v.z, 0.03f + RND.nextFloat() * 0.035f, BloodFx.shade(RND.nextFloat()), 14 + RND.nextInt(10), 0);
				}
			}
			if (RND.nextFloat() < 0.3f * k) {
				BloodFx.mist(at.add(whirlOut(yaw, arm).scale(0.6)), 1, 0.25f, Vec3.ZERO);
			}
		}
	}

	// ---------------------------------------------------------------- render: the streams

	static void render(WorldRenderContext context, PoseStack pose, Vec3 cam, Minecraft mc, float partial) {
		VertexConsumer vc = null;
		float now = mc.level.getGameTime() + partial;
		for (Player player : mc.level.players()) {
			UndeadState s = Undead.peek(player);
			if (s == null || !s.hasPower) {
				continue;
			}
			float age = now - s.animStart;
			boolean spray = s.flag(UndeadState.FLAG_BLOOD_SPRAY);
			boolean whirl = s.animId == UndeadState.ANIM_BLOOD_WHIRL && age >= UndeadEjectConfig.WHIRL_RISE_TICKS * 0.5f && age < UndeadEjectConfig.WHIRL_TICKS;
			if (!spray && !whirl) {
				continue;
			}
			if (vc == null) {
				vc = context.consumers().getBuffer(RenderType.entityCutoutNoCull(UndeadRender.PALETTE));
			}
			if (spray) {
				sprayStream(vc, pose, cam, mc, player, s, now, partial);
			}
			if (whirl) {
				whirlStream(vc, pose, cam, mc, player, s, age, partial);
			}
		}
	}

	private static int light(Minecraft mc, Vec3 at) {
		return LevelRenderer.getLightColor(mc.level, BlockPos.containing(at));
	}

	private static void sprayStream(VertexConsumer vc, PoseStack pose, Vec3 cam, Minecraft mc, Player player, UndeadState s, float now, float partial) {
		Vec3 eye = player.getEyePosition(partial);
		Vec3 look = player.getViewVector(partial);
		Vec3[] axis = UndeadBloodMoves.sprayAxis(player, eye, look);
		double len = axis[0].distanceTo(axis[1]);
		Vec3 right = new Vec3(-look.z, 0, look.x);
		right = right.lengthSqr() < 1.0e-6 ? new Vec3(1, 0, 0) : right.normalize();
		Vec3 up = right.cross(look).normalize();
		double rEnd = UndeadBloodMoves.sprayRadius(len / UndeadEjectConfig.SPRAY_RANGE);
		PoseStack.Pose last = pose.last();
		Matrix4f m = last.pose();
		boolean fp = firstPersonSelf(player);
		int lightEnd = light(mc, axis[1].subtract(look.scale(0.3)));
		for (int arm = UndeadState.RIGHT_ARM; arm <= UndeadState.LEFT_ARM; arm++) {
			if (s.broken(arm)) {
				continue;
			}
			Vec3 from = stump(player, arm, eye, look);
			double sd = arm == UndeadState.RIGHT_ARM ? 1 : -1;
			// strands: a core, then ones fanning out toward the edges (and up / down) of the 2-block end; the far ones thinner
			int strands = fp ? 3 : 5;
			for (int k = 0; k < strands; k++) {
				double lat = k == 0 ? 0.0 : (k % 2 == 1 ? 1 : -1) * (k <= 2 ? 0.45 : 0.9);
				double vert = k == 0 ? 0.0 : (k <= 2 ? 0.35 : -0.4);
				// each arm's strands lean to its own side of the cone
				Vec3 target = axis[1].add(right.scale((lat * 0.8 + 0.25 * sd) * rEnd)).add(up.scale(vert * rEnd));
				int seed = player.getId() * 31 + arm * 7 + k;
				strand(vc, last, m, cam, from, target, look, now, seed, k == 0 ? 1f : 0.62f, fp, light(mc, from), lightEnd);
			}
		}
	}

	/**
	 * One strand of the spray from {@code a} to {@code b}: a chain of tapered boxes that swells from the stump outward with
	 * bulges that run along it (the pulse), wavering and sagging a little.
	 */
	private static void strand(VertexConsumer vc, PoseStack.Pose last, Matrix4f m, Vec3 cam, Vec3 a, Vec3 b, Vec3 look, float now, int seed, float thick,
			boolean fp, int lightA, int lightB) {
		int n = 14;
		Vec3 d = b.subtract(a);
		double len = d.length();
		Vec3 side = new Vec3(-look.z, 0, look.x);
		side = side.lengthSqr() < 1.0e-6 ? new Vec3(1, 0, 0) : side.normalize();
		Vec3 up = side.cross(look).normalize();
		float px = 0, py = 0, pz = 0, pw = 0;
		for (int i = 0; i <= n; i++) {
			float t = i / (float) n;
			double sag = 0.18 * t * t * len / 10.0;
			float wob = Mth.sin(now * 0.9f + seed * 1.7f + t * 6f) * 0.07f * t, wob2 = Mth.cos(now * 1.1f + seed * 2.3f + t * 5f) * 0.06f * t;
			Vec3 p = a.add(d.scale(t)).add(side.scale(wob)).add(up.scale(wob2)).add(0, -sag, 0);
			// bulges running out along the stream with the heart's beat
			float run = Mth.sin((float) (t * len * 1.6 - now * 1.25) + seed);
			float base = fp ? 0.02f + 0.06f * t : 0.05f + 0.07f * t;
			float w = base * thick * (1f + 0.35f * Math.max(0f, run));
			float x = (float) (p.x - cam.x), y = (float) (p.y - cam.y), z = (float) (p.z - cam.z);
			// past half way the strand breaks up into slugs that run out with the flow (gaps travel along it)
			boolean gap = t > 0.5f && Mth.sin((float) (t * len * 2.4 - now * 1.7) + seed * 2.9f) < -0.25f - 0.6f * (1f - t);
			if (i > 0 && !(fp && i <= 1) && !gap) {
				int lt = t < 0.5f ? lightA : lightB;
				UndeadJets.segment(vc, last, m, px, py, pz, pw, x, y, z, w, TOP, SIDE, BOTTOM, lt, w > 0.03f);
			}
			px = x;
			py = y;
			pz = z;
			pw = w;
		}
	}

	private static void whirlStream(VertexConsumer vc, PoseStack pose, Vec3 cam, Minecraft mc, Player player, UndeadState s, float age, float partial) {
		Vec3 pos = player.getPosition(partial);
		PoseStack.Pose last = pose.last();
		Matrix4f m = last.pose();
		int lt = light(mc, pos.add(0, 1.3, 0));
		for (int arm = UndeadState.RIGHT_ARM; arm <= UndeadState.LEFT_ARM; arm++) {
			if (s.broken(arm)) {
				continue;
			}
			// parcels left the stump at earlier moments of the turn: each one flies out along the arm as it was then
			// a short rope out of the stump that breaks up as it winds out (the drops carry the spiral on to 10 blocks)
			int n = 12;
			float px = 0, py = 0, pz = 0, pw = 0;
			for (int i = 0; i <= n; i++) {
				float back = i * 0.42f;
				float y = whirlYaw(player, age - back, partial);
				Vec3 from = whirlStump(pos, y, arm);
				Vec3 out = whirlOut(y, arm);
				double dist = back * 0.8;
				Vec3 p = from.add(out.scale(dist)).add(0, -0.5 * 0.045 * back * back + 0.02 * back, 0);
				float w = (0.045f + 0.006f * back) * (1f + 0.3f * Mth.sin(back * 2.1f - age)) * (1f - 0.5f * i / (float) n);
				float x = (float) (p.x - cam.x), yy = (float) (p.y - cam.y), z = (float) (p.z - cam.z);
				boolean gap = i > n / 2 && Mth.sin(back * 3.1f - age * 1.4f + arm) < -0.2f;
				if (i > 0 && !gap) {
					UndeadJets.segment(vc, last, m, px, py, pz, pw, x, yy, z, w, TOP, SIDE, BOTTOM, lt, w > 0.03f);
				}
				px = x;
				py = yy;
				pz = z;
				pw = w;
			}
		}
	}
}
