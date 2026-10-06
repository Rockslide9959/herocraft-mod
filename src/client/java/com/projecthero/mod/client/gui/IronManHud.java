package com.projecthero.mod.client.gui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.lwjgl.glfw.GLFW;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.client.ModKeyBindings;
import com.projecthero.mod.ironman.IronManEnergy;
import com.projecthero.mod.ironman.ability.IronManAbilities;
import com.projecthero.mod.ironman.data.TonyStarkState;
import com.projecthero.mod.ironman.item.IronManArmorItem;
import com.projecthero.mod.ironman.item.IronManItems;
import com.projecthero.mod.ironman.suit.IronManSuit;
import com.projecthero.mod.ironman.suit.IronManSuits;
import com.projecthero.mod.ironman.ui.IronManUiLayout;
import com.projecthero.mod.ironman.ui.IronManUiLayout.Rect;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.EntityHitResult;

/**
 * The Iron Man helmet HUD (v0.14.21 redesign; spec sections 29 / 34, "changes 8" onwards).
 *
 * <ul>
 *   <li><b>Visor</b> -- while the faceplate is closed: a faint edge vignette + corner brackets (Mark I: a heavier
 *       eye-slit band in amber).</li>
 *   <li><b>Top-left block</b> -- suit name (+ small ALT / SPD), ENERGY and INTEGRITY Gauges with the exact values on
 *       their label line, one dim clock / position line, then <i>only the meters that matter right now</i>: AIR
 *       (underwater / refilling), HEAT, FLIGHT burst, OVERLOAD, WRIST LASER, MISSILE reload, plus the Mark II
 *       ceiling warning, a blinking SUIT CRITICAL banner and the SUIT OFFLINE line.</li>
 *   <li><b>Status chips</b> -- mob highlight, weapon-wheel binding, blades, entity scan, missiles ready, visor
 *       open, Protocol Phoenix cooldown.</li>
 *   <li><b>Ability strip</b> -- six slots (R G X Z V C) centred above the hotbar, read from the worn suit's
 *       {@code abilityInSlot()}: an icon, the bound key, and a cooldown sweep + seconds. Hold Left Alt to list
 *       the slot names above the strip.</li>
 *   <li>Unarmoured Tony Stark: the CALL ARMOUR chip and the Phoenix timer.</li>
 * </ul>
 * Mark I keeps its stripped-down retro HUD ({@code minimalHud}): amber cells, no telemetry / chips except its own
 * mob-highlight timer and the flamethrower heat. Every value comes from the synced {@link TonyStarkState}.
 */
public final class IronManHud {
	/** Cooldown sweep bookkeeping: key -> {readyAt, remaining when first seen}. */
	private static final Map<String, long[]> CD_SEEN = new HashMap<>();

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
		Font font = client.font;
		long now = client.level != null ? client.level.getGameTime() : 0L;
		int x = IronManUiLayout.HUD_X;
		int w = IronManUiLayout.HUD_W;

		ItemStack helmet = player.getItemBySlot(EquipmentSlot.HEAD);
		if (!(helmet.getItem() instanceof IronManArmorItem helmetPiece)) {
			// no helmet: a Tony Stark player who isn't suited up can always try to call the armour (the server
			// decides whether anything is actually reachable).
			int y = IronManUiLayout.HUD_Y;
			// v0.15.1: suit calling and Protocol Phoenix need the Stark Glasses (Stark Gear slot, Shift+N)
			boolean glasses = com.projecthero.mod.ironman.gear.StarkGear.canCall(player); // v0.15.4: or the Colantotte Bracelets
			if (!wearingAnyIronMan(player)) {
				String call = glasses
						? Component.translatable("hud.projecthero.ironman.call_armor",
								ModKeyBindings.ABILITY_SLOTS[5].getTranslatedKeyMessage()).getString()
						: Component.translatable("hud.projecthero.ironman.calling_offline").getString();
				IronManGui.chip(g, font, x, y, w, call, glasses ? IronManGui.CYAN : IronManGui.TEXT_DIM);
				y += 13;
			}
			long phoenixIn = state.phoenixReadyAt - now;
			if (phoenixIn > 0) {
				IronManGui.chip(g, font, x, y, w, Component.translatable("hud.projecthero.ironman.phoenix_cooldown",
						IronManUiLayout.mmss(phoenixIn)).getString(), IronManGui.GOLD);
			} else if (glasses) {
				IronManGui.chip(g, font, x, y, w, Component.translatable("hud.projecthero.ironman.phoenix_armed").getString(),
						IronManGui.GREEN);
			}
			return;
		}
		String suitId = helmetPiece.suitId();
		IronManSuit suit = IronManSuits.byId(suitId);
		if (suit == null) {
			return;
		}

