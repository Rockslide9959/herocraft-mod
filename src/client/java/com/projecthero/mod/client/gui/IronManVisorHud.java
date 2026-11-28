package com.projecthero.mod.client.gui;

import java.util.ArrayList;
import java.util.List;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.projecthero.mod.ironman.HulkbusterParts;
import com.projecthero.mod.ironman.ui.IronManUiLayout;
import com.projecthero.mod.ironman.ui.IronManUiLayout.Rect;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Player;

/**
 * v0.17.7 (user picked concept "A - JARVIS visor (movie look)"): the drawing pieces of the Iron Man helmet HUD.
 *
 * <ul>
 *   <li>{@link #visorArcs} -- curved dark visor edges top and bottom (heavier in the corners) with a faint cyan rim, thin
 *       cyan side arcs; red-blinking rims while the suit is offline.</li>
 *   <li>{@link #telemetry} -- the curved telemetry strip across the top: ALT, heading, SPD in the middle, the clock and
 *       X / Z on the outside when they fit. Drops under the boss bars when a boss is on screen.</li>
 *   <li>{@link #reticle} -- a minimal broken ring round the crosshair.</li>
 *   <li>{@link #topLeft} -- the user's block: "have the integrity and reactor on the top left also with which armour is being
 *       worn, have those bars be meter style bars, make them slightly long and also tell the player what it is after the
 *       bar ... have a paper doll view of the player in the top left aswell". A live paper doll in a holographic frame, the
 *       suit's name, "[Meter bar] Integrity (82%)" / "[Meter bar] Reactor (64%)", the Hulkbuster's parts, a dim notes line,
 *       then the status tags as small pills.</li>
 * </ul>
 * Layout numbers live in {@link IronManUiLayout} ({@code VISOR_*}).
 */
public final class IronManVisorHud {
	private IronManVisorHud() {
	}

	/** One "[bar] Label (value)" row of the top-left block. */
	public record Row(Component label, String value, float frac, int colour, int labelColour, int valueColour) {
	}

	/** The integrity colour: cyan, amber from 60%, red under 30% (Mark I: amber, red under 30%). */
	public static int integrityColour(float frac, boolean retro) {
		if (frac < 0.3f) {
			return IronManGui.RED;
		}
		if (retro || frac < 0.6f) {
			return IronManGui.AMBER;
		}
		return IronManGui.CYAN;
	}

	/** How many boss bars are on screen (0 when unknown). */
	public static int bossBars(Minecraft mc) {
		try {
			return ((com.projecthero.mod.client.mixin.BossHealthOverlayAccessor) mc.gui.getBossOverlay()).projecthero$events().size();
		} catch (Throwable t) {
			return 0;
		}
	}

	// ------------------------------------------------------------------ the visor frame

	/** The curved visor edges (faceplate closed, Mark II and up). */
	public static void visorArcs(GuiGraphics g, boolean offline, long now) {
		int sw = g.guiWidth();
		int sh = g.guiHeight();
		float cx = sw / 2f;
		float cy = sh / 2f;
		boolean blinkRed = offline && (now % 20) < 10;
		int rim = blinkRed ? IronManGui.RED : IronManGui.CYAN;
		int topC = 2, topE = Math.max(14, sh / 7);
		int botC = 1, botE = Math.max(12, sh / 8);
		for (int x = 0; x < sw; x += 2) {
			float t = Math.abs((x + 1 - cx) / cx);
			float k = (float) Math.pow(Math.min(1f, t), 2.4);
			int dTop = Math.round(topC + (topE - topC) * k);
			int dBot = Math.round(botC + (botE - botC) * k);
			int edgeA = Math.round(0x40 + 0x80 * k); // darker toward the corners
			g.fillGradient(x, 0, x + 2, dTop, (edgeA << 24) | 0x020A12, 0x28020A12);
			g.fillGradient(x, sh - dBot, x + 2, sh, 0x28020A12, (edgeA << 24) | 0x020A12);
			float rimA = 0.20f + 0.50f * k;
			g.fill(x, dTop, x + 2, dTop + 1, IronManGui.alpha(rim, rimA));
			g.fill(x, sh - dBot - 1, x + 2, sh - dBot, IronManGui.alpha(rim, rimA * 0.8f));
		}
		// thin cyan side arcs, bowing in at mid-height, fading out toward the corners
		int bow = Math.max(4, sw / 80);
		for (int y = 0; y < sh; y += 2) {
			float t = (y + 1 - cy) / cy;
			int x = 3 + Math.round(bow * (1f - t * t));
			int c = IronManGui.alpha(rim, (blinkRed ? 0.6f : 0.42f) * (1f - t * t * t * t));
			g.fill(x, y, x + 1, y + 2, c);
			g.fill(sw - 1 - x, y, sw - x, y + 2, c);
			// a second, fainter arc just inside it
			int c2 = IronManGui.alpha(rim, 0.16f * (1f - t * t));
			g.fill(x + 3, y, x + 4, y + 2, c2);
			g.fill(sw - 4 - x, y, sw - 3 - x, y + 2, c2);
		}
	}

