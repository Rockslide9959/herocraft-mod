package com.projecthero.mod.network;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * v0.16.7 (perf, user: "a lot of frame stuttering"): a bundle of {@code ServerLevel.sendParticles} calls sent as ONE
 * {@link ParticleBatchPayload} per viewer instead of one vanilla packet per call. Use it for anything drawn point by
 * point (rings, lines, arena borders, tracers): {@code add(...)} each point exactly as you would have called
 * {@code sendParticles}, then {@link #send}.
 *
 * <p>Viewer ranges match vanilla: an unforced point reaches players within 32 blocks, a forced one within
 * {@code forceRange} (vanilla 512; the event helpers used 128). A player whose client can't take the payload (a game
 * test's mock player) gets the old one-packet-per-point path, so behaviour there is unchanged.
 */
public final class ParticleBatch {
	/** Vanilla's reach for an unforced particle packet. */
	public static final double NORMAL_RANGE = 32.0;
	/** Vanilla's reach for a forced (long-range) one. */
	public static final double FORCE_RANGE = 512.0;
	/** Keep one payload well under the 1 MiB custom-payload cap (~31 bytes an entry). */
	private static final int MAX_PER_PAYLOAD = 4096;

	private final List<ParticleOptions> palette = new ArrayList<>(4);
	private int n;
	private int[] pal = new int[32];
	private boolean[] force = new boolean[32];
	private double[] x = new double[32], y = new double[32], z = new double[32];
	private int[] count = new int[32];
	private float[] dx = new float[32], dy = new float[32], dz = new float[32], speed = new float[32];
	private double[] range = new double[32];

	public boolean isEmpty() {
		return n == 0;
	}

	public int size() {
		return n;
	}

	/** One {@code sendParticles(particle, x, y, z, count, dx, dy, dz, speed)} -- unforced, 32 blocks. */
	public ParticleBatch add(ParticleOptions particle, double px, double py, double pz, int c, double ox, double oy, double oz, double sp) {
		return add(particle, false, NORMAL_RANGE, px, py, pz, c, ox, oy, oz, sp);
	}

	/** One forced {@code sendParticles(viewer, particle, true, ...)} for every viewer within {@code reach} of the point. */
	public ParticleBatch addForced(ParticleOptions particle, double reach, double px, double py, double pz, int c, double ox, double oy,
			double oz, double sp) {
		return add(particle, true, reach, px, py, pz, c, ox, oy, oz, sp);
	}

	public ParticleBatch add(ParticleOptions particle, boolean forced, double reach, double px, double py, double pz, int c, double ox,
			double oy, double oz, double sp) {
		if (n == pal.length) {
			grow();
		}
		int p = palette.indexOf(particle);
		if (p < 0) {
			p = palette.size();
			palette.add(particle);
		}
		pal[n] = p;
		force[n] = forced;
		range[n] = forced ? Math.min(reach, FORCE_RANGE) : Math.min(reach, NORMAL_RANGE);
		x[n] = px;
		y[n] = py;
		z[n] = pz;
		count[n] = c;
		dx[n] = (float) ox;
		dy[n] = (float) oy;
		dz[n] = (float) oz;
		speed[n] = (float) sp;
		n++;
		return this;
	}

	private void grow() {
		int m = pal.length * 2;
		pal = Arrays.copyOf(pal, m);
		force = Arrays.copyOf(force, m);
		x = Arrays.copyOf(x, m);
		y = Arrays.copyOf(y, m);
		z = Arrays.copyOf(z, m);
		count = Arrays.copyOf(count, m);
		dx = Arrays.copyOf(dx, m);
		dy = Arrays.copyOf(dy, m);
		dz = Arrays.copyOf(dz, m);
		speed = Arrays.copyOf(speed, m);
		range = Arrays.copyOf(range, m);
	}

	/** Sends every point to every player of {@code level} it reaches (one payload each). */
	public void send(ServerLevel level) {
		if (n == 0) {
			return;
		}
		for (ServerPlayer p : level.players()) {
			send(level, p);
		}
	}

	/** Sends {@code viewer} the points that reach it. */
	public void send(ServerLevel level, ServerPlayer viewer) {
		if (n == 0 || viewer.level() != level) {
			return;
		}
		double vx = viewer.getX(), vy = viewer.getY(), vz = viewer.getZ();
		int[] idx = new int[n];
		int k = 0;
		for (int i = 0; i < n; i++) {
			double ddx = x[i] - vx, ddy = y[i] - vy, ddz = z[i] - vz;
			if (ddx * ddx + ddy * ddy + ddz * ddz <= range[i] * range[i]) {
				idx[k++] = i;
			}
		}
		if (k == 0) {
			return;
		}
		if (!ServerPlayNetworking.canSend(viewer, ParticleBatchPayload.TYPE)) {
			for (int j = 0; j < k; j++) {
				int i = idx[j];
				level.sendParticles(viewer, palette.get(pal[i]), force[i], x[i], y[i], z[i], count[i], dx[i], dy[i], dz[i], speed[i]);
			}
			return;
		}
		for (int from = 0; from < k; from += MAX_PER_PAYLOAD) {
			int m = Math.min(MAX_PER_PAYLOAD, k - from);
			int first = idx[from];
			double ox = x[first], oy = y[first], oz = z[first];
			int[] ppal = new int[m];
			boolean[] pforce = new boolean[m];
			float[] px = new float[m], py = new float[m], pz = new float[m], pdx = new float[m], pdy = new float[m], pdz = new float[m],
					psp = new float[m];
			int[] pc = new int[m];
			for (int j = 0; j < m; j++) {
				int i = idx[from + j];
				ppal[j] = pal[i];
				pforce[j] = force[i];
				px[j] = (float) (x[i] - ox);
				py[j] = (float) (y[i] - oy);
				pz[j] = (float) (z[i] - oz);
				pc[j] = count[i];
				pdx[j] = dx[i];
				pdy[j] = dy[i];
				pdz[j] = dz[i];
				psp[j] = speed[i];
			}
			ServerPlayNetworking.send(viewer, new ParticleBatchPayload(List.copyOf(palette), ox, oy, oz, ppal, pforce, px, py, pz, pc, pdx,
					pdy, pdz, psp));
		}
	}
}
