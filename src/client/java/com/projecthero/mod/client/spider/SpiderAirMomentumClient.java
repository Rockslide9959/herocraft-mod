package com.projecthero.mod.client.spider;

import com.projecthero.mod.spider.SpiderAirControl;
import com.projecthero.mod.spider.SpiderClimb;
import com.projecthero.mod.spider.SpiderMan;
import com.projecthero.mod.spider.SpiderManConfig;
import com.projecthero.mod.spider.SpiderMoves;
import com.projecthero.mod.spider.SpiderSwing;
import com.projecthero.mod.spider.SpiderSwingInput;
import com.projecthero.mod.spider.data.SpiderMoveState;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.4 (user): Spider-Man's air momentum and aerial control on the owning client ({@link SpiderAirControl} has the
 * maths). Armed by a swing let go in the air ({@code SpiderSwingClient}), a Zip Boost ({@code SpiderInputClient}) or the
 * end of a Web Wings glide; every tick after vanilla's air step it puts the horizontal drag back, takes vanilla's own
 * weak air push back out, and steers by WASD. Disarmed the moment he lands, touches a wall, swims, grabs a wall, hangs,
 * glides, rides, flies or fires a line (the swing takes over; letting go arms it again).
 */
public final class SpiderAirMomentumClient {
	private static boolean armed;

	private SpiderAirMomentumClient() {
	}

	/** Start carrying his momentum (no-op when the config turns it off). */
	public static void arm() {
		armed = SpiderManConfig.moves().airMomentum;
	}

	/** Diagnostics / tests: is the momentum on? */
	public static boolean active() {
		return armed;
	}

	public static void reset() {
		armed = false;
	}

	private static boolean breaks(LocalPlayer p) {
		return !SpiderMan.hasPower(p) || p.onGround() || p.horizontalCollision || p.isInWater() || p.isInLava()
				|| p.isFallFlying() || p.getAbilities().flying || p.isPassenger() || p.onClimbable() || p.isSpectator()
				|| SpiderSwing.isSwinging(p) || SpiderClimb.attached(p) || SpiderWallRunClient.active()
				// the local glide only: the server's synced flag lags a tick or two behind letting go of Jump
				|| SpiderWebWingsClient.localGliding()
				|| SpiderMoves.state(p).has(SpiderMoveState.FLAG_HANGING);
	}

	/** End of every client tick (after vanilla has moved him and applied its drag). */
	public static void clientTick(Minecraft mc) {
		LocalPlayer p = mc.player;
		if (p == null) {
			armed = false;
			return;
		}
		if (!armed || mc.isPaused()) {
			return;
		}
		if (breaks(p)) {
			armed = false;
			return;
		}
		// the velocity he moved with (drag put back), minus vanilla's own little air push
		Vec3 moved = SpiderAirControl.cancelDrag(p.getDeltaMovement());
		Vec3 vanillaPush = SpiderAirControl.vanillaAirInput(p.xxa, p.zza, p.getYRot(), p.isSprinting());
		moved = moved.subtract(vanillaPush.x, 0.0, vanillaPush.z);
		int keys = SpiderSwingInput.pack(p.input.up, p.input.down, p.input.left, p.input.right, p.input.jumping,
				p.input.shiftKeyDown);
		p.setDeltaMovement(SpiderAirControl.steer(moved, p.getYRot(), keys));
	}
}
