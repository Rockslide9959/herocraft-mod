package com.projecthero.mod.client.gui;

import java.util.ArrayList;
import java.util.List;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.visual.MutationMeters;
import com.projecthero.mod.symbiote.SymbioteAgentVenomAbilities;
import com.projecthero.mod.symbiote.SymbioteHunger;
import com.projecthero.mod.symbiote.SymbioteHostType;
import com.projecthero.mod.symbiote.SymbioteState;
import com.projecthero.mod.symbiote.SymbioteVitals;
import com.projecthero.mod.symbiote.SymbioteVitalsManager;

import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/**
 * The Symbiote HUD: drawn only while the black suit is actually worn ({@link SymbioteState#active}). v0.17.3 (user: "I really
 * like the Minecraft-style HUD and ability keybinds ... apply this to all the powers"): on the shared Minecraft-style look.
 *
 * <p>Which block it draws depends on {@link SymbioteHostType#of}:
 * <ul>
 *   <li><b>Normal host</b> -- the bottom-right column ({@link VanillaHud.Stack}): the six {@code SymbioteAbilityManager} keys
 *       (R G Z X C V, cooldowns read straight off the synced {@link SymbioteState#abilityCooldowns}; Shift+G's Spike Fan on
 *       G's durability bar; Z fills while Onslaught charges; the blade / shield / thorns / grab light their key), the Biomass
 *       bar (the user's Hairline style, with the recovery tick while it is spent), the title.</li>
 *   <li><b>Black Suit Spider-Man</b> -- the Hunger bar (Hairline, Bonded / Overdrive ticks) and its tier / revive / Take Over
 *       words ride on {@link SpiderHud}'s column ({@link #blackSuitRows}).</li>
 *   <li><b>Agent Venom</b> -- a small block stacked above the Punisher's HUD: the title, the revive bar (Hairline), and a
 *       three-key strip for the Sneak extras (yellow keys: they are Shift moves), the V key framed while Symbiote Unleashed
 *       runs.</li>
 * </ul>
 */
public final class SymbioteHud {
	/** Black Suit Spider-Man's accent (the Symbiote's purple) and Agent Venom's (the suit's bone white). */
	private static final int ACCENT_BLACK_SUIT = 0xFF9A6CF0;
	private static final int ACCENT_AGENT_VENOM = 0xFFE4E6F0;
	private static final int COLOR_LABEL = 0xFFB090F0;
	private static final int COLOR_PURPLE = 0xFF8A5FE0;

	/** Slot ordinal (R G X Z V C) -> the Normal host ability id on that key (matches {@code AbilitySlot}). */
	private static final String[] NORMAL_SLOT_ABILITIES = {
			"tendril_strike", "spike_shot", "leap", "barrage", "blade", "spikes"
	};
	/** v0.16.6: the Shift move on each key (SymbioteAbilityManager.handle's sneak branches), for the move list. */
	private static final String[] NORMAL_SHIFT_ABILITIES = {
			"tendril_sweep", "spike_fan", "grapple", "onslaught", "shield", "tendril_grab"
	};
	private static final int[] AGENT_VENOM_COOLDOWNS = {
			SymbioteAgentVenomAbilities.CD_SWING, SymbioteAgentVenomAbilities.CD_SNATCH,
			SymbioteAgentVenomAbilities.CD_UNLEASHED
	};
	/** Shift+G's Spike Fan cooldown is drawn against this (10 s). */
	private static final int SPIKE_FAN_DISPLAY = 200;

	/** Set only by a client screenshot harness: the HUD draws as if the player were sneaking. */
	public static volatile boolean forceShift;

	/** SpiderHud drew the Black Suit rows on its own column this frame (it renders before this HUD). */
	private static boolean blackSuitDrawn;
	/** The top of the Punisher's HUD this frame, when PunisherHud reports it ({@link #anchorAgentVenom}), else MIN_VALUE. */
	private static int punisherTop = Integer.MIN_VALUE;

	private SymbioteHud() {
	}

	/**
	 * v0.17.3: the Punisher's HUD reports the top of its column each frame (PunisherHud renders before this HUD), so the
	 * Agent Venom block stacks right above it. Without a report it sits where the old Punisher row left room.
	 */
	public static void anchorAgentVenom(int top) {
		punisherTop = top;
	}

