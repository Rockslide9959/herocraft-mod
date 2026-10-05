package com.projecthero.mod.client.symbiote;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.symbiote.entity.SymbioteTendrilEntity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.13.19: draws a {@link SymbioteTendrilEntity} as a living tendril -- a glossy black tube that tapers to a
 * barbed point, lashes out from the host's hand (or out of the ground) along a gently curving, writhing path,
 * hangs on its target, then snaps back. Everything is computed per frame from the synced anchor / target, so
 * it stays attached to a moving host and a moving victim.
 */
public class SymbioteTendrilRenderer extends EntityRenderer<SymbioteTendrilEntity> {
	private static final ResourceLocation TEXTURE = ProjectHeroMod.id("textures/entity/symbiote_tendril.png");
	/** v0.14.25: Carnage's. */
	private static final ResourceLocation CRIMSON = ProjectHeroMod.id("textures/entity/symbiote_tendril_crimson.png");
	private static final int RETRACT_TICKS = 3;
	private static final int SIDES = 6;

	public SymbioteTendrilRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.shadowRadius = 0.0f;
	}

	@Override
	public boolean shouldRender(SymbioteTendrilEntity entity, Frustum frustum, double camX, double camY, double camZ) {
		return entity.shouldRenderAtSqrDistance(entity.distanceToSqr(camX, camY, camZ));
	}

	@Override
	public void render(SymbioteTendrilEntity e, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers,
			int light) {
		float age = e.tickCount + partialTick;
		int life = e.life();
		int extend = e.extendTicks();
		float reach;
		if (age < extend) {
			float t = age / extend;
			reach = 1.0f - (1.0f - t) * (1.0f - t); // ease out: whips out fast, settles
		} else if (age > life - RETRACT_TICKS) {
			reach = Math.max(0.0f, (life - age) / RETRACT_TICKS);
		} else {
			reach = 1.0f;
		}
		if (reach <= 0.02f) {
			return;
		}

		Vec3 origin = new Vec3(Mth.lerp(partialTick, e.xo, e.getX()), Mth.lerp(partialTick, e.yo, e.getY()),
				Mth.lerp(partialTick, e.zo, e.getZ()));
		Vec3 start = origin;
		if (e.anchor() != SymbioteTendrilEntity.ANCHOR_FIXED && e.level().getEntity(e.ownerId()) instanceof Player owner) {
			start = handOf(owner, e.anchor() == SymbioteTendrilEntity.ANCHOR_RIGHT_HAND, partialTick);
		} else if (e.anchor() == SymbioteTendrilEntity.ANCHOR_BODY && e.level().getEntity(e.ownerId()) instanceof net.minecraft.world.entity.LivingEntity body) {
			start = body.getPosition(partialTick).add(0, body.getBbHeight() * 0.62, 0);
		}
		Vec3 end = e.end();
		Entity target = e.targetId() >= 0 ? e.level().getEntity(e.targetId()) : null;
		if (target != null && target.isAlive()) {
			end = target.getPosition(partialTick).add(0, target.getBbHeight() * 0.5, 0);
		}
		Vec3 span = end.subtract(start);
		double length = span.length();
		if (length < 0.05) {
			return;
		}
		Vec3 dir = span.scale(1.0 / length);
		Vec3 side = dir.cross(new Vec3(0, 1, 0));
		side = side.lengthSqr() < 1.0e-6 ? new Vec3(1, 0, 0) : side.normalize();
		Vec3 up = side.cross(dir).normalize();

		int seed = e.getId() * 31;
		float time = age * 0.55f;
		double bow = (0.18 + 0.1 * Math.sin(seed)) * Math.min(3.0, length * 0.25); // the whip's arc
		int segments = Mth.clamp((int) (length * 2.5), 10, 44);
		int count = Math.max(2, Math.round(segments * reach) + 1);
		Vec3[] pts = new Vec3[count];
		float[] radii = new float[count];
		float width = e.width();
		for (int i = 0; i < count; i++) {
			double t = (double) i / segments; // 0..reach along the full path
			double arc = Math.sin(Math.PI * t);
			double wiggle = Math.sin(t * Math.PI * 3.0 - time + seed) * 0.12 * arc * Math.min(2.0, length * 0.2);
			double wiggle2 = Math.cos(t * Math.PI * 2.0 - time * 1.3 + seed * 0.5) * 0.08 * arc * Math.min(2.0, length * 0.2);
			Vec3 p = start.add(span.scale(t)).add(up.scale(bow * arc + wiggle2)).add(side.scale(wiggle));
			pts[i] = p.subtract(origin);
			double along = (double) i / Math.max(1, count - 1);
			radii[i] = (float) (width * (1.0 - 0.72 * along) * (i == count - 1 ? 0.15 : 1.0));
		}

		VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(e.crimson() ? CRIMSON : TEXTURE));
		PoseStack.Pose last = pose.last();
		GooMesh.tube(vc, last, light, pts, radii, SIDES, 0.35f);
		// a barbed tip: three short hooks flaring back from the point
		Vec3 tip = pts[count - 1];
		Vec3 back = count > 1 ? pts[count - 2].subtract(tip).normalize() : dir.scale(-1);
		for (int k = 0; k < 3; k++) {
			double ang = Math.PI * 2 * k / 3.0 + seed;
			Vec3 out = side.scale(Math.cos(ang)).add(up.scale(Math.sin(ang)));
			Vec3 barbTip = tip.add(back.scale(width * 1.6)).add(out.scale(width * 1.3));
			GooMesh.tube(vc, last, light, new Vec3[]{tip.add(back.scale(width * 0.4)), barbTip},
					new float[]{width * 0.35f, 0.005f}, 4, 0.0f);
		}
	}

	/** Client twin of {@code SymbioteHands.hand}, interpolated for smooth drawing. */
	static Vec3 handOf(Player player, boolean rightHand, float partialTick) {
		double scale = player.getScale();
		Vec3 look = player.getViewVector(partialTick);
		Vec3 flat = new Vec3(look.x, 0.0, look.z);
		Vec3 right = flat.lengthSqr() < 1.0e-6 ? new Vec3(1, 0, 0) : new Vec3(-flat.z, 0.0, flat.x).normalize();
		Vec3 shoulder = player.getPosition(partialTick).add(0.0, 1.375 * scale, 0.0)
				.add(right.scale((rightHand ? 0.3125 : -0.3125) * scale));
		return shoulder.add(look.scale(0.6 * scale));
	}

	@Override
	public ResourceLocation getTextureLocation(SymbioteTendrilEntity entity) {
		return TEXTURE;
	}
}