		float energy = state.suitEnergy.getOrDefault(suitId, 0.0f);
		float energyFrac = suit.energyCapacity() <= 0 ? 0f : IronManUiLayout.clamp01(energy / suit.energyCapacity());
		float maxIntegrity = IronManEnergy.maxIntegrity(suitId);
		float integrity = state.suitIntegrity.getOrDefault(suitId, maxIntegrity);
		float integrityFrac = IronManUiLayout.clamp01(integrity / maxIntegrity);
		boolean overloaded = state.overloadUntil > now;
		boolean offline = energyFrac <= 0.001f || integrity <= 0.001f || overloaded;

		// v0.11.12, explicit user request: Mark 1's HUD is deliberately stripped down to ENERGY, INTEGRITY, the
		// ability list and its own mob-highlight duration -- no altitude, speed, clock, target readout or Phoenix.
		boolean minimalHud = "mark_1".equals(suitId);
		boolean visorClosed = !player.getAttachedOrElse(ModAttachments.IRON_MAN_FACEPLATE_OPEN, false);
		int accent = minimalHud ? IronManGui.AMBER : IronManGui.CYAN;
		int dim = minimalHud ? IronManGui.AMBER_DIM : IronManGui.TEXT_DIM;

		if (visorClosed) {
			visor(g, minimalHud, offline, now);
			com.projecthero.mod.client.ironman.IronManBattleDamage.renderVisorCracks(g, integrityFrac, minimalHud); // v0.14.29 agent F
		} else {
			// v0.14.26: the HUD lives in the faceplate -- lift it and the display goes dark (just a reminder chip)
			IronManGui.chip(g, font, x, IronManUiLayout.HUD_Y, w,
					Component.translatable("hud.projecthero.ironman.hud_off_visor").getString(), IronManGui.TEXT_DIM);
			return;
		}

