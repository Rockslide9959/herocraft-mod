package com.projecthero.mod.client.ironman;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.armor.ArmorVisualDefinition;
import com.projecthero.mod.armor.SuperheroArmorVisuals;
import com.projecthero.mod.ironman.gantry.GantryTimeline;
import com.projecthero.mod.ironman.gantry.StarkGantryFloorBlockEntity;
import com.projecthero.mod.ironman.suit.IronManAssemblyPlan;
import com.projecthero.mod.ironman.suit.IronManSuitFx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;

/**
 * v0.15.5, explicit user request: an Iron Man suit on a <b>Stark Gantry</b> goes on (and comes off) in parts, not whole
 * pieces -- every texel of a piece belongs to one {@link GantryTimeline} stage, worked out from the skin-rig model it is
 * painted on:
 * <ul>
 *   <li>cube 0 of a bone is the base layer, cube 1 the outer (jacket / sleeve / pant-leg) layer;</li>
 *   <li>the torso base splits at y = 18 (top half carried on, bottom half built down from it), the arm base at y = 17
 *       (the gauntlet carried on, the arm built up from it), the thigh base at y = 8 (top half carried on, bottom half
 *       built down); the jacket builds top-down, the sleeves bottom-up, the pant-leg layer down the legs and over the
 *       boots; each boot (right, then left), the helmet and the faceplate are carried on whole.</li>
 * </ul>
 * The worn piece is drawn with every texel whose stage has not reached it yet transparent ({@link #texture}), built
 * texels flashing a white-hot then cyan seam for a few ticks; the piece the arm is carrying is drawn with only its own
 * stage's texels ({@link #solo}). Taking the suit off plays the same frames backwards. Textures are made lazily and
 * shared between every frame that looks the same (a frame is "how many texels are on", unless a seam is still hot).
 */
public final class IronManGantryBuild {
	private static final int SEAM_HOT = 0xFFE8FFFF;
	private static final int SEAM_CYAN = 0xFF4FE3FF;
	private static final int GLOW_TICKS = 3;

	/** Per texel of a piece: its stage (-1 = not this piece) and build position. */
	private record Parts(int w, int h, byte[] stage, float[] k) {
	}

	/** A piece under one plan: each texel's appear frame, all of them sorted, the last, and the frames made so far. */
	private static final class Timed {
		final float[] appear;
		final float[] sorted;
		final float last;
		final Map<String, ResourceLocation> bySignature = new HashMap<>();

		Timed(float[] appear) {
			this.appear = appear;
			float[] s = new float[appear.length];
			int n = 0;
			for (float v : appear) {
				if (!Float.isNaN(v)) {
					s[n++] = v;
				}
			}
			s = Arrays.copyOf(s, n);
			Arrays.sort(s);
			sorted = s;
			last = n == 0 ? 0f : s[n - 1];
		}

		int countAtOrBefore(float f) {
			int lo = 0;
			int hi = sorted.length;
			while (lo < hi) {
				int mid = (lo + hi) >>> 1;
				if (sorted[mid] <= f) {
					lo = mid + 1;
				} else {
					hi = mid;
				}
			}
			return lo;
		}
	}

	private static final Map<String, Parts> PARTS = new HashMap<>();
	private static final Map<String, Timed> TIMED = new HashMap<>();
	private static final Map<String, ResourceLocation> SOLO = new HashMap<>();
	private static final Map<String, NativeImage> SOURCES = new HashMap<>();
	private static int made;

	/**
	 * The stage whose part the gantry renderer is drawing in a clamp right now, or -1. Set around one draw call of the
	 * carried piece's armour stand, render thread only.
	 */
	public static int solo = -1;

	private IronManGantryBuild() {
	}

	/** Drops every cached frame (leaving a world / a resource reload). */
	public static void clear() {
		Minecraft mc = Minecraft.getInstance();
		for (Timed t : TIMED.values()) {
			t.bySignature.values().forEach(mc.getTextureManager()::release);
		}
		SOLO.values().forEach(mc.getTextureManager()::release);
		SOURCES.values().forEach(NativeImage::close);
		PARTS.clear();
		TIMED.clear();
		SOLO.clear();
		SOURCES.clear();
		StarkGantryFloorBlockEntity.clearClient();
	}

	// ---------------- reading the sequence ----------------

	/** The running gantry sequence {@code player} stands in (client view), or null. */
	public static StarkGantryFloorBlockEntity sequence(Player player) {
		return player.level() == null ? null : StarkGantryFloorBlockEntity.clientSequenceOf(player.level(), player.getId());
	}

	/**
	 * The texture to draw {@code player}'s piece in {@code slot} with while a gantry sequence is building it on / taking
	 * it off, or null when no sequence is (the caller then draws as usual).
	 */
	public static ResourceLocation texture(Player player, String setId, EquipmentSlot slot, ResourceLocation base, float partialTick) {
		StarkGantryFloorBlockEntity be = sequence(player);
		int bit = IronManSuitFx.bit(slot);
		if (be == null || bit < 0) {
			return null;
		}
		ArmorVisualDefinition def = SuperheroArmorVisuals.get(setId);
		if (def == null) {
			return base;
		}
		return frame(def.geometry(), base, bit, be.plan(), be.frameAt(partialTick));
	}

