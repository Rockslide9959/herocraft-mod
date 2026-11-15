package com.projecthero.mod.hideout;

import java.util.UUID;

import com.projecthero.mod.darkseid.entity.BoomTubeEntity;
import com.projecthero.mod.darkseid.entity.DarkseidEntityTypes;
import com.projecthero.mod.darkseid.entity.ParademonEntity;
import com.projecthero.mod.frostgiant.FrostGiantEntityTypes;
import com.projecthero.mod.symbiote.SymbioteHost;
import com.projecthero.mod.syndicate.SyndicateEntityTypes;
import com.projecthero.mod.syndicate.entity.SyndicateGunman;
import com.projecthero.mod.ultron.UltronEntityTypes;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;

/**
 * v0.17.5: the hideouts' guards -- every faction's <b>existing</b> mobs: Syndicate thugs / gunmen / enforcers, Ultron
 * drones / sentinel drones / heavies / lancers, Parademons, Symbiote-infested zombies / husks / skeletons / spiders /
 * vindicators, Rime Hounds / Jotun Raiders / Frost Shamans. A guard is persistent (never despawns), tagged
 * {@link #TAG}, and kept near its post ({@code restrictTo}) until it has someone to chase.
 *
 * <p>{@link #spawn} works on a {@code WorldGenLevel} (structure pieces) and a {@code ServerLevel} (alarms, tests).
 */
public final class HideoutGuards {
	public static final String TAG = "projecthero.hideout_guard";

	public enum Role {
		// Kingpin
		THUG, PISTOL, SHOTGUN, SNIPER, ENFORCER,
		// Ultron
		DRONE, SENTINEL, HEAVY, LANCER,
		// Apokolips
		PARADEMON, PARADEMON_RANGED, PARADEMON_ELITE, PARADEMON_BRUTE,
		// Symbiote hosts
		HOST_ZOMBIE, HOST_HUSK, HOST_SKELETON, HOST_SPIDER, HOST_VINDICATOR,
		// Frost
		HOUND, RAIDER, SHAMAN
	}

	private HideoutGuards() {
	}

	/** The faction a role belongs to. */
	public static HideoutFaction factionOf(Role role) {
		return switch (role) {
			case THUG, PISTOL, SHOTGUN, SNIPER, ENFORCER -> HideoutFaction.KINGPIN;
			case DRONE, SENTINEL, HEAVY, LANCER -> HideoutFaction.ULTRON;
			case PARADEMON, PARADEMON_RANGED, PARADEMON_ELITE, PARADEMON_BRUTE -> HideoutFaction.APOKOLIPS;
			case HOST_ZOMBIE, HOST_HUSK, HOST_SKELETON, HOST_SPIDER, HOST_VINDICATOR -> HideoutFaction.SYMBIOTE;
			case HOUND, RAIDER, SHAMAN -> HideoutFaction.FROST;
		};
	}

	/** True for a mob that guards a hideout. */
	public static boolean isGuard(Mob mob) {
		return mob.getTags().contains(TAG);
	}

	private static EntityType<? extends Mob> typeOf(Role role) {
		return switch (role) {
			case THUG -> SyndicateEntityTypes.THUG;
			case PISTOL, SHOTGUN, SNIPER -> SyndicateEntityTypes.GUNMAN;
			case ENFORCER -> SyndicateEntityTypes.ENFORCER;
			case DRONE -> UltronEntityTypes.DRONE;
			case SENTINEL -> UltronEntityTypes.SENTINEL_DRONE;
			case HEAVY -> UltronEntityTypes.HEAVY;
			case LANCER -> UltronEntityTypes.LANCER;
			case PARADEMON, PARADEMON_RANGED, PARADEMON_ELITE, PARADEMON_BRUTE -> DarkseidEntityTypes.PARADEMON;
			case HOST_ZOMBIE -> EntityType.ZOMBIE;
			case HOST_HUSK -> EntityType.HUSK;
			case HOST_SKELETON -> EntityType.SKELETON;
			case HOST_SPIDER -> EntityType.SPIDER;
			case HOST_VINDICATOR -> EntityType.VINDICATOR;
			case HOUND -> FrostGiantEntityTypes.HOUND;
			case RAIDER -> FrostGiantEntityTypes.RAIDER;
			case SHAMAN -> FrostGiantEntityTypes.SHAMAN;
		};
	}

