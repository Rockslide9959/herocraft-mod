package com.projecthero.mod.gametest;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

import com.projecthero.mod.event.EventManager;
import com.projecthero.mod.event.EventSavedData;
import com.projecthero.mod.event.EventState;
import com.projecthero.mod.horde.HordeBlocks;
import com.projecthero.mod.horde.HordeKind;
import com.projecthero.mod.horde.HordeRaid;
import com.projecthero.mod.horde.Hordes;
import com.projecthero.mod.horde.SpiderWaves;
import com.projecthero.mod.horde.entity.BroodQueen;
import com.projecthero.mod.horde.entity.BroodSpider;
import com.projecthero.mod.horde.entity.HordeEntityTypes;
import com.projecthero.mod.titan.entity.TitanEntity;
import com.projecthero.mod.titan.entity.TitanEntityTypes;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.14.16 Spider Horde: the seven brood variants (and spiderlings) and their traits, the wave table, the rebuilt
 * GeckoLib Brood Queen (stronger than the Titan, every attack runs its wind-up / strike / recovery and ends, the phases
 * open with a shriek) and the raid ending when she dies.
 */
public class SpiderHordeV01416GameTests implements FabricGameTest {

	private static void hard(GameTestHelper helper) {
		helper.getLevel().getServer().setDifficulty(Difficulty.HARD, true);
	}

