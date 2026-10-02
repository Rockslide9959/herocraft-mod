package com.projecthero.mod.gametest;

import com.projecthero.mod.combat.HeroTargets;
import com.projecthero.mod.hero.HeroConfig;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.hero.power.p22.PlantManipulationHandlers;
import com.projecthero.mod.hero.power.p22.ThornSentryEntity;
import com.projecthero.mod.item.ModItems;
import com.projecthero.mod.power.ThorPowers;
import com.projecthero.mod.power.ThorTargets;
import com.projecthero.mod.squad.Squad;
import com.projecthero.mod.squad.SquadManager;
import com.projecthero.mod.worthiness.Worthiness;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * v0.14.20: the shared targeting rules ({@link HeroTargets}). Rule 1 ({@code canHarm}) -- aimed / deliberate attacks hit
 * every living thing (cows, villagers, golems, PvP players) except the owner, their pets/summons, squadmates, creative
 * players and armour stands. Rule 2 ({@code isHostile}) -- automatic and huge-area powers only pick threats.
 *
 * <p>Everything stays inside the 8x8x8 cage (within ~4 blocks of the middle); mobs are NoAI so nothing wanders, and the
 * mock players are moved off world spawn into the test area.
 */
public class TargetingV01420GameTests implements FabricGameTest {
	private static ServerPlayer at(GameTestHelper helper, double x, double z) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 v = helper.absoluteVec(new Vec3(x, 2.0, z));
		p.moveTo(v.x, v.y, v.z, 0f, 0f);
		return p;
	}

	private static <T extends Mob> T mob(GameTestHelper helper, EntityType<T> type, double x, double z) {
		T m = type.create(helper.getLevel());
		Vec3 v = helper.absoluteVec(new Vec3(x, 2.0, z));
		m.moveTo(v.x, v.y, v.z, 0f, 0f);
		m.setNoAi(true);
		m.setPersistenceRequired();
		helper.getLevel().addFreshEntity(m);
		return m;
	}

	private static ServerPlayer thor(GameTestHelper helper, double x, double z) {
		ServerPlayer p = at(helper, x, z);
		Worthiness.setScore(p, Worthiness.TEST_WORTHY_SCORE);
		ItemStack hammer = new ItemStack(ModItems.MJOLNIR);
		p.setItemInHand(InteractionHand.MAIN_HAND, hammer);
		ThorPowers.toggleBinding(p, hammer);
		return p;
	}

	private static Squad squad(GameTestHelper helper, ServerPlayer leader, ServerPlayer mate) {
		SquadManager squads = SquadManager.get(helper.getLevel().getServer());
		Squad squad = squads.create("tgt" + leader.getUUID().toString().substring(0, 8), leader.getUUID());
		squads.addMember(squad, mate.getUUID());
		return squad;
	}

	// ------------------------------------------------------------------ rule 1 vs rule 2

	@GameTest(template = EMPTY_STRUCTURE)
	public void aimedRuleCoversEveryLivingThingAutoRuleOnlyThreats(GameTestHelper helper) {
		ServerPlayer p = at(helper, 2.5, 2.5);
		Cow cow = mob(helper, EntityType.COW, 4.5, 2.5);
		Villager villager = mob(helper, EntityType.VILLAGER, 2.5, 4.5);
		IronGolem golem = mob(helper, EntityType.IRON_GOLEM, 5.0, 5.0);
		Zombie zombie = mob(helper, EntityType.ZOMBIE, 1.0, 5.0);
		ArmorStand stand = EntityType.ARMOR_STAND.create(helper.getLevel());
		stand.moveTo(helper.absoluteVec(new Vec3(1.0, 2.0, 1.0)));
		helper.getLevel().addFreshEntity(stand);

		helper.assertTrue(HeroTargets.canHarm(p, cow), "rule 1 hits a cow");
		helper.assertTrue(HeroTargets.canHarm(p, villager), "rule 1 hits a villager");
		helper.assertTrue(HeroTargets.canHarm(p, golem), "rule 1 hits an iron golem");
		helper.assertTrue(HeroTargets.canHarm(p, zombie), "rule 1 hits a zombie");
		helper.assertFalse(HeroTargets.canHarm(p, p), "never the owner");
		helper.assertFalse(HeroTargets.canHarm(p, stand), "never an armour stand");

		helper.assertFalse(HeroTargets.isHostile(p, cow), "rule 2 leaves the cow alone");
		helper.assertFalse(HeroTargets.isHostile(p, villager), "rule 2 leaves the villager alone");
		helper.assertFalse(HeroTargets.isHostile(p, golem), "rule 2 leaves a calm golem alone");
		helper.assertTrue(HeroTargets.isHostile(p, zombie), "rule 2 picks the zombie");

		var around = AbilityHelpers.enemiesAround(p, p.position(), 6.0);
		var hostile = AbilityHelpers.hostilesAround(p, p.position(), 6.0);
		helper.assertTrue(around.contains(cow) && around.contains(villager) && around.contains(golem),
				"enemiesAround (rule 1) lists the passive mobs");
		helper.assertTrue(hostile.contains(zombie) && !hostile.contains(cow) && !hostile.contains(villager)
				&& !hostile.contains(golem), "hostilesAround (rule 2) lists only the zombie, got " + hostile);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void provokedMobsBecomeThreats(GameTestHelper helper) {
		ServerPlayer p = at(helper, 2.5, 2.5);
		IronGolem angry = mob(helper, EntityType.IRON_GOLEM, 5.0, 2.5);
		Cow cow = mob(helper, EntityType.COW, 2.5, 5.0);
		IronGolem hunter = mob(helper, EntityType.IRON_GOLEM, 5.0, 5.0);
		helper.assertFalse(HeroTargets.isHostile(p, angry), "a calm golem is not a threat");

		angry.setPersistentAngerTarget(p.getUUID());
		angry.setRemainingPersistentAngerTime(400);
		helper.assertTrue(HeroTargets.isHostile(p, angry), "a golem angry at the player is");

		hunter.setTarget(p);
		helper.assertTrue(HeroTargets.isHostile(p, hunter), "a mob targeting the player is");

		helper.assertFalse(HeroTargets.isHostile(p, cow), "an untouched cow is not");
		p.setLastHurtByMob(cow);
		helper.assertTrue(HeroTargets.isHostile(p, cow), "but something that just attacked the player is");
		helper.succeed();
	}

	// ------------------------------------------------------------------ real powers

	@GameTest(template = EMPTY_STRUCTURE)
	public void thunderclapAimedRuleHitsCowVillagerAndGolemButNotOwnPet(GameTestHelper helper) {
		ServerPlayer thor = thor(helper, 3.5, 3.5);
		Cow cow = mob(helper, EntityType.COW, 5.5, 3.5);
		Villager villager = mob(helper, EntityType.VILLAGER, 3.5, 5.5);
		IronGolem golem = mob(helper, EntityType.IRON_GOLEM, 1.5, 1.5);
		Wolf pet = mob(helper, EntityType.WOLF, 1.5, 4.5);
		pet.tame(thor);
		float cowHp = cow.getHealth();
		float villagerHp = villager.getHealth();
		float golemHp = golem.getHealth();
		float petHp = pet.getHealth();

		ThorPowers.thunderclap(thor);

		helper.assertTrue(cow.getHealth() < cowHp || cow.isDeadOrDying(), "the cow takes the shockwave");
		helper.assertTrue(villager.getHealth() < villagerHp || villager.isDeadOrDying(), "the villager too");
		helper.assertTrue(golem.getHealth() < golemHp, "and the iron golem");
		helper.assertTrue(pet.getHealth() == petHp, "but Thor's own wolf is spared");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void thornSentryAutoRuleIgnoresTheCowAndShootsTheZombie(GameTestHelper helper) {
		ServerPlayer p = at(helper, 2.5, 2.5);
		ThornSentryEntity sentry = PlantManipulationHandlers.plantSentry(p);
		Vec3 s = sentry.position();
		// the cow right next to the sentry, the zombie a little further away
		Cow cow = EntityType.COW.create(helper.getLevel());
		cow.moveTo(s.x + 1.5, s.y, s.z);
		cow.setNoAi(true);
		helper.getLevel().addFreshEntity(cow);
		Zombie zombie = mob(helper, EntityType.ZOMBIE, 5.5, 5.5);

		helper.assertTrue(sentry.findTarget(helper.getLevel(), p) == zombie,
				"the sentry skips the nearer cow and picks the zombie");
		helper.assertFalse(ThornSentryEntity.isHostileTo(cow, p), "a cow is never a sentry target");
		ThornSentryEntity.removeFor(p);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void stormAutoRuleSparesAnimalsButAimedRuleDoesNot(GameTestHelper helper) {
		ServerPlayer thor = thor(helper, 2.5, 2.5);
		Cow cow = mob(helper, EntityType.COW, 4.5, 2.5);
		Zombie zombie = mob(helper, EntityType.ZOMBIE, 2.5, 4.5);
		var aimed = ThorTargets.inRadius(helper.getLevel(), thor, thor.position(), 5.0);
		var storm = ThorTargets.hostilesInRadius(helper.getLevel(), thor, thor.position(), 5.0);
		helper.assertTrue(aimed.contains(cow) && aimed.contains(zombie), "Thor's aimed blasts reach both");
		helper.assertTrue(storm.contains(zombie) && !storm.contains(cow), "Storm Call / chain jumps only the zombie");
		helper.succeed();
	}

	// ------------------------------------------------------------------ friends

	@GameTest(template = EMPTY_STRUCTURE)
	public void squadmatesAndPetsAreNeverHit(GameTestHelper helper) {
		ServerPlayer p = at(helper, 2.5, 2.5);
		ServerPlayer mate = at(helper, 4.5, 2.5);
		Squad squad = squad(helper, p, mate);
		try {
			Wolf own = mob(helper, EntityType.WOLF, 2.5, 4.5);
			own.tame(p);
			Wolf matesPet = mob(helper, EntityType.WOLF, 4.5, 4.5);
			matesPet.tame(mate);
			own.setTarget(p); // even a pet that somehow targets its owner is never fair game
			helper.assertFalse(HeroTargets.canHarm(p, mate), "rule 1 spares a squadmate");
			helper.assertFalse(HeroTargets.isHostile(p, mate), "rule 2 spares a squadmate");
			helper.assertFalse(HeroTargets.canHarm(p, own) || HeroTargets.isHostile(p, own), "never your own pet");
			helper.assertFalse(HeroTargets.canHarm(p, matesPet) || HeroTargets.isHostile(p, matesPet),
					"never a squadmate's pet");
			helper.assertFalse(AbilityHelpers.hurt(p, own, 5.0f), "and AbilityHelpers.hurt refuses to hurt your pet");

			// friendly fire on: deliberate hits land on a squadmate, automatic ones still never pick them
			SquadManager.get(helper.getLevel().getServer()).setFriendlyFire(squad, true);
			boolean pvp = HeroTargets.pvpOn(p);
			helper.assertTrue(HeroTargets.canHarm(p, mate) == pvp, "friendly fire on: rule 1 follows the PvP setting");
			helper.assertFalse(HeroTargets.isHostile(p, mate), "rule 2 never auto-targets a squadmate");
		} finally {
			SquadManager.get(helper.getLevel().getServer()).disband(squad);
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void otherPlayersOnlyWithPvpOnAndOnlyThreatsForAutoRule(GameTestHelper helper) {
		ServerPlayer p = at(helper, 2.5, 2.5);
		ServerPlayer stranger = at(helper, 4.5, 4.5);
		ServerPlayer builder = at(helper, 1.5, 4.5);
		builder.setGameMode(GameType.CREATIVE);
		MinecraftServer server = helper.getLevel().getServer();
		boolean savedPvp = server.isPvpAllowed();
		boolean savedCfg = HeroConfig.get().abilityPvpDamage;
		try {
			// all synchronous within this tick, so no other test ever sees the toggled settings
			server.setPvpAllowed(true);
			HeroConfig.get().abilityPvpDamage = true;
			helper.assertTrue(HeroTargets.canHarm(p, stranger), "PvP on: an aimed power can hit another player");
			helper.assertFalse(HeroTargets.canHarm(p, builder), "never a creative player");
			helper.assertFalse(HeroTargets.isHostile(p, stranger), "a peaceful player is not auto-targeted");
			stranger.setLastHurtMob(p);
			helper.assertTrue(HeroTargets.isHostile(p, stranger), "one who just attacked the owner is");

			server.setPvpAllowed(false);
			helper.assertFalse(HeroTargets.canHarm(p, stranger), "PvP off: never another player");
			helper.assertFalse(HeroTargets.isHostile(p, stranger), "not even a hostile one");
			helper.assertFalse(AbilityHelpers.enemiesAround(p, p.position(), 6.0).contains(stranger),
					"and the area queries agree");

			server.setPvpAllowed(true);
			HeroConfig.get().abilityPvpDamage = false;
			helper.assertFalse(HeroTargets.canHarm(p, stranger), "the mod's ability-PvP switch turns it off too");
		} finally {
			server.setPvpAllowed(savedPvp);
			HeroConfig.get().abilityPvpDamage = savedCfg;
		}
		helper.succeed();
	}
}
