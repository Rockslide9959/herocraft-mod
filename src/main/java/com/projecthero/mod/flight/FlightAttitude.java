package com.projecthero.mod.flight;

import com.projecthero.mod.attachment.ModAttachments;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;

/**
 * v0.16.2: a flier's Physics Flight attitude as everyone else sees it -- whether they fly in Physics mode and how far they
 * are rolled (degrees, + = right wing down). Each client simulates its own flight and reports this with a
 * {@code FlightAttitudePayload}; the server only sanity-checks it and shares it through the synced
 * {@link ModAttachments#FLIGHT_ATTITUDE} attachment, so other players see the body bank and roll. Purely cosmetic: it
 * never changes movement, damage or any rule. Not persisted.
 */
public record FlightAttitude(boolean physics, float roll) {
	public static final FlightAttitude NONE = new FlightAttitude(false, 0.0f);

	public static final StreamCodec<ByteBuf, FlightAttitude> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.BOOL, FlightAttitude::physics,
			ByteBufCodecs.FLOAT, FlightAttitude::roll,
			FlightAttitude::new);

	/** The attitude a client reported, cleaned up: a non-finite roll is refused (null), any other wrapped into -180..180. */
	public static FlightAttitude sanitize(boolean physics, float roll) {
		if (!Float.isFinite(roll)) {
			return null;
		}
		return new FlightAttitude(physics, physics ? (float) PhysicsFlightModel.wrap(roll) : 0.0f);
	}

	/** Server side of {@code FlightAttitudePayload}: store and share the reported attitude (a grounded player never rolls). */
	public static void handle(ServerPlayer player, boolean physics, float roll) {
		FlightAttitude reported = sanitize(physics, roll);
		if (reported == null) {
			return;
		}
		if (!player.getAbilities().flying && reported.roll() != 0.0f) {
			reported = new FlightAttitude(reported.physics(), 0.0f);
		}
		if (!reported.equals(player.getAttachedOrElse(ModAttachments.FLIGHT_ATTITUDE, NONE))) {
			player.setAttached(ModAttachments.FLIGHT_ATTITUDE, reported);
		}
	}
}
