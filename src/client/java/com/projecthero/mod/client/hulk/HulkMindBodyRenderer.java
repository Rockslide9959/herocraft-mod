package com.projecthero.mod.client.hulk;

import java.util.Optional;
import java.util.UUID;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.hulk.entity.HulkMindBodyEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;

/**
 * v0.16.4 (user): the body a player leaves behind while he is in his mind -- drawn with his own skin, exactly as his
 * player would be: the skin (and wide / slim arms) comes from his entry in the tab list ({@link PlayerInfo#getSkin}, which
 * every client has for every online player, even one in another dimension), falling back to the default skin for his
 * UUID. Posed on his knees clutching his head and looking down ({@link HulkPose#applyHeadache}), and the vanilla
 * "shaking" wobble on top.
 */
public class HulkMindBodyRenderer extends LivingEntityRenderer<HulkMindBodyEntity, PlayerModel<HulkMindBodyEntity>> {
	private final PlayerModel<HulkMindBodyEntity> wide;
	private final PlayerModel<HulkMindBodyEntity> slim;

	public HulkMindBodyRenderer(EntityRendererProvider.Context ctx) {
		super(ctx, new Model(ctx.bakeLayer(ModelLayers.PLAYER), false), 0.5f);
		this.wide = this.model;
		this.slim = new Model(ctx.bakeLayer(ModelLayers.PLAYER_SLIM), true);
	}

	static PlayerSkin skinOf(HulkMindBodyEntity e) {
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
	public void render(HulkMindBodyEntity entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
		this.model = skinOf(entity).model() == PlayerSkin.Model.SLIM ? this.slim : this.wide;
		super.render(entity, yaw, partialTick, pose, buffers, light);
	}

	@Override
	public ResourceLocation getTextureLocation(HulkMindBodyEntity entity) {
		return skinOf(entity).texture();
	}

	@Override
	protected void scale(HulkMindBodyEntity entity, PoseStack pose, float partialTick) {
		pose.scale(0.9375f, 0.9375f, 0.9375f); // the player renderer's own scale
	}

	@Override
	protected boolean isShaking(HulkMindBodyEntity entity) {
		return true; // a slight, constant tremble
	}

	@Override
	protected boolean shouldShowName(HulkMindBodyEntity entity) {
		return false;
	}

	/** A player model that always holds the headache pose. */
	private static final class Model extends PlayerModel<HulkMindBodyEntity> {
		Model(ModelPart root, boolean slim) {
			super(root, slim);
		}

		@Override
		public void setupAnim(HulkMindBodyEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw,
				float headPitch) {
			super.setupAnim(entity, 0.0f, 0.0f, ageInTicks, 0.0f, 0.0f);
			HulkPose.applyHeadache(this, ageInTicks);
			this.leftSleeve.copyFrom(this.leftArm);
			this.rightSleeve.copyFrom(this.rightArm);
			this.leftPants.copyFrom(this.leftLeg);
			this.rightPants.copyFrom(this.rightLeg);
			this.jacket.copyFrom(this.body);
		}
	}
}
