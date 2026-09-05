package com.herocraft.mod.network;

import com.herocraft.mod.HeroCraftMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server &rarr; client Spider-Sense warning (v0.6.17). Fired when the sense picks up an incoming
 * threat the player cannot see coming -- an attack about to land, a primed explosion, a hazard
 * underfoot -- so the client can pulse the crosshair and point at the danger. v0.6.19: sent on
 * every scan while the threat persists, so the HUD marker stays lit until the danger is gone, and
 * the client keeps one marker per {@code kind} so several can show at once.
 *
 * @param kind     0 melee, 1 projectile, 2 explosion, 4 environment, 5 something targeting you
 *                 (3 "fall" retired in v0.6.19 -- Spider-Man takes no fall damage now)
 * @param yaw      absolute yaw (degrees) from the player toward the threat; the HUD makes it relative
 * @param vertical 0 = level, 1 = above, 2 = below
 */
public record SpiderSenseWarningPayload(int kind, float yaw, int vertical) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<SpiderSenseWarningPayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(HeroCraftMod.MOD_ID, "spider_sense_warning"));

	public static final StreamCodec<RegistryFriendlyByteBuf, SpiderSenseWarningPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, SpiderSenseWarningPayload::kind,
			ByteBufCodecs.FLOAT, SpiderSenseWarningPayload::yaw,
			ByteBufCodecs.VAR_INT, SpiderSenseWarningPayload::vertical,
			SpiderSenseWarningPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
