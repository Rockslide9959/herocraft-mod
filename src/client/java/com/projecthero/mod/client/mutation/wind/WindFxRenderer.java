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
			case WindFxEntity.SUFFOCATE -> suffocate(e, age, partialTick, pose, vc, buffers, bright);
			case WindFxEntity.STEP -> step(e, age, pose, vc, bright);
			case WindFxEntity.DOWNBURST -> downburst(e, age, pose, vc, buffers, bright);
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

	// ---------------- v0.16.6 Suffocate (G hold) ----------------

	/**
	 * A bubble of air round the victim's head that keeps collapsing in on itself -- it swells, is sucked inward to half its
	 * size, pops and swells again (once a second) -- with rings shrinking into it and streaks of stolen air spiralling
	 * out and away.
	 */
	private static void suffocate(WindFxEntity e, float age, float partialTick, PoseStack pose, VertexConsumer vc, MultiBufferSource buffers,
			int light) {
		Entity f = e.level().getEntity(e.follow());
		if (f == null) {
			return;
		}
		Vec3 at = f.getPosition(partialTick);
		Vec3 me = e.getPosition(partialTick);
		float life = e.maxLife();
		float fade = Mth.clamp(age / 5f, 0, 1) * Mth.clamp((life - age) / 8f, 0, 1);
		if (fade <= 0.01f) {
			return;
		}
		float size = Mth.clamp(f.getBbWidth() / 0.6f, 1.0f, 2.2f);
		pose.pushPose();
		pose.translate(at.x - me.x, at.y - me.y + f.getEyeHeight() - 0.05f * size, at.z - me.z);
		float ph = (age % 20f) / 20f;
		float squeeze = ph < 0.85f ? ph / 0.85f : 1 - (ph - 0.85f) / 0.15f; // sucked in, then pops back
		squeeze = squeeze * squeeze * (3 - 2 * squeeze);
		float r = 0.62f * size * (1 - 0.45f * squeeze);
		int rim = (int) ((120 + 110 * squeeze) * fade);
		pose.pushPose();
		pose.mulPose(Axis.YP.rotation(age * 0.25f));
		pose.scale(1 + 0.08f * squeeze, 1 - 0.1f * squeeze, 1 + 0.08f * squeeze);
		WindMesh.sphere(vc, pose.last(), r, 10, 18, age * 0.05f, 225, 240, 255, (int) (30 * fade), rim, light);
		WindMesh.sphere(vc, pose.last(), r * 0.92f, 8, 14, -age * 0.08f, 245, 250, 255, 0, (int) (rim * 0.45f), light);
		pose.popPose();
		// rings collapsing into the bubble, three tilts
		for (int k = 0; k < 3; k++) {
			float rp = ((age / 10f) + k / 3f) % 1f;
			float rr = r + (1.0f - rp) * 0.9f * size;
			int a = (int) (200 * fade * rp);
			pose.pushPose();
			pose.mulPose(Axis.YP.rotation(k * 1.05f + age * 0.1f));
			pose.mulPose(Axis.XP.rotation(Mth.HALF_PI * (k == 0 ? 0 : k == 1 ? 0.5f : 1f)));
			WindMesh.flatRing(vc, pose.last(), 0, rr * 0.9f, rr, 28, age * 0.2f, a, a / 3, light);
			pose.popPose();
		}
		// streaks of stolen air drawn up out of it
		VertexConsumer solid = buffers.getBuffer(RenderType.entityTranslucent(WindMesh.SOLID));
		Vector3f toEye = new Vector3f();
		Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
		for (int i = 0; i < 6; i++) {
			float sp = ((age * 0.06f) + i / 6f) % 1f;
			float a0 = i * Mth.TWO_PI / 6 + age * 0.18f;
			float aa = a0 + sp * 2.2f, ab = aa + 0.5f;
			float ra = r + sp * 0.9f * size, rb = ra + 0.3f * size;
			Vector3f p0 = new Vector3f(Mth.cos(aa) * ra, sp * 1.1f * size, Mth.sin(aa) * ra);
			Vector3f p1 = new Vector3f(Mth.cos(ab) * rb, (sp + 0.18f) * 1.1f * size, Mth.sin(ab) * rb);
			toEye.set((float) (cam.x - at.x), (float) (cam.y - at.y - f.getEyeHeight()), (float) (cam.z - at.z)).normalize();
			WindMesh.ribbon(solid, pose.last(), p0, p1, 0.06f * size, toEye, (int) (170 * fade * (1 - sp)), 0, 1, light);
		}
		pose.popPose();
	}

	// ---------------- v0.16.6 Sky Step (X hold) ----------------

	/** A little ring of air under the feet: a swirl that spreads out flat and fades. */
	private static void step(WindFxEntity e, float age, PoseStack pose, VertexConsumer vc, int light) {
		float t = life01(e, age);
		float fade = 1 - t * t;
		float rr = 0.35f + t * 1.0f;
		pose.pushPose();
		pose.translate(0, 0.05f, 0);
		pose.mulPose(Axis.YP.rotation(age * 0.4f));
		WindMesh.flatRing(vc, pose.last(), 0, rr * 0.7f, rr, 28, age * 0.2f, (int) (90 * fade), (int) (230 * fade), light);
		WindMesh.flatRing(vc, pose.last(), -0.12f, rr * 0.45f, rr * 0.62f, 24, -age * 0.25f, (int) (60 * fade), (int) (160 * fade), light);
		// a small flat swirl in the middle, the air the step stood on
		pose.mulPose(Axis.XP.rotation(Mth.HALF_PI));
		for (int i = 0; i < 4; i++) {
			WindMesh.spiralArm(vc, pose.last(), rr * 0.7f, 0.05f, i * Mth.HALF_PI, 0.5f, 0.09f, 12, (int) (200 * fade), (int) (60 * fade),
					age * 0.3f, light);
		}
		pose.popPose();
		// the rim, standing a hand high
		pose.pushPose();
		pose.mulPose(Axis.YP.rotation(-age * 0.5f));
		WindMesh.band(vc, pose.last(), 0, rr * 0.98f, 0.18f * (1 - t), rr * 1.05f, 28, 3, age * 0.1f, 0.2f, 0.8f, (int) (200 * fade), 0, light);
		pose.popPose();
	}

	// ---------------- v0.16.6 Downburst (C hold) ----------------

	/**
	 * A column of air 6 blocks across rushing down out of the sky: stacked rings racing downward behind a bright front,
	 * wrapped in falling streaks; where it lands a ring of air bursts outward along the ground and the column thins away.
	 * Before it lands, a faint ring on the ground marks the spot.
	 */
	private static void downburst(WindFxEntity e, float age, PoseStack pose, VertexConsumer vc, MultiBufferSource buffers, int light) {
		float drop = WindFxEntity.DOWNBURST_DROP_TICKS;
		float rad = (float) com.projecthero.mod.hero.power.p24.WindHandlers.DOWNBURST_RADIUS;
		float top = 16f;
		float front = age < drop ? top * (1 - age / drop) : 0; // the bottom of the falling column
		float after = Math.max(0, age - drop);
		float colFade = age < drop ? Mth.clamp(age / 2f, 0, 1) : Mth.clamp(1 - after / 10f, 0, 1);
		// the marker / landing ring on the ground
		pose.pushPose();
		pose.translate(0, 0.06f, 0);
		pose.mulPose(Axis.YP.rotation(age * 0.15f));
		if (age < drop) {
			int a = (int) (80 + 100 * (age / drop));
			WindMesh.flatRing(vc, pose.last(), 0, rad * 0.92f, rad, 40, age * 0.1f, a, a, light);
		} else {
			// the burst along the ground
			float sp = Mth.clamp(after / 12f, 0, 1);
			float rr = rad * (0.9f + sp * 1.4f);
			int a = (int) (230 * (1 - sp));
			WindMesh.flatRing(vc, pose.last(), 0, rr * 0.7f, rr, 40, age * 0.2f, a / 4, a, light);
			WindMesh.flatRing(vc, pose.last(), 0.02f, 0.2f, rad * (0.8f + sp * 0.5f), 40, -age * 0.2f, (int) (150 * (1 - sp)), a / 3, light);
			pose.mulPose(Axis.YP.rotation(-age * 0.4f));
			WindMesh.band(vc, pose.last(), 0, rr * 0.98f, 0.9f * (1 - sp) + 0.1f, rr * 1.12f, 40, 6, age * 0.15f, 0.1f, 0.9f, a, 0, light);
		}
		pose.popPose();
		if (colFade <= 0.01f) {
			return;
		}
		// the column: rings stacked from the front up, racing down
		float colR = rad * 0.95f * (age < drop ? 1 : 1 - 0.4f * Mth.clamp(after / 10f, 0, 1));
		int rings = 9;
		for (int k = 0; k < rings; k++) {
			float y = ((k / (float) rings) * top - age * 1.6f) % top;
			if (y < 0) {
				y += top;
			}
			if (y < front) {
				continue;
			}
			float yr = 1 - y / top;
			int a = (int) (150 * colFade * (0.35f + 0.65f * yr));
			pose.pushPose();
			pose.translate(0, y, 0);
			pose.mulPose(Axis.YP.rotation(age * 0.5f + k));
			WindMesh.band(vc, pose.last(), -0.2f, colR, 0.2f, colR * 1.03f, 32, 4, 0, 0.2f, 0.8f, a, a / 2, light);
			pose.popPose();
		}
		// the outer skin of the column
		pose.pushPose();
		pose.mulPose(Axis.YP.rotation(age * 0.2f));
		WindMesh.band(vc, pose.last(), front, colR * 1.02f, top, colR * 0.9f, 32, 4, -age * 0.08f, 1.0f, 0.0f, (int) (90 * colFade), 0, light);
		pose.popPose();
		// the bright front of the falling air
		if (age < drop + 2) {
			pose.pushPose();
			pose.translate(0, front, 0);
			pose.mulPose(Axis.YP.rotation(-age * 0.6f));
			WindMesh.flatRing(vc, pose.last(), 0, colR * 0.15f, colR * 1.05f, 32, age * 0.3f, (int) (120 * colFade), (int) (240 * colFade), light);
			pose.popPose();
		}
		// streaks rushing down inside it
		VertexConsumer solid = buffers.getBuffer(RenderType.entityTranslucent(WindMesh.SOLID));
		RandomSource r = RandomSource.create(e.getId() * 13L);
		Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
		Vector3f toEye = new Vector3f();
		for (int i = 0; i < 22; i++) {
			float ang = r.nextFloat() * Mth.TWO_PI;
			float rr = colR * (0.3f + r.nextFloat() * 0.7f);
			float len = 2.2f + r.nextFloat() * 2.5f;
			float y = ((r.nextFloat() * top) - age * 2.4f) % top;
			if (y < 0) {
				y += top;
			}
			if (y < front) {
				continue;
			}
			Vector3f a = new Vector3f(Mth.cos(ang) * rr, Math.min(top, y + len), Mth.sin(ang) * rr);
			Vector3f b = new Vector3f(Mth.cos(ang) * rr, Math.max(front, y), Mth.sin(ang) * rr);
			toEye.set((float) (cam.x - e.getX()), 0, (float) (cam.z - e.getZ())).normalize();
			WindMesh.ribbon(solid, pose.last(), a, b, 0.09f, toEye, (int) (170 * colFade), age * 0.3f, age * 0.3f + 1, light);
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
