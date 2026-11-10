package com.projecthero.mod.saiyan.moves.network;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * Server to client (v0.17.3): one Saiyan move effect for everyone near it. {@code kind} is one of the constants below;
 * {@code entity} the Saiyan (or -1), {@code a}/{@code b} positions or a position and a velocity, {@code f} a size or charge,
 * {@code rgb} a colour, {@code extra} an animation id, orb id or duration.
 */
public record SaiyanFxPayload(int kind, int entity, Vec3 a, Vec3 b, float f, int rgb, int extra) implements CustomPacketPayload {
	public static final int BEAM_CHARGE = 1;
	public static final int BEAM_FIRE = 2;
	public static final int BEAM_END = 3;
	public static final int ORB_CHARGE = 4;
	public static final int ORB_FLY = 5;
	public static final int ORB_BOOM = 6;
	public static final int IMPACT = 7;
	public static final int AFTERIMAGE = 8;
	public static final int RUSH = 9;
	public static final int ANIM = 10;
	public static final int SHOCKWAVE = 11;
	public static final int ORB_CANCEL = 12;

	public static final CustomPacketPayload.Type<SaiyanFxPayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "saiyan_fx"));

	public static final StreamCodec<RegistryFriendlyByteBuf, SaiyanFxPayload> CODEC = StreamCodec.of(SaiyanFxPayload::write, SaiyanFxPayload::read);

	private static void write(RegistryFriendlyByteBuf buf, SaiyanFxPayload p) {
		buf.writeByte(p.kind);
		buf.writeVarInt(p.entity + 1);
		vec(buf, p.a);
		vec(buf, p.b);
		buf.writeFloat(p.f);
		buf.writeInt(p.rgb);
		buf.writeVarInt(p.extra);
	}

	private static SaiyanFxPayload read(RegistryFriendlyByteBuf buf) {
		return new SaiyanFxPayload(buf.readByte(), buf.readVarInt() - 1, vec(buf), vec(buf), buf.readFloat(), buf.readInt(), buf.readVarInt());
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
