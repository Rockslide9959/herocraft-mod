package com.projecthero.mod.client.thor;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.client.render.ArmorSweepReveal;
import com.projecthero.mod.power.ThorFx;
import com.projecthero.mod.power.ThorVisuals;
import com.projecthero.mod.thorarmor.ThorArmor;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;

import org.joml.Vector3f;

/**
 * v0.14.4: Thor's Armour materialises piece by piece on the suit clock and comes off the same way in reverse.
 *
 * <p>v0.17.7 polish (user: "change the suitup animation, the suit should appear pixel by pixel like i said before please"): each piece
 * (boots, greaves, the chestplate with its arm plates, the winged helmet, then the cape) now <b>builds on the body texel by texel</b>
 * over its own window of the suit clock, the way the older {@link ArmorSweepReveal} sweep did: pre-built copies of the suit texture with
 * the not-yet-formed texels cleared, swapped in by {@code SuperheroArmorRenderer#getRenderType}. Each texel's place in the order comes
 * from where it sits on the rest-pose model ({@link ArmorSweepReveal#forEachTexel}): the boots and greaves fill from the ground up, the
 * chestplate spreads out from the sternum (shoulders and bracers last), the helmet climbs from the neck to the wing tips, the cape
 * runs down from the collar -- each with a little per-texel scatter so the edge reads as single pixels popping in, and the newest
 * texels flash storm-white for a frame or two. Coming off plays the same frames backwards, piece by piece. Once complete the shipped
 * texture is drawn untouched, so the finished look is exactly the same.
 *
 * <p>Read off the synced suit clock ({@link ThorFx#suitDir()} / {@link ThorFx#suitStart()}) with {@link ThorArmor#partProgress}, so every
 * viewer sees the same thing. The frames are built lazily on the render thread and kept for the session (a few dozen 64x64 textures).
 */
public final class ThorSuitReveal {
	/** Reveal steps per piece (the windows are 4-14 ticks, so this is a few new pixel batches per rendered frame). */
	private static final int STEPS = 24;
	/** How much of a texel's order is its place on the body (the rest is per-texel scatter). */
	private static final float ORDER_BODY = 0.76f;
	/** The newest texels: storm white, then a pale blue trail behind them. */
	private static final int EDGE_ARGB = 0xFFEAF6FF;
	private static final int TRAIL_ARGB = 0xFF8EC6FF;

	private static final ResourceLocation GEO = ProjectHeroMod.id("geo/thor.geo.json");
	private static final ResourceLocation CAPE = ProjectHeroMod.id("textures/entity/thor_cape.png");

	/** Built frames per slot pass / the cape (texture ids only -- nothing here holds a world or an entity). */
	private static final Map<String, ResourceLocation[]> FRAMES = new HashMap<>();

	private ThorSuitReveal() {
	}

	/** How much of {@code part} ({@code ThorArmor.PART_*}) is showing right now (0..1). */
	public static float progress(Player player, int part, float partialTick) {
		ThorFx fx = ThorVisuals.fx(player);
		if (fx.suitDir() == ThorFx.SUIT_NONE) {
			return 1.0f;
		}
		float elapsed = player.level().getGameTime() - fx.suitStart() + partialTick;
		return ThorArmor.partProgress(fx.suitDir(), part, elapsed);
	}

	/** The parts a slot's render pass draws, in build order (the chestplate's pass also carries the helmet). */
	private static int[] partsOf(EquipmentSlot slot) {
		return switch (slot) {
			case FEET -> new int[] { ThorArmor.PART_BOOTS };
			case LEGS -> new int[] { ThorArmor.PART_GREAVES };
			case CHEST -> new int[] { ThorArmor.PART_CHEST, ThorArmor.PART_HELMET };
			default -> new int[0];
		};
	}

	/**
	 * The texture to draw {@code slot}'s pass of Thor's Armour with right now: {@code base} once the pieces in it are complete (or no
	 * suit clock is running), otherwise the frame with only the formed texels.
	 */
	public static ResourceLocation texture(Player player, EquipmentSlot slot, ResourceLocation base, float partialTick) {
		if (ThorVisuals.fx(player).suitDir() == ThorFx.SUIT_NONE) {
			return base;
		}
		int[] parts = partsOf(slot);
		if (parts.length == 0) {
			return base;
		}
		// the parts of one pass never overlap in time (up or down), so their summed progress picks one frame of the whole pass
		float g = 0.0f;
		for (int part : parts) {
			g += progress(player, part, partialTick);
		}
		if (g >= parts.length - 0.001f) {
			return base;
		}
		ResourceLocation[] frames = FRAMES.computeIfAbsent(slot.getName() + "|" + base, k -> buildArmour(base, parts));
		return pick(frames, g, base);
	}

