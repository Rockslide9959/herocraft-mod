package com.projecthero.mod.client.speedforce;

import com.projecthero.mod.speedforce.SpeedForceConfig;
import com.projecthero.mod.speedforce.SpeedForceSpeedster;
import com.projecthero.mod.speedforce.SpeedForceWorks;
import com.projecthero.mod.speedforce.network.SpeedBuildPayload;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.BlockItem;

/**
 * v0.17.1 (user, Speed Build): the client half. Right-click held with blocks in the off hand, running in Speed Mode -- the
 * server is told ({@link SpeedBuildPayload}: a bridge, or sneaking a wall) and lays the blocks; meanwhile this client holds
 * the runner up at the bridge's level ({@link #floorY}, read by {@code SpeedForceLocalPlayerMixin}) so they don't drop
 * before the blocks arrive.
 */
public final class SpeedForceBuildClient {
	private static int mode;
	private static int sentMode = -1;
	private static int sinceSent;
	private static int floorY = Integer.MIN_VALUE;

	private SpeedForceBuildClient() {
	}

	static void initialize() {
		ClientTickEvents.END_CLIENT_TICK.register(SpeedForceBuildClient::tick);
		SpeedForceWorks.clientBuilding = p -> mode != SpeedBuildPayload.NONE && fast(p);
	}

	private static boolean fast(net.minecraft.world.entity.player.Player p) {
		double dx = p.getX() - p.xo;
		double dz = p.getZ() - p.zo;
		return Math.sqrt(dx * dx + dz * dz) * 20.0 >= SpeedForceConfig.BUILD_MIN_BPS;
	}

	private static void tick(Minecraft mc) {
		LocalPlayer p = mc.player;
		int want = SpeedBuildPayload.NONE;
		if (p != null && mc.screen == null && SpeedForceSpeedster.speedActive(p) && mc.options.keyUse.isDown()
				&& p.getOffhandItem().getItem() instanceof BlockItem && !p.getOffhandItem().isEmpty()) {
			want = p.isShiftKeyDown() ? SpeedBuildPayload.WALL : SpeedBuildPayload.BRIDGE;
		}
		if (want != mode) {
			if (want == SpeedBuildPayload.BRIDGE && p != null) {
				floorY = Mth.floor(p.getY() + 0.01);
			}
			mode = want;
		}
		if (p == null || mc.getConnection() == null) {
			sentMode = -1;
			return;
		}
		sinceSent++;
		if ((sentMode != mode || (mode != SpeedBuildPayload.NONE && sinceSent >= 20)) && ClientPlayNetworking.canSend(SpeedBuildPayload.TYPE)) {
			ClientPlayNetworking.send(new SpeedBuildPayload(mode));
			sentMode = mode;
			sinceSent = 0;
		}
	}

	/** The level the bridge holds the runner at (their feet), or {@link Integer#MIN_VALUE} when not bridging fast enough. */
	public static int floorY(LocalPlayer p) {
		if (mode != SpeedBuildPayload.BRIDGE) {
			return Integer.MIN_VALUE;
		}
		return fast(p) ? floorY : Integer.MIN_VALUE;
	}
}
