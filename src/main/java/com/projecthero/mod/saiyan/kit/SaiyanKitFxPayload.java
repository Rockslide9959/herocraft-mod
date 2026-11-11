package com.projecthero.mod.saiyan.kit;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * Server to client (v0.17.3 Saiyan revamp): one move-kit effect for everyone near it, drawn by {@code client.saiyan.kit}.
 * {@code kind} is one of the constants below; {@code entity} the Saiyan (or -1); {@code a}/{@code b} two positions or a
 * position and a velocity; {@code f} a size, charge or radius; {@code rgb} the Ki colour; {@code extra} a pose id, shot id or
 * duration.
 */
public record SaiyanKitFxPayload(int kind, int entity, Vec3 a, Vec3 b, float f, int rgb, int extra) implements CustomPacketPayload {
	/** A body pose ({@code extra} = {@link SaiyanKit} ANIM_*). */
	public static final int ANIM = 1;
	/** A punch / kick landing: a flash and spokes at {@code a}, size {@code f}. */
	public static final int IMPACT = 2;
	/** A Ki sphere leaves {@code a} at velocity {@code b}; {@code extra} = its id, {@code f} = its size. */
	public static final int SHOT = 3;
	/** Sphere {@code extra} bursts at {@code a}. */
	public static final int SHOT_END = 4;
	/** The Kamehameha starts charging. */
	public static final int BEAM_CHARGE = 5;
	/** ...fires at charge {@code f} for {@code extra} ticks. */
	public static final int BEAM_FIRE = 6;
	public static final int BEAM_END = 7;
	/** An afterimage left at {@code a} (yaw {@code f}), and a streak to {@code b}. */
	public static final int GHOST = 8;
	/** A Ki trail behind the Saiyan for {@code extra} ticks. */
	public static final int TRAIL = 9;
	/** The Explosive Wave: a dome and a ground ring of radius {@code f} round {@code a}. */
	public static final int DOME = 10;
	/** A slam's crater: a ground ring, dust and rock chips of radius {@code f} at {@code a} (no block is changed). */
	public static final int CRATER = 11;
	/** The Solar Flare's flash at {@code a}, reaching {@code f}. */
	public static final int FLASH = 12;
	/** Ki Rain's target ring at {@code a}, radius {@code f}, for {@code extra} ticks. */
	public static final int RAIN_MARK = 13;

	public static final CustomPacketPayload.Type<SaiyanKitFxPayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "saiyan_kit_fx"));

	public static final StreamCodec<RegistryFriendlyByteBuf, SaiyanKitFxPayload> CODEC = StreamCodec.of(SaiyanKitFxPayload::write,
			SaiyanKitFxPayload::read);

	private static void write(RegistryFriendlyByteBuf buf, SaiyanKitFxPayload p) {
		buf.writeByte(p.kind);
		buf.writeVarInt(p.entity + 1);
		vec(buf, p.a);
		vec(buf, p.b);
		buf.writeFloat(p.f);
		buf.writeInt(p.rgb);
		buf.writeVarInt(p.extra);
	}

	private static SaiyanKitFxPayload read(RegistryFriendlyByteBuf buf) {
		return new SaiyanKitFxPayload(buf.readByte(), buf.readVarInt() - 1, vec(buf), vec(buf), buf.readFloat(), buf.readInt(), buf.readVarInt());
	}

	private static void vec(RegistryFriendlyByteBuf buf, Vec3 v) {
		buf.writeDouble(v.x);
		buf.writeDouble(v.y);
		buf.writeDouble(v.z);
	}

	private static Vec3 vec(RegistryFriendlyByteBuf buf) {
		return new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
