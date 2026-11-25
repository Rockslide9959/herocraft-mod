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
 *   Storm          84%                                 Storm Energy: the vanilla XP bar in blue, the number over its middle
 *   [XP bar =========|=========-------]                (v0.17.7 polish)
 *   R Call Weapon  G Lightning Strike  ...             the boxes' move names, wrapped (only while Left-Alt is held)
 *   | R| G| Z| X| C| V|
 * </pre>
 * Drawn while the player holds Mjolnir / a worthy Stormbreaker or is bound to a hammer. Hold Left-Alt for the full move list.
 */
public final class ThorHud {
	private static final int COLOR_ENERGY = 0xFF1E96FF;
	private static final int COLOR_ENERGY_TEXT = 0xFF8FC8FF;
	private static final int COLOR_ENERGY_LOW_TEXT = 0xFF6F8FD8;
	/** v0.17.7 polish: the XP bar's fill, in Thor blue (the vanilla green's brightness), and its darker low-energy shade. */
	private static final int XP_BLUE = 0x3DA5FF;
	private static final int XP_LOW = 0x2A4E9A;
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

		// the boxes' move names, narrow and wrapped -- v0.17.7 (user): only while Left-Alt is held, like every other HUD
		if (net.minecraft.client.gui.screens.Screen.hasAltDown() || MoveListOverlay.held()) {
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
		}

		float ratio = Math.max(0.0f, Math.min(1.0f, StormEnergy.get(player) / StormEnergy.MAX));
		stormBar(s, ratio, gameTime);
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

	/**
	 * v0.17.7 polish (user: "Can you make thors bar look like a blue xp bar please"): Storm Energy as the vanilla experience bar -- its
	 * own background and progress sprites (the notches, highlight row and end caps), the progress tinted Thor blue by
	 * {@link com.projecthero.mod.client.sentry.SentryHud#xpBar} (the Sentry's Sun / Void bars use the same call) -- across the column's
	 * width (182 px at most, like the real one), with the percentage centred over it in the XP level's outlined digits and the label at
	 * the left (cut to fit). Darker blue under 20%, the top row breathing softly when full.
	 */
	private static void stormBar(VanillaHud.Stack s, float ratio, long gameTime) {
		GuiGraphics g = s.g;
		s.reserve(17);
		int top = s.top();
		int w = Math.min(182, s.width);
		int x = s.x0 + s.width - w;
		float brighten = ratio >= 0.999f ? 1.0f + 0.12f * (0.5f + 0.5f * net.minecraft.util.Mth.sin(gameTime * 0.15f)) : 1.0f;
		com.projecthero.mod.client.sentry.SentryHud.xpBar(g, x, top + 11, w, ratio, ratio < 0.2f ? XP_LOW : XP_BLUE, brighten, new float[0]);
		String pct = Math.round(ratio * 100.0f) + "%";
		int pw = s.font.width(pct);
		int tx = x + (w - pw) / 2;
		int ty = top + 2;
		// the XP level's look: black outline on four sides, then the colour
		g.drawString(s.font, pct, tx + 1, ty, 0xFF000000, false);
		g.drawString(s.font, pct, tx - 1, ty, 0xFF000000, false);
		g.drawString(s.font, pct, tx, ty + 1, 0xFF000000, false);
		g.drawString(s.font, pct, tx, ty - 1, 0xFF000000, false);
		g.drawString(s.font, pct, tx, ty, ratio < 0.2f ? COLOR_ENERGY_LOW_TEXT : COLOR_ENERGY_TEXT, false);
		String label = Component.translatable("hud.projecthero.vanilla.thor.storm").getString();
		int room = Math.max(0, tx - s.x0 - 4);
		if (s.font.width(label) > room) {
			label = s.font.plainSubstrByWidth(label, Math.max(0, room - s.font.width("."))) + ".";
		}
		g.drawString(s.font, label, s.x0, ty, VanillaHud.WHITE, true);
	}

	private static int remaining(CooldownState cds, ThorAbility a, long now) {
		return a != null && cds != null ? Math.max(0, cds.remainingTicks(a, now)) : 0;
	}

	private static float frac(ThorAbility a, int left) {
		return a == null ? 0f : VanillaHudExtras.frac("thor/" + a.name(), left);
	}
}
