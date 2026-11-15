package com.projecthero.mod.client.vaultdungeon;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.client.beamclash.BeamClashClient;
import com.projecthero.mod.client.darkseid.BeamDraw;
import com.projecthero.mod.client.syndicate.SyndicateRenderer;
import com.projecthero.mod.syndicate.entity.SyndicateCriminal;
import com.projecthero.mod.vaultdungeon.entity.WardenKrossBoss;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.5: Warden Kross -- the Syndicate player-model renderer (his skin, baton, shield, the enforcer's charge / slam /
 * dazed poses) plus his <b>Taser Line</b>: a jittering electric-blue line from his hand to whoever it is latched to
 * (the synced {@code taserTargetId}). Hidden while the beam is locked in a beam clash ({@link BeamClashClient#hides}),
 * which draws it instead.
 */
public class WardenKrossRenderer extends SyndicateRenderer {
	public WardenKrossRenderer(EntityRendererProvider.Context ctx) {
		super(ctx);
	}

	@Override
	public void render(SyndicateCriminal entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
		super.render(entity, yaw, partialTick, pose, buffers, light);
		if (entity instanceof WardenKrossBoss kross && kross.taserTargetId() >= 0 && !BeamClashClient.hides(kross)) {
			Entity t = kross.level().getEntity(kross.taserTargetId());
			if (t instanceof LivingEntity victim) {
				renderTaser(kross, victim, partialTick, pose, buffers);
			}
		}
	}

	@Override
	public boolean shouldRender(SyndicateCriminal entity, Frustum frustum, double camX, double camY, double camZ) {
		if (entity instanceof WardenKrossBoss k && k.taserTargetId() >= 0 && entity.distanceToSqr(camX, camY, camZ) < 64 * 64) {
			return true;
		}
		return super.shouldRender(entity, frustum, camX, camY, camZ);
	}

	private void renderTaser(WardenKrossBoss kross, LivingEntity victim, float partialTick, PoseStack pose, MultiBufferSource buffers) {
		Vec3 at = new Vec3(Mth.lerp(partialTick, kross.xo, kross.getX()), Mth.lerp(partialTick, kross.yo, kross.getY()),
				Mth.lerp(partialTick, kross.zo, kross.getZ()));
		Vec3 from = kross.taserFrom(partialTick).subtract(at);
		Vec3 vAt = new Vec3(Mth.lerp(partialTick, victim.xo, victim.getX()), Mth.lerp(partialTick, victim.yo, victim.getY()),
				Mth.lerp(partialTick, victim.zo, victim.getZ()));
		Vec3 to = vAt.add(0, victim.getBbHeight() * 0.55, 0).subtract(at);
		Vec3 cam = entityRenderDispatcher.camera.getPosition().subtract(at);
		PoseStack.Pose last = pose.last();
		// a jagged line: 8 segments, re-jittered every tick
		int n = 8;
		Vec3[] pts = new Vec3[n + 1];
		long seed = kross.tickCount * 341873128712L + kross.getId();
		Vec3 d = to.subtract(from);
		for (int i = 0; i <= n; i++) {
			double t = i / (double) n;
			double j = (i == 0 || i == n) ? 0 : 0.18;
			seed = seed * 6364136223846793005L + 1442695040888963407L;
			double ox = ((seed >>> 40) & 0xFF) / 255.0 - 0.5;
			double oy = ((seed >>> 24) & 0xFF) / 255.0 - 0.5;
			double oz = ((seed >>> 8) & 0xFF) / 255.0 - 0.5;
			pts[i] = from.add(d.scale(t)).add(ox * j, oy * j, oz * j);
		}
		float flicker = 0.8f + 0.2f * Mth.sin((kross.tickCount + partialTick) * 2.3f);
		// glow first, core second -- never interleave the two buffers (BeamDraw#beam)
		VertexConsumer glow = buffers.getBuffer(RenderType.debugQuads());
		for (int i = 0; i < n; i++) {
			BeamDraw.segment(glow, last, pts[i], pts[i + 1], cam, 0.07f, 0.07f, 0x55BBFF, 0.35f * flicker, 0.35f * flicker);
		}
		VertexConsumer core = buffers.getBuffer(RenderType.lightning());
		for (int i = 0; i < n; i++) {
			BeamDraw.segment(core, last, pts[i], pts[i + 1], cam, 0.022f, 0.022f, 0xCCF0FF, 0.95f * flicker, 0.95f * flicker);
		}
	}
}
