package com.projecthero.mod.client.carnage;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.carnage.CarnageEntityTypes;
import com.projecthero.mod.carnage.entity.CarnageEntity;
import com.projecthero.mod.carnage.entity.CrimsonMeteorEntity;

import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;

/**
 * v0.14.25: Carnage and his brood -- the slim player model in his Skindex skin ("carnage" by TheTickanatorDSSX), posed
 * from his synced action, with a crimson blade grown out of each forearm (drawn with the crimson tendril texture), and
 * the meteor he rides down in (a tumbling magma block; the trail is particles).
 */
public final class CarnageRenderer {
	static final ResourceLocation SKIN = ProjectHeroMod.id("textures/entity/carnage/carnage.png");
	static final ResourceLocation BLADE = ProjectHeroMod.id("textures/entity/symbiote_tendril_crimson.png");

	private CarnageRenderer() {
	}

	public static void initialize() {
		EntityRendererRegistry.register(CarnageEntityTypes.CARNAGE, Body::new);
		EntityRendererRegistry.register(CarnageEntityTypes.CRIMSON_SPAWN, Body::new);
		EntityRendererRegistry.register(CarnageEntityTypes.CRIMSON_METEOR, Meteor::new);
	}

	/** Carnage or one of his brood (the brood is the same model, made small by its SCALE attribute). */
	static final class Body extends HumanoidMobRenderer<Mob, Model> {
		Body(EntityRendererProvider.Context ctx) {
			super(ctx, new Model(ctx.bakeLayer(ModelLayers.PLAYER_SLIM)), 0.5f);
			addLayer(new Blades(this));
		}

		@Override
		public ResourceLocation getTextureLocation(Mob entity) {
			return SKIN;
		}

		@Override
		protected void scale(Mob entity, PoseStack pose, float partialTick) {
			super.scale(entity, pose, partialTick);
			pose.scale(0.9375f, 0.9375f, 0.9375f);
		}
	}

	/** The player model posed from {@link CarnageEntity#action}; the brood just runs with its arms out. */
	static final class Model extends PlayerModel<Mob> {
		Model(ModelPart root) {
			super(root, true);
		}

		@Override
		public void setupAnim(Mob e, float limbSwing, float limbSwingAmount, float age, float headYaw, float headPitch) {
			byte action = e instanceof CarnageEntity c ? c.action() : CarnageEntity.ACTION_NONE;
			this.crouching = action == CarnageEntity.ACTION_COCOON || action == CarnageEntity.ACTION_POUNCE;
			super.setupAnim(e, limbSwing, limbSwingAmount, age, headYaw, headPitch);
			if (!(e instanceof CarnageEntity)) {
				// the brood: a hunched, arms-forward scuttle
				rightArm.xRot = -1.2f + Mth.cos(limbSwing * 0.9f) * 0.5f * limbSwingAmount;
				leftArm.xRot = -1.2f - Mth.cos(limbSwing * 0.9f) * 0.5f * limbSwingAmount;
			} else {
				// a constant, slightly unhinged sway
				body.zRot = Mth.sin(age * 0.11f) * 0.05f;
				head.zRot += Mth.sin(age * 0.17f) * 0.12f;
				switch (action) {
					case CarnageEntity.ACTION_CLAW -> {
						float s = Mth.sin(age * 0.9f);
						rightArm.xRot = -1.9f + s * 1.1f;
						leftArm.xRot = -1.9f - s * 1.1f;
						rightArm.yRot = -0.3f;
						leftArm.yRot = 0.3f;
					}
					case CarnageEntity.ACTION_POUNCE -> {
						rightArm.xRot = -2.6f;
						leftArm.xRot = -2.6f;
						rightArm.zRot = 0.5f;
						leftArm.zRot = -0.5f;
					}
					case CarnageEntity.ACTION_LASH -> {
						rightArm.zRot = 1.6f;
						leftArm.zRot = -1.6f;
						rightArm.xRot = -0.3f;
						leftArm.xRot = -0.3f;
						body.xRot = -0.2f;
					}
					case CarnageEntity.ACTION_SPIKES -> {
						rightArm.xRot = -Mth.HALF_PI + head.xRot;
						leftArm.xRot = -Mth.HALF_PI + head.xRot;
						rightArm.yRot = -0.2f;
						leftArm.yRot = 0.2f;
					}
					case CarnageEntity.ACTION_COCOON -> {
						rightArm.xRot = -0.9f;
						leftArm.xRot = -0.9f;
						rightArm.yRot = 0.9f;
						leftArm.yRot = -0.9f;
						head.xRot = 0.9f;
					}
					case CarnageEntity.ACTION_EMERGE -> {
						rightArm.xRot = -2.9f;
						leftArm.xRot = -2.9f;
						rightArm.zRot = 0.25f + Mth.sin(age * 0.6f) * 0.2f;
						leftArm.zRot = -0.25f - Mth.sin(age * 0.6f) * 0.2f;
					}
					case CarnageEntity.ACTION_WRITHE -> {
						rightArm.xRot = -2.7f;
						leftArm.xRot = -2.7f;
						rightArm.zRot = -0.5f;
						leftArm.zRot = 0.5f;
						head.zRot = Mth.sin(age * 1.3f) * 0.4f;
						body.zRot = Mth.sin(age * 0.9f) * 0.15f;
					}
					default -> {
					}
				}
			}
			leftSleeve.copyFrom(leftArm);
			rightSleeve.copyFrom(rightArm);
			leftPants.copyFrom(leftLeg);
			rightPants.copyFrom(rightLeg);
			jacket.copyFrom(body);
			hat.copyFrom(head);
		}
	}