	/** The minimal centre reticle: four short arcs round the crosshair and two side ticks. */
	public static void reticle(GuiGraphics g, boolean offline, long now) {
		float cx = g.guiWidth() / 2f;
		float cy = g.guiHeight() / 2f;
		int c = offline && (now % 20) < 10 ? IronManGui.alpha(IronManGui.RED, 0.5f) : 0x6637C6E8;
		var vc = IronManGui.guiBuffer(g);
		var m = g.pose().last().pose();
		for (int k = 0; k < 4; k++) {
			double mid = Math.PI / 4 + k * Math.PI / 2;
			IronManGui.sector(vc, m, cx, cy, 9.0f, 10.0f, mid - 0.38, mid + 0.38, c);
		}
		g.flush();
		int ix = Math.round(cx), iy = Math.round(cy);
		g.fill(ix - 16, iy, ix - 12, iy + 1, c);
		g.fill(ix + 13, iy, ix + 17, iy + 1, c);
	}

	// ------------------------------------------------------------------ the curved telemetry strip

	private static final String[] COMPASS = { "n", "ne", "e", "se", "s", "sw", "w", "nw" };

	/**
	 * The curved telemetry strip across the top: segments laid along a shallow arc (the middle highest), a thin cyan arc
	 * under them. Segments go in by priority -- heading, then ALT + SPD, then the clock + X / Z -- each pair only while the
	 * strip still fits {@link IronManUiLayout#visorArcMaxWidth} and stays right of {@code keepLeft} (mirrored on the right).
	 * Returns the half-width it took (the top-left block keeps clear of it), 0 when nothing was drawn.
	 */
	public static int telemetry(GuiGraphics g, Font font, Minecraft mc, Player player, int bossBars, int keepLeft) {
		if (mc.level == null) {
			return 0;
		}
		int sw = g.guiWidth();
		int sh = g.guiHeight();
		double speed = player.getDeltaMovement().horizontalDistance() * 20.0;
		float heading = ((player.getYRot() + 180f) % 360f + 360f) % 360f;
		String dir = Component.translatable("hud.projecthero.ironman.compass." + COMPASS[Math.round(heading / 45f) % 8]).getString();
		long dayTime = mc.level.getDayTime();
		long tod = ((dayTime + 6000L) % 24000L + 24000L) % 24000L;

		Component alt = seg("hud.projecthero.ironman.strip.alt", (int) Math.round(player.getY()));
		Component hdg = seg("hud.projecthero.ironman.strip.hdg", Math.round(heading) % 360 + "° " + dir);
		Component spd = seg("hud.projecthero.ironman.strip.spd", (int) Math.round(speed));
		Component clock = seg("hud.projecthero.ironman.strip.clock",
				String.format(java.util.Locale.ROOT, "%02d:%02d", tod / 1000L, (tod % 1000L) * 60L / 1000L));
		Component pos = seg("hud.projecthero.ironman.strip.pos", (int) Math.floor(player.getX()), (int) Math.floor(player.getY()),
				(int) Math.floor(player.getZ()));
		int gap = IronManUiLayout.VISOR_ARC_GAP;
		int maxW = Math.min(IronManUiLayout.visorArcMaxWidth(sw), sw - 2 * keepLeft - 28);
		List<Component> segs = new ArrayList<>();
		if (font.width(hdg) > maxW) {
			return 0;
		}
		segs.add(hdg);
		if (widthOf(font, segs, gap) + font.width(alt) + font.width(spd) + 2 * gap <= maxW) {
			segs.add(0, alt);
			segs.add(spd);
			if (widthOf(font, segs, gap) + font.width(clock) + font.width(pos) + 2 * gap <= maxW) {
				segs.add(0, clock);
				segs.add(pos);
			}
		}
		int total = widthOf(font, segs, gap);
		int top = IronManUiLayout.visorArcTop(bossBars, sh);
		int cx = sw / 2;
		int half = total / 2 + 14;
		int depth = IronManUiLayout.VISOR_ARC_DEPTH;
		// the arc line under the segments: highest in the middle, curving down at the ends (a dark line under it for bright skies)
		for (int x = -half; x <= half; x++) {
			float t = x / (float) half;
			int y = top + 11 + Math.round(depth * t * t);
			float a = 1f - Math.abs(t * t * t);
			g.fill(cx + x, y + 1, cx + x + 1, y + 2, IronManGui.alpha(0xFF000000, 0.25f * a));
			g.fill(cx + x, y, cx + x + 1, y + 1, IronManGui.alpha(IronManGui.CYAN, 0.8f * a));
		}
		// short ticks along the arc and a gold centre notch
		for (int k = -4; k <= 4; k++) {
			if (k == 0) {
				continue;
			}
			int tx = cx + k * half / 5;
			float t = (tx - cx) / (float) half;
			int ty = top + 11 + Math.round(depth * t * t);
			g.fill(tx, ty + 1, tx + 1, ty + 3, IronManGui.alpha(IronManGui.CYAN, 0.5f * (1f - Math.abs(t))));
		}
		g.fill(cx, top + 12, cx + 1, top + 16, IronManGui.alpha(IronManGui.GOLD, 0.9f));
		// the segments, each on the curve, on a soft dark backing for bright skies
		int x = cx - total / 2;
		for (Component c : segs) {
			int w = font.width(c);
			float t = (x + w / 2f - cx) / (float) half;
			int y = top + Math.round((depth - 1) * t * t);
			g.fill(x - 3, y - 1, x + w + 3, y + 9, 0x50020A12);
			g.drawString(font, c, x, y, IronManGui.TEXT, true);
			x += w + gap;
		}
		return half;
	}

