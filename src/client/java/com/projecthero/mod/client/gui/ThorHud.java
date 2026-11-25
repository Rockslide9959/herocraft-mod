package com.projecthero.mod.client.gui;

import java.util.ArrayList;
import java.util.List;

import com.projecthero.mod.attachment.CooldownState;
import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.visual.MutationMeters;
import com.projecthero.mod.power.StormEnergy;
import com.projecthero.mod.power.ThorAbility;
import com.projecthero.mod.power.ThorFx;
import com.projecthero.mod.power.ThorKit;
import com.projecthero.mod.power.ThorState;

import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Player;

/**
 * The Thor HUD on the shared Minecraft-style column ({@link VanillaHud.Stack}), bottom-right. v0.17.7 Cosmic revamp: the keys follow
 * Thor's weapon state ({@link ThorState}) -- each box shows that state's move cooldown (Shift shows the Shift moves, the other move
 * on the durability bar), and a narrow wrapped line above the keys names the six moves the boxes hold right now:
 * <pre>
 *   [Mjolnir]  Storm-Charged +30%  God of Thunder     status tags (the state badge first)
 *   THOR                                               title
 *   Combo  [Meter ===----]  2/4                        dual-wield combo (Twin Gods), the Twin Thunder recharge
 *   Charge [Hairline =====---]  60%                    Thunder Fist charge (Unarmed, Shift+R held)
 *   Storm  [Hairline ========-]  84%                   Storm Energy
 *   R Call Weapon  G Lightning Strike  ...             the boxes' move names, wrapped
 *   | R| G| Z| X| C| V|
 * </pre>
 * Drawn while the player holds Mjolnir / a worthy Stormbreaker or is bound to a hammer. Hold Left-Alt for the full move list.
 */
public final class ThorHud {
	private static final int COLOR_ENERGY = 0xFF1E96FF;
	private static final int COLOR_ENERGY_LOW = 0xFF4C4C88;
	private static final int COLOR_ENERGY_TEXT = 0xFF8FC8FF;
	private static final int COLOR_GOLD = 0xFFF2C24E;
	private static final int COLOR_STORM = 0xFF9FD8FF;
	/** The state badge colours: Unarmed pale blue, Mjolnir storm blue, Stormbreaker Bifrost violet, Twin Gods gold. */
	private static final int[] STATE_COLOURS = { 0xFFCFE8FF, 0xFF6FB8FF, 0xFFD08CFF, 0xFFF2C24E };

	/** Set only by a client screenshot harness: the HUD draws as if the player were sneaking. */
	public static volatile boolean forceShift;

	private ThorHud() {
	}

