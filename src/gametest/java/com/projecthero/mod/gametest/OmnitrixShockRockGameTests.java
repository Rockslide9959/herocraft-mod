package com.projecthero.mod.gametest;

import com.projecthero.mod.hero.AbilityRouter;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.OmnitrixInfo;
import com.projecthero.mod.omnitrix.alien.AlienKit;
import com.projecthero.mod.omnitrix.alien.Aliens;
import com.projecthero.mod.omnitrix.alien.shockrock.ShockRockKit;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.3 Shock Rock (user: "make aliens based on their abilities and stuff they do in the show please"): a hidden prototype alien found by
 * a mistransform (the even draw itself is {@code OmnitrixCannonboltGameTests}, which takes every hidden alien); his dial glyph U+E020; no
 * move on H or N and a move on each of R/G/X/Z/V/C; and the kit's numbers.
 */
public class OmnitrixShockRockGameTests implements FabricGameTest {
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

	private static void floor(GameTestHelper helper) {
		for (int x = 0; x <= 7; x++) {
			for (int z = 0; z <= 7; z++) {
				helper.getLevel().setBlock(helper.absolutePos(new BlockPos(x, 1, z)), Blocks.STONE.defaultBlockState(), 3);
			}
		}
	}

	/** {@code p} transformed into {@code id} (found first), ticking. */
	private static ServerPlayer alien(GameTestHelper helper, String id) {
		floor(helper);
		ServerPlayer p = mock(helper, 3.5, 2, 1.5);
		helper.assertTrue(Omnitrix.grant(p), "the Omnitrix goes on");
		Aliens.discover(p, id, true);
		helper.assertTrue(Omnitrix.select(p, id), id + " is on the prototype's dial once found");
		helper.assertTrue(Omnitrix.transform(p), "transformed into " + id);
		helper.onEachTick(() -> Omnitrix.tick(p));
		return p;
	}

