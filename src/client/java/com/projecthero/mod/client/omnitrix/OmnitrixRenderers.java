package com.projecthero.mod.client.omnitrix;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.omnitrix.entity.AlienProjectileEntity;
import com.projecthero.mod.omnitrix.entity.OmnitrixMeteorEntity;
import com.projecthero.mod.omnitrix.entity.OmnitrixPodEntity;
import com.projecthero.mod.omnitrix.pod.OmnitrixPodBlockEntity;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** v0.17.1: the Omnitrix's renderers -- the falling meteor, the pod in its crater, the alien projectile, the Containment Pod's field. */
public final class OmnitrixRenderers {
	static final ResourceLocation POD_TEX = ProjectHeroMod.id("textures/entity/omnitrix/pod.png");
	static final ResourceLocation FIELD_TEX = ProjectHeroMod.id("textures/entity/omnitrix/pod_field.png");
	static final ResourceLocation GLOW_TEX = ProjectHeroMod.id("textures/entity/omnitrix/glow.png");

	private OmnitrixRenderers() {
	}

	/** The capsule: a heavy base, a glowing core with the watch, and two clamshell halves that swing open. Y is up, pixels. */
	static final class PodModel {
		final ModelPart base;
		final ModelPart core;
		final ModelPart halfA;
		final ModelPart halfB;
		final ModelPart rim;

		PodModel() {
			ModelPart root = AlienParts.bake(64, 64, r -> {
				r.addOrReplaceChild("base", CubeListBuilder.create().texOffs(0, 0).addBox(-7, 0, -7, 14, 5, 14), PartPose.ZERO);
				r.addOrReplaceChild("rim", CubeListBuilder.create().texOffs(0, 28).addBox(-7.5f, 4.5f, -7.5f, 15, 1, 15), PartPose.ZERO);
				r.addOrReplaceChild("core", CubeListBuilder.create().texOffs(40, 40).addBox(-3, 5, -3, 6, 5, 6), PartPose.ZERO);
				// each half hinges on its outer bottom edge
				r.addOrReplaceChild("half_a", CubeListBuilder.create().texOffs(0, 36).addBox(-7, 0, -7, 14, 9, 7), PartPose.offset(0, 5, 0));
				r.addOrReplaceChild("half_b", CubeListBuilder.create().texOffs(0, 36).addBox(-7, 0, 0, 14, 9, 7), PartPose.offset(0, 5, 0));
			});
			base = root.getChild("base");
			rim = root.getChild("rim");
			core = root.getChild("core");
			halfA = root.getChild("half_a");
			halfB = root.getChild("half_b");
		}

		void render(PoseStack pose, MultiBufferSource buffers, int light, float open, boolean empty, boolean glowAll) {
			VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(POD_TEX));
			pose.pushPose();
			pose.scale(1.5f, 1.5f, 1.5f);
			base.render(pose, vc, light, OverlayTexture.NO_OVERLAY);
			rim.render(pose, vc, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
			// the halves swing out about their outer bottom edges
			pose.pushPose();
			pose.translate(0, 5f / 16f, -7f / 16f);
			pose.mulPose(Axis.XP.rotation(-open * 1.9f));
			pose.translate(0, -5f / 16f, 7f / 16f);
			halfA.render(pose, vc, light, OverlayTexture.NO_OVERLAY);
			pose.popPose();
			pose.pushPose();
			pose.translate(0, 5f / 16f, 7f / 16f);
			pose.mulPose(Axis.XP.rotation(open * 1.9f));
			pose.translate(0, -5f / 16f, -7f / 16f);
			halfB.render(pose, vc, light, OverlayTexture.NO_OVERLAY);
			pose.popPose();
			if (!empty || glowAll) {
				core.render(pose, buffers.getBuffer(RenderType.eyes(POD_TEX)), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
			}
			pose.popPose();
		}
	}

