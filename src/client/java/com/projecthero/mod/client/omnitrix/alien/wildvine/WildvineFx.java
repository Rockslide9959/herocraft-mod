package com.projecthero.mod.client.omnitrix.alien.wildvine;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projecthero.mod.client.mutation.pyro.PyroDraw;
import com.projecthero.mod.client.omnitrix.OmnitrixRenderers;
import com.projecthero.mod.client.omnitrix.alien.brainstorm.BrainstormFx;
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
 * shooting out over their first ticks), the coils round a wrapped creature (a vine spiralling round it), the seed pod in flight, the
 * planted seed mines (half-buried black pods with a sprout, pulsing once armed), the Overgrowth thicket (tendrils rearing out of the
 * ground all round him) and the mound he moves as under the ground.
 */
public final class WildvineFx {
	private WildvineFx() {
	}

	private static boolean registered;
	/** Vine greens: the stem, its darker ribs, a leaf; the seed pods' near-black and their green tint. */
	private static final int STEM = 0xFF4E9A3C, RIB = 0xFF2E6A2E, LEAF = 0xFF6CBF3C, POD = 0xFF1C1E16, POD_HI = 0xFF3A4A2A;

	public static void register() {
		if (registered) {
			return;
		}
		registered = true;
		OmnitrixRenderers.projectileLook("wildvine_vine", WildvineFx::vine);
		OmnitrixRenderers.projectileLook("wildvine_wrap", WildvineFx::wrap);
		OmnitrixRenderers.projectileLook("wildvine_seed", WildvineFx::seed);
		OmnitrixRenderers.projectileLook("wildvine_mine", WildvineFx::mine);
		OmnitrixRenderers.projectileLook("wildvine_thicket", WildvineFx::thicket);
	}

	// ---------------------------------------------------------------- where his vines leave from

