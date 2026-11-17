package com.projecthero.mod.client.sentry;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.sentry.VoidMatter;
import com.projecthero.mod.sentry.network.VoidDecayPayload;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * v0.17.5 Void takeover rework 2 (user: "make it so that the blocks slowly fade to black before dissolving quickly"): every block the
 * server starts decaying ({@link VoidDecayPayload}, one batch a tick) gets a black shell drawn over it whose opacity rises over the fade
 * time (smoothstep, to 96%), so the real block slowly goes black; when the server turns it to air the shell stays fully black and
 * shrinks to nothing in {@link #DISSOLVE_TICKS} while a small burst of ink and smoke puffs out.
 *
 * <p>Built for thousands at once: positions live in one primitive-keyed map; only the faces open to air (checked every few ticks, not
 * every frame) are drawn, as plain position-colour quads into one translucent buffer; shells farther than {@link #RENDER_RANGE} are
 * skipped and at most {@link #MAX_DRAWN} are drawn a frame; at most {@link #MAX_BURSTS} dissolve bursts a tick spawn particles.
 * Cleared on disconnect and on a world change.
 */
public final class VoidDecayClient {
	/** The dissolve after the block is gone: the black shell shrinks away in this many ticks. */
	public static final int DISSOLVE_TICKS = 6;
	private static final double RENDER_RANGE = 160.0;
	private static final int MAX_DRAWN = 12000;
	private static final int MAX_BURSTS = 48;
	private static final float MAX_ALPHA = 0.96f;

	private static final class Decay {
		final long pos;
		final long start;
		final long end;
		long dissolveAt;
		/** Bit per {@link Direction} ordinal: that face is open (drawn). */
		int faces = 0x3F;

		Decay(long pos, long start, long end) {
			this.pos = pos;
			this.start = start;
			this.end = end;
		}
	}

	private static final Long2ObjectOpenHashMap<Decay> DECAYS = new Long2ObjectOpenHashMap<>();
	private static ClientLevel lastLevel;

	private VoidDecayClient() {
	}

	public static void reset() {
		DECAYS.clear();
		DECAYS.trim();
		lastLevel = null;
	}

	/** How many shells are live (the harness). */
	public static int count() {
		return DECAYS.size();
	}

	public static void receive(VoidDecayPayload p) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) {
			return;
		}
		if (mc.level != lastLevel) {
			reset();
			lastLevel = mc.level;
		}
		long now = mc.level.getGameTime();
		for (long pos : p.positions()) {
			DECAYS.putIfAbsent(pos, new Decay(pos, now, now + VoidMatter.decayTicks(pos, p.ticks())));
		}
	}

	public static void tick(Minecraft mc) {
		if (mc.level == null) {
			if (!DECAYS.isEmpty()) {
				reset();
			}
			return;
		}
		if (mc.level != lastLevel) {
			reset();
			lastLevel = mc.level;
			return;
		}
		if (DECAYS.isEmpty()) {
			return;
		}
		ClientLevel level = mc.level;
		long now = level.getGameTime();
		int bursts = 0;
		boolean faceCheck = now % 4 == 0;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		BlockPos.MutableBlockPos side = new BlockPos.MutableBlockPos();
		Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
		for (ObjectIterator<Long2ObjectMap.Entry<Decay>> it = DECAYS.long2ObjectEntrySet().fastIterator(); it.hasNext();) {
			Decay d = it.next().getValue();
			if (d.dissolveAt != 0L) {
				if (now - d.dissolveAt > DISSOLVE_TICKS) {
					it.remove();
				}
				continue;
			}
			pos.set(d.pos);
			BlockState st = level.getBlockState(pos);
			if (st.isAir()) {
				// gone on the server: the quick dissolve
				d.dissolveAt = now;
				d.faces = 0x3F;
				if (bursts < MAX_BURSTS && cam.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) < 64.0 * 64.0) {
					bursts++;
					burst(level, pos);
				}
				continue;
			}
			if (now > d.end + 100) {
				it.remove(); // never went (restored, or the chunk unloaded on the server): let the shell go
				continue;
			}
			if (faceCheck) {
				int mask = 0;
				for (Direction dir : Direction.values()) {
					side.setWithOffset(pos, dir);
					long sk = side.asLong();
					BlockState n = level.getBlockState(side);
					if (!n.isSolidRender(level, side) || DECAYS.containsKey(sk)) {
						mask |= 1 << dir.ordinal();
					}
				}
				d.faces = mask;
			}
		}
	}

	private static void burst(ClientLevel level, BlockPos pos) {
		var r = level.random;
		for (int i = 0; i < 3; i++) {
			level.addParticle(ParticleTypes.SQUID_INK, pos.getX() + r.nextDouble(), pos.getY() + r.nextDouble(), pos.getZ() + r.nextDouble(),
					(r.nextDouble() - 0.5) * 0.08, 0.04 + r.nextDouble() * 0.06, (r.nextDouble() - 0.5) * 0.08);
		}
		if (r.nextInt(2) == 0) {
			level.addParticle(ParticleTypes.LARGE_SMOKE, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 0.0, 0.05, 0.0);
		}
	}

	public static void render(WorldRenderContext context) {
		if (DECAYS.isEmpty()) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		ClientLevel level = mc.level;
		PoseStack pose = context.matrixStack();
		MultiBufferSource buffers = context.consumers();
		if (level == null || pose == null || buffers == null || level != lastLevel) {
			return;
		}
		float partial = context.tickCounter().getGameTimeDeltaPartialTick(false);
		float now = level.getGameTime() + partial;
		Vec3 cam = context.camera().getPosition();
		double range2 = RENDER_RANGE * RENDER_RANGE;
		pose.pushPose();
		pose.translate(-cam.x, -cam.y, -cam.z);
		Matrix4f m = pose.last().pose();
		VertexConsumer vc = buffers.getBuffer(RenderType.debugQuads());
		int drawn = 0;
		for (Decay d : DECAYS.values()) {
			if (drawn >= MAX_DRAWN) {
				break;
			}
			int x = BlockPos.getX(d.pos);
			int y = BlockPos.getY(d.pos);
			int z = BlockPos.getZ(d.pos);
			double dx = x + 0.5 - cam.x;
			double dy = y + 0.5 - cam.y;
			double dz = z + 0.5 - cam.z;
			if (dx * dx + dy * dy + dz * dz > range2 || d.faces == 0) {
				continue;
			}
			float alpha;
			float half;
			if (d.dissolveAt != 0L) {
				float k = Math.min(1f, Math.max(0f, (now - d.dissolveAt) / DISSOLVE_TICKS));
				alpha = MAX_ALPHA;
				half = 0.502f * (1f - k * k);
				if (half <= 0.01f) {
					continue;
				}
			} else {
				float k = Math.min(1f, Math.max(0f, (now - d.start) / Math.max(1f, d.end - d.start)));
				alpha = MAX_ALPHA * k * k * (3f - 2f * k);
				half = 0.502f;
				if (alpha < 0.02f) {
					continue;
				}
			}
			int a = (int) (alpha * 255f);
			cube(vc, m, x + 0.5f, y + 0.5f, z + 0.5f, half, d.dissolveAt != 0L ? 0x3F : d.faces, a);
			drawn++;
		}
		pose.popPose();
	}

	/** A black box (only the open faces), centre c, half size h; a faint violet in the black. */
	private static void cube(VertexConsumer vc, Matrix4f m, float cx, float cy, float cz, float h, int faces, int a) {
		float x0 = cx - h, x1 = cx + h, y0 = cy - h, y1 = cy + h, z0 = cz - h, z1 = cz + h;
		int r = 6, g = 2, b = 10;
		if ((faces & (1 << Direction.DOWN.ordinal())) != 0) {
			quad(vc, m, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, r, g, b, a);
		}
		if ((faces & (1 << Direction.UP.ordinal())) != 0) {
			quad(vc, m, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, r, g, b, a);
		}
		if ((faces & (1 << Direction.NORTH.ordinal())) != 0) {
			quad(vc, m, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0, r, g, b, a);
		}
		if ((faces & (1 << Direction.SOUTH.ordinal())) != 0) {
			quad(vc, m, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, r, g, b, a);
		}
		if ((faces & (1 << Direction.WEST.ordinal())) != 0) {
			quad(vc, m, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, r, g, b, a);
		}
		if ((faces & (1 << Direction.EAST.ordinal())) != 0) {
			quad(vc, m, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1, r, g, b, a);
		}
	}

	private static void quad(VertexConsumer vc, Matrix4f m, float ax, float ay, float az, float bx, float by, float bz, float cx, float cy,
			float cz, float dx, float dy, float dz, int r, int g, int b, int a) {
		vc.addVertex(m, ax, ay, az).setColor(r, g, b, a);
		vc.addVertex(m, bx, by, bz).setColor(r, g, b, a);
		vc.addVertex(m, cx, cy, cz).setColor(r, g, b, a);
		vc.addVertex(m, dx, dy, dz).setColor(r, g, b, a);
	}
}
