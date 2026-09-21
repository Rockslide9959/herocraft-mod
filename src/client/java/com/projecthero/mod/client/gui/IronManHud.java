package com.projecthero.mod.client.gui;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.client.ModKeyBindings;
import com.projecthero.mod.ironman.data.TonyStarkState;
import com.projecthero.mod.ironman.item.IronManArmorItem;
import com.projecthero.mod.ironman.suit.IronManSuit;
import com.projecthero.mod.ironman.suit.IronManSuits;

import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * The Iron Man HUD (spec sections 29, 34, "changes 8"):
 * <ul>
 *   <li>while wearing a valid Iron Man <b>helmet</b> with the Tony Stark power: suit name, ENERGY /
 *       INTEGRITY bars (with 2-decimal percentages), altitude / speed, a crosshair target readout,
 *       and a keybind + name + cooldown line for each of the six suit abilities;</li>
 *   <li>while <b>not</b> wearing a suit but having a developed one: a "CALL ARMOUR [C]" prompt.</li>
 * </ul>
 * Every value comes from the target-synced {@link TonyStarkState} attachment or the client's own view.
 */
public final class IronManHud {
	private static final int X = 6;
	private static final int Y = 24;

	private IronManHud() {
	}

	public static void render(GuiGraphics g, DeltaTracker delta) {
		Minecraft client = Minecraft.getInstance();
		Player player = client.player;
		if (player == null || client.options.hideGui) {
			return;
		}
		TonyStarkState state = player.getAttachedOrElse(ModAttachments.TONY_STARK_STATE, null);
		if (state == null || !state.hasPower) {
			return;
		}
		long now = client.level != null ? client.level.getGameTime() : 0L;

		ItemStack helmet = player.getItemBySlot(EquipmentSlot.HEAD);
		if (!(helmet.getItem() instanceof IronManArmorItem helmetPiece)) {
			// no helmet: a Tony Stark player who isn't suited up can always try to call the armour
			// (the server decides whether anything is actually reachable).
			int py = Y;
			if (!wearingAnyIronMan(player)) {
				g.drawString(client.font, Component.translatable("hud.projecthero.ironman.call_armor",
						ModKeyBindings.ABILITY_SLOTS[5].getTranslatedKeyMessage()).withStyle(ChatFormatting.AQUA),
						X, py, 0xFF7FE9FF);
				py += 12;
			}
			long phoenixIn = state.phoenixReadyAt - now;
			if (phoenixIn > 0) {
				long secs = (phoenixIn + 19) / 20;
				g.drawString(client.font, Component.literal(String.format(java.util.Locale.ROOT,
						"PHOENIX: %02d:%02d", secs / 60, secs % 60)).withStyle(ChatFormatting.GOLD), X, py, 0xFFFFC24A);
			}
			return;
		}
		String suitId = helmetPiece.suitId();
		IronManSuit suit = IronManSuits.byId(suitId);
		if (suit == null) {
			return;
		}

		float energy = state.suitEnergy.getOrDefault(suitId, 0.0f);
		float energyFrac = suit.energyCapacity() <= 0 ? 0f : clamp(energy / suit.energyCapacity());
		float maxIntegrity = com.projecthero.mod.ironman.IronManEnergy.maxIntegrity(suitId);
		float integrity = state.suitIntegrity.getOrDefault(suitId, maxIntegrity);
		float integrityFrac = clamp(integrity / maxIntegrity);

		// v0.11.12, explicit user request: Mark 1's HUD is deliberately stripped down to just ENERGY,
		// INTEGRITY, and the ability/keybind list (plus its own mob-highlight duration) -- no altitude,
		// speed, coordinates, clock, target readout, or Protocol Phoenix status.
		boolean minimalHud = "mark_1".equals(suitId);

		int y = Y;
		g.drawString(client.font, Component.translatable(suit.nameKey())
				.withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD), X, y, 0xFF7FE9FF);
		y += 12;
		bar(g, client, X, y, "ENERGY", energyFrac,
				pct(energyFrac) + "  " + amount(energy, suit.energyCapacity()),
				energyFrac < 0.15f ? 0xFFFF7A3C : 0xFF6FA8FF);
		y += 12;
		bar(g, client, X, y, "INTEGRITY", integrityFrac,
				pct(integrityFrac) + "  " + amount(integrity, maxIntegrity),
				integrityFrac < 0.3f ? 0xFFFF5555 : 0xFF66E0A0);
		y += 12;

