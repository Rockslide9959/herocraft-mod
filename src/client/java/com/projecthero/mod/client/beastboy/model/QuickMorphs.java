package com.projecthero.mod.client.beastboy.model;

import org.joml.Quaternionf;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.beastboy.BeastForm;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;

/**
 * v0.17.3 Beast Boy's quick-morph partials (morph round): the one body part a human-form move turns into, taken from the new blocky
 * animal models and attached to his own player model at the joint (shoulders, neck, hips, upper back), following his pose.
 *
 * <ul>
 *   <li>Smash (gorilla): both arms become the gorilla's arms ({@code GorillaGeo} armL/armR with forearm and hand), raised overhead and
 *   slammed down.</li>
 *   <li>Ram (rhino): his head becomes the rhino's head with both horns ({@code RhinoGeo} head), lowered to charge.</li>
 *   <li>Roar (T-Rex): his head becomes the T-Rex head on a neck stump ({@code TRexModel.quickHead}), jaws wide.</li>
 *   <li>Stomp (elephant): his head becomes the elephant's head with trunk, tusks and ears and his legs the elephant's legs
 *   ({@code ElephantGeo}); the trunk rears up, one leg lifts and stomps.</li>
 *   <li>Grab (octopus): four of the octopus's arms ({@code SeaShapes.octopus}) burst out of his back, reach round and coil.</li>
 * </ul>
 * The tiger pounce and the snake strike show the whole animal (BeastMorph's full-body morph).
 *
 * <p><b>Timeline</b> over the move's 10 ticks ({@code q} 0..1): his part swells and stretches toward the animal part (0..0.14), the
 * animal part breaks out of it at 45% size and springs to full with an overshoot while his part is hidden (0.10..0.30), the move plays
 * on the animal part, then it shrinks back (0.78..0.90) as his own part swells back out and settles (0.86..1). Green glow peaks at both
 * hand-overs. Pose stack for {@link #render}: the player model root (vanilla model space: y down, front -Z, units of 1/16 in parts).
 */
public final class QuickMorphs {
	private static final BigBox.Lazy GORILLA = new BigBox.Lazy(GorillaGeo::layer, GorillaGeo.TREE, "gorilla");
	private static final BigBox.Lazy RHINO = new BigBox.Lazy(RhinoGeo::layer, RhinoGeo.TREE, "rhino");
	private static final BigBox.Lazy ELEPHANT = new BigBox.Lazy(ElephantGeo::layer, ElephantGeo.TREE, "elephant");
	private static ModelPart octoRoot;
	private static java.util.Map<String, ModelPart> octo;

	/** Which of his parts each move replaces. */
	private static final int ARMS = 1, HEAD = 2, LEGS = 4, BACK = 8;

	private QuickMorphs() {
	}

	/** True for the quick morphs drawn as a part on his body (the rest show the whole animal). */
	public static boolean partial(BeastForm f) {
		return mask(f) != 0;
	}

	private static int mask(BeastForm f) {
		if (f == null) {
			return 0;
		}
		return switch (f) {
			case GORILLA -> ARMS;
			case RHINO, TREX -> HEAD;
			case ELEPHANT -> HEAD | LEGS;
			case OCTOPUS -> BACK;
			default -> 0;
		};
	}

	// ---------------------------------------------------------------- timeline

	private static float s(float t) {
		return BeastPose.smooth(t);
	}

	/** Kept for callers of the old partials: 0..1 how much of the animal part is out. */
	public static float envelope(float q) {
		return animalScale(q) > 0f ? Math.min(1f, animalScale(q)) : 0f;
	}

	/** His part's swell 0..1 (it stretches toward the animal part before handing over, and settles back after). */
	public static float humanSwell(float q) {
		if (q < 0.14f) {
			return s(q / 0.14f);
		}
		if (q > 0.86f) {
			return 1f - s((q - 0.86f) / 0.14f);
		}
		return 1f;
	}

	public static boolean humanHidden(float q) {
		return q >= 0.14f && q < 0.86f;
	}

	/** The animal part's size 0..~1.1 (0 = not drawn). */
	public static float animalScale(float q) {
		if (q < 0.1f || q >= 0.9f) {
			return 0f;
		}
		if (q < 0.22f) {
			return Mth.lerp(s((q - 0.1f) / 0.12f), 0.45f, 1.1f);
		}
		if (q < 0.3f) {
			return Mth.lerp(s((q - 0.22f) / 0.08f), 1.1f, 1f);
		}
		if (q < 0.78f) {
			return 1f;
		}
		return Mth.lerp(s((q - 0.78f) / 0.12f), 1f, 0.4f);
	}

