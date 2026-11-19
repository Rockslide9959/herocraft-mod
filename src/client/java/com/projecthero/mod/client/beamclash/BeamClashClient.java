package com.projecthero.mod.client.beamclash;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.beamclash.network.BeamClashForfeitPayload;
import com.projecthero.mod.beamclash.network.BeamClashMashPayload;
import com.projecthero.mod.beamclash.network.BeamClashPayload;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.Entity;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import org.joml.Matrix4f;

/**
 * v0.17.3: beam clashing on the client -- the left click ({@code BeamClashInputMixin}), the tug bar for whoever is in the clash,
 * and for everyone the two beams driven into a blazing struggle point. Each owner's own beam keeps drawing underneath; these are
 * drawn thick over them. The server sends every tick, so a clash that goes quiet for a few ticks is simply dropped.
 *
 * <p>v0.17.5: the lock. While you are in a clash every key but attack (the push), chat, the player list, screenshots and the camera
 * keys is held off ({@code BeamClashKeyMixin}), you hang where you are, and the first Escape (or anything else that would open the
 * pause menu) forfeits instead of pausing ({@code BeamClashInputMixin}). The HUD is a tug-of-war bar at the top middle: your side
 * on the left, the key to mash, the time left and "Esc to forfeit"; the result shows on the action bar.
 */
public final class BeamClashClient {
	private static final int BAR_W = 182;
	private static final int BAR_H = 7;
	private static final int TEXT_W = 220;

	private static final class View {
		BeamClashPayload now;
		BeamClashPayload before;
		long seen;

		Vec3 point(float partial) {
			return before == null ? now.point() : before.point().lerp(now.point(), partial);
		}
	}

	private static final Map<Integer, View> CLASHES = new ConcurrentHashMap<>();
	/** The local player is locked (read by the key mixin every frame, so a plain flag). */
	private static volatile boolean locked;
	private static boolean lockedBefore;
	private static boolean noGravityBefore;
	/** The clash id this player already forfeited, so one Escape sends one forfeit. */
	private static int forfeited = -1;

	private BeamClashClient() {
	}

