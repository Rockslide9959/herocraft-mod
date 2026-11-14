package com.projecthero.mod.gametest;

import com.projecthero.mod.hero.AbilityRouter;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.OmnitrixInfo;
import com.projecthero.mod.omnitrix.alien.AlienKit;
import com.projecthero.mod.omnitrix.alien.Aliens;
import com.projecthero.mod.omnitrix.alien.arctiguana.ArctiguanaKit;
import com.projecthero.mod.omnitrix.alien.bigchill.BigChillKit;

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
 * v0.17.3 Arctiguana (user: "make aliens based on their abilities and stuff they do in the show please"): a hidden prototype alien found
 * by a mistransform (the even draw itself is {@code OmnitrixCannonboltGameTests}, which takes every hidden alien); his dial glyph U+E01E;
 * no move on H or N and a move on each of R/G/X/Z/V/C; and his kit numbers.
 */
public class OmnitrixArctiguanaGameTests implements FabricGameTest {
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

	private static ArctiguanaKit arctiguana() {
		return (ArctiguanaKit) Aliens.kit(ArctiguanaKit.ID);
	}

	// ---------------------------------------------------------------- the dial

	@GameTest(template = EMPTY_STRUCTURE)
	public void arctiguanaIsAHiddenPrototypeAlienWithHisGlyph(GameTestHelper helper) {
		Object[][] all = { { Aliens.ARCTIGUANA, (char) 0xE01E } };
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

	// ---------------------------------------------------------------- Arctiguana

	/** Hold R (v0.17.4): three gusts of the breath (3 each) freeze a zombie solid in the ice shell; Deep Freeze does 17 and freezes everything near. */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void arctiguanaBreathFreezesSolidAndDeepFreezeHits17(GameTestHelper helper) {
		ServerPlayer p = alien(helper, ArctiguanaKit.ID);
		Zombie z = zombie(helper, 3.5, 2, 4.5, 100f);
		Zombie far = zombie(helper, 7.5, 2, 1.5, 100f); // square to his side: out of the cone
		helper.runAfterDelay(2, () -> {
			lookAt(p, z);
			arctiguana().holdStart(p, AbilitySlot.SLOT_1);
			helper.assertTrue(arctiguana().flag(p, ArctiguanaKit.F_BREATH), "breathing");
		});
		helper.runAfterDelay(2 + ArctiguanaKit.BREATH_TICKS - 2, () -> arctiguana().holdEnd(p, AbilitySlot.SLOT_1, ArctiguanaKit.BREATH_TICKS + 6)); // let go just before 1.5 s: 6 gusts
		helper.runAfterDelay(2 + ArctiguanaKit.BREATH_TICKS + 2, () -> {
			helper.assertFalse(arctiguana().flag(p, ArctiguanaKit.F_BREATH), "the breath stops when R comes up");
			helper.assertTrue(arctiguana().cooldownLeft(p, ArctiguanaKit.BREATH) > 0, "and its cooldown runs from the release");
			float lost = 100f - z.getHealth();
			helper.assertTrue(lost >= 3f * 3 - 0.05f && lost <= 3f * 6 + 0.05f, "3 a gust, 6 gusts at most: " + lost);
			helper.assertTrue(BigChillKit.shelled(z), "frozen solid in a block of ice");
			helper.assertFalse(BigChillKit.shelled(far), "out of the cone: untouched");
			arctiguana().cooldown(p, ArctiguanaKit.FREEZE, 0);
			float h = z.getHealth();
			z.invulnerableTime = 0;
			int n = arctiguana().deepFreeze(p);
			helper.assertTrue(n >= 1, "Deep Freeze caught the zombie: " + n);
			near(helper, h - z.getHealth(), ArctiguanaKit.FREEZE_DAMAGE, "Deep Freeze");
		});
		int frozeAt = 2 + ArctiguanaKit.BREATH_TICKS + 2;
		helper.runAfterDelay(frozeAt + ArctiguanaKit.FREEZE_TICKS - 10, () -> helper.assertTrue(BigChillKit.shelled(z), "still in the ice near the end of the 3 s"));
		helper.runAfterDelay(frozeAt + ArctiguanaKit.FREEZE_TICKS + 6, () -> {
			helper.assertFalse(BigChillKit.shelled(z), "the ice is gone after 3 s");
			helper.succeed();
		});
	}

	/** G: a wall of packed ice into air only (a block in the way stays), melting back to air after 8 s. */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 260)
	public void arctiguanaFrostWallGoesIntoAirAndMelts(GameTestHelper helper) {
		ServerPlayer p = alien(helper, ArctiguanaKit.ID);
		BlockPos stone = helper.absolutePos(new BlockPos(3, 2, 4));
		helper.runAfterDelay(2, () -> {
			helper.getLevel().setBlock(stone, Blocks.OAK_PLANKS.defaultBlockState(), 3);
			p.setYRot(0f);
			p.setXRot(0f);
			int n = arctiguana().frostWall(p);
			helper.assertTrue(n == ArctiguanaKit.WALL_WIDE * ArctiguanaKit.WALL_HIGH - 1, "the wall went up round the plank (14 of 15): " + n);
			helper.assertTrue(helper.getLevel().getBlockState(stone).is(Blocks.OAK_PLANKS), "never replaces a block");
			helper.assertTrue(helper.getLevel().getBlockState(stone.above()).is(Blocks.PACKED_ICE), "packed ice above it");
		});
		helper.runAfterDelay(2 + ArctiguanaKit.WALL_TICKS + 4, () -> {
			helper.assertTrue(helper.getLevel().getBlockState(stone.above()).isAir(), "melted back to air after 8 s");
			helper.assertTrue(helper.getLevel().getBlockState(stone).is(Blocks.OAK_PLANKS), "the plank is still there");
			helper.succeed();
		});
	}