		// Two passes: measure the text block, then draw a soft backdrop behind it (readable over a bright sky) and the block.
		int textBottom = IronManUiLayout.HUD_Y;
		boolean missilesReady = true;
		for (int pass = 0; pass < 2; pass++) {
			boolean draw = pass == 1;
			if (draw) {
				backdrop(g, x - 3, IronManUiLayout.HUD_Y - 3, w + 6, textBottom - IronManUiLayout.HUD_Y + 2, minimalHud);
			}
			int y = IronManUiLayout.HUD_Y;
			// ---- header: suit name, then (full HUD) ALT / SPD right-aligned
			Component name = Component.translatable(suit.nameKey()).withStyle(s -> s.withBold(true));
			if (draw) {
				g.drawString(font, name, x, y, accent, true);
			}
			if (!minimalHud) {
				double speed = player.getDeltaMovement().horizontalDistance() * 20.0;
				String tele = Component.translatable("hud.projecthero.ironman.alt_spd",
						(int) Math.round(player.getY()), (int) Math.round(speed)).getString();
				String t = IronManGui.fit(font, tele, w - font.width(name) - 8);
				if (draw) {
					g.drawString(font, t, x + w - font.width(t), y, IronManGui.TEXT_DIM, false);
				}
			}
			y += 12;

			// ---- ENERGY / INTEGRITY Gauges: exact values on the label line
			y = meter(draw, g, font, x, y, w, minimalHud, Component.translatable("hud.projecthero.ironman.energy_short").getString(),
					IronManUiLayout.pct(energyFrac) + " " + IronManUiLayout.amount(energy, suit.energyCapacity()),
					energyFrac, energyFrac < 0.15f ? IronManGui.ORANGE : IronManGui.BLUE);
			y = meter(draw, g, font, x, y, w, minimalHud, Component.translatable("hud.projecthero.ironman.integrity_short").getString(),
					IronManUiLayout.pct(integrityFrac) + " " + IronManUiLayout.amount(integrity, maxIntegrity),
					integrityFrac, integrityFrac < 0.3f ? IronManGui.RED : IronManGui.GREEN);

			// ---- one dim clock + position line ("changes 18" clock)
			if (!minimalHud && client.level != null) {
				long dayTime = client.level.getDayTime();
				long tod = ((dayTime + 6000L) % 24000L + 24000L) % 24000L;
				String line = Component.translatable("hud.projecthero.ironman.clock_pos",
						String.format(java.util.Locale.ROOT, "%02d:%02d", tod / 1000L, (tod % 1000L) * 60L / 1000L),
						dayTime / 24000L + 1L, (int) Math.floor(player.getX()), (int) Math.floor(player.getZ())).getString();
				if (draw) {
					g.drawString(font, IronManGui.fit(font, line, w), x, y, IronManGui.TEXT_DIM, false);
				}
				y += 11;
			}

			// ---- contextual meters, only while relevant
			if (!minimalHud && suit.airTankSeconds() > 0 && state.suitAir < 0.999f) {
				float airFrac = IronManUiLayout.clamp01(state.suitAir);
				y = meter(draw, g, font, x, y, w, false, Component.translatable("hud.projecthero.ironman.air").getString(),
						Math.round(airFrac * suit.airTankSeconds()) + "s", airFrac,
						airFrac < 0.25f ? IronManGui.RED : 0xFF5BC8FF);
			}
			float maxHeat = IronManAbilities.flamethrowerMaxHeat(suit);
			if ((hasAbility(suit, IronManAbilities.FLAMETHROWER) || suit.hasWeaponWheel()) && state.flamethrowerHeat > 0.5f) {
				float heatFrac = IronManUiLayout.clamp01(state.flamethrowerHeat / maxHeat);
				y = meter(draw, g, font, x, y, w, minimalHud, Component.translatable("hud.projecthero.ironman.heat").getString(),
						IronManUiLayout.pct(heatFrac) + " " + IronManUiLayout.amount(state.flamethrowerHeat, maxHeat),
						heatFrac, heatFrac > 0.8f ? IronManGui.RED : 0xFFFFA23C);
			}
			if (!minimalHud && state.timedFlightUntil > now) {
				long left = state.timedFlightUntil - now;
				y = meter(draw, g, font, x, y, w, false, Component.translatable("hud.projecthero.ironman.flight").getString(),
						IronManUiLayout.secs(left), left / (float) IronManAbilities.TIMED_FLIGHT_TICKS, IronManGui.BLUE);
			}
			if (!minimalHud && overloaded) {
				long left = state.overloadUntil - now;
				y = meter(draw, g, font, x, y, w, false, Component.translatable("hud.projecthero.ironman.overloaded").getString(),
						IronManUiLayout.secs(left), left / (float) IronManAbilities.OVERLOAD_TICKS, IronManGui.RED);
			} else if (!minimalHud && state.wristLaserUntil > now) {
				long left = state.wristLaserUntil - now;
				y = meter(draw, g, font, x, y, w, false, Component.translatable("hud.projecthero.ironman.ability.wrist_laser").getString(),
						IronManUiLayout.secs(left), left / (float) IronManAbilities.WRIST_LASER_TICKS, 0xFFFF3344);
			}
			long surgeLeft = com.projecthero.mod.ironman.ability.IronManMark6.surgeUntil(state) - now;
			if (!minimalHud && surgeLeft > 0 && com.projecthero.mod.ironman.ability.IronManMark6.SUIT_ID.equals(suitId)) {
				// v0.14.29: the Mark 6 Arc Reactor Surge timer
				y = meter(draw, g, font, x, y, w, false, Component.translatable("hud.projecthero.ironman.mk6_surge").getString(),
						IronManUiLayout.secs(surgeLeft), surgeLeft / (float) com.projecthero.mod.ironman.ability.IronManMark6.SURGE_TICKS, IronManGui.CYAN);
			}
			missilesReady = true;
			if (!minimalHud && suit.missileCount() > 0 && !com.projecthero.mod.ironman.ability.IronManMark3.isKitSuit(suitId)) {
				String key = suitId + "/" + IronManAbilities.MICRO_MISSILES;
				Long readyAt = state.abilityReadyAt.get(key);
				long left = readyAt == null ? 0 : readyAt - now;
				if (left > 0) {
					missilesReady = false;
					y = meter(draw, g, font, x, y, w, false, Component.translatable("hud.projecthero.ironman.missiles_short").getString(),
							Component.translatable("hud.projecthero.ironman.reload", IronManUiLayout.secs(left)).getString(),
							1f - cooldownFrac(key, readyAt, now), IronManGui.GOLD);
				}
			}

			// ---- warnings (wrapped to the block width; nothing may run off the side)
			if (!minimalHud && suit.altitudeCeiling() > 0.0) {
				double ceiling = suit.altitudeCeiling();
				if (player.getY() >= ceiling) {
					y = wrapped(draw, g, font, x, y, w, Component.translatable("hud.projecthero.ironman.systems_frozen"), IronManGui.RED);
				} else if (player.getY() >= ceiling - 20.0) {
					y = wrapped(draw, g, font, x, y, w, Component.translatable("hud.projecthero.ironman.altitude_warning", (int) ceiling),
							IronManGui.GOLD);
				}
			}
			if (!minimalHud && (energyFrac < 0.35f || integrityFrac < 0.35f) && !offline) {
				if (draw && (now % 20) < 13) {
					// only the integrity half of the warning says REPAIR; a suit that is merely low on power says RECHARGE
					String crit = IronManGui.fit(font, Component.translatable(integrityFrac < 0.35f
							? "hud.projecthero.ironman.critical_short" : "hud.projecthero.ironman.power_critical_short"), w - 6);
					g.fill(x, y, x + font.width(crit) + 6, y + 11, 0xC0400808);
					g.renderOutline(x, y, font.width(crit) + 6, 11, IronManGui.RED);
					g.drawString(font, crit, x + 3, y + 2, 0xFFFF7070, false);
				}
				y += 13;
			}
			if (!minimalHud && offline) {
				y = wrapped(draw, g, font, x, y, w, Component.translatable("hud.projecthero.ironman.offline"), IronManGui.RED);
			}

			textBottom = y;
		}
		int y = textBottom;

