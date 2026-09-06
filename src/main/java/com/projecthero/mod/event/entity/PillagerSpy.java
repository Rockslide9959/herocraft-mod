package com.projecthero.mod.event.entity;

import java.util.Optional;

import com.projecthero.mod.event.raid.SupervillainRaidStarter;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.level.Level;

/**
 * The Pillager Spy: a rare Pillager variant scouting villages for a Supervillain. It looks almost
 * exactly like a Pillager -- an observant player might notice the darker robe, the faint purple
 * ambient particle, and the name on the crosshair -- and behaves like one in a fight. Its purpose is
 * different: it heads for the nearest village and tries to land a hit on a player standing inside it.
 * When it does, {@link SupervillainRaidStarter#onSpyHitPlayer} marks that village for a Supervillain
 * Raid.
 *
 * <p>Deliberately <b>not</b> persistent: a rare hostile natural spawn that saved forever would pile
 * up in a long-lived world (same reasoning as {@code CursedZombie}).
 */
public class PillagerSpy extends Pillager {
	/** How far the spy will look for a village to travel toward. */
	private static final int VILLAGE_SEARCH = 128;

	private BlockPos villageGoal;
	private int villageRecheck;

	public PillagerSpy(EntityType<? extends PillagerSpy> type, Level level) {
		super(type, level);
		this.setCustomName(Component.translatable("entity.projecthero.pillager_spy"));
		this.xpReward = 8;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Pillager.createAttributes()
				.add(Attributes.MAX_HEALTH, 26.0)
				.add(Attributes.MOVEMENT_SPEED, 0.34)
				.add(Attributes.FOLLOW_RANGE, 40.0);
	}

	@Override
	protected void registerGoals() {
		super.registerGoals();
		// Priority 4: below its own self-defence / crossbow goals, above idle wandering -- it defends
		// itself first, but when nothing is threatening it, it makes for the village.
		this.goalSelector.addGoal(4, new SeekVillageGoal());
	}

	@Override
	public boolean removeWhenFarAway(double distanceToClosestPlayer) {
		return true; // never persist
	}

	@Override
	public void aiStep() {
		super.aiStep();
		if (level().isClientSide()) {
			// A faint, infrequent purple tell.
			if (random.nextInt(40) == 0) {
				level().addParticle(ParticleTypes.WITCH,
						getX() + (random.nextDouble() - 0.5) * getBbWidth(),
						getY() + getBbHeight() * 0.9,
						getZ() + (random.nextDouble() - 0.5) * getBbWidth(),
						0.0, 0.0, 0.0);
			}
		}
	}

	/** Whether {@code pos} is inside a recognised village. Used by the raid trigger. */
	public static boolean insideVillage(ServerLevel level, BlockPos pos) {
		return level.isVillage(pos);
	}

	@Override
	public void addAdditionalSaveData(CompoundTag tag) {
		super.addAdditionalSaveData(tag);
		if (villageGoal != null) {
			tag.putLong("VillageGoal", villageGoal.asLong());
		}
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		if (tag.contains("VillageGoal")) {
			villageGoal = BlockPos.of(tag.getLong("VillageGoal"));
		}
	}

	/**
	 * Cautiously travel toward the nearest village meeting point. Re-locates the target every few
	 * seconds; stops when there is nothing in range or the spy is already standing in a village
	 * (from there its ordinary target goals take over and it goes for a player).
	 */
	private final class SeekVillageGoal extends Goal {
		@Override
		public boolean canUse() {
			if (getTarget() != null) {
				return false; // in a fight -- let the crossbow goals run
			}
			if (!(level() instanceof ServerLevel server)) {
				return false;
			}
			if (insideVillage(server, blockPosition())) {
				return false;
			}
			return locateVillage(server) != null;
		}

		@Override
		public boolean canContinueToUse() {
			return canUse() && !getNavigation().isDone();
		}

		@Override
		public void start() {
			if (villageGoal != null) {
				getNavigation().moveTo(villageGoal.getX() + 0.5, villageGoal.getY(), villageGoal.getZ() + 0.5, 0.85);
			}
		}

		@Override
		public void tick() {
			if (--villageRecheck <= 0) {
				villageRecheck = 100;
				if (level() instanceof ServerLevel server) {
					BlockPos found = locateVillage(server);
					if (found != null && (villageGoal == null || !found.equals(villageGoal))) {
						villageGoal = found;
						getNavigation().moveTo(found.getX() + 0.5, found.getY(), found.getZ() + 0.5, 0.85);
					}
				}
			}
			if (villageGoal != null && getNavigation().isDone()) {
				getNavigation().moveTo(villageGoal.getX() + 0.5, villageGoal.getY(), villageGoal.getZ() + 0.5, 0.85);
			}
		}

		private BlockPos locateVillage(ServerLevel server) {
			PoiManager poi = server.getPoiManager();
			Optional<BlockPos> meeting = poi.findClosest(
					holder -> holder.is(PoiTypes.MEETING), blockPosition(), VILLAGE_SEARCH, PoiManager.Occupancy.ANY);
			meeting.ifPresent(pos -> villageGoal = pos);
			return meeting.orElse(villageGoal);
		}
	}

	// Hook used by the damage listener: give the escort/leader a way to identify a raid trigger.
	public void notifyDamagedPlayer(ServerLevel level, ServerPlayer player) {
		SupervillainRaidStarter.onSpyHitPlayer(level, this, player);
	}
}
