package com.projecthero.mod.miraculous.hawkmoth.network;

import java.util.List;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server to client, to EVERY player in the dimension (v0.16.6): which creatures carry an akuma right now -- {@code kinds}
 * 1 = akumatized (purple outline, butterfly mask), 2 = a Champion (deeper purple, mask, aura). The full list each time;
 * the client replaces its own. The akuma's outline is meant to be seen by everyone (unlike the private senses).
 */
public record HawkMothMarksPayload(List<Integer> ids, List<Integer> kinds) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<HawkMothMarksPayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "hawk_moth_marks"));

	public static final StreamCodec<RegistryFriendlyByteBuf, HawkMothMarksPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(256)), HawkMothMarksPayload::ids,
			ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(256)), HawkMothMarksPayload::kinds,
			HawkMothMarksPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
