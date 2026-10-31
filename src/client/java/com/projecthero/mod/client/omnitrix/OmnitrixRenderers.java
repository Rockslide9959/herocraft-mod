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
	static final ResourceLocation FIELD_TEX = ProjectHeroMod.id("textures/entity/omnitrix/pod_field.png");
	static final ResourceLocation GLOW_TEX = ProjectHeroMod.id("textures/entity/omnitrix/glow.png");

	private OmnitrixRenderers() {
	}

	/**
	 * v0.17.2 (user: "change his ability vfx to be similar to the pyrokinesis power"): a projectile kind's own look, drawn in
	 * place of the shared glowing core ({@code pose} is at the projectile's feet, as an entity renderer's).
	 */
	@FunctionalInterface
	public interface ProjectileLook {
		void render(AlienProjectileEntity e, float partial, PoseStack pose, MultiBufferSource buffers);
	}

	private static final java.util.Map<String, ProjectileLook> PROJECTILE_LOOKS = new java.util.HashMap<>();

	/** v0.17.2: every {@link AlienProjectileEntity} of {@code kind} is drawn by {@code look} (registered once at client init). */
	public static void projectileLook(String kind, ProjectileLook look) {
		PROJECTILE_LOOKS.put(kind, look);
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

	static final ResourceLocation SOFT_GLOW_TEX = ProjectHeroMod.id("textures/entity/omnitrix/pod_soft_glow.png");

	/** v0.17.1: a soft round camera-facing glow (additive), {@code size} its half width in blocks. */
	static void softGlow(PoseStack pose, MultiBufferSource buffers, float size, int rgb, int alpha) {
		pose.pushPose();
		pose.mulPose(net.minecraft.client.Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
		VertexConsumer vc = buffers.getBuffer(RenderType.eyes(SOFT_GLOW_TEX));
		int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
		int argb = (alpha << 24) | ((r * alpha / 255) << 16) | ((g * alpha / 255) << 8) | (b * alpha / 255);
		AlienParts.quad(vc, pose.last(), argb, LightTexture.FULL_BRIGHT, -size, -size, 0, size, -size, 0, size, size, 0, -size, size, 0, 0, 0, 1, 0, 0, 1, 1);
		pose.popPose();
	}

	/** The camera rotation, so a glow faces the viewer. */
	interface EntityRenderDispatcherView {
		org.joml.Quaternionf rotation();
	}

	/**
	 * v0.17.1 (user: "falls from much higher up"): the capsule streaking down -- shut, white-hot at the seams, tumbling inside a
	 * green and orange fireball with a long glowing tail behind it. Not drawn once it has struck (it is only carving the crater).
	 */
	static final class Meteor extends EntityRenderer<OmnitrixMeteorEntity> {
		Meteor(EntityRendererProvider.Context ctx) {
			super(ctx);
		}

		@Override
		public void render(OmnitrixMeteorEntity e, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
			if (e.landed()) {
				return;
			}
			pose.pushPose();
			float spin = (e.tickCount + partial) * 11f;
			pose.translate(0, 0.5, 0);
			// the tail: shrinking glows strung back along the path
			net.minecraft.world.phys.Vec3 v = e.getDeltaMovement();
			for (int k = 10; k >= 1; k--) {
				pose.pushPose();
				pose.translate(-v.x * k * 0.6, -v.y * k * 0.6, -v.z * k * 0.6);
				softGlow(pose, buffers, 3.6f - k * 0.25f, k > 5 ? 0xFF7A20 : 0x8CFF70, 230 - k * 18);
				pose.popPose();
			}
			softGlow(pose, buffers, 5.0f, 0x7CFF6A, 230);
			softGlow(pose, buffers, 3.0f, 0xFFB040, 220);
			softGlow(pose, buffers, 1.6f, 0xFFF6D0, 255);
			pose.mulPose(Axis.YP.rotationDegrees(spin));
			pose.mulPose(Axis.XP.rotationDegrees(spin * 0.6f));
			pose.translate(0, -OmnitrixPodModel.CY, 0);
			OmnitrixPodModel.render(pose, buffers, LightTexture.FULL_BRIGHT, 0f, 1f, 0f);
			pose.popPose();
			super.render(e, yaw, partial, pose, buffers, light);
		}

		@Override
		public ResourceLocation getTextureLocation(OmnitrixMeteorEntity e) {
			return OmnitrixPodModel.TEX;
		}
	}

	/**
	 * v0.17.1 (user: "make the omnitrix capsule look like the omnitrix capsule from the show"): {@link OmnitrixPodModel} -- hot
	 * seams fading after the fall, the lid swinging back on the right-click (eased, with a little overshoot), the cavity's
	 * green light coming up, and the watch in its cradle until it leaps ({@link OmnitrixLatchClient#renderLeapInWorld}).
	 */
	static final class Pod extends EntityRenderer<OmnitrixPodEntity> {
		Pod(EntityRendererProvider.Context ctx) {
			super(ctx);
			this.shadowRadius = 0.5f;
		}

		@Override
		public void render(OmnitrixPodEntity e, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
			pose.pushPose();
			pose.mulPose(Axis.YP.rotationDegrees(-e.getYRot()));
			float o = e.openAmount(partial);
			// a pneumatic pop: slow to unseal, swinging up and settling with a small bounce
			float open = o < 0.18f ? 0.05f * (o / 0.18f) : 0.05f + 0.95f * backOut((o - 0.18f) / 0.82f);
			float heat = Mth.clamp(1f - e.age(partial) / (com.projecthero.mod.omnitrix.OmnitrixConfig.POD_OPEN_DELAY + 80f), 0f, 1f);
			heat = heat * heat;
			float inner = e.empty() ? 0.25f : Mth.clamp(o * 1.4f - 0.2f, 0f, 1f) * (0.85f + Mth.sin(e.age(partial) * 0.15f) * 0.15f);
			OmnitrixPodModel.render(pose, buffers, light, open, heat, inner);
			pose.popPose();
			if (!e.empty() && o > 0.05f) {
				OmnitrixLatchClient.renderLeapInWorld(e, partial, pose, buffers, light);
			}
			super.render(e, yaw, partial, pose, buffers, light);
		}

		private static float backOut(float x) {
			x = Mth.clamp(x, 0f, 1f);
			float c1 = 1.2f, c3 = c1 + 1f;
			return 1f + c3 * (float) Math.pow(x - 1, 3) + c1 * (float) Math.pow(x - 1, 2);
		}

		@Override
		public ResourceLocation getTextureLocation(OmnitrixPodEntity e) {
			return OmnitrixPodModel.TEX;
		}
	}

	static final class Projectile extends EntityRenderer<AlienProjectileEntity> {
		Projectile(EntityRendererProvider.Context ctx) {
			super(ctx);
		}

		@Override
		public void render(AlienProjectileEntity e, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
			ProjectileLook look = PROJECTILE_LOOKS.get(e.kind());
			if (look != null) {
				look.render(e, partial, pose, buffers);
				super.render(e, yaw, partial, pose, buffers, light);
				return;
			}
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
