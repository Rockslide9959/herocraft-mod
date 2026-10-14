package com.projecthero.mod.client.spider;

import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.client.render.JointedLimbs;
import com.projecthero.mod.spider.SpiderMask;
import com.projecthero.mod.spider.SpiderSwing;
import com.projecthero.mod.spider.item.SymbioteArmorItem;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;

/**
 * v0.16.2 (user: "add an animation for the player removing and putting back on their mask, make it like how he does it
 * in the movies"): the client side of {@link SpiderMask}'s one-second take-off / put-on.
 *
 * <ul>
 * <li><b>Off</b> -- the right hand reaches up under the chin, takes hold and peels the mask up the face and over the top
 * of the head in one pull (the face uncovered from the chin up, the bunched fabric rolling up with the hand), then
 * brings it down to his side, the crumpled mask in his fist for a beat before it is put away.</li>
 * <li><b>On</b> -- both hands lift the bunched mask above the head and pull it down over it (the face covered from the
 * crown down, the hands riding the rolled edge down either side of the head), then the right hand gives a tug at the
 * neck to settle it.</li>
 * </ul>
 *
 * The arms are two-bone IK chains ({@link SpiderPerch#solve}, bent at the elbow through {@link JointedLimbs}) reaching
 * for hand targets set in the HEAD's frame, so the hand stays on the mask wherever he is looking. The red suit's mask
 * is drawn clipped at the moving edge ({@link #clippedHead}), the bunched edge as a rolled ring of fabric
 * ({@link #renderExtras}), and the mask in hand as a small crumpled lump showing its eyes. Everything is driven by the
 * synced start tick, so every viewer sees the same frame. First person: the hands sweep across the view
 * ({@code SpiderMaskHandMixin}) and the fabric passes over the eyes ({@link #hud}).
 */
public final class SpiderMaskAnim {
	/** A patch of plain red on the suit texture (the mask's crown), for the rolled fabric. */
	private static final float RED_U0 = 9f / 64f;
	private static final float RED_V0 = 1f / 64f;
	private static final float RED_U1 = 13f / 64f;
	private static final float RED_V1 = 3f / 64f;
	/** The black Symbiote suit's mask: the same patches, darkened. */
	private static final int SYMBIOTE_TINT = 0xFFB8B8C0;
	/** ... drawn from a patch of black (the eye outline on the same texture). */
	private static final float BLACK_U0 = 7f / 64f;
	private static final float BLACK_V0 = 9f / 64f;
	private static final float BLACK_U1 = 8f / 64f;
	private static final float BLACK_V1 = 11f / 64f;
	/** The fabric patch for the current draw (red suit or black). Render thread only. */
	private static float fu0 = RED_U0;
	private static float fv0 = RED_V0;
	private static float fu1 = RED_U1;
	private static float fv1 = RED_V1;

	/** The red suit's mask cut off at every quarter pixel (index = covered height x 4), built on first use. */
	private static final ModelPart[] CLIP_HEAD = new ModelPart[33];
	private static final ModelPart[] CLIP_HAT = new ModelPart[33];

	private SpiderMaskAnim() {
	}

	// ------------------------------------------------------------------ the timeline

	/** One moment of the animation: arm weights, hand targets (head frame, px), the rolled edge, the mask in hand. */
	public static final class Rig {
		public float p;
		public boolean open;
		/** How much of the head the mask covers (from the crown down). */
		public float c;
		public float kR;
		public float kL;
		public final Vector3f handR = new Vector3f();
		public final Vector3f handL = new Vector3f();
		/** The bunched / rolled fabric round the head at {@link #ringY} (head px), {@link #ringTh} thick. */
		public boolean ring;
		public float ringY;
		public float ringTh;
		/** The crumpled mask in the right fist. */
		public boolean lump;
		public float lumpScale = 1f;
		/** Extra head pitch (chin down) while he works the mask. */
		public float headPitch;
	}

