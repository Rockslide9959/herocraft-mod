package com.projecthero.mod.event.raid;

import java.util.List;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.event.EventConfig;
import com.projecthero.mod.event.EventInstance;
import com.projecthero.mod.event.EventManager;
import com.projecthero.mod.event.EventSavedData;
import com.projecthero.mod.event.boss.BossPowers;
import com.projecthero.mod.event.entity.AcidZombie;
import com.projecthero.mod.event.entity.CursedZombie;
import com.projecthero.mod.event.entity.EmpoweredZombie;
import com.projecthero.mod.event.entity.JuggernautZombie;
import com.projecthero.mod.event.entity.RaidZombie;
import com.projecthero.mod.event.entity.SwordSkeleton;
import com.projecthero.mod.grave.GraveboundCurse;
import com.projecthero.mod.grave.GraveboundState;
import com.projecthero.mod.grave.item.BossTrophyItem;
import com.projecthero.mod.grave.item.CorruptedPowerCoreItem;
import com.projecthero.mod.grave.item.GraveItems;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * The three reward layers: what raid enemies drop, what a Powered Zombie Boss drops, and what
 * completing all twelve waves produces.
 *
 * <h2>No duplication paths</h2>
 * <ul>
 *   <li>Per-kill drops happen once, in the death hook, on the server.</li>
 *   <li>Boss rewards are keyed to the boss entity dying, and an entity can only die once.</li>
 *   <li>The completion chest is guarded by {@code chestGenerated} on the raid, which is persisted --
 *       so a server restart between the last kill and the chest being placed cannot produce two.</li>
 *   <li>The first-clear Heart of the Grave is guarded by a per-player persistent flag
 *       ({@link GraveboundState#heartOfTheGraveGranted}), not by "is this raid number one" -- so
 *       deliberately failing and re-running raids cannot farm it.</li>
 * </ul>
 */
public final class ZombieRaidRewards {
	/** Loot table for the Cursed Grave Chest that appears after wave 12. */
	public static final ResourceKey<LootTable> COMPLETION_LOOT = ResourceKey.create(Registries.LOOT_TABLE,
			ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "chests/cursed_grave_chest"));

	private ZombieRaidRewards() {
	}

	// ---------------- layer 1: enemy drops ----------------

	/**
	 * Called from the death hook for any entity. Returns quickly for everything that is not a raid
	 * mob: a Cursed Zombie (which drops essence wherever it died, raid or not) or an entity actually
	 * owned by a running raid.
	 */
	public static void onEntityDied(LivingEntity entity, ServerLevel level) {
		if (entity instanceof CursedZombie) {
			dropCursedZombieEssence(entity, level);
			return;
		}
		EventInstance event = EventManager.owning(entity);
		if (!(event instanceof ZombieRaid raid)) {
			return;
		}
		raid.disown(entity.getUUID());

		RandomSource random = level.random;
		EventConfig.ZombieRaid cfg = EventConfig.raid();

		if (entity instanceof EmpoweredZombie boss) {
			raid.noteBossDefeated(boss.getUUID());
			dropBossRewards(boss, raid, level, random, cfg);
			return;
		}
		if (entity instanceof JuggernautZombie) {
			dropEssence(entity, level, randomBetween(random, cfg.graveEssenceFromJuggernautMin,
					cfg.graveEssenceFromJuggernautMax));
		} else if (entity instanceof AcidZombie || entity instanceof SwordSkeleton
				|| (entity instanceof RaidZombie rz && rz.variant() == RaidZombie.Variant.ARMOURED)) {
			dropEssence(entity, level, randomBetween(random, cfg.graveEssenceFromSpecialMin,
					cfg.graveEssenceFromSpecialMax));
		} else {
			dropEssence(entity, level, random.nextDouble() < cfg.graveEssenceFromBasicChance ? 1 : 0);
		}
	}

	private static void dropCursedZombieEssence(LivingEntity entity, ServerLevel level) {
		EventConfig.ZombieRaid cfg = EventConfig.raid();
		RandomSource random = level.random;
		int count = 0;
		if (random.nextDouble() < cfg.cursedZombieEssenceChance) {
			count = 1;
			if (random.nextDouble() < cfg.cursedZombieBonusEssenceChance) {
				count = 2;
			}
		}
		dropEssence(entity, level, count);
	}

	// ---------------- layer 2: boss rewards ----------------

	private static void dropBossRewards(EmpoweredZombie boss, ZombieRaid raid, ServerLevel level,
			RandomSource random, EventConfig.ZombieRaid cfg) {
		boolean finalBoss = boss.isFinalBoss();
		dropEssence(boss, level, finalBoss
				? randomBetween(random, cfg.graveEssenceFromFinalBossMin, cfg.graveEssenceFromFinalBossMax)
				: randomBetween(random, cfg.graveEssenceFromBossMin, cfg.graveEssenceFromBossMax));

		// Every Powered Zombie Boss drops a core that remembers its power.
		drop(level, boss, CorruptedPowerCoreItem.of(GraveItems.CORRUPTED_POWER_CORE, boss.powerKey()));
		if (!boss.secondPowerKey().isEmpty()) {
			drop(level, boss, CorruptedPowerCoreItem.of(GraveItems.CORRUPTED_POWER_CORE, boss.secondPowerKey()));
		}

		if (finalBoss || random.nextDouble() < cfg.bossTrophyChance) {
			drop(level, boss, BossTrophyItem.of(
					finalBoss ? GraveItems.FINAL_BOSS_TROPHY : GraveItems.BOSS_TROPHY, boss.powerKey()));
		}

		// Research progress for every participant present for the kill.
		for (ServerPlayer player : raid.participants().present()) {
			advanceResearch(player, boss.powerKey());
			if (!boss.secondPowerKey().isEmpty()) {
				advanceResearch(player, boss.secondPowerKey());
			}
		}
		if (raid.bossesDefeated() >= 3) {
			for (ServerPlayer player : raid.participants().onlineEligible(level)) {
				GraveboundCurse.award(player, "gravebound/power_breaker");
			}
		}
		level.playSound(null, boss.blockPosition(), SoundEvents.TOTEM_USE, SoundSource.HOSTILE, 1.0f, 0.6f);
	}

	/**
	 * Power research (spec section 38). Each boss kill of that power adds a chunk of progress; the
	 * player is told where they are, and told once when it completes.
	 */
	private static void advanceResearch(ServerPlayer player, String powerKey) {
		if (powerKey == null || powerKey.isEmpty()) {
			return;
		}
		GraveboundState state = GraveboundCurse.state(player).copy();
		int before = state.powerResearch.getOrDefault(powerKey, 0);
		if (before >= 100) {
			return;
		}
		int after = Math.min(100, before + RESEARCH_PER_KILL);
		state.powerResearch.put(powerKey, after);
		GraveboundCurse.save(player, state);

		player.sendSystemMessage(Component.translatable("message.projecthero.research.progress",
				BossPowers.displayName(powerKey), after).withStyle(ChatFormatting.AQUA));
		if (after >= 100) {
			player.sendSystemMessage(Component.translatable("message.projecthero.research.complete",
					BossPowers.displayName(powerKey)).withStyle(ChatFormatting.GOLD));
			GraveboundCurse.award(player, "gravebound/power_analysis");
		}
	}

	/** Four boss kills of the same power to fully analyse it. */
	private static final int RESEARCH_PER_KILL = 25;

	// ---------------- layer 3: completion ----------------

	/**
	 * Wave 12 is down. Places the Cursed Grave Chest, hands out the first-clear reward and awards the
	 * advancements. Called exactly once per raid, guarded by the raid's own persisted flag.
	 */
	public static void grantCompletion(ServerLevel level, ZombieRaid raid) {
		List<ServerPlayer> winners = raid.participants().onlineEligible(level);
		BlockPos chestPos = placeChest(level, raid);

		for (ServerPlayer player : winners) {
			GraveboundState state = GraveboundCurse.state(player).copy();
			state.raidsCompleted++;
			boolean firstClear = !state.heartOfTheGraveGranted;
			if (firstClear) {
				state.heartOfTheGraveGranted = true;
			}
			GraveboundCurse.save(player, state);

			if (firstClear) {
				give(player, new ItemStack(GraveItems.HEART_OF_THE_GRAVE));
				player.sendSystemMessage(Component.translatable("message.projecthero.raid.first_clear")
						.withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
			}

			GraveboundCurse.award(player, "gravebound/zombie_slayer");
			if (raid.participants().deaths(player.getUUID()) == 0) {
				GraveboundCurse.award(player, "gravebound/deathless");
			}
			if (raid.participants().eligibleCount() == 1) {
				GraveboundCurse.award(player, "gravebound/one_man_army");
			}
			if (raid.participants().eligibleCount() >= 4) {
				GraveboundCurse.award(player, "gravebound/last_stand");
			}
			if (state.raidsCompleted >= 10) {
				GraveboundCurse.award(player, "gravebound/gravewalker");
			}
			if (chestPos != null) {
				player.sendSystemMessage(Component.translatable("message.projecthero.raid.chest",
						chestPos.getX(), chestPos.getY(), chestPos.getZ()).withStyle(ChatFormatting.GOLD));
			}
		}
		EventSavedData.get(level).noteCompleted();
	}

	/**
	 * The Cursed Grave Chest. Uses a data-driven loot table for the bulk of its contents, then stamps
	 * in the two guaranteed items that cannot be expressed in a loot table -- the Grave Essence bundle
	 * and a Corrupted Power Core carrying the final boss's actual power.
	 */
	private static BlockPos placeChest(ServerLevel level, ZombieRaid raid) {
		BlockPos pos = chestSite(level, raid.center());
		if (pos == null) {
			return null;
		}
		level.setBlock(pos, Blocks.CHEST.defaultBlockState()
				.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH), net.minecraft.world.level.block.Block.UPDATE_ALL);
		if (level.getBlockEntity(pos) instanceof ChestBlockEntity chest) {
			chest.setLootTable(COMPLETION_LOOT, level.random.nextLong());
			// Force the table to roll now so the guaranteed items can be added alongside it, rather
			// than being wiped when the table unpacks on first open.
			chest.unpackLootTable(null);

			int essence = randomBetween(level.random, 30, 60);
			addToChest(chest, new ItemStack(GraveItems.GRAVE_ESSENCE, Math.min(64, essence)));
			if (essence > 64) {
				addToChest(chest, new ItemStack(GraveItems.GRAVE_ESSENCE, essence - 64));
			}
			String power = raid.finalBossPower();
			if (!power.isEmpty()) {
				addToChest(chest, CorruptedPowerCoreItem.of(GraveItems.CORRUPTED_POWER_CORE, power));
			}
			chest.setChanged();
		}
		level.playSound(null, pos, SoundEvents.RESPAWN_ANCHOR_SET_SPAWN, SoundSource.BLOCKS, 1.0f, 0.8f);
		level.sendParticles(net.minecraft.core.particles.ParticleTypes.SOUL_FIRE_FLAME,
				pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 40, 0.5, 0.6, 0.5, 0.03);
		return pos;
	}

	private static void addToChest(ChestBlockEntity chest, ItemStack stack) {
		for (int slot = 0; slot < chest.getContainerSize(); slot++) {
			if (chest.getItem(slot).isEmpty()) {
				chest.setItem(slot, stack);
				return;
			}
		}
	}

	/** A legal, visible spot for the chest: the surface at the raid centre, or a short search around it. */
	private static BlockPos chestSite(ServerLevel level, BlockPos center) {
		for (int radius = 0; radius <= 3; radius++) {
			for (int dx = -radius; dx <= radius; dx++) {
				for (int dz = -radius; dz <= radius; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
						continue;
					}
					int x = center.getX() + dx;
					int z = center.getZ() + dz;
					if (!level.isLoaded(new BlockPos(x, center.getY(), z))) {
						continue;
					}
					BlockPos surface = new BlockPos(x, level.getHeight(
							Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
					if (level.getBlockState(surface).canBeReplaced()
							&& level.getBlockState(surface.below()).isSolidRender(level, surface.below())) {
						return surface;
					}
				}
			}
		}
		return null;
	}

	// ---------------- helpers ----------------

	private static void dropEssence(Entity entity, ServerLevel level, int count) {
		if (count <= 0) {
			return;
		}
		drop(level, entity, new ItemStack(GraveItems.GRAVE_ESSENCE, count));
	}

	private static void drop(ServerLevel level, Entity at, ItemStack stack) {
		if (stack.isEmpty()) {
			return;
		}
		ItemEntity item = new ItemEntity(level, at.getX(), at.getY() + 0.5, at.getZ(), stack);
		item.setDefaultPickUpDelay();
		level.addFreshEntity(item);
	}

	private static void give(ServerPlayer player, ItemStack stack) {
		if (!player.getInventory().add(stack)) {
			player.drop(stack, false);
		}
	}

	private static int randomBetween(RandomSource random, int min, int max) {
		if (max <= min) {
			return Math.max(0, min);
		}
		return min + random.nextInt(max - min + 1);
	}

	/** Record a death for the "Deathless" advancement, if this player is in a raid. */
	public static void onPlayerDied(ServerPlayer player) {
		EventInstance event = EventManager.forPlayer(player);
		if (event != null) {
			event.participants().recordDeath(player.getUUID());
		}
	}

	/** Exposed for the debug command: hand a player Grave Essence. */
	public static void giveEssence(ServerPlayer player, int count) {
		give(player, new ItemStack(GraveItems.GRAVE_ESSENCE, count));
	}

	/** Exposed for the debug command / research display. */
	public static int research(ServerPlayer player, String powerKey) {
		Integer value = GraveboundCurse.state(player).powerResearch.get(powerKey);
		return value == null ? 0 : value;
	}

}
