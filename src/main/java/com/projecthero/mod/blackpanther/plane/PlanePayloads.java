package com.projecthero.mod.blackpanther.plane;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** v0.17.7 Black Panther Ancestral Plane: the packets and the saved return point. */
public final class PlanePayloads {
	private PlanePayloads() {
	}

	/**
	 * Server -> client: play plane scene {@code scene} ({@link PlaneScene} id; -1 stops) {@code elapsed} ticks in, anchored at the
	 * plane slot's origin ({@code x, y, z}), following entity {@code entity} (the eldest ancestor or Bast's spirit, or -1);
	 * {@code skippable}: seen before, so Space skips it.
	 */
	public record Scene(int scene, int elapsed, double x, double y, double z, int entity, boolean skippable) implements CustomPacketPayload {
		public static final Type<Scene> TYPE = new Type<>(ProjectHeroMod.id("ancestral_plane_scene"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Scene> CODEC = StreamCodec.of((buf, p) -> {
			buf.writeVarInt(p.scene + 1);
			buf.writeVarInt(p.elapsed);
			buf.writeDouble(p.x);
			buf.writeDouble(p.y);
			buf.writeDouble(p.z);
			buf.writeVarInt(p.entity + 1);
			buf.writeBoolean(p.skippable);
		}, buf -> new Scene(buf.readVarInt() - 1, buf.readVarInt(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readVarInt() - 1,
				buf.readBoolean()));

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/**
	 * Server -> client: the plane's HUD. {@code mode}: 0 none, 1 walk to the ancestors, 2 go into the shrine, 3 the trial
	 * ({@code a} round 0-2, {@code b} shades struck, {@code c} shades in the round, {@code d} wounds, {@code e} an event: 0 none,
	 * 1 round won, 2 round lost, 3 wounded, 4 a new round starts), 4 the spirit path ({@code a} checkpoint reached 0-4, {@code b}
	 * checkpoints, {@code c} progress 0-1000, {@code e} 1 checkpoint reached, 2 caught after a fall). {@code wake} 0-100: the hold-Sneak
	 * wake-up's progress. {@code origin}: the slot origin (for the guide trail).
	 */
	public record Hud(int mode, int a, int b, int c, int d, int e, int wake) implements CustomPacketPayload {
		public static final Type<Hud> TYPE = new Type<>(ProjectHeroMod.id("ancestral_plane_hud"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Hud> CODEC = StreamCodec.of((buf, p) -> {
			buf.writeVarInt(p.mode);
			buf.writeVarInt(p.a);
			buf.writeVarInt(p.b);
			buf.writeVarInt(p.c);
			buf.writeVarInt(p.d);
			buf.writeVarInt(p.e);
			buf.writeVarInt(p.wake);
		}, buf -> new Hud(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));

		public static Hud none() {
			return new Hud(0, 0, 0, 0, 0, 0, 0);
		}

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Client -> server: skip the scene playing now (only honoured for a scene seen before). */
	public record Skip() implements CustomPacketPayload {
		public static final Type<Skip> TYPE = new Type<>(ProjectHeroMod.id("ancestral_plane_skip"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Skip> CODEC = StreamCodec.unit(new Skip());

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/**
	 * Where the player fell asleep: the dimension, the position and look to come back to, and the bed (to wake in). Saved on the
	 * player ({@link AncestralPlane#RETURN}), so a logout, crash or restart in the plane still brings them back to bed.
	 */
	public record Return(String dimension, double x, double y, double z, float yRot, float xRot, Optional<BlockPos> bed) {
		public static final Codec<Return> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.STRING.fieldOf("dimension").forGetter(Return::dimension),
				Codec.DOUBLE.fieldOf("x").forGetter(Return::x),
				Codec.DOUBLE.fieldOf("y").forGetter(Return::y),
				Codec.DOUBLE.fieldOf("z").forGetter(Return::z),
				Codec.FLOAT.fieldOf("yRot").forGetter(Return::yRot),
				Codec.FLOAT.fieldOf("xRot").forGetter(Return::xRot),
				BlockPos.CODEC.optionalFieldOf("bed").forGetter(Return::bed)).apply(i, Return::new));
	}
}
