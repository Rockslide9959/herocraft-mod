package com.projecthero.mod.gametest;

import java.util.List;

import com.projecthero.mod.hero.Ability;
import com.projecthero.mod.hero.AbilityHandlers;
import com.projecthero.mod.hero.AbilityRouter;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.ExperimentalPowers;
import com.projecthero.mod.hero.Power;
import com.projecthero.mod.hero.Powers;
import com.projecthero.mod.hero.power.p10.TelekinesisHandlers;
import com.projecthero.mod.hero.power.p15.InvisibilityLightHandlers;
import com.projecthero.mod.hero.power.p19.ShadowManipulationHandlers;
import com.projecthero.mod.hero.power.p23.GravityHandlers;
import com.projecthero.mod.hero.power.p26.MagneticHandlers;
import com.projecthero.mod.hero.revamp.d.BatchDContent;
import com.projecthero.mod.hero.revamp.d.HardLightBladeItem;
import com.projecthero.mod.hero.revamp.d.MirrorImageEntity;
import com.projecthero.mod.hero.revamp.d.ShadowServantEntity;
import com.projecthero.mod.hero.visual.MutationMeters;
import com.projecthero.mod.hero.visual.MutationVisuals;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** v0.13.22 mutation revamp, batch D: regression coverage for the reworked kits. */
public class RevampBatchDGameTests implements FabricGameTest {
	private static final String[] KEYS = { "power_10_telekinesis", "power_11_teleportation",
			"power_15_invisibility_light_manipulation", "power_19_shadow_manipulation",
			"power_23_gravity_manipulation", "power_26_magnetic_manipulation" };

