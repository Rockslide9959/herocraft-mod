package com.projecthero.mod.gametest;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.HeroTiers;
import com.projecthero.mod.hero.PowerGrants;
import com.projecthero.mod.hero.UniquePowers;
import com.projecthero.mod.hero.UniquePowersData;
import com.projecthero.mod.kryptonian.Kryptonian;
import com.projecthero.mod.miraculous.Kwami;
import com.projecthero.mod.miraculous.KwamiCompanion;
import com.projecthero.mod.miraculous.Miraculous;
import com.projecthero.mod.miraculous.MiraculousConfig;
import com.projecthero.mod.miraculous.MiraculousPhrases;
import com.projecthero.mod.miraculous.data.MiraculousState;
import com.projecthero.mod.miraculous.entity.KwamiEntity;
import com.projecthero.mod.miraculous.item.MiraculousBoxItem;
import com.projecthero.mod.miraculous.item.MiraculousItems;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.6 the Miraculous core: grant / revoke for each Kwami and the one-Primary rule, the spoken phrases, the
 * transformation's stats on and off, the countdown's forced detransformation, the hungry Kwami (after every
 * detransformation) refusing the next transformation until fed, the box and the jewels (the unique-powers refusal before
 * anything is used up), half fall damage while transformed (none after a movement move), and the Kwami that follows and can't die. Mock players are
 * not ticked by the server, so tests drive {@link Miraculous#tick} themselves.
 */
public class MiraculousCoreGameTests implements FabricGameTest {
	private static ServerPlayer mock(GameTestHelper helper) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(3.5, 2.0, 3.5));
		p.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);
		try {
			java.lang.reflect.Field f = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
			f.setAccessible(true);
			f.setInt(p, 0);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
		return p;
	}

	/** Pretend the last change was long ago (the debounce and the transformation sequence are over). */
	private static void settle(ServerPlayer p) {
		MiraculousState n = Miraculous.state(p).copy();
		n.formChangeAt = p.level().getGameTime() - 200L;
		p.setAttached(ModAttachments.MIRACULOUS_STATE, n);
	}

	private static void fed(ServerPlayer p) {
		Miraculous.edit(p, n -> n.hungry = false);
	}

	// ---------------------------------------------------------------- grant / revoke

	@GameTest(template = EMPTY_STRUCTURE)
	public void grantAndRevokeEachKwami(GameTestHelper helper) {
		for (Kwami k : Kwami.values()) {
			ServerPlayer p = mock(helper);
			helper.assertTrue(HeroTiers.HERO_KEYS.contains(k.heroKey) && PowerGrants.HERO_TIER_KEYS.contains(k.heroKey), k.heroKey + " is a Hero-Tier key");
			helper.assertTrue(Miraculous.grant(p, k), k.id + " granted");
			helper.assertTrue(Miraculous.holds(p, k) && HeroTiers.holdsHero(p, k.heroKey) && HeroTiers.heroCount(p) == 1, k.id + " held, one hero");
			for (Kwami other : Kwami.values()) {
				if (other != k) {
					helper.assertFalse(HeroTiers.holdsHero(p, other.heroKey), "not also " + other.heroKey);
				}
			}
			helper.assertFalse(Miraculous.grant(p, k), "a second grant of the same Kwami does nothing");
			helper.assertTrue(PowerGrants.revokeHeroTier(p, k.heroKey), "revoke by key");
			helper.assertFalse(Miraculous.hasPower(p), k.id + " revoked");
			helper.assertTrue(KwamiCompanion.find(p) == null, "the Kwami is gone with the power");
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void oneKwamiAtATimeAndPrimaryReplacement(GameTestHelper helper) {
		ServerPlayer p = mock(helper);
		Kryptonian.grant(p);
		helper.assertTrue(Miraculous.grant(p, Kwami.TIKKI), "Tikki granted");
		helper.assertTrue(!Kryptonian.hasPower(p) && HeroTiers.heroCount(p) == 1, "the Miraculous replaced the Kryptonian");
		helper.assertTrue(PowerGrants.grantHero(p, "cat_noir").ok(), "the command grants Cat Noir");
		helper.assertTrue(Miraculous.holds(p, Kwami.PLAGG) && !Miraculous.holds(p, Kwami.TIKKI) && HeroTiers.heroCount(p) == 1,
				"Plagg replaced Tikki");
		Kryptonian.grant(p);
		helper.assertTrue(Kryptonian.hasPower(p) && !Miraculous.hasPower(p), "a new hero replaces the Miraculous");
		helper.succeed();
	}

	// ---------------------------------------------------------------- transforming

	@GameTest(template = EMPTY_STRUCTURE)
	public void phrasesTransformAndStatsComeAndGo(GameTestHelper helper) {
		ServerPlayer p = mock(helper);
		Miraculous.grant(p, Kwami.TRIXX);
		double armour = p.getAttributeValue(Attributes.ARMOR);
		helper.assertFalse(Miraculous.onChat(p, "Tikki, spots on!"), "another Kwami's phrase means nothing");
		helper.assertFalse(Miraculous.transformed(p), "still not transformed");
		helper.assertTrue(Miraculous.onChat(p, "  trixx ,  LET\u2019S   pounce!!! "), "the phrase, leniently (case, spaces, curly apostrophe)");
		helper.assertTrue(Miraculous.transformedAs(p, Kwami.TRIXX), "transformed");
		helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.ARMOR) - armour - MiraculousConfig.ARMOR_BONUS) < 0.01, "the suit's armour");
		// v0.16.6 (user): Regeneration I while transformed
		var regen = p.getEffect(net.minecraft.world.effect.MobEffects.REGENERATION);
		helper.assertTrue(regen != null && regen.getAmplifier() == 0, "Regeneration I while transformed");
		helper.assertFalse(Miraculous.canAct(p), "the moves wait for the sequence");
		Miraculous.onChat(p, "Let's rest!");
		helper.assertTrue(Miraculous.transformed(p), "can't change back mid-sequence");
		settle(p);
		helper.assertTrue(Miraculous.canAct(p), "the sequence is over");
		helper.assertTrue(Miraculous.onChat(p, "Trixx, lets rest"), "the detransform phrase with the name");
		helper.assertFalse(Miraculous.transformed(p), "changed back");
		helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.ARMOR) - armour) < 0.01, "the armour is gone");
		helper.assertTrue(p.getEffect(net.minecraft.world.effect.MobEffects.REGENERATION) == null, "and the Regeneration");
		helper.assertTrue(Miraculous.hungry(p), "every detransformation leaves the Kwami hungry");
		helper.assertTrue(MiraculousPhrases.match(Kwami.TIKKI, "SPOTS OFF") == MiraculousPhrases.Spoken.DETRANSFORM
				&& MiraculousPhrases.match(Kwami.PLAGG, "plagg claws out") == MiraculousPhrases.Spoken.TRANSFORM
				&& MiraculousPhrases.match(Kwami.PLAGG, "claws out") == MiraculousPhrases.Spoken.TRANSFORM // v0.16.7: the name is optional
				&& MiraculousPhrases.match(Kwami.TIKKI, "Spots on!") == MiraculousPhrases.Spoken.TRANSFORM
				&& MiraculousPhrases.match(Kwami.TIKKI, "claws out") == null
				&& MiraculousPhrases.match(Kwami.NOOROO, "Nooroo, DARK wings rise!") == MiraculousPhrases.Spoken.TRANSFORM
				&& MiraculousPhrases.match(Kwami.NOOROO, "nooroo dark wings fall") == MiraculousPhrases.Spoken.DETRANSFORM, "the phrase table");
		helper.succeed();
	}

	/** v0.16.7 (user): a spoken phrase goes out as the HERO saying it ("<Ladybug> Spots on!"), never under the player's name. */
	@GameTest(template = EMPTY_STRUCTURE)
	public void phrasesAreSpokenAsTheHero(GameTestHelper helper) {
		helper.assertFalse(com.projecthero.mod.miraculous.MiraculousShowExtras.speakPhrase(mock(helper), "Spots on!"), "no Kwami: just chat");
		ServerPlayer p = mock(helper);
		Miraculous.grant(p, Kwami.TIKKI);
		helper.assertFalse(com.projecthero.mod.miraculous.MiraculousShowExtras.speakPhrase(p, "hello there"), "other chat is untouched");
		helper.assertFalse(com.projecthero.mod.miraculous.MiraculousShowExtras.speakPhrase(p, "Claws out!"), "another Kwami's phrase is just chat");
		for (String said : new String[] { "Spots on!", "Tikki, spots on!" }) {
			helper.assertTrue(com.projecthero.mod.miraculous.MiraculousShowExtras.speakPhrase(p, said), "'" + said + "' is the phrase: the normal line is cancelled");
		}
		net.minecraft.network.chat.Component line = com.projecthero.mod.miraculous.MiraculousShowExtras.phraseLine(Kwami.TIKKI, "Tikki, spots on!");
		helper.assertTrue(line.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t && t.getKey().equals("chat.type.text")
				&& t.getArgs().length == 2 && t.getArgs()[0] instanceof net.minecraft.network.chat.Component hero
				&& hero.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents h && h.getKey().equals("projecthero.hero_tier.ladybug")
				&& hero.getStyle().getColor() != null && hero.getStyle().getColor().getValue() == (Kwami.TIKKI.color & 0xFFFFFF)
				&& t.getArgs()[1] instanceof net.minecraft.network.chat.Component said && said.getString().equals("Tikki, spots on!"),
				"a chat line from Ladybug, in Tikki's colour, in the player's own words: " + line);
		helper.assertTrue(line.getString().equals("<Ladybug> Tikki, spots on!"), "reads " + line.getString());
		helper.assertTrue(Miraculous.onChat(p, "Tikki, spots on!") && Miraculous.transformedAs(p, Kwami.TIKKI), "and the transformation still happens");
		settle(p);
		helper.assertTrue(com.projecthero.mod.miraculous.MiraculousShowExtras.speakPhrase(p, "Spots off!"), "the detransformation phrase too");
		for (Kwami k : Kwami.values()) {
			helper.assertTrue(com.projecthero.mod.miraculous.MiraculousShowExtras.phraseLine(k, "x").getString().startsWith("<" + MiraculousPhrases.heroName(k) + ">"),
					"every Kwami speaks as its hero: " + k.id);
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void countdownForcesDetransformAndTheKwamiMustBeFed(GameTestHelper helper) {
		ServerPlayer p = mock(helper);
		Miraculous.grant(p, Kwami.TIKKI);
		Miraculous.toggleForm(p);
		helper.assertTrue(Miraculous.transformed(p), "transformed by the command path");
		settle(p);
		Miraculous.startCountdown(p);
		helper.assertTrue(Miraculous.countdownLeft(p) == MiraculousConfig.COUNTDOWN_TICKS, "3 minutes");
		long end = p.level().getGameTime() + 2L;
		Miraculous.edit(p, n -> n.countdownEnd = end);
		helper.runAfterDelay(4, () -> {
			Miraculous.tick(p);
			helper.assertFalse(Miraculous.transformed(p), "time's up: forced detransformation");
			helper.assertTrue(Miraculous.hungry(p), "the Kwami is hungry");
			settle(p);
			Miraculous.toggleForm(p);
			helper.assertFalse(Miraculous.transformed(p), "a hungry Kwami refuses to transform");
			ItemStack stone = new ItemStack(Items.STONE);
			helper.assertFalse(Miraculous.feed(p, stone), "not food");
			ItemStack cookies = new ItemStack(Items.COOKIE, 3);
			helper.assertTrue(Miraculous.wantsFood(p, cookies), "eating a cookie feeds the hungry Kwami");
			helper.assertTrue(Miraculous.feed(p, cookies), "Tikki starts eating");
			helper.assertTrue(cookies.getCount() == 2, "one cookie used");
			// v0.16.6 (user): a meal takes 10 s -- still hungry (and no transforming) until it is over
			helper.assertTrue(Miraculous.hungry(p) && Miraculous.eatingSecondsLeft(p) == MiraculousConfig.EAT_TICKS / 20, "10 s to eat");
			Miraculous.toggleForm(p);
			helper.assertFalse(Miraculous.transformed(p), "no transforming mid-meal");
			helper.assertTrue(Miraculous.finishMeal(p), "the meal ends");
			helper.assertTrue(!Miraculous.hungry(p), "fed once the meal is over");
			helper.assertFalse(Miraculous.wantsFood(p, cookies), "not hungry: you eat your own cookies");
			settle(p);
			Miraculous.toggleForm(p);
			helper.assertTrue(Miraculous.transformed(p), "fed: transforming works again");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void snackWhenNotHungryHeals(GameTestHelper helper) {
		ServerPlayer p = mock(helper);
		Miraculous.grant(p, Kwami.PLAGG);
		ItemStack cheese = new ItemStack(MiraculousItems.CAMEMBERT, 2);
		helper.assertTrue(Miraculous.feed(p, cheese), "Plagg snacks");
		helper.assertFalse(Miraculous.feed(p, cheese), "not while still eating");
		helper.assertTrue(Miraculous.finishMeal(p), "the meal ends");
		helper.assertTrue(p.hasEffect(net.minecraft.world.effect.MobEffects.REGENERATION), "a little Regeneration for you when the snack is eaten");
		helper.succeed();
	}

	/** v0.16.6 (user): 10 pieces of any other food stand in for the favourite. */
	@GameTest(template = EMPTY_STRUCTURE)
	public void tenOfAnyFoodFeedsAHungryKwami(GameTestHelper helper) {
		ServerPlayer p = mock(helper);
		Miraculous.grant(p, Kwami.TRIXX);
		Miraculous.edit(p, n -> n.hungry = true);
		ItemStack bread = new ItemStack(Items.BREAD, 4);
		helper.assertTrue(Miraculous.feed(p, bread), "4 bread handed over");
		helper.assertTrue(bread.isEmpty() && Miraculous.hungry(p) && Miraculous.eatingSecondsLeft(p) == 0, "4 of 10: not eating yet");
		ItemStack apples = new ItemStack(Items.APPLE, 9);
		helper.assertTrue(Miraculous.feed(p, apples), "the rest");
		helper.assertTrue(apples.getCount() == 3, "only the 6 still needed are taken, left " + apples.getCount());
		helper.assertTrue(Miraculous.eatingSecondsLeft(p) == MiraculousConfig.SUBSTITUTE_EAT_TICKS / 20, "10 pieces: it eats, for 22 s (v0.16.7)");
		helper.assertTrue(Miraculous.finishMeal(p), "the meal ends");
		helper.assertFalse(Miraculous.hungry(p), "fed");
		helper.assertFalse(Miraculous.feed(p, new ItemStack(Items.BREAD, 10)), "substitute food only for a hungry Kwami");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void everyGrantPathPlaysTheWelcomeAndNoorooReturnsTheBottle(GameTestHelper helper) {
		ServerPlayer p = mock(helper);
		helper.assertTrue(PowerGrants.grantHero(p, "hawk_moth").ok(), "the command grants Hawk Moth");
		KwamiEntity k = KwamiCompanion.find(p);
		helper.assertTrue(k != null && k.kwami() == Kwami.NOOROO && k.mode() == KwamiEntity.MODE_POP_OUT, "Nooroo pops out and waves");
		helper.assertTrue(KwamiCompanion.introPending(p) == KwamiCompanion.INTRO_LINES, "the intro lines are on their way");
		Miraculous.edit(p, n -> n.hungry = true);
		ItemStack honey = new ItemStack(Items.HONEY_BOTTLE);
		helper.assertTrue(Miraculous.feed(p, honey), "Nooroo has the honey");
		helper.assertTrue(Miraculous.finishMeal(p), "the meal ends (it takes 10 s)");
		helper.assertTrue(honey.isEmpty() && p.getInventory().contains(new ItemStack(Items.GLASS_BOTTLE)), "the bottle comes back");
		helper.assertFalse(Miraculous.hungry(p), "fed");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void deathWhileTransformedLeavesItHungry(GameTestHelper helper) {
		ServerPlayer p = mock(helper);
		Miraculous.grant(p, Kwami.TRIXX);
		Miraculous.transform(p);
		Miraculous.onPlayerRespawn(p);
		helper.assertTrue(!Miraculous.transformed(p) && Miraculous.hungry(p), "respawned detransformed and hungry");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void halfFallDamageWhileTransformedAndNoneAfterAMovementMove(GameTestHelper helper) {
		ServerPlayer p = mock(helper);
		Miraculous.grant(p, Kwami.TIKKI);
		Miraculous.transform(p);
		settle(p);
		// v0.16.7 (user): half fall damage transformed
		float before = p.getHealth();
		p.hurt(p.damageSources().fall(), 6f);
		helper.assertTrue(Math.abs((before - p.getHealth()) - 6f * MiraculousConfig.FALL_DAMAGE_FACTOR) < 0.01f,
				"half fall damage transformed, lost " + (before - p.getHealth()));
		// ...and none for the landing after a movement move (a Pole Vault's cooldown starting arms it)
		Miraculous.setCooldown(p, "ladybug_pole_vault", 20);
		helper.assertTrue(Miraculous.hasSafeLanding(p), "the vault arms a safe landing");
		p.invulnerableTime = 0;
		before = p.getHealth();
		p.hurt(p.damageSources().fall(), 6f);
		helper.assertTrue(p.getHealth() == before, "the landing after the vault is free, health " + p.getHealth());
		helper.assertFalse(Miraculous.hasSafeLanding(p), "and it is used up");
		p.invulnerableTime = 0;
		p.hurt(p.damageSources().fall(), 6f);
		helper.assertTrue(p.getHealth() < before, "the next fall hurts (half) again");
		before = p.getHealth();
		Miraculous.detransform(p, false);
		p.invulnerableTime = 0;
		p.hurt(p.damageSources().fall(), 6f);
		helper.assertTrue(p.getHealth() < before, "fall damage again detransformed");
		helper.succeed();
	}

	// ---------------------------------------------------------------- the box and the jewels

	@GameTest(template = EMPTY_STRUCTURE)
	public void boxGivesAJewelAndTheJewelGrants(GameTestHelper helper) {
		ServerPlayer p = mock(helper);
		ItemStack jewel = MiraculousBoxItem.open(p);
		helper.assertTrue(jewel.is(MiraculousItems.LADYBUG_EARRINGS) || jewel.is(MiraculousItems.CAT_RING) || jewel.is(MiraculousItems.FOX_NECKLACE)
				|| jewel.is(MiraculousItems.TURTLE_BRACELET) || jewel.is(MiraculousItems.HORSESHOE_GLASSES)
				|| jewel.is(MiraculousItems.SNAKE_BRACELET), "one of the box jewels (v0.17.1: the Snake Bracelet is in the box too): " + jewel);
		for (int i = 0; i < 24; i++) {
			helper.assertFalse(MiraculousBoxItem.open(p).is(MiraculousItems.BUTTERFLY_BROOCH), "the Brooch is never in the box");
		}
		ServerPlayer q = mock(helper);
		q.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(MiraculousItems.FOX_NECKLACE));
		q.getMainHandItem().use(q.level(), q, InteractionHand.MAIN_HAND);
		helper.assertTrue(Miraculous.holds(q, Kwami.TRIXX), "the Necklace grants Trixx");
		helper.assertTrue(q.getMainHandItem().isEmpty(), "the jewel is used up");
		q.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(MiraculousItems.FOX_NECKLACE));
		q.getMainHandItem().use(q.level(), q, InteractionHand.MAIN_HAND);
		helper.assertTrue(q.getMainHandItem().getCount() == 1, "a second Necklace is kept");
		ServerPlayer r = mock(helper);
		ItemStack box = new ItemStack(MiraculousItems.MIRACULOUS_BOX);
		r.setItemInHand(InteractionHand.MAIN_HAND, box);
		var res = box.use(r.level(), r, InteractionHand.MAIN_HAND);
		helper.assertTrue(res.getObject().getItem() instanceof com.projecthero.mod.miraculous.item.KwamiJewelItem, "the box became a jewel in hand");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "miraculous_unique")
	public void uniquePowersRefusesBeforeTheJewelIsUsed(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		UniquePowersData.get(server).clearAll();
		UniquePowers.setEnabled(server, true);
		try {
			ServerPlayer a = mock(helper);
			ServerPlayer b = mock(helper);
			helper.assertTrue(Miraculous.grant(a, Kwami.TIKKI), "first Ladybug");
			b.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(MiraculousItems.LADYBUG_EARRINGS));
			b.getMainHandItem().use(b.level(), b, InteractionHand.MAIN_HAND);
			helper.assertFalse(Miraculous.hasPower(b), "refused: someone else is Ladybug");
			helper.assertTrue(b.getMainHandItem().getCount() == 1, "the Earrings are kept");
			for (int i = 0; i < 12; i++) {
				ItemStack j = MiraculousBoxItem.open(b);
				helper.assertFalse(j.is(MiraculousItems.LADYBUG_EARRINGS), "the box prefers a Kwami nobody holds");
			}
		} finally {
			UniquePowers.setEnabled(server, false);
			UniquePowersData.get(server).clearAll();
		}
		helper.succeed();
	}

	// ---------------------------------------------------------------- the Kwami

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 80)
	public void kwamiFollowsAndCanNeverDie(GameTestHelper helper) {
		ServerPlayer p = mock(helper);
		Miraculous.grant(p, Kwami.PLAGG);
		Miraculous.tick(p);
		KwamiEntity k = KwamiCompanion.find(p);
		helper.assertTrue(k != null && k.kwami() == Kwami.PLAGG, "Plagg is out");
		helper.assertTrue(k.distanceTo(p) < 2.5f, "beside the owner: " + k.distanceTo(p));
		helper.assertFalse(k.hurt(p.damageSources().lava(), 1000f), "lava does nothing");
		helper.assertFalse(k.hurt(p.damageSources().explosion(null, null), 1000f), "nor explosions");
		helper.assertFalse(k.hurt(p.damageSources().inWall(), 1000f), "nor suffocation");
		helper.assertTrue(k.fireImmune() && k.isInvulnerableTo(p.damageSources().fellOutOfWorld()), "fire immune, invulnerable");
		k.kill();
		helper.assertFalse(k.isRemoved(), "/kill just sends it back to its owner");
		// the owner moves far away: it is put straight back beside them
		Vec3 far = p.position().add(20, 0, 0);
		p.moveTo(far.x, far.y, far.z, 0f, 0f);
		k.tick();
		helper.assertTrue(k.distanceTo(p) < 2.5f, "snapped back after 20 blocks: " + k.distanceTo(p));
		p.moveTo(helper.absoluteVec(new Vec3(3.5, 2.0, 3.5)));
		k.discard();
		helper.runAfterDelay(1, () -> {
			Miraculous.tick(p); // the next tick
			KwamiEntity again = KwamiCompanion.find(p);
			helper.assertTrue(again != null && again != k && !again.isRemoved(), "removed anyway: re-created at once");
			// transforming pulls it into the jewel, then it is gone; changing back brings it out
			settle(p);
			Miraculous.transform(p);
			helper.runAfterDelay(MiraculousConfig.INTO_JEWEL_TICKS + 6, () -> {
				Miraculous.tick(p);
				helper.assertTrue(KwamiCompanion.find(p) == null, "inside the Ring while transformed");
				settle(p);
				Miraculous.detransform(p, false);
				KwamiEntity back = KwamiCompanion.find(p);
				helper.assertTrue(back != null && back.mode() == KwamiEntity.MODE_POP_TIRED, "out again, tired");
				helper.succeed();
			});
		});
	}

	/** v0.16.7 (user): Shift+N held 5 s takes the Miraculous off -- the jewel comes back; never while hungry or transformed. */
	@SuppressWarnings("removal")
	@GameTest(template = EMPTY_STRUCTURE)
	public void takingTheMiraculousOffGivesTheJewelBack(GameTestHelper helper) {
		ServerPlayer p = mock(helper);
		helper.assertTrue(Miraculous.grant(p, Kwami.TIKKI), "granted");
		Miraculous.edit(p, n -> n.hungry = true);
		helper.assertFalse(Miraculous.takeOff(p), "refused while Tikki is hungry");
		fed(p);
		Miraculous.edit(p, n -> n.transformed = true);
		helper.assertFalse(Miraculous.takeOff(p), "refused while transformed");
		Miraculous.edit(p, n -> n.transformed = false);
		helper.assertTrue(Miraculous.takeOff(p), "taken off");
		helper.assertFalse(Miraculous.hasPower(p), "the power is gone");
		helper.assertTrue(p.getInventory().contains(new ItemStack(MiraculousItems.LADYBUG_EARRINGS)), "the Earrings are back in the inventory");
		helper.succeed();
	}

	/** v0.16.7 (user): secret identity -- transformed, the display name is the hero name; and every Kwami has its chatter lines. */
	@SuppressWarnings("removal")
	@GameTest(template = EMPTY_STRUCTURE)
	public void secretIdentityAndKwamiChatter(GameTestHelper helper) {
		ServerPlayer p = mock(helper);
		String own = p.getGameProfile().getName();
		Miraculous.grant(p, Kwami.PLAGG);
		helper.assertTrue(p.getDisplayName().getString().contains(own), "not transformed: your own name");
		Miraculous.edit(p, n -> { n.hungry = false; n.transformed = true; });
		var shown = p.getDisplayName();
		helper.assertTrue(shown.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents tc
				&& tc.getKey().equals("projecthero.hero_tier.cat_noir"), "transformed: known only as Cat Noir, got " + shown);
		helper.assertTrue(p.getTabListDisplayName() != null, "the tab list hides you too");
		Miraculous.edit(p, n -> n.transformed = false);
		helper.assertTrue(p.getDisplayName().getString().contains(own), "changed back: your own name again");
		var lang = net.minecraft.locale.Language.getInstance();
		for (Kwami k : Kwami.values()) {
			for (String ev : new String[] { "countdown", "danger", "hurt", "victory", "idle" }) {
				String key = "message.projecthero.kwami_chat." + k.id + "." + ev + ".1";
				helper.assertTrue(!lang.getOrDefault(key).equals(key), "missing chatter line " + key);
			}
		}
		helper.succeed();
	}

	/** v0.17.1 (user): "Tikki, spots on!" in full plays the long sequence; "Spots on!" alone stays the quick one. */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void tikkiSpotsOnPlaysTheLongSequence(GameTestHelper helper) {
		helper.assertTrue(MiraculousPhrases.match(Kwami.TIKKI, "Tikki, spots on!") == MiraculousPhrases.Spoken.TRANSFORM_LONG, "the full phrase");
		helper.assertTrue(MiraculousPhrases.match(Kwami.TIKKI, "Spots on!") == MiraculousPhrases.Spoken.TRANSFORM, "the short phrase");
		helper.assertTrue(MiraculousPhrases.match(Kwami.PLAGG, "Plagg, claws out!") == MiraculousPhrases.Spoken.TRANSFORM,
				"only Ladybug has a long sequence so far");
		ServerPlayer p = mock(helper);
		Miraculous.grant(p, Kwami.TIKKI);
		fed(p);
		helper.assertTrue(Miraculous.onChat(p, "Tikki, spots on!") && Miraculous.transformedAs(p, Kwami.TIKKI), "she transforms");
		MiraculousState s = Miraculous.state(p);
		helper.assertTrue(s.animId == com.projecthero.mod.miraculous.ladybug.LadybugSequence.ANIM, "as the long sequence");
		helper.assertFalse(Miraculous.canAct(p), "no powers while it plays");
		helper.assertTrue(com.projecthero.mod.miraculous.ladybug.LadybugSequence.running(p), "it is running");
		helper.assertFalse(p.hurt(p.damageSources().generic(), 5f), "and nothing can hurt her");
		helper.assertTrue(Miraculous.formTicks(s) == com.projecthero.mod.miraculous.ladybug.LadybugSequence.TOTAL, "8.5 s long");
		helper.succeed();
	}
}
