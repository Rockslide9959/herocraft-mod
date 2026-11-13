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
import com.projecthero.mod.hero.visual.MutationMeters;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/**
 * The HeroPack ability HUD for the 27 mutation powers (spec section 9).
 *
 * <p>v0.17.3 (user: "I really like the Minecraft-style HUD and ability keybinds ... apply this to all the powers"): drawn from
 * the game's own pieces on the shared bottom-right column ({@link VanillaHud.Stack}), like the newest powers:
 * <pre>
 *   Shadow Strength: 120%                   per-power lines (Super Speed's packed-suit durability, Shadow Strength, Density)
 *   Super Speed - Speed                     the power's name (grey for the black-and-gray powers) + its decor
 *   Charged Punch [Hairline ====--]         "above" bars (charge-ups, running moves): Hairline meter rows
 *   Psi [boss bar ==========---]  64%       the registered / allow-listed meters, each in its own bar style
 *   +--+--+--+--+--+--+--+--+               R G Z X C V (+ H N for a power that has Utility moves): the item-cooldown sweep and
 *   |R |G |Z |X |C |V |H |N |               seconds; a running toggle gets the selection frame; a hold move fills its key; a Shift
 *   +--+--+--+--+--+--+--+--+               move with its own cooldown shows as the durability bar (its seconds in yellow)
 * </pre>
 * Hold Left Alt for the move list.
 *
 * <p>What counts as a meter at all is an explicit allow-list ({@code KIND}); everything else a power stores in its resource map
 * is bookkeeping and is never drawn. See {@link #collectMeters}.
 *
 * <p>Only rendered for experimental-power context. Thor keeps its own {@code ThorHud} (Storm Energy).
 */
public final class AbilityHud {
	/**
	 * v0.17.1: the top of what this HUD drew last frame (the power name / header line over the key row), GUI pixels --
	 * the Flash Suit visor keeps its speed dial above it.
	 */
	public static int lastTop = Integer.MAX_VALUE;

	/** The longest cooldown seen on each key since it last came off cooldown (the sweep's scale). */
	private static final int[] SEEN_MAX = new int[8];

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
		if (com.projecthero.mod.power.ThorPowers.wieldsThorWeapon(player)) { // v0.14.19: Stormbreaker too
			return;
		}
		Power power = Powers.byKey(state.activePower);
		if (power == null) {
			return;
		}

		// v0.16.7 (user): only the SELECTED power's bars -- with several elemental powers stacked, every other power's
		// always-on fuel bar (Earth, Water...) piled up under the selected one. (v0.9.3 gathered every owned power's.)
		long gameTime = client.level != null ? client.level.getGameTime() : 0L;
		List<Meter> meters = new ArrayList<>(collectMeters(state, power, gameTime));
		meters.sort((a, b) -> a.label.getString().compareToIgnoreCase(b.label.getString()));

		// 6, or 8 with the H / N utility slots (v0.13.22), or 0 for a passive-only power (v0.14.5).
		// v0.14.7: the H / N boxes only for a power that has H -- an N-only power (Super Speed's Speed Carry) shows just its six
		// core keys, and H stays the power wheel. v0.17.3: kept on the new strip -- these mutations' Utility moves really are
		// on H / N (ProjectHeroModClient: plain H / N, or Alt+H / Alt+N, send slots 7 / 8 when the selected mutation has them).
		int slots = power.abilities().size();
		if (slots > 6 && !power.hasSlot(AbilitySlot.SLOT_7)) {
			slots = 6;
		}
		List<AbilitySlot> order = new ArrayList<>();
		if (slots > 0) {
			for (AbilitySlot s : VanillaHud.ORDER) {
				order.add(s);
			}
			if (slots > 6) {
				order.add(AbilitySlot.SLOT_7);
				order.add(AbilitySlot.SLOT_8);
			}
		}
		VanillaHud.Stack stack = VanillaHud.Stack.bottomRight(graphics, order.size());
		boolean shift = player.isShiftKeyDown();
		boolean mono = AbilityHudExtras.isMono(power.key());

		// ---- the key strip
		stack.keys(order.size());
		for (int i = 0; i < order.size(); i++) {
			AbilitySlot slot = order.get(i);
			if (!power.hasSlot(slot)) {
				continue;
			}
			drawKey(stack, i, slot, power, state, gameTime, shift, client);
		}