		// "changes 17": built-in air tank -- an AIR bar while it is below full (draining underwater or
		// refilling once out of the water).
		if (!minimalHud && suit.airTankSeconds() > 0 && state.suitAir < 0.999f) {
			float airFrac = clamp(state.suitAir);
			bar(g, client, X, y, "AIR", airFrac,
					String.format(java.util.Locale.ROOT, "%ds", Math.round(airFrac * suit.airTankSeconds())),
					airFrac < 0.25f ? 0xFFFF5555 : 0xFF5BC8FF);
			y += 12;
		}
		y += 2;

		// "changes 17": low-power / low-integrity alert -- blink a warning telling the pilot to disengage
		// and repair once either gauge drops below 35%.
		if (!minimalHud && (energyFrac < 0.35f || integrityFrac < 0.35f)) {
			if ((now % 20) < 13) {
				g.drawString(client.font, Component.translatable("hud.projecthero.ironman.repair_warning")
						.withStyle(ChatFormatting.RED, ChatFormatting.BOLD), X, y, 0xFFFF4444);
			}
			y += 12;
		}

		// Mark 1 flamethrower heat gauge ("changes 14"; "changes 17": also the Mark 7 weapon-wheel
		// flamethrower, whose bar is 50% bigger) -- only shown once it starts building up.
		float maxHeat = com.projecthero.mod.ironman.ability.IronManAbilities.flamethrowerMaxHeat(suit);
		if (!minimalHud && (hasAbility(suit, com.projecthero.mod.ironman.ability.IronManAbilities.FLAMETHROWER) || suit.hasWeaponWheel())
				&& state.flamethrowerHeat > 0.5f) {
			float heatFrac = clamp(state.flamethrowerHeat / maxHeat);
			bar(g, client, X, y, "HEAT", heatFrac,
					pct(heatFrac) + "  " + amount(state.flamethrowerHeat, maxHeat),
					heatFrac > 0.8f ? 0xFFFF5555 : 0xFFFFA23C);
			y += 12;
		}

		// Mark 1 flight burst remaining ("changes 15") -- 13 s cooldown once it ends.
		if (!minimalHud && state.timedFlightUntil > now) {
			float total = com.projecthero.mod.ironman.ability.IronManAbilities.TIMED_FLIGHT_TICKS;
			float flightFrac = clamp((state.timedFlightUntil - now) / total);
			bar(g, client, X, y, "FLIGHT", flightFrac,
					String.format(java.util.Locale.ROOT, "%.1fs", (state.timedFlightUntil - now) / 20.0),
					0xFF6FA8FF);
			y += 12;
		}

		// Mark 4 systems overload ("changes 15" / 30 s "changes 16") -- the whole suit is offline after
		// the wrist laser fires. Drawn as its own label line + a full-width bar below it so the long
		// "OVERLOADED SYSTEMS" text and the bar never overlap ("changes 16" fix).
		if (!minimalHud && state.overloadUntil > now) {
			float total = com.projecthero.mod.ironman.ability.IronManAbilities.OVERLOAD_TICKS;
			float overFrac = clamp((state.overloadUntil - now) / total);
			g.drawString(client.font, Component.translatable("hud.projecthero.ironman.overloaded")
					.withStyle(ChatFormatting.RED, ChatFormatting.BOLD), X, y, 0xFFFF5555);
			y += 10;
			wideBar(g, X, y, overFrac,
					String.format(java.util.Locale.ROOT, "%.1fs", (state.overloadUntil - now) / 20.0), 0xFFFF5555);
			y += 12;
		} else if (!minimalHud && state.wristLaserUntil > now) {
			float total = com.projecthero.mod.ironman.ability.IronManAbilities.WRIST_LASER_TICKS;
			float laserFrac = clamp((state.wristLaserUntil - now) / total);
			g.drawString(client.font, Component.translatable("hud.projecthero.ironman.ability.wrist_laser")
					.withStyle(ChatFormatting.RED), X, y, 0xFFFF3344);
			y += 10;
			wideBar(g, X, y, laserFrac,
					String.format(java.util.Locale.ROOT, "%.1fs", (state.wristLaserUntil - now) / 20.0), 0xFFFF3344);
			y += 12;
		}