	/** Cold Blooded (freezing never hurts), Heat (fire x1.5), Frost Armour (40% less, frosts a melee attacker), Tail Whip (8 all round). */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 120)
	public void arctiguanaColdBloodedArmourAndTailWhip(GameTestHelper helper) {
		ServerPlayer p = alien(helper, ArctiguanaKit.ID);
		Zombie front = zombie(helper, 3.5, 2, 3.5, 100f);
		Zombie back = zombie(helper, 3.5, 2, 0.4, 100f);
		helper.runAfterDelay(2, () -> {
			float h = p.getHealth();
			p.invulnerableTime = 0;
			p.hurt(p.damageSources().freeze(), 6f);
			helper.assertTrue(p.getHealth() >= h, "freezing never hurts him");
			int hit = arctiguana().tailWhip(p);
			helper.assertTrue(hit >= 2, "the whip goes right round him: " + hit); // (a neighbouring test's mob can stray into reach)
			near(helper, 100f - front.getHealth(), ArctiguanaKit.WHIP_DAMAGE, "Tail Whip in front");
			near(helper, 100f - back.getHealth(), ArctiguanaKit.WHIP_DAMAGE, "Tail Whip behind");
			float plain = ((ArctiguanaKit) arctiguana()).incomingDamage(p, p.damageSources().mobAttack(front), 10f);
			near(helper, plain, 10f, "a plain hit unarmoured");
			float fire = arctiguana().incomingDamage(p, p.damageSources().onFire(), 4f);
			near(helper, fire, 6f, "Heat: fire half again");
			helper.assertTrue(arctiguana().frostArmour(p), "Frost Armour on");
			front.setTicksFrozen(0);
			float armoured = arctiguana().incomingDamage(p, p.damageSources().mobAttack(front), 10f);
			near(helper, armoured, 6f, "Frost Armour: 40% less");
			helper.assertTrue(front.getTicksFrozen() > 0, "the attacker is frosted");
			helper.succeed();
		});
	}

}