	public static void render(GuiGraphics g, DeltaTracker delta) {
		boolean spiderDrew = blackSuitDrawn;
		blackSuitDrawn = false;
		int anchor = punisherTop;
		punisherTop = Integer.MIN_VALUE;
		lastAnchor = anchor;
		Minecraft mc = Minecraft.getInstance();
		Player player = mc.player;
		if (player == null || mc.options.hideGui) {
			return;
		}
		SymbioteState s = player.getAttachedOrElse(ModAttachments.SYMBIOTE_STATE, null);
		if (s == null || !s.hasSymbiote) {
			return;
		}

		// A fresh wild bond that has not settled yet: show the takeover progress (centre screen), nothing else.
		SymbioteVitals vitals = player.getAttachedOrElse(ModAttachments.SYMBIOTE_VITALS, new SymbioteVitals());
		long gameNow = mc.level != null ? mc.level.getGameTime() : 0L;
		if (vitals.bondingUntil > gameNow) {
			float frac = 1.0f - Math.min(1.0f,
					(vitals.bondingUntil - gameNow) / (float) SymbioteVitalsManager.BONDING_TICKS);
			int w = 150;
			int x = g.guiWidth() / 2 - w / 2;
			int y = g.guiHeight() / 2 + 30;
			g.drawCenteredString(mc.font, Component.translatable("hud.projecthero.symbiote.bonding")
					.withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC), g.guiWidth() / 2, y - 12, 0xFFB090F0);
			g.fill(x - 1, y - 1, x + w + 1, y + 5, 0xFF2A2A32);
			g.fill(x, y, x + w, y + 4, 0xAA100018);
			g.fill(x, y, x + Math.round(w * frac), y + 4, COLOR_PURPLE);
			return;
		}

		SymbioteHostType type = SymbioteHostType.of(player);
		if (type != SymbioteHostType.NORMAL && !s.active) {
			return;
		}
		long now = mc.level != null ? mc.level.getGameTime() : 0L;

