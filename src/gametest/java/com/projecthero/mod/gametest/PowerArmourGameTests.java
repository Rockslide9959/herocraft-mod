package com.projecthero.mod.gametest;

import com.projecthero.mod.hero.AbilityRouter;
import com.projecthero.mod.powerarmour.ArmourTier;
import com.projecthero.mod.powerarmour.CoreType;
import com.projecthero.mod.powerarmour.ModuleType;
import com.projecthero.mod.powerarmour.PowerArmour;
import com.projecthero.mod.powerarmour.PowerArmourAttachments;
import com.projecthero.mod.powerarmour.PowerArmourConfig;
import com.projecthero.mod.powerarmour.PowerArmourMod;
import com.projecthero.mod.powerarmour.PowerArmourState;
import com.projecthero.mod.powerarmour.SuitData;
import com.projecthero.mod.powerarmour.block.CoreChargerBlockEntity;
import com.projecthero.mod.powerarmour.entity.PowerArmourFrameEntity;
import com.projecthero.mod.powerarmour.item.PowerArmourFrameItem;
import com.projecthero.mod.powerarmour.item.PowerCoreItem;
import com.projecthero.mod.powerarmour.kit.PowerArmourAbilityManager;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.5 Power Armour: tier ordering, stepping in grants the kit (and stores worn armour) and stepping out removes it, the
 * Primary's keys are suppressed while sealed, the core drains a step a second and an empty core leaves the suit unpowered, the
 * Core Charger charges a step a tick, slot limits and the output budget, a smithing upgrade keeps everything, and a frame
 * shrugs off explosions and survives a save. Mock players report creative; they are set to survival. Time checks assert step
 * sizes, not totals.
 */
public class PowerArmourGameTests implements FabricGameTest {
	private static final BlockPos AT = new BlockPos(3, 1, 3);

	private static void floor(GameTestHelper helper) {
		for (int x = 1; x <= 6; x++) {
			for (int z = 1; z <= 6; z++) {
				helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
			}
		}
	}

