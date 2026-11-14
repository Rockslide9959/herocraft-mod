package com.projecthero.mod.client.omnitrix.alien.shockrock;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.client.mutation.pyro.PyroDraw;
import com.projecthero.mod.client.omnitrix.OmnitrixRenderers;
import com.projecthero.mod.client.omnitrix.alien.ShockRockClient;
import com.projecthero.mod.client.omnitrix.alien.brainstorm.BrainstormFx;
import com.projecthero.mod.client.omnitrix.alien.jetray.RecalFx;
import com.projecthero.mod.omnitrix.alien.shockrock.ShockRockKit;
import com.projecthero.mod.omnitrix.entity.AlienProjectileEntity;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.4 Shock Rock's new constructs, drawn: the Blade Wave (a flat crescent of cyan light flying along his aim), the Rock Barrage's plates
 * (tumbling chunks of his rock with a cyan glow), the Energy Lash (a crackling tether from his fist to what it holds, re-rolled every
 * tick) and the Energy Dome (a faint shell of light round where he raised it, brighter at the rim, slowly turning).
 */
public final class ShockRockFx {
	private ShockRockFx() {
	}

	private static boolean registered;

	public static void register() {
		if (registered) {
			return;
		}
		registered = true;
		OmnitrixRenderers.projectileLook("shock_rock_wave", ShockRockFx::wave);
		OmnitrixRenderers.projectileLook("shock_rock_shard", ShockRockFx::shard);
		WorldRenderEvents.AFTER_ENTITIES.register(ShockRockFx::world);
	}

	/** The Blade Wave: a crescent of light, its horns trailing back, facing the way it flies (the carrier's yaw and pitch). */
	private static void wave(AlienProjectileEntity e, float partial, PoseStack pose, MultiBufferSource buffers) {
		float age = e.tickCount + partial;
		float k = Mth.clamp(Math.min(age / 1.5f, (ShockRockKit.WAVE_TICKS + 4 - age) / 3f), 0f, 1f);
		if (k <= 0f) {
			return;
		}
		float s = e.size();
		Vec3 fwd = Vec3.directionFromRotation(e.getXRot(), e.getYRot());
		Vec3 side = fwd.cross(new Vec3(0, 1, 0));
		side = side.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : side.normalize();
		Vec3 c = new Vec3(0, e.getBbHeight() * 0.5, 0);
		List<Vec3> pts = new ArrayList<>();
		for (int i = 0; i <= 12; i++) {
			double u = (i / 12.0) * 2 - 1; // -1..1 across
			double back = (1 - Math.cos(u * Math.PI * 0.5)) * 0.9 * s; // the horns sweep back
			pts.add(c.add(side.scale(u * 1.3 * s)).subtract(fwd.scale(back)));
		}
		int glow = e.colour() & 0xFFFFFF;
		PyroDraw.ribbon(pose, PyroDraw.additive(buffers), pts, u -> 0.32 * s * Math.sin(Math.PI * u), u -> PyroDraw.dim(glow, k * 0.8f));
		PyroDraw.ribbon(pose, PyroDraw.additive(buffers), pts, u -> 0.12 * s * Math.sin(Math.PI * u), u -> PyroDraw.dim(0xFFFFFF, k));
		PyroDraw.glow(pose, PyroDraw.glowBuffer(buffers), c, 0.6f * s, PyroDraw.dim(glow, k * 0.6f));
	}

	/** A Rock Barrage plate: a tumbling chunk of his rock with a cyan glow. */
	private static void shard(AlienProjectileEntity e, float partial, PoseStack pose, MultiBufferSource buffers) {
		float age = e.tickCount + partial;
		float r = e.size() * 0.5f * 16f;
		pose.pushPose();
		pose.translate(0, e.getBbHeight() * 0.5, 0);
		pose.mulPose(com.mojang.math.Axis.XP.rotation(age * 0.7f));
		pose.mulPose(com.mojang.math.Axis.YP.rotation(age * 0.5f + e.getId()));
		float[] uv = ShockRockClient.rockUv();
		com.projecthero.mod.client.omnitrix.AlienParts.box(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(ShockRockClient.SKIN)), -r, -r * 0.6f, -r, r, r * 0.6f, r,
				uv[0], uv[1], uv[2], uv[3], 0xFFFFFFFF, LightTexture.FULL_BRIGHT);
		pose.popPose();
		PyroDraw.glow(pose, PyroDraw.glowBuffer(buffers), new Vec3(0, e.getBbHeight() * 0.5, 0), e.size() * 1.1f, PyroDraw.dim(ShockRockKit.ENERGY, 0.5f));
	}

	private static void world(WorldRenderContext context) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || context.consumers() == null || context.matrixStack() == null) {
			return;
		}
		float partial = context.tickCounter().getGameTimeDeltaPartialTick(false);
		Vec3 cam = context.camera().getPosition();
		PoseStack pose = context.matrixStack();
		for (var pl : mc.level.players()) {
			if (!(pl instanceof AbstractClientPlayer p) || !ShockRockKit.isShockRock(p)) {
				continue;
			}
			Entity held = ShockRockKit.lashed(p);
			if (held != null) {

				Vec3 hand = BrainstormFx.hand(p, partial);
				Vec3 to = held.getPosition(partial).add(0, held.getBbHeight() * 0.55, 0);
				long seed = p.level().getGameTime() * 31L + p.getId();
				RecalFx.bolt(pose, context.consumers(), hand.subtract(cam), to.subtract(cam), 0.16f, 0xFFFFFF, ShockRockKit.ENERGY, 1f, seed);
				RecalFx.bolt(pose, context.consumers(), hand.subtract(cam), to.subtract(cam), 0.08f, ShockRockKit.ENERGY_HOT, ShockRockKit.ENERGY, 0.7f, seed + 7);
			}
			Vec3 dome = ShockRockKit.dome(p);
			if (dome != null) {
				float age = p.tickCount + partial;
				long until = com.projecthero.mod.omnitrix.Omnitrix.state(p).readyAt(ShockRockKit.ID + "." + ShockRockKit.DOME_UNTIL);
				float left = until - (p.level().getGameTime() + partial);
				float grow = Mth.clamp((ShockRockKit.DOME_TICKS - left) / 5f, 0f, 1f);
				float fade = Mth.clamp(left / 10f, 0f, 1f);
				pose.pushPose();
				pose.translate(dome.x - cam.x, dome.y - cam.y, dome.z - cam.z);
				PyroDraw.sphere(pose, PyroDraw.additive(context.consumers()), (float) ShockRockKit.DOME_RADIUS * grow,
						PyroDraw.dim(ShockRockKit.ENERGY_HOT, 0.10f * fade), PyroDraw.dim(ShockRockKit.ENERGY, 0.28f * fade), 0.02f, age, LightTexture.FULL_BRIGHT);
				pose.popPose();
			}
		}
	}
}