	private static Zombie zombie(GameTestHelper helper, double x, double y, double z, float health) {
		helper.getLevel().getServer().setDifficulty(Difficulty.HARD, true);
		Zombie m = EntityType.ZOMBIE.create(helper.getLevel());
		Vec3 at = helper.absoluteVec(new Vec3(x, y, z));
		m.moveTo(at.x, at.y, at.z, 180f, 0f);
		m.setNoAi(true);
		m.setPersistenceRequired();
		m.getAttribute(Attributes.MAX_HEALTH).setBaseValue(health);
		m.setHealth(health);
		m.getAttribute(Attributes.ARMOR).setBaseValue(0);
		m.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(Items.CARVED_PUMPKIN));
		helper.getLevel().addFreshEntity(m);
		return m;
	}

	private static void lookAt(ServerPlayer p, LivingEntity z) {
		Vec3 d = z.position().add(0, z.getBbHeight() * 0.5, 0).subtract(p.getEyePosition());
		float yaw = (float) (Math.atan2(-d.x, d.z) * 180 / Math.PI);
		float pitch = (float) (-Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * 180 / Math.PI);
		p.setYRot(yaw);
		p.setXRot(pitch);
		p.setYHeadRot(yaw);
	}

	private static void tap(ServerPlayer p, AbilitySlot slot, boolean shift) {
		p.setShiftKeyDown(shift);
		AbilityRouter.handleInput(p, slot.number(), true);
		AbilityRouter.handleInput(p, slot.number(), false);
		p.setShiftKeyDown(false);
	}

	private static void near(GameTestHelper helper, float got, float want, String what) {
		helper.assertTrue(Math.abs(got - want) < 0.05f, what + ": expected " + want + ", got " + got);
	}

	/** A fully charged swing (a mock player's attack timer does not run). */
	private static void chargeSwing(ServerPlayer p) {
		try {
			java.lang.reflect.Field f = LivingEntity.class.getDeclaredField("attackStrengthTicker");
			f.setAccessible(true);
			f.setInt(p, 100);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}

	private static ShockRockKit shockRock() {
		return (ShockRockKit) Aliens.kit(ShockRockKit.ID);
	}

	// ---------------------------------------------------------------- the dial

	@GameTest(template = EMPTY_STRUCTURE)
	public void shockRockIsAHiddenPrototypeAlienWithHisGlyph(GameTestHelper helper) {
		Object[][] all = { { Aliens.SHOCK_ROCK, '' } };
		ServerPlayer p = mock(helper, 1.5, 2, 1.5);
		Omnitrix.grant(p);
		for (Object[] row : all) {
			Aliens.Alien a = (Aliens.Alien) row[0];
			char want = (Character) row[1];
			helper.assertTrue(Aliens.kit(a.id()) != null && !a.recalibrated(), a.id() + ": a kit, on the prototype");
			helper.assertTrue(Aliens.hidden(a.id()) && !Aliens.itemOnly(a.id()) && Aliens.mistransformFinds().contains(a), a.id() + ": found by a mistransform");
			helper.assertTrue(Aliens.dial(false).contains(a) && !Aliens.dial(true).contains(a), a.id() + ": on the prototype's dial only");
			helper.assertFalse(Aliens.playable(p).contains(a) || Omnitrix.select(p, a.id()), a.id() + ": not dialable before found");
			helper.assertTrue(Aliens.unfound(p).contains(a), a.id() + ": in the draw");
			String glyph = OmnitrixInfo.icon(a).getString();
			helper.assertTrue(glyph.length() == 1 && glyph.charAt(0) == want,
					a.id() + "'s glyph is U+" + Integer.toHexString(want).toUpperCase() + ", got " + (glyph.isEmpty() ? "none" : Integer.toHexString(glyph.charAt(0))));
			AlienKit k = Aliens.kit(a.id());
			for (var m : k.moves()) {
				helper.assertTrue(!m.onSlot() || m.slot().ordinal() <= AbilitySlot.SLOT_6.ordinal(), a.id() + "." + m.id() + " on R/G/X/Z/V/C only");
			}
			for (AbilitySlot s : new AbilitySlot[] { AbilitySlot.SLOT_1, AbilitySlot.SLOT_2, AbilitySlot.SLOT_3, AbilitySlot.SLOT_4, AbilitySlot.SLOT_5,
					AbilitySlot.SLOT_6 }) {
				helper.assertTrue(k.moveOn(s, false, false) != null || k.moveOn(s, false, true) != null, a.id() + ": a move on " + s);
			}
			Aliens.discover(p, a.id(), true);
			helper.assertTrue(Aliens.playable(p).contains(a) && !Aliens.unfound(p).contains(a), a.id() + ": found, on the dial and out of the draw");
		}
		helper.succeed();
	}

	// ---------------------------------------------------------------- Shock Rock

	/** Energy starts full; the blade's slash is 13 (and cleaves the one beside); Rock Burst 18, x1.35 in Overload, which drains Energy. */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 160)
	public void shockRockBladeBurstAndOverload(GameTestHelper helper) {
		ServerPlayer p = alien(helper, ShockRockKit.ID);
		Zombie z = zombie(helper, 3.5, 2, 3.4, 200f);
		Zombie side = zombie(helper, 4.6, 2, 3.4, 200f);
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(ShockRockKit.energy(p) == ShockRockKit.MAX_ENERGY, "Energy starts full: " + ShockRockKit.energy(p));
			helper.assertTrue(shockRock().energyBlade(p), "the blade forms");
			lookAt(p, z);
			chargeSwing(p);
			helper.assertTrue(shockRock().onAttack(p, z), "the blade's slash replaces the punch");
			near(helper, 200f - z.getHealth(), ShockRockKit.BLADE_DAMAGE, "Energy Blade slash");
			near(helper, 200f - side.getHealth(), ShockRockKit.BLADE_CLEAVE, "it cleaves the one beside");
			helper.assertTrue(ShockRockKit.bladeHits(p) == ShockRockKit.BLADE_MAX_HITS - 1, "one hit used: " + ShockRockKit.bladeHits(p));
			float h = z.getHealth();
			z.invulnerableTime = 0;
			int n = shockRock().rockBurst(p);
			helper.assertTrue(n >= 2, "the burst hit both: " + n);
			near(helper, h - z.getHealth(), ShockRockKit.BURST_DAMAGE, "Rock Burst");
			helper.assertTrue(shockRock().overload(p), "Overload on");
			helper.assertTrue(ShockRockKit.overloaded(p), "overloaded");
			shockRock().cooldown(p, ShockRockKit.BURST, 0);
			Vec3 at = helper.absoluteVec(new Vec3(3.5, 2, 3.4));
			z.teleportTo(at.x, at.y, at.z);
			h = z.getHealth();
			z.invulnerableTime = 0;
			shockRock().rockBurst(p);
			near(helper, h - z.getHealth(), ShockRockKit.BURST_DAMAGE * ShockRockKit.OVERLOAD_BONUS, "Rock Burst in Overload");
		});
		helper.runAfterDelay(2 + 60, () -> {
			int e = ShockRockKit.energy(p);
			helper.assertTrue(e < ShockRockKit.MAX_ENERGY - 15, "Overload drains Energy (about 7 a second): " + e);
			helper.assertTrue(shockRock().overload(p), "V again ends it");
			helper.assertFalse(ShockRockKit.overloaded(p), "Overload off");
			helper.succeed();
		});
	}

	/** R: a bolt of 8; Shift+R: five bolts of 5 in half a second; X: the dash does 6 to whatever it passes through and spares him the fall. */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 160)
	public void shockRockBoltsAndDash(GameTestHelper helper) {
		ServerPlayer p = alien(helper, ShockRockKit.ID);
		Zombie z = zombie(helper, 3.5, 2, 5.5, 200f);
		helper.runAfterDelay(2, () -> {
			lookAt(p, z);
			tap(p, AbilitySlot.SLOT_1, false);
		});
		helper.runAfterDelay(22, () -> {
			near(helper, 200f - z.getHealth(), ShockRockKit.BLAST_DAMAGE, "Energy Blast");
			z.setHealth(200f);
			z.invulnerableTime = 0;
			lookAt(p, z);
			tap(p, AbilitySlot.SLOT_1, true);
		});
		helper.runAfterDelay(52, () -> {
			// i-frames are cleared per bolt by the projectile; at least four of the five must land on a still target
			float took = 200f - z.getHealth();
			helper.assertTrue(took >= ShockRockKit.BOLT_DAMAGE * 4 - 0.05f && took <= ShockRockKit.BOLT_DAMAGE * ShockRockKit.BOLTS + 0.05f,
					"Rapid Bolts: 4-5 bolts of " + ShockRockKit.BOLT_DAMAGE + ", took " + took);
			// a mock player is not moved by its own velocity, so the dash is checked on a zombie already in its path (a block ahead)
			Vec3 at = helper.absoluteVec(new Vec3(3.5, 2, 2.6));
			z.teleportTo(at.x, at.y, at.z);
			z.setDeltaMovement(Vec3.ZERO);
			z.setHealth(200f);
			z.invulnerableTime = 0;
			p.setYRot(0f);
			p.setYHeadRot(0f);
			p.setXRot(0f);
			tap(p, AbilitySlot.SLOT_3, false);
		});
		helper.runAfterDelay(58, () -> {
			near(helper, 200f - z.getHealth(), ShockRockKit.DASH_DAMAGE, "Energy Dash");
			helper.succeed();
		});
	}

	/** C: the shield stops a blow from in front, not one from behind; shots hurt him a third less; lightning heals him. */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 120)
	public void shockRockShieldArmourAndCore(GameTestHelper helper) {
		ServerPlayer p = alien(helper, ShockRockKit.ID);
		Zombie front = zombie(helper, 3.5, 2, 3.5, 100f);
		Zombie back = zombie(helper, 3.5, 2, 0.4, 100f);
		helper.runAfterDelay(2, () -> {
			p.setYRot(0f);
			p.setYHeadRot(0f);
			p.setXRot(0f);
			helper.assertTrue(shockRock().shield(p), "the shield forms");
			helper.assertFalse(shockRock().allowDamage(p, p.damageSources().mobAttack(front), 5f), "a blow from in front is stopped");
			helper.assertTrue(shockRock().allowDamage(p, p.damageSources().mobAttack(back), 5f), "one from behind is not");
			float shot = shockRock().incomingDamage(p, p.damageSources().arrow(EntityType.ARROW.create(helper.getLevel()), back), 9f);
			near(helper, shot, 9f * ShockRockKit.SHOT_TAKEN, "Rock Armour: shots a third less");
			p.setHealth(20f);
			helper.assertFalse(shockRock().allowDamage(p, p.damageSources().lightningBolt(), 5f), "lightning never hurts him");
			helper.assertTrue(p.getHealth() > 20f, "it heals him: " + p.getHealth());
			helper.succeed();
		});
	}
}
