package com.projecthero.mod.client.miraculous;

import java.util.List;

import com.projecthero.mod.client.gui.MoveListOverlay;
import com.projecthero.mod.client.gui.VanillaHud;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.miraculous.Kwami;
import com.projecthero.mod.miraculous.KwamiKit;
import com.projecthero.mod.miraculous.Miraculous;
import com.projecthero.mod.miraculous.MiraculousConfig;
import com.projecthero.mod.miraculous.MiraculousKits;
import com.projecthero.mod.miraculous.MiraculousPhrases;
import com.projecthero.mod.miraculous.data.MiraculousState;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * v0.16.6: the Miraculous HUD (bottom-right). v0.17.3 (user: "I really like the Minecraft-style HUD and ability keybinds ...
 * apply this to all the powers"): drawn from the game's own pieces on the shared column ({@link VanillaHud.Stack}):
 * <pre>
 *   Tikki is hungry -- feed her cookies        (when hungry / detransformed: wrapped to the column's width)
 *   Ladybug                                    the hero (grey while detransformed)
 *   Multitude [boss bar ======---]  3/5        a kit's own rows ({@link Panel#rows}: Multimouse, Viperion)
 *   o o o o o                                  the jewel's five spots in the hero's colour, one goes out every 36 s, the
 *                                              last one blinking (v0.17.4, user: the five dots are back, no Spots bar)
 *   +--+--+--+--+--+--+                        R G Z X C V: the shown move's cooldown (the Shift move's while Shift is held,
 *   |R |G |Z |X |C |V |                        yellow keys), the other move's as the durability bar; the selection frame
 *   +--+--+--+--+--+--+                        while that key's move runs; grey keys while detransformed
 * </pre>
 * Shown whenever the power is held. Hold Left-Alt for the move names ({@code projecthero.miraculous.ability.<id>}). Also draws
 * the short full-screen flash the holder sees at the transformation's flash and on detransforming, and the Shift+N take-off bar.
 */
public final class MiraculousHud {
	/** Boxes left to right: R G Z X C V (slots 1, 2, 4, 3, 6, 5). */
	private static final AbilitySlot[] SLOTS = VanillaHud.ORDER;

	/** A jewel spot that has gone out. */
	private static final int SPOT_OFF = 0xFF2A2A2A;

	/** Set only by the client screenshot harness: the HUD draws as if the player were sneaking. */
	public static volatile boolean forceShift;

	private MiraculousHud() {
	}

	/**
	 * v0.16.7 (user): a kit's own panel (Viperion's Second Chance), stacked right above the HUD's title -- the same width as
	 * the HUD column, so it reads as part of the ability HUD. Height 0 = nothing to show.
	 */
	public interface Panel {
		int height(net.minecraft.client.player.LocalPlayer player);

		void draw(GuiGraphics g, Minecraft mc, int x, int y, int width, float partial);

		/**
		 * v0.17.3: the panel as rows on the vanilla-style column (meter rows, tags) -- returns true when it drew itself this way
		 * (then {@link #height} / {@link #draw} are not used). The rows sit between the jewel's spots and the hero's name.
		 */
		default boolean rows(VanillaHud.Stack stack, net.minecraft.client.player.LocalPlayer player, float partial) {
			return false;
		}
	}

	private static final List<Panel> PANELS = new java.util.concurrent.CopyOnWriteArrayList<>();

	public static void addPanel(Panel panel) {
		PANELS.add(panel);
	}

	/** v0.16.7 (user): 0..1 while Shift+N is held to take the Miraculous off (set by the client key handler). */
	public static volatile float takeOffProgress;

	/** The take-off bar: centred above the hotbar, the label over it, a boss bar filling in the Kwami's colour. */
	private static void takeOffBar(GuiGraphics g, Minecraft mc, Kwami k, float progress) {
		int w = 100;
		int x = (g.guiWidth() - w) / 2;
		int y = g.guiHeight() - 62;
		Component label = Component.translatable("message.projecthero.miraculous.take_off_bar");
		g.drawString(mc.font, label, (g.guiWidth() - mc.font.width(label)) / 2, y - 11, VanillaHud.WHITE, true);
		VanillaHud.bar(g, x, y, w, Math.min(1f, progress), k.color);
	}

	public static void render(GuiGraphics g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null || mc.options.hideGui) {
			return;
		}
		MiraculousState s = Miraculous.peek(mc.player);
		Kwami k = Miraculous.kwami(mc.player);
		if (s == null || k == null) {
			return;
		}
		float partial = delta.getGameTimeDeltaPartialTick(false);
		if (takeOffProgress > 0f) {
			takeOffBar(g, mc, k, takeOffProgress);
		}
		flash(g, mc, s, k, partial);
		var exp = mc.player.getAttachedOrElse(com.projecthero.mod.attachment.ModAttachments.EXPERIMENTAL_STATE, null);
		if (exp != null && !exp.activePower.isEmpty()) {
			return;
		}
		long now = mc.level.getGameTime();
		boolean on = s.transformed;
		boolean shift = forceShift || mc.player.isShiftKeyDown();
		KwamiKit kit = MiraculousKits.of(k);
		int accent = k.color;
		VanillaHud.Stack stack = VanillaHud.Stack.bottomRight(g, SLOTS.length);

		// the six keys
		stack.keys(SLOTS.length);
		for (int i = 0; i < SLOTS.length; i++) {
			AbilitySlot slot = SLOTS[i];
			String[] ids = kit.idsOf(slot);
			if (ids.length == 0) {
				continue;
			}
			boolean showShift = shift && ids.length > 1;
			String shown = ids[showShift ? 1 : 0];
			String other = ids.length > 1 ? ids[showShift ? 0 : 1] : null;
			int cd = Miraculous.cooldownRemaining(mc.player, shown);
			float frac = Math.min(1f, cd / (float) Math.max(1, kit.maxCooldown(shown)));
			float otherFrac = other == null ? 0f
					: Math.min(1f, Miraculous.cooldownRemaining(mc.player, other) / (float) Math.max(1, kit.maxCooldown(other)));
			boolean active = on && kit.active(mc.player, slot);
			stack.slot(i, VanillaHud.keyLabel(slot), on, showShift, frac, VanillaHud.seconds(cd), otherFrac, active);
		}

		// the jewel's five spots (v0.17.4, user: "remove the dots bar, keep the 5 dots like how it was before"): five small
		// dots in the hero's colour above the keys, one gone per 36 s of the countdown, the last one blinking; faded while
		// detransformed -- drawn exactly as before v0.17.3's Spots bar
		int left = Miraculous.countdownLeft(mc.player);
		int spots = left > 0 ? (left + MiraculousConfig.COUNTDOWN_SPOT_TICKS - 1) / MiraculousConfig.COUNTDOWN_SPOT_TICKS : 5;
		boolean blink = left > 0 && (left <= 200 ? (now / 4) % 2 == 0 : (left % MiraculousConfig.COUNTDOWN_SPOT_TICKS) < 100 && (now / 6) % 2 == 0);
		stack.reserve(7);
		int sy = stack.top();
		for (int i = 0; i < 5; i++) {
			int sx = stack.x0 + i * 7;
			int c = i < spots ? (accent | 0xFF000000) : SPOT_OFF;
			if (i == spots - 1 && blink) {
				c = SPOT_OFF;
			}
			if (!on && c != SPOT_OFF) {
				c = (c & 0x00FFFFFF) | 0x70000000;
			}
			g.fill(sx + 1, sy, sx + 4, sy + 5, c);
			g.fill(sx, sy + 1, sx + 5, sy + 4, c);
		}

		// v0.16.7 (user): a kit's own panel (Viperion's Second Chance, Multimouse's Multitude)
		for (Panel panel : PANELS) {
			if (panel.rows(stack, mc.player, partial)) {
				continue;
			}
			int h = panel.height(mc.player);
			if (h > 0) {
				stack.reserve(h + 4);
				panel.draw(g, mc, stack.x0, stack.top(), stack.width, partial);
			}
		}

		// the hero (v0.16.6, user: no mm:ss -- the spots show the countdown)
		stack.title(Component.translatable("projecthero.hero_tier." + k.heroKey), on ? VanillaHud.WHITE : VanillaHud.GREY);

		// hungry / the phrase to say (wrapped to the column's width)
		Component note = null;
		int noteColour = VanillaHud.GREY;
		int eatLeft = Miraculous.eatingSecondsLeft(mc.player);
		if (eatLeft > 0) {
			note = Component.translatable("hud.projecthero.miraculous.eating", Component.translatable("entity.projecthero.kwami." + k.id)); // v0.16.7 (user): no countdown
			noteColour = VanillaHud.GREEN;
		} else if (!on && s.kwamiHidden) {
			note = Component.translatable("hud.projecthero.miraculous.hidden", Component.translatable("entity.projecthero.kwami." + k.id));
		} else if (s.hungry) {
			note = Component.translatable("hud.projecthero.miraculous.hungry." + k.id);
			noteColour = VanillaHud.GOLD;
		} else if (!on) {
			note = Component.translatable("hud.projecthero.miraculous.say", MiraculousPhrases.transformPhrase(k));
		}
		if (note != null) {
			stack.wrapped(note, noteColour);
		}

		// v0.16.6: Left-Alt -> the shared move list, stacked above the whole HUD
		if (MoveListOverlay.held()) {
			List<MoveListOverlay.Row> rows = new java.util.ArrayList<>();
			for (AbilitySlot slot : SLOTS) {
				String[] ids = kit.idsOf(slot);
				if (ids.length == 0) {
					continue;
				}
				rows.add(MoveListOverlay.Row.of(MoveListOverlay.key(slot), Component.translatable("projecthero.miraculous.ability." + ids[0]),
						ids.length > 1 ? Component.translatable("projecthero.miraculous.ability." + ids[1]) : null));
			}
			stack.moveList(rows);
		}
	}

	/** The holder's own screen flash: the hero's colour at the transformation's flash, and on detransforming. */
	private static void flash(GuiGraphics g, Minecraft mc, MiraculousState s, Kwami k, float partial) {
		float age = (float) (mc.level.getGameTime() - s.formChangeAt) + partial;
		float a;
		if (s.transformed && s.animId == MiraculousState.ANIM_TRANSFORM) {
			a = 1f - Math.abs(age - MiraculousConfig.FLASH_TICK - 1f) / 4f;
		} else if (!s.transformed && s.animId == MiraculousState.ANIM_DETRANSFORM) {
			a = 1f - age / 6f;
		} else {
			return;
		}
		if (a <= 0f || s.formChangeAt <= 0L) {
			return;
		}
		int alpha = (int) (Math.min(1f, a) * 150f);
		int rgb = (k.color & 0xFFFFFF);
		// mostly white light, tinted with the hero's colour
		int r = 255 - ((255 - ((rgb >> 16) & 0xFF)) / 3);
		int gg = 255 - ((255 - ((rgb >> 8) & 0xFF)) / 3);
		int b = 255 - ((255 - (rgb & 0xFF)) / 3);
		g.fill(0, 0, g.guiWidth(), g.guiHeight(), (alpha << 24) | (r << 16) | (gg << 8) | b);
	}
}