		if (type == SymbioteHostType.AGENT_VENOM) {
			renderAgentVenom(g, mc, player, vitals, now, anchor);
		} else if (type == SymbioteHostType.SPIDER_MAN) {
			if (!spiderDrew) {
				// Spider-Man's column is not on screen (a mutation owns the keys, Mjolnir in hand, ...): the rows stand alone
				VanillaHud.Stack st = VanillaHud.Stack.bottomRight(g, 6);
				List<VanillaHud.Tag> tags = new ArrayList<>();
				blackSuitRows(st, player, now, tags);
				blackSuitDrawn = false; // only SpiderHud's call counts as "drawn on its column"
				st.title(Component.translatable("hud.projecthero.symbiote.black_suit"), ACCENT_BLACK_SUIT);
				st.tags(tags);
			}
		} else {
			renderNormalHost(g, player, s, vitals, now);
		}
	}

	/**
	 * v0.13.19 Normal host, v0.17.3 on the Minecraft-style column. The blade and shield have no time limit (they feed on
	 * Biomass); while the bar is spent a white tick marks the {@link SymbioteVitalsManager#RECOVER_FRACTION} it has to climb
	 * back to before the abilities -- and the suit -- come back.
	 */
	private static void renderNormalHost(GuiGraphics g, Player player, SymbioteState s, SymbioteVitals v, long now) {
		boolean shift = forceShift || player.isShiftKeyDown();
		boolean charging = s.onslaughtChargeStart >= 0;
		float chargeFrac = charging ? Math.min(1.0f, (now - s.onslaughtChargeStart) / 60.0f) : 0.0f;
		int spikeFan = (int) Math.max(0L, v.spikeConeReadyAt - now);
		float spikeFanFrac = Math.min(1.0f, spikeFan / (float) SPIKE_FAN_DISPLAY);

		VanillaHud.Stack st = VanillaHud.Stack.bottomRight(g, 6).keys(6);
		for (int i = 0; i < VanillaHud.ORDER.length; i++) {
			AbilitySlot slot = VanillaHud.ORDER[i];
			int o = slot.ordinal();
			boolean active = (o == 3 && charging) || (o == 4 && (v.bladeActive || s.shieldHeld))
					|| (o == 5 && (v.thornsMode || s.tendrilGrabHeld));
			int cd = o < s.abilityCooldowns.size() ? (int) Math.max(0L, s.abilityCooldowns.get(o) - now) : 0;
			float cdFrac = VanillaHudExtras.frac("symbiote/" + o, cd);
			float shown = cdFrac, other = 0f;
			int secs = VanillaHud.seconds(cd);
			if (o == 1) {
				// Shift+G's Spike Fan runs its own cooldown
				if (shift) {
					shown = spikeFanFrac;
					secs = VanillaHud.seconds(spikeFan);
					other = cdFrac;
				} else {
					other = spikeFanFrac;
				}
			}
			st.slot(i, VanillaHud.keyLabel(slot), !v.broken, shift, shown, secs, other, active);
			if (o == 3 && charging) {
				st.hold(i, chargeFrac);
			}
		}

		// Biomass (Hairline, as before)
		float ratio = Math.max(0.0f, Math.min(1.0f, v.hp / SymbioteVitalsManager.MAX_HP));
		int fill = v.broken ? 0xFFC03030 : (ratio < 0.35f ? 0xFFE0A030 : COLOR_PURPLE);
		int valueColour = v.broken ? VanillaHud.RED : COLOR_LABEL;
		if (v.bladeActive || s.shieldHeld) {
			// feeding the blade / shield: the value breathes so you can see it is being spent
			valueColour = ((now / 6L) % 2L == 0L) ? valueColour : VanillaHud.WHITE;
		}
		Component label = Component.translatable("hud.projecthero.symbiote.hp");
		String value = Math.round(ratio * 100.0f) + "%";
		st.meter(label, ratio, fill, value, valueColour, MutationMeters.Style.HAIRLINE);
		if (v.broken) {
			VanillaHudExtras.tick(st, label, value, SymbioteVitalsManager.RECOVER_FRACTION);
		}
		st.title(Component.translatable("entity.projecthero.symbiote"), COLOR_LABEL);
		List<VanillaHud.Tag> tags = new ArrayList<>();
		if (v.broken) {
			tags.add(new VanillaHud.Tag(Component.translatable("hud.projecthero.symbiote.hp_broken").getString(), VanillaHud.RED));
		}
		st.tags(tags);
		if (MoveListOverlay.held()) {
			// v0.16.6: the shared move list (each key: plain move + Shift move)
			List<MoveListOverlay.Row> rows = new ArrayList<>();
			for (AbilitySlot slot : VanillaHud.ORDER) {
				int o = slot.ordinal();
				rows.add(MoveListOverlay.Row.of(MoveListOverlay.key(slot),
						Component.translatable("projecthero.symbiote.ability." + NORMAL_SLOT_ABILITIES[o]),
						Component.translatable("projecthero.symbiote.ability." + NORMAL_SHIFT_ABILITIES[o])));
			}
			st.moveList(rows);
		}
	}

	// ---------------- the hero hosts (v0.13.21) ----------------

	/**
	 * Black Suit Spider-Man (v0.16.2; v0.17.3 on the Minecraft-style column): adds the Hunger bar (Hairline, the Bonded (30)
	 * and Overdrive (80) thresholds ticked) to {@code s}, and the suit's words to {@code tags}: "Black Suit", the tier, the
	 * Symbiote revive (time / Ready / Hungry) and Take Over / Crash / "Won't let go". Nothing when the black suit is not
	 * worn. Called by {@link SpiderHud} (and by {@link #render} when Spider-Man's column is not on screen).
	 */
	static void blackSuitRows(VanillaHud.Stack st, Player player, long now, List<VanillaHud.Tag> tags) {
		SymbioteState s = player.getAttachedOrElse(ModAttachments.SYMBIOTE_STATE, null);
		boolean worn = s != null && s.hasSymbiote && s.active && SymbioteHostType.of(player) == SymbioteHostType.SPIDER_MAN;
		if (!worn) {
			return;
		}
		SymbioteVitals v = player.getAttachedOrElse(ModAttachments.SYMBIOTE_VITALS, new SymbioteVitals());
		if (v.bondingUntil > now) {
			return;
		}
		blackSuitDrawn = true;
		SymbioteHunger.Tier tier = SymbioteHunger.tier(player);
		float hunger = SymbioteHunger.get(player);
		int colour = switch (tier) {
			case RESTRAINED -> 0xFF8A86A0;
			case BONDED -> ACCENT_BLACK_SUIT;
			case OVERDRIVE -> 0xFFE04060;
			case TAKEOVER -> (now / 4) % 2 == 0 ? 0xFFFF3050 : 0xFFB01830;
		};
		Component label = Component.translatable("hud.projecthero.vanilla.symbiote.hunger");
		String value = Math.round(hunger) + "%";
		st.meter(label, hunger / SymbioteHunger.MAX, colour, value, VanillaHud.WHITE, MutationMeters.Style.HAIRLINE);
		for (float t : new float[] { SymbioteHunger.BONDED, SymbioteHunger.OVERDRIVE }) {
			VanillaHudExtras.tick(st, label, value, t / SymbioteHunger.MAX);
		}

		tags.add(new VanillaHud.Tag(Component.translatable("hud.projecthero.symbiote.black_suit").getString(), ACCENT_BLACK_SUIT));
		tags.add(new VanillaHud.Tag(Component.translatable("hud.projecthero.symbiote.hunger.tier." + tier.name().toLowerCase(java.util.Locale.ROOT))
				.getString(), colour));
		long reviveLeft = Math.max(0L, v.resurrectReadyAt - now);
		boolean fedEnough = hunger >= SymbioteHunger.RESURRECT_MIN || tier == SymbioteHunger.Tier.TAKEOVER;
		Component reviveValue = reviveLeft > 0L ? Component.literal(clock(reviveLeft))
				: Component.translatable(fedEnough ? "hud.projecthero.symbiote.revive_ready"
						: "hud.projecthero.symbiote.black_suit.revive_hungry");
		tags.add(new VanillaHud.Tag(Component.translatable("hud.projecthero.symbiote.black_suit.revive", reviveValue).getString(),
				reviveLeft <= 0L && fedEnough ? ACCENT_BLACK_SUIT : VanillaHud.GREY));

		long end = SymbioteHunger.takeoverEnd(player);
		if (tier == SymbioteHunger.Tier.TAKEOVER) {
			tags.add(new VanillaHud.Tag(Component.translatable("hud.projecthero.symbiote.hunger.takeover_left", (end - now + 19) / 20).getString(),
					colour));
		} else if (SymbioteHunger.crashing(player)) {
			tags.add(new VanillaHud.Tag(Component.translatable("hud.projecthero.symbiote.hunger.crash_left",
					(end + SymbioteHunger.CRASH_TICKS - now + 19) / 20).getString(), VanillaHud.GREY));
		} else if (hunger >= SymbioteHunger.MAX) {
			tags.add(new VanillaHud.Tag(Component.translatable("hud.projecthero.symbiote.hunger.takeover_ready",
					VanillaHud.keyLabel(AbilitySlot.SLOT_5)).getString(), (now / 6) % 2 == 0 ? 0xFFFF3050 : VanillaHud.WHITE));
		} else if (tier == SymbioteHunger.Tier.OVERDRIVE) {
			tags.add(new VanillaHud.Tag(Component.translatable("hud.projecthero.symbiote.hunger.locked").getString(), 0xFFE04060));
		}
	}

	/**
	 * Agent Venom (v0.17.3 on the Minecraft-style look): a block stacked above the Punisher's HUD, as wide as the column:
	 * <pre>
	 *   AGENT VENOM                           title
	 *   Revive [Hairline =======---]  12:34   the Symbiote revive recharging
	 *   +--+--+--+  Unleashed 8s              the three Sneak extras (Z X V, yellow: Shift moves); V framed while Symbiote
	 *   | Z| X| V|                            Unleashed runs, with its seconds left
	 *   +--+--+--+
	 * </pre>
	 */
	private static void renderAgentVenom(GuiGraphics g, Minecraft mc, Player player, SymbioteVitals v, long now, int anchor) {
		String[] abilities = SymbioteAgentVenomAbilities.ABILITIES;
		AbilitySlot[] keyedOn = SymbioteAgentVenomAbilities.KEYED_ON;
		int n = abilities.length;
		int[] cds = new int[n];
		for (int i = 0; i < n; i++) {
			cds[i] = com.projecthero.mod.punisher.Punisher.cooldownRemaining(player, abilities[i]);
		}
		// Unleashed's cooldown starts on activation, so its first UNLEASHED_TICKS are the power itself running
		int unleashedLeft = cds[2] - (SymbioteAgentVenomAbilities.CD_UNLEASHED - SymbioteAgentVenomAbilities.UNLEASHED_TICKS);

		int width = VanillaHud.width(6);
		int x0 = g.guiWidth() - 4 - width;
		int bottom = agentVenomBottom(g, anchor);
		int stripY = bottom - VanillaHud.HEIGHT;
		// the extras in R G Z X C V order
		Integer[] order = new Integer[n];
		for (int i = 0; i < n; i++) {
			order[i] = i;
		}
		java.util.Arrays.sort(order, java.util.Comparator.comparingInt(i -> orderIndex(keyedOn[i])));
		VanillaHud.strip(g, x0, stripY, n);
		for (int k = 0; k < n; k++) {
			int i = order[k];
			boolean running = i == 2 && unleashedLeft > 0;
			int cd = cds[i];
			float frac = running || cd <= 0 ? 0f : Math.min(1f, cd / (float) Math.max(1, AGENT_VENOM_COOLDOWNS[i]));
			VanillaHud.slot(g, x0, stripY, k, VanillaHud.keyLabel(keyedOn[i]), true, true, frac,
					VanillaHud.seconds(running ? unleashedLeft : cd), 0f, running);
		}
		if (unleashedLeft > 0) {
			String status = Component.translatable("hud.projecthero.agent_venom.unleashed").getString() + " " + VanillaHud.seconds(unleashedLeft) + "s";
			int sx = x0 + VanillaHud.width(n) + 4;
			g.drawString(mc.font, mc.font.plainSubstrByWidth(status, x0 + width - sx), sx, stripY + 7, ACCENT_AGENT_VENOM, true);
		}
		// the revive bar (Hairline)
		long reviveLeft = Math.max(0L, v.resurrectReadyAt - now);
		int total = SymbioteVitalsManager.resurrectCooldownFor(player);
		float frac = reviveLeft <= 0L ? 1.0f : 1.0f - Math.min(1.0f, reviveLeft / (float) Math.max(1, total));
		String value = reviveLeft <= 0L ? Component.translatable("hud.projecthero.symbiote.revive_ready").getString() : clock(reviveLeft);
		int by = stripY - 8;
		VanillaHud.meter(g, x0, by, width, Component.translatable("hud.projecthero.symbiote.revive"), frac, ACCENT_AGENT_VENOM, value,
				reviveLeft <= 0L ? ACCENT_AGENT_VENOM : VanillaHud.WHITE, MutationMeters.Style.HAIRLINE);
		String title = Component.translatable("hud.projecthero.agent_venom.title").getString();
		g.drawString(mc.font, mc.font.plainSubstrByWidth(title, width), x0, by - 14, ACCENT_AGENT_VENOM, true);
		// v0.16.6: no list of its own -- Left-Alt's shared move list (PunisherHud) names these as the keys' Shift moves,
		// stacked above this block (agentVenomPanelTop)
	}

	/** The bottom edge of the Agent Venom block: above the Punisher's reported column top, else where its old row left room. */
	private static int agentVenomBottom(GuiGraphics g, int anchor) {
		if (anchor != Integer.MIN_VALUE) {
			return anchor - 4;
		}
		return g.guiHeight() - 20 - 4 - 26 - 9 - 4; // PunisherHud's old row, its key letters 9 px above it, a 4-px gap
	}

	private static int orderIndex(AbilitySlot slot) {
		for (int i = 0; i < VanillaHud.ORDER.length; i++) {
			if (VanillaHud.ORDER[i] == slot) {
				return i;
			}
		}
		return VanillaHud.ORDER.length;
	}

	/** v0.16.6: the top of the Agent Venom block (its title line), so the Punisher's move list can stack above it. */
	static int agentVenomPanelTop(GuiGraphics g) {
		return agentVenomBottom(g, lastAnchor) - VanillaHud.HEIGHT - 8 - 14;
	}

	/** Last frame's anchor (for {@link #agentVenomPanelTop}, which PunisherHud calls before this frame's report reaches us). */
	private static int lastAnchor = Integer.MIN_VALUE;

	/** v0.16.6: Agent Venom's Sneak extras by the Punisher key each rides on (null = that key keeps the Punisher's own). */
	static Component agentVenomShift(AbilitySlot slot) {
		for (int i = 0; i < SymbioteAgentVenomAbilities.KEYED_ON.length; i++) {
			if (SymbioteAgentVenomAbilities.KEYED_ON[i] == slot) {
				return Component.translatable("projecthero.agent_venom.ability." + SymbioteAgentVenomAbilities.ABILITIES[i]);
			}
		}
		return null;
	}

	/** Ticks as m:ss. */
	private static String clock(long ticks) {
		long secs = (ticks + 19L) / 20L;
		return String.format(java.util.Locale.ROOT, "%d:%02d", secs / 60L, secs % 60L);
	}
}
