package com.projecthero.mod.client.omnitrix.alien.wildvine;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projecthero.mod.client.mutation.pyro.PyroDraw;
import com.projecthero.mod.client.omnitrix.OmnitrixRenderers;
import com.projecthero.mod.client.omnitrix.alien.brainstorm.BrainstormFx;
import com.projecthero.mod.omnitrix.alien.wildvine.WildvineKit;
import com.projecthero.mod.omnitrix.entity.AlienProjectileEntity;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.2 Wildvine's drawn plant-work: his vines (lit, ribbed green tubes with leaves, from his right hand to a hook or a creature,
 * shooting out over their first ticks), the Vine Lash (a vine that cracks out and whips back, a thorn bud on its tip), the Vine Slash's
 * fan of tendrils tearing along the ground and the stabs they end in, the coils round a wrapped creature, the seed pod in flight, the
 * planted seed mines (half-buried black pods with a sprout, pulsing once armed), the thicket (tendrils rearing out of the ground all
 * round him: Vine Snare and Overgrowth) and the mound he moves as under the ground.
 */
public final class WildvineFx {
	private WildvineFx() {
	}

	private static boolean registered;
	/** Vine greens: the stem, its darker ribs, a leaf. */
	private static final int STEM = 0xFF4E9A3C, RIB = 0xFF2E6A2E, LEAF = 0xFF6CBF3C;

	public static void register() {
		if (registered) {
			return;
		}
		registered = true;
		OmnitrixRenderers.projectileLook("wildvine_vine", WildvineFx::vine);
		OmnitrixRenderers.projectileLook("wildvine_lash", WildvineFx::lash);
		OmnitrixRenderers.projectileLook("wildvine_slash", WildvineFx::slash);
		OmnitrixRenderers.projectileLook("wildvine_stab", WildvineFx::stab);
		OmnitrixRenderers.projectileLook("wildvine_regrow", WildvineFx::regrow);
		OmnitrixRenderers.projectileLook("wildvine_wrap", WildvineFx::wrap);
		OmnitrixRenderers.projectileLook("wildvine_seed", WildvineFx::seed);
		OmnitrixRenderers.projectileLook("wildvine_mine", WildvineFx::mine);
		OmnitrixRenderers.projectileLook("wildvine_thicket", WildvineFx::thicket);
	}

	// ---------------------------------------------------------------- where his vines leave from

