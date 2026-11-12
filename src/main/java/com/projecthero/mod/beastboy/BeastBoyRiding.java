package com.projecthero.mod.beastboy;

import org.jetbrains.annotations.Nullable;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.3 Beast Boy: carrying players. Right-click Beast Boy as a <b>whale</b> (up to 4 riders, in a row along the back) or a
 * <b>pterodactyl</b> (one, astride) to climb on; Sneak gets off. Riders are thrown off when he changes into a body with no seats
 * ({@code BeastBoy.setForm}). The seats come from the synced state, so both sides agree ({@code BeastBoyRideMixin}).
 */
public final class BeastBoyRiding {
	private BeastBoyRiding() {
	}

	/** {@code UseEntityCallback}: climb onto him. */
	public static InteractionResult interact(Player clicker, Entity target) {
		if (!(target instanceof Player host) || clicker == host || clicker.isShiftKeyDown() || clicker.isPassenger()) {
			return InteractionResult.PASS;
		}
		BeastForm f = BeastBoy.form(host);
		int seats = BeastBoyForms.seats(f);
		if (seats == 0) {
			return InteractionResult.PASS;
		}
		if (clicker.level().isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		if (host.getPassengers().size() >= seats || host.isPassenger()) {
			return InteractionResult.FAIL;
		}
		if (clicker instanceof ServerPlayer sp && sp.startRiding(host, true)) {
			return InteractionResult.SUCCESS;
		}
		return InteractionResult.FAIL;
	}

	/** Where {@code passenger} sits on {@code vehicle} (relative to its feet, turned with its body), or null for vanilla's seat. */
	@Nullable
	public static Vec3 seat(Entity vehicle, Entity passenger) {
		if (!(vehicle instanceof Player host)) {
			return null;
		}
		BeastForm f = BeastBoy.form(host);
		if (BeastBoyForms.seats(f) == 0) {
			return null;
		}
		float yaw = host.yBodyRot * Mth.DEG_TO_RAD;
		int i = Math.max(0, host.getPassengers().indexOf(passenger));
		Vec3 local;
		if (f == BeastForm.WHALE) {
			local = new Vec3(0.0, f.height * 0.86, 0.9 - i * 0.8);
		} else {
			local = new Vec3(0.0, f.height * 0.75, -0.1);
		}
		return local.yRot(-yaw);
	}
}
