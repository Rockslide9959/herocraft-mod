package com.projecthero.mod.city;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * v0.16.2: the Food Bank's stock (food points) and its once-every-5-seconds work -- calling villagers with spare food over
 * to drop it off, and turning every {@link #NEWCOMER_COST} food into a newborn villager while a bed nearby is free.
 */
public class FoodBankBlockEntity extends BlockEntity {
	/** Food points per newborn (12 loaves of bread). */
	public static final int NEWCOMER_COST = 60;
	public static final int CAPACITY = 640;
	/** At most one newborn per bank this often (one in-game hour). */
	public static final int NEWCOMER_COOLDOWN_TICKS = 1000;
	/** Villagers within this many blocks bring their spare food here. */
	public static final int COLLECT_RADIUS = 32;
	/** Beds and villagers counted within this many blocks. */
	public static final int HOME_RADIUS = 48;
	/** Never grows a town past this many villagers within {@link #HOME_RADIUS}. */
	public static final int MAX_POPULATION = 60;
	/** What a villager keeps for itself (vanilla villagers need about this much to breed on their own). */
	private static final int VILLAGER_KEEPS = 12;
	private static final double HAND_OVER = 2.6;

	private int food;
	private long lastNewcomer = Long.MIN_VALUE / 2;

	public FoodBankBlockEntity(BlockPos pos, BlockState state) {
		super(CityBlocks.FOOD_BANK_BE, pos, state);
	}

	public int food() {
		return food;
	}

	public void setFoodForTest(int food) {
		this.food = food;
		setChanged();
	}

	/** Food points an item is worth here: its nutrition (wheat 2, a hay bale 18); nothing that hurts the eater. */
	public static int value(ItemStack stack) {
		if (stack.is(Items.WHEAT)) {
			return 2;
		}
		if (stack.is(Items.HAY_BLOCK)) {
			return 18;
		}
		FoodProperties fp = stack.get(DataComponents.FOOD);
		if (fp == null || !fp.effects().isEmpty() || stack.is(Items.ROTTEN_FLESH) || stack.is(Items.SPIDER_EYE) || stack.is(Items.POISONOUS_POTATO)
				|| stack.is(Items.PUFFERFISH)) {
			return 0;
		}
		return fp.nutrition();
	}

	/** Puts as much of {@code stack} in as fits (shrinking it if {@code consume}); the food points added. */
	public int deposit(ItemStack stack, boolean consume) {
		int each = value(stack);
		if (each <= 0 || food >= CAPACITY) {
			return 0;
		}
		int n = Math.min(stack.getCount(), Math.max(1, (CAPACITY - food + each - 1) / each));
		if (consume) {
			stack.shrink(n);
		}
		food = Math.min(CAPACITY, food + n * each);
		setChanged();
		return n * each;
	}

	void serverTick(ServerLevel level) {
		if ((level.getGameTime() + worldPosition.asLong()) % 100 != 0) {
			return;
		}
		collect(level);
		tryNewcomer(level);
	}

	/** Villagers carrying more food than they keep come over and hand the rest in. */
	private void collect(ServerLevel level) {
		if (food >= CAPACITY || level.isNight()) {
			return;
		}
		AABB box = new AABB(worldPosition).inflate(COLLECT_RADIUS);
		for (Villager v : level.getEntitiesOfClass(Villager.class, box, v -> v.isAlive() && !v.isBaby() && !v.isSleeping())) {
			int spare = carried(v.getInventory()) - VILLAGER_KEEPS;
			if (spare <= 0) {
				continue;
			}
			if (v.position().distanceTo(worldPosition.getCenter()) <= HAND_OVER) {
				int handed = handOver(v.getInventory());
				if (handed > 0) {
					v.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
					level.playSound(null, worldPosition, SoundEvents.COMPOSTER_FILL_SUCCESS, SoundSource.BLOCKS, 0.8f, 1.1f);
					level.sendParticles(ParticleTypes.HAPPY_VILLAGER, worldPosition.getX() + 0.5, worldPosition.getY() + 1.1, worldPosition.getZ() + 0.5, 5,
							0.3, 0.2, 0.3, 0.02);
				}
			} else if (spare >= VILLAGER_KEEPS && !v.isNoAi()) {
				v.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(worldPosition, 0.55f, 1));
			}
		}
	}

	private static int carried(SimpleContainer inv) {
		int total = 0;
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack s = inv.getItem(i);
			total += value(s) * s.getCount();
		}
		return total;
	}

	/** Takes the villager's food down to what it keeps; the food points handed over. */
	private int handOver(SimpleContainer inv) {
		int before = food;
		for (int i = 0; i < inv.getContainerSize() && food < CAPACITY; i++) {
			ItemStack s = inv.getItem(i);
			int each = value(s);
			while (each > 0 && !s.isEmpty() && carried(inv) - each >= VILLAGER_KEEPS && food < CAPACITY) {
				s.shrink(1);
				food = Math.min(CAPACITY, food + each);
			}
		}
		if (food != before) {
			inv.setChanged();
			setChanged();
		}
		return food - before;
	}

	/** Free beds around the bank, minus the villagers already waiting for one. */
	public int roomForNewcomers(ServerLevel level) {
		PoiManager poi = level.getPoiManager();
		long free = poi.getCountInRange(h -> h.is(PoiTypes.HOME), worldPosition, HOME_RADIUS, PoiManager.Occupancy.HAS_SPACE);
		List<Villager> people = level.getEntitiesOfClass(Villager.class, new AABB(worldPosition).inflate(HOME_RADIUS), Villager::isAlive);
		if (people.size() >= MAX_POPULATION) {
			return 0;
		}
		long homeless = people.stream().filter(v -> v.getBrain().getMemory(MemoryModuleType.HOME).isEmpty()).count();
		return (int) Math.max(0, Math.min(free - homeless, MAX_POPULATION - people.size()));
	}

	private void tryNewcomer(ServerLevel level) {
		if (food < NEWCOMER_COST || level.getGameTime() - lastNewcomer < NEWCOMER_COOLDOWN_TICKS || level.isRaided(worldPosition)) {
			return;
		}
		if (roomForNewcomers(level) <= 0) {
			return;
		}
		Villager born = spawnNewcomer(level);
		if (born != null) {
			food -= NEWCOMER_COST;
			lastNewcomer = level.getGameTime();
			setChanged();
		}
	}

	/** A baby villager next to the bank (null if there is no room to stand). Tests call this directly. */
	public Villager spawnNewcomer(ServerLevel level) {
		BlockPos spot = null;
		for (BlockPos p : BlockPos.withinManhattan(worldPosition, 2, 1, 2)) {
			if (level.getBlockState(p).getCollisionShape(level, p).isEmpty() && level.getBlockState(p.above()).getCollisionShape(level, p.above()).isEmpty()
					&& level.getBlockState(p.below()).isFaceSturdy(level, p.below(), Direction.UP)) {
				spot = p.immutable();
				break;
			}
		}
		if (spot == null) {
			return null;
		}
		Villager v = EntityType.VILLAGER.create(level);
		if (v == null) {
			return null;
		}
		v.moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, level.random.nextFloat() * 360f, 0f);
		v.finalizeSpawn(level, level.getCurrentDifficultyAt(spot), MobSpawnType.BREEDING, null);
		v.setAge(-24000);
		level.addFreshEntity(v);
		level.sendParticles(ParticleTypes.HEART, v.getX(), v.getY() + 0.8, v.getZ(), 7, 0.4, 0.4, 0.4, 0.02);
		level.playSound(null, spot, SoundEvents.VILLAGER_CELEBRATE, SoundSource.NEUTRAL, 1.0f, 1.3f);
		return v;
	}

	/** The line the bank shows when used. */
	public Component statusLine(ServerLevel level) {
		Component tail;
		if (food >= NEWCOMER_COST) {
			tail = roomForNewcomers(level) > 0 ? Component.translatable("block.projecthero.food_bank.ready")
					: Component.translatable("block.projecthero.food_bank.no_beds");
		} else {
			tail = Component.translatable("block.projecthero.food_bank.need", NEWCOMER_COST - food);
		}
		return Component.translatable("block.projecthero.food_bank.status", food, NEWCOMER_COST, tail).withStyle(ChatFormatting.GOLD);
	}

	@Override
	protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		tag.putInt("Food", food);
		tag.putLong("LastNewcomer", lastNewcomer);
	}

	@Override
	protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		food = tag.getInt("Food");
		lastNewcomer = tag.contains("LastNewcomer") ? tag.getLong("LastNewcomer") : Long.MIN_VALUE / 2;
	}
}
