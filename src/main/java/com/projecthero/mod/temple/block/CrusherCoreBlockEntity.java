package com.projecthero.mod.temple.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * v0.17.7: the Crushing Corridor. The corridor is {@code length} rows along {@code along} (row 0 holds this core in its
 * floor), {@code 2 * half + 1} cells across ({@code across}), {@code height} high above the floor.
 * <ol>
 * <li><b>Idle</b>: a survival player in the first {@link #ENTRY_ROWS} rows starts it (grinding, "the walls move!").</li>
 * <li><b>Closing</b>: every {@link #STEP_TICKS} the walls advance one cell from each side (spiked
 * {@link TempleBlocks#CRUSHER} blocks fill the air of that column for the corridor's whole length; an occupied cell is
 * skipped and its occupant takes {@link #CRUSH_DAMAGE}). After {@code half + 1} steps they meet: about
 * {@code (half + 1) * STEP_TICKS} ticks to get through (10.5 s for the temple's 5-wide, 30-long corridor).</li>
 * <li><b>Hold</b> {@link #HOLD_TICKS}: anyone still inside is crushed ({@link #CRUSH_DAMAGE} every 10 ticks + Slowness).</li>
 * <li><b>Retract</b>: every crusher block goes, {@link #COOLDOWN_TICKS} of rest, idle again.</li>
 * </ol>
 * Only the geometry is saved; leftover crusher blocks are cleared on the first tick after loading.
 */
public class CrusherCoreBlockEntity extends BlockEntity {
	public static final int STEP_TICKS = 70;
	public static final int HOLD_TICKS = 40;
	public static final int COOLDOWN_TICKS = 60;
	public static final int ENTRY_ROWS = 3;
	public static final float CRUSH_DAMAGE = 3.0f;

	public enum Phase {
		IDLE, CLOSING, HOLD, COOLDOWN
	}

	private Direction along = Direction.SOUTH;
	private Direction across = Direction.EAST;
	private int length = 30;
	private int half = 2;
	private int height = 4;

	private Phase phase = Phase.IDLE;
	private int stage;
	private int timer;
	private boolean fresh = true;
	private int idleClock;
	public int runs;

	public CrusherCoreBlockEntity(BlockPos pos, BlockState state) {
		super(TempleBlocks.CRUSHER_CORE_BE, pos, state);
	}

	public void setGeometry(Direction along, Direction across, int length, int half, int height) {
		this.along = along;
		this.across = across;
		this.length = length;
		this.half = half;
		this.height = height;
		setChanged();
	}

	public Phase phase() {
		return phase;
	}

	public int stage() {
		return stage;
	}

	public int stages() {
		return half + 1;
	}

	/** World position of corridor cell (row, column -half..half, level 0..height-1 above the floor). */
	public BlockPos cell(int row, int col, int level) {
		return worldPosition.relative(along, row).relative(across, col).above(1 + level);
	}

	/** The corridor's inside (rows from..to). */
	public AABB box(int fromRow, int toRow) {
		BlockPos a = cell(fromRow, -half, 0);
		BlockPos b = cell(toRow, half, height - 1);
		return new AABB(a).minmax(new AABB(b));
	}

	public static void serverTick(Level level, BlockPos pos, BlockState state, CrusherCoreBlockEntity be) {
		if (level instanceof ServerLevel server) {
			be.tick(server);
		}
	}

	void tick(ServerLevel level) {
		if (fresh) {
			fresh = false;
			retract(level, false);
		}
		switch (phase) {
			case IDLE -> {
				if (++idleClock % 5 == 0 && !intruders(level, box(0, ENTRY_ROWS - 1)).isEmpty()) {
					start(level);
				}
			}
			case CLOSING -> {
				if (--timer <= 0) {
					stage++;
					advance(level, stage);
					if (stage >= stages()) {
						phase = Phase.HOLD;
						timer = HOLD_TICKS;
					} else {
						timer = STEP_TICKS;
					}
				} else if (timer % 20 == 0) {
					level.playSound(null, cell(length / 2, 0, 1), SoundEvents.GRINDSTONE_USE, SoundSource.BLOCKS, 1.6f, 0.4f);
				}
			}
			case HOLD -> {
				if (timer % 10 == 0) {
					for (ServerPlayer p : intruders(level, box(0, length - 1))) {
						crush(level, p);
					}
				}
				if (--timer <= 0) {
					retract(level, true);
					phase = Phase.COOLDOWN;
					timer = COOLDOWN_TICKS;
				}
			}
			case COOLDOWN -> {
				if (--timer <= 0) {
					phase = Phase.IDLE;
				}
			}
		}
	}

	/** Starts the walls moving (no-op unless idle). */
	public void start(ServerLevel level) {
		if (phase != Phase.IDLE) {
			return;
		}
		phase = Phase.CLOSING;
		stage = 0;
		timer = STEP_TICKS;
		runs++;
		level.playSound(null, worldPosition, SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 2.0f, 0.4f);
		level.playSound(null, worldPosition, SoundEvents.WARDEN_EMERGE, SoundSource.BLOCKS, 0.8f, 1.6f);
		Component msg = Component.translatable("block.projecthero.crusher_core.start").withStyle(s -> s.withColor(0xC06040));
		for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, box(0, length - 1).inflate(4))) {
			p.displayClientMessage(msg, true);
		}
	}

	/** The walls' step {@code k} (1..half+1): column ±(half - k + 1) fills. */
	void advance(ServerLevel level, int k) {
		int col = half - k + 1;
		BlockState crusher = TempleBlocks.CRUSHER.defaultBlockState();
		for (int side : col == 0 ? new int[] { 0 } : new int[] { -col, col }) {
			for (int row = 0; row < length; row++) {
				for (int h = 0; h < height; h++) {
					BlockPos p = cell(row, side, h);
					if (!level.getBlockState(p).isAir()) {
						continue;
					}
					AABB space = new AABB(p);
					var inside = level.getEntitiesOfClass(Entity.class, space, e -> e.isAlive() && !e.isSpectator());
					if (!inside.isEmpty()) {
						for (Entity e : inside) {
							if (e instanceof ServerPlayer sp && sp.gameMode.isSurvival()) {
								crush(level, sp);
							}
						}
						continue;
					}
					level.setBlock(p, crusher, 3);
				}
				if (row % 3 == 1) {
					BlockPos p = cell(row, side, 0);
					level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SANDSTONE.defaultBlockState()), p.getX() + 0.5, p.getY() + 1.0,
							p.getZ() + 0.5, 6, 0.3, 0.8, 0.3, 0.05);
				}
			}
		}
		level.playSound(null, cell(length / 2, 0, 1), SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 2.2f, 0.5f);
		level.playSound(null, cell(length / 2, 0, 1), SoundEvents.STONE_PLACE, SoundSource.BLOCKS, 2.2f, 0.4f);
	}

	private void crush(ServerLevel level, ServerPlayer p) {
		p.hurt(level.damageSources().cramming(), CRUSH_DAMAGE);
		p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 1));
	}

	/** Removes every crusher block in the corridor. */
	public int retract(ServerLevel level, boolean loud) {
		int n = 0;
		for (int row = 0; row < length; row++) {
			for (int col = -half; col <= half; col++) {
				for (int h = 0; h < height; h++) {
					BlockPos p = cell(row, col, h);
					if (level.getBlockState(p).is(TempleBlocks.CRUSHER)) {
						level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
						n++;
					}
				}
			}
		}
		if (loud && n > 0) {
			level.playSound(null, cell(length / 2, 0, 1), SoundEvents.PISTON_CONTRACT, SoundSource.BLOCKS, 2.2f, 0.5f);
		}
		return n;
	}

	private static java.util.List<ServerPlayer> intruders(ServerLevel level, AABB box) {
		return level.getEntitiesOfClass(ServerPlayer.class, box, p -> p.isAlive() && p.gameMode.isSurvival());
	}

	/** Test seam: run {@code ticks} ticks now. */
	public void tickFor(ServerLevel level, int ticks) {
		for (int i = 0; i < ticks; i++) {
			tick(level);
		}
	}

	@Override
	protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		tag.putInt("Along", along.get3DDataValue());
		tag.putInt("Across", across.get3DDataValue());
		tag.putInt("Length", length);
		tag.putInt("Half", half);
		tag.putInt("Height", height);
	}

	@Override
	protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		if (tag.contains("Along")) {
			along = Direction.from3DDataValue(tag.getInt("Along"));
			across = Direction.from3DDataValue(tag.getInt("Across"));
			length = Math.max(1, tag.getInt("Length"));
			half = Math.max(0, tag.getInt("Half"));
			height = Math.max(1, tag.getInt("Height"));
		}
	}
}