		// ---- status chips
		List<String> chipText = new ArrayList<>();
		List<Integer> chipColor = new ArrayList<>();
		if (hasAbility(suit, IronManAbilities.MOB_HIGHLIGHT_TOGGLE)) {
			boolean on = state.mobHighlightOn;
			String t = Component.translatable(on ? "hud.projecthero.ironman.chip.highlight_on"
					: "hud.projecthero.ironman.chip.highlight_off").getString();
			if (on && minimalHud) {
				long until = IronManAbilities.mark1MobHighlightUntil(state, suitId);
				if (until > now) {
					t += " " + IronManUiLayout.secs(until - now);
				}
			}
			chipText.add(t);
			chipColor.add(on ? accent : IronManGui.TEXT_MUTED);
		}
		if (!minimalHud) {
			if (suit.passiveHighlightRange() > 0.0) {
				chipText.add(Component.translatable("hud.projecthero.ironman.chip.scan", (int) suit.passiveHighlightRange()).getString());
				chipColor.add(IronManGui.CYAN_DIM);
			}
			if (suit.hasWeaponWheel()) {
				chipText.add(Component.translatable("hud.projecthero.ironman.chip.wheel",
						Component.translatable("hud.projecthero.ironman.ability." + state.weaponWheelChoice)).getString());
				chipColor.add(IronManGui.GOLD);
			}
			if (com.projecthero.mod.ironman.ability.IronManMark3.shieldOn(state, suitId)) {
				chipText.add(Component.translatable("hud.projecthero.ironman.chip.mk3_shield").getString()); // v0.14.27
				chipColor.add(IronManGui.CYAN);
			}
			if (player.getAttachedOrElse(ModAttachments.IRON_MAN_BLADES, false)) {
				chipText.add(Component.translatable("hud.projecthero.ironman.chip.blades").getString());
				chipColor.add(IronManGui.CYAN);
			}
			if (suit.missileCount() > 0 && !com.projecthero.mod.ironman.ability.IronManMark3.isKitSuit(suitId) && missilesReady) {
				chipText.add(Component.translatable("hud.projecthero.ironman.missiles", suit.missileCount()).getString());
				chipColor.add(IronManGui.GOLD);
			}
			if (!visorClosed) {
				chipText.add(Component.translatable("hud.projecthero.ironman.chip.visor_open").getString());
				chipColor.add(IronManGui.TEXT_DIM);
			}
			long phoenixIn = state.phoenixReadyAt - now;
			if (phoenixIn > 0) {
				chipText.add(Component.translatable("hud.projecthero.ironman.phoenix_cooldown",
						IronManUiLayout.mmss(phoenixIn)).getString());
				chipColor.add(IronManGui.GOLD);
			}
		}
		if (!chipText.isEmpty()) {
			int[] widths = new int[chipText.size()];
			for (int i = 0; i < widths.length; i++) {
				widths[i] = IronManGui.chipWidth(font, chipText.get(i), w);
			}
			List<Rect> rects = IronManUiLayout.flowChips(widths, x, y + 1, w, 2, 11);
			for (int i = 0; i < rects.size(); i++) {
				Rect r = rects.get(i);
				IronManGui.chip(g, font, r.x(), r.y(), r.w(), chipText.get(i), chipColor.get(i));
			}
		}

