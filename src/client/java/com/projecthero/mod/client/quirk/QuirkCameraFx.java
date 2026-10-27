package com.projecthero.mod.client.quirk;

import com.projecthero.mod.quirk.fx.QuirkCamera;
import com.projecthero.mod.quirk.fx.QuirkCameraPayload;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

/**
 * v0.17.1 Quirk framework: the client side of {@link QuirkCamera} -- shake, hit-stop, FOV punch and vignette. Everything is
 * scaled by the vanilla accessibility sliders: "Distortion Effects" ({@code screenEffectScale}) for the shake and the
 * hit-stop, "FOV Effects" ({@code fovEffectScale}) for the FOV punch, so a player who turns those down gets a gentler (or
 * no) camera. Kits can also call these directly from client code ({@link #shake}, {@link #fovPunch}).
 *
 * <ul>
 * <li>shake: noise offsets on the camera's yaw / pitch, decaying linearly ({@code mixin.QuirkCameraShakeMixin});</li>
 * <li>hit-stop: the client level's tick freeze for a blink (mobs and their animations stop dead; your own player and
 * camera keep moving), then it lets go -- the One For All / fighting-game "impact frame" ({@link #tick});</li>
 * <li>FOV punch: a quick kick that springs back over its ticks ({@code mixin.QuirkFovPunchMixin});</li>
 * <li>vignette: a coloured screen-edge glow drawn by the HUD ({@link #drawVignette}).</li>
 * </ul>
 */
public final class QuirkCameraFx {
	private static float shake;
	private static int shakeLeft;
	private static int shakeTotal = 1;
	private static long hitStopEndMs;
	private static boolean frozeLevel;
	private static boolean wasFrozen;
	private static float fov;
	private static int fovLeft;
	private static int fovTotal = 1;
	private static int vignette;
	private static float vignetteStrength;
	private static int vignetteLeft;
	private static int vignetteTotal = 1;

	private QuirkCameraFx() {
	}

	public static void accept(QuirkCameraPayload p) {
		switch (p.kind()) {
			case QuirkCamera.SHAKE -> shake(p.strength(), p.ticks());
			case QuirkCamera.HIT_STOP -> hitStop(p.ticks());
			case QuirkCamera.FOV_PUNCH -> fovPunch(p.strength(), p.ticks());
			case QuirkCamera.VIGNETTE -> vignette(p.colour(), p.strength(), p.ticks());
			default -> {
			}
		}
	}

	private static float distortion() {
		return Minecraft.getInstance().options.screenEffectScale().get().floatValue();
	}

	public static void shake(float strength, int ticks) {
		float s = strength * distortion();
		if (s <= 0.01f || ticks <= 0) {
			return;
		}
		if (s >= current() || shakeLeft <= 0) {
			shake = Math.min(s, 1.5f);
			shakeLeft = ticks;
			shakeTotal = ticks;
		}
	}

	public static void hitStop(int ticks) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || distortion() <= 0.05f) {
			return;
		}
		int t = Math.min(ticks, QuirkCamera.MAX_HIT_STOP);
		hitStopEndMs = Math.max(hitStopEndMs, System.currentTimeMillis() + t * 50L);
		if (!frozeLevel) {
			wasFrozen = mc.level.tickRateManager().isFrozen();
			if (!wasFrozen) {
				mc.level.tickRateManager().setFrozen(true);
			}
			frozeLevel = true;
		}
	}

	public static void fovPunch(float amount, int ticks) {
		float a = amount * Minecraft.getInstance().options.fovEffectScale().get().floatValue();
		if (Math.abs(a) < 0.001f || ticks <= 0) {
			return;
		}
		fov = a;
		fovLeft = ticks;
		fovTotal = ticks;
	}

	public static void vignette(int rgb, float strength, int ticks) {
		vignette = rgb & 0xFFFFFF;
		vignetteStrength = Mth.clamp(strength, 0f, 1f);
		vignetteLeft = ticks;
		vignetteTotal = Math.max(1, ticks);
	}

	/** Every client tick (and on the render thread before the world, for the hit-stop's release). */
	public static void tick() {
		if (shakeLeft > 0) {
			shakeLeft--;
		}
		if (fovLeft > 0) {
			fovLeft--;
		}
		if (vignetteLeft > 0) {
			vignetteLeft--;
		}
		releaseHitStop(false);
	}

	/** Lets go of the hit-stop once it has run (or at once with {@code force}: leaving the world). */
	public static void releaseHitStop(boolean force) {
		if (!frozeLevel) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		if (force || mc.level == null || System.currentTimeMillis() >= hitStopEndMs) {
			if (mc.level != null && !wasFrozen) {
				mc.level.tickRateManager().setFrozen(false);
			}
			frozeLevel = false;
		}
	}

	public static boolean hitStopping() {
		return frozeLevel;
	}

	/** Leaving a world: everything off. */
	public static void reset() {
		releaseHitStop(true);
		shakeLeft = 0;
		fovLeft = 0;
		vignetteLeft = 0;
	}

	private static float current() {
		return shakeLeft <= 0 ? 0f : shake * shakeLeft / (float) shakeTotal;
	}

	private static float noise(int salt, float partial) {
		Minecraft mc = Minecraft.getInstance();
		float t = (mc.level == null ? 0 : mc.level.getGameTime() % 100000) + partial;
		float ph = t * 1.7f + salt * 13.1f;
		return Mth.sin(ph) * 0.6f + Mth.sin(ph * 2.3f + 1.1f) * 0.4f;
	}

	/** Camera yaw offset (degrees) this frame. */
	public static float yaw(float partial) {
		float c = current();
		return c <= 0f ? 0f : noise(1, partial) * c * 1.4f;
	}

	/** Camera pitch offset (degrees) this frame. */
	public static float pitch(float partial) {
		float c = current();
		return c <= 0f ? 0f : noise(2, partial) * c * 1.8f;
	}

	/** The FOV multiplier this frame (1 = none): a kick over the first two ticks, springing back. */
	public static float fovMultiplier(float partial) {
		if (fovLeft <= 0) {
			return 1f;
		}
		float age = fovTotal - fovLeft + partial;
		float env = age < 2f ? age / 2f : Math.max(0f, 1f - (age - 2f) / Math.max(1f, fovTotal - 2f));
		env = env * env * (3f - 2f * env);
		return 1f + fov * env;
	}

	/** The vignette, drawn by the Quirk HUD for every player (any power can be sent one). */
	public static void drawVignette(GuiGraphics g) {
		if (vignetteLeft <= 0 || vignetteStrength <= 0f) {
			return;
		}
		float k = vignetteStrength * vignetteLeft / (float) vignetteTotal;
		int w = g.guiWidth(), h = g.guiHeight();
		int bands = 14;
		int depth = Math.max(8, Math.min(w, h) / 6);
		for (int i = 0; i < bands; i++) {
			float f = 1f - i / (float) bands;
			int a = (int) (k * 120f * f * f);
			if (a <= 0) {
				continue;
			}
			int c = (a << 24) | vignette;
			int d0 = depth * i / bands, d1 = depth * (i + 1) / bands;
			g.fill(0, d0, w, d1, c);
			g.fill(0, h - d1, w, h - d0, c);
			g.fill(d0, d1, d1, h - d1, c);
			g.fill(w - d1, d1, w - d0, h - d1, c);
		}
	}
}
