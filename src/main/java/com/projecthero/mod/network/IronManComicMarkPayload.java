package com.projecthero.mod.network;

import java.util.List;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * v0.16.1 comic kit: server -&gt; ONE client "outline these entities for {@code ticks}" -- the Mark 1 V2 Searchlight, the
 * Hydro Sonar Pulse / Sonar Mark. Sent only to the wearer who used the move; the outline is drawn by that client alone
 * ({@code IronManHighlight} client marks), never by setting anything on the entities -- the private-highlight rule.
 */
public record IronManComicMarkPayload(List<Integer> entityIds, int ticks) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<IronManComicMarkPayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "iron_man_comic_mark"));

	public static final StreamCodec<RegistryFriendlyByteBuf, IronManComicMarkPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(256)), IronManComicMarkPayload::entityIds,
			ByteBufCodecs.VAR_INT, IronManComicMarkPayload::ticks,
			IronManComicMarkPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
