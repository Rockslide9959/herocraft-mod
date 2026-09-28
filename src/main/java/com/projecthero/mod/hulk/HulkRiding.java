package com.projecthero.mod.hulk;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.phys.Vec3;

/**
 * v0.13.14: a squad-mate can right-click the Hulk to climb onto his back -- one rider at a time, sneak to hop off.
 * The rider is thrown off when he changes back, dies, logs out or starts to rampage ({@code Hulk#ejectRider}). The
 * seat is on his back rather than on top of his head ({@code mixin/EntityHulkRideMixin}).
 */
public final class HulkRiding {
	private HulkRiding() {
	}

	/** Returns true if {@code rider} climbed on. */
	public static boolean tryMount(ServerPlayer rider, ServerPlayer hulk) {
		if (rider == hulk || !Hulk.isHulk(hulk) || rider.isPassenger() || rider.isSpectator() || Hulk.isHulk(rider)) {
			return false;
		}
		if (hulk.getServer() == null
				|| !com.projecthero.mod.squad.SquadManager.get(hulk.getServer()).sameSquad(hulk.getUUID(), rider.getUUID())) {
			return false;
		}
		if (HulkControl.rampaging(hulk)) {
			rider.displayClientMessage(Component.translatable("message.projecthero.hulk.ride_rampage").withStyle(ChatFormatting.RED), true);
			return true;
		}
		if (!hulk.getPassengers().isEmpty()) {
			rider.displayClientMessage(Component.translatable("message.projecthero.hulk.ride_full").withStyle(ChatFormatting.GRAY), true);
			return true;
		}
		if (!rider.startRiding(hulk, true)) {
			return false;
		}
		rider.displayClientMessage(Component.translatable("message.projecthero.hulk.ride_on").withStyle(ChatFormatting.GREEN), true);
		hulk.level().playSound(null, hulk.getX(), hulk.getY(), hulk.getZ(), SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.PLAYERS, 1.0f, 0.7f);
		return true;
	}

	/** Where a rider sits on the Hulk: high on his back, just behind his shoulders (vehicle-local, +Z forward). */
	public static Vec3 seat(Entity hulk, EntityDimensions dims) {
		return new Vec3(0.0, dims.height() * 0.58, -dims.width() * 0.45);
	}
}
