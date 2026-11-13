package com.projecthero.mod.client.gui;

import java.util.ArrayList;
import java.util.List;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.visual.MutationMeters;
import com.projecthero.mod.hulk.Hulk;
import com.projecthero.mod.hulk.HulkAbilities;
import com.projecthero.mod.hulk.HulkAbilityManager;
import com.projecthero.mod.hulk.HulkConfig;
import com.projecthero.mod.hulk.data.HulkState;
import com.projecthero.mod.hulk.gladiator.GladiatorAbilities;

import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/**
 * The Hulk HUD. v0.17.3: on the vanilla pieces ({@link VanillaHud.Stack}), bottom-right like the newest powers:
 * <pre>
 *   Angry  WORLDBREAKER  Carrying Steve          tags: the rage tier, World Breaker (ready / running / burned out), the rider
 *   Hulk Form                                    title (Gladiator Hulk in the gladiator gear, gold)
 *   Rage [Hairline ===|=====|==]   62%           the rage (tier marks), or World Breaker's countdown while it runs
 *   Leap [Gauge =========-]        100           only while X charges a leap / V winds up a throw (HulkMovesClient)
 *   +--+--+--+--+--+--+                          R G Z X C V: the tap move (Shift: the Shift move, yellow letters); the other
 *                                                move's cooldown is the durability bar; greyed while rampaging / calming
 * </pre>
 * Banner has no HUD (v0.16.4: no rage, no hold-H timer). Mid-screen: the death sequence and the HULK SMASH wind-up. The rampage
 * warning, the rampage view and its ring minigame are {@code HulkRampageClient}'s. Only drawn for a player with the Gamma power.
 */
public final class HulkHud {
	private static final int COLOR_BG = 0x80000000;
	private static final int COLOR_RAGE = 0xFF3FAF3A;
	private static final int COLOR_RAGE_READY = 0xFF7CFF4A;
	private static final int COLOR_RAGE_HULK = 0xFF9BFF3A;
	private static final int COLOR_EXHAUSTED = 0xFF8A8A8A;
	private static final int COLOR_GLADIATOR = 0xFFFFC34A;
	/** v0.15.18: indexed by {@link HulkConfig#rageTier}. */
	private static final String[] TIER_KEYS = { "hud.projecthero.hulk.tier.calm", "hud.projecthero.hulk.tier.angry",
			"hud.projecthero.hulk.tier.enraged" };
	private static final int[] TIER_COLOURS = { VanillaHud.GREY, VanillaHud.GOLD, VanillaHud.RED };

	private HulkHud() {
	}

