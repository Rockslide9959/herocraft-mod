package com.projecthero.mod.client.mutation.wind;

import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projecthero.mod.hero.power.p24.WindFxEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.5 Wind Manipulation effect models ({@link WindFxEntity}):
 * <ul>
 * <li>Vacuum -- a vortex facing the caster: spiral arms of air winding into a bright eye, rings collapsing inward;</li>
 * <li>Wind Push -- a cone of gust streak lines bursting out along the push, with pressure rings racing out;</li>
 * <li>Tailwind -- streak lines trailing behind the dashing hero;</li>
 * <li>Updraft -- rings of air racing up a column, wrapped in rising spiral streaks.</li>
 * </ul>
 */
public class WindFxRenderer extends EntityRenderer<WindFxEntity> {
	public WindFxRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.shadowRadius = 0.0f;
	}

	@Override
	public void render(WindFxEntity e, float entityYaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
		float age = e.tickCount + partialTick;
		int bright = LightTexture.pack(15, Math.max(10, LightTexture.sky(light)));
		// streaked surfaces first, then the solid gust lines (a shared buffer must be finished before the next is asked for)
		VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucent(WindMesh.STREAK));
		switch (e.kind()) {
			case WindFxEntity.VACUUM -> vacuum(e, age, pose, vc, bright);
			case WindFxEntity.PUSH -> push(e, age, pose, vc, buffers, bright);
			case WindFxEntity.DASH -> dash(e, age, partialTick, pose, vc, buffers, bright);
			case WindFxEntity.UPDRAFT -> updraft(e, age, pose, vc, buffers, bright);
			default -> {
			}
		}
		super.render(e, entityYaw, partialTick, pose, buffers, light);
	}

	private static float life01(WindFxEntity e, float age) {
		return Mth.clamp(age / Math.max(1, e.maxLife()), 0, 1);
	}

	// ---------------- Vacuum ----------------

	private static void vacuum(WindFxEntity e, float age, PoseStack pose, VertexConsumer vc, int light) {
		int closeAt = WindFxEntity.VACUUM_PULL_TICKS + WindFxEntity.VACUUM_HOLD_TICKS;
		float open = Mth.clamp(age / 5f, 0, 1);
		float close = age > closeAt ? Mth.clamp(1 - (age - closeAt) / 6f, 0, 1) : 1;
		float f = open * close;
		if (f <= 0.01f) {
			return;
		}
		float rad = 3.6f * (0.5f + 0.5f * open) * (close < 1 ? 0.6f + 0.4f * close : 1);
		pose.pushPose();
		// face the caster (its yaw is the caster's): the disc stands upright across the line of the cast
		pose.mulPose(Axis.YP.rotationDegrees(-e.getYRot()));
		pose.mulPose(Axis.ZP.rotation(age * 0.35f));
		int arms = 5;
		for (int i = 0; i < arms; i++) {
			WindMesh.spiralArm(vc, pose.last(), rad, 0.15f, i * Mth.TWO_PI / arms, 0.85f, 0.42f, 24, (int) (90 * f), (int) (255 * f),
					age * 0.2f, light);
		}
		// rings collapsing into the eye
		for (int k = 0; k < 3; k++) {
			float ph = ((age / 9f) + k / 3f) % 1f;
			float rr = rad * (1 - ph);
			int a = (int) (220 * f * ph);
			pose.pushPose();
			pose.mulPose(Axis.XP.rotation(Mth.HALF_PI));
			WindMesh.flatRing(vc, pose.last(), 0, rr * 0.86f, rr, 28, age * 0.1f, a, a / 3, light);
			pose.popPose();
		}
		pose.popPose();
		// the eye: a small bright churning ball
		pose.pushPose();
		pose.mulPose(Axis.YP.rotation(age * 0.5f));
		WindMesh.sphere(vc, pose.last(), 0.45f * f + 0.1f, 6, 12, age * 0.3f, 245, 250, 255, (int) (150 * f), (int) (60 * f), light);
		pose.popPose();
	}

	// ---------------- Wind Push ----------------

	private static void push(WindFxEntity e, float age, PoseStack pose, VertexConsumer vc, MultiBufferSource buffers, int light) {
		float t = life01(e, age);
		float fade = 1 - t * t;
		pose.pushPose();
		pose.mulPose(Axis.YP.rotationDegrees(-e.getYRot()));
		pose.mulPose(Axis.XP.rotationDegrees(e.getXRot()));
		Vector3f toEye = new Vector3f(0, 1, 0);
		RandomSource r = RandomSource.create(e.getId() * 31L);
		// pressure rings racing out
		for (int k = 0; k < 3; k++) {
			float d = age * 1.25f - k * 1.6f;
			if (d <= 0.2f || d > 10f) {
				continue;
			}
			float rr = 0.4f + d * 0.72f;
			pose.pushPose();
			pose.translate(0, 0, d);
			pose.mulPose(Axis.XP.rotation(Mth.HALF_PI));
			int a = (int) (150 * fade * (1 - d / 10f));
			WindMesh.flatRing(vc, pose.last(), 0, rr * 0.82f, rr, 28, age * 0.15f, a / 4, a, light);
			pose.popPose();
		}
		VertexConsumer solid = buffers.getBuffer(RenderType.entityTranslucent(WindMesh.SOLID));
		// streak lines fanning out in the cone (+z = forward)
		for (int i = 0; i < 30; i++) {
			float ang = r.nextFloat() * Mth.TWO_PI;
			float spread = (0.15f + r.nextFloat() * 0.6f);
			float speed = 1.1f + r.nextFloat() * 0.5f;
			float head = Math.min(10.5f, age * speed + r.nextFloat() * 0.8f);
			float tail = Math.max(0, head - 2.4f - r.nextFloat() * 1.5f);
			Vector3f dir = new Vector3f(Mth.cos(ang) * spread, Mth.sin(ang) * spread, 1).normalize();
			Vector3f a = new Vector3f(dir).mul(tail);
			Vector3f b = new Vector3f(dir).mul(head);
			toEye.set(-dir.y, dir.x, 0);
			WindMesh.ribbon(solid, pose.last(), a, b, 0.09f + 0.06f * r.nextFloat(), toEye, (int) (150 * fade), age * 0.2f, age * 0.2f + 1,
					light);
		}
		pose.popPose();
	}

	// ---------------- Tailwind ----------------

	private static void dash(WindFxEntity e, float age, float partialTick, PoseStack pose, VertexConsumer vc, MultiBufferSource buffers, int light) {
		float t = life01(e, age);
		float fade = t < 0.6f ? 1 : 1 - (t - 0.6f) / 0.4f;
		Entity f = e.level().getEntity(e.follow());
		// sit on the hero's body, not the entity's lagging position
		if (f != null) {
			Vec3 at = f.getPosition(partialTick);
			Vec3 me = e.getPosition(partialTick);
			pose.translate(at.x - me.x, at.y - me.y, at.z - me.z);
		}
		pose.pushPose();
		pose.translate(0, 1.0, 0);
		pose.mulPose(Axis.YP.rotationDegrees(-e.getYRot()));
		pose.mulPose(Axis.XP.rotationDegrees(e.getXRot()));
		// the burst ring at the start
		if (age < 8) {
			float rr = 0.6f + age * 0.25f;
			pose.pushPose();
			pose.translate(0, 0, -0.4f);
			pose.mulPose(Axis.XP.rotation(Mth.HALF_PI));
			int a = (int) (170 * (1 - age / 8f));
			WindMesh.flatRing(vc, pose.last(), 0, rr * 0.8f, rr, 24, age * 0.2f, a / 3, a, light);
			pose.popPose();
		}
		VertexConsumer solid = buffers.getBuffer(RenderType.entityTranslucent(WindMesh.SOLID));
		RandomSource r = RandomSource.create(e.getId() * 17L);
		Vector3f toEye = new Vector3f(1, 0, 0);
		for (int i = 0; i < 16; i++) {
			float ang = r.nextFloat() * Mth.TWO_PI;
			float rad = 0.35f + r.nextFloat() * 0.6f;
			float x = Mth.cos(ang) * rad, y = Mth.sin(ang) * rad * 1.4f;
			float len = 2.0f + r.nextFloat() * 2.5f;
			float lead = 0.6f - r.nextFloat() * 0.8f;
			Vector3f a = new Vector3f(x, y, lead);
			Vector3f b = new Vector3f(x, y, lead - len);
			toEye.set(-y, x, 0);
			WindMesh.ribbon(solid, pose.last(), a, b, 0.07f + 0.05f * r.nextFloat(), toEye, (int) (140 * fade), age * 0.3f, age * 0.3f + 1, light);
		}
		pose.popPose();
	}

	// ---------------- Updraft ----------------

	private static void updraft(WindFxEntity e, float age, PoseStack pose, VertexConsumer vc, MultiBufferSource buffers, int light) {
		float t = life01(e, age);
		float fade = t < 0.5f ? 1 : 1 - (t - 0.5f) / 0.5f;
		// rings racing up the column
		for (int k = 0; k < 6; k++) {
			float h = (age * 0.75f + k * 2.2f) % 13f;
			float rr = 1.5f - h * 0.05f;
			int a = (int) (170 * fade * (1 - h / 13f));
			pose.pushPose();
			pose.translate(0, h, 0);
			pose.mulPose(Axis.YP.rotation(age * 0.4f + k));
			WindMesh.band(vc, pose.last(), -0.12f, rr, 0.12f, rr * 1.04f, 24, 3, 0, 0.2f, 0.8f, a, a / 2, light);
			pose.popPose();
		}
		// spiral streaks climbing
		VertexConsumer solid = buffers.getBuffer(RenderType.entityTranslucent(WindMesh.SOLID));
		Vector3f toEye = new Vector3f();
		Minecraft mc = Minecraft.getInstance();
		Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
		for (int i = 0; i < 8; i++) {
			float a0 = i * Mth.TWO_PI / 8 + age * 0.3f;
			float h0 = ((age * 0.9f + i * 1.6f) % 12f);
			for (int s = 0; s < 3; s++) {
				float ha = h0 + s * 0.8f, hb = ha + 0.8f;
				float aa = a0 + s * 0.5f, ab = aa + 0.5f;
				float rr = 1.1f;
				Vector3f p0 = new Vector3f(Mth.cos(aa) * rr, ha, Mth.sin(aa) * rr);
				Vector3f p1 = new Vector3f(Mth.cos(ab) * rr, hb, Mth.sin(ab) * rr);
				toEye.set((float) (cam.x - e.getX()), (float) (cam.y - e.getY() - ha), (float) (cam.z - e.getZ())).normalize();
				WindMesh.ribbon(solid, pose.last(), p0, p1, 0.08f, toEye, (int) (130 * fade * (1 - ha / 14f)), 0, 1, light);
			}
		}
	}

	@Override
	public ResourceLocation getTextureLocation(WindFxEntity entity) {
		return WindMesh.STREAK;
	}

	@Override
	protected boolean shouldShowName(WindFxEntity entity) {
		return false;
	}
}
