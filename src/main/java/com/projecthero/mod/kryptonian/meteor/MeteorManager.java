package com.projecthero.mod.kryptonian.meteor;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import com.projecthero.mod.kryptonian.KryptonianConfig;
import com.projecthero.mod.kryptonian.item.KryptonianItems;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import org.joml.Vector3f;

/**
 * v0.14.8: the Kryptonite Meteor world event (v0.14.13: the nightly roll is gone -- the meteor is the world-generated
 * {@code KryptoniteCraterStructure} now; this class only drives an operator-summoned {@code /projecthero meteor}). Once every dusk in the Overworld there is a
 * {@link KryptonianConfig#METEOR_NIGHTLY_CHANCE} chance (guaranteed after {@link KryptonianConfig#METEOR_PITY_NIGHTS}
 * meteor-less nights) that tonight a meteor falls, at a random moment of the night, 80-200 blocks from a random online
 * player. Everyone in the Overworld sees the chat line; the chosen player is told roughly where it came down.
 *
 * <p>The fall is a {@link KryptoniteMeteorEntity} (the show); the impact is scheduled here by game time, so it lands
 * whether or not the entity's chunk is ticking: a modest crater (never block entities, never unbreakable blocks), a
 * scorched floor, a ring of kryptonite ore, and at the very centre one Meteor Core -- the only source of the Kryptonian
 * Crystal. Everything here is static scratch state dropped by {@code ServerStateReset}; a meteor in flight when the
 * server stops simply never lands.
 */
public final class MeteorManager {
	private static final DustParticleOptions GREEN = new DustParticleOptions(new Vector3f(0.35f, 1.0f, 0.3f), 3.0f);

	/** A meteor in the air: where it lands and when. */
	public record Pending(ServerLevel level, BlockPos target, long impactAt, UUID entity) {
	}

	private static final List<Pending> PENDING = new ArrayList<>();
	/** The Overworld day whose dusk roll has been made. */
	private static long lastRollDay = -1L;
	/** Game time tonight's meteor falls, or -1. */
	private static long scheduledAt = -1L;
	private static int nightsWithout;

	private MeteorManager() {
	}

	public static void clearSessionState() {
		PENDING.clear();
		lastRollDay = -1L;
		scheduledAt = -1L;
		nightsWithout = 0;
	}

	public static List<Pending> pending() {
		return List.copyOf(PENDING);
	}

	// ---------------------------------------------------------------- the nightly roll

	public static void tick(MinecraftServer server) {
		ServerLevel overworld = server.overworld();
		if (overworld == null) {
			return;
		}
		// v0.14.13: no more nightly meteors -- the Kryptonite Meteor is a rare world-generated crater now
		// (KryptoniteCraterStructure, found like Mjolnir). Only an operator's /projecthero meteor still drops one.
		tickImpacts(server);
	}

	// ---------------------------------------------------------------- summoning

	/** A meteor 80-200 blocks from {@code player} (the nightly event, and {@code /projecthero meteor}). */
	public static Pending summonNear(ServerPlayer player) {
		ServerLevel level = player.serverLevel();
		RandomSource random = level.random;
		BlockPos best = null;
		for (int attempt = 0; attempt < 8; attempt++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			double dist = KryptonianConfig.METEOR_MIN_DISTANCE
					+ random.nextDouble() * (KryptonianConfig.METEOR_MAX_DISTANCE - KryptonianConfig.METEOR_MIN_DISTANCE);
			int x = (int) Math.floor(player.getX() + Math.cos(angle) * dist);
			int z = (int) Math.floor(player.getZ() + Math.sin(angle) * dist);
			if (!level.getWorldBorder().isWithinBounds(new BlockPos(x, 0, z))) {
				continue;
			}
			BlockPos ground = ground(level, x, z);
			best = ground;
			if (level.getFluidState(ground.above()).isEmpty() && level.getFluidState(ground).isEmpty()) {
				break; // dry land
			}
		}
		if (best == null) {
			return null;
		}
		Pending p = launch(level, best, KryptonianConfig.METEOR_FALL_TICKS);
		announce(level, player, best);
		return p;
	}

	/** The block the meteor strikes: the top solid, non-leaf block of that column (loads the chunk if it must). */
	static BlockPos ground(ServerLevel level, int x, int z) {
		level.getChunk(x >> 4, z >> 4); // make sure the column is there to read
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
		return new BlockPos(x, Math.max(level.getMinBuildHeight() + 1, y), z);
	}

