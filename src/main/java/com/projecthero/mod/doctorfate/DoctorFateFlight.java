package com.projecthero.mod.doctorfate;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.doctorfate.data.FateAttachments;
import com.projecthero.mod.doctorfate.data.FateState;

import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.3 Doctor Fate: the golden-ankh hover flight. <b>Double-tap Space</b> in the air with the helmet on, built like the
 * Saiyan's: vanilla {@code mayfly}/{@code flying} so the server never kicks a hovering player, the movement itself the client's
 * shared directional flight (14 b/s, 28 sprinting, a dead hover with no input). It ends on landing, another double-tap, the helmet
 * coming off, or the end of a Possession (Nabu flies the body while he holds it, whatever the helmet).
 */
public final class DoctorFateFlight {
	public static final String FLIGHT = "flight";

	private static final Map<UUID, Long> STARTED = new ConcurrentHashMap<>();

	private DoctorFateFlight() {
	}

	static void clearSessionState() {
		STARTED.clear();
	}

	static void forget(UUID id) {
		STARTED.remove(id);
	}

	public static void toggle(ServerPlayer player) {
		if (DoctorFate.possessed(player)) {
			return;
		}
		if (DoctorFate.isFlying(player)) {
			stop(player);
			DoctorFate.startCooldown(player, FLIGHT, DoctorFateConfig.FLIGHT_TOGGLE_COOLDOWN);
			return;
		}
		if (player.isPassenger() || player.isFallFlying() || DoctorFate.cooldownRemaining(player, FLIGHT) > 0) {
			return;
		}
		if (!DoctorFate.wearsHelmet(player)) {
			DoctorFate.say(player, "message.projecthero.doctor_fate.needs_helmet", ChatFormatting.GRAY);
			return;
		}
		start(player);
		DoctorFate.startCooldown(player, FLIGHT, DoctorFateConfig.FLIGHT_TOGGLE_COOLDOWN);
	}

	public static void start(ServerPlayer player) {
		FateState n = DoctorFate.state(player).copy();
		n.flying = true;
		DoctorFate.save(player, n);
		STARTED.put(player.getUUID(), player.level().getGameTime());
		grantAbilities(player);
		player.resetFallDistance();
		ServerLevel level = (ServerLevel) player.level();
		Vec3 c = player.position();
		// v0.17.3 effects pass: a small golden ankh flares under him as he lifts off
		DoctorFateFx.send(level, com.projecthero.mod.doctorfate.network.FateSpellFxPayload.LANDING, c.add(0, 0.05, 0), player.getLookAngle(), 1.0f, 18);
		DoctorFate.sound(level, c, SoundEvents.BEACON_POWER_SELECT, 0.3f, 1.9f);
	}

	private static void grantAbilities(ServerPlayer player) {
		if (!player.getAbilities().instabuild && !player.isSpectator()) {
			player.getAbilities().mayfly = true;
			player.getAbilities().flying = true;
			player.onUpdateAbilities();
		}
	}

	public static void stop(ServerPlayer player) {
		FateState s = player.getAttachedOrElse(FateAttachments.STATE, null);
		boolean was = s != null && s.flying;
		if (was) {
			FateState n = s.copy();
			n.flying = false;
			DoctorFate.save(player, n);
		}
		STARTED.remove(player.getUUID());
		if (was && !player.getAbilities().instabuild && !player.isSpectator() && !anyOtherFlightWants(player)) {
			player.getAbilities().mayfly = false;
			player.getAbilities().flying = false;
			player.onUpdateAbilities();
		}
	}

	static void tick(ServerPlayer player) {
		if (!DoctorFate.isFlying(player)) {
			return;
		}
		boolean possessed = DoctorFate.possessed(player);
		if (player.isSpectator() || player.isPassenger() || player.isFallFlying() || !player.isAlive()
				|| (!possessed && !DoctorFate.wearsHelmet(player))) {
			stop(player);
			return;
		}
		long now = player.level().getGameTime();
		Long start = STARTED.computeIfAbsent(player.getUUID(), k -> now);
		if (!possessed && com.projecthero.mod.flight.FlightLanding.landed(player, start)) {
			stop(player);
			return;
		}
		if (!player.getAbilities().flying || !player.getAbilities().mayfly) {
			if (!possessed && now - start >= DoctorFateConfig.FLIGHT_LIFTOFF_GRACE && !player.getAbilities().flying && player.getAbilities().mayfly) {
				stop(player); // the client dropped vanilla flight itself (landed / double-tapped)
				DoctorFate.startCooldown(player, FLIGHT, DoctorFateConfig.FLIGHT_TOGGLE_COOLDOWN);
				return;
			}
			grantAbilities(player);
		}
		player.resetFallDistance();
		if (now % 4 == 0) {
			ServerLevel level = (ServerLevel) player.level();
			level.sendParticles(DoctorFate.dust(DoctorFate.GOLD, 0.9f), player.getX(), player.getY() - 0.1, player.getZ(), 2, 0.25, 0.05, 0.25, 0.0);
		}
	}

	private static boolean anyOtherFlightWants(ServerPlayer player) {
		return player.getAttachedOrElse(ModAttachments.FLYING, false)
				|| player.getAttachedOrElse(ModAttachments.HERO_FLYING, false)
				|| player.getAttachedOrElse(ModAttachments.IRON_MAN_FLYING, false)
				|| player.getAttachedOrElse(ModAttachments.REPULSOR_BOOTS_FLYING, false)
				|| player.getAttachedOrElse(ModAttachments.MAX_STEEL_FLYING, false)
				|| player.getAttachedOrElse(ModAttachments.GREEN_LANTERN_FLYING, false);
	}
}
