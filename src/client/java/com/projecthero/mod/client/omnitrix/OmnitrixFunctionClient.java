package com.projecthero.mod.client.omnitrix;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.OmnitrixCodeEntry;
import com.projecthero.mod.omnitrix.OmnitrixCodes;
import com.projecthero.mod.omnitrix.OmnitrixConfig;
import com.projecthero.mod.omnitrix.OmnitrixFunction;
import com.projecthero.mod.omnitrix.OmnitrixSounds;
import com.projecthero.mod.omnitrix.alien.Aliens;
import com.projecthero.mod.omnitrix.data.OmnitrixState;
import com.projecthero.mod.omnitrix.network.OmnitrixActionPayload;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * v0.17.5 (user: "after priming the omnitrix the player can shift+right click to go into function mode ... scrolling up or down
 * changes the number for the current code and pressing middle mouse button goes onto the next number"): the client side of the
 * watch's function mode -- the typing ({@link OmnitrixCodeEntry}, only the finished code goes to the server), the input hooks
 * (Shift + right-click, the scroll wheel, the middle button; {@code OmnitrixClient}, {@code mixin/OmnitrixScrollMixin},
 * {@code mixin/OmnitrixInputMixin}), the readout drawn on the watch face ({@link #readout}, used by {@link OmnitrixWatch}), the
 * HUD box and the self-destruct banner ({@link #drawHud}), and the Guidebook's "what you have found" lines.
 */
public final class OmnitrixFunctionClient {
	/** The readout's glyph atlas: 16 cells of 16x32 -- digits 0-9, the cursor bar (10), solid (11), a dash (12). */
	public static final ResourceLocation GLYPHS = ProjectHeroMod.id("textures/entity/omnitrix/code_glyphs.png");
	public static final int CURSOR = 10, SOLID = 11, DASH = 12;
	static final int GREEN = 0x7CFF2A, GREEN_DIM = 0x2E7A14, GREEN_HOT = 0xE2FFD2, RED = 0xFF3A26, RED_DIM = 0x6A140E, AMBER = 0xFFB020;

	/** What this client is typing. */
	private static final OmnitrixCodeEntry ENTRY = new OmnitrixCodeEntry();
	/** The function-mode session the entry belongs to (the server's FUNC time): a new session puts the cursor back on slot 1. */
	private static long session = Long.MIN_VALUE;
	/** The last code this client sent (shown with its green hints while the lock lasts). */
	private static String lastSent = "";
	/** The use key is still down from the Shift + right-click that toggled (a held button must not toggle again). */
	private static boolean toggleHeld;
	/** The Guidebook page's found-lines signature (rebuilt when it changes). */
	private static String guideSig = "";

	private OmnitrixFunctionClient() {
	}

	// ---------------------------------------------------------------- input

	static boolean inFunction(LocalPlayer p) {
		return p != null && OmnitrixFunction.active(p);
	}

	/** The entry, reset when a new function-mode session starts. */
	private static OmnitrixCodeEntry entry(OmnitrixState s) {
		long at = s.readyAt(OmnitrixFunction.FUNC);
		if (at != session) {
			session = at;
			ENTRY.set(ENTRY.code()); // keep the digits (handy for a Mastermind guess), cursor back to slot 1
			if (OmnitrixFunction.lastOutcome(s) == OmnitrixFunction.Outcome.MASTER || OmnitrixFunction.lastOutcome(s) == OmnitrixFunction.Outcome.ALIEN) {
				ENTRY.reset();
			}
		}
		return ENTRY;
	}

	/**
	 * A right-click while the watch is up: Shift + right-click with the core up goes into function mode, and out again. True =
	 * handled (nothing else may happen with the click). In function mode every other click is swallowed.
	 */
	static boolean onRightClick(LocalPlayer p) {
		OmnitrixState s = Omnitrix.state(p);
		boolean in = OmnitrixFunction.active(s);
		if (p.isShiftKeyDown() && (in || Omnitrix.coreUp(s, p.level().getGameTime()))) {
			if (!toggleHeld) {
				toggleHeld = true;
				ClientPlayNetworking.send(new OmnitrixActionPayload(OmnitrixActionPayload.Action.FUNCTION, ""));
			}
			return true;
		}
		return in;
	}

	/** The scroll wheel in function mode: the current slot's digit. True = consumed. */
	static boolean onScroll(LocalPlayer p, double dy) {
		OmnitrixState s = Omnitrix.state(p);
		if (!OmnitrixFunction.active(s)) {
			return false;
		}
		if (dy == 0 || OmnitrixFunction.locked(s, p.level().getGameTime())) {
			return true;
		}
		entry(s).scroll(dy > 0 ? 1 : -1);
		ui(OmnitrixSounds.USER_DIAL_CLICK, 1.35f + 0.05f * entry(s).digit(entry(s).slot()), 0.35f);
		return true;
	}

	/** The middle mouse button in function mode: next slot (Shift: back); on the sixth the code goes to the server. True = consumed. */
	public static boolean onMiddleClick() {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer p = mc.player;
		if (p == null || mc.screen != null || !inFunction(p)) {
			return false;
		}
		OmnitrixState s = Omnitrix.state(p);
		if (OmnitrixFunction.locked(s, p.level().getGameTime())) {
			ui(SoundEvents.LEVER_CLICK, 0.6f, 0.2f);
			return true;
		}
		OmnitrixCodeEntry e = entry(s);
		if (p.isShiftKeyDown()) {
			e.back();
			ui(SoundEvents.LEVER_CLICK, 1.2f, 0.25f);
			return true;
		}
		String code = e.advance();
		if (code == null) {
			ui(SoundEvents.LEVER_CLICK, 1.6f, 0.3f);
			return true;
		}
		lastSent = code;
		ui(SoundEvents.IRON_TRAPDOOR_CLOSE, 1.9f, 0.25f);
		ClientPlayNetworking.send(new OmnitrixActionPayload(OmnitrixActionPayload.Action.CODE, code));
		return true;
	}

	/** Middle-click must not pick a block while the watch is in function mode. */
	public static boolean blocksPick() {
		Minecraft mc = Minecraft.getInstance();
		return mc.player != null && inFunction(mc.player);
	}

	private static void ui(SoundEvent sound, float pitch, float volume) {
		Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
	}

	/** END_CLIENT_TICK: the toggle's release, the Guidebook page's found-lines. */
	static void tick(Minecraft mc) {
		if (toggleHeld && !mc.options.keyUse.isDown()) {
			toggleHeld = false;
		}
		LocalPlayer p = mc.player;
		String sig = p == null || p.getAttached(com.projecthero.mod.omnitrix.OmnitrixAttachments.STATE) == null ? ""
				: OmnitrixCodes.mask(p, "_", "") + OmnitrixCodes.knownAlienCodes(p) + foundSig(p);
		if (!sig.equals(guideSig)) {
			guideSig = sig;
			com.projecthero.mod.hero.guide.HeroPackGuide.refreshMasterControl();
		}
	}

	private static String foundSig(Player p) {
		StringBuilder b = new StringBuilder();
		for (String id : OmnitrixCodes.knownAlienCodes(p).keySet()) {
			b.append(Aliens.found(p, id) ? '1' : '0');
		}
		return b.toString();
	}

	/** The Guidebook's Master Control page: the reader's found digits and hidden-alien codes. */
	public static List<Component> guideLines() {
		List<Component> out = new ArrayList<>();
		LocalPlayer p = Minecraft.getInstance().player;
		if (p == null || p.getAttached(com.projecthero.mod.omnitrix.OmnitrixAttachments.STATE) == null) {
			return out;
		}
		int known = OmnitrixCodes.knownCount(p);
		if (known > 0) {
			out.add(Component.translatable("projecthero.guide.omnitrix.master.found.code",
					Component.literal(OmnitrixCodes.mask(p, "_", " ")).withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)).withStyle(ChatFormatting.WHITE));
			out.add(Component.translatable("projecthero.guide.omnitrix.master.found.count", known).withStyle(ChatFormatting.GRAY));
		}
		for (Map.Entry<String, String> e : OmnitrixCodes.knownAlienCodes(p).entrySet()) {
			Aliens.Alien a = Aliens.byId(e.getKey());
			Component code = Component.literal(e.getValue()).withStyle(ChatFormatting.GREEN);
			out.add(a != null && Aliens.found(p, a.id())
					? Component.translatable("projecthero.guide.omnitrix.master.found.alien_known", Component.translatable(a.nameKey()), code).withStyle(ChatFormatting.WHITE)
					: Component.translatable("projecthero.guide.omnitrix.master.found.alien_unknown", code).withStyle(ChatFormatting.WHITE));
		}
		return out;
	}

	// ---------------------------------------------------------------- the readout on the face

	/** What the face shows: six cells (glyph index) and their colours (rgb), the backing band's strength, the blinking slot (-1 none). */
	public record Readout(int[] cells, int[] colours, float alpha, int cursor, int cursorColour) {
	}

	/**
	 * The face readout for {@code p} now, or null. In function mode: the digits being typed (the local player's; another player's
	 * watch shows dashes), the current slot blinking, a wrong code's green hints during the lock. During a self-destruct countdown
	 * (function mode or not): the seconds left, red.
	 */
	public static Readout readout(Player p, OmnitrixState s, long now, float partial) {
		float t = now + partial;
		boolean blinkFast = (System.currentTimeMillis() / 125L) % 2L == 0L;
		int sd = OmnitrixFunction.selfDestructLeft(s, now);
		float fold = OmnitrixFunction.fold(s, t);
		float alpha = Mth.clamp((fold - 0.55f) / 0.45f, 0f, 1f);
		if (sd > 0 && alpha <= 0f) {
			int secs = (sd + 19) / 20;
			int col = blinkFast || sd < 100 && (now / 3) % 2 == 0 ? RED : RED_DIM;
			return new Readout(new int[] { DASH, DASH, secs / 10 % 10, secs % 10, DASH, DASH }, fill(col), 1f, -1, 0);
		}
		if (alpha <= 0f) {
			return null;
		}
		int[] cells = new int[OmnitrixCodeEntry.LENGTH];
		int[] cols = new int[OmnitrixCodeEntry.LENGTH];
		boolean mine = p == Minecraft.getInstance().player;
		OmnitrixFunction.Outcome last = OmnitrixFunction.lastOutcome(s);
		float age = OmnitrixFunction.resultAge(s, now, partial);
		boolean locked = OmnitrixFunction.locked(s, now);
		if (locked && last == OmnitrixFunction.Outcome.WRONG) {
			// the wrong code with its Mastermind hints: right-place digits flash green, the rest dull red
			int mask = OmnitrixFunction.hint(s);
			boolean flash = age > 30f || (System.currentTimeMillis() / 160L) % 2L == 0L;
			for (int i = 0; i < cells.length; i++) {
				cells[i] = mine && lastSent.length() == cells.length ? lastSent.charAt(i) - '0' : DASH;
				cols[i] = (mask >> i & 1) != 0 ? (flash ? GREEN_HOT : GREEN) : RED_DIM;
			}
			return new Readout(cells, cols, alpha, -1, 0);
		}
		OmnitrixCodeEntry e = mine ? entry(s) : null;
		boolean red = sd > 0;
		for (int i = 0; i < cells.length; i++) {
			cells[i] = e == null ? DASH : e.digit(i);
			boolean cur = e != null && i == e.slot();
			cols[i] = red ? (cur ? AMBER : RED) : cur ? GREEN_HOT : GREEN;
		}
		if (age < 12f && (last == OmnitrixFunction.Outcome.SELF_DESTRUCT_ARMED || last == OmnitrixFunction.Outcome.SELF_DESTRUCT_CANCELLED)) {
			java.util.Arrays.fill(cols, last == OmnitrixFunction.Outcome.SELF_DESTRUCT_ARMED ? RED : GREEN_HOT);
		}
		boolean blink = (System.currentTimeMillis() / 300L) % 2L == 0L;
		return new Readout(cells, cols, alpha, e != null && blink ? e.slot() : -1, red ? AMBER : GREEN_HOT);
	}

	/** After a code was accepted (the readout fading as the core comes back up): every digit lit. */
	public static Readout acceptedReadout(Player p, OmnitrixState s, long now, float partial) {
		OmnitrixFunction.Outcome last = OmnitrixFunction.lastOutcome(s);
		float age = OmnitrixFunction.resultAge(s, now, partial);
		if (age > OmnitrixConfig.FUNCTION_FOLD_TICKS + 4f || (last != OmnitrixFunction.Outcome.MASTER && last != OmnitrixFunction.Outcome.ALIEN)
				|| OmnitrixFunction.active(s)) {
			return null;
		}
		float a = 1f - Mth.clamp(age / (OmnitrixConfig.FUNCTION_FOLD_TICKS + 4f), 0f, 1f);
		int[] cells = new int[OmnitrixCodeEntry.LENGTH];
		boolean mine = p == Minecraft.getInstance().player;
		for (int i = 0; i < cells.length; i++) {
			cells[i] = mine && lastSent.length() == cells.length ? lastSent.charAt(i) - '0' : DASH;
		}
		return new Readout(cells, fill(GREEN_HOT), a, -1, 0);
	}

	private static int[] fill(int c) {
		int[] o = new int[OmnitrixCodeEntry.LENGTH];
		java.util.Arrays.fill(o, c);
		return o;
	}

	// ---------------------------------------------------------------- the HUD

	/**
	 * The function-mode box (bottom right, where the alien keys sit when transformed): the six slots as a short hotbar (the current
	 * one framed and blinking), the known Master Control digits, the lock or the result, and the controls wrapped under it. Plus the
	 * self-destruct banner at the top of the screen. Returns the box's top ({@code bottom} when nothing was drawn).
	 */
	static int drawHud(GuiGraphics g, Minecraft mc, LocalPlayer p, OmnitrixState s, long now, int x0, int bottom, int width) {
		drawSelfDestruct(g, mc, s, now);
		if (!OmnitrixFunction.active(s)) {
			return bottom;
		}
		var font = mc.font;
		int y = bottom - com.projecthero.mod.client.gui.VanillaHud.HEIGHT;
		com.projecthero.mod.client.gui.VanillaHud.strip(g, x0, y, OmnitrixCodeEntry.LENGTH);
		OmnitrixCodeEntry e = entry(s);
		boolean locked = OmnitrixFunction.locked(s, now);
		boolean wrong = locked && OmnitrixFunction.lastOutcome(s) == OmnitrixFunction.Outcome.WRONG;
		int mask = OmnitrixFunction.hint(s);
		boolean sd = OmnitrixFunction.selfDestructing(s);
		boolean blink = (System.currentTimeMillis() / 300L) % 2L == 0L;
		for (int i = 0; i < OmnitrixCodeEntry.LENGTH; i++) {
			String d;
			int col;
			if (wrong) {
				d = lastSent.length() == OmnitrixCodeEntry.LENGTH ? String.valueOf(lastSent.charAt(i)) : "-";
				col = (mask >> i & 1) != 0 ? 0xFF55FF55 : 0xFFAA4040;
			} else {
				d = String.valueOf(e.digit(i));
				col = sd ? 0xFFFF6040 : i == e.slot() ? 0xFFFFFFFF : 0xFF8FE870;
			}
			boolean cur = !locked && i == e.slot();
			com.projecthero.mod.client.gui.VanillaHud.slot(g, x0, y, i, "", true, false, 0f, 0, 0f, cur && blink);
			int ix = x0 + 3 + i * com.projecthero.mod.client.gui.VanillaHud.SLOT;
			g.drawString(font, d, ix + 8 - font.width(d) / 2, y + 7, col, true);
			if (cur) {
				g.fill(ix + 3, y + 17, ix + 13, y + 18, blink ? 0xFFFFFFFF : 0xFF8FE870);
			}
		}
		// the text over the slots, bottom-up, every line wrapped to the HUD's width: the status (the lock / the known digits), the
		// title, the controls
		List<Component> texts = new ArrayList<>();
		List<Integer> colours = new ArrayList<>();
		texts.add(Component.translatable("hud.projecthero.omnitrix.function.title"));
		colours.add(0xFF55FF55);
		texts.add(Component.translatable("hud.projecthero.omnitrix.function.help"));
		colours.add(0xFFAAAAAA);
		if (locked) {
			Component lock = Component.translatable("hud.projecthero.omnitrix.function.locked", (OmnitrixFunction.lockLeft(s, now) + 19) / 20);
			texts.add(wrong ? Component.translatable("hud.projecthero.omnitrix.function.denied").append("  ").append(lock) : lock);
			colours.add(0xFFFF5555);
		} else {
			texts.add(Component.translatable("hud.projecthero.omnitrix.function.known", OmnitrixCodes.mask(p, "_", " ")));
			colours.add(0xFF8FE870);
		}
		int ty = y - 1;
		for (int k = texts.size() - 1; k >= 0; k--) {
			List<FormattedCharSequence> lines = font.split(texts.get(k), width - 2);
			ty -= lines.size() * 10 + 1;
			int ly = ty;
			for (FormattedCharSequence line : lines) {
				g.drawString(font, line, x0 + 1, ly, colours.get(k), true);
				ly += 10;
			}
		}
		return ty;
	}

	/** The self-destruct countdown: red edges and a banner with the seconds and how to cancel it. */
	private static void drawSelfDestruct(GuiGraphics g, Minecraft mc, OmnitrixState s, long now) {
		int left = OmnitrixFunction.selfDestructLeft(s, now);
		if (left <= 0) {
			return;
		}
		int w = g.guiWidth(), h = g.guiHeight();
		boolean on = (now / (left < 100 ? 3 : 6)) % 2 == 0;
		int edge = on ? 0x70FF1A10 : 0x30FF1A10;
		g.fill(0, 0, w, 5, edge);
		g.fill(0, h - 5, w, h, edge);
		g.fill(0, 0, 5, h, edge);
		g.fill(w - 5, 0, w, h, edge);
		int bw = Math.min(236, w - 16);
		int x = w / 2 - bw / 2, y = 22;
		int secs = (left + 19) / 20;
		List<FormattedCharSequence> title = mc.font.split(Component.translatable("hud.projecthero.omnitrix.self_destruct_code"), bw - 8);
		List<FormattedCharSequence> sub = mc.font.split(Component.translatable("hud.projecthero.omnitrix.self_destruct_left", secs), bw - 8);
		int bh = 4 + title.size() * 10 + sub.size() * 10 + 8;
		g.fill(x, y, x + bw, y + bh, 0xE0180404);
		g.renderOutline(x, y, bw, bh, on ? 0xFFFF3A2E : 0xFF801010);
		int ly = y + 4;
		for (FormattedCharSequence line : title) {
			g.drawCenteredString(mc.font, line, w / 2, ly, on ? 0xFFFF3A2E : 0xFFFFB0A0);
			ly += 10;
		}
		for (FormattedCharSequence line : sub) {
			g.drawCenteredString(mc.font, line, w / 2, ly, 0xFFFFFFFF);
			ly += 10;
		}
		float frac = Mth.clamp(left / (float) OmnitrixConfig.SELF_DESTRUCT_TICKS, 0f, 1f);
		g.fill(x + 6, y + bh - 8, x + bw - 6, y + bh - 4, 0xFF300808);
		g.fill(x + 6, y + bh - 8, x + 6 + (int) ((bw - 12) * frac), y + bh - 4, on ? 0xFFFF3A2E : 0xFFC02018);
	}
}
