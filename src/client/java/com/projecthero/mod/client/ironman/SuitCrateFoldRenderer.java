package com.projecthero.mod.client.ironman;

import java.util.Map;
import java.util.WeakHashMap;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.ironman.crate.SuitCrateFoldEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Rotations;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;

/**
 * v0.16.7 (user) ERA I: draws the ejected suit ({@link SuitCrateFoldEntity}) -- its real GeckoLib armour on an invisible
 * client {@link ArmorStand} (the Sentry Mode trick, {@link IronManSentryRenderer}), back panels swung open where the wearer
 * shot out, lights on -- and then the fold: the arms come in, the head bows, the plates squash and shrink down into the
 * box while the crate's four walls swing up off the ground around it (one after another) and the lid slams shut. The box
 * is drawn on the very block the crate will stand on, with the crate's own textures, so the hand-over to the block is
 * seamless.
 */
public class SuitCrateFoldRenderer extends EntityRenderer<SuitCrateFoldEntity> {
	private static final ResourceLocation FRONT = ProjectHeroMod.id("textures/block/suit_crate_front.png");
	private static final ResourceLocation SIDE = ProjectHeroMod.id("textures/block/suit_crate_side.png");
	private static final ResourceLocation TOP = ProjectHeroMod.id("textures/block/suit_crate_top.png");
	private static final ResourceLocation INSIDE = ProjectHeroMod.id("textures/block/suit_crate_inside.png");
	private static final EquipmentSlot[] SLOTS = { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET };
	/** The crate's height (15 px). */
	private static final float H = 15f / 16f;
	private static final Map<SuitCrateFoldEntity, ArmorStand> STANDS = new WeakHashMap<>();

