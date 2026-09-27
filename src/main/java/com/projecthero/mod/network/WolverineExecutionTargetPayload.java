package com.projecthero.mod.network;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server &rarr; client (v0.13.4): the entity id Adamantium Execution has locked onto, or {@code -1} to
 * clear it. Sent only to the Wolverine himself -- his client renders exactly this entity glowing red
 * (see {@code EntityGlowMixin}); nothing is set on the mob itself, so no other player sees the glow.
 */
public record WolverineExecutionTargetPayload(int targetId) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<WolverineExecutionTargetPayload> TYPE = new CustomPacketPayload.Type<>(
			ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "wolverine_execution_target"));

	public static final StreamCodec<RegistryFriendlyByteBuf, WolverineExecutionTargetPayload> CODEC = StreamCodec.of(
			(buf, p) -> buf.writeVarInt(p.targetId + 1),
			buf -> new WolverineExecutionTargetPayload(buf.readVarInt() - 1));

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