	static double now(Player player) {
		return player.level().getGameTime() + Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
	}

	/** How much of the head the mask covers right now (1 = on, 0 = off). */
	public static float coverage(Player player) {
		return SpiderMask.coverage(player, now(player));
	}

	/** The animation at {@code time}, or null when none is playing. */
	public static Rig rig(Player player, double time) {
		if (!SpiderMask.wearingHood(player)) {
			return null;
		}
		float p = SpiderMask.progress(player, time);
		if (p >= 1f) {
			return null;
		}
		return rig(SpiderMask.isOpen(player), p);
	}

	/** The animation for a direction ({@code open} = taking it off) at progress {@code p} (also the harness's). */
	public static Rig rig(boolean open, float p) {
		Rig r = new Rig();
		r.p = p;
		r.open = open;
		r.c = SpiderMask.coverage(open, p);
		float edge = -8f + 8f * r.c;
		if (open) {
			takeOff(r, edge);
		} else {
			putOn(r, edge);
		}
		return r;
	}

	/** A point on an arc round the face and over the crown, radius 7 px about the middle of the head. */
	private static Vector3f arc(float x, float theta) {
		return new Vector3f(x, -4f - 7f * Mth.sin(theta), -7f * Mth.cos(theta));
	}

	private static void takeOff(Rig r, float edge) {
		float p = r.p;
		r.kR = smooth(p / 0.2f) * (1f - smooth((p - 0.84f) / 0.16f));
		final float peel0 = SpiderMask.OFF_PEEL_START;
		final float peel1 = SpiderMask.OFF_PEEL_END;
		final float overTop = 0.70f;
		final float lowered = 0.86f;
		if (p < peel0) {
			// up under the chin, fingers hooked under the mask's hem
			r.handR.set(-1.2f, 1.1f - 0.4f * smooth((p - 0.18f) / 0.12f), -5.2f);
		} else if (p < peel1) {
			// the peel: the hand drags the rolled hem up the face, the face uncovered beneath it
			float sin = Mth.clamp((-5f - edge) / 7f, -1f, 1f);
			r.handR.set(arc(-1.2f, (float) Math.asin(sin)));
			r.ring = r.c > 0.01f;
			r.ringY = edge;
			r.ringTh = 0.6f + 2.2f * (1f - r.c);
		} else if (p < overTop) {
			// ... and over the top of the head, off
			float q = smooth((p - peel1) / (overTop - peel1));
			r.handR.set(arc(Mth.lerp(q, -1.2f, -1.8f), Mth.lerp(q, (float) Math.asin(3f / 7f), 1.75f)));
			r.lump = true;
		} else {
			// brought down past the side of the head to his hip, the mask in his fist
			Vector3f top = arc(-1.8f, 1.75f);
			Vector3f side = new Vector3f(-8.2f, -3.5f, -2.5f);
			Vector3f low = new Vector3f(-6.2f, 9.5f, -4.2f);
			float q = Mth.clamp((p - overTop) / (lowered - overTop), 0f, 1f);
			if (q < 0.45f) {
				r.handR.set(top).lerp(side, smooth(q / 0.45f));
			} else {
				r.handR.set(side).lerp(low, smooth((q - 0.45f) / 0.55f));
			}
			r.lump = true;
			r.lumpScale = 1f - smooth((p - 0.92f) / 0.08f);
		}
		// chin dipped into the pull, head coming up as the mask clears it
		r.headPitch = 0.22f * Mth.sin(Mth.PI * Mth.clamp((p - 0.2f) / 0.5f, 0f, 1f));
	}

