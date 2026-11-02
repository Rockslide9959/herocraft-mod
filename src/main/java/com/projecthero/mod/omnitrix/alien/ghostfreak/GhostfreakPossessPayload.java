package com.projecthero.mod.omnitrix.alien.ghostfreak;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * v0.17.2 (user: "Ghostfreak can posses players for like 10 seconds") server -&gt; the POSSESSED player, every tick of the
 * possession: who holds them ({@code possessorId}, -1 = released), until when ({@code until}, game time), and Ghostfreak's own
 * movement keys and look this tick. The victim's client plays those keys as its own input and turns to that look, and takes
 * none of its own (GhostfreakClient). The stream itself is the lease: when the packets stop coming (a release, a lost
 * packet, a crash on the other end) the client lets go on its own within half a second.
 */
public record GhostfreakPossessPayload(int possessorId, long until, float forward, float strafe, boolean jump, float yaw, float pitch)
		implements CustomPacketPayload {
	public static final GhostfreakPossessPayload RELEASE = new GhostfreakPossessPayload(-1, 0L, 0f, 0f, false, 0f, 0f);

	public static final CustomPacketPayload.Type<GhostfreakPossessPayload> TYPE = new CustomPacketPayload.Type<>(ProjectHeroMod.id("ghostfreak_possess"));

	public static final StreamCodec<RegistryFriendlyByteBuf, GhostfreakPossessPayload> CODEC = StreamCodec.of((buf, p) -> {
		buf.writeVarInt(p.possessorId);
		buf.writeVarLong(p.until);
		buf.writeFloat(p.forward);
		buf.writeFloat(p.strafe);
		buf.writeBoolean(p.jump);
		buf.writeFloat(p.yaw);
		buf.writeFloat(p.pitch);
	}, buf -> new GhostfreakPossessPayload(buf.readVarInt(), buf.readVarLong(), buf.readFloat(), buf.readFloat(), buf.readBoolean(), buf.readFloat(),
			buf.readFloat()));

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