	private static ServerPlayer player(GameTestHelper helper) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		BlockPos at = helper.absolutePos(AT);
		p.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 1.5, 180f, 0f);
		return p;
	}

	private static SuitData charged(ArmourTier tier, CoreType core, float charge) {
		SuitData d = SuitData.fresh(tier);
		d.core = core;
		d.coreCharge = charge;
		return d;
	}

	private static PowerArmourFrameEntity frame(GameTestHelper helper, SuitData suit) {
		floor(helper);
		ServerLevel level = helper.getLevel();
		PowerArmourFrameEntity f = PowerArmourMod.FRAME.create(level);
		Vec3 feet = Vec3.atBottomCenterOf(helper.absolutePos(AT));
		f.moveTo(feet.x, feet.y, feet.z, 180f, 0f);
		f.setSuit(suit);
		level.addFreshEntity(f);
		return f;
	}

	private static double armour(ServerPlayer p) {
		return p.getAttributeValue(Attributes.ARMOR);
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void tierArmourOrdering(GameTestHelper helper) {
		ArmourTier[] climb = { ArmourTier.IRON, ArmourTier.HERO_ALLOY, ArmourTier.DIAMOND, ArmourTier.NETHERITE, ArmourTier.VIBRANIUM,
				ArmourTier.CELESTIAL };
		for (int i = 1; i < climb.length; i++) {
			ArmourTier a = climb[i - 1], b = climb[i];
			helper.assertTrue(b.armour > a.armour, b + " has more armour than " + a);
			helper.assertTrue(b.integrity > a.integrity, b + " has more integrity than " + a);
			helper.assertTrue(b.slots > a.slots, b + " has more slots than " + a);
			helper.assertTrue(b.damage > a.damage, b + " hits harder than " + a);
			helper.assertTrue(b.cooldown <= a.cooldown, b + " servos are at least as fast as " + a);
		}
		helper.assertTrue(ArmourTier.GOLD.armour < ArmourTier.IRON.armour, "gold plating is weaker than iron");
		helper.assertTrue(ArmourTier.GOLD.drain < ArmourTier.IRON.drain, "gold drains the core slower");
		helper.assertTrue(ArmourTier.NETHERITE.fireproof() && !ArmourTier.DIAMOND.fireproof(), "netherite and up are fireproof");
		helper.assertTrue(ArmourTier.VIBRANIUM.absorbsImpacts() && !ArmourTier.NETHERITE.absorbsImpacts(), "vibranium absorbs impacts");
		helper.assertTrue(ArmourTier.NETHERITE.knockback >= 1f, "netherite: no knockback");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void enterExitGrantsAndRemovesKit(GameTestHelper helper) {
		PowerArmourFrameEntity f = frame(helper, charged(ArmourTier.DIAMOND, CoreType.ARC_CORE, 1000f));
		ServerPlayer p = player(helper);
		p.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
		double before = armour(p);
		PowerArmour.enterInstant(p, f);
		helper.assertTrue(PowerArmour.sealed(p), "sealed after stepping in");
		helper.assertTrue(f.isRemoved(), "the frame leaves the world while worn");
		helper.assertTrue(PowerArmourAbilityManager.hasContext(p), "the armour kit owns the keys");
		helper.assertTrue(p.getItemBySlot(EquipmentSlot.HEAD).isEmpty(), "worn armour is stored");
		AttributeModifier suitArmour = p.getAttribute(Attributes.ARMOR).getModifier(com.projecthero.mod.ProjectHeroMod.id("power_armour_armor"));
		helper.assertTrue(suitArmour != null && Math.abs(suitArmour.amount() - ArmourTier.DIAMOND.armour) < 0.01, "the suit gives its tier's armour");
		helper.assertTrue(armour(p) >= ArmourTier.DIAMOND.armour, "armour at least the suit's, got " + armour(p));
		PowerArmourFrameEntity out = PowerArmour.exitInstant(p);
		helper.assertTrue(!PowerArmour.ownsKeys(p) && !PowerArmourAbilityManager.hasContext(p), "the kit is gone after stepping out");
		helper.assertTrue(out != null && out.isAlive(), "the frame stands again");
		helper.assertTrue(out.suit().core == CoreType.ARC_CORE && out.suit().tier == ArmourTier.DIAMOND, "the frame keeps its suit");
		helper.assertTrue(p.getItemBySlot(EquipmentSlot.HEAD).is(Items.IRON_HELMET), "the helmet comes back");
		helper.assertTrue(Math.abs(armour(p) - before) < 0.01, "armour back to normal, got " + armour(p));
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void primaryKeysSuppressedWhileInside(GameTestHelper helper) {
		PowerArmourFrameEntity f = frame(helper, charged(ArmourTier.IRON, CoreType.FUSION_CELL, 1000f));
		ServerPlayer p = player(helper);
		com.projecthero.mod.blackpanther.BlackPanther.grant(p);
		helper.assertTrue(com.projecthero.mod.blackpanther.BlackPantherAbilityManager.hasContext(p), "Black Panther holds the keys before");
		PowerArmour.enterInstant(p, f);
		for (int slot = 1; slot <= 6; slot++) {
			if (slot == 5) {
				continue; // V toggles the visor; fine, but keep the check simple
			}
			AbilityRouter.handleInput(p, slot, true);
			AbilityRouter.handleInput(p, slot, false);
		}
		helper.assertTrue(com.projecthero.mod.blackpanther.BlackPanther.state(p).abilityReadyAt.isEmpty(),
				"no Black Panther move fired while sealed");
		PowerArmourState s = PowerArmour.state(p);
		helper.assertTrue(s.readyAt.containsKey("stomp"), "G ran the suit's Ground Stomp");
		helper.assertTrue(com.projecthero.mod.blackpanther.BlackPanther.hasPower(p), "the Primary is still held (a Secondary never replaces it)");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 120)
	public void coreDrainsAndEmptyCoreIsUnpowered(GameTestHelper helper) {
		PowerArmourFrameEntity f = frame(helper, charged(ArmourTier.IRON, CoreType.FUSION_CELL, 500f));
		ServerPlayer p = player(helper);
		PowerArmour.enterInstant(p, f);
		float[] a = new float[1];
		helper.runAfterDelay(5, () -> a[0] = PowerArmour.suit(p).coreCharge);
		helper.runAfterDelay(25, () -> {
			float step = a[0] - PowerArmour.suit(p).coreCharge;
			float want = 20 * PowerArmourConfig.IDLE_DRAIN * ArmourTier.IRON.drain;
			helper.assertTrue(Math.abs(step - want) < want * 0.35f, "drains about " + want + " E a second, got " + step);
			PowerArmour.suit(p).coreCharge = 0f;
		});
		helper.runAfterDelay(30, () -> {
			helper.assertTrue(!PowerArmour.suit(p).powered(), "an empty core leaves the suit unpowered");
			AttributeModifier mod = p.getAttribute(Attributes.MOVEMENT_SPEED).getModifier(com.projecthero.mod.ProjectHeroMod.id("power_armour_speed"));
			helper.assertTrue(mod != null && mod.amount() <= PowerArmourConfig.UNPOWERED_SPEED + 0.001, "very slow when unpowered");
			AbilityRouter.handleInput(p, 2, true);
			helper.assertTrue(!PowerArmour.state(p).readyAt.containsKey("stomp"), "no moves when unpowered");
			PowerArmour.beginUnseal(p);
			helper.assertTrue(PowerArmour.state(p).phase == PowerArmourState.UNSEALING, "an unpowered suit still opens");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void chargerChargesCores(GameTestHelper helper) {
		floor(helper);
		BlockPos pos = AT;
		helper.setBlock(pos, PowerArmourMod.CHARGER);
		CoreChargerBlockEntity be = (CoreChargerBlockEntity) helper.getBlockEntity(pos);
		ItemStack core = new ItemStack(PowerArmourMod.core(CoreType.FUSION_CELL));
		PowerCoreItem.setCharge(core, 0f);
		be.setItem(0, core);
		be.setItem(1, new ItemStack(Items.COAL, 2));
		float[] a = new float[1];
		helper.runAfterDelay(5, () -> a[0] = PowerCoreItem.charge(be.getItem(0)));
		helper.runAfterDelay(25, () -> {
			float step = PowerCoreItem.charge(be.getItem(0)) - a[0];
			float want = 20 * CoreChargerBlockEntity.CHARGE_PER_TICK;
			helper.assertTrue(Math.abs(step - want) <= CoreChargerBlockEntity.CHARGE_PER_TICK * 2, "charges about " + want + " in 20 ticks, got " + step);
			helper.assertTrue(be.getItem(1).getCount() == 1, "one coal burning");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void moduleSlotsAndOutputBudget(GameTestHelper helper) {
		SuitData iron = SuitData.fresh(ArmourTier.IRON);
		helper.assertTrue(iron.modules.length == 3, "iron has 3 slots");
		helper.assertTrue(ArmourTier.IRON.slotsFor(com.projecthero.mod.powerarmour.BodyPart.CHEST) == 0, "iron has no chest slot");
		helper.assertTrue(iron.whyNot(0, ModuleType.GATLING) != null, "a weapon does not go in the helmet slot");
		helper.assertTrue(iron.whyNot(1, ModuleType.GATLING) == null, "a weapon goes on the arm");
		iron.modules[1] = ModuleType.GATLING;
		iron.core = CoreType.FUSION_CELL;
		iron.coreCharge = 1000f;
		helper.assertTrue("projecthero.power_armour.fit.over_budget".equals(iron.whyNot(2, ModuleType.SENTRY_TURRET)), "3 + 5 > 6 output");
		helper.assertTrue(iron.whyNot(2, ModuleType.REPAIR_DRONE) == null, "3 + 3 fits a Fusion Cell");
		iron.modules[2] = ModuleType.REPAIR_DRONE;
		helper.assertTrue(iron.load() == 6 && iron.output() == 6, "load 6 of 6");
		SuitData net = SuitData.fresh(ArmourTier.NETHERITE);
		helper.assertTrue(ArmourTier.NETHERITE.slotsFor(com.projecthero.mod.powerarmour.BodyPart.ARMS) == 2, "netherite has two arm slots");
		int arm1 = 1, arm2 = 5;
		net.modules[arm1] = ModuleType.RAIL_CANNON;
		helper.assertTrue("projecthero.power_armour.fit.one_weapon".equals(net.whyNot(arm2, ModuleType.GATLING)), "only one arm weapon");
		helper.assertTrue(net.whyNot(arm2, ModuleType.MAGNETIC_CLAMP) == null, "the clamp sits beside a weapon");
		helper.assertTrue("projecthero.power_armour.fit.duplicate".equals(net.whyNot(arm2, ModuleType.RAIL_CANNON)), "no duplicates");
		helper.assertTrue(net.whyNotCore(CoreType.FUSION_CELL) == null, "6 load on a 6-output core fits");
		net.modules[arm2] = ModuleType.MAGNETIC_CLAMP;
		helper.assertTrue(net.whyNotCore(CoreType.FUSION_CELL) != null, "8 load is too much for a Fusion Cell");
		helper.assertTrue(net.whyNotCore(CoreType.ARC_CORE) == null, "an Arc Core runs it");
		net.core = CoreType.FUSION_CELL;
		helper.assertTrue("projecthero.power_armour.fit.over_budget".equals(net.whyNot(4, ModuleType.REACTIVE_PLATING)), "over the budget");
		helper.assertTrue(net.whyNot(4, ModuleType.OVERCLOCK) == null, "Overclock pays for itself (+3 output, 0 load)");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void smithingUpgradeKeepsModules(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		SuitData suit = charged(ArmourTier.IRON, CoreType.FUSION_CELL, 640f);
		suit.modules[0] = ModuleType.NIGHT_VISOR;
		suit.modules[1] = ModuleType.GATLING;
		suit.primary = 0x123456;
		suit.integrity = 150f;
		ItemStack base = PowerArmourFrameItem.stackOf(suit);
		SmithingRecipeInput input = new SmithingRecipeInput(new ItemStack(PowerArmourMod.CIRCUIT_BOARD), base,
				new ItemStack(PowerArmourMod.plating(ArmourTier.GOLD)));
		var recipe = level.getRecipeManager().getRecipeFor(RecipeType.SMITHING, input, level);
		helper.assertTrue(recipe.isPresent(), "there is a smithing recipe iron -> gold");
		ItemStack result = recipe.get().value().assemble(input, level.registryAccess());
		helper.assertTrue(result.getItem() == PowerArmourMod.frame(ArmourTier.GOLD), "it makes a gold frame");
		SuitData up = PowerArmourFrameItem.suitOf(result);
		helper.assertTrue(up.tier == ArmourTier.GOLD, "the suit is gold now");
		helper.assertTrue(up.modules[0] == ModuleType.NIGHT_VISOR && up.modules[1] == ModuleType.GATLING, "modules kept");
		helper.assertTrue(up.core == CoreType.FUSION_CELL && Math.abs(up.coreCharge - 640f) < 0.01f, "core and charge kept");
		helper.assertTrue(up.primary == 0x123456, "paint kept");
		helper.assertTrue(Math.abs(up.integrityFraction() - 0.5f) < 0.01f, "integrity keeps its share");
		var next = level.getRecipeManager().getRecipeFor(RecipeType.SMITHING, new SmithingRecipeInput(new ItemStack(PowerArmourMod.CIRCUIT_BOARD),
				PowerArmourFrameItem.stackOf(SuitData.fresh(ArmourTier.IRON)), new ItemStack(PowerArmourMod.plating(ArmourTier.DIAMOND))), level);
		helper.assertTrue(next.isEmpty(), "no skipping tiers");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void frameSurvivesExplosionsAndPersists(GameTestHelper helper) {
		SuitData suit = charged(ArmourTier.VIBRANIUM, CoreType.STAR_CORE, 4321f);
		suit.modules[2] = ModuleType.MISSILE_POD;
		suit.glow = 0xFF00FF;
		PowerArmourFrameEntity f = frame(helper, suit);
		ServerLevel level = helper.getLevel();
		Vec3 c = f.position();
		level.explode(null, c.x, c.y + 0.5, c.z, 4f, Level.ExplosionInteraction.NONE);
		helper.runAfterDelay(5, () -> {
			helper.assertTrue(f.isAlive() && !f.isRemoved(), "the frame survives a blast");
			helper.assertTrue(f.distanceToSqr(c) < 4.0, "and is not thrown far");
			CompoundTag tag = new CompoundTag();
			f.saveWithoutId(tag);
			PowerArmourFrameEntity copy = PowerArmourMod.FRAME.create(level);
			copy.load(tag);
			helper.assertTrue(copy.suit().equals(f.suit()), "the suit survives a save");
			helper.assertTrue(copy.suit().coreCharge > 4000f && copy.suit().modules[2] == ModuleType.MISSILE_POD && copy.suit().glow == 0xFF00FF,
					"core, modules and paint saved");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 80)
	public void fallsDrainTheCoreNotHealth(GameTestHelper helper) {
		PowerArmourFrameEntity f = frame(helper, charged(ArmourTier.IRON, CoreType.FUSION_CELL, 1000f));
		ServerPlayer p = player(helper);
		PowerArmour.enterInstant(p, f);
		float hp = p.getHealth();
		float charge = PowerArmour.suit(p).coreCharge;
		// the damage hook itself (a fresh mock player is still spawn-invulnerable, so hurt() would never reach it)
		helper.assertTrue(!PowerArmour.allowDamage(p, p.damageSources().fall(), 6f), "the fall is cancelled while powered");
		helper.assertTrue(p.getHealth() == hp, "no fall damage while powered");
		helper.assertTrue(charge - PowerArmour.suit(p).coreCharge >= 6f * PowerArmourConfig.FALL_COST - 0.01f, "the fall cost core charge");
		float integ = PowerArmour.suit(p).integrity;
		helper.assertTrue(PowerArmour.allowDamage(p, p.damageSources().generic(), 5f), "an ordinary hit still lands");
		helper.assertTrue(integ - PowerArmour.suit(p).integrity >= 5f - 0.01f, "a hit costs integrity");
		helper.assertTrue(p.getAttached(PowerArmourAttachments.STATE) != null, "still in the suit");
		helper.succeed();
	}

	// ------------------------------------------------------------------ round 2: proportions + side-aware opening

	/** A mock player {@code dz} blocks from the frame along z (the frames face north, yaw 180: front is -z), looking at it. */
	private static ServerPlayer playerAt(GameTestHelper helper, double dz) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 feet = Vec3.atBottomCenterOf(helper.absolutePos(AT));
		p.moveTo(feet.x + 0.3, feet.y, feet.z + dz, dz < 0 ? 0f : 180f, 0f);
		return p;
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void clickSideIsStoredAndSynced(GameTestHelper helper) {
		// the pure rule: front / back half-plane, the look decides right beside it
		helper.assertTrue(PowerArmour.sideFrom(0, 0, 180f, 0.2, -1.0, 0, 1) == PowerArmourState.SIDE_FRONT, "in front (yaw 180 faces -z)");
		helper.assertTrue(PowerArmour.sideFrom(0, 0, 180f, -0.4, 1.2, 0, -1) == PowerArmourState.SIDE_BACK, "behind");
		helper.assertTrue(PowerArmour.sideFrom(0, 0, 0f, 0.0, 1.0, 0, -1) == PowerArmourState.SIDE_FRONT, "yaw 0 faces +z");
		helper.assertTrue(PowerArmour.sideFrom(0, 0, 180f, 1.0, 0.05, 0, -1) == PowerArmourState.SIDE_BACK, "beside it, looking the way it faces");
		helper.assertTrue(PowerArmour.sideFrom(0, 0, 180f, 1.0, 0.05, 0, 1) == PowerArmourState.SIDE_FRONT, "beside it, looking at its face");
		// a real click from behind
		PowerArmourFrameEntity f = frame(helper, charged(ArmourTier.IRON, CoreType.FUSION_CELL, 800f));
		ServerPlayer p = playerAt(helper, 1.6);
		helper.assertTrue(PowerArmour.tryEnter(p, f), "the click starts stepping in");
		PowerArmourState s = PowerArmour.state(p);
		helper.assertTrue(s.side == PowerArmourState.SIDE_BACK, "clicked from behind: the back opens, got " + s.side);
		helper.assertTrue(f.side() == PowerArmourState.SIDE_BACK && f.anim() == PowerArmourFrameEntity.OPENING, "the frame opens its back");
		// synced: the attachment's network codec and the frame's save both carry it
		helper.assertTrue(PowerArmourState.fromTag(s.toTag()).side == PowerArmourState.SIDE_BACK, "the side survives the state codec");
		CompoundTag tag = new CompoundTag();
		f.saveWithoutId(tag);
		PowerArmourFrameEntity copy = PowerArmourMod.FRAME.create(helper.getLevel());
		copy.load(tag);
		helper.assertTrue(copy.side() == PowerArmourState.SIDE_BACK, "the frame's side survives a save");
		// and from the front
		PowerArmourFrameEntity g = frame(helper, charged(ArmourTier.IRON, CoreType.FUSION_CELL, 800f));
		ServerPlayer q = playerAt(helper, -1.6);
		helper.assertTrue(PowerArmour.tryEnter(q, g), "the second click starts stepping in");
		helper.assertTrue(PowerArmour.state(q).side == PowerArmourState.SIDE_FRONT && g.side() == PowerArmourState.SIDE_FRONT,
				"clicked from the front: the front opens");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void frontEntryTurnsBackEntryWalksStraightIn(GameTestHelper helper) {
		for (int side : new int[] { PowerArmourState.SIDE_FRONT, PowerArmourState.SIDE_BACK }) {
			String name = side == PowerArmourState.SIDE_FRONT ? "front" : "back";
			PowerArmourState s = new PowerArmourState();
			s.side = side;
			s.fx = 10;
			s.fz = 10;
			s.fyaw = 180f; // faces -z
			s.sx = 10.5;
			s.sz = side == PowerArmourState.SIDE_FRONT ? 7.5 : 12.5;
			s.syaw = side == PowerArmourState.SIDE_FRONT ? 0f : 180f;
			s.phase = PowerArmourState.ENTER_OPEN;
			double[] turn = PowerArmour.drivenPose(s, PowerArmourConfig.ENTER_OPEN_TICKS);
			double want = side == PowerArmourState.SIDE_FRONT ? 10 - PowerArmourConfig.APPROACH : 10 + PowerArmourConfig.APPROACH;
			helper.assertTrue(Math.abs(turn[2] - want) < 1e-3 && Math.abs(turn[0] - 10) < 1e-3,
					name + ": walks to the approach point on its side, got z " + turn[2]);
			helper.assertTrue(Math.abs(Mth.wrapDegrees((float) turn[3] - 180f)) < 0.5f, name + ": ends facing the way the suit faces, got yaw " + turn[3]);
			if (side == PowerArmourState.SIDE_BACK) {
				for (int t = 0; t <= PowerArmourConfig.ENTER_OPEN_TICKS; t += 4) {
					double[] at = PowerArmour.drivenPose(s, t);
					helper.assertTrue(Math.abs(Mth.wrapDegrees((float) at[3] - 180f)) < 1f, "back: no turn round at tick " + t + ", yaw " + at[3]);
				}
			}
			s.phase = PowerArmourState.ENTER_WALK;
			double[] mid = PowerArmour.drivenPose(s, PowerArmourConfig.ENTER_WALK_TICKS / 2f);
			double[] in = PowerArmour.drivenPose(s, PowerArmourConfig.ENTER_WALK_TICKS);
			helper.assertTrue(Math.abs(in[0] - 10) < 1e-3 && Math.abs(in[2] - 10) < 1e-3, name + ": ends inside the frame");
			// front: steps back in (towards +z while facing -z); back: steps forward in (towards -z, facing -z)
			double movedZ = in[2] - mid[2];
			helper.assertTrue(side == PowerArmourState.SIDE_FRONT ? movedZ > 0 : movedZ < 0, name + ": walk direction, moved " + movedZ);
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 80)
	public void exitOpensTheEntrySide(GameTestHelper helper) {
		PowerArmourFrameEntity f = frame(helper, charged(ArmourTier.HERO_ALLOY, CoreType.ARC_CORE, 2000f));
		ServerPlayer p = playerAt(helper, 1.6);
		PowerArmour.enterInstant(p, f, PowerArmourState.SIDE_BACK);
		helper.assertTrue(PowerArmour.sealed(p) && PowerArmour.state(p).side == PowerArmourState.SIDE_BACK, "sealed, came in by the back");
		float yaw = p.getYRot();
		PowerArmour.beginUnseal(p);
		helper.assertTrue(PowerArmour.state(p).side == PowerArmourState.SIDE_BACK, "opening up keeps the entry side");
		helper.runAfterDelay(PowerArmourConfig.UNSEAL_TICKS + 3, () -> {
			PowerArmourState s = PowerArmour.state(p);
			helper.assertTrue(s != null && s.phase == PowerArmourState.EXIT_WALK, "stepping out");
			helper.assertTrue(p.level().getEntity(s.frameId) instanceof PowerArmourFrameEntity out && out.side() == PowerArmourState.SIDE_BACK
					&& out.anim() == PowerArmourFrameEntity.OPEN, "the frame stands open at the back");
			helper.assertTrue(p.level().getEntity(s.frameId) instanceof PowerArmourFrameEntity out2 && Math.abs(Mth.wrapDegrees(out2.getYRot() - yaw)) < 0.5f,
					"the frame keeps facing the way the suit faced");
			Vec3 fwd = PowerArmour.forward(s.fyaw);
			double along = (s.sx - s.fx) * fwd.x + (s.sz - s.fz) * fwd.z;
			helper.assertTrue(along < -0.5 || (s.sx == s.fx && s.sz == s.fz), "steps out behind it, along " + along);
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40)
	public void suitScaleIsUniform(GameTestHelper helper) {
		float[] k = PowerArmourConfig.suitScale();
		helper.assertTrue(k[0] == k[1] && k[1] == k[2], "one factor on every axis (height scale == width scale)");
		helper.assertTrue(k[1] > 1.05f && k[1] < 1.25f, "a little bigger than a player");
		helper.assertTrue(1.8f * k[1] < 2.0f, "a sealed player still fits under a 2-block doorway");
		helper.assertTrue((4f - 2f * PowerArmourConfig.SHELL) * k[0] >= 4f - 1e-4f, "the player's 4-px limbs fit inside the shell");
		PowerArmourFrameEntity f = frame(helper, charged(ArmourTier.IRON, CoreType.FUSION_CELL, 800f));
		ServerPlayer p = player(helper);
		float h0 = p.getBbHeight(), w0 = p.getBbWidth(), e0 = p.getEyeHeight();
		PowerArmour.enterInstant(p, f);
		helper.runAfterDelay(3, () -> { // dimensions refresh on the entity's next tick
			float sh = p.getBbHeight() / h0, sw = p.getBbWidth() / w0, se = p.getEyeHeight() / e0;
			helper.assertTrue(Math.abs(sh - k[1]) < 0.01f && Math.abs(sw - k[0]) < 0.01f && Math.abs(se - k[1]) < 0.01f,
					"sealed: hitbox and eye height scale by the suit's factor (h " + sh + ", w " + sw + ", eye " + se + ")");
			PowerArmour.exitInstant(p);
		});
		helper.runAfterDelay(7, () -> {
			helper.assertTrue(Math.abs(p.getBbHeight() - h0) < 0.01f && Math.abs(p.getScale() - 1f) < 0.001f, "back to normal size outside");
			helper.succeed();
		});
	}
}
