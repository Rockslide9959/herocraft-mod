package com.projecthero.mod.client.miraculous.multimouse;

import java.util.UUID;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.miraculous.multimouse.entity.MiniMouseEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * v0.17.1: a mini Multimouse -- a tiny copy of its owner: her own skin (from her player info; the arm width follows it)
 * with the suit, the ears, the rope belt and the tail over it. The size is the entity's generic.scale (applied by the
 * vanilla renderer). The vanilla body pass is skipped; the layer draws everything.
 */
public class MiniMouseRenderer extends HumanoidMobRenderer<MiniMouseEntity, PlayerModel<MiniMouseEntity>> {
	private final PlayerModel<MiniMouseEntity> wide;
	private final PlayerModel<MiniMouseEntity> slim;

	public MiniMouseRenderer(EntityRendererProvider.Context ctx) {
		super(ctx, new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER_SLIM), true), 0.5f);
		this.slim = this.model;
		this.wide = new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER), false);
		addLayer(new Body(this));
	}

	static PlayerSkin skinOf(MiniMouseEntity e) {
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

	static boolean slimOf(MiniMouseEntity e) {
		return skinOf(e).model() == PlayerSkin.Model.SLIM;
	}

	@Override
	public void render(MiniMouseEntity entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
		this.model = slimOf(entity) ? slim : wide;
		this.shadowRadius = 0.5f * entity.getScale();
		super.render(entity, yaw, partialTick, pose, buffers, light);
	}

	@Override
	protected RenderType getRenderType(MiniMouseEntity entity, boolean bodyVisible, boolean translucent, boolean glowing) {
		return null; // the Body layer draws it
	}

	@Override
	public ResourceLocation getTextureLocation(MiniMouseEntity entity) {
		return skinOf(entity).texture();
	}

	static final class Body extends RenderLayer<MiniMouseEntity, PlayerModel<MiniMouseEntity>> {
		Body(RenderLayerParent<MiniMouseEntity, PlayerModel<MiniMouseEntity>> parent) {
			super(parent);
		}

		@Override
		public void render(PoseStack pose, MultiBufferSource buffers, int light, MiniMouseEntity e, float limbSwing, float limbSwingAmount,
				float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
			boolean slim = slimOf(e);
			// pops in out of a sparkle: the suit spreads over it in its first few ticks
			float cover = Mth.clamp((e.tickCount + partialTick) / 6f, 0.36f, 1f);
			PlayerModel<MiniMouseEntity> m = getParentModel();
			m.setAllVisible(true);
			m.hat.visible = false;
			m.jacket.visible = false;
			m.leftSleeve.visible = false;
			m.rightSleeve.visible = false;
			m.leftPants.visible = false;
			m.rightPants.visible = false;
			int overlay = LivingEntityRenderer.getOverlayCoords(e, 0f);
			m.renderToBuffer(pose, buffers.getBuffer(RenderType.entityTranslucent(skinOf(e).texture())), light, overlay, 0xFFFFFFFF);
			MultimouseRender.renderSuit(pose, buffers, light, m, slim, cover, overlay);
			MultimouseRender.renderExtras(pose, buffers, light, m, 1f, ageInTicks, Mth.clamp(limbSwingAmount, 0f, 1f), limbSwing);
		}
	}
}
