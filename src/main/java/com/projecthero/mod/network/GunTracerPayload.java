package com.projecthero.mod.network;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * v0.15.16, server &rarr; nearby clients: one bullet (or shotgun pellet) just flew from the shooter's muzzle to
 * {@code (tx, ty, tz)}. {@code shooter} is the entity id (its client draws the streak from where it sees the gun),
 * {@code kind} the gun (0 pistol, 1 rifle, 2 shotgun, 3 sniper), {@code hit} 0 nothing, 1 a block, 2 a creature.
 * Purely cosmetic -- {@code client.firearm.GunTracers} draws the tracer and the impact flash.
 */
public record GunTracerPayload(int shooter, double fx, double fy, double fz, double tx, double ty, double tz, int kind, int hit)
		implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<GunTracerPayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "gun_tracer"));

	public static final StreamCodec<RegistryFriendlyByteBuf, GunTracerPayload> CODEC = StreamCodec.of(
			(buf, p) -> {
				buf.writeVarInt(p.shooter);
				buf.writeDouble(p.fx);
				buf.writeDouble(p.fy);
				buf.writeDouble(p.fz);
				buf.writeDouble(p.tx);
				buf.writeDouble(p.ty);
				buf.writeDouble(p.tz);
				buf.writeByte(p.kind);
				buf.writeByte(p.hit);
			},
			buf -> new GunTracerPayload(buf.readVarInt(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(),
					buf.readDouble(), buf.readDouble(), buf.readByte(), buf.readByte()));

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
