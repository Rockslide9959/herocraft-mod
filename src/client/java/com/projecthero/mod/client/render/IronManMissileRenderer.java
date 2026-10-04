package com.projecthero.mod.client.render;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.ironman.entity.IronManMissileEntity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * v0.14.21: the missile finally has a body. Before this it rendered nothing and was only the smoke/flame
 * particle streak the entity spawns in {@link IronManMissileEntity#tick} (that trail is unchanged).
 *
 * <p>A small procedural mesh, no model file: a slim square-section silver body with a red warhead band, a
 * four-sided red nose cone, four cruciform tail fins, a dark nozzle cap and a flickering full-bright exhaust
 * plume (two crossed quads, cyan core fading to orange). Oriented along the velocity the way vanilla turns
 * an arrow ({@code Axis.YP(yaw - 90)} then {@code Axis.ZP(pitch)}), so local +X is the nose. The body rolls
 * slowly about its own axis. Texture {@code textures/entity/iron_man_missile.png} (32x16) from
 * {@code scratchpad/gen_v01421_ironman_textures.js}:
 * <ul>
 *   <li>body side strip u 0-16, v 0-4 (tail to nose); nose u 16-20; nozzle cap u 20-24</li>
 *   <li>fin u 0-6, v 4-8</li>
 *   <li>exhaust plume u 0-16, v 8-16 (alpha-faded)</li>
 * </ul>
 */
public class IronManMissileRenderer extends EntityRenderer<IronManMissileEntity> {
	private static final ResourceLocation TEX = ProjectHeroMod.id("textures/entity/iron_man_missile.png");
	private static final float TW = 32f;
	private static final float TH = 16f;

	/** Body runs x = TAIL..FRONT, square cross-section of half-width R. */
	private static final float TAIL = -0.24f;
	private static final float FRONT = 0.2f;
	private static final float NOSE_TIP = 0.33f;
	private static final float R = 0.045f;
	private static final float FIN_LEN = 0.13f;
	private static final float FIN_OUT = 0.075f;

	public IronManMissileRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.shadowRadius = 0.0f;
	}

	@Override
	public void render(IronManMissileEntity entity, float yaw, float partialTicks, PoseStack pose,
			MultiBufferSource buffers, int light) {
		Vec3 v = entity.getDeltaMovement();
		float my;
		float mp;
		if (v.lengthSqr() > 1.0e-7) {
			my = (float) (Mth.atan2(v.x, v.z) * Mth.RAD_TO_DEG);
			mp = (float) (Mth.atan2(v.y, v.horizontalDistance()) * Mth.RAD_TO_DEG);
		} else {
			my = Mth.lerp(partialTicks, entity.yRotO, entity.getYRot());
			mp = -Mth.lerp(partialTicks, entity.xRotO, entity.getXRot());
		}
		float age = entity.tickCount + partialTicks;
		pose.pushPose();
		pose.translate(0.0, entity.getBbHeight() * 0.5, 0.0);
		pose.mulPose(Axis.YP.rotationDegrees(my - 90.0f));
		pose.mulPose(Axis.ZP.rotationDegrees(mp));
		pose.mulPose(Axis.XP.rotationDegrees(age * 18.0f)); // slow roll about the flight axis
		PoseStack.Pose p = pose.last();

		VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(TEX));
		body(vc, p, light);

		// exhaust: full-bright, translucent, flickering length
		float flicker = 0.85f + 0.15f * Mth.sin(age * 2.7f) + 0.08f * Mth.sin(age * 7.3f);
		VertexConsumer glow = buffers.getBuffer(RenderType.entityTranslucentEmissive(TEX));
		plume(glow, p, LightTexture.FULL_BRIGHT, 0.26f * flicker, 0.06f);
		pose.popPose();
		super.render(entity, yaw, partialTicks, pose, buffers, light);
	}

	private static void body(VertexConsumer vc, PoseStack.Pose p, int light) {
		// four long sides, each the full body strip (u 0..16 = tail..nose)
		float u0 = 0f, u1 = 16f, v0 = 0f, v1 = 4f;
		quad(vc, p, light, TAIL, -R, R, FRONT, -R, R, FRONT, R, R, TAIL, R, R, u0, v0, u1, v1, 0, 0, 1);
		quad(vc, p, light, TAIL, R, -R, FRONT, R, -R, FRONT, -R, -R, TAIL, -R, -R, u0, v0, u1, v1, 0, 0, -1);
		quad(vc, p, light, TAIL, R, R, FRONT, R, R, FRONT, R, -R, TAIL, R, -R, u0, v0, u1, v1, 0, 1, 0);
		quad(vc, p, light, TAIL, -R, -R, FRONT, -R, -R, FRONT, -R, R, TAIL, -R, R, u0, v0, u1, v1, 0, -1, 0);
		// nose cone: four triangles (a quad with its last two corners on the tip)
		float[][] ring = {{-R, R}, {R, R}, {R, -R}, {-R, -R}};
		for (int i = 0; i < 4; i++) {
			float[] a = ring[i];
			float[] b = ring[(i + 1) % 4];
			quad(vc, p, light, FRONT, a[0], a[1], FRONT, b[0], b[1], NOSE_TIP, 0, 0, NOSE_TIP, 0, 0,
					16f, 0f, 20f, 4f, 0.5f, 0.5f * (a[0] + b[0]) / R, 0.5f * (a[1] + b[1]) / R);
		}
		// nozzle cap at the tail
		quad(vc, p, light, TAIL, -R, -R, TAIL, R, -R, TAIL, R, R, TAIL, -R, R, 20f, 0f, 24f, 4f, -1, 0, 0);
		// cruciform fins at the tail (double-sided render type)
		float fx0 = TAIL;
		float fx1 = TAIL + FIN_LEN;
		quad(vc, p, light, fx0, R, 0, fx1, R, 0, fx1, R + FIN_OUT * 0.3f, 0, fx0, R + FIN_OUT, 0, 0f, 4f, 6f, 8f, 0, 0, 1);
		quad(vc, p, light, fx0, -R, 0, fx1, -R, 0, fx1, -R - FIN_OUT * 0.3f, 0, fx0, -R - FIN_OUT, 0, 0f, 4f, 6f, 8f, 0, 0, 1);
		quad(vc, p, light, fx0, 0, R, fx1, 0, R, fx1, 0, R + FIN_OUT * 0.3f, fx0, 0, R + FIN_OUT, 0f, 4f, 6f, 8f, 0, 1, 0);
		quad(vc, p, light, fx0, 0, -R, fx1, 0, -R, fx1, 0, -R - FIN_OUT * 0.3f, fx0, 0, -R - FIN_OUT, 0f, 4f, 6f, 8f, 0, 1, 0);
	}

	/** Two crossed quads trailing behind the nozzle; u runs from the nozzle (u 0) out to the faded tip (u 16). */
	private static void plume(VertexConsumer vc, PoseStack.Pose p, int light, float len, float half) {
		float x0 = TAIL - 0.005f;
		float x1 = TAIL - len;
		quad(vc, p, light, x0, -half, 0, x1, -half, 0, x1, half, 0, x0, half, 0, 0f, 8f, 16f, 16f, 0, 0, 1);
		quad(vc, p, light, x0, 0, -half, x1, 0, -half, x1, 0, half, x0, 0, half, 0f, 8f, 16f, 16f, 0, 1, 0);
	}

	/**
	 * Corners a,b,c,d in order; a->b is the +u direction (u0->u1) and b->c the +v direction... mapped as
	 * a=(u0,v0) b=(u1,v0) c=(u1,v1) d=(u0,v1). UVs in texture pixels.
	 */
	private static void quad(VertexConsumer vc, PoseStack.Pose p, int light,
			float ax, float ay, float az, float bx, float by, float bz,
			float cx, float cy, float cz, float dx, float dy, float dz,
			float u0, float v0, float u1, float v1, float nx, float ny, float nz) {
		float len = Mth.sqrt(nx * nx + ny * ny + nz * nz);
		if (len > 1.0e-6f) {
			nx /= len;
			ny /= len;
			nz /= len;
		}
		vertex(vc, p, light, ax, ay, az, u0 / TW, v0 / TH, nx, ny, nz);
		vertex(vc, p, light, bx, by, bz, u1 / TW, v0 / TH, nx, ny, nz);
		vertex(vc, p, light, cx, cy, cz, u1 / TW, v1 / TH, nx, ny, nz);
		vertex(vc, p, light, dx, dy, dz, u0 / TW, v1 / TH, nx, ny, nz);
	}

	private static void vertex(VertexConsumer vc, PoseStack.Pose p, int light, float x, float y, float z,
			float u, float v, float nx, float ny, float nz) {
		vc.addVertex(p, x, y, z)
				.setColor(255, 255, 255, 255)
				.setUv(u, v)
				.setOverlay(OverlayTexture.NO_OVERLAY)
				.setLight(light)
				.setNormal(p, nx, ny, nz);
	}

	@Override
	public ResourceLocation getTextureLocation(IronManMissileEntity entity) {
		return TEX;
	}
}
