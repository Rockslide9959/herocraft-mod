package com.projecthero.mod.client.ironman;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

import org.jetbrains.annotations.Nullable;

import com.mojang.blaze3d.platform.NativeImage;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.armor.SuperheroArmorVisuals;
import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.ironman.IronManDamageTiers;
import com.projecthero.mod.ironman.IronManEnergy;
import com.projecthero.mod.ironman.data.TonyStarkState;
import com.projecthero.mod.ironman.entity.IronManSentryEntity;
import com.projecthero.mod.ironman.item.IronManArmorItem;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * v0.15.15: progressive battle damage on every Iron Man suit (replaces the v0.14.29 three-step version), driven by the
 * synced suit integrity through the nine {@link IronManDamageTiers} (45 / 40 / 35 / 30 / 25 / 20 / 15 / 10 / 5 %).
 *
 * <p><b>Textures.</b> Each mark's 64x64 suit texture is upscaled 4x and damaged procedurally, texel by texel, from a
 * seed fixed per mark -- so every tier is a strict superset of the one before (the same blotches grow, the same
 * scratches stay and more join them). In order of appearance:
 * <ol>
 *   <li>grime + desaturation over the whole suit and spreading soot / scorch blotches with a heat-tint rim (tier 1+);</li>
 *   <li>scratches (bright bare-metal lines with a dark shadow) from tier 1, deep gouges from tier 2;</li>
 *   <li>chipped / cracked paint exposing the darker metal underneath (tier 2+);</li>
 *   <li>dents -- shaded hollows lit from the top left (tier 3+);</li>
 *   <li>long jagged cracks with a bright bevel (tier 4+), lens / reactor damage + flicker (tier 4+);</li>
 *   <li>missing plates (tier 6+): a dark inner frame with struts and rivets (exposed wiring from tier 8) inside a torn
 *       edge glowing orange-hot -- the hot edge is also written into a damaged copy of the glowmask, so it glows in the
 *       dark through {@link IronManSuitGlowLayer}.</li>
 * </ol>
 * Glowmask texels (eyes, reactor, palms) are left out of the base damage -- the damaged glowmask handles those (dead
 * pixels, crack lines, dimming). The generated textures are {@link DynamicTexture}s built lazily on the render thread,
 * one set per mark and tier, and released on resource reload and on disconnect.
 *
 * <p><b>Where it applies.</b> Hooked only on the shared armour texture path ({@code SuperheroArmorRenderer},
 * {@link IronManSuitGlowLayer}, the first-person gauntlets): a worn suit, a Sentry Mode suit (its own synced
 * integrity) and anything else rendering a stamped stack ({@code SUIT_INTEGRITY}). Mid-build reveal textures are left
 * alone (only the plain {@code textures/armor/<mark>.png} is swapped).
 *
 * <p><b>Particles.</b> Sparks from 25%, light smoke from 15%, and at 5% heavy smoke, frequent sparks and the odd small
 * electrical arc jumping across the suit. The closed visor HUD cracks too ({@link #renderVisorCracks}).
 */
public final class IronManBattleDamage {
	/** Upscale target: the damage is drawn at (at least) this many pixels across. */
	private static final int TARGET_SIZE = 256;

	private static final Map<String, Entry> CACHE = new HashMap<>();

	private IronManBattleDamage() {
	}

	public static void initialize() {
		ClientTickEvents.END_CLIENT_TICK.register(IronManBattleDamage::clientTick);
		ClientPlayConnectionEvents.DISCONNECT.register((h, mc) -> mc.execute(IronManBattleDamage::releaseAll));
		ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
			@Override
			public ResourceLocation getFabricId() {
				return ProjectHeroMod.id("ironman_battle_damage");
			}

			@Override
			public void onResourceManagerReload(ResourceManager manager) {
				releaseAll();
			}
		});
	}

	/** Free every generated texture (they rebuild lazily from the current resources next time a damaged suit renders). */
	public static void releaseAll() {
		Minecraft mc = Minecraft.getInstance();
		for (Entry e : CACHE.values()) {
			for (ResourceLocation[] arr : new ResourceLocation[][] { e.base, e.glowOn, e.glowOff }) {
				for (ResourceLocation id : arr) {
					if (id != null) {
						mc.getTextureManager().release(id);
					}
				}
			}
		}
		CACHE.clear();
	}

	// ---------------- integrity ----------------

	/** The integrity fraction (0..1) of the suit a piece belongs to, as worn by {@code player}; 1 if unknown. */
	public static float integrity(Player player, String suitId) {
		TonyStarkState s = player.getAttachedOrElse(ModAttachments.TONY_STARK_STATE, null);
		if (s == null || suitId == null) {
			return 1f;
		}
		float max = IronManEnergy.maxIntegrity(suitId);
		Float v = s.suitIntegrity.get(suitId);
		return v == null || max <= 0f ? 1f : Mth.clamp(v / max, 0f, 1f);
	}

	/**
	 * The integrity fraction of a suit piece as rendered on {@code wearer}: the player's suit pool, a Sentry Mode suit's
	 * own synced integrity, else the charge stamped on the stack. 1 (clean) when there is nothing to go on.
	 */
	public static float integrity(@Nullable Entity wearer, @Nullable ItemStack stack, String suitId) {
		if (wearer instanceof Player p) {
			return integrity(p, suitId);
		}
		if (!Float.isNaN(renderingIntegrity) && wearer != null) {
			return renderingIntegrity; // a Sentry Mode suit's stand, mid-render
		}
		if (wearer != null && stack != null && !stack.isEmpty()
				&& stack.get(com.projecthero.mod.item.ModDataComponents.SUIT_INTEGRITY) != null) {
			float max = IronManEnergy.maxIntegrity(suitId);
			return max <= 0f ? 1f : Mth.clamp(IronManEnergy.stackIntegrity(stack, suitId) / max, 0f, 1f);
		}
		return 1f;
	}

	/**
	 * Set by {@code IronManSentryRenderer} around the draw of a Sentry Mode suit's stand: that suit's live integrity
	 * fraction (the stamped stacks only hold the value it deployed with). NaN when not drawing a sentry.
	 */
	public static float renderingIntegrity = Float.NaN;

	/** A Sentry Mode suit's live integrity fraction. */
	public static float sentryIntegrity(IronManSentryEntity s) {
		float max = IronManEnergy.maxIntegrity(s.suitId());
		return max <= 0f ? 1f : Mth.clamp(s.integrity() / max, 0f, 1f);
	}

	/** The suit id of the worn chestplate (or helmet), else null. */
	public static String wornSuit(LivingEntity wearer) {
		if (wearer.getItemBySlot(EquipmentSlot.CHEST).getItem() instanceof IronManArmorItem c) {
			return c.suitId();
		}
		if (wearer.getItemBySlot(EquipmentSlot.HEAD).getItem() instanceof IronManArmorItem h) {
			return h.suitId();
		}
		return null;
	}

	/** The damage tier (0 clean .. 9 wrecked) of the suit {@code wearer} has on, 0 if none. */
	public static int tierOf(LivingEntity wearer) {
		String suit = wornSuit(wearer);
		if (suit == null) {
			return 0;
		}
		ItemStack chest = wearer.getItemBySlot(EquipmentSlot.CHEST);
		return IronManDamageTiers.tier(integrity(wearer, chest, suit));
	}

	// ---------------- texture hooks ----------------

	/** Old (v0.14.29) entry point -- a worn piece on a player. */
	public static ResourceLocation texture(Player player, IronManArmorItem piece, ResourceLocation texture) {
		return texture(player, null, piece, texture);
	}

	/**
	 * Hooked from {@code SuperheroArmorRenderer#getRenderType}: the damaged copy of {@code texture} for a piece at the
	 * suit's current tier, or {@code texture} unchanged (clean suit, or a mid-build reveal texture).
	 */
	public static ResourceLocation texture(@Nullable Entity wearer, @Nullable ItemStack stack, IronManArmorItem piece,
			ResourceLocation texture) {
		if (wearer == null) {
			return texture;
		}
		int tier = IronManDamageTiers.tier(integrity(wearer, stack, piece.suitId()));
		return damagedTexture(piece.armorSetId(), tier, texture);
	}

	/** First-person gauntlets: the worn suit's damaged texture for {@code setId}. */
	public static ResourceLocation firstPersonTexture(Player player, String setId, ResourceLocation texture) {
		String suit = wornSuit(player);
		if (suit == null) {
			return texture;
		}
		return damagedTexture(setId, IronManDamageTiers.tier(integrity(player, suit)), texture);
	}

	private static ResourceLocation damagedTexture(String setId, int tier, ResourceLocation texture) {
		if (tier <= 0 || !SuperheroArmorVisuals.has(setId)) {
			return texture;
		}
		Entry e = entry(setId);
		if (e == null || !texture.equals(e.source)) {
			return texture; // a reveal / gantry / suitcase frame -- leave it
		}
		ensureTier(e, tier);
		return e.base[tier] != null ? e.base[tier] : texture;
	}

	/**
	 * Hooked from {@link IronManSuitGlowLayer}: the glowmask to draw for a piece this frame -- {@code mask} for a clean
	 * suit, else the damaged copy (lens dead pixels / cracks / dimming + the glowing torn plate edges). While the
	 * lights flicker off it is the copy with only the hot edges, or null when there is nothing left to glow.
	 */
	public static @Nullable ResourceLocation glowmask(@Nullable Entity wearer, @Nullable ItemStack stack,
			IronManArmorItem piece, ResourceLocation mask) {
		if (wearer == null) {
			return mask;
		}
		int tier = IronManDamageTiers.tier(integrity(wearer, stack, piece.suitId()));
		boolean off = flickerOff(wearer, tier);
		if (tier <= 0) {
			return mask;
		}
		Entry e = entry(piece.armorSetId());
		if (e == null) {
			return off ? null : mask;
		}
		ensureTier(e, tier);
		if (off) {
			return tier >= IronManDamageTiers.MISSING_PLATE_TIER ? e.glowOff[tier] : null;
		}
		return e.glowOn[tier] != null ? e.glowOn[tier] : mask;
	}

	// ---------------- generation ----------------

	private static final class Entry {
		final String setId;
		final ResourceLocation source;
		final ResourceLocation[] base = new ResourceLocation[IronManDamageTiers.MAX_TIER + 1];
		final ResourceLocation[] glowOn = new ResourceLocation[IronManDamageTiers.MAX_TIER + 1];
		final ResourceLocation[] glowOff = new ResourceLocation[IronManDamageTiers.MAX_TIER + 1];
		final boolean[] tried = new boolean[IronManDamageTiers.MAX_TIER + 1];

		Entry(String setId, ResourceLocation source) {
			this.setId = setId;
			this.source = source;
		}
	}

	private static @Nullable Entry entry(String setId) {
		Entry e = CACHE.get(setId);
		if (e == null) {
			ResourceLocation src = SuperheroArmorVisuals.get(setId).texture();
			if (src == null) {
				return null;
			}
			e = new Entry(setId, src);
			CACHE.put(setId, e);
		}
		return e;
	}

	private static void ensureTier(Entry e, int tier) {
		if (e.tried[tier]) {
			return;
		}
		e.tried[tier] = true;
		NativeImage src = read(e.source);
		if (src == null) {
			ProjectHeroMod.LOGGER.warn("[ProjectHero] no battle-damage source texture for {}", e.source);
			return;
		}
		NativeImage glow = read(ProjectHeroMod.id("textures/armor/" + e.setId + "_glowmask.png"));
		try {
			Damage d = new Damage(src, glow, e.source.toString().hashCode() * 0x9E3779B1, tier);
			d.run();
			String tag = e.setId.replaceAll("[^a-z0-9_]", "_") + "_t" + tier;
			e.base[tier] = register("dynamic/ironman_battle_damage/" + tag, d.baseImage());
			e.glowOn[tier] = register("dynamic/ironman_battle_damage/" + tag + "_glow", d.glowImage(true));
			e.glowOff[tier] = register("dynamic/ironman_battle_damage/" + tag + "_glow_off", d.glowImage(false));
		} catch (RuntimeException ex) {
			ProjectHeroMod.LOGGER.warn("[ProjectHero] battle-damage texture for {} tier {} failed", e.setId, tier, ex);
		} finally {
			src.close();
			if (glow != null) {
				glow.close();
			}
		}
	}

	/** Dev aid: write one mark's generated base + glow textures for {@code tier} to {@code dir} as PNGs. */
	public static void dumpTier(String setId, int tier, java.nio.file.Path dir) throws java.io.IOException {
		ResourceLocation srcId = SuperheroArmorVisuals.get(setId).texture();
		NativeImage src = read(srcId);
		NativeImage glow = read(ProjectHeroMod.id("textures/armor/" + setId + "_glowmask.png"));
		if (src == null) {
			return;
		}
		Damage d = new Damage(src, glow, srcId.toString().hashCode() * 0x9E3779B1, tier);
		d.run();
		java.nio.file.Files.createDirectories(dir);
		try (NativeImage base = d.baseImage(); NativeImage on = d.glowImage(true)) {
			base.writeToFile(dir.resolve(setId + "_t" + tier + ".png"));
			on.writeToFile(dir.resolve(setId + "_t" + tier + "_glow.png"));
		} finally {
			src.close();
			if (glow != null) {
				glow.close();
			}
		}
	}

	private static ResourceLocation register(String path, NativeImage img) {
		ResourceLocation id = ProjectHeroMod.id(path);
		Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(img));
		return id;
	}

	private static NativeImage read(ResourceLocation id) {
		Optional<Resource> res = Minecraft.getInstance().getResourceManager().getResource(id);
		if (res.isEmpty()) {
			return null;
		}
		try (InputStream in = res.get().open()) {
			return NativeImage.read(in);
		} catch (Exception e) {
			return null;
		}
	}

	/**
	 * One tier's damage for one mark, drawn into float RGB buffers at the upscaled size. Every random choice comes from
	 * {@link #seed} (fixed per mark) and the element's index, never from the tier, so tiers only ever add damage.
	 */
	private static final class Damage {
		final int w;
		final int h;
		final int s;      // upscale factor
		final int seed;
		final int tier;
		final float[] r;
		final float[] g;
		final float[] b;
		final int[] a;
		final boolean[] lit;     // glowmask texel (or its protected margin): no base damage
		final float[] gr;        // glow buffers
		final float[] gg;
		final float[] gb;
		final float[] hr;        // hot-edge glow (kept separately, shown even while the lights flicker off)
		final float[] hg;
		final float[] hb;
		final boolean[] plate;   // pixel inside a missing plate

		Damage(NativeImage src, @Nullable NativeImage glow, int seed, int tier) {
			int sw = src.getWidth();
			int sh = src.getHeight();
			this.s = Math.max(1, TARGET_SIZE / Math.max(sw, sh));
			this.w = sw * s;
			this.h = sh * s;
			this.seed = seed;
			this.tier = tier;
			int n = w * h;
			r = new float[n];
			g = new float[n];
			b = new float[n];
			a = new int[n];
			lit = new boolean[n];
			gr = new float[n];
			gg = new float[n];
			gb = new float[n];
			hr = new float[n];
			hg = new float[n];
			hb = new float[n];
			plate = new boolean[n];
			for (int y = 0; y < h; y++) {
				for (int x = 0; x < w; x++) {
					int c = src.getPixelRGBA(x / s, y / s);
					int i = y * w + x;
					a[i] = (c >>> 24) & 0xFF;
					r[i] = c & 0xFF;
					g[i] = (c >> 8) & 0xFF;
					b[i] = (c >> 16) & 0xFF;
				}
			}
			if (glow != null) {
				boolean[] litBase = new boolean[sw * sh];
				for (int y = 0; y < sh; y++) {
					for (int x = 0; x < sw; x++) {
						int gx = x * glow.getWidth() / sw;
						int gy = y * glow.getHeight() / sh;
						int c = glow.getPixelRGBA(gx, gy);
						int ga = (c >>> 24) & 0xFF;
						int sum = (c & 0xFF) + ((c >> 8) & 0xFF) + ((c >> 16) & 0xFF);
						litBase[y * sw + x] = ga > 16 && sum > 48;
					}
				}
				boolean[] prot = protectLights(litBase, sw, sh);
				for (int y = 0; y < h; y++) {
					for (int x = 0; x < w; x++) {
						int i = y * w + x;
						lit[i] = prot[(y / s) * sw + x / s];
						int gx = (x / s) * glow.getWidth() / sw;
						int gy = (y / s) * glow.getHeight() / sh;
						int c = glow.getPixelRGBA(gx, gy);
						float ga = ((c >>> 24) & 0xFF) / 255f;
						gr[i] = (c & 0xFF) * ga;
						gg[i] = ((c >> 8) & 0xFF) * ga;
						gb[i] = ((c >> 16) & 0xFF) * ga;
					}
				}
			}
		}

		boolean paint(int x, int y) {
			if (x < 0 || y < 0 || x >= w || y >= h) {
				return false;
			}
			int i = y * w + x;
			return a[i] != 0 && !lit[i];
		}

		void mix(int x, int y, float cr, float cg, float cb, float t) {
			if (!paint(x, y)) {
				return;
			}
			int i = y * w + x;
			t = Mth.clamp(t, 0f, 1f);
			r[i] += (cr - r[i]) * t;
			g[i] += (cg - g[i]) * t;
			b[i] += (cb - b[i]) * t;
		}

		void scale(int x, int y, float k) {
			if (!paint(x, y)) {
				return;
			}
			int i = y * w + x;
			r[i] *= k;
			g[i] *= k;
			b[i] *= k;
		}

		/** Pixel scale: element sizes are given in 256-wide pixels and scaled to the actual buffer. */
		float px(float v) {
			return v * Math.max(w, h) / (float) TARGET_SIZE;
		}

		void run() {
			if (tier <= 0) {
				return;
			}
			grimeAndSoot();
			if (tier >= 2) {
				chips();
			}
			scratches();
			if (tier >= 2) {
				gouges();
			}
			if (tier >= 3) {
				dents();
			}
			if (tier >= 4) {
				cracks();
			}
			if (tier >= IronManDamageTiers.MISSING_PLATE_TIER) {
				missingPlates();
			}
			if (tier >= IronManDamageTiers.LIGHTS_TIER) {
				damageLights();
			}
		}

		// ---- 1: grime, desaturation and soot / scorch blotches ----
		void grimeAndSoot() {
			float desat = 0.045f * tier;
			float dark = 1f - 0.03f * tier;
			float th = 0.70f - 0.045f * tier;       // tier 1: 0.655 .. tier 9: 0.295
			float sootMax = 0.55f + 0.045f * tier;
			for (int y = 0; y < h; y++) {
				for (int x = 0; x < w; x++) {
					if (!paint(x, y)) {
						continue;
					}
					int i = y * w + x;
					float lum = 0.3f * r[i] + 0.59f * g[i] + 0.11f * b[i];
					r[i] = (r[i] + (lum - r[i]) * desat) * dark;
					g[i] = (g[i] + (lum - g[i]) * desat) * dark;
					b[i] = (b[i] + (lum - b[i]) * desat) * dark;
					float n = fbm(x, y, 1);
					if (n > th) {
						float t = Math.min(1f, (n - th) / 0.16f) * sootMax;
						// streaky soot: a fine noise breaks the blotch up
						t *= 0.75f + 0.25f * noise(x, y, px(3), 11);
						mix(x, y, 22, 18, 16, t);
					} else if (tier >= 2 && n > th - 0.022f) {
						mix(x, y, 150, 104, 48, 0.28f);   // straw / bronze heat tint
					} else if (tier >= 2 && n > th - 0.05f) {
						mix(x, y, 70, 78, 150, 0.22f);    // blue-purple temper colour
					}
				}
			}
		}

		// ---- 2: chipped / cracked paint exposing the metal under it ----
		void chips() {
			float th = 0.80f - 0.032f * (tier - 2);   // tier 2: 0.80 .. tier 9: 0.576
			boolean[] chip = new boolean[w * h];
			for (int y = 0; y < h; y++) {
				for (int x = 0; x < w; x++) {
					if (!paint(x, y)) {
						continue;
					}
					float c = noise(x, y, px(7), 21) * 0.6f + noise(x, y, px(3), 22) * 0.4f;
					// chips cluster near the scorching
					c += (fbm(x, y, 1) - 0.5f) * 0.18f;
					chip[y * w + x] = c > th;
				}
			}
			for (int y = 0; y < h; y++) {
				for (int x = 0; x < w; x++) {
					int i = y * w + x;
					if (chip[i]) {
						float m = 58 + 34 * noise(x, y, px(2), 23);
						mix(x, y, m, m + 2, m + 6, 0.9f);
						boolean edge = (y > 0 && !chip[i - w]) || (x > 0 && !chip[i - 1]);
						if (edge) {
							mix(x, y, 10, 10, 12, 0.55f);   // shadow under the paint lip
						}
					} else if ((x + 1 < w && chip[i + 1]) || (y + 1 < h && chip[i + w])) {
						mix(x, y, 225, 225, 222, 0.32f);    // the paint edge catching the light
					}
				}
			}
		}

		// ---- 3: scratches (tier 1+) and gouges (tier 2+) ----
		void scratches() {
			int total = 170;
			Random rng = new Random(seed ^ 0x51A7C4L);
			for (int i = 0; i < total; i++) {
				int appear = 1 + i * IronManDamageTiers.MAX_TIER / total;
				float x = rng.nextFloat() * w;
				float y = rng.nextFloat() * h;
				double ang = rng.nextDouble() * Math.PI;
				float len = px(6 + rng.nextFloat() * 24);
				float bright = 0.55f + rng.nextFloat() * 0.3f;
				if (appear > tier) {
					continue;
				}
				double dx = Math.cos(ang);
				double dy = Math.sin(ang);
				for (int k = 0; k < len; k++) {
					int sx = (int) (x + dx * k);
					int sy = (int) (y + dy * k);
					mix(sx, sy + 1, 14, 12, 12, 0.42f);
					mix(sx, sy, 214, 216, 220, bright);
				}
			}
		}

		void gouges() {
			int total = 56;
			Random rng = new Random(seed ^ 0x6A09E6L);
			for (int i = 0; i < total; i++) {
				int appear = 2 + i * (IronManDamageTiers.MAX_TIER - 1) / total;
				float x = rng.nextFloat() * w;
				float y = rng.nextFloat() * h;
				double ang = rng.nextDouble() * Math.PI;
				float len = px(8 + rng.nextFloat() * 18);
				if (appear > tier) {
					continue;
				}
				double dx = Math.cos(ang);
				double dy = Math.sin(ang);
				// perpendicular, pointing up-left: the lip that catches the light
				int lx = (int) Math.round(-dy);
				int ly = (int) Math.round(dx);
				if (lx + ly > 0) {
					lx = -lx;
					ly = -ly;
				}
				for (int k = 0; k < len; k++) {
					int sx = (int) (x + dx * k);
					int sy = (int) (y + dy * k);
					float taper = 1f - Math.abs(k / len - 0.5f) * 1.2f;
					mix(sx, sy, 22, 18, 16, 0.9f * taper + 0.1f);
					mix(sx - lx, sy - ly, 30, 26, 24, 0.7f * taper);
					mix(sx + lx, sy + ly, 232, 232, 230, 0.55f * taper);
				}
			}
		}

		// ---- 4: dents ----
		void dents() {
			int total = 40;
			Random rng = new Random(seed ^ 0x3C6EF3L);
			for (int i = 0; i < total; i++) {
				int appear = 3 + i * (IronManDamageTiers.MAX_TIER - 2) / total;
				int cx = rng.nextInt(w);
				int cy = rng.nextInt(h);
				float rad = px(4 + rng.nextFloat() * 8);
				if (appear > tier || !paint(cx, cy)) {
					continue;
				}
				int ri = (int) Math.ceil(rad);
				for (int dy = -ri; dy <= ri; dy++) {
					for (int dx = -ri; dx <= ri; dx++) {
						float d = (float) Math.sqrt(dx * dx + dy * dy) / rad;
						if (d >= 1f) {
							continue;
						}
						// concave: the inner wall facing the top-left light is in shadow, the far wall is lit
						float k = (dx + dy) / (rad * 1.414f) * (1f - d * d) * 2f;
						if (k < 0) {
							scale(cx + dx, cy + dy, 1f + k * 0.6f);
						} else {
							mix(cx + dx, cy + dy, 235, 235, 235, k * 0.32f);
						}
						scale(cx + dx, cy + dy, 1f - 0.18f * (1f - d));
						if (d > 0.86f && dx + dy > 0) {
							mix(cx + dx, cy + dy, 20, 18, 18, 0.35f);  // crease at the rim
						}
					}
				}
			}
		}

		// ---- 5: long jagged cracks ----
		void cracks() {
			int total = 34;
			Random rng = new Random(seed ^ 0xA54FF5L);
			for (int i = 0; i < total; i++) {
				int appear = 4 + i * (IronManDamageTiers.MAX_TIER - 3) / total;
				int x = rng.nextInt(w);
				int y = rng.nextInt(h);
				int len = (int) px(16 + rng.nextInt(36));
				long sub = rng.nextLong();
				if (appear > tier) {
					continue;
				}
				crackWalk(new Random(sub), x, y, len, 2);
			}
		}

		void crackWalk(Random rng, int x, int y, int len, int depth) {
			int dx = rng.nextBoolean() ? 1 : -1;
			int dy = rng.nextBoolean() ? 1 : -1;
			for (int k = 0; k < len; k++) {
				mix(x + 1, y, 205, 208, 210, 0.38f);     // bevel
				mix(x, y + 1, 30, 26, 24, 0.5f);
				mix(x, y, 5, 5, 6, 0.92f);                // the crack
				int q = rng.nextInt(5);
				if (q == 0) {
					x += dx;
				} else if (q == 1) {
					y += dy;
				} else {
					x += dx;
					y += dy;
				}
				if (rng.nextInt(7) == 0) {
					if (rng.nextBoolean()) {
						dx = -dx;
					} else {
						dy = -dy;
					}
				}
				if (depth > 0 && rng.nextInt(14) == 0) {
					crackWalk(new Random(rng.nextLong()), x, y, (len - k) / 2, depth - 1);
				}
			}
		}

		// ---- 6: missing plates ----
		void missingPlates() {
			int total = 24;
			int[] byTier = { 4, 9, 16, 24 }; // cumulative plates at tiers 6, 7, 8, 9
			Random rng = new Random(seed ^ 0x510E52L);
			for (int i = 0; i < total; i++) {
				int cx = rng.nextInt(w);
				int cy = rng.nextInt(h);
				float pa = px(5 + rng.nextFloat() * 9);
				float pb = px(4 + rng.nextFloat() * 8);
				int wireSeed = rng.nextInt();
				int tries = 0;
				while (tries++ < 30 && !paint(cx, cy)) {
					cx = rng.nextInt(w);
					cy = rng.nextInt(h);
				}
				int appear = IronManDamageTiers.MISSING_PLATE_TIER;
				while (appear - IronManDamageTiers.MISSING_PLATE_TIER < byTier.length
						&& i >= byTier[appear - IronManDamageTiers.MISSING_PLATE_TIER]) {
					appear++;
				}
				if (appear > tier || !paint(cx, cy)) {
					continue;
				}
				int ra = (int) Math.ceil(pa * 1.3f);
				int rb = (int) Math.ceil(pb * 1.3f);
				for (int dy = -rb; dy <= rb; dy++) {
					for (int dx = -ra; dx <= ra; dx++) {
						int x = cx + dx;
						int y = cy + dy;
						if (!paint(x, y)) {
							continue;
						}
						float e = Math.max(Math.abs(dx) / pa, Math.abs(dy) / pb)
								+ (noise(x, y, px(3), 31 + i) - 0.5f) * 0.55f;
						if (e < 1f) {
							plate[y * w + x] = true;
						}
					}
				}
				// exposed wiring through the hole (tier 8+)
				if (tier >= 8) {
					Random wr = new Random(wireSeed);
					int wires = 1 + wr.nextInt(2);
					for (int k = 0; k < wires; k++) {
						float[][] cols = { { 175, 38, 28 }, { 210, 170, 40 }, { 40, 90, 170 } };
						float[] col = cols[wr.nextInt(cols.length)];
						float off = (wr.nextFloat() - 0.5f) * pb;
						float freq = 0.25f + wr.nextFloat() * 0.3f;
						for (int dx = -ra; dx <= ra; dx++) {
							int y = cy + Math.round(off + (float) Math.sin(dx * freq) * px(2));
							wire(cx + dx, y, col);
							if (s >= 4) {
								wire(cx + dx, y + 1, new float[] { col[0] * 0.6f, col[1] * 0.6f, col[2] * 0.6f });
							}
						}
					}
				}
			}
			// paint the holes: inner frame, then the hot torn edge (needs the finished mask)
			int strutX = Math.max(3, Math.round(px(9)));
			int strutY = Math.max(3, Math.round(px(7)));
			for (int y = 0; y < h; y++) {
				for (int x = 0; x < w; x++) {
					int i = y * w + x;
					if (!plate[i] || isWire(i)) {
						continue;
					}
					float base = 18 + 10 * noise(x, y, px(4), 41);
					float cr = base;
					float cg = base + 1;
					float cb = base + 4;
					boolean sx = x % strutX == 0;
					boolean sy = y % strutY == 0;
					if (sx && sy) {
						cr = 96;
						cg = 98;
						cb = 104;               // rivet
					} else if (sx || sy) {
						cr = 50;
						cg = 54;
						cb = 60;                // strut
					}
					r[i] = cr;
					g[i] = cg;
					b[i] = cb;
				}
			}
			for (int y = 0; y < h; y++) {
				for (int x = 0; x < w; x++) {
					int i = y * w + x;
					int d = edgeDistance(x, y);
					if (plate[i] && d > 0) {
						if (d == 1) {
							set(i, 255, 214, 110);
							hot(i, 255, 170, 60);
						} else if (d == 2) {
							set(i, 240, 110, 24);
							hot(i, 210, 80, 12);
						} else if (d == 3) {
							set(i, 120, 36, 10);
							hot(i, 90, 22, 4);
						}
					} else if (!plate[i] && paint(x, y) && d > 0 && d <= 2) {
						// the torn, charred lip of the surviving plate
						mix(x, y, 34, 28, 24, d == 1 ? 0.7f : 0.4f);
					}
					if (plate[i] && d == 0 && hash(x, y, seed ^ 77) < 0.012f) {
						set(i, 200, 70, 14);    // embers deep in the frame
						hot(i, 150, 45, 6);
					}
				}
			}
		}

		private final java.util.BitSet wireBits = new java.util.BitSet();

		void wire(int x, int y, float[] col) {
			if (x < 0 || y < 0 || x >= w || y >= h) {
				return;
			}
			int i = y * w + x;
			if (!plate[i] || lit[i]) {
				return;
			}
			set(i, col[0], col[1], col[2]);
			wireBits.set(i);
		}

		boolean isWire(int i) {
			return wireBits.get(i);
		}

		/**
		 * For a plate pixel: 1..3 = how far it is inside the torn edge (0 = deeper). For a non-plate pixel: 1..2 = how
		 * far it is outside a hole (0 = farther). Chebyshev distance, scaled so the edge reads the same at any size.
		 */
		int edgeDistance(int x, int y) {
			int i = y * w + x;
			boolean in = plate[i];
			int reach = Math.max(1, s / 2);
			int max = in ? 3 : 2;
			for (int d = 1; d <= max; d++) {
				int rr = d * reach;
				for (int dy = -rr; dy <= rr; dy++) {
					for (int dx = -rr; dx <= rr; dx++) {
						if (Math.max(Math.abs(dx), Math.abs(dy)) != rr) {
							continue;
						}
						int nx = x + dx;
						int ny = y + dy;
						if (nx < 0 || ny < 0 || nx >= w || ny >= h) {
							continue;
						}
						int j = ny * w + nx;
						if (in ? (!plate[j] && a[j] != 0) : plate[j]) {
							return d;
						}
					}
				}
			}
			return 0;
		}

		void set(int i, float cr, float cg, float cb) {
			r[i] = cr;
			g[i] = cg;
			b[i] = cb;
		}

		void hot(int i, float cr, float cg, float cb) {
			hr[i] = Math.max(hr[i], cr);
			hg[i] = Math.max(hg[i], cg);
			hb[i] = Math.max(hb[i], cb);
		}

		// ---- 7: eye lenses / arc reactor / palms ----
		void damageLights() {
			int t = tier - IronManDamageTiers.LIGHTS_TIER + 1;  // 1..6
			float dead = 0.05f * t;                              // dead lens pixels
			float dim = 1f - 0.06f * t;                          // overall dimming
			float crackWidth = 0.012f * t;
			int block = Math.max(1, s / 2);
			for (int y = 0; y < h; y++) {
				for (int x = 0; x < w; x++) {
					int i = y * w + x;
					if (gr[i] + gg[i] + gb[i] <= 0f) {
						continue;
					}
					float k = dim;
					if (hash(x / block, y / block, seed ^ 0x77) < dead) {
						k = 0f;
					} else if (Math.abs(noise(x, y, px(6), 51) - 0.5f) < crackWidth) {
						k = 0.08f;   // a crack line through the lens
					} else if (hash(x / block, y / block, seed ^ 0x99) < dead * 0.6f) {
						k *= 0.4f;
					}
					gr[i] *= k;
					gg[i] *= k;
					gb[i] *= k;
				}
			}
		}

		NativeImage baseImage() {
			NativeImage img = new NativeImage(w, h, true);
			for (int y = 0; y < h; y++) {
				for (int x = 0; x < w; x++) {
					int i = y * w + x;
					img.setPixelRGBA(x, y, pack(a[i], r[i], g[i], b[i]));
				}
			}
			return img;
		}

		NativeImage glowImage(boolean lightsOn) {
			NativeImage img = new NativeImage(w, h, true);
			for (int y = 0; y < h; y++) {
				for (int x = 0; x < w; x++) {
					int i = y * w + x;
					float cr = hr[i];
					float cg = hg[i];
					float cb = hb[i];
					if (lightsOn) {
						cr = Math.max(cr, gr[i]);
						cg = Math.max(cg, gg[i]);
						cb = Math.max(cb, gb[i]);
					}
					// RenderType.eyes is additive: black = no glow; keep alpha full where anything glows
					int al = cr + cg + cb > 3f ? 255 : 0;
					img.setPixelRGBA(x, y, pack(al, cr, cg, cb));
				}
			}
			return img;
		}

		// ---- noise ----

		float noise(int x, int y, float cell, int salt) {
			cell = Math.max(1f, cell);
			float fx = x / cell;
			float fy = y / cell;
			int ix = Mth.floor(fx);
			int iy = Mth.floor(fy);
			float tx = smooth(fx - ix);
			float ty = smooth(fy - iy);
			int sd = seed ^ (salt * 0x27D4EB2D);
			float v00 = hash(ix, iy, sd);
			float v10 = hash(ix + 1, iy, sd);
			float v01 = hash(ix, iy + 1, sd);
			float v11 = hash(ix + 1, iy + 1, sd);
			return Mth.lerp(ty, Mth.lerp(tx, v00, v10), Mth.lerp(tx, v01, v11));
		}

		float fbm(int x, int y, int salt) {
			return noise(x, y, px(30), salt) * 0.55f + noise(x, y, px(13), salt + 100) * 0.3f
					+ noise(x, y, px(5), salt + 200) * 0.15f;
		}
	}

	private static float smooth(float t) {
		return t * t * (3 - 2 * t);
	}

	/** Deterministic per-texel hash in [0, 1). */
	static float hash(int x, int y, int seed) {
		int hsh = seed ^ (x * 0x1B873593) ^ (y * 0x5BD1E995);
		hsh ^= hsh >>> 15;
		hsh *= 0x2C1B3C6D;
		hsh ^= hsh >>> 12;
		hsh *= 0x297A2D39;
		hsh ^= hsh >>> 15;
		return (hsh >>> 8) / (float) (1 << 24);
	}

	private static int pack(int alpha, float r, float g, float b) {
		int ri = Mth.clamp(Math.round(r), 0, 255);
		int gi = Mth.clamp(Math.round(g), 0, 255);
		int bi = Mth.clamp(Math.round(b), 0, 255);
		return (alpha << 24) | (bi << 16) | (gi << 8) | ri;
	}

	/**
	 * The texels the damage must leave alone: every glowmask texel, its partner on the other skin layer (a scorched
	 * outer-layer texel in front of a lit eye would hide it), and a one-texel margin round both. In source-texel space.
	 */
	private static boolean[] protectLights(boolean[] lit, int w, int h) {
		boolean[] layered = new boolean[w * h];
		double s = w / 64.0;
		for (int x = 0; x < w; x++) {
			for (int y = 0; y < h; y++) {
				if (!lit[y * w + x]) {
					continue;
				}
				layered[y * w + x] = true;
				int[] p = partner((int) (x / s), (int) (y / s));
				if (p != null) {
					int px = (int) (p[0] * s + (x - (int) (x / s) * s));
					int py = (int) (p[1] * s + (y - (int) (y / s) * s));
					if (px >= 0 && py >= 0 && px < w && py < h) {
						layered[py * w + px] = true;
					}
				}
			}
		}
		boolean[] out = new boolean[w * h];
		for (int x = 0; x < w; x++) {
			for (int y = 0; y < h; y++) {
				if (!layered[y * w + x]) {
					continue;
				}
				for (int dx = -1; dx <= 1; dx++) {
					for (int dy = -1; dy <= 1; dy++) {
						int nx = x + dx;
						int ny = y + dy;
						if (nx >= 0 && ny >= 0 && nx < w && ny < h) {
							out[ny * w + nx] = true;
						}
					}
				}
			}
		}
		return out;
	}

	/** The same body texel on the other skin layer, in 64x64 player-skin UV space (null if none). */
	private static int[] partner(int u, int v) {
		if (v < 16) { // head <-> hat
			return u < 32 ? new int[]{u + 32, v} : new int[]{u - 32, v};
		}
		if (v < 32) { // right leg, body, right arm -> their jacket / pants / sleeve below
			return u < 56 ? new int[]{u, v + 16} : null;
		}
		if (v < 48) {
			return u < 56 ? new int[]{u, v - 16} : null;
		}
		if (u < 16) {
			return new int[]{u + 16, v};
		}
		if (u < 32) {
			return new int[]{u - 16, v};
		}
		return u < 48 ? new int[]{u + 16, v} : new int[]{u - 16, v};
	}

	// ---------------- glow flicker ----------------

	/** Old entry point: true on the frames a damaged worn suit's eye / reactor glow should drop out. */
	public static boolean glowFlickerOff(Player player) {
		return flickerOff(player, tierOf(player));
	}

	/** Out of 256: how often the lights are out, per tier (none above 30%; sputtering most of the time at 5%). */
	private static final int[] FLICKER = { 0, 0, 0, 0, 14, 24, 36, 50, 70, 104 };

	private static boolean flickerOff(Entity wearer, int tier) {
		if (tier < IronManDamageTiers.LIGHTS_TIER || wearer.level() == null) {
			return false;
		}
		long slot = wearer.level().getGameTime() / 2;
		int hsh = (int) ((slot * 0x9E3779B97F4A7C15L + wearer.getId() * 0xC2B2AE3D27D4EB4FL) >>> 40) & 0xFF;
		// at the bottom tiers the lights also go out in short runs, not single blinks
		if (tier >= 8) {
			long run = wearer.level().getGameTime() / 7;
			int h2 = (int) ((run * 0xD6E8FEB86659FD93L + wearer.getId() * 0x9E3779B97F4A7C15L) >>> 40) & 0xFF;
			if (h2 < (tier >= 9 ? 70 : 40)) {
				return true;
			}
		}
		return hsh < FLICKER[Math.min(tier, FLICKER.length - 1)];
	}

	// ---------------- sparks + smoke + arcs ----------------

	private static void clientTick(Minecraft mc) {
		ClientLevel level = mc.level;
		if (level == null || mc.player == null || mc.isPaused()) {
			return;
		}
		RandomSource rnd = level.getRandom();
		for (Player p : level.players()) {
			if (p.isInvisible() || p.distanceToSqr(mc.player) > 48 * 48) {
				continue;
			}
			int tier = tierOf(p);
			if (tier < IronManDamageTiers.SPARK_TIER) {
				continue;
			}
			boolean firstPersonSelf = p == mc.player && mc.options.getCameraType() == CameraType.FIRST_PERSON;
			emit(level, rnd, p, tier, firstPersonSelf);
		}
		for (IronManSentryEntity s : level.getEntitiesOfClass(IronManSentryEntity.class, mc.player.getBoundingBox().inflate(48))) {
			int tier = s.pieceCount() > 0 ? IronManDamageTiers.tier(sentryIntegrity(s)) : 0;
			if (tier >= IronManDamageTiers.SPARK_TIER && !s.isInvisible()) {
				emit(level, rnd, s, tier, false);
			}
		}
	}

	private static void emit(ClientLevel level, RandomSource rnd, Entity p, int tier, boolean firstPersonSelf) {
		float rate = firstPersonSelf ? 0.4f : 1f;
		boolean wrecked = tier >= IronManDamageTiers.MAX_TIER;
		// sparks: occasional at 25%, frequent at 5%
		float sparkChance = (wrecked ? 0.38f : 0.05f + 0.035f * (tier - IronManDamageTiers.SPARK_TIER)) * rate;
		if (rnd.nextFloat() < sparkChance) {
			Vec3 at = bodyPoint(p, rnd, firstPersonSelf);
			int n = (wrecked ? 4 : 2) + rnd.nextInt(wrecked ? 5 : 3);
			for (int i = 0; i < n; i++) {
				level.addParticle(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z,
						(rnd.nextDouble() - 0.5) * 0.3, rnd.nextDouble() * 0.18, (rnd.nextDouble() - 0.5) * 0.3);
			}
			if (wrecked && rnd.nextInt(3) == 0) {
				level.addParticle(ParticleTypes.LAVA, at.x, at.y, at.z, 0, 0, 0); // a hot fleck popping off
			}
		}
		// smoke: light from 15%, heavy at 5%
		if (tier >= IronManDamageTiers.SMOKE_TIER) {
			float smokeChance = (wrecked ? 0.55f : 0.10f + 0.08f * (tier - IronManDamageTiers.SMOKE_TIER)) * rate;
			if (rnd.nextFloat() < smokeChance) {
				Vec3 at = bodyPoint(p, rnd, firstPersonSelf);
				level.addParticle(wrecked && rnd.nextBoolean() ? ParticleTypes.LARGE_SMOKE : ParticleTypes.SMOKE,
						at.x, at.y, at.z, (rnd.nextDouble() - 0.5) * 0.02, 0.04 + rnd.nextDouble() * 0.03, (rnd.nextDouble() - 0.5) * 0.02);
			}
			if (wrecked && !firstPersonSelf && rnd.nextFloat() < 0.04f) {
				Vec3 at = bodyPoint(p, rnd, false);
				level.addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, at.x, at.y, at.z, 0, 0.03, 0); // a rising column
			}
		}
		// small electrical arcs jumping across the suit at 5%
		if (wrecked && rnd.nextFloat() < 0.07f * rate) {
			Vec3 from = bodyPoint(p, rnd, firstPersonSelf);
			Vec3 to = from.add((rnd.nextDouble() - 0.5) * 0.5, (rnd.nextDouble() - 0.5) * 0.4, (rnd.nextDouble() - 0.5) * 0.5);
			int steps = 7 + rnd.nextInt(4);
			for (int i = 0; i <= steps; i++) {
				double t = i / (double) steps;
				double jitter = (i == 0 || i == steps) ? 0 : 0.06;
				Vec3 at = from.lerp(to, t).add((rnd.nextDouble() - 0.5) * jitter, (rnd.nextDouble() - 0.5) * jitter,
						(rnd.nextDouble() - 0.5) * jitter);
				level.addParticle(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 0, 0, 0);
			}
		}
	}

	/** A random point on the suit's shoulders, chest, arms or legs, in world space (behind the camera for first person). */
	private static Vec3 bodyPoint(Entity p, RandomSource rnd, boolean firstPersonSelf) {
		double side;
		double up;
		double fwd;
		switch (rnd.nextInt(firstPersonSelf ? 3 : 5)) {
			case 0 -> { side = rnd.nextBoolean() ? 0.36 : -0.36; up = 1.38; fwd = -0.05; }  // shoulders
			case 1 -> { side = (rnd.nextDouble() - 0.5) * 0.4; up = 1.0 + rnd.nextDouble() * 0.3; fwd = -0.18; } // back
			case 2 -> { side = rnd.nextBoolean() ? 0.14 : -0.14; up = 0.3 + rnd.nextDouble() * 0.4; fwd = 0.0; } // legs
			case 3 -> { side = rnd.nextBoolean() ? 0.42 : -0.42; up = 0.85 + rnd.nextDouble() * 0.4; fwd = 0.0; } // arms
			default -> { side = (rnd.nextDouble() - 0.5) * 0.3; up = 1.05 + rnd.nextDouble() * 0.25; fwd = 0.17; } // chest
		}
		if (p.isCrouching()) {
			up *= 0.85;
		}
		if (p instanceof LivingEntity) {
			up *= p.getBbHeight() / 1.8;
		}
		float yaw = (p instanceof LivingEntity le ? le.yBodyRot : p.getYRot()) * Mth.DEG_TO_RAD;
		double sin = Mth.sin(yaw);
		double cos = Mth.cos(yaw);
		double wx = side * cos - fwd * sin;
		double wz = side * sin + fwd * cos;
		return new Vec3(p.getX() + wx, p.getY() + up, p.getZ() + wz);
	}

	// ---------------- visor cracks ----------------

	/** Fixed crack paths in screen fractions: {startX, startY, angle, length}. */
	private static final double[][] CRACKS = {
			{0.0, 0.08, 0.35, 0.30},
			{1.0, 0.85, Math.PI + 0.25, 0.26},
			{0.82, 0.0, Math.PI * 0.62, 0.22},
			{0.06, 1.0, -0.9, 0.24},
			{1.0, 0.30, Math.PI - 0.45, 0.20},
			{0.38, 0.0, Math.PI * 0.45, 0.16},
			{0.0, 0.55, 0.15, 0.22},
			{0.62, 1.0, -Math.PI * 0.55, 0.20},
	};
	/** How many of {@link #CRACKS} show per tier. */
	private static final int[] VISOR_CRACKS = { 0, 0, 1, 1, 2, 3, 4, 5, 6, 8 };

	/** Cracks across the closed visor, more with every damage tier from 40% down. */
	public static void renderVisorCracks(GuiGraphics g, float integrity, boolean retro) {
		int tier = IronManDamageTiers.tier(integrity);
		int count = VISOR_CRACKS[Math.min(tier, VISOR_CRACKS.length - 1)];
		if (count == 0) {
			return;
		}
		int sw = g.guiWidth();
		int sh = g.guiHeight();
		int glass = retro ? 0x8CFFE0B0 : 0x8CD8F4FF;
		int shadow = 0x50000000;
		for (int i = 0; i < count; i++) {
			double[] c = CRACKS[i];
			Random rng = new Random(9173L * (i + 1));
			crack(g, rng, c[0] * sw, c[1] * sh, c[2], c[3] * Math.min(sw, sh * 1.6), glass, shadow, 2);
		}
	}

	private static void crack(GuiGraphics g, Random rng, double x, double y, double angle, double length,
			int glass, int shadow, int depth) {
		double travelled = 0;
		while (travelled < length) {
			double seg = 6 + rng.nextDouble() * 10;
			double a = angle + (rng.nextDouble() - 0.5) * 0.9;
			double nx = x + Math.cos(a) * seg;
			double ny = y + Math.sin(a) * seg;
			line(g, (int) x + 1, (int) y + 1, (int) nx + 1, (int) ny + 1, shadow);
			line(g, (int) x, (int) y, (int) nx, (int) ny, glass);
			if (depth > 0 && rng.nextInt(4) == 0) {
				crack(g, rng, nx, ny, a + (rng.nextBoolean() ? 0.8 : -0.8), (length - travelled) * 0.45,
						glass & 0x80FFFFFF, shadow, depth - 1);
			}
			x = nx;
			y = ny;
			travelled += seg;
		}
	}

	private static void line(GuiGraphics g, int x0, int y0, int x1, int y1, int color) {
		int dx = Math.abs(x1 - x0);
		int dy = -Math.abs(y1 - y0);
		int sx = x0 < x1 ? 1 : -1;
		int sy = y0 < y1 ? 1 : -1;
		int err = dx + dy;
		int guard = 0;
		while (guard++ < 400) {
			g.fill(x0, y0, x0 + 1, y0 + 1, color);
			if (x0 == x1 && y0 == y1) {
				break;
			}
			int e2 = 2 * err;
			if (e2 >= dy) {
				err += dy;
				x0 += sx;
			}
			if (e2 <= dx) {
				err += dx;
				y0 += sy;
			}
		}
	}
}
