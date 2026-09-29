package com.projecthero.mod.client.moonknight;

import com.projecthero.mod.client.ModKeyBindings;
import com.projecthero.mod.moonknight.MoonKnight;
import com.projecthero.mod.moonknight.MoonKnightAlter;
import com.projecthero.mod.moonknight.MoonKnightConfig;
import com.projecthero.mod.network.MoonKnightActionPayload;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/**
 * Moon Knight Phase 5: the radial alter picker (HOLD C).
 *
 * <p><b>Controls</b> -- the mouse never leaves the game:
 * <ol>
 *   <li>hold C (Ability 5) for a moment ({@link MoonKnightConfig#ALTER_PICKER_OPEN_TICKS} ticks) while suited and not
 *       sneaking: the picker opens around the crosshair and the camera freezes;</li>
 *   <li>move the mouse toward an alter -- Marc (top), Steven (lower right), Jake (lower left). The mouse steers a small
 *       cursor from the centre (see {@code MoonKnightPickerMouseMixin}); the segment it points at lights up. Or roll
 *       the scroll wheel to step through them;</li>
 *   <li>let go of C to confirm. Letting go with the cursor still in the middle keeps the current alter.</li>
 * </ol>
 * The choice goes to the server as {@code MoonKnightActionPayload(SELECT_ALTER, ordinal)}; the server re-checks the
 * suit, the Fracture and the 2 s switch cooldown. Sneak+C is never the picker (it is the alter's special).
 */
public final class MoonKnightAlterPicker {
	/** Screen-space directions of the three segments (degrees, 0 = right, 90 = down): Marc, Steven, Jake. */
	private static final double[] SEGMENT_DEG = { -90.0, 30.0, 150.0 };
	private static final double CURSOR_MAX = 60.0;
	private static final double DEAD_ZONE = 12.0;
	private static final double MOUSE_SCALE = 0.35;
	private static final int RING = 64;
	private static final int BOX_W = 104;
	private static final int BOX_H = 28;

	private static boolean wasDown;
	private static boolean sneakPress;
	private static int heldTicks;
	private static boolean open;
	private static int openTicks;
	private static double cursorX;
	private static double cursorY;
	/** Alter ordinal highlighted, or -1 for "no change". */
	private static int highlighted = -1;

	private MoonKnightAlterPicker() {
	}

	public static boolean isOpen() {
		return open;
	}

	/** END_CLIENT_TICK. */
	public static void tick(Minecraft mc) {
		Player player = mc.player;
		boolean usable = player != null && mc.screen == null && MoonKnight.isTransformed(player);
		boolean down = usable && ModKeyBindings.ABILITY_SLOTS[5].isDown();
		if (!usable) {
			reset();
			wasDown = false;
			return;
		}
		if (down && !wasDown) {
			sneakPress = player.isShiftKeyDown();
			heldTicks = 0;
		}
		if (down) {
			heldTicks++;
			if (!open && !sneakPress && heldTicks >= MoonKnightConfig.ALTER_PICKER_OPEN_TICKS && !MoonKnight.fractured(player)) {
				open = true;
				openTicks = 0;
				cursorX = 0.0;
				cursorY = 0.0;
				highlighted = -1;
				player.playSound(net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME, 0.5f, 1.4f);
			}
			if (open) {
				openTicks++;
			}
		} else if (wasDown && open) {
			// released: confirm
			MoonKnightAlter current = MoonKnight.alter(player);
			if (highlighted >= 0 && highlighted != current.ordinal()) {
				ClientPlayNetworking.send(new MoonKnightActionPayload(MoonKnightActionPayload.Action.SELECT_ALTER, highlighted));
			}
			reset();
		} else if (!down) {
			reset();
		}
		wasDown = down;
	}

	private static void reset() {
		open = false;
		heldTicks = 0;
		highlighted = -1;
		cursorX = 0.0;
		cursorY = 0.0;
	}

