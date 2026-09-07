package com.projecthero.mod.client.gui;

import java.util.ArrayList;
import java.util.List;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.Ability;
import com.projecthero.mod.hero.AbilityActivation;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.Power;
import com.projecthero.mod.hero.Powers;
import com.projecthero.mod.hero.data.ExperimentalState;
import com.projecthero.mod.item.ModItems;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.lwjgl.glfw.GLFW;

/**
 * The six-slot HeroPack ability HUD (spec section 9). Compact by default: a row of six boxes near the
 * lower-right showing the slot key, cooldown shading + remaining seconds, and an ACTIVE edge for
 * toggled abilities. Hold Left Alt to expand and show full ability names.
 *
 * <p>Underneath the row, one bar per meter that is currently <em>in play</em> — draining or
 * refilling. A meter sitting full and idle is not drawn. The whole HUD is pushed up to make room for
 * however many bars are showing. Each bar is labelled with the ability it belongs to.
 *
 * <p>Only rendered for experimental-power context. Thor keeps its own {@code ThorHud} (Storm Energy).
 */
public final class AbilityHud {
	private static final int BOX = 20;
	private static final int GAP = 2;
	private static final int MARGIN = 4;
	private static final int BAR_ROW_H = 16;

	private static final int COLOR_BOX_BG = 0xC0101018;
	private static final int COLOR_BORDER = 0xFF2E2E44;
	private static final int COLOR_BORDER_ACTIVE = 0xFF66E0A0;
	private static final int COLOR_COOLDOWN = 0xB0000000;
	private static final int COLOR_KEY = 0xFFB8C0E0;

	private AbilityHud() {
	}

	public static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
		Minecraft client = Minecraft.getInstance();
		Player player = client.player;
		if (player == null || client.options.hideGui) {
			return;
		}

		ExperimentalState state = player.getAttachedOrElse(ModAttachments.EXPERIMENTAL_STATE, null);
		if (state == null || state.activePower.isEmpty()) {
			return;
		}
		// Thor takes the slots while Mjolnir is in hand -- don't show the experimental HUD then.
		if (player.getMainHandItem().is(ModItems.MJOLNIR) || player.getOffhandItem().is(ModItems.MJOLNIR)) {
			return;
		}
		Power power = Powers.byKey(state.activePower);
		if (power == null) {
			return;
		}

		// v0.9.3: the six boxes + names are the SELECTED power's kit, but the meter bars are gathered
		// from EVERY owned Experimental Tier power -- a second power's aura / stance / reserve keeps its
		// bar while a different power holds the slots.
		List<Meter> meters = new ArrayList<>();
		for (String key : state.ownedPowers) {
			Power owned = Powers.byKey(key);
			if (owned != null) {
				meters.addAll(collectMeters(state, owned));
			}
		}
		meters.sort((a, b) -> a.label.getString().compareToIgnoreCase(b.label.getString()));

		int screenW = graphics.guiWidth();
		int screenH = graphics.guiHeight();
		int totalW = 6 * BOX + 5 * GAP;
		int x0 = screenW - MARGIN - totalW;
		int barsBlock = meters.isEmpty() ? 0 : meters.size() * BAR_ROW_H + 4;
		int y0 = screenH - MARGIN - BOX - barsBlock;

		long gameTime = client.level != null ? client.level.getGameTime() : 0L;
		boolean expanded = GLFW.glfwGetKey(client.getWindow().getWindow(), GLFW.GLFW_KEY_LEFT_ALT) == GLFW.GLFW_PRESS;

		// Power name above the row.
		graphics.drawString(client.font, Component.translatable(power.nameKey()), x0, y0 - 10, 0xFFD8D8F0);

		for (int i = 0; i < 6; i++) {
			AbilitySlot slot = AbilitySlot.byNumber(i + 1);
			Ability ability = power.ability(slot);
			int x = x0 + i * (BOX + GAP);

			boolean toggled = ability.activation() == AbilityActivation.TOGGLE
					&& state.activeToggles.contains(power.key() + "/" + ability.id());
			Long readyAt = state.abilityReadyAt.get(power.key() + "/" + ability.id());
			int cdRemain = readyAt == null ? 0 : (int) Math.max(0L, readyAt - gameTime);

			graphics.fill(x, y0, x + BOX, y0 + BOX, COLOR_BOX_BG);
			int border = toggled ? COLOR_BORDER_ACTIVE : COLOR_BORDER;
			graphics.renderOutline(x, y0, BOX, BOX, border);

			graphics.drawString(client.font, String.valueOf(slot.defaultKey()), x + 2, y0 + 2, COLOR_KEY, false);

			if (cdRemain > 0) {
				graphics.fill(x + 1, y0 + 1, x + BOX - 1, y0 + BOX - 1, COLOR_COOLDOWN);
				String secs = String.format(java.util.Locale.ROOT, "%.0f", Math.ceil(cdRemain / 20.0f));
				graphics.drawCenteredString(client.font, secs, x + BOX / 2, y0 + BOX / 2 - 4, 0xFFFFFFFF);
			}

			if (expanded) {
				Component name = Component.translatable(ability.nameKey());
				int tw = client.font.width(name);
				// Stack the names in a column that ends just above the keybind-box row, so the
				// bottom entry stays on screen no matter how many meter bars are showing below.
				graphics.drawString(client.font, name, x0 - 8 - tw, y0 + i * 10 - 52, 0xFFCfcfe6);
			}
		}

