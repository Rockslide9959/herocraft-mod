package com.projecthero.mod.network;

import java.util.List;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * v0.17.1: server -&gt; nearby clients, how healthy one damaged hard-light block construct (a Hard-Light Wall, a
 * Containment Cage) is, so its blocks can dim, darken and flicker as it fails ({@code GreenLanternConstructHealthClient}).
 * Re-sent every {@code DURABILITY_RESEND_TICKS} while the construct is damaged (players walking up get it too); a
 * {@code health} below 0 means the construct is gone.
 *
 * @param id     the construct's instance id
 * @param health 0..1 of its HP, or -1 = forget it
 * @param cells  its placed blocks
 */
public record GreenLanternConstructHealthPayload(int id, float health, List<BlockPos> cells) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<GreenLanternConstructHealthPayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "green_lantern_construct_health"));

	public static final StreamCodec<RegistryFriendlyByteBuf, GreenLanternConstructHealthPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, GreenLanternConstructHealthPayload::id,
			ByteBufCodecs.FLOAT, GreenLanternConstructHealthPayload::health,
			BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list(512)), GreenLanternConstructHealthPayload::cells,
			GreenLanternConstructHealthPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
