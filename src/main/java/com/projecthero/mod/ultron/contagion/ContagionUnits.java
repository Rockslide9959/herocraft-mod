package com.projecthero.mod.ultron.contagion;

import java.util.Optional;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.ultron.UltronCombat;
import com.projecthero.mod.ultron.UltronEntityTypes;
import com.projecthero.mod.ultron.UltronFx;
import com.projecthero.mod.ultron.contagion.entity.UltronNexusEntity;
import com.projecthero.mod.ultron.entity.UltronPrimeEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.1: every robot the Contagion sends -- capture waves, the Heavy and the Ultron Prime echo at a Nexus, the
 * Hunter-Killer squads at 75% Saturation and the drones a Compromised Stark machine builds. All of them carry
 * {@link Contagion#UNIT_TAG} (cleaned up when the Contagion stops), are persistent, never force a chunk to load, and
 * have their health scaled for Ultron's version.
 *
 * <p><b>Lancers and Hijackers</b> (the harder Uprising's units, built separately) are looked up by id
 * ({@link #LANCER_ID}, {@link #HIJACKER_ID}); a world without them gets Ultron Drones instead.
 */
public final class ContagionUnits {
	public static final ResourceLocation LANCER_ID = ProjectHeroMod.id("ultron_lancer");
	public static final ResourceLocation HIJACKER_ID = ProjectHeroMod.id("ultron_hijacker");

	private ContagionUnits() {
	}

	/** The entity type registered under {@code id}, if it is a mob; empty otherwise. */
	@SuppressWarnings("unchecked")
	public static Optional<EntityType<? extends Mob>> mobType(ResourceLocation id) {
		Optional<EntityType<?>> t = BuiltInRegistries.ENTITY_TYPE.getOptional(id);
		if (t.isEmpty()) {
			return Optional.empty();
		}
		return Optional.of((EntityType<? extends Mob>) t.get());
	}

	/** A Lancer if that unit exists in this build, else a drone. */
	public static EntityType<? extends Mob> lancerOrDrone() {
		return mobType(LANCER_ID).orElse(UltronEntityTypes.DRONE);
	}

	/** A Hijacker if that unit exists in this build, else a drone. */
	public static EntityType<? extends Mob> hijackerOrDrone() {
		return mobType(HIJACKER_ID).orElse(UltronEntityTypes.DRONE);
	}

	/** Contagion units of any kind within {@code r} of {@code at}. */
	public static int countNear(ServerLevel level, Vec3 at, double r) {
		return level.getEntitiesOfClass(Mob.class, new AABB(at, at).inflate(r), m -> m.isAlive() && m.getTags().contains(Contagion.UNIT_TAG)
				&& !(m instanceof com.projecthero.mod.ultron.contagion.entity.ContagionStructureEntity)).size();
	}

	/**
	 * Makes one {@code type} at {@code at}, tagged and scaled, aimed at {@code target} (may be null). Null if it can't be made
	 * (unloaded spot, Peaceful, or the type would not create a mob).
	 */
	public static Mob spawn(ServerLevel level, EntityType<? extends Mob> type, Vec3 at, LivingEntity target, int version) {
		if (level.getDifficulty() == Difficulty.PEACEFUL || !level.isLoaded(BlockPos.containing(at))) {
			return null;
		}
		Entity e = type.create(level);
		if (!(e instanceof Mob mob)) {
			if (e != null) {
				e.discard();
			}
			return null;
		}
		mob.moveTo(at.x, at.y, at.z, level.random.nextFloat() * 360f, 0f);
		mob.finalizeSpawn(level, level.getCurrentDifficultyAt(BlockPos.containing(at)), MobSpawnType.EVENT, null);
		mob.addTag(Contagion.UNIT_TAG);
		mob.setPersistenceRequired();
		double scale = ContagionConfig.unitHealthScale(version);
		if (scale > 1.0 && mob.getAttribute(Attributes.MAX_HEALTH) != null) {
			double hp = mob.getAttribute(Attributes.MAX_HEALTH).getBaseValue() * scale;
			mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(hp);
			mob.setHealth((float) hp);
		}
		level.addFreshEntity(mob);
		if (target != null) {
			mob.setTarget(target);
		}
		level.sendParticles(ParticleTypes.FLASH, mob.getX(), mob.getY() + 1, mob.getZ(), 1, 0, 0, 0, 0);
		level.sendParticles(UltronFx.RED, mob.getX(), mob.getY() + 1, mob.getZ(), 8, 0.3, 0.6, 0.3, 0.02);
		return mob;
	}

	/** A spot on a ring {@code min..max} round {@code centre}, on the ground plus {@code up}; null if none is loaded. */
	public static Vec3 ringSpot(ServerLevel level, Vec3 centre, double min, double max, double up) {
		for (int attempt = 0; attempt < 8; attempt++) {
			double a = level.random.nextDouble() * Math.PI * 2;
			double r = min + level.random.nextDouble() * Math.max(0, max - min);
			double x = centre.x + Math.cos(a) * r;
			double z = centre.z + Math.sin(a) * r;
			BlockPos col = BlockPos.containing(x, centre.y, z);
			if (!level.isLoaded(col)) {
				continue;
			}
			int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, col.getX(), col.getZ());
			return new Vec3(x, ground + up, z);
		}
		return null;
	}

	private static Player nearestTarget(ServerLevel level, Vec3 at) {
		return UltronCombat.nearestPlayer(level, at, 64);
	}

	// ---------------------------------------------------------------- the Override capture

	/** One capture wave: drones from the sky, a Sentinel Drone, a Lancer and a Hijacker, up to the wave cap. */
	public static void captureWave(ServerLevel level, UltronNexusEntity nexus, int version) {
		ContagionConfig.Nexus cfg = ContagionConfig.nexus();
		Vec3 c = nexus.position();
		int room = cfg.waveCap - countNear(level, c, 48);
		Player target = nearestTarget(level, c);
		@SuppressWarnings("unchecked")
		EntityType<? extends Mob>[] wave = new EntityType[] { UltronEntityTypes.DRONE, UltronEntityTypes.DRONE, UltronEntityTypes.SENTINEL_DRONE,
				lancerOrDrone(), hijackerOrDrone() };
		for (EntityType<? extends Mob> type : wave) {
			if (room-- <= 0) {
				break;
			}
			boolean flier = type == UltronEntityTypes.DRONE;
			Vec3 at = ringSpot(level, c, cfg.captureRadius + 4, cfg.captureRadius + 14, flier ? 8.0 : 0.0);
			if (at != null) {
				spawn(level, type, at, target, version);
			}
		}
	}

	public static void spawnHeavy(ServerLevel level, UltronNexusEntity nexus, int version) {
		Vec3 at = ringSpot(level, nexus.position(), ContagionConfig.nexus().captureRadius + 4, ContagionConfig.nexus().captureRadius + 8, 0.0);
		if (at != null) {
			spawn(level, UltronEntityTypes.HEAVY, at, nearestTarget(level, nexus.position()), version);
		}
	}

	/** The Ultron Prime echo at 80%: a weaker Prime (600 health) with no body to jump to. */
	public static UltronPrimeEntity spawnPrimeEcho(ServerLevel level, UltronNexusEntity nexus, int version) {
		Vec3 at = nexus.position().add(0, 11, 0);
		Mob m = spawn(level, UltronEntityTypes.PRIME, at, nearestTarget(level, nexus.position()), 1);
		if (!(m instanceof UltronPrimeEntity prime)) {
			return null;
		}
		double hp = ContagionConfig.nexus().echoHealth * ContagionConfig.unitHealthScale(version);
		prime.getAttribute(Attributes.MAX_HEALTH).setBaseValue(hp);
		prime.setHealth((float) hp);
		prime.setCustomName(Component.translatable("entity.projecthero.ultron_prime_echo"));
		UltronFx.forced(level, ParticleTypes.EXPLOSION_EMITTER, at.x, at.y, at.z, 1, 0, 0, 0, 0);
		return prime;
	}

	// ---------------------------------------------------------------- Hunter-Killers and machine drones

	/** A Hunter-Killer squad (3 drones + a Heavy) sent at {@code player}, 18-28 blocks off; returns how many came. */
	public static int hunterSquad(ServerLevel level, Player player, int version) {
		ContagionConfig.Hunters cfg = ContagionConfig.hunters();
		int n = 0;
		for (int i = 0; i < cfg.drones; i++) {
			Vec3 at = ringSpot(level, player.position(), 18, 28, 8.0);
			if (at != null && spawn(level, UltronEntityTypes.DRONE, at, player, version) != null) {
				n++;
			}
		}
		for (int i = 0; i < cfg.heavies; i++) {
			Vec3 at = ringSpot(level, player.position(), 18, 26, 0.0);
			if (at != null && spawn(level, UltronEntityTypes.HEAVY, at, player, version) != null) {
				n++;
			}
		}
		return n;
	}

	/** A drone built by a Compromised Stark machine at {@code pos}. */
	public static Mob machineDrone(ServerLevel level, BlockPos pos, int version) {
		Vec3 at = Vec3.atCenterOf(pos).add(0, 1.6, 0);
		return spawn(level, UltronEntityTypes.DRONE, at, nearestTarget(level, at), version);
	}
}
