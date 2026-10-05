package com.projecthero.mod.gametest;

import java.util.List;

import com.projecthero.mod.carnage.CarnageEntityTypes;
import com.projecthero.mod.carnage.entity.CarnageEntity;
import com.projecthero.mod.carnage.entity.CrimsonSpawnEntity;
import com.projecthero.mod.firearm.item.FirearmItems;
import com.projecthero.mod.syndicate.SyndicateBust;
import com.projecthero.mod.syndicate.SyndicateEntityTypes;
import com.projecthero.mod.syndicate.SyndicateHideout;
import com.projecthero.mod.syndicate.SyndicateItems;
import com.projecthero.mod.syndicate.SyndicateRewards;
import com.projecthero.mod.syndicate.entity.KingpinEntity;
import com.projecthero.mod.syndicate.entity.SyndicateGunman;
import com.projecthero.mod.syndicate.entity.SyndicateSkin;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** v0.14.25: the Syndicate Bust (hideout geometry, waves, the Kingpin, the stash's haul) and Carnage. */
public class V01425GameTests implements FabricGameTest {

	@GameTest(template = EMPTY_STRUCTURE)
	public void hideoutGeometryRoundTripsInEveryRotation(GameTestHelper helper) {
		BlockPos origin = new BlockPos(100, 64, -50);
		for (Direction f : Direction.Plane.HORIZONTAL) {
			BlockPos stash = new SyndicateHideout.Site(origin, f).stash();
			helper.assertTrue(SyndicateHideout.originFromStash(stash, f).equals(origin), "origin <-> stash must round-trip facing " + f);
			helper.assertTrue(SyndicateHideout.inside(origin, f, Vec3.atCenterOf(stash)), "the stash is inside its warehouse facing " + f);
			Vec3 door = SyndicateHideout.point(origin, f, 0, 0, -SyndicateHideout.HALF - 3);
			helper.assertFalse(SyndicateHideout.inside(origin, f, door), "3 blocks out of the roller door is outside, facing " + f);
			// the back wall is forward of the roller door
			Vec3 front = SyndicateHideout.point(origin, f, 0, 0, -SyndicateHideout.HALF);
			Vec3 back = SyndicateHideout.point(origin, f, 0, 0, SyndicateHideout.HALF);
			Vec3 step = back.subtract(front).normalize();
			helper.assertTrue(step.distanceTo(new Vec3(f.getStepX(), 0, f.getStepZ())) < 1.0e-6, "door -> back wall runs along forward, facing " + f);
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void bustWavesGrowAndScaleWithTheParty(GameTestHelper helper) {
		int[] solo1 = SyndicateBust.waveMix(1, 1);
		helper.assertTrue(solo1[0] == 6 && solo1[1] + solo1[2] + solo1[3] + solo1[4] == 0, "wave 1 is six thugs and nothing else");
		int prev = 0;
		for (int n = 1; n <= SyndicateBust.WAVES; n++) {
			int total = 0;
			for (int c : SyndicateBust.waveMix(n, 1)) {
				total += c;
			}
			helper.assertTrue(total >= prev, "waves never shrink (wave " + n + ")");
			prev = total;
		}
		int[] big = SyndicateBust.waveMix(5, 8);
		helper.assertTrue(big[3] <= 4 && big[4] <= 4, "snipers and enforcers are capped at 4");
		helper.assertTrue(big[0] > SyndicateBust.waveMix(5, 1)[0], "a bigger party meets more thugs");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void stashPaysADossierPerFighterAndTheCane(GameTestHelper helper) {
		for (int party = 1; party <= 6; party++) {
			List<ItemStack> loot = SyndicateRewards.roll(RandomSource.create(party), helper.getLevel().registryAccess(), party);
			int dossiers = 0;
			boolean cane = false;
			for (ItemStack s : loot) {
				if (s.is(SyndicateItems.VILLAIN_DOSSIER)) {
					dossiers += s.getCount();
				}
				cane |= s.is(SyndicateItems.KINGPIN_CANE);
			}
			helper.assertTrue(dossiers == Math.min(4, party), "party of " + party + " gets " + Math.min(4, party) + " dossiers, got " + dossiers);
			helper.assertTrue(cane, "the stash always holds the Kingpin's Cane");
			helper.assertTrue(loot.size() <= 27, "the haul fits a chest");
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void gunmanKindsCarryTheirGunAndSkin(GameTestHelper helper) {
		for (SyndicateGunman.Kind kind : SyndicateGunman.Kind.values()) {
			SyndicateGunman g = SyndicateEntityTypes.GUNMAN.create(helper.getLevel());
			g.setKind(kind);
			helper.assertTrue(g.kind() == kind, "kind sticks");
			var gun = switch (kind) {
				case PISTOL -> FirearmItems.PUNISHER_PISTOL;
				case SHOTGUN -> FirearmItems.PUNISHER_SHOTGUN;
				case SNIPER -> FirearmItems.PUNISHER_SNIPER;
			};
			helper.assertTrue(g.getMainHandItem().is(gun), kind + " holds its Punisher gun");
			helper.assertTrue(g.skin() != SyndicateSkin.KINGPIN, kind + " doesn't wear the Kingpin's suit");
			g.discard();
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void kingpinHealthScalesAndCaps(GameTestHelper helper) {
		KingpinEntity k = SyndicateEntityTypes.KINGPIN.create(helper.getLevel());
		k.configure(1);
		helper.assertTrue(Math.abs(k.getMaxHealth() - 600f) < 0.01f, "600 for one fighter, got " + k.getMaxHealth());
		k.configure(3);
		helper.assertTrue(Math.abs(k.getMaxHealth() - 1000f) < 0.01f, "+200 per extra fighter, got " + k.getMaxHealth());
		k.configure(40);
		helper.assertTrue(k.getMaxHealth() <= (float) KingpinEntity.MAX_HEALTH, "capped at " + KingpinEntity.MAX_HEALTH);
		k.discard();
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void carnageTakesDoubleFireDamage(GameTestHelper helper) {
		helper.getLevel().getServer().setDifficulty(Difficulty.HARD, true);
		CarnageEntity c = CarnageEntityTypes.CARNAGE.create(helper.getLevel());
		c.moveTo(helper.absoluteVec(new Vec3(2, 2, 2)));
		c.configure(1);
		c.setNoAi(true);
		helper.getLevel().addFreshEntity(c);
		float before = c.getHealth();
		c.hurt(helper.getLevel().damageSources().onFire(), 5.0f);
		float lost = before - c.getHealth();
		helper.assertTrue(Math.abs(lost - 10.0f) < 0.01f, "fire does double (5 -> 10), lost " + lost);
		c.discard();
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void carnageSplitsIntoACocoonAndBrood(GameTestHelper helper) {
		helper.getLevel().getServer().setDifficulty(Difficulty.HARD, true);
		CarnageEntity c = CarnageEntityTypes.CARNAGE.create(helper.getLevel());
		c.moveTo(helper.absoluteVec(new Vec3(2, 2, 2)));
		c.finalizeSpawn(helper.getLevel(), helper.getLevel().getCurrentDifficultyAt(c.blockPosition()), MobSpawnType.COMMAND, null);
		c.configure(1);
		c.setNoAi(true);
		helper.getLevel().addFreshEntity(c);
		c.setHealth(c.getMaxHealth() * 0.76f);
		c.hurt(helper.getLevel().damageSources().generic(), 20.0f);
		helper.assertTrue(c.inCocoon(), "crossing 75% wraps him in the cocoon");
		c.invulnerableTime = 0;
		helper.assertFalse(c.hurt(helper.getLevel().damageSources().generic(), 50.0f), "the cocoon can't be hurt");
		int brood = helper.getLevel().getEntitiesOfClass(CrimsonSpawnEntity.class, new AABB(c.blockPosition()).inflate(8)).size();
		helper.assertTrue(brood >= 3, "a solo split releases at least three spawn, got " + brood);
		for (CrimsonSpawnEntity s : helper.getLevel().getEntitiesOfClass(CrimsonSpawnEntity.class, new AABB(c.blockPosition()).inflate(8))) {
			s.discard();
		}
		c.discard();
		helper.succeed();
	}
}
