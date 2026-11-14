package com.projecthero.mod.gametest;

import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.OmnitrixInfo;
import com.projecthero.mod.omnitrix.alien.AlienKit;
import com.projecthero.mod.omnitrix.alien.Aliens;
import com.projecthero.mod.omnitrix.alien.benwolf.BenwolfKit;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffects;
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
 * v0.17.3 Benwolf (user: "make aliens based on their abilities and stuff they do in the show please"): a hidden prototype alien found by
 * a mistransform (the even draw itself is {@code OmnitrixCannonboltGameTests}, which takes every hidden alien); his dial glyph U+E01F; no
 * move on H or N and a move on every kit key; and the kit's main numbers.
 */
public class OmnitrixBenwolfGameTests implements FabricGameTest {
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

	/** {@code p} transformed into Benwolf (found first), ticking. */
	private static ServerPlayer benwolfPlayer(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = mock(helper, 3.5, 2, 1.5);
		helper.assertTrue(Omnitrix.grant(p), "the Omnitrix goes on");
		Aliens.discover(p, BenwolfKit.ID, true);
		helper.assertTrue(Omnitrix.select(p, BenwolfKit.ID), "Benwolf is on the prototype's dial once found");
		helper.assertTrue(Omnitrix.transform(p), "transformed into Benwolf");
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

	private static void near(GameTestHelper helper, float got, float want, String what) {
		helper.assertTrue(Math.abs(got - want) < 0.05f, what + ": expected " + want + ", got " + got);
	}

	private static BenwolfKit benwolf() {
		return (BenwolfKit) Aliens.kit(BenwolfKit.ID);
	}

	private static Vec3 abs(GameTestHelper helper, double x, double y, double z) {
		return helper.absoluteVec(new Vec3(x, y, z));
	}

	// ---------------------------------------------------------------- the dial

	@GameTest(template = EMPTY_STRUCTURE)
	public void benwolfIsAHiddenPrototypeAlienWithHisGlyph(GameTestHelper helper) {
		Aliens.Alien a = Aliens.BENWOLF;
		char want = '';
		ServerPlayer p = mock(helper, 1.5, 2, 1.5);
		Omnitrix.grant(p);
		helper.assertTrue(Aliens.kit(a.id()) != null && !a.recalibrated(), "a kit, on the prototype");
		helper.assertTrue(Aliens.hidden(a.id()) && !Aliens.itemOnly(a.id()) && Aliens.mistransformFinds().contains(a), "found by a mistransform");
		helper.assertTrue(Aliens.dial(false).contains(a) && !Aliens.dial(true).contains(a), "on the prototype's dial only");
		helper.assertFalse(Aliens.playable(p).contains(a) || Omnitrix.select(p, a.id()), "not dialable before found");
		helper.assertTrue(Aliens.unfound(p).contains(a), "in the draw");
		String glyph = OmnitrixInfo.icon(a).getString();
		helper.assertTrue(glyph.length() == 1 && glyph.charAt(0) == want,
				"Benwolf's glyph is U+E01F, got " + (glyph.isEmpty() ? "none" : Integer.toHexString(glyph.charAt(0))));
		AlienKit k = Aliens.kit(a.id());
		for (var m : k.moves()) {
			helper.assertTrue(!m.onSlot() || m.slot().ordinal() <= AbilitySlot.SLOT_6.ordinal(), m.id() + " on R/G/X/Z/V/C only");
		}
		for (AbilitySlot s : new AbilitySlot[] { AbilitySlot.SLOT_1, AbilitySlot.SLOT_2, AbilitySlot.SLOT_3, AbilitySlot.SLOT_4, AbilitySlot.SLOT_5,
				AbilitySlot.SLOT_6 }) {
			helper.assertTrue(k.moveOn(s, false, false) != null || k.moveOn(s, false, true) != null, "a move on " + s);
		}
		Aliens.discover(p, a.id(), true);
		helper.assertTrue(Aliens.playable(p).contains(a) && !Aliens.unfound(p).contains(a), "found, on the dial and out of the draw");
		helper.succeed();
	}

	// ---------------------------------------------------------------- the kit

	/** R 8; G the cone 12 and thrown back; Shift+G the beam 16 through a line of them. */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 160)
	public void benwolfClawAndHowls(GameTestHelper helper) {
		ServerPlayer p = benwolfPlayer(helper);
		Zombie z = zombie(helper, 3.5, 2, 3.8, 200f);
		Zombie z2 = zombie(helper, 3.5, 2, 6.5, 200f);
		helper.runAfterDelay(2, () -> {
			lookAt(p, z);
			int c = benwolf().clawSwipe(p);
			helper.assertTrue(c >= 1, "the claw caught it");
			near(helper, 200f - z.getHealth(), BenwolfKit.CLAW_DAMAGE, "Claw Swipe");
			float h = z.getHealth(), h2 = z2.getHealth();
			z.invulnerableTime = 0;
			z2.invulnerableTime = 0;
			Vec3 before = z.position();
			int n = benwolf().sonicHowl(p);
			helper.assertTrue(n == 2, "the cone hit both: " + n);
			near(helper, h - z.getHealth(), BenwolfKit.HOWL_DAMAGE, "Sonic Howl");
			near(helper, h2 - z2.getHealth(), BenwolfKit.HOWL_DAMAGE, "Sonic Howl (the far one)");
			helper.assertTrue(z.getDeltaMovement().z > 0.5 || z.position().z > before.z, "thrown back: " + z.getDeltaMovement());
			helper.assertTrue(benwolf().flag(p, BenwolfKit.F_HOWL), "the rings of sound roll out");
			helper.assertTrue(BenwolfKit.jawOpen(p, 6f) > 0.9f, "the jaw is split wide mid-howl");
		});
		helper.runAfterDelay(30, () -> {
			// the zombies were thrown: put them back in a line
			Vec3 a = abs(helper, 3.5, 2, 4.5), b = abs(helper, 3.5, 2, 7.5);
			z.teleportTo(a.x, a.y, a.z);
			z2.teleportTo(b.x, b.y, b.z);
			z.setDeltaMovement(Vec3.ZERO);
			z2.setDeltaMovement(Vec3.ZERO);
		});
		helper.runAfterDelay(33, () -> {
			lookAt(p, z);
			float h = z.getHealth(), h2 = z2.getHealth();
			z.invulnerableTime = 0;
			z2.invulnerableTime = 0;
			// v0.17.4: Focused Howl is gone; Shift+G is the Disorienting Howl, all round him
			helper.assertTrue(benwolf().move("focused_howl") == null, "Focused Howl is gone");
			int n = benwolf().disorientingHowl(p);
			helper.assertTrue(n == 2, "the warbling howl caught both: " + n);
			near(helper, h - z.getHealth(), BenwolfKit.DISORIENT_DAMAGE, "Disorienting Howl");
			near(helper, h2 - z2.getHealth(), BenwolfKit.DISORIENT_DAMAGE, "Disorienting Howl (the far one)");
			helper.assertTrue(z.hasEffect(net.minecraft.world.effect.MobEffects.CONFUSION), "Nausea");
			helper.succeed();
		});
	}