	private static void putOn(Rig r, float edge) {
		float p = r.p;
		r.kR = smooth(p / 0.18f) * (1f - smooth((p - 0.86f) / 0.14f));
		r.kL = smooth(p / 0.18f) * (1f - smooth((p - 0.66f) / 0.14f));
		final float pull0 = SpiderMask.ON_PULL_START;
		final float pull1 = SpiderMask.ON_PULL_END;
		final float tugEnd = 0.86f;
		Vector3f chest = new Vector3f(-4.2f, 2.5f, -6.5f);
		Vector3f over = new Vector3f(-4.6f, -11.4f, -1.4f);
		float y;
		if (p < pull0) {
			// the bunched mask lifted up the front of the face and over the head in both hands
			float q = smooth(p / pull0);
			r.handR.set(chest).lerp(over, q);
			r.handR.z = Mth.lerp(q, chest.z, over.z) - 2.0f * Mth.sin(Mth.PI * q);
			y = r.handR.y;
			r.ring = y < -6.5f;
			r.lump = !r.ring;
			r.ringTh = 2.6f;
			r.ringY = y - 0.4f;
			r.handL.set(-r.handR.x, r.handR.y, r.handR.z);
		} else if (p < pull1) {
			// pulled down over the head, the hands riding the rolled hem down either side
			float s = (p - pull0) / (pull1 - pull0);
			y = Mth.lerp(smooth(s / 0.3f), over.y, edge + 0.4f);
			float x = Mth.lerp(smooth(s / 0.3f), over.x, -5.1f);
			float z = Mth.lerp(smooth(s / 0.3f), over.z, -0.8f);
			r.handR.set(x, y, z);
			r.handL.set(-x, y, z);
			r.ring = r.c < 0.99f;
			r.ringY = Math.min(edge, y - 0.4f);
			r.ringTh = 0.6f + 2.0f * (1f - r.c);
		} else {
			// the tug at the neck: the right hand to the hem under the chin, a short pull down, and away
			float q = Mth.clamp((p - pull1) / (tugEnd - pull1), 0f, 1f);
			Vector3f from = new Vector3f(-5.1f, 0.4f, -0.8f);
			Vector3f chin = new Vector3f(-1.2f, 0.9f, -5.3f);
			r.handR.set(from).lerp(chin, smooth(q / 0.35f));
			r.handR.y += 1.5f * Mth.sin(Mth.PI * Mth.clamp((q - 0.4f) / 0.45f, 0f, 1f));
			r.handL.set(5.1f, 0.4f, -0.8f);
		}
		r.headPitch = 0.16f * Mth.sin(Mth.PI * Mth.clamp((p - 0.3f) / 0.55f, 0f, 1f));
	}

	private static float smooth(float f) {
		return SpiderMask.smooth(f);
	}

	// ------------------------------------------------------------------ the pose

	/** Pose {@code m} (from {@code HumanoidModelMixin}, after every other pose -- the mask plays over them). */
	public static void apply(Player player, HumanoidModel<?> m) {
		if (com.projecthero.mod.client.ironman.IronManFlightPose.firstPersonHand
				|| com.projecthero.mod.client.moonknight.MoonKnightPose.firstPersonHand) {
			return;
		}
		Rig r = rig(player, now(player));
		if (r == null) {
			return;
		}
		// hanging on a web line both hands are on it: the mask still peels, the arms stay where they are
		if (SpiderSwing.isSwinging(player) || SpiderHang.amount(player) > 0f) {
			return;
		}
		pose(m, r);
	}

	/** {@link #apply} for a given moment (also the harness's). */
	public static void pose(HumanoidModel<?> m, Rig r) {
		float w = Math.max(r.kR, r.kL);
		// look roughly ahead while the hands work at the head; chin dipped into the pull
		m.head.xRot = Mth.lerp(w, m.head.xRot, Mth.clamp(m.head.xRot, -0.45f, 0.45f) + r.headPitch);
		m.head.yRot = Mth.lerp(w, m.head.yRot, Mth.clamp(m.head.yRot, -0.7f, 0.7f));
		m.hat.copyFrom(m.head);
		Matrix3f head = new Matrix3f().rotationZYX(m.head.zRot, m.head.yRot, m.head.xRot);
		Vector3f neck = new Vector3f(m.head.x, m.head.y, m.head.z);
		reach(m.rightArm, JointedLimbs.RIGHT_ARM, -1, r.kR, r.handR, head, neck);
		reach(m.leftArm, JointedLimbs.LEFT_ARM, 1, r.kL, r.handL, head, neck);
	}

