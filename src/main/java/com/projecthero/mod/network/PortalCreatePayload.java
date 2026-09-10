package com.projecthero.mod.network;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client &rarr; server: the destination chosen in the Portal picker. {@link #dimension} is a
 * dimension id string ({@code minecraft:overworld} etc). Re-validated server-side (owns Teleportation,
 * charge really completed).
 */
public record PortalCreatePayload(int x, int y, int z, String dimension) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<PortalCreatePayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "portal_create"));

	public static final StreamCodec<RegistryFriendlyByteBuf, PortalCreatePayload> CODEC = StreamCodec.of(
			(buf, p) -> {
				buf.writeInt(p.x);
				buf.writeInt(p.y);
				buf.writeInt(p.z);
				ByteBufCodecs.STRING_UTF8.encode(buf, p.dimension);
			},
			buf -> new PortalCreatePayload(buf.readInt(), buf.readInt(), buf.readInt(),
					ByteBufCodecs.STRING_UTF8.decode(buf)));

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
