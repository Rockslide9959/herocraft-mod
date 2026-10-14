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
import com.projecthero.mod.syndicate.SyndicateGunfire;
import com.projecthero.mod.syndicate.entity.SyndicateBulletEntity;
import com.projecthero.mod.syndicate.entity.SyndicateGunman;
import com.projecthero.mod.syndicate.entity.SyndicateThug;
import com.projecthero.mod.syndicate.entity.SyndicateSkin;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
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

	// ---- v0.16.2: the crooks fire real, dodgeable bullets aimed like a skeleton

	/** A still, gravity-free mob at {@code rel} (relative to the test). */
	private static <T extends Mob> T place(GameTestHelper helper, EntityType<T> type, Vec3 rel) {
		T m = type.create(helper.getLevel());
		m.moveTo(helper.absoluteVec(rel));
		m.setNoAi(true);
		m.setNoGravity(true);
		helper.getLevel().addFreshEntity(m);
		return m;
	}

	private static SyndicateGunman gunman(GameTestHelper helper, SyndicateGunman.Kind kind, Vec3 rel) {
		SyndicateGunman g = SyndicateEntityTypes.GUNMAN.create(helper.getLevel());
		g.setKind(kind);
		g.moveTo(helper.absoluteVec(rel));
		g.setNoAi(true);
		g.setNoGravity(true);
		helper.getLevel().addFreshEntity(g);
		return g;
	}

	private static Vec3 body(LivingEntity e) {
		return e.position().add(0, e.getBbHeight() * 0.5, 0);
	}

	private static List<SyndicateBulletEntity> bullets(GameTestHelper helper, SyndicateGunman owner) {
		return helper.getLevel().getEntitiesOfClass(SyndicateBulletEntity.class, owner.getBoundingBox().inflate(48), b -> b.getOwner() == owner);
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void syndicateBulletIsAProjectileThatTravels(GameTestHelper helper) {
		helper.getLevel().getServer().setDifficulty(Difficulty.HARD, true);
		SyndicateGunman g = gunman(helper, SyndicateGunman.Kind.PISTOL, new Vec3(1.5, 2, 0.5));
		var pig = place(helper, EntityType.PIG, new Vec3(1.5, 2, 7.0));
		float hp = pig.getHealth();
		int fired = g.shootAt(helper.getLevel(), body(pig));
		helper.assertTrue(fired == 1, "a pistol fires one bullet, fired " + fired);
		List<SyndicateBulletEntity> list = bullets(helper, g);
		helper.assertTrue(list.size() == 1, "one bullet entity in the world, found " + list.size());
		SyndicateBulletEntity b = list.get(0);
		helper.assertTrue(pig.getHealth() == hp, "not hit-scan: the target is untouched the moment the shot is fired");
		double speed = b.getDeltaMovement().length();
		helper.assertTrue(Math.abs(speed - SyndicateGunman.Kind.PISTOL.speed()) < 0.15, "pistol bullet flies ~2.4 blocks/tick (vanilla spread jitters it slightly), got " + speed);
		helper.assertTrue(speed > 1.6, "faster than a skeleton arrow");
		Vec3 start = b.position();
		// the pig is ~6.5 blocks off: ~3 ticks of flight, so a tick in the bullet is still on its way
		helper.runAfterDelay(1, () -> {
			helper.assertTrue(!b.isRemoved(), "still in flight a tick after the shot");
			double moved = b.position().distanceTo(start);
			helper.assertTrue(moved > 1.5 && moved < 5.5, "a tick or two at 2.4 blocks/tick of travel, moved " + moved);
			helper.assertTrue(pig.getHealth() == hp, "the target is not hit yet");
		});
		helper.runAfterDelay(20, () -> {
			helper.assertTrue(b.isRemoved(), "the bullet is gone once it has hit or flown its range");
			g.discard();
			pig.discard();
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void syndicateBulletDamagesWhatItHits(GameTestHelper helper) {
		helper.getLevel().getServer().setDifficulty(Difficulty.HARD, true);
		SyndicateGunman g = gunman(helper, SyndicateGunman.Kind.PISTOL, new Vec3(1.5, 2, 0.5));
		var pig = place(helper, EntityType.PIG, new Vec3(1.5, 2, 7.0));
		float hp = pig.getHealth();
		Vec3 muzzle = SyndicateGunfire.muzzle(g);
		SyndicateBulletEntity.fire(helper.getLevel(), g, muzzle, body(pig).subtract(muzzle), 2.4f, 0.0f, 4.0f, 23.0, 0);
		helper.assertTrue(pig.getHealth() == hp, "nothing lands the instant it is fired");
		helper.runAfterDelay(15, () -> {
			float lost = hp - pig.getHealth();
			helper.assertTrue(Math.abs(lost - 4.0f) < 0.01f, "the bullet does its 4 damage, lost " + lost);
			helper.assertTrue(bullets(helper, g).isEmpty(), "the bullet is used up on the hit");
			g.discard();
			pig.discard();
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void syndicateBulletPassesThroughTheCrew(GameTestHelper helper) {
		helper.getLevel().getServer().setDifficulty(Difficulty.HARD, true);
		SyndicateGunman g = gunman(helper, SyndicateGunman.Kind.SNIPER, new Vec3(1.5, 2, 0.5));
		SyndicateThug thug = place(helper, SyndicateEntityTypes.THUG, new Vec3(1.5, 2, 3.5));
		var pig = place(helper, EntityType.PIG, new Vec3(1.5, 2.6, 7.0));
		float thugHp = thug.getHealth();
		float pigHp = pig.getHealth();
		Vec3 muzzle = SyndicateGunfire.muzzle(g);
		Vec3 aim = body(pig);
		helper.assertTrue(thug.getBoundingBox().clip(muzzle, aim).isPresent(), "the thug stands in the line of fire");
		SyndicateBulletEntity.fire(helper.getLevel(), g, muzzle, aim.subtract(muzzle), 3.5f, 0.0f, 12.0f, 49.0, 3);
		helper.runAfterDelay(15, () -> {
			helper.assertTrue(thug.getHealth() == thugHp, "no friendly fire: the thug is untouched");
			helper.assertTrue(pig.getHealth() < pigHp, "the bullet carried on and hit the pig behind him");
			g.discard();
			thug.discard();
			pig.discard();
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void syndicateBulletSpreadFollowsDifficulty(GameTestHelper helper) {
		helper.assertTrue(SyndicateGunfire.skeletonInaccuracy(Difficulty.EASY) == 10f, "Easy = skeleton 10");
		helper.assertTrue(SyndicateGunfire.skeletonInaccuracy(Difficulty.NORMAL) == 6f, "Normal = skeleton 6");
		helper.assertTrue(SyndicateGunfire.skeletonInaccuracy(Difficulty.HARD) == 2f, "Hard = skeleton 2");
		for (Difficulty d : new Difficulty[] {Difficulty.EASY, Difficulty.NORMAL, Difficulty.HARD}) {
			float pistol = SyndicateGunman.Kind.PISTOL.inaccuracy(d);
			float shotgun = SyndicateGunman.Kind.SHOTGUN.inaccuracy(d);
			float sniper = SyndicateGunman.Kind.SNIPER.inaccuracy(d);
			helper.assertTrue(shotgun > pistol && pistol > sniper, d + ": shotgun wider than pistol wider than sniper");
		}
		helper.assertTrue(SyndicateGunman.Kind.SNIPER.speed() > SyndicateGunman.Kind.PISTOL.speed()
				&& SyndicateGunman.Kind.PISTOL.speed() > SyndicateGunman.Kind.SHOTGUN.speed(), "sniper fastest, shotgun slowest");
		// the real shots: mean miss angle of 80 pistol bullets with Easy vs Hard inaccuracy
		helper.getLevel().getServer().setDifficulty(Difficulty.HARD, true);
		SyndicateGunman g = gunman(helper, SyndicateGunman.Kind.PISTOL, new Vec3(1.5, 2, 0.5));
		Vec3 aim = g.position().add(0, 1.0, 20.0);
		Vec3 dir = aim.subtract(SyndicateGunfire.muzzle(g)).normalize();
		double easy = meanMiss(helper, g, aim, dir, SyndicateGunman.Kind.PISTOL.inaccuracy(Difficulty.EASY));
		double hard = meanMiss(helper, g, aim, dir, SyndicateGunman.Kind.PISTOL.inaccuracy(Difficulty.HARD));
		helper.assertTrue(hard > 0.0, "even on Hard a gunman is not a laser: some spread, got " + hard);
		helper.assertTrue(easy > hard * 2.5, "Easy scatters far more than Hard: easy " + easy + " rad vs hard " + hard + " rad");
		g.discard();
		helper.succeed();
	}

	private static double meanMiss(GameTestHelper helper, SyndicateGunman g, Vec3 aim, Vec3 dir, float inaccuracy) {
		int n = 80;
		for (int i = 0; i < n; i++) {
			SyndicateGunfire.fireBullets(helper.getLevel(), g, aim, 21, 4.0f, 2.4f, inaccuracy, 1, SyndicateGunfire.Report.PISTOL);
		}
		List<SyndicateBulletEntity> list = bullets(helper, g);
		helper.assertTrue(list.size() == n, "every shot is a bullet entity, found " + list.size());
		double sum = 0;
		for (SyndicateBulletEntity b : list) {
			sum += Math.acos(Math.min(1.0, b.getDeltaMovement().normalize().dot(dir)));
			b.discard();
		}
		return sum / n;
	}
}