	private static void reach(ModelPart arm, int limb, int side, float k, Vector3f handInHead, Matrix3f head, Vector3f neck) {
		if (k <= 0.001f) {
			return;
		}
		Vector3f shoulder = new Vector3f(arm.x, arm.y, arm.z);
		// the arm box's centre line is 1 px outboard of its pivot (wide arms): aim the pivot line 1 px inboard
		Vector3f local = new Vector3f(handInHead).add(-side * 1f, 0f, 0f);
		Vector3f target = head.transform(local).add(neck);
		// elbows out to the side and a little down
		Vector3f pole = new Vector3f(side * 1f, 0.55f, 0.35f).normalize();
		SpiderPerch.Chain c = SpiderPerch.solve(shoulder, target, 4f, 6f, pole, null, true);
		SpiderPerch.place(arm, k, shoulder, c.upper());
		JointedLimbs.setJoint(limb, c.joint(k));
	}

	// ------------------------------------------------------------------ the mask

	/** The red suit's mask (base + outer layer) covering the top {@code coverage} of the head, or null for none. */
	public static ModelPart clippedHead(float coverage, boolean hat) {
		int idx = Mth.clamp(Math.round(coverage * 32f), 0, 32);
		if (idx == 0) {
			return null;
		}
		ModelPart[] cache = hat ? CLIP_HAT : CLIP_HEAD;
		if (cache[idx] == null) {
			MeshDefinition mesh = new MeshDefinition();
			// same boxes as the suit rig's head / hat (PlayerModel at +0.04), only shorter: a cube's side faces map the
			// TOP rows of the face texture, so a shorter box is exactly the upper part of the mask
			mesh.getRoot().addOrReplaceChild("p", CubeListBuilder.create().texOffs(hat ? 32 : 0, 0)
					.addBox(-4f, -8f, -4f, 8f, idx / 4f, 8f, new CubeDeformation(hat ? 0.54f : 0.04f)), PartPose.ZERO);
			cache[idx] = LayerDefinition.create(mesh, 64, 64).bakeRoot().getChild("p");
		}
		return cache[idx];
	}

