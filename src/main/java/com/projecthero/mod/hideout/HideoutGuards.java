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
		HOUND, RAIDER, SHAMAN,
		// v0.17.7 Gravebound (the Zombie Raid's player-model undead)
		RISEN, RISEN_ARMOURED, ACID, BONE_BLADE, JUGGERNAUT, CURSED,
		// v0.17.7 Carnage (Crimson Thralls + Carnage's brood)
		THRALL, THRALL_BRUTE, THRALL_STALKER, THRALL_SPITTER, BROODLING,
		// v0.17.7 Oathbreaker (Oath-bound Knights)
		KNIGHT, SQUIRE, ARBALIST, OATHSWORN
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
			case RISEN, RISEN_ARMOURED, ACID, BONE_BLADE, JUGGERNAUT, CURSED -> HideoutFaction.GRAVE;
			case THRALL, THRALL_BRUTE, THRALL_STALKER, THRALL_SPITTER, BROODLING -> HideoutFaction.CARNAGE;
			case KNIGHT, SQUIRE, ARBALIST, OATHSWORN -> HideoutFaction.OATH;
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
			case RISEN, RISEN_ARMOURED -> com.projecthero.mod.event.entity.RaidEntityTypes.RAID_ZOMBIE;
			case ACID -> com.projecthero.mod.event.entity.RaidEntityTypes.ACID_ZOMBIE;
			case BONE_BLADE -> com.projecthero.mod.event.entity.RaidEntityTypes.SWORD_SKELETON;
			case JUGGERNAUT -> com.projecthero.mod.event.entity.RaidEntityTypes.JUGGERNAUT_ZOMBIE;
			case CURSED -> com.projecthero.mod.event.entity.RaidEntityTypes.CURSED_ZOMBIE;
			case THRALL, THRALL_BRUTE, THRALL_STALKER, THRALL_SPITTER -> HideoutEntityTypes.CRIMSON_THRALL;
			case BROODLING -> com.projecthero.mod.carnage.CarnageEntityTypes.CRIMSON_SPAWN;
			case KNIGHT, SQUIRE, ARBALIST, OATHSWORN -> HideoutEntityTypes.OATH_KNIGHT;
		};
	}

	/**
	 * A guard of {@code role} standing at {@code pos} (block feet position), facing {@code yaw}, held within
	 * {@code leash} blocks of its post. Returns the mob (already added to the level), or null.
	 */
	public static Mob spawn(ServerLevelAccessor level, Role role, BlockPos pos, float yaw, int leash) {
		return spawn(level, role, pos, yaw, leash, null);
	}

	/**
	 * As {@link #spawn(ServerLevelAccessor, Role, BlockPos, float, int)} with a v0.17.7 alarm role, applied before the mob
	 * joins the level (worldgen saves an added mob straight into the chunk, so later changes would be lost).
	 */
	public static Mob spawn(ServerLevelAccessor level, Role role, BlockPos pos, float yaw, int leash, com.projecthero.mod.alarm.GuardRole alarmRole) {
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
			case RISEN -> ((com.projecthero.mod.event.entity.RaidZombie) mob).setVariant(com.projecthero.mod.event.entity.RaidZombie.Variant.BASIC);
			case RISEN_ARMOURED -> ((com.projecthero.mod.event.entity.RaidZombie) mob).setVariant(com.projecthero.mod.event.entity.RaidZombie.Variant.ARMOURED);
			case THRALL -> ((com.projecthero.mod.hideout.mob.HideoutSoldier) mob).setKind(com.projecthero.mod.hideout.mob.HideoutSoldier.Kind.THRALL);
			case THRALL_BRUTE -> ((com.projecthero.mod.hideout.mob.HideoutSoldier) mob).setKind(com.projecthero.mod.hideout.mob.HideoutSoldier.Kind.BRUTE);
			case THRALL_STALKER -> ((com.projecthero.mod.hideout.mob.HideoutSoldier) mob).setKind(com.projecthero.mod.hideout.mob.HideoutSoldier.Kind.STALKER);
			case THRALL_SPITTER -> ((com.projecthero.mod.hideout.mob.HideoutSoldier) mob).setKind(com.projecthero.mod.hideout.mob.HideoutSoldier.Kind.SPITTER);
			case KNIGHT -> ((com.projecthero.mod.hideout.mob.HideoutSoldier) mob).setKind(com.projecthero.mod.hideout.mob.HideoutSoldier.Kind.KNIGHT);
			case SQUIRE -> ((com.projecthero.mod.hideout.mob.HideoutSoldier) mob).setKind(com.projecthero.mod.hideout.mob.HideoutSoldier.Kind.SQUIRE);
			case ARBALIST -> ((com.projecthero.mod.hideout.mob.HideoutSoldier) mob).setKind(com.projecthero.mod.hideout.mob.HideoutSoldier.Kind.ARBALIST);
			case OATHSWORN -> ((com.projecthero.mod.hideout.mob.HideoutSoldier) mob).setKind(com.projecthero.mod.hideout.mob.HideoutSoldier.Kind.OATHSWORN);
			default -> {
			}
		}
		mob.setPersistenceRequired();
		mob.addTag(TAG);
		if (leash > 0) {
			mob.restrictTo(pos, leash);
		}
		if (alarmRole != null) {
			com.projecthero.mod.alarm.AlarmGuards.setRole(mob, alarmRole);
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
			case GRAVE -> switch (random.nextInt(4)) {
				case 0 -> Role.BONE_BLADE;
				case 1 -> Role.ACID;
				default -> Role.RISEN;
			};
			case CARNAGE -> switch (random.nextInt(4)) {
				case 0 -> Role.THRALL_STALKER;
				case 1 -> Role.BROODLING;
				default -> Role.THRALL;
			};
			case OATH -> random.nextInt(3) == 0 ? Role.ARBALIST : (random.nextBoolean() ? Role.KNIGHT : Role.SQUIRE);
		};
		return reinforcementOf(level, faction, role, spot, random);
	}

	/** v0.17.7: a guard of a given {@code role} arriving the faction's way (bosses calling help). */
	public static Mob reinforcementOf(ServerLevel level, HideoutFaction faction, Role role, BlockPos spot, RandomSource random) {
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
			case GRAVE -> { // clawing up out of the ground
				level.sendParticles(ParticleTypes.SOUL, x, y, z, 14, 0.4, 0.5, 0.4, 0.04);
				level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK,
						net.minecraft.world.level.block.Blocks.ROOTED_DIRT.defaultBlockState()), x, mob.getY() + 0.1, z, 24, 0.4, 0.1, 0.4, 0.1);
			}
			case CARNAGE -> level.sendParticles(new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(0.8f, 0.05f, 0.07f), 1.4f),
					x, y, z, 30, 0.4, 0.6, 0.4, 0.05);
			case OATH -> level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, x, y, z, 20, 0.4, 0.6, 0.4, 0.03);
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
