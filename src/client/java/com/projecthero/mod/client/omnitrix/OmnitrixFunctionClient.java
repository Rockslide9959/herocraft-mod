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
	/**
	 * v0.17.5 code fix: a code sent and not answered yet -- when (wall clock ms, 0 = none) and the server's result / refusal times
	 * at that moment (an answer changes one of them).
	 */
	private static long pendingSince;
	private static long pendingResultAt = Long.MIN_VALUE, pendingRefuseAt = Long.MIN_VALUE;
	/** "No response from the watch" shows until (wall clock ms). */
	private static long noResponseUntil;
	/** How long a sent code waits for the server before the box says there was no answer. */
	static final long NO_RESPONSE_MS = 1500L;

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
		pendingSince = System.currentTimeMillis();
		pendingResultAt = s.timers.containsKey(OmnitrixFunction.RESULT_AT) ? s.readyAt(OmnitrixFunction.RESULT_AT) : Long.MIN_VALUE;
		pendingRefuseAt = s.timers.containsKey(OmnitrixFunction.REFUSE_AT) ? s.readyAt(OmnitrixFunction.REFUSE_AT) : Long.MIN_VALUE;
		noResponseUntil = 0L;
		ClientPlayNetworking.send(new OmnitrixActionPayload(OmnitrixActionPayload.Action.CODE, code));
		return true;
	}

	/**
	 * Every client tick while a code is out: the server's answer (a new result or refusal in the synced state, or the watch gone)
	 * ends the wait -- an accepted code puts the entry back to 000000 on slot 1 (user: "After putting in a working code the inputted
	 * code should get reset to 000000") -- and no answer within {@link #NO_RESPONSE_MS} says so in the box.
	 */
	private static void watchPending(LocalPlayer p) {
		if (pendingSince == 0L) {
			return;
		}
		if (p == null || !Omnitrix.hasPower(p)) {
			pendingSince = 0L;
			return;
		}
		OmnitrixState s = Omnitrix.state(p);
		long res = s.timers.containsKey(OmnitrixFunction.RESULT_AT) ? s.readyAt(OmnitrixFunction.RESULT_AT) : Long.MIN_VALUE;
		long ref = s.timers.containsKey(OmnitrixFunction.REFUSE_AT) ? s.readyAt(OmnitrixFunction.REFUSE_AT) : Long.MIN_VALUE;
		if (res != pendingResultAt) {
			pendingSince = 0L;
			if (ENTRY.afterResult(OmnitrixFunction.lastOutcome(s))) {
				lastSent = "";
			}
			return;
		}
		if (ref != pendingRefuseAt) {
			pendingSince = 0L;
			return;
		}
		if (System.currentTimeMillis() - pendingSince > NO_RESPONSE_MS) {
			pendingSince = 0L;
			noResponseUntil = System.currentTimeMillis() + 4000L;
			ui(SoundEvents.LEVER_CLICK, 0.5f, 0.3f);
		}
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
		watchPending(p);
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
	 * The face readout for {@code p} now, or null: only during a self-destruct countdown (function mode or not), the seconds left,
	 * red. v0.17.5 round 3 (user: "when entering in codes the code shouldnt show on the omnitrix face, it should only show on the
	 * players ability keys"): the digits being typed, the wrong code's hints and the accepted code are no longer on the face (every
	 * viewer could read them there) -- they are on the wearer's HUD key boxes ({@link #drawHud}). The face stays plain.
	 */
	public static Readout readout(Player p, OmnitrixState s, long now, float partial) {
		// v0.17.5 (user: "for the omnitrix self destruct remove all the indicators and bars besides a bar for self destruct above the
		// ability ui"): the face no longer counts the seconds down -- the one bar over the ability boxes does
		return null;
	}

	private static int[] fill(int c) {
		int[] o = new int[OmnitrixCodeEntry.LENGTH];
		java.util.Arrays.fill(o, c);
		return o;
	}

	// ---------------------------------------------------------------- the HUD

	/**
	 * The function-mode box (bottom right): the six ability key boxes (R G Z X C V, where the alien keys sit when transformed), each
	 * showing one digit of the code with its key small in the corner (the current one framed and blinking, a wrong code's hints in
	 * green, the lock as a bar draining on every key, red boxes in a self-destruct) -- the only place the code is ever shown -- then
	 * the known Master Control digits, the lock or the result, and the controls wrapped over it. Plus the
	 * self-destruct banner at the top of the screen. Returns the box's top ({@code bottom} when nothing was drawn).
	 */
	static int drawHud(GuiGraphics g, Minecraft mc, LocalPlayer p, OmnitrixState s, long now, int x0, int bottom, int width) {
		if (!OmnitrixFunction.active(s)) {
			// function mode closed mid-countdown: just the self-destruct bar where the ability boxes would be
			if (OmnitrixFunction.selfDestructing(s)) {
				int by = bottom - 6;
				drawSelfDestructBar(g, mc, s, now, x0, by);
				return by - 10;
			}
			return bottom;
		}
		var font = mc.font;
		int y = bottom - com.projecthero.mod.client.gui.VanillaHud.HEIGHT;
		com.projecthero.mod.client.gui.VanillaHud.strip(g, x0, y, OmnitrixCodeEntry.LENGTH);
		OmnitrixCodeEntry e = entry(s);
		boolean locked = OmnitrixFunction.locked(s, now);
		boolean wrong = locked && OmnitrixFunction.lastOutcome(s) == OmnitrixFunction.Outcome.WRONG;
		int mask = OmnitrixFunction.hint(s);
		// v0.17.5: the countdown no longer recolours the boxes -- only the bar over them shows it
		boolean sd = false;
		boolean blink = (System.currentTimeMillis() / 300L) % 2L == 0L;
		// the hints flash for a second and a half, then stay green
		boolean hintFlash = OmnitrixFunction.resultAge(s, now, 0f) <= 30f && (System.currentTimeMillis() / 160L) % 2L == 0L;
		OmnitrixFunction.Outcome lastOut = OmnitrixFunction.lastOutcome(s);
		float resultAge = OmnitrixFunction.resultAge(s, now, 0f);
		boolean sdArmedFlash = false;
		boolean sdCancelFlash = false;
		// the lock as a durability bar on every key, red when it has just started, green as it runs out
		float lockFrac = locked ? Mth.clamp(OmnitrixFunction.lockLeft(s, now) / (float) OmnitrixConfig.CODE_LOCK_TICKS, 0f, 1f) : 0f;
		boolean sdOn = sd && (now / (OmnitrixFunction.selfDestructLeft(s, now) < 100 ? 3 : 6)) % 2 == 0;
		// v0.17.5 round 3 (user: "the code shouldnt show on the omnitrix face, it should only show on the players ability keys"): the
		// six digits sit on the six ability key boxes, R G Z X C V = slot 1 .. 6, each key's letter shrunk into its corner
		for (int i = 0; i < OmnitrixCodeEntry.LENGTH; i++) {
			String d;
			int col;
			if (wrong) {
				// the wrong code, all one colour while the lock lasts (no hints)
				d = lastSent.length() == OmnitrixCodeEntry.LENGTH ? String.valueOf(lastSent.charAt(i)) : "-";
				col = 0xFFAA4040; // v0.17.5: every digit the same dull red -- no per-digit right/wrong hint
			} else {
				d = String.valueOf(e.digit(i));
				col = sd ? (i == e.slot() ? 0xFFFFB020 : 0xFFFF6040) : i == e.slot() ? 0xFFFFFFFF : 0xFF8FE870;
				if (locked) {
					col = 0xFF6A6A6A;
				}
			}
			if (sdArmedFlash) {
				col = 0xFFFF3A26;
			} else if (sdCancelFlash) {
				col = 0xFFE2FFD2;
			}
			boolean cur = !locked && i == e.slot();
			com.projecthero.mod.client.gui.VanillaHud.slot(g, x0, y, i, "", true, false, 0f, 0, lockFrac, cur && blink);
			int ix = x0 + 3 + i * com.projecthero.mod.client.gui.VanillaHud.SLOT, iy = y + 3;
			if (sd) {
				g.fill(ix, iy, ix + 16, iy + 16, sdOn ? 0x60FF2010 : 0x30FF2010);
			}
			// the key, small in the top-left corner
			String key = com.projecthero.mod.client.gui.VanillaHud.keyLabel(com.projecthero.mod.client.gui.VanillaHud.ORDER[i]);
			g.pose().pushPose();
			g.pose().translate(ix + 1f, iy + 1f, 0f);
			g.pose().scale(0.5f, 0.5f, 1f);
			g.drawString(font, key, 0, 0, 0xFF9A9A9A, true);
			g.pose().popPose();
			// the digit, a size up, in the middle
			g.pose().pushPose();
			g.pose().translate(ix + 8.5f - font.width(d) * 0.625f, iy + 2.5f, 0f);
			g.pose().scale(1.25f, 1.25f, 1f);
			g.drawString(font, d, 0, 0, col, true);
			g.pose().popPose();
			if (cur) {
				g.fill(ix + 3, iy + 14, ix + 13, iy + 15, blink ? 0xFFFFFFFF : 0xFF8FE870);
			}
		}
		// the text over the slots, bottom-up, every line wrapped to the HUD's width: the status (the lock / the known digits), the
		// title, the controls
		List<Component> texts = new ArrayList<>();
		List<Integer> colours = new ArrayList<>();
		texts.add(Component.translatable(wrong ? "hud.projecthero.omnitrix.function.denied" : "hud.projecthero.omnitrix.function.title"));
		colours.add(wrong ? 0xFFFF5555 : 0xFF55FF55);
		texts.add(Component.translatable("hud.projecthero.omnitrix.function.help"));
		colours.add(0xFFAAAAAA);
		OmnitrixFunction.Outcome last = OmnitrixFunction.lastOutcome(s);
		// v0.17.5 code fix: never silent -- a refused code says why, and a code nobody answered says that
		OmnitrixFunction.Refusal refused = OmnitrixFunction.lastRefusal(s);
		boolean refusedNow = refused != OmnitrixFunction.Refusal.NONE && refused != OmnitrixFunction.Refusal.LOCKED
				&& s.readyAt(OmnitrixFunction.REFUSE_AT) >= s.readyAt(OmnitrixFunction.FUNC) && now - s.readyAt(OmnitrixFunction.REFUSE_AT) < 80L
				&& s.readyAt(OmnitrixFunction.REFUSE_AT) >= s.readyAt(OmnitrixFunction.RESULT_AT);
		if (System.currentTimeMillis() < noResponseUntil) {
			texts.add(Component.translatable("hud.projecthero.omnitrix.function.no_response"));
			colours.add(0xFFFF5555);
		} else if (refusedNow) {
			texts.add(Component.translatable(refused.langKey()));
			colours.add(0xFFFF5555);
		}
		if (locked) {
			texts.add(Component.translatable("hud.projecthero.omnitrix.function.locked", (OmnitrixFunction.lockLeft(s, now) + 19) / 20));
			colours.add(0xFFFF5555);
		} else {
			texts.add(Component.translatable("hud.projecthero.omnitrix.function.known", OmnitrixCodes.mask(p, "_", " ")));
			colours.add(0xFF8FE870);
		}
		if (Omnitrix.masterControl(p)) {
			// v0.17.5 round 2 (user: "When I entered the master control code nothing happened" -- it was already on, so the code
			// turned it off): say so before he types
			texts.add(Component.translatable("hud.projecthero.omnitrix.function.mc_on"));
			colours.add(0xFFFFD24A);
		}
		int ty = y - 1;
		if (OmnitrixFunction.selfDestructing(s)) {
			// the one self-destruct indicator: a bar right over the ability boxes, the text stacks above it
			int by = y - 6;
			drawSelfDestructBar(g, mc, s, now, x0, by);
			ty = by - 10;
		}
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

	/**
	 * v0.17.5 (user: "for the omnitrix self destruct remove all the indicators and bars besides a bar for self destruct above the
	 * ability ui"): the whole countdown UI is one standard meter row -- "Self-destruct", a red bar draining, the seconds -- with
	 * {@code by} its bar's top. No red screen edges, no banner, no red boxes, no countdown on the face.
	 */
	private static void drawSelfDestructBar(GuiGraphics g, Minecraft mc, OmnitrixState s, long now, int x0, int by) {
		int left = OmnitrixFunction.selfDestructLeft(s, now);
		if (left <= 0) {
			return;
		}
		float frac = Mth.clamp(left / (float) OmnitrixConfig.SELF_DESTRUCT_TICKS, 0f, 1f);
		int secs = (left + 19) / 20;
		OmnitrixHud.meter(g, mc, x0, by, Component.translatable("hud.projecthero.omnitrix.self_destruct_bar"), frac, 0xE0281E,
				secs + " s", 0xFFFF6A5A);
	}
}
