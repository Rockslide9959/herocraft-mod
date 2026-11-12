package com.projecthero.mod.gametest;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.HeroTiers;
import com.projecthero.mod.hero.PowerGrants;
import com.projecthero.mod.shazam.Shazam;
import com.projecthero.mod.shazam.ShazamAbilityManager;
import com.projecthero.mod.shazam.ShazamConfig;
import com.projecthero.mod.shazam.ShazamDamage;
import com.projecthero.mod.shazam.ShazamFamily;
import com.projecthero.mod.shazam.ShazamMod;
import com.projecthero.mod.shazam.ShazamMoves;
import com.projecthero.mod.shazam.ShazamWizards;
import com.projecthero.mod.shazam.ShazamWord;
import com.projecthero.mod.shazam.data.ShazamState;
import com.projecthero.mod.shazam.entity.ShazamEntities;
import com.projecthero.mod.shazam.entity.WizardShazamEntity;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.3 Shazam: the word (the parser, transform / turn back, the 4 s gap, the voice rules, ordinary chat for everyone else), the
 * transform bolt's damage (never the speaker or a squadmate), the passives, every move, the Shazam Family and the Wizard's judgement.
 * Mock players don't go through the chat pipeline, so the chat hook's own method ({@link ShazamMod#onChat}) is driven directly; they
 * aren't ticked either, so {@link Shazam#tick} is called by hand. Mob hits are asserted as ratios of the expected damage.
 */
public class ShazamGameTests implements FabricGameTest {
	private static ServerPlayer mock(GameTestHelper helper, double x, double z) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(x, 2.0, z));
		p.moveTo(at.x, at.y, at.z, 0f, 0f);
		try { // a fresh mock player is spawn-protected
			java.lang.reflect.Field f = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
			f.setAccessible(true);
			f.setInt(p, 0);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
		return p;
	}

	/** A champion, already transformed. */
	private static ServerPlayer shazam(GameTestHelper helper, double x, double z) {
		ServerPlayer p = mock(helper, x, z);
		helper.assertTrue(Shazam.grant(p), "the grant works");
		helper.assertTrue(ShazamMod.onChat(p, "Shazam!") == Shazam.Spoken.TRANSFORMED, "the word transforms him");
		return p;
	}

	private static void edit(ServerPlayer p, java.util.function.Consumer<ShazamState> change) {
		ShazamState n = Shazam.state(p).copy();
		change.accept(n);
		p.setAttached(ModAttachments.SHAZAM_STATE, n);
	}

	private static void gapOver(ServerPlayer p) {
		edit(p, s -> s.lastWord = 0L);
	}

	private static Zombie zombie(GameTestHelper helper, double x, double z, double maxHealth) {
		Zombie zb = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(x, 2.0, z));
		zb.getAttribute(Attributes.MAX_HEALTH).setBaseValue(maxHealth);
		zb.setHealth((float) maxHealth);
		return zb;
	}

	private static void look(ServerPlayer p, LivingEntity at) {
		p.lookAt(EntityAnchorArgument.Anchor.EYES, at.position().add(0, at.getBbHeight() * 0.55, 0));
	}

	private static float lost(float before, LivingEntity e) {
		return before - e.getHealth();
	}

	private static boolean near(float got, float expected) {
		return got > expected * 0.85f && got < expected * 1.15f;
	}

	private static com.projecthero.mod.squad.Squad squad(GameTestHelper helper, ServerPlayer leader, ServerPlayer... members) {
		var squads = com.projecthero.mod.squad.SquadManager.get(helper.getLevel().getServer());
		var squad = squads.create("Shazam" + helper.getLevel().getGameTime() + leader.getId(), leader.getUUID());
		for (ServerPlayer m : members) {
			squads.addMember(squad, m.getUUID());
		}
		return squad;
	}

	private static void disband(GameTestHelper helper, com.projecthero.mod.squad.Squad squad) {
		com.projecthero.mod.squad.SquadManager.get(helper.getLevel().getServer()).disband(squad);
	}

	// ---------------------------------------------------------------- the word

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void wordParserAcceptsOnlyTheWholeWord(GameTestHelper helper) {
		for (String ok : new String[] { "shazam", "SHAZAM", "Shazam!", "Shazam.", "  shazam!!!  ", "\"Shazam!\"", "ShAzAm?", "shazam...", "*SHAZAM*" }) {
			helper.assertTrue(ShazamWord.isWord(ok), "accepts '" + ok + "'");
		}
		for (String no : new String[] { "", "!", "shazam now", "say shazam", "shazamm", "sha zam", "shazam shazam", "shaz", "captain marvel", "/shazam x" }) {
			helper.assertFalse(ShazamWord.isWord(no), "rejects '" + no + "'");
		}
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void wizardsGiftIsTheOnePrimary(GameTestHelper helper) {
		ServerPlayer p = mock(helper, 1.5, 1.5);
		com.projecthero.mod.kryptonian.Kryptonian.grant(p);
		helper.assertTrue(Shazam.grant(p), "granted");
		helper.assertTrue(Shazam.hasPower(p) && HeroTiers.holdsHero(p, Shazam.KEY), "a Hero-Tier power");
		helper.assertTrue(HeroTiers.HERO_KEYS.contains(Shazam.KEY) && PowerGrants.HERO_TIER_KEYS.contains(Shazam.KEY), "a Hero-Tier key");
		helper.assertTrue(!com.projecthero.mod.kryptonian.Kryptonian.hasPower(p), "it replaced the Kryptonian (one Primary)");
		helper.assertTrue(PowerGrants.NOT_FROM_SERUMS.contains(Shazam.KEY), "random serums never give it");
		helper.assertFalse(Shazam.transformed(p), "he starts as Billy");
		helper.assertTrue(p.getMaxHealth() == 20f, "no passives until he says the word");
		helper.assertFalse(Shazam.grant(p), "a second grant does nothing");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theWordTransformsAndTurnsBackWithAGap(GameTestHelper helper) {
		ServerPlayer p = mock(helper, 1.5, 1.5);
		ServerPlayer stranger = mock(helper, 6.5, 6.5);
		Shazam.grant(p);
		helper.assertTrue(ShazamMod.onChat(stranger, "Shazam!") == null, "anyone else's 'Shazam' is ordinary chat (posted)");
		helper.assertTrue(ShazamMod.onChat(p, "hello shazam") == null, "a sentence with the word in it is ordinary chat");
		Shazam.setLightning(p, 10f);
		helper.assertTrue(ShazamMod.wouldSwallow(p, "SHAZAM!"), "his word is kept out of chat");
		helper.assertTrue(ShazamMod.onChat(p, "SHAZAM!") == Shazam.Spoken.TRANSFORMED, "transformed");
		helper.assertTrue(Shazam.transformed(p), "he is the champion now");
		helper.assertTrue(Math.abs(p.getMaxHealth() - 50f) < 0.01f, "Stamina of Atlas: 50 health, got " + p.getMaxHealth());
		helper.assertTrue(Shazam.lightning(p) == ShazamConfig.LIGHTNING_MAX, "the transform bolt refilled the bar");
		helper.assertTrue(ShazamMod.onChat(p, "shazam") == Shazam.Spoken.TOO_SOON, "4 s between words");
		helper.assertTrue(Shazam.transformed(p), "still transformed");
		gapOver(p);
		helper.assertTrue(ShazamMod.onChat(p, "Shazam.") == Shazam.Spoken.TURNED_BACK, "saying it again turns him back");
		helper.assertFalse(Shazam.transformed(p), "Billy again");
		helper.assertTrue(p.getMaxHealth() == 20f && p.getHealth() <= 20f, "the passives are gone");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theGapIsFourSecondsExactly(GameTestHelper helper) {
		ServerPlayer p = shazam(helper, 1.5, 1.5);
		long spoke = Shazam.state(p).lastWord;
		edit(p, s -> s.lastWord = spoke - (ShazamConfig.WORD_GAP - 1));
		helper.assertTrue(Shazam.speak(p) == Shazam.Spoken.TOO_SOON, "one tick short of 4 s: too soon");
		edit(p, s -> s.lastWord = spoke - ShazamConfig.WORD_GAP);
		helper.assertTrue(Shazam.speak(p) == Shazam.Spoken.TURNED_BACK, "at 4 s it works");
		helper.assertTrue(ShazamConfig.WORD_GAP == 80, "the gap is 80 ticks");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void noVoiceUnderwaterOrInPowderSnow(GameTestHelper helper) {
		ServerPlayer p = mock(helper, 2.5, 2.5);
		Shazam.grant(p);
		BlockPos feet = p.blockPosition();
		helper.getLevel().setBlockAndUpdate(feet, Blocks.POWDER_SNOW.defaultBlockState());
		helper.assertTrue(ShazamWord.voice(p) == ShazamWord.Voice.POWDER_SNOW, "inside powder snow: no voice");
		helper.assertTrue(ShazamMod.onChat(p, "Shazam") == Shazam.Spoken.NO_VOICE, "'You can't say it!'");
		helper.assertFalse(Shazam.transformed(p), "nothing happened");
		helper.getLevel().setBlockAndUpdate(feet, Blocks.WATER.defaultBlockState());
		helper.getLevel().setBlockAndUpdate(feet.above(), Blocks.WATER.defaultBlockState());
		helper.assertTrue(ShazamWord.voice(p) == ShazamWord.Voice.UNDERWATER, "head underwater: no voice");
		helper.getLevel().setBlockAndUpdate(feet.above(), Blocks.BUBBLE_COLUMN.defaultBlockState());
		helper.assertTrue(ShazamWord.voice(p) != ShazamWord.Voice.UNDERWATER, "in an air bubble he can say it");
		helper.getLevel().setBlockAndUpdate(feet, Blocks.AIR.defaultBlockState());
		helper.getLevel().setBlockAndUpdate(feet.above(), Blocks.AIR.defaultBlockState());
		helper.assertTrue(ShazamWord.voice(p) == ShazamWord.Voice.OK, "in the open air he can");
		edit(p, s -> s.muffledUntil = helper.getLevel().getGameTime() + 100);
		helper.assertTrue(ShazamWord.voice(p) == ShazamWord.Voice.MUFFLED, "muffled (Black Adam, later)");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theTransformBoltSparesHimAndSquadmates(GameTestHelper helper) {
		ServerPlayer p = mock(helper, 4.5, 4.5);
		ServerPlayer mate = mock(helper, 5.5, 5.5);
		var squad = squad(helper, p, mate);
		Shazam.grant(p);
		Zombie near = zombie(helper, 6.0, 4.5, 100);
		Zombie far = zombie(helper, 4.5, 0.6, 100);
		float pBefore = p.getHealth(), mateBefore = mate.getHealth(), nearBefore = near.getHealth(), farBefore = far.getHealth();
		helper.assertTrue(ShazamMod.onChat(p, "shazam") == Shazam.Spoken.TRANSFORMED, "transformed");
		helper.assertTrue(near(lost(nearBefore, near), ShazamConfig.WORD_BOLT_DAMAGE), "12 to a creature within 3 blocks, got " + lost(nearBefore, near));
		helper.assertTrue(far.getHealth() == farBefore, "nothing beyond 3 blocks");
		helper.assertTrue(p.getHealth() >= pBefore, "never the speaker");
		helper.assertTrue(mate.getHealth() == mateBefore, "never a squadmate");
		// turning back calls the lightning too
		gapOver(p);
		float again = near.getHealth();
		helper.assertTrue(ShazamMod.onChat(p, "shazam") == Shazam.Spoken.TURNED_BACK, "turned back");
		helper.assertTrue(near(lost(again, near), ShazamConfig.WORD_BOLT_DAMAGE), "the turning-back bolt still hits nearby enemies");
		helper.assertTrue(mate.getHealth() == mateBefore, "and still never a squadmate");
		disband(helper, squad);
		helper.succeed();
	}

	// ---------------------------------------------------------------- passives

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void passivesOnlyWhileTransformed(GameTestHelper helper) {
		ServerPlayer p = shazam(helper, 1.5, 1.5);
		double baseSpeed = p.getAttribute(Attributes.MOVEMENT_SPEED).getBaseValue();
		helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.MOVEMENT_SPEED) - baseSpeed * 1.4) < 1e-3, "Speed of Mercury: +40%");
		helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.ATTACK_DAMAGE) - (1.0 + 10.0)) < 1e-3, "Strength of Hercules: +10 melee");
		helper.assertTrue(Math.abs(p.getBbHeight() * 1.0 - 1.9) < 0.02 || p.getAttributeValue(Attributes.SCALE) > 1.05, "0.1 blocks taller");
		// Stamina of Atlas: 35% off; magic +50% on top
		var dmg = p.damageSources();
		helper.assertTrue(Math.abs(ShazamDamage.factor(p, dmg.generic()) - 0.65f) < 1e-4, "35% damage resistance");
		helper.assertTrue(Math.abs(ShazamDamage.factor(p, dmg.magic()) - 0.975f) < 1e-4, "magic hurts 50% more (before the resistance)");
		helper.assertTrue(ShazamDamage.isMagic(dmg.indirectMagic(p, null)), "potions count as magic");
		helper.assertTrue(ShazamDamage.factor(p, dmg.genericKill()) == 1f, "/kill still kills");
		// no hunger drain
		p.getFoodData().addExhaustion(3.5f);
		Shazam.tick(p);
		helper.assertTrue(p.getFoodData().getExhaustionLevel() == 0f, "no hunger drain");
		// Courage of Achilles
		helper.assertFalse(p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 200)), "the Warden's darkness doesn't take");
		helper.assertTrue(Shazam.fearImmune(p), "other powers' fear asks fearImmune");
		helper.assertTrue(p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 200)), "other effects still do");
		// turned back: all gone
		gapOver(p);
		Shazam.speak(p);
		helper.assertTrue(ShazamDamage.factor(p, dmg.generic()) == 1f, "no resistance as Billy");
		helper.assertTrue(p.getAttributeValue(Attributes.ATTACK_DAMAGE) < 2.0, "no melee bonus as Billy");
		helper.assertTrue(p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 200)), "Billy can be afraid");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void lightningBarRefillsFromStrikesNearby(GameTestHelper helper) {
		ServerPlayer p = shazam(helper, 4.5, 4.5);
		Shazam.setLightning(p, 0f);
		var bolt = EntityType.LIGHTNING_BOLT.create(helper.getLevel());
		bolt.moveTo(p.getX() + 3, p.getY(), p.getZ());
		bolt.setVisualOnly(true);
		helper.getLevel().addFreshEntity(bolt);
		helper.assertTrue(Shazam.lightning(p) >= ShazamConfig.STRIKE_FILL - 0.01f, "a strike near him fills the bar, got " + Shazam.lightning(p));
		float now = Shazam.lightning(p);
		Shazam.bolt(helper.getLevel(), p.position());
		helper.assertTrue(Shazam.lightning(p) == now, "his own bolts don't count");
		helper.succeed();
	}

	// ---------------------------------------------------------------- moves

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void keysMapToTheSpec(GameTestHelper helper) {
		helper.assertTrue(ShazamAbilityManager.LIGHTNING_HAND.equals(ShazamAbilityManager.moveFor(AbilitySlot.SLOT_1, false)), "R");
		helper.assertTrue(ShazamAbilityManager.CALL_LIGHTNING.equals(ShazamAbilityManager.moveFor(AbilitySlot.SLOT_1, true)), "Shift+R");
		helper.assertTrue(ShazamAbilityManager.HERCULES_PUNCH.equals(ShazamAbilityManager.moveFor(AbilitySlot.SLOT_2, false)), "G");
		helper.assertTrue(ShazamAbilityManager.ATLAS_LIFT.equals(ShazamAbilityManager.moveFor(AbilitySlot.SLOT_2, true)), "Shift+G");
		helper.assertTrue(ShazamAbilityManager.MERCURY_DASH.equals(ShazamAbilityManager.moveFor(AbilitySlot.SLOT_3, false)), "X = movement");
		helper.assertTrue(ShazamAbilityManager.THUNDER_TACKLE.equals(ShazamAbilityManager.moveFor(AbilitySlot.SLOT_3, true)), "Shift+X");
		helper.assertTrue(ShazamAbilityManager.ZEUS_STORM.equals(ShazamAbilityManager.moveFor(AbilitySlot.SLOT_4, false)), "Z");
		helper.assertTrue(ShazamAbilityManager.ETERNITY_STRIKE.equals(ShazamAbilityManager.moveFor(AbilitySlot.SLOT_4, true)), "Shift+Z");
		helper.assertTrue(ShazamAbilityManager.WISDOM.equals(ShazamAbilityManager.moveFor(AbilitySlot.SLOT_5, false)), "V = utility");
		helper.assertTrue(ShazamAbilityManager.SHARE_POWER.equals(ShazamAbilityManager.moveFor(AbilitySlot.SLOT_5, true)), "Shift+V");
		helper.assertTrue(ShazamAbilityManager.THUNDERCLAP.equals(ShazamAbilityManager.moveFor(AbilitySlot.SLOT_6, false)), "C");
		helper.assertTrue(ShazamAbilityManager.ACHILLES_STAND.equals(ShazamAbilityManager.moveFor(AbilitySlot.SLOT_6, true)), "Shift+C");
		for (AbilitySlot s : AbilitySlot.values()) {
			if (s.ordinal() >= AbilitySlot.CORE_COUNT) {
				helper.assertTrue(ShazamAbilityManager.moveFor(s, false) == null && ShazamAbilityManager.moveFor(s, true) == null, "H / N are never moves");
			}
		}
		ServerPlayer billy = mock(helper, 1.5, 1.5);
		Shazam.grant(billy);
		helper.assertFalse(ShazamAbilityManager.hasContext(billy), "Billy has no moves until he says the word");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void lightningHandChainsToTwoMore(GameTestHelper helper) {
		ServerPlayer p = shazam(helper, 1.5, 1.5);
		Zombie a = zombie(helper, 1.5, 5.5, 100);
		Zombie b = zombie(helper, 3.5, 5.5, 100);
		Zombie c = zombie(helper, 5.5, 6.5, 100);
		Zombie d = zombie(helper, 6.5, 1.0, 100); // far from the chain
		look(p, a);
		float ha = a.getHealth(), hb = b.getHealth(), hc = c.getHealth(), hd = d.getHealth();
		helper.assertTrue(ShazamMoves.use(p, ShazamAbilityManager.LIGHTNING_HAND), "it fires");
		helper.assertTrue(near(lost(ha, a), 9f) && near(lost(hb, b), 9f) && near(lost(hc, c), 9f), "9 to the first and to 2 chained");
		helper.assertTrue(d.getHealth() == hd, "a 4th creature isn't chained");
		helper.assertTrue(Math.abs(Shazam.lightning(p) - (100f - ShazamConfig.HAND_COST)) < 0.01f, "8 lightning");
		helper.assertFalse(ShazamMoves.use(p, ShazamAbilityManager.LIGHTNING_HAND), "on cooldown");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void callTheLightningHits22(GameTestHelper helper) {
		ServerPlayer p = shazam(helper, 1.5, 1.5);
		Zombie z = zombie(helper, 5.5, 6.0, 100);
		look(p, z);
		float h = z.getHealth();
		helper.assertTrue(ShazamMoves.use(p, ShazamAbilityManager.CALL_LIGHTNING), "it fires");
		helper.assertTrue(near(lost(h, z), 22f), "22 damage, got " + lost(h, z));
		helper.assertTrue(Math.abs(Shazam.lightning(p) - 70f) < 0.01f, "30 lightning");
		helper.assertTrue(p.getHealth() == p.getMaxHealth(), "the bolt never hurts him");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void herculesPunchLaunchesAndWallsHurt(GameTestHelper helper) {
		ServerPlayer p = shazam(helper, 4.5, 2.0);
		Zombie z = zombie(helper, 4.5, 4.5, 100);
		look(p, z);
		float h = z.getHealth();
		helper.assertTrue(ShazamMoves.use(p, ShazamAbilityManager.HERCULES_PUNCH), "it lands");
		helper.assertTrue(near(lost(h, z), 16f), "16 damage, got " + lost(h, z));
		Vec3 v = z.getDeltaMovement();
		helper.assertTrue(Math.sqrt(v.x * v.x + v.z * v.z) > 1.2 && v.z > 0, "launched hard along his look (about 15 blocks)");
		helper.assertTrue(ShazamMoves.inFlight(p, z), "tracked for the wall hit");
		float h2 = z.getHealth();
		helper.assertTrue(ShazamMoves.impact(p, z), "hitting a wall");
		helper.assertTrue(near(lost(h2, z), ShazamConfig.WALL_DAMAGE), "extra damage against a wall");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void atlasLiftHoldsThenHurls(GameTestHelper helper) {
		ServerPlayer p = shazam(helper, 4.5, 2.0);
		Zombie z = zombie(helper, 4.5, 4.0, 100);
		look(p, z);
		helper.assertTrue(ShazamMoves.use(p, ShazamAbilityManager.ATLAS_LIFT), "grabbed");
		helper.assertTrue(ShazamMoves.lifted(p) == z, "holding it");
		helper.assertTrue(z.getY() > p.getY() + 1.5, "held overhead");
		helper.assertTrue(Math.abs(Shazam.lightning(p) - 85f) < 0.01f, "15 lightning");
		helper.assertTrue(ShazamMoves.use(p, ShazamAbilityManager.ATLAS_LIFT), "pressed again: hurled");
		helper.assertTrue(ShazamMoves.lifted(p) == null && ShazamMoves.inFlight(p, z), "thrown and flying");
		helper.assertTrue(z.getDeltaMovement().length() > 1.5, "thrown hard");
		helper.assertTrue(Math.abs(Shazam.lightning(p) - 85f) < 0.01f, "the throw is free");
		float h = z.getHealth();
		ShazamMoves.impact(p, z);
		helper.assertTrue(near(lost(h, z), ShazamConfig.LIFT_IMPACT_DAMAGE), "the landing hurts");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void zeusStormStrikesHostiles(GameTestHelper helper) {
		ServerPlayer p = shazam(helper, 1.5, 1.5);
		Zombie z = zombie(helper, 6.5, 6.5, 100);
		helper.assertTrue(ShazamMoves.use(p, ShazamAbilityManager.ZEUS_STORM), "the storm starts");
		helper.assertTrue(Shazam.storming(p), "for 5 seconds");
		helper.assertTrue(Math.abs(Shazam.lightning(p) - 50f) < 0.01f, "50 lightning");
		helper.assertTrue(Shazam.cooldownRemaining(p, ShazamAbilityManager.ZEUS_STORM) == ShazamConfig.STORM_COOLDOWN, "15 s cooldown");
		float h = z.getHealth();
		helper.assertTrue(ShazamMoves.stormBolt(p) == z, "a half-second bolt picks the hostile");
		helper.assertTrue(near(lost(h, z), 10f), "10 a bolt");
		helper.assertTrue(ShazamConfig.STORM_TICKS / ShazamConfig.STORM_EVERY == 10, "10 bolts over 5 s");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void eternityStrikeNeedsAFullBarAndHits50(GameTestHelper helper) {
		ServerPlayer p = shazam(helper, 4.5, 4.5);
		Zombie z = zombie(helper, 4.5, 7.0, 200);
		Shazam.setLightning(p, 99f);
		helper.assertFalse(ShazamMoves.use(p, ShazamAbilityManager.ETERNITY_STRIKE), "needs a full bar");
		Shazam.setLightning(p, 100f);
		helper.assertTrue(ShazamMoves.use(p, ShazamAbilityManager.ETERNITY_STRIKE), "it starts");
		helper.assertTrue(Shazam.eternityAge(p) == 0, "rising");
		helper.assertTrue(Shazam.cooldownRemaining(p, ShazamAbilityManager.ETERNITY_STRIKE) == 3 * 60 * 20, "3 minute cooldown");
		float h = z.getHealth();
		float ph = p.getHealth();
		ShazamMoves.eternityBlast(p);
		helper.assertTrue(near(lost(h, z), 50f), "50 within 10 blocks, got " + lost(h, z));
		helper.assertTrue(Shazam.lightning(p) == 0f && Shazam.eternityAge(p) < 0, "the bar is emptied and it's over");
		helper.assertTrue(p.getHealth() >= ph, "the huge bolt never hurts him");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void mercuryDashAndThunderTackle(GameTestHelper helper) {
		ServerPlayer p = shazam(helper, 1.5, 1.0);
		p.setYRot(0f);
		p.setXRot(0f);
		Vec3 before = p.position();
		helper.assertTrue(ShazamMoves.use(p, ShazamAbilityManager.MERCURY_DASH), "dashes");
		double moved = p.position().z - before.z;
		helper.assertTrue(moved > 3.0 && moved <= ShazamConfig.DASH_DISTANCE + 0.01, "along his look, stopped by the wall, moved " + moved);
		helper.assertTrue(Shazam.lightning(p) == 100f, "free");
		ServerPlayer q = shazam(helper, 6.5, 1.0);
		Zombie z = zombie(helper, 6.5, 6.0, 100);
		look(q, z);
		float h = z.getHealth();
		helper.assertTrue(ShazamMoves.use(q, ShazamAbilityManager.THUNDER_TACKLE), "the tackle");
		helper.assertTrue(near(lost(h, z), 18f), "18 damage, got " + lost(h, z));
		helper.assertTrue(q.distanceTo(z) < 3.0, "he flew to it");
		helper.assertTrue(Math.abs(Shazam.lightning(q) - 80f) < 0.01f, "20 lightning");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void thunderclapHitsTheConeOnly(GameTestHelper helper) {
		ServerPlayer p = shazam(helper, 4.5, 4.0);
		p.setYRot(0f);
		p.setXRot(0f);
		Zombie front = zombie(helper, 4.5, 7.0, 100);
		Zombie behind = zombie(helper, 4.5, 1.0, 100);
		p.setYRot(0f);
		p.setXRot(0f);
		float hf = front.getHealth(), hb = behind.getHealth();
		helper.assertTrue(ShazamMoves.use(p, ShazamAbilityManager.THUNDERCLAP), "clap");
		helper.assertTrue(near(lost(hf, front), 12f), "12 in the cone, got " + lost(hf, front));
		helper.assertTrue(behind.getHealth() == hb, "nothing behind him");
		helper.assertTrue(front.getDeltaMovement().z > 0.5, "knocked back");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void achillesStandTauntsAndShields(GameTestHelper helper) {
		ServerPlayer p = shazam(helper, 2.5, 2.5);
		ServerPlayer mate = mock(helper, 4.5, 2.5);
		var squad = squad(helper, p, mate);
		Zombie z = zombie(helper, 6.5, 6.5, 100);
		z.setTarget(mate);
		helper.assertTrue(ShazamMoves.use(p, ShazamAbilityManager.ACHILLES_STAND), "the stand");
		helper.assertTrue(z.getTarget() == p, "hostiles come for him");
		helper.assertTrue(ShazamDamage.factor(p, p.damageSources().generic()) == 0f, "he takes no damage");
		helper.assertTrue(Math.abs(ShazamDamage.factor(mate, mate.damageSources().generic()) - 0.7f) < 1e-4, "squadmates take 30% less");
		helper.assertTrue(Shazam.cooldownRemaining(p, ShazamAbilityManager.ACHILLES_STAND) == 30 * 20, "30 s cooldown");
		edit(p, s -> s.standUntil = 0L);
		helper.assertTrue(Math.abs(ShazamDamage.factor(p, p.damageSources().generic()) - 0.65f) < 1e-4, "over after 4 s");
		disband(helper, squad);
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void wisdomOfSolomonLastsTenSeconds(GameTestHelper helper) {
		ServerPlayer p = shazam(helper, 1.5, 1.5);
		helper.assertTrue(ShazamMoves.use(p, ShazamAbilityManager.WISDOM), "Wisdom");
		helper.assertTrue(Shazam.wise(p), "on");
		helper.assertTrue(Shazam.state(p).wisdomUntil - helper.getLevel().getGameTime() == ShazamConfig.WISDOM_TICKS, "10 s");
		helper.assertTrue(Shazam.lightning(p) == 100f, "free");
		helper.succeed();
	}

	// ---------------------------------------------------------------- the Shazam Family

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void sharingThePowerMakesAHalfStrengthFamily(GameTestHelper helper) {
		ServerPlayer p = shazam(helper, 1.5, 1.5);
		ServerPlayer mate = mock(helper, 1.5, 4.5);
		ServerPlayer outsider = mock(helper, 6.5, 6.5);
		var squad = squad(helper, p, mate);
		helper.assertFalse(ShazamFamily.share(p, outsider), "only a squadmate");
		look(p, mate);
		helper.assertTrue(ShazamMoves.use(p, ShazamAbilityManager.SHARE_POWER), "Shift+V at the squadmate");
		helper.assertTrue(Shazam.isFamily(mate) && Shazam.canSpeak(mate) && !Shazam.hasPower(mate), "family, not a second champion");
		helper.assertTrue(Shazam.state(mate).familyUntil - helper.getLevel().getGameTime() == ShazamConfig.FAMILY_TICKS, "for 10 minutes");
		helper.assertTrue(Shazam.cooldownRemaining(p, ShazamAbilityManager.SHARE_POWER) == ShazamConfig.SHARE_COOLDOWN, "10 minute cooldown");
		helper.assertTrue(ShazamMod.onChat(mate, "SHAZAM!") == Shazam.Spoken.TRANSFORMED, "the word transforms the family member too");
		helper.assertTrue(Math.abs(mate.getMaxHealth() - 25f) < 0.01f, "half the champion's health, got " + mate.getMaxHealth());
		helper.assertTrue(Math.abs(mate.getAttributeValue(Attributes.ATTACK_DAMAGE) - 6.0) < 1e-3, "half the melee bonus");
		helper.assertFalse(ShazamMoves.use(mate, ShazamAbilityManager.ETERNITY_STRIKE), "no Eternity Strike for the family");
		helper.assertFalse(ShazamAbilityManager.allowed(mate, ShazamAbilityManager.ETERNITY_STRIKE), "greyed on their HUD");
		Zombie z = zombie(helper, 1.5, 7.0, 100);
		look(mate, z);
		float h = z.getHealth();
		ShazamMoves.use(mate, ShazamAbilityManager.LIGHTNING_HAND);
		helper.assertTrue(near(lost(h, z), 4.5f), "half damage, got " + lost(h, z));
		// the 10 minutes run out
		edit(mate, s -> s.familyUntil = helper.getLevel().getGameTime());
		Shazam.tick(mate);
		helper.assertFalse(Shazam.canSpeak(mate) || Shazam.transformed(mate), "time's up: turned back, the word no longer works");
		helper.assertTrue(mate.getMaxHealth() == 20f, "the passives are gone");
		helper.assertTrue(ShazamMod.onChat(mate, "Shazam") == null, "and their 'Shazam' is ordinary chat again");
		disband(helper, squad);
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theFamilyHoldsFiveAtMost(GameTestHelper helper) {
		ServerPlayer p = shazam(helper, 1.5, 1.5);
		ServerPlayer[] mates = new ServerPlayer[6];
		for (int i = 0; i < mates.length; i++) {
			mates[i] = mock(helper, 2.5 + i % 3, 4.5 + i / 3);
		}
		var squad = squad(helper, p, mates);
		for (int i = 0; i < 5; i++) {
			Shazam.clearCooldowns(p);
			helper.assertTrue(ShazamFamily.share(p, mates[i]), "member " + (i + 1));
		}
		Shazam.clearCooldowns(p);
		helper.assertFalse(ShazamFamily.share(p, mates[5]), "a 6th is refused");
		helper.assertTrue(Shazam.state(p).familySize(helper.getLevel().getGameTime()) == ShazamConfig.FAMILY_MAX, "5 members");
		gapOver(p);
		Shazam.speak(p);
		helper.assertFalse(ShazamFamily.share(p, mates[5]), "only a transformed champion can share");
		Shazam.revoke(p);
		helper.assertFalse(Shazam.canSpeak(mates[0]), "the champion losing the power ends the family");
		disband(helper, squad);
		helper.succeed();
	}

	// ---------------------------------------------------------------- the Wizard

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theWizardJudgesThePureOfHeart(GameTestHelper helper) {
		ServerPlayer good = mock(helper, 1.5, 1.5);
		ServerPlayer bad = mock(helper, 6.5, 6.5);
		WizardShazamEntity w1 = ShazamEntities.WIZARD.create(helper.getLevel());
		Vec3 at = helper.absoluteVec(new Vec3(4.5, 2.0, 4.5));
		w1.moveTo(at.x, at.y, at.z);
		helper.getLevel().addFreshEntity(w1);
		helper.assertTrue(w1.isInvulnerableTo(good.damageSources().playerAttack(good)), "he can't be hurt");
		// a villager killed today: not pure
		var villager = helper.spawnWithNoFreeWill(EntityType.VILLAGER, new Vec3(6.5, 2.0, 1.5));
		villager.hurt(bad.damageSources().playerAttack(bad), 1000f);
		helper.assertFalse(ShazamWizards.pure(bad), "a villager killer is not pure of heart");
		helper.assertFalse(ShazamWizards.judge(w1, bad), "judged unworthy");
		helper.assertTrue(w1.isRemoved(), "he fades in a flash");
		helper.assertTrue(ShazamWizards.banned(bad), "and won't come back to him");
		helper.assertTrue(Shazam.state(bad).wizardBanUntil - helper.getLevel().getGameTime() == 5L * 24000L, "for 5 days");
		helper.assertFalse(Shazam.hasPower(bad), "no power");
		// the pure of heart
		WizardShazamEntity w2 = ShazamEntities.WIZARD.create(helper.getLevel());
		w2.moveTo(at.x, at.y, at.z);
		helper.getLevel().addFreshEntity(w2);
		helper.assertTrue(ShazamWizards.pure(good), "pure of heart");
		helper.assertTrue(ShazamWizards.judge(w2, good), "judged worthy");
		helper.assertTrue(Shazam.hasPower(good) && !Shazam.transformed(good), "granted; he still has to say the word");
		helper.assertTrue(w2.isRemoved(), "his work is done");
		helper.succeed();
	}
}
