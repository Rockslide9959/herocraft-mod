package com.projecthero.mod.omnitrix.alien.upgrade;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * v0.17.2 Upgrade revamp (Tech Takeover): server -&gt; everyone who can see it, when a machine is taken over or let go.
 * {@code entityId} is the machine, {@code ownerId} the Upgrade who holds it, {@code ticks} how long it is held from now (0 =
 * released). The client crawls his green circuit lines over it ({@code UpgradeFx}) and outlines it for its owner.
 */
public record UpgradeTakeoverPayload(int entityId, int ownerId, int ticks) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<UpgradeTakeoverPayload> TYPE = new CustomPacketPayload.Type<>(ProjectHeroMod.id("upgrade_takeover"));

	public static final StreamCodec<RegistryFriendlyByteBuf, UpgradeTakeoverPayload> CODEC = StreamCodec.of((buf, p) -> {
		buf.writeVarInt(p.entityId);
		buf.writeVarInt(p.ownerId);
		buf.writeVarInt(p.ticks);
	}, buf -> new UpgradeTakeoverPayload(buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
