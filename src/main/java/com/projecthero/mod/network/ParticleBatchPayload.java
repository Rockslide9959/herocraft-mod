package com.projecthero.mod.network;

import java.util.ArrayList;
import java.util.List;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * v0.16.7 (perf), server &rarr; client: many vanilla particle "packets" in one. Rings, lines and arena borders used to
 * call {@code ServerLevel.sendParticles} once per point -- one packet per point per viewer, hundreds a second, which is
 * what made the boss / event fights stutter. {@link ParticleBatch} collects those calls and sends each viewer this one
 * payload; the client ({@code client.ParticleBatchClient}) spawns exactly what the separate packets would have
 * (same particle, override flag, count, gaussian offsets and speed), so the effects look the same.
 *
 * <p>{@code palette} holds each distinct particle once; entry {@code i} is {@code palette[pal[i]]} at origin + (x, y, z)
 * (floats relative to {@code ox/oy/oz}), {@code count[i]} times with spread {@code dx/dy/dz} and {@code speed}.
 */
public record ParticleBatchPayload(List<ParticleOptions> palette, double ox, double oy, double oz, int[] pal, boolean[] force, float[] x,
		float[] y, float[] z, int[] count, float[] dx, float[] dy, float[] dz, float[] speed) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<ParticleBatchPayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "particle_batch"));

	public static final StreamCodec<RegistryFriendlyByteBuf, ParticleBatchPayload> CODEC = StreamCodec.of(ParticleBatchPayload::write,
			ParticleBatchPayload::read);

	public int size() {
		return pal.length;
	}

	private static void write(RegistryFriendlyByteBuf buf, ParticleBatchPayload p) {
		buf.writeVarInt(p.palette.size());
		for (ParticleOptions o : p.palette) {
			ParticleTypes.STREAM_CODEC.encode(buf, o);
		}
		buf.writeDouble(p.ox);
		buf.writeDouble(p.oy);
		buf.writeDouble(p.oz);
		int n = p.pal.length;
		buf.writeVarInt(n);
		for (int i = 0; i < n; i++) {
			buf.writeVarInt(p.pal[i]);
			buf.writeBoolean(p.force[i]);
			buf.writeFloat(p.x[i]);
			buf.writeFloat(p.y[i]);
			buf.writeFloat(p.z[i]);
			buf.writeVarInt(p.count[i]);
			buf.writeFloat(p.dx[i]);
			buf.writeFloat(p.dy[i]);
			buf.writeFloat(p.dz[i]);
			buf.writeFloat(p.speed[i]);
		}
	}

	private static ParticleBatchPayload read(RegistryFriendlyByteBuf buf) {
		int pn = buf.readVarInt();
		List<ParticleOptions> palette = new ArrayList<>(pn);
		for (int i = 0; i < pn; i++) {
			palette.add(ParticleTypes.STREAM_CODEC.decode(buf));
		}
		double ox = buf.readDouble();
		double oy = buf.readDouble();
		double oz = buf.readDouble();
		int n = buf.readVarInt();
		int[] pal = new int[n];
		boolean[] force = new boolean[n];
		float[] x = new float[n], y = new float[n], z = new float[n], dx = new float[n], dy = new float[n], dz = new float[n],
				speed = new float[n];
		int[] count = new int[n];
		for (int i = 0; i < n; i++) {
			pal[i] = buf.readVarInt();
			force[i] = buf.readBoolean();
			x[i] = buf.readFloat();
			y[i] = buf.readFloat();
			z[i] = buf.readFloat();
			count[i] = buf.readVarInt();
			dx[i] = buf.readFloat();
			dy[i] = buf.readFloat();
			dz[i] = buf.readFloat();
			speed[i] = buf.readFloat();
		}
		return new ParticleBatchPayload(palette, ox, oy, oz, pal, force, x, y, z, count, dx, dy, dz, speed);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