		if (power.key().equals("power_01_super_strength")) {
			renderStrengthExtras(graphics, client, state, x0, y0 - 20);
		}

		int bw = 6 * BOX + 5 * GAP;
		int barY = y0 + BOX + 4;
		for (Meter m : meters) {
			float ratio = Math.max(0.0f, Math.min(1.0f, m.value / m.max));
			graphics.fill(x0 - 1, barY - 1, x0 + bw + 1, barY + 5, COLOR_BORDER);
			graphics.fill(x0, barY, x0 + bw, barY + 4, 0xAA101018);
			graphics.fill(x0, barY, x0 + Math.round(bw * ratio), barY + 4, m.color);
			graphics.drawString(client.font, m.label, x0, barY + 5, 0xFF9AA6D0, false);
			barY += BAR_ROW_H;
		}
	}

	private record Meter(Component label, float value, float max, int color) {
	}

	/** Charged Punch / Power Leap / Bull Rush charge + cooldown bars, stacked above the ability row. */
	private static void renderStrengthExtras(GuiGraphics g, Minecraft client, ExperimentalState state, int x, int y) {
		String pk = "power_01_super_strength/";
		float cd = state.resources.getOrDefault(pk + "charged_cd", 0.0f);
		float effort = state.resources.getOrDefault(pk + "effort_left", 0.0f);
		float zCharge = state.resources.getOrDefault(pk + "z_charge", 0.0f);
		float zSmash = state.resources.getOrDefault(pk + "z_smash", 0.0f);
		float zRunEnd = state.resources.getOrDefault(pk + "z_run_end", 0.0f);
		float zCd = state.resources.getOrDefault(pk + "z_cd", 0.0f);
		float punchProg = com.projecthero.mod.client.ProjectHeroModClient.chargedPunchProgress();
		boolean punchReady = com.projecthero.mod.client.ProjectHeroModClient.chargedPunchReady();
		float leapProg = com.projecthero.mod.client.ProjectHeroModClient.leapChargeProgress();
		long now = client.level != null ? client.level.getGameTime() : 0L;

		int w = 6 * BOX + 5 * GAP;
		int[] rowY = { y };

		if (effort > 0.5f) {
			strengthBar(g, client, x, w, rowY, "Maximum Effort  " + (int) Math.ceil(effort / 20.0f) + "s",
					Math.min(1.0f, effort / 440.0f), 0xFFB98CFF, 0xFFCBB6FF);
		}
		if (zCharge > 0.5f) {
			float held = Math.max(0f, now - zCharge);
			strengthBar(g, client, x, w, rowY, zSmash > 0.5f ? "Impact Smash — charging" : "Bull Rush — charging",
					Math.min(1.0f, held / 100.0f), 0xFFE0703A, 0xFFF0A070);
		} else if (zRunEnd > 0.5f) {
			strengthBar(g, client, x, w, rowY, "BULL RUSH", 1.0f, 0xFFFFC24A, 0xFFFFE0A0);
		} else if (zCd > 0.5f) {
			strengthBar(g, client, x, w, rowY, "Bull Rush / Impact Smash  " + (int) Math.ceil(zCd / 20.0f) + "s",
					1.0f - Math.min(1.0f, zCd / 1800.0f), 0xFF6A5230, 0xFFB0A080);
		}
		if (leapProg > 0.01f) {
			strengthBar(g, client, x, w, rowY, "Power Leap", leapProg, 0xFF6FA8FF, 0xFFB8D0FF);
		}
		if (cd > 0.5f) {
			strengthBar(g, client, x, w, rowY, "Charged Punch  " + (int) Math.ceil(cd / 20.0f) + "s",
					1.0f - Math.min(1.0f, cd / 50.0f), 0xFF7A5A2A, 0xFFE8C98A);
		} else if (punchReady) {
			strengthBar(g, client, x, w, rowY, "Charged Punch — RELEASE", 1.0f, 0xFFFFC24A, 0xFFFFE0A0);
		} else if (punchProg > 0.01f) {
			strengthBar(g, client, x, w, rowY, "Charged Punch", punchProg, 0xFFE0A040, 0xFFE8C98A);
		}
	}

	private static void strengthBar(GuiGraphics g, Minecraft client, int x, int w, int[] rowY,
			String label, float ratio, int fill, int textColor) {
		int ry = rowY[0];
		g.fill(x - 1, ry - 1, x + w + 1, ry + 5, COLOR_BORDER);
		g.fill(x, ry, x + w, ry + 4, 0xAA101018);
		g.fill(x, ry, x + Math.round(w * Math.max(0f, Math.min(1f, ratio))), ry + 4, fill);
		g.drawString(client.font, label, x, ry - 9, textColor, false);
		rowY[0] = ry - 18;
	}

	/** Build the list of meters worth drawing right now for the active power. */
	private static List<Meter> collectMeters(ExperimentalState state, Power power) {
		List<Meter> out = new ArrayList<>();
		// Super Strength keeps only transient bookkeeping in its resource map -- its charged-punch
		// and Maximum Effort indicators are drawn separately (see renderStrengthExtras).
		if (power.key().equals("power_01_super_strength")) {
			return out;
		}
		String prefix = power.key() + "/";
		for (var e : state.resources.entrySet()) {
			if (!e.getKey().startsWith(prefix)) {
				continue;
			}
			String name = e.getKey().substring(prefix.length());
			if (isBookkeeping(name)) {
				continue;
			}
			float value = e.getValue();
			Kind kind = kindOf(name);

			if (name.equals("flight") && (!com.projecthero.mod.hero.power.HeroFlight.hasFlight(power)
					|| com.projecthero.mod.hero.power.HeroFlight.infinite(power))) {
				continue; // stale flight stamina on a grounded power
			}

			float max = maxOf(name);
			boolean show = switch (kind) {
				// reserves: shown only while depleted / recharging, hidden when full and idle
				case RESERVE -> value < max - 1.0f;
				// build-up meters + countdown timers: shown only while there is something on them
				case BUILD, TIMER -> value > 0.5f;
			};
			if (!show) {
				continue;
			}
			int color = switch (kind) {
				case BUILD -> name.equals("freeze_beam") ? 0xFF7EC8FF : 0xFFFFC24A; // frost-blue for cold buildup
				case TIMER -> 0xFFB98CFF;
				default -> 0xFF6FA8FF;
			};
			out.add(new Meter(label(power, name), value, max, color));
		}
		return out;
	}

	private enum Kind { RESERVE, BUILD, TIMER }

	private static Kind kindOf(String name) {
		// Fixed-duration self-flight countdowns (flameflight, rockflight) -- drain to nothing, then vanish.
		if (name.endsWith("flight") && !name.equals("flight")) {
			return Kind.TIMER;
		}
		return switch (name) {
			// build-up gauges: climb from zero while their mode runs, full bar is the fail state
			case "energy", "heat", "static_charge", "charge", "freeze_beam", "flamethrower" -> Kind.BUILD;
			case "total_darkness", "hurr", "singularity" -> Kind.TIMER;
			default -> Kind.RESERVE;
		};
	}

	private static float maxOf(String name) {
		if (name.endsWith("flight")) {
			return 100.0f; // "flight" stamina and the timed self-flight meters are all 0..100
		}
		return switch (name) {
			case "guard", "phase", "static_charge", "charge", "sparkle" -> 100.0f;
			case "hurr" -> 400.0f;
			default -> 500.0f;
		};
	}

	/** Label a meter with the ability it belongs to when we can name it, else a friendly fallback. */
	private static Component label(Power power, String name) {
		for (Ability a : power.abilities()) {
			if (a.id().equals(name)) {
				return Component.translatable(a.nameKey());
			}
		}
		return switch (name) {
			case "psi" -> Component.literal("Telekinetic Energy");
			case "energy" -> Component.literal("Energy");
			case "heat" -> Component.literal("Heat");
			case "guard" -> Component.literal("Guard");
			case "phase" -> Component.literal("Phase");
			case "flight" -> Component.literal("Flight");
			case "static_charge" -> Component.literal("Static Charge");
			case "charge" -> Component.literal("Charge");
			case "hurr" -> Component.literal("Hurricane");
			case "singularity" -> Component.literal("Singularity");
			case "sparkle" -> Component.literal("Sparkling Flight");
			default -> name.endsWith("flight") ? Component.literal("Flight")
					: Component.literal(capitalize(name.replace('_', ' ')));
		};
	}

	private static String capitalize(String s) {
		return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
	}

	/** Transient flags, entity ids, packed positions, tick counters -- never drawn as a bar. */
	private static boolean isBookkeeping(String name) {
		if (name.endsWith("_id") || name.endsWith("_x") || name.endsWith("_y") || name.endsWith("_z")
				|| name.endsWith("_until") || name.endsWith("_ticks") || name.endsWith("_bonus")
				|| name.endsWith("_prev") || name.endsWith("_hold")) {
			return true;
		}
		return switch (name) {
			case "beaming", "flaming", "diving", "charging", "slamming", "grabbed", "blocking", "deflecting",
					"singularity_active", "gripping", "shielding", "draining", "repelling", "storm", "wave",
					"crush", "frenzy", "well", "boulder", "mark_set", "aiming", "dashing", "sparkling", "beaming_holy",
					"elastic_fall", "slide_hold_ticks", "slide_sneak_prev" -> true;
			default -> false;
		};
	}
}
