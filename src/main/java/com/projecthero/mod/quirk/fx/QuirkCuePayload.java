package com.projecthero.mod.quirk.fx;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * v0.17.1 Quirk framework, server &rarr; the owner's client: a HUD cue. {@link #COMBO}: a combo route landed ({@code key}
 * is the route's lang key, {@code colour} the Quirk's colour); {@link #ULTIMATE}: the Plus Ultra ultimate fired ({@code
 * key} = the ultimate's lang key: the "PLUS ULTRA!" banner and a white-gold flash).
 */
public record QuirkCuePayload(int kind, String key, int colour) implements CustomPacketPayload {
	public static final int COMBO = 1;
	public static final int ULTIMATE = 2;

	public static final CustomPacketPayload.Type<QuirkCuePayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "quirk_cue"));

	public static final StreamCodec<RegistryFriendlyByteBuf, QuirkCuePayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, QuirkCuePayload::kind,
			ByteBufCodecs.STRING_UTF8, QuirkCuePayload::key,
			ByteBufCodecs.INT, QuirkCuePayload::colour,
			QuirkCuePayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
