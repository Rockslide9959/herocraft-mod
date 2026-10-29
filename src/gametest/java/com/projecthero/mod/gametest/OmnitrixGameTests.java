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
 * you; success pops the item and clears the power), the Meteor Scanner, the pod's touch, the watch (V raises / lowers, the
 * scroll cycle, the slap transforms, V reverts, the 3-minute timeout, the 90 s recharge, V refused during it), and Heatblast's
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
		var meteors = helper.getLevel().getEntitiesOfClass(OmnitrixMeteorEntity.class, new AABB(at, at).inflate(80, 120, 80));
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
		helper.getLevel().getEntitiesOfClass(OmnitrixMeteorEntity.class, p.getBoundingBox().inflate(80, 120, 80)).forEach(m -> m.discard());
		OmnitrixWorld.clearLoose(helper.getLevel().getServer());
		helper.assertTrue(helper.getLevel().getRecipeManager().byKey(com.projecthero.mod.ProjectHeroMod.id("meteor_scanner")).isPresent(),
				"the scanner has a recipe");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 120)
	public void touchingTheOpenPodPutsTheWatchOn(GameTestHelper helper) {
		floor(helper);
		OmnitrixPodEntity pod = OmnitrixPodEntity.place(helper.getLevel(), helper.absoluteVec(new Vec3(3.5, 2, 3.5)));
		ServerPlayer p = mock(helper, 1.5, 2, 1.5);
		helper.assertFalse(pod.touch(p), "a shut pod does nothing");
		helper.runAfterDelay(OmnitrixConfig.POD_OPEN_DELAY + 2, () -> {
			helper.assertTrue(pod.open(), "it opened");
			helper.assertTrue(pod.touch(p), "the watch jumps onto the wrist");
			helper.assertTrue(Omnitrix.hasPower(p) && pod.empty(), "worn; the pod is empty");
			ServerPlayer q = mock(helper, 1.5, 2, 2.5);
			helper.assertFalse(pod.touch(q), "an empty pod gives nothing");
			pod.discard();
			helper.succeed();
		});
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
		helper.assertTrue(Omnitrix.slap(p), "the click slaps the core down");
		helper.runAfterDelay(Omnitrix.SLAP_TICKS + 2, () -> {
			helper.assertTrue(Omnitrix.transformed(p) && !Omnitrix.selecting(p), "the slap transformed");
			helper.assertTrue(Omnitrix.state(p).active.equals(picked), "into the selected alien");
			helper.assertTrue(Omnitrix.timeLeft(p) > OmnitrixConfig.TRANSFORM_TICKS - 20, "for 3 minutes");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 260)
	public void vRevertsTheRechargeRefusesAndTheTimeoutTimesOut(GameTestHelper helper) {
		ServerPlayer p = wearer(helper, 2.5, 2, 2.5);
		pump(helper, p);
		Omnitrix.transform(p);
		OmnitrixAbilityManager.handle(p, AbilitySlot.SLOT_5, true);
		OmnitrixAbilityManager.handle(p, AbilitySlot.SLOT_5, false);
		// v0.17.1: V starts the power-down (five beeps), the revert lands POWER_DOWN_REVERT_TICK later
		helper.assertTrue(Omnitrix.transformed(p) && Omnitrix.poweringDown(p), "V as an alien starts the power-down");
		helper.runAfterDelay(Omnitrix.POWER_DOWN_REVERT_TICK + 3, () -> afterTheEarlyRevert(helper, p));
	}

	private static void afterTheEarlyRevert(GameTestHelper helper, ServerPlayer p) {
		helper.assertFalse(Omnitrix.transformed(p), "V as an alien reverts");
		helper.assertTrue(Math.abs(Omnitrix.rechargeLeft(p) - OmnitrixConfig.EARLY_RECHARGE_TICKS) <= 4, "a short recharge");
		OmnitrixAbilityManager.handle(p, AbilitySlot.SLOT_1, true);
		OmnitrixAbilityManager.handle(p, AbilitySlot.SLOT_1, false);
		helper.assertFalse(Omnitrix.selecting(p), "R during the recharge does not raise the watch");
		helper.assertTrue(Omnitrix.state(p).animId == Omnitrix.ANIM_DENIED, "the face flashes red instead");
		helper.assertFalse(Omnitrix.transform(p), "no transformation while recharging");
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

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void heatblastsMovesHitAndCoolDown(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = as(helper, HeatblastKit.ID);
		HeatblastKit kit = (HeatblastKit) Aliens.kit(HeatblastKit.ID);
		Zombie z = zombie(helper, 3.5, 2, 5.5, 60f);
		p.moveTo(p.getX(), p.getY(), p.getZ(), 0f, 12f); // facing +Z, a touch down
		helper.assertTrue(kit.fireBlast(p), "Fire Blast fires");
		helper.assertFalse(kit.fireBlast(p), "then cools down");
		helper.assertTrue(kit.cooldownLeft(p, HeatblastKit.FIRE_BLAST) > 0, "a cooldown is running");
		helper.assertTrue(!helper.getLevel().getEntitiesOfClass(AlienProjectileEntity.class, p.getBoundingBox().inflate(6)).isEmpty(), "a bolt in the air");
		helper.runAfterDelay(10, () -> {
			helper.assertTrue(z.getHealth() <= 60f - HeatblastKit.BLAST_DAMAGE + 0.01f && z.isOnFire(), "the bolt burned the zombie");
			float before = z.getHealth();
			z.invulnerableTime = 0;
			helper.assertTrue(kit.nova(p), "Fireball Nova");
			helper.assertTrue(z.getHealth() < before, "the nova hit the zombie within 6 blocks");
			helper.assertFalse(kit.nova(p), "the nova cools down");
			helper.assertTrue(kit.heatShield(p) && kit.flag(p, HeatblastKit.F_SHIELD), "Heat Shield on");
			float hp = p.getHealth();
			p.invulnerableTime = 0;
			p.hurt(helper.getLevel().damageSources().magic(), 10f);
			helper.assertTrue(Math.abs((hp - p.getHealth()) - 10f * HeatblastKit.SHIELD_FACTOR) < 0.05f, "the shield takes 60% off");
			helper.assertTrue(kit.fireJet(p) && p.getAbilities().mayfly && p.getAbilities().flying, "Fire Jet flies");
			Omnitrix.revert(p, Omnitrix.Reason.REVOKE);
			helper.assertTrue(!kit.flag(p, HeatblastKit.F_FLYING) && Math.abs(p.getAbilities().getFlyingSpeed() - 0.05f) < 1.0e-4f,
					"turning back ends the jets");
			z.discard();
			helper.succeed();
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
		helper.assertTrue(p.getAttributeValue(Attributes.ATTACK_DAMAGE) >= 7 - 1.0e-6, "the fist hits for 7");
		Omnitrix.revert(p, Omnitrix.Reason.REVOKE);
		helper.assertTrue(Math.abs(p.getScale() - 1f) < 0.01f && Math.abs(p.getMaxHealth() - 20f) < 0.01f, "the body comes off with the transformation");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void fourArmsMovesHitAndCoolDown(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = as(helper, FourArmsKit.ID);
		FourArmsKit kit = (FourArmsKit) Aliens.kit(FourArmsKit.ID);
		Zombie z = zombie(helper, 3.5, 2, 4.0, 60f);
		p.moveTo(p.getX(), p.getY(), p.getZ(), 0f, 25f); // facing +Z, looking down at it
		helper.assertTrue(AlienKit.lookTarget(p, FourArmsKit.REACH) == z, "the zombie is in reach");
		helper.assertTrue(kit.haymaker(p), "Haymaker");
		helper.assertTrue(Math.abs(z.getHealth() - (60f - FourArmsKit.HAYMAKER_DAMAGE)) < 0.05f, "12 damage");
		helper.assertFalse(kit.haymaker(p), "then cools down");
		z.moveTo(p.getX(), p.getY(), p.getZ() + 2.5, 180f, 0f);
		z.invulnerableTime = 0;
		p.setOnGround(true);
		float before = z.getHealth();
		helper.assertTrue(kit.groundPound(p), "Ground Pound");
		helper.assertTrue(z.getHealth() < before, "the ring of force hit");
		helper.assertFalse(kit.groundPound(p), "it cools down");
		// grab and throw
		z.moveTo(p.getX(), p.getY(), p.getZ() + 2.0, 180f, 0f);
		helper.assertTrue(kit.grab(p, z) && Omnitrix.state(p).heldEntity == z.getId(), "grabbed");
		helper.assertTrue(kit.throwHeld(p) && Omnitrix.state(p).heldEntity == 0, "thrown");
		helper.assertTrue(kit.cooldownLeft(p, FourArmsKit.THROW) > 0, "the throw cools down");
		// Iron Stance: 70% less
		helper.assertTrue(kit.ironStance(p), "Iron Stance");
		float hp = p.getHealth();
		p.invulnerableTime = 0;
		p.hurt(helper.getLevel().damageSources().magic(), 10f);
		helper.assertTrue(Math.abs((hp - p.getHealth()) - 10f * FourArmsKit.STANCE_FACTOR) < 0.05f, "the stance takes 70% off");
		helper.assertTrue(p.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) >= 1.0 - 1.0e-6, "and no knockback");
		z.discard();
		helper.succeed();
	}
}
