package com.projecthero.mod.miraculous.catnoir;

import java.util.List;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server to client, to players near (v0.16.6): a CATACLYSM touch landed at {@code origin}, for the client's VFX.
 * <ul>
 *   <li>On blocks: {@code blocks} are the ones crumbling (each cracks from {@code ring * CATACLYSM_STEP_TICKS}, ring =
 *       its rounded distance from {@code origin}); empty if nothing may crumble there -- the burst still shows.</li>
 *   <li>On a creature: {@code entityId} >= 0 (it darkens and cracks); {@code armourMask} bit i = equipment slot
 *       HEAD/CHEST/LEGS/FEET/BODY (0..4) held armour that shattered into dust.</li>
 * </ul>
 */
public record CataclysmFxPayload(BlockPos origin, List<BlockPos> blocks, int entityId, int armourMask) implements CustomPacketPayload {
	/**
	 * v0.16.7: the most blocks one payload carries. It was 1024 -- the 8-block crumble (up to
	 * {@link CatNoirConfig#CATACLYSM_MAX_BLOCKS}) went over it and the encode failure disconnected the player.
	 */
	public static final int MAX_BLOCKS = 4096;

	public static final CustomPacketPayload.Type<CataclysmFxPayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "cat_noir_cataclysm_fx"));

	public static final StreamCodec<RegistryFriendlyByteBuf, CataclysmFxPayload> CODEC = StreamCodec.composite(
			BlockPos.STREAM_CODEC, CataclysmFxPayload::origin,
			BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_BLOCKS)), CataclysmFxPayload::blocks,
			ByteBufCodecs.VAR_INT, CataclysmFxPayload::entityId,
			ByteBufCodecs.VAR_INT, CataclysmFxPayload::armourMask,
			CataclysmFxPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
