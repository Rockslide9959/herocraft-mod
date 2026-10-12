package com.projecthero.mod.client.ultron;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.client.darkseid.BeamDraw;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.ultron.UltronSignalPayload;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.1: Ultron in your systems, on the client.
 * <ul>
 *   <li>Network Override's telegraph: a red targeting reticle (a turning square inside a ring, with corner ticks) above
 *       the marked fighter for 1 s -- everyone near sees it.</li>
 *   <li>A jam on <em>your</em> keys: a Network Override fills the screen with red static and "NETWORK OVERRIDE" for 4 s; a
 *       Hijacker's one-key jam shows a flickering "[R] JAMMED" warning above the hotbar for 6 s.</li>
 * </ul>
 * Purely cosmetic -- the server ({@code UltronJam}) refuses the keys. Static client state is cleared on disconnect.
 */
public final class UltronJamClient {
	private static final Map<Integer, Long> RETICLES = new HashMap<>();
	private static long jamUntil;
	private static int jamTicks;
	private static int jamMask;
	private static boolean override;

	private UltronJamClient() {
	}

	public static void initialize() {
		ClientPlayNetworking.registerGlobalReceiver(UltronSignalPayload.TYPE, (payload, ctx) -> ctx.client().execute(() -> receive(payload)));
		WorldRenderEvents.AFTER_ENTITIES.register(UltronJamClient::renderReticles);
		HudRenderCallback.EVENT.register(UltronJamClient::renderHud);
		ClientPlayConnectionEvents.DISCONNECT.register((h, c) -> clear());
	}

	public static void clear() {
		RETICLES.clear();
		jamUntil = 0;
		jamMask = 0;
		override = false;
	}

