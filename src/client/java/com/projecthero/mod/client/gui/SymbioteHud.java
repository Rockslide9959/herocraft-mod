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
			"tendril_strike", "spike_shot", "leap", "barrage", "form", "maw_bite"
	};
	/** v0.17.5 moveset v2: the hold move on each key (null = none), and X's airborne move, for the move list. */
	private static final String[] NORMAL_HOLD_ABILITIES = {
			"impaling_spear", "spike_rain", null, "ground_eruption", null, "devour"
	};
	/** v0.17.5: each hold move's cooldown (ticks) and its index in the synced cooldown list, for the key box's thin strip. */
	private static final int[] HOLD_CD_INDEX = { 6, 7, 8, 9, -1, 11 };
	private static final int[] HOLD_CD_TICKS = { 160, 280, 120, 240, 0, 400 };
	/**
	 * v0.17.5: the Shift variant's own cooldown entry on R / X / Z (Tendril Sweep 12, Grapple 14, Onslaught 13 -- they no longer share
	 * the plain move's). G (Spike Fan) and C (Tendril Grab) are handled below as before; V has none.
	 */
	private static final int[] SHIFT_CD_INDEX = { 12, -1, 14, com.projecthero.mod.symbiote.monster.SymbioteMonster.CDI_MONSTER, -1, -1 }; // v0.17.5: Z = Monster form
	/** Client-side: when each of R G X Z V C went down without Sneak (for the hold fill), or -1. */
	private static final long[] HOLD_DOWN = { -1, -1, -1, -1, -1, -1 };
	/** v0.16.6: the Shift move on each key (SymbioteAbilityManager.handle's sneak branches), for the move list. */
	private static final String[] NORMAL_SHIFT_ABILITIES = {
			"tendril_sweep", "spike_fan", "grapple", "monster_form", "shield", "tendril_grab"
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

		// v0.17.5 (a Secondary power): stacked on a Primary, the column only shows while the suit is on -- and then it owns the corner
		// (every other power's column steps aside through VanillaHud.muteOthers); suit off, the Primary's HUD is the one on screen
		if (com.projecthero.mod.symbiote.Symbiote.dormant(player)) {
			return;
		}
		// v0.17.5 Monster form: while transformed the column shows the monster's moves and the form's timer instead
		if (com.projecthero.mod.client.symbiote.monster.SymbioteMonsterHud.render(g, player, v, now, shift,
				com.projecthero.mod.symbiote.Symbiote.ownsKeysOverPrimary(player))) {
			return;
		}
		VanillaHud.Stack st = (com.projecthero.mod.symbiote.Symbiote.ownsKeysOverPrimary(player)
				? VanillaHud.Stack.bottomRightOwned(g, 6) : VanillaHud.Stack.bottomRight(g, 6)).keys(6);
		for (int i = 0; i < VanillaHud.ORDER.length; i++) {
			AbilitySlot slot = VanillaHud.ORDER[i];
			int o = slot.ordinal();
			// v0.17.5: V lights while a Form (Blade / Thorns) or the shield is up; C while a grab is held
			boolean active = (o == 3 && charging) || (o == 4 && (v.bladeActive || v.thornsMode || s.shieldHeld))
					|| (o == 5 && s.tendrilGrabHeld);
			int cd = o < s.abilityCooldowns.size() ? (int) Math.max(0L, s.abilityCooldowns.get(o) - now) : 0;
			float cdFrac = VanillaHudExtras.frac("symbiote/" + o, cd);
			float shown = cdFrac, other = 0f;
			int secs = VanillaHud.seconds(cd);
			// v0.17.5: the key's hold move (and X's Dive Bomb) cools down on the thin strip along the bottom of the box
			float holdCd = holdCooldownFrac(s, o, now);
			if (o == 1) {
				// Shift+G's Spike Fan runs its own cooldown
				if (shift) {
					shown = spikeFanFrac;
					secs = VanillaHud.seconds(spikeFan);
					other = cdFrac;
				} else {
					other = Math.max(spikeFanFrac, holdCd);
				}
			} else if (o == 5) {
				// C: Maw Bite (cooldown index 10) is the tap now; Shift+C's grab keeps slot 5's own
				int maw = s.abilityCooldowns.size() > 10 ? (int) Math.max(0L, s.abilityCooldowns.get(10) - now) : 0;
				float mawFrac = VanillaHudExtras.frac("symbiote/maw", maw);
				if (shift) {
					other = mawFrac;
				} else {
					shown = mawFrac;
					secs = VanillaHud.seconds(maw);
					other = Math.max(cdFrac, holdCd);
				}
			} else if (o == 2) {
				// X: Grapple's own cooldown with Shift; v0.17.5 the strip carries Dive Bomb and the Symbiote Wings (whichever is longer)
				int scd = s.abilityCooldowns.size() > 14 ? (int) Math.max(0L, s.abilityCooldowns.get(14) - now) : 0;
				float shiftFrac = VanillaHudExtras.frac("symbiote/shift" + o, scd);
				float wings = wingsCooldownFrac(s, now);
				if (shift) {
					shown = shiftFrac;
					secs = VanillaHud.seconds(scd);
					other = Math.max(cdFrac, wings);
				} else {
					other = Math.max(Math.max(holdCd, shiftFrac), wings);
				}
			} else if (SHIFT_CD_INDEX[o] >= 0) {
				// R / X / Z: the plain move's cooldown, and the Shift move's while Shift is held (the same rule as G and C)
				int idx = SHIFT_CD_INDEX[o];
				int scd = idx < s.abilityCooldowns.size() ? (int) Math.max(0L, s.abilityCooldowns.get(idx) - now) : 0;
				float shiftFrac = VanillaHudExtras.frac("symbiote/shift" + o, scd);
				if (shift) {
					shown = shiftFrac;
					secs = VanillaHud.seconds(scd);
					other = cdFrac;
				} else {
					other = Math.max(holdCd, shiftFrac);
				}
			} else if (!shift) {
				other = holdCd;
			}
			st.slot(i, VanillaHud.keyLabel(slot), !v.broken, shift, shown, secs, other, active);
			if (o == 3 && charging) {
				st.hold(i, chargeFrac);
			} else {
				float hold = holdFill(o, shift, now);
				if (hold > 0f) {
					st.hold(i, hold);
				}
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
		// v0.17.5: the Form V cycles to (Normal shows nothing) -- Blade / Axe / Mace / Whip / Thorns
		int form = SymbioteVitalsManager.form(player);
		if (form != SymbioteVitalsManager.FORM_NORMAL) {
			tags.add(new VanillaHud.Tag(Component.translatable("message.projecthero.symbiote.form",
					Component.translatable("projecthero.symbiote.form." + SymbioteVitalsManager.formName(form))).getString(), COLOR_LABEL));
		}
		// v0.17.5 Symbiote Wings: flight time left while flying, "Gliding", or the cooldown (no new key, no new box)
		com.projecthero.mod.symbiote.SymbioteWings.Phase wp = com.projecthero.mod.symbiote.SymbioteWings.phase(v, now);
		if (wp == com.projecthero.mod.symbiote.SymbioteWings.Phase.FLYING) {
			tags.add(new VanillaHud.Tag(Component.translatable("hud.projecthero.symbiote.wings.flying",
					String.format(java.util.Locale.ROOT, "%.1f", com.projecthero.mod.symbiote.SymbioteWings.flightTicksLeft(player) / 20.0f))
					.getString(), COLOR_LABEL));
		} else if (wp == com.projecthero.mod.symbiote.SymbioteWings.Phase.GLIDING) {
			tags.add(new VanillaHud.Tag(Component.translatable("hud.projecthero.symbiote.wings.gliding").getString(), COLOR_LABEL));
		} else {
			int wcd = s.abilityCooldowns.size() > com.projecthero.mod.symbiote.SymbioteWings.CDI_WINGS
					? (int) Math.max(0L, s.abilityCooldowns.get(com.projecthero.mod.symbiote.SymbioteWings.CDI_WINGS) - now) : 0;
			if (wcd > 0) {
				tags.add(new VanillaHud.Tag(Component.translatable("hud.projecthero.symbiote.wings.cooldown", VanillaHud.seconds(wcd))
						.getString(), VanillaHud.GREY));
			}
		}
		st.tags(tags);
		if (MoveListOverlay.held()) {
			// v0.16.6: the shared move list (each key: plain move + Shift move); v0.17.5: + the hold move / X's air move
			List<MoveListOverlay.Row> rows = new ArrayList<>();
			for (AbilitySlot slot : VanillaHud.ORDER) {
				int o = slot.ordinal();
				Component extra = null;
				if (NORMAL_HOLD_ABILITIES[o] != null) {
					extra = Component.translatable("hud.projecthero.move_list.hold",
							Component.translatable("projecthero.symbiote.ability." + NORMAL_HOLD_ABILITIES[o]));
				} else if (o == AbilitySlot.SLOT_3.ordinal()) {
					// v0.17.5: + double-tap Space in the air for the Symbiote Wings
					extra = Component.translatable("hud.projecthero.symbiote.move_list.air_wings",
							Component.translatable("projecthero.symbiote.ability.dive_bomb"));
				}
				if (o == AbilitySlot.SLOT_4.ordinal()) {
					// v0.17.5 weapons: Z with a weapon out is that weapon's move
					extra = Component.translatable("hud.projecthero.symbiote.move_list.weapon_z",
							Component.translatable("projecthero.symbiote.ability." + NORMAL_HOLD_ABILITIES[o]));
				}
				rows.add(MoveListOverlay.Row.of(MoveListOverlay.key(slot),
						Component.translatable("projecthero.symbiote.ability." + NORMAL_SLOT_ABILITIES[o]),
						Component.translatable("projecthero.symbiote.ability." + NORMAL_SHIFT_ABILITIES[o]), extra));
			}
			st.moveList(rows);
		}
	}

	/** v0.17.5 Symbiote Wings: the wings' remaining cooldown, 0..1 of its full length. */
	private static float wingsCooldownFrac(SymbioteState s, long now) {
		int idx = com.projecthero.mod.symbiote.SymbioteWings.CDI_WINGS;
		if (idx >= s.abilityCooldowns.size()) {
			return 0f;
		}
		long left = Math.max(0L, s.abilityCooldowns.get(idx) - now);
		return Math.min(1f, left / (float) com.projecthero.mod.symbiote.SymbioteWings.COOLDOWN_TICKS);
	}

	/** v0.17.5: the hold move's (or Dive Bomb's) remaining cooldown on key {@code o}, 0..1 of its full length. */
	private static float holdCooldownFrac(SymbioteState s, int o, long now) {
		int idx = HOLD_CD_INDEX[o];
		if (idx < 0 || idx >= s.abilityCooldowns.size()) {
			return 0f;
		}
		long left = Math.max(0L, s.abilityCooldowns.get(idx) - now);
		return Math.min(1f, left / (float) Math.max(1, HOLD_CD_TICKS[o]));
	}

	/**
	 * v0.17.5: the key box fills while R / G / Z / C is held without Sneak -- up to the hold threshold (0.4 s), then on R the
	 * Impaling Spear's charge (another second). Read from the local key, so it costs no packets; 0 when nothing is held.
	 */
	private static float holdFill(int o, boolean shift, long now) {
		if (NORMAL_HOLD_ABILITIES[o] == null || o >= com.projecthero.mod.client.ModKeyBindings.ABILITY_SLOTS.length) {
			return 0f;
		}
		boolean down = com.projecthero.mod.client.ModKeyBindings.ABILITY_SLOTS[o].isDown();
		if (!down) {
			HOLD_DOWN[o] = -1;
			return 0f;
		}
		if (HOLD_DOWN[o] < 0) {
			HOLD_DOWN[o] = shift ? Long.MAX_VALUE : now; // a Sneak press is the Shift move: no hold
		}
		if (HOLD_DOWN[o] == Long.MAX_VALUE) {
			return 0f;
		}
		long held = now - HOLD_DOWN[o];
		int hold = com.projecthero.mod.symbiote.SymbioteAbilityManager.HOLD_TICKS;
		if (o == 0 && held >= hold) {
			return Math.min(1f, 0.35f + 0.65f * (held - hold) / 20f); // the Spear charging
		}
		return Math.min(1f, held / (float) hold) * (o == 0 ? 0.35f : 1f);
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