		if (!minimalHud) {
			int altitude = (int) Math.round(player.getY());
			double speed = player.getDeltaMovement().horizontalDistance() * 20.0;
			g.drawString(client.font, Component.literal(String.format(java.util.Locale.ROOT,
					"ALT %dm   SPD %.0f m/s", altitude, speed)).withStyle(ChatFormatting.GRAY), X, y, 0xFFB8C0E0);
			y += 10;
			g.drawString(client.font, Component.literal(String.format(java.util.Locale.ROOT,
					"X %.0f  Y %.0f  Z %.0f", player.getX(), player.getY(), player.getZ()))
					.withStyle(ChatFormatting.GRAY), X, y, 0xFFB8C0E0);
			y += 10;
			// "changes 18": in-game clock + day count.
			if (client.level != null) {
				long dayTime = client.level.getDayTime();
				long tod = ((dayTime + 6000L) % 24000L + 24000L) % 24000L;
				int hh = (int) (tod / 1000L);
				int mm = (int) ((tod % 1000L) * 60L / 1000L);
				long day = dayTime / 24000L + 1L;
				g.drawString(client.font, Component.literal(String.format(java.util.Locale.ROOT,
						"TIME %02d:%02d   DAY %d", hh, mm, day)).withStyle(ChatFormatting.GRAY), X, y, 0xFFB8C0E0);
			}
			y += 12;
		}

		// Mark 2's altitude ceiling ("changes 12"): a warning band below the hard cutoff, then a clear
		// "systems frozen" readout once IronManSuitTicker has actually locked everything out.
		if (!minimalHud && suit.altitudeCeiling() > 0.0) {
			double ceiling = suit.altitudeCeiling();
			if (player.getY() >= ceiling) {
				g.drawString(client.font, Component.translatable("hud.projecthero.ironman.systems_frozen")
						.withStyle(ChatFormatting.RED, ChatFormatting.BOLD), X, y, 0xFFFF5555);
				y += 12;
			} else if (player.getY() >= ceiling - 20.0) {
				g.drawString(client.font, Component.translatable("hud.projecthero.ironman.altitude_warning",
						(int) ceiling).withStyle(ChatFormatting.GOLD), X, y, 0xFFFFC24A);
				y += 12;
			}
		}

		// six ability lines: [key]  Name   (cooldown)
		for (int i = 0; i < 6; i++) {
			// Read straight off the suit's own ability table rather than a fixed id list -- Mark 1 /
			// Mark 2 ("changes 12") put different abilities in several of these slots, and this used to
			// always show the original five marks' layout regardless of which suit was actually worn.
			String abilityId = suit.abilityInSlot(i + 1);
			if (abilityId == null) {
				continue;
			}
			// "changes 16": the Mark 7 weapon-wheel slot 3 shows whichever ability the wheel has bound.
			String effectiveId = com.projecthero.mod.ironman.ability.IronManAbilities.WEAPON_WHEEL_SLOT.equals(abilityId)
					? state.weaponWheelChoice : abilityId;
			Long readyAt = state.abilityReadyAt.get(suitId + "/" + effectiveId);
			int cd = readyAt == null ? 0 : (int) Math.max(0, readyAt - now);
			Component key = Component.literal("[").append(ModKeyBindings.ABILITY_SLOTS[i].getTranslatedKeyMessage())
					.append("] ").withStyle(ChatFormatting.GOLD);
			String nameKey = "hud.projecthero.ironman.ability." + effectiveId;
			if (com.projecthero.mod.ironman.ability.IronManAbilities.REPULSOR_BLAST.equals(abilityId)
					&& suit.repulsorWindupTicks() > 0) {
				nameKey = "hud.projecthero.ironman.ability.repulsor_blast_windup"; // Mark 2: no charged variant
			}
			if (com.projecthero.mod.ironman.ability.IronManAbilities.MOB_HIGHLIGHT_TOGGLE.equals(abilityId)
					&& suit.hasWristLaser()) {
				nameKey = "hud.projecthero.ironman.ability.mob_highlight_toggle.wristlaser"; // Mark 4: sneak + V
			}
			Component name = Component.translatable(nameKey)
					.withStyle(cd > 0 ? ChatFormatting.DARK_GRAY : ChatFormatting.WHITE);
			Component line = Component.empty().append(key).append(name);
			if (com.projecthero.mod.ironman.ability.IronManAbilities.WEAPON_WHEEL_SLOT.equals(abilityId)) {
				line = line.copy().append(Component.translatable("hud.projecthero.ironman.wheel_marker")
						.withStyle(ChatFormatting.DARK_AQUA));
			}
			if (cd > 0) {
				line = line.copy().append(Component.literal(String.format(java.util.Locale.ROOT, "  %.1fs", cd / 20.0))
						.withStyle(ChatFormatting.RED));
			}
			g.drawString(client.font, line, X, y, 0xFFCfcfe6);
			y += 10;
		}

