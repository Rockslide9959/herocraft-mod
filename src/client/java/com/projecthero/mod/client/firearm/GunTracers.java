package com.projecthero.mod.client.firearm;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.projecthero.mod.client.darkseid.BeamDraw;
import com.projecthero.mod.network.GunTracerPayload;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * v0.15.16: bullet tracers and impact flashes ({@link GunTracerPayload}). Each bullet is a short hot streak racing from
 * the muzzle to where it struck (about 30 blocks a tick, so a long sniper shot is visibly fast, not instant), leaving a
 * faint fading line of smoke-light behind it, and a small flash where it lands -- orange sparks off a block, a red
 * burst off a creature. Additive, gone within about a quarter of a second.
 */
public final class GunTracers {
	private record Tracer(Vec3 from, Vec3 to, int kind, int hit, float born, double len) {
	}

	private static final List<Tracer> LIVE = new ArrayList<>();
	private static final float SPEED = 30f; // blocks per tick
	private static final float FADE = 4f;   // ticks the trail lingers after the head lands

	private GunTracers() {
	}

	public static void initialize() {
		ClientPlayNetworking.registerGlobalReceiver(GunTracerPayload.TYPE, (payload, context) -> context.client().execute(() -> add(payload)));
		WorldRenderEvents.AFTER_TRANSLUCENT.register(GunTracers::render);
		ClientPlayConnectionEvents.DISCONNECT.register((h, c) -> LIVE.clear());
	}

	private static float now() {
		Minecraft mc = Minecraft.getInstance();
		return mc.level == null ? 0f : mc.level.getGameTime() + mc.getTimer().getGameTimeDeltaPartialTick(false);
	}

	private static void add(GunTracerPayload p) {
		if (LIVE.size() > 256) {
			LIVE.remove(0);
		}
		Vec3 from = new Vec3(p.fx(), p.fy(), p.fz());
		Vec3 to = new Vec3(p.tx(), p.ty(), p.tz());
		LIVE.add(new Tracer(from, to, p.kind(), p.hit(), now(), from.distanceTo(to)));
	}

	private static void render(WorldRenderContext ctx) {
		if (LIVE.isEmpty() || ctx.consumers() == null) {
			return;
		}
		float t = now();
		Vec3 cam = ctx.camera().getPosition();
		PoseStack pose = ctx.matrixStack();
		MultiBufferSource buffers = ctx.consumers();
		VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
		pose.pushPose();
		pose.translate(-cam.x, -cam.y, -cam.z);
		PoseStack.Pose last = pose.last();
		Iterator<Tracer> it = LIVE.iterator();
		while (it.hasNext()) {
			Tracer tr = it.next();
			float age = t - tr.born;
			float travel = (float) tr.len / SPEED;
			if (age > travel + FADE || age < -1f) {
				it.remove();
				continue;
			}
			Vec3 dir = tr.to.subtract(tr.from).normalize();
			boolean heavy = tr.kind >= 2;
			float width = tr.kind == 2 ? 0.018f : tr.kind == 3 ? 0.045f : 0.028f;
			// the head: a short bright streak racing along
			float headDist = Math.min((float) tr.len, age * SPEED);
			if (age <= travel) {
				float streak = Math.min(headDist, tr.kind == 3 ? 7f : 3.5f);
				Vec3 head = tr.from.add(dir.scale(headDist));
				Vec3 tail = tr.from.add(dir.scale(headDist - streak));
				BeamDraw.segment(vc, last, tail, head, cam, width * 0.4f, width, 0xFFC860, 0.0f, 0.9f);
				BeamDraw.segment(vc, last, tail, head, cam, width * 0.15f, width * 0.4f, 0xFFF4D8, 0.0f, 1.0f);
			}
			// the faint line it leaves behind, fading
			float trail = age <= travel ? 0.18f : 0.18f * (1f - (age - travel) / FADE);
			if (heavy || tr.kind == 1) {
				Vec3 end = tr.from.add(dir.scale(headDist));
				BeamDraw.segment(vc, last, tr.from, end, cam, width * 0.5f, width * 0.5f, 0xFFE0B0, trail * 0.6f, trail);
			}
			// impact flash once the head lands
			if (tr.hit != 0 && age >= travel && age <= travel + 2.5f) {
				float f = 1f - (age - travel) / 2.5f;
				int rgb = tr.hit == 2 ? 0xFF3020 : 0xFFB040;
				float s = (tr.hit == 2 ? 0.25f : 0.2f) * (heavy ? 1.5f : 1f) * (0.6f + 0.4f * f);
				for (int i = 0; i < 3; i++) {
					double ang = i * Math.PI / 3 + tr.born;
					Vec3 axis = perpendicular(dir, ang).scale(s);
					BeamDraw.segment(vc, last, tr.to.subtract(axis), tr.to.add(axis), cam, s * 0.15f, s * 0.15f, rgb, f * 0.9f, f * 0.9f);
				}
				BeamDraw.segment(vc, last, tr.to.subtract(dir.scale(s * 0.6)), tr.to.add(dir.scale(0.02)), cam, s * 0.5f, s * 0.1f, 0xFFF0D0, f, f * 0.4f);
			}
		}
		pose.popPose();
	}

	private static Vec3 perpendicular(Vec3 dir, double angle) {
		Vec3 up = Math.abs(dir.y) > 0.95 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
		Vec3 a = dir.cross(up).normalize();
		Vec3 b = dir.cross(a).normalize();
		return a.scale(Mth.cos((float) angle)).add(b.scale(Mth.sin((float) angle)));
	}
}
