package com.projecthero.mod.client.hulk;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hulk.HulkLeap;
import com.projecthero.mod.hulk.data.HulkState;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import net.minecraft.client.Minecraft;
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
 *   <li><b>The leap bar:</b> a Gauge meter row above his key strip (v0.17.3: in HulkHud's vanilla column) while X is held:
 *       how far the leap will go (25 / 100 / 500 blocks).</li>
 * </ul>
 */
public final class HulkMovesClient {
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
	 * v0.16.4 (user): "change the hulks leap charge thing to be above his ability hud not the regular hud" -- v0.17.3: a Gauge
	 * meter row in {@code HulkHud}'s vanilla column, right above his key strip: how far the leap will go (25 / 100 / 500
	 * blocks), or -- v0.16.4 (user): "make the hulk have to charge up his throw by holding v" -- while V winds up a throw, how far
	 * the throw will go (20 up to 200 blocks). Draws nothing while neither is charging.
	 */
	public static void leapRow(com.projecthero.mod.client.gui.VanillaHud.Stack st) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null || mc.options.hideGui) {
			return;
		}
		HulkState s = player.getAttachedOrElse(ModAttachments.HULK_STATE, null);
		if (s == null || !s.hulk) {
			return;
		}
		float charge;
		int blocks;
		String labelKey;
		if (s.leapChargeStart <= 0L && com.projecthero.mod.hulk.HulkGrab.chargingThrow(player)) {
			charge = com.projecthero.mod.hulk.HulkGrab.throwCharge(player);
			blocks = (int) Math.round(com.projecthero.mod.hulk.HulkGrab.throwBlocksFor(charge));
			labelKey = "hud.projecthero.hulk.throw_label";
		} else if (s.leapChargeStart > 0L) {
			charge = HulkLeap.charge(player);
			blocks = (int) Math.round(HulkLeap.blocksFor(charge));
			labelKey = "hud.projecthero.hulk.leap_label";
		} else {
			return;
		}
		boolean full = charge >= 1.0f;
		// the Gauge style (the boss bar's ten notches; its middle notch is the old half-charge tick)
		st.meter(Component.translatable(labelKey), charge, COLOR_FILL, String.valueOf(blocks), full ? COLOR_FULL : COLOR_FILL,
				com.projecthero.mod.hero.visual.MutationMeters.Style.GAUGE);
	}
}
