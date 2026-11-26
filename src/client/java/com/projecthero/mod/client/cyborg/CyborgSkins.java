package com.projecthero.mod.client.cyborg;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.entity.player.PlayerModelPart;

/**
 * v0.17.7 (user): Cyborg at the player's own size. Each Cyborg player is drawn by vanilla, on the normal player model, with a
 * skin made out of his own skin and Cyborg's ({@link CyborgSkinComposite}) -- swapped in by
 * {@code CyborgFirstPersonArmMixin} (third person {@code getTextureLocation}, first person {@code renderHand}).
 *
 * <p>The player's skin is read back from the GPU (whatever texture his {@link PlayerSkin} names: a downloaded skin, a default
 * skin, another mod's), so wide / slim and HD skins all work. It is read once per skin, after the skin's texture id has been
 * the same for {@link #SETTLE_MS} (a freshly downloaded skin is uploaded a frame after it is handed out). Until then he is
 * drawn plain.
 *
 * <p>One {@link DynamicTexture} per player, rewritten only when what shows changes: the Mother Box spread (texels outward from
 * the chest core, {@link CyborgRender#convertOrder}) or the hologram's line ({@link CyborgRender#holoTint}, its cyan edge baked
 * in) -- both at true size. <b>Leaks:</b> an entry is released when his skin changes, when he leaves the level or hasn't been
 * drawn as Cyborg for {@link #IDLE_TICKS}, when the level changes, and on disconnect ({@link #clear}).
 */
public final class CyborgSkins {
	private CyborgSkins() {
	}

	private static final long SETTLE_MS = 600L, RETRY_MS = 1500L;
	private static final int IDLE_TICKS = 200;
	/** Line / spread steps per animation (the textures are only rewritten when this quantised value moves). */
	private static final int STEPS_Q = 1024;
	/** The hologram's building edge (ARGB 0xFF7FE6FF), in NativeImage's ABGR packing. */
	private static final int HOLO_EDGE_ABGR = 0xFFFFE67F;

	private static final class Entry {
		ResourceLocation skin;
		boolean slim;
		long seenAt, nextTry;
		/** The player's skin, {@link #n} x {@link #n}; null until read. */
		int[] plain;
		int n;
		DynamicTexture texture;
		ResourceLocation id;
		long key = Long.MIN_VALUE;
		long lastTick;
	}

	private static final Map<UUID, Entry> ENTRIES = new HashMap<>();
	/** Cyborg's skin (wide / slim) and the raw flesh (wide / slim), 64x64 ABGR; null until read. */
	private static int[][] cyborg, flesh;
	private static int serial;
	private static long tick;
	private static ClientLevel lastLevel;

