package com.projecthero.mod.client.ironman;

import java.util.HashMap;
import java.util.Map;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.ironman.item.IronManItems;
import com.projecthero.mod.ironman.suit.PortableForm;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * v0.16.7 (user) Era II stage 2: the Mk IX heat case prop ({@code PortableForms.MK9_HEAT_CASE}), drawn off the deploy
 * clock (a fold plays the same frames backwards):
 * <ol>
 *   <li>0 .. dropFrom: carried in the right hand -- the ordinary held item, drawn by this layer (the real one is hidden);</li>
 *   <li>dropFrom .. dropTo: set down -- the case leaves the hand (its live position, captured every frame from the body
 *       pass, so there is no jump) and is lowered in front of you, tipping onto its back with the vents up;</li>
 *   <li>dropTo .. openTo: both leaves swing open sideways over their outer top edges, all the way over to rest as braced
 *       legs, while the interior glows hotter and hotter, heat shimmer and embers rising out of it;</li>
 *   <li>the charcoal plates fly up out of the glow to wherever the suit is building (boots first);</li>
 *   <li>closeFrom ..: the empty case swings shut, the glow dies and it folds away into nothing.</li>
 * </ol>
 * The case is drawn in its own pixel frame: "lying" (X across, Y up, Z depth, origin at the bottom centre), the same
 * cubes as the item model {@code models/item/mark_9_heat_case.json} (which is offset by {@link #JSON_OFFSET}).
 */
final class Mk9HeatCaseProp implements IronManPortableClient.PortableProp {
	static final ResourceLocation TEXTURE = ProjectHeroMod.id("textures/item/mark_9_heat_case.png");
	/** The case's world scale on the floor (the held item model's display scale too). */
	static final float SCALE = 0.9f;
	/** Where the case comes to rest: its centre this far in front of the feet (blocks). */
	static final float FWD = 0.95f;
	/** Ticks the shut empty case takes to fold away to nothing. */
	static final int VANISH_TICKS = 6;
	/** How far each leaf swings open (degrees) -- over the top and down to rest as a leg. */
	static final float LEAF_OPEN = 246f;
	/** The item model's cubes are the prop's, moved by this many pixels. */
	static final Vector3f JSON_OFFSET = new Vector3f(8f, 5.25f, 8f);

	// ---- the case, in lying pixels (origin bottom centre)
	static final float HALF_W = 5.5f, BASE_H = 3.5f, LEAF_H = 2f, HALF_D = 5f;

	// ---- texture cells (4x4 texels on a 16x16 sheet)
	private static final int[] CHARCOAL = { 0, 0 }, CHARCOAL_DARK = { 4, 0 }, BRONZE = { 8, 0 }, HANDLE = { 12, 0 },
			VENT = { 0, 4 }, CORE = { 4, 4 }, EMBER = { 12, 4 }, CHARCOAL_LIGHT = { 0, 8 }, INTERIOR = { 8, 8 },
			WHITE_HOT = { 12, 8 };

	private record Capture(Matrix4f matrix, long frame) {
	}

	/** The live held-case matrix per player (body pass), valid for the render frame it was taken in. */
	private static final Map<Integer, Capture> HAND = new HashMap<>();
	private static long renderFrame;
	private static boolean hooked;
	private static ItemStack stack;

	Mk9HeatCaseProp() {
		if (!hooked) {
			hooked = true;
			WorldRenderEvents.START.register(context -> {
				renderFrame++;
				if (HAND.size() > 64) {
					HAND.clear();
				}
			});
		}
	}

	private static ItemStack stack() {
		if (stack == null) {
			stack = new ItemStack(IronManItems.MARK_9_HEAT_CASE);
		}
		return stack;
	}

	// ================================================================ the body pass: in the hand

	@Override
	public void renderBody(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player,
			PlayerModel<AbstractClientPlayer> model, PortableForm form, float frame) {
		pose.pushPose();
		model.translateToHand(HumanoidArm.RIGHT, pose);
		// vanilla ItemInHandLayer's hand frame
		pose.mulPose(Axis.XP.rotationDegrees(-90.0F));
		pose.mulPose(Axis.YP.rotationDegrees(180.0F));
		pose.translate(-1.0F / 16.0F, 0.125F, -0.625F);
		// remember where the held case is (the item's third-person transform + its -0.5 centring), so the world pass can
		// take it out of the hand from exactly there
		pose.pushPose();
		var baked = Minecraft.getInstance().getItemRenderer().getModel(stack(), player.level(), player, player.getId());
		baked.getTransforms().getTransform(ItemDisplayContext.THIRD_PERSON_RIGHT_HAND).apply(false, pose);
		pose.translate(-0.5F, -0.5F, -0.5F);
		HAND.put(player.getId(), new Capture(new Matrix4f(pose.last().pose()), renderFrame));
		pose.popPose();
		if (frame <= form.beats().dropFrom()) {
			Minecraft.getInstance().getEntityRenderDispatcher().getItemInHandRenderer().renderItem(player, stack(),
					ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, false, pose, buffers, light);
		}
		pose.popPose();
	}

	// ================================================================ the world pass: on the floor

	@Override
	public void renderWorld(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player,
			PortableForm form, float frame, IronManPortableClient.Anchor anchor) {
		PortableForm.Beats b = form.beats();
		if (frame <= b.dropFrom()) {
			return;
		}
		float vanish = smooth((frame - (b.total() - VANISH_TICKS)) / VANISH_TICKS);
		if (vanish >= 0.999f) {
			return;
		}
		// the anchor frame in the entity renderers' space (camera-relative, no camera rotation) -- the world-render pose
		// stack may carry the camera rotation, the body pass's does not, so rebuild it rather than read it
		net.minecraft.world.phys.Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
		Matrix4f here = new Matrix4f().translate((float) (anchor.pos().x - cam.x), (float) (anchor.pos().y - cam.y),
				(float) (anchor.pos().z - cam.z)).rotateY((float) Math.toRadians(-anchor.yaw()));
		floorLift = floorLift(player, anchor);
		Matrix4f caseM = caseMatrix(player, b, frame, here, vanish);
		float leaf = leafAngle(b, frame);
		float glow = Mth.clamp(leaf / 70f, 0f, 1f) * (1f - vanish);
		// the vents warm from the held item's flat orange to a full glow as the case is set down
		float vents = smooth((frame - b.dropFrom()) / (float) (b.dropTo() - b.dropFrom()));

		pose.pushPose();
		pose.mulPose(caseM);
		VertexConsumer solid = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
		drawCase(pose, solid, light, leaf, glow, vents);
		pose.popPose();

		if (glow > 0.01f) {
			renderHeat(pose, buffers, frame, glow, b);
		}
		renderPlates(pose, buffers, light, form, frame);
	}

	/** The case's matrix in the anchor frame: from the hand (live) to the floor in front, then shrinking away. */
	private static Matrix4f caseMatrix(AbstractClientPlayer player, PortableForm.Beats b, float frame, Matrix4f here,
			float vanish) {
		float floorScale = SCALE / 16f;
		Capture c = HAND.get(player.getId());
		Matrix4f hand;
		if (c != null && c.frame() == renderFrame) {
			hand = new Matrix4f(here).invert().mul(c.matrix()).scale(1f / 16f).translate(JSON_OFFSET);
		} else {
			// first person (no body pass): beside the right hand, standing on its end, vents out
			hand = new Matrix4f().translate(-0.32f, 0.42f, 0.06f).rotateZ((float) Math.toRadians(90)).scale(floorScale);
		}
		float t = Mth.clamp((frame - b.dropFrom()) / (float) (b.dropTo() - b.dropFrom()), 0f, 1f);
		// set down: eases out of the hand, decelerates onto the floor; tips onto its back on the way
		float move = smooth(t);
		float tip = smooth(Mth.clamp(t * 1.15f, 0f, 1f));
		Vector3f tH = hand.getTranslation(new Vector3f());
		float sH = hand.getScale(new Vector3f()).x;
		// the rotation part, columns normalised by hand (JOML's getNormalizedRotation trusts cached matrix properties)
		org.joml.Matrix3f r3 = hand.get3x3(new org.joml.Matrix3f());
		Vector3f cx = r3.getColumn(0, new Vector3f()).normalize(), cy = r3.getColumn(1, new Vector3f()).normalize();
		Vector3f cz = new Vector3f(cx).cross(cy).normalize();
		cy = new Vector3f(cz).cross(cx).normalize();
		Quaternionf qH = new Quaternionf().setFromNormalized(new org.joml.Matrix3f(cx, cy, cz)).normalize();
		float y = 0f;
		float land = frame - b.dropTo();
		if (land > 0f && land < 5f) {
			y += 0.018f * (float) Math.sin(Math.PI * land / 5f) * (1f - land / 5f); // a little settle as it lands
		}
		Vector3f tF = new Vector3f(0f, floorLift + y, FWD);
		Vector3f pos = new Vector3f(tH).lerp(tF, move);
		// a slight arc: it does not slide through the air in a straight line
		pos.y += 0.06f * (float) Math.sin(Math.PI * t) * (1f - t);
		Quaternionf rot = new Quaternionf(qH).slerp(new Quaternionf(), tip);
		float s = Mth.lerp(move, sH, floorScale) * (1f - vanish);
		pos.y *= 1f - 0.4f * vanish;
		return new Matrix4f().translationRotateScale(pos.x, pos.y, pos.z, rot.x, rot.y, rot.z, rot.w, s, s, s);
	}

	/** Where the case rests above the anchor (blocks): 0 on the floor, the surface when it is set down into lava. */
	private static float floorLift;

	/** The heat-proof case floats on lava (or any fluid) rather than sinking out of sight. */
	private static float floorLift(AbstractClientPlayer player, IronManPortableClient.Anchor anchor) {
		net.minecraft.world.phys.Vec3 at = anchor.at(0, 0.05, FWD);
		net.minecraft.core.BlockPos pos = net.minecraft.core.BlockPos.containing(at);
		float lift = 0f;
		for (int i = 0; i < 2; i++) {
			var fluid = player.level().getFluidState(pos);
			if (fluid.isEmpty()) {
				break;
			}
			lift = (float) (pos.getY() + fluid.getHeight(player.level(), pos) - anchor.pos().y);
			pos = pos.above();
		}
		return Mth.clamp(lift, 0f, 1.2f);
	}

	/** Degrees each leaf is open at {@code frame}: swings over between dropTo and openTo, shut again from closeFrom. */
	static float leafAngle(PortableForm.Beats b, float frame) {
		float open = (frame - b.dropTo() - 1f) / (float) (b.openTo() - b.dropTo() - 1);
		float shut = (frame - b.closeFrom()) / (float) (b.total() - VANISH_TICKS - b.closeFrom());
		float a = LEAF_OPEN * Math.min(swing(open), 1f - swing(shut));
		// the leaves bump down onto their feet
		float bump = frame - b.openTo();
		if (bump > 0f && bump < 5f && frame < b.closeFrom()) {
			a -= 7f * (float) Math.sin(Math.PI * bump / 5f) * (1f - bump / 5f);
		}
		return Math.max(0f, a);
	}

	/** A heavy swing: slow off the stop, fast over the top, slowing onto its feet. */
	private static float swing(float x) {
		x = Mth.clamp(x, 0f, 1f);
		return x * x * x * (x * (x * 6f - 15f) + 10f);
	}

	// ---------------- the case's cubes

	private static void drawCase(PoseStack pose, VertexConsumer vc, int light, float leafDeg, float glow, float vents) {
		int warm = brighten(light, glow * 0.8f);
		PoseStack.Pose p = pose.last();
		// base
		box(p, vc, -HALF_W, 0f, -HALF_D, HALF_W, BASE_H, HALF_D, CHARCOAL, 255, 255, 255, light);
		for (float bx : new float[] { -4.6f, 3.8f }) {
			box(p, vc, bx, -0.12f, -HALF_D - 0.15f, bx + 0.8f, BASE_H - 0.02f, HALF_D + 0.15f, BRONZE, 255, 255, 255, light);
		}
		// a bronze lip round the opening, catching the glow
		box(p, vc, -HALF_W - 0.1f, BASE_H - 0.5f, -HALF_D - 0.1f, HALF_W + 0.1f, BASE_H - 0.1f, HALF_D + 0.1f, BRONZE, 220,
				220, 220, warm);
		// handle on the +X end (the top when it is carried)
		box(p, vc, HALF_W, 0.8f, -1.7f, HALF_W + 0.9f, 2.0f, -1.1f, HANDLE, 255, 255, 255, light);
		box(p, vc, HALF_W, 0.8f, 1.1f, HALF_W + 0.9f, 2.0f, 1.7f, HANDLE, 255, 255, 255, light);
		box(p, vc, HALF_W + 0.5f, 0.8f, -1.7f, HALF_W + 1.0f, 2.0f, 1.7f, HANDLE, 255, 255, 255, light);
		// the hot interior (only seen once the leaves part)
		if (glow > 0.001f) {
			int r = (int) Mth.lerp(glow, 70, 255), g = (int) Mth.lerp(glow, 30, 200), bl = (int) Mth.lerp(glow, 12, 110);
			box(p, vc, -HALF_W + 0.8f, BASE_H - 0.3f, -HALF_D + 0.8f, HALF_W - 0.8f, BASE_H + 0.02f, HALF_D - 0.8f, CORE, r, g,
					bl, LightTexture.FULL_BRIGHT);
			// glowing honeycomb slots in the tray: the packed plates, red hot
			for (int i = 0; i < 3; i++) {
				for (int j = 0; j < 2; j++) {
					float x0 = -3.8f + i * 2.7f, z0 = -3.2f + j * 3.4f;
					box(p, vc, x0, BASE_H, z0, x0 + 2.1f, BASE_H + 0.35f, z0 + 2.8f, INTERIOR, (int) (255 * glow),
							(int) (150 * glow), (int) (90 * glow), LightTexture.FULL_BRIGHT);
				}
			}
		}
		// the leaves: hinged on their OUTER TOP edges, swinging up, over and down outwards
		for (int side = -1; side <= 1; side += 2) {
			pose.pushPose();
			pose.translate(side * HALF_W, BASE_H + LEAF_H, 0f);
			pose.mulPose(Axis.ZP.rotationDegrees(-side * leafDeg));
			PoseStack.Pose lp = pose.last();
			// leaf-local: x from the hinge inwards (-side), y 0 at the hinge (top) down to -LEAF_H
			float xa = -side * HALF_W, xb = 0f;
			float lx0 = Math.min(xa, xb), lx1 = Math.max(xa, xb);
			box(lp, vc, lx0, -LEAF_H, -HALF_D, lx1, 0f, HALF_D, CHARCOAL_LIGHT, 255, 255, 255, light);
			// the inner face, lit orange by the glow once open
			box(lp, vc, lx0 + 0.3f, -LEAF_H - 0.02f, -HALF_D + 0.3f, lx1 - 0.3f, -LEAF_H + 0.2f, HALF_D - 0.3f, CHARCOAL_DARK,
					(int) Mth.lerp(glow, 255, 255), (int) Mth.lerp(glow, 255, 170), (int) Mth.lerp(glow, 255, 120), warm);
			// bronze band segment (continues the base's bands)
			float band = side > 0 ? 3.8f - HALF_W : -4.6f + HALF_W; // hinge-relative x of the band's near edge
			box(lp, vc, band, -LEAF_H - 0.02f, -HALF_D - 0.15f, band + 0.8f, 0.12f, HALF_D + 0.15f, BRONZE,
					255, 255, 255, light);
			// the three vent slots (the middle one is split by the seam between the leaves)
			int ventLight = brighten(light, vents);
			for (float vx : new float[] { -2.75f, 0f, 2.75f }) {
				float v0 = vx - 0.25f, v1 = vx + 0.25f;
				// only the part of the slot on this leaf; to hinge-relative x
				float a0 = Math.max(v0, side > 0 ? 0f : -HALF_W), a1 = Math.min(v1, side > 0 ? HALF_W : 0f);
				if (a1 <= a0) {
					continue;
				}
				box(lp, vc, a0 - side * HALF_W, 0f, -3.2f, a1 - side * HALF_W, 0.1f, 3.2f, VENT, 255, 255, 255, ventLight);
			}
			pose.popPose();
		}
	}

	/** {@code light} with its block light raised toward full by {@code k} (0..1). */
	private static int brighten(int light, float k) {
		int block = LightTexture.block(light), sky = LightTexture.sky(light);
		int nb = Math.max(block, Math.round(15f * Mth.clamp(k, 0f, 1f)));
		int ns = Math.max(sky, Math.round(15f * Mth.clamp(k, 0f, 1f)));
		return LightTexture.pack(nb, ns);
	}

	// ---------------- heat: shimmer and embers

	/** Rises out of the open case: a wavering heat column and embers, a pure function of the frame. */
	private void renderHeat(PoseStack pose, MultiBufferSource buffers, float frame, float glow, PortableForm.Beats b) {
		VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucentEmissive(TEXTURE));
		float top = floorLift + BASE_H * SCALE / 16f;
		PoseStack.Pose p = pose.last();
		// heat shimmer: three crossed sheets of faint orange, wavering and fading upwards
		for (int k = 0; k < 3; k++) {
			float ph = frame * 0.55f + k * 2.1f;
			float w = 0.22f + 0.05f * k;
			float h = 0.55f + 0.18f * (float) Math.sin(ph * 0.7f);
			float sway = 0.05f * (float) Math.sin(ph);
			float a = glow * (0.16f - 0.035f * k);
			float rot = k * 60f + 10f * (float) Math.sin(ph * 0.4f);
			pose.pushPose();
			pose.translate(0f, top, FWD);
			pose.mulPose(Axis.YP.rotationDegrees(rot));
			sheet(pose.last(), vc, w, h, sway, a);
			pose.popPose();
		}
		// embers: launched one every half tick while the case is open, each rising and fading over 16 ticks
		float from = b.dropTo() + 3f, to = b.closeFrom() + 4f, life = 16f;
		int first = Math.max(0, (int) ((frame - life - from) / 0.5f));
		int last = (int) ((Math.min(frame, to) - from) / 0.5f);
		for (int i = first; i <= last; i++) {
			float launch = from + i * 0.5f;
			float t = (frame - launch) / life;
			if (t <= 0f || t >= 1f) {
				continue;
			}
			float r1 = hash(i, 11), r2 = hash(i, 12), r3 = hash(i, 13);
			float x = (r1 - 0.5f) * 0.55f + 0.08f * (float) Math.sin(t * 9f + r2 * 6f);
			float z = FWD + (r2 - 0.5f) * 0.45f + 0.06f * (float) Math.cos(t * 7f + r3 * 6f);
			float y = top + 0.02f + t * (0.7f + 0.6f * r3);
			float s = (0.018f + 0.014f * r1) * (1f - t * 0.7f);
			float fade = glow * (t < 0.15f ? t / 0.15f : 1f - (t - 0.15f) / 0.85f);
			int[] cell = t < 0.3f ? WHITE_HOT : t < 0.6f ? VENT : EMBER;
			pose.pushPose();
			pose.translate(x, y, z);
			pose.mulPose(Axis.YP.rotationDegrees(360f * r3 + 200f * t));
			pose.mulPose(Axis.XP.rotationDegrees(180f * r2 + 260f * t));
			boxW(pose.last(), vc, s, s, s, cell, 255, 255, 255, (int) (255 * Mth.clamp(fade, 0f, 1f)), LightTexture.FULL_BRIGHT);
			pose.popPose();
		}
	}

	/** One shimmer sheet: a quad from the floor of the case up {@code h}, bright at the bottom and gone at the top. */
	private static void sheet(PoseStack.Pose p, VertexConsumer vc, float w, float h, float sway, float alpha) {
		float u0 = INTERIOR[0] / 16f, u1 = (INTERIOR[0] + 4) / 16f, v0 = INTERIOR[1] / 16f, v1 = (INTERIOR[1] + 4) / 16f;
		int a0 = (int) (255 * Mth.clamp(alpha, 0f, 1f));
		float[][] q = { { -w, 0f, a0, u0, v1 }, { w, 0f, a0, u1, v1 }, { w + sway, h, 0, u1, v0 }, { -w + sway, h, 0, u0, v0 } };
		for (int side = 0; side < 2; side++) {
			for (int k = 0; k < 4; k++) {
				float[] v = q[side == 0 ? k : 3 - k];
				vc.addVertex(p, v[0], v[1], 0f).setColor(255, 200, 140, (int) v[2]).setUv(v[3], v[4])
						.setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(p, 0f, 0f, side == 0 ? 1f : -1f);
			}
		}
	}

	// ---------------- the plates in flight

	/** Ticks one plate takes from the case to the body. */
	static final float CHIP_FLIGHT = 7f;
	/** One plate leaves the case every this many ticks while the suit climbs on. */
	static final float CHIP_EVERY = 0.7f;

	/**
	 * Charcoal plates (now and then a bronze one) fly up out of the glowing case to wherever the suit is building (boots
	 * first, up to the helmet) -- a pure function of the frame, so the fold has them dive back into the case.
	 */
	private static void renderPlates(PoseStack pose, MultiBufferSource buffers, int light, PortableForm form, float frame) {
		int first = form.pieceOnAt(3);
		int last = form.beats().sealAt() - 6;
		if (frame < first || frame > last + CHIP_FLIGHT) {
			return;
		}
		VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
		float top = floorLift + BASE_H * SCALE / 16f;
		int n = (int) ((last - first) / CHIP_EVERY);
		for (int i = 0; i < n; i++) {
			float launch = first + i * CHIP_EVERY;
			float t = (frame - launch) / CHIP_FLIGHT;
			if (t <= 0f || t >= 1f) {
				continue;
			}
			float r1 = hash(i, 1), r2 = hash(i, 2), r3 = hash(i, 3);
			float sx = (r1 - 0.5f) * 0.45f, sz = FWD + (r2 - 0.5f) * 0.35f, sy = top + 0.02f;
			float h = Mk6BriefcaseProp.buildHeight(form, launch + CHIP_FLIGHT);
			float side = (r3 - 0.5f) * (h > 0.75f && h < 1.45f ? 0.95f : 0.45f);
			float ex = side, ez = 0.05f + (r2 - 0.5f) * 0.18f, ey = h;
			float e = t * t * (3f - 2f * t);
			float x = Mth.lerp(e, sx, ex), z = Mth.lerp(e, sz, ez);
			float y = Mth.lerp(e, sy, ey) + 0.3f * (float) Math.sin(Math.PI * t);
			float s = 0.06f * (1f - 0.55f * t) + 0.02f * r1;
			boolean bronze = (i % 4) == 2;
			// fresh out of the heat: glowing at first, cooling to charcoal on the way
			float hot = Mth.clamp(1f - t * 2.2f, 0f, 1f);
			int col = (int) Mth.lerp(hot, 255, 255);
			pose.pushPose();
			pose.translate(x, y, z);
			pose.mulPose(Axis.YP.rotationDegrees(360f * r3 + 400f * t));
			pose.mulPose(Axis.XP.rotationDegrees(180f * r1 + 300f * t));
			boxW(pose.last(), vc, s * 1.4f, s, s * 0.45f, bronze ? BRONZE : CHARCOAL_LIGHT, col,
					(int) Mth.lerp(hot, 255, 180), (int) Mth.lerp(hot, 255, 120), 255, brighten(light, hot));
			pose.popPose();
		}
	}

	// ---------------- geometry helpers

	/** A box in case pixels (the pose is already scaled to pixels). */
	private static void box(PoseStack.Pose p, VertexConsumer vc, float x0, float y0, float z0, float x1, float y1, float z1,
			int[] cell, int r, int g, int b, int light) {
		emit(p, vc, x0, y0, z0, x1, y1, z1, cell, r, g, b, 255, light);
	}

	/** A box of half-extents (hx, hy, hz) round the origin, in world units. */
	private static void boxW(PoseStack.Pose p, VertexConsumer vc, float hx, float hy, float hz, int[] cell, int r, int g,
			int b, int a, int light) {
		emit(p, vc, -hx, -hy, -hz, hx, hy, hz, cell, r, g, b, a, light);
	}

	private static void emit(PoseStack.Pose p, VertexConsumer vc, float x0, float y0, float z0, float x1, float y1, float z1,
			int[] cell, int r, int g, int b, int a, int light) {
		float u0 = (cell[0] + 0.25f) / 16f, u1 = (cell[0] + 3.75f) / 16f, v0 = (cell[1] + 0.25f) / 16f,
				v1 = (cell[1] + 3.75f) / 16f;
		float[][] faces = {
				{ 0, 0, 1, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1 },
				{ 0, 0, -1, x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0 },
				{ 1, 0, 0, x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1 },
				{ -1, 0, 0, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0 },
				{ 0, 1, 0, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0 },
				{ 0, -1, 0, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1 } };
		float[][] uv = { { u0, v1 }, { u1, v1 }, { u1, v0 }, { u0, v0 } };
		for (float[] f : faces) {
			for (int k = 0; k < 4; k++) {
				vc.addVertex(p, f[3 + k * 3], f[4 + k * 3], f[5 + k * 3]).setColor(r, g, b, a).setUv(uv[k][0], uv[k][1])
						.setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, f[0], f[1], f[2]);
			}
		}
	}

	private static float hash(int i, int salt) {
		int h = i * 374761393 + salt * 668265263;
		h = (h ^ (h >>> 13)) * 1274126177;
		return ((h ^ (h >>> 16)) & 0xFFFFFF) / (float) 0x1000000;
	}

	static float smooth(float x) {
		x = Mth.clamp(x, 0f, 1f);
		return x * x * (3f - 2f * x);
	}
}
