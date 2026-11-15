package com.projecthero.mod.client.sentry;

import com.projecthero.mod.sentry.Sentry;
import com.projecthero.mod.sentry.SentryConfig;
import com.projecthero.mod.sentry.ShameRooms;
import com.projecthero.mod.sentry.data.SentryState;

import net.minecraft.client.CameraType;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.5 the takeover, from the player's chair, and the Void's other client-only effects:
 * <ul>
 *   <li><b>The body is not yours</b>: the camera is forced into third person behind it (your own choice comes back afterwards), the
 *   movement / jump / sprint / sneak keys are let go every tick, and the body turns the way the Void drives it (at its synced target).
 *   The screen bleeds black at the edges with a gold rim; "THE VOID HAS YOU" and how long until the mind opens.</li>
 *   <li><b>The Shame Rooms</b> open on their own once the mind is open ({@link ShameRoomsScreen}); closed with Esc, they come back after
 *   5 s.</li>
 *   <li><b>The sky</b> ({@link #skyStrength}, read by the fog / sky / day-time mixins): dark near a takeover (full within 24 blocks,
 *   gone by 72) and a little dark around a Sentry past 90 Void; near a takeover the sky shows midnight ({@link #midnight}). Client only:
 *   the world's time never changes.</li>
 *   <li><b>Thresholds</b> on your own body: whispers from 50 Void, the aim drifting toward the nearest living thing from 75, a heartbeat
 *   from 90.</li>
 * </ul>
 */
public final class VoidTakeoverClient {
	private static CameraType savedCamera;
	private static long screenClosedAt;
	private static long nextWhisper;
	private static long nextBeat;
	private static float sky;

	private VoidTakeoverClient() {
	}

	public static void reset() {
		savedCamera = null;
		sky = 0f;
	}

	public static boolean active(Minecraft mc) {
		return mc.player != null && mc.level != null && Sentry.inTakeover(mc.player);
	}

	/** The Shame Rooms screen was closed (Esc): it may come back after 5 s. */
	static void screenClosed() {
		screenClosedAt = net.minecraft.Util.getMillis();
	}

	public static void startTick(Minecraft mc) {
		if (mc.player == null || mc.level == null) {
			sky = 0f;
			return;
		}
		sky = computeSky(mc);
		SentryState s = Sentry.peek(mc.player);
		boolean on = s != null && s.hasPower && s.inTakeover();
		if (on) {
			if (savedCamera == null) {
				savedCamera = mc.options.getCameraType();
			}
			if (mc.options.getCameraType() != CameraType.THIRD_PERSON_BACK) {
				mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
			}
			mc.options.keyUp.setDown(false);
			mc.options.keyDown.setDown(false);
			mc.options.keyLeft.setDown(false);
			mc.options.keyRight.setDown(false);
			mc.options.keyJump.setDown(false);
			mc.options.keySprint.setDown(false);
			mc.options.keyShift.setDown(false);
			mc.options.keyAttack.setDown(false);
			mc.options.keyUse.setDown(false);
			faceTheTarget(mc.player, s);
			long now = mc.level.getGameTime();
			if (ShameRooms.mindOpen(s, now) && mc.screen == null && net.minecraft.Util.getMillis() - screenClosedAt > 5000L) {
				mc.setScreen(new ShameRoomsScreen());
			}
		} else if (savedCamera != null) {
			mc.options.setCameraType(savedCamera);
			savedCamera = null;
			if (mc.screen instanceof ShameRoomsScreen) {
				mc.setScreen(null);
			}
		}
		if (s != null && s.hasPower && !on) {
			thresholdSounds(mc, s);
			aimDrift(mc, s);
		}
	}

	private static void faceTheTarget(Player player, SentryState s) {
		if (s.takeoverTarget >= 0 && player.level().getEntity(s.takeoverTarget) instanceof LivingEntity t && t.isAlive()) {
			Vec3 at = t.position().add(0, t.getBbHeight() * 0.5, 0);
			Vec3 d = at.subtract(player.getEyePosition());
			double flat = Math.sqrt(d.x * d.x + d.z * d.z);
			if (flat > 1.0e-3) {
				float yaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90.0f;
				float pitch = Mth.clamp((float) (-Mth.atan2(d.y, flat) * Mth.RAD_TO_DEG), -80.0f, 80.0f);
				player.setYRot(player.getYRot() + Mth.wrapDegrees(yaw - player.getYRot()) * 0.5f);
				player.setYHeadRot(player.getYRot());
				player.setXRot(Mth.lerp(0.5f, player.getXRot(), pitch));
			}
			return;
		}
		Vec3 v = player.getDeltaMovement();
		if (v.x * v.x + v.z * v.z > 0.004) {
			float want = (float) (Mth.atan2(v.z, v.x) * Mth.RAD_TO_DEG) - 90.0f;
			player.setYRot(player.getYRot() + Mth.wrapDegrees(want - player.getYRot()) * 0.3f);
			player.setYHeadRot(player.getYRot());
		}
	}

	private static void thresholdSounds(Minecraft mc, SentryState s) {
		long now = mc.level.getGameTime();
		if (s.voidLevel >= SentryConfig.VOID_WHISPERS && now >= nextWhisper) {
			nextWhisper = now + 200 + mc.level.random.nextInt(300);
			mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMBIENT_SOUL_SAND_VALLEY_MOOD.value(), 0.7f + mc.level.random.nextFloat() * 0.3f, 0.6f));
		}
		if (s.voidLevel >= SentryConfig.VOID_DIMMING && now >= nextBeat) {
			nextBeat = now + 24;
			mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.WARDEN_HEARTBEAT, 0.8f, 0.7f));
		}
	}

	/** From 75 Void the aim drifts toward the nearest living thing ahead (a little a tick; never while a screen is open). */
	private static void aimDrift(Minecraft mc, SentryState s) {
		if (s.voidLevel < SentryConfig.VOID_MISFIRE || mc.screen != null) {
			return;
		}
		Player p = mc.player;
		LivingEntity best = null;
		double bestD = 12.0 * 12.0;
		for (var e : mc.level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(12.0), e -> e != p && e.isAlive())) {
			Vec3 to = e.getEyePosition().subtract(p.getEyePosition());
			if (to.normalize().dot(p.getLookAngle()) < 0.5) {
				continue;
			}
			double d = to.lengthSqr();
			if (d < bestD) {
				bestD = d;
				best = e;
			}
		}
		if (best == null) {
			return;
		}
		float k = (s.voidLevel - SentryConfig.VOID_MISFIRE) / (SentryConfig.VOID_MAX - SentryConfig.VOID_MISFIRE);
		Vec3 d = best.position().add(0, best.getBbHeight() * 0.6, 0).subtract(p.getEyePosition());
		float yaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90.0f;
		float step = Mth.clamp(Mth.wrapDegrees(yaw - p.getYRot()), -1f, 1f) * (0.3f + 0.5f * k);
		p.setYRot(p.getYRot() + step);
	}

	// ---------------------------------------------------------------- the sky

	private static float computeSky(Minecraft mc) {
		float best = 0f;
		for (AbstractClientPlayer other : mc.level.players()) {
			if (!Sentry.inTakeover(other)) {
				continue;
			}
			double d = other.distanceTo(mc.player);
			best = Math.max(best, (float) Mth.clamp(1.0 - (d - 24.0) / 48.0, 0.0, 1.0));
		}
		if (Sentry.voidAt(mc.player, SentryConfig.VOID_DIMMING)) {
			best = Math.max(best, 0.45f);
		}
		return best;
	}

	/** 0..1: how dark the sky is around the camera (the Void nearby). */
	public static float skyStrength() {
		return sky;
	}

	/** Near a takeover the sky shows midnight. */
	public static boolean midnight() {
		return sky >= 0.5f; // (a Sentry past 90 Void alone only dims it to 0.45: no midnight for that)
	}

	// ---------------------------------------------------------------- the view

	/** The screen bleeding black with a gold rim, the banner and the countdown to the mind opening (HUD callback). */
	public static void renderHud(GuiGraphics g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		if (!active(mc) || mc.screen instanceof ShameRoomsScreen) {
			return;
		}
		SentryState s = Sentry.peek(mc.player);
		int w = g.guiWidth();
		int h = g.guiHeight();
		float t = mc.level.getGameTime() + delta.getGameTimeDeltaPartialTick(false);
		int band = Math.max(18, Math.min(w, h) / 6);
		float pulse = 0.85f + 0.15f * Mth.sin(t * 0.2f);
		int a = (int) (230 * pulse);
		g.fillGradient(0, 0, w, band, (a << 24), 0);
		g.fillGradient(0, h - band, w, h, 0, (a << 24));
		for (int x = 0; x < band; x += 2) {
			int aa = (int) (a * (1f - x / (float) band));
			g.fill(x, 0, x + 2, h, aa << 24);
			g.fill(w - x - 2, 0, w - x, h, aa << 24);
		}
		// the gold rim inside the black
		int gold = ((int) (120 * pulse) << 24) | 0xFFC83A;
		g.fill(band / 2, band / 2, w - band / 2, band / 2 + 1, gold);
		g.fill(band / 2, h - band / 2 - 1, w - band / 2, h - band / 2, gold);
		g.fill(band / 2, band / 2, band / 2 + 1, h - band / 2, gold);
		g.fill(w - band / 2 - 1, band / 2, w - band / 2, h - band / 2, gold);
		var font = mc.font;
		Component title = Component.translatable("hud.projecthero.sentry.takeover");
		g.drawCenteredString(font, title, w / 2, band / 2 + 6, 0xFFFFC83A);
		long now = mc.level.getGameTime();
		Component sub;
		if (s.room >= SentryConfig.ROOMS) {
			sub = Component.empty();
		} else if (now < s.mindLockedUntil) {
			long left = (s.mindLockedUntil - now + 19) / 20;
			sub = Component.translatable("hud.projecthero.sentry.mind_locked", left);
		} else {
			sub = Component.translatable("hud.projecthero.sentry.mind_open");
		}
		g.drawCenteredString(font, sub, w / 2, band / 2 + 18, 0xFFBBBBBB);
		Component rooms = Component.translatable("hud.projecthero.sentry.rooms", s.room, SentryConfig.ROOMS, Math.round(s.grip));
		g.drawCenteredString(font, rooms, w / 2, h - band / 2 - 14, 0xFFAA88FF);
	}
}
