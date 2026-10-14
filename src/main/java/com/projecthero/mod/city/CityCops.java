package com.projecthero.mod.city;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.crime.CrimeAI;
import com.projecthero.mod.crime.CrimeEvent;
import com.projecthero.mod.crime.Crimes;
import com.projecthero.mod.event.EventManager;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.2 City part B: City Cop villagers ({@code projecthero:city_cop}, job site the Precinct Desk).
 *
 * <ul>
 * <li><b>Stats:</b> a weaker iron golem in a villager body -- 40 HP, 8 armour, a baton (6 damage, one swing a second,
 * 2.4 reach), slow out-of-combat healing. They only flee (vanilla panic) below a quarter of their health.</li>
 * <li><b>Who they fight:</b> monsters within 12 blocks, crime crooks (only once the crime has run 20 s, and at half
 * damage, so the hero still matters), and a player only after that player hit them (within the last 10 s).</li>
 * <li><b>Patrol:</b> in work hours an idle cop walks to random spots within 32 blocks of its desk.</li>
 * <li><b>Crime calls:</b> {@link Crimes#addListener} tells them when a crime begins; cops within 64 blocks head to the
 * scene.</li>
 * </ul>
 */
public final class CityCops {
	static final ResourceLocation HEALTH_ID = ProjectHeroMod.id("city_cop_health");
	static final ResourceLocation ARMOR_ID = ProjectHeroMod.id("city_cop_armor");

	private static final Map<UUID, Long> LAST_ATTACK = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> LAST_COMBAT = new ConcurrentHashMap<>();
	/** cop -> crime scene it is answering (until the crime ends). */
	private static final Map<UUID, UUID> RESPONDING = new ConcurrentHashMap<>();

	private CityCops() {
	}

	public static boolean isCop(LivingEntity e) {
		return e instanceof Villager v && v.getVillagerData().getProfession() == CityBlocks.CITY_COP;
	}

	/** {@code CityVillagerPanicMixin}: a cop above {@code fleeHealthFraction} never panics at monsters or wounds. */
	public static boolean bravesIt(LivingEntity e) {
		return isCop(e) && e.getHealth() >= e.getMaxHealth() * CityConfig.cops().fleeHealthFraction;
	}

	/** Every villager, every 100 ticks: cops get their health / armour modifiers, ex-cops lose them. */
	static void syncStats(Villager v) {
		boolean cop = isCop(v);
		CityConfig.Cops cfg = CityConfig.cops();
		AttributeInstance health = v.getAttribute(Attributes.MAX_HEALTH);
		AttributeInstance armor = v.getAttribute(Attributes.ARMOR);
		if (health == null || armor == null) {
			return;
		}
		if (cop) {
			if (!health.hasModifier(HEALTH_ID)) {
				health.addPermanentModifier(new AttributeModifier(HEALTH_ID, cfg.maxHealth - 20.0, AttributeModifier.Operation.ADD_VALUE));
				v.setHealth(v.getMaxHealth());
			}
			if (!armor.hasModifier(ARMOR_ID)) {
				armor.addPermanentModifier(new AttributeModifier(ARMOR_ID, cfg.armor, AttributeModifier.Operation.ADD_VALUE));
			}
			if (v.getMainHandItem().isEmpty()) {
				v.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STICK));
				v.setDropChance(EquipmentSlot.MAINHAND, 0f);
			}
		} else {
			if (health.hasModifier(HEALTH_ID)) {
				health.removeModifier(HEALTH_ID);
				v.setHealth(Math.min(v.getHealth(), v.getMaxHealth()));
				if (v.getMainHandItem().is(Items.STICK)) {
					v.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
				}
			}
			if (armor.hasModifier(ARMOR_ID)) {
				armor.removeModifier(ARMOR_ID);
			}
		}
	}

	/** A cop's tick (from {@code CityVillagerMixin}, AI on). */
	static void tick(Villager cop, ServerLevel level) {
		if (cop.isBaby() || cop.isSleeping()) {
			return;
		}
		CityConfig.Cops cfg = CityConfig.cops();
		long now = level.getGameTime();
		LivingEntity target = cop.getTarget();
		if (target != null && !stillValid(cop, target, cfg)) {
			cop.setTarget(null);
			target = null;
		}
		if (cop.getHealth() < cop.getMaxHealth() * cfg.fleeHealthFraction) {
			cop.setTarget(null); // hurt: vanilla panic takes over
			return;
		}
		if (target == null && (cop.tickCount + cop.getId()) % 10 == 0) {
			target = findTarget(cop, level, cfg);
			cop.setTarget(target);
		}
		if (target != null) {
			LAST_COMBAT.put(cop.getUUID(), now);
			fight(cop, level, target, cfg, now);
			return;
		}
		// out of combat: heal, answer crime calls, patrol
		if (cop.tickCount % 20 == 0 && now - LAST_COMBAT.getOrDefault(cop.getUUID(), Long.MIN_VALUE / 2) > 100 && cop.getHealth() < cop.getMaxHealth()) {
			cop.heal(cfg.regenPerSecond);
		}
		if ((cop.tickCount + cop.getId()) % 20 == 0 && respond(cop, level, cfg)) {
			return;
		}
		if ((cop.tickCount + cop.getId()) % 200 == 0 && CityRepair.isWorkHours(level) && !cop.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET)) {
			patrol(cop, level, cfg);
		}
	}

	private static boolean stillValid(Villager cop, LivingEntity t, CityConfig.Cops cfg) {
		if (!t.isAlive() || t.level() != cop.level() || t.distanceToSqr(cop) > 32.0 * 32.0) {
			return false;
		}
		if (t instanceof Player p) {
			return !p.isCreative() && !p.isSpectator() && attackedBy(cop, p);
		}
		return true;
	}

	private static boolean attackedBy(Villager cop, Player p) {
		return cop.getLastHurtByMob() == p && cop.tickCount - cop.getLastHurtByMobTimestamp() < 200;
	}

	/** True if a crime crook may be engaged yet (its crime has run {@code crimeEngageDelaySeconds}). */
	public static boolean crookEngageable(Mob crook) {
		if (!crook.getTags().contains(CrimeAI.CROOK_TAG)) {
			return true;
		}
		return EventManager.owning(crook) instanceof CrimeEvent crime && crime.state().active()
				&& crime.runTicks() >= CityConfig.cops().crimeEngageDelaySeconds * 20L;
	}

	public static LivingEntity findTarget(Villager cop, ServerLevel level, CityConfig.Cops cfg) {
		LivingEntity attacker = cop.getLastHurtByMob();
		if (attacker instanceof Player p && attacker.isAlive() && !p.isCreative() && !p.isSpectator() && attackedBy(cop, p)) {
			return p;
		}
		double range = Math.max(cfg.monsterRange, 16);
		List<Mob> mobs = level.getEntitiesOfClass(Mob.class, cop.getBoundingBox().inflate(range), m -> m.isAlive() && m instanceof Enemy);
		Mob best = null;
		double bestD = Double.MAX_VALUE;
		for (Mob m : mobs) {
			boolean crook = m.getTags().contains(CrimeAI.CROOK_TAG);
			double d = m.distanceToSqr(cop);
			double limit = crook ? 16.0 : cfg.monsterRange;
			if (d > limit * limit || (crook && !crookEngageable(m)) || !cop.hasLineOfSight(m)) {
				continue;
			}
			if (d < bestD) {
				bestD = d;
				best = m;
			}
		}
		return best;
	}

	private static void fight(Villager cop, ServerLevel level, LivingEntity t, CityConfig.Cops cfg, long now) {
		if ((cop.tickCount & 3) == 0) {
			cop.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(t, 0.75f, 1));
			cop.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(t, true));
		}
		double reach = cfg.attackReach + t.getBbWidth() * 0.5;
		if (cop.distanceToSqr(t) > reach * reach || !cop.hasLineOfSight(t)) {
			return;
		}
		long last = LAST_ATTACK.getOrDefault(cop.getUUID(), Long.MIN_VALUE / 2);
		if (now - last < cfg.attackCooldownTicks) {
			return;
		}
		LAST_ATTACK.put(cop.getUUID(), now);
		float damage = cfg.attackDamage;
		if (t.getTags().contains(CrimeAI.CROOK_TAG)) {
			damage *= cfg.crookDamageMultiplier;
		}
		cop.swing(InteractionHand.MAIN_HAND, true);
		cop.getLookControl().setLookAt(t);
		if (t.hurt(level.damageSources().mobAttack(cop), damage)) {
			t.knockback(0.4, cop.getX() - t.getX(), cop.getZ() - t.getZ());
			level.playSound(null, t.blockPosition(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.NEUTRAL, 0.7f, 1.1f);
		}
	}

	/** Heads to a crime this cop was called to (or any running crime in range). True while it is answering one. */
	private static boolean respond(Villager cop, ServerLevel level, CityConfig.Cops cfg) {
		double range = cfg.crimeResponseRange;
		CrimeEvent nearest = null;
		double bestD = Double.MAX_VALUE;
		for (CrimeEvent c : Crimes.active(level.getServer())) {
			if (c.dimension() != level.dimension() || !c.state().active()) {
				continue;
			}
			double d = Vec3.atCenterOf(c.center()).distanceToSqr(cop.position());
			if (d <= range * range && d < bestD) {
				bestD = d;
				nearest = c;
			}
		}
		if (nearest == null) {
			RESPONDING.remove(cop.getUUID());
			return false;
		}
		RESPONDING.put(cop.getUUID(), nearest.id());
		BlockPos scene = nearest.center();
		List<Mob> crooks = nearest.crooks(level);
		if (!crooks.isEmpty()) {
			Mob closest = crooks.get(0);
			for (Mob m : crooks) {
				if (m.distanceToSqr(cop) < closest.distanceToSqr(cop)) {
					closest = m;
				}
			}
			scene = closest.blockPosition();
		}
		if (cop.distanceToSqr(Vec3.atCenterOf(scene)) > 6.0 * 6.0) {
			cop.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(scene, 0.7f, 4));
		}
		return true;
	}

	private static void patrol(Villager cop, ServerLevel level, CityConfig.Cops cfg) {
		Optional<GlobalPos> site = cop.getBrain().getMemory(MemoryModuleType.JOB_SITE);
		if (site.isEmpty() || site.get().dimension() != level.dimension()) {
			return;
		}
		BlockPos desk = site.get().pos();
		int r = Math.max(4, cfg.patrolRadius);
		int x = desk.getX() + level.random.nextInt(2 * r + 1) - r;
		int z = desk.getZ() + level.random.nextInt(2 * r + 1) - r;
		if (!level.hasChunk(x >> 4, z >> 4)) {
			return;
		}
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
		if (Math.abs(y - desk.getY()) > 12) {
			return; // a roof or a pit, not a street
		}
		cop.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(new BlockPos(x, y, z), 0.5f, 2));
	}

	/** {@link Crimes#addListener}: a crime has begun -- cops in range are called to it right away. */
	static void onCrimeBegin(ServerLevel level, CrimeEvent crime) {
		double range = CityConfig.cops().crimeResponseRange;
		for (Villager cop : level.getEntitiesOfClass(Villager.class, new AABB(crime.center()).inflate(range), CityCops::isCop)) {
			RESPONDING.put(cop.getUUID(), crime.id());
			cop.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(crime.center(), 0.7f, 4));
		}
	}

	/** True if {@code cop} is answering a crime call. */
	public static boolean responding(Villager cop) {
		return RESPONDING.containsKey(cop.getUUID());
	}

	static void forget(Villager cop) {
		LAST_ATTACK.remove(cop.getUUID());
		LAST_COMBAT.remove(cop.getUUID());
		RESPONDING.remove(cop.getUUID());
	}

	public static void clearSessionState() {
		LAST_ATTACK.clear();
		LAST_COMBAT.clear();
		RESPONDING.clear();
	}
}