	/**
	 * His right hand, with the arm reaching at {@code target}: in first person low and right of the view (so the vine rises into it),
	 * otherwise an arm's length from his right shoulder toward the target, by his body's facing and size.
	 */
	public static Vec3 hand(Player p, float partial, Vec3 target) {
		Minecraft mc = Minecraft.getInstance();
		if (p == mc.player && mc.options.getCameraType() == CameraType.FIRST_PERSON) {
			Vec3 eye = p.getEyePosition(partial);
			Vec3 look = p.getViewVector(partial);
			Vec3 right = look.cross(new Vec3(0, 1, 0));
			right = right.lengthSqr() < 1.0e-6 ? new Vec3(1, 0, 0) : right.normalize();
			return eye.add(look.scale(0.5)).add(right.scale(0.32)).add(0, -0.32, 0);
		}
		float yaw = Mth.lerp(partial, p.yBodyRotO, p.yBodyRot) * Mth.DEG_TO_RAD;
		Vec3 right = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw));
		float s = p.getScale();
		Vec3 shoulder = p.getPosition(partial).add(0, 1.4 * s, 0).add(right.scale(0.34 * s));
		Vec3 d = target.subtract(shoulder);
		return d.lengthSqr() < 1.0e-6 ? shoulder : shoulder.add(d.normalize().scale(0.72 * s));
	}

	private static int light(Entity e) {
		return LevelRenderer.getLightColor(e.level(), BlockPos.containing(e.position().add(0, 0.5, 0)));
	}

	private static void toCamera(PoseStack pose, Entity e, float partial) {
		Vec3 at = e.getPosition(partial);
		Vec3 c = BrainstormFx.cam();
		pose.translate(c.x - at.x, c.y - at.y, c.z - at.z);
	}

	// ---------------------------------------------------------------- the vine

	/** A vine from his hand to the hook here (colour 0) or to creature colour - 1 wherever it goes, shooting out over 3 ticks. */
	private static void vine(AlienProjectileEntity e, float partial, PoseStack pose, MultiBufferSource buffers) {
		Minecraft mc = Minecraft.getInstance();
		if (!(e.getOwner() instanceof Player owner)) {
			return;
		}
		Vec3 end = farEnd(mc, e, partial);
		if (end == null) {
			return;
		}
		Vec3 start = hand(owner, partial, end);
		float age = e.tickCount + partial;
		float reach = Mth.clamp(age / 3f, 0.08f, 1f);
		pose.pushPose();
		toCamera(pose, e, partial);
		Vec3 cam = BrainstormFx.cam();
		List<Vec3> pts = vinePath(start, end, reach, age, e.getId());
		List<Vec3> rel = new ArrayList<>(pts.size());
		for (Vec3 v : pts) {
			rel.add(v.subtract(cam));
		}
		int light = light(owner);
		tube(pose, PyroDraw.solidBuffer(buffers), rel, s -> 0.075 * (1.0 - 0.45 * s), light);
		leaves(pose, PyroDraw.solidBuffer(buffers), rel, e.getId(), light);
		pose.popPose();
	}

	/** Where a vine ends: this entity (its hook), or creature colour - 1 wherever it has got to; null if that creature is gone. */
	private static Vec3 farEnd(Minecraft mc, AlienProjectileEntity e, float partial) {
		if (e.colour() <= 0) {
			return e.getPosition(partial);
		}
		Entity t = mc.level.getEntity(e.colour() - 1);
		return t == null ? null : t.getPosition(partial).add(0, t.getBbHeight() * 0.55, 0);
	}

	// ---------------------------------------------------------------- the Vine Lash

	/**
	 * The Vine Lash: Ladybug's strike timing (3 ticks out, a beat, 4 back) on a vine -- a whip wave running down it on the way out, cracking
	 * straight as it lands and falling slack as it is drawn back into his hand, a thorn bud on its tip.
	 */
	private static void lash(AlienProjectileEntity e, float partial, PoseStack pose, MultiBufferSource buffers) {
		Minecraft mc = Minecraft.getInstance();
		if (!(e.getOwner() instanceof Player owner)) {
			return;
		}
		Vec3 end = farEnd(mc, e, partial);
		if (end == null) {
			return;
		}
		Vec3 start = hand(owner, partial, end);
		float age = e.tickCount + partial;
		int out = WildvineKit.LASH_OUT, back = WildvineKit.LASH_BACK;
		float reach;
		if (age < out) {
			float k = age / out;
			reach = 1f - (1f - k) * (1f - k);
		} else {
			float k = Mth.clamp((age - out - 1f) / back, 0f, 1f);
			reach = 1f - k * k * (3f - 2f * k);
		}
		if (reach < 0.02f) {
			return;
		}
		Vec3 d = end.subtract(start);
		double len = d.length();
		Vec3 dir = len < 1.0e-6 ? new Vec3(0, 0, 1) : d.scale(1.0 / len);
		Vec3 side = dir.cross(new Vec3(0, 1, 0));
		side = side.lengthSqr() < 1.0e-6 ? new Vec3(1, 0, 0) : side.normalize();
		Vec3 up = side.cross(dir).normalize();
		double amp = Math.min(0.9, len * 0.09) * (age < out ? 1.0 - reach * 0.7 : 0.25 + 0.75 * Mth.clamp(age - out - 1f, 0f, back) / back);
		Vec3 cam = BrainstormFx.cam();
		List<Vec3> rel = new ArrayList<>();
		int n = Math.max(10, Math.min(36, (int) (len * 3)));
		for (int i = 0; i <= n; i++) {
			double s = i / (double) n;
			double wave = Math.sin(s * Math.PI * 3.5 - age * 1.9) * amp * s * (1.0 - 0.4 * s);
			double curl = Math.cos(s * Math.PI * 2.5 - age * 1.4) * amp * 0.5 * s;
			rel.add(start.add(d.scale(s * reach)).add(side.scale(wave)).add(up.scale(curl)).subtract(cam));
		}
		int light = light(owner);
		pose.pushPose();
		toCamera(pose, e, partial);
		VertexConsumer vc = PyroDraw.solidBuffer(buffers);
		tube(pose, vc, rel, s -> 0.095 * (1.0 - 0.55 * s), light);
		leaves(pose, vc, rel, e.getId(), light);
		Vec3 tip = rel.get(rel.size() - 1);
		pose.pushPose();
		pose.translate(tip.x, tip.y, tip.z);
		pose.mulPose(Axis.YP.rotation((float) Math.atan2(dir.x, dir.z)));
		pose.mulPose(Axis.XP.rotation((float) (Math.PI * 0.5 - Math.asin(Mth.clamp(dir.y, -1.0, 1.0)))));
		bud(pose, vc, 0.2f, light);
		pose.popPose();
		pose.popPose();
	}

	/** A thorn bud, its point up +y: a dark green head with a ring of thorns. */
	private static void bud(PoseStack pose, VertexConsumer vc, float size, int light) {
		pose.pushPose();
		pose.scale(size * 0.7f, size, size * 0.7f);
		PyroDraw.sphere(pose, vc, 0.5f, LEAF | 0xFF000000, RIB, 0f, 0f, light);
		pose.popPose();
		for (int k = 0; k < 6; k++) {
			double a = k * Math.PI / 3;
			Vec3 foot = new Vec3(Math.cos(a) * size * 0.3, size * 0.05, Math.sin(a) * size * 0.3);
			Vec3 tip = new Vec3(Math.cos(a) * size * 0.72, size * 0.5, Math.sin(a) * size * 0.72);
			tubeColour(pose, vc, List.of(foot, foot.lerp(tip, 0.5), tip), s -> size * 0.13 * (1.0 - s), RIB, light);
		}
		tubeColour(pose, vc, List.of(new Vec3(0, size * 0.4, 0), new Vec3(0, size * 0.65, 0), new Vec3(0, size * 0.95, 0)), s -> size * 0.16 * (1.0 - s), RIB, light);
	}

	// ---------------------------------------------------------------- the Vine Slash

	/** The height of the ground under (x, z) near {@code y}, world space: 3 blocks up to 6 down, or {@code y} over a drop. */
	private static double groundAt(Entity e, double x, double y, double z) {
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		int bx = Mth.floor(x), bz = Mth.floor(z), by = Mth.floor(y);
		for (int h = by + 3; h >= by - 6; h--) {
			m.set(bx, h, bz);
			var st = e.level().getBlockState(m);
			var shape = st.getCollisionShape(e.level(), m);
			if (!shape.isEmpty()) {
				return h + shape.max(net.minecraft.core.Direction.Axis.Y);
			}
		}
		return y;
	}

	/**
	 * The Vine Slash: a fan of tendrils out of the ground at his feet, tearing along it (following the land) across a 70-degree cone,
	 * their heads rearing and thrashing as they run out at the speed the server hits at, then snatched back. Yaw = his heading, size =
	 * the range; every viewer draws the same fan from the entity's id.
	 */
	private static void slash(AlienProjectileEntity e, float partial, PoseStack pose, MultiBufferSource buffers) {
		float t = e.tickCount + partial;
		float life = WildvineKit.SLASH_LIFE;
		float range = e.size();
		float runOut = (float) (range / WildvineKit.SLASH_SPEED);
		float out = Mth.clamp(t / runOut, 0f, 1f);
		float back = Mth.clamp((t - (life - 7f)) / 7f, 0f, 1f);
		back = back * back;
		if (back >= 1f) {
			return;
		}
		VertexConsumer vc = PyroDraw.solidBuffer(buffers);
		int light = light(e);
		Vec3 base = e.getPosition(partial);
		float yaw = e.getYRot() * Mth.DEG_TO_RAD;
		java.util.Random rnd = new java.util.Random(e.getId() * 53L);
		int count = 28;
		for (int k = 0; k < count; k++) {
			double spread = (k / (count - 1.0) * 2.0 - 1.0) * Math.toRadians(35) + (rnd.nextDouble() - 0.5) * 0.06;
			double a = yaw + spread;
			Vec3 way = new Vec3(-Math.sin(a), 0, Math.cos(a));
			Vec3 side = new Vec3(way.z, 0, -way.x);
			// the long ones reach the full range, shorter ones fill the fan in behind them
			double full = range * (k % 3 == 0 ? 1.0 : 0.5 + rnd.nextDouble() * 0.45);
			double reach = full * Math.min(1.0, out * range / full) * (1.0 - back);
			double phase = rnd.nextDouble() * Math.PI * 2;
			double thick = 0.17 + rnd.nextDouble() * 0.1;
			if (reach < 0.2) {
				continue;
			}
			int n = Math.min(20, 6 + (int) (reach * 0.8));
			List<Vec3> pts = new ArrayList<>(n + 1);
			for (int i = 0; i <= n; i++) {
				double s = i / (double) n;
				double d = 0.5 + s * reach;
				double wig = Math.sin(s * 7.0 + t * 0.7 + phase) * 0.28 * Math.min(1.0, s * 3.0);
				Vec3 at = base.add(way.scale(d)).add(side.scale(wig));
				double gy = groundAt(e, at.x, base.y, at.z);
				// low along the ground, the head reared up and thrashing
				double head = s * s * s * s * (1.5 + 0.5 * Math.sin(t * 0.9 + phase)) * Math.min(1.0, reach / 3.0);
				double hump = Math.abs(Math.sin(s * 6.0 + phase + t * 0.3)) * 0.14;
				pts.add(new Vec3(at.x - base.x, gy + 0.08 + hump + head - base.y, at.z - base.z));
			}
			tube(pose, vc, pts, s -> thick * (1.0 - 0.6 * s), light);
			leaves(pose, vc, pts, k, light);
		}
	}

	/** Where a tendril of the slash reaches a creature: three spears up out of the ground round it, in fast, out over the last ticks. */
	private static void stab(AlienProjectileEntity e, float partial, PoseStack pose, MultiBufferSource buffers) {
		Entity t = Minecraft.getInstance().level.getEntity(e.colour() - 1);
		float age = e.tickCount + partial;
		float grow = Mth.clamp(age / 2.5f, 0f, 1f) * Mth.clamp((12f - age) / 4f, 0f, 1f);
		if (grow <= 0.01f) {
			return;
		}
		Vec3 foot = e.getPosition(partial);
		Vec3 at = t == null ? foot : t.getPosition(partial);
		double w = (t == null ? 0.3 : t.getBbWidth() * 0.5) + 0.45;
		double h = (t == null ? 1.8 : t.getBbHeight()) * 0.95;
		VertexConsumer vc = PyroDraw.solidBuffer(buffers);
		int light = light(e);
		for (int k = 0; k < 3; k++) {
			double a = k * Math.PI * 2 / 3 + e.getId();
			Vec3 root = new Vec3(Math.cos(a) * w, 0, Math.sin(a) * w).add(foot.subtract(e.getPosition(partial)));
			Vec3 tip = at.subtract(foot).add(Math.cos(a + 1.2) * 0.12, h * grow, Math.sin(a + 1.2) * 0.12);
			List<Vec3> pts = new ArrayList<>();
			for (int i = 0; i <= 9; i++) {
				double s = i / 9.0;
				Vec3 p = root.lerp(tip, s * s);
				pts.add(new Vec3(p.x, tip.y * s, p.z));
			}
			tube(pose, vc, pts, s -> 0.13 * (1.0 - 0.8 * s), light);
		}
	}

	/**
	 * The vine's points from {@code a} toward {@code b}, {@code reach} of the way: a gentle S-curve and a sag that both straighten as it
	 * pulls taut, with a slow living wriggle.
	 */
	static List<Vec3> vinePath(Vec3 a, Vec3 b, float reach, float age, int seed) {
		Vec3 d = b.subtract(a);
		double len = d.length();
		Vec3 dir = len < 1.0e-6 ? new Vec3(0, 0, 1) : d.scale(1.0 / len);
		Vec3 side = dir.cross(new Vec3(0, 1, 0));
		side = side.lengthSqr() < 1.0e-6 ? new Vec3(1, 0, 0) : side.normalize();
		Vec3 up = side.cross(dir).normalize();
		int n = Math.max(6, Math.min(40, (int) (len * 2.5)));
		double wig = Math.min(0.35, len * 0.03) * (1.3 - reach * 0.6);
		List<Vec3> pts = new ArrayList<>();
		for (int i = 0; i <= n; i++) {
			double t = i / (double) n * reach;
			double env = Math.sin(Math.PI * t); // pinned at both ends
			double w = Math.sin(t * Math.PI * 3 + age * 0.15 + seed) * wig * env;
			double sag = Math.min(0.4, len * 0.02) * 4 * t * (1 - t);
			pts.add(a.add(d.scale(t)).add(side.scale(w)).add(up.scale(w * 0.4)).add(0, -sag, 0));
		}
		return pts;
	}

	/**
	 * A lit tube along {@code pts} (camera-relative), radius {@code radius.apply(s)} at fraction s: six sides, every other one a darker
	 * rib, so it reads as a twisted stem.
	 */
	static void tube(PoseStack pose, VertexConsumer vc, List<Vec3> pts, java.util.function.DoubleUnaryOperator radius, int light) {
		int sides = 6;
		PoseStack.Pose last = pose.last();
		Vec3[] prev = null;
		Vec3[] prevN = null;
		for (int i = 0; i < pts.size(); i++) {
			Vec3 p = pts.get(i);
			Vec3 tan = (i < pts.size() - 1 ? pts.get(i + 1) : p).subtract(i > 0 ? pts.get(i - 1) : p);
			tan = tan.lengthSqr() < 1.0e-10 ? new Vec3(0, 1, 0) : tan.normalize();
			Vec3 n0 = Math.abs(tan.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
			Vec3 nx = tan.cross(n0).normalize();
			Vec3 ny = nx.cross(tan).normalize();
			double r = radius.applyAsDouble(i / (double) Math.max(1, pts.size() - 1));
			Vec3[] ring = new Vec3[sides];
			Vec3[] norm = new Vec3[sides];
			double twist = i * 0.35;
			for (int k = 0; k < sides; k++) {
				double a = twist + k * Math.PI * 2 / sides;
				norm[k] = nx.scale(Math.cos(a)).add(ny.scale(Math.sin(a)));
				ring[k] = p.add(norm[k].scale(r));
			}
			if (prev != null) {
				for (int k = 0; k < sides; k++) {
					int k1 = (k + 1) % sides;
					int c = k % 2 == 0 ? STEM : RIB;
					v(vc, last, prev[k], prevN[k], c, light);
					v(vc, last, ring[k], norm[k], c, light);
					v(vc, last, ring[k1], norm[k1], c, light);
					v(vc, last, prev[k1], prevN[k1], c, light);
				}
			}
			prev = ring;
			prevN = norm;
		}
	}

	/** Little leaves along a vine: a pair every few points, angled off it. */
	static void leaves(PoseStack pose, VertexConsumer vc, List<Vec3> pts, int seed, int light) {
		PoseStack.Pose last = pose.last();
		for (int i = 2; i < pts.size() - 1; i += 3) {
			Vec3 p = pts.get(i);
			Vec3 tan = pts.get(i + 1).subtract(pts.get(i - 1));
			if (tan.lengthSqr() < 1.0e-8) {
				continue;
			}
			tan = tan.normalize();
			Vec3 side = tan.cross(new Vec3(0, 1, 0));
			side = side.lengthSqr() < 1.0e-6 ? new Vec3(1, 0, 0) : side.normalize();
			double flip = ((i + seed) % 2 == 0) ? 1 : -1;
			Vec3 out = side.scale(flip).add(0, 0.45, 0).normalize();
			Vec3 tip = p.add(out.scale(0.2)).add(tan.scale(0.08));
			Vec3 w = tan.scale(0.06);
			Vec3 nrm = out.cross(tan).normalize();
			// a diamond leaf, both faces
			Vec3 a = p, b = p.add(out.scale(0.1)).add(w), c = tip, d = p.add(out.scale(0.1)).subtract(w);
			v(vc, last, a, nrm, LEAF, light);
			v(vc, last, b, nrm, LEAF, light);
			v(vc, last, c, nrm, LEAF, light);
			v(vc, last, d, nrm, LEAF, light);
			Vec3 back = nrm.scale(-1);
			v(vc, last, d, back, LEAF, light);
			v(vc, last, c, back, LEAF, light);
			v(vc, last, b, back, LEAF, light);
			v(vc, last, a, back, LEAF, light);
		}
	}

	private static void v(VertexConsumer vc, PoseStack.Pose last, Vec3 p, Vec3 n, int colour, int light) {
		vc.addVertex(last, (float) p.x, (float) p.y, (float) p.z).setColor((colour >> 16) & 255, (colour >> 8) & 255, colour & 255, 255).setUv(0.5f, 0.5f)
				.setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(last, (float) n.x, (float) n.y, (float) n.z);
	}

	// ---------------------------------------------------------------- the coils

	/** A vine spiralling round creature colour - 1, three turns from its feet to its shoulders, coiling on over the first ticks. */
	private static void wrap(AlienProjectileEntity e, float partial, PoseStack pose, MultiBufferSource buffers) {
		Minecraft mc = Minecraft.getInstance();
		Entity t = mc.level.getEntity(Math.abs(e.colour()) - 1);
		if (t == null) {
			return;
		}
		float age = e.tickCount + partial;
		coil(e, t, partial, pose, buffers, Mth.clamp(age / 5f, 0.1f, 1f), 1.0 - 0.06 * Math.max(0, Math.sin(age * 0.6)), 0.08);
	}

	/** Regrowth: a fresh shoot winds up him from his feet, then lets go and draws back down (20 ticks). */
	private static void regrow(AlienProjectileEntity e, float partial, PoseStack pose, MultiBufferSource buffers) {
		Entity t = Minecraft.getInstance().level.getEntity(Math.abs(e.colour()) - 1);
		if (t == null) {
			return;
		}
		float age = e.tickCount + partial;
		float grow = Mth.clamp(age / 8f, 0.05f, 1f) * Mth.clamp((20f - age) / 6f, 0.02f, 1f);
		coil(e, t, partial, pose, buffers, grow, 1.0 + 0.1 * Math.sin(age * 0.4), 0.2);
	}

	/** A vine spiralling round {@code t}, three turns from its feet to its shoulders, {@code grow} of the way up. */
	private static void coil(AlienProjectileEntity e, Entity t, float partial, PoseStack pose, MultiBufferSource buffers, float grow, double squeeze, double gap) {
		Vec3 base = t.getPosition(partial).subtract(e.getPosition(partial));
		double w = t.getBbWidth() * 0.5 + gap;
		double h = t.getBbHeight() * 0.92;
		List<Vec3> pts = new ArrayList<>();
		int n = 48;
		for (int i = 0; i <= n * grow; i++) {
			double s = i / (double) n;
			double a = s * Math.PI * 2 * 3 + e.getId();
			pts.add(base.add(Math.cos(a) * w * squeeze, 0.05 + s * h, Math.sin(a) * w * squeeze));
		}
		int light = LevelRenderer.getLightColor(t.level(), t.blockPosition());
		VertexConsumer vc = PyroDraw.solidBuffer(buffers);
		tube(pose, vc, pts, s -> 0.06, light);
		leaves(pose, vc, pts, e.getId(), light);
	}

	// ---------------------------------------------------------------- the seed pods

	/** The seed pod in flight: a black, ribbed pod tumbling end over end, a tuft of green at its stem. */
	private static void seed(AlienProjectileEntity e, float partial, PoseStack pose, MultiBufferSource buffers) {
		float t = e.tickCount + partial;
		Minecraft mc = Minecraft.getInstance();
		if (t < 3f && e.getOwner() == mc.player && mc.options.getCameraType() == CameraType.FIRST_PERSON) {
			return; // it leaves from between his eyes: a black disc over the whole view for its first ticks
		}
		VertexConsumer vc = PyroDraw.solidBuffer(buffers);
		int light = light(e);
		pose.pushPose();
		pose.translate(0, e.getBbHeight() * 0.5, 0);
		pose.mulPose(Axis.XP.rotation(t * 0.5f));
		pod(pose, vc, e.size(), t, light, 0f);
		pose.popPose();
	}

	/** A planted seed mine: a pod half sunk in the ground with a sprout; once armed (1 s) it swells and shrinks like a heartbeat. */
	private static void mine(AlienProjectileEntity e, float partial, PoseStack pose, MultiBufferSource buffers) {
		float t = e.tickCount + partial;
		VertexConsumer vc = PyroDraw.solidBuffer(buffers);
		int light = light(e);
		float rise = Mth.clamp(t / 6f, 0f, 1f);
		float pulse = t >= 20 ? 1f + 0.08f * Mth.sin(t * 0.5f) : 1f;
		pose.pushPose();
		pose.mulPose(Axis.YP.rotationDegrees(-e.getYRot()));
		pose.translate(0, -0.08 + 0.1 * rise, 0);
		pose.scale(pulse, pulse, pulse);
		pod(pose, vc, e.size() * 0.9f, t, light, t >= 20 ? 0.5f + 0.5f * Mth.sin(t * 0.5f) : 0f);
		pose.popPose();
	}

	/**
	 * The seed pods' colours. v0.17.2 (user: "wildvines bombs are black, please make the wildvine bombs black or at least make it match
	 * the colour of the bombs on the skin model please"): the skin's own pod colour, sampled from it (0x07030F, a violet-tinged
	 * near-black), glossy -- a charcoal sheen where the light catches it, a soft grey highlight on the shoulder.
	 */
	private static final int POD_BLACK = 0xFF07030F, POD_SHEEN = 0xFF24202C, POD_CAP = 0xFF0E0A16, POD_GLOSS = 0xFF3C3A46, POD_SHINE = 0xFFB0AEBA,
			POD_STEM = 0xFF151A12;

	/**
	 * A seed pod of {@code size}, its stem toward +y: a glossy black oval (the skin's pod colour) with a soft grey highlight on its
	 * shoulder, a slightly darker cap where it hangs and a short dark stem; {@code glow} warms it toward leaf green (an armed mine's
	 * pulse).
	 */
	static void pod(PoseStack pose, VertexConsumer vc, float size, float t, int light, float glow) {
		pose.pushPose();
		pose.scale(size * 0.82f, size * 1.08f, size * 0.82f);
		PyroDraw.sphere(pose, vc, 0.5f, PyroDraw.lerpRgb(POD_SHEEN, LEAF, glow) | 0xFF000000, POD_BLACK, 0.04f, t * 0.15f, light);
		pose.popPose();
		// the cap: a flattened, darker dome over the top
		pose.pushPose();
		pose.translate(0, size * 0.42f, 0);
		pose.scale(size * 0.56f, size * 0.24f, size * 0.56f);
		PyroDraw.sphere(pose, vc, 0.5f, POD_CAP, POD_BLACK, 0.02f, 0f, light);
		pose.popPose();
		// the gloss: a soft grey glow on the upper front shoulder, a brighter fleck in its middle (full bright: a highlight)
		pose.pushPose();
		pose.translate(-size * 0.15f, size * 0.18f, size * 0.33f);
		pose.scale(size * 0.24f, size * 0.32f, size * 0.07f);
		PyroDraw.sphere(pose, vc, 0.5f, POD_GLOSS, POD_GLOSS, 0f, 0f, PyroDraw.FULL);
		pose.popPose();
		pose.pushPose();
		pose.translate(-size * 0.17f, size * 0.22f, size * 0.355f);
		pose.scale(size * 0.1f, size * 0.15f, size * 0.05f);
		PyroDraw.sphere(pose, vc, 0.5f, POD_SHINE, POD_SHINE, 0f, 0f, PyroDraw.FULL);
		pose.popPose();
		List<Vec3> stem = List.of(new Vec3(0, size * 0.5, 0), new Vec3(size * 0.03, size * 0.6, 0), new Vec3(size * 0.08, size * 0.66, size * 0.02));
		tubeColour(pose, vc, stem, s -> size * 0.06, POD_STEM, light);
	}

	/** {@link #tube} in one colour (no ribs). */
	static void tubeColour(PoseStack pose, VertexConsumer vc, List<Vec3> pts, java.util.function.DoubleUnaryOperator radius, int colour, int light) {
		PoseStack.Pose last = pose.last();
		int sides = 6;
		Vec3[] prev = null, prevN = null;
		for (int i = 0; i < pts.size(); i++) {
			Vec3 p = pts.get(i);
			Vec3 tan = (i < pts.size() - 1 ? pts.get(i + 1) : p).subtract(i > 0 ? pts.get(i - 1) : p);
			tan = tan.lengthSqr() < 1.0e-10 ? new Vec3(0, 1, 0) : tan.normalize();
			Vec3 n0 = Math.abs(tan.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
			Vec3 nx = tan.cross(n0).normalize(), ny = nx.cross(tan).normalize();
			double r = radius.applyAsDouble(i / (double) Math.max(1, pts.size() - 1));
			Vec3[] ring = new Vec3[sides], norm = new Vec3[sides];
			for (int k = 0; k < sides; k++) {
				double a = k * Math.PI * 2 / sides;
				norm[k] = nx.scale(Math.cos(a)).add(ny.scale(Math.sin(a)));
				ring[k] = p.add(norm[k].scale(r));
			}
			if (prev != null) {
				for (int k = 0; k < sides; k++) {
					int k1 = (k + 1) % sides;
					v(vc, last, prev[k], prevN[k], colour, light);
					v(vc, last, ring[k], norm[k], colour, light);
					v(vc, last, ring[k1], norm[k1], colour, light);
					v(vc, last, prev[k1], prevN[k1], colour, light);
				}
			}
			prev = ring;
			prevN = norm;
		}
	}

	// ---------------------------------------------------------------- the pods on his back

	/** v0.17.2 (user: "make wildvines bombs the same size please"): all six the same. */
	private static final float POD_SIZE = 0.17f;

	/**
	 * v0.17.2 (user: "can you add little seed bomb models to wildvines back please"): where the skin painted its flat pods (now painted
	 * out) -- {x, y down, size in blocks} in body-bone pixels: three even pairs down his back. The first four are his seed bombs.
	 */
	private static final float[][] BACK_PODS = { { -2.0f, 4.4f, POD_SIZE }, { 2.0f, 4.4f, POD_SIZE }, { -2.0f, 8.1f, POD_SIZE }, { 2.0f, 8.1f, POD_SIZE },
			{ -2.0f, 11.6f, POD_SIZE }, { 2.0f, 11.6f, POD_SIZE } };

	/**
	 * v0.17.2 (user: "make it so that the player regenerates 1 seed bomb every 3 seconds please"): the pods ARE the stock. Pod {@code i}'s
	 * place in it: the bottom pair is the last to go, the top pair the first (he plucks them over his shoulder), so {@code rank} counts up
	 * from the bottom.
	 */
	static int podRank(int i) {
		return i >= 4 ? i - 4 : i >= 2 ? i : i + 4;
	}

	/** How grown back pod {@code i} is (0 gone .. 1 full) with {@code stock} pods and the next one {@code next} regrown. */
	public static float podGrowth(int i, int stock, float next) {
		int r = podRank(i);
		if (r < stock) {
			return 1f;
		}
		if (r > stock) {
			return 0f;
		}
		return next * next * (3f - 2f * next);
	}

	/** The seed pods on his back, on the body bone, swaying a little as he moves. */
	public static void backPods(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer p,
			net.minecraft.client.model.PlayerModel<AbstractClientPlayer> model, float partial, float age) {
		int stock = WildvineKit.pods(p);
		float next = WildvineKit.podRegrowth(p, partial);
		float walk = p.walkAnimation.speed(partial), step = p.walkAnimation.position(partial);
		VertexConsumer vc = PyroDraw.solidBuffer(buffers);
		pose.pushPose();
		model.body.translateAndRotate(pose);
		for (int i = 0; i < BACK_PODS.length; i++) {
			float[] b = BACK_PODS[i];
			// v0.17.3: his show transformation sprouts them one by one
			float shown = com.projecthero.mod.client.omnitrix.sequence.WildvineShow.pods(p, partial, i);
			float g = shown >= 0f ? shown : podGrowth(i, stock, next);
			if (g < 0.04f) {
				continue;
			}
			float size = b[2] * (0.25f + 0.75f * g);
			// hung from its cap, it sways: an idle bob, more with his stride, the two sides out of step
			float sway = Mth.sin(age * 0.12f + i * 1.7f) * 0.06f + Mth.sin(step * 0.6662f + (i % 2) * Mth.PI) * walk * 0.22f;
			pose.pushPose();
			pose.translate(b[0] / 16f, (b[1] - b[2] * 16f * 0.45f) / 16f, 2.25f / 16f);
			pose.mulPose(Axis.XP.rotation(-sway));
			pose.mulPose(Axis.ZP.rotation(sway * 0.4f * (i % 2 == 0 ? 1 : -1)));
			pose.translate(0, size * 0.48f, size * 0.36f);
			pose.mulPose(Axis.ZP.rotationDegrees(180f)); // the body bone's y runs down: stem up
			pose.mulPose(Axis.YP.rotationDegrees(180f)); // the shine faces out of his back
			pod(pose, vc, size, age, light, 0f);
			pose.popPose();
		}
		pose.popPose();
	}

	/** A pod in his hand on the way to being thrown or planted ({@code arm} posed already). */
	public static void handPod(PoseStack pose, MultiBufferSource buffers, int light, net.minecraft.client.model.geom.ModelPart arm, float age) {
		pose.pushPose();
		arm.translateAndRotate(pose);
		pose.translate(0, 0.72f, 0);
		pose.mulPose(Axis.ZP.rotationDegrees(180f));
		pod(pose, PyroDraw.solidBuffer(buffers), POD_SIZE * 1.15f, age, light, 0f);
		pose.popPose();
	}

	// ---------------------------------------------------------------- the thicket (Vine Snare, Overgrowth)

	/**
	 * Tendrils rearing out of the ground all round him (radius = size, ticks alive = colour): they shoot up in rings from the middle out,
	 * lean in and thrash, then sink back for the last ticks. The bigger the circle, the taller and thicker.
	 */
	private static void thicket(AlienProjectileEntity e, float partial, PoseStack pose, MultiBufferSource buffers) {
		float t = e.tickCount + partial;
		float life = Math.max(10, e.colour());
		float fade = Mth.clamp((life - t) / 8f, 0f, 1f);
		if (fade <= 0.01f) {
			return;
		}
		VertexConsumer vc = PyroDraw.solidBuffer(buffers);
		int light = light(e);
		float r = e.size();
		Vec3 base = e.getPosition(partial);
		java.util.Random rnd = new java.util.Random(e.getId() * 31L);
		int count = Math.round(r * 4f);
		for (int k = 0; k < count; k++) {
			double a = k * 2.39996 + rnd.nextDouble() * 0.2; // the golden angle: evenly scattered
			double d = 1.2 + Math.sqrt((k + 0.5) / count) * (r - 1.2);
			float grow = Mth.clamp((t - (float) (d * 0.45)) / 4f, 0f, 1f);
			grow = grow * (2f - grow) * fade;
			if (grow <= 0.01f) {
				continue;
			}
			double h = (1.2 + rnd.nextDouble() * r * 0.42) * grow;
			double phase = rnd.nextDouble() * Math.PI * 2;
			double thick = 0.09 + r * 0.012 + rnd.nextDouble() * 0.05;
			double x = Math.cos(a) * d, z = Math.sin(a) * d;
			double gy = groundAt(e, base.x + x, base.y, base.z + z) - base.y;
			Vec3 root = new Vec3(x, gy - 0.1, z);
			Vec3 in = new Vec3(-Math.cos(a), 0, -Math.sin(a));
			Vec3 round = new Vec3(-in.z, 0, in.x);
			List<Vec3> pts = new ArrayList<>();
			for (int i = 0; i <= 12; i++) {
				double s = i / 12.0;
				double sway = Math.sin(t * 0.35 + phase + s * 3.0) * 0.28 * s;
				// up, then the head arcs over toward him
				pts.add(root.add(0, Math.sin(s * Math.PI * 0.62) / Math.sin(Math.PI * 0.62) * h, 0).add(in.scale(s * s * s * h * 0.55)).add(round.scale(sway)));
			}
			tube(pose, vc, pts, s -> thick * (1.0 - 0.8 * s), light);
			leaves(pose, vc, pts, k, light);
		}
	}

	// ---------------------------------------------------------------- the burrow's mound

	/** Under the ground: a low mound of earth with a few shoots poking out, at his feet. */
	public static void mound(AbstractClientPlayer p, float partial, float grow, PoseStack pose, MultiBufferSource buffers, int light) {
		float t = p.tickCount + partial;
		VertexConsumer vc = PyroDraw.solidBuffer(buffers);
		pose.pushPose();
		pose.scale(grow, grow, grow);
		pose.pushPose();
		pose.scale(0.75f, 0.28f, 0.75f);
		PyroDraw.sphere(pose, vc, 1f, 0xFF6B4A2B, 0xFF4A321C, 0.18f, t * 0.4f, light);
		pose.popPose();
		for (int k = 0; k < 6; k++) {
			double a = k * Math.PI / 3 + 0.4;
			Vec3 root = new Vec3(Math.cos(a) * 0.3, 0.15, Math.sin(a) * 0.3);
			List<Vec3> pts = new ArrayList<>();
			for (int i = 0; i <= 5; i++) {
				double s = i / 5.0;
				pts.add(root.add(Math.sin(t * 0.3 + k) * 0.08 * s + Math.cos(a) * 0.15 * s, s * 0.45, Math.sin(a) * 0.15 * s));
			}
			tube(pose, vc, pts, s -> 0.045 * (1.0 - 0.6 * s), light);
			leaves(pose, vc, pts, k, light);
		}
		pose.popPose();
	}
}
