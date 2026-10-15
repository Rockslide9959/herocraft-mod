package com.projecthero.mod.client.hulk;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoReplacedEntityRenderer;

/**
 * v0.13.12 (Hulk Phase 3): draws the Hulk model in place of a player who is the Hulk. Called from
 * {@code PlayerRendererHulkMixin}, which cancels the vanilla player render -- so his armour, cape and held items are
 * hidden while he is out. GeckoLib applies the player's own scale attribute itself (v0.16.4: rage-driven, 3.5-6 blocks; see
 * {@link #MODEL_FIT}). Created from the {@code PlayerRenderer}'s context on every resource reload.
 *
 * <p>v0.16.4 (the morph, {@link HulkMorph}): the Hulk is always drawn solid -- never see-through. {@link #renderMorph} adds
 * the morph's swell (the same per-part numbers Banner had at the swap, applied to the bones' own cubes), the fit factor that
 * matches his silhouette to Banner's at the swap, and the gamma glow (extra light) after it. {@link #renderGhost} runs his
 * animation without drawing anything, so the transform clip is on the right frame when he takes over from Banner.
 */
public class HulkRenderer extends GeoReplacedEntityRenderer<AbstractClientPlayer, HulkAnimatable> {
	private static HulkRenderer instance;

	/**
	 * v0.16.4 (user: the Hulk is 3.5 blocks tall at 0 rage and 6 at 100): GeckoLib already multiplies the model by the player's
	 * SCALE attribute (height / 1.8), and hulk.geo.json stands 32 px = 2 blocks at scale 1 -- so this pre-scale makes the drawn
	 * Hulk exactly as tall as his hitbox (2 x 0.9 = 1.8 per unit of scale) instead of 11% taller. No second size factor anywhere.
	 */
	public static final float MODEL_FIT = 0.9f;

	/** Rendering only to advance the animation (nothing drawn). Rendering is single-threaded. */
	private boolean ghost;
	/** The morph swell of the draw in progress ({@link HulkMorph#bulge}), or null for none. */
	private float[] swell;
	private final float[] swellScratch = new float[HulkMorph.PARTS];

	public HulkRenderer(EntityRendererProvider.Context context) {
		super(context, new HulkModel(), HulkAnimatable.INSTANCE);
		this.shadowRadius = 0.5f;
		withScale(MODEL_FIT);
		addRenderLayer(new HulkGladiatorLayer(this)); // v0.15.3: Gladiator Hulk's gear (own texture, second pass)
		addRenderLayer(new HulkWorldBreakerLayer(this)); // v0.16.4 (user): World Breaker's glowing veins
	}

	/** v0.16.4: a ghost render (animation only) is in progress -- layers draw nothing either. */
	public boolean ghost() {
		return this.ghost;
	}

	/** v0.15.3: the main pass never draws the gladiator bones -- {@link HulkGladiatorLayer} does, with its own texture. */
	@Override
	public void preRender(PoseStack poseStack, HulkAnimatable animatable, software.bernie.geckolib.cache.object.BakedGeoModel model,
			MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender, float partialTick,
			int packedLight, int packedOverlay, int colour) {
		if (!isReRender) {
			HulkGladiatorLayer.baseVisibility(model);
		}
		super.preRender(poseStack, animatable, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, colour);
	}

	public static void rebuild(EntityRendererProvider.Context context) {
		instance = new HulkRenderer(context);
	}

	public static HulkRenderer get() {
		return instance;
	}

	/** Draws the Hulk solid, with the morph's swell / fit / glow while a change is under way. */
	public void renderMorph(AbstractClientPlayer player, float entityYaw, float partialTick, PoseStack poseStack,
			MultiBufferSource buffer, int packedLight) {
		float fit = HulkMorph.hulkFit(player, partialTick);
		float glow = HulkMorph.glow(player, partialTick);
		if (com.projecthero.mod.hulk.WorldBreaker.active(player)) {
			glow = Math.max(glow, 0.25f + 0.25f * HulkWorldBreakerLayer.pulse(player, partialTick)); // v0.16.4: World Breaker glows
		}
		int light = packedLight;
		if (glow > 0.0f) {
			int block = Math.max(LightTexture.block(packedLight), Math.round(15 * glow));
			int sky = Math.max(LightTexture.sky(packedLight), Math.round(15 * glow));
			light = LightTexture.pack(block, sky);
		}
		float w = this.scaleWidth;
		float h = this.scaleHeight;
		this.scaleWidth = MODEL_FIT * fit;
		this.scaleHeight = MODEL_FIT * fit;
		this.swell = HulkMorph.bulge(player, partialTick, this.swellScratch) ? this.swellScratch : null;
		try {
			render(player, entityYaw, partialTick, poseStack, buffer, light);
		} finally {
			this.scaleWidth = w;
			this.scaleHeight = h;
			this.swell = null;
		}
	}

