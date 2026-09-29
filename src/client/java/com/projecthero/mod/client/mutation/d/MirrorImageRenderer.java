package com.projecthero.mod.client.mutation.d;

import java.util.Optional;
import java.util.UUID;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.hero.revamp.d.MirrorImageEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidArmorModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;

/**
 * v0.13.22 (Light, V): draws a Mirror Image with its caster's own player skin (wide or slim arms to match) and the
 * illusion copies of their armour and held items, so from a distance it is indistinguishable from the real thing.
 */
public class MirrorImageRenderer extends HumanoidMobRenderer<MirrorImageEntity, PlayerModel<MirrorImageEntity>> {
	private final PlayerModel<MirrorImageEntity> wide;
	private final PlayerModel<MirrorImageEntity> slim;

	public MirrorImageRenderer(EntityRendererProvider.Context ctx) {
		super(ctx, new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER), false), 0.5f);
		this.wide = this.model;
		this.slim = new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER_SLIM), true);
		addLayer(new HumanoidArmorLayer<>(this,
				new HumanoidArmorModel<>(ctx.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
				new HumanoidArmorModel<>(ctx.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),
				ctx.getModelManager()));
	}

	private static PlayerSkin skinOf(MirrorImageEntity e) {
		Optional<UUID> id = e.ownerId();
		var conn = Minecraft.getInstance().getConnection();
		if (id.isPresent() && conn != null) {
			PlayerInfo info = conn.getPlayerInfo(id.get());
			if (info != null) {
				return info.getSkin();
			}
		}
		return DefaultPlayerSkin.get(id.orElse(e.getUUID()));
	}

	@Override
	public void render(MirrorImageEntity entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers,
			int light) {
		this.model = skinOf(entity).model() == PlayerSkin.Model.SLIM ? slim : wide;
		super.render(entity, yaw, partialTick, pose, buffers, light);
	}

	@Override
	public ResourceLocation getTextureLocation(MirrorImageEntity entity) {
		return skinOf(entity).texture();
	}
}