	/**
	 * The skin to draw {@code player} with, or null to leave his own (not Cyborg right now, or his skin isn't read yet). Rewrites
	 * his texture if the spread / hologram moved. Render thread only.
	 */
	@Nullable
	public static ResourceLocation texture(AbstractClientPlayer player) {
		if (!RenderSystem.isOnRenderThread()) {
			return null;
		}
		Minecraft mc = Minecraft.getInstance();
		float partial = mc.getTimer().getGameTimeDeltaPartialTick(false);
		CyborgRender.Anim a = CyborgRender.anim(player, partial);
		boolean fleshShows = com.projecthero.mod.cyborg.Cyborg.fleshShows(player);
		if (!a.anything() && !fleshShows) {
			return null;
		}
		PlayerSkin skin = player.getSkin();
		boolean slim = skin.model() == PlayerSkin.Model.SLIM;
		Entry e = ENTRIES.get(player.getUUID());
		long now = System.currentTimeMillis();
		if (e == null || !skin.texture().equals(e.skin) || e.slim != slim) {
			if (e != null) {
				release(e);
			}
			e = new Entry();
			e.skin = skin.texture();
			e.slim = slim;
			e.seenAt = now;
			ENTRIES.put(player.getUUID(), e);
		}
		e.lastTick = tick;
		if (e.plain == null) {
			if (now - e.seenAt < SETTLE_MS || now < e.nextTry) {
				return null;
			}
			if (!read(e)) {
				e.nextTry = now + RETRY_MS;
				return null;
			}
		}
		if (!sources()) {
			return null;
		}
		int shown = shownParts(player);
		boolean holo = a.hologramShowing();
		float spread = holo || !a.anything() ? 0f : Math.min(1f, a.spread() / CyborgRender.STEPS);
		long key = holo
				? 2L << 40 | (long) Math.round(a.holo() * STEPS_Q) << 8 | (a.building() ? 1 << 6 : 0) | shown
				: 1L << 40 | (long) Math.round(spread * STEPS_Q) << 8 | (fleshShows ? 1 << 7 : 0) | shown;
		if (key != e.key || e.id == null) {
			byte[] mode = new byte[64 * 64];
			int[] tint = null;
			if (holo) {
				// where the hologram has reached, his plain skin (both layers, the building edge cyan); behind it, Cyborg
				float line = a.holo();
				boolean building = a.building();
				tint = new int[64 * 64];
				for (int b = 0; b < mode.length; b++) {
					int t = CyborgRender.holoTint(b & 63, b >> 6, line, building);
					mode[b] = t == 0 ? CyborgSkinComposite.CYBORG : CyborgSkinComposite.PLAIN;
					tint[b] = t == 0 || t == 0xFFFFFFFF ? -1 : HOLO_EDGE_ABGR;
				}
			} else {
				// the Mother Box spread: Cyborg outward from the chest core over his skin (over the raw flesh while it shows)
				int q = Math.round(spread * STEPS_Q);
				byte under = fleshShows ? CyborgSkinComposite.FLESH : CyborgSkinComposite.PLAIN;
				for (int b = 0; b < mode.length; b++) {
					boolean on = q >= STEPS_Q || (q > 0 && CyborgRender.convertOrder(b & 63, b >> 6) <= spread);
					mode[b] = on ? CyborgSkinComposite.CYBORG : under;
				}
			}
			int[] out = new int[e.n * e.n];
			CyborgSkinComposite.compose(e.plain, e.n, cyborg[slim ? 1 : 0], fleshShows ? flesh[slim ? 1 : 0] : null, slim, shown, mode, tint, out);
			if (e.texture == null) {
				e.texture = new DynamicTexture(new NativeImage(e.n, e.n, true));
				e.id = ProjectHeroMod.id("dynamic/cyborg_skin/" + player.getUUID() + "_" + (serial++));
				mc.getTextureManager().register(e.id, e.texture);
			}
			NativeImage img = e.texture.getPixels();
			if (img == null) {
				return null;
			}
			for (int y = 0; y < e.n; y++) {
				for (int x = 0; x < e.n; x++) {
					img.setPixelRGBA(x, y, out[y * e.n + x]);
				}
			}
			e.texture.upload();
			e.key = key;
		}
		return e.id;
	}

	/** Whether {@code player} is being drawn with his Cyborg skin (the model then shows all of its second layer, see PlayerModelMixin). */
	public static boolean active(AbstractClientPlayer player) {
		return texture(player) != null;
	}

	private static int shownParts(AbstractClientPlayer p) {
		int s = 0;
		if (p.isModelPartShown(PlayerModelPart.HAT)) s |= CyborgSkinComposite.HAT;
		if (p.isModelPartShown(PlayerModelPart.JACKET)) s |= CyborgSkinComposite.JACKET;
		if (p.isModelPartShown(PlayerModelPart.RIGHT_SLEEVE)) s |= CyborgSkinComposite.RIGHT_SLEEVE;
		if (p.isModelPartShown(PlayerModelPart.LEFT_SLEEVE)) s |= CyborgSkinComposite.LEFT_SLEEVE;
		if (p.isModelPartShown(PlayerModelPart.RIGHT_PANTS_LEG)) s |= CyborgSkinComposite.RIGHT_PANTS;
		if (p.isModelPartShown(PlayerModelPart.LEFT_PANTS_LEG)) s |= CyborgSkinComposite.LEFT_PANTS;
		return s;
	}