	/** Z: a tap is the uncharged Shockwave (14 within 4); held 2 s it is 22 within 9. */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void benwolfShockwaveChargesFrom14To22(GameTestHelper helper) {
		ServerPlayer p = benwolfPlayer(helper);
		Zombie near = zombie(helper, 3.5, 2, 4.5, 200f);
		Zombie far = zombie(helper, 3.5, 2, 7.4, 200f);
		helper.runAfterDelay(2, () -> {
			benwolf().press(p, AbilitySlot.SLOT_4, false); // the tap
			near(helper, 200f - near.getHealth(), BenwolfKit.SHOCK_MIN, "uncharged Shockwave");
			near(helper, 200f - far.getHealth(), 0f, "out of the uncharged reach");
			helper.assertTrue(benwolf().cooldownLeft(p, BenwolfKit.SHOCKWAVE) > 0, "cooling down");
			benwolf().cooldown(p, BenwolfKit.SHOCKWAVE, 0);
			helper.assertTrue(benwolf().beginCharge(p), "the charge begins");
			helper.assertTrue(benwolf().flag(p, BenwolfKit.F_CHARGE), "charging");
		});
		helper.runAfterDelay(3 + BenwolfKit.CHARGE_FULL + 2, () -> {
			near.teleportTo(near.getX(), near.getY(), abs(helper, 3.5, 2, 4.5).z);
			far.teleportTo(far.getX(), far.getY(), abs(helper, 3.5, 2, 7.4).z);
			float h = near.getHealth(), hf = far.getHealth();
			near.invulnerableTime = 0;
			far.invulnerableTime = 0;
			benwolf().holdEnd(p, AbilitySlot.SLOT_4, BenwolfKit.CHARGE_FULL + 2);
			helper.assertFalse(benwolf().flag(p, BenwolfKit.F_CHARGE), "released");
			near(helper, h - near.getHealth(), BenwolfKit.SHOCK_MAX, "fully charged Shockwave");
			near(helper, hf - far.getHealth(), BenwolfKit.SHOCK_MAX, "fully charged reaches 9 blocks");
			helper.succeed();
		});
	}

