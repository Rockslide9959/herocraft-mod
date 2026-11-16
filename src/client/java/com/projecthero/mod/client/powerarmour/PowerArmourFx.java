package com.projecthero.mod.client.powerarmour;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.joml.Matrix4f;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import com.projecthero.mod.client.ironman.IronManBoxes;
import com.projecthero.mod.powerarmour.PowerArmour;
import com.projecthero.mod.powerarmour.PowerArmourConfig;
import com.projecthero.mod.powerarmour.PowerArmourMod;
import com.projecthero.mod.powerarmour.PowerArmourState;
import com.projecthero.mod.powerarmour.kit.PowerArmourAbilities;
import com.projecthero.mod.powerarmour.network.PowerArmourFxPayload;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.5 Power Armour: the world effects -- the visor's outlines (mobs, and the Ore Scanner's ores through walls), the Tesla
 * Coil's arcs, the Rail Cannon's beam (hidden while a beam clash hides it), the Shield Projector's bubble, the Repair Drone
 * circling its owner, plus the missile and turret renderers.
 */
public final class PowerArmourFx {
	private static final RenderType XRAY_LINES = new RenderType("projecthero_power_armour_xray_lines", DefaultVertexFormat.POSITION_COLOR_NORMAL,
			VertexFormat.Mode.LINES, 4096, false, false, () -> {
				RenderSystem.setShader(GameRenderer::getRendertypeLinesShader);
				RenderSystem.lineWidth(2.0f);
				RenderSystem.enableBlend();
				RenderSystem.defaultBlendFunc();
				RenderSystem.disableDepthTest();
				RenderSystem.depthMask(false);
				RenderSystem.disableCull();
			}, () -> {
				RenderSystem.enableCull();
				RenderSystem.depthMask(true);
				RenderSystem.enableDepthTest();
				RenderSystem.disableBlend();
				RenderSystem.lineWidth(1.0f);
			}) {
	};

	private record Arc(List<Vec3> points, long until) {
	}

	private static long[] ores = new long[0];
	private static long oresUntil;
	private static final List<Arc> ARCS = new ArrayList<>();

	private PowerArmourFx() {
	}

	public static void initialize() {
		EntityRendererRegistry.register(PowerArmourMod.MISSILE, ArmourMissileRenderer::new);
		EntityRendererRegistry.register(PowerArmourMod.TURRET, SentryTurretRenderer::new);
		ClientPlayNetworking.registerGlobalReceiver(PowerArmourFxPayload.TYPE, (payload, context) -> receive(payload));
		WorldRenderEvents.AFTER_TRANSLUCENT.register(PowerArmourFx::render);
		ClientPlayConnectionEvents.DISCONNECT.register((h, c) -> {
			ores = new long[0];
			ARCS.clear();
		});
	}

