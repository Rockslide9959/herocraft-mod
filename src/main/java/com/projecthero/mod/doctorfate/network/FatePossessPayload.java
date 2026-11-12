package com.projecthero.mod.doctorfate.network;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to the POSSESSED player (v0.17.3 Doctor Fate), every tick of a Possession: until when ({@code until}, game time; 0 =
 * released), and Nabu's movement keys and look this tick. The client plays them as its own input (the Ghostfreak pattern); when
 * the stream stops the client lets go on its own within half a second.
 */
public record FatePossessPayload(long until, float forward, float strafe, boolean up, boolean down, float yaw, float pitch)
		implements CustomPacketPayload {
	public static final FatePossessPayload RELEASE = new FatePossessPayload(0L, 0f, 0f, false, false, 0f, 0f);

	public static final CustomPacketPayload.Type<FatePossessPayload> TYPE = new CustomPacketPayload.Type<>(ProjectHeroMod.id("doctor_fate_possess"));

	public static final StreamCodec<RegistryFriendlyByteBuf, FatePossessPayload> CODEC = StreamCodec.of((buf, p) -> {
		buf.writeVarLong(p.until);
		buf.writeFloat(p.forward);
		buf.writeFloat(p.strafe);
		buf.writeBoolean(p.up);
		buf.writeBoolean(p.down);
		buf.writeFloat(p.yaw);
		buf.writeFloat(p.pitch);
	}, buf -> new FatePossessPayload(buf.readVarLong(), buf.readFloat(), buf.readFloat(), buf.readBoolean(), buf.readBoolean(), buf.readFloat(),
			buf.readFloat()));

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
