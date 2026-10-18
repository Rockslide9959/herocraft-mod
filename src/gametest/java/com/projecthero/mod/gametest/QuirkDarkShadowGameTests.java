package com.projecthero.mod.gametest;

import java.util.HashSet;
import java.util.Set;

import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.quirk.Quirk;
import com.projecthero.mod.quirk.QuirkAbilityManager;
import com.projecthero.mod.quirk.QuirkKit;
import com.projecthero.mod.quirk.QuirkKits;
import com.projecthero.mod.quirk.Quirks;
import com.projecthero.mod.quirk.darkshadow.DarkShadow;
import com.projecthero.mod.quirk.darkshadow.DarkShadowCombat;
import com.projecthero.mod.quirk.darkshadow.DarkShadowConfig;
import com.projecthero.mod.quirk.darkshadow.DarkShadowMoves;
import com.projecthero.mod.squad.Squad;
import com.projecthero.mod.squad.SquadManager;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.7 Dark Shadow: one test per move (the server effect and its number), the Shadow meter following the light,
 * strength scaling with the meter, Berserk (triggered by full Shadow in the dark; never hits its user or a squadmate,
 * even with friendly fire on), Calm through the core's key routing while berserk, and the Berserk burning out. Mock
 * players are not ticked by the server, so tests that run over time drive the Quirk tick themselves ({@link #pump}).
 */
public class QuirkDarkShadowGameTests implements FabricGameTest {
	private static final float EPS = 0.6f;

	// ---------------------------------------------------------------- helpers

	@SuppressWarnings("removal")
	private static ServerPlayer mock(GameTestHelper helper, double x, double y, double z) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(x, y, z));
		p.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);
		p.setYHeadRot(0f);
		p.setYBodyRot(0f);
		try {
			java.lang.reflect.Field f = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
			f.setAccessible(true);
			f.setInt(p, 0);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
		return p;
	}

	/** A Dark Shadow user facing +Z, every move ready, the meter at {@code meter}. */
	private static ServerPlayer user(GameTestHelper helper, double x, double y, double z, float meter) {
		ServerPlayer p = mock(helper, x, y, z);
		helper.assertTrue(Quirks.grant(p, Quirk.DARK_SHADOW), "Dark Shadow is granted");
		Quirks.edit(p, n -> n.abilityReadyAt.clear());
		Quirks.setMeter(p, meter);
		return p;
	}

	private static void pump(GameTestHelper helper, ServerPlayer p) {
		helper.onEachTick(() -> {
			p.tickCount++;
			Quirks.tick(p);
		});
	}

	private static void floor(GameTestHelper helper) {
		for (int x = 0; x <= 7; x++) {
			for (int z = 0; z <= 7; z++) {
				helper.getLevel().setBlock(helper.absolutePos(new BlockPos(x, 1, z)), Blocks.STONE.defaultBlockState(), 3);
			}
		}
	}

	/** A sealed stone room: walls x/z 1 and 7, floor y 1, roof y 5 -- inside (2..6, 2..4, 2..6) is pitch dark. */
	private static void darkRoom(GameTestHelper helper) {
		for (int x = 1; x <= 7; x++) {
			for (int y = 1; y <= 5; y++) {
				for (int z = 1; z <= 7; z++) {
					boolean shell = x == 1 || x == 7 || z == 1 || z == 7 || y == 1 || y == 5;
					helper.getLevel().setBlock(helper.absolutePos(new BlockPos(x, y, z)),
							shell ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 3);
				}
			}
		}
	}

	private static <T extends Mob> T mob(GameTestHelper helper, EntityType<T> type, double x, double y, double z, double health) {
		helper.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
		T m = type.create(helper.getLevel());
		Vec3 at = helper.absoluteVec(new Vec3(x, y, z));
		m.moveTo(at.x, at.y, at.z, 180.0f, 0.0f);
		m.setNoAi(true);
		m.setPersistenceRequired();
		m.getAttribute(Attributes.MAX_HEALTH).setBaseValue(health);
		m.setHealth((float) health);
		if (m.getAttribute(Attributes.ARMOR) != null) {
			m.getAttribute(Attributes.ARMOR).setBaseValue(0.0);
		}
		m.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.CARVED_PUMPKIN));
		helper.getLevel().addFreshEntity(m);
		return m;
	}

	private static QuirkKit kit() {
		return QuirkKits.of(Quirk.DARK_SHADOW);
	}

	// ---------------------------------------------------------------- the kit

	@GameTest(template = EMPTY_STRUCTURE)
	public void darkshadowKitRegistered(GameTestHelper helper) {
		Set<String> ids = new HashSet<>();
		for (AbilitySlot slot : new AbilitySlot[] { AbilitySlot.SLOT_1, AbilitySlot.SLOT_2, AbilitySlot.SLOT_3, AbilitySlot.SLOT_4,
				AbilitySlot.SLOT_5, AbilitySlot.SLOT_6 }) {
			String[] two = kit().idsOf(slot);
			helper.assertTrue(two.length == 2, "two moves on " + slot);
			for (String id : two) {
				helper.assertTrue(id.startsWith("quirk_dark_shadow_") && kit().maxCooldown(id) > 0, "id and cooldown " + id);
				ids.add(id);
			}
		}
		helper.assertTrue(ids.size() == 12, "12 distinct moves, " + ids.size());
		helper.assertTrue(kit().usableWhileOverloaded(AbilitySlot.SLOT_6, true), "Calm works while berserk");
		helper.assertFalse(kit().usableWhileOverloaded(AbilitySlot.SLOT_1, false), "Strike does not");
		helper.succeed();
	}

	// ---------------------------------------------------------------- the meter / strength

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 120)
	public void shadowRisesInTheDarkAndFallsInTheLight(GameTestHelper helper) {
		darkRoom(helper);
		ServerPlayer p = user(helper, 4.5, 2.0, 4.5, 50f);
		pump(helper, p);
		helper.runAfterDelay(6, () -> {
			helper.assertTrue(DarkShadowCombat.lightAt(p) <= DarkShadowConfig.DARK_LIGHT, "dark inside, light " + DarkShadowCombat.lightAt(p));
			Quirks.setMeter(p, 50f);
		});
		helper.runAfterDelay(31, () -> {
			float m = Quirks.meter(p);
			helper.assertTrue(m > 54f, "the meter rose in the dark, " + m);
			// a lamp right beside his eyes
			helper.getLevel().setBlock(helper.absolutePos(new BlockPos(5, 3, 4)), Blocks.GLOWSTONE.defaultBlockState(), 3);
		});
		helper.runAfterDelay(40, () -> {
			helper.assertTrue(DarkShadowCombat.lightAt(p) >= 12, "lit now, light " + DarkShadowCombat.lightAt(p));
			Quirks.setMeter(p, 50f);
		});
		helper.runAfterDelay(65, () -> {
			float m = Quirks.meter(p);
			helper.assertTrue(m < 46f, "the meter fell in the light, " + m);
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void strengthScalesWithShadow(GameTestHelper helper) {
		floor(helper);
		helper.assertTrue(Math.abs(DarkShadowCombat.strengthAt(0f) - DarkShadowConfig.STRENGTH_MIN) < 1e-4, "empty: x0.5");
		helper.assertTrue(Math.abs(DarkShadowCombat.strengthAt(100f) - DarkShadowConfig.STRENGTH_MAX) < 1e-4, "full: x1.6");
		ServerPlayer p = user(helper, 4.5, 2.0, 0.5, 0f);
		Zombie z = mob(helper, EntityType.ZOMBIE, 4.5, 2.0, 5.5, 100);
		helper.assertTrue(DarkShadowMoves.strike(p) == z, "the strike lands");
		float weak = 100f - z.getHealth();
		helper.assertTrue(Math.abs(weak - DarkShadowConfig.STRIKE_DAMAGE * DarkShadowConfig.STRENGTH_MIN) < EPS, "weak in the light, " + weak);
		helper.succeed();
	}

	// ---------------------------------------------------------------- R

	@GameTest(template = EMPTY_STRUCTURE)
	public void shadowStrikeHitsEightBlocksOut(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = user(helper, 4.5, 2.0, 0.2, 100f);
		Zombie z = mob(helper, EntityType.ZOMBIE, 4.5, 2.0, 7.2, 100);
		helper.assertTrue(DarkShadowMoves.strike(p) == z, "the strike reaches 7 blocks");
		float taken = 100f - z.getHealth();
		float want = DarkShadowConfig.STRIKE_DAMAGE * DarkShadowConfig.STRENGTH_MAX;
		helper.assertTrue(Math.abs(taken - want) < EPS, "9 x 1.6 in the dark, took " + taken);
		helper.assertTrue(DarkShadow.reach(p) != null, "the reach point is synced for the model");
		helper.assertTrue(!Quirks.ready(p, DarkShadowConfig.STRIKE), "on cooldown");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 80)
	public void shadowFlurryStrikesSixTimes(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = user(helper, 4.5, 2.0, 1.5, 50f);
		Zombie z = mob(helper, EntityType.ZOMBIE, 4.5, 2.0, 5.5, 200);
		pump(helper, p);
		helper.assertTrue(DarkShadowMoves.flurry(p), "flurry starts");
		helper.runAfterDelay(30, () -> {
			float taken = 200f - z.getHealth();
			float lo = DarkShadowConfig.FLURRY_HITS * DarkShadowConfig.FLURRY_DAMAGE * DarkShadowConfig.STRENGTH_MIN;
			float hi = DarkShadowConfig.FLURRY_HITS * DarkShadowConfig.FLURRY_DAMAGE * DarkShadowConfig.STRENGTH_MAX;
			helper.assertTrue(taken >= lo - EPS && taken <= hi + EPS, "six strikes, took " + taken);
			helper.assertFalse(DarkShadow.running(p, DarkShadow.V_FLURRY), "it ended");
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- G

	@GameTest(template = EMPTY_STRUCTURE)
	public void shadowGuardBlocksTheFront(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = user(helper, 4.5, 2.0, 3.5, 50f);
		Zombie front = mob(helper, EntityType.ZOMBIE, 4.5, 2.0, 5.5, 20);
		Zombie back = mob(helper, EntityType.ZOMBIE, 4.5, 2.0, 1.5, 20);
		DarkShadowMoves.guardOn(p);
		helper.assertTrue(DarkShadow.guarding(p), "the guard is up");
		DamageSource fromFront = helper.getLevel().damageSources().mobAttack(front);
		DamageSource fromBack = helper.getLevel().damageSources().mobAttack(back);
		float f = kit().modifyIncomingDamage(p, fromFront, 10f);
		float b = kit().modifyIncomingDamage(p, fromBack, 10f);
		helper.assertTrue(Math.abs(f - 10f * (1f - DarkShadowConfig.GUARD_BLOCK)) < 0.01f, "85% off from the front, " + f);
		helper.assertTrue(b == 10f, "nothing off from behind, " + b);
		kit().handle(p, AbilitySlot.SLOT_2, false, false);
		helper.assertFalse(DarkShadow.guarding(p), "released");
		helper.assertTrue(!Quirks.ready(p, DarkShadowConfig.GUARD), "the cooldown starts on release");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void shadowCounterAnswersTheHit(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = user(helper, 4.5, 2.0, 3.5, 100f);
		Zombie z = mob(helper, EntityType.ZOMBIE, 4.5, 2.0, 5.0, 100);
		DarkShadowMoves.counter(p);
		float got = kit().modifyIncomingDamage(p, helper.getLevel().damageSources().mobAttack(z), 8f);
		helper.assertTrue(got == 0f, "the hit is blocked, " + got);
		float taken = 100f - z.getHealth();
		helper.assertTrue(Math.abs(taken - DarkShadowConfig.COUNTER_DAMAGE * DarkShadowConfig.STRENGTH_MAX) < EPS, "answered, took " + taken);
		float second = kit().modifyIncomingDamage(p, helper.getLevel().damageSources().mobAttack(z), 8f);
		helper.assertTrue(second == 8f, "one counter only, " + second);
		helper.succeed();
	}

	// ---------------------------------------------------------------- X

	@GameTest(template = EMPTY_STRUCTURE)
	public void blackFallenAngelGivesFlight(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = user(helper, 4.5, 2.0, 4.5, 50f);
		helper.assertTrue(DarkShadowMoves.toggleAngel(p), "wings out");
		helper.assertTrue(p.getAbilities().mayfly && p.getAbilities().flying, "flying");
		helper.assertTrue(DarkShadow.angel(p), "synced");
		kit().handle(p, AbilitySlot.SLOT_3, true, false);
		helper.assertFalse(DarkShadow.angel(p), "the key turns it off");
		helper.assertFalse(p.getAbilities().mayfly, "the lent flight is taken back");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void shadowDiveDashesAndStrikes(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = user(helper, 4.5, 2.0, 2.5, 100f);
		Zombie z = mob(helper, EntityType.ZOMBIE, 4.5, 2.0, 3.8, 100);
		DarkShadowMoves.dive(p);
		Vec3 v = p.getDeltaMovement();
		helper.assertTrue(Math.abs(v.z - DarkShadowConfig.DIVE_SPEED) < 0.05, "dashing along the look, v=" + v);
		float taken = 100f - z.getHealth();
		helper.assertTrue(Math.abs(taken - DarkShadowConfig.DIVE_DAMAGE * DarkShadowConfig.STRENGTH_MAX) < EPS, "what it passes is struck, " + taken);
		helper.succeed();
	}

	// ---------------------------------------------------------------- Z

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void shadowGrabPullsItIn(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = user(helper, 4.5, 2.0, 0.5, 50f);
		Zombie z = mob(helper, EntityType.ZOMBIE, 4.5, 2.0, 7.5, 100);
		pump(helper, p);
		helper.assertTrue(DarkShadowMoves.grab(p) == z, "snatched");
		helper.runAfterDelay(12, () -> {
			double d = z.position().distanceTo(p.position());
			helper.assertTrue(d < DarkShadowConfig.GRAB_HOLD_DISTANCE + 0.6, "pulled to him, " + d);
			helper.assertTrue(z.getHealth() < 100f, "and hurt");
			helper.assertFalse(DarkShadow.on(p, DarkShadow.V_GRAB), "let go");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void grabAndThrowHurlsIt(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = user(helper, 4.5, 2.0, 0.5, 100f);
		Zombie z = mob(helper, EntityType.ZOMBIE, 4.5, 2.0, 6.5, 100);
		helper.assertTrue(DarkShadowMoves.grabThrow(p) == z, "snatched");
		Vec3 v = z.getDeltaMovement();
		helper.assertTrue(v.z > DarkShadowConfig.THROW_SPEED * 0.8 && v.y > 0.2, "hurled forward and up, v=" + v);
		float taken = 100f - z.getHealth();
		helper.assertTrue(Math.abs(taken - DarkShadowConfig.THROW_DAMAGE * DarkShadowConfig.STRENGTH_MAX) < EPS, "hurt, " + taken);
		helper.succeed();
	}

	// ---------------------------------------------------------------- V

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void abyssalRampageRefusedInTheLight(GameTestHelper helper) {
		darkRoom(helper);
		helper.getLevel().setBlock(helper.absolutePos(new BlockPos(4, 3, 3)), Blocks.GLOWSTONE.defaultBlockState(), 3);
		ServerPlayer p = user(helper, 3.5, 2.0, 3.5, 50f);
		helper.runAfterDelay(6, () -> {
			helper.assertTrue(DarkShadowCombat.lightAt(p) > DarkShadowConfig.DARK_LIGHT, "lit, " + DarkShadowCombat.lightAt(p));
			helper.assertFalse(DarkShadowMoves.rampage(p), "refused in the light");
			helper.assertTrue(Quirks.ready(p, DarkShadowConfig.RAMPAGE), "no cooldown spent");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 80)
	public void abyssalRampageHitsAllAround(GameTestHelper helper) {
		darkRoom(helper);
		ServerPlayer p = user(helper, 3.5, 2.0, 3.5, 30f);
		Zombie a = mob(helper, EntityType.ZOMBIE, 5.5, 2.0, 5.5, 200);
		Zombie b = mob(helper, EntityType.ZOMBIE, 2.5, 2.0, 5.5, 200);
		pump(helper, p);
		helper.runAfterDelay(6, () -> {
			helper.assertTrue(DarkShadowMoves.rampage(p), "the rampage starts in the dark");
		});
		helper.runAfterDelay(30, () -> {
			helper.assertTrue(a.getHealth() < 200f - 10f && b.getHealth() < 200f - 10f,
					"both hit again and again, " + a.getHealth() + " / " + b.getHealth());
			helper.assertTrue(p.getHealth() == p.getMaxHealth(), "never its user");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 160)
	public void abyssalSwallowHoldsAndChews(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = user(helper, 4.5, 2.0, 1.5, 50f);
		Zombie z = mob(helper, EntityType.ZOMBIE, 4.5, 2.0, 5.5, 100);
		pump(helper, p);
		helper.assertTrue(DarkShadowMoves.swallow(p) == z, "swallowed");
		helper.runAfterDelay(40, () -> {
			Vec3 maw = DarkShadowMoves.mawPoint(p);
			double d = z.position().add(0, z.getBbHeight() * 0.5, 0).distanceTo(maw);
			helper.assertTrue(d < 0.6, "held in the maw, " + d);
			helper.assertTrue(z.getHealth() < 100f - 3f, "chewed, " + z.getHealth());
		});
		helper.runAfterDelay(DarkShadowConfig.SWALLOW_TICKS + 10, () -> {
			helper.assertFalse(DarkShadow.on(p, DarkShadow.V_SWALLOW), "let go after 5 s");
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- C

	@GameTest(template = EMPTY_STRUCTURE)
	public void blackAnkhArmour(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = user(helper, 4.5, 2.0, 4.5, 100f);
		double armour = p.getAttributeValue(Attributes.ARMOR);
		double tough = p.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
		double dmg = p.getAttributeValue(Attributes.ATTACK_DAMAGE);
		helper.assertTrue(DarkShadowMoves.toggleAnkh(p), "on");
		helper.assertTrue(p.getAttributeValue(Attributes.ARMOR) - armour >= DarkShadowConfig.ANKH_ARMOR - 0.01, "armour");
		helper.assertTrue(p.getAttributeValue(Attributes.ARMOR_TOUGHNESS) - tough >= DarkShadowConfig.ANKH_TOUGHNESS - 0.01, "toughness");
		double gain = p.getAttributeValue(Attributes.ATTACK_DAMAGE) - dmg;
		helper.assertTrue(Math.abs(gain - DarkShadowConfig.ANKH_DAMAGE * DarkShadowConfig.STRENGTH_MAX) < 0.11, "claws: +damage x strength, " + gain);
		kit().handle(p, AbilitySlot.SLOT_6, true, false);
		helper.assertFalse(DarkShadow.ankh(p), "the key turns it off");
		helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.ARMOR) - armour) < 0.01, "armour gone");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void calmEndsBerserkThroughTheKeys(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = user(helper, 4.5, 2.0, 4.5, 100f);
		DarkShadowMoves.goBerserk(p);
		helper.assertTrue(DarkShadow.berserk(p) && Quirks.overloaded(p), "berserk");
		p.setShiftKeyDown(false);
		QuirkAbilityManager.handle(p, AbilitySlot.SLOT_1, true);
		helper.assertTrue(Quirks.ready(p, DarkShadowConfig.STRIKE), "moves are locked while berserk");
		p.setShiftKeyDown(true);
		QuirkAbilityManager.handle(p, AbilitySlot.SLOT_6, true);
		helper.assertFalse(Quirks.overloaded(p), "Calm ended the Berserk");
		helper.assertTrue(Quirks.meter(p) <= DarkShadowConfig.CALM_METER + 0.01f, "Shadow dropped, " + Quirks.meter(p));
		helper.assertFalse(DarkShadow.on(p, DarkShadow.V_BERSERK), "flag cleared");
		helper.succeed();
	}

	// ---------------------------------------------------------------- Berserk

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void berserkInTheDarkNeverHitsUserOrSquad(GameTestHelper helper) {
		darkRoom(helper);
		ServerPlayer p = user(helper, 3.5, 2.0, 3.5, 50f);
		ServerPlayer mate = mock(helper, 5.5, 2.0, 3.5);
		Zombie z = mob(helper, EntityType.ZOMBIE, 4.5, 2.0, 5.5, 200);
		SquadManager squads = SquadManager.get(helper.getLevel().getServer());
		Squad squad = squads.create("ds" + p.getUUID().toString().substring(0, 8), p.getUUID());
		squads.addMember(squad, mate.getUUID());
		squads.setFriendlyFire(squad, true); // the frenzy must spare squadmates even then
		pump(helper, p);
		helper.runAfterDelay(6, () -> Quirks.setMeter(p, 99.5f));
		helper.runAfterDelay(50, () -> {
			try {
				helper.assertTrue(DarkShadow.berserk(p), "full Shadow in the dark: berserk");
				helper.assertTrue(z.getHealth() < 200f - 10f, "it rampages at the zombie, " + z.getHealth());
				helper.assertTrue(mate.getHealth() == mate.getMaxHealth(), "never the squadmate, " + mate.getHealth());
				helper.assertTrue(p.getHealth() == p.getMaxHealth(), "never its user, " + p.getHealth());
			} finally {
				squads.disband(squad);
			}
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void berserkBurnsOutToSixty(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = user(helper, 4.5, 2.0, 4.5, 100f);
		Quirks.overload(p, 10);
		Quirks.setVal(p, DarkShadow.V_BERSERK, 1L);
		pump(helper, p);
		helper.runAfterDelay(16, () -> {
			helper.assertFalse(DarkShadow.berserk(p), "over");
			float m = Quirks.meter(p);
			helper.assertTrue(m > DarkShadowConfig.BERSERK_AFTER_METER - 3f && m < DarkShadowConfig.BERSERK_AFTER_METER + 3f, "back to 60, " + m);
			helper.succeed();
		});
	}
}
