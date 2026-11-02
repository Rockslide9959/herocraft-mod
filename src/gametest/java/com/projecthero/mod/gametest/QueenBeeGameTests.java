package com.projecthero.mod.gametest;

import com.projecthero.mod.hero.HeroTiers;
import com.projecthero.mod.hero.PowerGrants;
import com.projecthero.mod.hero.guide.HeroPackGuide;
import com.projecthero.mod.miraculous.Kwami;
import com.projecthero.mod.miraculous.Miraculous;
import com.projecthero.mod.miraculous.MiraculousConfig;
import com.projecthero.mod.miraculous.MiraculousKits;
import com.projecthero.mod.miraculous.MiraculousPhrases;
import com.projecthero.mod.miraculous.data.MiraculousState;
import com.projecthero.mod.miraculous.item.MiraculousBoxItem;
import com.projecthero.mod.miraculous.item.MiraculousItems;
import com.projecthero.mod.miraculous.queenbee.QueenBee;
import com.projecthero.mod.miraculous.queenbee.QueenBeeConfig;
import com.projecthero.mod.miraculous.queenbee.QueenBeeMoves;
import com.projecthero.mod.punisher.PunisherControl;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.2 Queen Bee (Pollen): one test per move -- the server effect and its number -- plus Venom (armed by Shift+Z,
 * delivered by her next hit or a punch, the countdown, bosses), the glide's numbers, the phrases, honeycomb, the wiring
 * (key, box, jewel, guide chapter). Mock players are not ticked by the server, so tests that run over time drive the
 * Miraculous tick themselves ({@link #pump}).
 */
public class QueenBeeGameTests implements FabricGameTest {
	private static final float EPS = 0.6f;

	// ---------------------------------------------------------------- helpers

	@SuppressWarnings("removal")
	private static ServerPlayer mock(GameTestHelper helper, double x, double y, double z) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(x, y, z));
		p.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);
		p.setYHeadRot(0f);
		try {
			java.lang.reflect.Field f = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
			f.setAccessible(true);
			f.setInt(p, 0);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
		return p;
	}

	/** Queen Bee facing +Z, the transformation finished, every move ready. */
	private static ServerPlayer queenBee(GameTestHelper helper, double x, double y, double z) {
		ServerPlayer p = mock(helper, x, y, z);
		helper.assertTrue(Miraculous.grant(p, Kwami.POLLEN), "Pollen is granted");
		Miraculous.transform(p);
		long now = helper.getLevel().getGameTime();
		Miraculous.edit(p, n -> {
			n.formChangeAt = now - 200L;
			n.animId = MiraculousState.ANIM_NONE;
			n.abilityReadyAt.clear();
		});
		helper.assertTrue(Miraculous.canAct(p), "the transformation has finished");
		return p;
	}

	private static void pump(GameTestHelper helper, ServerPlayer p) {
		helper.onEachTick(() -> {
			p.tickCount++;
			Miraculous.tick(p);
		});
	}

	private static void floor(GameTestHelper helper) {
		for (int x = 0; x <= 7; x++) {
			for (int z = 0; z <= 7; z++) {
				helper.getLevel().setBlock(helper.absolutePos(new BlockPos(x, 1, z)), Blocks.STONE.defaultBlockState(), 3);
			}
		}
	}

	private static <T extends Mob> T mob(GameTestHelper helper, EntityType<T> type, double x, double y, double z, double health, boolean ai) {
		helper.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
		T m = type.create(helper.getLevel());
		Vec3 at = helper.absoluteVec(new Vec3(x, y, z));
		m.moveTo(at.x, at.y, at.z, 180.0f, 0.0f);
		m.setNoAi(!ai);
		m.setPersistenceRequired();
		m.getAttribute(Attributes.MAX_HEALTH).setBaseValue(health);
		m.setHealth((float) health);
		if (m.getAttribute(Attributes.ARMOR) != null) {
			m.getAttribute(Attributes.ARMOR).setBaseValue(0.0);
		}
		m.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.CARVED_PUMPKIN));
		helper.getLevel().addFreshEntity(m);
		return m;
	}

	private static float taken(LivingEntity e, double max) {
		return (float) max - e.getHealth();
	}

	// ---------------------------------------------------------------- the numbers

	@GameTest(template = EMPTY_STRUCTURE)
	public void theApprovedNumbers(GameTestHelper helper) {
		helper.assertTrue(QueenBeeConfig.STRIKE_DAMAGE == 7f && QueenBeeConfig.STRIKE_FINISHER_DAMAGE == 9f && QueenBeeConfig.STRIKE_REACH == 5.0, "Top Strike 7 / 7 / 9, 5 blocks");
		helper.assertTrue(QueenBeeConfig.THROW_RANGE == 20.0 && QueenBeeConfig.THROW_DAMAGE == 14f && QueenBeeConfig.THROW_TARGETS == 3, "Top Throw 20 blocks, 14, 3 targets");
		helper.assertTrue(QueenBeeConfig.SHIELD_TICKS == 80 && QueenBeeConfig.SHIELD_DAMAGE == 4f && QueenBeeConfig.SHIELD_EVERY == 10, "Spinning Shield 4 s, 4 every 0.5 s");
		helper.assertTrue(QueenBeeConfig.TANGLE_RANGE == 14.0 && QueenBeeConfig.TANGLE_DAMAGE == 6f && QueenBeeConfig.TANGLE_TICKS == 60, "Top Tangle 14, 6 + 3 s");
		helper.assertTrue(QueenBeeConfig.SWING_RANGE == 30.0 && QueenBeeConfig.COPTER_TICKS == 300, "Top Swing 30, Top Copter 15 s");
		helper.assertTrue(QueenBeeConfig.DIVE_DAMAGE == 17f && QueenBeeConfig.DIVE_RADIUS == 4.0, "Stinger Dive 17 in 4");
		helper.assertTrue(QueenBeeConfig.VENOM_DAMAGE == 40f && QueenBeeConfig.VENOM_PARALYSE_TICKS == 200 && QueenBeeConfig.VENOM_ARMED_TICKS == 200
				&& QueenBeeConfig.VENOM_BOSS_SLOW_TICKS == 60, "Venom 40 + 10 s paralysis, armed 10 s, bosses slowed 3 s");
		helper.assertTrue(QueenBeeConfig.HIVE_RANGE == 32.0 && QueenBeeConfig.COMMAND_RADIUS == 10.0 && QueenBeeConfig.COMMAND_STRENGTH_TICKS == 200, "Hive Sense 32, Command 10 / 10 s");
		helper.assertTrue(QueenBeeConfig.STING_DAMAGE == 13f && QueenBeeConfig.STING_POISON_TICKS == 60, "Sting 13 + Poison 3 s");
		helper.assertTrue(QueenBeeConfig.VOLLEY_SHOTS == 6 && QueenBeeConfig.VOLLEY_DAMAGE == 6f, "Stinger Volley 6 x 6");
		helper.assertTrue(MiraculousConfig.SAFE_LANDING_MOVES.contains(QueenBeeConfig.SWING), "Top Swing lands safely");
		var kit = MiraculousKits.of(Kwami.POLLEN);
		helper.assertTrue(kit.idsOf(com.projecthero.mod.hero.AbilitySlot.SLOT_4)[1].equals(QueenBeeConfig.VENOM)
				&& kit.idsOf(com.projecthero.mod.hero.AbilitySlot.SLOT_3)[0].equals(QueenBeeConfig.SWING)
				&& kit.idsOf(com.projecthero.mod.hero.AbilitySlot.SLOT_5)[0].equals(QueenBeeConfig.HIVE), "Shift+Z Venom, X movement, V utility");
		helper.succeed();
	}

	// ---------------------------------------------------------------- R

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 80)
	public void topStrikeIsSevenSevenNine(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = queenBee(helper, 3.5, 2.0, 2.5);
		Zombie z = mob(helper, EntityType.ZOMBIE, 3.5, 2.0, 5.5, 100, false);
		pump(helper, p);
		helper.assertTrue(QueenBeeMoves.strike(p) == 1 && Miraculous.state(p).animId == QueenBee.ANIM_STRIKE, "press 1");
		helper.runAfterDelay(6, () -> {
			helper.assertTrue(Math.abs(taken(z, 100) - 7f) < EPS, "one swing, 7: took " + taken(z, 100));
			helper.assertTrue(QueenBeeMoves.strike(p) == 2, "press 2");
			helper.runAfterDelay(6, () -> {
				helper.assertTrue(QueenBeeMoves.strike(p) == 3, "press 3");
				helper.assertFalse(Miraculous.ready(p, QueenBeeConfig.STRIKE), "the cooldown starts with the third");
				helper.runAfterDelay(6, () -> {
					helper.assertTrue(Math.abs(taken(z, 100) - 23f) < EPS, "7 + 7 + 9 = 23: took " + taken(z, 100));
					helper.succeed();
				});
			});
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 120)
	public void topThrowRicochetsThroughThree(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = queenBee(helper, 3.5, 2.0, 0.5);
		Zombie a = mob(helper, EntityType.ZOMBIE, 3.5, 2.0, 5.5, 100, false);
		Zombie b = mob(helper, EntityType.ZOMBIE, 6.5, 2.0, 6.5, 100, false);
		Zombie c = mob(helper, EntityType.ZOMBIE, 0.5, 2.0, 7.0, 100, false);
		p.setXRot(10f);
		pump(helper, p);
		helper.assertTrue(QueenBeeMoves.topThrow(p) && QueenBee.running(p, QueenBee.V_THROW), "the top is out");
		helper.assertFalse(Miraculous.ready(p, QueenBeeConfig.THROW), "on cooldown");
		helper.runAfterDelay(50, () -> {
			helper.assertTrue(QueenBeeMoves.topHits(p) == 3, "three hit, got " + QueenBeeMoves.topHits(p));
			for (Zombie z : new Zombie[] { a, b, c }) {
				helper.assertTrue(Math.abs(taken(z, 100) - QueenBeeConfig.THROW_DAMAGE) < EPS, "14 each: took " + taken(z, 100));
			}
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- G

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 80)
	public void spinningShieldPulsesFour(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = queenBee(helper, 3.5, 2.0, 3.5);
		Zombie z = mob(helper, EntityType.ZOMBIE, 3.5, 2.0, 5.5, 100, false);
		pump(helper, p);
		QueenBeeMoves.shield(p);
		helper.assertTrue(QueenBee.shielding(p), "spinning");
		helper.runAfterDelay(12, () -> {
			helper.assertTrue(Math.abs(taken(z, 100) - 8f) < EPS, "two pulses of 4: took " + taken(z, 100));
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 80)
	public void topTangleRootsButOnlySlowsABoss(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = queenBee(helper, 3.5, 2.0, 0.5);
		Zombie z = mob(helper, EntityType.ZOMBIE, 3.5, 2.0, 6.5, 100, false);
		p.setXRot(8f);
		LivingEntity t = QueenBeeMoves.tangle(p);
		helper.assertTrue(t == z && QueenBeeMoves.tangled(p, z) && PunisherControl.isStunned(z), "wrapped up");
		helper.assertTrue(Math.abs(taken(z, 100) - 6f) < EPS, "6: took " + taken(z, 100));
		ServerPlayer q = queenBee(helper, 1.5, 2.0, 0.5);
		Zombie boss = mob(helper, EntityType.ZOMBIE, 1.5, 2.0, 6.5, 400, false);
		q.setXRot(8f);
		helper.assertTrue(QueenBeeMoves.tangle(q) == boss && !PunisherControl.isStunned(boss) && boss.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "a boss is only slowed");
		helper.succeed();
	}

	// ---------------------------------------------------------------- X

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void topSwingHooksTheBlockAhead(GameTestHelper helper) {
		ServerPlayer p = queenBee(helper, 3.5, 2.0, 0.5);
		helper.getLevel().setBlock(helper.absolutePos(new BlockPos(3, 3, 7)), Blocks.STONE.defaultBlockState(), 3);
		p.setXRot(-5f);
		Vec3 hook = QueenBeeMoves.topSwing(p);
		helper.assertTrue(hook != null && QueenBee.running(p, QueenBee.V_SWING), "hooked");
		helper.assertTrue(Miraculous.hasSafeLanding(p), "a safe landing waits");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void topCopterFliesAndLands(GameTestHelper helper) {
		ServerPlayer p = queenBee(helper, 3.5, 3.0, 3.5);
		helper.assertTrue(QueenBeeMoves.copter(p) && p.getAbilities().mayfly && p.getAbilities().flying && QueenBee.copter(p), "flying");
		helper.assertTrue(Miraculous.ready(p, QueenBeeConfig.COPTER), "the cooldown waits for the landing");
		helper.assertFalse(QueenBeeMoves.copter(p), "again: lands");
		helper.assertTrue(!p.getAbilities().mayfly && !QueenBee.copter(p) && !Miraculous.ready(p, QueenBeeConfig.COPTER), "landed, on cooldown");
		helper.succeed();
	}

	// ---------------------------------------------------------------- Z

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void stingerDiveLandsSeventeen(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = queenBee(helper, 3.5, 2.0, 3.5);
		Zombie z = mob(helper, EntityType.ZOMBIE, 5.5, 2.0, 3.5, 100, false);
		Zombie far = mob(helper, EntityType.ZOMBIE, 3.5, 2.0, 9.5, 100, false);
		QueenBeeMoves.dive(p);
		helper.assertTrue(QueenBee.val(p, QueenBee.V_DIVE) == 1L && Miraculous.hasSafeLanding(p), "leaping, safe landing");
		helper.assertTrue(QueenBeeMoves.slam(p) == 1, "one within 4");
		helper.assertTrue(Math.abs(taken(z, 100) - 17f) < EPS && taken(far, 100) < 0.01f, "17 near, none far: " + taken(z, 100));
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 80)
	public void venomArmsThenAPunchParalyses(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = queenBee(helper, 3.5, 2.0, 2.5);
		Zombie z = mob(helper, EntityType.ZOMBIE, 3.5, 2.0, 4.5, 100, false);
		pump(helper, p);
		helper.assertTrue(QueenBeeMoves.venom(p) && QueenBee.venomArmed(p), "armed");
		helper.assertTrue(Miraculous.state(p).countdownEnd > 0L, "the countdown started");
		helper.assertFalse(Miraculous.ready(p, QueenBeeConfig.VENOM), "on its cooldown");
		helper.assertTrue(QueenBeeMoves.deliverVenom(p, z), "the punch carries it");
		helper.assertTrue(Math.abs(taken(z, 100) - 40f) < EPS, "40: took " + taken(z, 100));
		helper.assertTrue(QueenBeeMoves.paralysed(p, z) && PunisherControl.isStunned(z) && !QueenBee.venomArmed(p), "paralysed, Venom used up");
		Vec3 at = z.position();
		z.setDeltaMovement(1.0, 0, 0);
		helper.runAfterDelay(10, () -> {
			helper.assertTrue(z.position().distanceTo(at) < 0.2, "frozen where it stood");
			helper.assertFalse(QueenBeeMoves.deliverVenom(p, z), "only once");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void venomRidesHerMovesAndOnlySlowsABoss(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = queenBee(helper, 3.5, 2.0, 2.5);
		Zombie boss = mob(helper, EntityType.ZOMBIE, 3.5, 2.0, 4.5, 400, false);
		QueenBeeMoves.venom(p);
		QueenBeeMoves.sting(p);
		helper.assertFalse(QueenBee.venomArmed(p), "the Sting delivered it");
		helper.assertTrue(!QueenBeeMoves.paralysed(p, boss) && boss.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "a boss is only slowed");
		helper.assertTrue(taken(boss, 400) <= 2 * 400 * QueenBeeConfig.BOSS_MAX_FRACTION_PER_HIT + EPS, "capped: took " + taken(boss, 400));
		helper.succeed();
	}

	// ---------------------------------------------------------------- V

	@GameTest(template = EMPTY_STRUCTURE)
	public void hiveSenseToggles(GameTestHelper helper) {
		ServerPlayer p = queenBee(helper, 3.5, 2.0, 3.5);
		helper.assertTrue(QueenBeeMoves.hive(p) && QueenBee.hiveSense(p), "on");
		helper.assertFalse(QueenBeeMoves.hive(p), "off");
		helper.assertFalse(QueenBee.hiveSense(p), "off is off");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void queensCommandCalmsMobs(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = queenBee(helper, 3.5, 2.0, 3.5);
		Zombie z = mob(helper, EntityType.ZOMBIE, 5.5, 2.0, 5.5, 100, true);
		z.setTarget(p);
		int[] r = QueenBeeMoves.command(p);
		helper.assertTrue(r[0] >= 1 && PunisherControl.refusesTarget(z), "the zombie stands down: " + r[0]);
		helper.assertFalse(Miraculous.ready(p, QueenBeeConfig.COMMAND), "on cooldown");
		helper.succeed();
	}

	// ---------------------------------------------------------------- C

	@GameTest(template = EMPTY_STRUCTURE)
	public void stingIsThirteenAndPoison(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = queenBee(helper, 3.5, 2.0, 2.5);
		var z = mob(helper, EntityType.VINDICATOR, /* not undead: Poison takes */ 3.5, 2.0, 4.5, 100, false);
		QueenBeeMoves.sting(p);
		helper.assertTrue(Math.abs(taken(z, 100) - 13f) < EPS && z.hasEffect(MobEffects.POISON), "13 + Poison: took " + taken(z, 100));
		helper.assertTrue(Miraculous.hasSafeLanding(p), "a safe landing after the lunge");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 80)
	public void stingerVolleyFiresSix(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = queenBee(helper, 3.5, 2.0, 0.5);
		var z = mob(helper, EntityType.VINDICATOR, /* not undead: Poison takes */ 3.5, 2.0, 7.0, 200, false);
		p.setXRot(5f);
		pump(helper, p);
		QueenBeeMoves.volley(p);
		helper.assertTrue(QueenBee.volley(p), "the top hovers");
		helper.runAfterDelay(40, () -> {
			helper.assertTrue(taken(z, 200) >= 6 * QueenBeeConfig.VOLLEY_DAMAGE - 1f && z.hasEffect(MobEffects.POISON), "six stingers: took " + taken(z, 200));
			helper.assertFalse(QueenBee.volley(p), "done");
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- the Kwami, the phrases, the wiring

	@GameTest(template = EMPTY_STRUCTURE)
	public void detransformingEndsEverything(GameTestHelper helper) {
		ServerPlayer p = queenBee(helper, 3.5, 3.0, 3.5);
		QueenBeeMoves.copter(p);
		QueenBeeMoves.hive(p);
		Miraculous.edit(p, n -> n.formChangeAt -= 200L);
		Miraculous.detransform(p, false);
		helper.assertTrue(!p.getAbilities().mayfly && Miraculous.state(p).vals.keySet().stream().noneMatch(k -> k.startsWith("qb_")), "landed, every qb_ value gone");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 80)
	public void phrasesTransformAndHoneycombFeedsPollen(GameTestHelper helper) {
		ServerPlayer p = mock(helper, 3.5, 2.0, 3.5);
		helper.assertTrue(PowerGrants.grantHero(p, "queen_bee").ok() && Miraculous.holds(p, Kwami.POLLEN) && HeroTiers.holdsHero(p, "queen_bee"),
				"the command grants Queen Bee");
		helper.assertTrue("Queen Bee".equals(MiraculousPhrases.heroName(Kwami.POLLEN)), "the hero's name");
		helper.assertTrue(MiraculousPhrases.match(Kwami.POLLEN, "pollen buzz on") == MiraculousPhrases.Spoken.TRANSFORM
				&& MiraculousPhrases.match(Kwami.POLLEN, "Buzz on!") == MiraculousPhrases.Spoken.TRANSFORM
				&& MiraculousPhrases.match(Kwami.POLLEN, "BUZZ OFF") == MiraculousPhrases.Spoken.DETRANSFORM
				&& MiraculousPhrases.match(Kwami.POLLEN, "Pollen, buzz off!") == MiraculousPhrases.Spoken.DETRANSFORM, "the phrase table");
		helper.assertTrue(Miraculous.onChat(p, "Pollen, buzz on!") && Miraculous.transformedAs(p, Kwami.POLLEN), "Queen Bee");
		Miraculous.edit(p, n -> n.formChangeAt -= 200L);
		helper.assertTrue(Miraculous.onChat(p, "Buzz off!") && !Miraculous.transformed(p) && Miraculous.hungry(p), "back, and Pollen is hungry");
		helper.assertTrue(Miraculous.foodOf(Kwami.POLLEN) == Items.HONEYCOMB, "honeycomb");
		ItemStack comb = new ItemStack(Items.HONEYCOMB, 4);
		helper.assertTrue(Miraculous.feed(p, comb) && comb.getCount() == 3, "she eats one");
		helper.assertTrue(Miraculous.finishMeal(p) && !Miraculous.hungry(p), "fed");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void boxJewelAndGuideChapter(GameTestHelper helper) {
		helper.assertTrue(MiraculousBoxItem.BOX_KWAMIS.contains(Kwami.POLLEN), "the Bee Comb is in the box's pool");
		helper.assertTrue(MiraculousItems.jewelOf(Kwami.POLLEN) == MiraculousItems.BEE_COMB && "bee_comb".equals(Kwami.POLLEN.jewel)
				&& "queen_bee".equals(Kwami.POLLEN.heroKey) && HeroTiers.HERO_KEYS.contains("queen_bee"), "jewelOf, the key");
		ServerPlayer r = mock(helper, 3.5, 2.0, 3.5);
		r.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(MiraculousItems.BEE_COMB));
		MiraculousItems.BEE_COMB.use(helper.getLevel(), r, net.minecraft.world.InteractionHand.MAIN_HAND);
		helper.assertTrue(Miraculous.holds(r, Kwami.POLLEN) && r.getMainHandItem().isEmpty(), "the Comb grants Pollen and is used up");
		var chapters = HeroPackGuide.chapters();
		var ch = HeroPackGuide.miraculousChapter(Kwami.POLLEN);
		helper.assertTrue(ch.title().getContents() instanceof TranslatableContents t && t.getKey().equals("projecthero.guide.queen_bee"), "her own chapter");
		helper.assertTrue(chapters.indexOf(ch) == 63, "after the Stands, at " + chapters.indexOf(ch));
		StringBuilder all = new StringBuilder();
		for (var line : ch.lines()) {
			all.append(line.getString()).append('\n');
		}
		helper.assertTrue(all.indexOf("Venom") >= 0 && all.indexOf("Pollen, buzz on!") >= 0 && all.indexOf("honeycomb") >= 0
				&& all.indexOf("Stinger Volley") >= 0 && all.indexOf("projecthero.") < 0, "the Venom section, the phrase, the food, the moves, no untranslated lines");
		helper.succeed();
	}
}
