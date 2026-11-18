package com.projecthero.mod.client.sentry;

import java.util.ArrayList;
import java.util.List;

import com.projecthero.mod.client.gui.MoveListOverlay;
import com.projecthero.mod.client.gui.VanillaHud;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.sentry.Sentry;
import com.projecthero.mod.sentry.SentryAbilityManager;
import com.projecthero.mod.sentry.SentryConfig;
import com.projecthero.mod.sentry.data.SentryState;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * v0.17.5 Sentry HUD, bottom-right, on the shared Minecraft-style column ({@link VanillaHud.Stack}):
 * <pre>
 *   Flying  Boost 0:21  Shield 0:04  Sense  Suit  Trial    status tags (wrap)
 *   THE SENTRY / THE VOID
 *   Sun  [the XP bar, tinted yellow]  82   fuels the Sentry moves (round 3: the real XP bar sprites, {@link #xpBar})
 *   Void [the XP bar, tinted red]     47   the danger: bright red low, darkening to near-black red full; 50/75/90 notches
 *   +----+----+----+----+----+----+           R G Z X C V: cooldowns; greyed when the Sun can't pay; yellow while Shift shows Shift
 *                                             (round 3: X shows the Flight Boost while flying, its seconds left while it runs;
 *                                             H = Let It Out / Return and 2x Space = flight are in the move list, no H box)
 * </pre>
 * The Void's thresholds also draw here, over the whole view: black veins creeping in from the screen edges from 50 Void (thicker at
 * 75), and from 90 a heartbeat pulse of darkness. Hold Left-Alt for the move list. The takeover's own view is
 * {@link VoidTakeoverClient}'s (this HUD steps aside then).
 */
public final class SentryHud {
	private static final AbilitySlot[] SLOTS = VanillaHud.ORDER;
	private static final int GOLD_TEXT = 0xFFFFC83A;
	private static final int VOID_TEXT = 0xFFB088FF;

	/** Set only by the client screenshot harness: the HUD draws as if the player were sneaking. */
	public static volatile boolean forceShift;

	private SentryHud() {
	}

	static String clock(long ticks) {
		long s = (ticks + 19) / 20;
		return (s / 60) + ":" + (s % 60 < 10 ? "0" : "") + (s % 60);
	}

	public static void render(GuiGraphics g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer p = mc.player;
		if (p == null || mc.level == null) {
			return;
		}
		SentryState s = Sentry.peek(p);
		if (s == null || !s.hasPower) {
			return;
		}
		float partial = delta.getGameTimeDeltaPartialTick(false);
		if (!s.inTakeover()) {
			thresholds(g, mc, s, partial);
		}
		if (mc.options.hideGui || s.inTakeover()) {
			return;
		}
		var exp = p.getAttachedOrElse(com.projecthero.mod.attachment.ModAttachments.EXPERIMENTAL_STATE, null);
		if (exp != null && !exp.activePower.isEmpty()) {
			return;
		}
		long now = mc.level.getGameTime();
		boolean dark = s.voidForm;
		VanillaHud.Stack hud = VanillaHud.Stack.bottomRight(g, SLOTS.length);
		hud.keys(SLOTS.length);
		keys(hud, p, s, dark);
		voidLine(hud, s, now, partial);
		sunLine(hud, s, now, partial);
		hud.title(Component.translatable(dark ? "hud.projecthero.sentry.title_void" : "hud.projecthero.sentry.title"), dark ? VOID_TEXT : GOLD_TEXT);
		List<VanillaHud.Tag> tags = new ArrayList<>();
		if (s.flying) {
			tags.add(new VanillaHud.Tag(Component.translatable("hud.projecthero.sentry.flying").getString(), VanillaHud.WHITE));
		}
		if (s.boostUntil > now) {
			tags.add(new VanillaHud.Tag(Component.translatable("hud.projecthero.sentry.boost").getString() + " " + clock(s.boostUntil - now), GOLD_TEXT));
		}
		if (s.senseUntil > now) {
			tags.add(new VanillaHud.Tag(Component.translatable("hud.projecthero.sentry.sense").getString() + " " + clock(s.senseUntil - now), GOLD_TEXT));
		}
		if (s.shroudUntil > now) {
			tags.add(new VanillaHud.Tag(Component.translatable("hud.projecthero.sentry.shroud").getString() + " " + clock(s.shroudUntil - now), VOID_TEXT));
		}
		if (s.meldUntil > now) {
			tags.add(new VanillaHud.Tag(Component.translatable("hud.projecthero.sentry.meld").getString() + " " + clock(s.meldUntil - now), VOID_TEXT));
		}
		if (s.shieldUntil > now) {
			tags.add(new VanillaHud.Tag(Component.translatable("hud.projecthero.sentry.shield").getString() + " " + clock(s.shieldUntil - now), GOLD_TEXT));
		}
		if (s.nightfallUntil > now) {
			tags.add(new VanillaHud.Tag(Component.translatable("hud.projecthero.sentry.eclipse").getString() + " " + clock(s.nightfallUntil - now), VOID_TEXT));
		}
		if (s.sunsStart != 0L) {
			tags.add(new VanillaHud.Tag(Component.translatable("hud.projecthero.sentry.suns").getString(), GOLD_TEXT));
		}
		if (Sentry.suitWorn(p)) {
			tags.add(new VanillaHud.Tag(Component.translatable("hud.projecthero.sentry.suit").getString(), 0xFF7FA8FF));
		}
		if (s.letOutTrialStart != 0L) {
			tags.add(new VanillaHud.Tag(Component.translatable("hud.projecthero.sentry.trial").getString(), VOID_TEXT));
		}
		if (s.letOutLockedUntil > now) {
			tags.add(new VanillaHud.Tag(Component.translatable("hud.projecthero.sentry.locked").getString() + " " + clock(s.letOutLockedUntil - now), 0xFFAAAAAA));
		}
		hud.tags(tags);
		List<MoveListOverlay.Row> rows = new ArrayList<>();
		if (MoveListOverlay.held()) {
			// round 3: H is Let It Out (merged: Return); Shift+H stays the power wheel
			rows.add(MoveListOverlay.Row.of(MoveListOverlay.key(6), Component.translatable("projecthero.sentry.ability."
					+ (dark ? SentryAbilityManager.RETURN : SentryAbilityManager.LET_IT_OUT)), null));
			for (AbilitySlot slot : SLOTS) {
				String[] ids = SentryAbilityManager.idsOf(slot, dark);
				if (ids.length == 0) {
					continue;
				}
				if (slot == AbilitySlot.SLOT_3 && !dark) {
					// X: the dash on the ground, the Flight Boost in the air
					rows.add(MoveListOverlay.Row.of(MoveListOverlay.key(slot), Component.translatable("hud.projecthero.sentry.x_both"), null));
					continue;
				}
				rows.add(MoveListOverlay.Row.of(MoveListOverlay.key(slot), Component.translatable("projecthero.sentry.ability." + ids[0]),
						ids.length > 1 ? Component.translatable("projecthero.sentry.ability." + ids[1]) : null));
			}
			// v0.17.5 rework: flight is double-tap Space (both forms)
			rows.add(MoveListOverlay.inline(Component.translatable("hud.projecthero.sentry.key_space").getString(),
					Component.translatable("projecthero.sentry.ability.flight")));
		}
		hud.moveList(rows);
	}

	private static void keys(VanillaHud.Stack hud, LocalPlayer p, SentryState s, boolean dark) {
		boolean shift = forceShift || p.isShiftKeyDown();
		long now = p.level().getGameTime();
		for (int i = 0; i < SLOTS.length; i++) {
			AbilitySlot slot = SLOTS[i];
			String[] ids = SentryAbilityManager.idsOf(slot, dark);
			if (ids.length == 0) {
				hud.slot(i, VanillaHud.keyLabel(slot), false, false, 0f, 0, 0f, false);
				continue;
			}
			String shown = ids[shift && ids.length > 1 ? 1 : 0];
			String other = ids.length > 1 ? ids[shift ? 0 : 1] : null;
			if (slot == AbilitySlot.SLOT_3 && !dark && (s.flying || s.boostUntil > now)) {
				// round 3: X in the air is the Flight Boost -- while it runs the box counts its seconds down, lit
				shown = SentryAbilityManager.FLIGHT_BOOST;
				if (s.boostUntil > now) {
					int left = (int) (s.boostUntil - now);
					hud.slot(i, VanillaHud.keyLabel(slot), true, shift, 1f - left / (float) SentryConfig.BOOST_TICKS, (left + 19) / 20, 0f, true);
					continue;
				}
			}
			int cd = SentryAbilityManager.cooldownRemaining(p, shown);
			float frac = Math.min(1f, cd / (float) Math.max(1, SentryAbilityManager.maxCooldown(shown)));
			float otherFrac = other == null ? 0f
					: Math.min(1f, SentryAbilityManager.cooldownRemaining(p, other) / (float) Math.max(1, SentryAbilityManager.maxCooldown(other)));
			boolean usable = SentryAbilityManager.allowed(p, shown);
			boolean active = (SentryAbilityManager.SOLAR_GAZE.equals(shown) && s.beamStart != 0L)
					|| (SentryAbilityManager.GOLDEN_SENSE.equals(shown) && s.senseUntil > now)
					|| (SentryAbilityManager.UMBRAL_SHROUD.equals(shown) && s.shroudUntil > now)
					|| (SentryAbilityManager.SHADOW_MELD.equals(shown) && s.meldUntil > now)
					|| (SentryAbilityManager.MOLECULAR_SHIELD.equals(shown) && s.shieldUntil > now)
					|| (SentryAbilityManager.MILLION_SUNS.equals(shown) && s.sunsStart != 0L)
					|| (SentryAbilityManager.DISASSEMBLE.equals(shown) && s.gripTarget >= 0)
					|| (SentryAbilityManager.SHADOW_ECLIPSE.equals(shown) && s.nightfallUntil > now);
			hud.slot(i, VanillaHud.keyLabel(slot), usable, shift, frac, cd > 0 ? (cd + 19) / 20 : 0, otherFrac, active);
		}
		if (s.sunsStart != 0L) {
			hud.hold(2, Mth.clamp((now - s.sunsStart) / (float) SentryConfig.SUNS_CHARGE_TICKS, 0f, 1f)); // Z: the charge rising
		}
		if (s.gripTarget >= 0) {
			hud.hold(1, Mth.clamp((now - s.gripStart) / (float) SentryConfig.UNMAKE_TEAR_TICKS, 0f, 1f)); // G: Disassemble coming apart
		}
	}

	// ---------------------------------------------------------------- the two bars (round 3: the vanilla XP bar itself)

	private static final net.minecraft.resources.ResourceLocation XP_BACKGROUND = net.minecraft.resources.ResourceLocation.withDefaultNamespace("hud/experience_bar_background");
	private static final net.minecraft.resources.ResourceLocation XP_PROGRESS = net.minecraft.resources.ResourceLocation.withDefaultNamespace("hud/experience_bar_progress");
	/** The XP bar sprites' size. */
	private static final int XP_W = 182, XP_H = 5;
	/** A grey copy of the vanilla progress sprite (its shading as brightness, the body at 1.0), made once from the resource pack. */
	private static net.minecraft.resources.ResourceLocation xpGrey;
	private static boolean xpGreyTried;
	/** Set only by the screenshot harness: draw the old bevelled bar (for the before / after sheet). */
	public static volatile boolean oldBars;

	/**
	 * Round 3 (user: "Make the bars look like experience bars and not these square bars please"): the real vanilla XP bar -- its
	 * background sprite (untouched) and its progress sprite turned grey ({@link #xpGrey}: each pixel's brightness relative to the green
	 * body, so the highlight row, the dark edge and the notches all stay) and tinted by the shader colour to {@code rgb} (Sun yellow, the
	 * Void's darkening red). Both are cropped from the left to {@code w} (at most 182) with the right-hand end cap kept, so the notches keep
	 * vanilla's spacing; 5 px tall like the XP bar. {@code marks}: extra notches (0..1) a shade stronger (the Void's 50 / 75 / 90).
	 */
	static void xpBar(GuiGraphics g, int x, int y, int w, float frac, int rgb, float brighten, float[] marks) {
		w = Math.max(4, Math.min(XP_W, w));
		com.mojang.blaze3d.systems.RenderSystem.enableBlend();
		g.blitSprite(XP_BACKGROUND, XP_W, XP_H, 0, 0, x, y, w - 1, XP_H);
		g.blitSprite(XP_BACKGROUND, XP_W, XP_H, XP_W - 1, 0, x + w - 1, y, 1, XP_H);
		int fill = Math.round(w * Mth.clamp(frac, 0f, 1f));
		if (fill > 0) {
			float k = grey0() ? brighten / 0.8f : brighten;
			float r = ((rgb >> 16) & 0xFF) / 255f * k;
			float gg = ((rgb >> 8) & 0xFF) / 255f * k;
			float b = (rgb & 0xFF) / 255f * k;
			net.minecraft.resources.ResourceLocation grey = xpGrey();
			g.flush();
			com.mojang.blaze3d.systems.RenderSystem.setShaderColor(r, gg, b, 1f);
			if (grey != null) {
				if (fill >= w) {
					g.blit(grey, x, y, 0, 0, w - 1, XP_H, XP_W, XP_H);
					g.blit(grey, x + w - 1, y, XP_W - 1, 0, 1, XP_H, XP_W, XP_H);
				} else {
					g.blit(grey, x, y, 0, 0, fill, XP_H, XP_W, XP_H);
				}
			} else if (fill >= w) {
				g.blitSprite(XP_PROGRESS, XP_W, XP_H, 0, 0, x, y, w - 1, XP_H);
				g.blitSprite(XP_PROGRESS, XP_W, XP_H, XP_W - 1, 0, x + w - 1, y, 1, XP_H);
			} else {
				g.blitSprite(XP_PROGRESS, XP_W, XP_H, 0, 0, x, y, fill, XP_H);
			}
			g.flush();
			com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
		}
		for (float m : marks) {
			int nx = x + Math.round(w * m);
			g.fill(nx, y + 1, nx + 1, y + XP_H - 1, nx < x + fill ? 0xB0000000 : 0x70FFFFFF);
		}
	}

	/**
	 * The grey progress sprite: read once from the active resource pack's {@code textures/gui/sprites/hud/experience_bar_progress.png}; every
	 * pixel's brightness = its largest channel over the sprite's most common largest channel (the body), with the body at 0.8 (so the highlight keeps headroom), as
	 * grey with the alpha kept. Null (the green sprite is used, tinted) if the image can't be read.
	 */
	private static boolean grey0() {
		return xpGrey() != null;
	}

	private static net.minecraft.resources.ResourceLocation xpGrey() {
		if (xpGreyTried) {
			return xpGrey;
		}
		xpGreyTried = true;
		var mc = Minecraft.getInstance();
		var res = mc.getResourceManager().getResource(net.minecraft.resources.ResourceLocation.withDefaultNamespace("textures/gui/sprites/hud/experience_bar_progress.png"));
		if (res.isEmpty()) {
			return null;
		}
		try (var in = res.get().open(); var src = com.mojang.blaze3d.platform.NativeImage.read(in)) {
			int[] hist = new int[256];
			for (int y = 0; y < src.getHeight(); y++) {
				for (int x = 0; x < src.getWidth(); x++) {
					int c = src.getPixelRGBA(x, y); // ABGR
					if ((c >>> 24) > 0) {
						hist[Math.max(c & 0xFF, Math.max((c >> 8) & 0xFF, (c >> 16) & 0xFF))]++;
					}
				}
			}
			int body = 1;
			for (int i = 1; i < 256; i++) {
				if (hist[i] > hist[body]) {
					body = i;
				}
			}
			var out = new com.mojang.blaze3d.platform.NativeImage(src.getWidth(), src.getHeight(), true);
			for (int y = 0; y < src.getHeight(); y++) {
				for (int x = 0; x < src.getWidth(); x++) {
					int c = src.getPixelRGBA(x, y);
					int m = Math.max(c & 0xFF, Math.max((c >> 8) & 0xFF, (c >> 16) & 0xFF));
					int v = Math.min(255, Math.round(204f * m / (float) body)); // the body at 0.8: the highlight keeps its headroom
					v = Math.min(255, v);
					out.setPixelRGBA(x, y, (c & 0xFF000000) | (v << 16) | (v << 8) | v);
				}
			}
			xpGrey = mc.getTextureManager().register("projecthero_sentry_xp_grey", new net.minecraft.client.renderer.texture.DynamicTexture(out));
		} catch (Exception e) {
			xpGrey = null;
		}
		return xpGrey;
	}

	/**
	 * v0.17.5 rework (user: "make the sentry bars look better, make the sun bar yellow and make the void bar fill up with a red and the
	 * more full the bar gets the darker it becomes, make them both minecraft style bars"): a new vanilla-style bar, drawn with fills so
	 * it stays crisp at every GUI scale -- the XP bar's look: a 1-px near-black outline, a dark trough (its top row darker still, like a
	 * recess), and a 5-px fill bevelled like vanilla pixel art (a light top row, the body, a dark bottom row), with the XP bar's ten
	 * notches. {@code rgb} is the fill body; {@code marks} are extra notches (0..1) drawn a little stronger (the Void's 50 / 75 / 90).
	 */
	static void bevelBar(GuiGraphics g, int x, int y, int w, float frac, int rgb, float highlight, float[] marks) {
		g.fill(x - 1, y - 1, x + w + 1, y + 6, 0xFF0B0B0B); // outline
		g.fill(x, y, x + w, y + 5, 0xFF2E2A26); // trough
		g.fill(x, y, x + w, y + 1, 0xFF1C1915); // its recessed top row
		int fill = Math.round(w * Mth.clamp(frac, 0f, 1f));
		if (fill > 0) {
			g.fill(x, y, x + fill, y + 5, 0xFF000000 | rgb);
			g.fill(x, y, x + fill, y + 1, 0xFF000000 | scale(rgb, highlight));
			g.fill(x, y + 1, x + fill, y + 2, 0xFF000000 | scale(rgb, 1f + (highlight - 1f) * 0.4f));
			g.fill(x, y + 4, x + fill, y + 5, 0xFF000000 | scale(rgb, 0.62f));
			g.fill(x + fill - 1, y, x + fill, y + 5, 0xFF000000 | scale(rgb, 0.8f)); // the fill's edge
		}
		for (int i = 1; i < 10; i++) { // the XP bar's notches
			int nx = x + Math.round(w * i / 10f);
			g.fill(nx, y + 1, nx + 1, y + 4, nx < x + fill ? 0x40000000 : 0x30FFFFFF);
		}
		for (float m : marks) {
			int nx = x + Math.round(w * m);
			g.fill(nx, y, nx + 1, y + 5, nx < x + fill ? 0x90000000 : 0x60FFFFFF);
		}
	}

	/** {@code rgb} with every channel times {@code k} (clamped). */
	static int scale(int rgb, float k) {
		int r = Math.min(255, Math.round(((rgb >> 16) & 0xFF) * k));
		int gg = Math.min(255, Math.round(((rgb >> 8) & 0xFF) * k));
		int b = Math.min(255, Math.round((rgb & 0xFF) * k));
		return (r << 16) | (gg << 8) | b;
	}

	static int lerp(int a, int b, float t) {
		int r = Math.round(((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * t);
		int gg = Math.round(((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * t);
		int bb = Math.round((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t);
		return (r << 16) | (gg << 8) | bb;
	}

	/** The Void's red for a fill fraction: bright red empty-ish, deep crimson half way, near-black red full. */
	static int voidColour(float frac) {
		float t = Mth.clamp(frac, 0f, 1f);
		return t < 0.55f ? lerp(0xE23030, 0x8E0F12, t / 0.55f) : lerp(0x8E0F12, 0x3A0505, (t - 0.55f) / 0.45f);
	}

	/** One labelled bar row: label left, number right, the bar between (11 px tall). */
	private static void barRow(VanillaHud.Stack hud, Component label, String value, int valueColour, float frac, int rgb, float highlight,
			float[] marks) {
		GuiGraphics g = hud.g;
		hud.reserve(11);
		int y = hud.top() + 2;
		// both bars start and end at the same x (the wider of the two labels)
		int labelW = Math.max(hud.font.width(label), Math.max(hud.font.width(Component.translatable("hud.projecthero.sentry.sun")),
				hud.font.width(Component.translatable("hud.projecthero.sentry.void"))));
		int valueW = Math.max(hud.font.width("100"), hud.font.width(value));
		int barX = hud.x0 + labelW + 5;
		int barW = Math.max(20, hud.width - labelW - 5 - valueW - 4);
		g.drawString(hud.font, label, hud.x0, y, VanillaHud.WHITE, true);
		g.drawString(hud.font, value, hud.x0 + hud.width - hud.font.width(value), y, valueColour, true);
		if (oldBars) {
			bevelBar(g, barX, y + 1, barW, frac, rgb, highlight, marks);
		} else {
			xpBar(g, barX, y + 1, barW, frac, rgb, highlight > 1.2f ? 1f + (highlight - 1.2f) * 0.6f : 1f, marks);
		}
	}

	/** The Sun: a warm yellow bar (#F5C518 body, #FFE14D-ish top row, dark-gold bottom row), its highlight pulsing softly when full. */
	private static void sunLine(VanillaHud.Stack hud, SentryState s, long now, float partial) {
		float frac = Mth.clamp(s.sun / SentryConfig.SUN_MAX, 0f, 1f);
		float hi = 1.18f;
		if (frac >= 0.999f) {
			hi += 0.12f * (0.5f + 0.5f * Mth.sin((now + partial) * 0.15f));
		}
		barRow(hud, Component.translatable("hud.projecthero.sentry.sun"), String.valueOf(Math.round(s.sun)), 0xFFFFE14D, frac, 0xF5C518, hi,
				new float[0]);
	}

	/** The Void: a red bar that darkens as it fills (one colour for the whole fill, by the fill), the 50 / 75 / 90 notches marked. */
	private static void voidLine(VanillaHud.Stack hud, SentryState s, long now, float partial) {
		float frac = Mth.clamp(s.voidLevel / SentryConfig.VOID_MAX, 0f, 1f);
		int valueColour = s.voidLevel >= SentryConfig.VOID_DIMMING ? 0xFFFF5555 : s.voidLevel >= SentryConfig.VOID_WHISPERS ? 0xFFFF8A8A : VanillaHud.WHITE;
		float hi = s.voidLevel >= SentryConfig.VOID_DIMMING ? 1.35f + 0.25f * Mth.sin((now + partial) * 0.5f) : 1.35f; // a heartbeat past 90
		barRow(hud, Component.translatable("hud.projecthero.sentry.void"), String.valueOf(Math.round(s.voidLevel)), valueColour, frac,
				voidColour(frac), hi, new float[] { SentryConfig.VOID_WHISPERS / SentryConfig.VOID_MAX, SentryConfig.VOID_MISFIRE / SentryConfig.VOID_MAX,
						SentryConfig.VOID_DIMMING / SentryConfig.VOID_MAX });
	}

	/** The Void's thresholds over the view: veins from the edges (50+), a heartbeat of darkness (90+). */
	private static void thresholds(GuiGraphics g, Minecraft mc, SentryState s, float partial) {
		float v = s.voidLevel;
		if (v < SentryConfig.VOID_WHISPERS) {
			return;
		}
		int w = g.guiWidth();
		int h = g.guiHeight();
		long now = mc.level.getGameTime();
		float t = now + partial;
		float k = Mth.clamp((v - SentryConfig.VOID_WHISPERS) / (SentryConfig.VOID_MAX - SentryConfig.VOID_WHISPERS), 0f, 1f);
		// black veins creeping in from the edges: branching jagged lines, fixed per vein, growing with the Void
		int veins = 10 + Math.round(k * 14);
		int reach = Math.round(Math.min(w, h) * (0.10f + 0.30f * k));
		for (int i = 0; i < veins; i++) {
			long seed = i * 977L + 13L;
			int side = i % 4;
			float along = ((seed * 31L) % 1000L) / 1000f;
			int x = side == 0 ? 0 : side == 1 ? w - 1 : Math.round(along * w);
			int y = side == 2 ? 0 : side == 3 ? h - 1 : Math.round(along * h);
			int dx = side == 0 ? 1 : side == 1 ? -1 : 0;
			int dy = side == 2 ? 1 : side == 3 ? -1 : 0;
			int len = Math.round(reach * (0.5f + 0.5f * (((seed * 17L) % 100L) / 100f)) * (0.85f + 0.15f * Mth.sin(t * 0.05f + i)));
			int thick = v >= SentryConfig.VOID_MISFIRE ? 3 : 2;
			for (int j = 0; j < len; j += 2) {
				int wob = Math.round(Mth.sin(j * 0.18f + i * 1.7f) * 4f);
				int px = x + dx * j + (dx == 0 ? wob : 0);
				int py = y + dy * j + (dy == 0 ? wob : 0);
				int a = (int) (200 * (1f - j / (float) Math.max(1, len)));
				int tw = Math.max(1, Math.round(thick * (1f - j / (float) Math.max(1, len))));
				g.fill(px, py, px + tw, py + tw, (a << 24) | 0x080410);
				if (j == len / 2 && v >= SentryConfig.VOID_MISFIRE) {
					// a branch
					for (int b = 0; b < len / 3; b += 2) {
						int bx = px + (dx == 0 ? b / 2 : dx * b);
						int bys = py + (dy == 0 ? b / 2 : dy * b);
						g.fill(bx, bys, bx + 1, bys + 1, ((a / 2) << 24) | 0x080410);
					}
				}
			}
		}
		if (v >= SentryConfig.VOID_DIMMING) {
			// the heartbeat: two quick pulses, a pause
			float beat = (t % 24f) / 24f;
			float pulse = beat < 0.12f ? beat / 0.12f : beat < 0.24f ? 1f - (beat - 0.12f) / 0.12f
					: beat < 0.34f ? (beat - 0.24f) / 0.10f * 0.7f : beat < 0.46f ? 0.7f - (beat - 0.34f) / 0.12f * 0.7f : 0f;
			int a = (int) (90 * pulse);
			if (a > 0) {
				int band = Math.min(w, h) / 5;
				g.fillGradient(0, 0, w, band, (a << 24) | 0x05000A, 0x0005000A);
				g.fillGradient(0, h - band, w, h, 0x0005000A, (a << 24) | 0x05000A);
			}
		}
	}
}