	private static void receive(UltronSignalPayload p) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) {
			return;
		}
		long now = mc.level.getGameTime();
		if (p.kind() == UltronSignalPayload.RETICLE) {
			RETICLES.put(p.entityId(), now + Math.max(1, p.ticks()));
		} else if (p.kind() == UltronSignalPayload.JAM) {
			// a fresh override always shows; a one-key jam never hides a running override
			boolean ov = p.entityId() == 1;
			if (ov || !override || now >= jamUntil) {
				override = ov;
				jamMask = p.mask();
				jamTicks = Math.max(1, p.ticks());
				jamUntil = now + jamTicks;
			}
		}
	}

	/** Test / harness hook: is a reticle showing over {@code entityId}? */
	public static boolean hasReticle(int entityId) {
		return RETICLES.containsKey(entityId);
	}

	// ---------------------------------------------------------------- the reticle

	private static void renderReticles(WorldRenderContext ctx) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || ctx.consumers() == null || ctx.matrixStack() == null || RETICLES.isEmpty()) {
			return;
		}
		float pt = ctx.tickCounter().getGameTimeDeltaPartialTick(false);
		float now = mc.level.getGameTime() + pt;
		Iterator<Map.Entry<Integer, Long>> it = RETICLES.entrySet().iterator();
		while (it.hasNext()) {
			if (it.next().getValue() < now) {
				it.remove();
			}
		}
		if (RETICLES.isEmpty()) {
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
			for (Map.Entry<Integer, Long> r : RETICLES.entrySet()) {
				Entity e = mc.level.getEntity(r.getKey());
				if (e == null) {
					continue;
				}
				Vec3 at = new Vec3(Mth.lerp(pt, e.xo, e.getX()), Mth.lerp(pt, e.yo, e.getY()) + e.getBbHeight() + 0.75,
						Mth.lerp(pt, e.zo, e.getZ()));
				float left = r.getValue() - now;
				drawReticle(vc, glow, last, at, cam, now, Mth.clamp(left / 20f, 0f, 1f));
			}
		}
		pose.popPose();
	}

	/** A ring with a turning square inside it and four corner ticks, lying flat-ish and facing up -- it closes in as it times out. */
	private static void drawReticle(VertexConsumer vc, boolean glow, PoseStack.Pose pose, Vec3 c, Vec3 cam, float time, float left) {
		float w = glow ? 0.07f : 0.025f;
		int color = glow ? UltronBeamClient.GLOW : UltronBeamClient.CORE;
		float alpha = glow ? 0.9f : 0.95f;
		double ring = 0.42 + 0.35 * left; // shrinks onto the target
		int seg = 20;
		for (int i = 0; i < seg; i++) {
			double a0 = i * Math.PI * 2 / seg;
			double a1 = (i + 1) * Math.PI * 2 / seg;
			BeamDraw.segment(vc, pose, c.add(Math.cos(a0) * ring, 0, Math.sin(a0) * ring), c.add(Math.cos(a1) * ring, 0, Math.sin(a1) * ring), cam,
					w, w, color, alpha, alpha);
		}
		double sq = ring * 0.62;
		double spin = time * 0.12;
		for (int i = 0; i < 4; i++) {
			double a0 = spin + i * Math.PI / 2;
			double a1 = spin + (i + 1) * Math.PI / 2;
			BeamDraw.segment(vc, pose, c.add(Math.cos(a0) * sq, 0, Math.sin(a0) * sq), c.add(Math.cos(a1) * sq, 0, Math.sin(a1) * sq), cam,
					w, w, color, alpha, alpha);
		}
		for (int i = 0; i < 4; i++) {
			double a = i * Math.PI / 2 + Math.PI / 4;
			Vec3 in = c.add(Math.cos(a) * ring * 1.05, 0, Math.sin(a) * ring * 1.05);
			Vec3 out = c.add(Math.cos(a) * (ring + 0.3), 0, Math.sin(a) * (ring + 0.3));
			BeamDraw.segment(vc, pose, in, out, cam, w, w, color, alpha, alpha);
		}
		// a thin drop line down to the head
		BeamDraw.segment(vc, pose, c, c.add(0, -0.55, 0), cam, w * 0.6f, w * 0.6f, color, alpha * 0.6f, 0f);
	}

	// ---------------------------------------------------------------- the jam overlay

	private static void renderHud(GuiGraphics g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null || mc.options.hideGui || jamMask == 0) {
			return;
		}
		long now = mc.level.getGameTime();
		if (now >= jamUntil) {
			jamMask = 0;
			override = false;
			return;
		}
		float pt = delta.getGameTimeDeltaPartialTick(false);
		float left = jamUntil - now - pt;
		int secs = (int) Math.ceil(left / 20f);
		int w = g.guiWidth();
		int h = g.guiHeight();
		Font font = mc.font;
		long seed = now * 31L;
		if (override) {
			// red static: a tint, scan lines and flickering noise blocks, fading over the last second
			float fade = Mth.clamp(left / 20f, 0f, 1f);
			int tint = (int) (0x38 * fade) << 24 | 0x900808;
			g.fill(0, 0, w, h, tint);
			for (int y = (int) (now % 4); y < h; y += 4) {
				g.fill(0, y, w, y + 1, (int) (0x22 * fade) << 24 | 0xFF2010);
			}
			java.util.Random r = new java.util.Random(seed);
			int blocks = 90;
			for (int i = 0; i < blocks; i++) {
				int x = r.nextInt(Math.max(1, w));
				int y = r.nextInt(Math.max(1, h));
				int bw = 4 + r.nextInt(26);
				int bh = 1 + r.nextInt(3);
				int a = (int) ((0x30 + r.nextInt(0x60)) * fade);
				int c = r.nextBoolean() ? 0xFF2A1A : 0xD8D8D8;
				g.fill(x, y, x + bw, y + bh, a << 24 | c);
			}
			Component title = Component.translatable("hud.projecthero.ultron.override").withStyle(net.minecraft.ChatFormatting.BOLD);
			Component sub = Component.translatable("hud.projecthero.ultron.override.sub", secs);
			int jitter = (now / 2) % 3 == 0 ? 1 : 0;
			g.drawString(font, title, (w - font.width(title)) / 2 + jitter, h / 2 - 30, 0xFF3020, true);
			g.drawString(font, sub, (w - font.width(sub)) / 2, h / 2 - 18, 0xFFC8C0, true);
			return;
		}
		// a one-key jam: a flickering warning above the hotbar
		if ((now / 3) % 5 == 0) {
			return; // the flicker
		}
		StringBuilder keys = new StringBuilder();
		for (AbilitySlot s : AbilitySlot.values()) {
			if ((jamMask & (1 << s.ordinal())) != 0) {
				if (keys.length() > 0) {
					keys.append(' ');
				}
				keys.append('[').append(s.defaultKey()).append(']');
			}
		}
		Component line = Component.translatable("hud.projecthero.ultron.jammed", keys.toString(), secs);
		int x = (w - font.width(line)) / 2;
		int y = h - 72;
		g.fill(x - 4, y - 3, x + font.width(line) + 4, y + 11, 0x90200404);
		g.drawString(font, line, x, y, 0xFF4030, true);
	}
}
