package com.projecthero.mod.network;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * v0.16.2: client -&gt; server Spider-Man reflex requests -- the Inverted Takedown (left-click while hanging upside down
 * over an unaware mob) and arming the perfect dodge (Jump during a Spider-Sense warning). Edge-triggered and fully
 * re-validated on the server ({@code SpiderTakedown.request}, {@code SpiderPerfectDodge.arm}).
 *
 * @param action {@link #TAKEDOWN} or {@link #DODGE}
 */
public record SpiderReflexPayload(int action) implements CustomPacketPayload {
	public static final int TAKEDOWN = 0;
	public static final int DODGE = 1;

	public static final CustomPacketPayload.Type<SpiderReflexPayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "spider_reflex"));

	public static final StreamCodec<RegistryFriendlyByteBuf, SpiderReflexPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, SpiderReflexPayload::action,
			SpiderReflexPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