	public static void render(GuiGraphics g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		Player player = mc.player;
		if (player == null || mc.options.hideGui || mc.level == null) {
			return;
		}
		HulkState s = player.getAttachedOrElse(ModAttachments.HULK_STATE, null);
		if (s == null || !s.hasPower) {
			return;
		}
		long now = mc.level.getGameTime();
		if (com.projecthero.mod.client.hulk.HulkRampageClient.viewTaken()) {
			return; // v0.16.4: the rampage view owns the screen (its ring minigame sits in the black strip)
		}
		// v0.16.4 (user): "Rage bar only in Hulk form" -- Banner has no rage, and no bar. v0.16.4 (user): no timer for the hold-H
		// cooldown either ("it also shouldnt show a timer for the 1 minute transformation cooldown")
		if (!s.hulk) {
			renderCentre(g, mc, player, s, now);
			return;
		}
		float pt = delta.getGameTimeDeltaPartialTick(false);
		boolean gladiator = GladiatorAbilities.active(player);
		boolean shift = player.isShiftKeyDown() || org.lwjgl.glfw.GLFW.glfwGetKey(mc.getWindow().getWindow(),
				org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT) == org.lwjgl.glfw.GLFW.GLFW_PRESS;

		VanillaHud.Stack st = VanillaHud.Stack.bottomRight(g, 6).keys(6);
		if (gladiator) {
			gladiatorKeys(st, player, s, now, shift);
		} else {
			keys(st, player, s, now, shift);
		}
		// v0.16.4 (user): the Super Leap / throw charge sits right above his keys
		com.projecthero.mod.client.hulk.HulkMovesClient.leapRow(st);

		// the rage (v0.16.4: it also sets his size and his damage, +0..+50%) -- a Hairline row, as the old 3-px bar was
		boolean worldBreaker = s.worldBreaker(now);
		int pct = Math.round(Math.max(0.0f, Math.min(1.0f, s.rage / HulkConfig.RAGE_MAX)) * 100.0f);
		int tier = HulkConfig.rageTier(s.rage);
		if (worldBreaker) {
			// v0.16.4 (user): World Breaker -- the rage row becomes its 30 s countdown, pulsing deep green
			int left = com.projecthero.mod.hulk.WorldBreaker.ticksLeft(player);
			float f = Math.max(0.0f, Math.min(1.0f, left / (float) Math.max(1, HulkConfig.worldBreaker().durationTicks)));
			float pulse = 0.5f + 0.5f * (float) Math.sin((now + pt) * 0.4f);
			st.meter(Component.translatable("hud.projecthero.hulk.world_breaker_label"), f, 0xFF2FD12A, ((left + 19) / 20) + "s",
					lerpColour(0xFF2FD12A, 0xFFD8FFB0, pulse), MutationMeters.Style.HAIRLINE);
		} else {
			int valueColour = tier == HulkConfig.TIER_CALM ? COLOR_RAGE : COLOR_RAGE_HULK;
			if (s.rage > HulkConfig.RAGE_TIER_ENRAGED) {
				// an Enraged Hulk: the value throbs, faster as it nears 100
				float heat = (s.rage - HulkConfig.RAGE_TIER_ENRAGED) / (HulkConfig.RAGE_MAX - HulkConfig.RAGE_TIER_ENRAGED);
				float t = (now + pt) * (0.25f + 0.35f * heat);
				valueColour = (Math.sin(t) > 0.3) ? 0xFFB8FF7A : COLOR_RAGE_READY;
			}
			Component label = Component.translatable("hud.projecthero.hulk.rage_label");
			String value = pct + "%";
			st.meter(label, pct / 100.0f, COLOR_RAGE, value, valueColour, MutationMeters.Style.HAIRLINE);
			// v0.15.18: where the Angry and Enraged tiers start
			MeterMarks.marks(st, label, value, 0xC0FFFFFF, HulkConfig.RAGE_TIER_ANGRY / HulkConfig.RAGE_MAX,
					HulkConfig.RAGE_TIER_ENRAGED / HulkConfig.RAGE_MAX);
		}

		// the form name (v0.15.3: the full gladiator gear swaps in the weapon kit)
		st.title(Component.translatable(gladiator ? "hud.projecthero.hulk.gladiator_form" : "hud.projecthero.hulk.form"),
				gladiator ? COLOR_GLADIATOR : VanillaHud.GREEN);

		// status tags: the rage tier, World Breaker, the rider
		List<VanillaHud.Tag> tags = new ArrayList<>();
		if (!worldBreaker) {
			tags.add(new VanillaHud.Tag(Component.translatable(TIER_KEYS[tier]).getString(), TIER_COLOURS[tier]));
		}
		if (worldBreaker) {
			tags.add(new VanillaHud.Tag(Component.translatable("hud.projecthero.hulk.world_breaker_active").getString(),
					(now / 4L) % 2L == 0L ? 0xFF9BFF3A : 0xFFE8FFD8));
		} else if (com.projecthero.mod.hulk.WorldBreaker.available(player)) {
			// the prompt: H unleashes it (pulsing)
			float pulse = 0.5f + 0.5f * (float) Math.sin((now + pt) * 0.35f);
			tags.add(new VanillaHud.Tag(Component.translatable("hud.projecthero.hulk.world_breaker_ready").getString(),
					lerpColour(0xFF3FAF3A, 0xFFE8FFD8, pulse)));
		} else if (com.projecthero.mod.hulk.WorldBreaker.coolingDown(player)) {
			// v0.16.4 (user dislikes timers on Hulk lockouts): just "burned out", greyed
			tags.add(new VanillaHud.Tag(Component.translatable("hud.projecthero.hulk.world_breaker_burnout").getString(), COLOR_EXHAUSTED));
		}
		st.tags(tags);
		// v0.15.21: he can't see his own back -- who is riding it (wrapped: a long name never sprawls)
		Player rider = com.projecthero.mod.hulk.HulkRiding.rider(player);
		if (rider != null) {
			st.wrapped(Component.translatable("hud.projecthero.hulk.carrying", rider.getDisplayName()), 0xFF9CFF9C);
		}

		if (MoveListOverlay.held()) {
			// v0.16.6: Left-Alt -> the shared move list (each key: tap move + Shift move), stacked above the whole HUD
			List<MoveListOverlay.Row> moves = new ArrayList<>();
			for (AbilitySlot slot : VanillaHud.ORDER) {
				String tap = gladiator ? GladiatorAbilities.tapId(slot) : HulkAbilityManager.abilityIdOf(slot);
				String alt = gladiator ? GladiatorAbilities.shiftId(slot) : HulkAbilityManager.shiftIdOf(slot);
				moves.add(MoveListOverlay.Row.of(MoveListOverlay.key(slot), Component.translatable(langName(gladiator, tap)),
						alt == null || alt.equals(tap) ? null : Component.translatable(langName(gladiator, alt))));
			}
			st.moveList(moves);
		} else if (shift) {
			// Shift held: the Shift moves' names, one line per key, stacked above the HUD (as before, now in the move-list look)
			List<MoveListOverlay.Row> moves = new ArrayList<>();
			for (AbilitySlot slot : VanillaHud.ORDER) {
				String id = gladiator ? GladiatorAbilities.shiftId(slot) : HulkAbilityManager.shiftIdOf(slot);
				if (id != null) {
					moves.add(MoveListOverlay.Row.of(MoveListOverlay.key(slot), Component.translatable(langName(gladiator, id)), null));
				}
			}
			if (!moves.isEmpty()) {
				MoveListOverlay.draw(g, st.x0 + st.width, st.top() - 4, moves);
			}
		}
		renderCentre(g, mc, player, s, now);
	}