	public static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
		if (com.projecthero.mod.client.gui.VanillaHud.secondaryOwnsHud()) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		Player player = client.player;
		if (player == null || client.options.hideGui) {
			return;
		}
		boolean holding = com.projecthero.mod.power.ThorPowers.wieldsThorWeapon(player);
		boolean bound = player.getAttachedOrElse(ModAttachments.BOUND_HAMMER_ID, null) != null
				&& !com.projecthero.mod.titanshifter.TitanShifter.phase(player).insideForm();
		if (holding || bound) {
			renderKit(graphics, client, player);
		}
	}

	private static void renderKit(GuiGraphics g, Minecraft client, Player player) {
		long gameTime = client.level != null ? client.level.getGameTime() : 0L;
		CooldownState cooldowns = player.getAttachedOrElse(ModAttachments.COOLDOWNS, null);
		boolean shift = forceShift || player.isShiftKeyDown();
		ThorState st = ThorState.of(player);
		ThorFx fx = com.projecthero.mod.power.ThorVisuals.fx(player);

		VanillaHud.Stack s = VanillaHud.Stack.bottomRight(g, 6).keys(6);
		for (int i = 0; i < VanillaHud.ORDER.length; i++) {
			AbilitySlot slot = VanillaHud.ORDER[i];
			ThorAbility shown = ThorKit.cooldownOf(st, slot, shift);
			ThorAbility other = ThorKit.cooldownOf(st, slot, !shift);
			int left = remaining(cooldowns, shown, gameTime);
			int otherLeft = other == shown ? 0 : remaining(cooldowns, other, gameTime);
			boolean channel = (slot == AbilitySlot.SLOT_6 && shift && fx.has(ThorFx.CH_BEAM))
					|| (slot == AbilitySlot.SLOT_1 && fx.has(ThorFx.CH_FIST));
			s.slot(i, VanillaHud.keyLabel(slot), true, shift, frac(shown, left), VanillaHud.seconds(left), frac(other, otherLeft), channel);
		}

		// the boxes' move names, narrow and wrapped
		MutableComponent names = Component.empty();
		for (int i = 0; i < VanillaHud.ORDER.length; i++) {
			AbilitySlot slot = VanillaHud.ORDER[i];
			if (i > 0) {
				names.append("  ");
			}
			names.append(Component.literal(VanillaHud.keyLabel(slot) + " ").withStyle(shift ? ChatFormatting.YELLOW : ChatFormatting.GOLD))
					.append(Component.translatable("projecthero.thor.ability." + ThorKit.moveId(st, slot, shift)).withStyle(ChatFormatting.GRAY));
		}
		s.wrapped(names, VanillaHud.WHITE);

		float ratio = Math.max(0.0f, Math.min(1.0f, StormEnergy.get(player) / StormEnergy.MAX));
		s.meter(Component.translatable("hud.projecthero.vanilla.thor.storm"), ratio, ratio < 0.2f ? COLOR_ENERGY_LOW : COLOR_ENERGY,
				Math.round(ratio * 100.0f) + "%", COLOR_ENERGY_TEXT, MutationMeters.Style.HAIRLINE);
		// Unarmed: the Thunder Fist charge while Shift+R is held (gold once full)
		if (fx.has(ThorFx.CH_FIST)) {
			float w = Math.min(1.0f, (gameTime - fx.chargeStart()) / (float) ThorKit.FIST_CHARGE_TICKS);
			s.meter(Component.translatable("hud.projecthero.vanilla.thor.charge"), w, w >= 1f ? COLOR_GOLD : COLOR_ENERGY,
					Math.round(w * 100f) + "%", w >= 1f ? COLOR_GOLD : VanillaHud.WHITE, MutationMeters.Style.HAIRLINE);
		}
		if (com.projecthero.mod.power.WeaponCombo.isDualWielding(player)) {
			com.projecthero.mod.power.WeaponComboState cs = com.projecthero.mod.power.WeaponCombo.state(player);
			int step = cs.weapon() == com.projecthero.mod.power.WeaponComboState.WEAPON_DUAL
					? com.projecthero.mod.power.WeaponCombo.step(player) : 0;
			int twin = remaining(cooldowns, ThorAbility.TWIN_THUNDER, gameTime);
			String value = twin > 0 ? VanillaHud.seconds(twin) + "s" : step + "/4";
			s.meter(Component.translatable("hud.projecthero.vanilla.thor.combo"), step / 4f, COLOR_GOLD, value,
					twin > 0 ? VanillaHud.GREY : COLOR_GOLD);
		}

		s.title(Component.translatable("hud.projecthero.vanilla.thor.title"), COLOR_GOLD);
		List<VanillaHud.Tag> tags = new ArrayList<>();
		tags.add(new VanillaHud.Tag(Component.translatable("hud.projecthero.thor.state." + st.id).getString(), STATE_COLOURS[st.ordinal()]));
		int pct = fx.stormPct();
		if (pct > 0) {
			tags.add(new VanillaHud.Tag(Component.translatable("hud.projecthero.thor.storm_charged", pct).getString(), COLOR_STORM));
		}
		if (ThorKit.active(player, ThorAbility.GOD_ACTIVE)) {
			tags.add(new VanillaHud.Tag(Component.translatable("projecthero.thor.ability.god_of_thunder").getString(), COLOR_GOLD));
		}
		if (ThorKit.active(player, ThorAbility.INCARNATE_ACTIVE)) {
			tags.add(new VanillaHud.Tag(Component.translatable("projecthero.thor.ability.lightning_incarnate").getString(), 0xFFFFFFFF));
		}
		s.tags(tags);

		if (MoveListOverlay.held()) {
			List<MoveListOverlay.Row> rows = new ArrayList<>();
			for (AbilitySlot slot : VanillaHud.ORDER) {
				rows.add(MoveListOverlay.Row.of(MoveListOverlay.key(slot),
						Component.translatable("projecthero.thor.ability." + ThorKit.moveId(st, slot, false)),
						Component.translatable("projecthero.thor.ability." + ThorKit.moveId(st, slot, true))));
			}
			s.moveList(rows);
		}
	}

	private static int remaining(CooldownState cds, ThorAbility a, long now) {
		return a != null && cds != null ? Math.max(0, cds.remainingTicks(a, now)) : 0;
	}

	private static float frac(ThorAbility a, int left) {
		return a == null ? 0f : VanillaHudExtras.frac("thor/" + a.name(), left);
	}
}
