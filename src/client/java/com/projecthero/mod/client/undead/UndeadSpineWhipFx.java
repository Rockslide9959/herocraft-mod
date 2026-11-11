package com.projecthero.mod.client.undead;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projecthero.mod.undead.Undead;
import com.projecthero.mod.undead.UndeadKitConfig;
import com.projecthero.mod.undead.UndeadKitNet;
import com.projecthero.mod.undead.UndeadSpineWhip;
import com.projecthero.mod.undead.data.UndeadState;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import org.joml.Vector3f;

/**
 * v0.17.3 tap Shift+V, Spine Whip (server: {@code undead.UndeadSpineWhip}) -- the client side, for every viewer, driven by the
 * synced animation ({@link UndeadKitConfig#ANIM_SPINE_WHIP} and its start) plus one packet for the snag:
 * <ul>
 * <li>the tear ({@link UndeadKitConfig#SPINE_WINDUP}): he reaches over his shoulder and hauls his spine out through a split down
 * his back, the vertebrae coming out one after another, blood where it tears;</li>
 * <li>the lash ({@link UndeadKitConfig#SPINE_SWEEP}): the spine flies out to its full length as a segmented whip of vertebrae --
 * each a body of bone with a spike up its back and a wing either side, a dark disc between them, getting smaller toward a hooked,
 * bloody tail-bone barb -- and sweeps across in front of him, the tip trailing the hand; a creature the tip snags
 * ({@link UndeadKitNet#SPINE_SNAG}) has the whip pulled taut to it as it is yanked in;</li>
 * <li>after ({@link UndeadKitConfig#SPINE_AFTER}): the whip goes slack and drops, he lets go and it crumbles from the tip back;</li>
 * <li>the split in his back ({@link #backWound}, third person, on the body) closing like a regrowing part.</li>
 * </ul>
 * The poses are {@link #pose} (third person) and {@link #firstPerson}. Fed by {@link UndeadKitFx} (watch, tick, render).
 */
public final class UndeadSpineWhipFx {
	private static final RandomSource RND = RandomSource.create();

	/** Vertebrae in the whip, hand to tip. */
	static final int SEGS = 24;

	/** player -> the start of the whip being drawn. */
	private static final Map<UUID, Long> WHIPS = new HashMap<>();
	/** player -> the start of the whip whose split in the back is still showing. */
	private static final Map<UUID, Long> WOUNDS = new HashMap<>();
	private static final Map<UUID, Long> SEEN = new HashMap<>();

	private record Snag(int entity, long at) {
	}

	/** whipper -> what the tip snagged. */
	private static final Map<UUID, Snag> SNAGS = new HashMap<>();

	private UndeadSpineWhipFx() {
	}

	static void reset() {
		WHIPS.clear();
		WOUNDS.clear();
		SEEN.clear();
		SNAGS.clear();
	}

	private static long now() {
		Minecraft mc = Minecraft.getInstance();
		return mc.level == null ? 0L : mc.level.getGameTime();
	}

	private static int whipLife() {
		return UndeadKitConfig.SPINE_WINDUP + UndeadKitConfig.SPINE_SWEEP + UndeadKitConfig.SPINE_AFTER;
	}

	static int woundLife(Player p) {
		return UndeadKitConfig.SPINE_WINDUP + Undead.regrowDelay(p) + Undead.regrowTicks(p, UndeadState.BODY);
	}

	private static boolean ownView(Player p) {
		Minecraft mc = Minecraft.getInstance();
		return p == mc.player && mc.options.getCameraType().isFirstPerson();
	}

	// ---------------------------------------------------------------- watching

	static void watch(Player p, UndeadState s, long now) {
		UUID id = p.getUUID();
		Long seen = SEEN.put(id, s.animStart);
		boolean fresh = seen == null || seen != s.animStart;
		if (fresh && s.animId == UndeadKitConfig.ANIM_SPINE_WHIP && now - s.animStart < UndeadKitConfig.SPINE_WINDUP) {
			WHIPS.put(id, s.animStart);
			WOUNDS.put(id, s.animStart);
			SNAGS.remove(id);
		} else if (fresh && s.animId == UndeadState.ANIM_REFORM) {
			WOUNDS.remove(id); // forced back together at once
			WHIPS.remove(id);
		}
		Long w = WHIPS.get(id);
		if (w != null) {
			long age = now - w;
			// broken off before the lash (the server dropped the animation), or done
			if (age > whipLife() + 1 || age < UndeadKitConfig.SPINE_WINDUP && s.animId != UndeadKitConfig.ANIM_SPINE_WHIP || !s.hasPower) {
				WHIPS.remove(id);
				if (age < UndeadKitConfig.SPINE_WINDUP) {
					WOUNDS.remove(id);
				}
			}
		}
		Long wd = WOUNDS.get(id);
		if (wd != null && (s.broken(UndeadState.BODY) || now - wd > woundLife(p) + 2 || !s.hasPower)) {
			WOUNDS.remove(id);
		}
	}

