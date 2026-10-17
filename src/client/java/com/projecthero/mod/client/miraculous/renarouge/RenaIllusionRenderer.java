package com.projecthero.mod.client.miraculous.renarouge;

import java.util.UUID;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.miraculous.renarouge.entity.RenaIllusionEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * v0.16.6: an illusion of Rena Rouge -- her own skin (from the owner's player info) with the same suit, fox tail and
 * ears and Flute over it (the arm width follows her skin). The vanilla body pass is skipped; the layer draws everything, so a dissolving illusion can fade: it runs the
 * transformation's light frames backwards, tinted orange, as it goes.
 */
public class RenaIllusionRenderer extends HumanoidMobRenderer<RenaIllusionEntity, PlayerModel<RenaIllusionEntity>> {
	private final PlayerModel<RenaIllusionEntity> wide;
	private final PlayerModel<RenaIllusionEntity> slim;

	public RenaIllusionRenderer(EntityRendererProvider.Context ctx) {
		super(ctx, new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER_SLIM), true), 0.5f);
		this.slim = this.model;
		this.wide = new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER), false);
		addLayer(new Body(this));
	}

	/** The owner's own skin (the illusion is her, suit and all). */
	static PlayerSkin skinOf(RenaIllusionEntity e) {
		UUID id = e.getOwnerUUID();
		var conn = Minecraft.getInstance().getConnection();
		if (id != null && conn != null) {
			PlayerInfo info = conn.getPlayerInfo(id);
			if (info != null) {
				return info.getSkin();
			}
		}
		return DefaultPlayerSkin.get(id != null ? id : e.getUUID());
	}

	static boolean slimOf(RenaIllusionEntity e) {
		return skinOf(e).model() == PlayerSkin.Model.SLIM;
	}

	@Override
	public void render(RenaIllusionEntity entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
		this.model = slimOf(entity) ? slim : wide;
		super.render(entity, yaw, partialTick, pose, buffers, light);
	}

	@Override
	protected RenderType getRenderType(RenaIllusionEntity entity, boolean bodyVisible, boolean translucent, boolean glowing) {
		return null; // the Body layer draws it
	}

	@Override
	public ResourceLocation getTextureLocation(RenaIllusionEntity entity) {
		return skinOf(entity).texture();
	}

	/** The suit, the fox parts and the Flute; fading into orange light while it dissolves. */
	static final class Body extends RenderLayer<RenaIllusionEntity, PlayerModel<RenaIllusionEntity>> {
		Body(RenderLayerParent<RenaIllusionEntity, PlayerModel<RenaIllusionEntity>> parent) {
			super(parent);
		}

		@Override
		public void render(PoseStack pose, MultiBufferSource buffers, int light, RenaIllusionEntity e, float limbSwing, float limbSwingAmount,
				float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
			float d = e.dissolveProgress(partialTick);
			boolean slim = slimOf(e);
			float cover = 1f - d;
			float alpha = 1f - 0.5f * d;
			int g = (int) Mth.lerp(d, 255, 170);
			int b = (int) Mth.lerp(d, 255, 70);
			int tint = (255 << 16) | (g << 8) | b;
			// the first few ticks of a new illusion: it shimmers in from the light
			float born = Mth.clamp((e.tickCount + partialTick) / 6f, 0f, 1f);
			if (born < 1f && d <= 0f) {
				cover = born;
			}
			// her own skin underneath (base layer and hat / hair; the jacket, sleeves and trousers are under the suit)
			PlayerModel<RenaIllusionEntity> m = getParentModel();
			m.setAllVisible(true);
			m.jacket.visible = false;
			m.leftSleeve.visible = false;
			m.rightSleeve.visible = false;
			m.leftPants.visible = false;
			m.rightPants.visible = false;
			int a = Math.round(255 * Mth.clamp(alpha * (d > 0f ? 1f - d : born), 0f, 1f));
			m.renderToBuffer(pose, buffers.getBuffer(RenderType.entityTranslucent(skinOf(e).texture())), light,
					net.minecraft.client.renderer.entity.LivingEntityRenderer.getOverlayCoords(e, 0f), net.minecraft.util.FastColor.ARGB32.color(a, tint));
			RenaRougeRender.renderSuit(pose, buffers, light, m, slim, cover, alpha, tint);
			RenaRougeRender.renderFoxParts(pose, buffers, light, getParentModel(), cover, ageInTicks, Mth.clamp(limbSwingAmount, 0f, 1f), alpha, tint);
			RenaRougeRender.renderFlute(pose, buffers, light, getParentModel(),
					e.kind() == RenaIllusionEntity.DOUBLE ? RenaRougeRender.FluteAt.HAND : RenaRougeRender.FluteAt.BACK, slim, alpha * cover);
		}
	}
}
