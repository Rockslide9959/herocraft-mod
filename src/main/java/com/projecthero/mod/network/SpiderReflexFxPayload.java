package com.projecthero.mod.network;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * v0.16.2: server -&gt; the Spider-Man's own client: the Inverted Takedown target under him (for the HUD prompt and the
 * left-click), and the perfect-dodge cues (the warning, the dodge itself, the counter landing).
 *
 * @param kind  {@link #TAKEDOWN_TARGET} (value = entity id, -1 none), {@link #DODGE_WARNING} (value = ticks the warning
 *              stays open, yaw = toward the threat), {@link #DODGE_PERFECT} (value = counter window ticks),
 *              {@link #COUNTER_LANDED}
 * @param value see {@code kind}
 * @param yaw   absolute yaw toward the threat (warning only)
 */
public record SpiderReflexFxPayload(int kind, int value, float yaw) implements CustomPacketPayload {
	public static final int TAKEDOWN_TARGET = 0;
	public static final int DODGE_WARNING = 1;
	public static final int DODGE_PERFECT = 2;
	public static final int COUNTER_LANDED = 3;

	public static final CustomPacketPayload.Type<SpiderReflexFxPayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "spider_reflex_fx"));

	public static final StreamCodec<RegistryFriendlyByteBuf, SpiderReflexFxPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, SpiderReflexFxPayload::kind,
			ByteBufCodecs.VAR_INT, SpiderReflexFxPayload::value,
			ByteBufCodecs.FLOAT, SpiderReflexFxPayload::yaw,
			SpiderReflexFxPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