	private static int widthOf(Font font, List<Component> segs, int gap) {
		int w = 0;
		for (Component c : segs) {
			w += font.width(c);
		}
		return w + gap * Math.max(0, segs.size() - 1);
	}

	/** One strip segment: the label dim cyan, the numbers bright. */
	private static Component seg(String key, Object... args) {
		Object[] bright = new Object[args.length];
		for (int i = 0; i < args.length; i++) {
			bright[i] = Component.literal(String.valueOf(args[i])).withStyle(s -> s.withColor(0xFFFFFF));
		}
		return Component.translatable(key, bright).withStyle(s -> s.withColor(IronManGui.CYAN & 0xFFFFFF));
	}

	// ------------------------------------------------------------------ the top-left block

	/**
	 * The top-left block: the paper doll in its frame, then (right of it) the suit's name, the "[bar] Label (value)" rows, the
	 * Hulkbuster parts line, the dim notes; then the status pills under the whole block. Returns the y under everything.
	 *
	 * @param name     the suit's mark ("Mk VII"), bold
	 * @param subtitle its comic name ("Silver Centurion"), dim (dropped first when space is short)
	 * @param parts    the Hulkbuster's six part damages ({@link HulkbusterParts#of}), or null
	 * @param arcHalf  the telemetry strip's half-width (the name line keeps clear of it)
	 */
	public static int topLeft(GuiGraphics g, Font font, Player player, boolean retro, boolean hulkbuster, Component name, Component subtitle,
			List<Row> rows, float[] parts, List<VanillaHud.Tag> notes, List<VanillaHud.Tag> pills, int bossBars, int arcHalf, int y0, long now) {
		int sw = g.guiWidth();
		int x0 = IronManUiLayout.HUD_X;
		int accent = retro ? IronManGui.AMBER : IronManGui.CYAN;
		int accentDim = retro ? IronManGui.AMBER_DIM : IronManGui.CYAN_DIM;
		int dollW = IronManUiLayout.VISOR_DOLL_W;
		int dollH = IronManUiLayout.VISOR_DOLL_H;
		int infoX = x0 + dollW + IronManUiLayout.VISOR_GAP;
		int rowsRight = IronManUiLayout.visorRowsRight(sw, bossBars, arcHalf);
		int nameRight = IronManUiLayout.visorNameRight(sw, bossBars, arcHalf);

		// ---- measure the rows: one bar length for all of them, the words after it
		int textW = rowsTextWidth(font, rows);
		int barW = IronManUiLayout.visorBarWidth(rowsRight - infoX, textW);
		boolean stacked = barW < 0; // too narrow for "[bar] words" on one line: the words go under the bar
		if (stacked) {
			barW = Math.max(IronManUiLayout.VISOR_BAR_MIN, Math.min(IronManUiLayout.VISOR_BAR_MAX, rowsRight - infoX));
		}
		int infoRight = stacked ? Math.max(infoX + barW, rowsRight) : Math.min(rowsRight, infoX + barW + 4 + textW);
		int infoW = Math.max(barW, infoRight - infoX);
		int rowH = stacked ? IronManUiLayout.VISOR_ROW_H + 9 : IronManUiLayout.VISOR_ROW_H;

		List<FormattedCharSequence> noteLines = flowTags(font, notes, infoW);
		int partsH = parts != null ? 11 : 0;
		int rowsTop = y0 + IronManUiLayout.VISOR_ROWS_TOP;
		int infoH = IronManUiLayout.VISOR_ROWS_TOP + 2 + rows.size() * rowH + partsH + noteLines.size() * 9 + 2;
		int blockH = Math.max(dollH, infoH);
		int nameRoom = Math.max(30, nameRight - infoX);
		Component bold = name.copy().withStyle(ChatFormatting.BOLD);
		String sub = subtitle == null ? "" : subtitle.getString();
		int nameLineW = Math.min(nameRoom, font.width(bold) + (sub.isEmpty() ? 0 : 4 + font.width(sub)));

		// ---- backing: a soft wash behind the words (stepped: the name line stays clear of the telemetry strip), an accent corner
		int bg = retro ? 0x48140A02 : 0x48020A12;
		g.fill(infoX - 3, y0, infoX + Math.min(infoW, Math.max(nameLineW, 40)) + 3, rowsTop, bg);
		g.fill(infoX - 3, rowsTop, infoX + infoW + 3, y0 + blockH, bg);
		g.fill(infoX - 3, y0, infoX + 37, y0 + 1, IronManGui.alpha(accent, 0.75f));
		g.fill(infoX - 3, y0 + 1, infoX - 2, y0 + 8, IronManGui.alpha(accent, 0.75f));

		// ---- the paper doll
		paperDollFrame(g, x0, y0, dollW, dollH, accent, accentDim, now);
		paperDoll(g, player, hulkbuster, x0 + 1, y0 + 1, x0 + dollW - 1, y0 + dollH - 4);

		// ---- name line: the mark bold, the comic name dim after it
		int ny = y0 + 3;
		String nameStr = IronManGui.fit(font, bold.getString(), nameRoom);
		int nw;
		if (font.width(bold) <= nameRoom) {
			g.drawString(font, bold, infoX, ny, accent, true);
			nw = font.width(bold);
		} else {
			g.drawString(font, Component.literal(nameStr).withStyle(ChatFormatting.BOLD), infoX, ny, accent, true);
			nw = nameRoom;
		}
		int subRoom = nameRoom - nw - 4;
		if (!sub.isEmpty() && subRoom >= font.width("Mk..")) {
			g.drawString(font, IronManGui.fit(font, sub, subRoom), infoX + nw + 4, ny, IronManGui.TEXT_DIM, true);
		}

		// ---- the rows
		int y = rowsTop + 2;
		for (Row r : rows) {
			if (stacked) {
				// a narrow screen: the bar on its own line, "Label (value)" under it
				g.flush();
				VanillaHud.bar(g, infoX, y + 1, barW, r.frac(), r.colour());
				String v = r.value() == null || r.value().isEmpty() ? "" : " (" + r.value() + ")";
				String l = IronManGui.fit(font, r.label().getString(), Math.max(10, infoW - font.width(v)));
				g.drawString(font, l, infoX, y + 8, r.labelColour(), true);
				g.drawString(font, IronManGui.fit(font, v, Math.max(0, infoW - font.width(l))), infoX + font.width(l), y + 8, r.valueColour(), true);
			} else {
				VanillaHud.meterAfter(g, font, infoX, y, barW, infoX + infoW, r.label(), r.value(), r.frac(), r.colour(), r.labelColour(),
						r.valueColour());
			}
			y += rowH;
		}

		// ---- the Hulkbuster's six parts, each coloured by its own health
		if (parts != null) {
			hulkbusterParts(g, font, infoX, y, infoW, parts, now);
			y += partsH;
		}

		// ---- dim notes (hull / power exact, absorb, regen)
		for (FormattedCharSequence l : noteLines) {
			g.drawString(font, l, infoX, y, IronManGui.TEXT_DIM, true);
			y += 9;
		}

		// ---- the status pills under the whole block
		int py = y0 + blockH + 4;
		int pillsW = Math.max(dollW + IronManUiLayout.VISOR_GAP + infoW, 80);
		return pills(g, font, x0, py, pillsW, pills);
	}