	private static ServerPlayer player(GameTestHelper helper, String key) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Power power = Powers.byKey(key);
		ExperimentalPowers.grant(p, power);
		ExperimentalPowers.setActive(p, power);
		BlockPos origin = new BlockPos(2, 2, 2);
		Vec3 pp = Vec3.atBottomCenterOf(helper.absolutePos(origin));
		p.setPos(pp.x, pp.y, pp.z);
		p.setYRot(0);
		p.setXRot(0);
		return p;
	}

	private static Zombie zombieAt(GameTestHelper helper, ServerPlayer p, int dx, int dz) {
		Zombie z = helper.spawn(EntityType.ZOMBIE, new BlockPos(2 + dx, 2, 2 + dz));
		z.setPersistenceRequired();
		return z;
	}

	private static void aimAt(ServerPlayer p, net.minecraft.world.entity.Entity e) {
		p.lookAt(EntityAnchorArgument.Anchor.EYES, e.position().add(0, e.getBbHeight() * 0.5, 0));
	}

	private static Power power(String key) {
		return Powers.byKey(key);
	}

	private static void press(ServerPlayer p, int slot) {
		AbilityRouter.handleInput(p, slot, true);
		AbilityRouter.handleInput(p, slot, false);
	}

	// ================================================================ registry

	@GameTest(template = EMPTY_STRUCTURE)
	public void batchPowersRegistered(GameTestHelper helper) {
		helper.assertTrue(Powers.count() == 27, "expected 27 powers");
		for (String key : KEYS) {
			Power p = Powers.byKey(key);
			helper.assertTrue(p != null && p.abilities().size() == 8, key + " must define 8 abilities");
			for (AbilitySlot slot : AbilitySlot.values()) {
				Ability a = p.ability(slot);
				helper.assertTrue(a != null && a.slot() == slot, key + " slot " + slot + " mismapped");
				helper.assertTrue(AbilityHandlers.has(p, a), key + "/" + a.id() + " has no handler");
			}
		}
		helper.assertTrue(MutationMeters.get("power_10_telekinesis", "psi") != null
				&& MutationMeters.get("power_10_telekinesis", "psi").always(), "Psi must be an always-on HUD meter");
		for (String flag : new String[] { "p10.eyes", "p15.cloak", "p15.prism", "p19.shadow_form", "p19.shadow_walk",
				"p23.field", "p26.hover" }) {
			helper.assertTrue(MutationVisuals.registeredFlags().contains(flag), "missing visual flag " + flag);
		}
		helper.succeed();
	}

	// ================================================================ 10 Telekinesis

	@GameTest(template = EMPTY_STRUCTURE, batch = "revamp_d_tk_orbit", timeoutTicks = 120)
	public void telekinesisGrabOrbitsThenThrowsOne(GameTestHelper helper) {
		ServerPlayer p = player(helper, "power_10_telekinesis");
		Power tk = power("power_10_telekinesis");
		ExperimentalPowers.setResource(p, tk, "psi", TelekinesisHandlers.MAX_PSI, TelekinesisHandlers.MAX_PSI);
		Zombie z = zombieAt(helper, p, 0, 4);
		aimAt(p, z);
		float psiBefore = ExperimentalPowers.getResource(p, tk, "psi");

		press(p, 5); // V: grab into the orbit

		helper.assertTrue(TelekinesisHandlers.orbitSize(p) == 1, "V should pull the zombie into the orbit");
		helper.assertTrue(ExperimentalPowers.getResource(p, tk, "psi") < psiBefore, "grabbing should cost Psi");
		helper.runAfterDelay(25, () -> {
			helper.assertTrue(z.distanceTo(p) < 5.0, "an orbiting zombie circles close to its telekinetic");
			helper.assertTrue(MutationVisuals.hasFlag(p, "p10.eyes"), "holding an orbit lights the purple eyes");
			p.setXRot(-89.0f); // look straight up: nothing grabbable -> V throws one instead
			ExperimentalPowers.triggerCooldown(p, tk, tk.ability(AbilitySlot.SLOT_5), 0);
			press(p, 5);
			helper.assertTrue(TelekinesisHandlers.orbitSize(p) == 0, "V with nothing aimed at throws an orbiter");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "revamp_d_tk_launch")
	public void telekinesisOrbitCapsAtThreeAndLaunchEmptiesIt(GameTestHelper helper) {
		ServerPlayer p = player(helper, "power_10_telekinesis");
		Power tk = power("power_10_telekinesis");
		ExperimentalPowers.setResource(p, tk, "psi", TelekinesisHandlers.MAX_PSI, TelekinesisHandlers.MAX_PSI);
		for (int i = 0; i < 4; i++) {
			ItemEntity item = new ItemEntity(helper.getLevel(), p.getX() + 1, p.getY() + 1, p.getZ() + i,
					new ItemStack(Items.COBBLESTONE));
			helper.getLevel().addFreshEntity(item);
			TelekinesisHandlers.addToOrbit(p, item);
		}
		helper.assertTrue(TelekinesisHandlers.orbitSize(p) == TelekinesisHandlers.ORBIT_MAX, "the orbit holds at most 3");

		press(p, 7); // H: Launch Orbit

		helper.assertTrue(TelekinesisHandlers.orbitSize(p) == 0, "Launch Orbit throws everything");
		helper.assertFalse(ExperimentalPowers.cooldownReady(p, tk, tk.ability(AbilitySlot.SLOT_7)), "Launch Orbit goes on cooldown");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "revamp_d_tk_lock", timeoutTicks = 120)
	public void telekinesisMindLockFreezesInMidAir(GameTestHelper helper) {
		ServerPlayer p = player(helper, "power_10_telekinesis");
		Power tk = power("power_10_telekinesis");
		ExperimentalPowers.setResource(p, tk, "psi", TelekinesisHandlers.MAX_PSI, TelekinesisHandlers.MAX_PSI);
		Zombie z = zombieAt(helper, p, 0, 5);
		aimAt(p, z);
		double y0 = z.getY();

		press(p, 8); // N: Mind Lock

		helper.assertTrue(TelekinesisHandlers.isLocked(z), "Mind Lock should hold the target");
		helper.runAfterDelay(20, () -> {
			helper.assertTrue(z.getY() > y0 + 0.8, "a Mind-Locked target hangs in the air");
			helper.assertTrue(z.getTarget() == null, "a Mind-Locked mob cannot target anything");
		});
		helper.runAfterDelay(TelekinesisHandlers.MIND_LOCK_TICKS + 10, () -> {
			helper.assertFalse(TelekinesisHandlers.isLocked(z), "the lock releases after 4 seconds");
			helper.succeed();
		});
	}

	// ================================================================ 11 Teleportation

	@GameTest(template = EMPTY_STRUCTURE, batch = "revamp_d_bamf", skyAccess = true, timeoutTicks = 120)
	public void bamfStrikeHitsAndChains(GameTestHelper helper) {
		ServerPlayer p = player(helper, "power_11_teleportation");
		Power tp = power("power_11_teleportation");
		Zombie first = zombieAt(helper, p, 0, 4);
		Zombie second = zombieAt(helper, p, 3, 4);
		aimAt(p, first);
		float h1 = first.getHealth();
		float h2 = second.getHealth();

		press(p, 7); // H: Bamf Strike

		helper.assertTrue(first.getHealth() < h1, "Bamf Strike should hit the aimed target");
		helper.assertTrue(p.distanceTo(first) < 3.5, "Bamf Strike lands you next to the target");
		helper.assertFalse(ExperimentalPowers.cooldownReady(p, tp, tp.ability(AbilitySlot.SLOT_7)), "Bamf goes on cooldown");
		helper.runAfterDelay(30, () -> {
			helper.assertTrue(second.getHealth() < h2, "Bamf Strike should chain to the second enemy");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "revamp_d_swap", skyAccess = true)
	public void swapTradesPlaces(GameTestHelper helper) {
		ServerPlayer p = player(helper, "power_11_teleportation");
		Zombie z = zombieAt(helper, p, 0, 4);
		aimAt(p, z);
		Vec3 mine = p.position();
		Vec3 theirs = z.position();

		press(p, 8); // N: Swap

		helper.assertTrue(p.position().distanceTo(theirs) < 1.0, "Swap moves you to the target's spot");
		helper.assertTrue(z.position().distanceTo(mine) < 1.0, "Swap moves the target to your spot");
		helper.succeed();
	}

	// ================================================================ 15 Light

	@GameTest(template = EMPTY_STRUCTURE, batch = "revamp_d_mirror", timeoutTicks = 60)
	public void mirrorImagesSpawnWearIllusionsAndShatter(GameTestHelper helper) {
		ServerPlayer p = player(helper, "power_15_invisibility_light_manipulation");
		p.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));

		press(p, 5); // V: Mirror Images

		List<MirrorImageEntity> images = helper.getLevel().getEntitiesOfClass(MirrorImageEntity.class,
				new AABB(p.blockPosition()).inflate(8.0), m -> m.ownerId().map(p.getUUID()::equals).orElse(false));
		helper.assertTrue(images.size() >= 2, "Mirror Images should spawn at least two images, got " + images.size());
		MirrorImageEntity img = images.get(0);
		helper.assertTrue(MirrorImageEntity.isIllusion(img.getItemBySlot(EquipmentSlot.CHEST)),
				"an image wears only stamped illusion copies of the caster's gear");
		// an illusion stack can never exist as a real item
		ItemEntity leaked = new ItemEntity(helper.getLevel(), p.getX(), p.getY() + 1, p.getZ(),
				img.getItemBySlot(EquipmentSlot.CHEST).copy());
		helper.getLevel().addFreshEntity(leaked);
		img.hurt(helper.getLevel().damageSources().generic(), 1.0f);
		helper.assertTrue(img.isRemoved(), "any hit shatters an image");
		helper.runAfterDelay(3, () -> {
			helper.assertTrue(leaked.isRemoved(), "an illusion item entity is deleted the moment it appears");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "revamp_d_blade", timeoutTicks = 60)
	public void hardLightBladeConjuresAndVanishes(GameTestHelper helper) {
		ServerPlayer p = player(helper, "power_15_invisibility_light_manipulation");
		Power light = power("power_15_invisibility_light_manipulation");

		press(p, 7); // H: Hard-Light Blade

		helper.assertTrue(HardLightBladeItem.isBlade(p.getMainHandItem()), "the blade appears in the main hand");
		// dropping one never leaves a real item behind
		ItemEntity dropped = new ItemEntity(helper.getLevel(), p.getX(), p.getY() + 1, p.getZ(),
				BatchDContent.HARD_LIGHT_BLADE.create(p, helper.getLevel().getGameTime() + 100));
		helper.getLevel().addFreshEntity(dropped);
		// switch away from it: the light goes out
		p.getInventory().selected = (p.getInventory().selected + 1) % 9;
		helper.runAfterDelay(4, () -> {
			helper.assertTrue(dropped.isRemoved(), "a dropped blade is deleted on the spot");
			helper.assertFalse(HardLightBladeItem.carries(p), "the blade vanishes once it leaves the hand");
			helper.assertTrue(ExperimentalPowers.getResource(p, light, "blade_until") < 0.5f, "blade state cleared");
			helper.assertFalse(ExperimentalPowers.cooldownReady(p, light, light.ability(AbilitySlot.SLOT_7)),
					"the blade's cooldown starts when it fades");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "revamp_d_prism", timeoutTicks = 60)
	public void prismShieldReflectsFrontalProjectiles(GameTestHelper helper) {
		ServerPlayer p = player(helper, "power_15_invisibility_light_manipulation");
		Power light = power("power_15_invisibility_light_manipulation");

		AbilityRouter.handleInput(p, 8, true); // N: hold Prism Shield
		helper.assertTrue(InvisibilityLightHandlers.prismActive(p), "holding N raises the prism");

		Zombie shooter = zombieAt(helper, p, 0, 6);
		Arrow arrow = new Arrow(EntityType.ARROW, helper.getLevel());
		arrow.setPos(p.getX(), p.getEyeY(), p.getZ() + 3.0);
		arrow.setOwner(shooter);
		arrow.setDeltaMovement(0, 0, -0.9);
		helper.getLevel().addFreshEntity(arrow);
		// the damage rule: an arrow fired from in front is refracted away
		helper.assertTrue(InvisibilityLightHandlers.prismReflects(p,
				helper.getLevel().damageSources().arrow(arrow, shooter), 5.0f), "a frontal arrow hit is reflected");

		helper.runAfterDelay(3, () -> {
			helper.assertTrue(arrow.getOwner() == p || arrow.getDeltaMovement().z > 0.0,
					"the prism bounces an incoming arrow back out");
			AbilityRouter.handleInput(p, 8, false);
			helper.assertFalse(InvisibilityLightHandlers.prismActive(p), "releasing N lowers the prism");
			helper.assertTrue(ExperimentalPowers.getResource(p, light, "prism") < InvisibilityLightHandlers.MAX_PRISM,
					"the prism bar drains while held");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "revamp_d_cloak", timeoutTicks = 40)
	public void cloakHidesArmourAndItemsForEveryone(GameTestHelper helper) {
		ServerPlayer p = player(helper, "power_15_invisibility_light_manipulation");

		press(p, 6); // C: Cloak

		helper.assertTrue(p.hasEffect(MobEffects.INVISIBILITY), "the cloak makes you invisible");
		helper.runAfterDelay(10, () -> {
			helper.assertTrue(MutationVisuals.hasFlag(p, "p15.cloak"), "the all-viewer cloak flag is on");
			helper.assertTrue(InvisibilityLightHandlers.hideArmor(p), "armour / held items are hidden");
			helper.succeed();
		});
	}

	// ================================================================ 19 Shadow

	@GameTest(template = EMPTY_STRUCTURE, batch = "revamp_d_bind")
	public void shadowBindRootsTheTarget(GameTestHelper helper) {
		ServerPlayer p = player(helper, "power_19_shadow_manipulation");
		Power sh = power("power_19_shadow_manipulation");
		Zombie z = zombieAt(helper, p, 0, 5);
		aimAt(p, z);
		float h = z.getHealth();

		press(p, 5); // V: Shadow Bind

		helper.assertTrue(ShadowManipulationHandlers.isBound(z), "Shadow Bind marks the target bound");
		helper.assertTrue(z.hasEffect(MobEffects.MOVEMENT_SLOWDOWN) && z.hasEffect(MobEffects.WEAKNESS),
				"a bound target is rooted and weakened");
		helper.assertTrue(z.getHealth() < h, "Shadow Bind deals damage");
		helper.assertFalse(ExperimentalPowers.cooldownReady(p, sh, sh.ability(AbilitySlot.SLOT_5)), "Shadow Bind goes on cooldown");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "revamp_d_walk", timeoutTicks = 60)
	public void shadowWalkHidesDrainsAndSurfaces(GameTestHelper helper) {
		ServerPlayer p = player(helper, "power_19_shadow_manipulation");
		Power sh = power("power_19_shadow_manipulation");
		ExperimentalPowers.setResource(p, sh, "shadow_walk", ShadowManipulationHandlers.MAX_WALK, ShadowManipulationHandlers.MAX_WALK);

		press(p, 7); // H: Shadow Walk

		helper.assertTrue(ShadowManipulationHandlers.shadowWalking(p), "H sinks you into the shadows");
		helper.runAfterDelay(10, () -> {
			helper.assertTrue(p.hasEffect(MobEffects.INVISIBILITY), "a shadow walker is invisible");
			helper.assertTrue(ExperimentalPowers.getResource(p, sh, "shadow_walk") < ShadowManipulationHandlers.MAX_WALK,
					"Shadow Walk drains its bar");
			helper.assertTrue(MutationVisuals.hasFlag(p, "p19.shadow_walk"), "the all-viewer walk flag is on");
			press(p, 1); // R: Shadow Bolt -- attacking surfaces you
			helper.assertFalse(ShadowManipulationHandlers.shadowWalking(p), "an attack ability surfaces a shadow walker");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "revamp_d_servant", timeoutTicks = 40)
	public void shadowServantNeverTurnsOnItsOwner(GameTestHelper helper) {
		ServerPlayer p = player(helper, "power_19_shadow_manipulation");
		Zombie z = zombieAt(helper, p, 3, 3);

		press(p, 8); // N: Shadow Servant

		List<ShadowServantEntity> servants = helper.getLevel().getEntitiesOfClass(ShadowServantEntity.class,
				new AABB(p.blockPosition()).inflate(8.0), s -> s.ownerId().map(p.getUUID()::equals).orElse(false));
		helper.assertTrue(servants.size() == 1, "N raises exactly one servant");
		ShadowServantEntity s = servants.get(0);
		helper.assertFalse(s.mayTarget(p), "a servant never targets its owner");
		s.setTarget(p);
		helper.assertTrue(s.getTarget() != p, "setTarget(owner) is refused");
		helper.assertTrue(s.isInvulnerableTo(helper.getLevel().damageSources().playerAttack(p)),
				"the owner cannot hurt their own servant");
		helper.assertTrue(s.mayTarget(z), "a servant will fight hostiles");
		helper.runAfterDelay(15, () -> {
			helper.assertTrue(s.getTarget() == null || s.getTarget() == z, "the servant only ever picks the hostile");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "revamp_d_form", timeoutTicks = 40)
	public void shadowFormRaisesTheSilhouetteFlag(GameTestHelper helper) {
		ServerPlayer p = player(helper, "power_19_shadow_manipulation");
		Power sh = power("power_19_shadow_manipulation");
		ExperimentalPowers.setResource(p, sh, "shadow_cloak", 115, 115);

		press(p, 6); // C: Shadow Cloak / Form

		helper.runAfterDelay(10, () -> {
			helper.assertTrue(MutationVisuals.hasFlag(p, "p19.shadow_form"), "Shadow Form shows the silhouette overlay");
			helper.succeed();
		});
	}

	// ================================================================ 23 Gravity

	@GameTest(template = EMPTY_STRUCTURE, batch = "revamp_d_invert", skyAccess = true, timeoutTicks = 200)
	public void invertLiftsThenCrashes(GameTestHelper helper) {
		ServerPlayer p = player(helper, "power_23_gravity_manipulation");
		Zombie z = zombieAt(helper, p, 0, 5);
		aimAt(p, z);
		double y0 = z.getY();
		float h = z.getHealth();

		press(p, 7); // H: Invert

		helper.assertTrue(GravityHandlers.isInverted(z), "Invert flips the target's gravity");
		helper.runAfterDelay(15, () -> helper.assertTrue(z.getY() > y0 + 2.0, "an inverted target falls UP"));
		helper.runAfterDelay(150, () -> {
			helper.assertFalse(GravityHandlers.isInverted(z), "gravity comes back");
			helper.assertTrue(!z.isAlive() || z.getHealth() < h, "the crash back down hurts");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "revamp_d_heavy", timeoutTicks = 80)
	public void heavyGroundPinsThingsDown(GameTestHelper helper) {
		ServerPlayer p = player(helper, "power_23_gravity_manipulation");
		helper.setBlock(new BlockPos(2, 1, 2), Blocks.STONE);
		p.setXRot(90.0f);
		Zombie flier = zombieAt(helper, p, 1, 3);
		flier.setPos(flier.getX(), flier.getY() + 5.0, flier.getZ());
		flier.setNoGravity(true);
		double y0 = flier.getY();

		press(p, 8); // N: Heavy Ground

		helper.assertTrue(GravityHandlers.hasHeavyGround(p), "N lays a Heavy Ground zone");
		helper.runAfterDelay(12, () -> {
			helper.assertTrue(flier.getY() < y0 - 1.0, "anything airborne inside is dragged down (even a no-gravity mob)");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "revamp_d_field", timeoutTicks = 40)
	public void gravityFieldShowsTheVioletShell(GameTestHelper helper) {
		ServerPlayer p = player(helper, "power_23_gravity_manipulation");
		Power g = power("power_23_gravity_manipulation");
		ExperimentalPowers.setResource(p, g, "nexus_bar", 115, 115);

		press(p, 6); // C: Gravitational Nexus

		helper.runAfterDelay(10, () -> {
			helper.assertTrue(MutationVisuals.hasFlag(p, "p23.field"), "the Gravity Field shell flag is on");
			helper.succeed();
		});
	}

	// ================================================================ 26 Magnetism

	@GameTest(template = EMPTY_STRUCTURE, batch = "revamp_d_disarm")
	public void disarmRipsIronWithoutDuplicating(GameTestHelper helper) {
		ServerPlayer p = player(helper, "power_26_magnetic_manipulation");
		Power mag = power("power_26_magnetic_manipulation");
		Zombie bare = zombieAt(helper, p, 0, 4);
		aimAt(p, bare);

		press(p, 8); // N on a bare zombie: nothing to rip, no cooldown
		helper.assertTrue(ExperimentalPowers.cooldownReady(p, mag, mag.ability(AbilitySlot.SLOT_8)),
				"Disarm with no metal must not spend its cooldown");

		bare.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
		press(p, 8);

		helper.assertTrue(bare.getMainHandItem().isEmpty(), "Disarm rips the iron sword away");
		List<ItemEntity> swords = helper.getLevel().getEntitiesOfClass(ItemEntity.class,
				new AABB(bare.blockPosition()).inflate(4.0), i -> i.getItem().is(Items.IRON_SWORD));
		helper.assertTrue(swords.size() == 1 && swords.get(0).getItem().getCount() == 1,
				"exactly one sword drops -- moved, never copied");
		helper.assertFalse(ExperimentalPowers.cooldownReady(p, mag, mag.ability(AbilitySlot.SLOT_8)), "Disarm goes on cooldown");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "revamp_d_disarm2")
	public void disarmNeverTouchesMjolnirOrGold(GameTestHelper helper) {
		Zombie z = helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 2, 4));
		z.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(com.projecthero.mod.item.ModItems.MJOLNIR));
		z.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.GOLDEN_HELMET));
		helper.assertTrue(MagneticHandlers.ripMetal(z).isEmpty(), "Mjolnir and gold are never magnetic");
		helper.assertTrue(z.getMainHandItem().is(com.projecthero.mod.item.ModItems.MJOLNIR), "Mjolnir stays put");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "revamp_d_hover", skyAccess = true, timeoutTicks = 40)
	public void magnetoHoverNeedsMetal(GameTestHelper helper) {
		ServerPlayer p = player(helper, "power_26_magnetic_manipulation");
		Power mag = power("power_26_magnetic_manipulation");
		ExperimentalPowers.setResource(p, mag, "hover", MagneticHandlers.MAX_HOVER, MagneticHandlers.MAX_HOVER);

		p.setPos(p.getX(), p.getY() + 30.0, p.getZ()); // up in open air: no metal anywhere below
		press(p, 7); // H with no metal anywhere
		helper.assertFalse(MagneticHandlers.hovering(p), "no metal nearby -- nothing to push against");

		p.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_INGOT));
		press(p, 7);
		helper.assertTrue(MagneticHandlers.hovering(p), "holding iron lets you hover");
		helper.assertTrue(p.getAbilities().mayfly, "hovering grants flight while metal is in reach");
		helper.runAfterDelay(10, () -> {
			helper.assertTrue(ExperimentalPowers.getResource(p, mag, "hover") < MagneticHandlers.MAX_HOVER,
					"hovering drains its bar");
			press(p, 7);
			helper.assertFalse(MagneticHandlers.hovering(p), "H again ends the hover");
			helper.assertFalse(p.getAbilities().mayfly, "ending the hover takes the flight away");
			helper.succeed();
		});
	}
}