	/** Raw mouse movement while open (from the MouseHandler mixin); the camera does not turn. */
	public static void onMouseMoved(double dx, double dy) {
		cursorX += dx * MOUSE_SCALE;
		cursorY += dy * MOUSE_SCALE;
		double len = Math.hypot(cursorX, cursorY);
		if (len > CURSOR_MAX) {
			cursorX *= CURSOR_MAX / len;
			cursorY *= CURSOR_MAX / len;
			len = CURSOR_MAX;
		}
		if (len < DEAD_ZONE) {
			highlighted = -1;
			return;
		}
		double deg = Math.toDegrees(Math.atan2(cursorY, cursorX));
		int best = 0;
		double bestDiff = Double.MAX_VALUE;
		for (int i = 0; i < SEGMENT_DEG.length; i++) {
			double diff = Math.abs(((deg - SEGMENT_DEG[i]) % 360.0 + 540.0) % 360.0 - 180.0);
			if (diff < bestDiff) {
				bestDiff = diff;
				best = i;
			}
		}
		highlighted = best;
	}

	/** Scroll wheel while open: step to the next / previous alter and park the cursor on it. */
	public static void onScroll(double amount) {
		Player player = Minecraft.getInstance().player;
		int from = highlighted >= 0 ? highlighted : (player == null ? 0 : MoonKnight.alter(player).ordinal());
		int n = MoonKnightAlter.values().length;
		highlighted = Math.floorMod(from + (amount < 0 ? 1 : -1), n);
		double a = Math.toRadians(SEGMENT_DEG[highlighted]);
		cursorX = Math.cos(a) * CURSOR_MAX * 0.7;
		cursorY = Math.sin(a) * CURSOR_MAX * 0.7;
	}

	/** HudRenderCallback: the three segments around the crosshair. */
	public static void render(GuiGraphics g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		if (!open || mc.player == null || mc.options.hideGui) {
			return;
		}
		int cx = g.guiWidth() / 2;
		int cy = g.guiHeight() / 2;
		float fade = Math.min(1.0f, (openTicks + delta.getGameTimeDeltaPartialTick(false)) / 3.0f);
		int a = Math.max(40, Math.round(fade * 255.0f)); // never 0: the font treats alpha 0 as opaque
		MoonKnightAlter current = MoonKnight.alter(mc.player);

		// a faint ring of moonlight
		for (int deg = 0; deg < 360; deg += 4) {
			double r = Math.toRadians(deg);
			int x = cx + (int) Math.round(Math.cos(r) * (RING - 22));
			int y = cy + (int) Math.round(Math.sin(r) * (RING - 22));
			g.fill(x, y, x + 1, y + 1, argb(a / 3, 0xDDE6FF));
		}
		// the steering cursor
		int kx = cx + (int) Math.round(cursorX * 0.6);
		int ky = cy + (int) Math.round(cursorY * 0.6);
		g.fill(kx - 1, ky - 1, kx + 2, ky + 2, argb(a, 0xFFFFFF));

		for (MoonKnightAlter alter : MoonKnightAlter.values()) {
			int i = alter.ordinal();
			double r = Math.toRadians(SEGMENT_DEG[i]);
			int bx = cx + (int) Math.round(Math.cos(r) * RING) - BOX_W / 2;
			int by = cy + (int) Math.round(Math.sin(r) * RING) - BOX_H / 2;
			boolean lit = highlighted == i;
			boolean isCurrent = alter == current;
			g.fill(bx, by, bx + BOX_W, by + BOX_H, argb(Math.round(a * (lit ? 0.85f : 0.6f)), lit ? 0x2A2C38 : 0x101014));
			g.renderOutline(bx, by, BOX_W, BOX_H, argb(a, lit ? 0xF2EEE0 : (isCurrent ? 0x8A8A9A : 0x3A3A44)));
			Component name = Component.translatable(alter.nameKey()).withStyle(alter.colour());
			if (isCurrent) {
				name = Component.translatable("hud.projecthero.moon_knight.picker_current", name);
			}
			g.drawCenteredString(mc.font, name, bx + BOX_W / 2, by + 5, argb(a, lit ? 0xFFFFFF : 0xC8C4B8));
			Component special = Component.translatable("projecthero.moon_knight.alter_special." + alter.id());
			g.drawCenteredString(mc.font, special, bx + BOX_W / 2, by + 16, argb(a, lit ? 0xB8C8FF : 0x8A8A9A));
		}
		Component hint = Component.translatable("hud.projecthero.moon_knight.picker_hint");
		g.drawCenteredString(mc.font, hint, cx, cy + RING + BOX_H / 2 + 8, argb(a, 0xA0A0A8));
	}

	private static int argb(int alpha, int rgb) {
		return (Math.max(0, Math.min(255, alpha)) << 24) | (rgb & 0xFFFFFF);
	}
}
