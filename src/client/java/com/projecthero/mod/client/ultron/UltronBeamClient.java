package com.projecthero.mod.client.ultron;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.client.darkseid.BeamDraw;
import com.projecthero.mod.ultron.UltronBeamPayload;
import com.projecthero.mod.ultron.UltronFx;

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
 * v0.15.12: Ultron's red beams on the client -- every {@link UltronBeamPayload} becomes a short-lived beam drawn after the
 * entities. Two passes, as {@link BeamDraw} requires: every soft glow into {@code debugQuads} first, then every hot core
 * into {@code lightning} (never interleaved -- that crashes a batched buffer). All of it red.
 */
public final class UltronBeamClient {
	/** The red glow and the hot core every Ultron beam uses. */
	public static final int GLOW = 0xFF1408;
	public static final int CORE = 0xFFB4A8;

	private record Beam(Vec3 a, Vec3 b, int kind, int life, long born) {
	}

	private static final List<Beam> BEAMS = new ArrayList<>();

	private UltronBeamClient() {
	}

	public static void initialize() {
		ClientPlayNetworking.registerGlobalReceiver(UltronBeamPayload.TYPE, (payload, ctx) -> ctx.client().execute(() -> add(payload)));
		WorldRenderEvents.AFTER_ENTITIES.register(UltronBeamClient::render);
		ClientPlayConnectionEvents.DISCONNECT.register((h, c) -> BEAMS.clear());
	}

	private static void add(UltronBeamPayload p) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) {
			return;
		}
		if (BEAMS.size() > 128) {
			BEAMS.remove(0);
		}
		BEAMS.add(new Beam(p.from(), p.to(), p.kind(), Math.max(1, p.life()), mc.level.getGameTime()));
	}

	/** Half-width of each kind's glow. */
	public static float width(int kind) {
		return switch (kind) {
			case UltronFx.SNIPER_SHOT -> 0.16f;
			case UltronFx.TELEGRAPH -> 0.05f;
			case UltronFx.ENCEPHALO -> 0.32f;
			case UltronFx.CANNON -> 0.95f;
			case UltronFx.STREAK -> 0.24f;
			case UltronFx.LASER_SIGHT -> 0.03f;
			case UltronFx.TETHER -> 0.07f;
			default -> 0.12f;
		};
	}

	private static void render(WorldRenderContext ctx) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || ctx.consumers() == null || ctx.matrixStack() == null || BEAMS.isEmpty()) {
			return;
		}
		float pt = ctx.tickCounter().getGameTimeDeltaPartialTick(false);
		float now = mc.level.getGameTime() + pt;
		Iterator<Beam> it = BEAMS.iterator();
		while (it.hasNext()) {
			Beam b = it.next();
			if (now - b.born() > b.life() + 1) {
				it.remove();
			}
		}
		if (BEAMS.isEmpty()) {
			return;
		}
		MultiBufferSource buffers = ctx.consumers();
		Vec3 cam = ctx.camera().getPosition();
		PoseStack pose = ctx.matrixStack();
		pose.pushPose();
		pose.translate(-cam.x, -cam.y, -cam.z);
		PoseStack.Pose last = pose.last();
		for (int pass = 0; pass < 2; pass++) {
			boolean glow = pass == 0;
			VertexConsumer vc = buffers.getBuffer(glow ? RenderType.debugQuads() : RenderType.lightning());
			for (Beam b : BEAMS) {
				float age = now - b.born();
				float fade = Mth.clamp(1f - age / (b.life() + 1f), 0f, 1f);
				if (fade <= 0f) {
					continue;
				}
				draw(vc, glow, last, b.a(), b.b(), cam, b.kind(), fade, now);
			}
		}
		pose.popPose();
	}

	/** One beam of {@code kind}: {@code glow} pass or core pass. Positions in the pose's space; {@code cam} likewise. */
	public static void draw(VertexConsumer vc, boolean glow, PoseStack.Pose pose, Vec3 a, Vec3 b, Vec3 cam, int kind, float fade, float time) {
		float w = width(kind);
		float flicker = 0.85f + 0.15f * Mth.sin(time * 2.1f);
		switch (kind) {
			case UltronFx.TELEGRAPH, UltronFx.LASER_SIGHT -> {
				// a warning line: dim glow, faint core
				if (glow) {
					BeamDraw.beam(vc, true, pose, a, b, cam, w, GLOW, 0.75f * fade * flicker);
				} else {
					BeamDraw.beam(vc, false, pose, a, b, cam, w * 1.4f, GLOW, 0.5f * fade);
				}
			}
			case UltronFx.TETHER -> {
				if (glow) {
					BeamDraw.beam(vc, true, pose, a, b, cam, w, GLOW, 0.8f * fade * flicker);
				} else {
					BeamDraw.beam(vc, false, pose, a, b, cam, w, CORE, 0.6f * fade);
				}
			}
			default -> {
				if (glow) {
					BeamDraw.beam(vc, true, pose, a, b, cam, w, GLOW, fade * flicker);
					// a bright flare where it leaves
					Vec3 d = b.subtract(a);
					double len = d.length();
					if (len > 0.5) {
						BeamDraw.beam(vc, true, pose, a, a.add(d.scale(Math.min(0.8, len) / len)), cam, w * 2.2f, GLOW, 0.8f * fade);
					}
				} else {
					BeamDraw.beam(vc, false, pose, a, b, cam, w, CORE, fade * flicker);
				}
			}
		}
	}
}