	/** The widest "Label (value)" of {@code rows}. */
	public static int rowsTextWidth(Font font, List<Row> rows) {
		int w = 0;
		for (Row r : rows) {
			w = Math.max(w, rowTextWidth(font, r));
		}
		return w;
	}

	private static int rowTextWidth(Font font, Row r) {
		String v = r.value() == null || r.value().isEmpty() ? "" : " (" + r.value() + ")";
		return font.width(r.label()) + font.width(v);
	}

	/** The holographic frame behind the paper doll: a faint wash, scanlines, a floor ring and corner brackets. */
	private static void paperDollFrame(GuiGraphics g, int x, int y, int w, int h, int accent, int accentDim, long now) {
		g.fill(x, y, x + w, y + h, IronManGui.alpha(accentDim, 0.22f));
		for (int sy = y + 2; sy < y + h - 1; sy += 3) {
			g.fill(x + 1, sy, x + w - 1, sy + 1, IronManGui.alpha(accent, 0.06f));
		}
		// a slow scan sweep down the frame
		int sweep = y + 1 + (int) ((now % 60) * (h - 3) / 60);
		g.fill(x + 1, sweep, x + w - 1, sweep + 1, IronManGui.alpha(accent, 0.22f));
		// the floor ring the doll stands on
		int fy = y + h - 5;
		g.fill(x + 6, fy, x + w - 6, fy + 1, IronManGui.alpha(accent, 0.45f));
		g.fill(x + 10, fy + 1, x + w - 10, fy + 2, IronManGui.alpha(accent, 0.25f));
		g.renderOutline(x, y, w, h, IronManGui.alpha(accentDim, 0.45f));
		IronManGui.brackets(g, x, y, w, h, 5, IronManGui.alpha(accent, 0.9f));
	}