	/** Starts the fall at an angle from high above {@code target}; the impact comes {@code fallTicks} later. */
	public static Pending launch(ServerLevel level, BlockPos target, int fallTicks) {
		RandomSource random = level.random;
		double angle = random.nextDouble() * Math.PI * 2.0;
		double height = KryptonianConfig.METEOR_START_HEIGHT;
		Vec3 end = Vec3.atCenterOf(target).add(0, 0.5, 0);
		Vec3 start = end.add(Math.cos(angle) * height * 0.6, height, Math.sin(angle) * height * 0.6);
		double maxY = level.getMaxBuildHeight() + 60.0;
		if (start.y > maxY) {
			start = new Vec3(start.x, maxY, start.z);
		}
		KryptoniteMeteorEntity meteor = KryptonianItems.METEOR.create(level);
		UUID id = null;
		if (meteor != null) {
			meteor.setPath(start, end.subtract(0, 1.0, 0), fallTicks);
			if (level.addFreshEntity(meteor)) {
				id = meteor.getUUID();
			}
		}
		Pending p = new Pending(level, target, level.getGameTime() + fallTicks, id);
		PENDING.add(p);
		return p;
	}

	private static void announce(ServerLevel level, ServerPlayer near, BlockPos target) {
		for (ServerPlayer p : level.players()) {
			p.sendSystemMessage(Component.translatable("message.projecthero.meteor.streaks").withStyle(ChatFormatting.GREEN, ChatFormatting.ITALIC));
		}
		double dx = target.getX() + 0.5 - near.getX();
		double dz = target.getZ() + 0.5 - near.getZ();
		int dist = (int) Math.round(Math.sqrt(dx * dx + dz * dz) / 10.0) * 10;
		near.sendSystemMessage(Component.translatable("message.projecthero.meteor.direction",
				Component.translatable("message.projecthero.meteor.dir." + compass(dx, dz)), dist).withStyle(ChatFormatting.DARK_GREEN));
	}

	/** One of n, ne, e, se, s, sw, w, nw (Minecraft: -Z is north). */
	static String compass(double dx, double dz) {
		double deg = Math.toDegrees(Math.atan2(dx, -dz));
		int i = (int) Math.floorMod(Math.round(deg / 45.0), 8L);
		return new String[] { "n", "ne", "e", "se", "s", "sw", "w", "nw" }[i];
	}

	// ---------------------------------------------------------------- impacts

	private static void tickImpacts(MinecraftServer server) {
		if (PENDING.isEmpty()) {
			return;
		}
		for (Iterator<Pending> it = PENDING.iterator(); it.hasNext();) {
			Pending p = it.next();
			if (p.level().getServer() != server) {
				it.remove();
				continue;
			}
			if (p.level().getGameTime() < p.impactAt()) {
				continue;
			}
			it.remove();
			if (p.entity() != null) {
				Entity e = p.level().getEntity(p.entity());
				if (e != null) {
					e.discard();
				}
			}
			impact(p.level(), p.target());
		}
	}