	/** Runs the Hulk's animation for this player without drawing anything (Banner is drawn instead). */
	public void renderGhost(AbstractClientPlayer player, float entityYaw, float partialTick, PoseStack poseStack,
			MultiBufferSource buffer, int packedLight) {
		this.ghost = true;
		try {
			render(player, entityYaw, partialTick, poseStack, buffer, packedLight);
		} finally {
			this.ghost = false;
		}
	}

	@Override
	public RenderType getRenderType(HulkAnimatable animatable, ResourceLocation texture, MultiBufferSource buffer, float partialTick) {
		// a null type: GeckoLib still runs the animation, but draws no cubes
		return this.ghost ? null : super.getRenderType(animatable, texture, buffer, partialTick);
	}

	/** v0.16.4: the arms move out as the swollen chest widens (the same offset Banner's arms get). */
	@Override
	public void renderRecursively(PoseStack poseStack, HulkAnimatable animatable, GeoBone bone, RenderType renderType,
			MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight,
			int packedOverlay, int colour) {
		float[] s = this.swell;
		String name = bone.getName();
		if (s != null && ("right_arm".equals(name) || "left_arm".equals(name))) {
			float off = HulkMorph.armOffset(s[HulkMorph.BODY]) / 16.0f;
			poseStack.pushPose();
			poseStack.translate(Math.signum(bone.getPivotX()) * off, 0.0f, 0.0f);
			super.renderRecursively(poseStack, animatable, bone, renderType, bufferSource, buffer, isReRender, partialTick, packedLight,
					packedOverlay, colour);
			poseStack.popPose();
			return;
		}
		super.renderRecursively(poseStack, animatable, bone, renderType, bufferSource, buffer, isReRender, partialTick, packedLight,
				packedOverlay, colour);
	}

	/**
	 * v0.16.4: the morph swell, on each bone's own cubes only (so a swollen chest does not also inflate the head and arms
	 * hanging off it -- just as Banner's model parts are separate): thickness (x / z) about the limb's axis.
	 */
	@Override
	public void renderCubesOfBone(PoseStack poseStack, GeoBone bone, VertexConsumer buffer, int packedLight, int packedOverlay, int colour) {
		float[] s = this.swell;
		if (s == null || bone.isHidden()) {
			super.renderCubesOfBone(poseStack, bone, buffer, packedLight, packedOverlay, colour);
			return;
		}
		float sx;
		float sy = 1.0f;
		float sz;
		float px = bone.getPivotX();
		switch (bone.getName()) {
			case "body" -> {
				sx = HulkMorph.bodyWidth(s[HulkMorph.BODY]);
				sz = s[HulkMorph.BODY];
			}
			case "right_arm", "right_forearm", "right_hand" -> {
				sx = sz = s[HulkMorph.RIGHT_ARM];
				px = Math.signum(px) * 5.0f; // about the shoulder line, like Banner's one-piece arm
			}
			case "left_arm", "left_forearm", "left_hand" -> {
				sx = sz = s[HulkMorph.LEFT_ARM];
				px = Math.signum(px) * 5.0f;
			}
			case "right_leg", "right_shin", "right_foot" -> sx = sz = s[HulkMorph.RIGHT_LEG];
			case "left_leg", "left_shin", "left_foot" -> sx = sz = s[HulkMorph.LEFT_LEG];
			case "head" -> sx = sy = sz = s[HulkMorph.HEAD];
			default -> {
				super.renderCubesOfBone(poseStack, bone, buffer, packedLight, packedOverlay, colour);
				return;
			}
		}
		float ox = px / 16.0f;
		float oy = bone.getPivotY() / 16.0f;
		float oz = bone.getPivotZ() / 16.0f;
		poseStack.pushPose();
		poseStack.translate(ox, oy, oz);
		poseStack.scale(sx, sy, sz);
		poseStack.translate(-ox, -oy, -oz);
		super.renderCubesOfBone(poseStack, bone, buffer, packedLight, packedOverlay, colour);
		poseStack.popPose();
	}

	/**
	 * Name tags the way a player gets them (the replaced renderer only shows custom names by default). Never on a ghost render
	 * (Banner's own renderer draws his tag then).
	 */
	@Override
	public boolean shouldShowName(AbstractClientPlayer player) {
		Minecraft mc = Minecraft.getInstance();
		return !this.ghost && Minecraft.renderNames() && player != mc.getCameraEntity() && mc.player != null
				&& !player.isInvisibleTo(mc.player) && !player.isVehicle();
	}
}