		// ---- meters above the keys: the persistent ones nearest the keys, then the "above" Hairline rows
		// v0.14.5: registered meters flagged "above" were drawn as Hairline bars over the key row -- now Hairline meter rows.
		List<AbilityHudExtras.AboveBar> above = new ArrayList<>();
		for (java.util.Iterator<Meter> it = meters.iterator(); it.hasNext();) {
			Meter m = it.next();
			if (m.above) {
				above.add(new AbilityHudExtras.AboveBar(m.label.getString() + (m.valueText.isEmpty() ? "" : "  " + m.valueText),
						Math.max(0f, Math.min(1f, m.value / m.max)), m.color, m.textColor));
				it.remove();
			}
		}
		above.addAll(AbilityHudExtras.above(power.key(), client, state, gameTime));
		for (int k = meters.size() - 1; k >= 0; k--) { // drawn bottom-up, so they read alphabetically top-down
			Meter m = meters.get(k);
			float ratio = Math.max(0.0f, Math.min(1.0f, m.value / m.max));
			stack.meter(m.label, ratio, m.color, m.valueText, m.textColor != 0 ? m.textColor : VanillaHud.WHITE, m.style);
		}
		for (int k = above.size() - 1; k >= 0; k--) {
			AbilityHudExtras.AboveBar b = above.get(k);
			String[] lv = splitLabel(b.label());
			stack.meter(Component.literal(lv[0]), Math.max(0f, Math.min(1f, b.ratio())), b.fill(), lv[1],
					b.textColor() != 0 ? b.textColor() : VanillaHud.WHITE, MutationMeters.Style.HAIRLINE);
		}

		// ---- the power's name (v0.14.5: grey for the black-and-gray powers) and the power's decor right of it
		Component powerName = Component.translatable(power.nameKey());
		stack.title(powerName, mono ? VanillaHud.GREY : VanillaHud.WHITE);
		int nameY = stack.top();
		AbilityHudExtras.Decor decor = AbilityHudExtras.decor(power.key());
		if (decor != null) {
			int nameW = Math.min(stack.width, client.font.width(powerName));
			decor.draw(graphics, client, state, stack.x0 + nameW + 6, nameY);
		}

		// ---- per-power lines above the name
		// v0.14.16: Super Speed's packed Flash Suit durability (draws nothing when there is none: its row is kept free)
		AbilityHudExtras.Decor header = AbilityHudExtras.header(power.key());
		if (header != null) {
			stack.reserve(10);
			header.draw(graphics, client, state, stack.x0, stack.top());
		}
		if (power.key().equals("power_19_shadow_manipulation")) {
			shadowLevel(stack, client);
		}
		if (power.key().equals("power_18_density_manipulation")) {
			float d = state.resources.getOrDefault("power_18_density_manipulation/density", 100.0f);
			if (d <= 0.0f) {
				d = 100.0f;
			}
			boolean anchored = state.resources.getOrDefault("power_18_density_manipulation/anchor_until", 0.0f) > gameTime;
			Component line = Component.literal("Density: " + Math.round(d) + "%" + (anchored ? "  [ANCHOR]" : ""));
			int col = d < 100 ? 0xFF7FD0FF : (d > 100 ? 0xFFFFB24A : VanillaHud.WHITE);
			stack.line(line, col);
		}
		lastTop = stack.top() - 2;

