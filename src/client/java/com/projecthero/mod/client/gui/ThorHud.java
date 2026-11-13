package com.projecthero.mod.client.gui;

import java.util.ArrayList;
import java.util.List;

import com.projecthero.mod.attachment.CooldownState;
import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.visual.MutationMeters;
import com.projecthero.mod.power.StormEnergy;
import com.projecthero.mod.power.ThorAbility;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/**
 * The Thor HUD. v0.17.3 (user: "I really like the Minecraft-style HUD and ability keybinds ... apply this to all the powers"):
 * moved onto the shared Minecraft-style column ({@link VanillaHud.Stack}), bottom-right like the newest powers:
 * <pre>
 *   Storm-Charged +30%                 status tags
 *   THOR                               title
 *   Combo  [Meter ===----]  2/4        dual-wield combo steps (Mjolnir + Stormbreaker), the Twin Thunder recharge
 *   Wrath  [Hairline =====---]  60%    God of Thunder's Wrath buildup while Z is held
 *   Storm  [Hairline ========-]  84%   Storm Energy (the user's Hairline style, as before)
 *   +--+--+--+--+--+--+                R G Z X C V: cooldown sweep + seconds; Shift+G's Thunder Field and Shift+V's Thunderclap
 *   | R| G| Z| X| C| V|                on the durability bar (shown in the slot while Shift is held); Z fills while Wrath charges
 *   +--+--+--+--+--+--+
 * </pre>
 * Drawn while the player is holding Mjolnir or is bound to a hammer (v0.12.32). Hold Left-Alt for the move list.
 */
public final class ThorHud {
	/** v0.13.5: the Storm Energy bar's blue (brightened), and its low colour. */
	private static final int COLOR_ENERGY = 0xFF1E96FF;
	private static final int COLOR_ENERGY_LOW = 0xFF4C4C88;
	private static final int COLOR_ENERGY_TEXT = 0xFF8FC8FF;
	private static final int COLOR_GOLD = 0xFFF2C24E;
	private static final int COLOR_STORM = 0xFF9FD8FF;

	/** Slot ordinal (R G X Z V C) -> the plain-key ability id. Matches {@code ThorAbilityAdapter}. */
	private static final String[] SLOT_NAME_KEYS = {
			"call_mjolnir", "lightning_strike", "lightning_beam",
			"god_of_thunders_wrath", "hammer_volley", "chain_lightning"
	};
	/** v0.16.6: each key's Shift move (ThorAbilityAdapter), null where it has none. */
	private static final String[] SLOT_SHIFT_KEYS = { null, "thunder_field", null, null, "thunderclap", null };
	private static final ThorAbility[] SLOT_COOLDOWNS = {
			ThorAbility.CALL_HAMMER, ThorAbility.LIGHTNING_STRIKE, null,
			ThorAbility.GOD_OF_THUNDER, ThorAbility.HAMMER_VOLLEY, ThorAbility.CHAIN_LIGHTNING
	};
	/** The Shift move's cooldown on each key (Shift+G Thunder Field, Shift+V Thunderclap). */
	private static final ThorAbility[] SLOT_SHIFT_COOLDOWNS = { null, ThorAbility.THUNDER_FIELD, null, null, ThorAbility.THUNDERCLAP, null };

	/** Ticks the player must hold Z to charge God of Thunder's Wrath (mirrors {@code ThorPowers}). */
	private static final int WRATH_CHARGE_TICKS = 5 * 20;

	/** Set only by a client screenshot harness: the HUD draws as if the player were sneaking. */
	public static volatile boolean forceShift;

	private ThorHud() {
	}

	public static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
		Minecraft client = Minecraft.getInstance();
		Player player = client.player;
		if (player == null || client.options.hideGui) {
			return;
		}

		// v0.14.19: a worthy Stormbreaker carries Thor's kit as well
		boolean holdingMjolnir = com.projecthero.mod.power.ThorPowers.wieldsThorWeapon(player);

		// v0.12.32: a player who is BOUND to a hammer keeps the kit on screen even with Mjolnir out of their hands
		// (the abilities themselves already work bound-or-holding); it steps aside only while they are inside a Titan.
		boolean bound = player.getAttachedOrElse(ModAttachments.BOUND_HAMMER_ID, null) != null
				&& !com.projecthero.mod.titanshifter.TitanShifter.phase(player).insideForm();