	/** Is any part of {@code player}'s piece in {@code slot} still missing (its lights stay dark until it is whole)? */
	public static boolean incomplete(Player player, EquipmentSlot slot, String setId, float partialTick) {
		StarkGantryFloorBlockEntity be = sequence(player);
		int bit = IronManSuitFx.bit(slot);
		ArmorVisualDefinition def = SuperheroArmorVisuals.get(setId);
		if (be == null || bit < 0 || def == null) {
			return false;
		}
		Timed t = timed(def.geometry(), def.texture(), bit, be.plan());
		return t != null && be.frameAt(partialTick) < t.last;
	}

	/** The texture of the stand-drawn piece while {@link #solo} is set: only that stage's texels. */
	public static ResourceLocation soloTexture(String setId, EquipmentSlot slot, ResourceLocation base) {
		int bit = IronManSuitFx.bit(slot);
		ArmorVisualDefinition def = SuperheroArmorVisuals.get(setId);
		if (solo < 0 || bit < 0 || def == null) {
			return base;
		}
		int stage = solo;
		String key = def.geometry() + "|" + base + "|" + bit + "|solo" + stage;
		ResourceLocation got = SOLO.get(key);
		if (got != null) {
			return got;
		}
		Parts parts = parts(def.geometry(), base, bit);
		NativeImage src = SOURCES.get(def.geometry() + "|" + base + "|" + bit);
		if (parts == null || src == null) {
			return base;
		}
		NativeImage img = new NativeImage(parts.w(), parts.h(), true);
		img.copyFrom(src);
		for (int i = 0; i < parts.stage().length; i++) {
			if (parts.stage()[i] >= 0 && parts.stage()[i] != stage) {
				img.setPixelRGBA(i % parts.w(), i / parts.w(), 0);
			}
		}
		ResourceLocation id = register("solo_" + bit + "_" + stage, img);
		SOLO.put(key, id);
		return id;
	}

	// ---------------- frames ----------------

	static ResourceLocation frame(ResourceLocation geometry, ResourceLocation base, int bit, GantryTimeline.Plan plan, float f) {
		Timed t = timed(geometry, base, bit, plan);
		Parts parts = parts(geometry, base, bit);
		NativeImage src = SOURCES.get(geometry + "|" + base + "|" + bit);
		if (t == null || parts == null || src == null) {
			return base;
		}
		int fi = (int) Math.floor(f);
		if (fi >= t.last + GLOW_TICKS) {
			return base;
		}
		boolean hot = false;
		for (int i = 0; i < t.appear.length; i++) {
			float a = t.appear[i];
			if (!Float.isNaN(a) && a <= fi && fi - a < GLOW_TICKS && !GantryTimeline.carried(parts.stage()[i])) {
				hot = true;
				break;
			}
		}
		String sig = hot ? "t" + fi : "c" + t.countAtOrBefore(fi);
		ResourceLocation got = t.bySignature.get(sig);
		if (got != null) {
			return got;
		}
		NativeImage img = new NativeImage(parts.w(), parts.h(), true);
		img.copyFrom(src);
		for (int i = 0; i < t.appear.length; i++) {
			float a = t.appear[i];
			if (Float.isNaN(a)) {
				continue;
			}
			int x = i % parts.w();
			int y = i / parts.w();
			if (fi < a) {
				img.setPixelRGBA(x, y, 0);
				continue;
			}
			int orig = src.getPixelRGBA(x, y);
			if (((orig >>> 24) & 0xFF) == 0 || GantryTimeline.carried(parts.stage()[i])) {
				continue;
			}
			float age = fi - a;
			if (age < 1f) {
				img.setPixelRGBA(x, y, IronManAssemblyReveal.argbToAbgr(SEAM_HOT));
			} else if (age < GLOW_TICKS) {
				img.setPixelRGBA(x, y, IronManAssemblyReveal.blendAbgr(orig, IronManAssemblyReveal.argbToAbgr(SEAM_CYAN),
						age < 2f ? 0.6f : 0.3f));
			}
		}
		ResourceLocation id = register(bit + "_" + plan.mask() + "_" + sig, img);
		t.bySignature.put(sig, id);
		return id;
	}

