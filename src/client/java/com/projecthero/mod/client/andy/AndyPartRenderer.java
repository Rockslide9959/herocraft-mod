package com.projecthero.mod.client.andy;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projecthero.mod.andy.data.AndyState;
import com.projecthero.mod.andy.entity.AndyPartEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import org.joml.Matrix4f;

/**
 * v0.17.3: a launched piece of Andy, in its owner's own skin: the twisted arm spinning fist-first like a drill, the head
 * tumbling (card and all), or a fingertip. Remembers where it last drew the piece so it can burst from exactly there.
 */
public class AndyPartRenderer extends EntityRenderer<AndyPartEntity> {
	/** Entity id -> the piece's own space (blocks) in the world, last frame; plus skin and model for the burst. */
	record Last(Matrix4f matrix, ResourceLocation tex, boolean slim, int part) {
	}

	static final Map<Integer, Last> LAST = new ConcurrentHashMap<>();

	private final PlayerModel<Player> wide;
	private final PlayerModel<Player> slimModel;

	public AndyPartRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.shadowRadius = 0.15f;
		this.wide = new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false);
		this.slimModel = new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER_SLIM), true);
	}

	static Player owner(AndyPartEntity e) {
		Entity o = e.getOwner();
		return o instanceof Player p ? p : null;
	}

	@Override
	public void render(AndyPartEntity e, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
		Player owner = owner(e);
		ResourceLocation tex = owner != null ? AndyRender.skin(owner) : net.minecraft.client.resources.DefaultPlayerSkin.getDefaultTexture();
		boolean slim = owner != null && AndyRender.slim(owner);
		float age = e.tickCount + partial;
		Vec3 cam = this.entityRenderDispatcher.camera.getPosition();
		pose.pushPose();
		float ry = Mth.rotLerp(partial, e.yRotO, e.getYRot());
		float rx = Mth.lerp(partial, e.xRotO, e.getXRot());
		pose.translate(0, e.getBbHeight() * 0.5, 0);
		pose.mulPose(Axis.YP.rotationDegrees(ry));
		pose.mulPose(Axis.XP.rotationDegrees(-rx));
		if (e.isFinger()) {
			finger(e, pose, buffers, light, tex, slim, age);
			pose.popPose();
			return;
		}
		PlayerModel<Player> model = slim ? this.slimModel : this.wide;
		int part = e.bodyPart();
		if (e.style() == AndyPartEntity.STYLE_DRILL) {
			// fist first along the flight, spinning about it
			if (!e.stuck()) {
				pose.mulPose(Axis.ZP.rotationDegrees(age * 47f));
			}
			pose.mulPose(Axis.XP.rotationDegrees(-90f));
			pose.scale(-1f, -1f, 1f);
			pose.scale(0.9375f, 0.9375f, 0.9375f);
			float[] b = AndyTexels.box(part, slim);
			pose.translate(-(b[0] + b[3] * 0.5f) / 16f, -(b[1] + b[4] * 0.5f) / 16f, 0f);
		} else {
			// the head: tumbling while it flies, still once it lies
			if (e.getDeltaMovement().lengthSqr() > 0.01) {
				pose.mulPose(Axis.XP.rotationDegrees(age * 24f));
			}
			pose.scale(-1f, -1f, 1f);
			pose.scale(0.9375f, 0.9375f, 0.9375f);
			pose.translate(0f, 4f / 16f, 0f);
		}
		ModelPart base = AndyRender.part(model, part);
		ModelPart over = AndyRender.overlay(model, part);
		base.resetPose();
		over.resetPose();
		base.x = base.y = base.z = 0f;
		over.x = over.y = over.z = 0f;
		base.visible = true;
		over.visible = true;
		VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucent(tex));
		base.render(pose, vc, light, OverlayTexture.NO_OVERLAY);
		over.render(pose, vc, light, OverlayTexture.NO_OVERLAY);
		if (part == AndyState.HEAD) {
			AndyRender.card(pose, buffers, light, 0f);
		}
		LAST.put(e.getId(), new Last(new Matrix4f().translation((float) cam.x, (float) cam.y, (float) cam.z).mul(pose.last().pose()), tex, slim, part));
		pose.popPose();
		super.render(e, yaw, partial, pose, buffers, light);
	}

	/** A fingertip: a 1x1x2.5 px stub of the hand's skin (red-hot for the Crimson Bullet). */
	private void finger(AndyPartEntity e, PoseStack pose, MultiBufferSource buffers, int light, ResourceLocation tex, boolean slim, float age) {
		int u = slim ? 48 : 49, v = 18;
		VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(tex));
		float s = e.style() == AndyPartEntity.STYLE_CRIMSON ? 1.8f : 1f;
		pose.mulPose(Axis.ZP.rotationDegrees(age * 30f));
		org.joml.Vector3f[] c = new org.joml.Vector3f[8];
		for (int i = 0; i < 8; i++) {
			c[i] = new org.joml.Vector3f(((i & 1) == 0 ? -0.6f : 0.6f) * s / 16f, ((i & 2) == 0 ? -0.6f : 0.6f) * s / 16f, ((i & 4) == 0 ? -1.4f : 1.4f) * s / 16f);
		}
		AndyRender.cubePublic(vc, pose.last(), c, (u + 0.1f) / 64f, (v + 0.1f) / 64f, (u + 0.9f) / 64f, (v + 0.9f) / 64f, light);
		if (e.style() != AndyPartEntity.STYLE_FINGER) {
			// a red glow round the bullet
			VertexConsumer glow = buffers.getBuffer(RenderType.lightning());
			float r = (e.style() == AndyPartEntity.STYLE_CRIMSON ? 3.2f : 1.6f) / 16f;
			float len = r * 2.4f;
			Matrix4f m = pose.last().pose();
			for (int k = 0; k < 3; k++) {
				float a = k * Mth.PI / 3f;
				float cx = Mth.cos(a) * r, cy = Mth.sin(a) * r;
				glowQuad(glow, m, -cx, -cy, cx, cy, len, 0.95f, 0.1f, 0.1f, 0.55f);
			}
		}
	}

	private static void glowQuad(VertexConsumer vc, Matrix4f m, float x0, float y0, float x1, float y1, float len, float r, float g, float b, float a) {
		vc.addVertex(m, x0, y0, -len).setColor(r, g, b, 0f);
		vc.addVertex(m, x1, y1, -len).setColor(r, g, b, 0f);
		vc.addVertex(m, x1, y1, len * 0.3f).setColor(r, g, b, a);
		vc.addVertex(m, x0, y0, len * 0.3f).setColor(r, g, b, a);
		vc.addVertex(m, x0, y0, len * 0.3f).setColor(r, g, b, a);
		vc.addVertex(m, x1, y1, len * 0.3f).setColor(r, g, b, a);
		vc.addVertex(m, x1, y1, -len).setColor(r, g, b, 0f);
		vc.addVertex(m, x0, y0, -len).setColor(r, g, b, 0f);
	}

	@Override
	@SuppressWarnings("deprecation")
	public ResourceLocation getTextureLocation(AndyPartEntity e) {
		Player owner = owner(e);
		return owner instanceof AbstractClientPlayer acp ? acp.getSkin().texture() : net.minecraft.client.resources.DefaultPlayerSkin.getDefaultTexture();
	}

	/** The piece is gone: a launched arm or head bursts into pixels where it was last drawn; a finger sprinkles a few. */
	static void removed(AndyPartEntity e) {
		Last last = LAST.remove(e.getId());
		Player owner = owner(e);
		ResourceLocation tex = owner != null ? AndyRender.skin(owner) : net.minecraft.client.resources.DefaultPlayerSkin.getDefaultTexture();
		if (e.isFinger()) {
			PixelCubes.sprinkle(tex, owner != null && AndyRender.slim(owner) ? 48 : 49, 18, e.position(), e.style() == AndyPartEntity.STYLE_CRIMSON ? 8 : 4,
					e.style() == AndyPartEntity.STYLE_FINGER ? 0xFFFFFFFF : 0xFFFF7070);
			return;
		}
		if (last != null && Minecraft.getInstance().level != null) {
			PixelCubes.burst(last.tex(), last.slim(), last.part(), last.matrix(), Vec3.ZERO, 0xFFFFFFFF, 0.8f);
		}
	}
}