	/** Green glow 0..1: peaks at both hand-overs. */
	public static float glow(float q) {
		return Math.max(0f, 1f - Math.abs(q - 0.13f) / 0.1f) + Math.max(0f, 1f - Math.abs(q - 0.87f) / 0.1f) * 0.8f;
	}

	/** 0..1 through the move itself (between the hand-overs). */
	private static float moveT(float q) {
		return Mth.clamp((q - 0.2f) / 0.6f, 0f, 1f);
	}

	// ---------------------------------------------------------------- his own parts

	/**
	 * Called after the player renderer set his model's part visibility: swells the parts the move replaces and hides them while the
	 * animal part is out. Returns true when it changed anything (the caller resets the scales next time).
	 */
	public static boolean adjustHuman(PlayerModel<?> m, BeastForm quick, float q) {
		int mask = mask(quick);
		if (mask == 0) {
			return false;
		}
		float sw = humanSwell(q);
		boolean hide = humanHidden(q);
		if ((mask & ARMS) != 0) {
			part(m.leftArm, m.leftSleeve, sw, 0.6f, 0.25f, hide);
			part(m.rightArm, m.rightSleeve, sw, 0.6f, 0.25f, hide);
		}
		if ((mask & HEAD) != 0) {
			part(m.head, m.hat, sw, 0.35f, 0.3f, hide);
		}
		if ((mask & LEGS) != 0) {
			part(m.leftLeg, m.leftPants, sw, 0.5f, 0f, hide);
			part(m.rightLeg, m.rightPants, sw, 0.5f, 0f, hide);
		}
		if ((mask & BACK) != 0) {
			float b = 1f + 0.25f * sw * (hide ? 0.4f : 1f);
			m.body.zScale = b;
			m.jacket.zScale = b;
		}
		return true;
	}

	private static void part(ModelPart a, ModelPart b, float sw, float thick, float stretch, boolean hide) {
		for (ModelPart p : new ModelPart[] { a, b }) {
			p.xScale = 1f + thick * sw;
			p.zScale = 1f + thick * sw;
			p.yScale = 1f + stretch * sw;
			if (hide) {
				p.visible = false;
			}
		}
	}

	/** Puts the scales back (the model is shared by every player). */
	public static void resetHuman(PlayerModel<?> m) {
		for (ModelPart p : new ModelPart[] { m.head, m.hat, m.body, m.jacket, m.leftArm, m.rightArm, m.leftSleeve, m.rightSleeve, m.leftLeg, m.rightLeg,
				m.leftPants, m.rightPants }) {
			p.xScale = 1f;
			p.yScale = 1f;
			p.zScale = 1f;
		}
	}

	// ---------------------------------------------------------------- the animal parts

	/**
	 * Draws the animal part of {@code quick} at {@code q} on the posed player model {@code m}. Pose stack at the model root.
	 *
	 * @return false when nothing was drawn
	 */
	public static boolean render(BeastForm quick, float q, PlayerModel<?> m, PoseStack pose, MultiBufferSource buffers, int light, int overlay,
			float age) {
		float k = animalScale(q);
		int mask = mask(quick);
		if (k <= 0f || mask == 0) {
			return false;
		}
		float mt = moveT(q);
		float gl = Math.min(1f, glow(q));
		switch (quick) {
			case GORILLA -> gorillaArms(m, pose, buffers, light, overlay, k, mt, gl);
			case RHINO -> rhinoHead(m, pose, buffers, light, overlay, k, mt, gl, age);
			case TREX -> trexHead(m, pose, buffers, light, k, mt, gl, age);
			case ELEPHANT -> elephant(m, pose, buffers, light, overlay, k, mt, gl, age);
			case OCTOPUS -> tentacles(m, pose, buffers, light, overlay, k, mt, gl, age);
			default -> {
				return false;
			}
		}
		return true;
	}

	/** First person: the gorilla arm in place of the hand the game draws (the smash lifts and slams it). */
	public static boolean renderHand(BeastForm quick, float q, ModelPart arm, boolean right, PoseStack pose, MultiBufferSource buffers, int light) {
		float k = animalScale(q);
		if (k <= 0f || quick != BeastForm.GORILLA) {
			return false;
		}
		BigBox.Rig r = GORILLA.get();
		r.reset();
		ModelPart a = r.p(right ? "armR" : "armL");
		float mt = moveT(q);
		float lift = mt < 0.4f ? s(mt / 0.4f) : (mt < 0.55f ? 1f - s((mt - 0.4f) / 0.15f) * 1.4f : -0.4f * (1f - s((mt - 0.55f) / 0.45f)));
		pose.pushPose();
		a.x = arm.x;
		a.y = arm.y;
		a.z = arm.z;
		a.xRot = arm.xRot - lift * 0.6f;
		a.yRot = arm.yRot;
		a.zRot = arm.zRot;
		float sc = 0.62f * k;
		a.xScale = a.yScale = a.zScale = sc;
		draw(a, r.tex, pose, buffers, light, OverlayTexture.NO_OVERLAY, Math.min(1f, glow(q)));
		pose.popPose();
		return true;
	}

