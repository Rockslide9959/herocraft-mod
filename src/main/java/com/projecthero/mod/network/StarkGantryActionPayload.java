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
 * {@link #EQUIP} = the suit racked at {@code pos} was picked; {@link #UNEQUIP} = "Remove Suit". v0.15.9: {@link #SWAP} =
 * "Swap Suit" -- the worn suit comes off and the picked one goes on, in one sequence. For EQUIP / SWAP a non-empty
 * {@code packSuit} means "the suit of that id carried in my pack" instead of the platform at {@code pos}. Everything is
 * re-validated server-side by {@code com.projecthero.mod.ironman.gantry.StarkGantry}.
 */
public record StarkGantryActionPayload(int action, BlockPos pos, String packSuit) implements CustomPacketPayload {
	public static final int OPEN = 0;
	public static final int EQUIP = 1;
	public static final int UNEQUIP = 2;
	/** v0.15.9: swap the worn suit for the picked one. */
	public static final int SWAP = 3;
	/** Longest suit id the server will read (every real id is far shorter). */
	public static final int MAX_SUIT_ID = 64;

	public StarkGantryActionPayload(int action, BlockPos pos) {
		this(action, pos, "");
	}

	public static final CustomPacketPayload.Type<StarkGantryActionPayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "stark_gantry_action"));

	public static final StreamCodec<RegistryFriendlyByteBuf, StarkGantryActionPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, StarkGantryActionPayload::action,
			BlockPos.STREAM_CODEC, StarkGantryActionPayload::pos,
			ByteBufCodecs.stringUtf8(MAX_SUIT_ID), StarkGantryActionPayload::packSuit,
			StarkGantryActionPayload::new);

	/** Run one request (public so the gametests can feed it arbitrary -- also malformed -- payloads). */
	public static void handleServer(net.minecraft.server.level.ServerPlayer player, StarkGantryActionPayload p) {
		String pack = p.packSuit == null ? "" : p.packSuit;
		BlockPos pos = p.pos == null ? BlockPos.ZERO : p.pos;
		switch (p.action) {
			case OPEN -> com.projecthero.mod.ironman.gantry.StarkGantry.openMenu(player);
			case EQUIP -> com.projecthero.mod.ironman.gantry.StarkGantry.beginEquip(player, pos, pack);
			case UNEQUIP -> com.projecthero.mod.ironman.gantry.StarkGantry.beginUnequip(player);
			case SWAP -> com.projecthero.mod.ironman.gantry.StarkGantry.beginSwap(player, pos, pack);
			default -> {
			}
		}
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