	/**
	 * The live player, small, turned a little to the side like the inventory screen's doll -- wearing whatever suit is on.
	 * The Hulkbuster is drawn as its own body. Never throws.
	 */
	private static void paperDoll(GuiGraphics g, Player p, boolean hulkbuster, int x0, int y0, int x1, int y1) {
		float yaw = 180f + IronManUiLayout.VISOR_DOLL_YAW;
		if (hulkbuster) {
			com.projecthero.mod.client.ironman.HulkbusterDisplay.preview(g, x0, y0, x1, y1, yaw);
			return;
		}
		if (p.isInvisible()) {
			return;
		}
		float bodyRot = p.yBodyRot, bodyRotO = p.yBodyRotO, yRot = p.getYRot(), yRotO = p.yRotO;
		float xRot = p.getXRot(), xRotO = p.xRotO, head = p.yHeadRot, headO = p.yHeadRotO;
		g.flush();
		g.enableScissor(x0, y0, x1, y1);
		try {
			p.yBodyRot = yaw;
			p.yBodyRotO = yaw;
			p.setYRot(yaw);
			p.yRotO = yaw;
			p.setXRot(-4f);
			p.xRotO = -4f;
			p.yHeadRot = yaw - 10f;
			p.yHeadRotO = yaw - 10f;
			float h = 1.8f; // a fixed frame (a crouch or a flight pose must not zoom the doll)
			float scale = (y1 - y0) * 0.88f / h;
			float cx = (x0 + x1) / 2f;
			float cy = (y0 + y1) / 2f + 1f;
			Quaternionf pose = new Quaternionf().rotateZ((float) Math.PI);
			Quaternionf cam = new Quaternionf().rotateX((float) Math.toRadians(-6));
			pose.mul(cam);
			InventoryScreen.renderEntityInInventory(g, cx, cy, scale, new Vector3f(0f, h / 2f, 0f), pose, cam, p);
		} catch (RuntimeException ignored) {
			// a third-party renderer misbehaving must never take the HUD down with it
		} finally {
			p.yBodyRot = bodyRot;
			p.yBodyRotO = bodyRotO;
			p.setYRot(yRot);
			p.yRotO = yRotO;
			p.setXRot(xRot);
			p.xRotO = xRotO;
			p.yHeadRot = head;
			p.yHeadRotO = headO;
			g.disableScissor();
		}
	}