	/** X and Shift+R launch him (no fall damage while airborne); Sensitive Ears: blasts hit him x1.5; the body's numbers. */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void benwolfLeapPounceEarsAndBody(GameTestHelper helper) {
		ServerPlayer p = benwolfPlayer(helper);
		Zombie z = zombie(helper, 3.5, 2, 9.5, 200f);
		helper.runAfterDelay(2, () -> {
			var st = benwolf().stats();
			helper.assertTrue(st.maxHealth() == 40f && st.attack() == 7f && st.speed() > 1f, "a strong, quick body: " + st);
			helper.assertTrue(benwolf().wolfLeap(p), "Wolf Leap");
			helper.assertTrue(p.getDeltaMovement().horizontalDistance() > 1.2 && p.getDeltaMovement().y > 0.5, "launched: " + p.getDeltaMovement());
			helper.assertTrue(benwolf().flag(p, BenwolfKit.F_LEAP), "leaping");
			helper.assertFalse(benwolf().allowDamage(p, p.damageSources().fall(), 6f), "no fall damage mid-leap");
			lookAt(p, z);
			helper.assertTrue(benwolf().pounce(p), "Pounce");
			helper.assertTrue(benwolf().flag(p, BenwolfKit.F_POUNCE) && p.getDeltaMovement().z > 0.3, "flung at the zombie: " + p.getDeltaMovement());
			helper.assertFalse(benwolf().pounce(p), "one pounce at a time");
			near(helper, benwolf().incomingDamage(p, p.damageSources().explosion(null, null), 4f), 4f * BenwolfKit.EARS_TAKEN, "Sensitive Ears");
			near(helper, benwolf().incomingDamage(p, p.damageSources().generic(), 4f), 4f, "an ordinary hit");
			helper.succeed();
		});
	}

	/** C: six slashes of 5 over 1.2 s; V: Tracking Senses outlines creatures (for him) and gives night vision, then ends. */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 420)
	public void benwolfFrenzyAndTrackingSenses(GameTestHelper helper) {
		ServerPlayer p = benwolfPlayer(helper);
		Zombie z = zombie(helper, 3.5, 2, 3.6, 200f);
		helper.runAfterDelay(2, () -> {
			lookAt(p, z);
			helper.assertTrue(benwolf().frenzy(p), "Frenzy");
			helper.assertTrue(benwolf().trackingSenses(p), "Tracking Senses");
			helper.assertTrue(p.hasEffect(MobEffects.NIGHT_VISION), "night vision");
			helper.assertTrue(BenwolfKit.senses(p, z), "the zombie is picked out");
		});
		helper.runAfterDelay(2 + BenwolfKit.FRENZY_HITS * BenwolfKit.FRENZY_EVERY + 6, () -> {
			near(helper, 200f - z.getHealth(), BenwolfKit.FRENZY_DAMAGE * BenwolfKit.FRENZY_HITS, "six slashes of 5");
			helper.assertFalse(benwolf().flag(p, BenwolfKit.F_FRENZY), "the flurry is over");
		});
		helper.runAfterDelay(2 + BenwolfKit.SENSES_TICKS + 4, () -> {
			helper.assertFalse(benwolf().flag(p, BenwolfKit.F_SENSES), "the senses fade after 15 s");
			helper.assertFalse(BenwolfKit.senses(p, z), "nothing picked out");
			helper.succeed();
		});
	}
}