	private static ResourceLocation register(String tag, NativeImage img) {
		ResourceLocation id = ProjectHeroMod.id("dynamic/ironman_gantry/" + (made++) + "_" + tag.replaceAll("[^a-z0-9_]", "_"));
		Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(img));
		return id;
	}

	private static Timed timed(ResourceLocation geometry, ResourceLocation base, int bit, GantryTimeline.Plan plan) {
		String key = geometry + "|" + base + "|" + bit + "|" + plan.mask();
		Timed t = TIMED.get(key);
		if (t != null) {
			return t;
		}
		Parts parts = parts(geometry, base, bit);
		if (parts == null) {
			return null;
		}
		float[] appear = new float[parts.stage().length];
		for (int i = 0; i < appear.length; i++) {
			int s = parts.stage()[i];
			appear[i] = s < 0 ? Float.NaN : plan.appear(s, parts.k()[i]);
		}
		t = new Timed(appear);
		TIMED.put(key, t);
		return t;
	}

	// ---------------- geometry -> per-texel stage ----------------

	private static Parts parts(ResourceLocation geometry, ResourceLocation base, int bit) {
		String key = geometry + "|" + base + "|" + bit;
		if (PARTS.containsKey(key)) {
			return PARTS.get(key);
		}
		Parts parts = null;
		Minecraft mc = Minecraft.getInstance();
		Optional<Resource> texRes = mc.getResourceManager().getResource(base);
		Optional<Resource> geoRes = mc.getResourceManager().getResource(geometry);
		if (texRes.isPresent() && geoRes.isPresent()) {
			try (InputStream in = texRes.get().open();
					Reader reader = new InputStreamReader(geoRes.get().open(), StandardCharsets.UTF_8)) {
				NativeImage src = NativeImage.read(in);
				JsonObject geo = JsonParser.parseReader(reader).getAsJsonObject();
				List<IronManAssemblyReveal.Sample> samples = new ArrayList<>();
				IronManAssemblyReveal.collect(geo, src.getWidth(), src.getHeight(), bit, samples);
				parts = classify(samples, src.getWidth(), src.getHeight(), bit);
				SOURCES.put(key, src);
			} catch (Exception e) {
				ProjectHeroMod.LOGGER.warn("[ProjectHero] could not build the Stark Gantry parts for {}: {}", base, e.toString());
			}
		}
		PARTS.put(key, parts);
		return parts;
	}

	/** Every texel's stage + build position; a texel used twice goes with the later stage (nothing shows early). */
	static Parts classify(List<IronManAssemblyReveal.Sample> samples, int w, int h, int bit) {
		byte[] stage = new byte[w * h];
		float[] k = new float[w * h];
		Arrays.fill(stage, (byte) -1);
		for (IronManAssemblyReveal.Sample s : samples) {
			float[] sk = stageOf(s.bone(), s.cube(), s.pos().y, s.index(), bit);
			int st = (int) sk[0];
			int i = s.index();
			if (stage[i] < 0 || st > stage[i] || st == stage[i] && sk[1] > k[i]) {
				stage[i] = (byte) st;
				k[i] = sk[1];
			}
		}
		return new Parts(w, h, stage, k);
	}

	/** {stage, k} of a texel of {@code bone}'s cube {@code cube} at rest height {@code y} (model pixels). */
	static float[] stageOf(String bone, int cube, float y, int texel, int bit) {
		float scatter = IronManAssemblyPlan.hash("gantry", texel);
		boolean right = bone.startsWith("right_");
		switch (bit) {
			case GantryTimeline.HEAD:
				return new float[] { "faceplate".equals(bone) ? GantryTimeline.FACEPLATE : GantryTimeline.HELMET, 0f };
			case GantryTimeline.CHEST:
				if (bone.contains("gauntlet") || bone.contains("upper_arm") || bone.contains("shoulder") || bone.contains("blade")) {
					if (bone.contains("gauntlet") || bone.contains("blade") || cube == 0 && y < 17f) {
						return new float[] { right ? GantryTimeline.R_GAUNTLET : GantryTimeline.L_GAUNTLET, 0f };
					}
					if (cube == 0) {
						return new float[] { right ? GantryTimeline.R_ARM : GantryTimeline.L_ARM, mix((y - 17f) / 7.5f, scatter, 0.08f) };
					}
					return new float[] { right ? GantryTimeline.R_SLEEVE : GantryTimeline.L_SLEEVE, mix((y - 11.5f) / 13f, scatter, 0.22f) };
				}
				if (cube == 0) {
					return y >= 18f ? new float[] { GantryTimeline.TORSO_TOP, 0f }
							: new float[] { GantryTimeline.TORSO_BOTTOM, mix((18f - y) / 6.5f, scatter, 0.08f) };
				}
				return new float[] { GantryTimeline.JACKET, mix((24.5f - y) / 13f, scatter, 0.22f) };
			case GantryTimeline.LEGS:
				if (cube == 0) {
					return y >= 8f ? new float[] { GantryTimeline.THIGH_TOP, 0f }
							: new float[] { GantryTimeline.THIGH_BOTTOM, mix((8f - y) / 4.4f, scatter, 0.08f) };
				}
				return new float[] { GantryTimeline.PANTS, mix((12.75f - y) / 13.5f, scatter, 0.22f) };
			default:
				if (cube == 0) {
					return new float[] { right ? GantryTimeline.R_BOOT : GantryTimeline.L_BOOT, 0f };
				}
				return new float[] { GantryTimeline.PANTS, mix((12.75f - y) / 13.5f, scatter, 0.22f) };
		}
	}

	private static float mix(float k, float scatter, float amount) {
		k = Math.max(0f, Math.min(1f, k));
		return (1f - amount) * k + amount * scatter;
	}
}