	/** "Parts  HD TR LA RA LL RL": each part's letters in its health colour (holo / orange / red; broken: blinking dark red). */
	private static void hulkbusterParts(GuiGraphics g, Font font, int x, int y, int maxW, float[] eff, long now) {
		String label = Component.translatable("hud.projecthero.ironman.hud.parts").getString();
		g.drawString(font, label, x, y, IronManGui.TEXT_DIM, true);
		int cx = x + font.width(label) + 5;
		boolean blink = (now % 16) < 9;
		for (int i = 0; i < HulkbusterParts.COUNT && i < eff.length; i++) {
			String s = Component.translatable("hud.projecthero.ironman.hb_abbr." + HulkbusterParts.NAMES[i]).getString();
			if (cx + font.width(s) > x + maxW) {
				break;
			}
			float hp = 1f - eff[i];
			boolean broken = HulkbusterParts.broken(eff[i]);
			int c = broken ? (blink ? IronManGui.RED : 0xFF701010) : hp > 0.66f ? IronManGui.CYAN : hp > 0.33f ? IronManGui.ORANGE : IronManGui.RED;
			g.drawString(font, s, cx, y, c, true);
			if (broken) {
				g.fill(cx - 1, y + 4, cx + font.width(s), y + 5, 0xFFFF3030); // struck through
			}
			cx += font.width(s) + 4;
		}
	}

	/** Dim note tags flowed onto lines no wider than {@code maxW} (at most two lines), separated by " . ". */
	private static List<FormattedCharSequence> flowTags(Font font, List<VanillaHud.Tag> tags, int maxW) {
		List<FormattedCharSequence> out = new ArrayList<>();
		if (tags == null || tags.isEmpty()) {
			return out;
		}
		net.minecraft.network.chat.MutableComponent line = Component.empty();
		int w = 0;
		String sep = "  ";
		for (VanillaHud.Tag t : tags) {
			String text = IronManGui.fit(font, t.text(), maxW);
			int tw = font.width(text);
			if (w > 0 && w + font.width(sep) + tw > maxW) {
				out.add(line.getVisualOrderText());
				if (out.size() >= 2) {
					return out;
				}
				line = Component.empty();
				w = 0;
			}
			if (w > 0) {
				line.append(sep);
				w += font.width(sep);
			}
			int colour = t.colour();
			line.append(Component.literal(text).withStyle(s -> s.withColor(colour & 0xFFFFFF)));
			w += tw;
		}
		if (w > 0) {
			out.add(line.getVisualOrderText());
		}
		return out;
	}

	/** The status tags as small pills (dark wash, a coloured left edge, shadowed text), flowed under the block. */
	private static int pills(GuiGraphics g, Font font, int x, int y, int maxW, List<VanillaHud.Tag> tags) {
		if (tags == null || tags.isEmpty()) {
			return y;
		}
		int[] widths = new int[tags.size()];
		String[] texts = new String[tags.size()];
		for (int i = 0; i < widths.length; i++) {
			texts[i] = IronManGui.fit(font, tags.get(i).text(), maxW - 8);
			widths[i] = font.width(texts[i]) + 8;
		}
		List<Rect> rects = IronManUiLayout.flowChips(widths, x, y, maxW, 3, 11);
		int bottom = y;
		for (int i = 0; i < rects.size(); i++) {
			Rect r = rects.get(i);
			int c = tags.get(i).colour();
			g.fill(r.x(), r.y(), r.x() + r.w(), r.y() + 11, 0x9A020A12);
			g.fill(r.x(), r.y(), r.x() + 1, r.y() + 11, IronManGui.alpha(c, 0.95f));
			g.fill(r.x() + 1, r.y() + 10, r.x() + r.w(), r.y() + 11, IronManGui.alpha(c, 0.25f));
			g.drawString(font, texts[i], r.x() + 5, r.y() + 2, c, true);
			bottom = Math.max(bottom, r.y() + 11);
		}
		return bottom;
	}
}
