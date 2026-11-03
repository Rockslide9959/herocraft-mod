package com.projecthero.mod.gametest;

import java.lang.reflect.Method;

import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.ExperimentalPowers;
import com.projecthero.mod.hero.HeroTiers;
import com.projecthero.mod.hero.PowerGrants;
import com.projecthero.mod.hero.Powers;
import com.projecthero.mod.hero.UniquePowers;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.OmnitrixAbilityManager;
import com.projecthero.mod.omnitrix.OmnitrixAttachments;
import com.projecthero.mod.omnitrix.OmnitrixConfig;
import com.projecthero.mod.omnitrix.OmnitrixEntities;
import com.projecthero.mod.omnitrix.OmnitrixMeteors;
import com.projecthero.mod.omnitrix.OmnitrixWorld;
import com.projecthero.mod.omnitrix.alien.AlienKit;
import com.projecthero.mod.omnitrix.alien.Aliens;
import com.projecthero.mod.omnitrix.alien.fourarms.FourArmsKit;
import com.projecthero.mod.omnitrix.alien.heatblast.HeatblastKit;
import com.projecthero.mod.omnitrix.data.OmnitrixState;
import com.projecthero.mod.omnitrix.entity.AlienProjectileEntity;
import com.projecthero.mod.omnitrix.entity.OmnitrixMeteorEntity;
import com.projecthero.mod.omnitrix.entity.OmnitrixPodEntity;
import com.projecthero.mod.omnitrix.item.OmnitrixItems;
import com.projecthero.mod.omnitrix.pod.OmnitrixPodBlock;
import com.projecthero.mod.omnitrix.pod.OmnitrixPodBlockEntity;
import com.projecthero.mod.omnitrix.pod.OmnitrixPodBlocks;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1 the Omnitrix: the lock (every grant path refused while it is worn), death keeps it, the Containment Pod's
 * calibration (an Enchanted Golden Apple eater ends on 4 HP; without one you die; stepping out fails; a totem does not save
 * you; success pops the item and clears the power), the Meteor Scanner, the pod's touch, the watch (R raises / lowers, the
 * scroll cycle, the slap transforms, N reverts (v0.17.1 user: was V), the 3-minute timeout, the 90 s recharge, the slap refused
 * during it), and Heatblast's
 * and Four Arms' moves, immunities and bodies. Mock players are not ticked by the server, so tests over time drive
 * {@link Omnitrix#tick} and the effect tick themselves.
 */
public class OmnitrixGameTests implements FabricGameTest {
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

	private static ServerPlayer wearer(GameTestHelper helper, double x, double y, double z) {
		ServerPlayer p = mock(helper, x, y, z);
		helper.assertTrue(Omnitrix.grant(p), "the Omnitrix goes on");
		return p;
	}

	private static void pump(GameTestHelper helper, ServerPlayer p) {
		helper.onEachTick(() -> {
			p.tickCount++;
			Omnitrix.tick(p);
		});
	}

	private static final Method TICK_EFFECTS;

	static {
		try {
			TICK_EFFECTS = LivingEntity.class.getDeclaredMethod("tickEffects");
			TICK_EFFECTS.setAccessible(true);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}

	private static void tickEffects(ServerPlayer p) {
		try {
			TICK_EFFECTS.invoke(p);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
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

	private static void floor(GameTestHelper helper) {
		for (int x = 0; x <= 7; x++) {
			for (int z = 0; z <= 7; z++) {
				helper.getLevel().setBlock(helper.absolutePos(new BlockPos(x, 1, z)), Blocks.STONE.defaultBlockState(), 3);
			}
		}
	}

	/** A fuelled Containment Pod at (x, 2, z) with its upper half, the block entity returned. */
	private static OmnitrixPodBlockEntity pod(GameTestHelper helper, int x, int z) {
		floor(helper);
		BlockPos lower = helper.absolutePos(new BlockPos(x, 2, z));
		var state = OmnitrixPodBlocks.POD.defaultBlockState().setValue(OmnitrixPodBlock.FACING, Direction.NORTH);
		helper.getLevel().setBlock(lower, state.setValue(OmnitrixPodBlock.HALF, DoubleBlockHalf.LOWER), 3);
		helper.getLevel().setBlock(lower.above(), state.setValue(OmnitrixPodBlock.HALF, DoubleBlockHalf.UPPER), 3);
		OmnitrixPodBlockEntity be = (OmnitrixPodBlockEntity) helper.getLevel().getBlockEntity(lower);
		be.addFuel(OmnitrixConfig.FUEL_PER_EXTRACTION);
		return be;
	}

	private static ServerPlayer inPod(GameTestHelper helper, OmnitrixPodBlockEntity be) {
		Vec3 c = be.centre();
		ServerPlayer p = mock(helper, 0, 0, 0);
		p.moveTo(c.x, c.y, c.z, 0f, 0f);
		Omnitrix.grant(p);
		p.getFoodData().setFoodLevel(17); // no natural healing (needs 18), no starving
		p.getFoodData().setSaturation(0f);
		return p;
	}

	// ================================================================ the lock

	/** v0.17.1: with the not-yet-public switch off, the admin grant refuses the watch and the guide search skips its chapter. */
	@GameTest(template = EMPTY_STRUCTURE)
	public void theReleaseSwitchHidesTheOmnitrix(GameTestHelper helper) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		int chapter = com.projecthero.mod.hero.guide.HeroPackGuide.chapters().indexOf(com.projecthero.mod.hero.guide.HeroPackGuide.omnitrixChapter());
		helper.assertTrue(com.projecthero.mod.hero.guide.HeroPackGuide.search("Omnitrix").stream().anyMatch(h -> h.chapter() == chapter), "found while on");
		com.projecthero.mod.omnitrix.OmnitrixFeature.forceForTests(false);
		try {
			helper.assertFalse(PowerGrants.grantHero(p, "omnitrix").ok(), "the grant is refused while off");
			helper.assertFalse(com.projecthero.mod.omnitrix.Omnitrix.hasPower(p), "no watch");
			helper.assertTrue(com.projecthero.mod.hero.guide.HeroPackGuide.search("Omnitrix").stream().noneMatch(h -> h.chapter() == chapter),
					"the guide search skips its chapter");
		} finally {
			com.projecthero.mod.omnitrix.OmnitrixFeature.forceForTests(null);
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void theLockRefusesEveryGrantPath(GameTestHelper helper) {
		ServerPlayer p = wearer(helper, 2.5, 2, 2.5);
		helper.assertTrue(HeroTiers.HERO_KEYS.contains("omnitrix") && PowerGrants.HERO_TIER_KEYS.contains("omnitrix"), "a Hero-Tier key");
		helper.assertTrue(HeroTiers.holdsHero(p, "omnitrix") && HeroTiers.heroCount(p) == 1, "held as the one Primary");
		// a hero grant (the admin grant without force, and the heroic serums go through it)
		helper.assertFalse(PowerGrants.grantHero(p, "hulk").ok(), "the admin / serum hero grant is refused");
		helper.assertFalse(com.projecthero.mod.hulk.Hulk.grant(p), "a hero's own grant routine is refused");
		helper.assertTrue(UniquePowers.refuse(p, "thor"), "every acquisition item's gate refuses (Mjolnir, the Arc Reactor...)");
		// mutations: the serum grant and the low-level grant
		var mutation = Powers.mutations().get(0);
		helper.assertFalse(PowerGrants.grantExperimental(p, mutation), "a mutation serum is refused");
		helper.assertFalse(ExperimentalPowers.grant(p, mutation), "mutation exposure / the stack command is refused");
		helper.assertTrue(Omnitrix.rejectsAny(p), "the power wheel's swap is refused");
		// the random serum keeps its bottle and the watch stays
		ItemStack serum = new ItemStack(com.projecthero.mod.hero.item.RandomPowerSerumItem.ANY_SERUM);
		ItemStack back = serum.getItem().finishUsingItem(serum, helper.getLevel(), p);
		helper.assertTrue(back.is(com.projecthero.mod.hero.item.RandomPowerSerumItem.ANY_SERUM), "the random serum is not used up");
		helper.assertTrue(Omnitrix.hasPower(p) && !com.projecthero.mod.hulk.Hulk.hasPower(p) && ExperimentalPowers.state(p).ownedPowers.isEmpty(),
				"still only the Omnitrix");
		// the Omnitrix itself is never refused (already worn: just false), and an operator's force passes
		helper.assertFalse(PowerGrants.missingHeroTiers(p).contains("omnitrix"), "no serum ever rolls the Omnitrix");
		PowerGrants.Result forced = UniquePowers.forced(() -> PowerGrants.grantHero(p, "hulk"));
		helper.assertTrue(forced.ok() && !Omnitrix.hasPower(p) && com.projecthero.mod.hulk.Hulk.hasPower(p), "force replaces it (operators only)");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void deathKeepsTheWatch(GameTestHelper helper) {
		ServerPlayer p = wearer(helper, 2.5, 2, 2.5);
		helper.assertTrue(OmnitrixAttachments.STATE.isPersistent() && OmnitrixAttachments.STATE.copyOnDeath(), "saved and copied on death");
		Omnitrix.transform(p);
		helper.assertTrue(Omnitrix.transformed(p), "transformed");
		p.hurt(helper.getLevel().damageSources().genericKill(), 1000f);
		helper.assertTrue(p.isDeadOrDying(), "dead");
		helper.assertTrue(Omnitrix.hasPower(p) && !Omnitrix.transformed(p), "dead: back to human, the watch still on");
		helper.succeed();
	}

	// ================================================================ the Containment Pod

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 1000)
	public void extractionLeavesAGoldenAppleEaterOnTwoHearts(GameTestHelper helper) {
		OmnitrixPodBlockEntity be = pod(helper, 3, 3);
		ServerPlayer p = inPod(helper, be);
		ItemStack apple = new ItemStack(Items.ENCHANTED_GOLDEN_APPLE);
		apple.getItem().finishUsingItem(apple, helper.getLevel(), p);
		helper.assertTrue(p.getAbsorptionAmount() == 16f && p.hasEffect(net.minecraft.world.effect.MobEffects.REGENERATION), "Absorption IV + Regeneration II");
		helper.assertTrue(Math.abs(OmnitrixConfig.extractionTotal() - 47f) < 1.0e-3f, "the schedule totals 47");
		helper.assertTrue(be.start(p), "the extraction starts");
		float[] minHp = { 99f };
		helper.onEachTick(() -> {
			if (!p.isDeadOrDying()) {
				tickEffects(p);
				if (be.running()) {
					minHp[0] = Math.min(minHp[0], p.getHealth());
				}
			}
		});
		helper.runAfterDelay(OmnitrixConfig.EXTRACTION_TICKS + 20, () -> {
			helper.assertFalse(p.isDeadOrDying(), "the apple eater survives");
			float hp = p.getHealth();
			System.out.println("[omnitrix-calibration] end HP " + hp + ", lowest " + minHp[0]);
			helper.assertTrue(hp >= 3f && hp <= 5f, "ends on 2 hearts (4 +/- 1), got " + hp);
			helper.assertFalse(Omnitrix.hasPower(p), "the power is gone");
			helper.assertTrue(p.getAttached(OmnitrixAttachments.EXTRACTION) == null, "no extraction left");
			var items = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(be.getBlockPos()).inflate(4),
					e -> e.getItem().is(OmnitrixItems.OMNITRIX));
			helper.assertTrue(items.size() == 1, "the Omnitrix popped off as an item");
			// whoever puts it on next is locked in the same way
			ServerPlayer next = mock(helper, 1.5, 2, 1.5);
			ItemStack watch = items.get(0).getItem().copy();
			next.setItemInHand(InteractionHand.MAIN_HAND, watch);
			watch.use(helper.getLevel(), next, InteractionHand.MAIN_HAND);
			helper.assertTrue(Omnitrix.hasPower(next) && next.getMainHandItem().isEmpty(), "the next wearer has it");
			helper.assertTrue(PowerGrants.grantHero(next, "hulk").ok() == false, "and is locked in");
			items.get(0).discard();
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void extractionKillsAPlayerWithoutTheAppleAndTheWatchStays(GameTestHelper helper) {
		OmnitrixPodBlockEntity be = pod(helper, 3, 3);
		ServerPlayer p = inPod(helper, be);
		be.start(p);
		helper.runAfterDelay(40, () -> {
			helper.assertTrue(p.isDeadOrDying(), "20 HP and no apple: dead");
			helper.assertTrue(Omnitrix.hasPower(p), "the Omnitrix stays on");
			helper.assertFalse(be.running(), "the extraction failed");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void aTotemDoesNotSaveYou(GameTestHelper helper) {
		OmnitrixPodBlockEntity be = pod(helper, 3, 3);
		ServerPlayer p = inPod(helper, be);
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.TOTEM_OF_UNDYING));
		be.start(p);
		helper.runAfterDelay(40, () -> {
			helper.assertTrue(p.isDeadOrDying(), "the totem did not save them");
			helper.assertTrue(p.getStats().getValue(Stats.ITEM_USED.get(Items.TOTEM_OF_UNDYING)) == 0, "the totem never popped");
			helper.assertTrue(Omnitrix.hasPower(p) && !be.running(), "failed, the watch still on");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void steppingOutFailsWithASpike(GameTestHelper helper) {
		OmnitrixPodBlockEntity be = pod(helper, 3, 3);
		ServerPlayer p = inPod(helper, be);
		p.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);
		p.setHealth(100f);
		be.start(p);
		helper.runAfterDelay(30, () -> {
			float before = p.getHealth();
			Vec3 c = be.centre();
			p.moveTo(c.x + 2.5, c.y, c.z, 0f, 0f);
			helper.runAfterDelay(3, () -> {
				helper.assertFalse(be.running(), "stepping out fails it");
				helper.assertTrue(Omnitrix.hasPower(p), "the watch stays");
				helper.assertTrue(before - p.getHealth() >= OmnitrixConfig.STEP_OUT_DAMAGE - 0.01f, "the field lashed out");
				helper.assertTrue(p.getAttached(OmnitrixAttachments.EXTRACTION) == null, "no self-destruct HUD left");
				helper.succeed();
			});
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void thePodNeedsFuelAndAHuman(GameTestHelper helper) {
		OmnitrixPodBlockEntity be = pod(helper, 3, 3);
		ServerPlayer p = inPod(helper, be);
		Omnitrix.transform(p);
		helper.assertFalse(be.tryStart(p), "an alien cannot start it");
		Omnitrix.revert(p, Omnitrix.Reason.REVOKE);
		helper.assertTrue(be.tryStart(p), "a human inside with fuel can");
		helper.assertTrue(be.fuel() == 0, "it burned the fuel");
		helper.assertFalse(Omnitrix.activate(p), "the watch cannot be raised while the pod holds it");
		helper.succeed();
	}

	// ================================================================ getting it

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void theMeteorScannerDropsTheMeteorWithin50BlocksOnTheSurface(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = mock(helper, 3.5, 2, 3.5);
		Vec3 at = OmnitrixMeteors.scannerDrop(p);
		helper.assertTrue(at != null, "a landing spot was found");
		double dx = at.x - p.getX(), dz = at.z - p.getZ();
		helper.assertTrue(Math.sqrt(dx * dx + dz * dz) <= OmnitrixConfig.SCANNER_RADIUS, "within 50 blocks");
		BlockPos top = helper.getLevel().getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(at));
		helper.assertTrue(top.getY() == BlockPos.containing(at).getY(), "on the surface");
		var meteors = helper.getLevel().getEntitiesOfClass(OmnitrixMeteorEntity.class, new AABB(at, at).inflate(160, 320, 160));
		helper.assertTrue(meteors.size() == 1, "one meteor is falling");
		// a second scan refuses while it is active
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(OmnitrixItems.METEOR_SCANNER));
		p.getMainHandItem().use(helper.getLevel(), p, InteractionHand.MAIN_HAND);
		helper.assertTrue(p.getMainHandItem().is(OmnitrixItems.METEOR_SCANNER), "the second scanner is kept (one already falling)");
		meteors.forEach(m -> m.discard());
		OmnitrixWorld.clearLoose(helper.getLevel().getServer());
		// and a scan with nothing nearby is consumed
		p.getMainHandItem().use(helper.getLevel(), p, InteractionHand.MAIN_HAND);
		helper.assertTrue(p.getMainHandItem().isEmpty(), "used up");
		helper.getLevel().getEntitiesOfClass(OmnitrixMeteorEntity.class, p.getBoundingBox().inflate(160, 320, 160)).forEach(m -> m.discard());
		OmnitrixWorld.clearLoose(helper.getLevel().getServer());
		helper.assertTrue(helper.getLevel().getRecipeManager().byKey(com.projecthero.mod.ProjectHeroMod.id("meteor_scanner")).isPresent(),
				"the scanner has a recipe");
		helper.succeed();
	}

	// ================================================================ the watch

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void vRaisesTheWatchScrollTurnsItAndTheSlapTransforms(GameTestHelper helper) {
		ServerPlayer p = wearer(helper, 2.5, 2, 2.5);
		pump(helper, p);
		OmnitrixAbilityManager.handle(p, AbilitySlot.SLOT_1, true);
		OmnitrixAbilityManager.handle(p, AbilitySlot.SLOT_1, false);
		helper.assertTrue(Omnitrix.selecting(p), "R raises the watch (v0.17.1: was V)");
		OmnitrixAbilityManager.handle(p, AbilitySlot.SLOT_1, true);
		OmnitrixAbilityManager.handle(p, AbilitySlot.SLOT_1, false);
		helper.assertFalse(Omnitrix.selecting(p) || Omnitrix.transformed(p), "R again lowers it (no transformation, no cost)");
		helper.assertTrue(Omnitrix.rechargeLeft(p) == 0, "lowering costs nothing");
		Omnitrix.activate(p);
		String first = Omnitrix.state(p).selected;
		// v0.17.1: the right hand presses the side button at PRESS_TICKS, the core is up at CORE_UP_TICKS -- no turning before
		helper.assertTrue(Omnitrix.PRESS_TICKS < Omnitrix.CORE_UP_TICKS && Omnitrix.CORE_UP_TICKS < 20, "the activation is well under 1 s");
		helper.assertFalse(Omnitrix.cycle(p, 1), "the dial can't turn before the core is up");
		helper.assertTrue(Omnitrix.state(p).selected.equals(first), "the early scroll changed nothing");
		Omnitrix.finishRaise(p);
		helper.assertTrue(Omnitrix.cycle(p, 1), "the scroll wheel turns the dial");
		String second = Omnitrix.state(p).selected;
		helper.assertTrue(!second.equals(first) && Aliens.playable(second), "to the next alien with a kit");
		// v0.17.1 (user): a 0.2 s cooldown -- a second turn in the same tick is refused
		helper.assertFalse(Omnitrix.cycle(p, 1), "the dial can't be turned again within 0.2 s");
		helper.assertTrue(Omnitrix.state(p).selected.equals(second), "the refused turn changed nothing");
		for (int k = 1; k < Aliens.playable().size(); k++) { // v0.17.1 wave A: however many aliens have kits by now
			Omnitrix.clearDialCooldown(p);
			Omnitrix.cycle(p, 1);
		}
		helper.assertTrue(Omnitrix.state(p).selected.equals(first), "coming-soon aliens are skipped (a full turn of the playable ones wraps)");
		Omnitrix.clearDialCooldown(p);
		Omnitrix.cycle(p, -1);
		helper.assertTrue(!Omnitrix.state(p).selected.equals(first) && Aliens.playable(Omnitrix.state(p).selected), "and turns back");
		String picked = Omnitrix.state(p).selected;
		Omnitrix.noGlitch(p); // v0.17.1: the wrong-alien glitch has its own test
		helper.assertTrue(Omnitrix.slap(p), "the click slaps the core down");
		helper.runAfterDelay(Omnitrix.SLAP_TICKS + 2, () -> {
			helper.assertTrue(Omnitrix.transformed(p) && !Omnitrix.selecting(p), "the slap transformed");
			helper.assertTrue(Omnitrix.state(p).active.equals(picked), "into the selected alien");
			helper.assertTrue(Omnitrix.timeLeft(p) > OmnitrixConfig.TRANSFORM_TICKS - 20, "for 3 minutes");
			helper.succeed();
		});
	}

	/** v0.17.1: a click while the dial is still coming up is held, and lands the moment the core is up. */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void anEarlyClickWaitsForTheCore(GameTestHelper helper) {
		ServerPlayer p = wearer(helper, 2.5, 2, 2.5);
		pump(helper, p);
		helper.assertTrue(Omnitrix.activate(p), "R raises the watch");
		helper.assertTrue(Omnitrix.slap(p), "the early click is taken");
		// (the grant itself plays ANIM_SLAP -- the watch slamming on -- so look for a slap during this raise)
		helper.assertFalse(Omnitrix.state(p).animId == Omnitrix.ANIM_SLAP && Omnitrix.state(p).animStart > Omnitrix.state(p).selectStart,
				"but the core is not slapped yet");
		helper.assertFalse(Omnitrix.slap(p), "and a second click does not queue another");
		helper.runAfterDelay(Omnitrix.CORE_UP_TICKS - 3, () -> {
			helper.assertTrue(Omnitrix.selecting(p) && !Omnitrix.transformed(p), "still waiting for the core");
			helper.runAfterDelay(Omnitrix.SLAP_TICKS + 6, () -> {
				helper.assertTrue(Omnitrix.transformed(p), "the held slap landed once the core was up");
				helper.succeed();
			});
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 260)
	public void nRevertsTheRechargeRefusesAndTheTimeoutTimesOut(GameTestHelper helper) {
		ServerPlayer p = wearer(helper, 2.5, 2, 2.5);
		pump(helper, p);
		Omnitrix.transform(p);
		// v0.17.1 (user: "make the detransform keybind N"): N, through the router as the client sends it
		com.projecthero.mod.hero.AbilityRouter.handleInput(p, 8, true);
		com.projecthero.mod.hero.AbilityRouter.handleInput(p, 8, false);
		// v0.17.2 (user: "remove the ability to detransform by pressing N"): N does nothing without Master Control
		helper.assertTrue(Omnitrix.transformed(p) && !Omnitrix.poweringDown(p), "N no longer starts the power-down");
		// an early revert (as Master Control's N gives) still leaves the short recharge
		Omnitrix.revertEarly(p);
		helper.assertTrue(Omnitrix.transformed(p) && Omnitrix.poweringDown(p), "the power-down starts");
		helper.runAfterDelay(Omnitrix.POWER_DOWN_REVERT_TICK + 3, () -> afterTheEarlyRevert(helper, p));
	}

	private static void afterTheEarlyRevert(GameTestHelper helper, ServerPlayer p) {
		helper.assertFalse(Omnitrix.transformed(p), "the early revert lands");
		helper.assertTrue(Math.abs(Omnitrix.rechargeLeft(p) - OmnitrixConfig.EARLY_RECHARGE_TICKS) <= 4, "a short recharge");
		OmnitrixAbilityManager.handle(p, AbilitySlot.SLOT_1, true);
		OmnitrixAbilityManager.handle(p, AbilitySlot.SLOT_1, false);
		// v0.17.1 (user): the watch still comes up while it recharges, but the dial stays locked on the red face and the slap is
		// refused
		helper.assertTrue(Omnitrix.selecting(p), "R during the recharge still raises the watch");
		Omnitrix.finishRaise(p); // the right hand's button press is over (the core is up)
		String before = Omnitrix.state(p).selected;
		helper.assertFalse(Omnitrix.cycle(p, 1), "the dial does not turn while recharging");
		helper.assertTrue(before.equals(Omnitrix.state(p).selected), "no alien is selected while recharging");
		helper.assertFalse(Omnitrix.slap(p), "but the core will not go down while recharging");
		helper.assertTrue(Omnitrix.state(p).animId == Omnitrix.ANIM_DENIED, "the face flashes red instead");
		helper.assertFalse(Omnitrix.transform(p), "no transformation while recharging");
		Omnitrix.deactivate(p);
		// the 3-minute timeout: jump to its last tick
		OmnitrixState n = Omnitrix.state(p).copy();
		n.rechargeUntil = 0L;
		Omnitrix.save(p, n);
		Omnitrix.transform(p);
		OmnitrixState t = Omnitrix.state(p).copy();
		helper.assertTrue(t.endsAt - t.transformedAt == OmnitrixConfig.TRANSFORM_TICKS, "3 minutes");
		t.endsAt = helper.getLevel().getGameTime() + 2;
		Omnitrix.save(p, t);
		helper.runAfterDelay(4 + Omnitrix.POWER_DOWN_REVERT_TICK, () -> {
			helper.assertFalse(Omnitrix.transformed(p), "timed out");
			helper.assertTrue(Omnitrix.state(p).animId == Omnitrix.ANIM_TIMEOUT, "with the timeout flash");
			helper.assertTrue(Math.abs(Omnitrix.rechargeLeft(p) - OmnitrixConfig.RECHARGE_TICKS) <= 3, "then a 90 s recharge");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void theDialListsAllTenAndOnlyKitsArePlayable(GameTestHelper helper) {
		helper.assertTrue(Aliens.all().size() == 10, "the original ten are on the dial");
		helper.assertTrue(Aliens.playable(HeatblastKit.ID) && Aliens.playable(FourArmsKit.ID), "Heatblast and Four Arms are playable");
		ServerPlayer p = wearer(helper, 2.5, 2, 2.5);
		// v0.17.1 wave A: more aliens get kits as they are built -- whichever is still without one stays "coming soon"
		for (Aliens.Alien a : Aliens.all()) {
			if (!Aliens.playable(a.id())) {
				helper.assertFalse(Omnitrix.select(p, a.id()), "a coming-soon alien cannot be picked: " + a.id());
			}
		}
		helper.assertTrue(Omnitrix.select(p, FourArmsKit.ID) && Omnitrix.state(p).selected.equals(FourArmsKit.ID), "Four Arms picked");
		helper.assertTrue(Omnitrix.activate(p) && Omnitrix.state(p).selected.equals(FourArmsKit.ID), "the pick persists");
		helper.succeed();
	}

	// ================================================================ Heatblast

	private static ServerPlayer as(GameTestHelper helper, String alien) {
		ServerPlayer p = wearer(helper, 3.5, 2, 1.5);
		Omnitrix.select(p, alien);
		helper.assertTrue(Omnitrix.transform(p), "transformed into " + alien);
		return p;
	}

	/**
	 * v0.17.2 (user: "make heatblasts and pyrokinesis abilities actually spawn fire"): the Volcanic Eruption's vents keep burning
	 * (real fire blocks), and the shared ring helper lays a ring of fire.
	 */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 80)
	public void heatblastMovesLeaveRealFire(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = as(helper, HeatblastKit.ID);
		HeatblastKit kit = (HeatblastKit) Aliens.kit(HeatblastKit.ID);
		p.moveTo(p.getX(), p.getY(), p.getZ(), 0f, 0f);
		helper.assertTrue(kit.eruption(p), "C: Volcanic Eruption");
		pump(helper, p);
		helper.runAfterDelay(14, () -> {
			int fires = 0;
			for (int x = 0; x <= 7; x++) {
				for (int z = 0; z <= 7; z++) {
					if (helper.getLevel().getBlockState(helper.absolutePos(new BlockPos(x, 2, z))).is(Blocks.FIRE)) {
						fires++;
					}
				}
			}
			helper.assertTrue(fires >= 2, "the vents leave fire burning: " + fires);
			int ring = com.projecthero.mod.hero.power.p08.PyrokinesisHandlers.fireRing(p, helper.getLevel(), helper.absoluteVec(new Vec3(4, 2, 4)), 2.5, 40);
			helper.assertTrue(ring >= 6, "a ring of fire: " + ring);
			Omnitrix.revert(p, Omnitrix.Reason.REVOKE);
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void heatblastIsFireproofAndHasHisBody(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = as(helper, HeatblastKit.ID);
		helper.assertTrue(Math.abs(p.getMaxHealth() - 26f) < 0.01f, "26 health");
		helper.assertTrue(Math.abs(p.getHealth() - 26f) < 0.01f, "full health converted to full");
		float hp = p.getHealth();
		p.hurt(helper.getLevel().damageSources().inFire(), 6f);
		p.invulnerableTime = 0;
		p.hurt(helper.getLevel().damageSources().lava(), 6f);
		p.invulnerableTime = 0;
		p.hurt(helper.getLevel().damageSources().hotFloor(), 6f);
		helper.assertTrue(p.getHealth() == hp, "fire, lava and magma do nothing");
		p.invulnerableTime = 0;
		p.hurt(helper.getLevel().damageSources().magic(), 4f);
		helper.assertTrue(p.getHealth() < hp, "other damage still hurts");
		// health converts back by fraction
		float frac = p.getHealth() / p.getMaxHealth();
		Omnitrix.revert(p, Omnitrix.Reason.REVOKE);
		helper.assertTrue(Math.abs(p.getMaxHealth() - 20f) < 0.01f && Math.abs(p.getHealth() / 20f - frac) < 0.02f, "health converts back by fraction");
		helper.succeed();
	}

	private static HeatblastKit heat() {
		return (HeatblastKit) Aliens.kit(HeatblastKit.ID);
	}

	/** v0.17.2: jumps a Heatblast kit timer (e.g. "reignite_at") to {@code at}. */
	private static void heatTimer(ServerPlayer p, String name, long at) {
		OmnitrixState n = Omnitrix.state(p).copy();
		if (at <= 0L) {
			n.timers.remove(HeatblastKit.ID + "." + name);
		} else {
			n.timers.put(HeatblastKit.ID + "." + name, at);
		}
		Omnitrix.save(p, n);
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void heatblastFireHealsHimAndSnowballsHurt(GameTestHelper helper) {
		// v0.17.2 Pyronite Body: standing in fire heals a heart a second; snowballs hurt him
		floor(helper);
		ServerPlayer p = as(helper, HeatblastKit.ID);
		p.invulnerableTime = 0;
		p.hurt(helper.getLevel().damageSources().magic(), 10f);
		float hurt = p.getHealth();
		BlockPos feet = helper.absolutePos(new BlockPos(3, 2, 1));
		helper.getLevel().setBlock(feet, Blocks.FIRE.defaultBlockState(), 3);
		helper.assertTrue(HeatblastKit.inFireOrLava(p), "he stands in the fire");
		pump(helper, p);
		helper.runAfterDelay(45, () -> {
			helper.assertTrue(p.getHealth() >= hurt + 2f * HeatblastKit.FIRE_HEAL - 0.01f, "two seconds in the fire healed 2 hearts: " + hurt + " -> " + p.getHealth());
			helper.getLevel().setBlock(feet, Blocks.AIR.defaultBlockState(), 3);
			float before = p.getHealth();
			Vec3 from = p.position().add(0, 1.2, 3.0);
			net.minecraft.world.entity.projectile.Snowball ball = new net.minecraft.world.entity.projectile.Snowball(helper.getLevel(), from.x, from.y, from.z);
			ball.setDeltaMovement(0, 0, -0.5);
			helper.getLevel().addFreshEntity(ball);
			helper.runAfterDelay(15, () -> {
				helper.assertTrue(ball.isRemoved(), "the snowball hit him");
				helper.assertTrue(p.getHealth() < before - 0.5f, "a snowball hurts a Pyronite (3, less his iron-level armour and Resistance): " + before + " -> " + p.getHealth());
				Omnitrix.revert(p, Omnitrix.Reason.REVOKE);
				helper.succeed();
			});
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void heatblastVolleyAndLassoHitAndCoolDown(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = as(helper, HeatblastKit.ID);
		HeatblastKit kit = heat();
		Zombie z = zombie(helper, 3.5, 2, 7.5, 60f);
		p.moveTo(p.getX(), p.getY(), p.getZ(), 0f, 6f); // facing +Z, a touch down
		helper.assertTrue(kit.volley(p), "R: Fireball Volley");
		helper.assertFalse(kit.volley(p), "then it cools down");
		pump(helper, p);
		helper.runAfterDelay(16, () -> {
			helper.assertTrue(z.getHealth() <= 60f - HeatblastKit.VOLLEY_SHOTS * HeatblastKit.VOLLEY_DAMAGE + 0.01f && z.isOnFire(),
					"all three fireballs burned the zombie: " + z.getHealth());
			float before = z.getHealth();
			// v0.17.2 (user: "put heatblast magma bomb move onto G and make Shift+G a 12 block cone move"): G the Magma Bomb, Shift+G the
			// Blaze Cone -- the zombie 6 blocks ahead is caught in it
			helper.assertTrue(HeatblastKit.MAGMA.equals(kit.moveOn(com.projecthero.mod.hero.AbilitySlot.SLOT_2, false, false).id()), "G is the Magma Bomb");
			helper.assertTrue(HeatblastKit.CONE.equals(kit.moveOn(com.projecthero.mod.hero.AbilitySlot.SLOT_2, true, false).id()), "Shift+G the Blaze Cone");
			z.invulnerableTime = 0;
			helper.assertTrue(kit.blazeCone(p) >= 1, "Shift+G: the Blaze Cone catches it");
			helper.assertTrue(z.getHealth() <= before - HeatblastKit.CONE_DAMAGE + 0.01f, "for 16: " + (before - z.getHealth()));
			helper.assertTrue(kit.blazeCone(p) == -1, "then it cools down");
			z.discard();
			Omnitrix.revert(p, Omnitrix.Reason.REVOKE);
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void heatblastFlamethrowerRunsWhileHeldAndMagmaPatchBurns(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = as(helper, HeatblastKit.ID);
		HeatblastKit kit = heat();
		Zombie z = zombie(helper, 3.5, 2, 4.5, 60f);
		p.moveTo(p.getX(), p.getY(), p.getZ(), 0f, 15f);
		helper.assertTrue(kit.flamethrower(p) && kit.flag(p, HeatblastKit.F_STREAM), "Shift+R: the Flamethrower");
		pump(helper, p);
		helper.runAfterDelay(12, () -> {
			helper.assertTrue(z.getHealth() < 60f && z.isOnFire(), "the stream burns what is in front: " + z.getHealth());
			kit.release(p, AbilitySlot.SLOT_1, true, 12);
			helper.assertFalse(kit.flag(p, HeatblastKit.F_STREAM), "letting go of the keys stops it");
			helper.assertFalse(kit.flamethrower(p), "and it cools down");
			helper.assertTrue(kit.magmaBomb(p), "Shift+G: a Magma Bomb");
			z.clearFire();
			float before = z.getHealth();
			HeatblastKit.magmaPatch(p, helper.getLevel(), z.position());
			helper.runAfterDelay(12, () -> {
				helper.assertTrue(z.getHealth() < before && z.isOnFire(), "the burning patch hurts what stands in it: " + before + " -> " + z.getHealth());
				z.discard();
				Omnitrix.revert(p, Omnitrix.Reason.REVOKE);
				helper.succeed();
			});
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 160)
	public void heatblastRockRideNeedsATwoSecondHoldThenFlies(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = as(helper, HeatblastKit.ID);
		HeatblastKit kit = heat();
		pump(helper, p);
		// a short hold: no rock -- a Flame Jump instead
		kit.holdStart(p, AbilitySlot.SLOT_3);
		helper.assertTrue(kit.flag(p, HeatblastKit.F_RIDE_CHARGE), "holding X charges the Rock Ride");
		kit.holdEnd(p, AbilitySlot.SLOT_3, 15);
		helper.assertFalse(kit.flag(p, HeatblastKit.F_RIDE_CHARGE) || kit.flag(p, HeatblastKit.F_RIDING), "let go early: no ride");
		helper.assertTrue(kit.cooldownLeft(p, HeatblastKit.JUMP) > 0 && p.getDeltaMovement().y > 0.9, "a Flame Jump instead");
		helper.assertTrue(kit.cooldownLeft(p, HeatblastKit.RIDE) == 0, "the ride's cooldown is untouched");
		// the full hold
		kit.holdStart(p, AbilitySlot.SLOT_3);
		int hold = com.projecthero.mod.omnitrix.OmnitrixConfig.KIT_HOLD_TICKS;
		long pressed = helper.getLevel().getGameTime() - hold;
		helper.runAfterDelay(HeatblastKit.RIDE_CHARGE_TICKS - hold - 8, () -> {
			helper.assertTrue(kit.flag(p, HeatblastKit.F_RIDE_CHARGE) && !kit.flag(p, HeatblastKit.F_RIDING), "still charging short of 2 s");
			helper.assertTrue(kit.rideCharge(p) > 0.6f && kit.rideCharge(p) < 1f, "the charge bar fills: " + kit.rideCharge(p));
		});
		helper.runAfterDelay(HeatblastKit.RIDE_CHARGE_TICKS - hold + 2, () -> {
			long now = helper.getLevel().getGameTime();
			helper.assertTrue(now - pressed >= HeatblastKit.RIDE_CHARGE_TICKS, "2 s from the press");
			helper.assertTrue(kit.flag(p, HeatblastKit.F_RIDING) && HeatblastKit.riding(p), "the rock rips out and he rides it");
			helper.assertTrue(kit.rideBlock(p).is(Blocks.STONE), "a rock made of what he stood on: " + kit.rideBlock(p));
			helper.assertTrue(kit.rideLeft(p) > HeatblastKit.RIDE_TICKS - 10 && kit.rideLeft(p) <= HeatblastKit.RIDE_TICKS, "up to a minute: " + kit.rideLeft(p));
			helper.assertTrue(p.getAbilities().mayfly && !p.getAbilities().flying, "(never kicked for floating, never creative flight)");
			float hp = p.getHealth();
			p.invulnerableTime = 0;
			p.hurt(helper.getLevel().damageSources().fall(), 6f);
			helper.assertTrue(p.getHealth() == hp, "no fall damage on the rock");
			// sneak drops off
			p.setShiftKeyDown(true);
			helper.runAfterDelay(8, () -> {
				p.setShiftKeyDown(false);
				helper.assertFalse(kit.flag(p, HeatblastKit.F_RIDING), "sneak drops off");
				helper.assertTrue(p.isCreative() || !p.getAbilities().mayfly, "the anti-float allowance goes with the rock (a mock player counts as creative)");
				helper.assertTrue(kit.cooldownLeft(p, HeatblastKit.RIDE) > 0, "the ride cools down from stepping off");
				p.invulnerableTime = 0;
				p.hurt(helper.getLevel().damageSources().fall(), 6f);
				helper.assertTrue(p.getHealth() == hp, "no fall damage dropping off it");
				// a ride that runs its minute out
				heatTimer(p, HeatblastKit.RIDE, 0L);
				helper.assertTrue(kit.startRide(p), "ride again");
				heatTimer(p, "ride_end", helper.getLevel().getGameTime() + 2);
				helper.runAfterDelay(4, () -> {
					helper.assertFalse(kit.flag(p, HeatblastKit.F_RIDING), "the rock crumbles after its minute");
					helper.assertFalse(kit.startRide(p), "and cools down");
					Omnitrix.revert(p, Omnitrix.Reason.REVOKE);
					helper.succeed();
				});
			});
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 120)
	public void heatblastWaterDousesHimAndHeReignitesTenSecondsAfter(GameTestHelper helper) {
		floor(helper);
		var level = helper.getLevel();
		// a one-block pool where he stands (glass round it so the water cannot spread)
		BlockPos cell = helper.absolutePos(new BlockPos(3, 2, 1));
		for (BlockPos n : new BlockPos[] { cell.north(), cell.south(), cell.east(), cell.west() }) {
			level.setBlock(n, Blocks.GLASS.defaultBlockState(), 3);
		}
		ServerPlayer p = as(helper, HeatblastKit.ID);
		HeatblastKit kit = heat();
		helper.assertTrue(kit.heatAura(p) && kit.flag(p, HeatblastKit.F_AURA), "Heat Aura on");
		kit.holdStart(p, AbilitySlot.SLOT_3);
		helper.assertTrue(kit.flag(p, HeatblastKit.F_RIDE_CHARGE), "a Rock Ride charging");
		pump(helper, p);
		level.setBlock(cell, Blocks.WATER.defaultBlockState(), 3);
		helper.runAfterDelay(3, () -> {
			helper.assertTrue(HeatblastKit.doused(p) && kit.flag(p, HeatblastKit.F_WET), "water puts him out");
			helper.assertFalse(kit.flag(p, HeatblastKit.F_AURA) || kit.flag(p, HeatblastKit.F_RIDE_CHARGE), "the aura and the charge go out");
			helper.assertTrue(kit.reigniteLeft(p) == 0, "no countdown while still in the water");
			helper.assertFalse(kit.volley(p) || kit.fireTornado(p) || kit.heatShield(p), "no powers while doused");
			helper.assertTrue(kit.cooldownLeft(p, HeatblastKit.VOLLEY) == 0, "(a refused move costs no cooldown)");
			p.invulnerableTime = 0;
			float hp = p.getHealth();
			p.hurt(level.damageSources().lava(), 6f);
			helper.assertTrue(p.getHealth() == hp, "still lava-proof while doused");
			level.setBlock(cell, Blocks.AIR.defaultBlockState(), 3);
			helper.runAfterDelay(3, () -> {
				helper.assertTrue(HeatblastKit.doused(p) && !kit.flag(p, HeatblastKit.F_WET), "out of the water, still out");
				int left = kit.reigniteLeft(p);
				helper.assertTrue(left > HeatblastKit.REIGNITE_TICKS - 6 && left <= HeatblastKit.REIGNITE_TICKS, "the 10 s countdown runs: " + left);
				// wet again: the countdown starts over
				level.setBlock(cell, Blocks.WATER.defaultBlockState(), 3);
				helper.runAfterDelay(3, () -> {
					helper.assertTrue(kit.flag(p, HeatblastKit.F_WET) && kit.reigniteLeft(p) == 0, "getting wet again stops the countdown");
					level.setBlock(cell, Blocks.AIR.defaultBlockState(), 3);
					helper.runAfterDelay(3, () -> {
						helper.assertTrue(kit.reigniteLeft(p) > HeatblastKit.REIGNITE_TICKS - 6, "and it restarts from 10 s: " + kit.reigniteLeft(p));
						// skip to the end of the countdown
						heatTimer(p, "reignite_at", level.getGameTime() + 2);
						helper.runAfterDelay(4, () -> {
							helper.assertFalse(HeatblastKit.doused(p), "the flames burst back");
							helper.assertTrue(kit.volley(p), "and his powers with them");
							Omnitrix.revert(p, Omnitrix.Reason.REVOKE);
							helper.succeed();
						});
					});
				});
			});
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void heatblastFireRelightsHimAtOnceAndADousingEndsTheRide(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = as(helper, HeatblastKit.ID);
		HeatblastKit kit = heat();
		helper.assertTrue(kit.startRide(p) && kit.flag(p, HeatblastKit.F_RIDING), "on the rock");
		kit.douse(p, false); // a splash
		helper.assertTrue(HeatblastKit.doused(p) && !kit.flag(p, HeatblastKit.F_RIDING), "doused: he drops off the rock");
		helper.assertTrue(kit.reigniteLeft(p) > HeatblastKit.REIGNITE_TICKS - 3, "a splash starts the countdown at once");
		p.invulnerableTime = 0;
		float hp = p.getHealth();
		p.hurt(helper.getLevel().damageSources().fall(), 6f);
		helper.assertTrue(p.getHealth() == hp, "no fall damage for the drop");
		helper.assertTrue(kit.onUse(p, InteractionHand.MAIN_HAND) == net.minecraft.world.InteractionResult.FAIL, "no fire bolt while doused");
		pump(helper, p);
		helper.getLevel().setBlock(helper.absolutePos(new BlockPos(3, 2, 1)), Blocks.FIRE.defaultBlockState(), 3);
		helper.runAfterDelay(3, () -> {
			helper.assertFalse(HeatblastKit.doused(p), "stepping into fire relights him at once");
			Omnitrix.revert(p, Omnitrix.Reason.REVOKE);
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 120)
	public void heatblastTornadoAndSupernovaBurn(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = as(helper, HeatblastKit.ID);
		HeatblastKit kit = heat();
		Zombie z = zombie(helper, 3.5, 2, 7.5, 80f);
		p.moveTo(p.getX(), p.getY(), p.getZ(), 0f, 6f);
		helper.assertTrue(kit.fireTornado(p) && kit.tornadoAt(p) != null, "Z: a Fire Tornado where he looks");
		helper.assertTrue(kit.tornadoAt(p).distanceTo(z.position()) < 1.5, "on the creature he aimed at: " + kit.tornadoAt(p));
		pump(helper, p);
		helper.runAfterDelay(25, () -> {
			helper.assertTrue(z.getHealth() < 80f && z.isOnFire(), "the tornado burns: " + z.getHealth());
			float before = z.getHealth();
			z.invulnerableTime = 0;
			helper.assertTrue(kit.supernova(p) && kit.flag(p, HeatblastKit.F_CHARGING), "Shift+Z: the Supernova charges");
			helper.runAfterDelay(HeatblastKit.SUPERNOVA_CHARGE + 2, () -> {
				helper.assertFalse(kit.flag(p, HeatblastKit.F_CHARGING), "then goes off");
				helper.assertTrue(z.getHealth() <= before - HeatblastKit.SUPERNOVA_EDGE_DAMAGE + 0.01f, "a huge blast: " + before + " -> " + z.getHealth());
				helper.assertTrue(kit.timer(p, "ring_end") > helper.getLevel().getGameTime(), "and a ring of fire left burning");
				z.discard();
				Omnitrix.revert(p, Omnitrix.Reason.REVOKE);
				helper.assertFalse(kit.flag(p, HeatblastKit.F_TORNADO), "turning back ends the tornado");
				helper.succeed();
			});
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 140)
	public void heatblastEruptionShieldDashAndMeteorCrash(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = as(helper, HeatblastKit.ID);
		HeatblastKit kit = heat();
		Zombie z = zombie(helper, 3.5, 2, 5.5, 80f);
		helper.assertTrue(kit.eruption(p), "C: Volcanic Eruption");
		pump(helper, p);
		helper.runAfterDelay(14, () -> {
			helper.assertTrue(z.getHealth() <= 80f - HeatblastKit.ERUPT_DAMAGE + 0.01f && z.isOnFire(), "a geyser caught the zombie: " + z.getHealth());
			helper.assertTrue(kit.heatShield(p) && kit.flag(p, HeatblastKit.F_SHIELD), "Shift+V: Heat Shield");
			float hp = p.getHealth();
			p.invulnerableTime = 0;
			p.hurt(helper.getLevel().damageSources().magic(), 10f);
			helper.assertTrue(Math.abs((hp - p.getHealth()) - 10f * HeatblastKit.SHIELD_FACTOR * 0.8f) < 0.05f, "the dome takes 60% off (and Resistance I a fifth of the rest)");
			helper.assertTrue(kit.blazeDash(p) && kit.flag(p, HeatblastKit.F_DASH), "Shift+X: Blaze Dash");
			z.teleportTo(p.getX(), p.getY(), p.getZ() + 2.0);
			z.invulnerableTime = 0;
			float before = z.getHealth();
			helper.assertTrue(kit.meteorCrash(p) && kit.flag(p, HeatblastKit.F_METEOR), "Shift+C: Meteor Crash");
			helper.runAfterDelay(34, () -> {
				helper.assertTrue(kit.timer(p, "meteor_dive") > 0, "past the top: diving");
				p.setOnGround(true);
				helper.runAfterDelay(3, () -> {
					helper.assertFalse(kit.flag(p, HeatblastKit.F_METEOR), "the crash lands");
					helper.assertTrue(z.getHealth() <= before - HeatblastKit.METEOR_DAMAGE + 0.01f, "a fiery impact: " + before + " -> " + z.getHealth());
					z.discard();
					Omnitrix.revert(p, Omnitrix.Reason.REVOKE);
					helper.succeed();
				});
			});
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void heatblastMeltsSandIntoGlass(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = as(helper, HeatblastKit.ID);
		HeatblastKit kit = (HeatblastKit) Aliens.kit(HeatblastKit.ID);
		BlockPos sand = helper.absolutePos(new BlockPos(3, 2, 3));
		helper.getLevel().setBlock(sand, Blocks.SAND.defaultBlockState(), 3);
		helper.assertTrue(kit.melt(p, sand) && helper.getLevel().getBlockState(sand).is(Blocks.GLASS), "sand melts into glass");
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.RAW_IRON, 5));
		helper.assertTrue(kit.cooldownLeft(p, HeatblastKit.SMELT) > 0, "smelting shares a short cooldown");
		helper.succeed();
	}

	// ================================================================ Four Arms

	@GameTest(template = EMPTY_STRUCTURE)
	public void fourArmsIsBigToughAndSlow(GameTestHelper helper) {
		ServerPlayer p = as(helper, FourArmsKit.ID);
		helper.assertTrue(Math.abs(p.getMaxHealth() - 40f) < 0.01f, "40 health");
		helper.assertTrue(Math.abs(p.getScale() - 1.4f) < 0.01f, "1.4x the size");
		helper.assertTrue(p.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) >= 0.6 - 1.0e-6, "hard to knock back");
		helper.assertTrue(p.getAttributeValue(Attributes.ARMOR) >= 8 - 1.0e-6, "heavy armour");
		helper.assertTrue(p.getAttributeValue(Attributes.MOVEMENT_SPEED) < 0.1 * 0.9, "slower than a player");
		helper.assertTrue(p.getAttributeValue(Attributes.ATTACK_DAMAGE) >= 11 - 1.0e-6, "the fist hits for 11 (+10)");
		Omnitrix.revert(p, Omnitrix.Reason.REVOKE);
		helper.assertTrue(Math.abs(p.getScale() - 1f) < 0.01f && Math.abs(p.getMaxHealth() - 20f) < 0.01f, "the body comes off with the transformation");
		helper.succeed();
	}

	/**
	 * v0.17.2 (user: "Make fourarms R ability a 4 stage R combo where player has to press R 4 times"): R is the Four-Fist Combo --
	 * jab 8, cross 8, the lower pair 2 x 5, then the four-fist hammer 16 (46 in all); a short gap between presses, a longer one after.
	 */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void fourArmsFlurryLandsFourPunchesAndAnUppercut(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = as(helper, FourArmsKit.ID);
		FourArmsKit kit = (FourArmsKit) Aliens.kit(FourArmsKit.ID);
		Zombie z = zombie(helper, 3.5, 2, 4.0, 100f);
		p.moveTo(p.getX(), p.getY(), p.getZ(), 0f, 25f); // facing +Z, looking down at it
		helper.assertTrue(AlienKit.lookTarget(p, FourArmsKit.REACH) == z, "the zombie is in reach");
		float[] expect = { FourArmsKit.COMBO_JAB_DAMAGE, FourArmsKit.COMBO_JAB_DAMAGE, 2 * FourArmsKit.COMBO_BODY_DAMAGE, FourArmsKit.COMBO_HAMMER_DAMAGE };
		float hp = z.getHealth();
		for (int i = 0; i < 4; i++) {
			helper.assertTrue(kit.comboStep(p) == i, "blow " + (i + 1) + " is next");
			helper.assertTrue(kit.flurry(p) == 1, "R: blow " + (i + 1));
			helper.assertTrue(Math.abs(hp - z.getHealth() - expect[i]) < 0.05f, "blow " + (i + 1) + " does " + expect[i] + ": " + (hp - z.getHealth()));
			hp = z.getHealth();
			if (i < 3) {
				helper.assertTrue(kit.flurry(p) == -1, "a short gap between presses");
				var n = Omnitrix.state(p).copy();
				n.timers.remove(kit.key(FourArmsKit.FLURRY));
				Omnitrix.save(p, n);
			}
		}
		helper.assertTrue(kit.comboStep(p) == 0 && kit.cooldownLeft(p, FourArmsKit.FLURRY) > FourArmsKit.COMBO_GAP, "the hammer ends it, with a longer wait");
		z.discard();
		helper.succeed();
	}

	/** v0.17.2 revamp: Shift+R Hammer Smash -- the crack runs along the ground and launches a zombie 5 blocks ahead (14). */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void fourArmsHammerSmashCracksALine(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = as(helper, FourArmsKit.ID);
		FourArmsKit kit = (FourArmsKit) Aliens.kit(FourArmsKit.ID);
		Zombie z = zombie(helper, 3.5, 2, 6.5, 60f);
		p.moveTo(p.getX(), p.getY(), p.getZ(), 0f, 0f);
		helper.assertTrue(kit.hammerSmash(p), "Hammer Smash");
		helper.assertFalse(kit.hammerSmash(p), "it cools down");
		pump(helper, p);
		helper.runAfterDelay(18, () -> {
			helper.assertTrue(Math.abs(z.getHealth() - (60f - FourArmsKit.HAMMER_DAMAGE)) < 0.05f, "the crack reached it once: " + z.getHealth());
			z.discard();
			helper.succeed();
		});
	}

	/** v0.17.2 revamp: G grabs, G again throws; Shift+G while holding is the Giant Swing, which batters a bystander then flings. */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 120)
	public void fourArmsGrabThrowAndGiantSwing(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = as(helper, FourArmsKit.ID);
		FourArmsKit kit = (FourArmsKit) Aliens.kit(FourArmsKit.ID);
		Zombie z = zombie(helper, 3.5, 2, 3.5, 60f);
		helper.assertTrue(kit.grab(p, z) && Omnitrix.state(p).heldEntity == z.getId(), "grabbed");
		helper.assertTrue(kit.throwHeld(p) && Omnitrix.state(p).heldEntity == 0, "thrown");
		helper.assertTrue(kit.cooldownLeft(p, FourArmsKit.THROW) > 0, "the throw cools down");
		helper.assertFalse(kit.giantSwing(p), "no swing with empty hands");
		Zombie bystander = zombie(helper, 3.5, 2, 4.1, 60f);
		pump(helper, p);
		helper.runAfterDelay(25, () -> {
			z.moveTo(p.getX() + 1, p.getY(), p.getZ(), 0f, 0f);
			helper.assertTrue(kit.grab(p, z), "grabbed again");
			helper.assertTrue(kit.giantSwing(p) && kit.flag(p, FourArmsKit.F_SWINGING), "Giant Swing");
			helper.assertFalse(kit.throwHeld(p), "no plain throw mid-swing");
			helper.runAfterDelay(FourArmsKit.SWING_TICKS + 3, () -> {
				helper.assertTrue(bystander.getHealth() <= 60f - FourArmsKit.SWING_HIT_DAMAGE + 0.05f, "the swung zombie battered the bystander: " + bystander.getHealth());
				helper.assertFalse(kit.flag(p, FourArmsKit.F_SWINGING), "the swing ended");
				helper.assertTrue(Omnitrix.state(p).heldEntity == 0, "in a fling");
				helper.assertTrue(kit.cooldownLeft(p, FourArmsKit.SWING) > 0, "the swing cools down");
				z.discard();
				bystander.discard();
				helper.succeed();
			});
		});
	}

	/** v0.17.2 revamp: Shift+C Bear Hug -- six squeezes (2 each) over 3 s, then the slam (8): 20; the hugged cannot hurt him. */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 120)
	public void fourArmsBearHugCrushesThenSlams(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = as(helper, FourArmsKit.ID);
		FourArmsKit kit = (FourArmsKit) Aliens.kit(FourArmsKit.ID);
		Zombie z = zombie(helper, 3.5, 2, 3.5, 60f);
		helper.assertTrue(kit.bearHug(p, z) && kit.flag(p, FourArmsKit.F_HUGGING) && kit.hugged(p) == z, "Bear Hug");
		helper.assertFalse(kit.bearHug(p, z), "one hug at a time");
		float hp = p.getHealth();
		p.invulnerableTime = 0;
		p.hurt(helper.getLevel().damageSources().mobAttack(z), 5f);
		helper.assertTrue(Math.abs(p.getHealth() - hp) < 0.01f, "the hugged cannot hurt him");
		pump(helper, p);
		helper.runAfterDelay(FourArmsKit.HUG_TICKS + 3, () -> {
			float total = 6 * FourArmsKit.HUG_SQUEEZE_DAMAGE + FourArmsKit.HUG_SLAM_DAMAGE;
			helper.assertTrue(Math.abs(z.getHealth() - (60f - total)) < 0.05f, "crushed and slammed: 20, " + z.getHealth());
			helper.assertFalse(kit.flag(p, FourArmsKit.F_HUGGING), "let go after the slam");
			z.discard();
			helper.succeed();
		});
	}

	/** v0.17.2 revamp: Shift+V Four-Arm Guard -- a blow from in front is blocked, one from behind is not; an arrow is swatted back. */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void fourArmsGuardBlocksTheFrontAndSwatsArrows(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = as(helper, FourArmsKit.ID);
		FourArmsKit kit = (FourArmsKit) Aliens.kit(FourArmsKit.ID);
		p.moveTo(p.getX(), p.getY(), p.getZ(), 0f, 0f);
		Zombie front = zombie(helper, 3.5, 2, 4.0, 20f);
		Zombie back = zombie(helper, 3.5, 2, 0.2, 20f);
		helper.assertTrue(kit.guard(p) && kit.flag(p, FourArmsKit.F_GUARD), "Four-Arm Guard");
		float hp = p.getHealth();
		p.invulnerableTime = 0;
		p.hurt(helper.getLevel().damageSources().mobAttack(front), 5f);
		helper.assertTrue(Math.abs(p.getHealth() - hp) < 0.01f, "a blow from in front is blocked");
		p.invulnerableTime = 0;
		p.hurt(helper.getLevel().damageSources().mobAttack(back), 5f);
		helper.assertTrue(p.getHealth() < hp - 0.01f, "one from behind lands");
		net.minecraft.world.entity.projectile.Arrow arrow = EntityType.ARROW.create(helper.getLevel());
		arrow.setPos(p.getX(), p.getY() + 1.6, p.getZ() + 2.6);
		arrow.setOwner(front);
		arrow.setDeltaMovement(0, 0, -0.4);
		helper.getLevel().addFreshEntity(arrow);
		pump(helper, p);
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(p.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) >= 1.0 - 1.0e-6, "no knockback while guarding");
			helper.assertTrue(arrow.getOwner() == p && arrow.getDeltaMovement().z > 0, "the arrow is swatted back: " + arrow.getDeltaMovement());
			arrow.discard();
			front.discard();
			back.discard();
			helper.succeed();
		});
	}

	/** v0.17.2 revamp: C Rampage -- +4 punch, no knockback, quakes; turning back clears it all. Small mobs never knock him back. */
	@GameTest(template = EMPTY_STRUCTURE)
	public void fourArmsRampageAndSmallMobsCannotShiftHim(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = as(helper, FourArmsKit.ID);
		FourArmsKit kit = (FourArmsKit) Aliens.kit(FourArmsKit.ID);
		Zombie z = zombie(helper, 3.5, 2, 3.5, 60f);
		helper.assertTrue(FourArmsKit.smallMob(z), "a zombie is a small mob");
		helper.assertFalse(FourArmsKit.smallMob(EntityType.IRON_GOLEM.create(helper.getLevel())), "an iron golem is not");
		p.invulnerableTime = 0;
		p.hurt(helper.getLevel().damageSources().mobAttack(z), 2f);
		helper.assertTrue(p.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) >= 1.0 - 1.0e-6, "the zombie's blow cannot shift him");
		double base = p.getAttributeValue(Attributes.ATTACK_DAMAGE);
		helper.assertTrue(kit.rampage(p) && kit.flag(p, FourArmsKit.F_RAMPAGE), "Rampage");
		helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.ATTACK_DAMAGE) - (base + FourArmsKit.RAMPAGE_BONUS)) < 1.0e-4, "punches hit 6 harder");
		helper.assertFalse(kit.onAttack(p, z), "the punch goes through (and quakes)");
		helper.assertTrue(kit.punchIndex(p) == 1, "the next punch is the next fist");
		kit.onAttack(p, z);
		kit.onAttack(p, z);
		helper.assertTrue(kit.punchIndex(p) == 3, "the 4th punch is the hammer");
		Omnitrix.revert(p, Omnitrix.Reason.REVOKE);
		helper.assertFalse(kit.flag(p, FourArmsKit.F_RAMPAGE), "turning back ends the Rampage");
		helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.ATTACK_DAMAGE) - 1.0) < 1.0e-4, "and its punch bonus");
		helper.assertTrue(p.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) < 1.0 - 1.0e-6, "and the no-knockback");
		z.discard();
		helper.succeed();
	}

	/** v0.17.2 revamp: Z Thunder Clap -- 17, stunned and dizzy, glass in the cone shatters and fire in it goes out. */
	@GameTest(template = EMPTY_STRUCTURE)
	public void fourArmsThunderClapStunsShattersAndDouses(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = as(helper, FourArmsKit.ID);
		FourArmsKit kit = (FourArmsKit) Aliens.kit(FourArmsKit.ID);
		p.moveTo(p.getX(), p.getY(), p.getZ(), 0f, 0f);
		Zombie z = zombie(helper, 3.5, 2, 4.0, 60f);
		BlockPos glass = helper.absolutePos(new BlockPos(3, 3, 6));
		BlockPos fire = helper.absolutePos(new BlockPos(2, 2, 7));
		helper.getLevel().setBlock(glass, Blocks.GLASS.defaultBlockState(), 3);
		helper.getLevel().setBlock(fire, Blocks.FIRE.defaultBlockState(), 3);
		helper.assertTrue(helper.getLevel().getBlockState(fire).is(Blocks.FIRE), "a fire is lit");
		helper.assertTrue(kit.clap(p), "Thunder Clap");
		helper.assertTrue(Math.abs(z.getHealth() - (60f - FourArmsKit.CLAP_DAMAGE)) < 0.05f, "17 damage: " + z.getHealth());
		helper.assertTrue(z.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN) && z.hasEffect(net.minecraft.world.effect.MobEffects.CONFUSION),
				"stunned and dizzy");
		helper.assertTrue(helper.getLevel().getBlockState(glass).isAir(), "the glass shattered");
		helper.assertFalse(helper.getLevel().getBlockState(fire).is(Blocks.FIRE), "the fire went out");
		helper.assertFalse(kit.clap(p), "it cools down");
		z.discard();
		helper.succeed();
	}

	/** v0.17.2 revamp: Shift+Z Meteor Slam -- up into the sky first, and the quake does 50 however far he fell. */
	@GameTest(template = EMPTY_STRUCTURE)
	public void fourArmsMeteorSlamRisesAndScalesWithTheFall(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = as(helper, FourArmsKit.ID);
		FourArmsKit kit = (FourArmsKit) Aliens.kit(FourArmsKit.ID);
		helper.assertTrue(Math.abs(FourArmsKit.meteorDamage(0) - FourArmsKit.METEOR_DAMAGE) < 1e-4
				&& Math.abs(FourArmsKit.meteorDamage(10) - (FourArmsKit.METEOR_DAMAGE + 10 * FourArmsKit.METEOR_PER_BLOCK)) < 1e-4, "50 at any height");
		helper.assertTrue(Math.abs(FourArmsKit.meteorDamage(80) - FourArmsKit.METEOR_MAX_DAMAGE) < 1e-4, "50 from a long fall too");
		p.setOnGround(true);
		helper.assertTrue(kit.meteorSlam(p), "Meteor Slam");
		helper.assertTrue(kit.flag(p, FourArmsKit.F_LEAPING) && kit.timer(p, "meteor_rise") > 0, "he rises first");
		helper.assertTrue(p.getDeltaMovement().y >= FourArmsKit.METEOR_RISE - 1e-3, "sky-high");
		helper.assertFalse(kit.meteorSlam(p), "the ultimate cools down");
		Omnitrix.revert(p, Omnitrix.Reason.REVOKE);
		helper.assertFalse(kit.flag(p, FourArmsKit.F_LEAPING) || kit.timer(p, "meteor_rise") > 0, "turning back clears it");
		helper.succeed();
	}

	/** v0.17.2 revamp: Shift+X Tetramand Charge bursts glass but not stone; Shift+G Boulder Rip tears the ground up and hurls it. */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void fourArmsChargeBurstsGlassAndBoulderRip(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = as(helper, FourArmsKit.ID);
		FourArmsKit kit = (FourArmsKit) Aliens.kit(FourArmsKit.ID);
		p.moveTo(p.getX(), p.getY(), p.getZ(), 0f, 0f);
		var l = helper.getLevel();
		helper.assertTrue(FourArmsKit.weakBlock(l, BlockPos.ZERO, Blocks.OAK_LEAVES.defaultBlockState()) && FourArmsKit.weakBlock(l, BlockPos.ZERO, Blocks.GLASS_PANE.defaultBlockState()),
				"leaves and panes are weak");
		helper.assertFalse(FourArmsKit.weakBlock(l, BlockPos.ZERO, Blocks.STONE.defaultBlockState()), "stone is not");
		BlockPos glassLow = helper.absolutePos(new BlockPos(3, 2, 3));
		BlockPos glassHigh = helper.absolutePos(new BlockPos(3, 3, 3));
		BlockPos stone = helper.absolutePos(new BlockPos(4, 2, 3));
		l.setBlock(glassLow, Blocks.GLASS.defaultBlockState(), 3);
		l.setBlock(glassHigh, Blocks.GLASS.defaultBlockState(), 3);
		l.setBlock(stone, Blocks.STONE.defaultBlockState(), 3);
		helper.assertTrue(kit.charge(p) && kit.flag(p, FourArmsKit.F_CHARGING), "Tetramand Charge");
		pump(helper, p);
		helper.runAfterDelay(FourArmsKit.CHARGE_TICKS + 3, () -> {
			helper.assertTrue(l.getBlockState(glassLow).isAir() && l.getBlockState(glassHigh).isAir(), "the glass burst");
			helper.assertTrue(l.getBlockState(stone).is(Blocks.STONE), "the stone held");
			helper.assertFalse(kit.flag(p, FourArmsKit.F_CHARGING), "the charge ended");
			l.setBlock(stone, Blocks.AIR.defaultBlockState(), 3);
			p.moveTo(p.getX(), p.getY(), p.getZ(), 0f, 0f);
			BlockPos front = BlockPos.containing(p.getX(), p.getY() - 0.5, p.getZ() + 1.2);
			helper.assertTrue(l.getBlockState(front).is(Blocks.STONE), "stone floor in front");
			helper.assertTrue(kit.boulderRip(p), "Boulder Rip");
			helper.assertTrue(l.getBlockState(front).isAir(), "the ground in front was torn up");
			helper.assertFalse(l.getEntitiesOfClass(com.projecthero.mod.spider.entity.ThrownBlockEntity.class, p.getBoundingBox().inflate(5)).isEmpty(), "and hurled");
			helper.assertFalse(kit.boulderRip(p), "it cools down");
			helper.succeed();
		});
	}
}
