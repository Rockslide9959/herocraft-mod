package com.projecthero.mod.client.speedforce;

import java.util.Optional;
import java.util.UUID;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.projecthero.mod.speedforce.entity.SpeedForceEchoEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * v0.17.1 (user, Speed Force Memories): an echo -- a pale, see-through copy of the runner in their own skin (wide or slim
 * arms to match), mid-stride, replaying one of their runs up the road. Caught (run through), it fades to a ghost of a ghost.
 */
public class SpeedForceEchoRenderer extends EntityRenderer<SpeedForceEchoEntity> {
	private final PlayerModel<AbstractClientPlayer> wide;
	private final PlayerModel<AbstractClientPlayer> slim;

	public SpeedForceEchoRenderer(EntityRendererProvider.Context ctx) {
		super(ctx);
		this.wide = new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER), false);
		this.slim = new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER_SLIM), true);
		this.shadowRadius = 0f;
	}

	private static PlayerSkin skinOf(SpeedForceEchoEntity e) {
		Optional<UUID> id = e.owner();
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
	public void render(SpeedForceEchoEntity e, float yaw, float pt, PoseStack pose, MultiBufferSource buffers, int light) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) {
			return;
		}
		PlayerSkin skin = skinOf(e);
		PlayerModel<AbstractClientPlayer> model = skin.model() == PlayerSkin.Model.SLIM ? slim : wide;
		float bodyYaw = Mth.rotLerp(pt, e.yRotO, e.getYRot());
		double moved = Math.sqrt((e.getX() - e.xo) * (e.getX() - e.xo) + (e.getZ() - e.zo) * (e.getZ() - e.zo));
		float time = e.tickCount + pt;
		float limb = time * 0.9f;
		float limbSpeed = (float) Math.min(1.0, moved * 2.0);
		float alpha = e.spent() ? 0.1f : 0.24f + 0.05f * Mth.sin(time * 0.3f);
		pose.pushPose();
		pose.mulPose(Axis.YP.rotationDegrees(180.0f - bodyYaw));
		pose.scale(-1.0f, -1.0f, 1.0f);
		pose.scale(0.9375f, 0.9375f, 0.9375f);
		pose.translate(0.0f, -1.501f, 0.0f);
		model.attackTime = 0f;
		model.riding = false;
		model.young = false;
		model.crouching = false;
		model.setupAnim(mc.player, limb, limbSpeed, time, 0f, 0f);
		// a run's lean: the body forward, the arms pumping
		model.body.xRot = 0.25f * limbSpeed;
		model.jacket.copyFrom(model.body);
		int a = Math.round(alpha * 255f);
		model.renderToBuffer(pose, buffers.getBuffer(RenderType.entityTranslucent(skin.texture())), LightTexture.FULL_BRIGHT,
				OverlayTexture.NO_OVERLAY, (a << 24) | 0xFFF0C8);
		pose.popPose();
		super.render(e, yaw, pt, pose, buffers, light);
	}

	@Override
	public ResourceLocation getTextureLocation(SpeedForceEchoEntity e) {
		return skinOf(e).texture();
	}
}
