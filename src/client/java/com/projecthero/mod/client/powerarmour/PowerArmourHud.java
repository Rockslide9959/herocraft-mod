package com.projecthero.mod.client.powerarmour;

import java.util.ArrayList;
import java.util.List;

import com.projecthero.mod.client.gui.MoveListOverlay;
import com.projecthero.mod.client.gui.VanillaHud;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.visual.MutationMeters;
import com.projecthero.mod.powerarmour.ModuleType;
import com.projecthero.mod.powerarmour.PowerArmour;
import com.projecthero.mod.powerarmour.PowerArmourConfig;
import com.projecthero.mod.powerarmour.PowerArmourState;
import com.projecthero.mod.powerarmour.SuitData;
import com.projecthero.mod.powerarmour.kit.PowerArmourAbilities;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * v0.17.5 Power Armour: the HUD inside the suit, on the vanilla pieces ({@link VanillaHud.Stack}, bottom-right) -- it owns the
 * corner while you are sealed (every other power's column steps aside):
 * <pre>
 *   Visor  Shield  Drone              tags: what is running (UNPOWERED in red)
 *   DIAMOND POWER ARMOUR              title in the glow colour
 *   Bank      [Hairline =====---]  280      only with a Capacitor Bank
 *   Integrity [Meter ==========-]  92%
 *   Core      [Slab  =======----]  640      the core's charge
 *   +--+--+--+--+--+--+                     R G Z X C V: the move (Shift: the Shift move), its cooldown, holds fill the slot
 * </pre>
 * Over the whole screen, a thin helmet frame in the glow colour (brackets at the corners, a boot line while sealing).
 */
public final class PowerArmourHud {
	private static final int TITLE = 0xFFE8E8E8;

	private PowerArmourHud() {
	}

	public static void render(GuiGraphics g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer p = mc.player;
		if (p == null || mc.level == null || mc.options.hideGui) {
			return;
		}
		PowerArmourState s = PowerArmour.state(p);
		if (s == null || s.suit == null || !s.wearing()) {
			return;
		}
		float now = mc.level.getGameTime() + delta.getGameTimeDeltaPartialTick(false);
		SuitData suit = s.suit;
		int glow = 0xFF000000 | suit.glowColour();
		helmetFrame(g, mc, s, now, glow);
		if (!s.sealed()) {
			return;
		}
		boolean shift = p.isShiftKeyDown();
		long t = (long) now;
		VanillaHud.Stack st = VanillaHud.Stack.bottomRightOwned(g, 6).keys(6);
		for (int i = 0; i < VanillaHud.ORDER.length; i++) {
			AbilitySlot slot = VanillaHud.ORDER[i];
			String[] keys = cooldownKeys(slot, suit);
			String shown = keys[shift ? 1 : 0], other = keys[shift ? 0 : 1];
			float frac = cdFrac(s, shown, t), otherFrac = cdFrac(s, other, t);
			int secs = VanillaHud.seconds((int) Math.max(0, s.readyAt.getOrDefault(shown, 0L) - t));
			boolean usable = suit.powered() && (slot != AbilitySlot.SLOT_1 || !shift || suit.weapon() != null)
					&& (slot != AbilitySlot.SLOT_6 || suit.back() != null);
			if (slot == AbilitySlot.SLOT_5 && shift) {
				usable = true;
			}
			boolean active = (slot == AbilitySlot.SLOT_5 && s.visor) || (slot == AbilitySlot.SLOT_6 && (s.shieldUntil > t || s.droneUntil > t))
					|| (slot == AbilitySlot.SLOT_3 && s.thrusting) || (slot == AbilitySlot.SLOT_2 && s.grabbed >= 0);
			st.slot(i, VanillaHud.keyLabel(slot), usable, shift, frac, secs, otherFrac, active);
			float hold = switch (slot) {
				case SLOT_1 -> s.charge == PowerArmourAbilities.CHARGE_PUNCH ? (now - s.chargeStart) / PowerArmourConfig.PUNCH_CHARGE_TICKS
						: s.charge == PowerArmourAbilities.CHARGE_RAIL ? (now - s.chargeStart) / PowerArmourConfig.RAIL_CHARGE_TICKS : 0f;
				case SLOT_5 -> s.charge == PowerArmourAbilities.CHARGE_EXIT ? (now - s.chargeStart) / PowerArmourConfig.EXIT_HOLD_TICKS : 0f;
				case SLOT_4 -> s.overloadAt > 0 ? 1f - (s.overloadAt - now) / PowerArmourConfig.OVERLOAD_WINDUP : 0f;
				default -> 0f;
			};
			st.hold(i, Mth.clamp(hold, 0f, 1f));
		}
		float cap = Math.max(1f, suit.capacity());
		st.meter(Component.translatable("hud.projecthero.power_armour.core"), suit.core == null ? 0f : suit.coreCharge / cap, 0xFF4FD8FF,
				suit.core == null ? "-" : String.valueOf(Math.round(suit.coreCharge)), 0xFF9FEFFF, MutationMeters.Style.SLAB);
		float integ = suit.integrityFraction();
		st.meter(Component.translatable("hud.projecthero.power_armour.integrity"), integ, integ < 0.3f ? 0xFFFF5555 : 0xFFB8C0CC,
				Math.round(integ * 100) + "%", integ < 0.3f ? 0xFFFF7777 : 0xFFE0E0E0, MutationMeters.Style.METER);
		if (suit.has(ModuleType.CAPACITOR_BANK)) {
			st.meter(Component.translatable("hud.projecthero.power_armour.bank"), suit.bank / Math.max(1f, suit.bankCapacity()), 0xFFFFB050,
					String.valueOf(Math.round(suit.bank)), 0xFFFFD090, MutationMeters.Style.HAIRLINE);
		}
		st.title(Component.translatable("hud.projecthero.power_armour.title", Component.translatable(suit.tier.translationKey())), glow);
		st.tags(tags(s, t, p));
		if (MoveListOverlay.held()) {
			st.moveList(moveRows(suit));
		}
	}

	/** Cooldown keys for the plain and the Shift move on a slot. */
	private static String[] cooldownKeys(AbilitySlot slot, SuitData suit) {
		return switch (slot) {
			case SLOT_1 -> new String[] { "punch", weaponKey(suit) };
			case SLOT_2 -> new String[] { "stomp", "grip" };
			case SLOT_3 -> new String[] { "jets", "jets" };
			case SLOT_4 -> new String[] { "charge", "overload" };
			case SLOT_6 -> new String[] { backKey(suit), backKey(suit) };
			default -> new String[] { "", "" };
		};
	}

	private static String weaponKey(SuitData suit) {
		ModuleType w = suit.weapon();
		if (w == null) {
			return "";
		}
		return switch (w) {
			case TESLA_COIL -> "tesla";
			case RAIL_CANNON -> "rail";
			case POWER_FIST -> "piledriver";
			default -> "";
		};
	}

	private static String backKey(SuitData suit) {
		ModuleType b = suit.back();
		if (b == null) {
			return "";
		}
		return switch (b) {
			case MISSILE_POD -> "missiles";
			case SHIELD_PROJECTOR -> "shield";
			case REPAIR_DRONE -> "drone";
			case SENTRY_TURRET -> "turret";
			case THRUSTER_PACK -> "afterburner";
			default -> "";
		};
	}

	private static int maxCooldown(String key, SuitData suit) {
		int base = switch (key) {
			case "punch" -> PowerArmourConfig.PUNCH_COOLDOWN;
			case "stomp" -> PowerArmourConfig.STOMP_COOLDOWN;
			case "grip" -> PowerArmourConfig.GRIP_COOLDOWN;
			case "jets" -> PowerArmourConfig.JETS_COOLDOWN;
			case "charge" -> PowerArmourConfig.CHARGE_COOLDOWN;
			case "overload" -> PowerArmourConfig.OVERLOAD_COOLDOWN;
			case "tesla" -> PowerArmourConfig.TESLA_COOLDOWN;
			case "rail" -> PowerArmourConfig.RAIL_COOLDOWN;
			case "piledriver" -> PowerArmourConfig.PILEDRIVER_COOLDOWN;
			case "missiles" -> PowerArmourConfig.MISSILE_COOLDOWN;
			case "shield" -> PowerArmourConfig.SHIELD_COOLDOWN;
			case "drone" -> PowerArmourConfig.DRONE_COOLDOWN;
			case "turret" -> PowerArmourConfig.TURRET_COOLDOWN;
			case "afterburner" -> PowerArmourConfig.AFTERBURNER_COOLDOWN;
			default -> 1;
		};
		return Math.max(1, Math.round(base * suit.tier.cooldown));
	}

	private static float cdFrac(PowerArmourState s, String key, long now) {
		if (key.isEmpty()) {
			return 0f;
		}
		long left = s.readyAt.getOrDefault(key, 0L) - now;
		return left <= 0 ? 0f : Math.min(1f, left / (float) maxCooldown(key, s.suit));
	}

	private static List<VanillaHud.Tag> tags(PowerArmourState s, long now, LocalPlayer p) {
		List<VanillaHud.Tag> tags = new ArrayList<>();
		if (!s.suit.powered()) {
			tags.add(tag("unpowered", 0xFFFF5555));
		}
		if (s.suit.wrecked()) {
			tags.add(tag("damaged", 0xFFFF8855));
		}
		if (s.visor) {
			tags.add(tag(s.suit.has(ModuleType.TARGET_LOCK) ? "lock" : "visor", 0xFF9FEFFF));
		}
		if (s.shieldUntil > now) {
			tags.add(new VanillaHud.Tag(Component.translatable("hud.projecthero.power_armour.tag.shield", VanillaHud.seconds((int) (s.shieldUntil - now)))
					.getString(), 0xFF88CCFF));
		}
		if (s.droneUntil > now) {
			tags.add(tag("drone", 0xFF66FF99));
		}
		if (PowerArmourAbilities.turretOut(s, now)) {
			tags.add(tag("turret", 0xFFFF8080));
		}
		if (s.thrusting) {
			tags.add(tag("thrust", 0xFFFFB060));
		}
		return tags;
	}

	private static VanillaHud.Tag tag(String key, int colour) {
		return new VanillaHud.Tag(Component.translatable("hud.projecthero.power_armour.tag." + key).getString(), colour);
	}

	private static List<MoveListOverlay.Row> moveRows(SuitData suit) {
		List<MoveListOverlay.Row> rows = new ArrayList<>();
		ModuleType w = suit.weapon(), b = suit.back();
		rows.add(MoveListOverlay.Row.of(MoveListOverlay.key(AbilitySlot.SLOT_1), move(suit.has(ModuleType.POWER_FIST) ? "power_punch" : "punch"),
				w == null ? move("no_weapon") : Component.translatable(w.translationKey())));
		rows.add(MoveListOverlay.Row.of(MoveListOverlay.key(AbilitySlot.SLOT_2), move(suit.has(ModuleType.SEISMIC_PLATES) ? "seismic_stomp" : "stomp"),
				move(suit.has(ModuleType.MAGNETIC_CLAMP) ? "clamp" : "grip")));
		rows.add(MoveListOverlay.Row.of(MoveListOverlay.key(AbilitySlot.SLOT_4), move(suit.has(ModuleType.BREACHER_PLATING) ? "breach" : "charge"),
				move("overload")));
		rows.add(MoveListOverlay.Row.of(MoveListOverlay.key(AbilitySlot.SLOT_3), move(suit.has(ModuleType.THRUSTER_PACK) ? "thrust" : "jets"), null));
		rows.add(MoveListOverlay.Row.of(MoveListOverlay.key(AbilitySlot.SLOT_6), b == null ? move("no_back") : Component.translatable(b.translationKey()),
				null));
		rows.add(MoveListOverlay.Row.of(MoveListOverlay.key(AbilitySlot.SLOT_5), move("visor"), move("exit")));
		return rows;
	}

	private static Component move(String id) {
		return Component.translatable("hud.projecthero.power_armour.move." + id);
	}

	/** A thin helmet frame in the glow colour: corner brackets, and a boot line while the suit seals. */
	private static void helmetFrame(GuiGraphics g, Minecraft mc, PowerArmourState s, float now, int glow) {
		if (!mc.options.getCameraType().isFirstPerson()) {
			return;
		}
		int w = g.guiWidth(), h = g.guiHeight();
		float a;
		if (s.phase == PowerArmourState.SEALING) {
			a = Mth.clamp((now - s.phaseStart - PowerArmourConfig.SEAL_TICKS * 0.75f) / (PowerArmourConfig.SEAL_TICKS * 0.25f), 0f, 1f);
		} else if (s.phase == PowerArmourState.UNSEALING) {
			a = 1f - Mth.clamp((now - s.phaseStart) / (PowerArmourConfig.UNSEAL_TICKS * 0.5f), 0f, 1f);
		} else {
			a = s.suit.powered() ? 1f : 0.35f + 0.25f * Mth.sin(now * 0.6f);
		}
		if (a <= 0.01f) {
			return;
		}
		int col = ((int) (a * 0x70) << 24) | (glow & 0xFFFFFF);
		int len = Math.max(12, w / 14), m = 6;
		// corners
		g.fill(m, m, m + len, m + 1, col);
		g.fill(m, m, m + 1, m + len, col);
		g.fill(w - m - len, m, w - m, m + 1, col);
		g.fill(w - m - 1, m, w - m, m + len, col);
		g.fill(m, h - m - 1, m + len, h - m, col);
		g.fill(m, h - m - len, m + 1, h - m, col);
		g.fill(w - m - len, h - m - 1, w - m, h - m, col);
		g.fill(w - m - 1, h - m - len, w - m, h - m, col);
		// a soft dark edge (the inside of the helmet)
		int edge = ((int) (a * 0x50) << 24);
		g.fillGradient(0, 0, w, 14, edge, 0);
		g.fillGradient(0, h - 14, w, h, 0, edge);
		if (s.phase == PowerArmourState.SEALING) {
			float p = Mth.clamp((now - s.phaseStart) / PowerArmourConfig.SEAL_TICKS, 0f, 1f);
			Component text = Component.translatable(p < 0.95f ? "hud.projecthero.power_armour.sealing" : "hud.projecthero.power_armour.online");
			int tw = mc.font.width(text);
			g.drawString(mc.font, text, w / 2 - tw / 2, h / 2 + 20, glow, true);
			g.fill(w / 2 - 40, h / 2 + 31, w / 2 - 40 + Math.round(80 * p), h / 2 + 32, glow);
		}
	}
}
