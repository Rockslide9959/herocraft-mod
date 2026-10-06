package com.projecthero.mod.client.ironman;

import com.projecthero.mod.ironman.fabricator.IronManSuitPlatformBlockEntity;
import com.projecthero.mod.ironman.suit.IronManSuitFx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;

/**
 * v0.15.3, explicit user request: while a Suit Platform's robotic arms put the suit on (deploy) or take it off
 * (retrieve), the wearer stands with their back to the platform and can't turn. The server re-asserts the heading every
 * tick ({@code IronManSuitPlatformBlockEntity#lockFacing}); this keeps the wearer's own client on it too -- mouse look is
 * swallowed ({@code mixin.EntityTurnLockMixin}) and body / head are held at the platform's synced {@code seqYaw} -- so the
 * view never jitters between the two. Found by scanning the few blocks round the player for the platform whose running
 * sequence names them, and only while their synced pose says a platform sequence is on.
 */
public final class PlatformFacingLock {
	private static final int SCAN = (int) Math.ceil(IronManSuitPlatformBlockEntity.SEQ_RANGE) + 1;
	private static boolean locked;
	private static float yaw;

	private PlatformFacingLock() {
	}

	/** True while the local player's heading is held by a platform sequence. */
	public static boolean locked() {
		return locked;
	}

	public static void tick(Minecraft mc) {
		LocalPlayer p = mc.player;
		locked = false;
		if (p == null || mc.level == null) {
			return;
		}
		int kind = IronManSuitFx.of(p).poseKind();
		if (kind != IronManSuitFx.POSE_PLATFORM && kind != IronManSuitFx.POSE_PLATFORM_OFF) {
			return;
		}
		IronManSuitPlatformBlockEntity be = find(mc, p);
		if (be == null) {
			return;
		}
		locked = true;
		yaw = be.seqYaw();
		apply(p);
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

	private static IronManSuitPlatformBlockEntity find(Minecraft mc, LocalPlayer p) {
		BlockPos c = p.blockPosition();
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		for (int dy = -2; dy <= 1; dy++) {
			for (int dx = -SCAN; dx <= SCAN; dx++) {
				for (int dz = -SCAN; dz <= SCAN; dz++) {
					m.set(c.getX() + dx, c.getY() + dy, c.getZ() + dz);
					if (mc.level.getBlockEntity(m) instanceof IronManSuitPlatformBlockEntity be
							&& be.sequenceRunning() && be.seqPlayerEntity() == p.getId()) {
						return be;
					}
				}
			}
		}
		return null;
	}
}