		if (holdingMjolnir || bound) {
			renderKit(graphics, client, player);
		}
		// v0.13.4: the hammerless-flight countdown text was removed per user request.
	}

	private static void renderKit(GuiGraphics g, Minecraft client, Player player) {
		long gameTime = client.level != null ? client.level.getGameTime() : 0L;
		CooldownState cooldowns = player.getAttachedOrElse(ModAttachments.COOLDOWNS, null);
		boolean shift = forceShift || player.isShiftKeyDown();
		int wrath = player.getAttachedOrElse(ModAttachments.THOR_WRATH_CHARGE, 0);

		VanillaHud.Stack s = VanillaHud.Stack.bottomRight(g, 6).keys(6);
		for (int i = 0; i < VanillaHud.ORDER.length; i++) {
			AbilitySlot slot = VanillaHud.ORDER[i];
			int o = slot.ordinal();
			boolean hasShift = SLOT_SHIFT_KEYS[o] != null;
			ThorAbility shown = shift && hasShift ? SLOT_SHIFT_COOLDOWNS[o] : SLOT_COOLDOWNS[o];
			ThorAbility other = hasShift ? (shift ? SLOT_COOLDOWNS[o] : SLOT_SHIFT_COOLDOWNS[o]) : null;
			int left = remaining(cooldowns, shown, gameTime);
			int otherLeft = remaining(cooldowns, other, gameTime);
			s.slot(i, VanillaHud.keyLabel(slot), true, shift && hasShift, frac(shown, left), VanillaHud.seconds(left),
					frac(other, otherLeft), false);
			if (slot == AbilitySlot.SLOT_4 && wrath > 0) {
				s.hold(i, Math.min(1f, wrath / (float) WRATH_CHARGE_TICKS));
			}
		}

		// Storm Energy (Hairline, as before)
		float ratio = Math.max(0.0f, Math.min(1.0f, StormEnergy.get(player) / StormEnergy.MAX));
		s.meter(Component.translatable("hud.projecthero.vanilla.thor.storm"), ratio, ratio < 0.2f ? COLOR_ENERGY_LOW : COLOR_ENERGY,
				Math.round(ratio * 100.0f) + "%", COLOR_ENERGY_TEXT, MutationMeters.Style.HAIRLINE);
		// God of Thunder's Wrath buildup while Z is held (Hairline, as before; gold once full)
		if (wrath > 0) {
			float w = Math.min(1.0f, wrath / (float) WRATH_CHARGE_TICKS);
			s.meter(Component.translatable("hud.projecthero.vanilla.thor.wrath"), w, w >= 1f ? COLOR_GOLD : COLOR_ENERGY,
					Math.round(w * 100f) + "%", w >= 1f ? COLOR_GOLD : VanillaHud.WHITE, MutationMeters.Style.HAIRLINE);
		}
		// v0.16.1: the dual-wield combo's four steps, and the Twin Thunder Strike's recharge
		if (com.projecthero.mod.power.WeaponCombo.isDualWielding(player)) {
			com.projecthero.mod.power.WeaponComboState st = com.projecthero.mod.power.WeaponCombo.state(player);
			int step = st.weapon() == com.projecthero.mod.power.WeaponComboState.WEAPON_DUAL
					? com.projecthero.mod.power.WeaponCombo.step(player) : 0;
			int twin = remaining(cooldowns, ThorAbility.TWIN_THUNDER, gameTime);
			String value = twin > 0 ? VanillaHud.seconds(twin) + "s" : step + "/4";
			s.meter(Component.translatable("hud.projecthero.vanilla.thor.combo"), step / 4f, COLOR_GOLD, value,
					twin > 0 ? VanillaHud.GREY : COLOR_GOLD);
		}

		s.title(Component.translatable("hud.projecthero.vanilla.thor.title"), COLOR_GOLD);
		List<VanillaHud.Tag> tags = new ArrayList<>();
		int pct = com.projecthero.mod.power.ThorVisuals.fx(player).stormPct();
		if (pct > 0) {
			tags.add(new VanillaHud.Tag(Component.translatable("hud.projecthero.thor.storm_charged", pct).getString(), COLOR_STORM));
		}
		s.tags(tags);

		if (MoveListOverlay.held()) {
			// v0.16.6: the shared move list (G: Shift = Thunder Field, V: Shift = Thunderclap)
			List<MoveListOverlay.Row> rows = new ArrayList<>();
			for (AbilitySlot slot : VanillaHud.ORDER) {
				int o = slot.ordinal();
				rows.add(MoveListOverlay.Row.of(MoveListOverlay.key(slot), Component.translatable("projecthero.thor.ability." + SLOT_NAME_KEYS[o]),
						SLOT_SHIFT_KEYS[o] == null ? null : Component.translatable("projecthero.thor.ability." + SLOT_SHIFT_KEYS[o])));
			}
			s.moveList(rows);
		}
	}

	private static int remaining(CooldownState cds, ThorAbility a, long now) {
		return a != null && cds != null ? Math.max(0, cds.remainingTicks(a, now)) : 0;
	}

	/** 0..1 of {@code a}'s cooldown still to run (its length learned from the first value seen). */
	private static float frac(ThorAbility a, int left) {
		return a == null ? 0f : VanillaHudExtras.frac("thor/" + a.name(), left);
	}
}