	/**
	 * His right hand: in first person low and right of the view (so the vine rises into it), otherwise out in front of his right
	 * shoulder at arm's length, by his body's facing and size.
	 */
	public static Vec3 hand(Player p, float partial) {
		Minecraft mc = Minecraft.getInstance();
		if (p == mc.player && mc.options.getCameraType() == CameraType.FIRST_PERSON) {
			Vec3 eye = p.getEyePosition(partial);
			Vec3 look = p.getViewVector(partial);
			Vec3 right = look.cross(new Vec3(0, 1, 0));
			right = right.lengthSqr() < 1.0e-6 ? new Vec3(1, 0, 0) : right.normalize();
			return eye.add(look.scale(0.5)).add(right.scale(0.32)).add(0, -0.32, 0);
		}
		float yaw = Mth.lerp(partial, p.yBodyRotO, p.yBodyRot) * Mth.DEG_TO_RAD;
		Vec3 fwd = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
		Vec3 right = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw));
		float s = p.getScale();
		return p.getPosition(partial).add(0, 1.3 * s, 0).add(right.scale(0.36 * s)).add(fwd.scale(0.4 * s));
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
		Vec3 start = hand(owner, partial);
		Vec3 end = e.getPosition(partial);
		if (e.colour() > 0) {
			Entity t = mc.level.getEntity(e.colour() - 1);
			if (t == null) {
				return;
			}
			end = t.getPosition(partial).add(0, t.getBbHeight() * 0.55, 0);
		}
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
		float grow = Mth.clamp(age / 5f, 0.1f, 1f);
		Vec3 base = t.getPosition(partial).subtract(e.getPosition(partial));
		double w = t.getBbWidth() * 0.5 + 0.08;
		double h = t.getBbHeight() * 0.92;
		double squeeze = 1.0 - 0.06 * Math.max(0, Math.sin(age * 0.6));
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

	/** The seed pods' colours (v0.17.2, user: "add little seed bomb models to wildvines back"): olive-brown skin, green belly, dark cap. */
	private static final int POD_SKIN = 0xFF7A7036, POD_BELLY = 0xFF46592A, POD_CAP = 0xFF2B2216, POD_SHINE = 0xFFE4EEB8, POD_STEM = 0xFF5A6A2A;

	/**
	 * A seed pod of {@code size}, its stem toward +y: a round green-brown body (a little longer than wide), a darker cap where it
	 * hangs, a short stem with a leaf and a glint of shine on its shoulder; {@code glow} warms the body toward leaf green (an armed
	 * mine's pulse).
	 */
	static void pod(PoseStack pose, VertexConsumer vc, float size, float t, int light, float glow) {
		pose.pushPose();
		pose.scale(size * 0.85f, size * 1.05f, size * 0.85f);
		PyroDraw.sphere(pose, vc, 0.5f, PyroDraw.lerpRgb(POD_SKIN, LEAF, glow) | 0xFF000000, POD_BELLY, 0.06f, t * 0.15f, light);
		pose.popPose();
		// the cap: a flattened dark dome over the top
		pose.pushPose();
		pose.translate(0, size * 0.4f, 0);
		pose.scale(size * 0.62f, size * 0.26f, size * 0.62f);
		PyroDraw.sphere(pose, vc, 0.5f, POD_CAP, 0xFF1A140C, 0.04f, 0f, light);
		pose.popPose();
		// the shine: a small bright fleck on the upper front shoulder (full bright, so it reads as a highlight)
		pose.pushPose();
		pose.translate(-size * 0.17f, size * 0.2f, size * 0.3f);
		pose.scale(size * 0.13f, size * 0.17f, size * 0.07f);
		PyroDraw.sphere(pose, vc, 0.5f, POD_SHINE, POD_SHINE, 0f, 0f, PyroDraw.FULL);
		pose.popPose();
		List<Vec3> stem = List.of(new Vec3(0, size * 0.48, 0), new Vec3(size * 0.04, size * 0.62, 0), new Vec3(size * 0.12, size * 0.72, size * 0.03));
		tubeColour(pose, vc, stem, s -> size * 0.07, POD_STEM, light);
		if (size > 0.1f) {
			leaves(pose, vc, List.of(new Vec3(0, size * 0.5, 0), new Vec3(0, size * 0.55, 0), new Vec3(size * 0.04, size * 0.62, 0),
					new Vec3(size * 0.12, size * 0.72, size * 0.03)), 1, light);
		}
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

	/**
	 * v0.17.2 (user: "can you add little seed bomb models to wildvines back please"): where the skin painted its flat pods (now painted
	 * out) -- {x, y down, size in blocks} in body-bone pixels: the big upper pair (texels 33..34 / 37..38, rows 23..25), the slimmer
	 * pair under them (34 / 37, rows 27..29) and two buds below (row 31). The first four are his seed bombs.
	 */
	private static final float[][] BACK_PODS = { { -2.0f, 4.4f, 0.20f }, { 2.0f, 4.4f, 0.20f }, { -1.5f, 8.5f, 0.15f }, { 1.5f, 8.5f, 0.15f },
			{ -1.5f, 11.4f, 0.08f }, { 1.5f, 11.4f, 0.08f } };

	/**
	 * How grown back pod {@code i} is (0 gone .. 1 full): a Seed Bomb plucks one of the four big ones (which, by the throw's cooldown end:
	 * the same for every viewer) and a Seed Mines plants three of them; each regrows over its move's cooldown.
	 */
	public static float podGrowth(Player p, int i, int bombLeft, long bombEnd, int mineLeft, long mineEnd) {
		if (i >= 4) {
			return 1f;
		}
		float g = 1f;
		if (bombLeft > 0 && Math.floorMod(bombEnd, 4) == i) {
			g = Math.min(g, 1f - bombLeft / (float) com.projecthero.mod.omnitrix.alien.wildvine.WildvineKit.SEED_BOMB_COOLDOWN);
		}
		if (mineLeft > 0 && Math.floorMod(mineEnd, 4) != i) {
			g = Math.min(g, 1f - mineLeft / (float) com.projecthero.mod.omnitrix.alien.wildvine.WildvineKit.SEED_MINES_COOLDOWN);
		}
		// nothing for the first half of the wait, then it swells back
		float k = Mth.clamp((g - 0.5f) * 2f, 0f, 1f);
		return k * k * (3f - 2f * k);
	}

	/** The seed pods on his back, on the body bone, swaying a little as he moves. */
	public static void backPods(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer p,
			net.minecraft.client.model.PlayerModel<AbstractClientPlayer> model, float partial, float age) {
		var kit = com.projecthero.mod.omnitrix.alien.Aliens.kit(com.projecthero.mod.omnitrix.alien.wildvine.WildvineKit.ID);
		if (kit == null) {
			return;
		}
		int bombLeft = kit.cooldownLeft(p, com.projecthero.mod.omnitrix.alien.wildvine.WildvineKit.SEED_BOMB);
		int mineLeft = kit.cooldownLeft(p, com.projecthero.mod.omnitrix.alien.wildvine.WildvineKit.SEED_MINES);
		long bombEnd = com.projecthero.mod.omnitrix.Omnitrix.state(p).readyAt(kit.key(com.projecthero.mod.omnitrix.alien.wildvine.WildvineKit.SEED_BOMB));
		long mineEnd = com.projecthero.mod.omnitrix.Omnitrix.state(p).readyAt(kit.key(com.projecthero.mod.omnitrix.alien.wildvine.WildvineKit.SEED_MINES));
		float walk = p.walkAnimation.speed(partial), step = p.walkAnimation.position(partial);
		VertexConsumer vc = PyroDraw.solidBuffer(buffers);
		pose.pushPose();
		model.body.translateAndRotate(pose);
		for (int i = 0; i < BACK_PODS.length; i++) {
			float[] b = BACK_PODS[i];
			float g = podGrowth(p, i, bombLeft, bombEnd, mineLeft, mineEnd);
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

	// ---------------------------------------------------------------- the Overgrowth

	/** Tendrils rearing out of the ground all round him (radius = size): they shoot up, sway, then sink back for the last ticks. */
	private static void thicket(AlienProjectileEntity e, float partial, PoseStack pose, MultiBufferSource buffers) {
		float t = e.tickCount + partial;
		float grow = Mth.clamp(t / 5f, 0f, 1f) * Mth.clamp((30f - t) / 8f, 0f, 1f);
		if (grow <= 0.01f) {
			return;
		}
		VertexConsumer vc = PyroDraw.solidBuffer(buffers);
		int light = light(e);
		float r = e.size();
		java.util.Random rnd = new java.util.Random(e.getId() * 31L);
		for (int k = 0; k < 18; k++) {
			double a = k * Math.PI * 2 / 18 + rnd.nextDouble() * 0.3;
			double d = 1.5 + rnd.nextDouble() * (r - 1.5);
			double h = (1.4 + rnd.nextDouble() * 1.6) * grow;
			Vec3 root = new Vec3(Math.cos(a) * d, 0, Math.sin(a) * d);
			Vec3 lean = new Vec3(-Math.cos(a), 0, -Math.sin(a)).scale(0.6); // they curl in toward him
			List<Vec3> pts = new ArrayList<>();
			for (int i = 0; i <= 10; i++) {
				double s = i / 10.0;
				double sway = Math.sin(t * 0.25 + k) * 0.15 * s;
				pts.add(root.add(lean.scale(s * s)).add(sway, s * h, sway * 0.5));
			}
			tube(pose, vc, pts, s -> 0.12 * (1.0 - 0.75 * s), light);
			leaves(pose, vc, pts, k, light);
		}
	}

	// ---------------------------------------------------------------- the burrow's mound

	/** Under the ground: a low mound of earth with a few shoots poking out, at his feet. */
	public static void mound(AbstractClientPlayer p, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
		float t = p.tickCount + partial;
		VertexConsumer vc = PyroDraw.solidBuffer(buffers);
		pose.pushPose();
		pose.scale(0.75f, 0.28f, 0.75f);
		PyroDraw.sphere(pose, vc, 1f, 0xFF6B4A2B, 0xFF4A321C, 0.18f, t * 0.4f, light);
		pose.popPose();
		for (int k = 0; k < 4; k++) {
			double a = k * Math.PI / 2 + 0.4;
			Vec3 root = new Vec3(Math.cos(a) * 0.3, 0.15, Math.sin(a) * 0.3);
			List<Vec3> pts = new ArrayList<>();
			for (int i = 0; i <= 5; i++) {
				double s = i / 5.0;
				pts.add(root.add(Math.sin(t * 0.3 + k) * 0.08 * s + Math.cos(a) * 0.15 * s, s * 0.45, Math.sin(a) * 0.15 * s));
			}
			tube(pose, vc, pts, s -> 0.045 * (1.0 - 0.6 * s), light);
			leaves(pose, vc, pts, k, light);
		}
	}
}
