package com.projecthero.mod.network;

import java.util.List;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server &rarr; client: open the Blank Blueprint picker ("changes 21"). One {@link Entry} per mark
 * that has a blueprint, in linear progression order, each flagged {@code unlocked} (the whole previous
 * mark's suit is built) with the {@code prerequisiteSuitId} the player still needs to finish. The pick
 * is re-validated server-side in {@link com.projecthero.mod.network.ModNetworking}.
 */
public record IronManBlueprintPickerPayload(List<Entry> entries) implements CustomPacketPayload {
	public record Entry(String suitId, boolean unlocked, String prerequisiteSuitId) {
	}

	public static final CustomPacketPayload.Type<IronManBlueprintPickerPayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "iron_man_blueprint_picker"));

	public static final StreamCodec<RegistryFriendlyByteBuf, IronManBlueprintPickerPayload> CODEC =
			StreamCodec.ofMember(IronManBlueprintPickerPayload::write, IronManBlueprintPickerPayload::read);

	private static void write(IronManBlueprintPickerPayload payload, RegistryFriendlyByteBuf buf) {
		buf.writeVarInt(payload.entries.size());
		for (Entry e : payload.entries) {
			buf.writeUtf(e.suitId());
			buf.writeBoolean(e.unlocked());
			buf.writeUtf(e.prerequisiteSuitId() == null ? "" : e.prerequisiteSuitId());
		}
	}

	private static IronManBlueprintPickerPayload read(RegistryFriendlyByteBuf buf) {
		int n = buf.readVarInt();
		java.util.ArrayList<Entry> out = new java.util.ArrayList<>(n);
		for (int i = 0; i < n; i++) {
			String suitId = buf.readUtf();
			boolean unlocked = buf.readBoolean();
			String prereq = buf.readUtf();
			out.add(new Entry(suitId, unlocked, prereq.isEmpty() ? null : prereq));
		}
		return new IronManBlueprintPickerPayload(out);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
