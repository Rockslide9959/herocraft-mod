package com.herocraft.mod.network;

import java.util.List;

import com.herocraft.mod.HeroCraftMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server &rarr; client: the list of Iron Man suits the player can currently call (spec "changes 9").
 * Sent when the player presses the call-armour key unarmoured; the client opens the selection screen.
 * Each option carries where the suit is ({@link Source}), its live charge / integrity, and how far
 * away it is, so the player can make an informed pick.
 */
public record IronManSuitListPayload(List<Option> options) implements CustomPacketPayload {
	/** 0 = a bound Suit Platform (possibly in an unloaded chunk), 1 = fully in the player's inventory. */
	public record Option(String suitId, int source, float energyFrac, float integrityFrac, int distance) {
	}

	public static final int SOURCE_PLATFORM = 0;
	public static final int SOURCE_INVENTORY = 1;

	public static final CustomPacketPayload.Type<IronManSuitListPayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(HeroCraftMod.MOD_ID, "iron_man_suit_list"));

	public static final StreamCodec<RegistryFriendlyByteBuf, IronManSuitListPayload> CODEC =
			StreamCodec.ofMember(IronManSuitListPayload::write, IronManSuitListPayload::read);

	private static void write(IronManSuitListPayload payload, RegistryFriendlyByteBuf buf) {
		buf.writeVarInt(payload.options.size());
		for (Option o : payload.options) {
			buf.writeUtf(o.suitId());
			buf.writeVarInt(o.source());
			buf.writeFloat(o.energyFrac());
			buf.writeFloat(o.integrityFrac());
			buf.writeVarInt(o.distance());
		}
	}

	private static IronManSuitListPayload read(RegistryFriendlyByteBuf buf) {
		int n = buf.readVarInt();
		java.util.ArrayList<Option> out = new java.util.ArrayList<>(n);
		for (int i = 0; i < n; i++) {
			out.add(new Option(buf.readUtf(), buf.readVarInt(), buf.readFloat(), buf.readFloat(), buf.readVarInt()));
		}
		return new IronManSuitListPayload(out);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
