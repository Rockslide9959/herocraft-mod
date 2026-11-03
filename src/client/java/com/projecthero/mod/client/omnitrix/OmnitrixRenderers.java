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
	 * seams fading after the fall, the shell plates sliding apart round both sides on the right-click (v0.17.2), the cavity's
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
			// v0.17.2 (user: "its supposed to slide open in 2 ways"): a short unlatch click (the plates jog a few degrees and
			// hold), then the two sides slide round the sphere, easing in and out and settling with a small overshoot
			float open;
			if (o < 0.12f) {
				float k = Mth.clamp(o / 0.04f, 0f, 1f);
				open = 0.025f * k * k * (3f - 2f * k);
			} else {
				float t = (o - 0.12f) / 0.88f;
				float s = t < 0.5f ? 4f * t * t * t : 1f - (float) Math.pow(-2f * t + 2f, 3) / 2f;
				float settle = Mth.clamp((t - 0.72f) / 0.28f, 0f, 1f);
				open = 0.025f + 0.975f * s + 0.03f * Mth.sin(settle * Mth.PI);
			}
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

	/**
	 * The Containment Pod's lights -- v0.17.2 (user: "make the omnitrix containment pod look better"): a green emitter ring round
	 * the floor and another under the cap, the Omnitrix hourglass lit on the floor plate, and between them the energy field -- a
	 * smooth 24-sided tube that fades out toward both ends, a faint shimmer at rest, roaring bright (red pulses, rings racing up)
	 * while extracting.
	 */
	static final class PodField implements BlockEntityRenderer<OmnitrixPodBlockEntity> {
		private static final int SEG = 24;
		private static final float C = 0.5f, R = 0.38f;
		private static final float Y0 = 2.25f / 16f, Y1 = 1f + 11f / 16f;

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
			PoseStack.Pose p = pose.last();
			// from inside the tube (the wearer) the field is only a faint shimmer, or it would fill the screen
			var cam = net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
			double dx = cam.x - (be.getBlockPos().getX() + 0.5), dz = cam.z - (be.getBlockPos().getZ() + 0.5);
			boolean inside = dx * dx + dz * dz < 0.36 && cam.y > be.getBlockPos().getY() && cam.y < be.getBlockPos().getY() + 2.2;
			int col = running && (int) (t / 4) % 2 == 0 ? 0xFF6050 : 0x7CFF6A;
			float pulse = 0.8f + 0.2f * Mth.sin(t * 0.15f);
			// the two emitter rings: one round the floor, one under the cap
			ring(vc, p, Y0 + 0.005f, R + 0.05f, 0.035f, col, (int) ((running ? 230 : 170) * pulse));
			ring(vc, p, Y1 - 0.01f, R + 0.05f, 0.035f, col, (int) ((running ? 230 : 170) * pulse));
			// the hourglass on the floor plate
			hourglass(vc, p, Y0 + 0.01f, 0.2f, col, (int) ((running ? 220 : 150) * pulse));
			// the field: bands up the tube, fading in from the floor and out under the cap
			int peak = inside ? 22 : running ? 130 : 55;
			float scroll = (t * (running ? 0.08f : 0.015f)) % 1f;
			int bands = 8;
			for (int bnd = 0; bnd < bands; bnd++) {
				float f0 = bnd / (float) bands, f1 = (bnd + 1) / (float) bands;
				float ya = Mth.lerp(f0, Y0, Y1), yb = Mth.lerp(f1, Y0, Y1);
				float fade = Math.min(1f, Math.min((f0 + f1) * 0.5f, 1f - (f0 + f1) * 0.5f) * 4f);
				int a = (int) (peak * fade);
				if (a < 3) {
					continue;
				}
				int argb = argb(col, a);
				for (int k = 0; k < SEG; k++) {
					double a0 = k * Math.PI * 2 / SEG, a1 = (k + 1) * Math.PI * 2 / SEG;
					float x0 = C + (float) Math.cos(a0) * R, z0 = C + (float) Math.sin(a0) * R;
					float x1 = C + (float) Math.cos(a1) * R, z1 = C + (float) Math.sin(a1) * R;
					AlienParts.quad(vc, p, argb, LightTexture.FULL_BRIGHT, x0, ya, z0, x1, ya, z1, x1, yb, z1, x0, yb, z0, 0, 0, 1, k / (float) SEG,
							scroll + f1 * 2f, (k + 1) / (float) SEG, scroll + f0 * 2f);
				}
			}
			if (running) {
				// the pulse rings racing up the tube
				for (int r = 0; r < 3; r++) {
					float ry = Y0 + ((t * 0.05f + r / 3f) % 1f) * (Y1 - Y0);
					ring(vc, p, ry, R + 0.03f, 0.04f, 0xFFFFFF, 220);
				}
			}
		}

		private static int argb(int rgb, int a) {
			a = Mth.clamp(a, 0, 255);
			return (a << 24) | (((rgb >> 16) & 0xFF) * a / 255 << 16) | (((rgb >> 8) & 0xFF) * a / 255 << 8) | ((rgb & 0xFF) * a / 255);
		}

		/** A flat glowing ring at height {@code y}, {@code w} wide, round the pod's middle. */
		private static void ring(VertexConsumer vc, PoseStack.Pose p, float y, float r, float w, int rgb, int a) {
			int argb = argb(rgb, a);
			for (int k = 0; k < SEG; k++) {
				double a0 = k * Math.PI * 2 / SEG, a1 = (k + 1) * Math.PI * 2 / SEG;
				float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0), c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
				float ri = r - w, ro = r + w;
				AlienParts.quad(vc, p, argb, LightTexture.FULL_BRIGHT, C + c0 * ri, y, C + s0 * ri, C + c1 * ri, y, C + s1 * ri, C + c1 * ro, y, C + s1 * ro,
						C + c0 * ro, y, C + s0 * ro, 0, 1, 0, 0.45f, 0.45f, 0.55f, 0.55f);
			}
		}

		/** The Omnitrix hourglass lying on the floor: two triangles tip to tip, in a thin ring. */
		private static void hourglass(VertexConsumer vc, PoseStack.Pose p, float y, float r, int rgb, int a) {
			int argb = argb(rgb, a);
			float w = r * 0.85f;
			// upper triangle (toward -z) and lower (toward +z), drawn as degenerate quads
			// both windings, so it shows from above whatever way the quad helper culls
			AlienParts.quad(vc, p, argb, LightTexture.FULL_BRIGHT, C - w, y, C - r, C + w, y, C - r, C, y, C, C, y, C, 0, 1, 0, 0.45f, 0.45f, 0.55f, 0.55f);
			AlienParts.quad(vc, p, argb, LightTexture.FULL_BRIGHT, C, y, C, C, y, C, C + w, y, C - r, C - w, y, C - r, 0, 1, 0, 0.45f, 0.45f, 0.55f, 0.55f);
			AlienParts.quad(vc, p, argb, LightTexture.FULL_BRIGHT, C, y, C, C, y, C, C + w, y, C + r, C - w, y, C + r, 0, 1, 0, 0.45f, 0.45f, 0.55f, 0.55f);
			AlienParts.quad(vc, p, argb, LightTexture.FULL_BRIGHT, C - w, y, C + r, C + w, y, C + r, C, y, C, C, y, C, 0, 1, 0, 0.45f, 0.45f, 0.55f, 0.55f);
			ring(vc, p, y, r + 0.03f, 0.015f, rgb, a);
		}

		@Override
		public boolean shouldRenderOffScreen(OmnitrixPodBlockEntity be) {
			return true;
		}
	}
}
