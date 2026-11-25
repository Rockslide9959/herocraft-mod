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
		helper.assertTrue(Math.abs(p.getMaxHealth() - 40f) < 0.01f, "Stamina of Atlas: 40 health, got " + p.getMaxHealth());
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
		helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.ATTACK_DAMAGE) - (1.0 + 16.0)) < 1e-3, "Strength of Hercules: +16 melee (v0.17.7 Planet)");
		helper.assertTrue(Math.abs(p.getBbHeight() * 1.0 - 1.9) < 0.02 || p.getAttributeValue(Attributes.SCALE) > 1.05, "0.1 blocks taller");
		// Stamina of Atlas (v0.17.7 Planet): netherite-level armour instead of the old flat 35%; magic +50%
		var dmg = p.damageSources();
		helper.assertTrue(p.getAttributeValue(Attributes.ARMOR) >= 20.0 && p.getAttributeValue(Attributes.ARMOR_TOUGHNESS) >= 12.0, "armour 20, toughness 12");
		helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) - 0.4) < 1e-3, "40% knockback resistance");
		helper.assertTrue(ShazamDamage.factor(p, dmg.generic()) == 1f, "no flat cut any more (the armour does it)");
		helper.assertTrue(Math.abs(ShazamDamage.factor(p, dmg.magic()) - 1.5f) < 1e-4, "magic hurts 50% more");
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
		helper.assertTrue(ShazamDamage.factor(p, dmg.generic()) == 1f && p.getAttributeValue(Attributes.ARMOR) < 1.0, "no toughness as Billy");
		helper.assertTrue(p.getAttributeValue(Attributes.ATTACK_DAMAGE) < 2.0, "no melee bonus as Billy");
		helper.assertTrue(p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 200)), "Billy can be afraid");
		helper.succeed();
	}

	/** v0.17.4 (user): the bar fills 4 a second while he's transformed (a step of 0.2 a tick; a storm adds its own). */
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 20)
	public void lightningBarFillsFourASecond(GameTestHelper helper) {
		ServerPlayer p = shazam(helper, 4.5, 4.5);
		helper.assertTrue(Math.abs(ShazamConfig.LIGHTNING_REGEN_PER_TICK * 20f - 4f) < 1e-4f, "4 a second");
		float[] seen = new float[1];
		helper.runAfterDelay(1, () -> {
			Shazam.setLightning(p, 50f);
			Shazam.tick(p);
			seen[0] = Shazam.lightning(p);
		});
		helper.runAfterDelay(2, () -> {
			Shazam.tick(p);
			float step = Shazam.lightning(p) - seen[0];
			float expected = ShazamConfig.LIGHTNING_REGEN_PER_TICK + (helper.getLevel().isThundering() ? ShazamConfig.STORM_FILL_PER_TICK : 0f);
			helper.assertTrue(Math.abs(step - expected) < 1e-3f, "one tick fills " + expected + ", got " + step);
			Shazam.setLightning(p, ShazamConfig.LIGHTNING_MAX);
		});
		helper.runAfterDelay(3, () -> {
			Shazam.tick(p);
			helper.assertTrue(Shazam.lightning(p) == ShazamConfig.LIGHTNING_MAX, "never past full");
			helper.succeed();
		});
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
	// v0.17.7: the Planet-tier kit's moves are tested in ShazamKitGameTests.

	// ---------------------------------------------------------------- the Shazam Family

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void sharingThePowerMakesAHalfStrengthFamily(GameTestHelper helper) {
		ServerPlayer p = shazam(helper, 1.5, 1.5);
		ServerPlayer mate = mock(helper, 1.5, 4.5);
		ServerPlayer outsider = mock(helper, 6.5, 6.5);
		var squad = squad(helper, p, mate);
		helper.assertFalse(ShazamFamily.share(p, outsider), "only a squadmate");
		look(p, mate);
		helper.assertTrue(ShazamMoves.use(p, ShazamAbilityManager.SHARE_POWER), "hold V at the squadmate (v0.17.7; was Shift+V)");
		helper.assertTrue(Shazam.isFamily(mate) && Shazam.canSpeak(mate) && !Shazam.hasPower(mate), "family, not a second champion");
		helper.assertTrue(Shazam.state(mate).familyUntil - helper.getLevel().getGameTime() == ShazamConfig.FAMILY_TICKS, "for 10 minutes");
		helper.assertTrue(Shazam.cooldownRemaining(p, ShazamAbilityManager.SHARE_POWER) == ShazamConfig.SHARE_COOLDOWN, "10 minute cooldown");
		helper.assertTrue(ShazamMod.onChat(mate, "SHAZAM!") == Shazam.Spoken.TRANSFORMED, "the word transforms the family member too");
		helper.assertTrue(Math.abs(mate.getMaxHealth() - 25f) < 0.01f, "half the champion's health, got " + mate.getMaxHealth());
		helper.assertTrue(Math.abs(mate.getAttributeValue(Attributes.ATTACK_DAMAGE) - 9.0) < 1e-3, "half the melee bonus (+8)");
		helper.assertFalse(ShazamMoves.use(mate, ShazamAbilityManager.THUNDERSTRIKE), "no ultimate for the family");
		helper.assertFalse(ShazamAbilityManager.allowed(mate, ShazamAbilityManager.THUNDERSTRIKE), "greyed on their HUD");
		Zombie z = zombie(helper, 1.5, 7.0, 100);
		look(mate, z);
		float h = z.getHealth();
		ShazamMoves.use(mate, ShazamAbilityManager.HERCULES_COMBO);
		helper.assertTrue(near(lost(h, z), ShazamConfig.COMBO_DAMAGE_1 * 0.5f), "half damage, got " + lost(h, z));
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

	// ---------------------------------------------------------------- v0.17.3 effects pass: the landing, the dash fix

	/** A fast touchdown out of his flight is a hero landing in the lightning-crack style; a gentle one is not. */
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 100)
	public void shazamFlightLandsInTheLightningCrackStyle(GameTestHelper helper) {
		ServerPlayer p = shazam(helper, 3.5, 3.5);
		helper.assertTrue(com.projecthero.mod.flight.landing.HeroLandingStyle.byId(com.projecthero.mod.flight.landing.HeroLandingStyle.SHAZAM.id)
				== com.projecthero.mod.flight.landing.HeroLandingStyle.SHAZAM, "the style id round-trips");
		Vec3 fast = new Vec3(0.0, -0.9, 0.0);
		for (int i = 0; i < 4; i++) {
			helper.runAfterDelay(i, () -> {
				edit(p, s -> s.flying = true);
				p.setOnGround(false);
				p.setKnownMovement(fast);
				com.projecthero.mod.flight.landing.HeroLanding.tick(p);
			});
		}
		helper.runAfterDelay(4, () -> {
			p.setOnGround(true);
			com.projecthero.mod.flight.landing.HeroLanding.tick(p);
			var a = com.projecthero.mod.flight.landing.HeroLanding.active(p);
			helper.assertTrue(a != null && a.style() == com.projecthero.mod.flight.landing.HeroLandingStyle.SHAZAM,
					"a fast touchdown out of Shazam's flight is a Shazam hero landing, got " + (a == null ? "none" : a.style()));
			com.projecthero.mod.flight.landing.HeroLanding.end(p);
			// a gentle one is not
			edit(p, s -> s.flying = true);
			p.setOnGround(false);
			p.setKnownMovement(new Vec3(0.05, -0.2, 0.0));
		});
		for (int i = 5; i < 9; i++) {
			helper.runAfterDelay(i, () -> {
				p.setOnGround(false);
				p.setKnownMovement(new Vec3(0.05, -0.2, 0.0));
				com.projecthero.mod.flight.landing.HeroLanding.tick(p);
			});
		}
		helper.runAfterDelay(9, () -> {
			p.setOnGround(true);
			com.projecthero.mod.flight.landing.HeroLanding.tick(p);
			helper.assertTrue(!com.projecthero.mod.flight.landing.HeroLanding.landing(p), "a gentle touchdown just lands");
			com.projecthero.mod.flight.landing.HeroLanding.forget(p);
			p.discard();
			helper.succeed();
		});
	}

	/** Mercury Dash looking a little down while standing used to run into the floor and do nothing; now it dashes along the ground. */
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void mercuryDashLookingSlightlyDownStillDashes(GameTestHelper helper) {
		ServerPlayer p = shazam(helper, 1.5, 1.5);
		for (int i = 0; i < 200 && p.level().noCollision(p, p.getBoundingBox().move(0, -0.05, 0)); i++) {
			p.teleportTo(p.getX(), p.getY() - 0.05, p.getZ()); // stand him right on the floor
		}
		p.setOnGround(true);
		p.setYRot(0f);
		p.setXRot(20f);
		Vec3 from = p.position();
		helper.assertTrue(ShazamMoves.use(p, ShazamAbilityManager.MERCURY_DASH), "the dash goes off");
		double moved = p.position().distanceTo(from);
		helper.assertTrue(moved >= 2.0, "it moved him along the ground, moved " + moved);
		helper.assertTrue(Math.abs(p.getY() - from.y) < 0.6, "and stayed on the ground, dy " + (p.getY() - from.y) + " onGround " + p.onGround() + " look " + p.getViewVector(1f));
		p.discard();
		helper.succeed();
	}

	/** His moves' bolts are no LightningBolt entities any more (the geometry is client side), so they never burn or refill his bar. */
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void shazamBoltsSpawnNoVanillaLightning(GameTestHelper helper) {
		ServerPlayer p = shazam(helper, 3.5, 3.5);
		Shazam.bolt(helper.getLevel(), p.position());
		Shazam.bigBolt(helper.getLevel(), p.position());
		int bolts = helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.LightningBolt.class, p.getBoundingBox().inflate(8)).size();
		helper.assertTrue(bolts == 0, "no vanilla bolts, found " + bolts);
		p.discard();
		helper.succeed();
	}
}
