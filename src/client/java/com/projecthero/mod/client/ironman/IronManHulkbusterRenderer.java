package com.projecthero.mod.client.ironman;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

import software.bernie.geckolib.renderer.GeoReplacedEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * v0.16.1: draws the Hulkbuster body ({@link IronManHulkbusterAnimatable}) in place of a player wearing the full Mark 8
 * -- so the vanilla body, the armour pieces, the cape and held items are all replaced, like the Hulk. Called from
 * {@code PlayerRendererHulkbusterMixin}. GeckoLib scales a replaced entity by its scale attribute (the Mark 8's 1.45x);
 * the body is modelled at its true size (v0.16.7: a Steve player model x1.5, 3 blocks at model scale 1), so that scale is divided back out here.
 * The glowmask (eyes, reactor, palms, thrusters) is drawn full-bright by the auto-glow layer.
 */
public class IronManHulkbusterRenderer extends GeoReplacedEntityRenderer<AbstractClientPlayer, IronManHulkbusterAnimatable>
		implements HulkbusterPartRender.View {
	private static IronManHulkbusterRenderer instance;

	public IronManHulkbusterRenderer(EntityRendererProvider.Context context) {
		super(context, new IronManHulkbusterModel<>(), IronManHulkbusterAnimatable.INSTANCE);
		this.shadowRadius = 1.0f;
		addRenderLayer(new AutoGlowingGeoLayer<>(this) {
			@Override
			public void render(PoseStack poseStack, IronManHulkbusterAnimatable animatable, software.bernie.geckolib.cache.object.BakedGeoModel model,
					net.minecraft.client.renderer.RenderType renderType, MultiBufferSource bufferSource,
					com.mojang.blaze3d.vertex.VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
				// v0.16.7 (user): the lights stay dark while the suit builds on
				if (!buildTexture) {
					super.render(poseStack, animatable, model, renderType, bufferSource, buffer, partialTick, packedLight, packedOverlay);
				}
			}
		});
		addRenderLayer(new HulkbusterPartRender.Layer<>(this, this)); // v0.16.7 (user): each part at its own damage
		addRenderLayer(new software.bernie.geckolib.renderer.layer.GeoRenderLayer<>(this) {
			@Override
			public void render(PoseStack poseStack, IronManHulkbusterAnimatable animatable, software.bernie.geckolib.cache.object.BakedGeoModel model,
					net.minecraft.client.renderer.RenderType renderType, MultiBufferSource bufferSource,
					com.mojang.blaze3d.vertex.VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
				renderInnerFrame(poseStack, animatable, model, bufferSource, partialTick, packedLight, packedOverlay);
			}
		});
	}

	/** v0.16.7 (user): the parts the last draw left out (first person hides head + torso) -- the inner frame does too. */
	private int lastHidden;

	/**
	 * v0.16.7 (user) "you dont really see the player while the suits building itself ... not just making the player
	 * invisible": during the pod build the Hulkbuster's dark inner frame ({@link HulkbusterBuildClient#frameTextures}) is
	 * drawn round the pilot at the suit's full size -- every part at rest (no flying offsets), only where no plate is on
	 * yet -- so through the gaps of the half-built suit you see gunmetal struts and glowing seams, not the player inside.
	 */
	private void renderInnerFrame(PoseStack poseStack, IronManHulkbusterAnimatable animatable,
			software.bernie.geckolib.cache.object.BakedGeoModel model, MultiBufferSource bufferSource, float partialTick, int packedLight,
			int packedOverlay) {
		AbstractClientPlayer p = currentEntity;
		if (p == null || IronManHulkbusterAnimatable.active(p)) {
			return;
		}
		net.minecraft.resources.ResourceLocation[] tex = HulkbusterBuildClient.frameTextures(p, partialTick);
		if (tex == null || tex[0] == null) {
			return;
		}
		HulkbusterVeronicaClient.BuildPose build = HulkbusterVeronicaClient.buildPose(p, partialTick);
		float[][] saved = new float[com.projecthero.mod.ironman.HulkbusterParts.COUNT][];
		for (int part = 0; part < saved.length; part++) {
			final int k = part;
			model.getBone(HulkbusterPartRender.rootBone(part)).ifPresent(b -> {
				saved[k] = new float[] { b.getPosX(), b.getPosY(), b.getPosZ(), b.getRotX(), b.getRotY(), b.getRotZ() };
				if (build != null) { // the frame stands at rest round the pilot while the pieces fly in
					b.setPosX(0f);
					b.setPosY(0f);
					b.setPosZ(0f);
					b.setRotX(b.getRotX() - build.rot[k][0]);
					b.setRotY(b.getRotY() - build.rot[k][1]);
					b.setRotZ(b.getRotZ() - build.rot[k][2]);
				}
			});
		}
		HulkbusterPartRender.show(model, HulkbusterPartRender.ALL & ~lastHidden);
		try {
			net.minecraft.client.renderer.RenderType base = net.minecraft.client.renderer.RenderType.entityCutoutNoCull(tex[0]);
			reRender(model, poseStack, bufferSource, animatable, base, bufferSource.getBuffer(base), partialTick, packedLight,
					packedOverlay, 0xFFFFFFFF);
			if (tex[1] != null) {
				net.minecraft.client.renderer.RenderType glow = net.minecraft.client.renderer.RenderType.eyes(tex[1]);
				reRender(model, poseStack, bufferSource, animatable, glow, bufferSource.getBuffer(glow), partialTick,
						net.minecraft.client.renderer.LightTexture.FULL_SKY, packedOverlay, 0xFFFFFFFF);
			}
		} finally {
			for (int part = 0; part < saved.length; part++) {
				final float[] s = saved[part];
				if (s != null) {
					model.getBone(HulkbusterPartRender.rootBone(part)).ifPresent(b -> {
						b.setPosX(s[0]);
						b.setPosY(s[1]);
						b.setPosZ(s[2]);
						b.setRotX(s[3]);
						b.setRotY(s[4]);
						b.setRotZ(s[5]);
					});
				}
			}
			HulkbusterPartRender.show(model, HulkbusterPartRender.baseMask(partTiers == null ? new int[6] : partTiers, visibleParts));
		}
	}

	/** v0.16.7 (user): this frame draws the build-on texture ({@link HulkbusterBuildClient}) instead of the plain one. */
	private boolean buildTexture;
	private net.minecraft.resources.ResourceLocation buildFrame;

	@Override
	public net.minecraft.resources.ResourceLocation getTextureLocation(IronManHulkbusterAnimatable animatable) {
		return buildFrame != null ? buildFrame : super.getTextureLocation(animatable);
	}

	/** v0.16.7 (user) Veronica: this frame's per-part tiers / the parts there at all (the mid-air build). */
	private int[] partTiers;
	private int visibleParts = HulkbusterPartRender.ALL;
	/** v0.16.7 (user): parts the next draw must leave out (the first-person build view hides the head / torso). */
	public static int hideNext;

	@Override
	public int[] partTiers() {
		return partTiers;
	}

	@Override
	public int visibleParts() {
		return visibleParts;
	}

	public static void rebuild(EntityRendererProvider.Context context) {
		instance = new IronManHulkbusterRenderer(context);
	}

	public static IronManHulkbusterRenderer get() {
		return instance;
	}

	/** v0.16.7 (user): the pose at the player's render origin, for {@link HulkbusterLocators} (render thread). */
	private final org.joml.Matrix4f origin = new org.joml.Matrix4f();
	private float frameTick;

	/** Render {@code player} as the Hulkbuster at model scale 1 (undoing the scale attribute GeckoLib applies). */
	public void renderBody(AbstractClientPlayer player, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffer,
			int packedLight) {
		float s = Math.max(0.01f, player.getScale());
		origin.set(poseStack.last().pose());
		frameTick = partialTick;
		buildFrame = HulkbusterBuildClient.texture(player, partialTick); // v0.16.7 (user): building on, gantry style
		buildTexture = buildFrame != null;
		poseStack.pushPose();
		poseStack.scale(1f / s, 1f / s, 1f / s);
		try {
			render(player, entityYaw, partialTick, poseStack, buffer, packedLight);
		} finally {
			poseStack.popPose();
			buildFrame = null;
			buildTexture = false;
		}
	}

	/** The baked model is shared with the Rocket Fist / first-person arms, which hide bones: show everything again. */
	@Override
	public void preRender(PoseStack poseStack, IronManHulkbusterAnimatable animatable, software.bernie.geckolib.cache.object.BakedGeoModel model,
			MultiBufferSource bufferSource, com.mojang.blaze3d.vertex.VertexConsumer buffer, boolean isReRender, float partialTick,
			int packedLight, int packedOverlay, int colour) {
		if (!isReRender) {
			for (software.bernie.geckolib.cache.object.GeoBone bone : model.topLevelBones()) {
				showAll(bone);
			}
			// v0.16.7 (user) Veronica: broken / damaged parts are drawn by the part layer; a build shows what has arrived
			partTiers = null;
			visibleParts = HulkbusterPartRender.ALL;
			if (currentEntity != null) {
				if (IronManHulkbusterAnimatable.active(currentEntity)) {
					partTiers = HulkbusterPartRender.tiers(com.projecthero.mod.ironman.HulkbusterParts.of(currentEntity));
				} else {
					HulkbusterVeronicaClient.BuildPose b = HulkbusterVeronicaClient.buildPose(currentEntity, partialTick);
					visibleParts = b == null ? HulkbusterPartRender.ALL : b.visible;
				}
			}
			visibleParts &= ~hideNext;
			lastHidden = hideNext;
			hideNext = 0;
			if (partTiers != null || visibleParts != HulkbusterPartRender.ALL) {
				HulkbusterPartRender.show(model, HulkbusterPartRender.baseMask(partTiers == null ? new int[6] : partTiers, visibleParts));
			}
		}
		super.preRender(poseStack, animatable, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, colour);
	}

	/** v0.16.7 (user): record the jet / palm / reactor locators for the flight and unibeam VFX (no fx geometry any more). */
	@Override
	public void renderRecursively(PoseStack poseStack, IronManHulkbusterAnimatable animatable,
			software.bernie.geckolib.cache.object.GeoBone bone, net.minecraft.client.renderer.RenderType renderType,
			MultiBufferSource bufferSource, com.mojang.blaze3d.vertex.VertexConsumer buffer, boolean isReRender, float partialTick,
			int packedLight, int packedOverlay, int colour) {
		if (!isReRender && currentEntity != null) {
			int slot = HulkbusterLocators.slotOf(bone);
			if (slot >= 0) {
				HulkbusterLocators.capture(currentEntity, slot, bone, poseStack, origin, frameTick);
			}
			int part = HulkbusterLocators.partSlotOf(bone);
			if (part >= 0) {
				HulkbusterLocators.capturePart(currentEntity, part, bone, poseStack, origin);
			}
		}
		super.renderRecursively(poseStack, animatable, bone, renderType, bufferSource, buffer, isReRender, partialTick, packedLight,
				packedOverlay, colour);
	}

	static void showAll(software.bernie.geckolib.cache.object.GeoBone bone) {
		bone.setHidden(false);
		bone.setChildrenHidden(false);
		for (software.bernie.geckolib.cache.object.GeoBone child : bone.getChildBones()) {
			showAll(child);
		}
	}

	@Override
	public boolean shouldShowName(AbstractClientPlayer player) {
		Minecraft mc = Minecraft.getInstance();
		return Minecraft.renderNames() && player != mc.getCameraEntity() && mc.player != null && !player.isInvisibleTo(mc.player)
				&& !player.isVehicle();
	}
}