		// ---- Left Alt: the shared move list -- each key's move with its Shift move (read from the move descriptions, the only
		// place the mutations name them) and its hold move (HoldTap)
		if (MoveListOverlay.held() && !order.isEmpty()) {
			List<String> descKeys = new ArrayList<>();
			for (Ability a : power.abilities()) {
				descKeys.add(a.descKey());
			}
			List<MoveListOverlay.Row> rows = new ArrayList<>();
			for (AbilitySlot slot : order) {
				if (!power.hasSlot(slot)) {
					continue;
				}
				Ability ability = power.ability(slot);
				Component holdMove = com.projecthero.mod.hero.HoldTap.hasHold(power.key(), ability.id())
						? Component.translatable("hud.projecthero.move_list.hold", Component.translatable(ability.nameKey() + ".hold")) : null;
				rows.add(MoveListOverlay.Row.of(MoveListOverlay.key(slot.ordinal()), Component.translatable(ability.nameKey()),
						MoveListOverlay.shiftFromDescs(slot.ordinal(), descKeys), holdMove));
			}
			stack.moveList(rows);
		}
	}

	/** One key of the strip: its cooldown, toggle, hold fill and (for Super Speed / ShiftStrips) its Shift move's cooldown. */
	private static void drawKey(VanillaHud.Stack stack, int i, AbilitySlot slot, Power power, ExperimentalState state, long gameTime,
			boolean shift, Minecraft client) {
		Ability ability = power.ability(slot);
		int idx = slot.ordinal();
		// v0.17.1: a HOLD key may light its box too, while its own toggle mode runs (Chlorokinesis' Verdant Form / Glide)
		boolean toggled = (ability.activation() == AbilityActivation.TOGGLE || ability.activation() == AbilityActivation.HOLD)
				&& state.activeToggles.contains(power.key() + "/" + ability.id());
		Long readyAt = state.abilityReadyAt.get(power.key() + "/" + ability.id());
		int cdRemain = readyAt == null ? 0 : (int) Math.max(0L, readyAt - gameTime);
		// v0.14.5: Super Strength's Shift+G (Thunderclap) and Shift+V (Rip & Hurl) keep their own cooldowns in the clap_cd /
		// rip_cd resources (ticks) -- the G / V key shows whichever of the two moves is longer.
		if (power.key().equals("power_01_super_strength") && (slot == AbilitySlot.SLOT_2 || slot == AbilitySlot.SLOT_5)) {
			String extra = slot == AbilitySlot.SLOT_2 ? "clap_cd" : "rip_cd";
			int alt = Math.round(state.resources.getOrDefault("power_01_super_strength/" + extra, 0.0f));
			if (alt > cdRemain) {
				cdRemain = alt;
			}
		}
		// the Shift move's own cooldown, when the power keeps one for this key: ticks left and the full length
		int shiftLeft = 0;
		float shiftFull = 0f;
		boolean hasShift = false;
		// v0.14.7: Super Speed's Shift+R / Shift+G / Shift+X (Mach Punch, Speed Vortex, Speed Sweep) keep absolute ready-at
		// times in their own resources; v0.17.1: so do Shift+Z Lightning Throw and Shift+V Afterimage Decoys.
		String speedExtra = !power.key().equals("power_04_super_speed") ? null : switch (slot) {
			case SLOT_1 -> "mach_ready";
			case SLOT_2 -> "vortex_ready";
			case SLOT_3 -> "sweep_ready";
			case SLOT_4 -> "lightning_ready";
			case SLOT_5 -> "decoy_ready";
			default -> null;
		};
		if (speedExtra != null) {
			float readyAtAlt = state.resources.getOrDefault("power_04_super_speed/" + speedExtra, 0.0f);
			shiftLeft = (int) Math.max(0L, (long) readyAtAlt - gameTime);
			shiftFull = speedShiftCooldown(speedExtra);
			hasShift = true;
		} else {
			// v0.17.1: the same for any power that registers a Shift strip (ShiftStrips: a countdown in ticks + its full)
			String strip = com.projecthero.mod.hero.visual.ShiftStrips.get(power.key(), slot);
			if (strip != null) {
				shiftLeft = Math.round(state.resources.getOrDefault(power.key() + "/" + strip, 0.0f));
				shiftFull = state.resources.getOrDefault(power.key() + "/" + strip + "_full", 0.0f);
				hasShift = true;
			}
		}
		if (cdRemain <= 0) {
			SEEN_MAX[idx] = 0;
		} else if (cdRemain > SEEN_MAX[idx]) {
			SEEN_MAX[idx] = cdRemain;
		}
		float plainFrac = cdRemain <= 0 ? 0f : Math.min(1f, cdRemain / (float) Math.max(1, SEEN_MAX[idx]));
		float shiftFrac = shiftLeft <= 0 ? 0f : shiftFull <= 0.5f ? 1f : Math.min(1f, shiftLeft / shiftFull);
		// v0.17.1 (user): the Shift move's wait is not folded into the key -- while Shift is held the key shows the Shift move
		// (yellow letter, its sweep and seconds) and the plain move's wait becomes the durability bar
		boolean showShift = hasShift && shift;
		float cooldown = showShift ? shiftFrac : plainFrac;
		int seconds = VanillaHud.seconds(showShift ? shiftLeft : cdRemain);
		float other = hasShift ? (showShift ? plainFrac : shiftFrac) : 0f;
		stack.slot(i, VanillaHud.keyLabel(slot), true, showShift, cooldown, seconds, other, toggled);
		if (hasShift && !showShift && cdRemain <= 0 && shiftLeft > 0) {
			// the plain move is ready but its Shift move still waits: the Shift move's seconds, in yellow, where a stack count sits
			String n = String.valueOf(VanillaHud.seconds(shiftLeft));
			int ix = stack.stripX() + 3 + i * VanillaHud.SLOT, iy = stack.stripY() + 3;
			graphicsPush(stack, n, ix + 17 - client.font.width(n), iy + 9, VanillaHud.YELLOW);
		}
		// v0.16.6: a key with a hold move fills up while it is held (full = the hold move fires)
		stack.hold(i, holdProgress(idx, power.key(), ability.id(), gameTime));
	}

	/** Shadowed text above the slot contents (the stack-count layer). */
	private static void graphicsPush(VanillaHud.Stack stack, String text, int x, int y, int colour) {
		stack.g.pose().pushPose();
		stack.g.pose().translate(0f, 0f, 200f);
		stack.g.drawString(stack.font, text, x, y, colour, true);
		stack.g.pose().popPose();
	}

	/**
	 * An "above" bar's label split into the row's label and its value: "Charged Punch -- RELEASE" -> "Charged Punch" +
	 * "RELEASE", "Maximum Effort  12s" -> "Maximum Effort" + "12s".
	 */
	private static String[] splitLabel(String label) {
		int dash = label.indexOf(" — ");
		if (dash > 0) {
			return new String[] { label.substring(0, dash).trim(), label.substring(dash + 3).trim() };
		}
		int gap = label.indexOf("  ");
		if (gap > 0) {
			return new String[] { label.substring(0, gap).trim(), label.substring(gap).trim() };
		}
		return new String[] { label, "" };
	}

	/**
	 * A meter row. {@code style}: the user's bar style (Slab / Meter / Gauge / Hairline); {@code above}: drawn with the "above"
	 * Hairline rows (charge-ups, running moves); {@code textColor}: the value's colour (0 = white); {@code valueText}: the
	 * value right of the bar (may be empty).
	 */
	private record Meter(Component label, float value, float max, int color, MutationMeters.Style style, boolean above, int textColor,
			String valueText) {
		Meter(Component label, float value, float max, int color, boolean hairline, String valueText, int textColor) {
			this(label, value, max, color, hairline ? MutationMeters.Style.HAIRLINE : MutationMeters.Style.METER, false, textColor, valueText);
		}
	}

	/** v0.16.6: game time each ability key went down (plain, no Sneak), or -1. */
	private static final long[] HOLD_DOWN_SINCE = { -1, -1, -1, -1, -1, -1, -1, -1 };

	/** v0.17.1: the full length of a Super Speed Shift move's cooldown (the durability bar's scale). */
	private static int speedShiftCooldown(String readyKey) {
		int base = switch (readyKey) {
			case com.projecthero.mod.hero.power.p04.SuperSpeedMoves.MACH_READY -> com.projecthero.mod.hero.power.p04.SuperSpeedMoves.MACH_CD;
			case com.projecthero.mod.hero.power.p04.SuperSpeedMoves.VORTEX_READY -> com.projecthero.mod.hero.power.p04.SuperSpeedMoves.VORTEX_CD;
			case com.projecthero.mod.hero.power.p04.SuperSpeedMoves.SWEEP_READY -> com.projecthero.mod.hero.power.p04.SuperSpeedMoves.SWEEP_CD;
			case com.projecthero.mod.hero.power.p04.SuperSpeedMoves.LIGHTNING_READY -> com.projecthero.mod.hero.power.p04.SuperSpeedMoves.LIGHTNING_CD;
			case com.projecthero.mod.hero.power.p04.SuperSpeedMoves.DECOY_READY -> com.projecthero.mod.hero.power.p04.SuperSpeedMoves.DECOY_CD;
			default -> 0;
		};
		return com.projecthero.mod.hero.HeroConfig.get().scaledCooldown(base);
	}

	/** 0..1 of the way to a key's hold move (0 = not held / no hold move / a Shift press). */
	private static float holdProgress(int i, String powerKey, String abilityId, long gameTime) {
		if (i >= HOLD_DOWN_SINCE.length || i >= com.projecthero.mod.client.ModKeyBindings.ABILITY_SLOTS.length) {
			return 0f;
		}
		boolean down = com.projecthero.mod.client.ModKeyBindings.ABILITY_SLOTS[i].isDown();
		if (!down || !com.projecthero.mod.hero.HoldTap.hasHold(powerKey, abilityId)) {
			HOLD_DOWN_SINCE[i] = -1;
			return 0f;
		}
		if (HOLD_DOWN_SINCE[i] < 0) {
			HOLD_DOWN_SINCE[i] = net.minecraft.client.gui.screens.Screen.hasShiftDown() ? Long.MAX_VALUE : gameTime;
		}
		if (HOLD_DOWN_SINCE[i] == Long.MAX_VALUE) {
			return 0f;
		}
		return Math.min(1f, (gameTime - HOLD_DOWN_SINCE[i]) / (float) com.projecthero.mod.hero.HoldTap.HOLD_TICKS);
	}

	/** v0.13.11: powers whose bars are all Hairline bars. */
	private static final java.util.Set<String> HAIRLINE_POWERS = java.util.Set.of("power_08_pyrokinesis");

	/** Shadow Manipulation's ambient-light strength line, above the power name. */
	private static void shadowLevel(VanillaHud.Stack stack, Minecraft client) {
		if (client.player == null || client.level == null) {
			return;
		}
		float tier = com.projecthero.mod.hero.power.p19.ShadowManipulationHandlers.tier(
				client.level, client.player.blockPosition());
		int pct = Math.round(tier * 100);
		int color = tier >= 1.3f ? 0xFFB98CFF : tier >= 1.0f ? 0xFF9AA6D0 : tier >= 0.75f ? 0xFFE8C98A : 0xFFFFC24A;
		stack.line(Component.literal("Shadow Strength: " + pct + "%"), color);
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
					String valueText = spec.showValue() ? Math.round(Math.max(0f, Math.min(1f, v / spec.max())) * 100) + "%" : "";
					// v0.14.5: an "above" meter is a Hairline row with the charge-ups (v0.17.3: the vanilla Hairline cut)
					out.add(new Meter(Component.literal(spec.label()), v, spec.max(), spec.color(),
							spec.above() ? MutationMeters.Style.HAIRLINE : spec.style(), spec.above(), spec.textColor(), valueText));
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
			String valueText = "";
			int valueColour = 0;
			// v0.17.3: a hold-to-charge meter ("Stretch Punch -- charging") shows its name and how far it has charged
			if (name.endsWith("_charge") && !name.equals("static_charge") && !name.equals("blade_charge")) {
				String[] lv = splitLabel(text.getString());
				text = Component.literal(lv[0]);
				valueText = Math.round(Math.max(0f, Math.min(1f, value / max)) * 100) + "%";
			}
			// Telekinesis' Psi burnout is the one meter state that is a hard lockout rather than a
			// shortage, so it gets its own red bar and label instead of just reading as "empty".
			if (name.equals("psi") && state.resources.getOrDefault(prefix + "burnout_until", 0.0f) > gameTime) {
				color = 0xFFE05252;
				text = Component.literal("Psi");
				valueText = "BURNED OUT";
				valueColour = VanillaHud.RED;
			}
			// v0.10.21: Energy Absorption's bar is always on screen -- show the exact percentage next
			// to the label rather than leaving the player to eyeball the fill.
			if (name.equals("energy")) {
				text = Component.literal("Energy");
				valueText = String.format(java.util.Locale.ROOT, "%.2f", value / max * 100.0f) + "%";
			}
			out.add(new Meter(text, value, max, color, HAIRLINE_POWERS.contains(power.key()), valueText, valueColour));
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
