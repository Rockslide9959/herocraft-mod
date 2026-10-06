package com.projecthero.mod.network;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * v0.15.4, client &rarr; server: the Stark Gantry. {@link #OPEN} = H pressed on the floor (asks for the menu);
 * {@link #EQUIP} = the suit racked at {@code pos} was picked; {@link #UNEQUIP} = "Remove armour". Everything is
 * re-validated server-side by {@code com.projecthero.mod.ironman.gantry.StarkGantry}.
 */
public record StarkGantryActionPayload(int action, BlockPos pos) implements CustomPacketPayload {
	public static final int OPEN = 0;
	public static final int EQUIP = 1;
	public static final int UNEQUIP = 2;

	public static final CustomPacketPayload.Type<StarkGantryActionPayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "stark_gantry_action"));

	public static final StreamCodec<RegistryFriendlyByteBuf, StarkGantryActionPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, StarkGantryActionPayload::action,
			BlockPos.STREAM_CODEC, StarkGantryActionPayload::pos,
			StarkGantryActionPayload::new);

	public static void handleServer(net.minecraft.server.level.ServerPlayer player, StarkGantryActionPayload p) {
		switch (p.action) {
			case OPEN -> com.projecthero.mod.ironman.gantry.StarkGantry.openMenu(player);
			case EQUIP -> com.projecthero.mod.ironman.gantry.StarkGantry.beginEquip(player, p.pos);
			case UNEQUIP -> com.projecthero.mod.ironman.gantry.StarkGantry.beginUnequip(player);
			default -> {
			}
		}
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
