package com.projecthero.mod.network;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client -&gt; server (v0.17.1): the Green Lantern Construct Menu and the H key. {@code action} is one of the constants
 * below; {@code key} is a {@code GreenLanternBinding.KEYS} id ("G", "SG", ...), {@code entry} an entry id, and
 * {@code pressed} the H key's edge. The server re-validates everything ({@code GreenLanternBindings}).
 */
public record GreenLanternMenuPayload(int action, String key, String entry, boolean pressed) implements CustomPacketPayload {
	public static final int BIND = 0;
	public static final int CLEAR = 1;
	public static final int RESET = 2;
	public static final int SPAWN = 3;
	public static final int KEY_H = 4;

	public static final CustomPacketPayload.Type<GreenLanternMenuPayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "green_lantern_menu"));

	public static final StreamCodec<RegistryFriendlyByteBuf, GreenLanternMenuPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, GreenLanternMenuPayload::action,
			ByteBufCodecs.STRING_UTF8, GreenLanternMenuPayload::key,
			ByteBufCodecs.STRING_UTF8, GreenLanternMenuPayload::entry,
			ByteBufCodecs.BOOL, GreenLanternMenuPayload::pressed,
			GreenLanternMenuPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
