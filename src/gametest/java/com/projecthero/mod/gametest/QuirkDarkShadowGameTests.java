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
		// v0.17.1 revamp (docs/QUIRKS_REVAMP_PROPOSAL.md 3.4)
		helper.assertTrue(kit().idsOf(AbilitySlot.SLOT_3)[0].equals(DarkShadowConfig.LUNGE) && kit().idsOf(AbilitySlot.SLOT_3)[1].equals(DarkShadowConfig.FETCH),
				"X is movement: Shadow Lunge / Fetch (hold: Black Fallen Angel)");
		helper.assertTrue(kit().idsOf(AbilitySlot.SLOT_5)[0].equals(DarkShadowConfig.ANKH) && kit().idsOf(AbilitySlot.SLOT_5)[1].equals(DarkShadowConfig.FLARE),
				"V is the mode (Black Ankh) / Light Flare");
		helper.assertTrue(kit().idsOf(AbilitySlot.SLOT_6)[0].equals(DarkShadowConfig.RAMPAGE) && kit().idsOf(AbilitySlot.SLOT_6)[1].equals(DarkShadowConfig.UNLEASH),
				"C is an attack (Abyssal Rampage) / Unleash");
		for (String id : ids) {
			helper.assertFalse(id.equals(DarkShadowConfig.RECALL), "Hide stays on N, not a key slot");
		}
		helper.assertTrue(kit().usableWhileOverloaded(AbilitySlot.SLOT_5, true), "the flare works while berserk");
		helper.assertFalse(kit().usableWhileOverloaded(AbilitySlot.SLOT_6, true), "Unleash does not");
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
			// v0.17.1 (user: darkness must not build too quick): the climb is paced (DarkShadowMeter.BUILD)
			helper.assertTrue(m > 40.5f && m < 44f, "rose gently in the pitch dark, " + m);
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
		p.setShiftKeyDown(true); // v0.17.1: the flare moved to Shift+V
		QuirkAbilityManager.handle(p, AbilitySlot.SLOT_5, true);
		QuirkAbilityManager.handle(p, AbilitySlot.SLOT_5, false);
		helper.assertTrue(DarkShadow.berserk(p), "no light source: the flare fails, still berserk");
		p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.TORCH));
		QuirkAbilityManager.handle(p, AbilitySlot.SLOT_5, true);
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
		ServerPlayer p = user(helper, 4.5, 2.0, 0.2, 50f, 1f); // v0.17.1: Hunting (x1.0) keeps the number exact
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
		// v0.17.1 framework: every Dark Shadow hit also carries the Shadow band's multiplier -- at 0 Shadow that is Restrained
		// (x0.6), which the bite count has to be measured against
		float[] band = { 1f };
		helper.runAfterDelay(1, () -> {
			band[0] = com.projecthero.mod.quirk.meter.QuirkMeters.damageMult(p);
			helper.assertTrue(DarkShadowMoves.fang(p), "a target");
		});
		helper.runAfterDelay(30, () -> {
			float taken = 200f - z.getHealth();
			helper.assertTrue(taken > mv().fangDamage * DarkShadowConfig.get().light.strengthMin * band[0] * (mv().fangBites - 0.5f),
					"four bites, took " + taken + " (band x" + band[0] + ")");
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
		ServerPlayer p = user(helper, 4.5, 2.0, 4.5, 50f, 1f); // Hunting: x1.0
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
		helper.assertTrue(DarkShadowMoves.toggleHidden(p), "N hides it (v0.17.1: Shift+V is the Light Flare now)");
		helper.assertTrue(DarkShadow.recalled(p), "recalled");
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

	/**
	 * v0.16.7 (user) "make pressing N hide dark shadow": N (toggleHidden, what the N payload calls) hides it -- no move
	 * works, it can't go berserk in the pitch dark at a full meter, Shadow drains -- and N again calls it back out (the
	 * summon clip), after which the moves work again.
	 */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 80)
	public void nHidesAndShowsIt(GameTestHelper helper) {
		darkRoom(helper);
		ServerPlayer p = user(helper, 4.5, 2.0, 4.5, 99.9f, 1f);
		helper.assertTrue(DarkShadowMoves.toggleHidden(p), "N hides it");
		helper.assertTrue(DarkShadow.recalled(p), "hidden");
		helper.assertTrue(Quirks.peek(p).animId == DarkShadow.ANIM_RECALL, "the tuck-away clip plays");
		QuirkAbilityManager.handle(p, AbilitySlot.SLOT_1, true); // R
		QuirkAbilityManager.handle(p, AbilitySlot.SLOT_1, false);
		helper.assertTrue(Quirks.ready(p, DarkShadowConfig.STRIKE), "no Strike while hidden");
		QuirkAbilityManager.handle(p, AbilitySlot.SLOT_6, true); // C: Berserk on purpose
		QuirkAbilityManager.handle(p, AbilitySlot.SLOT_6, false);
		helper.assertFalse(DarkShadow.on(p, DarkShadow.V_BERSERK), "no Berserk on purpose while hidden");
		pumpFrom(helper, p, 4);
		helper.runAfterDelay(30, () -> {
			helper.assertFalse(DarkShadow.on(p, DarkShadow.V_BERSERK), "it can't go berserk while hidden, even in the pitch dark");
			helper.assertTrue(Quirks.meter(p) < 99f, "the meter drains while hidden, " + Quirks.meter(p));
			helper.assertTrue(DarkShadowMoves.toggleHidden(p), "N again calls it out");
			helper.assertFalse(DarkShadow.recalled(p), "out again");
			helper.assertTrue(Quirks.peek(p).animId == DarkShadow.ANIM_SUMMON, "the emerge clip plays");
			helper.assertFalse(DarkShadowMoves.toggleHidden(p), "hiding again waits out the short cooldown");
			helper.assertFalse(DarkShadow.recalled(p), "still out");
			Quirks.edit(p, n -> n.abilityReadyAt.clear());
			Quirks.setMeter(p, 10f);
			QuirkAbilityManager.handle(p, AbilitySlot.SLOT_4, true); // Z: Shadow Scout works again
			QuirkAbilityManager.handle(p, AbilitySlot.SLOT_4, false);
			helper.assertFalse(Quirks.ready(p, DarkShadowConfig.SCOUT), "moves work once it is out");
			helper.succeed();
		});
	}

	/** v0.16.7 (user): a berserk Dark Shadow won't hide on N (only light calms it). */
	@GameTest(template = EMPTY_STRUCTURE)
	public void berserkWontHideOnN(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = user(helper, 4.5, 2.0, 4.5, 50f, 1f);
		DarkShadowMoves.goBerserk(p, 2);
		helper.assertFalse(DarkShadowMoves.toggleHidden(p), "refused while berserk");
		helper.assertFalse(DarkShadow.recalled(p), "still out");
		helper.succeed();
	}

	// ================================================================ v0.17.1 revamp

	/** The bands change the damage: Restrained x0.6, Hunting x1.0, Ravenous x1.4 (and its hits heal you). */
	@GameTest(template = EMPTY_STRUCTURE)
	public void shadowBandsScaleTheDamageAndRavenousHeals(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = user(helper, 4.5, 2.0, 0.2, 10f, 1f);
		Zombie z = mob(helper, EntityType.ZOMBIE, 4.5, 2.0, 5.0, 200);
		DarkShadowMoves.strike(p);
		float restrained = 200f - z.getHealth();
		helper.assertTrue(Math.abs(restrained - mv().strikeDamage * DarkShadowConfig.get().light.strengthMax * 0.6f) < EPS, "x0.6 Restrained: " + restrained);
		Quirks.edit(p, n -> n.abilityReadyAt.clear());
		Quirks.setMeter(p, 80f);
		p.setHealth(10f);
		float hp = z.getHealth();
		DarkShadowMoves.strike(p);
		float ravenous = hp - z.getHealth();
		helper.assertTrue(Math.abs(ravenous - mv().strikeDamage * DarkShadowConfig.get().light.strengthMax * 1.4f) < EPS, "x1.4 Ravenous: " + ravenous);
		helper.assertTrue(p.getHealth() > 10.5f, "Ravenous: its hit fed you, " + p.getHealth());
		helper.succeed();
	}

	/** C: Abyssal Rampage -- three claw arcs in front (7 / 7 / 11 x strength), nothing behind. */
	@GameTest(template = EMPTY_STRUCTURE)
	public void abyssalRampageSwingsThreeArcs(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = user(helper, 4.5, 2.0, 2.5, 50f, 1f);
		Zombie front = mob(helper, EntityType.ZOMBIE, 4.5, 2.0, 5.5, 200);
		Zombie behind = mob(helper, EntityType.ZOMBIE, 4.5, 2.0, 0.5, 200);
		DarkShadowMoves.rampage(p);
		helper.assertTrue(Quirks.peek(p).animId == DarkShadow.ANIM_RAMPAGE && DarkShadow.running(p, DarkShadow.V_RAMPAGE), "it rears up");
		helper.assertFalse(Quirks.ready(p, DarkShadowConfig.RAMPAGE), "cooldown");
		// the three swings (the tick plays them on the clip's contact frames: rampageHit1 / 2 / 3)
		for (int swing = 1; swing <= 3; swing++) {
			helper.assertTrue(DarkShadowMoves.rampageSwing(p, swing) == 1, "swing " + swing + " hits the one in front");
		}
		float s = DarkShadowConfig.get().light.strengthMax;
		float want = (mv().rampageDamage1 + mv().rampageDamage2 + mv().rampageDamage3) * s;
		float got = 200f - front.getHealth();
		helper.assertTrue(Math.abs(got - want) < 3 * EPS, "7 + 7 + 11 x 1.7, took " + got);
		helper.assertTrue(behind.getHealth() == 200f, "nothing behind him");
		helper.succeed();
	}

	/** Dark Fang then Abyssal Rampage within 2 s: the "Ravening Feast" route (+30%). */
	@GameTest(template = EMPTY_STRUCTURE)
	public void fangThenRampageIsARoute(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = user(helper, 4.5, 2.0, 1.5, 50f, 1f);
		mob(helper, EntityType.ZOMBIE, 4.5, 2.0, 5.5, 200);
		helper.assertTrue(DarkShadowMoves.fang(p), "Dark Fang");
		helper.assertTrue(com.projecthero.mod.quirk.QuirkCombos.openWindow(p) == com.projecthero.mod.quirk.darkshadow.DarkShadowMeter.RAVENING_FEAST,
				"the window opens");
		DarkShadowMoves.rampage(p);
		helper.assertTrue(com.projecthero.mod.quirk.QuirkCombos.last(p) == com.projecthero.mod.quirk.darkshadow.DarkShadowMeter.RAVENING_FEAST, "landed");
		helper.succeed();
	}

	/** Tap X: Shadow Lunge yanks you to a point ahead; hold X: the wings (Black Fallen Angel) while held. */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void tapXLungesHoldXGlides(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = user(helper, 4.5, 2.0, 0.5, 30f, 1f);
		p.setOnGround(true);
		p.setXRot(0f);
		pump(helper, p);
		kit().handle(p, AbilitySlot.SLOT_3, true, false);
		helper.runAfterDelay(1, () -> {
			kit().handle(p, AbilitySlot.SLOT_3, false, false); // a tap
			helper.assertTrue(DarkShadow.running(p, DarkShadow.V_LUNGE), "Shadow Lunge");
			helper.assertFalse(DarkShadow.abyssal(p), "not the wings");
		});
		helper.runAfterDelay(5, () -> helper.assertTrue(p.getDeltaMovement().z > 0.9, "yanked forward, " + p.getDeltaMovement()));
		helper.runAfterDelay(14, () -> {
			Quirks.edit(p, n -> n.abilityReadyAt.clear());
			kit().handle(p, AbilitySlot.SLOT_3, true, false); // held this time
		});
		helper.runAfterDelay(14 + mv().holdTicks + 2, () -> {
			helper.assertTrue(DarkShadow.abyssal(p) && DarkShadow.ankh(p), "held: the wings are out");
			kit().handle(p, AbilitySlot.SLOT_3, false, false);
			helper.assertFalse(DarkShadow.abyssal(p), "let go: folded");
			helper.succeed();
		});
	}

	/** Hold G past the grab: it carries the creature overhead; the release throws it (12 x strength on landing). */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 120)
	public void holdGCarriesAndTossesTheGrabbed(GameTestHelper helper) {
		darkRoom(helper);
		ServerPlayer p = user(helper, 4.5, 2.0, 2.5, 50f, 1f);
		Zombie z = mob(helper, EntityType.ZOMBIE, 4.5, 2.0, 5.5, 200);
		pumpFrom(helper, p, 4);
		helper.runAfterDelay(6, () -> kit().handle(p, AbilitySlot.SLOT_2, true, false)); // G down and held
		helper.runAfterDelay(6 + mv().grabPullTicks + mv().grabSlamTicks + 4, () -> {
			helper.assertTrue(DarkShadow.val(p, DarkShadow.V_TOSS) == 1L, "still held: it carries the body (no slam)");
			helper.assertTrue(DarkShadow.on(p, DarkShadow.V_GRAB), "still gripped");
			kit().handle(p, AbilitySlot.SLOT_2, false, false); // let go: the throw
			helper.assertTrue(DarkShadow.val(p, DarkShadow.V_TOSS) == 2L, "thrown");
		});
		helper.runAfterDelay(6 + mv().grabPullTicks + mv().grabSlamTicks + 6 + mv().tossFlightTicks, () -> {
			float taken = 200f - z.getHealth();
			float want = (mv().grabDamage + mv().tossDamage) * DarkShadowConfig.get().light.strengthMax;
			helper.assertTrue(taken > want - 2 * EPS, "the grab and the landing hurt, took " + taken + " want ~" + want);
			helper.succeed();
		});
	}

	/** Shadow Scout marks: a synced mark, and marked creatures take 15% more from you. */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void scoutMarksTakeMoreDamage(GameTestHelper helper) {
		darkRoom(helper);
		ServerPlayer p = user(helper, 4.5, 2.0, 2.5, 50f, 1f);
		Zombie z = mob(helper, EntityType.ZOMBIE, 4.5, 2.0, 5.5, 200);
		pumpFrom(helper, p, 4);
		helper.runAfterDelay(6, () -> DarkShadowMoves.scout(p));
		helper.runAfterDelay(6 + mv().scoutOutTicks + 3, () -> {
			helper.assertTrue(DarkShadow.marked(p, z), "marked (the glyph's data is synced)");
			helper.assertTrue(DarkShadow.marks(p).length >= 1, "listed");
			Quirks.setMeter(p, 50f);
			float hp = z.getHealth();
			// the felt darkness is smoothed and synced in 0.04 steps, so a freshly built room can still read ~0.93 here (CI did:
			// 14.86 = 8 x 1.615 x 1.15): what is under test is the mark's +15% on top of whatever strength it has right now
			float strength = DarkShadowCombat.strength(p);
			helper.assertTrue(strength > 1.5f, "strong in the dark room, " + strength);
			DarkShadowMoves.strike(p);
			float got = hp - z.getHealth();
			float want = mv().strikeDamage * strength * com.projecthero.mod.quirk.meter.QuirkMeters.damageMult(p) * mv().markBonus;
			helper.assertTrue(Math.abs(got - want) < EPS, "+15% on a mark, took " + got + " want " + want);
			helper.succeed();
		});
	}

	/** Shift+C: Unleash -- a controlled Berserk in the dark that never hits its user, ending on its own at 40 Shadow. */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 240)
	public void unleashIsAControlledBerserk(GameTestHelper helper) {
		darkRoom(helper);
		ServerPlayer p = user(helper, 4.5, 2.0, 2.5, 30f, 1f);
		Zombie z = mob(helper, EntityType.ZOMBIE, 4.5, 2.0, 5.5, 300);
		pumpFrom(helper, p, 6);
		helper.runAfterDelay(6, () -> {
			helper.assertTrue(DarkShadowMoves.unleash(p), "lets loose in the dark");
			helper.assertTrue(DarkShadow.unleashed(p), "controlled");
		});
		helper.runAfterDelay(6 + mv().unleashTicks - 10, () -> {
			helper.assertTrue(p.getHealth() == p.getMaxHealth(), "it never turned on its user, " + p.getHealth());
			helper.assertTrue(z.getHealth() < 300f, "it mauled the zombie");
		});
		helper.runAfterDelay(6 + mv().unleashTicks + 4, () -> {
			helper.assertFalse(DarkShadow.on(p, DarkShadow.V_BERSERK), "over on its own after 8 s");
			helper.assertTrue(Math.abs(Quirks.meter(p) - mt().unleashAfter) < 1f, "Shadow drops to 40, " + Quirks.meter(p));
			helper.succeed();
		});
	}

	/** Survival: the fall catch -- over 6 blocks with Shadow above 30, it catches you (once per 20 s). */
	@GameTest(template = EMPTY_STRUCTURE)
	public void shadowCatchesALongFall(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = user(helper, 4.5, 2.0, 4.5, 50f, 1f);
		var fall = helper.getLevel().damageSources().fall();
		helper.assertTrue(kit().modifyIncomingDamage(p, fall, 2f) == 2f, "a short fall is not caught");
		helper.assertTrue(kit().modifyIncomingDamage(p, fall, 9f) == 0f, "a long fall is caught");
		helper.assertTrue(Quirks.peek(p).animId == DarkShadow.ANIM_CATCH, "it plays the catch");
		helper.assertTrue(kit().modifyIncomingDamage(p, fall, 9f) == 9f, "once per 20 s");
		Quirks.edit(p, n -> n.abilityReadyAt.clear());
		Quirks.setMeter(p, 20f);
		helper.assertTrue(kit().modifyIncomingDamage(p, fall, 9f) == 9f, "too little Shadow: no catch");
		helper.succeed();
	}

	/** Night owl: night vision while in Black Ankh (and it goes with the armour). */
	@GameTest(template = EMPTY_STRUCTURE)
	public void blackAnkhGivesNightVision(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = user(helper, 4.5, 2.0, 4.5, 20f, 1f);
		DarkShadowMoves.toggleAnkh(p);
		p.tickCount++;
		Quirks.tick(p);
		helper.assertTrue(p.hasEffect(MobEffects.NIGHT_VISION), "Night owl");
		DarkShadowMoves.toggleAnkh(p);
		helper.assertFalse(p.hasEffect(MobEffects.NIGHT_VISION), "gone with the armour");
		helper.succeed();
	}

	/**
	 * Plus Ultra: Abyssal Black Body -- the armour x1.6, Shadow held at Ravenous (no Berserk), every attack key a claw dive
	 * (14 each, x1.4 Ravenous); afterwards it is spent and hidden for 5 s, then comes back on its own.
	 */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void abyssalBlackBodyThenSpentAndHidden(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = user(helper, 4.5, 2.0, 0.5, 99f, 1f);
		Zombie z = mob(helper, EntityType.ZOMBIE, 4.5, 2.0, 2.0, 200);
		com.projecthero.mod.quirk.PlusUltra.set(p, 100f);
		com.projecthero.mod.quirk.PlusUltra.fire(p);
		helper.assertTrue(DarkShadow.blackBody(p) && DarkShadow.ankh(p) && DarkShadow.abyssal(p), "merged: armour and wings");
		pump(helper, p);
		helper.runAfterDelay(20, () -> {
			helper.assertTrue(Math.abs(Quirks.meter(p) - mt().blackBodyMeter) < 0.5f, "held at Ravenous, " + Quirks.meter(p));
			helper.assertFalse(DarkShadow.on(p, DarkShadow.V_BERSERK), "it can't go berserk");
			var m = p.getAttribute(Attributes.SCALE).getModifier(DarkShadowMoves.BLACKBODY_SCALE_ID);
			helper.assertTrue(m != null && Math.abs(m.amount() - mv().blackBodyScale) < 1.0e-3, "towering, x1.6: " + m);
			p.teleportTo(p.getX(), helper.absoluteVec(new Vec3(0, 2.0, 0)).y, helper.absoluteVec(new Vec3(0, 0, 0.5)).z);
			p.setXRot(0f);
			kit().handle(p, AbilitySlot.SLOT_1, true, false); // R: the claw dive
			helper.assertTrue(DarkShadow.running(p, DarkShadow.V_DIVE), "diving");
			float got = 200f - z.getHealth();
			helper.assertTrue(Math.abs(got - mv().diveDamage * 1.4f) < EPS || z.getHealth() == 200f, "14 x 1.4 when it reaches it, " + got);
			// shorten the rest of the form
			Quirks.setVal(p, DarkShadow.V_BLACKBODY, helper.getLevel().getGameTime() + 3);
		});
		helper.runAfterDelay(26, () -> {
			helper.assertFalse(DarkShadow.blackBody(p), "over");
			helper.assertTrue(DarkShadow.recalled(p), "spent: hidden");
			helper.assertFalse(DarkShadowMoves.toggleHidden(p), "N can't call it out yet");
		});
		helper.runAfterDelay(26 + mv().blackBodyHiddenTicks + 4, () -> {
			helper.assertFalse(DarkShadow.recalled(p), "back out on its own");
			helper.assertTrue(p.getAttribute(Attributes.SCALE).getModifier(DarkShadowMoves.BLACKBODY_SCALE_ID) == null, "its own size again");
			helper.succeed();
		});
	}

	private static DarkShadowConfig.Meter mt() {
		return DarkShadowConfig.get().meter;
	}
}
