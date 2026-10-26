package com.projecthero.mod.client.flash;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.client.ModKeyBindings;
import com.projecthero.mod.client.speedforce.SpeedForceHud;
import com.projecthero.mod.client.speedforce.SpeedForceHud.Palette;
import com.projecthero.mod.flash.FlashSuit;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.data.ExperimentalState;
import com.projecthero.mod.hero.power.p04.SuperSpeedHandlers;
import com.projecthero.mod.hero.power.p04.SuperSpeedTimeSlow;
import com.projecthero.mod.speedforce.SpeedForceAbilities;
import com.projecthero.mod.speedforce.SpeedForceColours;
import com.projecthero.mod.speedforce.SpeedForceConfig;
import com.projecthero.mod.speedforce.SpeedForceRealm;
import com.projecthero.mod.speedforce.SpeedForceSpeedster;
import com.projecthero.mod.speedforce.data.SpeedForceState;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1 (user: "change the look of the flash suit heads up display to make it look better ... when the flash suit heads up
 * display is on then the regular speed force speedster HUD should disappear ... spread out similar to the iron man armour
 * heads up display"): the Flash Suit visor -- the ONE HUD while a speedster wears the full suit, its readouts spread round
 * the screen edges like the Iron Man helmet's, in the Flash's red and gold with the gauges in the runner's own lightning
 * colour ({@link SpeedForceColours}).
 * <ul>
 *   <li><b>Visor</b> -- a faint cowl-lens vignette and angled corner brackets; the faster you run, the deeper the tint and
 *       the more lightning crackles down the screen edges; Flash Time washes it blue.</li>
 *   <li><b>Top-left</b> -- the bolt, THE FLASH and the mode (Speed Mode, Flash Time, Phasing, the Speed Force...).</li>
 *   <li><b>Top-centre</b> -- a heading strip (compass) with the status chips under it ({@link #statusChips}: Momentum
 *       Lock, Cruise, Ground-Stick, Surge, the sound barrier...). Pushed down under any boss bars.</li>
 *   <li><b>Top-right</b> -- the timers that are running ({@link #timers}: the charge, Flash Time, the portal, the Remnant,
 *       the drain, the Black Flash's power loss; Overdrive / Time Slow / exhaustion for a plain Speedster), under the
 *       status-effect icons.</li>
 *   <li><b>Left edge</b> -- the Speed Force meter, a curved lens-edge gauge (above the chat).</li>
 *   <li><b>Crosshair</b> -- two thin brackets in Speed Mode, and a target card (name, health, distance) for the mob you
 *       look at, up to 48 blocks.</li>
 *   <li><b>Right edge</b> -- the six key boxes stacked in a column (R G Z X C V -- never H or N), drawn by
 *       {@link SpeedForceHud#drawKeys}; Left Alt lists the moves to their left.</li>
 *   <li><b>Bottom-right corner</b> -- the speedometer dial beside the column (blocks a second, the momentum % in its gap,
 *       mph and the run's top speed over it; the outer ring is the speed up to your top speed with the sound barrier marked,
 *       the inner ring the momentum with the 25 / 60% tiers). A plain Speedster keeps the Super Speed key row (AbilityHud)
 *       and the dial sits above it.</li>
 * </ul>
 * Nothing sits over the hotbar, the health / hunger bars or the action-bar line; the HUD hides with F1 and behind any
 * screen but the chat. New Speed Force info hooks in through {@link #addChipSource} / {@link #addTimerSource}.
 */
public final class FlashSuitHud {
	private static final String PLAIN_KEY = SuperSpeedHandlers.KEY;
	/** The plain Speedster's dial: Overdrive's top sprint, blocks a second. */
	private static final float PLAIN_MAX = 120f;
	private static final float MPH_PER_BPS = 2.23694f;
	private static final double TARGET_RANGE = 48.0;

	private static final int WHITE = 0xFFFFFFFF;
	private static final int DIM = 0xFF9A8F8A;
	private static final int RED = 0xFFFF3A2E;
	private static final int FLASH_RED = 0xFFD8231B;
	private static final int FLASH_RED_DEEP = 0xFF5A0A08;
	private static final int GOLD = 0xFFFFD23A;
	private static final int FLASH_BLUE = 0xFF8FD8FF;
	private static final int PHASE = 0xFFC8B8FF;
	private static final int PLATE = 0x9A0C0505;

	/** A status chip under the heading strip. */
	public record Chip(Component text, int colour) {
	}

	/** A timer row top-right: the label, the bar's fill (0..1), the value at the right. */
	public record Timer(Component label, float frac, Component value, int colour) {
	}

	private static final List<BiConsumer<Player, List<Chip>>> CHIP_SOURCES = new ArrayList<>();
	private static final List<BiConsumer<Player, List<Timer>>> TIMER_SOURCES = new ArrayList<>();

	private static float speed;
	private static float top;
	private static int idleTicks;
	private static int cHeld;
	private static int targetId = -1;

	private FlashSuitHud() {
	}

	public static void initialize() {
		HudRenderCallback.EVENT.register(FlashSuitHud::render);
		ClientTickEvents.END_CLIENT_TICK.register(FlashSuitHud::tick);
	}

	/** v0.17.1 hook: more status chips (drawn after the built-in ones, Speed Force Speedster or plain Speedster). */
	public static void addChipSource(BiConsumer<Player, List<Chip>> source) {
		CHIP_SOURCES.add(source);
	}

	/** v0.17.1 hook: more timer rows (drawn after the built-in ones). */
	public static void addTimerSource(BiConsumer<Player, List<Timer>> source) {
		TIMER_SOURCES.add(source);
	}

	// ---------------------------------------------------------------- who sees it

	/**
	 * The Speed Force Speedster in the full Flash Suit: the visor replaces {@link SpeedForceHud} entirely (it also shows while
	 * the Black Flash has taken the power, with the time left).
	 */
	public static boolean replacesSpeedForceHud(Player p) {
		if (p == null || !FlashSuit.wearsFull(p)) {
			return false;
		}
		SpeedForceState s = SpeedForceSpeedster.peek(p);
		if (s == null || !s.hasPower) {
			return false;
		}
		ExperimentalState exp = p.getAttachedOrElse(ModAttachments.EXPERIMENTAL_STATE, null);
		return exp == null || exp.activePower.isEmpty();
	}

	/** A plain Super Speed Speedster (the selected power) in the full suit. */
	private static boolean plain(Player p) {
		if (p == null || !FlashSuit.wearsFull(p)) {
			return false;
		}
		ExperimentalState s = p.getAttachedOrElse(ModAttachments.EXPERIMENTAL_STATE, null);
		return s != null && PLAIN_KEY.equals(s.activePower);
	}

	private static boolean shows(Player p) {
		return replacesSpeedForceHud(p) || plain(p);
	}

	// ---------------------------------------------------------------- tick: speed, the C hold, the target

	private static void tick(Minecraft mc) {
		Player p = mc.player;
		if (!shows(p) || mc.isPaused() || mc.level == null) {
			return;
		}
		double dx = p.getX() - p.xo;
		double dy = p.getY() - p.yo;
		double dz = p.getZ() - p.zo;
		float now = (float) Math.sqrt(dx * dx + dy * dy + dz * dz) * 20f;
		if (now > 400f) {
			now = speed; // a teleport (Rewind, the portal)
		}
		speed = Mth.lerp(0.35f, speed, now);
		if (speed > top) {
			top = speed;
		}
		// the run's top speed resets after 10 s standing still
		idleTicks = speed < 1f ? idleTicks + 1 : 0;
		if (idleTicks > 200) {
			top = 0f;
		}
		cHeld = mc.screen == null && ModKeyBindings.ABILITY_SLOTS[AbilitySlot.SLOT_6.ordinal()].isDown() ? cHeld + 1 : 0;
		if (p.tickCount % 2 == 0) {
			targetId = pickTarget(p);
		}
	}

	private static int pickTarget(Player p) {
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getViewVector(1f);
		HitResult block = p.pick(TARGET_RANGE, 1f, false);
		double max = block.getType() == HitResult.Type.MISS ? TARGET_RANGE : block.getLocation().distanceTo(eye);
		Vec3 end = eye.add(look.scale(max));
		AABB box = p.getBoundingBox().expandTowards(look.scale(max)).inflate(1.0);
		EntityHitResult hit = ProjectileUtil.getEntityHitResult(p, eye, end, box,
				e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator() && e.isPickable() && !e.isInvisible(), max * max);
		return hit == null ? -1 : hit.getEntity().getId();
	}

	// ---------------------------------------------------------------- render

	private static void render(GuiGraphics g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		Player p = mc.player;
		if (mc.options.hideGui || mc.level == null || p == null || p.isSpectator()) {
			return;
		}
		if (mc.screen != null && !(mc.screen instanceof ChatScreen)) {
			return;
		}
		boolean sf = replacesSpeedForceHud(p);
		if (!sf && !plain(p)) {
			return;
		}
		Font font = mc.font;
		long now = mc.level.getGameTime();
		int sw = g.guiWidth();
		int sh = g.guiHeight();
		SpeedForceState s = sf ? SpeedForceSpeedster.peek(p) : null;
		ExperimentalState exp = sf ? null : p.getAttachedOrElse(ModAttachments.EXPERIMENTAL_STATE, null);
		Palette pal = Palette.of(sf ? SpeedForceColours.clamp(s.colour) : 0);
		boolean lost = sf && SpeedForceSpeedster.powerLost(p) > 0;
		float topBps = sf ? (float) SpeedForceConfig.topBps(true) : PLAIN_MAX;
		float speedFrac = Mth.clamp(speed / topBps, 0f, 1f);
		boolean flashTime = sf ? s.flashLeft == SpeedForceState.Z_FLASH_TIME : res(exp, SuperSpeedTimeSlow.LEFT) > 0f;

		visor(g, sw, sh, pal, speedFrac, flashTime, lost, now);
		identity(g, font, p, s, exp, pal, lost, now);
		int bosses = bossBars(mc);
		int compassY = 6 + bosses * 19;
		if (compassY > 6) {
			compassY += 6;
		}
		compassY = Math.min(compassY, sh / 3);
		// the top-centre strip stays clear of the timer column top-right (and drops it below the chips on a narrow screen)
		int timersLeft = sw - 8 - TIMER_W - 3;
		int compassW = Mth.clamp(2 * (timersLeft - 36 - sw / 2), 96, 170);
		compass(g, font, sw / 2, compassY, compassW, p.getYRot(), pal);
		List<Chip> chips = statusChips(mc, p, s, exp, now);
		chips(g, font, sw / 2, compassY + 19, Math.max(96, 2 * (timersLeft - 4 - sw / 2)), chips);
		int timersY = effectsBottom(p);
		if (bosses > 0 && timersLeft < sw / 2 + 92) {
			timersY = Math.max(timersY, compassY); // under the boss bars where they reach across
		}
		// the key column along the right edge (Speed Force Speedster; a plain Speedster keeps the Super Speed key row)
		int colX = sw - 7 - 20;
		int colTop = sf && !lost ? sh - 7 - SpeedForceHud.keysColumnHeight() : sh;
		timersBlock(g, font, sw - 8, colX - 6, colTop, timersY, timers(p, s, exp, now));
		if (sf && !lost) {
			meterArc(g, font, sh, s, p, pal, now);
		}
		crosshair(g, font, mc, sw / 2, sh / 2, s, pal, now);

		// bottom-right: the speedometer dial, in the corner beside the key column (over a plain Speedster's key row)
		int r = 29;
		int dcx;
		int dcy;
		if (sf) {
			dcx = colX - 9 - r;
			dcy = sh - 11 - r;
		} else {
			int keysW = SpeedForceHud.keysWidth();
			int abTop = com.projecthero.mod.client.gui.AbilityHud.lastTop;
			int keysY = sh - 7 - SpeedForceHud.keysHeight();
			dcx = sw - 7 - keysW / 2;
			dcy = Math.min(keysY - 6, abTop == Integer.MAX_VALUE ? keysY - 6 : abTop - 4) - 4 - r;
		}
		dial(g, font, dcx, dcy, r, s, exp, pal, topBps, lost, now);
		if (sf && !lost) {
			// a red spine with a gold tip down the key column, then the boxes (Left Alt lists the moves to their left)
			g.fill(colX - 3, colTop, colX - 2, sh - 7, alpha(FLASH_RED, 0.75f));
			g.fill(colX - 3, colTop - 6, colX - 2, colTop, GOLD);
			g.fill(colX - 3, colTop - 6, colX + 20, colTop - 5, GOLD);
			SpeedForceHud.drawKeys(g, mc, p, s, now, pal, colX, colTop, colX - 6, sh - 7, true);
		}
	}

	// ---------------------------------------------------------------- the visor

	private static void visor(GuiGraphics g, int sw, int sh, Palette pal, float speedFrac, boolean flashTime, boolean lost, long now) {
		int tint = flashTime ? (FLASH_BLUE & 0xFFFFFF) : lost ? 0x200404 : (FLASH_RED_DEEP & 0xFFFFFF);
		int edgeA = Math.round(0x22 + 0x5A * speedFrac + (flashTime ? 0x30 : 0));
		if (flashTime) {
			g.fill(0, 0, sw, sh, 0x0E000000 | (FLASH_BLUE & 0xFFFFFF));
		}
		int band = Math.max(10, sh / 11);
		g.fillGradient(0, 0, sw, band, (edgeA << 24) | tint, tint);
		g.fillGradient(0, sh - band, sw, sh, tint, (edgeA << 24) | tint);
		int side = Math.max(12, sw / 16);
		int steps = 10;
		for (int i = 0; i < steps; i++) {
			int a = Math.round(edgeA * (1f - i / (float) steps) * (1f - i / (float) steps));
			int c = (a << 24) | tint;
			int x0 = side * i / steps;
			int x1 = side * (i + 1) / steps;
			g.fill(x0, 0, x1, sh, c);
			g.fill(sw - x1, 0, sw - x0, sh, c);
		}
		// the cowl lens: angled corner brackets
		int bc = lost ? alpha(DIM, 0.45f) : alpha(FLASH_RED, 0.7f);
		int tip = lost ? alpha(DIM, 0.6f) : alpha(GOLD, 0.85f);
		corner(g, 3, 3, 1, 1, bc, tip);
		corner(g, sw - 4, 3, -1, 1, bc, tip);
		corner(g, 3, sh - 4, 1, -1, bc, tip);
		corner(g, sw - 4, sh - 4, -1, -1, bc, tip);
		// lightning crackling down the edges, more the faster you go
		if (!lost && (speedFrac > 0.2f || flashTime)) {
			float amount = flashTime ? 1f : (speedFrac - 0.2f) / 0.8f;
			RandomSource rnd = RandomSource.create(now / 2 * 31L + 7L);
			int bolts = 1 + Math.round(amount * 3f);
			for (int side2 = 0; side2 < 2; side2++) {
				for (int b = 0; b < bolts; b++) {
					if (rnd.nextFloat() > 0.35f + amount * 0.5f) {
						continue;
					}
					int len = 24 + rnd.nextInt(40);
					int y = Math.round(sh * 0.15f) + rnd.nextInt(Math.max(1, Math.round(sh * 0.7f) - len));
					int x = side2 == 0 ? 3 + rnd.nextInt(6) : sw - 4 - rnd.nextInt(6);
					bolt(g, rnd, x, y, len, side2 == 0 ? 1 : -1, alpha(0xFF000000 | pal.light(), 0.85f), alpha(0xFF000000 | pal.accent(), 0.35f));
				}
			}
		}
	}

	/** One angled bracket: a 45-degree cut at the corner, the arms running out from it, a gold tick at each end. */
	private static void corner(GuiGraphics g, int x, int y, int dx, int dy, int colour, int tip) {
		int cut = 6;
		int arm = 26;
		line(g, x, y + dy * cut, x + dx * cut, y, colour);
		hLine(g, x + dx * cut, x + dx * arm, y, colour);
		vLine(g, x, y + dy * cut, y + dy * arm, colour);
		hLine(g, x + dx * (arm - 4), x + dx * arm, y + dy, tip);
		vLine(g, x + dx, y + dy * (arm - 4), y + dy * arm, tip);
	}

	/** A jagged bolt running down from {@code x, y}, jittering toward the screen ({@code inward}) and back. */
	private static void bolt(GuiGraphics g, RandomSource rnd, int x, int y, int len, int inward, int core, int glow) {
		int px = x;
		int py = y;
		int base = x;
		while (py < y + len) {
			int ny = py + 3 + rnd.nextInt(5);
			int nx = base + inward * rnd.nextInt(7) - inward * 2;
			line(g, px + 1, py, nx + 1, ny, glow);
			line(g, px, py, nx, ny, core);
			if (rnd.nextFloat() < 0.18f) {
				// a fork
				line(g, nx, ny, nx + inward * (3 + rnd.nextInt(6)), ny + 2 + rnd.nextInt(5), glow);
			}
			px = nx;
			py = ny;
		}
	}

	// ---------------------------------------------------------------- top-left: identity + mode

	private static void identity(GuiGraphics g, Font font, Player p, SpeedForceState s, ExperimentalState exp, Palette pal, boolean lost, long now) {
		int x = 10;
		int y = 9;
		g.drawString(font, Component.literal("⚡"), x, y, lost ? DIM : GOLD, true);
		g.drawString(font, Component.translatable("hud.projecthero.flash.title"), x + 10, y, WHITE, true);
		Component mode;
		int colour;
		if (s != null) {
			Object[] m = sfMode(p, s, lost, now, pal);
			mode = (Component) m[0];
			colour = (Integer) m[1];
		} else {
			Object[] m = plainMode(p, exp);
			mode = (Component) m[0];
			colour = (Integer) m[1];
		}
		String text = fit(font, mode.getString().toUpperCase(Locale.ROOT), 110);
		g.drawString(font, text, x + 10, y + 11, colour, true);
		int w = Math.max(font.width(text), font.width(Component.translatable("hud.projecthero.flash.title"))) + 14;
		// the underline: red, then a gold slash
		g.fill(x, y + 21, x + w, y + 22, alpha(FLASH_RED, 0.85f));
		line(g, x + w + 1, y + 22, x + w + 5, y + 18, GOLD);
	}

	private static Object[] sfMode(Player p, SpeedForceState s, boolean lost, long now, Palette pal) {
		if (lost) {
			return new Object[] { Component.translatable("hud.projecthero.flash_visor.powerless"), RED };
		}
		if (SpeedForceRealm.inside(p)) {
			return new Object[] { Component.translatable("hud.projecthero.speed_force.mode.realm"), pal.light() };
		}
		if (s.flashLeft == SpeedForceState.Z_PORTAL) {
			return new Object[] { Component.translatable("hud.projecthero.speed_force.mode.portal"), pal.light() };
		}
		if (s.flashLeft == SpeedForceState.Z_FLASH_TIME) {
			return new Object[] { Component.translatable("hud.projecthero.speed_force.mode.flash_time"), FLASH_BLUE };
		}
		if (s.phasing) {
			return new Object[] { Component.translatable("hud.projecthero.speed_force.mode.phasing"), PHASE };
		}
		if (s.tornadoLeft > 0) {
			return new Object[] { Component.translatable("hud.projecthero.speed_force.mode.tornado"), pal.accent() };
		}
		if (SpeedForceAbilities.tornadoArms(p)) {
			return new Object[] { Component.translatable("hud.projecthero.speed_force.mode.arms"), pal.accent() };
		}
		if (s.carryId != 0) {
			return new Object[] { Component.translatable("hud.projecthero.speed_force.mode.carrying"), pal.accent() };
		}
		if (s.chargeStart != 0L) {
			return new Object[] { Component.translatable("hud.projecthero.speed_force.mode.charging"), pal.light() };
		}
		if (s.drained > 0) {
			return new Object[] { Component.translatable("hud.projecthero.speed_force.mode.drained"), RED };
		}
		if (s.speedMode) {
			return new Object[] { Component.translatable(speed > 12f ? "hud.projecthero.speed_force.mode.run" : "hud.projecthero.speed_force.mode.speed"),
					speed >= SpeedForceConfig.SONIC_BOOM_BPS ? RED : pal.accent() };
		}
		return new Object[] { Component.translatable("hud.projecthero.speed_force.mode.walk"), DIM };
	}

	private static Object[] plainMode(Player p, ExperimentalState s) {
		if (res(s, SuperSpeedHandlers.EXHAUST) > 0f) {
			return new Object[] { Component.translatable("hud.projecthero.flash.mode.exhausted"), DIM };
		}
		if (res(s, SuperSpeedTimeSlow.LEFT) > 0f) {
			return new Object[] { Component.translatable("hud.projecthero.flash.mode.time_slow"), FLASH_BLUE };
		}
		if (SuperSpeedHandlers.phasing(p)) {
			return new Object[] { Component.translatable("hud.projecthero.flash.mode.phasing"), PHASE };
		}
		if (res(s, SuperSpeedHandlers.OVERDRIVE_LEFT) > 0f) {
			return new Object[] { Component.translatable("hud.projecthero.flash.mode.overdrive"), RED };
		}
		if (s.activeToggles.contains(PLAIN_KEY + "/speed_mode")) {
			return new Object[] { Component.translatable("hud.projecthero.flash.mode.speed"), GOLD };
		}
		return new Object[] { Component.translatable("hud.projecthero.flash.mode.walk"), WHITE };
	}

	// ---------------------------------------------------------------- top-centre: heading + chips

	private static final String[] POINTS = { "N", "NE", "E", "SE", "S", "SW", "W", "NW" };

	private static void compass(GuiGraphics g, Font font, int cx, int y, int w, float yaw, Palette pal) {
		float bearing = Mth.positiveModulo(yaw + 180f, 360f);
		float ppd = w / 110f; // about 110 degrees across
		int x0 = cx - w / 2;
		int x1 = cx + w / 2;
		// the strip: a faint plate fading out at both ends
		for (int i = 0; i < w; i++) {
			float t = Math.abs(i - w / 2f) / (w / 2f);
			int a = Math.round(0x80 * (1f - t * t));
			g.fill(x0 + i, y, x0 + i + 1, y + 12, (a << 24) | 0x0C0505);
			int la = Math.round(0xC0 * (1f - t));
			g.fill(x0 + i, y + 12, x0 + i + 1, y + 13, (la << 24) | (FLASH_RED & 0xFFFFFF));
		}
		int from = (int) Math.floor(bearing - 55f);
		int to = (int) Math.ceil(bearing + 55f);
		for (int d = from; d <= to; d++) {
			if (Math.floorMod(d, 15) != 0) {
				continue;
			}
			float off = (d - bearing) * ppd;
			int x = Math.round(cx + off);
			if (x < x0 + 2 || x > x1 - 2) {
				continue;
			}
			float t = Math.abs(off) / (w / 2f);
			float fade = 1f - t * t;
			int nd = Math.floorMod(d, 360);
			if (nd % 45 == 0) {
				String label = POINTS[nd / 45];
				int c = nd == 0 ? RED : WHITE;
				g.drawString(font, label, x - font.width(label) / 2, y + 2, alpha(c, Math.max(0.15f, fade)), false);
				g.fill(x, y + 10, x + 1, y + 12, alpha(GOLD, fade));
			} else {
				g.fill(x, y + 8, x + 1, y + 12, alpha(DIM, fade * 0.9f));
			}
		}
		// the caret and the bearing in degrees under it
		g.fill(cx - 2, y + 13, cx + 3, y + 14, GOLD);
		g.fill(cx - 1, y + 14, cx + 2, y + 15, GOLD);
		g.fill(cx, y + 15, cx + 1, y + 16, GOLD);
		String deg = String.format(Locale.ROOT, "%03d", Math.round(bearing) % 360);
		int dw = font.width(deg);
		g.fill(x1 + 3, y + 1, x1 + 7 + dw, y + 11, PLATE);
		g.drawString(font, deg, x1 + 5, y + 2, 0xFF000000 | pal.light(), false);
	}

	/**
	 * The status chips (built-in, then {@link #addChipSource}'s). Kept to states the rest of the HUD does not already show:
	 * the mode itself is top-left, the timers top-right.
	 */
	public static List<Chip> statusChips(Minecraft mc, Player p, SpeedForceState s, ExperimentalState exp, long now) {
		List<Chip> out = new ArrayList<>();
		if (s != null) {
			Palette pal = Palette.of(SpeedForceColours.clamp(s.colour));
			if (SpeedForceSpeedster.powerLost(p) <= 0) {
				if (s.speedMode && cHeld >= SpeedForceConfig.HOLD_TICKS && s.chargeStart == 0L && !s.phasing
						&& speed > SpeedForceConfig.CHARGE_STILL_BPS) {
					out.add(new Chip(Component.translatable("hud.projecthero.flash_visor.momentum_lock"), WHITE));
				} else if (s.speedMode && s.momentum > 0.005f && speed > 15f && !p.isFallFlying()
						&& !com.projecthero.mod.client.speedforce.SpeedForceSprintKey.physicallyDown(mc)) {
					out.add(new Chip(Component.translatable("hud.projecthero.flash_visor.cruise"), pal.light()));
				}
				if (s.surgeUntil > now) {
					out.add(new Chip(Component.translatable("hud.projecthero.flash_visor.surge"), pal.accent()));
				}
				if (speed >= SpeedForceConfig.SONIC_BOOM_BPS) {
					out.add(new Chip(Component.translatable("hud.projecthero.flash_visor.sound_barrier"), RED));
				}
			}
		} else if (exp != null) {
			if (res(exp, SuperSpeedHandlers.OVERDRIVE_LEFT) > 0f && speed >= 40f) {
				out.add(new Chip(Component.translatable("hud.projecthero.flash_visor.sound_barrier"), RED));
			}
		}
		if (com.projecthero.mod.client.speed.SpeedsterGroundStick.enabled()) {
			out.add(new Chip(Component.translatable("hud.projecthero.flash_visor.ground_stick"), GOLD));
		}
		for (BiConsumer<Player, List<Chip>> src : CHIP_SOURCES) {
			src.accept(p, out);
		}
		return out;
	}

	private static void chips(GuiGraphics g, Font font, int cx, int y, int maxW, List<Chip> chips) {
		if (chips.isEmpty()) {
			return;
		}
		List<String> texts = new ArrayList<>();
		int total = 0;
		int shown = 0;
		for (Chip c : chips) {
			String t = fit(font, c.text().getString().toUpperCase(Locale.ROOT), 110);
			int w = font.width(t) + 8;
			if (total + w > maxW) {
				break;
			}
			texts.add(t);
			total += w + 3;
			shown++;
		}
		total -= 3;
		int x = cx - total / 2;
		for (int i = 0; i < shown; i++) {
			Chip c = chips.get(i);
			String t = texts.get(i);
			int w = font.width(t) + 8;
			g.fill(x, y, x + w, y + 11, PLATE);
			g.fill(x, y, x + 2, y + 11, c.colour());
			g.fill(x + 2, y + 10, x + w, y + 11, alpha(c.colour(), 0.45f));
			g.drawString(font, t, x + 5, y + 2, c.colour(), false);
			x += w + 3;
		}
	}

	// ---------------------------------------------------------------- top-right: timers

	/** The timer rows (built-in, then {@link #addTimerSource}'s). */
	public static List<Timer> timers(Player p, SpeedForceState s, ExperimentalState exp, long now) {
		List<Timer> out = new ArrayList<>();
		if (s != null) {
			Palette pal = Palette.of(SpeedForceColours.clamp(s.colour));
			int lost = SpeedForceSpeedster.powerLost(p);
			if (lost > 0) {
				out.add(new Timer(Component.translatable("hud.projecthero.flash_visor.power_lost"), lost / (20f * 60f * 20f),
						Component.literal(mmss(lost)), RED));
			} else {
				float max = SpeedForceSpeedster.meterMax(p);
				if (s.chargeStart != 0L) {
					float c = Math.min(1f, (now - s.chargeStart) / (float) SpeedForceConfig.CHARGE_TICKS);
					out.add(new Timer(Component.translatable("hud.projecthero.speed_force.charge"), c,
							c >= 1f ? Component.translatable("hud.projecthero.speed_force.ready") : pct(c), c >= 1f ? WHITE : pal.accent()));
				}
				if (s.flashLeft == SpeedForceState.Z_FLASH_TIME) {
					out.add(new Timer(Component.translatable("hud.projecthero.speed_force.flash_time"), s.meter / max,
							Component.translatable("hud.projecthero.speed_force.per_second",
									String.format(Locale.ROOT, "%.0f", SpeedForceConfig.FLASH_COST_PER_SECOND)), FLASH_BLUE));
				}
				if (s.flashLeft == SpeedForceState.Z_PORTAL) {
					float c = Math.min(1f, s.flashTotal / (float) SpeedForceConfig.PORTAL_TICKS);
					out.add(new Timer(Component.translatable("hud.projecthero.speed_force.portal"), c, pct(c), pal.light()));
				}
				if (s.remnantUntil > now) {
					out.add(new Timer(Component.translatable("hud.projecthero.speed_force.remnant"),
							(s.remnantUntil - now) / (float) SpeedForceConfig.REMNANT_TICKS, secs(s.remnantUntil - now), pal.accent()));
				}
				if (s.drained > 0) {
					out.add(new Timer(Component.translatable("hud.projecthero.speed_force.drained"), s.drained / (float) SpeedForceConfig.DRAINED_TICKS,
							secs(s.drained), RED));
				}
			}
		} else if (exp != null) {
			float od = res(exp, SuperSpeedHandlers.OVERDRIVE_LEFT);
			if (od > 0f) {
				out.add(new Timer(Component.translatable("hud.projecthero.flash.overdrive"), od / (30f * 20f), secs((long) od), RED));
			}
			float ts = res(exp, SuperSpeedTimeSlow.LEFT);
			if (ts > 0f) {
				out.add(new Timer(Component.translatable("hud.projecthero.flash.time_slow"), ts / (45f * 20f), secs((long) ts), FLASH_BLUE));
			} else if (res(exp, SuperSpeedHandlers.TS_CHARGING) > 0.5f) {
				float c = res(exp, SuperSpeedHandlers.TS_CHARGE);
				out.add(new Timer(Component.translatable("hud.projecthero.flash.charging"), c / SuperSpeedHandlers.TS_CHARGE_TICKS,
						secs((long) (SuperSpeedHandlers.TS_CHARGE_TICKS - c)), FLASH_BLUE));
			}
			float ex = res(exp, SuperSpeedHandlers.EXHAUST);
			if (ex > 0f) {
				out.add(new Timer(Component.translatable("hud.projecthero.flash.exhausted"), ex / (30f * 20f), secs((long) ex), DIM));
			}
		}
		for (BiConsumer<Player, List<Timer>> src : TIMER_SOURCES) {
			src.accept(p, out);
		}
		return out;
	}

	private static final int TIMER_W = 118;

	/** Right-aligned rows; a row that would reach the key column steps left of it ({@code narrowRight}). */
	private static void timersBlock(GuiGraphics g, Font font, int fullRight, int narrowRight, int colTop, int y, List<Timer> rows) {
		int w = TIMER_W;
		for (Timer t : rows) {
			int right = y + 15 > colTop - 8 ? narrowRight : fullRight;
			int x = right - w;
			g.fill(x - 3, y - 2, right + 2, y + 13, PLATE);
			g.fill(right + 1, y - 2, right + 2, y + 13, t.colour());
			String value = t.value().getString();
			int vw = font.width(value);
			String label = fit(font, t.label().getString().toUpperCase(Locale.ROOT), w - vw - 6);
			g.drawString(font, label, x, y, t.colour(), false);
			g.drawString(font, value, right - 2 - vw, y, WHITE, false);
			g.fill(x, y + 9, right - 2, y + 11, 0xC0000000 | (FLASH_RED_DEEP & 0xFFFFFF));
			g.fill(x, y + 9, x + Math.round((right - 2 - x) * Mth.clamp(t.frac(), 0f, 1f)), y + 11, t.colour());
			y += 17;
		}
	}

	/** Below the vanilla status-effect icons (top-right): beneficial at y 1, harmful at y 27. */
	private static int effectsBottom(Player p) {
		boolean good = false;
		boolean bad = false;
		for (MobEffectInstance e : p.getActiveEffects()) {
			if (!e.showIcon()) {
				continue;
			}
			if (e.getEffect().value().isBeneficial()) {
				good = true;
			} else {
				bad = true;
			}
		}
		return bad ? 58 : good ? 32 : 10;
	}

	// ---------------------------------------------------------------- left edge: the Speed Force meter

	private static void meterArc(GuiGraphics g, Font font, int sh, SpeedForceState s, Player p, Palette pal, long now) {
		float max = SpeedForceSpeedster.meterMax(p);
		float frac = Mth.clamp(s.meter / max, 0f, 1f);
		int topY = 44;
		int bottomY = Math.min(sh / 2 + 40, sh - 136); // clear of the chat
		int half = Math.min(54, (bottomY - topY) / 2);
		if (half < 18) {
			return;
		}
		int cy = topY + half;
		float r = 86f;
		float cx = 9f + r;
		float a = (float) Math.asin(half / r);
		int segs = Math.max(10, half * 2 / 4);
		boolean drained = s.drained > 0;
		boolean low = frac < 0.2f;
		boolean blink = low && (now / 6) % 2 == 0;
		for (int i = 0; i < segs; i++) {
			float t = (i + 0.5f) / segs; // 0 = bottom
			float ang = a - 2f * a * t;
			int x = Math.round(cx - r * Mth.cos(ang));
			int y = Math.round(cy + r * Mth.sin(ang));
			boolean lit = t <= frac;
			int c = !lit ? 0x90000000 | (pal.deep() & 0xFFFFFF)
					: drained ? DIM : blink ? RED : lerpColour(0xFF000000 | pal.accent(), 0xFF000000 | pal.light(), t);
			g.fill(x - 1, y - 1, x + 3, y + 2, c);
			// the inner guide
			int gx = Math.round(cx - (r - 6f) * Mth.cos(ang));
			if (i % 2 == 0) {
				g.fill(gx, y, gx + 1, y + 1, alpha(FLASH_RED, 0.55f));
			}
		}
		// end caps
		int capX = Math.round(cx - r * Mth.cos(a));
		g.fill(capX - 2, cy - half - 3, capX + 6, cy - half - 2, GOLD);
		g.fill(capX - 2, cy + half + 2, capX + 6, cy + half + 3, alpha(FLASH_RED, 0.9f));
		// the value inside the curve
		String v = String.format(Locale.ROOT, "%.0f", s.meter);
		int tx = 19;
		g.pose().pushPose();
		g.pose().translate(tx, cy - 9, 0);
		g.pose().scale(1.5f, 1.5f, 1f);
		g.drawString(font, v, 0, 0, drained ? DIM : blink ? RED : 0xFF000000 | pal.light(), true);
		g.pose().popPose();
		g.drawString(font, fit(font, Component.translatable("hud.projecthero.flash_visor.meter").getString(), 70), tx, cy + 5, DIM, false);
	}

	// ---------------------------------------------------------------- crosshair: brackets + target card

	private static void crosshair(GuiGraphics g, Font font, Minecraft mc, int cx, int cy, SpeedForceState s, Palette pal, long now) {
		boolean speedOn = s != null && s.speedMode && SpeedForceSpeedster.powerLost(mc.player) <= 0;
		if (speedOn) {
			int c = cHeld >= SpeedForceConfig.HOLD_TICKS && speed > SpeedForceConfig.CHARGE_STILL_BPS ? alpha(WHITE, 0.75f)
					: alpha(0xFF000000 | pal.accent(), 0.6f);
			for (int k = -3; k <= 3; k++) {
				float ang = k * 0.13f;
				int dx = Math.round(12f * Mth.cos(ang));
				int dy = Math.round(12f * Mth.sin(ang));
				g.fill(cx + dx, cy + dy, cx + dx + 1, cy + dy + 1, c);
				g.fill(cx - dx, cy + dy, cx - dx + 1, cy + dy + 1, c);
			}
		}
		if (targetId < 0 || mc.level == null) {
			return;
		}
		Entity e = mc.level.getEntity(targetId);
		if (!(e instanceof LivingEntity le) || !le.isAlive()) {
			return;
		}
		boolean hostile = e instanceof Enemy;
		int x = cx + 16;
		int y = cy + 10;
		int w = 90;
		String name = fit(font, e.getDisplayName().getString(), w - 4);
		float hp = le.getHealth();
		float maxHp = Math.max(1f, le.getMaxHealth());
		g.fill(x, y, x + w, y + 22, PLATE);
		g.fill(x, y, x + 1, y + 22, hostile ? RED : GOLD);
		line(g, x - 6, y - 4, x, y, alpha(hostile ? RED : GOLD, 0.7f));
		g.drawString(font, name, x + 4, y + 2, hostile ? 0xFFFF8070 : WHITE, false);
		g.fill(x + 4, y + 12, x + w - 4, y + 14, 0xC0000000 | (FLASH_RED_DEEP & 0xFFFFFF));
		g.fill(x + 4, y + 12, x + 4 + Math.round((w - 8) * Mth.clamp(hp / maxHp, 0f, 1f)), y + 14, hostile ? RED : 0xFF60E070);
		String hpText = String.format(Locale.ROOT, "%.0f/%.0f", hp, maxHp);
		String dist = Component.translatable("hud.projecthero.flash_visor.distance",
				String.format(Locale.ROOT, "%.0f", Math.sqrt(e.distanceToSqr(mc.player)))).getString();
		g.drawString(font, hpText, x + 4, y + 15, DIM, false);
		g.drawString(font, dist, x + w - 4 - font.width(dist), y + 15, DIM, false);
	}

	// ---------------------------------------------------------------- bottom-right: the speedometer

	private static void dial(GuiGraphics g, Font font, int cx, int cy, int r, SpeedForceState s, ExperimentalState exp, Palette pal,
			float topBps, boolean lost, long now) {
		// a soft round plate
		for (int dy = -r - 3; dy <= r + 3; dy++) {
			int half = (int) Math.sqrt(Math.max(0, (r + 3) * (r + 3) - dy * dy));
			float t = Math.abs(dy) / (float) (r + 3);
			int a = Math.round(0xA8 * (1f - t * t * 0.45f));
			g.fill(cx - half, cy + dy, cx + half, cy + dy + 1, (a << 24) | 0x0C0505);
		}
		float start = (float) Math.toRadians(135);
		float sweep = (float) Math.toRadians(270);
		float sFrac = Mth.clamp(speed / topBps, 0f, 1f);
		boolean sonic = s != null && speed >= SpeedForceConfig.SONIC_BOOM_BPS;
		int segs = 48;
		for (int i = 0; i < segs; i++) {
			float t = (i + 0.5f) / segs;
			float ang = start + sweep * t;
			int x = Math.round(cx + r * Mth.cos(ang));
			int y = Math.round(cy + r * Mth.sin(ang));
			int c = t <= sFrac ? (sonic ? lerpColour(GOLD, RED, t) : lerpColour(GOLD, FLASH_RED, t)) : 0xB0000000 | (FLASH_RED_DEEP & 0xFFFFFF);
			g.fill(x - 1, y - 1, x + 2, y + 2, c);
		}
		// marks: the sound barrier (Speed Force) or the Speed Mode / Overdrive tiers (plain), the run's top speed
		if (s != null) {
			tick(g, cx, cy, r, start + sweep * (float) (SpeedForceConfig.SONIC_BOOM_BPS / topBps), WHITE);
		} else {
			tick(g, cx, cy, r, start + sweep * (20f / topBps), WHITE);
			tick(g, cx, cy, r, start + sweep * (40f / topBps), WHITE);
		}
		if (top > 0.5f) {
			tick(g, cx, cy, r + 1, start + sweep * Mth.clamp(top / topBps, 0f, 1f), RED);
		}
		// the inner ring: the momentum (Speed Force) or Overdrive's time left (plain)
		float inner = -1f;
		int innerColour = 0xFF000000 | pal.accent();
		if (s != null && !lost) {
			inner = Mth.clamp(s.momentum, 0f, 1f);
			if (cHeld >= SpeedForceConfig.HOLD_TICKS && s.speedMode && speed > SpeedForceConfig.CHARGE_STILL_BPS) {
				innerColour = WHITE;
			}
		} else if (exp != null && res(exp, SuperSpeedHandlers.OVERDRIVE_LEFT) > 0f) {
			inner = Mth.clamp(res(exp, SuperSpeedHandlers.OVERDRIVE_LEFT) / (30f * 20f), 0f, 1f);
			innerColour = RED;
		}
		if (inner >= 0f) {
			int ri = r - 6;
			int isegs = 36;
			for (int i = 0; i < isegs; i++) {
				float t = (i + 0.5f) / isegs;
				float ang = start + sweep * t;
				int x = Math.round(cx + ri * Mth.cos(ang));
				int y = Math.round(cy + ri * Mth.sin(ang));
				int c = t <= inner ? (innerColour == WHITE || innerColour == RED ? innerColour
						: lerpColour(0xFF000000 | pal.accent(), 0xFF000000 | pal.light(), t)) : 0x90000000 | (pal.deep() & 0xFFFFFF);
				g.fill(x, y, x + 2, y + 2, c);
			}
			if (s != null) {
				for (float m : new float[] { 0.25f, 0.60f }) {
					float ang = start + sweep * m;
					int x = Math.round(cx + (ri - 3) * Mth.cos(ang));
					int y = Math.round(cy + (ri - 3) * Mth.sin(ang));
					g.fill(x, y, x + 1, y + 1, WHITE);
				}
			}
		}
		// the number, big, in the middle; b/s under it; the momentum in the gap at the bottom; mph below the dial
		String bps = String.format(Locale.ROOT, "%.0f", speed);
		int bw = font.width(bps) * 2;
		g.pose().pushPose();
		g.pose().translate(cx - bw / 2f + 1, cy - 11, 0);
		g.pose().scale(2f, 2f, 1f);
		g.drawString(font, bps, 0, 0, sonic ? RED : WHITE, true);
		g.pose().popPose();
		String unit = Component.translatable("hud.projecthero.flash_visor.bps").getString();
		g.drawString(font, unit, cx - font.width(unit) / 2 + 1, cy + 7, DIM, false);
		if (s != null && !lost) {
			String m = String.format(Locale.ROOT, "%.0f%%", s.momentum * 100f);
			g.drawString(font, m, cx - font.width(m) / 2 + 1, cy + r - 5, innerColour == WHITE ? WHITE : 0xFF000000 | pal.light(), true);
		}
		// over the dial: the run's top speed, then the speed in mph
		String mph = Component.translatable("hud.projecthero.flash_visor.mph", String.format(Locale.ROOT, "%.0f", speed * MPH_PER_BPS)).getString();
		g.drawString(font, mph, cx - font.width(mph) / 2 + 1, cy - r - 14, alpha(GOLD, 0.95f), true);
		String topLine = Component.translatable("hud.projecthero.flash_visor.top", String.format(Locale.ROOT, "%.0f", top)).getString();
		g.drawString(font, topLine, cx - font.width(topLine) / 2 + 1, cy - r - 24, DIM, true);
	}

	/** A short radial tick across the dial's ring at {@code ang}. */
	private static void tick(GuiGraphics g, int cx, int cy, int r, float ang, int colour) {
		for (int k = -3; k <= 2; k++) {
			int x = Math.round(cx + (r + k) * Mth.cos(ang));
			int y = Math.round(cy + (r + k) * Mth.sin(ang));
			g.fill(x, y, x + 1, y + 1, colour);
		}
	}

	// ---------------------------------------------------------------- helpers

	private static int bossBars(Minecraft mc) {
		try {
			return ((com.projecthero.mod.client.mixin.BossHealthOverlayAccessor) mc.gui.getBossOverlay()).projecthero$events().size();
		} catch (Throwable t) {
			return 0;
		}
	}

	private static float res(ExperimentalState s, String name) {
		return s == null ? 0f : s.resources.getOrDefault(PLAIN_KEY + "/" + name, 0f);
	}

	private static String fit(Font font, String text, int maxW) {
		if (font.width(text) <= maxW) {
			return text;
		}
		return font.plainSubstrByWidth(text, Math.max(0, maxW - font.width("..."))) + "...";
	}

	private static Component pct(float f) {
		return Component.literal(String.format(Locale.ROOT, "%.0f%%", f * 100f));
	}

	private static Component secs(long ticks) {
		return Component.literal(String.format(Locale.ROOT, "%ds", (Math.max(0, ticks) + 19) / 20));
	}

	private static String mmss(long ticks) {
		long sec = (Math.max(0, ticks) + 19) / 20;
		return String.format(Locale.ROOT, "%d:%02d", sec / 60, sec % 60);
	}

	private static int alpha(int argb, float a) {
		return (Math.round(Mth.clamp(a, 0f, 1f) * ((argb >>> 24) & 0xFF)) << 24) | (argb & 0xFFFFFF);
	}

	private static void hLine(GuiGraphics g, int x0, int x1, int y, int c) {
		g.fill(Math.min(x0, x1), y, Math.max(x0, x1) + 1, y + 1, c);
	}

	private static void vLine(GuiGraphics g, int x, int y0, int y1, int c) {
		g.fill(x, Math.min(y0, y1), x + 1, Math.max(y0, y1) + 1, c);
	}

	/** A 1 px line, stepped. */
	private static void line(GuiGraphics g, int x0, int y0, int x1, int y1, int c) {
		int n = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
		if (n == 0) {
			g.fill(x0, y0, x0 + 1, y0 + 1, c);
			return;
		}
		for (int i = 0; i <= n; i++) {
			int x = x0 + Math.round((x1 - x0) * i / (float) n);
			int y = y0 + Math.round((y1 - y0) * i / (float) n);
			g.fill(x, y, x + 1, y + 1, c);
		}
	}

	private static int lerpColour(int a, int b, float t) {
		int r = (int) Mth.lerp(t, (a >> 16) & 0xFF, (b >> 16) & 0xFF);
		int gr = (int) Mth.lerp(t, (a >> 8) & 0xFF, (b >> 8) & 0xFF);
		int bl = (int) Mth.lerp(t, a & 0xFF, b & 0xFF);
		return 0xFF000000 | (r << 16) | (gr << 8) | bl;
	}
}