	static void glow(PoseStack pose, MultiBufferSource buffers, EntityRenderDispatcherView view, float size, int rgb, int alpha) {
		pose.pushPose();
		pose.mulPose(view.rotation());
		VertexConsumer vc = buffers.getBuffer(RenderType.eyes(GLOW_TEX));
		int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
		int argb = (alpha << 24) | ((r * alpha / 255) << 16) | ((g * alpha / 255) << 8) | (b * alpha / 255);
		AlienParts.quad(vc, pose.last(), argb, LightTexture.FULL_BRIGHT, -size, -size, 0, size, -size, 0, size, size, 0, -size, size, 0, 0, 0, 1, 0, 0, 1, 1);
		pose.popPose();
	}

	/** The camera rotation, so a glow faces the viewer. */
	interface EntityRenderDispatcherView {
		org.joml.Quaternionf rotation();
	}

	static final class Meteor extends EntityRenderer<OmnitrixMeteorEntity> {
		private final PodModel model = new PodModel();

		Meteor(EntityRendererProvider.Context ctx) {
			super(ctx);
		}

		@Override
		public void render(OmnitrixMeteorEntity e, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
			pose.pushPose();
			float spin = (e.tickCount + partial) * 14f;
			pose.translate(0, 0.5, 0);
			glow(pose, buffers, entityRenderDispatcher::cameraOrientation, 2.2f, 0x7CFF6A, 200);
			glow(pose, buffers, entityRenderDispatcher::cameraOrientation, 1.4f, 0xFFB040, 160);
			pose.mulPose(Axis.YP.rotationDegrees(spin));
			pose.mulPose(Axis.XP.rotationDegrees(spin * 0.6f));
			pose.translate(0, -0.45, 0);
			model.render(pose, buffers, LightTexture.FULL_BRIGHT, 0f, false, true);
			pose.popPose();
			super.render(e, yaw, partial, pose, buffers, light);
		}

		@Override
		public ResourceLocation getTextureLocation(OmnitrixMeteorEntity e) {
			return POD_TEX;
		}
	}

	static final class Pod extends EntityRenderer<OmnitrixPodEntity> {
		private final PodModel model = new PodModel();

		Pod(EntityRendererProvider.Context ctx) {
			super(ctx);
			this.shadowRadius = 0.6f;
		}

		@Override
		public void render(OmnitrixPodEntity e, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
			pose.pushPose();
			pose.mulPose(Axis.YP.rotationDegrees(-e.getYRot()));
			float open = e.openAmount(partial);
			open = 1f - (1f - open) * (1f - open);
			model.render(pose, buffers, light, open, e.empty(), false);
			if (!e.empty() && open > 0.2f) {
				// the watch itself (its 3D model) on the core, face up, turning and bobbing slightly
				float bob = Mth.sin((e.tickCount + partial) * 0.12f) * 0.03f;
				pose.pushPose();
				pose.translate(0, 1.15 + bob, 0);
				pose.mulPose(Axis.YP.rotationDegrees((e.tickCount + partial) * 1.5f));
				pose.scale(1.0f, 1.0f, 1.0f);
				OmnitrixItemRenderer.drawCentred(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(OmnitrixWatch.TEX)), light);
				pose.popPose();
				pose.pushPose();
				pose.translate(0, 1.3, 0);
				glow(pose, buffers, entityRenderDispatcher::cameraOrientation, 0.6f + bob * 3, 0x7CFF6A, 120);
				pose.popPose();
			}
			pose.popPose();
			super.render(e, yaw, partial, pose, buffers, light);
		}

		@Override
		public ResourceLocation getTextureLocation(OmnitrixPodEntity e) {
			return POD_TEX;
		}
	}

	static final class Projectile extends EntityRenderer<AlienProjectileEntity> {
		Projectile(EntityRendererProvider.Context ctx) {
			super(ctx);
		}

