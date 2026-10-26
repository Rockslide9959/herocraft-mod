package com.projecthero.mod.client.falcon;

import java.util.ArrayList;
import java.util.List;

import com.projecthero.mod.client.ModKeyBindings;
import com.projecthero.mod.client.gui.MoveListOverlay;
import com.projecthero.mod.falcon.Falcon;
import com.projecthero.mod.falcon.FalconAbilityManager;
import com.projecthero.mod.falcon.FalconConfig;
import com.projecthero.mod.falcon.data.FalconState;
import com.projecthero.mod.falcon.entity.RedwingEntity;
import com.projecthero.mod.hero.AbilitySlot;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1: the Falcon HUD (bottom-right), in the framed mono style of the reworked powers (like {@code QuirkHud}):
 * <pre>
 *   Falcon                      34 b/s     the title, and the speed while flying (JETS while X burns them)
 *   Thrust [=========-----] 63             the meter (red; pulses while the jets burn)
 *   Ammo   [=============-] 88             the Wrist Gun rounds (brass; red and flashing when nearly dry)
 *   [R] [G] [Z] [X] [C] [V]                the six keys: the plain move's cooldown, or the Shift move's while sneaking
 *                                          (a small bar under the box marks the Shift view); lit while that key's move runs
 * </pre>
 * With the Tactical Goggles on, the creature in the crosshair (out to 60 blocks) gets a readout: name, health, distance.
 * Hold Left-Alt for the move names.
 */
public final class FalconHud {
	private static final int BOX = 20;
	private static final int GAP = 2;
	private static final int MARGIN = 4;
	private static final int BAR_H = 4;

	private static final int BOX_BG = 0xD0080808;
	private static final int BORDER = 0xFF4A4A4A;
	private static final int ACCENT = 0xFFE8283C;
	private static final int KEY = 0xFFB4B4B4;
	private static final int NAME = 0xFFE2E2E2;
	private static final int HINT = 0xFF9A9A9A;
	private static final int COOLDOWN = 0xB0000000;
	private static final int BAR_BG = 0xC0141414;
	private static final int THRUST = 0xFFE8283C;
	private static final int THRUST_BOOST = 0xFFFF9A3A;
	private static final int AMMO = 0xFFE8B23A;
	private static final int AMMO_LOW = 0xFFFF4040;

	/** Boxes left to right: R G Z X C V (slots 1, 2, 4, 3, 6, 5), as on the other Hero-Tier HUDs. */
	private static final AbilitySlot[] SLOTS = { AbilitySlot.SLOT_1, AbilitySlot.SLOT_2, AbilitySlot.SLOT_4, AbilitySlot.SLOT_3,
			AbilitySlot.SLOT_6, AbilitySlot.SLOT_5 };

	/** Set only by the client screenshot harness: the HUD draws as if the player were sneaking. */
	public static volatile boolean forceShift;

	private FalconHud() {
	}

	public static void render(GuiGraphics g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null || mc.options.hideGui || !Falcon.hasPower(mc.player)) {
			return;
		}
		FalconState s = Falcon.peek(mc.player);
		if (s == null) {
			return;
		}
		var exp = mc.player.getAttachedOrElse(com.projecthero.mod.attachment.ModAttachments.EXPERIMENTAL_STATE, null);
		if (exp != null && !exp.activePower.isEmpty()) {
			return;
		}
		long now = mc.level.getGameTime();
		boolean shift = forceShift || mc.player.isShiftKeyDown();
		int totalW = BOX * SLOTS.length + GAP * (SLOTS.length - 1);
		int x0 = g.guiWidth() - MARGIN - totalW;
		int y0 = g.guiHeight() - MARGIN - BOX;
		if (x0 < g.guiWidth() / 2 + 91 + 2) {
			y0 = g.guiHeight() - 41 - BOX; // clear of the hotbar and food bar on a narrow window
		}