	/**
	 * The rolled hem and the mask in hand (from {@code SpiderSuitLayer}, red suit and Black Suit alike), drawn on the
	 * parent model as {@link #apply} posed it.
	 */
	public static void renderExtras(PoseStack pose, MultiBufferSource buffers, int light, HumanoidModel<?> m, Player player) {
		if (player.isInvisible()) {
			return;
		}
		Rig r = rig(player, now(player));
		if (r == null || (!r.ring && !r.lump)) {
			return;
		}
		boolean symbiote = player.getItemBySlot(EquipmentSlot.HEAD).getItem() instanceof SymbioteArmorItem;
		int colour = symbiote ? SYMBIOTE_TINT : 0xFFFFFFFF;
		fu0 = symbiote ? BLACK_U0 : RED_U0;
		fv0 = symbiote ? BLACK_V0 : RED_V0;
		fu1 = symbiote ? BLACK_U1 : RED_U1;
		fv1 = symbiote ? BLACK_V1 : RED_V1;
		VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucent(SpiderSuitRender.TEXTURE));
		if (r.ring) {
			pose.pushPose();
			m.head.translateAndRotate(pose);
			pose.scale(1f / 16f, 1f / 16f, 1f / 16f);
			ring(pose.last(), vc, r.ringY, r.ringTh, colour, light);
			pose.popPose();
		}
		boolean armFree = !SpiderSwing.isSwinging(player) && SpiderHang.amount(player) <= 0f;
		if (r.lump && r.lumpScale > 0.02f && armFree) {
			pose.pushPose();
			m.rightArm.translateAndRotate(pose);
			pose.scale(1f / 16f, 1f / 16f, 1f / 16f);
			Quaternionf j = JointedLimbs.joint(JointedLimbs.RIGHT_ARM);
			if (j != null) {
				pose.translate(-1f, JointedLimbs.jointY(JointedLimbs.RIGHT_ARM), 0f);
				pose.mulPose(j);
				pose.translate(1f, -JointedLimbs.jointY(JointedLimbs.RIGHT_ARM), 0f);
			}
			pose.translate(-1f, 10.6f, -0.6f);
			pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(14f));
			pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(-18f));
			pose.scale(r.lumpScale, r.lumpScale, r.lumpScale);
			lump(pose.last(), vc, colour, light);
			pose.popPose();
		}
	}

	/** The rolled hem: a fabric band round the head (px, head frame) from {@code y - th/2} to {@code y + th/2}. */
	private static void ring(PoseStack.Pose p, VertexConsumer vc, float y, float th, int colour, int light) {
		float ho = 4.6f + th * 0.12f;
		float hi = 4.0f;
		float y0 = y - th * 0.5f;
		float y1 = y + th * 0.5f;
		int dark = scale(colour, 0.72f);
		// outer walls
		quad(p, vc, -ho, y0, -ho, ho, y0, -ho, ho, y1, -ho, -ho, y1, -ho, 0, 0, -1, colour, light, fu0, fv0, fu1, fv1);
		quad(p, vc, ho, y0, ho, -ho, y0, ho, -ho, y1, ho, ho, y1, ho, 0, 0, 1, colour, light, fu0, fv0, fu1, fv1);
		quad(p, vc, -ho, y0, ho, -ho, y0, -ho, -ho, y1, -ho, -ho, y1, ho, -1, 0, 0, colour, light, fu0, fv0, fu1, fv1);
		quad(p, vc, ho, y0, -ho, ho, y0, ho, ho, y1, ho, ho, y1, -ho, 1, 0, 0, colour, light, fu0, fv0, fu1, fv1);
		// inner walls (seen when it is held above the head)
		quad(p, vc, -hi, y0, -hi, hi, y0, -hi, hi, y1, -hi, -hi, y1, -hi, 0, 0, 1, dark, light, fu0, fv0, fu1, fv1);
		quad(p, vc, hi, y0, hi, -hi, y0, hi, -hi, y1, hi, hi, y1, hi, 0, 0, -1, dark, light, fu0, fv0, fu1, fv1);
		quad(p, vc, -hi, y0, hi, -hi, y0, -hi, -hi, y1, -hi, -hi, y1, hi, 1, 0, 0, dark, light, fu0, fv0, fu1, fv1);
		quad(p, vc, hi, y0, -hi, hi, y0, hi, hi, y1, hi, hi, y1, -hi, -1, 0, 0, dark, light, fu0, fv0, fu1, fv1);
		// the top and bottom of the roll (frames)
		for (int s = 0; s < 2; s++) {
			float yy = s == 0 ? y0 : y1;
			float ny = s == 0 ? -1f : 1f;
			int c = s == 0 ? colour : dark;
			quad(p, vc, -ho, yy, -ho, ho, yy, -ho, ho, yy, -hi, -ho, yy, -hi, 0, ny, 0, c, light, fu0, fv0, fu1, fv1);
			quad(p, vc, -ho, yy, hi, ho, yy, hi, ho, yy, ho, -ho, yy, ho, 0, ny, 0, c, light, fu0, fv0, fu1, fv1);
			quad(p, vc, -ho, yy, -hi, -hi, yy, -hi, -hi, yy, hi, -ho, yy, hi, 0, ny, 0, c, light, fu0, fv0, fu1, fv1);
			quad(p, vc, hi, yy, -hi, ho, yy, -hi, ho, yy, hi, hi, yy, hi, 0, ny, 0, c, light, fu0, fv0, fu1, fv1);
		}
	}

	/** The crumpled mask in the fist: a small wad, its face (with the eyes) turned out. */
	private static void lump(PoseStack.Pose p, VertexConsumer vc, int colour, int light) {
		float x = 2.6f;
		float y = 2.1f;
		float z = 1.9f;
		int dark = scale(colour, 0.8f);
		// the face of the mask, crumpled: the front of the head texture (8,8)-(16,16)
		quad(p, vc, x, -y, -z, -x, -y, -z, -x, y, -z, x, y, -z, 0, 0, -1, colour, light, 8f / 64f, 8f / 64f, 16f / 64f,
				16f / 64f);
		quad(p, vc, -x, -y, z, x, -y, z, x, y, z, -x, y, z, 0, 0, 1, dark, light, 24f / 64f, 8f / 64f, 32f / 64f, 16f / 64f);
		quad(p, vc, -x, -y, -z, -x, -y, z, -x, y, z, -x, y, -z, -1, 0, 0, dark, light, fu0, fv0, fu1, fv1);
		quad(p, vc, x, -y, z, x, -y, -z, x, y, -z, x, y, z, 1, 0, 0, dark, light, fu0, fv0, fu1, fv1);
		quad(p, vc, -x, -y, z, -x, -y, -z, x, -y, -z, x, -y, z, 0, -1, 0, colour, light, 8f / 64f, 0f, 16f / 64f, 8f / 64f);
		quad(p, vc, -x, y, -z, -x, y, z, x, y, z, x, y, -z, 0, 1, 0, dark, light, fu0, fv0, fu1, fv1);
	}

	private static int scale(int argb, float f) {
		int r = (int) (((argb >> 16) & 0xFF) * f);
		int g = (int) (((argb >> 8) & 0xFF) * f);
		int b = (int) ((argb & 0xFF) * f);
		return (argb & 0xFF000000) | (r << 16) | (g << 8) | b;
	}

	/** One textured quad (corners in order, UVs u0v0 at the first corner's top ... mapped to the face's corners). */
	private static void quad(PoseStack.Pose p, VertexConsumer vc, float x0, float y0, float z0, float x1, float y1, float z1,
			float x2, float y2, float z2, float x3, float y3, float z3, float nx, float ny, float nz, int colour, int light,
			float u0, float v0, float u1, float v1) {
		// corners 0,1 are the face's top edge (model y down: the smaller y), 2,3 the bottom edge, wound 0->1->2->3
		vc.addVertex(p, x0, y0, z0).setColor(colour).setUv(u0, v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, nx, ny, nz);
		vc.addVertex(p, x1, y1, z1).setColor(colour).setUv(u1, v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, nx, ny, nz);
		vc.addVertex(p, x2, y2, z2).setColor(colour).setUv(u1, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, nx, ny, nz);
		vc.addVertex(p, x3, y3, z3).setColor(colour).setUv(u0, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, nx, ny, nz);
	}

	// ------------------------------------------------------------------ first person

	/**
	 * First person: where the arm on {@code right}'s side is drawn -- {dx, dy, e} (screen offsets in the hand renderer's
	 * units, e = how far it is stood up toward the vertical) -- or null when that arm is not part of the animation now.
	 */
	public static float[] firstPersonArm(Player player, boolean right, float partial) {
		if (!SpiderMask.wearingHood(player)) {
			return null;
		}
		float p = SpiderMask.progress(player, player.level().getGameTime() + partial);
		if (p >= 1f) {
			return null;
		}
		return firstPersonArm(SpiderMask.isOpen(player), right, p);
	}

	public static float[] firstPersonArm(boolean open, boolean right, float p) {
		if (open) {
			if (!right) {
				return null;
			}
			final float peel0 = SpiderMask.OFF_PEEL_START;
			final float peel1 = SpiderMask.OFF_PEEL_END;
			if (p < peel0) {
				// up from below, to the chin (just under the bottom of the view)
				float q = smooth(p / peel0);
				return new float[] {-0.3f * q, Mth.lerp(q, -0.95f, -0.12f), 0.35f * q};
			}
			if (p < 0.72f) {
				// the peel: the hand sweeps up across the view, and out of the top
				float q = smooth((p - peel0) / (0.72f - peel0));
				return new float[] {-0.3f + 0.05f * q, Mth.lerp(q, -0.12f, 1.0f), 0.35f};
			}
			// back down past the right of the view, the mask in hand
			float q = smooth((p - 0.72f) / 0.22f);
			if (q >= 1f) {
				return null;
			}
			return new float[] {0.1f, Mth.lerp(q, 0.75f, -1.0f), 0.1f};
		}
		final float pull0 = SpiderMask.ON_PULL_START;
		final float pull1 = SpiderMask.ON_PULL_END;
		if (p < pull0) {
			// both hands up from below and out of the top, carrying the mask over the head
			float q = smooth(p / pull0);
			return new float[] {-0.15f * q, Mth.lerp(q, -0.95f, 0.95f), 0.3f};
		}
		if (p < pull1) {
			// back down either side of the view, pulling it on
			float q = smooth((p - pull0) / (pull1 - pull0));
			return new float[] {Mth.lerp(q, -0.15f, 0.1f), Mth.lerp(q, 0.95f, -0.12f), 0.3f};
		}
		float q = (p - pull1) / (1f - pull1);
		if (!right) {
			float d = smooth(q / 0.5f);
			return d >= 1f ? null : new float[] {0.1f, Mth.lerp(d, -0.12f, -1.0f), 0.3f};
		}
		// the tug at the neck: in toward the middle bottom, a short pull down, then away
		float in = smooth(q / 0.35f);
		float tug = 0.12f * Mth.sin(Mth.PI * Mth.clamp((q - 0.35f) / 0.35f, 0f, 1f));
		float out = smooth((q - 0.7f) / 0.3f);
		if (out >= 1f) {
			return null;
		}
		return new float[] {Mth.lerp(in, 0.1f, -0.3f), Mth.lerp(in, -0.12f, -0.3f) - tug - 0.8f * out, 0.3f};
	}

	/**
	 * First person: the fabric passing over the eyes -- a red band whose hem rises up the screen as the mask is peeled
	 * off, or comes down it as the mask is pulled on (after which he is looking out through the lenses again).
	 */
	public static void hud(GuiGraphics g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		Player player = mc.player;
		if (player == null || !mc.options.getCameraType().isFirstPerson() || mc.options.hideGui) {
			return;
		}
		if (!SpiderMask.wearingHood(player)) {
			return;
		}
		double t = player.level().getGameTime() + delta.getGameTimeDeltaPartialTick(false);
		float p = SpiderMask.progress(player, t);
		if (p >= 1f) {
			return;
		}
		float c = SpiderMask.coverage(SpiderMask.isOpen(player), p);
		// the hem is level with the eyes about half way; the band is off-screen at either end
		float f = Mth.clamp((c - 0.08f) / 0.84f, 0f, 1f);
		if (f <= 0f || f >= 1f) {
			return;
		}
		int w = g.guiWidth();
		int h = g.guiHeight();
		boolean symbiote = player.getItemBySlot(EquipmentSlot.HEAD).getItem() instanceof SymbioteArmorItem;
		int hemCol = symbiote ? 0xF0101012 : 0xF07A0B26;
		int topCol = symbiote ? 0x00101012 : 0x007A0B26;
		int hem = Math.round(f * h * 1.45f);
		int top = hem - Math.round(h * 0.45f);
		g.fillGradient(0, top, w, hem, topCol, hemCol);
		// the rolled hem itself
		g.fill(0, hem - Math.max(2, h / 60), w, hem, symbiote ? 0xFF050506 : 0xFF5C0719);
	}
}