		@Override
		public void render(AlienProjectileEntity e, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
			pose.pushPose();
			pose.translate(0, e.getBbHeight() * 0.5, 0);
			float s = e.size();
			float flick = 1f + Mth.sin((e.tickCount + partial) * 1.7f) * 0.12f;
			glow(pose, buffers, entityRenderDispatcher::cameraOrientation, s * 1.6f * flick, e.colour(), 230);
			glow(pose, buffers, entityRenderDispatcher::cameraOrientation, s * 0.8f, 0xFFF4C0, 255);
			pose.popPose();
			super.render(e, yaw, partial, pose, buffers, light);
		}

		@Override
		public ResourceLocation getTextureLocation(AlienProjectileEntity e) {
			return GLOW_TEX;
		}
	}

	/** The Containment Pod's energy field: a faint green tube, roaring bright (and red toward the end) while extracting. */
	static final class PodField implements BlockEntityRenderer<OmnitrixPodBlockEntity> {
		PodField(BlockEntityRendererProvider.Context ctx) {
		}

		@Override
		public void render(OmnitrixPodBlockEntity be, float partial, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
			if (be.getLevel() == null) {
				return;
			}
			boolean running = be.ticks() > 0;
			float t = be.getLevel().getGameTime() + partial;
			VertexConsumer vc = buffers.getBuffer(RenderType.eyes(FIELD_TEX));
			float r = 0.43f;
			float y0 = 2f / 16f, y1 = 1.88f;
			float scroll = (t * (running ? 0.08f : 0.015f)) % 1f;
			int a = running ? 130 : 60;
			// from inside the tube (the wearer) the field is only a faint shimmer, or it would fill the screen
			var cam = net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
			double dx = cam.x - (be.getBlockPos().getX() + 0.5), dz = cam.z - (be.getBlockPos().getZ() + 0.5);
			if (dx * dx + dz * dz < 0.36 && cam.y > be.getBlockPos().getY() && cam.y < be.getBlockPos().getY() + 2.2) {
				a = 22;
			}
			int col = running && (int) (t / 4) % 2 == 0 ? 0xFFFF6050 : 0xFF7CFF6A;
			int argb = (a << 24) | (((col >> 16) & 0xFF) * a / 255 << 16) | (((col >> 8) & 0xFF) * a / 255 << 8) | ((col & 0xFF) * a / 255);
			PoseStack.Pose p = pose.last();
			float c = 0.5f;
			for (int k = 0; k < 8; k++) {
				double a0 = k * Math.PI / 4, a1 = (k + 1) * Math.PI / 4;
				float x0 = c + (float) Math.cos(a0) * r, z0 = c + (float) Math.sin(a0) * r;
				float x1 = c + (float) Math.cos(a1) * r, z1 = c + (float) Math.sin(a1) * r;
				AlienParts.quad(vc, p, argb, LightTexture.FULL_BRIGHT, x0, y0, z0, x1, y0, z1, x1, y1, z1, x0, y1, z0, 0, 0, 1, k / 8f, scroll + 2f,
						(k + 1) / 8f, scroll);
			}
			if (running) {
				// the pulse rings racing up the tube
				for (int ring = 0; ring < 3; ring++) {
					float ry = y0 + ((t * 0.05f + ring / 3f) % 1f) * (y1 - y0);
					for (int k = 0; k < 8; k++) {
						double a0 = k * Math.PI / 4, a1 = (k + 1) * Math.PI / 4;
						float rr = r + 0.04f;
						AlienParts.quad(vc, p, 0xFFFFFFFF, LightTexture.FULL_BRIGHT, c + (float) Math.cos(a0) * rr, ry, c + (float) Math.sin(a0) * rr,
								c + (float) Math.cos(a1) * rr, ry, c + (float) Math.sin(a1) * rr, c + (float) Math.cos(a1) * rr, ry + 0.06f,
								c + (float) Math.sin(a1) * rr, c + (float) Math.cos(a0) * rr, ry + 0.06f, c + (float) Math.sin(a0) * rr, 0, 0, 1, 0, 0, 1, 0.2f);
					}
				}
			}
		}

		@Override
		public boolean shouldRenderOffScreen(OmnitrixPodBlockEntity be) {
			return true;
		}
	}
}