	/**
	 * The strike: blast, crater, scorched floor, the Meteor Core at the centre and kryptonite ore around it. Returns
	 * the Meteor Core's position.
	 */
	public static BlockPos impact(ServerLevel level, BlockPos aim) {
		level.getChunk(aim.getX() >> 4, aim.getZ() >> 4);
		BlockPos ground = ground(level, aim.getX(), aim.getZ());
		// if the aimed point was already lower (a test floor, a cave mouth) trust it
		if (aim.getY() < ground.getY() && !level.getBlockState(aim).isAir()) {
			ground = aim;
		}
		Vec3 c = Vec3.atCenterOf(ground).add(0, 1.0, 0);
		RandomSource random = level.random;

		// the blast: hurts and throws whatever is standing there, but breaks nothing by itself
		level.explode(null, c.x, c.y, c.z, 3.0f, false, Level.ExplosionInteraction.NONE);
		level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y, c.z, 2, 1.0, 0.5, 1.0, 0.0);
		level.sendParticles(GREEN, c.x, c.y + 1.0, c.z, 80, 3.0, 2.0, 3.0, 0.0);
		level.sendParticles(ParticleTypes.LAVA, c.x, c.y, c.z, 30, 2.0, 1.0, 2.0, 0.0);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, c.x, c.y + 2.0, c.z, 60, 3.0, 3.0, 3.0, 0.03);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.AMBIENT, 8.0f, 0.5f);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.AMBIENT, 12.0f, 0.6f);

		double r = KryptonianConfig.METEOR_CRATER_RADIUS;
		double ry = r * 0.7;
		int ir = (int) Math.ceil(r);
		BlockPos center = ground.above();
		// carve the bowl, remembering the lowest carved block of each column
		java.util.Map<Long, Integer> floorY = new java.util.HashMap<>();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int dx = -ir; dx <= ir; dx++) {
			for (int dz = -ir; dz <= ir; dz++) {
				for (int dy = -ir; dy <= ir + 2; dy++) {
					double f = (dx * dx + dz * dz) / (r * r) + (dy * dy) / (ry * ry);
					if (dy > 0) {
						f = (dx * dx + dz * dz) / (r * r); // straight up above the bowl: clear the column (grass, logs)
						if (f > 1.0) {
							continue;
						}
					} else if (f > 1.0) {
						continue;
					}
					pos.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
					if (carve(level, pos)) {
						long key = BlockPos.asLong(pos.getX(), 0, pos.getZ());
						floorY.merge(key, pos.getY(), Math::min);
					}
				}
			}
		}
		// scorch the floor and a few fires on the rim
		for (var e : floorY.entrySet()) {
			BlockPos col = BlockPos.of(e.getKey());
			BlockPos floor = new BlockPos(col.getX(), e.getValue() - 1, col.getZ());
			BlockState below = level.getBlockState(floor);
			if (replaceable(level, floor, below) && !below.isAir() && random.nextFloat() < 0.6f) {
				float roll = random.nextFloat();
				BlockState scorch = roll < 0.15f ? Blocks.MAGMA_BLOCK.defaultBlockState()
						: roll < 0.55f ? Blocks.BLACKSTONE.defaultBlockState()
						: roll < 0.8f ? Blocks.BASALT.defaultBlockState() : Blocks.COARSE_DIRT.defaultBlockState();
				level.setBlock(floor, scorch, 3);
			}
			if (random.nextFloat() < 0.08f && level.getBlockState(floor.above()).isAir()
					&& level.getBlockState(floor).isFaceSturdy(level, floor, net.minecraft.core.Direction.UP)) {
				level.setBlock(floor.above(), Blocks.FIRE.defaultBlockState(), 3);
			}
		}
		// the heart of the crater: the Meteor Core, sunk into the floor at the centre
		Integer centerFloor = floorY.get(BlockPos.asLong(center.getX(), 0, center.getZ()));
		BlockPos core = centerFloor == null ? ground : new BlockPos(center.getX(), centerFloor - 1, center.getZ());
		if (!replaceable(level, core, level.getBlockState(core))) {
			core = core.above();
		}
		level.setBlock(core, KryptonianItems.METEOR_CORE.defaultBlockState(), 3);
		placeOre(level, core, random);
		return core;
	}

	/** Removes one block of the crater if it may be removed. True if the spot is now air. */
	private static boolean carve(ServerLevel level, BlockPos pos) {
		BlockState st = level.getBlockState(pos);
		if (st.isAir()) {
			return true;
		}
		if (!replaceable(level, pos, st) || !st.getFluidState().isEmpty()) {
			return false;
		}
		level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
		return true;
	}

	/** Never a block entity (chests...), never anything unbreakable, never the core / ore we placed. */
	private static boolean replaceable(ServerLevel level, BlockPos pos, BlockState st) {
		return !st.hasBlockEntity() && st.getDestroySpeed(level, pos) >= 0.0f
				&& !st.is(KryptonianItems.METEOR_CORE) && level.getWorldBorder().isWithinBounds(pos);
	}

	private static void placeOre(ServerLevel level, BlockPos core, RandomSource random) {
		List<BlockPos> spots = new ArrayList<>();
		for (int dx = -2; dx <= 2; dx++) {
			for (int dy = -1; dy <= 1; dy++) {
				for (int dz = -2; dz <= 2; dz++) {
					if ((dx == 0 && dy == 0 && dz == 0) || Math.abs(dx) + Math.abs(dz) + Math.abs(dy) > 3) {
						continue;
					}
					spots.add(core.offset(dx, dy, dz));
				}
			}
		}
		java.util.Collections.shuffle(spots, new java.util.Random(random.nextLong()));
		// the floor around the core first, then a few crystals sticking up out of the bowl
		spots.sort(java.util.Comparator.comparingInt(p -> (p.getY() > core.getY() ? 1 : 0)));
		int placed = 0;
		for (BlockPos p : spots) {
			if (placed >= KryptonianConfig.METEOR_ORE_COUNT) {
				break;
			}
			BlockState st = level.getBlockState(p);
			boolean embed = !st.isAir() && replaceable(level, p, st) && st.getFluidState().isEmpty();
			boolean jut = st.isAir() && p.getY() == core.getY() + 1 && random.nextFloat() < 0.4f;
			if (embed || jut) {
				level.setBlock(p, KryptonianItems.KRYPTONITE_ORE.defaultBlockState(), 3);
				placed++;
			}
		}
	}
}