	/**
	 * A survival mock player that can't be hurt (so it stays a valid target through every attack). Not
	 * {@code helper.makeMockServerPlayerInLevel()}: vanilla's mock player overrides {@code isCreative()} to always answer
	 * true, and both the Queen and the horde ignore creative players. Remove it with {@link #dropFighter}.
	 */
	private static ServerPlayer fighter(GameTestHelper helper, Vec3 rel) {
		ServerLevel level = helper.getLevel();
		net.minecraft.server.network.CommonListenerCookie cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(
				new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "test-fighter"), false);
		ServerPlayer p = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation()) {
			@Override
			public boolean isSpectator() {
				return false;
			}

			@Override
			public boolean isCreative() {
				return false;
			}
		};
		net.minecraft.network.Connection connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
		new io.netty.channel.embedded.EmbeddedChannel(connection);
		level.getServer().getPlayerList().placeNewPlayer(connection, p, cookie);
		p.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(rel);
		p.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);
		p.getAbilities().invulnerable = true;
		return p;
	}

	private static void dropFighter(GameTestHelper helper, ServerPlayer p) {
		helper.getLevel().getServer().getPlayerList().remove(p);
	}

	private static void clearSpiders(GameTestHelper helper, AABB box) {
		for (Mob m : helper.getLevel().getEntitiesOfClass(Mob.class, box, m -> m instanceof Spider)) {
			m.discard();
		}
	}

	private static BroodSpider variant(GameTestHelper helper, BroodSpider.Variant v) {
		BroodSpider s = HordeEntityTypes.BROOD_SPIDER.create(helper.getLevel());
		Vec3 at = helper.absoluteVec(new Vec3(1.5, 2, 1.5));
		s.moveTo(at.x, at.y, at.z, 0f, 0f);
		s.setVariant(v);
		s.finalizeSpawn(helper.getLevel(), helper.getLevel().getCurrentDifficultyAt(s.blockPosition()), MobSpawnType.EVENT, null);
		return s;
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void everyBroodVariantSpawnsWithItsTraits(GameTestHelper helper) {
		Set<String> looks = new HashSet<>();
		for (BroodSpider.Variant v : BroodSpider.Variant.values()) {
			BroodSpider s = variant(helper, v);
			helper.assertTrue(s.variant() == v, v + " keeps its variant");
			helper.assertTrue(Math.abs(s.getAttributeValue(Attributes.SCALE) - v.scale) < 1.0e-4, v + " is its own size");
			helper.assertTrue(s.getMaxHealth() == (float) v.health && s.getHealth() == s.getMaxHealth(), v + " has its own health, full");
			helper.assertTrue(s.getName().getContents() instanceof TranslatableContents t && t.getKey().endsWith("." + v.id()),
					v + " has its own name");
			helper.assertTrue(looks.add(v.scale + "/" + v.health), v + " looks unlike the others");
			helper.assertTrue(s.getPassengers().isEmpty(), v + " never brings a jockey");
		}
		double hordeSpeed = HordeEntityTypes.HORDE_SPIDER.create(helper.getLevel()).getAttributeValue(Attributes.MOVEMENT_SPEED);
		helper.assertTrue(variant(helper, BroodSpider.Variant.HUNTER).getAttributeValue(Attributes.MOVEMENT_SPEED) > hordeSpeed,
				"Hunters outrun the Horde Spider");
		BroodSpider brute = variant(helper, BroodSpider.Variant.BRUTE);
		helper.assertTrue(brute.getAttributeValue(Attributes.ARMOR) >= 14 && brute.getBbWidth() > 2.0f, "Brutes are big and plated");
		BroodSpider mother = variant(helper, BroodSpider.Variant.BROODMOTHER);
		helper.assertTrue(mother.getMaxHealth() > brute.getMaxHealth() && mother.getBbWidth() > brute.getBbWidth(),
				"the Broodmother is the biggest of them");
		BroodSpider stalker = variant(helper, BroodSpider.Variant.STALKER);
		helper.assertTrue(stalker.hasEffect(MobEffects.INVISIBILITY) && stalker.hasAmbush(), "Shadow Stalkers spawn hidden, ambush ready");
		helper.assertFalse(variant(helper, BroodSpider.Variant.HUNTER).hasEffect(MobEffects.INVISIBILITY),
				"no other variant rolls vanilla's invisibility");
		helper.assertTrue(variant(helper, BroodSpider.Variant.SPIDERLING).getBbWidth() < 1.0f, "spiderlings are tiny");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void spiderWavesGetNastierWithEveryWave(GameTestHelper helper) {
		helper.assertTrue(SpiderWaves.weight(SpiderWaves.Pick.VENOM, 1) == 0 && SpiderWaves.weight(SpiderWaves.Pick.BROODMOTHER, 5) == 0,
				"no spitters on wave 1, no Broodmothers before wave 6");
		helper.assertTrue(SpiderWaves.weight(SpiderWaves.Pick.BRUTE, 8) > SpiderWaves.weight(SpiderWaves.Pick.BRUTE, 4),
				"Brutes grow commoner");
		RandomSource r = RandomSource.create(1416L);
		EnumSet<SpiderWaves.Pick> seen = EnumSet.noneOf(SpiderWaves.Pick.class);
		for (int i = 0; i < 3000; i++) {
			seen.add(SpiderWaves.pick(r, HordeRaid.WAVES));
		}
		helper.assertTrue(seen.size() == SpiderWaves.Pick.values().length, "the last wave sends every kind, got " + seen);
		EnumSet<SpiderWaves.Pick> first = EnumSet.noneOf(SpiderWaves.Pick.class);
		for (int i = 0; i < 1000; i++) {
			first.add(SpiderWaves.pick(r, 1));
		}
		helper.assertTrue(first.equals(EnumSet.of(SpiderWaves.Pick.HORDE_SPIDER, SpiderWaves.Pick.CAVE_SPIDER, SpiderWaves.Pick.HUNTER)),
				"wave 1 is only the easy three, got " + first);
		for (int i = 0; i < 40; i++) {
			helper.assertTrue(SpiderWaves.create(helper.getLevel(), 1 + i % HordeRaid.WAVES) != null, "every pick makes a spider");
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "v01416_brood_death")
	public void broodmothersBurstIntoSpiderlings(GameTestHelper helper) {
		hard(helper);
		ServerLevel level = helper.getLevel();
		BroodSpider mother = variant(helper, BroodSpider.Variant.BROODMOTHER);
		level.addFreshEntity(mother);
		AABB box = mother.getBoundingBox().inflate(6.0);
		mother.kill();
		int lings = level.getEntitiesOfClass(BroodSpider.class, box, s -> s.variant() == BroodSpider.Variant.SPIDERLING).size();
		helper.assertTrue(lings >= 4, "a dead Broodmother bursts into at least four spiderlings, got " + lings);
		BroodSpider burster = variant(helper, BroodSpider.Variant.BURSTER);
		level.addFreshEntity(burster);
		burster.kill();
		int clouds = level.getEntitiesOfClass(net.minecraft.world.entity.AreaEffectCloud.class, box).size();
		helper.assertTrue(clouds >= 1, "a dead Acid Burster still leaves an acid puddle");
		clearSpiders(helper, box);
		for (var c : level.getEntitiesOfClass(net.minecraft.world.entity.AreaEffectCloud.class, box)) {
			c.discard();
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void theBroodQueenOutclassesTheTitan(GameTestHelper helper) {
		BroodQueen queen = HordeEntityTypes.BROOD_QUEEN.create(helper.getLevel());
		TitanEntity titan = TitanEntityTypes.TITAN.create(helper.getLevel());
		queen.configure(1);
		helper.assertTrue(queen.getMaxHealth() >= titan.getMaxHealth() * 1.5f,
				"the Queen has at least half again the Titan's health: " + queen.getMaxHealth() + " vs " + titan.getMaxHealth());
		helper.assertTrue(queen.getAttributeValue(Attributes.ARMOR) > titan.getAttributeValue(Attributes.ARMOR)
				&& queen.getAttributeValue(Attributes.ARMOR_TOUGHNESS) > titan.getAttributeValue(Attributes.ARMOR_TOUGHNESS),
				"and more armour");
		helper.assertTrue(queen.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) >= 1.0, "she can't be knocked back");
		float solo = queen.getMaxHealth();
		queen.configure(3);
		helper.assertTrue(queen.getMaxHealth() > solo, "more fighters, more health");
		helper.assertTrue(queen.getBbWidth() >= 4.0f && queen.getBbHeight() >= 3.0f, "she is huge");
		helper.assertTrue(queen.fireImmune(), "fire can't hurt her");
		helper.assertTrue(BroodQueen.phaseFor(3000, 3000) == 1 && BroodQueen.phaseFor(1500, 3000) == 2 && BroodQueen.phaseFor(500, 3000) == 3,
				"three phases by thirds");
		helper.succeed();
	}

	private static BroodQueen placedQueen(GameTestHelper helper, ServerPlayer target) {
		BroodQueen queen = HordeEntityTypes.BROOD_QUEEN.create(helper.getLevel());
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 2, 2.5));
		queen.moveTo(at.x, at.y, at.z, 0f, 0f);
		queen.setNoAi(true); // the test drives her brain itself
		queen.configure(1);
		helper.getLevel().addFreshEntity(queen);
		queen.skipIntro();
		queen.setTarget(target);
		return queen;
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "v01416_queen_attacks", timeoutTicks = 100)
	public void everyQueenAttackWindsUpStrikesAndEnds(GameTestHelper helper) {
		hard(helper);
		ServerPlayer target = fighter(helper, new Vec3(2.5, 2, 7.5));
		BroodQueen queen = placedQueen(helper, target);
		AABB area = queen.getBoundingBox().inflate(40.0);
		for (BroodQueen.Attack a : BroodQueen.Attack.values()) {
			queen.forceAttack(a);
			helper.assertTrue(queen.currentAttack() == a && queen.isBusy(), a + " starts and owns her pose");
			int starts = 1;
			int ticks = 0;
			int last = queen.attackTick();
			while (queen.currentAttack() != null && ticks < 1000) {
				queen.combatTick();
				ticks++;
				if (queen.currentAttack() != null && queen.attackTick() < last) {
					starts++; // a Frenzy chaining into its next lunge
				}
				last = queen.currentAttack() == null ? 0 : queen.attackTick();
			}
			helper.assertTrue(queen.currentAttack() == null && !queen.isBusy(), a + " ends and lets go of her pose (" + ticks + " ticks)");
			helper.assertTrue(ticks >= a.windup, a + " winds up first");
			if (a == BroodQueen.Attack.FRENZY) {
				helper.assertTrue(starts == 3, "Frenzy is three lunges, got " + starts);
			}
			queen.setNoGravity(false);
		}
		helper.assertTrue(queen.acidPoolCount() > 0, "Acid Rain left pools behind");
		helper.assertTrue(queen.broodCount() > 0, "the Egg Burst hatched a brood");
		queen.discard();
		dropFighter(helper, target);
		clearSpiders(helper, area);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "v01416_queen_cycle", timeoutTicks = 100)
	public void theQueenPicksHerOwnAttacksAndShrieksIntoEachPhase(GameTestHelper helper) {
		hard(helper);
		ServerPlayer target = fighter(helper, new Vec3(2.5, 2, 6.5));
		BroodQueen queen = placedQueen(helper, target);
		AABB area = queen.getBoundingBox().inflate(40.0);
		Set<BroodQueen.Attack> used = new HashSet<>();
		for (int i = 0; i < 3000; i++) {
			queen.combatTick();
			if (queen.currentAttack() != null) {
				used.add(queen.currentAttack());
			}
		}
		helper.assertTrue(used.size() >= 3, "she cycles through several attacks on her own, used " + used);
		helper.assertFalse(used.contains(BroodQueen.Attack.SHRIEK), "no shriek while she is unhurt");
		helper.assertTrue(queen.phase() == 1, "phase 1 at full health");
		// down to half: the next tick between attacks shrieks her into phase 2
		queen.setHealth(queen.getMaxHealth() * 0.5f);
		for (int i = 0; i < 200 && queen.phase() == 1; i++) {
			queen.combatTick();
		}
		helper.assertTrue(queen.phase() == 2 && queen.currentAttack() == BroodQueen.Attack.SHRIEK, "half health: a shriek opens phase 2");
		float before = queen.getHealth();
		queen.invulnerableTime = 0;
		queen.hurt(queen.damageSources().playerAttack(target), 50f);
		helper.assertTrue(queen.getHealth() == before, "she can't be hurt while she shrieks");
		queen.setHealth(queen.getMaxHealth() * 0.2f);
		for (int i = 0; i < 400 && queen.phase() < 3; i++) {
			queen.combatTick();
		}
		helper.assertTrue(queen.phase() == 3, "a fifth of her health: phase 3");
		helper.assertTrue(queen.phaseMultiplier() > 1.25f, "and she hits harder");
		queen.discard();
		dropFighter(helper, target);
		clearSpiders(helper, area);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "v01416_spider_raid", timeoutTicks = 200)
	public void theSpiderHordeIsWonWhenTheQueenDies(GameTestHelper helper) {
		hard(helper);
		ServerLevel level = helper.getLevel();
		BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
		ServerPlayer p = fighter(helper, new Vec3(4.5, 2, 4.5));
		level.setBlock(pos, HordeBlocks.SPIDER_HORDE.defaultBlockState(), 3);
		helper.assertTrue(Hordes.start(level, pos, HordeKind.SPIDER), "the Spider Horde wakes");
		HordeRaid raid = (HordeRaid) EventManager.at(level, pos);
		raid.tick(level); // locks the fighter in
		helper.assertTrue(raid.isFighter(p.getUUID()), "the player is a fighter");
		for (int i = 0; i < 60 && !raid.bossPhase(); i++) {
			raid.debugAdvance(level);
			raid.tick(level);
		}
		helper.assertTrue(raid.bossPhase(), "eight waves later, the boss");
		AABB arena = new AABB(pos).inflate(HordeRaid.ARENA, 24, HordeRaid.ARENA);
		var queens = level.getEntitiesOfClass(BroodQueen.class, arena, BroodQueen::isAlive);
		helper.assertTrue(queens.size() == 1, "the Brood Queen climbs out, found " + queens.size());
		BroodQueen queen = queens.get(0);
		helper.assertTrue(queen.getMaxHealth() > 2700f, "with more than half again a Titan's health");
		queen.kill();
		raid.tick(level);
		helper.assertTrue(raid.state() == EventState.COMPLETED, "killing her wins the horde, state " + raid.state());
		helper.assertFalse(level.getBlockState(pos).is(HordeBlocks.SPIDER_HORDE), "and the block turns into the reward");
		EventSavedData.get(level).remove(raid.id());
		level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
		queen.discard();
		clearSpiders(helper, arena);
		dropFighter(helper, p);
		helper.succeed();
	}
}