	/** A curved crimson blade along each forearm, past the fist. Carnage only (the brood has bare claws). */
	static final class Blades extends RenderLayer<Mob, Model> {
		Blades(RenderLayerParent<Mob, Model> parent) {
			super(parent);
		}

		@Override
		public void render(PoseStack pose, MultiBufferSource buffers, int light, Mob e, float limbSwing, float limbSwingAmount,
				float partialTick, float age, float headYaw, float headPitch) {
			if (!(e instanceof CarnageEntity c)) {
				return;
			}
			VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(BLADE));
			if (c.action() == CarnageEntity.ACTION_COCOON) {
				cocoon(pose, vc, light, age);
				return;
			}
			blade(pose, vc, light, getParentModel().rightArm, 1f, age);
			blade(pose, vc, light, getParentModel().leftArm, -1f, age);
		}

		/** The split cocoon: ten pulsing ribbons of crimson goo wrapped round him from crown to feet (model space: y down, feet at 1.5). */
		private static void cocoon(PoseStack pose, VertexConsumer vc, int light, float age) {
			PoseStack.Pose last = pose.last();
			int ribbons = 10;
			int steps = 8;
			float pulse = 1.0f + Mth.sin(age * 0.3f) * 0.06f;
			for (int r = 0; r < ribbons; r++) {
				float a0 = (float) (r * Math.PI * 2 / ribbons) + age * 0.01f;
				for (int i = 0; i < steps; i++) {
					float t0 = i / (float) steps, t1 = (i + 1) / (float) steps;
					float y0 = -0.35f + t0 * 1.85f, y1 = -0.35f + t1 * 1.85f;
					float r0 = (0.18f + 0.62f * Mth.sin(t0 * Mth.PI)) * pulse, r1 = (0.18f + 0.62f * Mth.sin(t1 * Mth.PI)) * pulse;
					// each ribbon twists a little round the body as it goes down
					float b0 = a0 + t0 * 0.9f, b1 = a0 + t1 * 0.9f;
					float w = 0.32f;
					quad(vc, last, light,
							Mth.cos(b0 - w) * r0, y0, Mth.sin(b0 - w) * r0, Mth.cos(b1 - w) * r1, y1, Mth.sin(b1 - w) * r1,
							Mth.cos(b1 + w) * r1, y1, Mth.sin(b1 + w) * r1, Mth.cos(b0 + w) * r0, y0, Mth.sin(b0 + w) * r0,
							t0, t1);
				}
			}
		}

