package com.projecthero.mod.client.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.visual.MutationMeters;
import com.projecthero.mod.moonknight.MoonKnight;
import com.projecthero.mod.moonknight.MoonKnightAlter;
import com.projecthero.mod.moonknight.MoonKnightConfig;
import com.projecthero.mod.moonknight.MoonKnightLunar;
import com.projecthero.mod.moonknight.data.MoonKnightAction;
import com.projecthero.mod.moonknight.data.MoonKnightState;

import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

/**
 * Moon Knight's HUD, shown only while transformed. v0.17.3: on the vanilla pieces ({@link VanillaHud.Stack}), bottom-right like
 * the newest powers:
 * <pre>
 *   GLIDE / BLOCK                                   status tag
 *   Marc Spector                                    title: the alter (or "... -- fractured 12s")
 *   (moon) x1.5 Full Moon            (crescent)     the lunar state + its multiplier, Khonshu's Resurrection
 *   Vengeance [Hairline ========--]  62%
 *   +--+--+--+--+--+--+                             R G Z X C V: cooldowns; a charging HOLD (the dart fan, the Eye of
 *                                                   Khonshu) fills its key from the bottom
 * </pre>
 * v0.14.4: the icon is the lunar state -- the sun by day (and always in the Nether / End), tonight's moon on an ordinary night,
 * a haloed full moon under the full moon -- with its multiplier (x0.7 / x1.0 / x1.5) and the state's name. Under the crosshair
 * it names the Grapple Kick's current lock-on target. The crescent is Khonshu's Resurrection (bright = charged, dark = spent).
 * Every key is labelled with the key actually bound to it. Hold Left Alt for the ability names.
 */
public final class MoonKnightHud {
	private static final int COLOR_LABEL = 0xFFE8E4D8;
	private static final int COLOR_VENGEANCE = 0xFFF2EEE0;
	private static final int COLOR_VENGEANCE_LOW = 0xFFB04040;
	private static final ResourceLocation MOON_PHASES = ResourceLocation.withDefaultNamespace("textures/environment/moon_phases.png");
	private static final ResourceLocation SUN = ResourceLocation.withDefaultNamespace("textures/environment/sun.png");
	private static final int LUNAR_ROW = 14;

	// v0.13.21 keys: R Darts, G Grapple Kick, Z Khonshu, X Dash, C Truncheon, V Alters -- indexed like VanillaHud.ORDER
	private static final String[] IDS = { "darts", "kick", "khonshu", "dash", "truncheon", "alter" };

	/** v0.16.6: the move list's names per key (the guide's key table): the tap move and the Sneak move. */
	private static final String[] TAP_MOVES = { "darts", "dive_kick", "moonbeam", "dash", "truncheon", "alter" };
	private static final String[] SNEAK_MOVES = { "moon_mark", "shadow_step", "judgement", "grapple", "slam", "alter_special" };

	/**
	 * v0.17.3: the longest cooldown seen on each key since it last came back (the server sends only the ticks left), so the
	 * white sweep can drain like an item cooldown. Plain ints, nothing world-bound.
	 */
	private static final int[] PEAK = new int[6];

	private MoonKnightHud() {
	}

