package com.projecthero.mod.network;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * v0.17.1: server -&gt; client, a Green Lantern cue for this player's screen -- either a landed Construct Combo (the HUD
 * popup, {@code GreenLanternComboPopup}) or a line the Power Ring says ({@code GreenLanternRingVoiceClient}, dropped
 * there if the player muted the ring in the suits screen).
 *
 * @param kind {@link #COMBO} or {@link #VOICE}
 * @param key  the combo id ({@code projecthero.green_lantern.combo.<key>}) or the line id
 *             ({@code message.projecthero.green_lantern.ring_voice.<key>})
 * @param arg  a translation key filled into the line's {@code %s} (a rank or combo name), or empty
 */
public record GreenLanternCuePayload(int kind, String key, String arg) implements CustomPacketPayload {
	public static final int COMBO = 0;
	public static final int VOICE = 1;

	public static final CustomPacketPayload.Type<GreenLanternCuePayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "green_lantern_cue"));

	public static final StreamCodec<RegistryFriendlyByteBuf, GreenLanternCuePayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, GreenLanternCuePayload::kind,
			ByteBufCodecs.STRING_UTF8, GreenLanternCuePayload::key,
			ByteBufCodecs.STRING_UTF8, GreenLanternCuePayload::arg,
			GreenLanternCuePayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