	/** The snag packet: whose whip it was (the Undead standing at {@code b}) and what it caught. */
	static void onSnag(UndeadKitNet.Fx fx) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) {
			return;
		}
		Player best = null;
		double bd = 4.0;
		for (Player p : mc.level.players()) {
			double d = p.position().distanceToSqr(fx.b());
			if (WHIPS.containsKey(p.getUUID()) && d < bd) {
				best = p;
				bd = d;
			}
		}
		if (best != null) {
			SNAGS.put(best.getUUID(), new Snag(fx.extra(), now()));
		}
		Vec3 dir = fx.b().add(0, 1, 0).subtract(fx.a());
		BloodFx.spurt(fx.a(), dir.lengthSqr() > 1.0e-4 ? dir.normalize() : new Vec3(0, 1, 0), 6, 0.2, 0.35, Vec3.ZERO);
		UndeadSwordFx.bone(fx.a(), new Vec3(0, 0.08, 0), 4, 0.08);
	}

	// ---------------------------------------------------------------- the whip's shape

	private static float ease(float t) {
		t = Mth.clamp(t, 0f, 1f);
		return t * t * (3f - 2f * t);
	}

	private static Vec3 dir(Vec3 f, Vec3 right, float deg) {
		double a = Math.toRadians(deg);
		return f.scale(Math.cos(a)).add(right.scale(Math.sin(a)));
	}

	/** The whip's frame for {@code p}: {flat forward, right, the chest, the feet}. */
	private static Vec3[] frame(Player p, float partial) {
		float yaw = p.getViewYRot(partial) * Mth.DEG_TO_RAD;
		Vec3 f = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
		Vec3 right = f.cross(new Vec3(0, 1, 0));
		Vec3 feet = p.getPosition(partial);
		return new Vec3[] { f, right, feet.add(0, p.getBbHeight() * 0.62, 0), feet };
	}

	/**
	 * Where the whip hand is {@code age} ticks in (world): over his shoulder, hauling it up over his head, then swung across in
	 * front of him, then dropped at the far side. Seen from his own eyes, low in front of the view where the hand is drawn.
	 */
	private static Vec3 hand(Player p, float age, float sd, Vec3[] fr, boolean own) {
		Vec3 f = fr[0], right = fr[1], chest = fr[2];
		Vec3 up = new Vec3(0, 1, 0);
		int w = UndeadKitConfig.SPINE_WINDUP, sw = UndeadKitConfig.SPINE_SWEEP;
		if (age < w) {
			float reach = ease(age / 2.5f), pull = ease((age - 2.5f) / (w - 2.5f));
			Vec3 behind = chest.add(right.scale(sd * 0.22)).add(up.scale(0.55 + 0.2 * reach)).subtract(f.scale(0.25));
			Vec3 over = chest.add(right.scale(sd * 0.3)).add(up.scale(0.95)).add(f.scale(0.25));
			return behind.lerp(over, pull);
		}
		float tl = (age - w + 1f) / sw;
		if (tl <= 1.15f) {
			float a = UndeadSpineWhip.angle(sd, tl + 0.15f);
			if (own) {
				return p.getEyePosition().add(dir(f, right, a).scale(0.55)).add(0, -0.5, 0);
			}
			return chest.add(up.scale(0.12)).add(right.scale(sd * 0.12)).add(dir(f, right, a).scale(0.62));
		}
		float ta = Mth.clamp((age - w - sw) / UndeadKitConfig.SPINE_AFTER, 0f, 1f);
		float a = UndeadSpineWhip.angle(sd, 1f) * (1f - 0.3f * ta);
		Vec3 base = own ? p.getEyePosition().add(dir(f, right, a).scale(0.55)).add(0, -0.5, 0) : chest.add(up.scale(0.12)).add(dir(f, right, a).scale(0.62));
		return base.add(0, -0.45 * ease(ta / 0.5f), 0);
	}

	/**
	 * The whip's points (world, hand first) {@code age} ticks in, and how many of its segments are still there (it crumbles
	 * from the tip back once he lets go). Null when there is nothing to draw.
	 */
	private static Vec3[] chain(Player p, UndeadState s, float age, float partial, boolean own, int[] count) {
		Vec3[] fr = frame(p, partial);
		Vec3 f = fr[0], right = fr[1], feet = fr[3];
		Vec3 up = new Vec3(0, 1, 0);
		float sd = UndeadSpineWhip.side(p, s);
		int w = UndeadKitConfig.SPINE_WINDUP, sw = UndeadKitConfig.SPINE_SWEEP;
		double seg = UndeadKitConfig.SPINE_RANGE / SEGS;
		Vec3 h = hand(p, age, sd, fr, own);
		Vec3[] pts = new Vec3[SEGS + 1];
		pts[0] = h;
		count[0] = SEGS;
		if (age < w) {
			if (own) {
				return null; // (over his own shoulder it would be right in the camera)
			}
			// coming out of the split in his back, up over his shoulder to the hand; torn free near the end, the rest swinging out
			Vec3 back = fr[2].subtract(f.scale(0.14)).add(0, -0.05, 0);
			float free = Mth.clamp((age - (w - 2f)) / 2f, 0f, 1f);
			Vec3 end = back.add(f.scale(-0.35 * free)).add(up.scale(-0.45 * free)).add(right.scale(-sd * 0.3 * free));
			Vec3 ctrl = back.subtract(f.scale(0.45)).add(up.scale(0.45));
			double len = h.distanceTo(ctrl) + ctrl.distanceTo(end);
			int n = Mth.clamp((int) Math.ceil(len / seg * (0.6 + 0.6 * free)), 2, 9);
			count[0] = n;
			for (int i = 1; i <= SEGS; i++) {
				double u = Math.min(1.0, i / (double) n);
				// quadratic from the hand through ctrl to the end
				Vec3 a = h.lerp(ctrl, u), b = ctrl.lerp(end, u);
				pts[i] = a.lerp(b, u);
			}
			return pts;
		}
		Snag sn = SNAGS.get(p.getUUID());
		Entity caught = sn != null && Minecraft.getInstance().level != null ? Minecraft.getInstance().level.getEntity(sn.entity()) : null;
		float ta = Mth.clamp((age - w - sw) / UndeadKitConfig.SPINE_AFTER, 0f, 1f);
		float crumble = Mth.clamp((ta - 0.3f) / 0.7f, 0f, 1f);
		count[0] = Math.max(0, Math.round(SEGS * (1f - crumble)));
		Vec3[] taut = null;
		float pull = 0f;
		if (caught != null && caught.isAlive() && now() - sn.at() < 12) {
			// pulled taut to what it caught as it comes in, a little slack in the middle (blended in from the swing)
			Vec3 to = caught.getPosition(partial).add(0, caught.getBbHeight() * 0.55, 0);
			float since = now() - sn.at() + partial;
			float slack = 0.12f + 0.25f * Mth.clamp(since / 8f, 0f, 1f);
			pull = ease(since / 2f);
			taut = new Vec3[SEGS + 1];
			for (int i = 1; i <= SEGS; i++) {
				double u = i / (double) SEGS;
				taut[i] = h.lerp(to, u).add(0, -slack * Math.sin(u * Math.PI) - 0.6 * ta * u, 0);
			}
		}
		float tl = (age - w + 1f) / sw;
		float extend = Math.min(1f, 0.3f + 0.7f * ease((age - w + 0.6f) / 1.6f));
		double floor = feet.y + 0.06;
		for (int i = 1; i <= SEGS; i++) {
			float u = i / (float) SEGS;
			// the tip trails the hand, so the whip bows back against the swing until it snaps straight at the end
			float a = UndeadSpineWhip.angle(sd, tl - 0.5f * u);
			double drop = seg * extend * (0.02 + 0.07 * u);
			Vec3 next = pts[i - 1].add(dir(f, right, a).scale(seg * extend)).add(0, -drop, 0);
			// going slack after the lash: it sags to the ground, the tip first
			next = next.add(0, -1.3 * ta * ta * u - 0.2 * ta, 0);
			if (next.y < floor) {
				next = new Vec3(next.x, floor, next.z);
			}
			pts[i] = next;
		}
		if (taut != null) {
			for (int i = 1; i <= SEGS; i++) {
				pts[i] = pts[i].lerp(taut[i], pull);
			}
		}
		return pts;
	}

	// ---------------------------------------------------------------- ticking

	static void tick(long now) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) {
			reset();
			return;
		}
		SNAGS.values().removeIf(sn -> now - sn.at() > 40);
		int[] count = new int[1];
		int[] before = new int[1];
		for (Iterator<Map.Entry<UUID, Long>> it = WHIPS.entrySet().iterator(); it.hasNext();) {
			Map.Entry<UUID, Long> en = it.next();
			Player p = mc.level.getPlayerByUUID(en.getKey());
			UndeadState s = p == null ? null : Undead.peek(p);
			if (p == null || s == null) {
				it.remove();
				continue;
			}
			long age = now - en.getValue();
			boolean own = ownView(p);
			Vec3 carry = p.getDeltaMovement();
			Vec3[] fr = frame(p, 0f);
			Vec3 back = fr[2].subtract(fr[0].scale(0.15));
			int w = UndeadKitConfig.SPINE_WINDUP;
			if (!own && age >= 1 && age < w && age % 2 == 1) {
				// blood where the vertebrae tear out of his back
				BloodFx.spurt(back.add(0, 0.25 - age * 0.04, 0), fr[0].scale(-1).add(0, 0.8, 0), 1, 0.06, 0.3, carry);
			}
			if (!own && age == w - 2) {
				// torn free
				BloodFx.spurt(back.add(0, -0.15, 0), fr[0].scale(-1).add(0, 0.5, 0), 3, 0.15, 0.35, carry);
				UndeadSwordFx.bone(back, fr[0].scale(-0.1), 2, 0.07);
			}
			Vec3[] pts = chain(p, s, age, 0f, own, count);
			Vec3[] prev = age > 0 ? chain(p, s, age - 1, 0f, own, before) : null;
			if (pts != null && age >= w && age < w + UndeadKitConfig.SPINE_SWEEP + 1) {
				// drops flung off the whip as it cracks across
				for (int k = 0; k < 3; k++) {
					int i = 6 + RND.nextInt(SEGS - 6);
					Vec3 at = pts[i];
					Vec3 v = prev != null ? at.subtract(prev[i]).scale(0.25) : Vec3.ZERO;
					BloodFx.drop(at.x, at.y, at.z, v.x, v.y + 0.04, v.z, 0.025f + RND.nextFloat() * 0.025f, BloodFx.shade(RND.nextFloat()), 16 + RND.nextInt(10), 0);
				}
			}
			if (pts != null && prev != null && count[0] < before[0]) {
				// crumbling from the tip back: bone dust where each vertebra goes
				for (int i = count[0]; i < before[0] && i < SEGS; i++) {
					if ((i & 1) == 0) {
						UndeadSwordFx.bone(pts[i + 1], new Vec3(0, 0.03, 0), 1, 0.05);
					}
				}
			}
			if (age > whipLife() + 1) {
				it.remove();
			}
		}
		for (Map.Entry<UUID, Long> en : WOUNDS.entrySet()) {
			Player p = mc.level.getPlayerByUUID(en.getKey());
			if (p == null || ownView(p)) {
				continue;
			}
			long age = now - en.getValue();
			int w = UndeadKitConfig.SPINE_WINDUP;
			if (age > w && age < w + Undead.regrowDelay(p) && age % 6 == 0) {
				Vec3[] fr = frame(p, 0f);
				BloodFx.drip(fr[2].subtract(fr[0].scale(0.16)).add(0, -0.1 - RND.nextDouble() * 0.4, 0));
			}
		}
	}

	// ---------------------------------------------------------------- drawing: the whip (world)

	/** From {@link UndeadKitFx#render} (in its "solid" pass): every whip in the world. */
	static void render(PoseStack pose, MultiBufferSource buffers, Vec3 cam, float partial, float now) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || WHIPS.isEmpty()) {
			return;
		}
		int[] count = new int[1];
		for (Map.Entry<UUID, Long> en : WHIPS.entrySet()) {
			Player p = mc.level.getPlayerByUUID(en.getKey());
			UndeadState s = p == null ? null : Undead.peek(p);
			if (p == null || s == null) {
				continue;
			}
			float age = now - en.getValue();
			if (age < 0f || age > whipLife()) {
				continue;
			}
			boolean own = ownView(p);
			Vec3[] pts = chain(p, s, age, partial, own, count);
			if (pts == null || count[0] <= 0) {
				continue;
			}
			int light = LevelRenderer.getLightColor(mc.level, BlockPos.containing(p.getEyePosition(partial)));
			drawWhip(pose, buffers, light, pts, count[0], cam, own ? 0.55 : 0.0, age < UndeadKitConfig.SPINE_WINDUP);
		}
	}

	private static Vector3f px(Vec3 v, Vec3 cam) {
		return new Vector3f((float) ((v.x - cam.x) * 16.0), (float) ((v.y - cam.y) * 16.0), (float) ((v.z - cam.z) * 16.0));
	}

	/**
	 * The whip along {@code pts} (world, hand first), its first {@code count} vertebrae; any within {@code clear} blocks of the
	 * camera are left out (his own view stays clear). {@code tearing}: still coming out of his back -- the far end is the raw,
	 * bloody root, not the barb yet.
	 */
	private static void drawWhip(PoseStack pose, MultiBufferSource buffers, int light, Vec3[] pts, int count, Vec3 cam, double clear, boolean tearing) {
		VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(UndeadRender.PALETTE));
		Vector3f worldUp = new Vector3f(0, 1, 0);
		int n = Math.min(count, pts.length - 1);
		for (int i = 0; i < n; i++) {
			if (clear > 0 && (pts[i].distanceTo(cam) < clear || pts[i + 1].distanceTo(cam) < clear)) {
				continue;
			}
			float u = i / (float) SEGS;
			float sc = Mth.lerp(u, 1.45f, 0.65f);
			Vector3f a = px(pts[i], cam), b = px(pts[i + 1], cam);
			Vector3f d = new Vector3f(b).sub(a);
			float len = d.length();
			if (len < 1.0e-3f) {
				continue;
			}
			d.div(len);
			// "up" for this vertebra: the world's up, kept square to the whip
			Vector3f side = new Vector3f(d).cross(worldUp);
			if (side.lengthSquared() < 1.0e-4f) {
				side = new Vector3f(1, 0, 0);
			}
			side.normalize();
			Vector3f up = new Vector3f(side).cross(d).normalize();
			float gap = Math.min(0.55f * sc, len * 0.18f);
			boolean bloody = i >= SEGS - 3 || i == 0;
			int boneCol = UndeadBones.mix(i % 2 == 0 ? UndeadBones.BONE : UndeadBones.mix(UndeadBones.BONE, UndeadBones.BONE_SHADE, 0.45f), UndeadBones.BONE_SHADE,
					u * 0.5f);
			if (bloody) {
				boneCol = UndeadBones.mix(boneCol, UndeadBoneSwordRender.BLOOD_WET, 0.35f);
			}
			int procCol = UndeadBones.mix(UndeadBones.BONE_SHADE, UndeadBones.BONE_DARK, 0.25f + 0.3f * u);
			// the body of the vertebra
			Vector3f a1 = new Vector3f(a).add(new Vector3f(d).mul(gap)), b1 = new Vector3f(b).sub(new Vector3f(d).mul(gap));
			float cw = 2.1f * sc;
			UndeadBones.bar(vc, pose, a1, b1, up, cw, cw * 0.9f, 0f, boneCol, light);
			// its rims (the end plates), a touch wider
			UndeadBones.lump(vc, pose, a1, d, up, cw * 1.12f, cw * 1.02f, 0.35f * sc, procCol, light);
			// the disc to the next one: dark, wet
			UndeadBones.lump(vc, pose, a, d, up, cw * 0.82f, cw * 0.74f, gap * 2f + 0.1f, i % 3 == 0 ? 0x7C2A26 : 0x8E3A32, light);
			Vector3f mid = new Vector3f(a1).add(b1).mul(0.5f);
			// the spike up its back, leaning back toward the hand
			Vector3f tip = new Vector3f(mid).add(new Vector3f(up).mul(1.9f * sc)).sub(new Vector3f(d).mul(0.7f * sc));
			UndeadBones.bar(vc, pose, mid, tip, new Vector3f(d).negate(), 0.55f * sc, 0.9f * sc, 0.05f, procCol, light);
			// the wings either side
			for (int k = -1; k <= 1; k += 2) {
				Vector3f wing = new Vector3f(mid).add(new Vector3f(side).mul(k * 1.75f * sc)).add(new Vector3f(up).mul(0.25f * sc));
				UndeadBones.bar(vc, pose, mid, wing, up, 0.5f * sc, 0.5f * sc, 0.02f, UndeadBones.mix(boneCol, UndeadBones.BONE_SHADE, 0.5f), light);
			}
		}
		if (n <= 0) {
			return;
		}
		Vector3f h = px(pts[0], cam);
		if (clear <= 0 || pts[0].distanceTo(cam) >= clear) {
			// the torn top of the neck in his fist: raw and red
			Vector3f d0 = px(pts[1], cam).sub(h).normalize();
			UndeadBones.lump(vc, pose, new Vector3f(h).sub(new Vector3f(d0).mul(0.4f)), d0, worldUp, 1.9f, 1.9f, 1.0f, UndeadBoneSwordRender.BLOOD, light);
			UndeadBones.lump(vc, pose, new Vector3f(h).sub(new Vector3f(d0).mul(0.9f)).add(0, -0.6f, 0), new Vector3f(0, -1, 0), worldUp, 0.5f, 0.5f, 1.0f,
					UndeadBoneSwordRender.BLOOD_WET, light);
		}
		if (n < SEGS && !tearing) {
			return; // crumbling: the barb went first
		}
		Vector3f end = px(pts[n], cam);
		Vector3f de = new Vector3f(end).sub(px(pts[n - 1], cam)).normalize();
		if (tearing) {
			// the end still coming out of him: raw and bloody
			UndeadBones.lump(vc, pose, end, de, worldUp, 1.6f, 1.6f, 1.0f, UndeadBoneSwordRender.BLOOD_WET, light);
			return;
		}
		if (clear > 0 && pts[n].distanceTo(cam) < clear) {
			return;
		}
		// the tail-bone barb: a tapering point with a hook curled back off it, bloody where it tore loose
		Vector3f side = new Vector3f(de).cross(worldUp);
		if (side.lengthSquared() < 1.0e-4f) {
			side = new Vector3f(1, 0, 0);
		}
		side.normalize();
		Vector3f up = new Vector3f(side).cross(de).normalize();
		Vector3f p1 = new Vector3f(end).add(new Vector3f(de).mul(1.4f));
		Vector3f p2 = new Vector3f(p1).add(new Vector3f(de).mul(1.3f));
		Vector3f p3 = new Vector3f(p2).add(new Vector3f(de).mul(1.1f));
		UndeadBones.bar(vc, pose, end, p1, up, 1.0f, 0.95f, 0.05f, UndeadBones.mix(UndeadBones.BONE_SHADE, UndeadBoneSwordRender.BLOOD_WET, 0.3f), light);
		UndeadBones.bar(vc, pose, p1, p2, up, 0.7f, 0.65f, 0.04f, UndeadBones.BONE_SHADE, light);
		UndeadBones.bar(vc, pose, p2, p3, up, 0.35f, 0.32f, 0.03f, UndeadBones.BONE_DARK, light);
		Vector3f hook = new Vector3f(p1).add(new Vector3f(up).mul(1.3f)).sub(new Vector3f(de).mul(0.9f));
		UndeadBones.bar(vc, pose, new Vector3f(p1).add(new Vector3f(de).mul(0.3f)), hook, de, 0.38f, 0.45f, 0.03f, UndeadBones.BONE_DARK, light);
		UndeadBones.lump(vc, pose, new Vector3f(end).add(new Vector3f(up).mul(-0.6f)), new Vector3f(0, -1, 0), worldUp, 0.4f, 0.4f, 0.8f,
				UndeadBoneSwordRender.BLOOD_WET, light);
	}

	// ---------------------------------------------------------------- drawing: the split in his back (third person)

	private static float jag(int i) {
		return ((i * 53 + 7) % 11) / 10f;
	}

	/**
	 * From the model layer ({@link UndeadBoneSwordRender#thirdPerson}): the split down the middle of his back the spine came out
	 * through -- torn open from the neck down as it is hauled out, a dark red groove with the empty sockets in it and a raw edge;
	 * then closing like a regrowing part (bone, the muscle in from the edges, the skin).
	 */
	static void backWound(PoseStack pose, MultiBufferSource buffers, int light, Player player, PlayerModel<?> model, UndeadState s, float now) {
		Long w = WOUNDS.get(player.getUUID());
		if (w == null || s.broken(UndeadState.BODY)) {
			return;
		}
		float age = now - w;
		int windup = UndeadKitConfig.SPINE_WINDUP;
		int delay = Undead.regrowDelay(player);
		int grow = Undead.regrowTicks(player, UndeadState.BODY);
		if (age < 0.5f || age > windup + delay + grow) {
			return;
		}
		float open = ease(age / (windup - 1f));
		float c = Mth.clamp((age - windup - delay) / grow, 0f, 1f);
		float muscle = Mth.clamp((c - 0.3f) / 0.55f, 0f, 1f);
		float skin = Mth.clamp((c - 0.85f) / 0.15f, 0f, 1f);
		float width = 1f - skin;
		if (width <= 0.02f) {
			return;
		}
		pose.pushPose();
		model.body.translateAndRotate(pose);
		VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(UndeadRender.PALETTE));
		PoseStack.Pose pp = pose.last();
		float top = 0.4f, bottom = top + (11.2f - top) * open;
		int bands = 10;
		float bh = (11.2f - top) / bands;
		// (body px: the back face at z = +2, the jacket layer out to +2.25)
		for (int i = 0; i < bands; i++) {
			float y0 = top + i * bh, y1 = Math.min(bottom, y0 + bh + 0.02f);
			if (y1 <= y0) {
				break;
			}
			float hw = (0.75f + 0.3f * jag(i)) * width;
			UndeadBoneSwordRender.box(vc, pp, -hw, y0, 2.27f, hw, y1, 2.3f, 0x3A050A, light);
			// the empty sockets the vertebrae came out of
			UndeadBoneSwordRender.box(vc, pp, -hw * 0.5f, y0 + bh * 0.2f, 2.27f, hw * 0.5f, y0 + bh * 0.55f, 2.32f, 0x240205, light);
			float rim = 0.4f + 0.25f * jag(i + 4);
			UndeadBoneSwordRender.box(vc, pp, -hw - rim, y0, 2.27f, -hw + 0.12f, y1, 2.38f, BloodFx.FLESH[1 + (i % 2)], light);
			UndeadBoneSwordRender.box(vc, pp, hw - 0.12f, y0, 2.27f, hw + rim, y1, 2.38f, BloodFx.FLESH[2 - (i % 2)], light);
			UndeadBoneSwordRender.box(vc, pp, -hw - 0.04f, y0, 2.36f, -hw + 0.2f, y1, 2.41f, UndeadBoneSwordRender.BLOOD_GLOSS, light);
			UndeadBoneSwordRender.box(vc, pp, hw - 0.2f, y0, 2.36f, hw + 0.04f, y1, 2.41f, UndeadBoneSwordRender.BLOOD_GLOSS, light);
			float flap = 0.2f + 0.25f * jag(i + 6);
			UndeadBoneSwordRender.box(vc, pp, -hw - rim - flap, y0, 2.27f, -hw - rim + 0.05f, y1, 2.34f, UndeadBoneSwordRender.SKIN_DARK, light);
			UndeadBoneSwordRender.box(vc, pp, hw + rim - 0.05f, y0, 2.27f, hw + rim + flap, y1, 2.34f, UndeadBoneSwordRender.SKIN_DARK, light);
			if (muscle > 0f) {
				float m = (hw + 0.05f) * muscle;
				UndeadBoneSwordRender.box(vc, pp, -hw, y0, 2.3f, -hw + m, y1, 2.44f, BloodFx.FLESH[1 + (i % 3)], light);
				UndeadBoneSwordRender.box(vc, pp, hw - m, y0, 2.3f, hw, y1, 2.44f, BloodFx.FLESH[1 + (i % 3)], light);
			}
		}
		if (age >= windup && muscle < 1f) {
			// a thin run of blood from the bottom of it
			float run = Mth.clamp((age - windup) / 14f, 0f, 1f) * (1f - muscle);
			UndeadBoneSwordRender.box(vc, pp, -0.15f, bottom - 0.2f, 2.28f, 0.15f, Math.min(11.9f, bottom + 0.4f + 0.8f * run), 2.36f, UndeadBoneSwordRender.BLOOD_WET,
					light);
		}
		pose.popPose();
	}

	// ---------------------------------------------------------------- poses

	private static void set(ModelPart part, float weight, float x, float y, float z) {
		part.xRot = Mth.lerp(weight, part.xRot, x);
		part.yRot = Mth.lerp(weight, part.yRot, y);
		part.zRot = Mth.lerp(weight, part.zRot, z);
	}

	/**
	 * Third person, from {@link UndeadBoneSwordRender}'s kit poses: the whip arm reaches up and back over the shoulder and hauls
	 * up and over (hunched into the pull, the other arm braced), then is swung flat across in front of him with the body twisting
	 * into it, and follows through, dropping. True if it set the pose.
	 */
	static boolean pose(Player player, UndeadState s, HumanoidModel<?> model, float age) {
		if (s.animId != UndeadKitConfig.ANIM_SPINE_WHIP || age < 0f || age >= whipLife()) {
			return false;
		}
		float sd = UndeadSpineWhip.side(player, s);
		ModelPart arm = sd > 0 ? model.rightArm : model.leftArm;
		ModelPart other = sd > 0 ? model.leftArm : model.rightArm;
		int w = UndeadKitConfig.SPINE_WINDUP, sw = UndeadKitConfig.SPINE_SWEEP;
		if (age < w) {
			float reach = ease(age / 2.5f), pull = ease((age - 2.5f) / (w - 2.5f));
			float strain = Mth.sin(age * 3.7f) * 0.05f * reach * (1f - pull);
			set(arm, reach, Mth.lerp(pull, -3.45f, -2.55f) + strain, Mth.lerp(pull, -sd * 0.3f, sd * 0.1f), Mth.lerp(pull, -sd * 0.35f, sd * 0.05f));
			set(other, reach, -0.7f, sd * 0.2f, -sd * 0.25f);
			model.body.xRot += 0.18f * reach + 0.12f * pull;
			model.head.xRot += 0.25f * reach;
			return true;
		}
		float tl = (age - w + 1f) / sw;
		if (tl <= 1.15f) {
			float a = UndeadSpineWhip.angle(sd, tl + 0.15f) * Mth.DEG_TO_RAD;
			float in = ease((age - w + 1f) / 1.2f);
			set(arm, Math.max(in, 0.6f), -1.45f, a, sd * 0.1f);
			set(other, 1f, -0.45f, -sd * 0.15f, -sd * 0.35f);
			model.body.yRot += 0.32f * a;
			return true;
		}
		float ta = Mth.clamp((age - w - sw) / UndeadKitConfig.SPINE_AFTER, 0f, 1f);
		float wgt = 1f - ease((ta - 0.35f) / 0.65f);
		float a = UndeadSpineWhip.angle(sd, 1f) * Mth.DEG_TO_RAD;
		set(arm, wgt, Mth.lerp(ease(ta / 0.5f), -1.45f, -0.5f), a * (1f - 0.3f * ta), sd * 0.1f);
		set(other, wgt, -0.45f, -sd * 0.15f, -sd * 0.35f);
		model.body.yRot += 0.32f * a * wgt;
		return true;
	}

	/**
	 * First person (from {@link UndeadBoneSwordRender#firstPerson}): the whip hand goes up and out of the top of the view as it
	 * reaches back for the spine, comes back over, then sweeps low across the bottom of the view from its own side to the other
	 * and drops away -- the view ahead stays clear. True if it drew (the caller cancels vanilla's hands).
	 */
	static boolean firstPerson(AbstractClientPlayer player, UndeadState s, float age, PoseStack pose, MultiBufferSource buffers, int light) {
		if (s.animId != UndeadKitConfig.ANIM_SPINE_WHIP || age < 0f || age >= whipLife()) {
			return false;
		}
		if (pose == null) {
			return true; // the other hand's call: both are drawn (or hidden) on the main hand's
		}
		float sdArm = UndeadSpineWhip.side(player, s);
		HumanoidArm side = sdArm > 0 ? HumanoidArm.RIGHT : HumanoidArm.LEFT;
		int w = UndeadKitConfig.SPINE_WINDUP, sw = UndeadKitConfig.SPINE_SWEEP;
		float x, y, z, yaw, pitch, roll;
		if (age < w) {
			float reach = ease(age / 2.5f), pull = ease((age - 2.5f) / (w - 2.5f));
			float gone = reach * (1f - pull);
			// down out of the bottom of the view (reaching back over his shoulder, where the camera can not see), then back up
			x = sdArm * 0.1f * gone;
			y = -0.75f * gone;
			z = 0.1f * gone;
			yaw = 0f;
			pitch = -20f * gone;
			roll = 0f;
		} else {
			float tl = (age - w + 1f) / sw;
			float ta = Mth.clamp((age - w - sw) / UndeadKitConfig.SPINE_AFTER, 0f, 1f);
			float a = UndeadSpineWhip.angle(sdArm, Math.min(1f, tl + 0.15f)) / (float) UndeadKitConfig.SPINE_ARC; // +1 own side .. -1 other
			float in = 1f - ease((age - w) / 1.5f);
			// low across the bottom of the view; dropped away after
			x = a * 0.48f - sdArm * 0.12f;
			y = 0.02f - 0.4f * in - 0.55f * ease(ta / 0.6f);
			z = -0.05f;
			yaw = -a * 38f;
			pitch = -12f;
			roll = sdArm * 10f;
		}
		final float fyaw = yaw, fpitch = pitch, froll = roll;
		pose.pushPose();
		pose.translate(x, y, z);
		UndeadBoneSwordRender.fpArm(player, pose, buffers, light, side, p -> {
			p.mulPose(Axis.YP.rotationDegrees(fyaw));
			p.mulPose(Axis.XP.rotationDegrees(fpitch));
			p.mulPose(Axis.ZP.rotationDegrees(froll));
		});
		pose.popPose();
		return true;
	}
}