	public static void render(GuiGraphics g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		Player player = mc.player;
		if (player == null || mc.options.hideGui || mc.level == null) {
			return;
		}
		MoonKnightState s = MoonKnight.peek(player);
		if (s == null || !s.hasPact || !s.transformed) {
			return;
		}
		MoonKnightAction action = com.projecthero.mod.moonknight.MoonKnightAnim.action(player);
		long now = mc.level.getGameTime();
		drawKickTarget(g, mc, player);

		// ---- the six keys
		VanillaHud.Stack st = VanillaHud.Stack.bottomRight(g, 6).keys(6);
		for (int i = 0; i < VanillaHud.ORDER.length; i++) {
			AbilitySlot slot = VanillaHud.ORDER[i];
			int cd = cooldown(player, IDS[i]);
			if (cd <= 0) {
				PEAK[i] = 0;
			} else if (cd > PEAK[i]) {
				PEAK[i] = cd;
			}
			st.slot(i, VanillaHud.keyLabel(slot), true, false, cd > 0 ? Math.min(1f, cd / (float) Math.max(1, PEAK[i])) : 0f,
					VanillaHud.seconds(cd), 0f, false);
			// a charging HOLD (the dart fan, the Eye of Khonshu) fills its key from the bottom
			if (action.has(MoonKnightAction.FLAG_CHARGING) && action.chargeKey == slot.number()) {
				int max = slot == AbilitySlot.SLOT_4 ? MoonKnightConfig.EYE_HOLD_TICKS : MoonKnightConfig.DART_FAN_MAX_CHARGE;
				st.hold(i, Math.min(1.0f, (now - action.chargeStart) / (float) Math.max(1, max)));
			}
		}

		// ---- vengeance (Hairline)
		float frac = Math.max(0.0f, Math.min(1.0f, s.vengeance / MoonKnightConfig.VENGEANCE_MAX));
		boolean low = frac < 0.15f;
		st.meter(Component.translatable("hud.projecthero.moon_knight.vengeance_label"), frac, low ? COLOR_VENGEANCE_LOW : COLOR_VENGEANCE,
				Math.round(frac * 100.0f) + "%", low ? COLOR_VENGEANCE_LOW : COLOR_LABEL, MutationMeters.Style.HAIRLINE);

		// ---- the lunar row (v0.14.4: DAY sun / NIGHT tonight's moon / FULL MOON) + multiplier, resurrection crescent
		st.reserve(LUNAR_ROW);
		int x0 = st.x0;
		int ry = st.top() + 1;
		MoonKnightLunar.State lunar = MoonKnightLunar.state(mc.level);
		switch (lunar) {
			case DAY -> {
				g.setColor(0.8f, 0.78f, 0.62f, 1.0f);
				g.blit(SUN, x0 - 2, ry - 2, 16, 16, 0, 0, 32, 32, 32, 32);
			}
			case NIGHT -> {
				int phase = mc.level.getMoonPhase();
				g.setColor(0.78f, 0.8f, 0.86f, 1.0f);
				g.blit(MOON_PHASES, x0, ry, 12, 12, (phase % 4) * 32, (phase / 4) * 32, 32, 32, 128, 64);
			}
			case FULL_MOON -> {
				// the full moon, with a faint halo so it reads as the strongest state at a glance
				g.fill(x0 - 1, ry - 1, x0 + 13, ry + 13, 0x30E8F0FF);
				g.blit(MOON_PHASES, x0, ry, 12, 12, 0, 0, 32, 32, 128, 64);
			}
		}
		g.setColor(1.0f, 1.0f, 1.0f, 1.0f);
		int powerColor = switch (lunar) {
			case FULL_MOON -> 0xFFFFFFFF;
			case NIGHT -> 0xFFD8D4C8;
			case DAY -> 0xFF9A968C;
		};
		String mult = String.format(Locale.ROOT, "x%.1f", lunar.power());
		int tx = x0 + 15;
		g.drawString(mc.font, mult, tx, ry + 2, powerColor, true);
		tx += mc.font.width(mult) + 4;
		int nameRoom = x0 + st.width - 12 - tx; // clear of the crescent
		if (nameRoom > 10) {
			String name = Component.translatable(lunar.nameKey()).getString();
			g.drawString(mc.font, mc.font.plainSubstrByWidth(name, nameRoom), tx, ry + 2, VanillaHud.GREY, true);
		}
		drawCrescent(g, x0 + st.width - 9, ry + 1, s.resurrectionCharged);

		// ---- the alter (title)
		MoonKnightAlter alter = MoonKnightAlter.byOrdinal(s.alter);
		Component alterName = Component.translatable(alter.nameKey()).withStyle(alter.colour());
		if (s.fractureUntil > now) {
			alterName = Component.translatable("hud.projecthero.moon_knight.fractured", alterName,
					(int) Math.ceil((s.fractureUntil - now) / 20.0)).withStyle(ChatFormatting.RED);
		}
		st.wrapped(alterName, COLOR_LABEL);

		// ---- glide / cape block
		List<VanillaHud.Tag> tags = new ArrayList<>();
		if (action.has(MoonKnightAction.FLAG_GLIDING)) {
			tags.add(new VanillaHud.Tag(Component.translatable("hud.projecthero.moon_knight.glide").getString(), 0xFF8FE0FF));
		} else if (action.has(MoonKnightAction.FLAG_CAPE_BLOCK)) {
			// v0.13.21: the Cape Block (hold right click)
			tags.add(new VanillaHud.Tag(Component.translatable("hud.projecthero.moon_knight.cape_block").getString(), VanillaHud.WHITE));
		}
		st.tags(tags);

		// v0.16.6: Left-Alt -> the shared move list (each key: tap move + Sneak move)
		List<MoveListOverlay.Row> rows = new ArrayList<>();
		for (int i = 0; i < 6; i++) {
			rows.add(MoveListOverlay.Row.of(MoveListOverlay.key(VanillaHud.ORDER[i]),
					Component.translatable("projecthero.moon_knight.move." + TAP_MOVES[i]),
					Component.translatable("projecthero.moon_knight.move." + SNEAK_MOVES[i])));
		}
		st.moveList(rows);
	}