	/**
	 * A guard of {@code role} standing at {@code pos} (block feet position), facing {@code yaw}, held within
	 * {@code leash} blocks of its post. Returns the mob (already added to the level), or null.
	 */
	public static Mob spawn(ServerLevelAccessor level, Role role, BlockPos pos, float yaw, int leash) {
		Mob mob = typeOf(role).create(level.getLevel());
		if (mob == null) {
			return null;
		}
		mob.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, yaw, 0f);
		mob.setYHeadRot(yaw);
		mob.setYBodyRot(yaw);
		mob.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.STRUCTURE, null);
		switch (role) {
			case PISTOL -> ((SyndicateGunman) mob).setKind(SyndicateGunman.Kind.PISTOL);
			case SHOTGUN -> ((SyndicateGunman) mob).setKind(SyndicateGunman.Kind.SHOTGUN);
			case SNIPER -> ((SyndicateGunman) mob).setKind(SyndicateGunman.Kind.SNIPER);
			case PARADEMON -> ((ParademonEntity) mob).setup(ParademonEntity.Variant.STANDARD, null);
			case PARADEMON_RANGED -> ((ParademonEntity) mob).setup(ParademonEntity.Variant.RANGED, null);
			case PARADEMON_ELITE -> ((ParademonEntity) mob).setup(ParademonEntity.Variant.ELITE, null);
			case PARADEMON_BRUTE -> ((ParademonEntity) mob).setup(ParademonEntity.Variant.BRUTE, null);
			case HOST_ZOMBIE, HOST_HUSK, HOST_SKELETON, HOST_SPIDER, HOST_VINDICATOR -> SymbioteHost.mark(mob);
			default -> {
			}
		}
		mob.setPersistenceRequired();
		mob.addTag(TAG);
		if (leash > 0) {
			mob.restrictTo(pos, leash);
		}
		level.addFreshEntityWithPassengers(mob);
		return mob;
	}

	/** A guard called in by an alarm at {@code spot}, with its faction's arrival effect. */
	public static Mob reinforcement(ServerLevel level, HideoutFaction faction, BlockPos spot, RandomSource random) {
		Role role = switch (faction) {
			case KINGPIN -> random.nextInt(3) == 0 ? Role.PISTOL : Role.THUG;
			case ULTRON -> random.nextInt(3) == 0 ? Role.SENTINEL : Role.DRONE;
			case APOKOLIPS -> random.nextInt(3) == 0 ? Role.PARADEMON_RANGED : Role.PARADEMON;
			case SYMBIOTE -> switch (random.nextInt(4)) {
				case 0 -> Role.HOST_SPIDER;
				case 1 -> Role.HOST_SKELETON;
				default -> Role.HOST_ZOMBIE;
			};
			case FROST -> Role.HOUND;
		};
		Mob mob = spawn(level, role, spot, random.nextFloat() * 360f, 12);
		if (mob == null) {
			return null;
		}
		double x = mob.getX();
		double y = mob.getY() + 0.8;
		double z = mob.getZ();
		switch (faction) {
			case APOKOLIPS -> BoomTubeEntity.open(level, mob.position().add(0, 1.2, 0), BoomTubeEntity.Kind.MELEE, 1.6f, 30, (UUID) null);
			case SYMBIOTE -> level.sendParticles(ParticleTypes.SQUID_INK, x, y, z, 30, 0.4, 0.6, 0.4, 0.1);
			case ULTRON -> level.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, y, z, 20, 0.4, 0.6, 0.4, 0.1);
			case FROST -> level.sendParticles(ParticleTypes.SNOWFLAKE, x, y, z, 30, 0.5, 0.6, 0.5, 0.05);
			default -> level.sendParticles(ParticleTypes.POOF, x, y, z, 8, 0.3, 0.4, 0.3, 0.02);
		}
		return mob;
	}

	/**
	 * A free standing spot (solid floor, two air blocks) between {@code min} and {@code max} blocks from
	 * {@code around}, at roughly its height. Null after 24 misses.
	 */
	public static BlockPos findSpot(ServerLevel level, BlockPos around, int min, int max, RandomSource random) {
		for (int i = 0; i < 24; i++) {
			double a = random.nextDouble() * Math.PI * 2;
			double r = min + random.nextDouble() * (max - min);
			int x = around.getX() + (int) Math.round(Math.cos(a) * r);
			int z = around.getZ() + (int) Math.round(Math.sin(a) * r);
			for (int dy = 3; dy >= -4; dy--) {
				BlockPos feet = new BlockPos(x, around.getY() + dy, z);
				BlockState floor = level.getBlockState(feet.below());
				if (floor.isFaceSturdy(level, feet.below(), net.minecraft.core.Direction.UP)
						&& level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
						&& level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()
						&& level.getFluidState(feet).isEmpty()) {
					return feet;
				}
			}
		}
		return null;
	}
}
