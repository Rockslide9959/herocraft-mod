package com.projecthero.mod.network;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server &rarr; client (v0.13.4): one segment of a crackling lightning arc -- from {@code fromEntityId}
 * (or the caster's own hand, if {@code fromEntityId < 0}) to {@code targetEntityId} (or the raw point
 * {@code (x, y, z)}, if {@code targetEntityId < 0}). Replaces the old approach of spawning a line of
 * {@code ELECTRIC_SPARK} particles: the client instead draws a jagged, animated multi-strand line
 * between the two resolved endpoints every frame for as long as the segment is held/fading.
 *
 * <p>Segments are keyed by {@code (casterId, slot)}: Lightning Beam (X, continuous) resends the same
 * slot every tick while held, so it just keeps replacing itself; Chain Lightning (C, one-shot) sends
 * one segment per hop with a distinct slot, so the whole chain renders as connected segments at once
 * and then fades together. {@code holdTicks == 0 && fadeTicks == 0} removes the segment outright.
 */
public record ThorLightningArcPayload(int casterId, int slot, int fromEntityId, int targetEntityId,
		double x, double y, double z, int holdTicks, int fadeTicks) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<ThorLightningArcPayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "thor_lightning_arc"));

	public static final StreamCodec<RegistryFriendlyByteBuf, ThorLightningArcPayload> CODEC = StreamCodec.of(
			(buf, p) -> {
				buf.writeVarInt(p.casterId);
				buf.writeVarInt(p.slot);
				buf.writeVarInt(p.fromEntityId + 1);
				buf.writeVarInt(p.targetEntityId + 1);
				buf.writeDouble(p.x);
				buf.writeDouble(p.y);
				buf.writeDouble(p.z);
				buf.writeVarInt(p.holdTicks);
				buf.writeVarInt(p.fadeTicks);
			},
			buf -> new ThorLightningArcPayload(buf.readVarInt(), buf.readVarInt(), buf.readVarInt() - 1,
					buf.readVarInt() - 1, buf.readDouble(), buf.readDouble(), buf.readDouble(),
					buf.readVarInt(), buf.readVarInt()));

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
