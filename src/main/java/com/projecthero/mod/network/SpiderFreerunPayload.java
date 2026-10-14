package com.projecthero.mod.network;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * v0.16.2: client -&gt; server requests for Spider-Man's freerunning -- the Wall Run starting / ending and the swing
 * tricks. Its own payload (not more {@link SpiderActionPayload} ordinals) so it never collides with other moves being
 * added there. Every request is re-checked by {@code SpiderWallRun} / {@code SpiderTricks}; the movement itself is
 * client-simulated like the rest of his traversal, the server owns the synced pose, the trick count and the cost.
 *
 * @param action one of {@link #WALL_RUN_START}, {@link #WALL_RUN_STOP}, {@link #TRICK}
 * @param arg    the wall face ({@code Direction#get3DDataValue}) for a start, the trick kind for a trick
 */
public record SpiderFreerunPayload(int action, int arg) implements CustomPacketPayload {
	public static final int WALL_RUN_START = 0;
	public static final int WALL_RUN_STOP = 1;
	public static final int TRICK = 2;

	public static final CustomPacketPayload.Type<SpiderFreerunPayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "spider_freerun"));

	public static final StreamCodec<RegistryFriendlyByteBuf, SpiderFreerunPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, SpiderFreerunPayload::action,
			ByteBufCodecs.VAR_INT, SpiderFreerunPayload::arg,
			SpiderFreerunPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
