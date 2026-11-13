package com.projecthero.mod.client.gui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.lwjgl.glfw.GLFW;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.client.ModKeyBindings;
import com.projecthero.mod.hero.visual.MutationMeters;
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
 *   <li><b>Top-right</b> (v0.16.7 user redesign) -- one slim telemetry strip (ALT, SPD, X Y Z, clock / day).</li>
 *   <li><b>Top-left</b> -- the suit picture: a dial (segmented INTEGRITY / REACTOR rings) around a wireframe suit that
 *       reddens with damage (the Hulkbuster: its six parts).</li>
 *   <li><b>Bottom-right</b> (v0.17.3, user: "I really like the Minecraft-style HUD and ability keybinds ... apply this to all
 *       the powers") -- the shared Minecraft-style column ({@link VanillaHud.Stack}): the six keys (R G Z X C V, read from
 *       the worn suit's {@code abilityInSlot()}, each with its item icon), the INTEGRITY / REACTOR gauges and the meters that
 *       matter right now (AIR, HEAT, HEAT SINK, FLIGHT burst, OVERLOAD, WRIST LASER, MISSILE reload), the suit's full name,
 *       the gauges' notes and the status chips as tags (mob highlight, weapon-wheel binding, blades, entity scan, missiles
 *       ready, Protocol Phoenix cooldown), the Mark II ceiling warning. Hold Left Alt for the move list.</li>
 *   <li><b>Target scanner</b> -- a holographic reticle on the target and a threat card beside the crosshair.</li>
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
		int x = IronManUiLayout.rightColumnX(g.guiWidth()); // v0.16.7 (user): the HUD is on the left; this column (strip, meters, chips) is top-right
		int w = IronManUiLayout.HUD_W;

		ItemStack helmet = player.getItemBySlot(EquipmentSlot.HEAD);
		if (!(helmet.getItem() instanceof IronManArmorItem helmetPiece)) {
			// no helmet: a Tony Stark player who isn't suited up can always try to call the armour (the server
			// decides whether anything is actually reachable).
			int y = IronManUiLayout.HUD_Y;
			// v0.15.9: one of your suits standing as a sentry -- its mode, energy and integrity
			y = com.projecthero.mod.client.ironman.IronManSentryClient.renderHud(g, font, x, y, w);
			// v0.15.1: suit calling and Protocol Phoenix need the Stark Glasses (Stark Gear slot, Shift+N)
			boolean glasses = com.projecthero.mod.ironman.gear.StarkGear.canCall(player); // v0.15.4: or the Colantotte Bracelets
			// v0.15.7: no gear on -> no chip at all (it used to say "needs glasses"); the bracelets call the Mark 7
			if (glasses && !wearingAnyIronMan(player)) {
				String call = Component.translatable(com.projecthero.mod.ironman.gear.StarkGear.hasBracelets(player)
						? "hud.projecthero.ironman.call_mark_7" : "hud.projecthero.ironman.call_armor",
						ModKeyBindings.ABILITY_SLOTS[5].getTranslatedKeyMessage()).getString();
				IronManGui.chip(g, font, x, y, w, call, IronManGui.CYAN);
				y += 13;
			}
			// v0.15.7: Protocol Phoenix belongs to the glasses -- the bracelets never show it
			boolean phoenix = com.projecthero.mod.ironman.gear.StarkGear.phoenixArmed(player);
			long phoenixIn = state.phoenixReadyAt - now;
			if (phoenix && phoenixIn > 0) {
				IronManGui.chip(g, font, x, y, w, Component.translatable("hud.projecthero.ironman.phoenix_cooldown",
						IronManUiLayout.mmss(phoenixIn)).getString(), IronManGui.GOLD);
			} else if (phoenix) {
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
		// v0.16.7 (user) Era I: the Shift+C emergency eject bar -- drawn first, so it shows on a wrecked (dark-visor) suit too
		ejectBar(g, font, player, suit, now);
		IronManFoldHoldBar.render(g, font, player, suit, now, delta.getGameTimeDeltaPartialTick(false)); // v0.16.7 (user) Era II: the Shift+C fold hold
		if (com.projecthero.mod.ironman.gantry.StarkGantryFloorBlockEntity.visorDark(player)) {
			// v0.15.15, explicit user request: on a Stark Gantry the HUD goes off the moment the arms lift the faceplate
			// away (and comes on once it is fitted) -- the display lives in the faceplate, not the helmet
			return;
		}

		// v0.16.1 comic rework: no suit energy -- integrity is THE bar; at 0 the suit is disabled and the visor goes dark
		float maxIntegrity = IronManEnergy.maxIntegrity(suitId);
		float integrity = state.suitIntegrity.getOrDefault(suitId, maxIntegrity);
		float integrityFrac = IronManUiLayout.clamp01(integrity / maxIntegrity);
		boolean overloaded = state.overloadUntil > now;
		boolean disabled = integrity <= 0.001f;
		boolean offline = disabled || overloaded || state.brownout.contains(suitId); // v0.16.7: an Era I brownout too

		// v0.11.12, explicit user request: Mark 1's HUD is deliberately stripped down to ENERGY, INTEGRITY, the
		// ability list and its own mob-highlight duration -- no altitude, speed, clock, target readout or Phoenix.
		boolean minimalHud = "mark_1".equals(suitId);
		boolean visorClosed = !player.getAttachedOrElse(ModAttachments.IRON_MAN_FACEPLATE_OPEN, false);
		if (visorClosed && disabled) {
			// v0.16.1: a disabled suit -- the visor is dark, only the failure readout flickers through
			darkVisor(g, font, now, minimalHud);
			com.projecthero.mod.client.ironman.IronManBattleDamage.renderVisorCracks(g, 0f, minimalHud);
			return;
		}
		if (visorClosed) {
			visor(g, minimalHud, offline, now);
			com.projecthero.mod.client.ironman.IronManBattleDamage.renderVisorCracks(g, integrityFrac, minimalHud); // v0.14.29 agent F
		} else {
			// v0.14.26: the HUD lives in the faceplate -- lift it and the display goes dark (just a reminder chip)
			IronManGui.chip(g, font, x, IronManUiLayout.HUD_Y, w,
					Component.translatable("hud.projecthero.ironman.hud_off_visor").getString(), IronManGui.TEXT_DIM);
			return;
		}

		// v0.16.7 (user): the slim telemetry strip (ALT, SPD, X Y Z, clock / day) stays a helmet readout, top-right
		if (!minimalHud) {
			telemetryStrip(g, font, client, player);
		}
		// v0.17.3: the dial + wireframe suit (Hulkbuster: its six parts) stay a visor readout, top-left; the suit's name,
		// INTEGRITY, REACTOR and their notes moved to the Minecraft-style column below
		suitDial(g, player, state, suit, now, minimalHud, integrity, maxIntegrity);
		boostTag(g, font, player, state, suit, now);

		// ---- the target scanner (Mark II+, visor closed): v0.16.7 (user) holographic reticle + threat card
		boolean hbBlind = com.projecthero.mod.ironman.HulkbusterParts.SUIT_ID.equals(suit.id())
				&& com.projecthero.mod.ironman.HulkbusterParts.broken(com.projecthero.mod.ironman.HulkbusterParts.of(player)[0]);
		// v0.16.7 (user): a Hulkbuster with its head broken has no targeting HUD
		if (!minimalHud && visorClosed && !hbBlind && !MoveListOverlay.held()) { // the Left-Alt move list takes the space while held
			com.projecthero.mod.client.ironman.IronManTargetScanner.renderPanel(g, font, player, suit, delta.getGameTimeDeltaPartialTick(false));
		}

		column(g, font, player, state, suit, suitId, now, minimalHud, offline, integrity, maxIntegrity, overloaded);
	}

	/**
	 * v0.17.3 (user: "I really like the Minecraft-style HUD and ability keybinds ... apply this to all the powers"): the
	 * suit's corner block on the shared Minecraft-style column ({@link VanillaHud.Stack}), bottom-right:
	 * <pre>
	 *   Altitude warning / SYSTEMS FROZEN          warnings (wrapped)
	 *   HIGHLIGHT ON  X: Unibeam  abs 40% ...      tags: the old status chips, the gauges' notes and exact values
	 *   MK III CLASSIC                             title: the suit's full name
	 *   MISSILES [Gauge ====--]  reload 4s         the contextual meters, only while relevant (AIR, HEAT, HEAT SINK, FLIGHT,
	 *   FLIGHT   [Gauge ======]  12s               OVERLOAD / WRIST LASER, MISSILES reload) -- the user's Gauge style, as before
	 *   REACTOR  [Gauge =======-]  84%             Era I / II
	 *   INTEGRITY [Gauge =========]  96%
	 *   +--+--+--+--+--+--+                        R G Z X C V: each move's item icon, the key in the cell's corner, cooldown
	 *   |R |G |Z |X |C |V |                        sweep + seconds, the Shift move's cooldown on the durability bar, a running
	 *   +--+--+--+--+--+--+                        toggle framed, greyed + red while the suit is offline
	 * </pre>
	 * Mark I keeps its stripped-down set (INTEGRITY, HEAT, its mob-highlight timer) in amber.
	 */
	private static void column(GuiGraphics g, Font font, Player player, TonyStarkState state, IronManSuit suit, String suitId, long now,
			boolean retro, boolean offline, float integrity, float maxIntegrity, boolean overloaded) {
		int accent = retro ? IronManGui.AMBER : IronManGui.CYAN;
		VanillaHud.Stack s = VanillaHud.Stack.bottomRight(g, 6).keys(6);
		List<MoveListOverlay.Row> rows = keys(s, font, player, state, suit, suitId, now, retro, offline);

		// ---- INTEGRITY and (Era I / II) REACTOR
		boolean reactor = suit.hasReactor();
		float iFrac = IronManUiLayout.clamp01(integrity / Math.max(1f, maxIntegrity));
		float cap = Math.max(1f, suit.energyCapacity());
		float en = reactor ? Math.max(0f, Math.min(cap, state.suitEnergy.getOrDefault(suit.id(), cap))) : 0f;
		float eFrac = en / cap;
		boolean brownout = state.brownout.contains(suit.id());
		boolean low = reactor && !brownout && eFrac < IronManEnergy.LOW_POWER;
		boolean critical = iFrac < 0.35f;
		boolean blink = (now % 20) < 12;
		int iFill = retro ? IronManGui.AMBER : iFrac < 0.3f ? IronManGui.RED : iFrac < 0.6f ? IronManGui.ORANGE : HOLO_FILL;
		int eFill = brownout ? ((now / 6) % 2 == 0 ? IronManGui.RED : 0xFF601010) : low ? IronManGui.ORANGE : retro ? IronManGui.AMBER : REACTOR_FILL;
		s.meter(Component.translatable("hud.projecthero.vanilla.ironman.integrity"), iFrac, iFill, pctText(iFrac),
				critical && blink ? VanillaHud.RED : retro ? IronManGui.AMBER : VanillaHud.WHITE, MutationMeters.Style.GAUGE);
		if (reactor) {
			s.meter(Component.translatable("hud.projecthero.vanilla.ironman.reactor"), eFrac, eFill, pctText(eFrac),
					brownout ? VanillaHud.RED : retro ? IronManGui.AMBER : VanillaHud.WHITE, MutationMeters.Style.GAUGE);
		}

		// ---- contextual meters, only while relevant
		if (!retro && suit.airTankSeconds() > 0 && state.suitAir < 0.999f) {
			float airFrac = IronManUiLayout.clamp01(state.suitAir);
			s.meter(Component.translatable("hud.projecthero.vanilla.ironman.air"), airFrac, airFrac < 0.25f ? IronManGui.RED : 0xFF5BC8FF,
					Math.round(airFrac * suit.airTankSeconds()) + "s", VanillaHud.WHITE, MutationMeters.Style.GAUGE);
		}
		float maxHeat = IronManAbilities.flamethrowerMaxHeat(suit);
		if ((hasAbility(suit, IronManAbilities.FLAMETHROWER) || suit.hasWeaponWheel() || com.projecthero.mod.ironman.ability.IronManMark6.isKitSuit(suitId)) // v0.15.4
				&& state.flamethrowerHeat > 0.5f) {
			float heatFrac = IronManUiLayout.clamp01(state.flamethrowerHeat / maxHeat);
			s.meter(Component.translatable("hud.projecthero.vanilla.ironman.heat"), heatFrac, heatFrac > 0.8f ? IronManGui.RED : 0xFFFFA23C,
					IronManUiLayout.pct(heatFrac) + " " + IronManUiLayout.amount(state.flamethrowerHeat, maxHeat),
					retro ? IronManGui.AMBER : VanillaHud.WHITE, MutationMeters.Style.GAUGE);
		}
		// v0.16.7 (user) Era II stage 3: the Mk IX Inferno's heat sink -- always shown while it is worn (HOT from 50, FULL at 100)
		if (com.projecthero.mod.ironman.ability.IronManMark9.SUIT.equals(suitId)) {
			float sink = com.projecthero.mod.ironman.ability.IronManMark9.syncedHeat(player);
			float sinkFrac = IronManUiLayout.clamp01(sink / com.projecthero.mod.ironman.ability.IronManMark9.HEAT_MAX);
			String sinkValue = Math.round(sink) + "%"; // whole points: the sink is a 0-100 gauge
			if (sink >= com.projecthero.mod.ironman.ability.IronManMark9.HEAT_MAX - 0.5f) {
				sinkValue += " " + Component.translatable("hud.projecthero.ironman.heat_sink.full").getString();
			} else if (sink >= com.projecthero.mod.ironman.ability.IronManMark9.HOT) {
				sinkValue += " " + Component.translatable("hud.projecthero.ironman.heat_sink.hot").getString();
			}
			s.meter(Component.translatable("hud.projecthero.vanilla.ironman.heat_sink"), sinkFrac,
					sink >= com.projecthero.mod.ironman.ability.IronManMark9.HEAT_MAX - 0.5f ? IronManGui.RED
							: sink >= com.projecthero.mod.ironman.ability.IronManMark9.HOT ? 0xFFFF6A1A : 0xFFFFA23C,
					sinkValue, VanillaHud.WHITE, MutationMeters.Style.GAUGE);
		}
		if (!retro && state.timedFlightUntil > now) {
			long left = state.timedFlightUntil - now;
			s.meter(Component.translatable("hud.projecthero.vanilla.ironman.flight"), left / (float) IronManAbilities.TIMED_FLIGHT_TICKS,
					IronManGui.BLUE, IronManUiLayout.secs(left), VanillaHud.WHITE, MutationMeters.Style.GAUGE);
		}
		if (!retro && overloaded) {
			long left = state.overloadUntil - now;
			s.meter(Component.translatable("hud.projecthero.vanilla.ironman.overload"), left / (float) IronManAbilities.OVERLOAD_TICKS,
					IronManGui.RED, IronManUiLayout.secs(left), VanillaHud.RED, MutationMeters.Style.GAUGE);
		} else if (!retro && state.wristLaserUntil > now) {
			long left = state.wristLaserUntil - now;
			s.meter(Component.translatable("hud.projecthero.vanilla.ironman.wrist_laser"), left / (float) IronManAbilities.WRIST_LASER_TICKS,
					0xFFFF3344, IronManUiLayout.secs(left), VanillaHud.WHITE, MutationMeters.Style.GAUGE);
		}
		boolean missilesReady = true;
		if (!retro && suit.missileCount() > 0 && !com.projecthero.mod.ironman.ability.IronManMark3.isKitSuit(suitId)
				&& !com.projecthero.mod.ironman.ability.IronManMark6.isKitSuit(suitId)) {
			String key = suitId + "/" + IronManAbilities.MICRO_MISSILES;
			Long readyAt = state.abilityReadyAt.get(key);
			long left = readyAt == null ? 0 : readyAt - now;
			if (left > 0) {
				missilesReady = false;
				s.meter(Component.translatable("hud.projecthero.vanilla.ironman.missiles"), 1f - cooldownFrac(key, readyAt, now), IronManGui.GOLD,
						Component.translatable("hud.projecthero.ironman.reload", IronManUiLayout.secs(left)).getString(), VanillaHud.WHITE,
						MutationMeters.Style.GAUGE);
			}
		}

		// ---- the title: the suit's full name ("MK III CLASSIC")
		String name = (Component.translatable(suit.nameKey()).getString() + " "
				+ Component.translatable(suit.displayKey()).getString()).toUpperCase(java.util.Locale.ROOT);
		s.wrapped(Component.literal(name), accent); // long comic names ("MK VII SILVER CENTURION") wrap rather than cut short

		// ---- tags: the gauges' notes and exact values, then the old status chips
		List<VanillaHud.Tag> tags = new ArrayList<>();
		if (critical && !offline && !retro) {
			tags.add(new VanillaHud.Tag(Component.translatable("hud.projecthero.ironman.status.critical").getString(),
					blink ? VanillaHud.RED : 0xFFFF9090));
		} else {
			tags.add(new VanillaHud.Tag(Component.translatable("hud.projecthero.ironman.status.absorb", Math.round(suit.absorbShare() * 100f))
					.getString(), VanillaHud.GREY));
		}
		tags.add(new VanillaHud.Tag(Component.translatable("hud.projecthero.vanilla.ironman.integrity_exact", Math.round(integrity),
				Math.round(maxIntegrity)).getString(), retro ? IronManGui.AMBER : VanillaHud.WHITE));
		if (reactor) {
			tags.add(new VanillaHud.Tag(Component.translatable("hud.projecthero.vanilla.ironman.reactor_exact", Math.round(en), Math.round(cap))
					.getString(), retro ? IronManGui.AMBER : VanillaHud.WHITE));
			if (brownout) {
				tags.add(new VanillaHud.Tag(Component.translatable("hud.projecthero.ironman.status.brownout").getString(),
						(now / 10) % 2 == 0 ? VanillaHud.RED : 0xFFFF9090));
			} else if (low) {
				tags.add(new VanillaHud.Tag(Component.translatable("hud.projecthero.ironman.status.low_power").getString(), VanillaHud.GOLD));
			} else if (com.projecthero.mod.ironman.ability.IronManSpaceBoost.boosting(player)) {
				// v0.16.7 (user): the Mk VIII Flight Boost drinks the reactor at 2.5x
				tags.add(new VanillaHud.Tag(Component.translatable("hud.projecthero.ironman.status.boost_drain").getString(), VanillaHud.GOLD));
			} else if (gantryCached(player, now)) {
				tags.add(new VanillaHud.Tag(Component.translatable("hud.projecthero.ironman.status.gantry",
						Math.round(IronManEnergy.GANTRY_CHARGE_PER_SECOND)).getString(), VanillaHud.GREEN));
			} else {
				tags.add(new VanillaHud.Tag(Component.translatable("hud.projecthero.ironman.status.regen", fmt1(suit.energyRegenPerSecond()))
						.getString(), VanillaHud.GREY));
			}
		}
		if (hasAbility(suit, IronManAbilities.MOB_HIGHLIGHT_TOGGLE)) {
			boolean on = player.getAttachedOrElse(ModAttachments.IRON_MAN_HIGHLIGHT_ON, false); // v0.15.4: wearer-only sync
			String t = Component.translatable(on ? "hud.projecthero.ironman.chip.highlight_on"
					: "hud.projecthero.ironman.chip.highlight_off").getString();
			if (on && retro) {
				long until = IronManAbilities.mark1MobHighlightUntil(state, suitId);
				if (until > now) {
					t += " " + IronManUiLayout.secs(until - now);
				}
			}
			tags.add(new VanillaHud.Tag(t, on ? (retro ? IronManGui.AMBER : VanillaHud.GREEN) : VanillaHud.GREY));
		}
		if (!retro) {
			if (suit.passiveHighlightRange() > 0.0) {
				tags.add(new VanillaHud.Tag(Component.translatable("hud.projecthero.ironman.chip.scan", (int) suit.passiveHighlightRange()).getString(),
						VanillaHud.GREY));
			}
			if (suit.hasWeaponWheel()) {
				tags.add(new VanillaHud.Tag(Component.translatable("hud.projecthero.ironman.chip.wheel",
						Component.translatable("hud.projecthero.ironman.ability." + state.weaponWheelChoice)).getString(), VanillaHud.GOLD));
			}
			if (player.getAttachedOrElse(ModAttachments.IRON_MAN_BLADES, false)) {
				tags.add(new VanillaHud.Tag(Component.translatable("hud.projecthero.ironman.chip.blades").getString(), accent));
			}
			if (suit.missileCount() > 0 && !com.projecthero.mod.ironman.ability.IronManMark3.isKitSuit(suitId)
					&& !com.projecthero.mod.ironman.ability.IronManMark6.isKitSuit(suitId) && missilesReady) {
				tags.add(new VanillaHud.Tag(Component.translatable("hud.projecthero.ironman.missiles", suit.missileCount()).getString(),
						VanillaHud.GOLD));
			}
			long phoenixIn = state.phoenixReadyAt - now;
			if (phoenixIn > 0 && com.projecthero.mod.ironman.gear.StarkGear.phoenixArmed(player)) { // v0.15.7: glasses only
				tags.add(new VanillaHud.Tag(Component.translatable("hud.projecthero.ironman.phoenix_cooldown",
						IronManUiLayout.mmss(phoenixIn)).getString(), VanillaHud.GOLD));
			}
		}
		s.tags(tags);

		// ---- warnings (wrapped to the column width; nothing may run off the side)
		if (!retro && suit.altitudeCeiling() > 0.0) {
			double ceiling = suit.altitudeCeiling();
			if (player.getY() >= ceiling) {
				s.wrapped(Component.translatable("hud.projecthero.ironman.systems_frozen"), VanillaHud.RED);
			} else if (player.getY() >= ceiling - 20.0) {
				s.wrapped(Component.translatable("hud.projecthero.ironman.altitude_warning", (int) ceiling), VanillaHud.GOLD);
			}
		}
		// v0.16.7 (user): no SUIT CRITICAL banner / SUIT OFFLINE line -- the INTEGRITY value blinks CRITICAL and the keys
		// grey out (red wash) while the suit is offline

		if (MoveListOverlay.held()) {
			s.moveList(rows); // v0.16.6: the shared move list
		}
	}

	// ------------------------------------------------------------------ v0.16.7 (user) top-left telemetry strip

	/**
	 * The slim telemetry strip: ALT, SPD, X Y Z and the clock / day, segments separated by thin dividers, on one line --
	 * wrapping onto more only when the screen is too narrow (it never runs into the status block). Returns the y under it.
	 */
	private static int telemetryStrip(GuiGraphics g, Font font, Minecraft client, Player player) {
		if (client.level == null) {
			return IronManUiLayout.HUD_Y;
		}
		double speed = player.getDeltaMovement().horizontalDistance() * 20.0;
		long dayTime = client.level.getDayTime();
		long tod = ((dayTime + 6000L) % 24000L + 24000L) % 24000L;
		List<Component> segs = new ArrayList<>();
		segs.add(seg("hud.projecthero.ironman.strip.alt", (int) Math.round(player.getY())));
		segs.add(seg("hud.projecthero.ironman.strip.spd", (int) Math.round(speed)));
		segs.add(seg("hud.projecthero.ironman.strip.pos", (int) Math.floor(player.getX()), (int) Math.floor(player.getY()),
				(int) Math.floor(player.getZ())));
		segs.add(seg("hud.projecthero.ironman.strip.clock",
				String.format(java.util.Locale.ROOT, "%02d:%02d", tod / 1000L, (tod % 1000L) * 60L / 1000L), dayTime / 24000L + 1L));
		int maxW = IronManUiLayout.stripMaxWidth(g.guiWidth()) - 10;
		int[] widths = new int[segs.size()];
		for (int i = 0; i < widths.length; i++) {
			widths[i] = Math.min(maxW, font.width(segs.get(i)));
		}
		int sep = 11;
		int[] line = IronManUiLayout.flowSegments(widths, sep, maxW);
		int lines = line[line.length - 1] + 1;
		int[] lineW = new int[lines];
		for (int i = 0; i < widths.length; i++) {
			lineW[line[i]] += (lineW[line[i]] > 0 ? sep : 0) + widths[i];
		}
		int innerW = 0;
		for (int lw : lineW) {
			innerW = Math.max(innerW, lw);
		}
		int h = lines * 10 + 5;
		int bw = innerW + 10;
		int x0 = g.guiWidth() - IronManUiLayout.HUD_X - bw; // v0.16.7 (user): top-right (the status block is top-left now)
		int y0 = IronManUiLayout.HUD_Y;
		g.fill(x0, y0, x0 + bw, y0 + h, 0x8C050B12);
		g.renderOutline(x0, y0, bw, h, IronManGui.alpha(IronManGui.CYAN_DIM, 0.55f));
		g.fill(x0, y0, x0 + 2, y0 + h, IronManGui.alpha(IronManGui.CYAN, 0.9f)); // the brighter left accent edge
		g.fill(x0 + bw - 5, y0 - 1, x0 + bw - 1, y0, IronManGui.alpha(IronManGui.RED, 0.8f)); // a small red tick
		int cx = x0 + 6;
		int curLine = 0;
		for (int i = 0; i < segs.size(); i++) {
			if (line[i] != curLine) {
				curLine = line[i];
				cx = x0 + 6;
			} else if (cx > x0 + 6) {
				g.fill(cx + 5, y0 + 4 + curLine * 10, cx + 6, y0 + 10 + curLine * 10, IronManGui.alpha(IronManGui.CYAN_DIM, 0.8f));
				cx += sep;
			}
			g.drawString(font, IronManGui.fit(font, segs.get(i), maxW), cx, y0 + 3 + curLine * 10, IronManGui.TEXT_DIM, false);
			cx += widths[i];
		}
		return y0 + h + 4;
	}

	/** One strip segment: the label dim, the numbers bright. */
	private static Component seg(String key, Object... args) {
		Object[] bright = new Object[args.length];
		for (int i = 0; i < args.length; i++) {
			bright[i] = Component.literal(String.valueOf(args[i])).withStyle(s -> s.withColor(IronManGui.TEXT & 0xFFFFFF));
		}
		return Component.translatable(key, bright).withStyle(s -> s.withColor(IronManGui.CYAN_DIM & 0xFFFFFF));
	}

	// ------------------------------------------------------------------ v0.16.7 (user) top-right suit status

	/** Pale holographic blue for the wireframe, and the healthy integrity fill. */
	private static final int HOLO = 0xFFA9DDF2;
	private static final int HOLO_FILL = 0xFFBFEFFF;
	private static final int REACTOR_FILL = 0xFF4FE0FF;

	/**
	 * v0.16.7 (user) top-left suit picture, kept as a visor readout in v0.17.3 (the gauges, name and notes moved to the
	 * Minecraft-style column): a dial (an outer segmented ring for INTEGRITY, an inner one for the REACTOR, small red ticks)
	 * around a wireframe suit whose parts turn red as the hull is damaged (the Hulkbuster: each of its six parts by its own
	 * health), in a small holographic frame. Mark I keeps its amber retro look.
	 */
	private static void suitDial(GuiGraphics g, Player player, TonyStarkState state, IronManSuit suit, long now, boolean retro,
			float integrity, float maxIntegrity) {
		boolean reactor = suit.hasReactor(); // v0.16.7 (user): Era I and Era II
		int size = IronManUiLayout.STATUS_DIAL + 2 * IronManUiLayout.STATUS_PAD + 4;
		int bx = IronManUiLayout.STATUS_MARGIN;
		int by = IronManUiLayout.STATUS_MARGIN;
		int line = retro ? IronManGui.AMBER : IronManGui.CYAN;
		int lineDim = retro ? IronManGui.AMBER_DIM : IronManGui.CYAN_DIM;
		g.fill(bx, by, bx + size, by + size, retro ? 0xA0140C04 : 0xA0040C14);
		g.renderOutline(bx, by, size, size, IronManGui.alpha(lineDim, 0.45f));
		IronManGui.brackets(g, bx, by, size, size, 6, IronManGui.alpha(line, 0.85f));
		float iFrac = IronManUiLayout.clamp01(integrity / Math.max(1f, maxIntegrity));
		float cap = Math.max(1f, suit.energyCapacity());
		float en = reactor ? Math.max(0f, Math.min(cap, state.suitEnergy.getOrDefault(suit.id(), cap))) : 0f;
		float eFrac = en / cap;
		boolean brownout = state.brownout.contains(suit.id());
		boolean low = reactor && !brownout && eFrac < IronManEnergy.LOW_POWER;
		int iFill = retro ? IronManGui.AMBER : iFrac < 0.3f ? IronManGui.RED : iFrac < 0.6f ? IronManGui.ORANGE : HOLO_FILL;
		int eFill = brownout ? ((now / 6) % 2 == 0 ? IronManGui.RED : 0xFF601010) : low ? IronManGui.ORANGE : retro ? IronManGui.AMBER : REACTOR_FILL;
		float dcx = bx + size / 2f;
		float dcy = by + size / 2f;
		dial(g, dcx, dcy, iFrac, iFill, reactor ? eFrac : -1f, eFill, line, lineDim);
		if (com.projecthero.mod.ironman.HulkbusterParts.SUIT_ID.equals(suit.id())) {
			// v0.16.7 (user) Veronica: the Hulkbuster's six parts, each coloured by its own health (broken = red, crossed)
			hulkbusterSilhouette(g, Math.round(dcx) - 6, Math.round(dcy) - 11, com.projecthero.mod.ironman.HulkbusterParts.of(player), now);
		} else {
			silhouette(g, Math.round(dcx) - 5, Math.round(dcy) - 10, iFrac, now, retro);
		}
	}

	/**
	 * v0.16.7 (user): the Mk VIII Flight Boost tag, centred under the crosshair -- "BOOST x2" pulsing orange-white while it
	 * runs, a dim cyan "BOOST ARMED" while it waits for sprint flight.
	 */
	private static void boostTag(GuiGraphics g, Font font, Player player, TonyStarkState state, IronManSuit suit, long now) {
		if (!com.projecthero.mod.ironman.ability.IronManSpaceBoost.armed(state, suit.id())) {
			return;
		}
		boolean on = com.projecthero.mod.ironman.ability.IronManSpaceBoost.boosting(player);
		String tag = IronManGui.fit(font, Component.translatable(on ? "hud.projecthero.ironman.status.boost"
				: "hud.projecthero.ironman.status.boost_armed").getString(), Math.min(IronManUiLayout.HUD_W, g.guiWidth() - 32) - 12);
		int w = font.width(tag) + 12;
		int h = 13;
		// centred just under the crosshair, clear of the corner readouts at any window size
		int x = (g.guiWidth() - w) / 2;
		int y = g.guiHeight() / 2 + 14;
		int line = on ? IronManGui.ORANGE : IronManGui.CYAN_DIM;
		g.fill(x, y, x + w, y + h, on ? 0xB0281204 : 0xA0040C14);
		g.renderOutline(x, y, w, h, IronManGui.alpha(line, 0.55f));
		IronManGui.brackets(g, x, y, w, h, 3, IronManGui.alpha(on ? 0xFFFFD8A0 : IronManGui.CYAN, 0.9f));
		// a little chevron that pulses while the boost runs
		int pulse = on ? (int) (now % 10) : 0;
		int chev = on ? (pulse < 5 ? 0xFFFFFFFF : IronManGui.ORANGE) : IronManGui.CYAN_DIM;
		g.fill(x + 3, y + 4, x + 5, y + 9, chev);
		g.fill(x + 5, y + 5, x + 6, y + 8, chev);
		int text = on ? (pulse < 5 ? 0xFFFFF2E0 : 0xFFFFB060) : IronManGui.TEXT_DIM;
		g.drawString(font, tag, x + 9, y + 3, text, true);
	}

	/** The dial: an outer segmented ring (integrity), an optional inner one (reactor), four small red ticks. */
	private static void dial(GuiGraphics g, float cx, float cy, float iFrac, int iFill, float eFrac, int eFill, int line, int lineDim) {
		var vc = IronManGui.guiBuffer(g);
		var m = g.pose().last().pose();
		int segs = 30;
		double step = Math.PI * 2 / segs;
		int lit = (int) Math.ceil(iFrac * segs - 1e-4);
		for (int k = 0; k < segs; k++) {
			double a0 = -Math.PI / 2 + k * step + 0.03;
			double a1 = a0 + step - 0.06;
			IronManGui.sector(vc, m, cx, cy, 15.5f, 17.5f, a0, a1, k < lit ? IronManGui.alpha(iFill, 0.95f) : IronManGui.alpha(lineDim, 0.3f));
		}
		if (eFrac >= 0f) {
			int esegs = 20;
			double es = Math.PI * 2 / esegs;
			int elit = (int) Math.ceil(eFrac * esegs - 1e-4);
			for (int k = 0; k < esegs; k++) {
				double a0 = -Math.PI / 2 + k * es + 0.04;
				double a1 = a0 + es - 0.08;
				IronManGui.sector(vc, m, cx, cy, 12.5f, 13.8f, a0, a1, k < elit ? IronManGui.alpha(eFill, 0.9f) : IronManGui.alpha(lineDim, 0.25f));
			}
		}
		for (int k = 0; k < 4; k++) {
			double a = k * Math.PI / 2 - Math.PI / 4;
			IronManGui.sector(vc, m, cx, cy, 17.9f, 19.3f, a - 0.07, a + 0.07, IronManGui.alpha(IronManGui.RED, 0.85f));
		}
		IronManGui.sector(vc, m, cx, cy, 10.8f, 11.3f, 0, Math.PI * 2, IronManGui.alpha(line, 0.25f));
		g.flush();
	}

	/**
	 * A 10 x 20 px wireframe of the suit. Each part goes red (blinking once the hull is critical) as integrity falls, in a
	 * fixed order -- right arm, left leg, left arm, torso, right leg, helmet -- so the figure reads as battle damage.
	 */
	private static void silhouette(GuiGraphics g, int x, int y, float iFrac, long now, boolean retro) {
		int ok = retro ? IronManGui.AMBER : HOLO;
		boolean blink = iFrac < 0.35f && (now % 16) < 8;
		// {x, y, w, h, damaged below this integrity}
		int[][] parts = {
				{ 3, 0, 4, 4, 10 },   // helmet
				{ 2, 5, 6, 7, 30 },   // torso
				{ 0, 5, 2, 7, 45 },   // left arm (screen left)
				{ 8, 5, 2, 7, 80 },   // right arm
				{ 2, 13, 2, 7, 60 },  // left leg
				{ 6, 13, 2, 7, 20 } };// right leg
		for (int[] p : parts) {
			boolean damaged = iFrac * 100f < p[4];
			int c = damaged ? (blink ? 0xFFFF9090 : IronManGui.RED) : ok;
			g.fill(x + p[0], y + p[1], x + p[0] + p[2], y + p[1] + p[3], IronManGui.alpha(c, damaged ? 0.75f : 0.35f));
			g.renderOutline(x + p[0], y + p[1], p[2], p[3], IronManGui.alpha(c, 0.95f));
		}
		// the eye slit and the arc reactor
		g.fill(x + 4, y + 2, x + 6, y + 3, retro ? IronManGui.AMBER : 0xFFFFFFFF);
		g.fill(x + 4, y + 7, x + 6, y + 9, retro ? IronManGui.AMBER : 0xFFE6FBFF);
	}

	/**
	 * v0.16.7 (user) Hulkbuster Veronica: a 12 x 22 px wireframe of the Hulkbuster -- head, torso, both arms, both legs --
	 * each part filled by its own health (holo blue whole, orange damaged, red badly), a broken part dark red with a
	 * blinking red cross.
	 */
	private static void hulkbusterSilhouette(GuiGraphics g, int x, int y, float[] eff, long now) {
		// {x, y, w, h} per part, in HulkbusterParts order (the suit faces you: its left arm is on screen right)
		int[][] box = {
				{ 4, 0, 4, 4 },    // head
				{ 3, 5, 6, 8 },    // torso
				{ 10, 5, 2, 8 },   // left arm
				{ 0, 5, 2, 8 },    // right arm
				{ 6, 14, 2, 8 },   // left leg
				{ 4, 14, 2, 8 } }; // right leg
		boolean blink = (now % 16) < 9;
		for (int i = 0; i < box.length; i++) {
			int[] b = box[i];
			float hp = 1f - eff[i];
			boolean broken = com.projecthero.mod.ironman.HulkbusterParts.broken(eff[i]);
			int c = broken ? 0xFF701010 : hp > 0.66f ? HOLO : hp > 0.33f ? IronManGui.ORANGE : IronManGui.RED;
			g.fill(x + b[0], y + b[1], x + b[0] + b[2], y + b[1] + b[3], IronManGui.alpha(c, broken ? 0.9f : 0.45f));
			g.renderOutline(x + b[0], y + b[1], b[2], b[3], IronManGui.alpha(broken ? (blink ? IronManGui.RED : 0xFFFF9090) : c, 0.95f));
			if (broken && blink) {
				// a small red cross over the part
				int cx = x + b[0] + b[2] / 2, cy = y + b[1] + b[3] / 2;
				for (int k = -1; k <= 1; k++) {
					g.fill(cx + k, cy + k, cx + k + 1, cy + k + 1, 0xFFFF3030);
					g.fill(cx + k, cy - k, cx + k + 1, cy - k + 1, 0xFFFF3030);
				}
			}
		}
		// the eye slit and the reactor (dark when that part is gone)
		boolean head = com.projecthero.mod.ironman.HulkbusterParts.broken(eff[0]);
		boolean torso = com.projecthero.mod.ironman.HulkbusterParts.broken(eff[1]);
		if (!head) {
			g.fill(x + 5, y + 2, x + 7, y + 3, 0xFFFFFFFF);
		}
		if (!torso) {
			g.fill(x + 5, y + 7, x + 7, y + 9, 0xFFE6FBFF);
		}
	}

	private static String pctText(float frac) {
		return Math.round(IronManUiLayout.clamp01(frac) * 100f) + "%";
	}

	private static String fmt1(float v) {
		return v == Math.floor(v) ? String.valueOf((int) v) : String.format(java.util.Locale.ROOT, "%.1f", v);
	}

	/** The gantry floor check, at most twice a second (it walks block entities). */
	private static long gantryCheckedAt = -100L;
	private static boolean gantryOn;

	private static boolean gantryCached(Player player, long now) {
		if (now - gantryCheckedAt >= 10L || now < gantryCheckedAt) {
			gantryCheckedAt = now;
			gantryOn = com.projecthero.mod.ironman.ability.era1.EraOneGantry.onGantryFloor(player);
		}
		return gantryOn;
	}

	/** v0.16.1: the visor of a disabled (0 integrity) suit -- near-black, a flickering red failure readout. */
	private static void darkVisor(GuiGraphics g, Font font, long now, boolean retro) {
		int sw = g.guiWidth();
		int sh = g.guiHeight();
		g.fill(0, 0, sw, sh, 0xC8020204);
		if ((now % 30) < 22) {
			Component title = Component.translatable("hud.projecthero.ironman.disabled_title");
			int tw = font.width(title);
			g.drawString(font, title, (sw - tw) / 2, sh / 2 - 20, retro ? IronManGui.AMBER : IronManGui.RED, true);
		}
		int y = sh / 2 - 6;
		for (FormattedCharSequence line : font.split(Component.translatable("hud.projecthero.ironman.disabled_hint"), Math.min(220, sw - 32))) {
			g.drawString(font, line, (sw - font.width(line)) / 2, y, IronManGui.TEXT_DIM, true);
			y += 10;
		}
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

	/**
	 * v0.17.3: the six keys on the Minecraft-style strip (R G Z X C V), read from the worn suit's {@code abilityInSlot()}: each
	 * move's item icon where a hotbar item sits, the key letter in the cell's corner (yellow while Shift picks the Shift moves),
	 * the cooldown sweep + seconds, a running toggle framed, a weapon-wheel key's small gold notch, and -- while the suit is
	 * offline -- greyed keys under a red wash. Returns the move-list rows.
	 */
	private static List<MoveListOverlay.Row> keys(VanillaHud.Stack s, Font font, Player player, TonyStarkState state, IronManSuit suit,
			String suitId, long now, boolean retro, boolean offline) {
		boolean shiftHeld = player.isShiftKeyDown();
		List<MoveListOverlay.Row> rows = new ArrayList<>();
		for (int i = 0; i < VanillaHud.ORDER.length; i++) {
			com.projecthero.mod.hero.AbilitySlot slot = VanillaHud.ORDER[i];
			int o = slot.ordinal();
			String key = VanillaHud.keyLabel(slot);
			String abilityId = suit.abilityInSlot(o + 1);
			if (abilityId == null) {
				VanillaHudExtras.iconSlot(s, i, key, false, false, 0f, 0, 0f, false);
				continue;
			}
			// v0.16.1: the comic kit -- the slot shows the move Shift would fire while Shift is held, else the tap move
			if (com.projecthero.mod.ironman.ability.IronManComicKit.isKitSlot(abilityId)) {
				MoveListOverlay.Row row = comicSlot(s, font, player, state, suitId, i, slot, now, offline);
				if (row != null) {
					rows.add(row);
				}
				continue;
			}
			boolean wheel = IronManAbilities.WEAPON_WHEEL_SLOT.equals(abilityId);
			// v0.14.27: the Mark III G key shows the weapon picked on its wheel (and that weapon's cooldown)
			boolean mk3Arsenal = com.projecthero.mod.ironman.ability.IronManMark3.ARSENAL.equals(abilityId)
					|| com.projecthero.mod.ironman.ability.IronManMark6.WEAPON.equals(abilityId); // v0.15.4: the Mark 6 / 7 G key too
			String effectiveId = wheel ? state.weaponWheelChoice
					: com.projecthero.mod.ironman.ability.IronManMark6.WEAPON.equals(abilityId) ? com.projecthero.mod.ironman.ability.IronManMark6.selectedWeapon(state, suitId)
					: mk3Arsenal ? com.projecthero.mod.ironman.ability.IronManMark3.selectedWeapon(state, suitId) : abilityId;
			// v0.15.4: the Mark 6 / 7 V key reads the shield's cooldown, X the flares'
			String cdKey = suitId + "/" + com.projecthero.mod.ironman.ability.IronManMark6.hudCooldownId(effectiveId);
			Long readyAt = state.abilityReadyAt.get(cdKey);
			long left = readyAt == null ? 0 : Math.max(0, readyAt - now);
			boolean active = isActive(player, state, abilityId, now)
					|| (com.projecthero.mod.ironman.ability.IronManMark3.WHEEL.equals(abilityId)
							&& com.projecthero.mod.ironman.ability.IronManMark3.shieldOn(state, suitId)); // v0.14.29: Mark 4 shield

			VanillaHudExtras.icon(s, i, icon(effectiveId, suit));
			VanillaHudExtras.iconSlot(s, i, key, !offline, shiftHeld, left > 0 ? cooldownFrac(cdKey, readyAt, now) : 0f,
					IronManUiLayout.ceilSecs(left), 0f, active && !offline);
			if (offline && left <= 0) {
				VanillaHudExtras.tint(s, i, 0x70500000);
			}
			if (wheel || mk3Arsenal) {
				// a small gold corner notch marks the weapon-wheel key
				goldNotch(s, i);
			}
			rows.add(MoveListOverlay.inline(MoveListOverlay.key(slot), Component.translatable(nameKey(suit, abilityId, effectiveId))));
		}
		return rows;
	}

	/** The weapon-wheel key's gold notch, in the top-right corner of its cell. */
	private static void goldNotch(VanillaHud.Stack s, int i) {
		int x = VanillaHudExtras.cellX(s, i) + 16;
		int y = VanillaHudExtras.cellY(s);
		s.g.pose().pushPose();
		s.g.pose().translate(0, 0, 200);
		s.g.fill(x - 4, y, x, y + 1, IronManGui.GOLD);
		s.g.fill(x - 1, y, x, y + 4, IronManGui.GOLD);
		s.g.pose().popPose();
	}

	/** A hold / charge fill on key {@code i}, lifted over its item icon. */
	private static void hold(VanillaHud.Stack s, int i, float progress) {
		s.g.pose().pushPose();
		s.g.pose().translate(0, 0, 200);
		s.hold(i, progress);
		s.g.pose().popPose();
	}

	/**
	 * v0.16.1: one comic-kit key. The slot shows the move Shift would fire while Shift is held (else the tap move): its icon,
	 * cooldown sweep + seconds, the other variant's cooldown on the durability bar. The Mk VIII Flight Boost key is framed
	 * while armed (an orange wash while it runs); a move a broken Hulkbuster part has taken offline is greyed with a red cross.
	 * Returns the move-list row ("[R] Tap / Shift  cooldown").
	 */
	private static MoveListOverlay.Row comicSlot(VanillaHud.Stack s, Font font, Player player, TonyStarkState state, String suitId, int i,
			com.projecthero.mod.hero.AbilitySlot slot, long now, boolean offline) {
		int o = slot.ordinal();
		var kitKey = com.projecthero.mod.ironman.ability.IronManComicKit.kit(suitId) == null ? null
				: com.projecthero.mod.ironman.ability.IronManComicKit.kit(suitId).key(o + 1);
		if (kitKey == null) {
			VanillaHudExtras.iconSlot(s, i, VanillaHud.keyLabel(slot), false, false, 0f, 0, 0f, false);
			return null;
		}
		boolean shift = player.isShiftKeyDown();
		if (kitKey.shift() == null) {
			// v0.16.7 (user) Era I: the C key -- its tap move, and with Shift held the emergency EJECT
			// v0.16.7 (user) Era II: its tap move (a utility moved off V), and with Shift held FOLD into the portable form
			return ejectSlot(s, font, player, state, suitId, kitKey.tap(), i, slot, now, offline, shift);
		}
		var shown = kitKey.get(shift);
		var other = kitKey.get(!shift);
		String cdKey = suitId + "/" + shown.cooldownKey();
		Long readyAt = state.abilityReadyAt.get(cdKey);
		long left = readyAt == null ? 0 : Math.max(0, readyAt - now);
		String otherKey = suitId + "/" + other.cooldownKey();
		Long otherAt = state.abilityReadyAt.get(otherKey);
		long otherLeft = otherAt == null ? 0 : Math.max(0, otherAt - now);
		boolean boostArmed = com.projecthero.mod.ironman.ability.IronManSpaceBoost.MOVE.equals(shown.id())
				&& com.projecthero.mod.ironman.ability.IronManSpaceBoost.armed(state, suitId);
		boolean blocked = com.projecthero.mod.ironman.HulkbusterParts.SUIT_ID.equals(suitId) && com.projecthero.mod.ironman.HulkbusterParts
				.blockedBy(shown.id(), com.projecthero.mod.ironman.HulkbusterParts.of(player)) != null;

		VanillaHudExtras.icon(s, i, new ItemStack(shown.icon()));
		VanillaHudExtras.iconSlot(s, i, VanillaHud.keyLabel(slot), !offline && !blocked, shift,
				left > 0 ? cooldownFrac(cdKey, readyAt, now) : 0f, IronManUiLayout.ceilSecs(left),
				otherLeft > 0 ? cooldownFrac(otherKey, otherAt, now) : 0f, boostArmed && !offline);
		if (boostArmed && com.projecthero.mod.ironman.ability.IronManSpaceBoost.boosting(player)) {
			VanillaHudExtras.tint(s, i, 0x40FF8020); // v0.16.7 (user): orange while the Flight Boost runs
		}
		if (offline && left <= 0) {
			VanillaHudExtras.tint(s, i, 0x70500000);
		}
		if (blocked) {
			// v0.16.7 (user) Veronica: a move a broken Hulkbuster part has taken offline -- greyed, with a red cross
			VanillaHudExtras.tint(s, i, 0xB0303030);
			int cx = VanillaHudExtras.cellX(s, i) + 7, cy = VanillaHudExtras.cellY(s) + 7, r = 5;
			s.g.pose().pushPose();
			s.g.pose().translate(0, 0, 210);
			for (int k = -r; k <= r; k++) {
				s.g.fill(cx + k, cy + k, cx + k + 2, cy + k + 2, 0xFFFF3A3A);
				s.g.fill(cx + k, cy - k - 1, cx + k + 2, cy - k + 1, 0xFFFF3A3A);
			}
			s.g.pose().popPose();
		}
		// v0.16.7: a row of the shared move list (tap move, its Shift move, the cooldown)
		return MoveListOverlay.Row.of(MoveListOverlay.key(slot), Component.translatable(kitKey.tap().nameKey()),
				Component.translatable(kitKey.shift().nameKey()), left > 0 ? Component.literal(IronManUiLayout.secs(left)) : null);
	}

	/**
	 * v0.16.7 (user) Era I / II: the C key. Plain: the tap move (icon, cooldown sweep) like any kit key; a hold-C suit
	 * (Hulkbuster / Mk VII) fills the cell while C is held. Shift held: EJECT (Era I, the Suit Crate icon, the cell filling
	 * over the 3 s hold) or FOLD (Era II, the packed suit's icon), the word in small gold letters along the bottom of the cell.
	 */
	private static MoveListOverlay.Row ejectSlot(VanillaHud.Stack s, Font font, Player player, TonyStarkState state, String suitId,
			com.projecthero.mod.ironman.ability.IronManComicKit.Move tap, int i, com.projecthero.mod.hero.AbilitySlot slot, long now,
			boolean offline, boolean shift) {
		String cdKey = suitId + "/" + tap.cooldownKey();
		Long readyAt = state.abilityReadyAt.get(cdKey);
		long left = readyAt == null ? 0 : Math.max(0, readyAt - now);
		// v0.16.7 (user) Era II: Shift+C folds into the portable form (its packed item is the Shift icon)
		com.projecthero.mod.ironman.suit.PortableForm fold = com.projecthero.mod.ironman.ability.era1.IronManEraOne.isEraOne(suitId)
				? null : com.projecthero.mod.ironman.suit.PortableForms.byId(suitId);
		// v0.16.7 (user): the Hulkbuster / Silver Centurion C has a hold move -- the cell fills while C is held
		boolean holdKey = tap.input() == com.projecthero.mod.ironman.ability.IronManComicKit.Input.HOLD;
		boolean cDown = holdKey && !shift && Minecraft.getInstance().screen == null && ModKeyBindings.ABILITY_SLOTS[5].isDown();
		if (!cDown) {
			cHeldSince = -1L;
		} else if (cHeldSince < 0L || cHeldSince > now) {
			cHeldSince = now;
		}
		String key = VanillaHud.keyLabel(slot);
		if (shift) {
			VanillaHudExtras.icon(s, i, new ItemStack(fold != null ? fold.packedItem().get() : IronManItems.SUIT_CRATE));
			VanillaHudExtras.iconSlot(s, i, key, true, true, 0f, 0, 0f, true);
			if (fold == null && ejectHeldSince >= 0L) {
				hold(s, i, Math.min(1f, (now - ejectHeldSince) / (float) com.projecthero.mod.ironman.ability.era1.EraOneEject.HOLD_TICKS));
			}
			// small (x0.6) so the word fits the 16 px cell
			String e = Component.translatable(fold != null ? "hud.projecthero.ironman.era2.fold" : "hud.projecthero.ironman.era1.eject").getString();
			float sc = Math.min(0.6f, 16f / Math.max(1, font.width(e)));
			s.g.pose().pushPose();
			s.g.pose().translate(VanillaHudExtras.cellX(s, i) + 8f, VanillaHudExtras.cellY(s) + 11f, 220f);
			s.g.pose().scale(sc, sc, 1f);
			s.g.drawString(font, e, -font.width(e) / 2, 0, VanillaHud.GOLD, true);
			s.g.pose().popPose();
		} else {
			VanillaHudExtras.icon(s, i, new ItemStack(tap.icon()));
			VanillaHudExtras.iconSlot(s, i, key, !offline, false, left > 0 ? cooldownFrac(cdKey, readyAt, now) : 0f,
					IronManUiLayout.ceilSecs(left), 0f, false);
			if (cDown) {
				hold(s, i, Math.min(1f, (now - cHeldSince) / (float) com.projecthero.mod.ironman.ability.IronManComicMoves.SENTRY_HOLD_TICKS));
			}
			if (offline && left <= 0) {
				VanillaHudExtras.tint(s, i, 0x70500000);
			}
		}
		return MoveListOverlay.Row.of(MoveListOverlay.key(slot), Component.translatable(tap.nameKey()),
				Component.translatable(fold != null ? "hud.projecthero.ironman.era2.fold_move" : "hud.projecthero.ironman.era1.eject_move"),
				left > 0 ? Component.literal(IronManUiLayout.secs(left)) : null);
	}

	/** v0.16.7 (user): the game time this client started holding Shift+C on an Era I suit, or -1. */
	private static long ejectHeldSince = -1L;

	/**
	 * v0.16.7 (user): while Shift+C is held on a worn Era I suit, a bar fills over {@code EraOneEject.HOLD_TICKS} (3 s),
	 * centred low on the screen like the Miraculous take-off bar (the C box fills up too). The server times the real
	 * eject; this only shows it.
	 */
	private static void ejectBar(GuiGraphics g, Font font, Player player, IronManSuit suit, long now) {
		Minecraft mc = Minecraft.getInstance();
		boolean holding = suit.eraOne() && mc.screen == null && ModKeyBindings.ABILITY_SLOTS[5].isDown() && player.isShiftKeyDown();
		if (!holding) {
			ejectHeldSince = -1L;
			return;
		}
		if (ejectHeldSince < 0L || ejectHeldSince > now) {
			ejectHeldSince = now;
		}
		float progress = Math.min(1f, (now - ejectHeldSince) / (float) com.projecthero.mod.ironman.ability.era1.EraOneEject.HOLD_TICKS);
		// v0.16.7 (user): nothing above the hotbar -- the C box in the ability column shows the eject progress
	}

	/** v0.16.7 (user): the game time this client started holding plain C on a hold-C suit (Hulkbuster / Mk VII), or -1. */
	private static long cHeldSince = -1L;

	private static boolean isActive(Player player, TonyStarkState state, String abilityId, long now) {
		if (IronManAbilities.MOB_HIGHLIGHT_TOGGLE.equals(abilityId)) {
			return player.getAttachedOrElse(ModAttachments.IRON_MAN_HIGHLIGHT_ON, false); // v0.15.4: wearer-only sync
		}
		if (IronManAbilities.BLADE.equals(abilityId)) {
			return player.getAttachedOrElse(ModAttachments.IRON_MAN_BLADES, false);
		}
		if (IronManAbilities.TIMED_FLIGHT.equals(abilityId)) {
			return state.timedFlightUntil > now;
		}
		// v0.15.4: the Mark 6 / 7 X box lights while its supersonic boost runs
		if (com.projecthero.mod.ironman.ability.IronManMark6.FLARES.equals(abilityId)) {
			String worn = suitIdOf(player);
			return worn != null && com.projecthero.mod.ironman.ability.IronManFlares.boosting(state, worn, now);
		}
		if (com.projecthero.mod.ironman.ability.IronManMark3.WHEEL.equals(abilityId)) {
			return com.projecthero.mod.ironman.ability.IronManMark3.shieldOn(state); // v0.14.27: Sneak+V shield is up
		}
		return false;
	}

	/** v0.15.4: the id of the Iron Man suit whose helmet the player wears, or null. */
	private static String suitIdOf(Player player) {
		return player.getItemBySlot(EquipmentSlot.HEAD).getItem() instanceof IronManArmorItem a ? a.suitId() : null;
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
			// v0.15.4: the Mark 6 / Mark 7 modern kit
			case com.projecthero.mod.ironman.ability.IronManMark6.WEAPON, com.projecthero.mod.ironman.ability.IronManMark6.BARRAGE -> new ItemStack(Items.TARGET);
			case com.projecthero.mod.ironman.ability.IronManMark6.FLARES -> new ItemStack(Items.GLOWSTONE_DUST);
			case com.projecthero.mod.ironman.ability.IronManMark6.UNIBEAM, com.projecthero.mod.ironman.ability.IronManMark7.UNIBEAM -> new ItemStack(IronManItems.ARC_REACTOR);
			case com.projecthero.mod.ironman.ability.IronManMark6.WHEEL -> new ItemStack(Items.COMPASS);
			case com.projecthero.mod.ironman.ability.IronManMark7.LASER -> new ItemStack(Items.REDSTONE_TORCH);
			case com.projecthero.mod.ironman.ability.IronManMark8.SENTRY -> new ItemStack(Items.ARMOR_STAND); // v0.15.9
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
