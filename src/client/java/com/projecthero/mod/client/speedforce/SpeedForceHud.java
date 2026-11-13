package com.projecthero.mod.client.speedforce;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.client.gui.MoveListOverlay;
import com.projecthero.mod.client.gui.VanillaHud;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.visual.MutationMeters;
import com.projecthero.mod.speedforce.SpeedForceAbilities;
import com.projecthero.mod.speedforce.SpeedForceAbilityManager;
import com.projecthero.mod.speedforce.SpeedForceColours;
import com.projecthero.mod.speedforce.SpeedForceConfig;
import com.projecthero.mod.speedforce.SpeedForceRealm;
import com.projecthero.mod.speedforce.SpeedForceSpeedster;
import com.projecthero.mod.speedforce.data.SpeedForceState;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * v0.17.1: the Speed Force Speedster's HUD. v0.17.3 (user: "I really like the Minecraft-style HUD and ability keybinds ... apply
 * this to all the powers"): built from the game's own pieces on the shared {@link VanillaHud.Stack}, bottom-right:
 * <pre>
 *   73 mph  Top 60 b/s  Suit +50%  Best 41 b/s     suited: the speed readouts (were the top-left speed panel)
 *   Speed Force - Speed Mode                        the title and the mode
 *   Drained / Remnant / Flash Time / Portal / Charge / Static   Hairline rows, only while they matter
 *   Speed [Meter ========---]  38                   suited: the speed up to the top speed (red past the sound barrier)
 *   Momentum [Meter ====----]  42%                  in Speed Mode
 *   Speed Force [Meter =====-]  80                  in Speed Mode or while it refills
 *   +----+----+----+----+----+----+                 R G Z X C V (never H or N): the shown move's cooldown sweeps the slot, the
 *   | R  | G  | Z  | X  | C  | V  |                 other (Shift) move's is the durability bar, the hold move's a second bar
 *   +----+----+----+----+----+----+                 over it; Z's portal fills the slot while it forms; a running move lights up
 * </pre>
 * Hold Left-Alt for the move names. v0.17.1 (user: "make the speed force speedster hud colours change depending on what lightning
 * colour the player chose"): the title and the values are drawn in the player's own {@link SpeedForceColours} tones ({@link Palette}).
 */
public final class SpeedForceHud {
	private static final float MPH_PER_BPS = 2.23694f;

	private static final int WHITE = VanillaHud.WHITE;
	private static final int DIM = VanillaHud.GREY;
	private static final int RED = VanillaHud.RED;
	private static final int FLASH_BLUE = 0xFF8FD8FF;

	private static final AbilitySlot[] SLOTS = VanillaHud.ORDER; // v0.17.1 (user): H and N never get a box on an ability HUD

	private static float speed;
	private static float top;
	private static int idleTicks;

	private SpeedForceHud() {
	}

	/**
	 * The HUD's colours from one lightning colour: {@code accent} (titles, bars, borders: the brightest-reading of the
	 * colour's tones), {@code light} (the meter, the ready strips), {@code deep} (the trough the bars run in, the panel's
	 * title band and edge).
	 */
	public record Palette(int accent, int light, int deep, int edge) {
		public static Palette of(int colour) {
			int t = 1;
			while (t < 3 && luminance(SpeedForceColours.tone(colour, t)) < 0.45) {
				t++;
			}
			int accent = 0xFF000000 | SpeedForceColours.tone(colour, t);
			int light = 0xFF000000 | SpeedForceColours.tone(colour, Math.min(3, t + 1));
			int base = SpeedForceColours.tone(colour, t == 1 ? 0 : t - 1);
			int deep = 0xFF000000 | scale(base, 0.28f);
			int edge = 0xFF000000 | scale(SpeedForceColours.tone(colour, t), 0.7f);
			return new Palette(accent, light, deep, edge);
		}

		private static double luminance(int rgb) {
			return (0.299 * ((rgb >> 16) & 0xFF) + 0.587 * ((rgb >> 8) & 0xFF) + 0.114 * (rgb & 0xFF)) / 255.0;
		}

		private static int scale(int rgb, float f) {
			int r = Math.round(((rgb >> 16) & 0xFF) * f);
			int g = Math.round(((rgb >> 8) & 0xFF) * f);
			int b = Math.round((rgb & 0xFF) * f);
			return (r << 16) | (g << 8) | b;
		}
	}

	static boolean shows(Player p) {
		if (p == null || !SpeedForceSpeedster.hasPower(p)) {
			return false;
		}
		var exp = p.getAttachedOrElse(ModAttachments.EXPERIMENTAL_STATE, null);
		return exp == null || exp.activePower.isEmpty();
	}

	static void tick(Minecraft mc) {
		Player p = mc.player;
		if (!shows(p) || mc.isPaused()) {
			return;
		}
		double dx = p.getX() - p.xo;
		double dy = p.getY() - p.yo;
		double dz = p.getZ() - p.zo;
		float now = (float) Math.sqrt(dx * dx + dy * dy + dz * dz) * 20f;
		if (now > 400f) {
			now = speed; // a teleport (Rewind)
		}
		speed = Mth.lerp(0.35f, speed, now);
		if (speed > top) {
			top = speed;
		}
		idleTicks = speed < 1f ? idleTicks + 1 : 0;
		if (idleTicks > 200) {
			top = 0f;
		}
	}

	public static void render(GuiGraphics g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		Player p = mc.player;
		if (mc.options.hideGui || mc.level == null || !shows(p)) {
			return;
		}
		if (com.projecthero.mod.client.flash.FlashSuitHud.replacesSpeedForceHud(p)) {
			return; // v0.17.1 (user): in the full Flash Suit the visor HUD is the one HUD (it draws these keys itself)
		}
		SpeedForceState s = SpeedForceSpeedster.peek(p);
		if (s == null) {
			return;
		}
		long now = mc.level.getGameTime();
		Palette pal = Palette.of(SpeedForceColours.clamp(s.colour));
		boolean suit = SpeedForceSpeedster.suited(p);
		float max = SpeedForceSpeedster.meterMax(p);

		VanillaHud.Stack st = VanillaHud.Stack.bottomRight(g, SLOTS.length).keys(SLOTS.length);
		slots(st, p, s, now, p.isShiftKeyDown());

		// the meters, the steady ones nearest the keys
		if (s.speedMode || s.meter < max) {
			st.meter(Component.translatable("hud.projecthero.speed_force.meter"), s.meter / max, s.drained > 0 ? DIM : pal.light(),
					String.format(Locale.ROOT, "%.0f", s.meter), s.drained > 0 ? DIM : pal.light());
		}
		if (s.speedMode) {
			st.meter(Component.translatable("hud.projecthero.speed_force.momentum"), s.momentum, pal.accent(),
					String.format(Locale.ROOT, "%.0f%%", s.momentum * 100f), s.surgeUntil > now ? WHITE : pal.accent());
		}
		float topBps = (float) SpeedForceConfig.topBps(suit);
		if (suit) {
			// v0.17.3: the suited speed panel's gauge, now a row in the column (red past the sound barrier)
			boolean sonic = speed >= SpeedForceConfig.SONIC_BOOM_BPS;
			st.meter(Component.translatable("hud.projecthero.vanilla_hud.speed"), speed / topBps, sonic ? RED : pal.accent(),
					String.format(Locale.ROOT, "%.0f", speed), sonic ? RED : pal.accent());
		}
		// v0.17.1 (user): the static charge -- only while there is some (the next melee hit lets it go as chain lightning)
		Integer charge = p.getAttached(com.projecthero.mod.speedforce.SpeedForceAttachments.STATIC_CHARGE);
		if (charge != null && charge > 0 && s.speedMode) {
			boolean ready = charge >= SpeedForceConfig.STATIC_MIN_RELEASE;
			// charged: the value flickers white (the next hit lets it go)
			int valueColour = ready ? ((now / 4) % 2 == 0 ? WHITE : pal.light()) : DIM;
			hairline(st, Component.translatable("hud.projecthero.speed_force.static"), charge / SpeedForceConfig.STATIC_MAX,
					String.valueOf(charge), ready ? pal.light() : DIM, valueColour);
		}
		if (s.chargeStart != 0L) {
			float c = Math.min(1f, (now - s.chargeStart) / (float) SpeedForceConfig.CHARGE_TICKS);
			hairline(st, Component.translatable("hud.projecthero.speed_force.charge"), c,
					c >= 1f ? Component.translatable("hud.projecthero.speed_force.ready").getString() : pct(c), c >= 1f ? WHITE : pal.accent(),
					c >= 1f ? WHITE : pal.accent());
		}
		if (s.flashLeft == SpeedForceState.Z_PORTAL) {
			float c = Math.min(1f, s.flashTotal / (float) SpeedForceConfig.PORTAL_TICKS);
			hairline(st, Component.translatable("hud.projecthero.speed_force.portal"), c, pct(c), pal.light(), pal.light());
		}
		if (s.flashLeft == SpeedForceState.Z_FLASH_TIME) {
			hairline(st, Component.translatable("hud.projecthero.speed_force.flash_time"), s.meter / max,
					Component.translatable("hud.projecthero.speed_force.per_second",
							String.format(Locale.ROOT, "%.0f", SpeedForceConfig.FLASH_COST_PER_SECOND)).getString(), FLASH_BLUE, FLASH_BLUE);
		}
		if (s.remnantUntil > now) {
			hairline(st, Component.translatable("hud.projecthero.speed_force.remnant"), (s.remnantUntil - now) / (float) SpeedForceConfig.REMNANT_TICKS,
					seconds(s.remnantUntil - now), pal.accent(), pal.accent());
		}
		if (s.drained > 0) {
			hairline(st, Component.translatable("hud.projecthero.speed_force.drained"), s.drained / (float) SpeedForceConfig.DRAINED_TICKS,
					seconds(s.drained), RED, RED);
		}

		// the title: the power and the mode
		boolean realm = SpeedForceRealm.inside(p);
		Component mode = Component.translatable(realm ? "hud.projecthero.speed_force.mode.realm"
				: s.chargeStart != 0L ? "hud.projecthero.speed_force.mode.charging"
				: s.flashLeft == SpeedForceState.Z_PORTAL ? "hud.projecthero.speed_force.mode.portal"
				: s.flashLeft == SpeedForceState.Z_FLASH_TIME ? "hud.projecthero.speed_force.mode.flash_time"
				: s.phasing ? "hud.projecthero.speed_force.mode.phasing"
				: s.drained > 0 ? "hud.projecthero.speed_force.mode.drained"
				: s.speedMode ? "hud.projecthero.speed_force.mode.speed" : "hud.projecthero.speed_force.mode.walk");
		boolean lit = s.speedMode || s.chargeStart != 0L || realm;
		st.title(Component.translatable("hud.projecthero.speed_force.title").append(" - ").append(mode),
				s.drained > 0 ? RED : lit ? pal.light() : WHITE);

		if (suit) {
			// v0.17.3: the rest of the suited speed panel as status words (mph, the top speed, the suit bonus, the run's best)
			List<VanillaHud.Tag> tags = new ArrayList<>();
			tags.add(new VanillaHud.Tag(Component.translatable("hud.projecthero.flash.mph",
					String.format(Locale.ROOT, "%.0f", speed * MPH_PER_BPS)).getString(), DIM));
			tags.add(new VanillaHud.Tag(Component.translatable("hud.projecthero.speed_force.top",
					String.format(Locale.ROOT, "%.0f", topBps)).getString(), DIM));
			tags.add(new VanillaHud.Tag(Component.translatable("hud.projecthero.speed_force.suit").getString(), pal.accent()));
			if (top > 0.5f) {
				tags.add(new VanillaHud.Tag(Component.translatable("hud.projecthero.vanilla_hud.speed_best",
						String.format(Locale.ROOT, "%.0f", top)).getString(), RED));
			}
			st.tags(tags);
		}
		st.moveList(moveRows());
	}

	/** A Hairline row (a timer or a charge that only shows while it matters). */
	private static void hairline(VanillaHud.Stack st, Component label, float frac, String value, int colour, int valueColour) {
		st.meter(label, frac, colour, value, valueColour, MutationMeters.Style.HAIRLINE);
	}

	/**
	 * v0.17.3: the six keys (R G Z X C V -- never H or N) on a {@link VanillaHud.Stack}'s strip (call {@code keys(6)} first). The
	 * shown move (the tap move, the Shift move while Shift is held) sweeps the slot with its cooldown, the other one is the
	 * durability bar, the hold move's cooldown a second durability bar over it; Z's portal fills the slot while it forms; a
	 * running move gets the selection frame. Shared by this HUD and the Flash Suit visor.
	 */
	public static void slots(VanillaHud.Stack st, Player p, SpeedForceState s, long now, boolean shift) {
		for (int i = 0; i < SLOTS.length; i++) {
			AbilitySlot slot = SLOTS[i];
			String[] ids = SpeedForceAbilityManager.idsOf(slot);
			if (ids.length == 0) {
				continue;
			}
			String shown = ids[shift && ids.length > 1 ? 1 : 0];
			String other = ids.length > 1 ? ids[shift ? 0 : 1] : null;
			int cd = SpeedForceSpeedster.cooldownRemaining(p, shown);
			boolean active = (slot == AbilitySlot.SLOT_1 && s.holdAnim && s.animId == SpeedForceState.ANIM_MASS_WINDUP)
					|| (slot == AbilitySlot.SLOT_2 && (s.tornadoLeft > 0 || (s.holdAnim && s.animId == SpeedForceState.ANIM_THROW_CHARGE)))
					|| (slot == AbilitySlot.SLOT_3 && SpeedForceAbilities.tornadoArms(p))
					|| (slot == AbilitySlot.SLOT_4 && (s.flashLeft > 0 || s.remnantUntil > now))
					|| (slot == AbilitySlot.SLOT_6 && (s.speedMode || s.chargeStart != 0L || s.phasing));
			st.slot(i, VanillaHud.keyLabel(slot), true, shift, cooldown(p, shown), VanillaHud.seconds(cd),
					other == null ? 0f : cooldown(p, other), active);
			if (slot == AbilitySlot.SLOT_4 && s.flashLeft == SpeedForceState.Z_PORTAL) {
				st.hold(i, Math.min(1f, s.flashTotal / (float) SpeedForceConfig.PORTAL_TICKS)); // the portal forming
			} else if (ids.length > 2) {
				holdBar(st, i, cooldown(p, ids[2]));
			}
		}
	}

	/** The move list (Left-Alt): tap / Shift / hold per key, then the Speed Force menu key. */
	public static List<MoveListOverlay.Row> moveRows() {
		List<MoveListOverlay.Row> rows = new ArrayList<>();
		for (AbilitySlot slot : SLOTS) {
			String[] ids = SpeedForceAbilityManager.idsOf(slot);
			if (ids.length < 3) {
				continue;
			}
			String key = MoveListOverlay.key(slot);
			// Z has no tap: Flash Time IS the hold (from the press), its portal the third
			Component plain = slot == AbilitySlot.SLOT_4 ? Component.translatable("hud.projecthero.speed_force.hold", name(ids[0])) : name(ids[0]);
			Component hold = Component.translatable("hud.projecthero.speed_force.hold", name(ids[2]));
			rows.add(MoveListOverlay.Row.of(key, plain, name(ids[1]), hold));
		}
		rows.add(MoveListOverlay.Row.of(MoveListOverlay.key(AbilitySlot.SLOT_8), name(SpeedForceAbilities.RESCUE),
				Component.translatable("projecthero.speed_force_speedster.ability.speed_force_menu")));
		return rows;
	}

	/** 0..1 of {@code id}'s cooldown left. */
	private static float cooldown(Player p, String id) {
		int cd = SpeedForceSpeedster.cooldownRemaining(p, id);
		return cd <= 0 ? 0f : Math.min(1f, cd / (float) Math.max(1, SpeedForceAbilityManager.maxCooldown(id)));
	}

	/**
	 * The hold move's cooldown: a second durability bar just over the slot's own (the Shift move's), red when it has just gone,
	 * green as it comes back.
	 */
	private static void holdBar(VanillaHud.Stack st, int i, float cooldown) {
		if (cooldown <= 0f) {
			return;
		}
		int ix = st.stripX() + 3 + i * VanillaHud.SLOT;
		int iy = st.stripY() + 3;
		float left = 1f - Mth.clamp(cooldown, 0f, 1f);
		int w = Math.max(1, Math.round(13f * left));
		st.g.fill(RenderType.guiOverlay(), ix + 2, iy + 11, ix + 15, iy + 13, 0xFF000000);
		st.g.fill(RenderType.guiOverlay(), ix + 2, iy + 11, ix + 2 + w, iy + 12, 0xFF000000 | Mth.hsvToRgb(left / 3f, 1f, 1f));
	}

	private static Component name(String id) {
		return Component.translatable("projecthero.speed_force_speedster.ability." + id);
	}

	private static String seconds(long ticks) {
		return String.format(Locale.ROOT, "%ds", (Math.max(0, ticks) + 19) / 20);
	}

	private static String pct(float f) {
		return String.format(Locale.ROOT, "%.0f%%", f * 100f);
	}
}
