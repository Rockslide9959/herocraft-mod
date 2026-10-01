package com.projecthero.mod.ironman.sorter;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * v0.14.16: the Stark Sorter Bot -- a little red-and-gold hovering robot that a {@link SortingStationBlockEntity}
 * launches when Sort is pressed.
 *
 * <h2>The trip loop (server)</h2>
 * {@code SPAWN} (unfolds over the dock) -> {@code FETCH} (reaches into the station; the station moves a load into
 * its own "in transit" list) -> {@code OUTBOUND} (flies a low arc to the container's opening) -> {@code DEPOSIT}
 * (opens the lid, files the load, closes it) -> {@code HOMEBOUND} -> {@code FETCH} ... until the station has
 * nothing left that fits anywhere -> {@code DESPAWN} (folds away, the station reports to the player).
 *
 * <h2>Why it flies through things</h2>
 * Like Steel it extends {@link Entity}, not {@code Mob}: no pathfinding, no AI, no physics. Every leg is a timed
 * arc from where it is to where it is going ({@link #SPEED} blocks per tick, eased), so it always arrives -- a
 * pathfinder could fail on a chest boxed in by other chests, which is exactly how storage rooms are built.
 *
 * <h2>Never the owner of anything</h2>
 * The items it appears to carry belong to the station ({@link SortingStationBlockEntity#carried()}); the bot
 * only shows a synced copy. It is invulnerable, can't be pushed, is never saved (the entity type is
 * {@code noSave}), and vanishes if its station is broken, unloaded, or no longer recognises it.
 */
public class SorterBotEntity extends Entity implements GeoEntity {
	/** Cruise speed in blocks per tick (~8 blocks/s). */
	public static final double SPEED = 0.4;
	public static final int SPAWN_TICKS = 16;
	public static final int PICKUP_TICKS = 5;
	public static final int DEPOSIT_TICKS = 10;
	public static final int DESPAWN_TICKS = 14;

	public static final int ANIM_SPAWN = 0;
	public static final int ANIM_IDLE = 1;
	public static final int ANIM_FLY = 2;
	public static final int ANIM_PICKUP = 3;
	public static final int ANIM_DEPOSIT = 4;
	public static final int ANIM_DESPAWN = 5;

	private static final EntityDataAccessor<Integer> DATA_ANIM =
			SynchedEntityData.defineId(SorterBotEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<ItemStack> DATA_CARRIED =
			SynchedEntityData.defineId(SorterBotEntity.class, EntityDataSerializers.ITEM_STACK);

	private static final RawAnimation SPAWN = RawAnimation.begin().thenPlayAndHold("animation.stark_sorter_bot.spawn");
	private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.stark_sorter_bot.idle");
	private static final RawAnimation FLY = RawAnimation.begin().thenLoop("animation.stark_sorter_bot.fly");
	private static final RawAnimation PICKUP = RawAnimation.begin().thenLoop("animation.stark_sorter_bot.pickup");
	private static final RawAnimation DEPOSIT = RawAnimation.begin().thenLoop("animation.stark_sorter_bot.deposit");
	private static final RawAnimation DESPAWN = RawAnimation.begin().thenPlayAndHold("animation.stark_sorter_bot.despawn");

	private enum Phase { SPAWN, FETCH, OUTBOUND, DEPOSIT, HOMEBOUND, DESPAWN }

	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

	private BlockPos station;
	private Phase phase = Phase.SPAWN;
	private int phaseTick;
	private SortPlan.Target target;
	private boolean barrelOpenedByUs;

	private Vec3 legFrom = Vec3.ZERO;
	private Vec3 legTo = Vec3.ZERO;
	private int legTicks;
	private int legTick;
	private double legArc;

	// client-side smoothing: a plain Entity snaps to each position packet; easing toward it in tick() (after
	// xo/yo/zo are captured) lets the renderer interpolate the flight instead of stepping at 20 Hz
	private int lerpSteps;
	private double lerpX;
	private double lerpY;
	private double lerpZ;
	private float lerpYRot;

	public SorterBotEntity(EntityType<? extends SorterBotEntity> type, Level level) {
		super(type, level);
		this.noPhysics = true;
		this.setNoGravity(true);
	}

	/** Launch a bot over {@code be}'s dock. */
	public static SorterBotEntity spawn(ServerLevel level, SortingStationBlockEntity be) {
		SorterBotEntity bot = StarkSorter.BOT.create(level);
		if (bot == null) {
			return null;
		}
		Vec3 dock = be.dockPoint();
		bot.station = be.getBlockPos().immutable();
		bot.moveTo(dock.x, dock.y, dock.z, 180f, 0f);
		bot.setAnim(ANIM_SPAWN);
		level.addFreshEntity(bot);
		return bot;
	}

	// ------------------------------------------------------------------ synced state

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(DATA_ANIM, ANIM_SPAWN);
		builder.define(DATA_CARRIED, ItemStack.EMPTY);
	}

	public int anim() {
		return entityData.get(DATA_ANIM);
	}

	private void setAnim(int anim) {
		if (entityData.get(DATA_ANIM) != anim) {
			entityData.set(DATA_ANIM, anim);
		}
	}

	/** What the bot visibly holds (a copy of the station's first in-transit stack). */
	public ItemStack shownCargo() {
		return entityData.get(DATA_CARRIED);
	}

	private void showCargo(ItemStack stack) {
		entityData.set(DATA_CARRIED, stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1));
	}

	// ------------------------------------------------------------------ tick

	@Override
	public void tick() {
		super.tick();
		setDeltaMovement(Vec3.ZERO);
		if (level().isClientSide()) {
			if (lerpSteps > 0) {
				double f = 1.0 / lerpSteps;
				setPos(getX() + (lerpX - getX()) * f, getY() + (lerpY - getY()) * f, getZ() + (lerpZ - getZ()) * f);
				setYRot(getYRot() + Mth.wrapDegrees(lerpYRot - getYRot()) * (float) f);
				lerpSteps--;
			}
			int a = anim();
			if (a != ANIM_SPAWN && a != ANIM_DESPAWN && tickCount % 2 == 0) {
				level().addParticle(ParticleTypes.SOUL_FIRE_FLAME, getX() + (random.nextDouble() - 0.5) * 0.12,
						getY() + 0.04, getZ() + (random.nextDouble() - 0.5) * 0.12, 0, -0.04, 0);
			}
			return;
		}
		ServerLevel server = (ServerLevel) level();
		SortingStationBlockEntity be = station == null || !server.hasChunkAt(station) ? null
				: server.getBlockEntity(station) instanceof SortingStationBlockEntity s ? s : null;
		if (be == null || !be.isRunning() || !getUUID().equals(be.botId())) {
			abort();
			return;
		}
		phaseTick++;
		switch (phase) {
			case SPAWN -> {
				setAnim(ANIM_SPAWN);
				if (phaseTick >= SPAWN_TICKS) {
					enter(Phase.FETCH);
				}
			}
			case FETCH -> tickFetch(server, be);
			case OUTBOUND -> {
				if (advanceLeg()) {
					enter(Phase.DEPOSIT);
				}
			}
			case DEPOSIT -> tickDeposit(server, be);
			case HOMEBOUND -> {
				if (advanceLeg()) {
					be.returnCarried(); // whatever a full chest refused
					showCargo(ItemStack.EMPTY);
					enter(Phase.FETCH);
				}
			}
			case DESPAWN -> {
				setAnim(ANIM_DESPAWN);
				if (phaseTick >= DESPAWN_TICKS) {
					poof(server);
					be.finish();
					discard();
				}
			}
		}
	}

	private void enter(Phase next) {
		phase = next;
		phaseTick = 0;
	}

	private void tickFetch(ServerLevel server, SortingStationBlockEntity be) {
		if (phaseTick == 1) {
			SortingStationBlockEntity.Load load = be.takeLoad();
			if (load == null) {
				enter(Phase.DESPAWN);
				return;
			}
			target = load.target();
			setAnim(ANIM_PICKUP);
			faceTowards(Vec3.atCenterOf(be.getBlockPos()));
			server.playSound(null, getX(), getY(), getZ(), SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.35f, 1.6f);
			return;
		}
		if (phaseTick == 3) {
			showCargo(be.firstCarried());
		}
		if (phaseTick >= PICKUP_TICKS && target != null) {
			startLeg(target.approach());
			enter(Phase.OUTBOUND);
		}
	}

	private void tickDeposit(ServerLevel server, SortingStationBlockEntity be) {
		if (phaseTick == 1) {
			setAnim(ANIM_DEPOSIT);
			faceTowards(target.center());
			openLid(server);
		} else if (phaseTick == 5) {
			be.depositCarried(target);
			showCargo(be.firstCarried());
			Vec3 c = target.center();
			server.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y + 0.4, c.z, 6, 0.25, 0.1, 0.25, 0.05);
			server.playSound(null, c.x, c.y, c.z, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 0.5f, 1.3f);
		} else if (phaseTick == DEPOSIT_TICKS - 1) {
			closeLid(server);
		} else if (phaseTick >= DEPOSIT_TICKS) {
			startLeg(be.dockPoint());
			enter(Phase.HOMEBOUND);
		}
	}

	// ------------------------------------------------------------------ flying

	private void startLeg(Vec3 to) {
		legFrom = position();
		legTo = to;
		double dist = legFrom.distanceTo(legTo);
		legTicks = Math.max(4, Mth.ceil(dist / SPEED));
		legTick = 0;
		legArc = Math.min(1.2, 0.2 + dist * 0.1);
		setAnim(ANIM_FLY);
		faceTowards(to);
	}

	/** One tick along the current arc. True once it has arrived. */
	private boolean advanceLeg() {
		legTick = Math.min(legTicks, legTick + 1);
		double t = legTick / (double) legTicks;
		double e = t * t * (3 - 2 * t);
		Vec3 p = legFrom.lerp(legTo, e).add(0, legArc * 4 * e * (1 - e), 0);
		setPos(p.x, p.y, p.z);
		return legTick >= legTicks;
	}

	private void faceTowards(Vec3 point) {
		double dx = point.x - getX();
		double dz = point.z - getZ();
		if (dx * dx + dz * dz < 1.0e-4) {
			return;
		}
		setYRot((float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90f);
	}

	// ------------------------------------------------------------------ lids

	private void openLid(ServerLevel server) {
		BlockPos pos = target.key();
		BlockState state = server.getBlockState(pos);
		if (state.getBlock() instanceof ChestBlock) {
			int openers = ChestBlockEntity.getOpenCount(server, pos);
			server.blockEvent(pos, state.getBlock(), 1, openers + 1);
			if (openers == 0) {
				server.playSound(null, pos, SoundEvents.CHEST_OPEN, SoundSource.BLOCKS, 0.4f, 1.15f);
			}
		} else if (state.getBlock() instanceof BarrelBlock && !state.getValue(BarrelBlock.OPEN)) {
			server.setBlock(pos, state.setValue(BarrelBlock.OPEN, true), 3);
			server.playSound(null, pos, SoundEvents.BARREL_OPEN, SoundSource.BLOCKS, 0.4f, 1.15f);
			barrelOpenedByUs = true;
		}
	}

	private void closeLid(ServerLevel server) {
		BlockPos pos = target.key();
		BlockState state = server.getBlockState(pos);
		if (state.getBlock() instanceof ChestBlock) {
			int openers = ChestBlockEntity.getOpenCount(server, pos);
			server.blockEvent(pos, state.getBlock(), 1, openers);
			if (openers == 0) {
				server.playSound(null, pos, SoundEvents.CHEST_CLOSE, SoundSource.BLOCKS, 0.4f, 1.15f);
			}
		} else if (barrelOpenedByUs && state.getBlock() instanceof BarrelBlock && state.getValue(BarrelBlock.OPEN)) {
			server.setBlock(pos, state.setValue(BarrelBlock.OPEN, false), 3);
			server.playSound(null, pos, SoundEvents.BARREL_CLOSE, SoundSource.BLOCKS, 0.4f, 1.15f);
		}
		barrelOpenedByUs = false;
	}

	// ------------------------------------------------------------------ leaving

	/** Vanish on the spot (station broken / unloaded / job cancelled). Never touches items. */
	public void abort() {
		if (isRemoved()) {
			return;
		}
		if (level() instanceof ServerLevel server) {
			if (phase == Phase.DEPOSIT && target != null) {
				closeLid(server);
			}
			poof(server);
		}
		discard();
	}

	private void poof(ServerLevel server) {
		server.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 0.45, getZ(), 18, 0.25, 0.3, 0.25, 0.12);
		server.sendParticles(ParticleTypes.END_ROD, getX(), getY() + 0.45, getZ(), 6, 0.2, 0.2, 0.2, 0.03);
		server.playSound(null, getX(), getY(), getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.NEUTRAL, 0.35f, 2.0f);
	}

	// ------------------------------------------------------------------ entity plumbing

	@Override
	protected void readAdditionalSaveData(CompoundTag tag) {
		// never saved (noSave entity type); a reloaded station recovers anything that was in transit
	}

	@Override
	protected void addAdditionalSaveData(CompoundTag tag) {
	}

	@Override
	public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
		lerpX = x;
		lerpY = y;
		lerpZ = z;
		lerpYRot = yRot;
		lerpSteps = Math.max(1, Math.min(steps, 3));
	}

	@Override
	public double lerpTargetX() {
		return lerpSteps > 0 ? lerpX : getX();
	}

	@Override
	public double lerpTargetY() {
		return lerpSteps > 0 ? lerpY : getY();
	}

	@Override
	public double lerpTargetZ() {
		return lerpSteps > 0 ? lerpZ : getZ();
	}

	@Override
	public float lerpTargetYRot() {
		return lerpSteps > 0 ? lerpYRot : getYRot();
	}

	@Override
	public boolean isPickable() {
		return false;
	}

	@Override
	public boolean isInvulnerableTo(DamageSource source) {
		return true;
	}

	@Override
	public boolean hurt(DamageSource source, float amount) {
		return false;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean canBeCollidedWith() {
		return false;
	}

	@Override
	public boolean isPushedByFluid() {
		return false;
	}

	@Override
	public boolean fireImmune() {
		return true;
	}

	@Override
	public boolean displayFireAnimation() {
		return false;
	}

	@Override
	public boolean shouldBeSaved() {
		return false;
	}

	@Override
	public boolean canChangeDimensions(Level from, Level to) {
		return false;
	}

	// ------------------------------------------------------------------ GeckoLib

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<>(this, "main", 3, state -> state.setAndContinue(switch (anim()) {
			case ANIM_SPAWN -> SPAWN;
			case ANIM_FLY -> FLY;
			case ANIM_PICKUP -> PICKUP;
			case ANIM_DEPOSIT -> DEPOSIT;
			case ANIM_DESPAWN -> DESPAWN;
			default -> IDLE;
		})));
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return cache;
	}
}
