package com.projecthero.mod.network;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server &rarr; client: the viewer's own squad roster, refreshed a few times a second while they are in
 * one. This is what the squad screen (P) draws -- teammate positions, health and hero identity all come
 * from here rather than from the client guessing, since a squadmate in another dimension or an unloaded
 * chunk is not a client-side entity at all.
 *
 * <p>An empty {@link #squadName()} means "you are not in a squad"; see {@link #none()}.
 */
public record SquadInfoPayload(String squadName, List<Member> members) implements CustomPacketPayload {
	/**
	 * @param identityKey translation key for the hero identity currently holding their ability slots
	 *                    (see {@code HeroIdentity}), or {@code ""} for none
	 */
	public record Member(UUID id, String name, boolean online, float health, float maxHealth, float absorption,
			int x, int y, int z, String dimension, String identityKey, boolean leader) {
	}

	public static final CustomPacketPayload.Type<SquadInfoPayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "squad_info"));

	public static final StreamCodec<RegistryFriendlyByteBuf, SquadInfoPayload> CODEC =
			StreamCodec.ofMember(SquadInfoPayload::write, SquadInfoPayload::read);

	public static SquadInfoPayload none() {
		return new SquadInfoPayload("", List.of());
	}

	private static void write(SquadInfoPayload payload, RegistryFriendlyByteBuf buf) {
		buf.writeUtf(payload.squadName);
		buf.writeVarInt(payload.members.size());
		for (Member m : payload.members) {
			buf.writeUUID(m.id());
			buf.writeUtf(m.name());
			buf.writeBoolean(m.online());
			buf.writeFloat(m.health());
			buf.writeFloat(m.maxHealth());
			buf.writeFloat(m.absorption());
			buf.writeVarInt(m.x());
			buf.writeVarInt(m.y());
			buf.writeVarInt(m.z());
			buf.writeUtf(m.dimension());
			buf.writeUtf(m.identityKey());
			buf.writeBoolean(m.leader());
		}
	}

	private static SquadInfoPayload read(RegistryFriendlyByteBuf buf) {
		String name = buf.readUtf();
		int n = buf.readVarInt();
		List<Member> out = new ArrayList<>(n);
		for (int i = 0; i < n; i++) {
			out.add(new Member(buf.readUUID(), buf.readUtf(), buf.readBoolean(), buf.readFloat(), buf.readFloat(),
					buf.readFloat(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
					buf.readUtf(), buf.readUtf(), buf.readBoolean()));
		}
		return new SquadInfoPayload(name, out);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