	/** The cape's texture right now: the collar first, running down to the hem (and back up on H). */
	public static ResourceLocation capeTexture(Player player, float partialTick) {
		float p = progress(player, ThorArmor.PART_CAPE, partialTick);
		if (p >= 0.999f) {
			return CAPE;
		}
		ResourceLocation[] frames = FRAMES.computeIfAbsent("cape", k -> buildCape());
		return pick(frames, p, CAPE);
	}

	private static ResourceLocation pick(ResourceLocation[] frames, float g, ResourceLocation base) {
		if (frames.length == 0) {
			return base;
		}
		int f = Math.max(0, Math.min(frames.length - 1, (int) Math.floor(g * STEPS)));
		return frames[f];
	}

	// ---------------- building the frames ----------------

	/** Per texel: which of the pass's parts it belongs to (-1 = none) and its reveal step (0..STEPS-1). */
	private record Order(int[] part, int[] step) {
	}

	private static ResourceLocation[] buildArmour(ResourceLocation base, int[] parts) {
		NativeImage src = read(base);
		if (src == null) {
			return new ResourceLocation[0];
		}
		int w = src.getWidth();
		int h = src.getHeight();
		int n = w * h;
		int[] part = new int[n];
		float[] value = new float[n];
		java.util.Arrays.fill(part, -1);
		java.util.Arrays.fill(value, Float.NaN);
		Vector3f sternum = new Vector3f(0f, 19f, -2f);
		boolean ok = ArmorSweepReveal.forEachTexel(GEO, w, h, (i, bone, p) -> {
			int idx = indexOf(parts, partOfBone(bone));
			if (idx < 0) {
				return;
			}
			int thorPart = parts[idx];
			// "first" = smallest value
			float v = thorPart == ThorArmor.PART_CHEST ? p.distance(sternum) : p.y;
			if (part[i] < 0 || idx < part[i] || (idx == part[i] && v < value[i])) {
				part[i] = idx;
				value[i] = v;
			}
		});
		if (!ok) {
			src.close();
			return new ResourceLocation[0];
		}
		// normalise each part's values to 0..1, mix in the scatter, bucket into steps
		int[] step = new int[n];
		for (int idx = 0; idx < parts.length; idx++) {
			float lo = Float.POSITIVE_INFINITY;
			float hi = Float.NEGATIVE_INFINITY;
			for (int i = 0; i < n; i++) {
				if (part[i] == idx) {
					lo = Math.min(lo, value[i]);
					hi = Math.max(hi, value[i]);
				}
			}
			float span = Math.max(1.0e-3f, hi - lo);
			for (int i = 0; i < n; i++) {
				if (part[i] == idx) {
					float body = (value[i] - lo) / span;
					step[i] = stepOf(ORDER_BODY * body + (1f - ORDER_BODY) * scatter(i, parts[idx]));
				}
			}
		}
		ResourceLocation[] frames = frames(src, new Order(part, step), parts.length,
				"thor_suit_" + base.getPath().replaceAll("[^a-z0-9_]", "_") + "_" + parts[0]);
		src.close();
		return frames;
	}

