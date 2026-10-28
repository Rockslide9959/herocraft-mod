package com.projecthero.mod.gametest;

import java.util.EnumSet;
import java.util.Set;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.HeroTiers;
import com.projecthero.mod.hero.PowerGrants;
import com.projecthero.mod.hero.guide.HeroPackGuide;
import com.projecthero.mod.miraculous.Kwami;
import com.projecthero.mod.miraculous.KwamiCompanion;
import com.projecthero.mod.miraculous.Miraculous;
import com.projecthero.mod.miraculous.MiraculousPhrases;
import com.projecthero.mod.miraculous.data.MiraculousState;
import com.projecthero.mod.miraculous.entity.KwamiEntity;
import com.projecthero.mod.miraculous.item.KwamiJewelItem;
import com.projecthero.mod.miraculous.item.MiraculousBoxItem;
import com.projecthero.mod.miraculous.item.MiraculousItems;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.6: the core of the two newer Kwamis -- Wayzz (Carapace, the Turtle Bracelet, dried kelp) and Kaalki (Pegasus,
 * the Horseshoe Glasses, sugar): granted and revoked by their hero keys, their phrases, their foods, the Miraculous Box
 * holding both jewels (and the jewels granting), and their two guide chapters.
 */
public class MiraculousTurtleHorseGameTests implements FabricGameTest {
	private static ServerPlayer mock(GameTestHelper helper) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(3.5, 2.0, 3.5));
		p.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);
		return p;
	}

	/** Pretend the last change was long ago (the debounce and the transformation sequence are over). */
	private static void settle(ServerPlayer p) {
		MiraculousState n = Miraculous.state(p).copy();
		n.formChangeAt = p.level().getGameTime() - 200L;
		p.setAttached(ModAttachments.MIRACULOUS_STATE, n);
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void carapaceAndPegasusGrantAndRevoke(GameTestHelper helper) {
		for (Kwami k : new Kwami[] { Kwami.WAYZZ, Kwami.KAALKI }) {
			ServerPlayer p = mock(helper);
			helper.assertTrue(HeroTiers.HERO_KEYS.contains(k.heroKey) && PowerGrants.HERO_TIER_KEYS.contains(k.heroKey), k.heroKey + " is a Hero-Tier key");
			helper.assertTrue(PowerGrants.grantHero(p, k.heroKey).ok(), "the command grants " + k.heroKey);
			helper.assertTrue(Miraculous.holds(p, k) && HeroTiers.holdsHero(p, k.heroKey) && HeroTiers.heroCount(p) == 1, k.id + " held, one hero");
			KwamiEntity e = KwamiCompanion.find(p);
			helper.assertTrue(e != null && e.kwami() == k && e.mode() == KwamiEntity.MODE_POP_OUT, k.id + " pops out and waves");
			helper.assertTrue(KwamiCompanion.introPending(p) == KwamiCompanion.INTRO_LINES, "the intro lines are on their way");
			helper.assertFalse(PowerGrants.grantHero(p, k.heroKey).ok(), "a second grant does nothing");
			helper.assertTrue(PowerGrants.revokeHeroTier(p, k.heroKey), "revoke by key");
			helper.assertFalse(Miraculous.hasPower(p) || HeroTiers.holdsHero(p, k.heroKey), k.id + " revoked");
			helper.assertTrue(KwamiCompanion.find(p) == null, "the Kwami is gone with the power");
		}
		ServerPlayer p = mock(helper);
		Miraculous.grant(p, Kwami.WAYZZ);
		helper.assertTrue(PowerGrants.grantHero(p, "pegasus").ok() && Miraculous.holds(p, Kwami.KAALKI) && HeroTiers.heroCount(p) == 1,
				"Kaalki replaces Wayzz (one Primary)");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void shellOnShellOffFullGallopDismount(GameTestHelper helper) {
		ServerPlayer p = mock(helper);
		Miraculous.grant(p, Kwami.WAYZZ);
		helper.assertFalse(Miraculous.onChat(p, "Kaalki, full gallop!"), "the other Kwami's phrase means nothing");
		helper.assertTrue(Miraculous.onChat(p, "  wayzz,  SHELL on!! "), "Wayzz, shell on! (leniently)");
		helper.assertTrue(Miraculous.transformedAs(p, Kwami.WAYZZ), "Carapace");
		settle(p);
		helper.assertTrue(Miraculous.onChat(p, "Shell off!"), "Shell off!");
		helper.assertTrue(!Miraculous.transformed(p) && Miraculous.hungry(p), "changed back, Wayzz hungry");

		ServerPlayer q = mock(helper);
		Miraculous.grant(q, Kwami.KAALKI);
		helper.assertTrue(Miraculous.onChat(q, "Kaalki, full gallop!"), "Kaalki, full gallop!");
		helper.assertTrue(Miraculous.transformedAs(q, Kwami.KAALKI), "Pegasus");
		settle(q);
		helper.assertTrue(Miraculous.onChat(q, "kaalki dismount"), "the detransform phrase with the name");
		helper.assertTrue(!Miraculous.transformed(q) && Miraculous.hungry(q), "changed back, Kaalki hungry");

		helper.assertTrue(MiraculousPhrases.match(Kwami.WAYZZ, "WAYZZ SHELL ON") == MiraculousPhrases.Spoken.TRANSFORM_LONG /* v0.17.1: the full phrase is the long one */
				&& MiraculousPhrases.match(Kwami.WAYZZ, "wayzz, shell off") == MiraculousPhrases.Spoken.DETRANSFORM
				&& MiraculousPhrases.match(Kwami.WAYZZ, "shell on") == MiraculousPhrases.Spoken.TRANSFORM /* v0.16.7: the name is optional now */
				&& MiraculousPhrases.match(Kwami.KAALKI, "Kaalki full gallop") == MiraculousPhrases.Spoken.TRANSFORM_LONG
				&& MiraculousPhrases.match(Kwami.KAALKI, "full gallop") == MiraculousPhrases.Spoken.TRANSFORM
				&& MiraculousPhrases.match(Kwami.KAALKI, "DISMOUNT!") == MiraculousPhrases.Spoken.DETRANSFORM, "the phrase table");
		helper.assertTrue("Carapace".equals(MiraculousPhrases.heroName(Kwami.WAYZZ)) && "Pegasus".equals(MiraculousPhrases.heroName(Kwami.KAALKI)), "hero names");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void driedKelpForWayzzSugarForKaalki(GameTestHelper helper) {
		helper.assertTrue(Miraculous.foodOf(Kwami.WAYZZ) == Items.DRIED_KELP && Miraculous.foodOf(Kwami.KAALKI) == Items.SUGAR, "the favourite foods");
		for (Kwami k : new Kwami[] { Kwami.WAYZZ, Kwami.KAALKI }) {
			ServerPlayer p = mock(helper);
			Miraculous.grant(p, k);
			Miraculous.edit(p, n -> n.hungry = true);
			ItemStack food = new ItemStack(Miraculous.foodOf(k), 3);
			helper.assertTrue(Miraculous.wantsFood(p, food), k.id + " wants its food (the use-to-feed path)");
			helper.assertFalse(Miraculous.wantsFood(p, new ItemStack(Items.COOKIE)), k.id + " does not want cookies that way");
			helper.assertTrue(Miraculous.feed(p, food), k.id + " eats");
			helper.assertTrue(food.getCount() == 2, "one piece is eaten, left " + food.getCount());
			helper.assertTrue(Miraculous.eatingSecondsLeft(p) > 0, "the meal takes a while");
			helper.assertTrue(Miraculous.finishMeal(p), "the meal ends");
			helper.assertFalse(Miraculous.hungry(p), k.id + " fed");
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void theBoxCanGiveTheBraceletAndTheGlasses(GameTestHelper helper) {
		helper.assertTrue(MiraculousBoxItem.BOX_KWAMIS.contains(Kwami.WAYZZ) && MiraculousBoxItem.BOX_KWAMIS.contains(Kwami.KAALKI)
				&& !MiraculousBoxItem.BOX_KWAMIS.contains(Kwami.NOOROO), "the box's pool");
		ServerPlayer p = mock(helper);
		Set<Kwami> seen = EnumSet.noneOf(Kwami.class);
		for (int i = 0; i < 200 && seen.size() < 5; i++) {
			ItemStack j = MiraculousBoxItem.open(p);
			helper.assertTrue(j.getItem() instanceof KwamiJewelItem && !j.is(MiraculousItems.BUTTERFLY_BROOCH), "a box jewel: " + j);
			if (j.is(MiraculousItems.TURTLE_BRACELET)) {
				seen.add(Kwami.WAYZZ);
			} else if (j.is(MiraculousItems.HORSESHOE_GLASSES)) {
				seen.add(Kwami.KAALKI);
			} else {
				seen.add(j.is(MiraculousItems.LADYBUG_EARRINGS) ? Kwami.TIKKI : j.is(MiraculousItems.CAT_RING) ? Kwami.PLAGG : Kwami.TRIXX);
			}
		}
		helper.assertTrue(seen.contains(Kwami.WAYZZ) && seen.contains(Kwami.KAALKI), "the box gave both new jewels: " + seen);
		helper.assertTrue(MiraculousItems.jewelOf(Kwami.WAYZZ) == MiraculousItems.TURTLE_BRACELET
				&& MiraculousItems.jewelOf(Kwami.KAALKI) == MiraculousItems.HORSESHOE_GLASSES, "jewelOf");
		ServerPlayer q = mock(helper);
		q.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(MiraculousItems.TURTLE_BRACELET));
		q.getMainHandItem().use(q.level(), q, InteractionHand.MAIN_HAND);
		helper.assertTrue(Miraculous.holds(q, Kwami.WAYZZ) && q.getMainHandItem().isEmpty(), "the Bracelet grants Wayzz and is used up");
		ServerPlayer r = mock(helper);
		r.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(MiraculousItems.HORSESHOE_GLASSES));
		r.getMainHandItem().use(r.level(), r, InteractionHand.MAIN_HAND);
		helper.assertTrue(Miraculous.holds(r, Kwami.KAALKI) && r.getMainHandItem().isEmpty(), "the Glasses grant Kaalki and are used up");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void carapaceAndPegasusHaveTheirOwnGuideChapters(GameTestHelper helper) {
		var chapters = HeroPackGuide.chapters();
		helper.assertTrue(chapters.size() == 62 + com.projecthero.mod.hero.Powers.enabled().size(), "62 framing chapters (v0.16.7: + the Quirks, + Viperion; v0.17.1: + Falcon, + Silver Surfer, + the Speed Force Speedster, + Multimouse, + the Omnitrix) + the powers, got " + chapters.size());
		String[] want = { "projecthero.guide.carapace", "projecthero.guide.pegasus" };
		Kwami[] ks = { Kwami.WAYZZ, Kwami.KAALKI };
		for (int i = 0; i < 2; i++) {
			var ch = HeroPackGuide.miraculousChapter(ks[i]);
			helper.assertTrue(ch.title().getContents() instanceof TranslatableContents t && t.getKey().equals(want[i]),
					ks[i].id + " opens its own chapter, got " + ch.title().getContents());
			helper.assertTrue(ch.lines().size() > 20, want[i] + " has its sections and the controls table");
			helper.assertTrue(chapters.indexOf(ch) == 46 + i, want[i] + " sits after Hawk Moth, at " + chapters.indexOf(ch));
		}
		helper.succeed();
	}
}
