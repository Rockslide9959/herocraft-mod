package com.projecthero.mod.client.andy;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.andy.data.AndyState;
import com.projecthero.mod.andy.entity.AndyCloneEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import org.joml.Matrix4f;

/**
 * v0.17.3: one of Andy's split-off selves (Dead Quardead, Division Bullet): a player model in the owner's own skin, tinted
 * fleshy (skin), red (blood) or drawn in bone (bones), a little see-through. Remembers its pose so it bursts from where it
 * stood.
 */
public class AndyCloneRenderer extends EntityRenderer<AndyCloneEntity> {
	public static final ResourceLocation BONE = ProjectHeroMod.id("textures/entity/andy/bone.png");

	record Last(Matrix4f[] parts, ResourceLocation tex, boolean slim, int tint) {
	}

	static final Map<Integer, Last> LAST = new ConcurrentHashMap<>();

	private final PlayerModel<AndyCloneEntity> wide;
	private final PlayerModel<AndyCloneEntity> slimModel;

	public AndyCloneRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.shadowRadius = 0.5f;
		this.wide = new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false);
		this.slimModel = new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER_SLIM), true);
	}

	static PlayerSkin skinOf(AndyCloneEntity e) {
		UUID owner = e.getOwnerUUID();
		if (owner != null && Minecraft.getInstance().level != null && Minecraft.getInstance().level.getPlayerByUUID(owner) instanceof AbstractClientPlayer p) {
			return p.getSkin();
		}
		return DefaultPlayerSkin.get(owner != null ? owner : e.getUUID());
	}

	static int tint(AndyCloneEntity e) {
		return switch (e.tint()) {
			case AndyCloneEntity.TINT_BLOOD -> 0xE8E83A3A;
			case AndyCloneEntity.TINT_BONE -> 0xF0FFFFFF;
			default -> 0xE0FFD9C9;
		};
	}

	@Override
	public void render(AndyCloneEntity e, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
		PlayerSkin skin = skinOf(e);
		boolean slim = skin.model() == PlayerSkin.Model.SLIM;
		ResourceLocation tex = e.tint() == AndyCloneEntity.TINT_BONE ? BONE : skin.texture();
		PlayerModel<AndyCloneEntity> model = slim ? this.slimModel : this.wide;
		pose.pushPose();
		float bodyYaw = Mth.rotLerp(partial, e.yBodyRotO, e.yBodyRot);
		float headYaw = Mth.rotLerp(partial, e.yHeadRotO, e.yHeadRot) - bodyYaw;
		float pitch = Mth.lerp(partial, e.xRotO, e.getXRot());
		pose.mulPose(Axis.YP.rotationDegrees(180f - bodyYaw));
		pose.scale(-1f, -1f, 1f);
		pose.scale(0.9375f, 0.9375f, 0.9375f);
		pose.translate(0f, -1.501f, 0f);
		model.setAllVisible(true);
		model.attackTime = e.getAttackAnim(partial);
		model.riding = false;
		model.young = false;
		float speed = e.walkAnimation.speed(partial);
		float pos = e.walkAnimation.position(partial);
		model.prepareMobModel(e, pos, speed, partial);
		model.setupAnim(e, pos, speed, e.tickCount + partial, headYaw, pitch);
		int overlay = LivingEntityRenderer.getOverlayCoords(e, 0f);
		model.renderToBuffer(pose, buffers.getBuffer(RenderType.entityTranslucent(tex)), light, overlay, tint(e));
		Vec3 cam = this.entityRenderDispatcher.camera.getPosition();
		Matrix4f[] parts = new Matrix4f[AndyState.PARTS];
		for (int i = 0; i < AndyState.PARTS; i++) {
			pose.pushPose();
			AndyRender.part(model, i).translateAndRotate(pose);
			parts[i] = new Matrix4f().translation((float) cam.x, (float) cam.y, (float) cam.z).mul(pose.last().pose());
			pose.popPose();
		}
		LAST.put(e.getId(), new Last(parts, tex, slim, tint(e) | 0xFF000000));
		pose.popPose();
		super.render(e, yaw, partial, pose, buffers, light);
	}

	@Override
	public ResourceLocation getTextureLocation(AndyCloneEntity e) {
		return skinOf(e).texture();
	}

	/** Gone: the whole self bursts into pixels. */
	static void removed(AndyCloneEntity e) {
		Last last = LAST.remove(e.getId());
		if (last == null) {
			return;
		}
		for (int i = 0; i < AndyState.PARTS; i++) {
			PixelCubes.burst(last.tex(), last.slim(), i, last.parts()[i], Vec3.ZERO, last.tint(), 0.9f);
		}
	}

	static void forgetAll() {
		LAST.clear();
	}

	static boolean known(Player p) {
		return p != null;
	}
}