		// the two meters, Ammo under Thrust, their bars lined up
		Component thrustLabel = Component.translatable("hud.projecthero.falcon.thrust");
		Component ammoLabel = Component.translatable("hud.projecthero.falcon.ammo");
		int labelW = Math.max(mc.font.width(thrustLabel), mc.font.width(ammoLabel));
		int ammoY = y0 - 4 - BAR_H;
		int thrustY = ammoY - 10;
		float thrust = Mth.clamp(s.thrust, 0f, FalconConfig.THRUST_MAX);
		int thrustColour = s.boosting && s.flying ? ((now / 3) % 2 == 0 ? THRUST_BOOST : THRUST) : THRUST;
		meter(g, mc, x0, thrustY, totalW, labelW, thrustLabel, thrust / FalconConfig.THRUST_MAX, Math.round(thrust), thrustColour);
		float ammo = Mth.clamp(s.ammo, 0f, FalconConfig.AMMO_MAX);
		int ammoColour = ammo < FalconConfig.AMMO_MAX * 0.2f ? ((now / 4) % 2 == 0 ? AMMO_LOW : AMMO) : AMMO;
		meter(g, mc, x0, ammoY, totalW, labelW, ammoLabel, ammo / FalconConfig.AMMO_MAX, (int) Math.floor(ammo), ammoColour);
		int by = thrustY;

		// the title line, with the speed readout while flying (JETS while they burn)
		int ty = by - 13;
		g.drawString(mc.font, Component.translatable("hud.projecthero.falcon.title"), x0, ty, NAME, false);
		if (s.flying) {
			boolean jets = s.boosting;
			Component right = Component.translatable(jets ? "hud.projecthero.falcon.speed_jets" : "hud.projecthero.falcon.speed",
					(int) Math.round(FalconFlightClient.speedBps()));
			int rc = jets ? ((now / 3) % 2 == 0 ? THRUST_BOOST : 0xFFFFD08A) : HINT;
			g.drawString(mc.font, right, x0 + totalW - mc.font.width(right), ty, rc, false);
		}

		// the goggles line, above the title
		if (s.goggles) {
			ty -= 11;
			g.drawString(mc.font, Component.translatable("hud.projecthero.falcon.goggles", FalconClient.markedCount()), x0, ty, 0xFFFF6070, false);
		}

		// Left-Alt: the move list (plain and Shift per key), above the whole HUD
		if (MoveListOverlay.held()) {
			List<MoveListOverlay.Row> rows = new ArrayList<>();
			for (AbilitySlot slot : SLOTS) {
				String[] ids = FalconAbilityManager.idsOf(slot);
				if (ids.length > 0) {
					rows.add(MoveListOverlay.Row.of(keyLabel(slot), Component.translatable("projecthero.falcon.ability." + ids[0]),
							ids.length > 1 ? Component.translatable("projecthero.falcon.ability." + ids[1]) : null));
				}
			}
			MoveListOverlay.draw(g, x0 + totalW, ty, rows);
		}

		boolean redwingBusy = mc.level.getEntity(s.redwingId) instanceof RedwingEntity r && r.busy();
		for (int i = 0; i < SLOTS.length; i++) {
			AbilitySlot slot = SLOTS[i];
			int x = x0 + i * (BOX + GAP);
			String[] ids = FalconAbilityManager.idsOf(slot);
			String id = ids[Math.min(ids.length - 1, shift ? 1 : 0)];
			int cd = Falcon.cooldownRemaining(mc.player, id);
			int max = Math.max(1, FalconAbilityManager.maxCooldown(id));
			float cost = FalconAbilityManager.cost(id);
			boolean usable = s.thrust + 1.0e-3f >= cost && (!id.equals(com.projecthero.mod.falcon.FalconAbilities.GUNS) || s.ammo >= FalconConfig.AMMO_PER_SHOT);
			boolean active = switch (slot) {
				case SLOT_1 -> s.firingUntil > now;
				case SLOT_2 -> Falcon.shieldUp(mc.player);
				case SLOT_3 -> s.rollUntil > now || s.boosting || (s.animId == FalconState.ANIM_AFTERBURNER && now - s.animStart < 10);
				case SLOT_4 -> redwingBusy;
				case SLOT_5 -> s.goggles || s.carryId != -1;
				case SLOT_6 -> s.animId == FalconState.ANIM_DIVE && now - s.animStart < FalconConfig.DIVE_MAX_TICKS && s.flying;
				default -> false;
			};
			g.fill(x, y0, x + BOX, y0 + BOX, BOX_BG);
			g.renderOutline(x, y0, BOX, BOX, active ? ACCENT : BORDER);
			g.drawString(mc.font, keyLabel(slot), x + 2, y0 + 2, usable ? KEY : 0xFF5A5A5A, false);
			if (shift) {
				// the Shift view: a small bar along the bottom of the box
				g.fill(x + 3, y0 + BOX - 3, x + BOX - 3, y0 + BOX - 2, usable ? ACCENT : 0xFF5A5A5A);
			}
			if (cd > 0) {
				int h = (int) ((BOX - 2) * Math.min(1f, cd / (float) max));
				g.fill(x + 1, y0 + BOX - 1 - h, x + BOX - 1, y0 + BOX - 1, COOLDOWN);
				g.drawCenteredString(mc.font, String.valueOf((cd + 19) / 20), x + BOX / 2, y0 + BOX / 2 - 4, 0xFFFFFFFF);
			}
		}

