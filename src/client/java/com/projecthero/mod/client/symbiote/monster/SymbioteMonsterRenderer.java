package com.projecthero.mod.client.symbiote.monster;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoReplacedEntityRenderer;

/**
 * v0.17.5 Symbiote Monster form: draws the hulking monster ({@link SymbioteMonsterAnimatable}) in place of a transformed host -- so
 * the suit, held items and the cape are all replaced, like the Hulk. Called from {@code PlayerRendererSymbioteMonsterMixin}. GeckoLib
 * scales a replaced entity by its scale attribute; the body is drawn at its true size instead ({@link #MODEL_SCALE}: the 96-px model
 * stands {@code SymbioteMonster.HEIGHT} = 7 blocks), so that scale is divided back out. The texture is the host's colour bake.
 */
public class SymbioteMonsterRenderer extends GeoReplacedEntityRenderer<AbstractClientPlayer, SymbioteMonsterAnimatable> {
	/** The model's rest height is 95.6 px (5.98 blocks at scale 1): drawn at 7 blocks. */
	public static final float MODEL_SCALE = (float) (com.projecthero.mod.symbiote.monster.SymbioteMonster.HEIGHT * 16.0 / 95.6);
	private static SymbioteMonsterRenderer instance;

	public SymbioteMonsterRenderer(EntityRendererProvider.Context context) {
		super(context, new SymbioteMonsterModel<>(), SymbioteMonsterAnimatable.INSTANCE);
		this.shadowRadius = 1.6f;
		withScale(MODEL_SCALE);
	}

	public static void rebuild(EntityRendererProvider.Context context) {
		instance = new SymbioteMonsterRenderer(context);
	}

	public static SymbioteMonsterRenderer get() {
		return instance;
	}

	/** Render {@code player} as the monster at its true size (undoing the scale attribute GeckoLib applies). */
	public void renderBody(AbstractClientPlayer player, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffer,
			int packedLight) {
		float s = Math.max(0.01f, player.getScale());
		poseStack.pushPose();
		poseStack.scale(1f / s, 1f / s, 1f / s);
		try {
			render(player, entityYaw, partialTick, poseStack, buffer, packedLight);
		} finally {
			poseStack.popPose();
		}
	}

	@Override
	public ResourceLocation getTextureLocation(SymbioteMonsterAnimatable animatable) {
		return currentEntity != null ? SymbioteMonsterTint.texture(currentEntity) : SymbioteMonsterTint.CLASSIC;
	}

	@Override
	public RenderType getRenderType(SymbioteMonsterAnimatable animatable, ResourceLocation texture, MultiBufferSource bufferSource,
			float partialTick) {
		return RenderType.entityCutoutNoCull(texture);
	}

	/** The baked model is shared with the first-person claws, which hide bones: show everything again. */
	@Override
	public void preRender(PoseStack poseStack, SymbioteMonsterAnimatable animatable, BakedGeoModel model, MultiBufferSource bufferSource,
			VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay, int colour) {
		if (!isReRender) {
			for (software.bernie.geckolib.cache.object.GeoBone bone : model.topLevelBones()) {
				SymbioteMonsterFirstPerson.showAll(bone);
			}
		}
		super.preRender(poseStack, animatable, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, colour);
	}

	@Override
	public boolean shouldShowName(AbstractClientPlayer player) {
		Minecraft mc = Minecraft.getInstance();
		return Minecraft.renderNames() && player != mc.getCameraEntity() && mc.player != null && !player.isInvisibleTo(mc.player)
				&& !player.isVehicle();
	}

	/** Light boosted a little so the dark goo still reads at night (the eyes and emblem catch it most). */
	static int lift(int packedLight) {
		return LightTexture.pack(Math.max(LightTexture.block(packedLight), 3), LightTexture.sky(packedLight));
	}
}