		// ---- target readout just under the crosshair (helmet feel; replaces the old "TARGET" line)
		// v0.14.26: the target scanner (Mark II+, visor closed): name / health / armour / distance / threat of the target
		// under the crosshair out to 100 blocks (Mark II: 25), or the Mark III targeting lock
		if (!minimalHud && visorClosed) {
			com.projecthero.mod.client.ironman.IronManTargetScanner.renderPanel(g, font, player, suit, delta.getGameTimeDeltaPartialTick(false));
		}

		abilityStrip(g, font, player, state, suit, suitId, now, minimalHud, offline);
	}

	// ------------------------------------------------------------------ pieces

	/** One Gauge row (label + value line, 3 px bar). Returns the next y. */
	private static int meter(boolean draw, GuiGraphics g, Font font, int x, int y, int w, boolean retro, String label, String value,
			float frac, int fill) {
		if (!draw) {
			return y + IronManUiLayout.GAUGE_ROW_H;
		}
		if (retro) {
			g.drawString(font, label, x, y, IronManGui.AMBER_DIM, false);
			String v = IronManGui.fit(font, value, w - font.width(label) - 6);
			g.drawString(font, v, x + w - font.width(v), y, IronManGui.AMBER, false);
			IronManGui.cells(g, x, y + 10, w, frac, IronManGui.AMBER, IronManGui.AMBER_BG);
		} else {
			IronManGui.gauge(g, font, x, y, w, label, IronManGui.TEXT_DIM, value, IronManGui.TEXT, frac, fill);
		}
		return y + IronManUiLayout.GAUGE_ROW_H;
	}

	private static int wrapped(boolean draw, GuiGraphics g, Font font, int x, int y, int w, Component text, int color) {
		for (FormattedCharSequence line : font.split(text, w)) {
			if (draw) {
				g.drawString(font, line, x, y, color, true);
			}
			y += 10;
		}
		return y + 2;
	}

	/** A soft dark plate behind the text block so it stays readable over a bright sky (Mark I: warm tint). */
	private static void backdrop(GuiGraphics g, int x, int y, int w, int h, boolean retro) {
		if (h <= 0) {
			return;
		}
		int c = retro ? 0x8C140C04 : 0x8C050B12;
		g.fill(x, y, x + w, y + h, c);
		g.fill(x, y + h, x + w, y + h + 1, retro ? IronManGui.alpha(IronManGui.AMBER_DIM, 0.6f) : IronManGui.alpha(IronManGui.CYAN_DIM, 0.6f));
	}

	/** The faint visor frame while the faceplate is closed. */
	private static void visor(GuiGraphics g, boolean retro, boolean offline, long now) {
		int sw = g.guiWidth();
		int sh = g.guiHeight();
		int tint = retro ? 0x00140A00 : 0x00041826;
		int edge = retro ? 0x70000000 : 0x38000000;
		int band = retro ? Math.max(14, sh / 9) : Math.max(10, sh / 14);
		g.fillGradient(0, 0, sw, band, edge | tint, tint);
		g.fillGradient(0, sh - band, sw, sh, tint, edge | tint);
		int side = retro ? Math.max(18, sw / 12) : Math.max(10, sw / 22);
		int steps = 8;
		for (int i = 0; i < steps; i++) {
			int a = Math.round(((edge >>> 24) & 0xFF) * (1f - i / (float) steps));
			int c = (a << 24) | tint;
			int x0 = side * i / steps;
			int x1 = side * (i + 1) / steps;
			g.fill(x0, 0, x1, sh, c);
			g.fill(sw - x1, 0, sw - x0, sh, c);
		}
		if (!retro) {
			int c = offline && (now % 20) < 10 ? IronManGui.alpha(IronManGui.RED, 0.5f) : IronManGui.CYAN_FAINT;
			IronManGui.brackets(g, 3, 3, sw - 6, sh - 6, 16, c);
			// a small fixed reticle ring ticks around the crosshair
			int cx = sw / 2;
			int cy = sh / 2;
			g.fill(cx - 12, cy, cx - 8, cy + 1, IronManGui.CYAN_FAINT);
			g.fill(cx + 9, cy, cx + 13, cy + 1, IronManGui.CYAN_FAINT);
		}
	}

	/** Fraction of the cooldown still to run (1 = just started), tracking each new ready-at the first time it is seen. */
	private static float cooldownFrac(String key, Long readyAt, long now) {
		if (readyAt == null || readyAt <= now) {
			CD_SEEN.remove(key);
			return 0f;
		}
		long[] seen = CD_SEEN.get(key);
		if (seen == null || seen[0] != readyAt) {
			seen = new long[] { readyAt, readyAt - now };
			CD_SEEN.put(key, seen);
		}
		return IronManUiLayout.cooldownFraction(readyAt, seen[1], now);
	}

	private static void abilityStrip(GuiGraphics g, Font font, Player player, TonyStarkState state, IronManSuit suit,
			String suitId, long now, boolean retro, boolean offline) {
		Rect[] slots = IronManUiLayout.abilityStrip(g.guiWidth(), g.guiHeight());
		boolean expanded = GLFW.glfwGetKey(Minecraft.getInstance().getWindow().getWindow(), GLFW.GLFW_KEY_LEFT_ALT) == GLFW.GLFW_PRESS;
		int accent = retro ? IronManGui.AMBER : IronManGui.CYAN;
		List<String> names = new ArrayList<>();
		for (int i = 0; i < 6; i++) {
			Rect r = slots[i];
			String abilityId = suit.abilityInSlot(i + 1);
			if (abilityId == null) {
				g.fill(r.x(), r.y(), r.right(), r.bottom(), 0x60060A10);
				g.renderOutline(r.x(), r.y(), r.w(), r.h(), 0x40303A48);
				continue;
			}
			boolean wheel = IronManAbilities.WEAPON_WHEEL_SLOT.equals(abilityId);
			// v0.14.27: the Mark III G box shows the weapon picked on its wheel (and that weapon's cooldown)
			boolean mk3Arsenal = com.projecthero.mod.ironman.ability.IronManMark3.ARSENAL.equals(abilityId);
			String effectiveId = wheel ? state.weaponWheelChoice
					: mk3Arsenal ? com.projecthero.mod.ironman.ability.IronManMark3.selectedWeapon(state, suitId) : abilityId;
			String cdKey = suitId + "/" + com.projecthero.mod.ironman.ability.IronManMark6.hudCooldownId(effectiveId); // v0.14.29: G shields = the barrier cooldown
			Long readyAt = state.abilityReadyAt.get(cdKey);
			long left = readyAt == null ? 0 : Math.max(0, readyAt - now);
			boolean active = isActive(player, state, abilityId, now)
					|| (com.projecthero.mod.ironman.ability.IronManMark3.WHEEL.equals(abilityId)
							&& com.projecthero.mod.ironman.ability.IronManMark3.shieldOn(state, suitId)); // v0.14.29: Mark 4 shield

			int bg = retro ? 0xC0140C04 : 0xC0081018;
			g.fill(r.x(), r.y(), r.right(), r.bottom(), bg);
			int border = offline ? 0xFF6A2020 : active ? IronManGui.GOLD : retro ? IronManGui.AMBER_DIM : IronManGui.PANEL_BORDER;
			g.renderOutline(r.x(), r.y(), r.w(), r.h(), border);
			if (!offline && left <= 0) {
				g.fill(r.x() + 1, r.bottom() - 2, r.right() - 1, r.bottom() - 1, IronManGui.alpha(accent, active ? 0.9f : 0.45f));
			}
			ItemStack icon = icon(effectiveId, suit);
			int ix = r.x() + (r.w() - 16) / 2;
			int iy = r.y() + 2;
			if (!icon.isEmpty()) {
				g.renderFakeItem(icon, ix, iy);
			}
			g.pose().pushPose();
			g.pose().translate(0, 0, 200); // over the item icon
			if (left > 0) {
				float frac = cooldownFrac(cdKey, readyAt, now);
				int coverH = Math.round((r.h() - 2) * frac);
				g.fill(r.x() + 1, r.bottom() - 1 - coverH, r.right() - 1, r.bottom() - 1, 0xB0000000);
				String s = String.valueOf(IronManUiLayout.ceilSecs(left));
				g.drawString(font, s, r.x() + (r.w() - font.width(s)) / 2, r.y() + 7, 0xFFFFFFFF, true);
			} else if (offline) {
				g.fill(r.x() + 1, r.y() + 1, r.right() - 1, r.bottom() - 1, 0x90200000);
			} else if (com.projecthero.mod.ironman.ability.IronManMark6.SURGE.equals(abilityId) && com.projecthero.mod.ironman.ability.IronManMark6.surging(state, now)) {
				// v0.14.29: the Arc Reactor Surge's remaining time, in cyan, on its X box
				String s = String.valueOf(IronManUiLayout.ceilSecs(com.projecthero.mod.ironman.ability.IronManMark6.surgeUntil(state) - now));
				g.drawString(font, s, r.x() + (r.w() - font.width(s)) / 2, r.y() + 7, IronManGui.CYAN, true);
			} else if (IronManAbilities.TIMED_FLIGHT.equals(abilityId) && state.timedFlightUntil > now) {
				// v0.14.27: the flight burst's remaining time, in gold, on its own key box
				String s = String.valueOf(IronManUiLayout.ceilSecs(state.timedFlightUntil - now));
				g.drawString(font, s, r.x() + (r.w() - font.width(s)) / 2, r.y() + 7, IronManGui.GOLD, true);
			}
			String key = keyLabel(i);
			g.drawString(font, key, r.x() + 2, r.y() + 1, retro ? IronManGui.AMBER : IronManGui.GOLD, true);
			if (wheel || mk3Arsenal) {
				// a small gold corner notch marks the weapon-wheel slot
				g.fill(r.right() - 5, r.y() + 1, r.right() - 1, r.y() + 2, IronManGui.GOLD);
				g.fill(r.right() - 2, r.y() + 1, r.right() - 1, r.y() + 5, IronManGui.GOLD);
			}
			g.pose().popPose();
			if (expanded) {
				names.add("[" + keyLabel(i) + "] " + Component.translatable(nameKey(suit, abilityId, effectiveId)).getString()
						+ (left > 0 ? "  " + IronManUiLayout.secs(left) : ""));
			}
		}
		if (expanded && !names.isEmpty()) {
			int sx = slots[0].x();
			int sw = slots[5].right() - sx;
			int top = slots[0].y() - 4 - names.size() * 10;
			g.fill(sx - 2, top - 2, sx + sw + 2, slots[0].y() - 2, 0xC0060A10);
			for (int i = 0; i < names.size(); i++) {
				g.drawString(font, IronManGui.fit(font, names.get(i), sw), sx, top + i * 10, IronManGui.TEXT, false);
			}
		}
	}

	private static boolean isActive(Player player, TonyStarkState state, String abilityId, long now) {
		if (IronManAbilities.MOB_HIGHLIGHT_TOGGLE.equals(abilityId)) {
			return state.mobHighlightOn;
		}
		if (IronManAbilities.BLADE.equals(abilityId)) {
			return player.getAttachedOrElse(ModAttachments.IRON_MAN_BLADES, false);
		}
		if (IronManAbilities.TIMED_FLIGHT.equals(abilityId)) {
			return state.timedFlightUntil > now;
		}
		// v0.14.29: Mark 6 surge running / Sneak+V highlight on
		if (com.projecthero.mod.ironman.ability.IronManMark6.SURGE.equals(abilityId)) {
			return com.projecthero.mod.ironman.ability.IronManMark6.surging(state, now);
		}
		if (com.projecthero.mod.ironman.ability.IronManMark6.BARRAGE.equals(abilityId)) {
			return state.mobHighlightOn;
		}
		if (com.projecthero.mod.ironman.ability.IronManMark3.WHEEL.equals(abilityId)) {
			return com.projecthero.mod.ironman.ability.IronManMark3.shieldOn(state); // v0.14.27: Sneak+V shield is up
		}
		return false;
	}

	/** The HUD name key for a slot -- the same per-suit variants the old text list used. */
	static String nameKey(IronManSuit suit, String abilityId, String effectiveId) {
		if (IronManAbilities.REPULSOR_BLAST.equals(abilityId) && suit.hasDash()) {
			return "hud.projecthero.ironman.ability.repulsor_blast.dash"; // v0.14.27: hold = charged, Shift = dash
		}
		if (IronManAbilities.REPULSOR_BLAST.equals(abilityId) && suit.repulsorWindupTicks() > 0) {
			return "hud.projecthero.ironman.ability.repulsor_blast_windup"; // Mark 2: no charged variant
		}
		if (IronManAbilities.MOB_HIGHLIGHT_TOGGLE.equals(abilityId) && suit.hasWristLaser()) {
			return "hud.projecthero.ironman.ability.mob_highlight_toggle.wristlaser"; // Mark 4: sneak + V
		}
		return "hud.projecthero.ironman.ability." + effectiveId;
	}

	/** A recognisable item icon for each ability. */
	static ItemStack icon(String abilityId, IronManSuit suit) {
		return switch (abilityId) {
			case IronManAbilities.REPULSOR_BLAST, IronManAbilities.CHARGED_REPULSOR -> new ItemStack(IronManItems.REPULSOR);
			case IronManAbilities.REPULSOR_BARRIER -> new ItemStack(Items.SHIELD);
			case IronManAbilities.MICRO_MISSILES -> new ItemStack(Items.FIREWORK_STAR);
			case IronManAbilities.HOMING_MISSILES -> new ItemStack(Items.TARGET); // v0.14.21 round two
			case IronManAbilities.UNIBEAM -> new ItemStack(IronManItems.ARC_REACTOR);
			case IronManAbilities.SUIT_TOGGLE -> suit.armor(net.minecraft.world.item.ArmorItem.Type.HELMET) != null
					? new ItemStack(suit.armor(net.minecraft.world.item.ArmorItem.Type.HELMET)) : new ItemStack(Items.ARMOR_STAND);
			case IronManAbilities.STRONG_PUNCH -> new ItemStack(Items.PISTON);
			case IronManAbilities.FLAMETHROWER -> new ItemStack(Items.FIRE_CHARGE);
			case IronManAbilities.ROCKET -> new ItemStack(Items.FIREWORK_ROCKET);
			case IronManAbilities.FLARE -> new ItemStack(Items.GLOWSTONE_DUST);
			case IronManAbilities.SONIC_CLAP -> new ItemStack(Items.BELL); // v0.14.27
			case IronManAbilities.BLADE -> new ItemStack(Items.IRON_SWORD);
			case IronManAbilities.MOB_HIGHLIGHT_TOGGLE -> new ItemStack(Items.ENDER_EYE);
			case IronManAbilities.TIMED_FLIGHT -> new ItemStack(Items.FEATHER);
			case IronManAbilities.WRIST_LASER -> new ItemStack(Items.BLAZE_ROD);
			case IronManAbilities.WEAPON_WHEEL -> new ItemStack(Items.COMPASS);
			case IronManAbilities.SUPERSONIC_FLIGHT -> new ItemStack(Items.ELYTRA);
			case IronManAbilities.ENTITY_GLOW_TOGGLE -> new ItemStack(Items.GLOW_INK_SAC);
			// v0.14.27: Mark III kit
			case com.projecthero.mod.ironman.ability.IronManMark3.ROCKETS -> new ItemStack(Items.FIREWORK_ROCKET);
			case com.projecthero.mod.ironman.ability.IronManMark3.MINIGUN -> new ItemStack(Items.CROSSBOW);
			case com.projecthero.mod.ironman.ability.IronManMark3.MICRO_MISSILES -> new ItemStack(Items.TARGET);
			case com.projecthero.mod.ironman.ability.IronManMark3.ARSENAL -> new ItemStack(Items.FIREWORK_ROCKET);
			case com.projecthero.mod.ironman.ability.IronManMark3.FLARES -> new ItemStack(Items.GLOWSTONE_DUST);
			case com.projecthero.mod.ironman.ability.IronManMark3.UNIBEAM -> new ItemStack(IronManItems.ARC_REACTOR);
			case com.projecthero.mod.ironman.ability.IronManMark3.WHEEL -> new ItemStack(Items.COMPASS);
			// v0.14.29: Mark 6 / Mark 7 kits
			case com.projecthero.mod.ironman.ability.IronManMark6.SHIELD, com.projecthero.mod.ironman.ability.IronManMark7.SHIELD -> new ItemStack(Items.SHIELD);
			case com.projecthero.mod.ironman.ability.IronManMark6.SURGE -> new ItemStack(Items.NETHER_STAR);
			case com.projecthero.mod.ironman.ability.IronManMark6.UNIBEAM, com.projecthero.mod.ironman.ability.IronManMark7.UNIBEAM -> new ItemStack(IronManItems.ARC_REACTOR);
			case com.projecthero.mod.ironman.ability.IronManMark6.BARRAGE -> new ItemStack(Items.TARGET);
			default -> ItemStack.EMPTY;
		};
	}

	/** The key actually bound to slot {@code i} (tracks rebinds), at most two characters. */
	private static String keyLabel(int i) {
		String text = ModKeyBindings.ABILITY_SLOTS[i].getTranslatedKeyMessage().getString();
		return text.length() > 2 ? text.substring(0, 2) : text;
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
}
