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

	/**
	 * Where a rider sits on the Hulk: high on his back, just behind his shoulders. v0.15.21: turned with his body -- the
	 * offset used to be returned unrotated, so the rider hung off a fixed world side (only "behind" while he faced south).
	 */
	public static Vec3 seat(Entity hulk, EntityDimensions dims) {
		float yaw = hulk instanceof net.minecraft.world.entity.LivingEntity living ? living.yBodyRot : hulk.getYRot();
		return new Vec3(0.0, dims.height() * 0.62, -dims.width() * 0.42).yRot(-yaw * net.minecraft.util.Mth.DEG_TO_RAD);
	}

	/** v0.15.21: the player riding this Hulk's back, or null. Client-safe (passengers are synced). */
	public static net.minecraft.world.entity.player.Player rider(net.minecraft.world.entity.player.Player hulk) {
		for (Entity e : hulk.getPassengers()) {
			if (e instanceof net.minecraft.world.entity.player.Player p) {
				return p;
			}
		}
		return null;
	}

	/** v0.15.21: is this player riding a Hulk's back? Client-safe. */
	public static boolean ridingHulk(Entity rider) {
		return rider.getVehicle() instanceof net.minecraft.world.entity.player.Player h && Hulk.isHulk(h);
	}

	/**
	 * v0.15.21, both sides, right after the vehicle placed the rider ({@code EntityHulkRideMixin}): the rider's body
	 * faces the way the Hulk's does, and the rider's own view turns with him (like a boat) -- the rider can still look
	 * round freely, up to a little past each shoulder.
	 */
	public static void carryRider(net.minecraft.world.entity.LivingEntity hulk, Entity passenger) {
		if (!(passenger instanceof net.minecraft.world.entity.LivingEntity rider)) {
			return;
		}
		rider.yBodyRot = hulk.yBodyRot;
		rider.yBodyRotO = hulk.yBodyRotO;
		if (rider instanceof net.minecraft.world.entity.player.Player p && p.isLocalPlayer()) {
			float turn = net.minecraft.util.Mth.wrapDegrees(hulk.yBodyRot - hulk.yBodyRotO);
			p.setYRot(p.getYRot() + turn);
			p.setYHeadRot(p.getYHeadRot() + turn);
			clampLook(hulk, p);
		}
	}

	/** The rider's head stays within {@value #LOOK_LIMIT} degrees of the Hulk's facing (see {@link #carryRider}). */
	public static void clampLook(net.minecraft.world.entity.LivingEntity hulk, Entity rider) {
		float rel = net.minecraft.util.Mth.wrapDegrees(rider.getYRot() - hulk.yBodyRot);
		float clamped = net.minecraft.util.Mth.clamp(rel, -LOOK_LIMIT, LOOK_LIMIT);
		if (clamped != rel) {
			float fix = clamped - rel;
			rider.yRotO += fix;
			rider.setYRot(rider.getYRot() + fix);
			rider.setYHeadRot(rider.getYRot());
		}
	}

	private static final float LOOK_LIMIT = 120.0f;

	/** Server tick, from {@code Hulk.tick}: whoever rides him (and for 8 s after they hop off) takes no fall damage. */
	public static void tick(ServerPlayer hulk) {
		for (Entity e : hulk.getPassengers()) {
			if (e instanceof ServerPlayer mate) {
				HulkGrab.protectLanding(mate);
			}
		}
	}
}