		if (s.goggles) {
			gogglesReadout(g, mc);
		}
	}

	/** One labelled meter: {@code label}, the bar (filled to {@code frac}), the number on the right. */
	private static void meter(GuiGraphics g, Minecraft mc, int x0, int by, int totalW, int labelW, Component label, float frac, int number, int colour) {
		String value = String.valueOf(number);
		int valueW = mc.font.width("100");
		int barX = x0 + labelW + 4;
		int barW = Math.max(20, totalW - labelW - 4 - valueW - 3);
		g.drawString(mc.font, label, x0, by - 2, NAME, false);
		g.fill(barX - 1, by - 1, barX + barW + 1, by + BAR_H + 1, BORDER);
		g.fill(barX, by, barX + barW, by + BAR_H, BAR_BG);
		int fill = Math.round(barW * Mth.clamp(frac, 0f, 1f));
		if (fill > 0) {
			g.fill(barX, by, barX + fill, by + BAR_H, colour);
			g.fill(barX, by, barX + fill, by + 1, 0x60FFFFFF);
		}
		g.drawString(mc.font, value, x0 + totalW - mc.font.width(value), by - 2, NAME, false);
	}

	/** The Tactical Goggles: the creature nearest the crosshair (within ~4 degrees, 60 blocks): name, health, distance. */
	private static void gogglesReadout(GuiGraphics g, Minecraft mc) {
		Vec3 eye = mc.player.getEyePosition();
		Vec3 look = mc.player.getLookAngle();
		LivingEntity best = null;
		double bestDot = 0.9975;
		for (Entity e : mc.level.entitiesForRendering()) {
			if (!(e instanceof LivingEntity le) || e == mc.player || !le.isAlive() || e.distanceToSqr(mc.player) > FalconConfig.GOGGLES_RANGE * FalconConfig.GOGGLES_RANGE) {
				continue;
			}
			Vec3 to = e.position().add(0, e.getBbHeight() * 0.5, 0).subtract(eye);
			double dot = to.normalize().dot(look);
			if (dot > bestDot) {
				bestDot = dot;
				best = le;
			}
		}
		int cx = g.guiWidth() / 2;
		int cy = g.guiHeight() / 2 + 12;
		if (best == null) {
			return;
		}
		Component name = best.getDisplayName();
		String stats = String.format(java.util.Locale.ROOT, "%.0f / %.0f HP   %.0f m", best.getHealth(), best.getMaxHealth(), best.distanceTo(mc.player));
		int w = Math.min(220, Math.max(mc.font.width(name), mc.font.width(stats)) + 8);
		g.fill(cx - w / 2, cy, cx + w / 2, cy + 22, 0xA0100608);
		g.renderOutline(cx - w / 2, cy, w, 22, ACCENT);
		List<FormattedCharSequence> nl = mc.font.split(name, w - 6);
		g.drawCenteredString(mc.font, nl.isEmpty() ? FormattedCharSequence.EMPTY : nl.get(0), cx, cy + 2, ACCENT);
		g.drawCenteredString(mc.font, stats, cx, cy + 12, NAME);
	}

	private static String keyLabel(AbilitySlot slot) {
		String text = ModKeyBindings.ABILITY_SLOTS[slot.ordinal()].getTranslatedKeyMessage().getString();
		return text.length() > 2 ? text.substring(0, 2) : text;
	}
}
