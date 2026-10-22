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
 *   <li><b>Top-left</b> (v0.16.7 user redesign) -- one slim telemetry strip (ALT, SPD, X Y Z, clock / day), then
 *       <i>only the meters that matter right now</i>: AIR (underwater / refilling), HEAT, FLIGHT burst, OVERLOAD, WRIST
 *       LASER, MISSILE reload, the Mark II ceiling warning, and the status chips (mob highlight, weapon-wheel binding,
 *       blades, entity scan, missiles ready, Protocol Phoenix cooldown).</li>
 *   <li><b>Top-right</b> (v0.16.7) -- the suit status in the helmet's holographic line-art style: the suit's full name, a
 *       dial (segmented INTEGRITY / REACTOR rings around a wireframe suit that reddens with damage) and the INTEGRITY and
 *       (Era I) REACTOR gauges with percentages, exact values, absorb share, regen and LOW POWER / BROWNOUT.</li>
 *   <li><b>Ability column</b> (v0.16.7) -- six boxes (R G X Z V C) stacked along the bottom-right edge, read from the
 *       worn suit's {@code abilityInSlot()}: an icon, the key letter beside it, a cooldown sweep + seconds. Hold Left Alt
 *       for the move names, one row to the left of each box. Nothing sits above the hotbar.</li>
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
		int accent = minimalHud ? IronManGui.AMBER : IronManGui.CYAN;
		int dim = minimalHud ? IronManGui.AMBER_DIM : IronManGui.TEXT_DIM;

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

		// v0.16.7 (user) HUD redesign: the top-left keeps only the slim telemetry strip (ALT, SPD, X Y Z, clock / day -- the
		// v0.15.20 user request for X, Y and Z stays) with the contextual meters / chips under it; the suit's name, INTEGRITY and
		// REACTOR moved to the top-right status block; the key boxes to a column along the bottom-right edge.
		int y = minimalHud ? IronManUiLayout.HUD_Y : telemetryStrip(g, font, client, player);

		// Two passes: measure the contextual block, then draw a soft backdrop behind it (readable over a bright sky) and the block.
		int blockTop = y;
		int textBottom = blockTop;
		boolean missilesReady = true;
		for (int pass = 0; pass < 2; pass++) {
			boolean draw = pass == 1;
			if (draw && textBottom > blockTop) {
				backdrop(g, x - 3, blockTop - 3, w + 6, textBottom - blockTop + 2, minimalHud);
			}
			y = blockTop;
			// ---- contextual meters, only while relevant
			if (!minimalHud && suit.airTankSeconds() > 0 && state.suitAir < 0.999f) {
				float airFrac = IronManUiLayout.clamp01(state.suitAir);
				y = meter(draw, g, font, x, y, w, false, Component.translatable("hud.projecthero.ironman.air").getString(),
						Math.round(airFrac * suit.airTankSeconds()) + "s", airFrac,
						airFrac < 0.25f ? IronManGui.RED : 0xFF5BC8FF);
			}
			float maxHeat = IronManAbilities.flamethrowerMaxHeat(suit);
			if ((hasAbility(suit, IronManAbilities.FLAMETHROWER) || suit.hasWeaponWheel() || com.projecthero.mod.ironman.ability.IronManMark6.isKitSuit(suitId)) // v0.15.4
					&& state.flamethrowerHeat > 0.5f) {
				float heatFrac = IronManUiLayout.clamp01(state.flamethrowerHeat / maxHeat);
				y = meter(draw, g, font, x, y, w, minimalHud, Component.translatable("hud.projecthero.ironman.heat").getString(),
						IronManUiLayout.pct(heatFrac) + " " + IronManUiLayout.amount(state.flamethrowerHeat, maxHeat),
						heatFrac, heatFrac > 0.8f ? IronManGui.RED : 0xFFFFA23C);
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
				y = meter(draw, g, font, x, y, w, minimalHud, Component.translatable("hud.projecthero.ironman.heat_sink").getString(),
						sinkValue, sinkFrac, sink >= com.projecthero.mod.ironman.ability.IronManMark9.HEAT_MAX - 0.5f ? IronManGui.RED
								: sink >= com.projecthero.mod.ironman.ability.IronManMark9.HOT ? 0xFFFF6A1A : 0xFFFFA23C);
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
			missilesReady = true;
			if (!minimalHud && suit.missileCount() > 0 && !com.projecthero.mod.ironman.ability.IronManMark3.isKitSuit(suitId)
					&& !com.projecthero.mod.ironman.ability.IronManMark6.isKitSuit(suitId)) {
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
			// v0.16.7 (user): the SUIT CRITICAL banner and the SUIT OFFLINE line are gone from here -- the status block's
			// INTEGRITY gauge blinks CRITICAL and the key boxes go red while the suit is offline

			textBottom = y;
		}
		y = textBottom;

		// ---- status chips
		List<String> chipText = new ArrayList<>();
		List<Integer> chipColor = new ArrayList<>();
		if (hasAbility(suit, IronManAbilities.MOB_HIGHLIGHT_TOGGLE)) {
			boolean on = player.getAttachedOrElse(ModAttachments.IRON_MAN_HIGHLIGHT_ON, false); // v0.15.4: wearer-only sync
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
			if (player.getAttachedOrElse(ModAttachments.IRON_MAN_BLADES, false)) {
				chipText.add(Component.translatable("hud.projecthero.ironman.chip.blades").getString());
				chipColor.add(IronManGui.CYAN);
			}
			if (suit.missileCount() > 0 && !com.projecthero.mod.ironman.ability.IronManMark3.isKitSuit(suitId)
					&& !com.projecthero.mod.ironman.ability.IronManMark6.isKitSuit(suitId) && missilesReady) {
				chipText.add(Component.translatable("hud.projecthero.ironman.missiles", suit.missileCount()).getString());
				chipColor.add(IronManGui.GOLD);
			}
			long phoenixIn = state.phoenixReadyAt - now;
			if (phoenixIn > 0 && com.projecthero.mod.ironman.gear.StarkGear.phoenixArmed(player)) { // v0.15.7: glasses only
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

		// ---- v0.16.7 (user): the top-right suit status block (name, INTEGRITY, REACTOR, the dial + suit silhouette)
		statusBlock(g, font, player, state, suit, now, minimalHud, integrity, maxIntegrity, offline);

		// ---- the target scanner (Mark II+, visor closed): v0.16.7 (user) holographic reticle + threat card
		boolean hbBlind = com.projecthero.mod.ironman.HulkbusterParts.SUIT_ID.equals(suit.id())
				&& com.projecthero.mod.ironman.HulkbusterParts.broken(com.projecthero.mod.ironman.HulkbusterParts.of(player)[0]);
		// v0.16.7 (user): a Hulkbuster with its head broken has no targeting HUD
		if (!minimalHud && visorClosed && !hbBlind && !MoveListOverlay.held()) { // the Left-Alt move list takes the space while held
			com.projecthero.mod.client.ironman.IronManTargetScanner.renderPanel(g, font, player, suit, delta.getGameTimeDeltaPartialTick(false));
		}

		abilityStrip(g, font, player, state, suit, suitId, now, minimalHud, offline);
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

	/** Pale holographic blue for the gauge labels, and the healthy integrity fill. */
	private static final int HOLO = 0xFFA9DDF2;
	private static final int HOLO_FILL = 0xFFBFEFFF;
	private static final int REACTOR_FILL = 0xFF4FE0FF;

	/**
	 * The top-right status block, in the helmet's holographic line-art style: the suit's full name, a dial (an outer
	 * segmented ring for INTEGRITY, an inner one for the REACTOR, small red ticks) around a wireframe suit silhouette whose
	 * parts turn red as the hull is damaged, and beside it the INTEGRITY and (Era I) REACTOR gauges -- a percentage, a
	 * segmented bar, the exact values and the absorb share / regen / LOW POWER / BROWNOUT. Mark I keeps its amber retro look.
	 */
	private static void statusBlock(GuiGraphics g, Font font, Player player, TonyStarkState state, IronManSuit suit, long now,
			boolean retro, float integrity, float maxIntegrity, boolean offline) {
		boolean reactor = suit.hasReactor(); // v0.16.7 (user): Era I and Era II
		Rect b = IronManUiLayout.statusBlock(g.guiWidth(), reactor);
		int line = retro ? IronManGui.AMBER : IronManGui.CYAN;
		int lineDim = retro ? IronManGui.AMBER_DIM : IronManGui.CYAN_DIM;
		int label = retro ? 0xFFD09040 : HOLO;
		// frame: a faint wash, a hairline outline, brighter corner brackets and a few red ticks
		g.fill(b.x(), b.y(), b.right(), b.bottom(), retro ? 0xA0140C04 : 0xA0040C14);
		g.renderOutline(b.x(), b.y(), b.w(), b.h(), IronManGui.alpha(lineDim, 0.45f));
		IronManGui.brackets(g, b.x(), b.y(), b.w(), b.h(), 6, IronManGui.alpha(line, 0.85f));
		for (int k = 0; k < 3; k++) {
			g.fill(b.right() - 14 - k * 4, b.bottom() - 1, b.right() - 12 - k * 4, b.bottom() + 1, IronManGui.alpha(IronManGui.RED, 0.85f));
		}
		int px = b.x() + IronManUiLayout.STATUS_PAD;
		int py = b.y() + IronManUiLayout.STATUS_PAD;
		int innerW = b.w() - 2 * IronManUiLayout.STATUS_PAD;

		// header: "MK III CLASSIC"
		String name = (Component.translatable(suit.nameKey()).getString() + " "
				+ Component.translatable(suit.displayKey()).getString()).toUpperCase(java.util.Locale.ROOT);
		// bold when it fits, regular for the long comic names ("MK VII SILVER CENTURION"), never cut short
		Component boldName = Component.literal(name).withStyle(s -> s.withBold(true));
		int nameW;
		if (font.width(boldName) <= innerW - 9) {
			g.drawString(font, boldName, px, py, line, true);
			nameW = font.width(boldName);
		} else {
			String n = IronManGui.fit(font, name, innerW - 9);
			g.drawString(font, n, px, py, line, true);
			nameW = font.width(n);
		}
		if (nameW < innerW - 11) { // the little red / dim tick pair, only where the name leaves room for it
			g.fill(px + innerW - 7, py + 2, px + innerW - 1, py + 3, IronManGui.alpha(IronManGui.RED, 0.85f));
			g.fill(px + innerW - 7, py + 5, px + innerW - 3, py + 6, IronManGui.alpha(lineDim, 0.9f));
		}
		g.fill(px, py + 10, px + innerW, py + 11, IronManGui.alpha(lineDim, 0.5f));

		int rowTop = py + IronManUiLayout.STATUS_HEADER_H;
		float iFrac = IronManUiLayout.clamp01(integrity / Math.max(1f, maxIntegrity));
		float cap = Math.max(1f, suit.energyCapacity());
		float en = reactor ? Math.max(0f, Math.min(cap, state.suitEnergy.getOrDefault(suit.id(), cap))) : 0f;
		float eFrac = en / cap;
		boolean brownout = state.brownout.contains(suit.id());
		boolean low = reactor && !brownout && eFrac < IronManEnergy.LOW_POWER;
		boolean critical = iFrac < 0.35f;
		int iFill = retro ? IronManGui.AMBER : iFrac < 0.3f ? IronManGui.RED : iFrac < 0.6f ? IronManGui.ORANGE : HOLO_FILL;
		int eFill = brownout ? ((now / 6) % 2 == 0 ? IronManGui.RED : 0xFF601010) : low ? IronManGui.ORANGE : retro ? IronManGui.AMBER : REACTOR_FILL;

		// the dial + silhouette
		int gaugesH = reactor ? 2 * IronManUiLayout.STATUS_GAUGE_H + IronManUiLayout.STATUS_GAUGE_GAP : IronManUiLayout.STATUS_GAUGE_H;
		int dialH = Math.max(IronManUiLayout.STATUS_DIAL, gaugesH);
		float dcx = px + IronManUiLayout.STATUS_DIAL / 2f;
		float dcy = rowTop + dialH / 2f;
		dial(g, dcx, dcy, iFrac, iFill, reactor ? eFrac : -1f, eFill, line, lineDim);
		if (com.projecthero.mod.ironman.HulkbusterParts.SUIT_ID.equals(suit.id())) {
			// v0.16.7 (user) Veronica: the Hulkbuster's six parts, each coloured by its own health (broken = red, crossed)
			hulkbusterSilhouette(g, Math.round(dcx) - 6, Math.round(dcy) - 11, com.projecthero.mod.ironman.HulkbusterParts.of(player), now);
		} else {
			silhouette(g, Math.round(dcx) - 5, Math.round(dcy) - 10, iFrac, now, retro);
		}

		// the gauges
		int gx = px + IronManUiLayout.STATUS_DIAL + 6;
		int gw = px + innerW - gx;
		int gy = rowTop + (dialH - gaugesH) / 2;
		boolean blink = (now % 20) < 12;
		String iLabel = Component.translatable("hud.projecthero.ironman.status.integrity").getString();
		String iRight = critical && !offline && !retro
				? Component.translatable("hud.projecthero.ironman.status.critical").getString()
				: Component.translatable("hud.projecthero.ironman.status.absorb", Math.round(suit.absorbShare() * 100f)).getString();
		gauge(g, font, gx, gy, gw, iLabel, critical && blink ? IronManGui.RED : label, pctText(iFrac), iFrac, iFill,
				new String[] { Math.round(integrity) + "/" + Math.round(maxIntegrity), String.valueOf(Math.round(integrity)), "" },
				iRight, critical ? (blink ? IronManGui.RED : 0xFFFF9090) : IronManGui.TEXT_DIM, retro);
		if (reactor) {
			int ry = gy + IronManUiLayout.STATUS_GAUGE_H + IronManUiLayout.STATUS_GAUGE_GAP;
			String eRight;
			int eRightColour;
			if (brownout) {
				eRight = Component.translatable("hud.projecthero.ironman.status.brownout").getString();
				eRightColour = (now / 10) % 2 == 0 ? IronManGui.RED : 0xFFFF9090;
			} else if (low) {
				eRight = Component.translatable("hud.projecthero.ironman.status.low_power").getString();
				eRightColour = IronManGui.ORANGE;
			} else if (gantryCached(player, now)) {
				eRight = Component.translatable("hud.projecthero.ironman.status.gantry", Math.round(IronManEnergy.GANTRY_CHARGE_PER_SECOND)).getString();
				eRightColour = IronManGui.GREEN;
			} else {
				eRight = Component.translatable("hud.projecthero.ironman.status.regen", fmt1(suit.energyRegenPerSecond())).getString();
				eRightColour = IronManGui.TEXT_DIM;
			}
			gauge(g, font, gx, ry, gw, Component.translatable("hud.projecthero.ironman.status.reactor").getString(),
					brownout ? IronManGui.RED : label, pctText(eFrac), eFrac, eFill,
					new String[] { Math.round(en) + "/" + Math.round(cap), String.valueOf(Math.round(en)), "" },
					eRight, eRightColour, retro);
		}
	}

	/** One status gauge: label + percentage, the segmented bar, then the exact value (shortened to fit) and a right note. */
	private static void gauge(GuiGraphics g, Font font, int x, int y, int w, String label, int labelColour, String pct, float frac,
			int fill, String[] valueChoices, String right, int rightColour, boolean retro) {
		g.drawString(font, IronManGui.fit(font, label, w - font.width(pct) - 4), x, y, labelColour, false);
		g.drawString(font, pct, x + w - font.width(pct), y, retro ? IronManGui.AMBER : 0xFFFFFFFF, true);
		if (retro) {
			IronManGui.cells(g, x, y + 10, w, frac, IronManGui.AMBER, IronManGui.AMBER_BG);
		} else {
			holoBar(g, x, y + 10, w, frac, fill);
		}
		String r = IronManGui.fit(font, right, w);
		int room = w - font.width(r) - (r.isEmpty() ? 0 : 5);
		String v = "";
		for (String c : valueChoices) {
			if (font.width(c) <= room) {
				v = c;
				break;
			}
		}
		g.drawString(font, v, x, y + 15, retro ? 0xFFD09040 : IronManGui.TEXT_DIM, false);
		g.drawString(font, r, x + w - font.width(r), y + 15, rightColour, false);
	}

	/** A 3 px holographic bar: a faint track, the fill in ten segments with 1 px gaps, a bright leading edge. */
	private static void holoBar(GuiGraphics g, int x, int y, int w, float frac, int fill) {
		g.fill(x, y, x + w, y + 3, 0x50103040);
		int fw = Math.round(w * IronManUiLayout.clamp01(frac));
		g.fill(x, y, x + fw, y + 3, IronManGui.alpha(fill, 0.9f));
		for (int k = 1; k < 10; k++) {
			int sx = x + w * k / 10;
			g.fill(sx, y, sx + 1, y + 3, 0xA0020810);
		}
		if (fw > 1) {
			g.fill(x + fw - 1, y - 1, x + fw, y + 4, 0xFFFFFFFF);
		}
		g.fill(x, y + 4, x + w, y + 5, IronManGui.alpha(IronManGui.CYAN_DIM, 0.35f));
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
	 * v0.16.7 (user): the six key boxes, stacked in a column along the bottom-right edge (nothing above the hotbar any
	 * more), each in a thin holographic frame with its key letter to the left. Left Alt opens the move list to the LEFT of
	 * the column, one row per key, level with its box.
	 */
	private static void abilityStrip(GuiGraphics g, Font font, Player player, TonyStarkState state, IronManSuit suit,
			String suitId, long now, boolean retro, boolean offline) {
		Rect[] slots = IronManUiLayout.abilityStrip(g.guiWidth(), g.guiHeight());
		boolean expanded = MoveListOverlay.held(); // v0.16.6: the shared move list
		int accent = retro ? IronManGui.AMBER : IronManGui.CYAN;
		boolean shiftHeld = player.isShiftKeyDown();
		MoveListOverlay.Row[] names = new MoveListOverlay.Row[6];
		// a thin spine joining the column, behind the boxes
		g.fill(slots[0].x() + slots[0].w() / 2, slots[0].y() - 4, slots[0].x() + slots[0].w() / 2 + 1, slots[5].bottom(),
				IronManGui.alpha(retro ? IronManGui.AMBER_DIM : IronManGui.CYAN_DIM, 0.35f));
		g.fill(slots[0].x() + 3, slots[0].y() - 5, slots[0].right() - 3, slots[0].y() - 4, IronManGui.alpha(IronManGui.RED, 0.8f));
		for (int i = 0; i < 6; i++) {
			Rect r = slots[i];
			String abilityId = suit.abilityInSlot(i + 1);
			keyTag(g, font, i, r, retro, shiftHeld && abilityId != null);
			if (abilityId == null) {
				g.fill(r.x(), r.y(), r.right(), r.bottom(), 0x40060A10);
				g.renderOutline(r.x(), r.y(), r.w(), r.h(), 0x40303A48);
				continue;
			}
			// v0.16.1: the comic kit -- the box shows the move Shift would fire while Shift is held, else the tap move
			if (com.projecthero.mod.ironman.ability.IronManComicKit.isKitSlot(abilityId)) {
				names[i] = comicBox(g, font, player, state, suitId, i, r, now, retro, offline, accent);
				continue;
			}
			boolean wheel = IronManAbilities.WEAPON_WHEEL_SLOT.equals(abilityId);
			// v0.14.27: the Mark III G box shows the weapon picked on its wheel (and that weapon's cooldown)
			boolean mk3Arsenal = com.projecthero.mod.ironman.ability.IronManMark3.ARSENAL.equals(abilityId)
					|| com.projecthero.mod.ironman.ability.IronManMark6.WEAPON.equals(abilityId); // v0.15.4: the Mark 6 / 7 G box too
			String effectiveId = wheel ? state.weaponWheelChoice
					: com.projecthero.mod.ironman.ability.IronManMark6.WEAPON.equals(abilityId) ? com.projecthero.mod.ironman.ability.IronManMark6.selectedWeapon(state, suitId)
					: mk3Arsenal ? com.projecthero.mod.ironman.ability.IronManMark3.selectedWeapon(state, suitId) : abilityId;
			// v0.15.4: the Mark 6 / 7 V box reads the shield's cooldown, X the flares'
			String cdKey = suitId + "/" + com.projecthero.mod.ironman.ability.IronManMark6.hudCooldownId(effectiveId);
			Long readyAt = state.abilityReadyAt.get(cdKey);
			long left = readyAt == null ? 0 : Math.max(0, readyAt - now);
			boolean active = isActive(player, state, abilityId, now)
					|| (com.projecthero.mod.ironman.ability.IronManMark3.WHEEL.equals(abilityId)
							&& com.projecthero.mod.ironman.ability.IronManMark3.shieldOn(state, suitId)); // v0.14.29: Mark 4 shield

			holoBox(g, r, offline ? 0xFF8A2828 : active ? IronManGui.GOLD : 0, retro);
			if (!offline && left <= 0) {
				g.fill(r.x() + 2, r.bottom() - 2, r.right() - 2, r.bottom() - 1, IronManGui.alpha(accent, active ? 0.9f : 0.5f));
			}
			ItemStack icon = icon(effectiveId, suit);
			int ix = r.x() + (r.w() - 16) / 2;
			int iy = r.y() + 3;
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
			} else if (IronManAbilities.TIMED_FLIGHT.equals(abilityId) && state.timedFlightUntil > now) {
				// v0.14.27: the flight burst's remaining time, in gold, on its own key box
				String s = String.valueOf(IronManUiLayout.ceilSecs(state.timedFlightUntil - now));
				g.drawString(font, s, r.x() + (r.w() - font.width(s)) / 2, r.y() + 7, IronManGui.GOLD, true);
			}
			if (wheel || mk3Arsenal) {
				// a small gold corner notch marks the weapon-wheel slot
				g.fill(r.right() - 5, r.y() + 1, r.right() - 1, r.y() + 2, IronManGui.GOLD);
				g.fill(r.right() - 2, r.y() + 1, r.right() - 1, r.y() + 5, IronManGui.GOLD);
			}
			g.pose().popPose();
			names[i] = MoveListOverlay.inline(MoveListOverlay.key(i), Component.translatable(nameKey(suit, abilityId, effectiveId)));
		}
		if (expanded) {
			moveList(g, font, slots, names, retro);
		}
	}

	/** The key letter just left of its box (gold while Shift picks the Shift moves). */
	private static void keyTag(GuiGraphics g, Font font, int i, Rect r, boolean retro, boolean shift) {
		String key = keyLabel(i);
		int c = shift ? IronManGui.GOLD : retro ? IronManGui.AMBER : IronManGui.CYAN;
		g.drawString(font, key, r.x() - 3 - font.width(key), r.y() + 7, c, true);
	}

	/** One box's thin holographic frame: a dark translucent fill, a hairline outline and brighter corners. */
	private static void holoBox(GuiGraphics g, Rect r, int override, boolean retro) {
		g.fill(r.x(), r.y(), r.right(), r.bottom(), retro ? 0x90140C04 : 0x80040C14);
		int line = override != 0 ? override : retro ? IronManGui.AMBER_DIM : IronManGui.alpha(IronManGui.CYAN_DIM, 0.75f);
		g.renderOutline(r.x(), r.y(), r.w(), r.h(), line);
		if (override == 0) {
			IronManGui.brackets(g, r.x(), r.y(), r.w(), r.h(), 4, retro ? IronManGui.AMBER : IronManGui.alpha(IronManGui.CYAN, 0.9f));
		}
	}

	/**
	 * v0.16.7 (user): the Left-Alt move list, to the left of the key column -- one row per key, level with its box (two
	 * lines at most, the second shortened if it still does not fit), on a soft dark plate with a cyan edge.
	 */
	private static void moveList(GuiGraphics g, Font font, Rect[] slots, MoveListOverlay.Row[] rows, boolean retro) {
		int left = slots[0].right() + 6; // v0.16.7 (user): the column is on the left, so the list opens to its right
		for (int i = 0; i < 6; i++) {
			MoveListOverlay.Row row = rows[i];
			if (row == null || row.plain() == null) {
				continue;
			}
			// rows level with vanilla's bottom-centre stack (hotbar, XP, hearts / food, held-item name, action bar) stop short of it
			int rightLimit = slots[i].bottom() > g.guiHeight() - 76 ? g.guiWidth() / 2 - 91 - 29 - 4 : g.guiWidth() / 2 - 16;
			int maxW = Math.max(40, Math.min(MoveListOverlay.maxWidth(g), rightLimit - left - 4));
			// one line when it fits; else the tap move on line one and the Shift move (+ cooldown) on line two, each shortened to fit
			net.minecraft.network.chat.MutableComponent tap = row.plain().copy().withStyle(s -> s.withColor(IronManGui.TEXT & 0xFFFFFF));
			net.minecraft.network.chat.MutableComponent second = Component.empty();
			if (row.shift() != null) {
				second.append(Component.translatable("hud.projecthero.move_list.shift", row.shift())
						.withStyle(s -> s.withColor(IronManGui.GOLD & 0xFFFFFF)));
			}
			if (row.extra() != null) {
				if (row.shift() != null) {
					second.append(Component.literal("  "));
				}
				second.append(row.extra().copy().withStyle(s -> s.withColor(IronManGui.TEXT_DIM & 0xFFFFFF)));
			}
			boolean hasSecond = row.shift() != null || row.extra() != null;
			net.minecraft.network.chat.MutableComponent one = tap.copy();
			if (hasSecond) {
				one.append(Component.literal("  ")).append(second.copy());
			}
			List<FormattedCharSequence> shown = new ArrayList<>();
			if (font.width(one) <= maxW) {
				shown.add(one.getVisualOrderText());
			} else {
				shown.add(fitLine(font, tap, maxW));
				if (hasSecond) {
					shown.add(fitLine(font, second, maxW));
				}
			}
			int w = 0;
			for (FormattedCharSequence p : shown) {
				w = Math.max(w, font.width(p));
			}
			Rect r = slots[i];
			int h = shown.size() * 10 + 2;
			int x = left + 4;
			int y = r.y() + (r.h() - h) / 2;
			g.fill(left - 2, y - 1, x + w + 3, y + h, 0xC0040A12);
			g.fill(left - 2, y - 1, left - 1, y + h, IronManGui.alpha(retro ? IronManGui.AMBER : IronManGui.CYAN, 0.9f));
			g.fill(left - 4, r.y() + r.h() / 2, left - 2, r.y() + r.h() / 2 + 1, IronManGui.alpha(IronManGui.CYAN_DIM, 0.8f));
			int ly = y + 1;
			for (FormattedCharSequence p : shown) {
				g.drawString(font, p, x, ly, IronManGui.TEXT, false);
				ly += 10;
			}
		}
	}

	/** A styled line cut to {@code maxW} with a trailing "..." (its colours kept), unchanged when it fits. */
	private static FormattedCharSequence fitLine(Font font, Component c, int maxW) {
		if (font.width(c) <= maxW) {
			return c.getVisualOrderText();
		}
		net.minecraft.network.chat.FormattedText cut = font.substrByWidth(c, Math.max(0, maxW - font.width("...")));
		return net.minecraft.locale.Language.getInstance().getVisualOrder(
				net.minecraft.network.chat.FormattedText.composite(cut, Component.literal("...").withStyle(s -> s.withColor(IronManGui.TEXT_DIM & 0xFFFFFF))));
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

	/** v0.16.1: one comic-kit key box. Returns the expanded-list line ("[R] Tap / Shift  cooldowns"). */
	private static MoveListOverlay.Row comicBox(GuiGraphics g, Font font, Player player, TonyStarkState state, String suitId, int i, Rect r, long now,
			boolean retro, boolean offline, int accent) {
		var kitKey = com.projecthero.mod.ironman.ability.IronManComicKit.kit(suitId) == null ? null
				: com.projecthero.mod.ironman.ability.IronManComicKit.kit(suitId).key(i + 1);
		if (kitKey == null) {
			return null;
		}
		boolean shift = player.isShiftKeyDown();
		if (kitKey.shift() == null) {
			// v0.16.7 (user) Era I: the C box -- its tap move, and with Shift held the emergency EJECT
			// v0.16.7 (user) Era II: its tap move (a utility moved off V), and with Shift held FOLD into the portable form
			return ejectKeyBox(g, font, player, state, suitId, kitKey.tap(), i, r, now, retro, offline, accent, shift);
		}
		var shown = kitKey.get(shift);
		var other = kitKey.get(!shift);
		String cdKey = suitId + "/" + shown.cooldownKey();
		Long readyAt = state.abilityReadyAt.get(cdKey);
		long left = readyAt == null ? 0 : Math.max(0, readyAt - now);
		Long otherAt = state.abilityReadyAt.get(suitId + "/" + other.cooldownKey());
		long otherLeft = otherAt == null ? 0 : Math.max(0, otherAt - now);

		holoBox(g, r, offline ? 0xFF8A2828 : shift ? IronManGui.GOLD : 0, retro); // v0.16.7 (user): the holographic frame
		if (!offline && left <= 0) {
			g.fill(r.x() + 1, r.bottom() - 2, r.right() - 1, r.bottom() - 1, IronManGui.alpha(accent, 0.6f));
		}
		g.renderFakeItem(new ItemStack(shown.icon()), r.x() + (r.w() - 16) / 2, r.y() + 3);
		g.pose().pushPose();
		g.pose().translate(0, 0, 200);
		if (left > 0) {
			float frac = cooldownFrac(cdKey, readyAt, now);
			int coverH = Math.round((r.h() - 2) * frac);
			g.fill(r.x() + 1, r.bottom() - 1 - coverH, r.right() - 1, r.bottom() - 1, 0xB0000000);
			String s = String.valueOf(IronManUiLayout.ceilSecs(left));
			g.drawString(font, s, r.x() + (r.w() - font.width(s)) / 2, r.y() + 7, 0xFFFFFFFF, true);
		} else if (offline) {
			g.fill(r.x() + 1, r.y() + 1, r.right() - 1, r.bottom() - 1, 0x90200000);
		}
		// v0.16.7 (user) Veronica: a move a broken Hulkbuster part has taken offline -- greyed, with a red cross
		if (com.projecthero.mod.ironman.HulkbusterParts.SUIT_ID.equals(suitId) && com.projecthero.mod.ironman.HulkbusterParts
				.blockedBy(shown.id(), com.projecthero.mod.ironman.HulkbusterParts.of(player)) != null) {
			g.fill(r.x() + 1, r.y() + 1, r.right() - 1, r.bottom() - 1, 0xB0303030);
			int cx = r.x() + r.w() / 2, cy = r.y() + 11, s = 6;
			for (int k = -s; k <= s; k++) {
				g.fill(cx + k, cy + k, cx + k + 2, cy + k + 2, 0xFFFF3A3A);
				g.fill(cx + k, cy - k - 1, cx + k + 2, cy - k + 1, 0xFFFF3A3A);
			}
		}
		// the other variant's cooldown: a thin bar along the top edge (red while it cools down)
		if (otherLeft > 0) {
			g.fill(r.x() + 1, r.y() + 1, r.right() - 1, r.y() + 2, 0xC0FF5050);
		}
		if (shift) {
			g.drawString(font, "⇧", r.right() - 7, r.y() + 1, IronManGui.GOLD, true);
		}
		g.pose().popPose();
		// v0.16.7: a row of the shared move list (tap move, its Shift move, the cooldown)
		return MoveListOverlay.Row.of(MoveListOverlay.key(i), Component.translatable(kitKey.tap().nameKey()),
				Component.translatable(kitKey.shift().nameKey()), left > 0 ? Component.literal(IronManUiLayout.secs(left)) : null);
	}

	/**
	 * v0.16.7 (user) Era I: the C key box. Plain: the tap move (icon, cooldown sweep) like any kit box. Shift held: EJECT --
	 * the Suit Crate icon in a gold frame (the whole box glows while the 3 s hold runs). The move-list row is
	 * "[C] tap / Shift: Emergency eject (hold 3 s)".
	 */
	private static MoveListOverlay.Row ejectKeyBox(GuiGraphics g, Font font, Player player, TonyStarkState state, String suitId,
			com.projecthero.mod.ironman.ability.IronManComicKit.Move tap, int i, Rect r, long now, boolean retro, boolean offline,
			int accent, boolean shift) {
		String cdKey = suitId + "/" + tap.cooldownKey();
		Long readyAt = state.abilityReadyAt.get(cdKey);
		long left = readyAt == null ? 0 : Math.max(0, readyAt - now);
		// v0.16.7 (user) Era II: Shift+C folds into the portable form (its packed item is the Shift icon)
		com.projecthero.mod.ironman.suit.PortableForm fold = com.projecthero.mod.ironman.ability.era1.IronManEraOne.isEraOne(suitId)
				? null : com.projecthero.mod.ironman.suit.PortableForms.byId(suitId);
		// v0.16.7 (user): the Hulkbuster / Silver Centurion C has a hold move -- the box fills while C is held
		boolean holdKey = tap.input() == com.projecthero.mod.ironman.ability.IronManComicKit.Input.HOLD;
		boolean cDown = holdKey && !shift && Minecraft.getInstance().screen == null && ModKeyBindings.ABILITY_SLOTS[5].isDown();
		if (!cDown) {
			cHeldSince = -1L;
		} else if (cHeldSince < 0L || cHeldSince > now) {
			cHeldSince = now;
		}
		holoBox(g, r, shift ? IronManGui.GOLD : offline ? 0xFF8A2828 : 0, retro); // v0.16.7 (user): the holographic frame
		if (shift) {
			if (fold == null && ejectHeldSince >= 0L) {
				float p = Math.min(1f, (now - ejectHeldSince) / (float) com.projecthero.mod.ironman.ability.era1.EraOneEject.HOLD_TICKS);
				int fillH = Math.round((r.h() - 2) * p);
				g.fill(r.x() + 1, r.bottom() - 1 - fillH, r.right() - 1, r.bottom() - 1, 0x80FFC24A);
			}
			g.renderFakeItem(new ItemStack(fold != null ? fold.packedItem().get() : IronManItems.SUIT_CRATE), r.x() + (r.w() - 16) / 2, r.y() + 3);
		} else {
			if (cDown) {
				float p = Math.min(1f, (now - cHeldSince) / (float) com.projecthero.mod.ironman.ability.IronManComicMoves.SENTRY_HOLD_TICKS);
				int fillH = Math.round((r.h() - 2) * p);
				g.fill(r.x() + 1, r.bottom() - 1 - fillH, r.right() - 1, r.bottom() - 1, p >= 1f ? 0x90FFC24A : 0x604FE0FF);
			}
			if (!offline && left <= 0) {
				g.fill(r.x() + 1, r.bottom() - 2, r.right() - 1, r.bottom() - 1, IronManGui.alpha(accent, 0.6f));
			}
			g.renderFakeItem(new ItemStack(tap.icon()), r.x() + (r.w() - 16) / 2, r.y() + 3);
		}
		g.pose().pushPose();
		g.pose().translate(0, 0, 200);
		if (shift) {
			// small (x0.7) so the word fits the 26 px box
			String e = Component.translatable(fold != null ? "hud.projecthero.ironman.era2.fold" : "hud.projecthero.ironman.era1.eject").getString();
			float sc = Math.min(0.7f, (r.w() - 2) / (float) Math.max(1, font.width(e)));
			g.pose().pushPose();
			g.pose().translate(r.x() + r.w() / 2f, r.bottom() - 7f, 0f);
			g.pose().scale(sc, sc, 1f);
			g.drawString(font, e, -font.width(e) / 2, 0, IronManGui.GOLD, true);
			g.pose().popPose();
		} else if (left > 0) {
			float frac = cooldownFrac(cdKey, readyAt, now);
			int coverH = Math.round((r.h() - 2) * frac);
			g.fill(r.x() + 1, r.bottom() - 1 - coverH, r.right() - 1, r.bottom() - 1, 0xB0000000);
			String s = String.valueOf(IronManUiLayout.ceilSecs(left));
			g.drawString(font, s, r.x() + (r.w() - font.width(s)) / 2, r.y() + 7, 0xFFFFFFFF, true);
		} else if (offline) {
			g.fill(r.x() + 1, r.y() + 1, r.right() - 1, r.bottom() - 1, 0x90200000);
		}
		if (shift) {
			g.drawString(font, "⇧", r.right() - 7, r.y() + 1, IronManGui.GOLD, true);
		}
		g.pose().popPose();
		return MoveListOverlay.Row.of(MoveListOverlay.key(i), Component.translatable(tap.nameKey()),
				Component.translatable(fold != null ? "hud.projecthero.ironman.era2.fold_move" : "hud.projecthero.ironman.era1.eject_move"),
				left > 0 ? Component.literal(IronManUiLayout.secs(left)) : null);
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