		y += 3;
		if (!minimalHud) {
			// "changes 16": Mark 7 -- always-on entity highlight + which ability the weapon wheel bound to X.
			if (suit.passiveHighlightRange() > 0.0) {
				g.drawString(client.font, Component.translatable("hud.projecthero.ironman.passive_highlight",
						(int) suit.passiveHighlightRange()).withStyle(ChatFormatting.AQUA), X, y, 0xFF7FE9FF);
				y += 10;
			}
			if (suit.hasWeaponWheel()) {
				g.drawString(client.font, Component.translatable("hud.projecthero.ironman.wheel_bound",
						Component.translatable("hud.projecthero.ironman.ability." + state.weaponWheelChoice))
						.withStyle(ChatFormatting.GOLD), X, y, 0xFFFFC24A);
				y += 10;
			}
			// "changes 19": Mark 5 blades + faceplate state.
			if (player.getAttachedOrElse(ModAttachments.IRON_MAN_BLADES, false)) {
				g.drawString(client.font, Component.translatable("hud.projecthero.ironman.blades_active")
						.withStyle(ChatFormatting.AQUA), X, y, 0xFF7FE9FF);
				y += 10;
			}
			if (player.getAttachedOrElse(ModAttachments.IRON_MAN_FACEPLATE_OPEN, false)) {
				g.drawString(client.font, Component.translatable("hud.projecthero.ironman.faceplate_open")
						.withStyle(ChatFormatting.GRAY), X, y, 0xFFB8C0E0);
				y += 10;
			}
		}

		// "changes 13": mob-highlight toggle state + a Micro-Missiles ammo / reload readout. Kept for
		// Mark 1 even in the stripped-down HUD -- explicit user request, "show the remaining duration of
		// the mob highlight thats left on the heads up display" -- with its own countdown appended.
		if (hasAbility(suit, com.projecthero.mod.ironman.ability.IronManAbilities.MOB_HIGHLIGHT_TOGGLE)) {
			boolean on = state.mobHighlightOn;
			Component line = Component.translatable(on
					? "hud.projecthero.ironman.highlight_on" : "hud.projecthero.ironman.highlight_off")
					.withStyle(on ? ChatFormatting.AQUA : ChatFormatting.DARK_GRAY);
			if (on && minimalHud) {
				long until = com.projecthero.mod.ironman.ability.IronManAbilities.mark1MobHighlightUntil(state, suitId);
				if (until > now) {
					line = line.copy().append(Component.literal(String.format(java.util.Locale.ROOT,
							"  %.1fs", (until - now) / 20.0)).withStyle(ChatFormatting.GRAY));
				}
			}
			g.drawString(client.font, line, X, y, on ? 0xFF7FE9FF : 0xFF6A7286);
			y += 10;
		}
		if (!minimalHud && suit.missileCount() > 0) {
			Long mReadyAt = state.abilityReadyAt.get(suitId + "/"
					+ com.projecthero.mod.ironman.ability.IronManAbilities.MICRO_MISSILES);
			int mCd = mReadyAt == null ? 0 : (int) Math.max(0, mReadyAt - now);
			Component missiles = mCd > 0
					? Component.translatable("hud.projecthero.ironman.missiles_reload",
							String.format(java.util.Locale.ROOT, "%.1f", mCd / 20.0))
							.withStyle(ChatFormatting.RED)
					: Component.translatable("hud.projecthero.ironman.missiles", suit.missileCount())
							.withStyle(ChatFormatting.GOLD);
			g.drawString(client.font, missiles, X, y, mCd > 0 ? 0xFFFF5555 : 0xFFFFC24A);
			y += 10;
		}

		// "changes 17": Protocol Phoenix -- emergency-resurrection cooldown readout. v0.11.12, explicit
		// user request: never shown on Mark 1's HUD.
		if (!minimalHud) {
			long readyIn = state.phoenixReadyAt - now;
			if (readyIn > 0) {
				long secs = (readyIn + 19) / 20;
				g.drawString(client.font, Component.literal(String.format(java.util.Locale.ROOT,
						"PHOENIX: %02d:%02d", secs / 60, secs % 60)).withStyle(ChatFormatting.GOLD), X, y, 0xFFFFC24A);
			} else {
				g.drawString(client.font, Component.literal("PHOENIX: READY").withStyle(ChatFormatting.AQUA),
						X, y, 0xFF7FE9FF);
			}
			y += 10;
		}

