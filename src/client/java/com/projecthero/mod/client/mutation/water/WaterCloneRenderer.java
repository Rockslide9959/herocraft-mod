package com.projecthero.mod.client.mutation.water;

import java.util.Optional;
import java.util.UUID;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.hero.power.p25.WaterCloneEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * v0.16.6 hold V -- the Water Clone: its caster's player model (wide or slim arms to match his skin) drawn as a
 * translucent body of water -- a water skin with caustic streaks, his own skin showing faintly through it washed blue,
 * its limbs swelling and settling in slow ripples -- inside a thin outer film of water that shimmers over it, with drops
 * running down it and a puddle round its feet. It walks, swings and fights with the player model's own animations.
 */
public class WaterCloneRenderer extends HumanoidMobRenderer<WaterCloneEntity, PlayerModel<WaterCloneEntity>> {
	private final PlayerModel<WaterCloneEntity> wide;
	private final PlayerModel<WaterCloneEntity> slim;
	/** The water skin laid over the player-model UVs (v0.16.6, generated: scratchpad/gen_water_clone_v0166.js). */
	private static final ResourceLocation WATER = com.projecthero.mod.ProjectHeroMod.id("textures/entity/mutation/p25_water_clone.png");
	/** This pass: its tint (ARGB), whether it draws the water skin (else the caster's), and whether it washes the skin out. */
	private int tint = 0xB4FFFFFF;
	private boolean water;
	private boolean wash;