	private static ResourceLocation[] buildCape() {
		NativeImage src = read(CAPE);
		if (src == null) {
			return new ResourceLocation[0];
		}
		int w = src.getWidth();
		int h = src.getHeight();
		int[] part = new int[w * h];
		int[] step = new int[w * h];
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				int i = y * w + x;
				// the cloth's v runs from the collar down to the hem (FlowingCapeLayer / MoonKnightCapeLayer#drawCape)
				step[i] = stepOf(ORDER_BODY * (y + 0.5f) / h + (1f - ORDER_BODY) * scatter(i, ThorArmor.PART_CAPE));
			}
		}
		ResourceLocation[] frames = frames(src, new Order(part, step), 1, "thor_cape");
		src.close();
		return frames;
	}

	/** Frame {@code f} of {@code parts * STEPS + 1}: the parts before {@code f / STEPS} whole, that part's first {@code f % STEPS} steps. */
	private static ResourceLocation[] frames(NativeImage src, Order order, int parts, String tag) {
		Minecraft mc = Minecraft.getInstance();
		int w = src.getWidth();
		int h = src.getHeight();
		int count = parts * STEPS + 1;
		ResourceLocation[] out = new ResourceLocation[count];
		for (int f = 0; f < count; f++) {
			int active = f / STEPS;
			int k = f % STEPS;
			NativeImage img = new NativeImage(w, h, true);
			img.copyFrom(src);
			for (int y = 0; y < h; y++) {
				for (int x = 0; x < w; x++) {
					int i = y * w + x;
					int pi = order.part()[i];
					if (pi < 0 || pi < active) {
						continue; // not part of this pass, or already whole
					}
					int s = order.step()[i];
					if (pi > active || s >= k) {
						img.setPixelRGBA(x, y, 0);
						continue;
					}
					int rgba = src.getPixelRGBA(x, y);
					if (((rgba >>> 24) & 0xFF) == 0) {
						continue;
					}
					if (s == k - 1) {
						img.setPixelRGBA(x, y, abgr(EDGE_ARGB));
					} else if (s == k - 2) {
						img.setPixelRGBA(x, y, blendAbgr(rgba, abgr(TRAIL_ARGB), 0.55f));
					}
				}
			}
			ResourceLocation id = ProjectHeroMod.id("dynamic/thor_suit/" + tag + "_" + f);
			mc.getTextureManager().register(id, new DynamicTexture(img));
			out[f] = id;
		}
		return out;
	}

	private static NativeImage read(ResourceLocation tex) {
		Optional<Resource> res = Minecraft.getInstance().getResourceManager().getResource(tex);
		if (res.isEmpty()) {
			return null;
		}
		try (InputStream in = res.get().open()) {
			return NativeImage.read(in);
		} catch (Exception e) {
			ProjectHeroMod.LOGGER.warn("[ProjectHero] could not build Thor's suit-up frames for {}: {}", tex, e.toString());
			return null;
		}
	}

	private static int partOfBone(String bone) {
		return switch (bone) {
			case "armorRightBoot", "armorLeftBoot" -> ThorArmor.PART_BOOTS;
			case "armorRightLeg", "armorLeftLeg" -> ThorArmor.PART_GREAVES;
			case "armorBody", "armorRightArm", "armorLeftArm" -> ThorArmor.PART_CHEST;
			case "armorHead" -> ThorArmor.PART_HELMET;
			default -> -1;
		};
	}

	private static int indexOf(int[] parts, int part) {
		for (int i = 0; i < parts.length; i++) {
			if (parts[i] == part) {
				return i;
			}
		}
		return -1;
	}

	private static int stepOf(float order) {
		return Math.max(0, Math.min(STEPS - 1, (int) Math.floor(order * STEPS)));
	}

	/** A fixed 0..1 scatter per texel (and part), so the build edge is ragged pixels rather than a straight line. */
	private static float scatter(int texel, int part) {
		long x = texel * 0x9E3779B97F4A7C15L + part * 0xC2B2AE3D27D4EB4FL;
		x ^= x >>> 31;
		x *= 0xBF58476D1CE4E5B9L;
		x ^= x >>> 29;
		return (x >>> 40) / (float) (1L << 24);
	}

	/** ARGB to NativeImage's ABGR. */
	private static int abgr(int argb) {
		int a = argb >>> 24;
		int r = (argb >> 16) & 0xFF;
		int g = (argb >> 8) & 0xFF;
		int b = argb & 0xFF;
		return (a << 24) | (b << 16) | (g << 8) | r;
	}

	/** Two ABGR colours mixed ({@code t} of {@code b}), keeping {@code a}'s alpha. */
	private static int blendAbgr(int a, int b, float t) {
		int out = a & 0xFF000000;
		for (int shift = 0; shift < 24; shift += 8) {
			int ca = (a >> shift) & 0xFF;
			int cb = (b >> shift) & 0xFF;
			out |= Math.round(ca + (cb - ca) * t) << shift;
		}
		return out;
	}
}