	/** Reads the player's skin texture back from the GPU. False if it isn't there yet (no storage / empty). */
	private static boolean read(Entry e) {
		AbstractTexture tex = Minecraft.getInstance().getTextureManager().getTexture(e.skin);
		GlStateManager._bindTexture(tex.getId());
		int w = GlStateManager._getTexLevelParameter(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
		int h = GlStateManager._getTexLevelParameter(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
		if (w < 16 || h < 16 || w > 1024 || h > 1024) {
			return false;
		}
		int[] px = new int[w * h];
		boolean any = false;
		try (NativeImage img = new NativeImage(NativeImage.Format.RGBA, w, h, false)) {
			img.downloadTexture(0, false);
			for (int y = 0; y < h; y++) {
				for (int x = 0; x < w; x++) {
					int c = img.getPixelRGBA(x, y);
					px[y * w + x] = c;
					any |= c != 0;
				}
			}
		} catch (Exception ex) {
			ProjectHeroMod.LOGGER.warn("[cyborg] could not read a player's skin", ex);
			return false;
		}
		if (!any) {
			return false;
		}
		if (w == h && w % 64 == 0) {
			e.plain = px;
			e.n = w;
		} else {
			e.plain = CyborgSkinComposite.resample(px, w, h, 64);
			e.n = 64;
		}
		return true;
	}

	/** Cyborg's skins and the flesh, read from the resource packs once. */
	private static boolean sources() {
		if (cyborg != null) {
			return true;
		}
		int[] wide = load(CyborgRender.SKIN), slim = load(CyborgRender.SKIN_SLIM), raw = load(CyborgRender.FLESH);
		if (wide == null || slim == null) {
			return false;
		}
		if (raw == null) {
			raw = new int[64 * 64];
		}
		cyborg = new int[][] { wide, slim };
		flesh = new int[][] { raw, CyborgSkinComposite.slimOf(raw) };
		return true;
	}

	private static int @Nullable [] load(ResourceLocation loc) {
		Optional<Resource> res = Minecraft.getInstance().getResourceManager().getResource(loc);
		if (res.isEmpty()) {
			return null;
		}
		try (InputStream in = res.get().open(); NativeImage img = NativeImage.read(in)) {
			int w = img.getWidth(), h = img.getHeight();
			int[] px = new int[w * h];
			for (int y = 0; y < h; y++) {
				for (int x = 0; x < w; x++) {
					px[y * w + x] = img.getPixelRGBA(x, y);
				}
			}
			return w == 64 && h == 64 ? px : CyborgSkinComposite.resample(px, w, h, 64);
		} catch (Exception ex) {
			ProjectHeroMod.LOGGER.warn("[cyborg] could not read " + loc, ex);
			return null;
		}
	}

	private static void release(Entry e) {
		if (e.id != null) {
			Minecraft.getInstance().getTextureManager().release(e.id); // closes the DynamicTexture and its image
			e.id = null;
			e.texture = null;
		}
		e.plain = null;
	}

	/** Client tick: drop players who left or stopped being Cyborg, everything when the level changes. */
	public static void tick(Minecraft mc) {
		tick++;
		if (mc.level != lastLevel) {
			lastLevel = mc.level;
			clear();
			return;
		}
		if (tick % 40 != 0 || ENTRIES.isEmpty()) {
			return;
		}
		for (Iterator<Map.Entry<UUID, Entry>> it = ENTRIES.entrySet().iterator(); it.hasNext();) {
			Map.Entry<UUID, Entry> me = it.next();
			if (mc.level == null || mc.level.getPlayerByUUID(me.getKey()) == null || tick - me.getValue().lastTick > IDLE_TICKS) {
				release(me.getValue());
				it.remove();
			}
		}
	}

	/** Disconnect / level change: every texture goes (Cyborg's own skins are re-read next time, after a resource pack change too). */
	public static void clear() {
		for (Entry e : ENTRIES.values()) {
			release(e);
		}
		ENTRIES.clear();
		cyborg = null;
		flesh = null;
	}

	/** Players with a texture right now (debug / tests). */
	public static int size() {
		return ENTRIES.size();
	}
}