	public SuitCrateFoldRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.shadowRadius = 0.4f;
	}

	@Override
	public void render(SuitCrateFoldEntity e, float entityYaw, float partialTick, PoseStack pose, MultiBufferSource buffers,
			int packedLight) {
		if (e.level() == null) {
			return;
		}
		float fold = e.foldProgress(partialTick);
		float yaw = e.getYRot();
		float boxYaw = e.facing().toYRot();
		// where the box stands, relative to the entity (the target block's centre, on its floor)
		BlockPos target = e.target();
		double bx = 0.0;
		double by = 0.0;
		double bz = 0.0;
		if (target != null) {
			bx = target.getX() + 0.5 - Mth.lerp(partialTick, e.xo, e.getX());
			by = target.getY() - Mth.lerp(partialTick, e.yo, e.getY());
			bz = target.getZ() + 0.5 - Mth.lerp(partialTick, e.zo, e.getZ());
		}

		// ---- the suit: stands, then collapses into the box (gone by 75% of the fold)
		float collapse = smooth(fold / 0.7f);
		float vanish = smooth((fold - 0.5f) / 0.25f);
		if (vanish < 1f) {
			ArmorStand stand = STANDS.computeIfAbsent(e, SuitCrateFoldRenderer::makeStand);
			for (int i = 0; i < 4; i++) {
				ItemStack want = e.stack(i);
				if (!ItemStack.matches(stand.getItemBySlot(SLOTS[i]), want)) {
					stand.setItemSlot(SLOTS[i], want.copy());
				}
			}
			// arms fold across the chest, the head bows, the legs draw together
			stand.setHeadPose(new Rotations(32f * collapse, 0f, 0f));
			stand.setRightArmPose(new Rotations(-15f - 75f * collapse, -35f * collapse, 10f - 6f * collapse));
			stand.setLeftArmPose(new Rotations(-10f - 75f * collapse, 35f * collapse, -10f + 6f * collapse));
			stand.setRightLegPose(new Rotations(-20f * collapse, 0f, 1f));
			stand.setLeftLegPose(new Rotations(20f * collapse, 0f, -1f));
			float sy = (1f - 0.55f * collapse) * (1f - vanish);
			float sxz = (1f - 0.3f * collapse) * (1f - vanish);
			pose.pushPose();
			// slides onto the crate's block as it folds
			pose.translate(bx * collapse, by * collapse, bz * collapse);
			pose.mulPose(Axis.YP.rotationDegrees(-Mth.rotLerp(collapse, yaw, boxYaw)));
			pose.translate(0.0, 0.05 * collapse, 0.0);
			pose.scale(Math.max(0.001f, sxz), Math.max(0.001f, sy), Math.max(0.001f, sxz));
			var dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
			dispatcher.setRenderShadow(false);
			// the back hangs open where the wearer shot out, and shuts as it folds; the lights die halfway down
			IronManSentryClient.openDeg = (1f - smooth(fold / 0.4f)) * IronManSentryClient.OPEN_DEG;
			IronManSentryClient.dark = fold > 0.45f;
			try {
				dispatcher.render(stand, 0.0, 0.0, 0.0, 0.0f, partialTick, pose, buffers, packedLight);
			} finally {
				IronManSentryClient.openDeg = 0f;
				IronManSentryClient.dark = false;
				dispatcher.setRenderShadow(true);
				pose.popPose();
			}
		}

		// ---- the crate assembling around it
		if (fold > 0f) {
			pose.pushPose();
			pose.translate(bx, by, bz);
			// the block model's front is its north face, turned to the facing: in this frame the front is +Z after -yaw
			pose.mulPose(Axis.YP.rotationDegrees(-boxYaw));
			float grow = smooth(fold / 0.12f); // the floor plate and flat walls slide out from under the suit
			pose.scale(grow, 1f, grow);
			drawCrate(pose, buffers, packedLight, fold);
			pose.popPose();
		}
		super.render(e, entityYaw, partialTick, pose, buffers, packedLight);
	}

	/** The floor, the four walls swinging up off the ground (front last), and the lid slamming shut. */
	private static void drawCrate(PoseStack pose, MultiBufferSource buffers, int light, float fold) {
		// floor
		panel(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(INSIDE)), light, -0.5f, 0.002f, -0.5f, 0.5f, 0.002f, 0.5f, true, 0f, 0f, 1f, 1f);
		// walls: back, left, right, front -- each starts up a little later; flat (90 deg outwards) -> upright
		float[] start = { 0.12f, 0.2f, 0.28f, 0.36f };
		float[] turn = { 180f, 90f, -90f, 0f };
		for (int i = 0; i < 4; i++) {
			float up = smooth((fold - start[i]) / 0.34f);
			float angle = 90f * (1f - up);
			pose.pushPose();
			pose.mulPose(Axis.YP.rotationDegrees(turn[i]));
			pose.translate(0.0, 0.0, 0.5);
			pose.mulPose(Axis.XP.rotationDegrees(angle));
			ResourceLocation tex = i == 3 ? FRONT : SIDE;
			// a wall in the z = 0 plane, x -0.5..0.5, y 0..H (the crate's side texture below its lid band)
			wall(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(tex)), light, H);
			pose.popPose();
		}
		// the lid: hinged on the back edge, stands up behind until the walls are up, then slams shut
		if (fold > 0.45f) {
			float shut = smooth((fold - 0.72f) / 0.24f);
			float lidAngle = -110f * (1f - shut);
			pose.pushPose();
			pose.translate(0.0, H, -0.5);
			pose.mulPose(Axis.XP.rotationDegrees(lidAngle));
			VertexConsumer top = buffers.getBuffer(RenderType.entityCutoutNoCull(TOP));
			panel(pose, top, light, -0.5f, 0f, 0f, 0.5f, 0f, 1f, true, 0f, 0f, 1f, 1f);
			pose.popPose();
		}
	}

	/** A wall quad in the local z = 0 plane (x -0.5..0.5, y 0..h), textured with the side texture's rows 0..16 squeezed in. */
	private static void wall(PoseStack pose, VertexConsumer vc, int light, float h) {
		PoseStack.Pose p = pose.last();
		// seen from outside (+z) the texture reads left-to-right; the inside sees it mirrored, which is fine for a crate
		vertex(vc, p, -0.5f, 0f, 0f, 0f, 1f, light, 0f, 0f, 1f);
		vertex(vc, p, 0.5f, 0f, 0f, 1f, 1f, light, 0f, 0f, 1f);
		vertex(vc, p, 0.5f, h, 0f, 1f, 0f, light, 0f, 0f, 1f);
		vertex(vc, p, -0.5f, h, 0f, 0f, 0f, light, 0f, 0f, 1f);
	}

	/** A horizontal quad from (x0, y, z0) to (x1, y, z1). */
	private static void panel(PoseStack pose, VertexConsumer vc, int light, float x0, float y0, float z0, float x1, float y1,
			float z1, boolean up, float u0, float v0, float u1, float v1) {
		PoseStack.Pose p = pose.last();
		vertex(vc, p, x0, y0, z0, u0, v0, light, 0f, 1f, 0f);
		vertex(vc, p, x0, y1, z1, u0, v1, light, 0f, 1f, 0f);
		vertex(vc, p, x1, y1, z1, u1, v1, light, 0f, 1f, 0f);
		vertex(vc, p, x1, y0, z0, u1, v0, light, 0f, 1f, 0f);
	}

	private static void vertex(VertexConsumer vc, PoseStack.Pose p, float x, float y, float z, float u, float v, int light,
			float nx, float ny, float nz) {
		vc.addVertex(p, x, y, z).setColor(255, 255, 255, 255).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
				.setLight(light).setNormal(p, nx, ny, nz);
	}

	private static float smooth(float x) {
		x = Mth.clamp(x, 0f, 1f);
		return x * x * (3f - 2f * x);
	}

	private static ArmorStand makeStand(SuitCrateFoldEntity e) {
		ArmorStand stand = new ArmorStand(e.level(), 0, 0, 0);
		stand.setInvisible(true); // only the armour shows
		stand.setNoBasePlate(true);
		stand.setShowArms(true);
		stand.setNoGravity(true);
		stand.setYRot(0f);
		stand.yBodyRot = 0f;
		stand.yHeadRot = 0f;
		return stand;
	}

	@Override
	public ResourceLocation getTextureLocation(SuitCrateFoldEntity e) {
		return SIDE;
	}

	@Override
	protected int getBlockLightLevel(SuitCrateFoldEntity e, BlockPos pos) {
		return Math.max(super.getBlockLightLevel(e, pos), 4);
	}
}
