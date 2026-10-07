package com.projecthero.mod.ultron;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * v0.15.12: server &rarr; nearby clients: one of Ultron's red energy beams, from {@code (ax, ay, az)} to
 * {@code (bx, by, bz)}, drawn for {@code life} ticks (client {@code UltronBeamClient}). Purely cosmetic -- the damage was
 * already dealt (hit-scan) on the server. {@code kind} picks width and brightness: see {@link UltronFx}.
 */
public record UltronBeamPayload(float ax, float ay, float az, float bx, float by, float bz, byte kind, byte life)
		implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<UltronBeamPayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "ultron_beam"));

	public static final StreamCodec<RegistryFriendlyByteBuf, UltronBeamPayload> CODEC = StreamCodec.of((buf, p) -> {
		buf.writeFloat(p.ax);
		buf.writeFloat(p.ay);
		buf.writeFloat(p.az);
		buf.writeFloat(p.bx);
		buf.writeFloat(p.by);
		buf.writeFloat(p.bz);
		buf.writeByte(p.kind);
		buf.writeByte(p.life);
	}, buf -> new UltronBeamPayload(buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(),
			buf.readFloat(), buf.readByte(), buf.readByte()));

	public UltronBeamPayload(Vec3 a, Vec3 b, int kind, int life) {
		this((float) a.x, (float) a.y, (float) a.z, (float) b.x, (float) b.y, (float) b.z, (byte) kind, (byte) life);
	}

	public Vec3 from() {
		return new Vec3(ax, ay, az);
	}

	public Vec3 to() {
		return new Vec3(bx, by, bz);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
