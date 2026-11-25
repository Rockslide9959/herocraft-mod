package com.projecthero.mod.shazam.journey;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** v0.17.7 Shazam acquisition: the journey's packets and its saved return point. */
public final class JourneyPayloads {
	private JourneyPayloads() {
	}

	/**
	 * Server -> client: play journey scene {@code scene} ({@link JourneyScene} id; -1 stops) {@code elapsed} ticks in, anchored at
	 * ({@code x, y, z}) facing {@code yaw} (the illusion for the descent, the realm slot's origin for the rest), following entity
	 * {@code entity} (the train or the Wizard, or -1).
	 */
	public record Scene(int scene, int elapsed, double x, double y, double z, float yaw, int entity) implements CustomPacketPayload {
		public static final Type<Scene> TYPE = new Type<>(ProjectHeroMod.id("shazam_journey_scene"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Scene> CODEC = StreamCodec.of((buf, p) -> {
			buf.writeVarInt(p.scene + 1);
			buf.writeVarInt(p.elapsed);
			buf.writeDouble(p.x);
			buf.writeDouble(p.y);
			buf.writeDouble(p.z);
			buf.writeFloat(p.yaw);
			buf.writeVarInt(p.entity + 1);
		}, buf -> new Scene(buf.readVarInt() - 1, buf.readVarInt(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readFloat(),
				buf.readVarInt() - 1));

		public static Scene stop() {
			return new Scene(-1, 0, 0, 0, 0, 0f, -1);
		}

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/**
	 * Server -> client: the illusion of the stairway (only its player is sent it) -- shown at ({@code x, y, z}) (the top step's
	 * near edge, on the ground) descending toward {@code yaw}, {@code age} ticks old; {@code active} false fades it away.
	 */
	public record Illusion(boolean active, double x, double y, double z, float yaw, int age) implements CustomPacketPayload {
		public static final Type<Illusion> TYPE = new Type<>(ProjectHeroMod.id("shazam_illusion"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Illusion> CODEC = StreamCodec.of((buf, p) -> {
			buf.writeBoolean(p.active);
			buf.writeDouble(p.x);
			buf.writeDouble(p.y);
			buf.writeDouble(p.z);
			buf.writeFloat(p.yaw);
			buf.writeVarInt(p.age);
		}, buf -> new Illusion(buf.readBoolean(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readFloat(), buf.readVarInt()));

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Client -> server: "SHAZAM!" -- the player said his name (the prompt's button or key). */
	public record Answer() implements CustomPacketPayload {
		public static final Type<Answer> TYPE = new Type<>(ProjectHeroMod.id("shazam_journey_answer"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Answer> CODEC = StreamCodec.unit(new Answer());

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/**
	 * Where a player was when the illusion first appeared to them -- the dimension, position and look they are returned to after
	 * the journey. Saved with the player ({@link ShazamJourney#RETURN}), so a logout, crash or restart mid-journey still brings them
	 * home on their next login.
	 */
	public record Return(String dimension, double x, double y, double z, float yRot, float xRot) {
		public static final Codec<Return> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.STRING.fieldOf("dimension").forGetter(Return::dimension),
				Codec.DOUBLE.fieldOf("x").forGetter(Return::x),
				Codec.DOUBLE.fieldOf("y").forGetter(Return::y),
				Codec.DOUBLE.fieldOf("z").forGetter(Return::z),
				Codec.FLOAT.fieldOf("yRot").forGetter(Return::yRot),
				Codec.FLOAT.fieldOf("xRot").forGetter(Return::xRot)).apply(i, Return::new));
	}
}
