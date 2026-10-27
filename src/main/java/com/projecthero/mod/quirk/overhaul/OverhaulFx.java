package com.projecthero.mod.quirk.overhaul;

import com.projecthero.mod.quirk.fx.QuirkFx;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1 Overhaul revamp: his look, built from the Quirk VFX toolkit ({@link QuirkFx}) -- every effect is one synced
 * effect entity, so every viewer sees the same thing. The spikes are the toolkit's jagged, kinked SPIKE meshes textured
 * from the ground block they rise out of (never plain blocks), a body he takes apart cracks with red lines and then
 * bursts into SHARDS, and whatever he puts back flies back together in white-teal.
 */
public final class OverhaulFx {
	/** The red of a body coming apart. */
	public static final int RED = 0xC8101A;
	public static final int RED_DARK = 0x5A0410;
	/** The white-teal of matter put back together. */
	public static final int MEND = 0x9FF2E0;
	public static final int MEND_DEEP = 0x2FB8A0;

	private OverhaulFx() {
	}

	/** What a spike or shard rising out of the ground at {@code cell} (an open cell over the ground) is made of. */
	public static BlockState groundAt(ServerLevel level, BlockPos cell) {
		BlockState g = level.getBlockState(cell.below());
		return g.isAir() ? Blocks.STONE.defaultBlockState() : g;
	}

	/**
	 * One jagged spike mesh rising out of the ground cell {@code cell}, leaning along {@code lean} (a little: 0 = straight up),
	 * {@code height} blocks tall and {@code base} blocks across its foot.
	 */
	public static void spike(ServerLevel level, BlockPos cell, Vec3 lean, float height, float base, int life) {
		RandomSource r = level.random;
		Vec3 dir = new Vec3(lean.x * 0.35 + (r.nextDouble() - 0.5) * 0.22, 1.0, lean.z * 0.35 + (r.nextDouble() - 0.5) * 0.22);
		Vec3 at = Vec3.atBottomCenterOf(cell).add((r.nextDouble() - 0.5) * 0.3, 0, (r.nextDouble() - 0.5) * 0.3);
		QuirkFx.spike(level, at, dir).block(groundAt(level, cell)).size(height * (0.85f + 0.3f * r.nextFloat())).size2(base).life(life).spawn();
	}

	/** A crack web on the ground at {@code at}. */
	public static void crack(ServerLevel level, Vec3 at, float size, int life) {
		QuirkFx.decal(level, at).style(QuirkFx.DECAL_CRACK).colour(0x2A1A14).size(size).life(life).yaw(level.random.nextFloat() * 360f).spawn();
	}

	/** The ground breaking apart into its own fragments at {@code at} (falling back, or settling when {@code back}). */
	public static void groundShards(ServerLevel level, Vec3 at, BlockState block, float size, int count, int life, boolean back) {
		QuirkFx.Builder b = QuirkFx.shards(level, at).block(block).size(size).count(count).life(life);
		if (back) {
			b.reassemble();
		}
		b.spawn();
	}

	/**
	 * A body he touches cracks: jagged red glowing lines run across it (three short ribbons between points on its body). The
	 * burst ({@link #burst}) follows a few ticks later.
	 */
	public static void bodyCracks(ServerLevel level, LivingEntity e) {
		RandomSource r = level.random;
		double w = e.getBbWidth() * 0.55, h = e.getBbHeight();
		Vec3 base = e.position();
		for (int i = 0; i < 4; i++) {
			Vec3 a = base.add((r.nextDouble() - 0.5) * 2 * w, h * (0.25 + r.nextDouble() * 0.6), (r.nextDouble() - 0.5) * 2 * w);
			Vec3 b = a.add((r.nextDouble() - 0.5) * w * 1.6, (r.nextDouble() - 0.5) * h * 0.45, (r.nextDouble() - 0.5) * w * 1.6);
			QuirkFx.ribbon(level, a, b).style(QuirkFx.RIBBON_LIGHT).colours(0xFF4A50, RED).size(0.05f).life(9).spawn();
		}
	}

	/** A body bursting into red fragments ({@code big}: it came apart -- a kill). */
	public static void burst(ServerLevel level, LivingEntity e, boolean big) {
		Vec3 c = e.position().add(0, e.getBbHeight() * 0.55, 0);
		float s = Mth.clamp(e.getBbHeight() * 0.7f, 0.8f, 3f);
		QuirkFx.shards(level, c).colours(RED, RED_DARK).size(s * (big ? 1.6f : 0.8f)).count(big ? 42 : 14).life(big ? 34 : 18).spawn();
		if (big) {
			QuirkFx.shards(level, c).block(Blocks.NETHER_WART_BLOCK.defaultBlockState()).size(s * 1.2f).count(22).life(30).spawn();
			QuirkFx.ring(level, c).colours(0xFFB0B0, RED).size(s * 1.8f).thickness(0.25f).life(10).flags(QuirkFx.F_VERTICAL)
					.vec(new Vec3(level.random.nextDouble() - 0.5, 0.4, level.random.nextDouble() - 0.5)).spawn();
		}
	}

	/** A body (or a held item, an armour piece) taken apart and put back: it breaks to white-teal fragments that snap back. */
	public static void reassemble(ServerLevel level, Vec3 c, float size) {
		QuirkFx.shards(level, c).colours(0xFFFFFF, MEND).size(size).count(30).life(26).reassemble().spawn();
		QuirkFx.ring(level, c.subtract(0, size * 0.5, 0)).style(QuirkFx.RING_SOFT).colours(0xFFFFFF, MEND_DEEP).size(size * 1.8f).thickness(0.4f)
				.life(18).ground().spawn();
	}

	/** The white-teal ripple of ground reforming behind a wave (Total Disassembly, Restore). */
	public static void reformRipple(ServerLevel level, Vec3 at, float radius, int life) {
		QuirkFx.ring(level, at).style(QuirkFx.RING_SOFT).colours(0xFFFFFF, MEND).size(radius).thickness(0.9f).life(life).ground().spawn();
	}

	/** A small wave of the floor lifting and settling (Ground Shift, Terrain Surf's wake). */
	public static void ripple(ServerLevel level, BlockPos cell, float size) {
		BlockState g = groundAt(level, cell);
		groundShards(level, Vec3.atBottomCenterOf(cell).add(0, 0.15, 0), g, size, 8, 14, false);
	}
}
