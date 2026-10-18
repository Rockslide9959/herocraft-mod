package com.projecthero.mod.gametest;

import com.projecthero.mod.miraculous.Kwami;
import com.projecthero.mod.miraculous.Miraculous;
import com.projecthero.mod.miraculous.hawkmoth.HawkMothAkumas;
import com.projecthero.mod.miraculous.hawkmoth.HawkMothConfig;
import com.projecthero.mod.miraculous.hawkmoth.HawkMothKit;
import com.projecthero.mod.miraculous.hawkmoth.entity.AkumaEntity;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.6 Hawk Moth (Nooroo): one test per move's server effect -- the cane numbers, an akuma's loyalty / time / cap and
 * Recall, the shield against an arrow, Swarm Blind, a Champion mob's size / health / damage and its restoration, the
 * player offer (nothing without Accept, restored after), Rise starting the countdown and detransforming clearing it
 * all. Mock players are not ticked by the server, so tests drive {@link Miraculous#tick} themselves ({@link #pump}); the
 * akumas' own tick is a server tick event and runs by itself.
 */
public class HawkMothGameTests implements FabricGameTest {
	private static final float EPS = 0.05f;

	// ---------------------------------------------------------------- helpers

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

	/** Hawk Moth facing +Z, transformed and done transforming. */
	private static ServerPlayer hawkMoth(GameTestHelper helper, double x, double y, double z) {
		ServerPlayer p = mock(helper, x, y, z);
		helper.assertTrue(Miraculous.grant(p, Kwami.NOOROO), "Nooroo is granted");
		Miraculous.transform(p);
		long back = helper.getLevel().getGameTime() - 200L;
		Miraculous.edit(p, n -> {
			n.formChangeAt = back;
			n.animId = 0;
		});
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
		helper.getLevel().getServer().setDifficulty(Difficulty.HARD, true);
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
		m.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(net.minecraft.world.item.Items.CARVED_PUMPKIN));
		helper.getLevel().addFreshEntity(m);
		return m;
	}

	private static boolean near(double a, double b) {
		return Math.abs(a - b) < EPS;
	}

	// ---------------------------------------------------------------- R

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 80)
	public void caneComboDealsNineEachThreeTimes(GameTestHelper helper) {
		floor(helper);
		ServerPlayer hm = hawkMoth(helper, 3.5, 2.0, 1.5);
		Zombie z = mob(helper, EntityType.ZOMBIE, 3.5, 2.0, 3.5, 100, false);
		HawkMothKit.caneStrike(hm);
		helper.assertTrue(near(z.getHealth(), 91f), "blow 1: 9 damage, health " + z.getHealth());
		helper.runAfterDelay(HawkMothConfig.STRIKE_GAP + 1, () -> {
			HawkMothKit.caneStrike(hm);
			helper.assertTrue(near(z.getHealth(), 82f), "blow 2: 9 damage, health " + z.getHealth());
		});
		helper.runAfterDelay(2 * (HawkMothConfig.STRIKE_GAP + 1), () -> {
			HawkMothKit.caneStrike(hm);
			helper.assertTrue(near(z.getHealth(), 73f), "blow 3: 9 damage, health " + z.getHealth());
			helper.assertTrue(Miraculous.cooldownRemaining(hm, HawkMothKit.CANE_STRIKE) > 0, "a rest after the third blow");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 80)
	public void caneSwordLungesForFourteen(GameTestHelper helper) {
		floor(helper);
		ServerPlayer hm = hawkMoth(helper, 3.5, 2.0, 1.5);
		Zombie z = mob(helper, EntityType.ZOMBIE, 3.5, 2.0, 3.5, 100, false);
		pump(helper, hm);
		HawkMothKit.caneSword(hm);
		helper.assertTrue(Miraculous.state(hm).val(HawkMothKit.V_SWORD_AT) > 0L, "the blade is being drawn");
		helper.assertTrue(near(z.getHealth(), 100f), "no hit while the blade is drawn");
		helper.runAfterDelay(HawkMothConfig.SWORD_DRAW_TICKS + HawkMothConfig.SWORD_LUNGE_TICKS + 2, () -> {
			helper.assertTrue(near(z.getHealth(), 86f), "the lunge cuts once for 14, health " + z.getHealth());
			helper.assertTrue(Miraculous.cooldownRemaining(hm, HawkMothKit.CANE_SWORD) > 0, "Cane Sword is cooling down");
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- G

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void akumaFightsForHawkMothNeverHimAndEnds(GameTestHelper helper) {
		floor(helper);
		ServerPlayer hm = hawkMoth(helper, 1.5, 2.0, 1.5);
		Zombie ally = mob(helper, EntityType.ZOMBIE, 1.5, 2.0, 3.5, 20, true);
		Zombie enemy = mob(helper, EntityType.ZOMBIE, 2.5, 2.0, 5.5, 200, false);
		enemy.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);
		enemy.setHealth(100);
		pump(helper, hm);
		HawkMothKit.akuma(hm);
		helper.assertTrue(HawkMothAkumas.akumasOut(hm.getUUID()) == 1, "an akuma is on its way");
		long[] bondedAt = { -1L, 0L };
		helper.onEachTick(() -> {
			HawkMothAkumas.Bond b = HawkMothAkumas.bond(ally);
			if (b != null && bondedAt[0] < 0) {
				bondedAt[0] = helper.getLevel().getGameTime();
				helper.assertTrue(near(ally.getMaxHealth(), 30f), "+50% health: max " + ally.getMaxHealth());
				helper.assertTrue(near(ally.getAttributeValue(Attributes.ATTACK_DAMAGE), 4.5), "+50% damage: "
						+ ally.getAttributeValue(Attributes.ATTACK_DAMAGE));
				helper.assertTrue(b.until - helper.getLevel().getGameTime() <= HawkMothConfig.AKUMA_TICKS, "an akuma lasts 30 s");
				// what hurts its own side is vetoed
				float before = hm.getHealth();
				hm.hurt(helper.getLevel().damageSources().mobAttack(ally), 6f);
				helper.assertTrue(near(hm.getHealth(), before), "an akuma never hurts Hawk Moth");
			}
			// (before the akuma lands it is just a zombie and may well go for him; once akumatized, never)
			if (b != null && helper.getLevel().getGameTime() > bondedAt[0] + 1 && ally.getTarget() == hm) {
				helper.fail("the akumatized zombie targeted Hawk Moth");
			}
			// once it has fought the hostile: fast-forward to the end of its 30 s
			HawkMothAkumas.Bond b2 = HawkMothAkumas.bond(ally);
			if (b2 != null && bondedAt[1] == 0L && ally.getTarget() == enemy && enemy.getHealth() < 100f) {
				bondedAt[1] = 1L;
				b2.until = helper.getLevel().getGameTime();
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(bondedAt[0] > 0, "the akuma reached the zombie");
			helper.assertTrue(bondedAt[1] == 1L, "it hunted and hurt the hostile (target " + ally.getTarget() + ", health " + enemy.getHealth() + ")");
			helper.assertTrue(HawkMothAkumas.bond(ally) == null, "the bond ends at its time");
			helper.assertTrue(near(ally.getMaxHealth(), 20f), "health back to normal: " + ally.getMaxHealth());
			helper.assertTrue(near(ally.getAttributeValue(Attributes.ATTACK_DAMAGE), 3.0), "damage back to normal");
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 80)
	public void akumaCapIsThreeAndRecallFreesAll(GameTestHelper helper) {
		floor(helper);
		ServerPlayer hm = hawkMoth(helper, 3.5, 2.0, 0.5);
		Zombie a = mob(helper, EntityType.ZOMBIE, 0.5, 2.0, 6.5, 20, false);
		Zombie b = mob(helper, EntityType.ZOMBIE, 1.5, 2.0, 6.5, 20, false);
		Zombie c = mob(helper, EntityType.ZOMBIE, 5.5, 2.0, 6.5, 20, false);
		Zombie d = mob(helper, EntityType.ZOMBIE, 3.5, 2.0, 2.5, 20, false);
		HawkMothAkumas.akumatize(hm, a, false);
		HawkMothAkumas.akumatize(hm, b, false);
		HawkMothAkumas.akumatize(hm, c, false);
		helper.assertTrue(HawkMothAkumas.akumasOut(hm.getUUID()) == 3, "three akumas out");
		HawkMothKit.akuma(hm);
		helper.assertTrue(HawkMothAkumas.akumasOut(hm.getUUID()) == 3, "a fourth is refused");
		helper.assertTrue(helper.getLevel().getEntitiesOfClass(AkumaEntity.class, new AABB(hm.blockPosition()).inflate(10)).isEmpty(),
				"no fourth akuma flies");
		Miraculous.setCooldown(hm, HawkMothKit.AKUMA, 200);
		HawkMothKit.recall(hm);
		helper.assertTrue(HawkMothAkumas.bond(a) == null && HawkMothAkumas.bond(b) == null && HawkMothAkumas.bond(c) == null, "Recall frees every mob");
		helper.assertTrue(near(a.getMaxHealth(), 20f) && near(c.getMaxHealth(), 20f), "the freed mobs are normal again");
		helper.assertTrue(Miraculous.cooldownRemaining(hm, HawkMothKit.AKUMA) == 0, "and the Akuma cooldown is back");
		helper.assertTrue(HawkMothAkumas.bond(d) == null, "the fourth zombie never carried one");
		helper.succeed();
	}

	// ---------------------------------------------------------------- Z / V

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void butterflyShieldStopsAnArrow(GameTestHelper helper) {
		floor(helper);
		ServerPlayer hm = hawkMoth(helper, 3.5, 2.0, 0.5);
		pump(helper, hm);
		HawkMothKit.raiseShield(hm);
		helper.assertTrue(HawkMothKit.shieldUp(hm), "the shield is up");
		Arrow arrow = new Arrow(EntityType.ARROW, helper.getLevel());
		Vec3 at = helper.absoluteVec(new Vec3(3.5, 3.4, 7.0));
		arrow.setPos(at.x, at.y, at.z);
		arrow.setDeltaMovement(0, 0, -1.6);
		arrow.setBaseDamage(4.0);
		helper.getLevel().addFreshEntity(arrow);
		float full = hm.getHealth();
		helper.runAfterDelay(20, () -> {
			helper.assertTrue(near(hm.getHealth(), full), "the arrow never hurt him: " + hm.getHealth() + " / " + full);
			helper.assertTrue(arrow.isRemoved() || arrow.getDeltaMovement().lengthSqr() < 0.01, "the arrow was stopped");
			HawkMothKit.lowerShield(hm);
			helper.assertTrue(!HawkMothKit.shieldUp(hm) && Miraculous.cooldownRemaining(hm, HawkMothKit.SHIELD) > 0, "released: down and cooling");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void swarmBlindBlindsAndSlowsHostiles(GameTestHelper helper) {
		floor(helper);
		ServerPlayer hm = hawkMoth(helper, 3.5, 2.0, 1.5);
		Zombie z = mob(helper, EntityType.ZOMBIE, 3.5, 2.0, 5.5, 20, false);
		HawkMothKit.swarmBlind(hm);
		helper.assertTrue(z.hasEffect(MobEffects.BLINDNESS), "Blindness");
		helper.assertTrue(z.hasEffect(MobEffects.MOVEMENT_SLOWDOWN) && z.getEffect(MobEffects.MOVEMENT_SLOWDOWN).getAmplifier() == 1, "Slowness II");
		helper.assertTrue(Math.abs(z.getEffect(MobEffects.BLINDNESS).getDuration() - HawkMothConfig.BLIND_TICKS) <= 2, "for 6 s");
		helper.succeed();
	}

	// ---------------------------------------------------------------- Shift+Z

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 80)
	public void championMobGrowsAndIsRestored(GameTestHelper helper) {
		floor(helper);
		ServerPlayer hm = hawkMoth(helper, 1.5, 2.0, 1.5);
		Zombie z = mob(helper, EntityType.ZOMBIE, 4.5, 2.0, 5.5, 20, false);
		HawkMothAkumas.Bond b = HawkMothAkumas.akumatize(hm, z, true);
		helper.assertTrue(b.champion && b.power != null, "a Champion with a themed power");
		helper.assertTrue(near(z.getAttributeValue(Attributes.SCALE), 1.5), "1.5x size: " + z.getAttributeValue(Attributes.SCALE));
		helper.assertTrue(near(z.getMaxHealth(), 60f) && near(z.getHealth(), 60f), "3x health: " + z.getMaxHealth());
		helper.assertTrue(near(z.getAttributeValue(Attributes.ATTACK_DAMAGE), 6.0), "2x damage: " + z.getAttributeValue(Attributes.ATTACK_DAMAGE));
		helper.assertTrue(b.until - helper.getLevel().getGameTime() == HawkMothConfig.CHAMPION_TICKS, "60 s");
		helper.assertTrue(HawkMothAkumas.championOut(hm.getUUID()), "one Champion out");
		b.until = helper.getLevel().getGameTime() + 2;
		helper.runAfterDelay(6, () -> {
			helper.assertTrue(HawkMothAkumas.bond(z) == null, "the Champion's time is up");
			helper.assertTrue(near(z.getAttributeValue(Attributes.SCALE), 1.0), "normal size");
			helper.assertTrue(near(z.getMaxHealth(), 20f) && z.getHealth() <= 20f, "health capped back: " + z.getHealth());
			helper.assertTrue(near(z.getAttributeValue(Attributes.ATTACK_DAMAGE), 3.0), "normal damage");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 80)
	public void playerOfferNeedsAcceptAndIsRestored(GameTestHelper helper) {
		floor(helper);
		ServerPlayer hm = hawkMoth(helper, 1.5, 2.0, 1.5);
		ServerPlayer p = mock(helper, 4.5, 2.0, 4.5);
		helper.assertTrue(HawkMothAkumas.makeOffer(hm, p), "the offer is made");
		helper.assertTrue(near(p.getMaxHealth(), 20f) && near(p.getAttributeValue(Attributes.SCALE), 1.0) && HawkMothAkumas.champion(p) == null,
				"nothing changes before Accept");
		helper.assertFalse(HawkMothAkumas.makeOffer(hm, p), "no second offer while one is open");
		helper.assertTrue(HawkMothAkumas.accept(p), "Accept");
		helper.assertTrue(HawkMothAkumas.champion(p) != null, "a Champion");
		helper.assertTrue(near(p.getMaxHealth(), 60f) && near(p.getHealth(), 60f), "3x max health: " + p.getMaxHealth());
		helper.assertTrue(near(p.getAttributeValue(Attributes.SCALE), 1.5), "1.5x size");
		helper.assertTrue(near(p.getAttributeValue(Attributes.ATTACK_DAMAGE), 2.0), "+100% damage: " + p.getAttributeValue(Attributes.ATTACK_DAMAGE));
		HawkMothAkumas.champion(p).until = helper.getLevel().getGameTime() + 2;
		helper.runAfterDelay(6, () -> {
			helper.assertTrue(HawkMothAkumas.champion(p) == null, "the time is up");
			helper.assertTrue(near(p.getMaxHealth(), 20f) && p.getHealth() <= 20f, "health capped back: " + p.getHealth());
			helper.assertTrue(near(p.getAttributeValue(Attributes.SCALE), 1.0) && near(p.getAttributeValue(Attributes.ATTACK_DAMAGE), 1.0),
					"size and damage restored");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void refusedOrIgnoredOfferChangesNothing(GameTestHelper helper) {
		floor(helper);
		ServerPlayer hm = hawkMoth(helper, 1.5, 2.0, 1.5);
		ServerPlayer p = mock(helper, 4.5, 2.0, 4.5);
		ServerPlayer q = mock(helper, 5.5, 2.0, 2.5);
		HawkMothAkumas.makeOffer(hm, p);
		HawkMothAkumas.refuse(p, false);
		helper.assertTrue(HawkMothAkumas.offer(p) == null && HawkMothAkumas.champion(p) == null, "refused: no offer, no Champion");
		helper.assertFalse(HawkMothAkumas.accept(p), "Accept after Refuse does nothing");
		helper.assertTrue(near(p.getMaxHealth(), 20f), "unchanged");
		HawkMothAkumas.makeOffer(hm, q);
		HawkMothAkumas.setOfferUntil(q, helper.getLevel().getGameTime() + 1);
		helper.runAfterDelay(5, () -> {
			helper.assertTrue(HawkMothAkumas.offer(q) == null, "an unanswered offer runs out");
			helper.assertFalse(HawkMothAkumas.accept(q), "and counts as refused");
			helper.assertTrue(near(q.getMaxHealth(), 20f) && HawkMothAkumas.champion(q) == null, "unchanged");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 140)
	public void riseAkumaStartsTheCountdownAndMakesAChampion(GameTestHelper helper) {
		floor(helper);
		ServerPlayer hm = hawkMoth(helper, 3.5, 2.0, 0.5);
		Zombie z = mob(helper, EntityType.ZOMBIE, 3.5, 2.0, 5.5, 20, false);
		pump(helper, hm);
		HawkMothKit.riseAkuma(hm);
		helper.assertTrue(Miraculous.countdownLeft(hm) == 0, "no countdown while his hands are cupped");
		helper.runAfterDelay(HawkMothConfig.RISE_CUP_TICKS + 2, () -> helper.assertTrue(Miraculous.countdownLeft(hm) > 0,
				"the countdown starts when the akuma is released"));
		helper.succeedWhen(() -> {
			HawkMothAkumas.Bond b = HawkMothAkumas.bond(z);
			helper.assertTrue(b != null && b.champion, "the zombie rises as a Champion");
			helper.assertTrue(Miraculous.countdownLeft(hm) > 0, "countdown running");
		});
	}

	// ---------------------------------------------------------------- cleanup

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void detransformClearsEverything(GameTestHelper helper) {
		floor(helper);
		ServerPlayer hm = hawkMoth(helper, 1.5, 2.0, 1.5);
		ServerPlayer p = mock(helper, 5.5, 2.0, 5.5);
		ServerPlayer q = mock(helper, 6.5, 2.0, 2.5);
		Zombie a = mob(helper, EntityType.ZOMBIE, 4.5, 2.0, 1.5, 20, false);
		Zombie c = mob(helper, EntityType.ZOMBIE, 1.5, 2.0, 5.5, 20, false);
		HawkMothAkumas.akumatize(hm, a, false);
		HawkMothAkumas.akumatize(hm, c, true);
		HawkMothAkumas.makeOffer(hm, p);
		HawkMothKit.swarmFlight(hm);
		HawkMothKit.raiseShield(hm);
		helper.assertTrue(hm.getAbilities().mayfly && HawkMothKit.flying(hm), "flying");
		Miraculous.detransform(hm, false);
		helper.assertTrue(HawkMothAkumas.bond(a) == null && HawkMothAkumas.bond(c) == null, "every akuma and the Champion freed");
		helper.assertTrue(near(a.getMaxHealth(), 20f) && near(c.getMaxHealth(), 20f) && near(c.getAttributeValue(Attributes.SCALE), 1.0),
				"mobs back to normal");
		helper.assertTrue(HawkMothAkumas.offer(p) == null, "the open offer is withdrawn");
		helper.assertFalse(hm.getAbilities().mayfly, "the swarm stops carrying him");
		helper.assertFalse(HawkMothKit.shieldUp(hm) || HawkMothKit.flying(hm), "no kit value left");
		// a Champion player is freed too (Nooroo is hungry after every detransformation: fed first)
		long settled = helper.getLevel().getGameTime() - 200L;
		Miraculous.edit(hm, n -> {
			n.hungry = false;
			n.formChangeAt = settled; // (and past the gap between two changes)
		});
		Miraculous.transform(hm);
		helper.assertTrue(Miraculous.transformed(hm), "transformed again");
		long back = helper.getLevel().getGameTime() - 200L;
		Miraculous.edit(hm, n -> n.formChangeAt = back);
		HawkMothAkumas.makeOffer(hm, q);
		HawkMothAkumas.accept(q);
		helper.assertTrue(near(q.getMaxHealth(), 60f), "q is a Champion");
		Miraculous.detransform(hm, false);
		helper.assertTrue(HawkMothAkumas.champion(q) == null && near(q.getMaxHealth(), 20f) && near(q.getAttributeValue(Attributes.SCALE), 1.0),
				"the Champion player is restored when Hawk Moth detransforms");
		helper.succeed();
	}
}
