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
 * <p>Underneath the row, one bar per meter that is currently <em>in play</em> — draining, filling, or
 * counting down. A meter sitting full and idle is not drawn, and a charge gauge that has been spent or
 * cancelled disappears the same tick. The whole HUD is pushed up to make room for however many bars are
 * showing. Each bar is labelled with the ability it belongs to.
 *
 * <p>What counts as a meter at all is an explicit allow-list ({@code KIND}); everything else a power
 * stores in its resource map is bookkeeping and is never drawn. See {@link #collectMeters}.
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
		long meterTime = client.level != null ? client.level.getGameTime() : 0L;
		List<Meter> meters = new ArrayList<>();
		for (String key : state.ownedPowers) {
			Power owned = Powers.byKey(key);
			if (owned != null) {
				meters.addAll(collectMeters(state, owned, meterTime));
			}
		}
		meters.sort((a, b) -> a.label.getString().compareToIgnoreCase(b.label.getString()));

		int screenW = graphics.guiWidth();
		int screenH = graphics.guiHeight();
		// 6, or 8 with the H / N utility slots (v0.13.22), or 0 for a passive-only power (v0.14.5)
		int slots = power.abilities().size();
		// v0.14.7: the H / N boxes only for a power that has H -- an N-only power (Super Speed's Speed Carry) shows
		// just its six core keys, and H stays the power wheel
		if (slots > 6 && !power.hasSlot(AbilitySlot.SLOT_7)) {
			slots = 6;
		}
		// A passive-only power still lays out as wide as a six-key row so its name + bars have room.
		int layoutSlots = Math.max(6, slots);
		int totalW = layoutSlots * BOX + (layoutSlots - 1) * GAP + (slots > 6 ? UTILITY_GAP : 0);
		int x0 = screenW - MARGIN - totalW;
		// v0.14.5: registered meters flagged "above" are drawn as Hairline bars over the key row instead.
		List<AbilityHudExtras.AboveBar> above = new ArrayList<>();
		for (java.util.Iterator<Meter> it = meters.iterator(); it.hasNext();) {
			Meter m = it.next();
			if (m.above) {
				above.add(new AbilityHudExtras.AboveBar(m.label.getString(), Math.max(0f, Math.min(1f, m.value / m.max)),
						m.color, m.textColor));
				it.remove();
			}
		}
		above.addAll(AbilityHudExtras.above(power.key(), client, state, meterTime));
		int slabs = (int) meters.stream().filter(m -> m.style == com.projecthero.mod.hero.visual.MutationMeters.Style.SLAB).count();
		int barsBlock = meters.isEmpty() ? 0 : meters.size() * BAR_ROW_H + slabs * 2 + 4;
		int y0 = screenH - MARGIN - (slots == 0 ? 0 : BOX) - barsBlock;

		long gameTime = client.level != null ? client.level.getGameTime() : 0L;
		boolean expanded = GLFW.glfwGetKey(client.getWindow().getWindow(), GLFW.GLFW_KEY_LEFT_ALT) == GLFW.GLFW_PRESS;

		// v0.14.5: black-and-gray theme for the powers that ask for it.
		boolean mono = AbilityHudExtras.isMono(power.key());
		int boxBg = mono ? MONO_BOX_BG : COLOR_BOX_BG;
		int borderIdle = mono ? MONO_BORDER : COLOR_BORDER;
		int borderActive = mono ? MONO_BORDER_ACTIVE : COLOR_BORDER_ACTIVE;
		int keyColor = mono ? MONO_KEY : COLOR_KEY;
		int labelColor = mono ? MONO_LABEL : 0xFF9AA6D0;
		int barBg = mono ? MONO_BAR_BG : 0xAA101018;

		// Hairline bars above the key row, bottom-up.
		int aboveH = above.size() * ABOVE_ROW_H;
		for (int k = 0; k < above.size(); k++) {
			AbilityHudExtras.AboveBar b = above.get(k);
			int by = y0 - 5 - k * ABOVE_ROW_H;
			graphics.fill(x0, by, x0 + totalW, by + 3, barBg);
			graphics.fill(x0, by, x0 + Math.round(totalW * Math.max(0f, Math.min(1f, b.ratio()))), by + 3, b.fill());
			graphics.drawString(client.font, b.label(), x0, by - 9, b.textColor() != 0 ? b.textColor() : labelColor, false);
		}

		// Power name above the row (and above any Hairline bars).
		int nameY = y0 - 10 - aboveH;
		Component powerName = Component.translatable(power.nameKey());
		graphics.drawString(client.font, powerName, x0, nameY, mono ? MONO_NAME : 0xFFD8D8F0);
		AbilityHudExtras.Decor decor = AbilityHudExtras.decor(power.key());
		if (decor != null) {
			decor.draw(graphics, client, state, x0 + client.font.width(powerName) + 6, nameY);
		}

		for (int i = 0; i < slots; i++) {
			AbilitySlot slot = AbilitySlot.byNumber(i + 1);
			Ability ability = power.ability(slot);
			int x = x0 + i * (BOX + GAP) + (slot.isUtility() ? UTILITY_GAP : 0);

			boolean toggled = ability.activation() == AbilityActivation.TOGGLE
					&& state.activeToggles.contains(power.key() + "/" + ability.id());
			Long readyAt = state.abilityReadyAt.get(power.key() + "/" + ability.id());
			int cdRemain = readyAt == null ? 0 : (int) Math.max(0L, readyAt - gameTime);
			// v0.14.5: Super Strength's Shift+G (Thunderclap) and Shift+V (Rip & Hurl) keep their own cooldowns in
			// the clap_cd / rip_cd resources (ticks) -- the G / V box shows whichever of the two moves is longer.
			if (power.key().equals("power_01_super_strength")
					&& (slot == AbilitySlot.SLOT_2 || slot == AbilitySlot.SLOT_5)) {
				String extra = slot == AbilitySlot.SLOT_2 ? "clap_cd" : "rip_cd";
				int alt = Math.round(state.resources.getOrDefault("power_01_super_strength/" + extra, 0.0f));
				if (alt > cdRemain) {
					cdRemain = alt;
				}
			}
			// v0.14.7: Super Speed's Shift+R / Shift+G / Shift+X (Mach Punch, Speed Vortex, Speed Sweep) keep absolute
			// ready-at times in their own resources -- the R / G / X box shows the longer of the two waits.
			if (power.key().equals("power_04_super_speed")
					&& (slot == AbilitySlot.SLOT_1 || slot == AbilitySlot.SLOT_2 || slot == AbilitySlot.SLOT_3)) {
				String extra = slot == AbilitySlot.SLOT_1 ? "mach_ready" : slot == AbilitySlot.SLOT_2 ? "vortex_ready" : "sweep_ready";
				float readyAtAlt = state.resources.getOrDefault("power_04_super_speed/" + extra, 0.0f);
				int alt = (int) Math.max(0L, (long) readyAtAlt - gameTime);
				if (alt > cdRemain) {
					cdRemain = alt;
				}
			}

			graphics.fill(x, y0, x + BOX, y0 + BOX, boxBg);
			int border = toggled ? borderActive : borderIdle;
			graphics.renderOutline(x, y0, BOX, BOX, border);

			graphics.drawString(client.font, keyLabel(i), x + 2, y0 + 2, slot.isUtility() ? COLOR_KEY_UTILITY : keyColor, false);

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

		if (power.key().equals("power_19_shadow_manipulation")) {
			renderShadowLevel(graphics, client, x0, nameY - 10);
		}
		if (power.key().equals("power_18_density_manipulation")) {
			float d = state.resources.getOrDefault("power_18_density_manipulation/density", 100.0f);
			if (d <= 0.0f) {
				d = 100.0f;
			}
			boolean anchored = state.resources.getOrDefault("power_18_density_manipulation/anchor_until", 0.0f) > gameTime;
			Component line = Component.literal("Density: " + Math.round(d) + "%" + (anchored ? "  [ANCHOR]" : ""));
			int col = d < 100 ? 0xFF7FD0FF : (d > 100 ? 0xFFFFB24A : 0xFFD8D8F0);
			graphics.drawString(client.font, line, x0, nameY - 10, col);
		}

		int bw = totalW;
		int barY = y0 + (slots == 0 ? 0 : BOX) + 4;
		for (Meter m : meters) {
			float ratio = Math.max(0.0f, Math.min(1.0f, m.value / m.max));
			if (m.style == com.projecthero.mod.hero.visual.MutationMeters.Style.SLAB) {
				// Slab: a thick 7 px bar
				graphics.fill(x0 - 1, barY - 1, x0 + bw + 1, barY + 7, borderIdle);
				graphics.fill(x0, barY, x0 + bw, barY + 6, barBg);
				graphics.fill(x0, barY, x0 + Math.round(bw * ratio), barY + 6, m.color);
				graphics.drawString(client.font, m.label, x0, barY + 7, m.textColor != 0 ? m.textColor : labelColor, false);
				barY += BAR_ROW_H + 2;
				continue;
			}
			if (m.style == com.projecthero.mod.hero.visual.MutationMeters.Style.GAUGE) {
				// Gauge: ten separate cells
				int cells = 10;
				int cw = (bw - (cells - 1)) / cells;
				int lit = (int) Math.ceil(ratio * cells - 1e-4);
				for (int c = 0; c < cells; c++) {
					int cx = x0 + c * (cw + 1);
					graphics.fill(cx, barY, cx + cw, barY + 4, c < lit ? m.color : barBg);
				}
			} else if (m.hairline) {
				// v0.13.11: a Hairline bar -- 3 px, no border (Pyrokinesis)
				graphics.fill(x0, barY + 1, x0 + bw, barY + 4, barBg);
				graphics.fill(x0, barY + 1, x0 + Math.round(bw * ratio), barY + 4, m.color);
			} else {
				graphics.fill(x0 - 1, barY - 1, x0 + bw + 1, barY + 5, borderIdle);
				graphics.fill(x0, barY, x0 + bw, barY + 4, barBg);
				graphics.fill(x0, barY, x0 + Math.round(bw * ratio), barY + 4, m.color);
			}
			graphics.drawString(client.font, m.label, x0, barY + 5, m.textColor != 0 ? m.textColor : labelColor, false);
			barY += BAR_ROW_H;
		}
	}

	/** {@code hairline}: drawn as a borderless 3 px Hairline bar instead of the bordered Meter bar. */
	private record Meter(Component label, float value, float max, int color, boolean hairline,
			com.projecthero.mod.hero.visual.MutationMeters.Style style, boolean above, int textColor) {
		Meter(Component label, float value, float max, int color, boolean hairline) {
			this(label, value, max, color, hairline, hairline ? com.projecthero.mod.hero.visual.MutationMeters.Style.HAIRLINE
					: com.projecthero.mod.hero.visual.MutationMeters.Style.METER, false, 0);
		}
	}

	/** v0.14.5 black-and-gray theme ({@link AbilityHudExtras#mono}). */
	private static final int MONO_BOX_BG = 0xD0080808;
	private static final int MONO_BORDER = 0xFF4A4A4A;
	private static final int MONO_BORDER_ACTIVE = 0xFFC8C8C8;
	private static final int MONO_KEY = 0xFFB4B4B4;
	private static final int MONO_NAME = 0xFFE2E2E2;
	private static final int MONO_LABEL = 0xFF9A9A9A;
	private static final int MONO_BAR_BG = 0xAA0C0C0C;
	/** One Hairline bar above the keys: 9 px label + 3 px bar. */
	private static final int ABOVE_ROW_H = 13;

	/** v0.13.22: a small gap sets the two utility boxes (H / N) apart from the six core keys. */
	private static final int UTILITY_GAP = 4;
	private static final int COLOR_KEY_UTILITY = 0xFFE0C890;

	/** The key actually bound to slot {@code i} (tracks rebinds; H / N are the Utility 1 / 2 keys). */
	private static String keyLabel(int i) {
		net.minecraft.client.KeyMapping km = i < 6 ? com.projecthero.mod.client.ModKeyBindings.ABILITY_SLOTS[i]
				: i == 6 ? com.projecthero.mod.client.ModKeyBindings.POWER_SELECT
				: com.projecthero.mod.client.ModKeyBindings.MAX_STEEL_TRANSFORM;
		String text = km.getTranslatedKeyMessage().getString();
		return text.length() > 2 ? text.substring(0, 2) : text;
	}

	/** v0.13.11: powers whose bars are all Hairline bars. */
	private static final java.util.Set<String> HAIRLINE_POWERS = java.util.Set.of("power_08_pyrokinesis");

	/** Shadow Manipulation's ambient-light strength indicator, shown above the ability row. */
	private static void renderShadowLevel(GuiGraphics g, Minecraft client, int x, int y) {
		if (client.player == null || client.level == null) {
			return;
		}
		float tier = com.projecthero.mod.hero.power.p19.ShadowManipulationHandlers.tier(
				client.level, client.player.blockPosition());
		int pct = Math.round(tier * 100);
		int color = tier >= 1.3f ? 0xFFB98CFF : tier >= 1.0f ? 0xFF9AA6D0 : tier >= 0.75f ? 0xFFE8C98A : 0xFFFFC24A;
		g.drawString(client.font, "Shadow Strength: " + pct + "%", x, y, color, false);
	}

	/**
	 * Build the list of meters worth drawing right now for one owned power.
	 *
	 * <h2>v0.10.10: an allow-list, not a deny-list</h2>
	 * This used to draw a bar for every entry in the power's resource map that a hand-maintained
	 * {@code isBookkeeping} list did not happen to name, defaulting anything unrecognised to
	 * "reserve, max 500" -- which is drawn whenever it is not nearly full. Every flag, entity id,
	 * mode number and charge counter that was ever added without also being added to that list
	 * therefore became a <em>permanent</em> bar sitting at 1/500 (Water Manipulation's
	 * {@code spraying} flag and Crystalkinesis' {@code skating} flag are exactly this, and
	 * {@code spraying} is why Water Beam appeared to have two bars), and the meters that were
	 * classified simply had the wrong kind, so they never emptied off the screen.
	 *
	 * <p>The resource map is a general-purpose per-power scratchpad -- most of what is in it is
	 * bookkeeping, and bookkeeping is the default. So a name now has to be listed in {@link #KIND}
	 * to be drawn at all, and its {@link Kind} decides when it is worth showing:
	 * <ul>
	 *   <li>{@code RESERVE} -- a pool that starts full and is spent. Shown while it is depleted.</li>
	 *   <li>{@code BUILD} -- a gauge that climbs from zero while a channel runs and where a FULL bar is
	 *       the fail state (overheat, frostbite, an ultimate finishing its charge). Shown while non-zero.</li>
	 *   <li>{@code TIMER} -- a countdown on an active effect. Shown while it is still running.</li>
	 * </ul>
	 */
	private static List<Meter> collectMeters(ExperimentalState state, Power power, long gameTime) {
		List<Meter> out = new ArrayList<>();
		// Super Strength's Charged Punch / Power Leap / Bull Rush / Maximum Effort bars are Hairlines above the keys
		// (SuperStrengthClientV0145, via AbilityHudExtras); only registered meters (MutationMeters) are gathered here.
		boolean registeredOnly = power.key().equals("power_01_super_strength");
		String prefix = power.key() + "/";
		for (var e : state.resources.entrySet()) {
			if (!e.getKey().startsWith(prefix)) {
				continue;
			}
			String name = e.getKey().substring(prefix.length());
			com.projecthero.mod.hero.visual.MutationMeters.Spec spec =
					com.projecthero.mod.hero.visual.MutationMeters.get(power.key(), name);
			if (spec != null) {
				float v = e.getValue();
				boolean visible = spec.always() || switch (spec.kind()) {
					case RESERVE -> v < spec.max() - 1.0f;
					case BUILD, TIMER -> v > 0.5f;
				};
				if (visible) {
					String text = spec.label() + (spec.showValue()
							? "  " + Math.round(Math.max(0f, Math.min(1f, v / spec.max())) * 100) + "%" : "");
					out.add(new Meter(Component.literal(text), v, spec.max(), spec.color(),
							spec.style() == com.projecthero.mod.hero.visual.MutationMeters.Style.HAIRLINE || spec.above(),
						spec.style(), spec.above(), spec.textColor()));
				}
				continue;
			}
			if (registeredOnly) {
				continue;
			}
			Kind kind = kindOf(name);
			if (kind == null) {
				continue; // bookkeeping: a flag, an entity id, a packed position, a tick counter
			}
			float value = e.getValue();

			if (name.equals("flight") && (!com.projecthero.mod.hero.power.HeroFlight.hasFlight(power)
					|| com.projecthero.mod.hero.power.HeroFlight.infinite(power))) {
				continue; // stale flight stamina on a grounded power
			}

			float max = maxOf(name);
			boolean show = switch (kind) {
				// reserves: shown only while depleted / recharging, hidden when full and idle
				case RESERVE -> value < max - 1.0f;
				// build-up gauges + countdown timers: shown only while there is something on them
				case BUILD, TIMER -> value > 0.5f;
			};
			// v0.10.13: Telekinesis' Psi reserve is always shown -- it is the whole power's fuel gauge
			// and players kept losing track of it once it topped back up and the bar vanished.
			// v0.10.19: Energy Absorption's Energy bar and Shockwave's Charge bar are likewise always on
			// screen -- both are the whole power's resource, not a situational buff.
			if (name.equals("psi") || name.equals("ecell") || name.equals("energy") || name.equals("charge")) {
				show = true;
			}
			if (!show) {
				continue;
			}
			int color = switch (kind) {
				case BUILD -> name.equals("freeze_beam") ? 0xFF7EC8FF : 0xFFFFC24A; // frost-blue for cold buildup
				case TIMER -> 0xFFB98CFF;
				default -> 0xFF6FA8FF;
			};
			Component text = label(power, name);
			// Telekinesis' Psi burnout is the one meter state that is a hard lockout rather than a
			// shortage, so it gets its own red bar and label instead of just reading as "empty".
			if (name.equals("psi") && state.resources.getOrDefault(prefix + "burnout_until", 0.0f) > gameTime) {
				color = 0xFFE05252;
				text = Component.literal("Psi — BURNED OUT");
			}
			// v0.10.21: Energy Absorption's bar is always on screen -- show the exact percentage next
			// to the label rather than leaving the player to eyeball the fill.
			if (name.equals("energy")) {
				text = Component.literal("Energy: "
						+ String.format(java.util.Locale.ROOT, "%.2f", value / max * 100.0f) + "%");
			}
			out.add(new Meter(text, value, max, color, HAIRLINE_POWERS.contains(power.key())));
		}
		return out;
	}

	private enum Kind { RESERVE, BUILD, TIMER }

	/**
	 * The complete set of resource names that are meters. Anything not in here is bookkeeping and is
	 * never drawn -- see {@link #collectMeters}. Timed self-flight countdowns ({@code rockflight},
	 * {@code flameflight}, ...) are matched by suffix rather than listed, since each power names its own.
	 */
	private static final java.util.Map<String, Kind> KIND = java.util.Map.ofEntries(
			// --- reserves: start full, spent by use, regenerate while idle ---
			java.util.Map.entry("psi", Kind.RESERVE),            // Telekinesis
			java.util.Map.entry("flight", Kind.RESERVE),         // HeroFlight stamina
			java.util.Map.entry("phase", Kind.RESERVE),          // Density Manipulation
			java.util.Map.entry("sparkle", Kind.RESERVE),        // Invisibility / Light
			java.util.Map.entry("charged_mode", Kind.RESERVE),   // Electrokinesis stance
			java.util.Map.entry("crystal_armor", Kind.RESERVE),  // Crystalkinesis stance
			java.util.Map.entry("earth_armor", Kind.RESERVE),    // Geokinesis stance
			java.util.Map.entry("flame_body", Kind.RESERVE),     // Pyrokinesis stance
			java.util.Map.entry("frozen_armor", Kind.RESERVE),   // Cryokinesis stance
			java.util.Map.entry("giant_form", Kind.RESERVE),     // Size Manipulation stance
			java.util.Map.entry("repulsion_field", Kind.RESERVE), // Shockwave stance
			java.util.Map.entry("tailwind", Kind.RESERVE),       // Wind stance
			java.util.Map.entry("shadow_cloak", Kind.RESERVE),   // Shadow Manipulation Shadow Cloak
			java.util.Map.entry("field_bar", Kind.RESERVE),      // Energy Absorption Absorption Field
			java.util.Map.entry("nexus_bar", Kind.RESERVE),      // Gravity Manipulation Gravitational Nexus
			// --- build-up gauges: climb from zero, a full bar is the fail state ---
			java.util.Map.entry("energy", Kind.BUILD),           // Energy Absorption
			java.util.Map.entry("heat", Kind.BUILD),             // Laser Vision / Pyrokinesis
			java.util.Map.entry("static_charge", Kind.BUILD),    // Electrokinesis
			java.util.Map.entry("charge", Kind.BUILD),           // Shockwave
			java.util.Map.entry("freeze_beam", Kind.BUILD),      // Cryokinesis channel
			java.util.Map.entry("flamethrower", Kind.BUILD),     // Pyrokinesis channel
			java.util.Map.entry("water", Kind.BUILD),            // Water Manipulation channel
			java.util.Map.entry("ult_charge", Kind.BUILD),       // shared hold-to-charge ultimate meter
			java.util.Map.entry("fb_charge", Kind.BUILD),        // Laser Vision Focused Beam charge
			java.util.Map.entry("portal_charge", Kind.BUILD),    // Teleportation Portal charge
			java.util.Map.entry("stretch_charge", Kind.BUILD),   // Elasticity Stretch Punch charge
			java.util.Map.entry("sonic_charge", Kind.BUILD),     // Sonic Scream hold-to-charge
			java.util.Map.entry("storm_charge", Kind.BUILD),     // Electrokinesis Storm Bolt hold-to-charge
			java.util.Map.entry("ecell", Kind.RESERVE),          // Electrokinesis charge cell
			java.util.Map.entry("senses", Kind.RESERVE),         // Sonic Scream Enhanced Senses meter
			java.util.Map.entry("light_charge", Kind.BUILD),     // Light Blast hold-to-charge
			java.util.Map.entry("holy_charge", Kind.BUILD),      // Holy Light hold-to-charge
			java.util.Map.entry("zone_charge", Kind.BUILD),      // Shadow Zone hold-to-charge
			java.util.Map.entry("blast_charge", Kind.BUILD),     // Energy Blast hold-to-charge
			java.util.Map.entry("pulse_charge", Kind.BUILD),     // Maximum Pulse hold-to-charge
			// --- countdowns on something currently running ---
			java.util.Map.entry("overdrive_ticks", Kind.TIMER),  // Super Speed Overdrive
			java.util.Map.entry("whirl_ticks", Kind.TIMER),      // Super Speed Whirlwind
			java.util.Map.entry("total_darkness", Kind.TIMER),   // Shadow Manipulation (legacy key, unused now)
			java.util.Map.entry("hurr", Kind.TIMER),             // Wind hurricane
			java.util.Map.entry("singularity", Kind.TIMER),      // Density Manipulation ultimate
			java.util.Map.entry("crush_hold_ticks", Kind.TIMER), // Gravity Crush hold
			java.util.Map.entry("blade_charge", Kind.TIMER));    // Wind blade window

	/** The meter kind for {@code name}, or {@code null} when it is bookkeeping and must not be drawn. */
	private static Kind kindOf(String name) {
		// Fixed-duration self-flight countdowns (flameflight, rockflight) -- drain to nothing, then vanish.
		if (name.endsWith("flight") && !name.equals("flight")) {
			return Kind.TIMER;
		}
		return KIND.get(name);
	}

	private static float maxOf(String name) {
		if (name.endsWith("flight")) {
			return 100.0f; // "flight" stamina and the timed self-flight meters are all 0..100
		}
		return switch (name) {
			case "phase", "static_charge", "charge", "sparkle", "ult_charge",
					"fb_charge", "portal_charge", "stretch_charge", "sonic_charge", "storm_charge", "senses",
					"light_charge", "holy_charge", "zone_charge", "blast_charge", "pulse_charge" -> 100.0f;
			// v0.10.15: this fell through to the 500 default while Telekinesis' real max is 1000, so the
			// bar only started showing once Psi had already dropped below half -- looked like it was
			// draining from a half-empty bar. See TelekinesisHandlers.MAX_PSI.
			case "psi" -> 1000.0f;
			case "ecell" -> 1000.0f;
			case "blade_charge" -> 40.0f;
			case "whirl_ticks" -> 160.0f;
			case "hurr" -> 220.0f;
			case "overdrive_ticks" -> 600.0f;
			case "crush_hold_ticks" -> 160.0f;
			case "shadow_cloak", "field_bar", "nexus_bar" -> 100.0f;
			case "total_darkness", "singularity" -> 500.0f;
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
			case "psi" -> Component.literal("Psi");
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
			case "overdrive_ticks" -> Component.literal("Overdrive");
			case "whirl_ticks" -> Component.literal("Whirlwind");
			case "blade_charge" -> Component.literal("Wind Blade");
			case "ult_charge" -> Component.literal("Ultimate — charging");
			case "fb_charge" -> Component.literal("Focused Beam — charging");
			case "portal_charge" -> Component.literal("Portal — charging");
			case "stretch_charge" -> Component.literal("Stretch Punch — charging");
			case "sonic_charge" -> Component.literal("Scream — charging");
			case "storm_charge" -> Component.literal("Storm Bolt — charging");
			case "light_charge" -> Component.literal("Light Blast — charging");
			case "holy_charge" -> Component.literal("Holy Light — charging");
			case "zone_charge" -> Component.literal("Shadow Zone — charging");
			case "blast_charge" -> Component.literal("Energy Blast — charging");
			case "pulse_charge" -> Component.literal("Maximum Pulse — charging");
			case "crush_hold_ticks" -> Component.literal("Gravity Crush");
			case "shadow_cloak" -> Component.literal("Shadow Cloak");
			case "field_bar" -> Component.literal("Absorption Field");
			case "nexus_bar" -> Component.literal("Gravitational Nexus");
			case "ecell" -> Component.literal("Charge");
			case "senses" -> Component.literal("Enhanced Senses");
			default -> name.endsWith("flight") ? Component.literal("Flight")
					: Component.literal(capitalize(name.replace('_', ' ')));
		};
	}

	private static String capitalize(String s) {
		return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
	}
}
