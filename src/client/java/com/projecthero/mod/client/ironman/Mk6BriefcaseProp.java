package com.projecthero.mod.client.ironman;

import org.joml.Quaternionf;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.projecthero.mod.ironman.item.IronManItems;
import com.projecthero.mod.ironman.suit.PortableForm;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * v0.16.7 (user) Era II stage 2: the Mk VI briefcase prop ({@code PortableForms.MK6_BRIEFCASE}), drawn off the deploy
 * clock (a fold plays the same frames backwards):
 * <ol>
 *   <li>0 .. dropFrom: carried in the right hand (the ordinary held item is hidden meanwhile);</li>
 *   <li>dropFrom .. dropTo: let go -- it tips over and drops onto the floor in front, lying on its back;</li>
 *   <li>dropTo .. openTo: the lid flips open forward over its far hinge, the packed plates rise inside;</li>
 *   <li>closeFrom ..: once the suit is on, the empty case snaps shut and folds away into nothing.</li>
 * </ol>
 * The model is the item's own GeckoLib model ({@link MarkVSuitcaseRenderer}), posed through
 * {@link MarkVSuitcaseRenderer#openness}.
 */
final class Mk6BriefcaseProp implements IronManPortableClient.PortableProp {
	/** The case's draw scale (the held item's scale). */
	static final float SCALE = 0.8f;
	/** Where the case comes to rest: its hinge edge this far in front of the feet (blocks). */
	static final float HINGE_FWD = 1.15f;
	/** Ticks the shut empty case takes to fold away to nothing. */
	static final int VANISH_TICKS = 6;
	/** Where the dropped case starts (anchor frame): beside the right hand, low. */
	static final float START_RIGHT = 0.42f, START_UP = 0.18f, START_FWD = 0.05f;

	private static ItemStack stack;

	private static ItemStack stack() {
		if (stack == null) {
			stack = new ItemStack(IronManItems.MARK_V_SUITCASE);
		}
		return stack;
	}

	@Override
	public void renderBody(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player,
			PlayerModel<AbstractClientPlayer> model, PortableForm form, float frame) {
		if (frame > form.beats().dropFrom()) {
			return;
		}
		pose.pushPose();
		model.translateToHand(HumanoidArm.RIGHT, pose);
		// vanilla ItemInHandLayer's hand frame, then the item's own third-person transform
		pose.mulPose(Axis.XP.rotationDegrees(-90.0F));
		pose.mulPose(Axis.YP.rotationDegrees(180.0F));
		pose.translate(-1.0F / 16.0F, 0.125F, -0.625F);
		Minecraft.getInstance().getEntityRenderDispatcher().getItemInHandRenderer().renderItem(player, stack(),
				ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, false, pose, buffers, light);
		pose.popPose();
	}

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
		// the drop: linear in x / z, gravity (ease-in) in y with a little toss up, tipping onto its back as it falls
		float t = Mth.clamp((frame - b.dropFrom()) / (float) (b.dropTo() - b.dropFrom()), 0f, 1f);
		float tip = smooth(t);
		float restUp = 2f / 16f * SCALE; // the base half's thickness, so it lies on the floor
		float x = Mth.lerp(t, -START_RIGHT, 0f); // local -x is the wearer's right
		float z = Mth.lerp(t, START_FWD, HINGE_FWD);
		float y = Mth.lerp(t * t, START_UP, restUp) + 0.22f * (float) Math.sin(Math.PI * t) * (1f - t);
		// a small bounce when it lands
		float land = frame - b.dropTo();
		if (land > 0f && land < 4f) {
			y += 0.025f * (float) Math.sin(Math.PI * land / 4f);
		}
		y *= 1f - vanish;
		Quaternionf carried = new Quaternionf().rotateY((float) Math.toRadians(90));
		Quaternionf flat = new Quaternionf().rotateY((float) Math.PI).rotateX((float) Math.toRadians(90));
		Quaternionf rot = new Quaternionf(carried).slerp(flat, tip);
		// lid: opens over dropTo .. openTo, shuts again from closeFrom
		float open = smooth((frame - b.dropTo()) / (float) (b.openTo() - b.dropTo()));
		open = Math.min(open, 1f - smooth((frame - b.closeFrom()) / (float) (b.total() - VANISH_TICKS - b.closeFrom())));
		float s = SCALE * (1f - vanish);
		pose.pushPose();
		pose.translate(x, y, z);
		pose.mulPose(rot);
		pose.scale(s, s, s);
		MarkVSuitcaseRenderer.openness = open;
		try {
			Minecraft.getInstance().getItemRenderer().renderStatic(player, stack(), ItemDisplayContext.NONE, false, pose,
					buffers, player.level(), light, OverlayTexture.NO_OVERLAY, player.getId());
		} finally {
			MarkVSuitcaseRenderer.openness = 0f;
		}
		pose.popPose();
		renderChips(pose, buffers, light, form, frame);
	}

	// ---------------- the plates in flight ----------------

	private static final net.minecraft.resources.ResourceLocation TEXTURE =
			com.projecthero.mod.ProjectHeroMod.id("textures/entity/mark_v_suitcase.png");
	/** Ticks one plate takes from the tray to the body. */
	static final float CHIP_FLIGHT = 7f;
	/** One plate leaves the tray every this many ticks while the suit climbs on. */
	static final float CHIP_EVERY = 0.75f;

	/**
	 * Small red / gold plates fly up out of the open tray to wherever the suit is building right now (boots first, up to
	 * the helmet) -- a pure function of the frame, so the fold (frames backwards) has them fly back down into the case.
	 */
	private static void renderChips(PoseStack pose, MultiBufferSource buffers, int light, PortableForm form, float frame) {
		int first = form.pieceOnAt(3);
		int last = form.beats().sealAt() - 4;
		if (frame < first || frame > last + CHIP_FLIGHT) {
			return;
		}
		var vc = buffers.getBuffer(net.minecraft.client.renderer.RenderType.entityCutoutNoCull(TEXTURE));
		int n = (int) ((last - first) / CHIP_EVERY);
		for (int i = 0; i < n; i++) {
			float launch = first + i * CHIP_EVERY;
			float t = (frame - launch) / CHIP_FLIGHT;
			if (t <= 0f || t >= 1f) {
				continue;
			}
			float r1 = hash(i, 1), r2 = hash(i, 2), r3 = hash(i, 3);
			// from a spot in the tray ...
			float sx = (r1 - 0.5f) * 0.5f, sz = HINGE_FWD - 0.12f - r2 * 0.3f, sy = 0.16f;
			// ... to the body, at the height the build has climbed to when it lands
			float h = buildHeight(form, launch + CHIP_FLIGHT);
			float side = (r3 - 0.5f) * (h > 0.75f && h < 1.45f ? 0.95f : 0.45f);
			float ex = side, ez = 0.05f + (r2 - 0.5f) * 0.18f, ey = h;
			float e = t * t * (3f - 2f * t);
			float x = Mth.lerp(e, sx, ex), z = Mth.lerp(e, sz, ez);
			float y = Mth.lerp(e, sy, ey) + 0.35f * (float) Math.sin(Math.PI * t);
			float s = 0.06f * (1f - 0.55f * t) + 0.02f * r1;
			boolean gold = (i % 3) == 1;
			pose.pushPose();
			pose.translate(x, y, z);
			pose.mulPose(Axis.YP.rotationDegrees(360f * r3 + 400f * t));
			pose.mulPose(Axis.XP.rotationDegrees(180f * r1 + 300f * t));
			cube(pose, vc, s * 1.4f, s, s * 0.45f, gold ? 41.5f : 33.5f, light);
			pose.popPose();
		}
	}

	/** Height (blocks) the suit has climbed to at {@code frame}: the stage that is building then. */
	static float buildHeight(PortableForm form, float frame) {
		float[][] at = { { form.pieceOnAt(3), 0.12f }, { form.pieceOnAt(2), 0.45f }, { form.pieceOnAt(1), 0.95f },
				{ form.pieceOnAt(0), 1.45f }, { form.beats().sealAt(), 1.75f } };
		if (frame <= at[0][0]) {
			return at[0][1];
		}
		for (int i = 0; i < at.length - 1; i++) {
			if (frame <= at[i + 1][0]) {
				return Mth.lerp((frame - at[i][0]) / Math.max(1f, at[i + 1][0] - at[i][0]), at[i][1], at[i + 1][1]);
			}
		}
		return at[at.length - 1][1];
	}

	private static float hash(int i, int salt) {
		int h = i * 374761393 + salt * 668265263;
		h = (h ^ (h >>> 13)) * 1274126177;
		return ((h ^ (h >>> 16)) & 0xFFFFFF) / (float) 0x1000000;
	}

	/** A box of half-extents (hx, hy, hz), every face one flat texel (texel u, row 9.5) of the case texture. */
	private static void cube(PoseStack pose, com.mojang.blaze3d.vertex.VertexConsumer vc, float hx, float hy, float hz,
			float texU, int light) {
		float u = texU / 64f, v = 9.5f / 64f;
		PoseStack.Pose p = pose.last();
		float[][] faces = {
				{ 0, 0, 1, -hx, -hy, hz, hx, -hy, hz, hx, hy, hz, -hx, hy, hz },
				{ 0, 0, -1, hx, -hy, -hz, -hx, -hy, -hz, -hx, hy, -hz, hx, hy, -hz },
				{ 1, 0, 0, hx, -hy, hz, hx, -hy, -hz, hx, hy, -hz, hx, hy, hz },
				{ -1, 0, 0, -hx, -hy, -hz, -hx, -hy, hz, -hx, hy, hz, -hx, hy, -hz },
				{ 0, 1, 0, -hx, hy, hz, hx, hy, hz, hx, hy, -hz, -hx, hy, -hz },
				{ 0, -1, 0, -hx, -hy, -hz, hx, -hy, -hz, hx, -hy, hz, -hx, -hy, hz } };
		for (float[] f : faces) {
			for (int k = 0; k < 4; k++) {
				vc.addVertex(p, f[3 + k * 3], f[4 + k * 3], f[5 + k * 3]).setColor(255, 255, 255, 255).setUv(u, v)
						.setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, f[0], f[1], f[2]);
			}
		}
	}

	static float smooth(float x) {
		x = Mth.clamp(x, 0f, 1f);
		return x * x * (3f - 2f * x);
	}
}