	public WaterCloneRenderer(EntityRendererProvider.Context ctx) {
		super(ctx, new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER), false), 0.4f);
		this.wide = this.model;
		this.slim = new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER_SLIM), true);
	}

	private static PlayerSkin skinOf(WaterCloneEntity e) {
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
	public void render(WaterCloneEntity entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
		this.model = skinOf(entity).model() == PlayerSkin.Model.SLIM ? slim : wide;
		float t = entity.tickCount + partialTick;
		int lit = Math.max(light & 0xFFFF, 0xA0) | (light & 0xFFFF0000);
		// the body: water, see-through, each part breathing in its own ripple
		ripple(t, 0.035f);
		water = true;
		wash = false;
		tint = 0x9CFFFFFF;
		super.render(entity, yaw, partialTick, pose, tinted(buffers), lit);
		// the caster's own skin showing faintly through it, washed blue (his face, his clothes)
		pose.pushPose();
		scaleAboutMiddle(pose, entity, 1.012f, 1.004f);
		water = false;
		wash = true;
		tint = 0x9A8CC8FF;
		super.render(entity, yaw, partialTick, pose, tinted(buffers), lit);
		pose.popPose();
		// the outer film of water, a little bigger, shimmering
		pose.pushPose();
		scaleAboutMiddle(pose, entity, 1.08f, 1.035f);
		ripple(t * 1.3f + 2f, 0.05f);
		water = true;
		wash = false;
		tint = WaterMesh.fade(0x70E6F4FF, 0.45f + 0.55f * Mth.sin(t * 0.25f) * Mth.sin(t * 0.25f));
		super.render(entity, yaw, partialTick, pose, tinted(buffers), lit);
		pose.popPose();
		ripple(0f, 0f);
		water = false;
		// drops running down it, and the puddle it stands in
		VertexConsumer vc = WaterMesh.buffer(buffers);
		float body = Mth.rotLerp(partialTick, entity.yBodyRotO, entity.yBodyRot) * Mth.DEG_TO_RAD;
		for (int k = 0; k < 5; k++) {
			float ph = (t * 0.03f + k * 0.21f) % 1f;
			float a = body + k * 1.7f;
			float rr = 0.32f;
			pose.pushPose();
			pose.translate(Mth.cos(a) * rr, 1.75f - ph * 1.75f, Mth.sin(a) * rr);
			WaterMesh.sphere(vc, pose, 0.045f + 0.02f * (k % 2), 1.6f, 0.1f, t, WaterMesh.LIGHT, lit, WaterMesh.still());
			pose.popPose();
		}
		if (entity.onGround()) {
			WaterMesh.disc(vc, pose, 0.62f + 0.05f * Mth.sin(t * 0.3f), 0.03f, t, WaterMesh.fade(WaterMesh.LIGHT, 0.75f), lit, WaterMesh.flow());
			WaterMesh.ring(vc, pose, 0.66f, 0.04f, 18, 0.3f, t * 2f, WaterMesh.fade(WaterMesh.FOAM, 0.6f), lit, WaterMesh.flow());
		}
	}

	private static void scaleAboutMiddle(PoseStack pose, WaterCloneEntity entity, float xz, float y) {
		pose.translate(0, entity.getBbHeight() * 0.5, 0);
		pose.scale(xz, y, xz);
		pose.translate(0, -entity.getBbHeight() * 0.5, 0);
	}

	/** Each body part swells and settles out of step with the others (amount 0 = back to normal). */
	private void ripple(float t, float amount) {
		ModelPart[] parts = { model.head, model.hat, model.body, model.jacket, model.rightArm, model.rightSleeve, model.leftArm,
				model.leftSleeve, model.rightLeg, model.rightPants, model.leftLeg, model.leftPants };
		for (int i = 0; i < parts.length; i++) {
			float k = 1f + amount * Mth.sin(t * 0.21f + (i / 2) * 1.3f);
			float j = 1f + amount * Mth.cos(t * 0.17f + (i / 2) * 0.9f);
			parts[i].xScale = k;
			parts[i].yScale = j;
			parts[i].zScale = k;
		}
	}

	private MultiBufferSource tinted(MultiBufferSource buffers) {
		int c = tint;
		boolean w = wash;
		return type -> new Tinted(buffers.getBuffer(type), c, w);
	}

	@Override
	protected RenderType getRenderType(WaterCloneEntity entity, boolean bodyVisible, boolean translucent, boolean glowing) {
		return RenderType.entityTranslucent(getTextureLocation(entity));
	}

	@Override
	public ResourceLocation getTextureLocation(WaterCloneEntity entity) {
		return water ? WATER : skinOf(entity).texture();
	}

	@Override
	protected boolean shouldShowName(WaterCloneEntity entity) {
		return false;
	}

	/** A vertex sink that multiplies every vertex colour by a tint (skin pixels become water). */
	private static final class Tinted implements VertexConsumer {
		private final VertexConsumer to;
		private final int a, r, g, b;
		private final boolean wash;

		Tinted(VertexConsumer to, int argb, boolean wash) {
			this.to = to;
			this.wash = wash;
			this.a = (argb >>> 24) & 255;
			this.r = (argb >> 16) & 255;
			this.g = (argb >> 8) & 255;
			this.b = argb & 255;
		}

		@Override
		public VertexConsumer addVertex(float x, float y, float z) {
			to.addVertex(x, y, z);
			return this;
		}

		@Override
		public VertexConsumer setColor(int red, int green, int blue, int alpha) {
			int rr = red * r / 255;
			int gg = green * g / 255;
			int bb = blue * b / 255;
			if (wash) {
				// a skin pass: the vertex shade kept faintly, the colour pushed toward the water's
				rr = (red * 70 / 255 + 185) * r / 255;
				gg = (green * 70 / 255 + 185) * g / 255;
				bb = (blue * 70 / 255 + 185) * b / 255;
			}
			to.setColor(Math.min(255, rr), Math.min(255, gg), Math.min(255, bb), alpha * a / 255);
			return this;
		}

		@Override
		public VertexConsumer setUv(float u, float v) {
			to.setUv(u, v);
			return this;
		}

		@Override
		public VertexConsumer setUv1(int u, int v) {
			to.setUv1(u, v);
			return this;
		}

		@Override
		public VertexConsumer setUv2(int u, int v) {
			to.setUv2(u, v);
			return this;
		}

		@Override
		public VertexConsumer setNormal(float x, float y, float z) {
			to.setNormal(x, y, z);
			return this;
		}

		@Override
		public void addVertex(float x, float y, float z, int color, float u, float v, int overlay, int light, float nx, float ny, float nz) {
			addVertex(x, y, z);
			setColor((color >> 16) & 255, (color >> 8) & 255, color & 255, (color >>> 24) & 255);
			setUv(u, v);
			setUv1(overlay & 0xFFFF, overlay >> 16 & 0xFFFF);
			setUv2(light & 0xFFFF, light >> 16 & 0xFFFF);
			setNormal(nx, ny, nz);
		}
	}
}
