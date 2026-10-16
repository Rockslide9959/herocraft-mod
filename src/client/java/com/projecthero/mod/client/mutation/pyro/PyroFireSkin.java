package com.projecthero.mod.client.mutation.pyro;

import com.mojang.blaze3d.platform.NativeImage;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.hero.visual.MutationVisuals;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * v0.16.5 Pyrokinesis Flame Body (user: "use this skin but make the entire skin have animated fire textures"): the
 * player wears {@link #SKIN} (the user's NameMC flame skin) and, over every part of it, a living fire layer -- a 64x64
 * texture in the player-skin layout that is regenerated every client tick from scrolling, periodic value noise, so the
 * flames climb up the body and flicker. It is drawn additive and full-bright ({@code RenderType.eyes}), so the dark parts
 * of the pattern let the skin show through.
 */
public final class PyroFireSkin {
	/** The user's flame skin (https://namemc.com/skin/5e7bb58498e66c55). */
	public static final ResourceLocation SKIN = ProjectHeroMod.id("textures/entity/mutation/p08_flame_skin.png");
	/** The live fire layer. */
	public static final ResourceLocation FIRE = ProjectHeroMod.id("dynamic/p08_fire_skin");

	private static final int W = 64;
	/** The noise repeats every this many rows, so the upward scroll loops seamlessly. */
	private static final int PERIOD = 64;
	private static DynamicTexture texture;
	private static final float[] LATTICE = new float[16 * 16];

	private PyroFireSkin() {
	}

	/** Whether {@code p} is drawn in the flame skin right now (Flame Body on). */
	public static boolean wearing(Player p) {
		return MutationVisuals.hasFlag(p, "p08.flame_body");
	}

	/** From the client tick: regenerates the fire frame while anyone in view is in Flame Body. */
	public static void tick(Minecraft mc) {
		if (mc.level == null || mc.isPaused()) {
			return;
		}
		boolean any = false;
		for (Player p : mc.level.players()) {
			if (wearing(p)) {
				any = true;
				break;
			}
		}
		if (!any) {
			return;
		}
		if (texture == null) {
			java.util.Random r = new java.util.Random(0x9E3779B9L);
			for (int i = 0; i < LATTICE.length; i++) {
				LATTICE[i] = r.nextFloat();
			}
			texture = new DynamicTexture(W, W, true);
			mc.getTextureManager().register(FIRE, texture);
		}
		paint(texture.getPixels(), mc.level.getGameTime() + mc.getTimer().getGameTimeDeltaPartialTick(false));
		texture.upload();
	}

	/** Periodic value noise on a 16x16 lattice (cells of {@code W / 16} = 4 px), wrapping in both directions. */
	private static float noise(float x, float y) {
		int x0 = Mth.floor(x);
		int y0 = Mth.floor(y);
		float fx = x - x0;
		float fy = y - y0;
		fx = fx * fx * (3 - 2 * fx);
		fy = fy * fy * (3 - 2 * fy);
		float a = lat(x0, y0), b = lat(x0 + 1, y0), c = lat(x0, y0 + 1), d = lat(x0 + 1, y0 + 1);
		return Mth.lerp(fy, Mth.lerp(fx, a, b), Mth.lerp(fx, c, d));
	}

	private static float lat(int x, int y) {
		return LATTICE[Math.floorMod(y, 16) * 16 + Math.floorMod(x, 16)];
	}

	private static void paint(NativeImage img, float time) {
		// rows scroll up 1.5 px a tick; the lattice wraps every PERIOD px so the loop never jumps
		float scroll = (time * 1.5f) % PERIOD;
		for (int y = 0; y < W; y++) {
			for (int x = 0; x < W; x++) {
				float sy = y + scroll;
				float n = noise(x / 4.0f, sy / 4.0f) * 0.55f
						+ noise(x / 2.0f + 5.3f, sy / 2.0f) * 0.3f
						+ noise(x + 1.7f, sy + time * 0.6f) * 0.15f;
				// flames lick upward: within each 8-px band the lower rows burn hotter
				float band = (y % 8) / 7.0f;
				float v = Mth.clamp((n - 0.42f) * 2.6f + band * 0.25f, 0.0f, 1.0f);
				img.setPixelRGBA(x, y, ramp(v));
			}
		}
	}

	/** Black (invisible when added) -> deep red -> orange -> yellow-white; as ABGR for {@link NativeImage}. */
	private static int ramp(float v) {
		float r, g, b;
		if (v < 0.35f) {
			float t = v / 0.35f;
			r = 0.7f * t;
			g = 0.08f * t;
			b = 0.0f;
		} else if (v < 0.75f) {
			float t = (v - 0.35f) / 0.4f;
			r = 0.7f + 0.3f * t;
			g = 0.08f + 0.5f * t;
			b = 0.02f * t;
		} else {
			float t = (v - 0.75f) / 0.25f;
			r = 1.0f;
			g = 0.58f + 0.37f * t;
			b = 0.02f + 0.6f * t;
		}
		int ri = (int) (r * 255), gi = (int) (g * 255), bi = (int) (b * 255);
		return 0xFF000000 | bi << 16 | gi << 8 | ri;
	}

	/** Whether the fire layer has been created (it is only drawn once a frame exists). */
	public static boolean ready() {
		return texture != null;
	}
}
