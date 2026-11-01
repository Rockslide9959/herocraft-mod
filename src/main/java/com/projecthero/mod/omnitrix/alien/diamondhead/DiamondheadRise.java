package com.projecthero.mod.omnitrix.alien.diamondhead;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.joml.Vector3f;

import com.mojang.math.Transformation;
import com.projecthero.mod.hero.power.TempBlocks;
import com.projecthero.mod.omnitrix.OmnitrixSfx;
import com.projecthero.mod.omnitrix.OmnitrixSounds;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.2 Diamondhead revamp (user: "for diamondheads structures of building or whatever dont have the blocks just appear, the
 * blocks needs to summon out of the ground and raise up, make it look cool please"): every crystal construct grows UP out of the
 * ground. The real block goes in at once (so it is solid straight away) but in its invisible {@code rising} state, while a
 * vanilla block display of the crystal, started under the ground, slides up into its place -- each column of a construct rising
 * whole, the columns staggered from the middle outward, with a grinding crunch and dust at the base. When it arrives the block
 * turns visible and the display goes. Going away is the other way round ({@link PetrosapienCrystalBlock#sink}).
 */
public final class DiamondheadRise {
	/** A column's rise (ticks) and the stagger per block of distance from the construct's middle. */
	public static final int RISE_TICKS = 8;
	public static final float STAGGER = 1.6f;
	/** The tag on the displays (a stray one, left by a crash, is recognisable). */
	public static final String TAG = "projecthero.dh_rise";

	/** The crystal coming up: a stony grinding crunch under a crystal chime. */
	public static final OmnitrixSfx.Cue SND_RISE = OmnitrixSfx.cue("diamondhead_rise")
			.layer(SoundEvents.GRINDSTONE_USE, 0.25f, 0.6f)
			.layer(SoundEvents.CALCITE_PLACE, 0.45f, 0.6f)
			.layer(SoundEvents.AMETHYST_BLOCK_RESONATE, 0.3f, 1.1f, 4)
			.gap(2);
	/** Going back down: a lower grind and a crumble. */
	public static final OmnitrixSfx.Cue SND_SINK = OmnitrixSfx.cue("diamondhead_sink")
			.layer(SoundEvents.GRINDSTONE_USE, 0.2f, 0.5f)
			.layer(SoundEvents.CALCITE_BREAK, 0.35f, 0.7f)
			.gap(3);

	/** One display on its way: up (or down), and what happens when it gets there. */
	private record Move(ServerLevel level, BlockPos pos, UUID display, long go, long arrive, float depth, boolean up) {
	}

	private static final List<Move> MOVES = new ArrayList<>();

	private DiamondheadRise() {
	}

	static void initialize() {
		ServerTickEvents.END_SERVER_TICK.register(s -> tick());
		ServerLifecycleEvents.SERVER_STOPPING.register(s -> stopping());
	}

	/** Forgets every move (ServerStateReset: the server has stopped already, see {@link #stopping}). */
	public static void clearSessionState() {
		MOVES.clear();
	}

	/** The server is stopping: every move ends at once -- the blocks shown, the displays removed (none is saved with the world). */
	private static void stopping() {
		for (Move m : MOVES) {
			finish(m);
		}
		MOVES.clear();
	}

	/** A construct being raised: {@link #place} its cells, then {@link #start}. */
	public static Batch batch(ServerLevel level, int riseTicks) {
		return new Batch(level, riseTicks);
	}

	public static final class Batch {
		private final ServerLevel level;
		private final int rise;
		private final List<BlockPos> cells = new ArrayList<>();

		private Batch(ServerLevel level, int rise) {
			this.level = level;
			this.rise = rise;
		}

		/** Places one (invisible, already solid) crystal for {@code ttl} after it has risen. False if the spot was taken. */
		public boolean place(BlockPos pos, int ttl) {
			int lead = rise + 12;
			if (!TempBlocks.place(level, pos, DiamondheadKit.crystal().setValue(PetrosapienCrystalBlock.RISING, true), ttl + lead)) {
				return false;
			}
			cells.add(pos.immutable());
			return true;
		}

		public int size() {
			return cells.size();
		}

		/** Sends every placed cell up out of the ground: whole columns, the middle first. */
		public void start() {
			if (cells.isEmpty()) {
				return;
			}
			Map<Long, List<BlockPos>> columns = new HashMap<>();
			double cx = 0, cz = 0;
			for (BlockPos c : cells) {
				columns.computeIfAbsent(((long) c.getX() << 32) ^ (c.getZ() & 0xffffffffL), k -> new ArrayList<>()).add(c);
				cx += c.getX();
				cz += c.getZ();
			}
			cx /= cells.size();
			cz /= cells.size();
			long now = level.getGameTime();
			int sounds = 0;
			for (List<BlockPos> col : columns.values()) {
				int bottom = Integer.MAX_VALUE, top = Integer.MIN_VALUE;
				for (BlockPos c : col) {
					bottom = Math.min(bottom, c.getY());
					top = Math.max(top, c.getY());
				}
				BlockPos base = col.get(0).atY(bottom);
				// the whole column starts under the ground and comes up together
				float depth = top - bottom + 1;
				double d = Math.sqrt((base.getX() - cx) * (base.getX() - cx) + (base.getZ() - cz) * (base.getZ() - cz));
				long go = now + 1 + Math.round(d * STAGGER);
				for (BlockPos c : col) {
					Display.BlockDisplay disp = display(level, c, depth);
					if (disp != null) {
						MOVES.add(new Move(level, c, disp.getUUID(), go, go + rise, depth, true));
					}
				}
				if (sounds++ < 4) {
					OmnitrixSounds.play(level, Vec3.atBottomCenterOf(base), SND_RISE, 0.8f, 0.9f + level.random.nextFloat() * 0.25f);
				}
			}
		}
	}

	private static Display.BlockDisplay display(ServerLevel level, BlockPos at, float drop) {
		Display.BlockDisplay d = EntityType.BLOCK_DISPLAY.create(level);
		if (d == null) {
			return null;
		}
		d.setBlockState(DiamondheadKit.crystal());
		d.setTransformationInterpolationDelay(0);
		d.setTransformationInterpolationDuration(0);
		d.setTransformation(new Transformation(new Vector3f(0f, -drop, 0f), null, null, null));
		d.moveTo(at.getX(), at.getY(), at.getZ(), 0f, 0f);
		d.addTag(TAG);
		level.addFreshEntity(d);
		return d;
	}

	/** A crystal going away sinks back into the ground ({@code from} the block it was). */
	static void sink(ServerLevel level, BlockPos pos) {
		Display.BlockDisplay d = display(level, pos, 0f);
		if (d == null) {
			return;
		}
		long now = level.getGameTime();
		MOVES.add(new Move(level, pos.immutable(), d.getUUID(), now + 1, now + 1 + RISE_TICKS, 1.05f, false));
		if (level.random.nextInt(3) == 0) {
			OmnitrixSounds.play(level, Vec3.atBottomCenterOf(pos), SND_SINK, 0.7f, 0.9f + level.random.nextFloat() * 0.2f);
		}
	}

	private static void tick() {
		Iterator<Move> it = MOVES.iterator();
		while (it.hasNext()) {
			Move m = it.next();
			long now = m.level().getGameTime();
			Entity e = m.level().getEntity(m.display());
			if (!(e instanceof Display.BlockDisplay d)) {
				finishBlock(m);
				it.remove();
				continue;
			}
			if (now == m.go()) {
				// off it goes, the client easing it there over the rise
				d.setTransformationInterpolationDelay(0);
				d.setTransformationInterpolationDuration((int) (m.arrive() - m.go()));
				float y = m.up() ? 0f : -m.depth();
				float s = m.up() ? 1f : 0.85f;
				d.setTransformation(new Transformation(new Vector3f((1f - s) / 2f, y, (1f - s) / 2f), null, new Vector3f(s, s, s), null));
				dust(m, 6);
			} else if (now > m.go() && now < m.arrive() && (now - m.go()) % 3 == 0) {
				dust(m, 3);
			}
			if (now == m.arrive()) {
				finishBlock(m);
			}
			if (now >= m.arrive() + 2) {
				// the block is drawn by now: the display can go
				d.discard();
				it.remove();
			}
		}
	}

	/** Dust and crystal chips at the foot of the moving column. */
	private static void dust(Move m, int n) {
		ServerLevel l = m.level();
		BlockPos below = m.pos().below();
		BlockState ground = l.getBlockState(below);
		Vec3 at = Vec3.atBottomCenterOf(m.pos());
		if (!ground.isAir() && !ground.is(DiamondheadBlocks.CRYSTAL)) {
			l.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), at.x, at.y + 0.05, at.z, n, 0.35, 0.02, 0.35, 0.08);
			l.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, DiamondheadKit.crystal()), at.x, at.y + 0.1, at.z, n / 2 + 1, 0.3, 0.05, 0.3, 0.1);
		}
		if (m.up()) {
			l.sendParticles(DiamondheadKit.SPARKLE, at.x, at.y + 0.3, at.z, 1, 0.3, 0.2, 0.3, 0.0);
		}
	}

	/** A rising crystal arrives: shown. */
	private static void finishBlock(Move m) {
		if (!m.up() || !m.level().hasChunkAt(m.pos())) {
			return;
		}
		BlockState st = m.level().getBlockState(m.pos());
		if (st.is(DiamondheadBlocks.CRYSTAL) && st.getValue(PetrosapienCrystalBlock.RISING)) {
			m.level().setBlock(m.pos(), st.setValue(PetrosapienCrystalBlock.RISING, false), Block.UPDATE_CLIENTS);
		}
	}

	private static void finish(Move m) {
		finishBlock(m);
		Entity e = m.level().getEntity(m.display());
		if (e != null) {
			e.discard();
		}
	}
}