	private static void receive(PowerArmourFxPayload p) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) {
			return;
		}
		long now = mc.level.getGameTime();
		List<Double> d = p.data();
		if (p.kind() == PowerArmourFxPayload.ORES) {
			long[] out = new long[d.size() / 3];
			for (int i = 0; i < out.length; i++) {
				out[i] = net.minecraft.core.BlockPos.asLong((int) Math.round(d.get(i * 3)), (int) Math.round(d.get(i * 3 + 1)),
						(int) Math.round(d.get(i * 3 + 2)));
			}
			ores = out;
			oresUntil = now + 50;
		} else if (p.kind() == PowerArmourFxPayload.ARC) {
			List<Vec3> pts = new ArrayList<>();
			for (int i = 0; i + 2 < d.size(); i += 3) {
				pts.add(new Vec3(d.get(i), d.get(i + 1), d.get(i + 2)));
			}
			ARCS.add(new Arc(pts, now + 6));
		}
	}

	/** The local wearer's visor outlines living things within 32 blocks (client entities only). */
	public static boolean visorOutlines(Entity e) {
		Minecraft mc = Minecraft.getInstance();
		Player me = mc.player;
		if (me == null || e == me || !(e instanceof LivingEntity le) || !le.isAlive()) {
			return false;
		}
		PowerArmourState s = PowerArmour.state(me);
		return s != null && s.sealed() && s.visor && e.distanceToSqr(me) < PowerArmourConfig.VISOR_RANGE * PowerArmourConfig.VISOR_RANGE;
	}

	// ================================================================== world render

	private static void render(WorldRenderContext ctx) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || ctx.consumers() == null || ctx.matrixStack() == null) {
			return;
		}
		PoseStack pose = ctx.matrixStack();
		MultiBufferSource buffers = ctx.consumers();
		Vec3 cam = ctx.camera().getPosition();
		float partial = ctx.tickCounter().getGameTimeDeltaPartialTick(false);
		long now = mc.level.getGameTime();
		float time = now + partial;
		pose.pushPose();
		pose.translate(-cam.x, -cam.y, -cam.z);
		// ores through walls (the local wearer, visor on)
		PowerArmourState mine = mc.player == null ? null : PowerArmour.state(mc.player);
		if (ores.length > 0 && now < oresUntil && mine != null && mine.sealed() && mine.visor) {
			VertexConsumer lines = buffers.getBuffer(XRAY_LINES);
			float a = 0.55f + 0.25f * Mth.sin(time * 0.2f);
			for (long l : ores) {
				int x = net.minecraft.core.BlockPos.getX(l), y = net.minecraft.core.BlockPos.getY(l), z = net.minecraft.core.BlockPos.getZ(l);
				LevelRenderer.renderLineBox(pose, lines, x + 0.05, y + 0.05, z + 0.05, x + 0.95, y + 0.95, z + 0.95, 1f, 0.8f, 0.3f, a);
			}
			if (buffers instanceof MultiBufferSource.BufferSource src) {
				src.endBatch(XRAY_LINES);
			}
		}
		// tesla arcs
		for (Iterator<Arc> it = ARCS.iterator(); it.hasNext();) {
			Arc arc = it.next();
			if (now > arc.until) {
				it.remove();
				continue;
			}
			for (int i = 0; i + 1 < arc.points.size(); i++) {
				jagged(pose, buffers.getBuffer(RenderType.lightning()), arc.points.get(i), arc.points.get(i + 1), time, 0xA0C8F0FF);
			}
		}
		// every wearer in view: rail beams, shields, drones
		for (Player p : mc.level.players()) {
			PowerArmourState s = PowerArmour.state(p);
			if (s == null || !s.sealed()) {
				continue;
			}
			Vec3 at = p.getPosition(partial);
			int rgb = s.suit.glowColour();
			if (s.anim == PowerArmourAbilities.ANIM_RAIL && time - s.animStart < PowerArmourConfig.RAIL_BEAM_TICKS
					&& !com.projecthero.mod.client.beamclash.BeamClashClient.hides(p)) {
				float k = 1f - (time - s.animStart) / PowerArmourConfig.RAIL_BEAM_TICKS;
				Vec3 eye = p.getEyePosition(partial).add(0, -0.25, 0);
				Vec3 dir = p.getViewVector(partial);
				Vec3 end = beamEnd(p, eye, dir);
				beam(pose, buffers.getBuffer(RenderType.lightning()), eye.add(dir.scale(0.7)), end, 0.09f + 0.12f * k, (Math.round(k * 200) << 24) | rgb);
				beam(pose, buffers.getBuffer(RenderType.lightning()), eye.add(dir.scale(0.7)), end, 0.04f, (Math.round(k * 255) << 24) | 0xFFFFFF);
			}
			if (s.shieldUntil > now) {
				float left = Math.min(1f, (s.shieldUntil - time) / 10f);
				bubble(pose, buffers.getBuffer(RenderType.lightning()), at.add(0, 1.0, 0), 1.6f, time, (Math.round(55 * left) << 24) | rgb);
			}
			if (s.droneUntil > now) {
				drone(pose, buffers, p, at, time, rgb);
			}
		}
		pose.popPose();
	}

	private static Vec3 beamEnd(Player p, Vec3 eye, Vec3 dir) {
		var hit = p.level().clip(new net.minecraft.world.level.ClipContext(eye, eye.add(dir.scale(PowerArmourConfig.RAIL_RANGE)),
				net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, p));
		return hit.getLocation();
	}

	/** A glowing square-section beam from a to b (half width w, blocks). */
	static void beam(PoseStack pose, VertexConsumer vc, Vec3 a, Vec3 b, float w, int argb) {
		Vec3 d = b.subtract(a);
		double len = d.length();
		if (len < 1e-3) {
			return;
		}
		pose.pushPose();
		pose.translate(a.x, a.y, a.z);
		pose.mulPose(new org.joml.Quaternionf().rotationTo(0f, 1f, 0f, (float) (d.x / len), (float) (d.y / len), (float) (d.z / len)));
		Matrix4f m = pose.last().pose();
		float l = (float) len;
		float[][] sides = { { -w, -w, w, -w }, { w, -w, w, w }, { w, w, -w, w }, { -w, w, -w, -w } };
		for (float[] s : sides) {
			vc.addVertex(m, s[0], 0, s[1]).setColor(argb);
			vc.addVertex(m, s[2], 0, s[3]).setColor(argb);
			vc.addVertex(m, s[2], l, s[3]).setColor(argb);
			vc.addVertex(m, s[0], l, s[1]).setColor(argb);
		}
		pose.popPose();
	}

	private static void jagged(PoseStack pose, VertexConsumer vc, Vec3 a, Vec3 b, float time, int argb) {
		int segs = Math.max(3, (int) (a.distanceTo(b) * 2.5));
		long seed = (long) (time * 0.5f) * 31L + (long) (a.x * 7 + b.z * 13);
		java.util.Random r = new java.util.Random(seed);
		Vec3 prev = a;
		for (int i = 1; i <= segs; i++) {
			Vec3 next = a.lerp(b, i / (double) segs);
			if (i < segs) {
				next = next.add((r.nextDouble() - 0.5) * 0.45, (r.nextDouble() - 0.5) * 0.45, (r.nextDouble() - 0.5) * 0.45);
			}
			beam(pose, vc, prev, next, 0.035f, argb);
			beam(pose, vc, prev, next, 0.012f, 0xFFFFFFFF);
			prev = next;
		}
	}

	/** A shimmering hex-ish bubble (latitude / longitude quads with flickering cells). */
	private static void bubble(PoseStack pose, VertexConsumer vc, Vec3 c, float r, float time, int argb) {
		int lat = 10, lon = 16;
		Matrix4f m = pose.last().pose();
		for (int i = 0; i < lat; i++) {
			double t0 = Math.PI * i / lat, t1 = Math.PI * (i + 1) / lat;
			for (int j = 0; j < lon; j++) {
				double p0 = 2 * Math.PI * j / lon, p1 = 2 * Math.PI * (j + 1) / lon;
				float flick = 0.6f + 0.4f * Mth.sin(time * 0.3f + i * 1.7f + j * 2.3f);
				int a = Math.round(((argb >>> 24) & 0xFF) * flick);
				int col = (a << 24) | (argb & 0xFFFFFF);
				float in = 0.93f;
				Vec3 q0 = sph(c, r, t0, p0), q1 = sph(c, r, t0, p1), q2 = sph(c, r, t1, p1), q3 = sph(c, r, t1, p0);
				Vec3 mid = q0.add(q1).add(q2).add(q3).scale(0.25);
				q0 = mid.lerp(q0, in);
				q1 = mid.lerp(q1, in);
				q2 = mid.lerp(q2, in);
				q3 = mid.lerp(q3, in);
				vc.addVertex(m, (float) q0.x, (float) q0.y, (float) q0.z).setColor(col);
				vc.addVertex(m, (float) q1.x, (float) q1.y, (float) q1.z).setColor(col);
				vc.addVertex(m, (float) q2.x, (float) q2.y, (float) q2.z).setColor(col);
				vc.addVertex(m, (float) q3.x, (float) q3.y, (float) q3.z).setColor(col);
			}
		}
	}

	private static Vec3 sph(Vec3 c, float r, double theta, double phi) {
		return c.add(r * Math.sin(theta) * Math.cos(phi), r * 1.15 * Math.cos(theta), r * Math.sin(theta) * Math.sin(phi));
	}

	/** The Repair Drone: a little voxel quad-rotor circling above its owner's shoulder, a green beam to the suit every second. */
	private static void drone(PoseStack pose, MultiBufferSource buffers, Player p, Vec3 at, float time, int rgb) {
		float a = time * 0.09f;
		Vec3 pos = at.add(Math.cos(a) * 1.1, 2.15 + 0.1 * Math.sin(time * 0.2f), Math.sin(a) * 1.1);
		VertexConsumer vc = buffers.getBuffer(RenderType.entityCutout(SuitRenderer.MODULE_TEX));
		int light = LightTexture.FULL_BRIGHT;
		pose.pushPose();
		pose.translate(pos.x, pos.y, pos.z);
		pose.mulPose(Axis.YP.rotation(-a));
		IronManBoxes.draw(pose, vc, IronManBoxes.Box.swatch(-2.2f, -1.2f, -2.2f, 2.2f, 1.2f, 2.2f, 5f, 1f, 2f, 2f), 32, 32, light,
				OverlayTexture.NO_OVERLAY, 0xFF000000 | 0xC8CCD4);
		for (int i = 0; i < 4; i++) {
			pose.pushPose();
			pose.mulPose(Axis.YP.rotationDegrees(45 + i * 90));
			IronManBoxes.draw(pose, vc, IronManBoxes.Box.swatch(-0.4f, 0.6f, 2f, 0.4f, 1.0f, 4.6f, 1f, 1f, 2f, 2f), 32, 32, light,
					OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
			pose.translate(0, 0, 4.6f / 16f);
			pose.mulPose(Axis.YP.rotationDegrees(time * 80f));
			IronManBoxes.draw(pose, vc, IronManBoxes.Box.swatch(-1.8f, 1.0f, -0.3f, 1.8f, 1.2f, 0.3f, 13f, 1f, 2f, 2f), 32, 32, light,
					OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
			pose.popPose();
		}
		IronManBoxes.draw(pose, buffers.getBuffer(RenderType.eyes(SuitRenderer.MODULE_TEX)),
				IronManBoxes.Box.swatch(-0.6f, -1.6f, -0.6f, 0.6f, -1.2f, 0.6f, 1f, 5f, 2f, 2f), 32, 32, light, OverlayTexture.NO_OVERLAY, 0xFF60FF90);
		pose.popPose();
		if (((int) time) % 20 < 4) {
			beam(pose, buffers.getBuffer(RenderType.lightning()), pos.add(0, -0.1, 0), at.add(0, 1.2, 0), 0.03f, 0x9060FF90);
		}
	}
}