		y += 2;
		if (!minimalHud) {
			HitResult hit = client.hitResult;
			if (hit instanceof EntityHitResult ehr && ehr.getEntity() instanceof LivingEntity target && target.isAlive()) {
				g.drawString(client.font, Component.literal("TARGET  ").withStyle(ChatFormatting.GOLD)
						.append(target.getName().copy().withStyle(ChatFormatting.WHITE))
						.append(Component.literal(String.format(java.util.Locale.ROOT, "  %.1fm", player.distanceTo(target)))
								.withStyle(ChatFormatting.GRAY)), X, y, 0xFFFFC24A);
				y += 10;
				if (state.targetingActive(now)) {
					drawReticle(g);
				}
			}
			if (energyFrac <= 0.001f || integrity <= 0.001f || state.overloadUntil > now) {
				g.drawString(client.font, Component.translatable("hud.projecthero.ironman.offline")
						.withStyle(ChatFormatting.RED), X, y, 0xFFFF5555);
			}
		}
	}

	private static boolean hasAbility(IronManSuit suit, String abilityId) {
		for (int slot = 1; slot <= 6; slot++) {
			if (abilityId.equals(suit.abilityInSlot(slot))) {
				return true;
			}
		}
		return false;
	}

	private static boolean wearingAnyIronMan(Player player) {
		for (EquipmentSlot slot : new EquipmentSlot[] {
				EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET }) {
			if (player.getItemBySlot(slot).getItem() instanceof IronManArmorItem) {
				return true;
			}
		}
		return false;
	}

	/** "76,67%" -- two decimals, comma separator (per the request). */
	private static String pct(float frac) {
		return String.format(java.util.Locale.ROOT, "%.2f", clamp(frac) * 100f).replace('.', ',') + "%";
	}

	/** "(7667 / 10000)" -- the real current/max values alongside the percentage ("changes 14"). */
	private static String amount(float current, float max) {
		return "(" + Math.round(current) + " / " + Math.round(max) + ")";
	}

	private static void drawReticle(GuiGraphics g) {
		int cx = g.guiWidth() / 2, cy = g.guiHeight() / 2, s = 9, c = 0xC0FFC24A;
		g.fill(cx - s, cy - s, cx - s + 5, cy - s + 1, c);
		g.fill(cx - s, cy - s, cx - s + 1, cy - s + 5, c);
		g.fill(cx + s - 5, cy - s, cx + s, cy - s + 1, c);
		g.fill(cx + s - 1, cy - s, cx + s, cy - s + 5, c);
		g.fill(cx - s, cy + s - 1, cx - s + 5, cy + s, c);
		g.fill(cx - s, cy + s - 5, cx - s + 1, cy + s, c);
		g.fill(cx + s - 5, cy + s - 1, cx + s, cy + s, c);
		g.fill(cx + s - 1, cy + s - 5, cx + s, cy + s, c);
	}

	private static float clamp(float f) {
		return Math.max(0f, Math.min(1f, f));
	}

	/** A label-less bar the full HUD width, with the value drawn to its right -- for the overload / laser timers. */
	private static void wideBar(GuiGraphics g, int x, int y, float frac, String valueText, int color) {
		int w = 120;
		g.fill(x - 1, y - 1, x + w + 1, y + 5, 0xFF2E2E44);
		g.fill(x, y, x + w, y + 4, 0xAA101018);
		g.fill(x, y, x + Math.round(w * clamp(frac)), y + 4, color);
		g.drawString(Minecraft.getInstance().font, valueText, x + w + 4, y - 1, 0xFFB8C0E0, false);
	}

	private static void bar(GuiGraphics g, Minecraft client, int x, int y, String label, float frac, String pctText, int color) {
		int w = 90;
		g.drawString(client.font, label, x, y - 1, 0xFF9AA6D0, false);
		int barX = x + 58;
		g.fill(barX - 1, y - 1, barX + w + 1, y + 5, 0xFF2E2E44);
		g.fill(barX, y, barX + w, y + 4, 0xAA101018);
		g.fill(barX, y, barX + Math.round(w * clamp(frac)), y + 4, color);
		g.drawString(client.font, pctText, barX + w + 4, y - 1, 0xFFB8C0E0, false);
	}
}
