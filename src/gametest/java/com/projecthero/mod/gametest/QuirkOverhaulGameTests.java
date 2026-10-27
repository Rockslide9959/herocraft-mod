package com.projecthero.mod.gametest;

import java.util.List;

import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.power.p05.GeoTerrain;
import com.projecthero.mod.quirk.PlusUltra;
import com.projecthero.mod.quirk.Quirk;
import com.projecthero.mod.quirk.QuirkCombos;
import com.projecthero.mod.quirk.QuirkKits;
import com.projecthero.mod.quirk.Quirks;
import com.projecthero.mod.quirk.data.QuirkState;
import com.projecthero.mod.quirk.fx.QuirkFxEntity;
import com.projecthero.mod.quirk.meter.QuirkMeterSpec;
import com.projecthero.mod.quirk.meter.QuirkMeters;
import com.projecthero.mod.quirk.overhaul.Overhaul;
import com.projecthero.mod.quirk.overhaul.OverhaulConfig;
import com.projecthero.mod.quirk.overhaul.OverhaulMeter;
import com.projecthero.mod.quirk.overhaul.OverhaulMoves;
import com.projecthero.mod.quirk.overhaul.OverhaulRestore;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1 Overhaul revamp (Kai Chisaki): the key layout (tap / Shift / hold), Matter (fed by the ground, spent by building,
 * gained by disassembling, its bands sizing what he builds), the gloves coming off on their own, one test per move -- the
 * server effect and its number -- the survival uses (Mend, Disassembly mining, Restore), Total Disassembly and every
 * temporary block put back exactly (walls, pillar, pits, the surf's wake, the ground the ultimate lifts), and the boss rules.
 * Mock players are not ticked by the server, so tests that run over time drive the Quirk tick themselves ({@link #pump}).
 * The ground is three layers of stone (y 0..2); he stands on it at y 3, facing +Z.
 */
public class QuirkOverhaulGameTests implements FabricGameTest {
	private static final float EPS = 0.6f;

	// ---------------------------------------------------------------- helpers

	@SuppressWarnings("removal")
	private static ServerPlayer mock(GameTestHelper helper, double x, double y, double z, float pitch) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(x, y, z));
		p.moveTo(at.x, at.y, at.z, 0.0f, pitch);
		p.setYHeadRot(0f);
		p.setOnGround(true);
		try {
			java.lang.reflect.Field f = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
			f.setAccessible(true);
			f.setInt(p, 0);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
		return p;
	}

	/** Overhaul facing +Z, full Matter, every move ready. */
	private static ServerPlayer overhaul(GameTestHelper helper, double x, double z) {
		return overhaul(helper, x, z, 0f);
	}

	private static ServerPlayer overhaul(GameTestHelper helper, double x, double z, float pitch) {
		ServerPlayer p = mock(helper, x, 3.0, z, pitch);
		helper.assertTrue(Quirks.grant(p, Quirk.OVERHAUL), "Overhaul is granted");
		Quirks.edit(p, n -> {
			n.vals.put(Overhaul.V_INIT, 1L);
			n.abilityReadyAt.clear();
			n.animId = QuirkState.ANIM_NONE;
		});
		Quirks.setMeter(p, 100f);
		return p;
	}

	private static void pump(GameTestHelper helper, ServerPlayer p) {
		helper.onEachTick(() -> {
			p.tickCount++;
			p.setOnGround(true);
			Quirks.tick(p);
		});
	}

	private static void ground(GameTestHelper helper) {
		for (int x = 0; x <= 7; x++) {
			for (int z = 0; z <= 7; z++) {
				for (int y = 0; y <= 2; y++) {
					helper.getLevel().setBlock(helper.absolutePos(new BlockPos(x, y, z)), Blocks.STONE.defaultBlockState(), 3);
				}
			}
		}
	}

	private static <T extends Mob> T mob(GameTestHelper helper, EntityType<T> type, double x, double z, double health) {
		helper.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
		T m = type.create(helper.getLevel());
		Vec3 at = helper.absoluteVec(new Vec3(x, 3.0, z));
		m.moveTo(at.x, at.y, at.z, 180.0f, 0.0f);
		m.setNoAi(true);
		m.setOnGround(true);
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

	private static BlockPos abs(GameTestHelper helper, int x, int y, int z) {
		return helper.absolutePos(new BlockPos(x, y, z));
	}

	/** Nothing temporary or out of place left above the stone ground (y 3..7 all air, y 0..2 all stone). */
	private static void assertRestored(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		for (int x = 0; x <= 7; x++) {
			for (int z = 0; z <= 7; z++) {
				for (int y = 0; y <= 7; y++) {
					BlockPos pos = abs(helper, x, y, z);
					boolean air = level.getBlockState(pos).isAir();
					if (y <= 2) {
						helper.assertTrue(level.getBlockState(pos).is(Blocks.STONE), "the ground is whole at " + x + "," + y + "," + z);
					} else {
						helper.assertTrue(air, "nothing left standing at " + x + "," + y + "," + z + ": " + level.getBlockState(pos));
					}
					helper.assertFalse(GeoTerrain.isTemp(level, pos), "no ledger entry left at " + x + "," + y + "," + z);
				}
			}
		}
	}

	/** Toolkit effects spawned in the test box. */
	private static int fx(GameTestHelper helper) {
		return helper.getLevel().getEntitiesOfClass(QuirkFxEntity.class, new AABB(abs(helper, 0, 0, 0)).expandTowards(8, 8, 8).inflate(2)).size();
	}

	private static float taken(LivingEntity e) {
		return e.getMaxHealth() - e.getHealth();
	}

	// ---------------------------------------------------------------- the kit / Matter / the gloves

	@GameTest(template = EMPTY_STRUCTURE)
	public void overhaulKitFollowsTheDocsLayout(GameTestHelper helper) {
		var kit = QuirkKits.of(Quirk.OVERHAUL);
		helper.assertTrue(kit.idsOf(AbilitySlot.SLOT_1)[0].equals(OverhaulConfig.DISASSEMBLE) && kit.idsOf(AbilitySlot.SLOT_1)[1].equals(OverhaulConfig.DISARM),
				"R: Disassemble / Disarm");
		helper.assertTrue(kit.idsOf(AbilitySlot.SLOT_2)[0].equals(OverhaulConfig.SPIKE_WAVE) && kit.idsOf(AbilitySlot.SLOT_2)[1].equals(OverhaulConfig.SPIKE_FIELD),
				"G: Ground Spikes / Spike Field (hold: Thorn Forest)");
		helper.assertTrue(kit.idsOf(AbilitySlot.SLOT_3)[0].equals(OverhaulConfig.GROUND_SHIFT) && kit.idsOf(AbilitySlot.SLOT_3)[1].equals(OverhaulConfig.PILLAR),
				"X: the movement keys (hold: Terrain Surf)");
		helper.assertTrue(kit.idsOf(AbilitySlot.SLOT_4)[0].equals(OverhaulConfig.STRIKE) && kit.idsOf(AbilitySlot.SLOT_4)[1].equals(OverhaulConfig.PIT),
				"Z: Overhaul Strike / Disassemble Terrain");
		helper.assertTrue(kit.idsOf(AbilitySlot.SLOT_5)[0].equals(OverhaulConfig.REASSEMBLE) && kit.idsOf(AbilitySlot.SLOT_5)[1].equals(OverhaulConfig.FUSION),
				"V: Reassemble / Fusion (hold: Restore)");
		helper.assertTrue(kit.idsOf(AbilitySlot.SLOT_6)[0].equals(OverhaulConfig.SHRAPNEL) && kit.idsOf(AbilitySlot.SLOT_6)[1].equals(OverhaulConfig.WALL),
				"C: Shrapnel Burst / Wall Burst (hold at 100: the ultimate)");
		for (AbilitySlot s : AbilitySlot.values()) {
			for (String id : kit.idsOf(s)) {
				helper.assertFalse(id.equals(OverhaulConfig.GLOVES), "the Gloves toggle and its slot are gone");
				helper.assertTrue(kit.maxCooldown(id) > 0, "a cooldown on " + id);
			}
		}
		helper.assertTrue(kit.hasUltimate() && kit.maxCooldown(OverhaulConfig.THORN) > 0 && kit.maxCooldown(OverhaulConfig.RESTORE) > 0, "the holds");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void matterIsASpendsMeterWithTheDocsBands(GameTestHelper helper) {
		QuirkMeterSpec s = QuirkMeters.spec(Quirk.OVERHAUL);
		helper.assertTrue(s.direction == QuirkMeterSpec.Direction.SPENDS && s.sweetFrom == 25f && s.redFrom == 80f, "Starved 0-24, Stocked, Overflowing 80+");
		helper.assertTrue(OverhaulMoves.constructScale(10f) == OverhaulConfig.STARVED_SCALE, "Starved: constructs small");
		helper.assertTrue(OverhaulMoves.constructScale(50f) == 1f, "Stocked: normal");
		helper.assertTrue(OverhaulMoves.constructScale(90f) == OverhaulConfig.OVERFLOWING_SCALE && OverhaulConfig.OVERFLOWING_SCALE == 1.5f,
				"Overflowing: 1.5x");
		helper.assertTrue(Math.abs(OverhaulMoves.matterRegenPerTick(true, false) * 20f - 10f) < 0.01f, "10 a second on stone or earth");
		helper.assertTrue(Math.abs(OverhaulMoves.matterRegenPerTick(false, false) * 20f - 3f) < 0.01f, "3 on wood or in the air");
		helper.assertTrue(OverhaulMoves.matterRegenPerTick(true, true) < OverhaulMoves.matterRegenPerTick(true, false), "slower fused");
		helper.succeed();
	}

	/** Matter refills from the ground under his feet: fast on stone, slowly on planks. */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 80)
	public void matterRefillsFromTheGround(GameTestHelper helper) {
		ground(helper);
		for (int x = 5; x <= 7; x++) {
			for (int z = 0; z <= 2; z++) {
				helper.getLevel().setBlock(abs(helper, x, 2, z), Blocks.OAK_PLANKS.defaultBlockState(), 3);
			}
		}
		ServerPlayer stone = overhaul(helper, 1.5, 1.5);
		ServerPlayer wood = overhaul(helper, 6.5, 1.5);
		Quirks.setMeter(stone, 20f);
		Quirks.setMeter(wood, 20f);
		pump(helper, stone);
		pump(helper, wood);
		helper.runAfterDelay(40, () -> {
			float a = Quirks.meter(stone), b = Quirks.meter(wood);
			helper.assertTrue(Math.abs(a - 40f) <= 1.5f, "10 a second on stone: 20 -> " + a);
			helper.assertTrue(Math.abs(b - 26f) <= 1.0f, "3 a second on wood: 20 -> " + b);
			helper.succeed();
		});
	}

	/** Spikes / walls / Fusion SPEND Matter; the disassembling moves GAIN it; a kill gives +15. */
	@GameTest(template = EMPTY_STRUCTURE)
	public void buildingSpendsAndDisassemblingGains(GameTestHelper helper) {
		ground(helper);
		ServerPlayer p = overhaul(helper, 4.5, 1.5);
		Zombie z = mob(helper, EntityType.ZOMBIE, 4.5, 3.5, 100);
		Quirks.setMeter(p, 50f);
		helper.assertTrue(OverhaulMoves.disassemble(p), "Disassemble");
		helper.assertTrue(Math.abs(Quirks.meter(p) - (50f + OverhaulConfig.DISASSEMBLE_GAIN)) < 0.01f, "+5 Matter: " + Quirks.meter(p));
		helper.assertTrue(OverhaulMoves.spikeWave(p), "Ground Spikes");
		helper.assertTrue(Math.abs(Quirks.meter(p) - (55f - OverhaulConfig.SPIKE_WAVE_COST)) < 0.01f, "12 spent: " + Quirks.meter(p));
		helper.assertTrue(OverhaulMoves.shrapnel(p) >= 1, "Shrapnel Burst hit");
		helper.assertTrue(Math.abs(Quirks.meter(p) - (43f + OverhaulConfig.SHRAPNEL_GAIN)) < 0.01f, "+8 Matter: " + Quirks.meter(p));
		helper.assertTrue(z.isAlive(), "(still standing)");
		Quirks.setMeter(p, 10f);
		helper.assertFalse(OverhaulMoves.wall(p), "a wall you can't afford doesn't happen");
		helper.assertTrue(Quirks.meter(p) == 10f && !Quirks.overloaded(p), "nothing spent, no lockout");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void aKillGivesFifteenMatterAndBurstsIntoShards(GameTestHelper helper) {
		ground(helper);
		ServerPlayer p = overhaul(helper, 4.5, 1.5);
		Zombie a = mob(helper, EntityType.ZOMBIE, 4.5, 3.5, 10);
		Zombie b = mob(helper, EntityType.ZOMBIE, 6.0, 5.0, 100);
		Quirks.setMeter(p, 40f);
		int fx0 = fx(helper);
		helper.assertTrue(OverhaulMoves.disassemble(p), "the touch landed");
		helper.assertFalse(a.isAlive(), "the body came apart");
		helper.assertTrue(Math.abs(taken(b) - OverhaulConfig.DISASSEMBLE_BURST_DAMAGE) < EPS, "its pieces hit the neighbour for 6, " + taken(b));
		helper.assertTrue(Math.abs(Quirks.meter(p) - (40f + OverhaulConfig.DISASSEMBLE_GAIN + OverhaulConfig.MATTER_PER_KILL)) < 0.01f,
				"+5 for the touch, +15 for the kill: " + Quirks.meter(p));
		helper.assertTrue(fx(helper) > fx0 + 3, "the kill burst into shards (toolkit effects): " + (fx(helper) - fx0));
		helper.succeed();
	}

	/** The gloves are on by default; a move tugs them off for 8 s and bare hands disassemble on a punch. */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void theGlovesComeOffOnTheirOwn(GameTestHelper helper) {
		ground(helper);
		ServerPlayer p = overhaul(helper, 4.5, 1.5);
		Zombie z = mob(helper, EntityType.ZOMBIE, 4.5, 3.5, 100);
		helper.assertTrue(Overhaul.gloved(p), "gloves on to start");
		try {
			// a fully charged swing (mock players never tick their attack timer)
			java.lang.reflect.Field f = LivingEntity.class.getDeclaredField("attackStrengthTicker");
			f.setAccessible(true);
			f.setInt(p, 100);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
		helper.assertFalse(QuirkKits.of(Quirk.OVERHAUL).keyLocked(p, AbilitySlot.SLOT_1, false), "nothing is locked by the gloves");
		OverhaulMoves.bareTouch(p, z);
		pump(helper, p);
		helper.runAfterDelay(3, () -> {
			helper.assertTrue(z.getHealth() == 100f, "gloved: a punch is just a punch");
			helper.assertTrue(OverhaulMoves.disassemble(p), "a move works with the gloves on");
			helper.assertFalse(Overhaul.gloved(p), "and tugged them off");
			float hp = z.getHealth();
			OverhaulMoves.bareTouch(p, z);
			helper.runAfterDelay(3, () -> helper.assertTrue(hp - z.getHealth() > OverhaulConfig.TOUCH_PASSIVE_DAMAGE - EPS, "bare: +4 on the punch"));
		});
		helper.runAfterDelay(OverhaulConfig.BARE_TICKS + 12, () -> {
			helper.assertTrue(Overhaul.gloved(p), "back on after 8 s");
			helper.succeed();
		});
	}

	/** Germophobe: after a mob hits him, his next move within 3 s hits 20% harder (then it is spent). */
	@GameTest(template = EMPTY_STRUCTURE)
	public void germophobeHitsBackHarder(GameTestHelper helper) {
		ground(helper);
		ServerPlayer p = overhaul(helper, 4.5, 1.5);
		Zombie z = mob(helper, EntityType.ZOMBIE, 4.5, 3.5, 100);
		QuirkKits.of(Quirk.OVERHAUL).modifyIncomingDamage(p, helper.getLevel().damageSources().mobAttack(z), 3f);
		helper.assertTrue(OverhaulMoves.germophobe(p), "touched: he wants it gone");
		OverhaulMoves.disassemble(p);
		helper.assertTrue(Math.abs(taken(z) - OverhaulConfig.DISASSEMBLE_DAMAGE * OverhaulConfig.GERMOPHOBE_MULT) < EPS, "18 x 1.2, took " + taken(z));
		helper.assertFalse(OverhaulMoves.germophobe(p), "spent on that move");
		helper.succeed();
	}

	// ---------------------------------------------------------------- R

	@GameTest(template = EMPTY_STRUCTURE)
	public void disassembleHitsForEighteen(GameTestHelper helper) {
		ground(helper);
		ServerPlayer p = overhaul(helper, 4.5, 1.5);
		Zombie z = mob(helper, EntityType.ZOMBIE, 4.5, 3.5, 100);
		int fx0 = fx(helper);
		helper.assertTrue(OverhaulMoves.disassemble(p), "the touch landed");
		helper.assertTrue(Math.abs(taken(z) - OverhaulConfig.DISASSEMBLE_DAMAGE) < EPS, "18 damage, took " + taken(z));
		helper.assertTrue(Quirks.cooldownRemaining(p, OverhaulConfig.DISASSEMBLE) > 0, "cooldown set");
		helper.assertTrue(fx(helper) > fx0, "the body cracks (red ribbons) and bursts (shards)");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void disarmDestroysAMobsWeapon(GameTestHelper helper) {
		ground(helper);
		ServerPlayer p = overhaul(helper, 4.5, 1.5);
		Zombie z = mob(helper, EntityType.ZOMBIE, 4.5, 3.5, 100);
		z.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
		Quirks.setMeter(p, 50f);
		ItemStack taken = OverhaulMoves.disarm(p);
		helper.assertTrue(taken.is(Items.IRON_SWORD), "the sword came apart: " + taken);
		helper.assertTrue(z.getMainHandItem().isEmpty(), "its hand is empty");
		helper.assertTrue(helper.getLevel().getEntitiesOfClass(ItemEntity.class, z.getBoundingBox().inflate(4)).isEmpty(), "nothing dropped: it is gone");
		helper.assertTrue(Math.abs(Quirks.meter(p) - (50f + OverhaulConfig.DISARM_GAIN)) < 0.01f, "it gains a little Matter");
		Quirks.edit(p, n -> n.abilityReadyAt.clear());
		taken = OverhaulMoves.disarm(p);
		helper.assertTrue(taken.is(Items.CARVED_PUMPKIN), "empty-handed: a piece of its armour instead, " + taken);
		helper.succeed();
	}

	// ---------------------------------------------------------------- G

	/** Ground Spikes are toolkit meshes (no blocks: nothing to put back), and they hit. */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 80)
	public void groundSpikesAreJaggedMeshesThatHit(GameTestHelper helper) {
		ground(helper);
		ServerPlayer p = overhaul(helper, 4.5, 0.5);
		Zombie z = mob(helper, EntityType.ZOMBIE, 4.5, 4.5, 100);
		pump(helper, p);
		int fx0 = fx(helper);
		helper.assertTrue(OverhaulMoves.spikeWave(p), "the spikes start");
		helper.runAfterDelay(12, () -> {
			helper.assertTrue(Math.abs(taken(z) - OverhaulConfig.SPIKE_WAVE_DAMAGE) < EPS, "a spike hit for 10, took " + taken(z));
			helper.assertTrue(fx(helper) >= fx0 + 4, "spike meshes rose: " + (fx(helper) - fx0));
			helper.assertTrue(GeoTerrain.pending() == 0 || !GeoTerrain.isTemp(helper.getLevel(), abs(helper, 4, 3, 2)), "no blocks: meshes");
			assertRestored(helper);
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void spikeFieldHitsAllAround(GameTestHelper helper) {
		ground(helper);
		ServerPlayer p = overhaul(helper, 4.0, 4.0);
		Zombie a = mob(helper, EntityType.ZOMBIE, 4.0, 1.5, 100);
		Zombie b = mob(helper, EntityType.ZOMBIE, 6.5, 4.0, 100);
		Quirks.setMeter(p, 60f); // Stocked: normal size
		pump(helper, p);
		helper.assertTrue(OverhaulMoves.spikeField(p), "the field starts");
		helper.runAfterDelay(10, () -> {
			helper.assertTrue(Math.abs(taken(a) - OverhaulConfig.SPIKE_FIELD_DAMAGE) < EPS && Math.abs(taken(b) - OverhaulConfig.SPIKE_FIELD_DAMAGE) < EPS,
					"both hit for 9: " + taken(a) + ", " + taken(b));
			helper.assertTrue(p.getHealth() == p.getMaxHealth(), "never himself");
			helper.succeed();
		});
	}

	/** Hold G: past the tap it becomes the Thorn Forest aim; the release raises it -- 16 an impale, cover for 4 s. */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 120)
	public void holdGRaisesTheThornForest(GameTestHelper helper) {
		ground(helper);
		ServerPlayer p = overhaul(helper, 4.5, 0.5, 40f); // looking down the box at the ground
		Zombie z = mob(helper, EntityType.ZOMBIE, 4.5, 4.5, 100);
		var kit = QuirkKits.of(Quirk.OVERHAUL);
		pump(helper, p);
		kit.handle(p, AbilitySlot.SLOT_2, true, false); // G down
		helper.runAfterDelay(OverhaulConfig.HOLD_TICKS + 3, () -> {
			helper.assertTrue(Overhaul.thornAiming(p), "held past the tap: aiming the forest");
			helper.assertTrue(z.getHealth() == 100f, "nothing yet");
			kit.handle(p, AbilitySlot.SLOT_2, false, false); // G up: it rises
			helper.assertFalse(Overhaul.thornAiming(p), "fired");
			helper.assertTrue(Math.abs(Quirks.meter(p) - (100f - OverhaulConfig.THORN_COST)) < 1f, "40 Matter: " + Quirks.meter(p));
		});
		helper.runAfterDelay(OverhaulConfig.HOLD_TICKS + 3 + 2 * OverhaulConfig.THORN_WAVE_GAP + 3, () -> {
			helper.assertTrue(Math.abs(taken(z) - OverhaulConfig.THORN_DAMAGE) < EPS, "impaled for 16, took " + taken(z));
			helper.assertTrue(z.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "pinned");
			helper.assertTrue(kit.maxCooldown(OverhaulConfig.THORN) == OverhaulConfig.THORN_COOLDOWN
					&& Quirks.cooldownRemaining(p, OverhaulConfig.THORN) > 0, "a real cooldown");
			assertRestored(helper);
			helper.succeed();
		});
	}

	/** A quick tap of G is still Ground Spikes (fired on the release). */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40)
	public void aTapOfGIsStillGroundSpikes(GameTestHelper helper) {
		ground(helper);
		ServerPlayer p = overhaul(helper, 4.5, 0.5);
		var kit = QuirkKits.of(Quirk.OVERHAUL);
		pump(helper, p);
		kit.handle(p, AbilitySlot.SLOT_2, true, false);
		helper.runAfterDelay(2, () -> {
			kit.handle(p, AbilitySlot.SLOT_2, false, false);
			helper.assertFalse(Quirks.ready(p, OverhaulConfig.SPIKE_WAVE), "Ground Spikes fired on the release");
			helper.assertTrue(Quirks.ready(p, OverhaulConfig.THORN), "not the forest");
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- C

	@GameTest(template = EMPTY_STRUCTURE)
	public void shrapnelBurstIsFifteenPointBlankAndLessFurther(GameTestHelper helper) {
		ground(helper);
		ServerPlayer p = overhaul(helper, 4.5, 0.5);
		Zombie near = mob(helper, EntityType.ZOMBIE, 4.5, 2.5, 100);
		Zombie far = mob(helper, EntityType.ZOMBIE, 4.5, 7.4, 100);
		helper.assertTrue(OverhaulMoves.shrapnel(p) == 2, "both in the cone");
		helper.assertTrue(Math.abs(taken(near) - 15f) < EPS, "3 x 5 point blank, took " + taken(near));
		helper.assertTrue(taken(far) > 2f && taken(far) < 15f - EPS, "fewer shards further out, took " + taken(far));
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 260)
	public void wallBurstRisesAndCrumblesBack(GameTestHelper helper) {
		ground(helper);
		ServerLevel level = helper.getLevel();
		ServerPlayer p = overhaul(helper, 4.5, 1.5);
		Quirks.setMeter(p, 60f); // Stocked: the 5x3 wall
		pump(helper, p);
		helper.assertTrue(OverhaulMoves.wall(p), "the wall starts");
		helper.runAfterDelay(12, () -> {
			for (int x = 2; x <= 6; x++) {
				for (int y = 3; y <= 5; y++) {
					helper.assertTrue(level.getBlockState(abs(helper, x, y, 3)).is(Blocks.STONE), "wall block at " + x + "," + y);
				}
			}
		});
		helper.runAfterDelay(OverhaulConfig.WALL_TTL + 15, () -> {
			assertRestored(helper);
			helper.succeed();
		});
	}

	/** Overflowing builds 1.5x: the wall is 7 wide and 4 high. */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 260)
	public void overflowingMatterBuildsBigger(GameTestHelper helper) {
		ground(helper);
		ServerLevel level = helper.getLevel();
		ServerPlayer p = overhaul(helper, 3.5, 1.5);
		Quirks.setMeter(p, 100f);
		pump(helper, p);
		helper.assertTrue(OverhaulMoves.wall(p), "the wall starts");
		helper.runAfterDelay(14, () -> {
			helper.assertTrue(level.getBlockState(abs(helper, 0, 3, 3)).is(Blocks.STONE) && level.getBlockState(abs(helper, 6, 3, 3)).is(Blocks.STONE),
					"7 wide");
			helper.assertTrue(level.getBlockState(abs(helper, 3, 6, 3)).is(Blocks.STONE), "4 high");
		});
		helper.runAfterDelay(OverhaulConfig.WALL_TTL + 15, () -> {
			assertRestored(helper);
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void terrainIsRefusedWhenTerrainDamageIsOff(GameTestHelper helper) {
		ground(helper);
		ServerPlayer p = overhaul(helper, 4.5, 1.5);
		var cfg = com.projecthero.mod.hero.HeroConfig.get();
		boolean was = cfg.abilityTerrainDamage;
		cfg.abilityTerrainDamage = false;
		try {
			helper.assertTrue(OverhaulMoves.wall(p), "the move still plays");
			helper.assertTrue(GeoTerrain.pending() == 0 || !GeoTerrain.isTemp(helper.getLevel(), abs(helper, 4, 3, 3)), "but places no block");
		} finally {
			cfg.abilityTerrainDamage = was;
		}
		helper.succeed();
	}

	// ---------------------------------------------------------------- X (movement)

	/** Tap X: Ground Shift -- the floor carries him 8 blocks along his look. */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40)
	public void groundShiftCarriesHimAlong(GameTestHelper helper) {
		ground(helper);
		ServerPlayer p = overhaul(helper, 4.5, 1.5);
		var kit = QuirkKits.of(Quirk.OVERHAUL);
		pump(helper, p);
		kit.handle(p, AbilitySlot.SLOT_3, true, false);
		helper.runAfterDelay(2, () -> {
			kit.handle(p, AbilitySlot.SLOT_3, false, false); // a tap
			helper.assertTrue(Overhaul.running(p, Overhaul.V_SHIFT), "the floor is carrying him");
			helper.assertTrue(p.getDeltaMovement().z > 0.8, "along his look: " + p.getDeltaMovement());
			helper.assertFalse(OverhaulMoves.surfing(p), "a tap is not the surf");
			helper.assertTrue(Math.abs(Quirks.meter(p) - (100f - OverhaulConfig.GROUND_SHIFT_COST)) < 1.5f, "6 Matter");
			helper.succeed();
		});
	}

	/** Hold X: Terrain Surf runs for as long as X is held, drains Matter, ends on release. */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void holdXSurfsUntilReleased(GameTestHelper helper) {
		ground(helper);
		ServerPlayer p = overhaul(helper, 4.5, 4.5);
		var kit = QuirkKits.of(Quirk.OVERHAUL);
		pump(helper, p);
		kit.handle(p, AbilitySlot.SLOT_3, true, false); // X down
		helper.assertFalse(OverhaulMoves.surfing(p), "not yet: it could still be a tap");
		helper.runAfterDelay(OverhaulConfig.HOLD_TICKS + 2, () -> {
			helper.assertTrue(OverhaulMoves.surfing(p), "held: on the wave");
			helper.assertTrue(kit.active(p, AbilitySlot.SLOT_3), "the X box lights");
			float fall = kit.modifyIncomingDamage(p, helper.getLevel().damageSources().fall(), 6f);
			helper.assertTrue(fall == 0f, "no fall damage while surfing");
		});
		final int held = 60;
		helper.runAfterDelay(held, () -> {
			helper.assertTrue(OverhaulMoves.surfing(p), "still riding while X is held");
			helper.assertTrue(Quirks.meter(p) < 100f - OverhaulConfig.SURF_COST - 10f, "it drains Matter: " + Quirks.meter(p));
			kit.handle(p, AbilitySlot.SLOT_3, false, false); // X up
			helper.assertFalse(OverhaulMoves.surfing(p), "off the wave on release");
			helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.STEP_HEIGHT) - 0.6) < 1.0e-3, "the step assist goes with it");
		});
		helper.runAfterDelay(held + OverhaulConfig.SURF_SAFE_TICKS + OverhaulConfig.SURF_WAKE_TTL + 15, () -> {
			assertRestored(helper);
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 120)
	public void terrainSurfEndsWhenMatterRunsDry(GameTestHelper helper) {
		ground(helper);
		ServerPlayer p = overhaul(helper, 4.5, 4.5);
		Quirks.setMeter(p, OverhaulConfig.SURF_COST + 4f);
		pump(helper, p);
		helper.assertTrue(OverhaulMoves.surf(p), "on the wave");
		helper.runAfterDelay(16, () -> {
			helper.assertFalse(OverhaulMoves.surfing(p), "dry: off the wave");
			helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.STEP_HEIGHT) - 0.6) < 1.0e-3, "no step assist left behind");
		});
		helper.runAfterDelay(16 + OverhaulConfig.SURF_WAKE_TTL + 15, () -> {
			assertRestored(helper);
			helper.succeed();
		});
	}

	/** v0.16.7 (user): 2-block step assist while surfing -- the wave carries him up a 2-block rise (and not without it). */
	@GameTest(template = EMPTY_STRUCTURE)
	public void terrainSurfStepsUpTwoBlockRises(GameTestHelper helper) {
		ground(helper);
		ServerLevel level = helper.getLevel();
		for (int x = 2; x <= 7; x++) {
			for (int z = 6; z <= 7; z++) {
				level.setBlock(abs(helper, x, 3, z), Blocks.STONE.defaultBlockState(), 3);
				level.setBlock(abs(helper, x, 4, z), Blocks.STONE.defaultBlockState(), 3);
			}
		}
		ServerPlayer p = overhaul(helper, 4.5, 4.5);
		double y0 = p.getY();
		Vec3 push = new Vec3(0, -0.08, OverhaulConfig.SURF_SPEED);
		for (int i = 0; i < 4; i++) {
			p.setOnGround(true);
			p.move(net.minecraft.world.entity.MoverType.SELF, push);
		}
		helper.assertTrue(Math.abs(p.getY() - y0) < 0.01, "a 2-block rise stops him on foot: y " + (p.getY() - y0));
		p.teleportTo(p.getX(), y0, helper.absoluteVec(new Vec3(4.5, 3, 4.5)).z);
		p.setOnGround(true);
		helper.assertTrue(OverhaulMoves.surf(p), "on the wave");
		for (int i = 0; i < 4; i++) {
			p.setOnGround(true);
			p.move(net.minecraft.world.entity.MoverType.SELF, push);
		}
		helper.assertTrue(Math.abs(p.getY() - (y0 + 2.0)) < 0.01, "carried up onto the ledge: y +" + (p.getY() - y0));
		OverhaulMoves.endSurf(p);
		helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.STEP_HEIGHT) - 0.6) < 1.0e-3, "back to a player's own step");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 120)
	public void pillarLaunchFlingsHimAndCleansUp(GameTestHelper helper) {
		ground(helper);
		ServerLevel level = helper.getLevel();
		ServerPlayer p = overhaul(helper, 4.5, 4.5);
		pump(helper, p);
		helper.assertTrue(OverhaulMoves.pillar(p), "the pillar rises");
		helper.assertTrue(p.getDeltaMovement().y >= OverhaulConfig.PILLAR_UP - 0.01, "flung up: " + p.getDeltaMovement());
		p.teleportTo(p.getX(), p.getY() + 5, p.getZ());
		helper.runAfterDelay(6, () -> helper.assertTrue(level.getBlockState(abs(helper, 4, 3, 4)).is(Blocks.STONE), "the pillar's base"));
		helper.runAfterDelay(OverhaulConfig.PILLAR_TTL + 15, () -> {
			assertRestored(helper);
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- Z

	@GameTest(template = EMPTY_STRUCTURE)
	public void overhaulStrikeTouchesTheFirstEnemy(GameTestHelper helper) {
		ground(helper);
		ServerPlayer p = overhaul(helper, 4.5, 1.5);
		Zombie z = mob(helper, EntityType.ZOMBIE, 4.5, 2.8, 100);
		Quirks.setMeter(p, 50f);
		helper.assertTrue(OverhaulMoves.strike(p), "the dash starts");
		helper.assertTrue(Math.abs(taken(z) - OverhaulConfig.STRIKE_DAMAGE) < EPS, "touched for 14, " + taken(z));
		helper.assertTrue(Math.abs(Quirks.meter(p) - (50f + OverhaulConfig.STRIKE_GAIN)) < 0.01f, "free, +3 on the touch");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 160)
	public void disassembleTerrainPitsEveryoneAndClosesAgain(GameTestHelper helper) {
		ground(helper);
		ServerLevel level = helper.getLevel();
		ServerPlayer p = overhaul(helper, 4.5, 1.5, 20f);
		Zombie a = mob(helper, EntityType.ZOMBIE, 4.5, 5.5, 100);
		Zombie b = mob(helper, EntityType.ZOMBIE, 2.5, 5.5, 100);
		double y0 = a.getY();
		Quirks.setMeter(p, 50f);
		pump(helper, p);
		List<LivingEntity> caught = OverhaulMoves.pit(p);
		helper.assertTrue(caught.size() == 2, "both pitted, " + caught.size());
		helper.assertTrue(Math.abs(Quirks.meter(p) - (50f + OverhaulConfig.PIT_GAIN)) < 1f, "+10 Matter: " + Quirks.meter(p));
		helper.assertTrue(Math.abs(a.getY() - (y0 - OverhaulConfig.PIT_DEPTH)) < 0.05, "it dropped two blocks: " + (y0 - a.getY()));
		helper.assertTrue(Math.abs(taken(a) - OverhaulConfig.PIT_DAMAGE) < EPS, "6 damage, " + taken(a));
		helper.assertTrue(QuirkCombos.openWindow(p) == OverhaulMeter.BURIED_ALIVE, "Buried Alive's window opens");
		helper.runAfterDelay(8, () -> {
			helper.assertTrue(level.getBlockState(abs(helper, 4, 3, 5)).is(Blocks.STONE), "the lid closed over it");
			helper.assertTrue(a.isAlive() && !a.isInWall(), "trapped, not suffocating");
		});
		helper.runAfterDelay(OverhaulConfig.PIT_TICKS + 15, () -> {
			helper.assertTrue(a.getY() >= y0 - 0.01 && b.getY() >= y0 - 0.01, "put back on top");
			assertRestored(helper);
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void bossesAreCappedUnmovedAndNeverPitted(GameTestHelper helper) {
		ground(helper);
		ServerPlayer p = overhaul(helper, 4.5, 1.5);
		Zombie boss = mob(helper, EntityType.ZOMBIE, 4.5, 3.5, 1000);
		boss.setDeltaMovement(Vec3.ZERO);
		helper.assertTrue(OverhaulMoves.pit(p).isEmpty(), "a boss is never pitted");
		helper.assertTrue(Quirks.meter(p) == 100f, "nothing gained");
		Quirks.setVal(p, Overhaul.V_FUSION, helper.getLevel().getGameTime() + 100); // fused: the hardest touch
		helper.assertTrue(OverhaulMoves.disassemble(p), "the touch lands");
		float cap = boss.getMaxHealth() * OverhaulConfig.BOSS_MAX_FRACTION_PER_HIT;
		helper.assertTrue(taken(boss) <= cap + EPS, "capped at 10% a hit, took " + taken(boss));
		helper.assertTrue(boss.getDeltaMovement().horizontalDistance() < 1.0e-3, "no knockback: " + boss.getDeltaMovement());
		helper.assertTrue(OverhaulMoves.disarm(p).isEmpty(), "a boss is never disarmed");
		helper.succeed();
	}

	// ---------------------------------------------------------------- V (+ survival)

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40)
	public void reassembleHealsToFull(GameTestHelper helper) {
		ground(helper);
		ServerPlayer p = overhaul(helper, 2.5, 2.5);
		p.setHealth(5f);
		p.addEffect(new MobEffectInstance(MobEffects.POISON, 200, 1));
		p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 200, 0));
		LivingEntity body = OverhaulMoves.reassemble(p);
		helper.assertTrue(body == p, "himself (nobody else looked at)");
		helper.assertTrue(p.getHealth() == p.getMaxHealth(), "whole");
		helper.assertFalse(p.hasEffect(MobEffects.POISON), "the poison is gone");
		helper.assertTrue(p.hasEffect(MobEffects.MOVEMENT_SPEED), "good effects stay");
		helper.assertTrue(Math.abs(Quirks.meter(p) - (100f - OverhaulConfig.REASSEMBLE_COST)) < 0.01f, "30 Matter");
		helper.assertTrue(Quirks.cooldownRemaining(p, OverhaulConfig.REASSEMBLE) > OverhaulConfig.REASSEMBLE_COOLDOWN - 100, "a real cooldown (45 s)");
		helper.succeed();
	}

	/** Hold V: Restore -- the blocks explosions broke rebuild as a wave. */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void holdVRestoresTheBlastedGround(GameTestHelper helper) {
		ground(helper);
		ServerLevel level = helper.getLevel();
		ServerPlayer p = overhaul(helper, 2.5, 2.5);
		BlockPos hole = abs(helper, 5, 3, 5);
		OverhaulRestore.record(level, hole, Blocks.COBBLESTONE.defaultBlockState(), level.getGameTime());
		var kit = QuirkKits.of(Quirk.OVERHAUL);
		pump(helper, p);
		kit.handle(p, AbilitySlot.SLOT_5, true, false); // V down, held
		helper.runAfterDelay(OverhaulConfig.RESTORE_HOLD_TICKS + 2, () -> {
			helper.assertFalse(Quirks.ready(p, OverhaulConfig.RESTORE), "held a second: Restore ran");
			helper.assertTrue(Quirks.ready(p, OverhaulConfig.REASSEMBLE), "not Reassemble");
			kit.handle(p, AbilitySlot.SLOT_5, false, false);
			helper.assertTrue(Quirks.ready(p, OverhaulConfig.REASSEMBLE), "and the release doesn't fire the tap");
		});
		helper.runAfterDelay(OverhaulConfig.RESTORE_HOLD_TICKS + OverhaulConfig.RESTORE_WAVE_TICKS + 12, () -> {
			helper.assertTrue(level.getBlockState(hole).is(Blocks.COBBLESTONE), "the broken block is back");
			level.setBlock(hole, Blocks.AIR.defaultBlockState(), 3);
			helper.succeed();
		});
	}

	/** Survival: Mend -- Shift+V with a damaged tool in the off hand puts back 100 durability for 20 Matter. */
	@GameTest(template = EMPTY_STRUCTURE)
	public void mendRepairsTheOffHandTool(GameTestHelper helper) {
		ground(helper);
		ServerPlayer p = overhaul(helper, 2.5, 2.5);
		ItemStack pick = new ItemStack(Items.IRON_PICKAXE);
		pick.setDamageValue(180);
		p.setItemInHand(InteractionHand.OFF_HAND, pick);
		p.setShiftKeyDown(true);
		QuirkKits.of(Quirk.OVERHAUL).handle(p, AbilitySlot.SLOT_5, true, true); // Shift+V
		ItemStack after = p.getOffhandItem();
		helper.assertTrue(after.getDamageValue() == 180 - OverhaulConfig.MEND_DURABILITY, "+100 durability: damage " + after.getDamageValue());
		helper.assertTrue(Math.abs(Quirks.meter(p) - (100f - OverhaulConfig.MEND_COST)) < 0.01f, "20 Matter");
		helper.assertFalse(Overhaul.fused(p), "Mend, not Fusion");
		Quirks.edit(p, n -> n.abilityReadyAt.clear());
		after.setDamageValue(0);
		helper.assertFalse(OverhaulMoves.mend(p), "nothing to mend on a whole tool");
		helper.succeed();
	}

	/** Survival: Sneak + a bare-hand punch on stone takes a 3x3 of it apart into its drops. */
	@GameTest(template = EMPTY_STRUCTURE)
	public void disassemblyMiningBreaksAThreeByThreeIntoDrops(GameTestHelper helper) {
		ground(helper);
		ServerLevel level = helper.getLevel();
		ServerPlayer p = overhaul(helper, 1.5, 1.5);
		BlockPos hit = abs(helper, 4, 2, 4);
		int n = OverhaulMoves.mine(p, hit, Direction.UP);
		helper.assertTrue(n == 9, "a 3x3 came apart: " + n);
		for (int x = 3; x <= 5; x++) {
			for (int z = 3; z <= 5; z++) {
				helper.assertTrue(level.getBlockState(abs(helper, x, 2, z)).isAir(), "gone at " + x + "," + z);
			}
		}
		helper.assertTrue(level.getBlockState(abs(helper, 4, 1, 4)).is(Blocks.STONE), "only the face it was hit on");
		int drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(hit).inflate(3)).stream().mapToInt(e -> e.getItem().getCount()).sum();
		helper.assertTrue(drops >= 9, "into its drops (cobblestone): " + drops);
		helper.assertTrue(Math.abs(Quirks.meter(p) - (100f - OverhaulConfig.MINING_COST)) < 0.01f, "6 Matter");
		// only where he may build
		p.setGameMode(GameType.ADVENTURE);
		Quirks.edit(p, s -> s.abilityReadyAt.clear());
		helper.runAfterDelay(OverhaulConfig.MINING_COOLDOWN + 1, () -> {
			helper.assertTrue(OverhaulMoves.mine(p, abs(helper, 1, 2, 5), Direction.UP) == 0, "no building rights (adventure): nothing breaks");
			helper.assertTrue(level.getBlockState(abs(helper, 1, 2, 5)).is(Blocks.STONE), "untouched");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void fusionGrowsArmoursAndEnds(GameTestHelper helper) {
		ground(helper);
		ServerPlayer p = overhaul(helper, 4.5, 4.5);
		double armour = p.getAttributeValue(Attributes.ARMOR);
		pump(helper, p);
		helper.assertTrue(OverhaulMoves.fusion(p), "fused");
		helper.assertTrue(Overhaul.fused(p), "the Fusion timer runs");
		helper.assertTrue(p.getAttributeValue(Attributes.ARMOR) >= armour + OverhaulConfig.FUSION_ARMOR - 0.01, "stone armour");
		helper.assertTrue(p.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE) >= 3.0 + OverhaulConfig.FUSION_REACH_BONUS - 0.01, "extra reach");
		helper.runAfterDelay(OverhaulConfig.FUSION_GROW_TICKS + 4, () -> {
			AttributeModifier m = p.getAttribute(Attributes.SCALE).getModifier(OverhaulMoves.SCALE_ID);
			helper.assertTrue(m != null && Math.abs(m.amount() - OverhaulConfig.FUSION_SCALE) < 1.0e-3, "grown to full size: " + m);
			helper.assertTrue(OverhaulMoves.fusion(p), "pressed again: off");
			helper.assertFalse(Overhaul.fused(p), "ended");
			helper.assertTrue(Quirks.cooldownRemaining(p, OverhaulConfig.FUSION) > OverhaulConfig.FUSION_COOLDOWN - 5, "its cooldown starts at the end");
			helper.assertTrue(p.getAttributeValue(Attributes.ARMOR) < armour + 0.01, "the stone falls away");
		});
		helper.runAfterDelay(2 * OverhaulConfig.FUSION_GROW_TICKS + 12, () -> {
			helper.assertTrue(p.getAttribute(Attributes.SCALE).getModifier(OverhaulMoves.SCALE_ID) == null, "back to his own size");
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- Plus Ultra: Total Disassembly

	/**
	 * Total Disassembly: three waves of spikes roll out (12 each and a toss), the ground they lift breaks apart and is put
	 * back exactly behind them, he is fused and can't be knocked back while it runs, and it spends all his Matter.
	 */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 160)
	public void totalDisassemblyHitsThreeTimesAndReassemblesTheGround(GameTestHelper helper) {
		ground(helper);
		ServerLevel level = helper.getLevel();
		ServerPlayer p = overhaul(helper, 4.0, 4.0);
		Zombie z = mob(helper, EntityType.ZOMBIE, 4.0, 7.0, 100);
		pump(helper, p);
		OverhaulMoves.ultimate(p, 3.8); // kept inside the test's own 8x8 box
		helper.assertTrue(Overhaul.ultimate(p) && Overhaul.fused(p), "fused for the ultimate");
		helper.assertTrue(p.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) >= 1.0, "can't be knocked back");
		helper.runAfterDelay(OverhaulConfig.ULT_WAVES[0] + OverhaulConfig.ULT_WAVE_TICKS / 2 + 2, () -> {
			boolean opened = false;
			for (int x = 0; x <= 7 && !opened; x++) {
				for (int zz = 0; zz <= 7 && !opened; zz++) {
					opened = GeoTerrain.isTemp(level, abs(helper, x, 2, zz));
				}
			}
			helper.assertTrue(opened, "the first wave took the ground apart");
		});
		helper.runAfterDelay(OverhaulConfig.ULT_TICKS + 2, () -> {
			// he is fused through it (design: "during it he is fused"), so each 12-damage wave lands x FUSION_DAMAGE_MULT
			float wave = OverhaulConfig.ULT_DAMAGE * OverhaulConfig.FUSION_DAMAGE_MULT;
			helper.assertTrue(Math.abs(taken(z) - 3 * wave) < 3 * EPS, "three waves, 12 each x" + OverhaulConfig.FUSION_DAMAGE_MULT + " fused: took " + taken(z));
			helper.assertFalse(Overhaul.ultimate(p), "over");
			helper.assertTrue(Quirks.meter(p) <= 1f, "every scrap of Matter went into it: " + Quirks.meter(p));
			helper.assertTrue(Quirks.ready(p, OverhaulConfig.FUSION), "the ultimate's Fusion leaves no Fusion cooldown");
		});
		helper.runAfterDelay(OverhaulConfig.ULT_TICKS + 30, () -> {
			z.discard();
			assertRestored(helper);
			helper.succeed();
		});
	}

	/** The ultimate through the framework: a full gauge, hold C -> Total Disassembly (not the placeholder). */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40)
	public void holdingCAtAFullGaugeFiresTotalDisassembly(GameTestHelper helper) {
		ground(helper);
		ServerPlayer p = overhaul(helper, 4.0, 4.0);
		PlusUltra.set(p, 100f);
		PlusUltra.fire(p);
		helper.assertTrue(PlusUltra.gauge(p) == 0f && Overhaul.ultimate(p), "Total Disassembly runs");
		QuirkKits.of(Quirk.OVERHAUL).stopAll(p);
		helper.assertFalse(Overhaul.ultimate(p), "stopAll ends it");
		helper.succeed();
	}
}