	public static void initialize() {
		ClientPlayNetworking.registerGlobalReceiver(BeamClashPayload.TYPE, (payload, context) -> receive(payload));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			CLASHES.clear();
			locked = false;
			lockedBefore = false;
			forfeited = -1;
		});
		ClientTickEvents.START_CLIENT_TICK.register(client -> tickLock());
		WorldRenderEvents.AFTER_ENTITIES.register(BeamClashClient::render);
		HudRenderCallback.EVENT.register(BeamClashClient::hud);
	}

	private static void receive(BeamClashPayload p) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) {
			return;
		}
		if (p.over()) {
			CLASHES.remove(p.id());
			result(mc, p);
			return;
		}
		View v = CLASHES.computeIfAbsent(p.id(), k -> new View());
		v.before = v.now;
		v.now = p;
		v.seen = mc.level.getGameTime();
		if (mc.level.random.nextBoolean()) {
			Vec3 c = p.point();
			for (int i = 0; i < 3; i++) {
				mc.level.addParticle(ParticleTypes.ELECTRIC_SPARK, c.x, c.y, c.z, (mc.level.random.nextDouble() - 0.5) * 0.9,
						(mc.level.random.nextDouble() - 0.3) * 0.9, (mc.level.random.nextDouble() - 0.5) * 0.9);
			}
		}
	}

	/** The clash the local player is fighting, or null. */
	private static View mine() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null) {
			return null;
		}
		long now = mc.level.getGameTime();
		for (View v : CLASHES.values()) {
			if (now - v.seen > 6) {
				continue;
			}
			if (v.now.a() == mc.player.getId() || v.now.b() == mc.player.getId()) {
				return v;
			}
		}
		return null;
	}

	/** This entity's beam is locked in a clash: the clash draws it, so its usual beam stays hidden. */
	public static boolean hides(net.minecraft.world.entity.Entity e) {
		long now = e.level().getGameTime();
		for (View v : CLASHES.values()) {
			if (now - v.seen <= 6 && (v.now.a() == e.getId() || v.now.b() == e.getId())) {
				return true;
			}
		}
		return false;
	}

	/** The same for a beam sent with no owner, by where it starts. */
	public static boolean hidesFrom(Vec3 start) {
		for (View v : CLASHES.values()) {
			if (v.now.fromA().distanceToSqr(start) < 9.0 || v.now.fromB().distanceToSqr(start) < 9.0) {
				return true;
			}
		}
		return false;
	}

	/** In a clash, server particles this near your camera would only blind you. */
	public static boolean blocksParticleAt(double x, double y, double z) {
		Minecraft mc = Minecraft.getInstance();
		return !CLASHES.isEmpty() && mc.gameRenderer != null && mine() != null
				&& mc.gameRenderer.getMainCamera().getPosition().distanceToSqr(x, y, z) < 16.0;
	}

	/** A left click: in a clash it pushes, and neither swings nor mines. */
	public static boolean onClick() {
		if (mine() == null) {
			return false;
		}
		ClientPlayNetworking.send(new BeamClashMashPayload());
		return true;
	}

	public static boolean inClash() {
		return mine() != null;
	}

	/** The key mixin's question: is the local player locked in a clash right now. */
	public static boolean locked() {
		return locked;
	}

	/** Escape (or the pause menu opening) during a clash: give it up. True when it was taken (the menu then doesn't open). */
	public static boolean onPause() {
		View v = mine();
		if (v == null) {
			return false;
		}
		if (forfeited != v.now.id()) {
			forfeited = v.now.id();
			ClientPlayNetworking.send(new BeamClashForfeitPayload());
		}
		return true;
	}

	/** Hang where you are: no drifting, no falling, for as long as the clash holds you. */
	private static void tickLock() {
		Minecraft mc = Minecraft.getInstance();
		boolean now = mc.player != null && mc.level != null && mine() != null;
		locked = now;
		if (mc.player == null) {
			lockedBefore = false;
			return;
		}
		if (now && !lockedBefore) {
			noGravityBefore = mc.player.isNoGravity();
			mc.player.setNoGravity(true);
		} else if (!now && lockedBefore) {
			mc.player.setNoGravity(noGravityBefore);
		}
		if (now) {
			mc.player.setDeltaMovement(Vec3.ZERO);
			mc.player.setSprinting(false);
		}
		lockedBefore = now;
	}

	/** The last packet of one of your own clashes: say how it went on the action bar. */
	private static void result(Minecraft mc, BeamClashPayload p) {
		if (mc.player == null || (p.a() != mc.player.getId() && p.b() != mc.player.getId())) {
			return;
		}
		int me = mc.player.getId();
		String key;
		ChatFormatting colour;
		if (p.winner() == me) {
			key = p.forfeit() ? "hud.projecthero.beam_clash.they_forfeit" : "hud.projecthero.beam_clash.won";
			colour = ChatFormatting.GOLD;
		} else if (p.winner() >= 0) {
			key = p.forfeit() ? "hud.projecthero.beam_clash.you_forfeit" : "hud.projecthero.beam_clash.lost";
			colour = ChatFormatting.RED;
		} else {
			key = "hud.projecthero.beam_clash.draw";
			colour = ChatFormatting.GRAY;
		}
		mc.gui.setOverlayMessage(Component.translatable(key).withStyle(colour), false);
	}

	private static void render(WorldRenderContext context) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || context.consumers() == null || context.matrixStack() == null || CLASHES.isEmpty()) {
			return;
		}
		long now = mc.level.getGameTime();
		float partial = context.tickCounter().getGameTimeDeltaPartialTick(false);
		float age = now + partial;
		CLASHES.values().removeIf(v -> now - v.seen > 6);
		PoseStack pose = context.matrixStack();
		Vec3 cam = context.camera().getPosition();
		for (View v : CLASHES.values()) {
			Vec3 point = v.point(partial);
			pose.pushPose();
			pose.translate(-cam.x, -cam.y, -cam.z);
			Matrix4f m = pose.last().pose();
			// one buffer at a time: asking for a second closes the first
			VertexConsumer solid = context.consumers().getBuffer(RenderType.debugQuads());
			float wobble = 0.08f * Mth.sin(age * 1.7f);
			Vec3 startA = start(v.now.fromA(), point, cam);
			Vec3 startB = start(v.now.fromB(), point, cam);
			ribbon(solid, m, cam, startA, point, 0.75f + wobble, v.now.rgbA(), 0.72f);
			ribbon(solid, m, cam, startB, point, 0.75f - wobble, v.now.rgbB(), 0.72f);
			orb(solid, m, cam, point, 1.15f + 0.18f * Mth.sin(age * 2.3f), v.now.rgbA(), 0.65f);
			orb(solid, m, cam, point, 0.85f + 0.14f * Mth.sin(age * 3.1f + 1f), v.now.rgbB(), 0.75f);
			VertexConsumer glow = context.consumers().getBuffer(RenderType.lightning());
			ribbon(glow, m, cam, startA, point, 0.28f, 0xFFFFFF, 0.9f);
			ribbon(glow, m, cam, startB, point, 0.28f, 0xFFFFFF, 0.9f);
			orb(glow, m, cam, point, 0.6f, 0xFFFFFF, 1f);
			for (int i = 0; i < 6; i++) {
				// the energy spraying sideways off the contact
				float a = age * 0.9f + i * 1.05f + 2.4f * Mth.sin(age * 0.37f + i);
				Vec3 axis = v.now.fromB().subtract(v.now.fromA()).normalize();
				Vec3 up = Math.abs(axis.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
				Vec3 s = axis.cross(up).normalize();
				Vec3 t = axis.cross(s);
				Vec3 tip = point.add(s.scale(Mth.cos(a) * 2.2)).add(t.scale(Mth.sin(a) * 2.2));
				ribbon(glow, m, cam, point, tip, 0.1f, i % 2 == 0 ? v.now.rgbA() : v.now.rgbB(), 0.8f);
			}
			pose.popPose();
		}
	}

	/** A beam fired from your own eyes would fill your screen: from the camera it starts a few blocks out. */
	private static Vec3 start(Vec3 from, Vec3 point, Vec3 cam) {
		if (from.distanceToSqr(cam) > 6.0) {
			return from;
		}
		Vec3 d = point.subtract(from);
		double len = d.length();
		return len < 3.5 ? point : from.add(d.scale(3.0 / len));
	}

	private static void ribbon(VertexConsumer vc, Matrix4f m, Vec3 cam, Vec3 a, Vec3 b, float width, int rgb, float alpha) {
		Vec3 dir = b.subtract(a);
		if (dir.lengthSqr() < 1.0e-6) {
			return;
		}
		Vec3 side = dir.cross(cam.subtract(a.add(b).scale(0.5)));
		if (side.lengthSqr() < 1.0e-8) {
			return;
		}
		side = side.normalize().scale(width * 0.5);
		quad(vc, m, a.subtract(side), a.add(side), b.add(side), b.subtract(side), rgb, alpha);
	}

	/** A filled disc facing the camera. */
	private static void orb(VertexConsumer vc, Matrix4f m, Vec3 cam, Vec3 c, float radius, int rgb, float alpha) {
		Vec3 view = cam.subtract(c).normalize();
		Vec3 up = Math.abs(view.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
		Vec3 s = view.cross(up).normalize();
		Vec3 t = view.cross(s);
		int sides = 16;
		for (int i = 0; i < sides; i++) {
			float a0 = i * Mth.TWO_PI / sides;
			float a1 = (i + 1) * Mth.TWO_PI / sides;
			Vec3 p0 = c.add(s.scale(Mth.cos(a0) * radius)).add(t.scale(Mth.sin(a0) * radius));
			Vec3 p1 = c.add(s.scale(Mth.cos(a1) * radius)).add(t.scale(Mth.sin(a1) * radius));
			quad(vc, m, c, p0, p1, p1, rgb, alpha);
		}
	}

	/** Both windings: these render types may cull. */
	private static void quad(VertexConsumer vc, Matrix4f m, Vec3 a, Vec3 b, Vec3 c, Vec3 d, int rgb, float alpha) {
		vertex(vc, m, a, rgb, alpha);
		vertex(vc, m, b, rgb, alpha);
		vertex(vc, m, c, rgb, alpha);
		vertex(vc, m, d, rgb, alpha);
		vertex(vc, m, d, rgb, alpha);
		vertex(vc, m, c, rgb, alpha);
		vertex(vc, m, b, rgb, alpha);
		vertex(vc, m, a, rgb, alpha);
	}

	private static void vertex(VertexConsumer vc, Matrix4f m, Vec3 p, int rgb, float alpha) {
		vc.addVertex(m, (float) p.x, (float) p.y, (float) p.z).setColor(((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f, alpha);
	}

	private static void hud(GuiGraphics g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		View v = mine();
		if (v == null || mc.options.hideGui) {
			return;
		}
		boolean sideA = v.now.a() == mc.player.getId();
		// the bar reads from the player's side: yours on the left, and winning fills it
		float mine = (sideA ? v.now.balance() : -v.now.balance()) * 0.5f + 0.5f;
		int yours = 0xFF000000 | (sideA ? v.now.rgbA() : v.now.rgbB());
		int theirs = 0xFF000000 | (sideA ? v.now.rgbB() : v.now.rgbA());
		int cx = g.guiWidth() / 2;
		int x = cx - BAR_W / 2;
		int y = Math.max(40, g.guiHeight() / 7);
		// title and the clock
		Component title = Component.translatable("hud.projecthero.beam_clash.title").withStyle(ChatFormatting.BOLD);
		g.drawCenteredString(mc.font, title, cx, y - 22, 0xFFFFD24A);
		Component clock = Component.translatable("hud.projecthero.beam_clash.time", (v.now.ticksLeft() + 19) / 20);
		g.drawString(mc.font, clock, x + BAR_W - mc.font.width(clock), y - 22, 0xFFAAAAAA);
		// the two names over the ends
		Entity other = mc.level.getEntity(sideA ? v.now.b() : v.now.a());
		Component you = Component.translatable("hud.projecthero.beam_clash.you");
		Component them = other == null ? Component.literal("?") : other.getDisplayName();
		int nameW = BAR_W / 2 - 4;
		g.drawString(mc.font, clip(mc, you, nameW), x, y - 11, 0xFF000000 | brighten(yours));
		FormattedCharSequence t = clip(mc, them, nameW);
		g.drawString(mc.font, t, x + BAR_W - mc.font.width(t), y - 11, 0xFF000000 | brighten(theirs));
		// the tug bar
		int split = x + Math.round(BAR_W * Mth.clamp(mine, 0f, 1f));
		g.fill(x - 1, y - 1, x + BAR_W + 1, y + BAR_H + 1, 0xFF000000);
		g.fill(x, y, split, y + BAR_H, yours);
		g.fill(split, y, x + BAR_W, y + BAR_H, theirs);
		g.fill(x, y, x + BAR_W, y + 1, 0x40FFFFFF);
		g.fill(cx, y + BAR_H - 2, cx + 1, y + BAR_H, 0x80FFFFFF);
		g.fill(split - 1, y - 2, split + 1, y + BAR_H + 2, 0xFFFFFFFF);
		// what to press, wrapped to a fixed width
		float pulse = 0.5f + 0.5f * Mth.sin((mc.level.getGameTime() + delta.getGameTimeDeltaPartialTick(false)) * 1.2f);
		int shade = 180 + Math.round(75 * pulse);
		Component mash = Component.translatable("hud.projecthero.beam_clash.mash", mc.options.keyAttack.getTranslatedKeyMessage());
		int ty = y + BAR_H + 5;
		for (FormattedCharSequence line : mc.font.split(mash, TEXT_W)) {
			g.drawCenteredString(mc.font, line, cx, ty, 0xFF000000 | (shade << 16) | (shade << 8) | shade);
			ty += 10;
		}
		for (FormattedCharSequence line : mc.font.split(Component.translatable("hud.projecthero.beam_clash.forfeit"), TEXT_W)) {
			g.drawCenteredString(mc.font, line, cx, ty, 0xFF9A9A9A);
			ty += 10;
		}
	}

	private static FormattedCharSequence clip(Minecraft mc, Component c, int w) {
		java.util.List<FormattedCharSequence> lines = mc.font.split(c, w);
		return lines.isEmpty() ? FormattedCharSequence.EMPTY : lines.get(0);
	}

	/** Beam colours can be dark: names drawn in them are lifted toward white so they read on any sky. */
	private static int brighten(int argb) {
		int r = (((argb >> 16) & 255) + 255) >> 1;
		int gr = (((argb >> 8) & 255) + 255) >> 1;
		int b = ((argb & 255) + 255) >> 1;
		return (r << 16) | (gr << 8) | b;
	}
}
