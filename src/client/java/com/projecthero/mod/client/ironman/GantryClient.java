package com.projecthero.mod.client.ironman;

import com.projecthero.mod.ironman.TonyStark;
import com.projecthero.mod.ironman.gantry.GantryTimeline;
import com.projecthero.mod.ironman.gantry.StarkGantry;
import com.projecthero.mod.ironman.gantry.StarkGantryFloorBlockEntity;
import com.projecthero.mod.network.StarkGantryActionPayload;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/**
 * v0.15.4: the wearer's side of a Stark Gantry sequence. While the centre tile under them runs a sequence naming them:
 * <ul>
 *   <li><b>facing lock</b> -- mouse turning is swallowed ({@code mixin.EntityTurnLockMixin}) and body / head are held on
 *       the gantry's synced heading (the server re-asserts it too), so the view never fights the server;</li>
 *   <li><b>the lift</b> -- the client owns its player's movement, so it is the client that rides the lift: every tick
 *       the player is set onto the lift pad's height for the next frame (gravity off locally, never synced), which the
 *       renderer interpolates smoothly; the server only corrects real drift;</li>
 *   <li>v0.15.5, user request: <b>the camera</b> swings round to the front third-person view for the whole sequence so
 *       they watch the suit go on (or come off), and back to whatever view they had when it ends; the view no longer
 *       zooms in while the gantry holds them still ({@link #holdFov}).</li>
 * </ul>
 * Also the H key: Tony Stark standing on gantry floor asks the server for the gantry menu ({@link #wantsH} / {@link #pressH}).
 */
public final class GantryClient {
	private static boolean locked;
	private static float yaw;
	private static boolean ownNoGravity;
	/** The view the player had before the sequence moved the camera (null while it has not). */
	private static CameraType cameraBefore;

	private GantryClient() {
	}

	/** True while the local player's heading is held by a gantry sequence. */
	public static boolean locked() {
		return locked;
	}

	/**
	 * The gantry pins the wearer with a zero movement speed, and vanilla derives the field of view from movement speed --
	 * so without this the view zoomed in for the whole sequence. True while the FOV should be held neutral.
	 */
	public static boolean holdFov() {
		return locked;
	}

	public static void tick(Minecraft mc) {
		LocalPlayer p = mc.player;
		locked = false;
		if (p == null || mc.level == null) {
			ownNoGravity = false;
			cameraBefore = null;
			return;
		}
		StarkGantryFloorBlockEntity be = find(mc, p);
		if (be == null) {
			if (ownNoGravity) {
				p.setNoGravity(false);
				ownNoGravity = false;
			}
			if (cameraBefore != null) {
				// the sequence is over: back to the view they had (unless they changed it themselves meanwhile)
				if (mc.options.getCameraType() == CameraType.THIRD_PERSON_FRONT) {
					mc.options.setCameraType(cameraBefore);
				}
				cameraBefore = null;
			}
			return;
		}
		locked = true;
		yaw = be.yaw();
		apply(p);
		if (cameraBefore == null) {
			cameraBefore = mc.options.getCameraType();
			mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
		}
		// ride the lift: where the pad will be next tick (the renderer lerps from here to there)
		float f = be.frameAt(1f);
		Vec3 at = be.standAt().add(0, GantryTimeline.lift(f, be.plan()), 0);
		if (p.position().distanceToSqr(at) < 4.0) {
			p.setPos(at.x, at.y, at.z);
			p.setDeltaMovement(Vec3.ZERO);
			p.fallDistance = 0f;
			if (!p.isNoGravity()) {
				p.setNoGravity(true);
				ownNoGravity = true;
			}
		}
	}

	/** Snap the local player's body and head onto the locked heading. */
	public static void apply(LocalPlayer p) {
		p.setYRot(yaw);
		p.yRotO = yaw;
		p.setYHeadRot(yaw);
		p.yHeadRotO = yaw;
		p.setYBodyRot(yaw);
		p.yBodyRotO = yaw;
	}

	private static StarkGantryFloorBlockEntity find(Minecraft mc, LocalPlayer p) {
		BlockPos c = p.blockPosition();
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		for (int dy = -2; dy <= 0; dy++) {
			for (int dx = -2; dx <= 2; dx++) {
				for (int dz = -2; dz <= 2; dz++) {
					m.set(c.getX() + dx, c.getY() + dy, c.getZ() + dz);
					if (mc.level.getBlockEntity(m) instanceof StarkGantryFloorBlockEntity be && be.running()
							&& be.playerEntity() == p.getId()) {
						return be;
					}
				}
			}
		}
		return null;
	}

	/** H belongs to the gantry: Tony Stark on gantry floor (or held by a running sequence, when H does nothing). */
	public static boolean wantsH(LocalPlayer p) {
		return locked || TonyStark.hasPower(p) && StarkGantry.tileUnder(p) != null;
	}

	public static void pressH() {
		if (!locked) {
			ClientPlayNetworking.send(new StarkGantryActionPayload(StarkGantryActionPayload.OPEN, BlockPos.ZERO));
		}
	}
}
