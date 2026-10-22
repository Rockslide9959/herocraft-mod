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

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.7 (user) Quirk revamp -- Dark Shadow: the kit layout (X movement, V mode), the darkness it feels following the
 * light (and a held torch), the meter, Berserk (in the pitch dark; hits mobs and, non-lethally, its user; light calms it;
 * the flare works through the keys while berserk), intentional Berserk only in darkness, Black Ankh and Abyssal Black
 * Ankh, every move's server effect, Recall, and the versioned config. Mock players are not ticked by the server, so tests
 * that run over time drive the Quirk tick themselves ({@link #pump}); tests that set the darkness directly don't pump.
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

	/** A Dark Shadow user facing +Z, every move ready, the meter at {@code meter}, the darkness at {@code dark} (0..1). */
	private static ServerPlayer user(GameTestHelper helper, double x, double y, double z, float meter, float dark) {
		ServerPlayer p = mock(helper, x, y, z);
		helper.assertTrue(Quirks.grant(p, Quirk.DARK_SHADOW), "Dark Shadow is granted");
		Quirks.edit(p, n -> n.abilityReadyAt.clear());
		Quirks.setMeter(p, meter);
		Quirks.setVal(p, DarkShadow.V_DARK, Math.round(dark * 1000f));
		return p;
	}

	private static void pump(GameTestHelper helper, ServerPlayer p) {
		helper.onEachTick(() -> {
			p.tickCount++;
			Quirks.tick(p);
		});
	}

	/** The Quirk tick from the {@code from}-th tick on (the light engine needs a few ticks after a room is built). */
	private static void pumpFrom(GameTestHelper helper, ServerPlayer p, long from) {
		long[] n = { 0 };
		helper.onEachTick(() -> {
			if (n[0]++ >= from) {
				p.tickCount++;
				Quirks.tick(p);
			}
		});
	}

	private static void floor(GameTestHelper helper) {
		for (int x = 0; x <= 7; x++) {
			for (int z = 0; z <= 7; z++) {
				helper.getLevel().setBlock(helper.absolutePos(new BlockPos(x, 1, z)), Blocks.STONE.defaultBlockState(), 3);
			}
		}
	}

	/** A sealed stone room: walls x/z 1 and 7, floor y 1, roof y 6 -- inside (2..6, 2..5, 2..6) is pitch dark (give the light engine a few ticks). */
	private static void darkRoom(GameTestHelper helper) {
		for (int x = 1; x <= 7; x++) {
			for (int y = 1; y <= 6; y++) {
				for (int z = 1; z <= 7; z++) {
					boolean shell = x == 1 || x == 7 || z == 1 || z == 7 || y == 1 || y == 6;
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

	private static DarkShadowConfig.Moves mv() {
		return DarkShadowConfig.get().moves;
	}

	// ---------------------------------------------------------------- the kit and the config

	@GameTest(template = EMPTY_STRUCTURE)
	public void darkshadowKitLayout(GameTestHelper helper) {
		Set<String> ids = new HashSet<>();
		for (AbilitySlot slot : new AbilitySlot[] { AbilitySlot.SLOT_1, AbilitySlot.SLOT_2, AbilitySlot.SLOT_3, AbilitySlot.SLOT_4,
				AbilitySlot.SLOT_5, AbilitySlot.SLOT_6 }) {
			String[] two = kit().idsOf(slot);
			helper.assertTrue(two.length == 2, "a tap and a Shift move on " + slot);
			for (String id : two) {
				helper.assertTrue(id.startsWith("quirk_dark_shadow_") && kit().maxCooldown(id) > 0, "id and cooldown " + id);
				ids.add(id);
			}
		}
		helper.assertTrue(ids.size() == 12, "12 distinct moves, " + ids.size());
		helper.assertTrue(kit().idsOf(AbilitySlot.SLOT_3)[0].equals(DarkShadowConfig.ABYSSAL), "X is the movement move (Abyssal Black Ankh)");
		helper.assertTrue(kit().idsOf(AbilitySlot.SLOT_5)[0].equals(DarkShadowConfig.ANKH), "V is the mode (Black Ankh)");
		helper.assertTrue(kit().usableWhileOverloaded(AbilitySlot.SLOT_6, true), "the flare works while berserk");
		helper.assertFalse(kit().usableWhileOverloaded(AbilitySlot.SLOT_1, false), "Strike does not");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void darkshadowConfigIsVersioned(GameTestHelper helper) {
		helper.assertTrue(DarkShadowConfig.SPEC.currentVersion() >= 1, "versioned");
		DarkShadowConfig fresh = DarkShadowConfig.SPEC.fromJson("{\"moves\": {\"strikeDamage\": \"lots\"}, \"bogus\": 3}");
		helper.assertTrue(fresh.configVersion != null && fresh.configVersion == DarkShadowConfig.SPEC.currentVersion(), "stamped");
		helper.assertTrue(Math.abs(fresh.moves.strikeDamage - new DarkShadowConfig().moves.strikeDamage) < 1e-4, "a bad value gets its default");
		helper.assertTrue(fresh.light != null && fresh.berserk != null && fresh.meter != null, "every section filled in");
		helper.succeed();
	}

	// ---------------------------------------------------------------- light and darkness

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 140)
	public void darknessFollowsTheLight(GameTestHelper helper) {
		darkRoom(helper);
		ServerPlayer p = user(helper, 4.5, 2.0, 4.5, 0f, 0f);
		pump(helper, p);
		helper.runAfterDelay(40, () -> {
			helper.assertTrue(DarkShadowCombat.lightAt(p) <= 3, "pitch dark inside, light " + DarkShadowCombat.lightAt(p));
			float d = DarkShadow.darkness(p);
			helper.assertTrue(d > 0.9f, "it feels the dark, " + d);
			helper.assertTrue(DarkShadow.size(d, false) > DarkShadow.size(0f, false) * 1.8f, "big in the dark");
			helper.assertTrue(DarkShadowCombat.strength(p) > 1.5f, "strong in the dark, " + DarkShadowCombat.strength(p));
			// a lamp right where it floats
			BlockPos at = BlockPos.containing(DarkShadowCombat.shadowPos(p)).above();
			helper.getLevel().setBlock(at, Blocks.GLOWSTONE.defaultBlockState(), 3);
		});
		helper.runAfterDelay(90, () -> {
			float d = DarkShadow.darkness(p);
			helper.assertTrue(d < 0.15f, "it shrinks in the light, " + d);
			helper.assertTrue(DarkShadowCombat.strength(p) < 0.6f, "weak in the light, " + DarkShadowCombat.strength(p));
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40)
	public void heldTorchIsLight(GameTestHelper helper) {
		darkRoom(helper);
		ServerPlayer p = user(helper, 4.5, 2.0, 4.5, 0f, 1f);
		helper.runAfterDelay(6, () -> {
			helper.assertTrue(DarkShadowCombat.lightAt(p) <= 3, "dark first, " + DarkShadowCombat.lightAt(p));
			p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.TORCH));
			int l = DarkShadowCombat.lightAt(p);
			helper.assertTrue(l >= DarkShadowConfig.get().light.calmLight, "a torch in hand lights it, " + l);
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 120)
	public void meterRisesInPitchDarkFallsInLight(GameTestHelper helper) {
		darkRoom(helper);
		ServerPlayer p = user(helper, 4.5, 2.0, 4.5, 40f, 1f);
		pump(helper, p);
		helper.runAfterDelay(30, () -> {
			float m = Quirks.meter(p);
			helper.assertTrue(m > 44f, "rose in the pitch dark, " + m);
			p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.LANTERN));
			Quirks.setMeter(p, 40f);
		});
		helper.runAfterDelay(60, () -> {
			float m = Quirks.meter(p);
			helper.assertTrue(m < 36f, "fell in the light, " + m);
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- Berserk

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 160)
	public void berserkHitsMobsAndItsUserButNeverKills(GameTestHelper helper) {
		darkRoom(helper);
		ServerPlayer p = user(helper, 4.5, 2.0, 2.5, 50f, 1f);
		p.setHealth(4f);
		Zombie z = mob(helper, EntityType.ZOMBIE, 4.5, 2.0, 5.5, 200);
		pumpFrom(helper, p, 6);
		helper.runAfterDelay(6, () -> Quirks.setMeter(p, 99.8f));
		helper.runAfterDelay(12, () -> {
			helper.assertTrue(DarkShadow.berserk(p), "full Shadow in the dark: berserk");
			helper.assertTrue(Quirks.overloaded(p), "the keys are locked");
		});
		helper.runAfterDelay(120, () -> {
			helper.assertTrue(z.getHealth() < 200f - DarkShadowConfig.get().berserk.damage, "it mauled the zombie, " + z.getHealth());
			helper.assertTrue(p.getHealth() < 4f, "it turned on its user too, " + p.getHealth());
			helper.assertTrue(p.isAlive() && p.getHealth() >= DarkShadowConfig.get().berserk.selfFloor - 0.01f, "never fatally, " + p.getHealth());
			helper.assertTrue(DarkShadow.berserk(p), "still berserk: no light yet");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void lightCalmsBerserk(GameTestHelper helper) {
		darkRoom(helper);
		ServerPlayer p = user(helper, 4.5, 2.0, 4.5, 50f, 1f);
		pumpFrom(helper, p, 3);
		helper.runAfterDelay(2, () -> {
			DarkShadowMoves.goBerserk(p, 1);
			helper.assertTrue(DarkShadow.berserk(p), "berserk");
		});
		helper.runAfterDelay(8, () -> {
			helper.assertTrue(DarkShadow.berserk(p), "still berserk in the dark");
			p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.TORCH));
		});
		helper.runAfterDelay(12, () -> {
			helper.assertFalse(DarkShadow.on(p, DarkShadow.V_BERSERK), "a torch in hand calms it");
			helper.assertFalse(Quirks.overloaded(p), "the keys work again");
			helper.assertTrue(Quirks.meter(p) <= DarkShadowConfig.get().berserk.afterMeter + 0.01f, "the meter settled, " + Quirks.meter(p));
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40)
	public void flareThroughTheKeysWhileBerserk(GameTestHelper helper) {
		darkRoom(helper);
		ServerPlayer p = user(helper, 4.5, 2.0, 4.5, 50f, 1f);
		helper.runAfterDelay(6, () -> flareSteps(helper, p));
	}

	private static void flareSteps(GameTestHelper helper, ServerPlayer p) {
		DarkShadowMoves.goBerserk(p, 2);
		p.setShiftKeyDown(false);
		QuirkAbilityManager.handle(p, AbilitySlot.SLOT_1, true);
		QuirkAbilityManager.handle(p, AbilitySlot.SLOT_1, false);
		helper.assertTrue(Quirks.ready(p, DarkShadowConfig.STRIKE), "Strike refused while berserk");
		p.setShiftKeyDown(true);
		QuirkAbilityManager.handle(p, AbilitySlot.SLOT_6, true);
		QuirkAbilityManager.handle(p, AbilitySlot.SLOT_6, false);
		helper.assertTrue(DarkShadow.berserk(p), "no light source: the flare fails, still berserk");
		p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.TORCH));
		QuirkAbilityManager.handle(p, AbilitySlot.SLOT_6, true);
		helper.assertFalse(DarkShadow.on(p, DarkShadow.V_BERSERK), "the flare ends Berserk");
		helper.assertTrue(DarkShadow.running(p, DarkShadow.V_FLARE), "it shines");
		helper.assertTrue(Quirks.meter(p) <= mv().flareMeter + 0.01f, "Shadow drops");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40)
	public void berserkOnPurposeOnlyInTheDark(GameTestHelper helper) {
		darkRoom(helper);
		ServerPlayer p = user(helper, 4.5, 2.0, 4.5, 20f, 1f);
		helper.runAfterDelay(6, () -> unleashSteps(helper, p));
	}

	private static void unleashSteps(GameTestHelper helper, ServerPlayer p) {
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.TORCH));
		helper.assertFalse(DarkShadowMoves.unleash(p), "refused with a light in hand");
		p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		helper.assertTrue(DarkShadowMoves.unleash(p), "lets loose in the dark");
		helper.assertTrue(DarkShadow.val(p, DarkShadow.V_BERSERK) == 2L && DarkShadow.berserk(p), "on purpose");
		helper.succeed();
	}

	// ---------------------------------------------------------------- R

	@GameTest(template = EMPTY_STRUCTURE)
	public void shadowStrikeReachAndDamageFollowTheDark(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = user(helper, 4.5, 2.0, 0.2, 0f, 1f);
		Zombie z = mob(helper, EntityType.ZOMBIE, 4.5, 2.0, 7.4, 100);
		LivingEntity hit = DarkShadowMoves.strike(p);
		helper.assertTrue(hit == z, "7 blocks in the dark, hit " + hit);
		float want = mv().strikeDamage * DarkShadowConfig.get().light.strengthMax;
		helper.assertTrue(Math.abs((100f - z.getHealth()) - want) < EPS, "8 x 1.7, took " + (100f - z.getHealth()));
		helper.assertTrue(DarkShadow.reach(p) != null, "the reach point is synced for the model");
		Quirks.edit(p, n -> n.abilityReadyAt.clear());
		Quirks.setVal(p, DarkShadow.V_DARK, 0L);
		helper.assertTrue(DarkShadowMoves.strike(p) == null, "too far in daylight");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void darkFangBitesFourTimes(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = user(helper, 4.5, 2.0, 1.5, 0f, 1f);
		Zombie z = mob(helper, EntityType.ZOMBIE, 4.5, 2.0, 5.5, 200);
		pump(helper, p);
		helper.runAfterDelay(1, () -> helper.assertTrue(DarkShadowMoves.fang(p), "a target"));
		helper.runAfterDelay(30, () -> {
			float taken = 200f - z.getHealth();
			helper.assertTrue(taken > mv().fangDamage * DarkShadowConfig.get().light.strengthMin * (mv().fangBites - 0.5f), "four bites, took " + taken);
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- G

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void shadowGrabLiftsAndSlams(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = user(helper, 4.5, 2.0, 0.5, 0f, 1f);
		Zombie z = mob(helper, EntityType.ZOMBIE, 4.5, 2.0, 7.0, 200);
		double y0 = z.getY();
		LivingEntity got = DarkShadowMoves.grab(p);
		helper.assertTrue(got == z, "grabbed " + got);
		helper.onEachTick(() -> Quirks.tick(p));
		helper.runAfterDelay(mv().grabPullTicks, () -> helper.assertTrue(z.getY() > y0 + 1.5, "hoisted, y " + (z.getY() - y0)));
		helper.runAfterDelay(mv().grabPullTicks + mv().grabSlamTicks + 4, () -> {
			helper.assertTrue(Math.abs(z.getY() - y0) < 0.6, "slammed back down, dy " + (z.getY() - y0));
			float taken = 200f - z.getHealth();
			helper.assertTrue(taken > mv().grabSlamDamage * DarkShadowConfig.get().light.strengthMin, "the slam hurt, took " + taken);
			helper.assertFalse(DarkShadow.on(p, DarkShadow.V_GRAB), "let go");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void shadowShieldSoaksFromEverySide(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = user(helper, 4.5, 2.0, 4.5, 0f, 1f);
		Zombie behind = mob(helper, EntityType.ZOMBIE, 4.5, 2.0, 2.5, 20);
		DarkShadowMoves.shieldOn(p);
		float got = kit().modifyIncomingDamage(p, p.damageSources().mobAttack(behind), 10f);
		helper.assertTrue(Math.abs(got - 10f * (1f - mv().shieldBlockMax)) < 0.05f, "95% soaked from behind in the dark, " + got);
		DarkShadowMoves.shieldOff(p);
		helper.assertTrue(kit().modifyIncomingDamage(p, p.damageSources().mobAttack(behind), 10f) == 10f, "down: no help");
		helper.assertFalse(Quirks.ready(p, DarkShadowConfig.SHIELD), "cooldown after release");
		helper.succeed();
	}

	// ---------------------------------------------------------------- X

	@GameTest(template = EMPTY_STRUCTURE)
	public void abyssalBlackAnkhWingsAndArmour(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = user(helper, 4.5, 2.0, 4.5, 0f, 1f);
		p.setOnGround(true);
		double armour = p.getAttributeValue(Attributes.ARMOR);
		helper.assertTrue(DarkShadowMoves.toggleAbyssal(p), "the wings open");
		helper.assertTrue(DarkShadow.ankh(p) && DarkShadow.abyssal(p), "Black Ankh comes with them");
		helper.assertTrue(p.getAttributeValue(Attributes.ARMOR) > armour + 5, "armoured");
		helper.assertTrue(p.getDeltaMovement().y > 0.5, "a wing beat lifts you, " + p.getDeltaMovement().y);
		helper.assertFalse(DarkShadowMoves.toggleAbyssal(p), "folded again");
		helper.assertTrue(DarkShadow.ankh(p) && !DarkShadow.abyssal(p), "the armour stays");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void fetchBringsItems(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = user(helper, 4.5, 2.0, 0.5, 0f, 1f);
		p.setXRot(20f);
		Vec3 at = helper.absoluteVec(new Vec3(4.5, 2.1, 5.5));
		ItemEntity item = new ItemEntity(helper.getLevel(), at.x, at.y, at.z, new ItemStack(Items.DIAMOND));
		item.setDeltaMovement(Vec3.ZERO);
		helper.getLevel().addFreshEntity(item);
		helper.assertTrue(DarkShadowMoves.fetch(p) == 2, "it goes for the item");
		pump(helper, p);
		helper.runAfterDelay(mv().fetchTicks + 3, () -> {
			helper.assertTrue(item.distanceTo(p) < 2.0, "the item is at your feet, " + item.distanceTo(p));
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void fetchCarriesYouToABlock(GameTestHelper helper) {
		floor(helper);
		for (int y = 2; y <= 4; y++) {
			helper.getLevel().setBlock(helper.absolutePos(new BlockPos(4, y, 7)), Blocks.STONE.defaultBlockState(), 3);
		}
		ServerPlayer p = user(helper, 4.5, 2.0, 0.5, 0f, 1f);
		helper.assertTrue(DarkShadowMoves.fetch(p) == 1, "no items: it carries you");
		helper.assertTrue(DarkShadow.val(p, DarkShadow.V_FETCH) == 1L, "synced");
		helper.succeed();
	}

	// ---------------------------------------------------------------- Z

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void shadowScoutMarksMobs(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = user(helper, 4.5, 2.0, 0.5, 0f, 1f);
		Zombie z = mob(helper, EntityType.ZOMBIE, 5.5, 2.0, 7.0, 100);
		DarkShadowMoves.scout(p);
		pump(helper, p);
		helper.runAfterDelay(mv().scoutOutTicks + 3, () -> {
			helper.assertTrue(z.hasEffect(MobEffects.GLOWING), "marked");
			helper.assertTrue(DarkShadow.scoutTarget(p) != null, "the flight is synced");
		});
		helper.runAfterDelay(DarkShadowMoves.scoutTicks() + 3, () -> {
			helper.assertFalse(DarkShadow.on(p, DarkShadow.V_SCOUT), "home again");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void sunshineAnkhHitsAllAround(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = user(helper, 4.5, 2.0, 4.5, 0f, 1f);
		Zombie a = mob(helper, EntityType.ZOMBIE, 1.5, 2.0, 4.5, 100);
		Zombie b = mob(helper, EntityType.ZOMBIE, 7.0, 2.0, 4.5, 100);
		Zombie c = mob(helper, EntityType.ZOMBIE, 4.5, 2.0, 1.5, 100);
		helper.assertTrue(DarkShadowMoves.nova(p) == 3, "all three");
		float want = mv().novaDamage * DarkShadowConfig.get().light.strengthMax;
		helper.assertTrue(Math.abs((100f - a.getHealth()) - want) < EPS && b.getHealth() < 100f && c.getHealth() < 100f, "10 x 1.7 each");
		helper.succeed();
	}

	// ---------------------------------------------------------------- V

	@GameTest(template = EMPTY_STRUCTURE)
	public void blackAnkhArmourAndClawDash(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = user(helper, 4.5, 2.0, 1.5, 0f, 1f);
		double armour = p.getAttributeValue(Attributes.ARMOR);
		double reach = p.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE);
		helper.assertTrue(DarkShadowMoves.toggleAnkh(p), "on");
		helper.assertTrue(p.getAttributeValue(Attributes.ARMOR) >= armour + mv().ankhArmorMax - 0.5, "full armour in the dark");
		helper.assertTrue(p.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE) > reach + 0.5, "the claws reach further");
		p.setDeltaMovement(Vec3.ZERO);
		DarkShadowMoves.strike(p);
		helper.assertTrue(p.getDeltaMovement().z > 1.0, "R in Black Ankh is a dash, vz " + p.getDeltaMovement().z);
		helper.assertTrue(DarkShadow.running(p, DarkShadow.V_DASH), "dashing");
		helper.assertFalse(DarkShadowMoves.toggleAnkh(p), "off");
		helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.ARMOR) - armour) < 1e-6, "armour gone");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void recallTucksItAway(GameTestHelper helper) {
		darkRoom(helper);
		ServerPlayer p = user(helper, 4.5, 2.0, 4.5, 99.9f, 1f);
		p.setShiftKeyDown(true);
		QuirkAbilityManager.handle(p, AbilitySlot.SLOT_5, true); // Shift+V
		QuirkAbilityManager.handle(p, AbilitySlot.SLOT_5, false);
		helper.assertTrue(DarkShadow.recalled(p), "recalled");
		p.setShiftKeyDown(false);
		QuirkAbilityManager.handle(p, AbilitySlot.SLOT_4, true); // Z
		QuirkAbilityManager.handle(p, AbilitySlot.SLOT_4, false);
		helper.assertTrue(Quirks.ready(p, DarkShadowConfig.SCOUT), "no moves while recalled");
		pump(helper, p);
		helper.runAfterDelay(20, () -> {
			helper.assertFalse(DarkShadow.on(p, DarkShadow.V_BERSERK), "it can't go berserk while recalled");
			helper.assertTrue(Quirks.meter(p) < 90f, "the meter drains fast, " + Quirks.meter(p));
			helper.succeed();
		});
	}
}