	private static String langName(boolean gladiator, String id) {
		return gladiator ? GladiatorAbilities.langName(id) : "projecthero.hulk.ability." + id;
	}

	/** v0.16.4: a straight blend of two ARGB colours ({@code t} 0..1). */
	private static int lerpColour(int a, int b, float t) {
		t = Math.max(0.0f, Math.min(1.0f, t));
		int out = 0;
		for (int shift = 0; shift <= 24; shift += 8) {
			int ca = (a >>> shift) & 0xFF;
			int cb = (b >>> shift) & 0xFF;
			out |= (Math.round(ca + (cb - ca) * t) & 0xFF) << shift;
		}
		return out;
	}

	private static float frac(int cd, int max) {
		return cd <= 0 || max <= 0 ? 0f : Math.min(1f, cd / (float) max);
	}

	/**
	 * v0.16.4 (user): the rebuilt kit -- each key shows the tap move (or, while Shift is held, the Shift move) and the other
	 * one's cooldown as the durability bar. The leap row is HulkMovesClient's.
	 */
	private static void keys(VanillaHud.Stack st, Player player, HulkState s, long now, boolean shift) {
		boolean locked = s.rampaging(now) || s.combat.calming;
		for (int i = 0; i < VanillaHud.ORDER.length; i++) {
			AbilitySlot slot = VanillaHud.ORDER[i];
			String tap = HulkAbilityManager.abilityIdOf(slot);
			String alt = HulkAbilityManager.shiftIdOf(slot);
			String main = shift ? alt : tap;
			String other = shift ? tap : alt;
			boolean active = (slot == AbilitySlot.SLOT_5 && s.combat.holding) || (slot == AbilitySlot.SLOT_4 && s.combat.chargeUntil > now)
					|| (slot == AbilitySlot.SLOT_3 && (s.leapChargeStart > 0L || s.leaping));
			int cd = locked ? 0 : HulkAbilities.cooldownRemaining(player, main);
			float otherFrac = other.equals(main) || locked ? 0f
					: frac(HulkAbilities.cooldownRemaining(player, other), HulkAbilityManager.maxCooldown(other));
			st.slot(i, VanillaHud.keyLabel(slot), !locked, shift, frac(cd, HulkAbilityManager.maxCooldown(main)), VanillaHud.seconds(cd),
					otherFrac, active);
		}
	}

