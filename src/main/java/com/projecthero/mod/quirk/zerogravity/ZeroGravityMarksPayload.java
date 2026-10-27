package com.projecthero.mod.quirk.zerogravity;

import java.util.List;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * v0.17.1 Quirks revamp ("Touch marks", docs 3.7): server to every client near a Zero Gravity user -- the entities she floats
 * right now (creatures, items, her rubble's block displays), so every viewer draws the glowing pink fingertip prints on them.
 * Re-sent every half second while anything floats (and once, empty, when it all comes down); the client drops a list that
 * is not refreshed within two seconds.
 */
public record ZeroGravityMarksPayload(int owner, List<Integer> ids) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<ZeroGravityMarksPayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "zero_gravity_marks"));

	public static final StreamCodec<RegistryFriendlyByteBuf, ZeroGravityMarksPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, ZeroGravityMarksPayload::owner,
			ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(512)), ZeroGravityMarksPayload::ids,
			ZeroGravityMarksPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
