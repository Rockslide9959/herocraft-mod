package com.projecthero.mod.client.undead;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.projecthero.mod.undead.data.UndeadState;
import com.projecthero.mod.undead.entity.UndeadCloneEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import org.joml.Matrix4f;

/**
 * v0.17.3: the Division Bullet's clone (user: "clones just look exactly the same as the player themself"): a player model in the
 * owner's own skin -- wide or slim, hat and jacket layers and all -- untinted and solid, walking, looking and swinging like a
 * player. Remembers its pose so it bursts into pixels from where it stood.
 */
public class UndeadCloneRenderer extends EntityRenderer<UndeadCloneEntity> {
	record Last(Matrix4f[] parts, ResourceLocation tex, boolean slim) {
	}

	static final Map<Integer, Last> LAST = new ConcurrentHashMap<>();

	private final PlayerModel<UndeadCloneEntity> wide;
	private final PlayerModel<UndeadCloneEntity> slimModel;

	public UndeadCloneRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.shadowRadius = 0.5f;
		this.wide = new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false);
		this.slimModel = new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER_SLIM), true);
	}

	/** The owner's skin (his default skin while he is out of sight). */
	static PlayerSkin skinOf(UndeadCloneEntity e) {
		UUID owner = e.getOwnerUUID();
		if (owner != null && Minecraft.getInstance().level != null && Minecraft.getInstance().level.getPlayerByUUID(owner) instanceof AbstractClientPlayer p) {
			return p.getSkin();
		}
		return DefaultPlayerSkin.get(owner != null ? owner : e.getUUID());
	}

	@Override
	public void render(UndeadCloneEntity e, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
		PlayerSkin skin = skinOf(e);
		boolean slim = skin.model() == PlayerSkin.Model.SLIM;
		ResourceLocation tex = skin.texture();
		PlayerModel<UndeadCloneEntity> model = slim ? this.slimModel : this.wide;
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
		model.crouching = false;
		float speed = e.walkAnimation.speed(partial);
		float pos = e.walkAnimation.position(partial);
		model.prepareMobModel(e, pos, speed, partial);
		model.setupAnim(e, pos, speed, e.tickCount + partial, headYaw, pitch);
		int overlay = LivingEntityRenderer.getOverlayCoords(e, 0f);
		// translucent like the vanilla player's own layer pass, so the hat / jacket layers' clear texels stay clear
		model.renderToBuffer(pose, buffers.getBuffer(RenderType.entityTranslucent(tex)), light, overlay, 0xFFFFFFFF);
		Vec3 cam = this.entityRenderDispatcher.camera.getPosition();
		Matrix4f[] parts = new Matrix4f[UndeadState.PARTS];
		for (int i = 0; i < UndeadState.PARTS; i++) {
			pose.pushPose();
			UndeadRender.part(model, i).translateAndRotate(pose);
			parts[i] = new Matrix4f().translation((float) cam.x, (float) cam.y, (float) cam.z).mul(pose.last().pose());
			pose.popPose();
		}
		LAST.put(e.getId(), new Last(parts, tex, slim));
		pose.popPose();
		super.render(e, yaw, partial, pose, buffers, light);
	}

	@Override
	public ResourceLocation getTextureLocation(UndeadCloneEntity e) {
		return skinOf(e).texture();
	}

	/** Gone: the whole self bursts into pixels. */
	static void removed(UndeadCloneEntity e) {
		Last last = LAST.remove(e.getId());
		if (last == null) {
			return;
		}
		for (int i = 0; i < UndeadState.PARTS; i++) {
			PixelCubes.burst(last.tex(), last.slim(), i, last.parts()[i], Vec3.ZERO, 0xFFFFFFFF, 0.9f);
		}
	}

	static void forgetAll() {
		LAST.clear();
	}
}
