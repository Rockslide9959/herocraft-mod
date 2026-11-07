package com.projecthero.mod.beamclash.network;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * Server to client, every tick of a beam clash, to everyone near it (v0.17.3): the two sides' entity ids, where each beam
 * starts, the struggle point, the colours, the balance (-1..1, a to b), and {@code over} on the last one.
 */
public record BeamClashPayload(int id, int a, int b, Vec3 fromA, Vec3 fromB, Vec3 point, int rgbA, int rgbB, float balance, boolean over)
		implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<BeamClashPayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "beam_clash"));

	public static final StreamCodec<RegistryFriendlyByteBuf, BeamClashPayload> CODEC = StreamCodec.of(BeamClashPayload::write, BeamClashPayload::read);

	private static void write(RegistryFriendlyByteBuf buf, BeamClashPayload p) {
		buf.writeVarInt(p.id);
		buf.writeVarInt(p.a);
		buf.writeVarInt(p.b);
		vec(buf, p.fromA);
		vec(buf, p.fromB);
		vec(buf, p.point);
		buf.writeInt(p.rgbA);
		buf.writeInt(p.rgbB);
		buf.writeFloat(p.balance);
		buf.writeBoolean(p.over);
	}

	private static BeamClashPayload read(RegistryFriendlyByteBuf buf) {
		return new BeamClashPayload(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), vec(buf), vec(buf), vec(buf), buf.readInt(), buf.readInt(),
				buf.readFloat(), buf.readBoolean());
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