	/**
	 * v0.15.3: the Gladiator kit's six keys. Each shows the tap move's cooldown -- or, while Shift is held, the Shift move's --
	 * and the other one's as the durability bar.
	 */
	private static void gladiatorKeys(VanillaHud.Stack st, Player player, HulkState s, long now, boolean shift) {
		boolean locked = s.rampaging(now) || s.combat.calming;
		boolean busy = GladiatorAbilities.busy(player);
		for (int i = 0; i < VanillaHud.ORDER.length; i++) {
			AbilitySlot slot = VanillaHud.ORDER[i];
			String tap = GladiatorAbilities.tapId(slot);
			String alt = GladiatorAbilities.shiftId(slot);
			String main = shift ? alt : tap;
			String other = shift ? tap : alt;
			boolean active = switch (slot) {
				case SLOT_3 -> busy && !GladiatorAbilities.whirling(player) && !GladiatorAbilities.grappling(player);
				case SLOT_4 -> GladiatorAbilities.roaring(player);
				case SLOT_5 -> GladiatorAbilities.whirling(player) || GladiatorAbilities.grappling(player);
				case SLOT_6 -> GladiatorAbilities.axeAway(player) || GladiatorAbilities.hammerAway(player);
				default -> false;
			};
			int cd = locked ? 0 : HulkAbilities.cooldownRemaining(player, main);
			float otherFrac = locked || other == null || other.equals(main) ? 0f
					: frac(HulkAbilities.cooldownRemaining(player, other), GladiatorAbilities.maxCooldown(other));
			st.slot(i, VanillaHud.keyLabel(slot), !locked, shift, frac(cd, GladiatorAbilities.maxCooldown(main)), VanillaHud.seconds(cd),
					otherFrac, active);
		}
	}

	/**
	 * Mid-screen: the death sequence and the HULK SMASH wind-up (v0.16.4: no change / hold-H bar any more). (v0.16.4: the rampage
	 * warning and the ring minigame are {@code HulkRampageClient}'s; the keep-control prompt and the calm-down are gone.)
	 */
	private static void renderCentre(GuiGraphics g, Minecraft mc, Player player, HulkState s, long now) {
		int cx = g.guiWidth() / 2;
		int y = g.guiHeight() / 2 - 62; // above the crosshair, clear of the action bar and the hotbar
		if (s.dying()) {
			int col = (now / 8L) % 2L == 0L ? 0xFFFF5544 : 0xFF8A2A2A;
			g.drawCenteredString(mc.font, Component.translatable("hud.projecthero.hulk.dying").withStyle(ChatFormatting.BOLD), cx, y, col);
			return;
		}
		// v0.16.4 (user): "remove the bar that shows up while youre transforming ... remove the text above that bar aswell" --
		// no "changing" / hold-H progress bar or text; the change itself (pose, morph, sound) shows it
		if (Hulk.changing(s, now) || Hulk.holdProgress(player) > 0.0f || (!s.hulk && s.combat.strainStart != 0L)) {
			return;
		}
		if (com.projecthero.mod.hulk.HulkControl.warning(s, now)) {
			return; // the arrow string is up (HulkRampageClient)
		}
		float smash = HulkAbilities.hulkSmashCharge(player);
		if (smash > 0.0f) {
			int w = 120;
			g.drawCenteredString(mc.font, Component.translatable("hud.projecthero.hulk.hulk_smash_charge", Math.round(smash * 100.0f))
					.withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD), cx, y, 0xFF7CFF4A);
			g.fill(cx - w / 2, y + 11, cx + w / 2, y + 14, COLOR_BG);
			g.fill(cx - w / 2, y + 11, cx - w / 2 + Math.round(w * smash), y + 14, smash >= 1.0f ? 0xFFFFFFFF : 0xFF7CFF4A);
		}
	}
}
