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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.entity.player.Player;
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
	/** v0.14.4: anywhere this close to a village bell counts as "in the village" (see {@link #insideVillage}). */
	public static final int VILLAGE_BELL_RADIUS = 64;
	/** v0.14.4: the spy does not despawn while a player is at least this close. */
	public static final int LINGER_RANGE = 96;
	/** v0.14.4: the spy stops walking once it is this close to the bell it is heading for. */
	private static final int ARRIVE_DISTANCE = 24;

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

		// v0.14.4: the spy is a scout, not a raider. Vanilla's Pillager goals made it open fire on the
		// first player it saw -- and PillagerSpySpawner puts it 32-56 blocks from a player who is, by
		// construction, OUTSIDE the village, so almost every natural spy spent itself shooting at someone
		// in the fields (a hit outside a village does nothing), got killed, and never marked anything.
		// It also shot villagers, emptying the very village it was scouting. Now it only picks a fight
		// with a player who is standing in a village (the one hit that means something), still shoots
		// back at anyone who attacks it (HurtByTargetGoal is untouched), and still fights iron golems.
		this.targetSelector.removeAllGoals(goal -> goal instanceof NearestAttackableTargetGoal<?>);
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false,
				this::isVillageTarget));
		this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, IronGolem.class, true));
	}

	/**
	 * v0.14.4: a spy lingers while a player is within {@link #LINGER_RANGE} blocks, instead of taking
	 * vanilla's random despawn roll the moment it is 32 blocks from everyone. It spawns 32-56 blocks
	 * from the player and walks on to the village, so the old rule usually deleted it before it ever
	 * got there. Still never persistent: with nobody within range it despawns exactly as before.
	 */
	@Override
	public boolean removeWhenFarAway(double distanceToClosestPlayer) {
		return distanceToClosestPlayer > LINGER_RANGE * LINGER_RANGE;
	}

	/** Target filter: only a player standing in a village is worth shooting at unprovoked. */
	private boolean isVillageTarget(LivingEntity candidate) {
		if (!(level() instanceof ServerLevel server)) {
			return false;
		}
		double follow = getAttributeValue(Attributes.FOLLOW_RANGE);
		if (distanceToSqr(candidate) > follow * follow) {
			return false; // cheap reject before any village lookup
		}
		BlockPos at = candidate.blockPosition();
		// The bell this spy is already walking to is the cheap answer; fall back to the full test.
		if (villageGoal != null && nearBell(at, villageGoal)) {
			return true;
		}
		return insideVillage(server, at);
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

	/**
	 * Whether {@code pos} is inside a village. Used by the raid trigger, the spy's targeting and the
	 * natural spawner.
	 *
	 * <p>v0.14.4 -- the real reason Supervillain Raids stopped repeating. This used to be just vanilla's
	 * {@link ServerLevel#isVillage}, which only counts village POIs (beds, job sites, the bell) that are
	 * <em>claimed by a living villager</em> ({@code PoiManager.isVillageCenter} filters on
	 * {@code Occupancy.IS_OCCUPIED}, and a villager releases all its POIs when it dies). A Supervillain
	 * Raid's Vindicators, Evokers and Ravagers kill villagers, so after beating one the village was very
	 * often no longer a "village" at all: spies kept spawning (the spawner finds the bell regardless of
	 * occupancy) but their hits could never mark it again. A village is now also anywhere within
	 * {@link #VILLAGE_BELL_RADIUS} blocks of a bell -- which a raided, even villager-less, village still
	 * has -- on top of vanilla's occupied-POI test.
	 */
	public static boolean insideVillage(ServerLevel level, BlockPos pos) {
		if (level.isVillage(pos)) {
			return true;
		}
		return level.getPoiManager().findClosest(holder -> holder.is(PoiTypes.MEETING), pos,
				VILLAGE_BELL_RADIUS, PoiManager.Occupancy.ANY).isPresent();
	}

	private static boolean nearBell(BlockPos at, BlockPos bell) {
		return at.distSqr(bell) <= (double) VILLAGE_BELL_RADIUS * VILLAGE_BELL_RADIUS;
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
			BlockPos goal = locateVillage(server);
			if (goal == null) {
				return false;
			}
			// v0.14.4: "arrived" = near the bell it is heading for (or in vanilla's occupied village). The
			// old test was vanilla isVillage alone, which never becomes true in a village whose villagers
			// are dead, so the spy used to circle a raided village forever.
			return blockPosition().distSqr(goal) > ARRIVE_DISTANCE * ARRIVE_DISTANCE
					&& !server.isVillage(blockPosition());
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

	/**
	 * v0.12.23: the spy marks a village when it ATTACKS a player in one, not only when its arrow deals damage --
	 * a hero power that dodges or soaks the bolt used to mean the village was never marked.
	 */
	@Override
	public void performRangedAttack(net.minecraft.world.entity.LivingEntity target, float velocity) {
		super.performRangedAttack(target, velocity);
		if (target instanceof ServerPlayer player && level() instanceof ServerLevel level
				&& !player.isCreative() && !player.isSpectator()) {
			notifyDamagedPlayer(level, player);
		}
	}

	// Hook used by the damage listener: give the escort/leader a way to identify a raid trigger.
	public void notifyDamagedPlayer(ServerLevel level, ServerPlayer player) {
		SupervillainRaidStarter.onSpyHitPlayer(level, this, player);
	}
}
