package com.projecthero.mod.network;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * v0.14.29 (agent F): server -&gt; client "JARVIS says this line". The client draws it in its own wrapped speech box
 * (or drops it if the player turned JARVIS off with {@code /jarvis off}).
 *
 * @param line  the line id; the text is {@code message.projecthero.ironman.jarvis.<line>}
 * @param crude true for the Mark 1's bare-bones {@code SYSTEM:} readout instead of JARVIS
 */
public record IronManJarvisPayload(String line, boolean crude) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<IronManJarvisPayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "iron_man_jarvis"));

	public static final StreamCodec<RegistryFriendlyByteBuf, IronManJarvisPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8, IronManJarvisPayload::line,
			ByteBufCodecs.BOOL, IronManJarvisPayload::crude,
			IronManJarvisPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