	/**
	 * v0.14.4: under the crosshair, who the Grapple Kick would hit right now ({@code MoonKnightKickPreviewClient}) --
	 * "[G] Zombie 23m", white while it is ready, grey while it recharges.
	 */
	private static void drawKickTarget(GuiGraphics g, Minecraft mc, Player player) {
		net.minecraft.world.entity.LivingEntity t = com.projecthero.mod.client.moonknight.MoonKnightKickPreviewClient.target();
		if (t == null || mc.screen != null) {
			return;
		}
		boolean ready = MoonKnight.cooldownRemaining(player, "kick") <= 0;
		Component text = Component.translatable("hud.projecthero.moon_knight.kick_target", keyLabel(AbilitySlot.SLOT_2),
				t.getDisplayName(), Math.round(player.distanceTo(t)));
		int w = mc.font.width(text);
		g.drawString(mc.font, text, g.guiWidth() / 2 - w / 2, g.guiHeight() / 2 + 10, ready ? 0xE0F2F0E8 : 0xA0909090, true);
	}

	/** The longest cooldown among a key's tap / hold / sneak variants ({@code <id>}, {@code <id>_hold}, {@code <id>_sneak}). */
	private static int cooldown(Player player, String id) {
		return Math.max(MoonKnight.cooldownRemaining(player, id),
				Math.max(MoonKnight.cooldownRemaining(player, id + "_hold"), MoonKnight.cooldownRemaining(player, id + "_sneak")));
	}

	private static String keyLabel(AbilitySlot slot) {
		return VanillaHud.keyLabel(slot).toUpperCase(Locale.ROOT);
	}

	/** Khonshu's Resurrection: a small crescent, bright white when charged, dark when spent. */
	private static void drawCrescent(GuiGraphics g, int x, int y, boolean charged) {
		int c = charged ? 0xFFF8F6EE : 0xFF3C3A36;
		// a 9x9 disc with a 5 px bite taken out of its right side
		for (int dy = 0; dy < 9; dy++) {
			for (int dx = 0; dx < 9; dx++) {
				double ox = dx - 4.0;
				double oy = dy - 4.0;
				boolean inDisc = ox * ox + oy * oy <= 16.5;
				double bx = dx - 6.2;
				boolean inBite = bx * bx + oy * oy <= 11.0;
				if (inDisc && !inBite) {
					g.fill(x + dx, y + dy, x + dx + 1, y + dy + 1, c);
				}
			}
		}
	}
}