	/** The part, then the green glow over it. */
	private static void draw(ModelPart p, net.minecraft.resources.ResourceLocation tex, PoseStack pose, MultiBufferSource buffers, int light, int overlay,
			float glow) {
		p.render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(tex)), light, overlay, 0xFFFFFFFF);
		if (glow > 0.01f) {
			int g = 0xFF000000 | ((int) (150 * glow) << 16) | ((int) (255 * glow) << 8) | (int) (120 * glow);
			p.render(pose, buffers.getBuffer(RenderType.eyes(tex)), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, g);
		}
	}

	private static void gorillaArms(PlayerModel<?> m, PoseStack pose, MultiBufferSource buffers, int light, int overlay, float k, float mt, float gl) {
		BigBox.Rig r = GORILLA.get();
		r.reset();
		// wind up overhead, slam down in front, hold, let go
		float up = mt < 0.4f ? BigRig.easeOut(mt / 0.4f) : 0f;
		float slam = mt >= 0.4f ? BigRig.easeIn(Mth.clamp((mt - 0.4f) / 0.14f, 0f, 1f)) : 0f;
		float rel = s((mt - 0.82f) / 0.18f);
		for (int side = -1; side <= 1; side += 2) {
			ModelPart human = side < 0 ? m.rightArm : m.leftArm;
			ModelPart a = r.p(side < 0 ? "armR" : "armL");
			ModelPart fore = r.p(side < 0 ? "foreR" : "foreL");
			float target = mt < 0.4f ? -2.75f * up : Mth.lerp(slam, -2.75f, -1.05f);
			target = Mth.lerp(rel, target, 0f);
			a.x = human.x - side * 0.5f;
			a.y = human.y;
			a.z = human.z;
			a.xRot = Mth.lerp(Math.min(1f, mt * 4f), human.xRot, target);
			a.yRot = m.body.yRot;
			a.zRot = side * (0.12f + 0.15f * up * (1f - slam));
			fore.xRot = -0.35f * up * (1f - slam) - 0.15f;
			float sc = 0.62f * k;
			a.xScale = a.yScale = a.zScale = sc;
			draw(a, r.tex, pose, buffers, light, overlay, gl);
		}
	}

	private static void rhinoHead(PlayerModel<?> m, PoseStack pose, MultiBufferSource buffers, int light, int overlay, float k, float mt, float gl,
			float age) {
		BigBox.Rig r = RHINO.get();
		r.reset();
		ModelPart h = r.p("head");
		float lower = s(mt / 0.25f) * (1f - s((mt - 0.85f) / 0.15f));
		h.x = m.head.x;
		h.y = m.head.y - 3f;
		h.z = m.head.z + 1f;
		h.xRot = m.head.xRot * 0.6f + 0.15f + lower * 0.45f + Mth.sin(age * 1.6f) * 0.03f * lower;
		h.yRot = m.head.yRot;
		h.zRot = m.head.zRot;
		r.p("jaw").xRot = 0.15f * lower;
		float sc = 0.62f * k;
		h.xScale = h.yScale = h.zScale = sc;
		draw(h, r.tex, pose, buffers, light, overlay, gl);
	}

	private static void trexHead(PlayerModel<?> m, PoseStack pose, MultiBufferSource buffers, int light, float k, float mt, float gl, float age) {
		float jaw = s(mt / 0.2f) * (1f - s((mt - 0.82f) / 0.18f));
		pose.pushPose();
		pose.translate(m.head.x / 16f, m.head.y / 16f, m.head.z / 16f);
		pose.mulPose(new Quaternionf().rotationZYX(m.head.zRot, m.head.yRot, m.head.xRot));
		// model space -> feet-style space (+Y up, +Z front) for the mesh head
		pose.scale(1f, -1f, -1f);
		pose.translate(0f, 3.5f / 16f, -3.5f / 16f);
		float sc = 0.85f * k;
		pose.scale(sc, sc, sc);
		pose.mulPose(com.mojang.math.Axis.YP.rotation(Mth.sin(age * 2.2f) * 0.05f * jaw));
		pose.mulPose(com.mojang.math.Axis.XP.rotation(-0.35f - jaw * 0.25f));
		BeastMesh.draw(p -> TRexModel.quickHead(p, jaw, age), pose, buffers, light, 0xFFFFFFFF, gl, false);
		pose.popPose();
	}

	private static void elephant(PlayerModel<?> m, PoseStack pose, MultiBufferSource buffers, int light, int overlay, float k, float mt, float gl,
			float age) {
		BigBox.Rig r = ELEPHANT.get();
		r.reset();
		// trunk up and trumpet, then whip down with the stomp
		float rear = s(mt / 0.35f) * (1f - s((mt - 0.45f) / 0.15f));
		float lift = mt < 0.45f ? s((mt - 0.08f) / 0.35f) : Math.max(0f, 1f - (mt - 0.45f) / 0.08f);
		ModelPart h = r.p("head");
		h.x = m.head.x;
		h.y = m.head.y - 4f;
		h.z = m.head.z + 1.5f;
		h.xRot = m.head.xRot * 0.6f - rear * 0.3f;
		h.yRot = m.head.yRot;
		h.zRot = m.head.zRot;
		for (int i = 1; i <= 7; i++) {
			r.p("trunk" + i).xRot = -rear * 0.42f + (1f - rear) * Mth.sin(age * 0.25f - i * 0.5f) * 0.06f;
		}
		r.p("earL").yRot = -Mth.sin(age * 0.5f) * 0.25f * rear;
		r.p("earR").yRot = Mth.sin(age * 0.5f) * 0.25f * rear;
		float hs = 0.55f * k;
		h.xScale = h.yScale = h.zScale = hs;
		draw(h, r.tex, pose, buffers, light, overlay, gl);
		// legs: his right leg lifts and stomps
		for (int side = -1; side <= 1; side += 2) {
			ModelPart human = side < 0 ? m.rightLeg : m.leftLeg;
			ModelPart leg = r.p(side < 0 ? "legFR" : "legFL");
			ModelPart shin = r.p(side < 0 ? "shinFR" : "shinFL");
			ModelPart foot = r.p(side < 0 ? "footFR" : "footFL");
			float l = side < 0 ? lift : 0f;
			leg.x = human.x;
			leg.y = human.y;
			leg.z = human.z;
			leg.xRot = human.xRot * 0.5f - l * 1.0f;
			leg.yRot = 0f;
			leg.zRot = 0f;
			shin.xRot = l * 1.1f;
			foot.xRot = -l * 0.1f;
			leg.xScale = leg.zScale = 0.62f * k;
			leg.yScale = 0.5f * k;
			draw(leg, r.tex, pose, buffers, light, overlay, gl);
		}
	}

	private static void tentacles(PlayerModel<?> m, PoseStack pose, MultiBufferSource buffers, int light, int overlay, float k, float mt, float gl,
			float age) {
		if (octoRoot == null) {
			octoRoot = SeaShapes.octopus();
			octo = SeaShapes.octopusParts(octoRoot);
		}
		float reach = s(mt / 0.3f) * (1f - s((mt - 0.75f) / 0.25f));
		float coil = s((mt - 0.3f) / 0.25f) * (1f - s((mt - 0.8f) / 0.2f));
		pose.pushPose();
		pose.translate(m.body.x / 16f, m.body.y / 16f, m.body.z / 16f);
		pose.mulPose(new Quaternionf().rotationZYX(m.body.zRot, m.body.yRot, m.body.xRot));
		for (int i = 0; i < 4; i++) {
			int side = i % 2 == 0 ? 1 : -1;
			boolean upper = i < 2;
			ModelPart[] chain = new ModelPart[9];
			for (int j = 0; j < 9; j++) {
				chain[j] = octo.get("arm" + i + "_" + j);
				chain[j].resetPose();
			}
			ModelPart a = chain[0];
			a.x = side * 2.4f;
			a.y = upper ? 3.5f : 8.5f;
			a.z = 2.5f;
			// out of the back and round to the front
			a.yRot = side * Mth.lerp(reach, 2.5f, 1.25f) + Mth.sin(age * 0.3f + i) * 0.05f;
			a.xRot = (upper ? 0.3f : -0.15f) - reach * 0.15f;
			for (int j = 1; j < 9; j++) {
				chain[j].yRot = side * (0.1f + (j >= 5 ? 0.42f * coil : 0.06f * reach)) + Mth.sin(age * 0.4f - j * 0.6f + i) * 0.06f;
				chain[j].xRot = Mth.sin(age * 0.33f - j * 0.5f + i * 1.3f) * 0.07f;
			}
			float sc = 0.7f * k;
			a.xScale = a.yScale = a.zScale = sc;
			draw(a, OctopusModel.TEX, pose, buffers, light, overlay, gl);
		}
		pose.popPose();
	}
}