		private static void blade(PoseStack pose, VertexConsumer vc, int light, ModelPart arm, float side, float age) {
			pose.pushPose();
			arm.translateAndRotate(pose);
			// model units: the arm hangs down +y, 12 px long; the blade starts at the elbow on the outer side
			float flex = Mth.sin(age * 0.25f) * 0.02f;
			float x0 = side * 0.09f;
			float[][] spine = { { 0.30f, 0.0f }, { 0.55f, 0.07f }, { 0.80f, 0.16f }, { 1.02f, 0.30f + flex }, { 1.16f, 0.48f + flex } };
			float[] widths = { 0.05f, 0.09f, 0.10f, 0.07f, 0.0f };
			PoseStack.Pose last = pose.last();
			for (int i = 0; i < spine.length - 1; i++) {
				float ya = spine[i][0], yb = spine[i + 1][0];
				float oa = spine[i][1], ob = spine[i + 1][1];
				float wa = widths[i], wb = widths[i + 1];
				// the blade is flat in the arm's x/y plane, curving outward (x) and backward (z) toward its tip
				quad(vc, last, light,
						x0 + side * oa, ya, -wa, x0 + side * ob, yb, -wb,
						x0 + side * ob, yb, wb, x0 + side * oa, ya, wa,
						i / 4f, (i + 1) / 4f);
				quad(vc, last, light,
						x0 + side * (oa + wa * 0.6f), ya, 0, x0 + side * (ob + wb * 0.6f), yb, 0,
						x0 + side * ob, yb, 0, x0 + side * oa, ya, 0,
						i / 4f, (i + 1) / 4f);
			}
			pose.popPose();
		}

		private static void quad(VertexConsumer vc, PoseStack.Pose pose, int light, float ax, float ay, float az, float bx, float by,
				float bz, float cx, float cy, float cz, float dx, float dy, float dz, float v0, float v1) {
			vertex(vc, pose, light, ax, ay, az, 0f, v0);
			vertex(vc, pose, light, bx, by, bz, 0f, v1);
			vertex(vc, pose, light, cx, cy, cz, 1f, v1);
			vertex(vc, pose, light, dx, dy, dz, 1f, v0);
		}

		private static void vertex(VertexConsumer vc, PoseStack.Pose pose, int light, float x, float y, float z, float u, float v) {
			vc.addVertex(pose, x, y, z).setColor(255, 255, 255, 255).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
					.setNormal(pose, 0f, 0f, 1f);
		}
	}

	/** A tumbling magma block, glowing at full brightness. */
	static final class Meteor extends EntityRenderer<CrimsonMeteorEntity> {
		private final net.minecraft.client.renderer.block.BlockRenderDispatcher blocks;

		Meteor(EntityRendererProvider.Context ctx) {
			super(ctx);
			this.blocks = ctx.getBlockRenderDispatcher();
		}

		@Override
		public void render(CrimsonMeteorEntity e, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
			pose.pushPose();
			float spin = (e.tickCount + partialTick) * 18f;
			pose.mulPose(Axis.YP.rotationDegrees(spin));
			pose.mulPose(Axis.XP.rotationDegrees(spin * 0.7f));
			pose.scale(1.6f, 1.6f, 1.6f);
			pose.translate(-0.5, -0.5, -0.5);
			blocks.renderSingleBlock(Blocks.MAGMA_BLOCK.defaultBlockState(), pose, buffers, 0xF000F0, OverlayTexture.NO_OVERLAY);
			pose.popPose();
			super.render(e, yaw, partialTick, pose, buffers, light);
		}

		@Override
		@SuppressWarnings("deprecation")
		public ResourceLocation getTextureLocation(CrimsonMeteorEntity e) {
			return TextureAtlas.LOCATION_BLOCKS;
		}
	}
}
