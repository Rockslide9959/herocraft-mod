package com.projecthero.mod.client.bluebeetle;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.bluebeetle.entity.PlasmaBoltEntity;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import org.joml.Matrix4f;

/**
 * v0.16.5 (user: "give plasma blaster a projectile model"): a Plasma Blaster bolt -- a glowing blue orb (three crossed
 * discs of light round a white-blue core) with a short tapering trail behind it. Additive {@code lightning} quads, both
 * windings (the type is culled).
 */
public class PlasmaBoltRenderer extends EntityRenderer<PlasmaBoltEntity> {
	private static final int SIDES = 10;

	public PlasmaBoltRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.shadowRadius = 0f;
	}

	@Override
	public void render(PlasmaBoltEntity bolt, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
		VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
		pose.pushPose();
		pose.translate(0f, bolt.getBbHeight() * 0.5f, 0f);
		Matrix4f m = pose.last().pose();
		float age = bolt.tickCount + partial;
		float pulse = 0.9f + 0.1f * Mth.sin(age * 2.3f);
		// the orb: an outer blue glow, an inner pale shell, a white core
		orb(vc, m, 0.24f * pulse, 0.2f, 0.5f, 1.0f, 0.45f);
		orb(vc, m, 0.15f, 0.45f, 0.78f, 1.0f, 0.75f);
		orb(vc, m, 0.08f, 0.9f, 0.97f, 1.0f, 0.95f);
		// the trail, back along where it came from
		Vec3 v = bolt.getDeltaMovement();
		if (v.lengthSqr() > 1.0e-4) {
			Vec3 back = v.normalize().scale(-1.1);
			trail(vc, m, back, 0.2f, 0.25f, 0.6f, 1.0f, 0.5f);
			trail(vc, m, back.scale(0.75), 0.09f, 0.8f, 0.95f, 1.0f, 0.8f);
		}
		pose.popPose();
		super.render(bolt, yaw, partial, pose, buffers, light);
	}

	/** Three crossed discs (xy, yz, xz) of radius {@code r}. */
	private static void orb(VertexConsumer vc, Matrix4f m, float r, float cr, float cg, float cb, float a) {
		for (int plane = 0; plane < 3; plane++) {
			for (int i = 0; i < SIDES; i++) {
				double a0 = Math.PI * 2 * i / SIDES, a1 = Math.PI * 2 * (i + 1) / SIDES;
				float c0 = (float) Math.cos(a0) * r, s0 = (float) Math.sin(a0) * r, c1 = (float) Math.cos(a1) * r, s1 = (float) Math.sin(a1) * r;
				float[] p0 = point(plane, c0, s0), p1 = point(plane, c1, s1);
				vc.addVertex(m, 0f, 0f, 0f).setColor(cr, cg, cb, a);
				vc.addVertex(m, p0[0], p0[1], p0[2]).setColor(cr, cg, cb, a * 0.25f);
				vc.addVertex(m, p1[0], p1[1], p1[2]).setColor(cr, cg, cb, a * 0.25f);
				vc.addVertex(m, 0f, 0f, 0f).setColor(cr, cg, cb, a);
				vc.addVertex(m, 0f, 0f, 0f).setColor(cr, cg, cb, a);
				vc.addVertex(m, p1[0], p1[1], p1[2]).setColor(cr, cg, cb, a * 0.25f);
				vc.addVertex(m, p0[0], p0[1], p0[2]).setColor(cr, cg, cb, a * 0.25f);
				vc.addVertex(m, 0f, 0f, 0f).setColor(cr, cg, cb, a);
			}
		}
	}

	private static float[] point(int plane, float a, float b) {
		return switch (plane) {
			case 0 -> new float[] { a, b, 0f };
			case 1 -> new float[] { 0f, a, b };
			default -> new float[] { a, 0f, b };
		};
	}

	/** Two crossed sheets tapering from the orb ({@code width} wide) to nothing at {@code back}. */
	private static void trail(VertexConsumer vc, Matrix4f m, Vec3 back, float width, float r, float g, float b, float a) {
		Vec3 d = back.normalize();
		Vec3 s1 = d.cross(new Vec3(0, 1, 0));
		if (s1.lengthSqr() < 1.0e-4) {
			s1 = new Vec3(1, 0, 0);
		}
		s1 = s1.normalize().scale(width);
		Vec3 s2 = d.cross(s1).normalize().scale(width);
		for (Vec3 s : new Vec3[] { s1, s2 }) {
			float tx = (float) back.x, ty = (float) back.y, tz = (float) back.z;
			vc.addVertex(m, (float) -s.x, (float) -s.y, (float) -s.z).setColor(r, g, b, a);
			vc.addVertex(m, (float) s.x, (float) s.y, (float) s.z).setColor(r, g, b, a);
			vc.addVertex(m, tx, ty, tz).setColor(r, g, b, 0f);
			vc.addVertex(m, tx, ty, tz).setColor(r, g, b, 0f);
			vc.addVertex(m, (float) s.x, (float) s.y, (float) s.z).setColor(r, g, b, a);
			vc.addVertex(m, (float) -s.x, (float) -s.y, (float) -s.z).setColor(r, g, b, a);
			vc.addVertex(m, tx, ty, tz).setColor(r, g, b, 0f);
			vc.addVertex(m, tx, ty, tz).setColor(r, g, b, 0f);
		}
	}

	@Override
	public ResourceLocation getTextureLocation(PlasmaBoltEntity entity) {
		return TextureAtlas.LOCATION_BLOCKS;
	}
}
