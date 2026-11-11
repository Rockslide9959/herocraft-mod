package com.projecthero.mod.saiyan.kit.network;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * Server to client (v0.17.3): the Spirit Bomb's look away from the caster -- a thrown bomb in flight ({@link #FLY}, re-sent
 * now and then to correct drift), its impact ({@link #IMPACT}: flash, dome, ring, smoke, sound) and a held bomb fizzling out
 * ({@link #FIZZLE}). The held, charging bomb is drawn from the caster's synced {@code SpiritBombState}. Purely cosmetic.
 */
public record SpiritBombFxPayload(int kind, int id, Vec3 pos, Vec3 vel, float radius, float charge) implements CustomPacketPayload {
	public static final int FLY = 0, IMPACT = 1, FIZZLE = 2;

	public static final CustomPacketPayload.Type<SpiritBombFxPayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "saiyan_spirit_bomb_fx"));

	public static final StreamCodec<RegistryFriendlyByteBuf, SpiritBombFxPayload> CODEC = StreamCodec.of(SpiritBombFxPayload::write,
			SpiritBombFxPayload::read);

	private static void write(RegistryFriendlyByteBuf buf, SpiritBombFxPayload p) {
		buf.writeByte(p.kind);
		buf.writeVarInt(p.id);
		vec(buf, p.pos);
		vec(buf, p.vel);
		buf.writeFloat(p.radius);
		buf.writeFloat(p.charge);
	}

	private static SpiritBombFxPayload read(RegistryFriendlyByteBuf buf) {
		return new SpiritBombFxPayload(buf.readByte(), buf.readVarInt(), vec(buf), vec(buf), buf.readFloat(), buf.readFloat());
	}

	private static void vec(RegistryFriendlyByteBuf buf, Vec3 v) {
		buf.writeDouble(v.x);
		buf.writeDouble(v.y);
		buf.writeDouble(v.z);
	}

	private static Vec3 vec(RegistryFriendlyByteBuf buf) {
		return new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
