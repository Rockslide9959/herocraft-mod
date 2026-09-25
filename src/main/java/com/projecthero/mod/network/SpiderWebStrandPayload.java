package com.projecthero.mod.network;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server &rarr; client (v0.12.20): draw a web strand from a player's hand to a point (or to a living entity)
 * -- the same line the Web Swing draws -- that stays solid for {@code holdTicks} and then phases out over
 * {@code fadeTicks}. Used by Web Zip and the Combat Mode moves.
 *
 * <p>A strand is keyed by {@code (playerId, slot)}: a newer strand with the same key replaces the old one, and
 * {@code holdTicks == 0 && fadeTicks == 0} simply removes it. When {@code targetEntityId >= 0} the far end
 * follows that entity while it exists (Web-Throw, Web Strike), otherwise it stays at {@code (x, y, z)}.
 */
public record SpiderWebStrandPayload(int playerId, int slot, int targetEntityId, double x, double y, double z,
		int holdTicks, int fadeTicks, boolean rightHand) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<SpiderWebStrandPayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "spider_web_strand"));

	public static final StreamCodec<RegistryFriendlyByteBuf, SpiderWebStrandPayload> CODEC = StreamCodec.of(
			(buf, p) -> {
				buf.writeVarInt(p.playerId);
				buf.writeVarInt(p.slot);
				buf.writeVarInt(p.targetEntityId + 1);
				buf.writeDouble(p.x);
				buf.writeDouble(p.y);
				buf.writeDouble(p.z);
				buf.writeVarInt(p.holdTicks);
				buf.writeVarInt(p.fadeTicks);
				buf.writeBoolean(p.rightHand);
			},
			buf -> new SpiderWebStrandPayload(buf.readVarInt(), buf.readVarInt(), buf.readVarInt() - 1,
					buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readVarInt(), buf.readVarInt(),
					buf.readBoolean()));

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
