package com.projecthero.mod.omnitrix.entity;

import java.util.ArrayList;
import java.util.List;

import org.joml.Vector3f;

import com.projecthero.mod.omnitrix.OmnitrixConfig;
import com.projecthero.mod.omnitrix.OmnitrixEntities;
import com.projecthero.mod.omnitrix.OmnitrixSounds;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1: the Omnitrix's pod coming down, built on the Carnage meteor ({@code CrimsonMeteorEntity}): a slanted fall, the
 * batched long-range trail (forced particles, one payload per viewer every other tick) and a shock on impact -- in green and
 * white, with the {@link OmnitrixPodEntity} left at the bottom of its crater.
 *
 * <p>v0.17.1 (user: "falls from much higher up ... a bigger explosion and a much larger crater"): it starts
 * {@link OmnitrixConfig#METEOR_FALL_HEIGHT} blocks up (closer in when that chunk is not ticking) and takes
 * {@link OmnitrixConfig#METEOR_FALL_TICKS}; the impact throws a triple blast, a smoke column, flying debris and a screen shake
 * out to 64 blocks, and the crater ({@link #carve}) is a real bowl {@link OmnitrixConfig#CRATER_RADIUS} wide and
 * {@link OmnitrixConfig#CRATER_DEPTH} deep with a raised, scorched rim of ejecta: carved centre-out,
 * {@link OmnitrixConfig#CRATER_COLUMNS_PER_TICK} columns a tick (the meteor lingers, unseen, until it is done -- no static
 * state, and a save mid-carve resumes). Saved like any entity, so a meteor caught mid-fall keeps falling when its chunk loads.
 */
public class OmnitrixMeteorEntity extends Entity {
	private static final DustParticleOptions GREEN = new DustParticleOptions(new Vector3f(0.3f, 1.0f, 0.25f), 2.4f);
	private static final DustParticleOptions WHITE = new DustParticleOptions(new Vector3f(0.9f, 1.0f, 0.9f), 1.4f);
	/** v0.17.1: the impact has happened -- the entity is now only the crater's carver (not drawn). */
	private static final EntityDataAccessor<Boolean> LANDED = SynchedEntityData.defineId(OmnitrixMeteorEntity.class, EntityDataSerializers.BOOLEAN);
	public static final int CRATER_RADIUS = OmnitrixConfig.CRATER_RADIUS;
	/** The rim of ejecta reaches this far past the bowl's edge; the scorching a little further. */
	private static final double RIM = 2.6, SCORCH = 5.0;
	/** Flying debris blocks thrown out by the blast. */
	private static final int DEBRIS = 14;

	/** The struck block (the crater's centre) once landed. */
	private BlockPos struck;
	/** The next crater column to carve (index into {@link #columns}). */
	private int carved;
	private int debrisLeft = DEBRIS;
	private boolean podPlaced;

	public OmnitrixMeteorEntity(EntityType<? extends OmnitrixMeteorEntity> type, Level level) {
		super(type, level);
		this.noPhysics = true;
		setNoGravity(true);
	}

	/** Launches a meteor that lands at {@code impact} {@link OmnitrixConfig#METEOR_FALL_TICKS} from now. */
	public static OmnitrixMeteorEntity launch(ServerLevel level, Vec3 impact) {
		OmnitrixMeteorEntity m = OmnitrixEntities.OMNITRIX_METEOR.create(level);
		if (m == null) {
			return null;
		}
		double a = level.random.nextDouble() * Math.PI * 2;
		Vec3 dir = new Vec3(Math.cos(a) * 0.45, 1.0, Math.sin(a) * 0.45);
		// v0.17.1: as high as it can start while its chunk still ticks (it would hang frozen in an unticked one)
		double h = OmnitrixConfig.METEOR_FALL_HEIGHT;
		Vec3 start = impact.add(dir.scale(h));
		while (h > 40 && !level.isPositionEntityTicking(BlockPos.containing(start.x, impact.y, start.z))) {
			h -= 20;
			start = impact.add(dir.scale(h));
		}
		if (!level.isPositionEntityTicking(BlockPos.containing(start.x, impact.y, start.z))) {
			// nothing to the side ticks: straight down the impact's own column, from the full height
			h = OmnitrixConfig.METEOR_FALL_HEIGHT;
			start = impact.add(0, h, 0);
		}
		int ticks = Math.max(30, (int) Math.round(OmnitrixConfig.METEOR_FALL_TICKS * h / OmnitrixConfig.METEOR_FALL_HEIGHT));
		m.moveTo(start.x, start.y, start.z, 0f, 0f);
		m.setDeltaMovement(impact.subtract(start).scale(1.0 / ticks));
		level.addFreshEntity(m);
		OmnitrixSounds.play(level, impact, OmnitrixSounds.METEOR, 4.0f, 1.0f, SoundSource.HOSTILE);
		level.playSound(null, impact.x, impact.y, impact.z, SoundEvents.FIRECHARGE_USE, SoundSource.HOSTILE, 3.0f, 0.5f);
		return m;
	}

	public boolean landed() {
		return entityData.get(LANDED);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(LANDED, false);
	}

	@Override
	public void tick() {
		super.tick();
		if (landed()) {
			if (level() instanceof ServerLevel server) {
				carveStep(server);
			}
			return;
		}
		Vec3 v = getDeltaMovement();
		Vec3 from = position();
		Vec3 to = from.add(v);
		if (level() instanceof ServerLevel server) {
			BlockHitResult hit = server.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, this));
			if (hit.getType() != HitResult.Type.MISS) {
				impact(server, hit.getLocation(), hit.getBlockPos());
				return;
			}
			if (tickCount % 2 == 0) {
				com.projecthero.mod.network.ParticleBatch batch = new com.projecthero.mod.network.ParticleBatch();
				for (Vec3 at : new Vec3[] { from.subtract(v), from.subtract(v.scale(0.5)), from }) {
					batch.addForced(ParticleTypes.FLAME, 320, at.x, at.y, at.z, 6, 0.5, 0.5, 0.5, 0.01);
					batch.addForced(GREEN, 320, at.x, at.y, at.z, 8, 0.7, 0.7, 0.7, 0.01);
					batch.addForced(WHITE, 320, at.x, at.y, at.z, 3, 0.35, 0.35, 0.35, 0.01);
					batch.addForced(ParticleTypes.LARGE_SMOKE, 320, at.x, at.y, at.z, 2, 0.6, 0.6, 0.6, 0.01);
				}
				for (var p : server.players()) {
					if (p.position().distanceToSqr(from) < 320 * 320) {
						batch.send(server, p);
					}
				}
			}
			if (tickCount % 10 == 0) {
				server.playSound(null, getX(), getY(), getZ(), SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 6.0f, 0.5f);
			}
			if (tickCount > 400) {
				discard();
				return;
			}
		}
		setPos(to.x, to.y, to.z);
	}

	private void impact(ServerLevel level, Vec3 at, BlockPos hit) {
		level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 8.0f, 0.55f);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.HOSTILE, 6.0f, 0.6f);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.DRAGON_FIREBALL_EXPLODE, SoundSource.HOSTILE, 5.0f, 0.5f);
		for (int i = 0; i < 3; i++) {
			level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x + (i - 1) * 2.5, at.y + 0.8 + i * 0.6, at.z + (1 - i) * 1.5, 1, 0, 0, 0, 0);
		}
		level.sendParticles(ParticleTypes.FLASH, at.x, at.y + 1.0, at.z, 2, 0, 0, 0, 0);
		level.sendParticles(GREEN, at.x, at.y + 1.0, at.z, 260, 5.0, 2.0, 5.0, 0.0);
		level.sendParticles(ParticleTypes.LAVA, at.x, at.y + 0.5, at.z, 60, 3.0, 0.6, 3.0, 0.0);
		level.sendParticles(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, at.x, at.y + 0.5, at.z, 40, 2.5, 0.5, 2.5, 0.06);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y + 1.0, at.z, 120, 5.0, 1.5, 5.0, 0.12);
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, level.getBlockState(hit)), at.x, at.y + 0.5, at.z, 200, 4.0, 1.0, 4.0, 0.4);
		// the shock: hurt and fling the living close by
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(CRATER_RADIUS))) {
			Vec3 out = e.position().subtract(at);
			double d = out.length();
			if (d > CRATER_RADIUS) {
				continue;
			}
			double k = 1.0 - d / CRATER_RADIUS;
			Vec3 h = out.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : new Vec3(out.x, 0, out.z).normalize();
			e.hurt(level.damageSources().explosion(this, null), (float) (4.0 + 8.0 * k));
			e.push(h.x * (0.6 + 1.2 * k), 0.4 + 0.5 * k, h.z * (0.6 + 1.2 * k));
			e.hurtMarked = true;
		}
		// the screen shake (the shared tremor payload), fading out to 64 blocks
		for (ServerPlayer p : level.players()) {
			double d = p.position().distanceTo(at);
			if (d < 64 && p.connection != null) {
				net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(p,
						new com.projecthero.mod.network.TitanShakePayload((float) (1.1 * (1.0 - d / 64.0)), 26));
			}
		}
		struck = hit.immutable();
		carved = 0;
		entityData.set(LANDED, true);
		setDeltaMovement(Vec3.ZERO);
		setPos(Vec3.atBottomCenterOf(struck.above()));
		carveStep(level);
	}

	// ================================================================ the crater

	/** The crater's columns {dx, dz}, centre first then outward (the blast reaches the rim last). */
	private static final List<int[]> COLUMNS = buildColumns();

	private static List<int[]> buildColumns() {
		List<int[]> out = new ArrayList<>();
		int r = (int) Math.ceil(CRATER_RADIUS + SCORCH);
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				if (Math.sqrt(dx * dx + dz * dz) <= CRATER_RADIUS + SCORCH + 0.3) {
					out.add(new int[] { dx, dz });
				}
			}
		}
		out.sort((a, b) -> Integer.compare(a[0] * a[0] + a[1] * a[1], b[0] * b[0] + b[1] * b[1]));
		return List.copyOf(out);
	}

	/** v0.17.1: the bowl's depth below the struck block at {@code d} blocks from the centre (a parabola, 0 at the edge). */
	public static int depthAt(double d) {
		if (d > CRATER_RADIUS) {
			return 0;
		}
		double f = d / CRATER_RADIUS;
		return (int) Math.round(OmnitrixConfig.CRATER_DEPTH * (1.0 - f * f));
	}

	/** One tick's worth of columns; the pod goes down once the centre is open, the carver goes once the rim is done. */
	private void carveStep(ServerLevel level) {
		if (struck == null) {
			discard();
			return;
		}
		int end = Math.min(COLUMNS.size(), carved + OmnitrixConfig.CRATER_COLUMNS_PER_TICK);
		for (; carved < end; carved++) {
			int[] c = COLUMNS.get(carved);
			column(level, struck, c[0], c[1], this);
		}
		if (!podPlaced) {
			podPlaced = true;
			BlockPos bottom = struck.below(depthAt(0) - 1);
			OmnitrixPodEntity.place(level, Vec3.atBottomCenterOf(bottom));
		}
		if (carved >= COLUMNS.size()) {
			discard();
		}
	}

	/** Carves the whole crater at once (gametests; the meteor itself spreads it over ticks). Returns the bowl's lowest open block. */
	public static BlockPos crater(ServerLevel level, BlockPos struck) {
		for (int[] c : COLUMNS) {
			column(level, struck, c[0], c[1], null);
		}
		return struck.below(depthAt(0) - 1);
	}

	/** One column of the crater; {@code thrower} (the meteor) flings some of it out as debris, null throws none. */
	private static void column(ServerLevel level, BlockPos centre, int dx, int dz, OmnitrixMeteorEntity thrower) {
		var rnd = level.random;
		double d = Math.sqrt(dx * dx + dz * dz);
		int x = centre.getX() + dx, z = centre.getZ() + dz;
		int cy = centre.getY();
		int topY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
		boolean flat = Math.abs(topY - cy) <= 6;
		if (d <= CRATER_RADIUS + 0.3) {
			int floor = cy - depthAt(d);
			int clearTo = Math.max(cy, Math.min(topY, cy + 8));
			for (int y = floor + 1; y <= clearTo + 2; y++) {
				BlockPos pos = new BlockPos(x, y, z);
				BlockState s = level.getBlockState(pos);
				if (s.isAir() || (y > clearTo && !s.canBeReplaced() && !s.is(net.minecraft.tags.BlockTags.LEAVES))) {
					continue;
				}
				if (diggable(level, pos)) {
					// some of what is blown out flies, and lands round the crater as debris
					if (thrower != null && y == floor + 1 && d > 2.5 && level.random.nextFloat() < 0.18f) {
						thrower.fling(level, pos, d, dx, dz, s);
					}
					level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
				}
			}
			// the scorched lining (two deep in the hot middle, so magma never hangs over a void)
			for (int k = 0; k < (d < 4.5 ? 2 : 1); k++) {
				BlockPos pos = new BlockPos(x, floor - k, z);
				if (!diggable(level, pos)) {
					continue;
				}
				level.setBlock(pos, k == 0 ? lining(rnd, d, level.getBlockState(pos)) : Blocks.BLACKSTONE.defaultBlockState(), 2);
			}
			// flames licking the slopes
			if (d > 2.0 && rnd.nextFloat() < 0.045f) {
				BlockPos above = new BlockPos(x, floor + 1, z);
				if (level.getBlockState(above).isAir() && level.getBlockState(above.below()).isFaceSturdy(level, above.below(), Direction.UP)) {
					level.setBlock(above, Blocks.FIRE.defaultBlockState(), 3);
				}
			}
			return;
		}
		if (!flat) {
			return;
		}
		BlockPos surface = new BlockPos(x, topY, z);
		double past = d - CRATER_RADIUS;
		// the raised rim: ejecta heaped on the ground, thinning out
		if (past <= RIM && rnd.nextFloat() < 0.85f * (1.0 - past / RIM) + 0.1f) {
			int pile = past < RIM * 0.45 && rnd.nextFloat() < 0.5f ? 2 : 1;
			for (int k = 1; k <= pile; k++) {
				BlockPos pos = surface.above(k);
				BlockState s = level.getBlockState(pos);
				if ((s.isAir() || s.canBeReplaced()) && s.getFluidState().isEmpty()) {
					level.setBlock(pos, ejecta(rnd, level.getBlockState(surface)), 2);
				}
			}
			scorch(level, surface, 0.6f);
			return;
		}
		// scorch marks fading out past the rim
		scorch(level, surface, (float) (0.55 * (1.0 - (past - RIM) / (SCORCH - RIM))));
	}

	private static void scorch(ServerLevel level, BlockPos surface, float chance) {
		if (level.random.nextFloat() >= chance || !diggable(level, surface)) {
			return;
		}
		BlockState s = level.getBlockState(surface);
		if (s.is(Blocks.GRASS_BLOCK) || s.is(Blocks.DIRT) || s.is(Blocks.PODZOL) || s.is(Blocks.MYCELIUM)) {
			level.setBlock(surface, level.random.nextFloat() < 0.3f ? Blocks.BLACKSTONE.defaultBlockState() : Blocks.COARSE_DIRT.defaultBlockState(), 2);
		} else if (s.is(Blocks.STONE) || s.is(Blocks.SAND) || s.is(Blocks.GRAVEL) || s.is(Blocks.SNOW_BLOCK)) {
			level.setBlock(surface, level.random.nextFloat() < 0.5f ? Blocks.BLACKSTONE.defaultBlockState() : Blocks.TUFF.defaultBlockState(), 2);
		}
		BlockPos above = surface.above();
		BlockState a = level.getBlockState(above);
		if (!a.isAir() && a.canBeReplaced() && a.getFluidState().isEmpty()) {
			level.setBlock(above, Blocks.AIR.defaultBlockState(), 2);
		}
	}

	private static BlockState lining(net.minecraft.util.RandomSource rnd, double d, BlockState was) {
		double n = rnd.nextDouble();
		if (d < 2.5) {
			return n < 0.28 ? Blocks.MAGMA_BLOCK.defaultBlockState() : n < 0.75 ? Blocks.BLACKSTONE.defaultBlockState()
					: Blocks.OBSIDIAN.defaultBlockState();
		}
		if (d < 5.5) {
			return n < 0.42 ? Blocks.BLACKSTONE.defaultBlockState() : n < 0.68 ? Blocks.BASALT.defaultBlockState()
					: n < 0.76 ? Blocks.MAGMA_BLOCK.defaultBlockState() : n < 0.9 ? Blocks.COARSE_DIRT.defaultBlockState()
							: Blocks.TUFF.defaultBlockState();
		}
		return n < 0.34 ? Blocks.COARSE_DIRT.defaultBlockState() : n < 0.56 ? Blocks.BLACKSTONE.defaultBlockState()
				: n < 0.7 ? Blocks.GRAVEL.defaultBlockState() : n < 0.8 ? Blocks.COBBLESTONE.defaultBlockState()
						: n < 0.84 ? Blocks.MAGMA_BLOCK.defaultBlockState() : was.isAir() ? Blocks.COARSE_DIRT.defaultBlockState() : was;
	}

	private static BlockState ejecta(net.minecraft.util.RandomSource rnd, BlockState ground) {
		if (ground.is(net.minecraft.tags.BlockTags.SAND)) {
			return rnd.nextFloat() < 0.7f ? Blocks.SAND.defaultBlockState() : Blocks.SANDSTONE.defaultBlockState();
		}
		double n = rnd.nextDouble();
		return n < 0.36 ? Blocks.COARSE_DIRT.defaultBlockState() : n < 0.56 ? Blocks.DIRT.defaultBlockState()
				: n < 0.7 ? Blocks.GRAVEL.defaultBlockState() : n < 0.84 ? Blocks.COBBLESTONE.defaultBlockState()
						: Blocks.BLACKSTONE.defaultBlockState();
	}

	/** Throws {@code s} (scorched) out of the crater as a falling block -- it lands as debris. */
	private void fling(ServerLevel level, BlockPos pos, double d, int dx, int dz, BlockState s) {
		if (debrisLeft <= 0) {
			return;
		}
		debrisLeft--;
		BlockState as = s.is(net.minecraft.tags.BlockTags.DIRT) || s.is(Blocks.GRASS_BLOCK) ? Blocks.COARSE_DIRT.defaultBlockState()
				: s.isCollisionShapeFullBlock(level, pos) && !s.hasBlockEntity() ? s : Blocks.COBBLESTONE.defaultBlockState();
		FallingBlockEntity fb = FallingBlockEntity.fall(level, pos, as);
		double hx = dx / Math.max(0.5, d), hz = dz / Math.max(0.5, d);
		double sp = 0.35 + level.random.nextDouble() * 0.35;
		fb.setDeltaMovement(hx * sp, 0.55 + level.random.nextDouble() * 0.45, hz * sp);
		fb.dropItem = false;
		fb.hurtMarked = true;
	}

	private static boolean diggable(ServerLevel level, BlockPos pos) {
		BlockState s = level.getBlockState(pos);
		return !s.is(Blocks.BEDROCK) && s.getDestroySpeed(level, pos) >= 0f && level.getBlockEntity(pos) == null
				&& s.getFluidState().isEmpty() && level.isInWorldBounds(pos);
	}

	@Override
	public boolean shouldRenderAtSqrDistance(double distance) {
		return distance < 400 * 400;
	}

	@Override
	protected void readAdditionalSaveData(CompoundTag tag) {
		if (tag.contains("struck_x")) {
			struck = new BlockPos(tag.getInt("struck_x"), tag.getInt("struck_y"), tag.getInt("struck_z"));
			carved = tag.getInt("carved");
			podPlaced = tag.getBoolean("pod_placed");
			debrisLeft = 0;
			entityData.set(LANDED, true);
		}
	}

	@Override
	protected void addAdditionalSaveData(CompoundTag tag) {
		if (struck != null) {
			tag.putInt("struck_x", struck.getX());
			tag.putInt("struck_y", struck.getY());
			tag.putInt("struck_z", struck.getZ());
			tag.putInt("carved", carved);
			tag.putBoolean("pod_placed", podPlaced);
		}
	}
}
