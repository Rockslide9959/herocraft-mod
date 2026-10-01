package com.projecthero.mod.horde;

import com.projecthero.mod.event.entity.RaidEntityTypes;
import com.projecthero.mod.event.entity.RaidZombie;
import com.projecthero.mod.horde.entity.BoneTyrant;
import com.projecthero.mod.horde.entity.BroodQueen;
import com.projecthero.mod.horde.entity.HordeEntityTypes;
import com.projecthero.mod.titan.entity.TitanEntity;
import com.projecthero.mod.titan.entity.TitanEntityTypes;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * v0.14.12: what comes out of each Horde block. The mix gets nastier as the waves go on:
 * <ul>
 *   <li><b>Zombie</b>: the raid's own daylight-proof zombies (plain, babies, then armoured), husks, acid spitters from
 *       wave 3, a few Juggernauts from wave 5. Boss: a Titan.</li>
 *   <li><b>Skeleton</b>: skeletons and strays, bogged (poison arrows) and sword skeletons from wave 2, wither
 *       skeletons from wave 4 -- helmeted, so the sun can't burn them, the helmets better with every wave. Boss: the
 *       Bone Tyrant.</li>
 *   <li><b>Spider</b>: fast, web-spitting Horde Spiders and cave spiders. Boss: the Brood Queen.</li>
 * </ul>
 */
public final class HordeWaves {
	private HordeWaves() {
	}

	/** One unplaced horde mob for wave {@code wave} (escorts during the boss fight roll as wave 8). */
	public static Mob create(ServerLevel level, HordeKind kind, int wave, boolean escort) {
		RandomSource r = level.random;
		int w = escort ? HordeRaid.WAVES : wave;
		int roll = r.nextInt(100);
		return switch (kind) {
			case ZOMBIE -> {
				if (w >= 5 && roll < 4) {
					yield RaidEntityTypes.JUGGERNAUT_ZOMBIE.create(level);
				}
				if (w >= 3 && roll < 16) {
					yield RaidEntityTypes.ACID_ZOMBIE.create(level);
				}
				if (roll < 30) {
					yield EntityType.HUSK.create(level);
				}
				RaidZombie z = RaidEntityTypes.RAID_ZOMBIE.create(level);
				if (z != null) {
					z.setVariant(w >= 2 && roll < 30 + 4 * w ? RaidZombie.Variant.ARMOURED
							: roll > 82 ? RaidZombie.Variant.BABY : RaidZombie.Variant.BASIC);
				}
				yield z;
			}
			case SKELETON -> {
				if (w >= 4 && roll < 12) {
					yield EntityType.WITHER_SKELETON.create(level);
				}
				if (w >= 2 && roll < 28) {
					yield RaidEntityTypes.SWORD_SKELETON.create(level);
				}
				if (w >= 2 && roll < 40) {
					yield EntityType.BOGGED.create(level);
				}
				if (roll < 62) {
					yield EntityType.STRAY.create(level);
				}
				yield EntityType.SKELETON.create(level);
			}
			case SPIDER -> roll < 30 ? EntityType.CAVE_SPIDER.create(level) : HordeEntityTypes.HORDE_SPIDER.create(level);
		};
	}

	/** After {@code finalizeSpawn}: skeletons get helmets (no sunburn, better ones later); nothing drops its gear. */
	public static void dress(Mob mob, HordeKind kind, int wave) {
		if (mob instanceof AbstractSkeleton && mob.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
			ItemStack helmet = new ItemStack(wave >= 6 ? Items.IRON_HELMET : wave >= 3 ? Items.CHAINMAIL_HELMET : Items.LEATHER_HELMET);
			mob.setItemSlot(EquipmentSlot.HEAD, helmet);
			if (wave >= 5 && mob.getRandom().nextInt(3) == 0) {
				mob.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.CHAINMAIL_CHESTPLATE));
			}
		}
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			mob.setDropChance(slot, 0f);
		}
		// a vanilla spider can roll a skeleton jockey in finalizeSpawn, which addFreshEntity would never add
		mob.ejectPassengers();
	}

	/** The boss, placed and in the world. {@code players} = fighters still in the raid. */
	public static LivingEntity createBoss(ServerLevel level, HordeKind kind, Vec3 at, int players) {
		switch (kind) {
			case ZOMBIE -> {
				TitanEntity titan = TitanEntityTypes.TITAN.create(level);
				if (titan == null) {
					return null;
				}
				titan.moveTo(at.x, at.y, at.z, level.random.nextFloat() * 360f, 0f);
				level.addFreshEntity(titan);
				titan.onTransformed();
				return titan;
			}
			case SKELETON -> {
				BoneTyrant tyrant = HordeEntityTypes.BONE_TYRANT.create(level);
				if (tyrant == null) {
					return null;
				}
				tyrant.moveTo(at.x, at.y, at.z, level.random.nextFloat() * 360f, 0f);
				tyrant.finalizeSpawn(level, level.getCurrentDifficultyAt(tyrant.blockPosition()), MobSpawnType.EVENT, null);
				tyrant.configure(players);
				level.addFreshEntity(tyrant);
				return tyrant;
			}
			default -> {
				BroodQueen queen = HordeEntityTypes.BROOD_QUEEN.create(level);
				if (queen == null) {
					return null;
				}
				queen.moveTo(at.x, at.y, at.z, level.random.nextFloat() * 360f, 0f);
				queen.finalizeSpawn(level, level.getCurrentDifficultyAt(queen.blockPosition()), MobSpawnType.EVENT, null);
				queen.configure(players);
				queen.ejectPassengers(); // a vanilla spider can roll a jockey -- not the queen
				level.addFreshEntity(queen);
				return queen;
			}
		}
	}
}
