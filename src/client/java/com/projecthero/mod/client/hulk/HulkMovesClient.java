package com.projecthero.mod.client.hulk;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hulk.HulkLeap;
import com.projecthero.mod.hulk.data.HulkState;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.4 (user): the client half of the Hulk's rebuilt kit.
 * <ul>
 *   <li><b>Super Leap, no drag:</b> while the server has him {@link HulkState#leaping}, every tick after vanilla's air step
 *       his horizontal speed is put back to the launch value and the vertical drag taken off ({@link HulkLeap#cancelDrag})
 *       -- only gravity bends the arc, he keeps his speed until he lands. It lets go the moment he hits a wall, water,
 *       starts flying or lands.</li>
 *   <li><b>The leap bar:</b> a Gauge bar over the XP bar, just above the hotbar, while X is held: how far the leap will go
 *       (25 / 100 / 500 blocks), with a tick at half charge.</li>
 * </ul>
 */
public final class HulkMovesClient {
	private static final int COLOR_BG = 0xA0000000;
	private static final int COLOR_BORDER = 0xFF1E3A1E;
	private static final int COLOR_FILL = 0xFF7CFF4A;
	private static final int COLOR_FULL = 0xFFE8FFD8;

	/** The leap stopped holding its speed (hit a wall...) -- until the server says it is over. */
	private static boolean released;

	private HulkMovesClient() {
	}

	public static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(HulkMovesClient::tick);
	}

	private static void tick(Minecraft mc) {
		LocalPlayer player = mc.player;
		if (player == null || mc.isPaused()) {
			return;
		}
		HulkState s = player.getAttachedOrElse(ModAttachments.HULK_STATE, null);
		if (s == null || !s.hulk || !s.leaping || (s.moves.leapVx == 0.0 && s.moves.leapVz == 0.0)) {
			released = false;
			return;
		}
		if (released) {
			return;
		}
		if (player.horizontalCollision || player.isInWater() || player.isInLava() || player.getAbilities().flying
				|| player.isPassenger() || player.isFallFlying()) {
			released = true;
			return;
		}
		if (player.onGround()) {
			return; // the first tick (not off the ground yet) or the landing -- the server ends the leap
		}
		Vec3 v = HulkLeap.cancelDrag(player.getDeltaMovement(), s.moves);
		player.setDeltaMovement(v);
	}

	/**
	 * v0.16.4 (user): "change the hulks leap charge thing to be above his ability hud not the regular hud" -- drawn by
	 * {@code HulkHud} right above his ability boxes, {@code w} wide from {@code x0}, its bottom at {@code bottom}.
	 */
	public static void renderLeapBar(GuiGraphics g, int x0, int bottom, int w) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null || mc.options.hideGui) {
			return;
		}
		float charge = HulkLeap.charge(player);
		HulkState s = player.getAttachedOrElse(ModAttachments.HULK_STATE, null);
		if (s == null || !s.hulk || s.leapChargeStart <= 0L) {
			return;
		}
		int y = bottom - 4;
		boolean full = charge >= 1.0f;
		g.fill(x0 - 1, y - 1, x0 + w + 1, y + 4, COLOR_BORDER);
		g.fill(x0, y, x0 + w, y + 3, COLOR_BG);
		g.fill(x0, y, x0 + Math.round(w * charge), y + 3, full ? COLOR_FULL : COLOR_FILL);
		int mid = x0 + w / 2;
		g.fill(mid, y - 1, mid + 1, y + 4, 0xC0FFFFFF);
		int blocks = (int) Math.round(HulkLeap.blocksFor(charge));
		Component label = Component.translatable("hud.projecthero.hulk.leap", blocks);
		int lw = mc.font.width(label);
		g.drawString(mc.font, label, x0 + (w - lw) / 2, y - 10, full ? COLOR_FULL : COLOR_FILL, true);
	}
}
