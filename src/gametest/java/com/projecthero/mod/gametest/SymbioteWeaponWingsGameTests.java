package com.projecthero.mod.gametest;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.symbiote.Symbiote;
import com.projecthero.mod.symbiote.SymbioteAbilityManager;
import com.projecthero.mod.symbiote.SymbioteAnim;
import com.projecthero.mod.symbiote.SymbioteState;
import com.projecthero.mod.symbiote.SymbioteVitals;
import com.projecthero.mod.symbiote.SymbioteVitalsManager;
import com.projecthero.mod.symbiote.SymbioteWeapon;
import com.projecthero.mod.symbiote.SymbioteWeaponMoves;
import com.projecthero.mod.symbiote.SymbioteWings;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.5 Symbiote weapons + wings: V's cycle (Normal -> Blade -> Axe -> Mace -> Whip -> Thorns -> Normal, a full hand skipping the
 * weapons), each weapon's numbers on the host's attributes, each weapon's Z (Blade Slash, Cleave, Ground Smash, Lash) firing and doing
 * its effect, the four sharing Z's tap cooldown entry (a weapon swap never hands out a fresh Z), and the Symbiote Wings (not from the
 * ground; from the air: Biomass, their own cooldown, 6 s of flight then a glide, the dissolve on landing). Mock players are not reliably
 * ticked, so the tests call {@link SymbioteAbilityManager#serverTick} themselves; damage is asserted as "took at least half of it".
 */
public class SymbioteWeaponWingsGameTests implements FabricGameTest {

	/** A survival host, suit on, at (3.5, 2, 1.5) facing +Z on a stone floor in a cleared room. */
	private static ServerPlayer host(GameTestHelper helper) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(3.5, 2.0, 1.5));
		BlockPos base = BlockPos.containing(at);
		for (BlockPos pos : BlockPos.betweenClosed(base.offset(-3, 0, -1), base.offset(3, 5, 6))) {
			helper.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
		}
		for (BlockPos pos : BlockPos.betweenClosed(base.offset(-3, -1, -1), base.offset(3, -1, 6))) {
			helper.getLevel().setBlock(pos, Blocks.STONE.defaultBlockState(), 2);
		}
		p.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);
		p.setYHeadRot(0);
		p.setOnGround(true);
		Symbiote.grant(p);
		Symbiote.state(p).toggleReadyAt = 0L;
		Symbiote.toggle(p);
		Symbiote.state(p).transformStartTick -= 10_000L;
		Symbiote.tick(p);
		helper.assertTrue(Symbiote.isActive(p) && SymbioteAbilityManager.hasContext(p), "suited Normal host");
		return p;
	}

	private static void tap(ServerPlayer p, AbilitySlot slot) {
		SymbioteAbilityManager.handle(p, slot, true);
		SymbioteAbilityManager.handle(p, slot, false);
	}

	private static long cd(ServerPlayer p, int index) {
		return Math.max(0L, Symbiote.state(p).abilityCooldowns.get(index) - p.level().getGameTime());
	}

	private static IronGolem golem(GameTestHelper helper, double x, double z) {
		IronGolem g = helper.spawn(EntityType.IRON_GOLEM, new Vec3(x, 2, z));
		g.setPersistenceRequired();
		g.setNoAi(true);
		return g;
	}

	private static Husk husk(GameTestHelper helper, double x, double z) {
		Husk h = helper.spawn(EntityType.HUSK, new Vec3(x, 2, z));
		h.setPersistenceRequired();
		h.setNoAi(true);
		h.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 20 * 60, 0, false, false));
		return h;
	}

	private static float lost(LivingEntity e) {
		return e.getMaxHealth() - e.getHealth();
	}

	/** V until the Form is {@code form} (at most six presses). */
	private static void formTo(ServerPlayer p, int form) {
		for (int i = 0; i < 6 && SymbioteVitalsManager.form(p) != form; i++) {
			tap(p, AbilitySlot.SLOT_5);
		}
	}

	// ---------------- the cycle ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void vCyclesThroughEveryWeapon(GameTestHelper helper) {
		ServerPlayer p = host(helper);
		double baseReach = p.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE);
		double baseDamage = p.getAttributeValue(Attributes.ATTACK_DAMAGE);
		int[] expected = { SymbioteVitalsManager.FORM_BLADE, SymbioteVitalsManager.FORM_AXE, SymbioteVitalsManager.FORM_MACE,
				SymbioteVitalsManager.FORM_WHIP, SymbioteVitalsManager.FORM_THORNS, SymbioteVitalsManager.FORM_NORMAL };
		SymbioteWeapon[] weapons = { SymbioteWeapon.BLADE, SymbioteWeapon.AXE, SymbioteWeapon.MACE, SymbioteWeapon.WHIP };
		int prev = SymbioteVitalsManager.FORM_NORMAL;
		for (int i = 0; i < expected.length; i++) {
			tap(p, AbilitySlot.SLOT_5);
			int form = SymbioteVitalsManager.form(p);
			helper.assertTrue(form == expected[i], "press " + (i + 1) + ": form " + expected[i] + ", was " + form);
			SymbioteVitals v = SymbioteVitalsManager.vitals(p);
			helper.assertTrue(v.prevForm == prev && v.formSince == p.level().getGameTime(), "the change is stamped (growth / melt)");
			prev = form;
			if (i < weapons.length) {
				SymbioteWeapon w = weapons[i];
				helper.assertTrue(SymbioteVitalsManager.bladeActive(p) && SymbioteVitalsManager.weapon(p) == w, w + " out");
				helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.ATTACK_DAMAGE) - (baseDamage + w.bonusDamage)) < 0.01,
						w + ": +" + w.bonusDamage + " melee");
				helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE) - (baseReach + w.reach)) < 0.01,
						w + ": +" + w.reach + " reach");
				helper.assertTrue(!SymbioteVitalsManager.thornsMode(p), "no Thorns with a weapon");
			}
		}
		helper.assertTrue(!SymbioteVitalsManager.bladeActive(p) && !SymbioteVitalsManager.thornsMode(p), "back to Normal");
		helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.ATTACK_DAMAGE) - baseDamage) < 0.01
				&& Math.abs(p.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE) - baseReach) < 0.01, "modifiers gone");
		// a full hand skips every weapon
		p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
		tap(p, AbilitySlot.SLOT_5);
		helper.assertTrue(SymbioteVitalsManager.form(p) == SymbioteVitalsManager.FORM_THORNS, "full hand: Normal -> Thorns");
		helper.succeed();
	}

	// ---------------- each weapon's Z ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void bladeZIsBladeSlash(GameTestHelper helper) {
		ServerPlayer p = host(helper);
		IronGolem g = golem(helper, 3.5, 4.0);
		formTo(p, SymbioteVitalsManager.FORM_BLADE);
		tap(p, AbilitySlot.SLOT_4);
		helper.assertTrue(lost(g) >= 5.0f, "Blade Slash cut the golem (lost " + lost(g) + ")");
		helper.assertTrue(cd(p, 3) > SymbioteWeapon.BLADE.zCooldown - 5, "Z's tap entry cools for Blade Slash's 15 s");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void axeZIsCleaveDownTheLine(GameTestHelper helper) {
		ServerPlayer p = host(helper);
		IronGolem inLine = golem(helper, 3.5, 5.0);
		Husk aside = husk(helper, 6.2, 2.5); // 2.7 blocks to the side (a golem there would touch the barrier wall and suffocate)
		formTo(p, SymbioteVitalsManager.FORM_AXE);
		tap(p, AbilitySlot.SLOT_4);
		helper.assertTrue(SymbioteWeaponMoves.pending(p), "the chop is on its way down");
		helper.assertTrue(p.getAttachedOrCreate(ModAttachments.SYMBIOTE_VITALS).animId == SymbioteAnim.AXE_CLEAVE, "Cleave pose");
		helper.assertTrue(lost(inLine) == 0.0f, "nothing before it lands");
		helper.assertTrue(cd(p, 3) > SymbioteWeapon.AXE.zCooldown - 5, "Z's tap entry cools for Cleave's 14 s");
		helper.runAfterDelay(SymbioteWeaponMoves.CLEAVE_DELAY + 1, () -> {
			SymbioteAbilityManager.serverTick(p);
			helper.assertTrue(lost(inLine) >= SymbioteWeaponMoves.CLEAVE_DAMAGE * 0.5f, "Cleave hit the golem in line (lost " + lost(inLine) + ")");
			helper.assertTrue(lost(aside) == 0.0f, "not the one off to the side");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void maceZIsGroundSmash(GameTestHelper helper) {
		ServerPlayer p = host(helper);
		Husk a = husk(helper, 2.5, 4.0);
		Husk b = husk(helper, 5.0, 4.5);
		formTo(p, SymbioteVitalsManager.FORM_MACE);
		tap(p, AbilitySlot.SLOT_4);
		helper.assertTrue(SymbioteWeaponMoves.pending(p), "the mace is coming down");
		helper.runAfterDelay(SymbioteWeaponMoves.SMASH_DELAY + 1, () -> {
			SymbioteAbilityManager.serverTick(p);
			helper.assertTrue(lost(a) >= SymbioteWeaponMoves.SMASH_DAMAGE * 0.5f && lost(b) >= SymbioteWeaponMoves.SMASH_DAMAGE * 0.5f,
					"both husks in the ring were smashed (" + lost(a) + ", " + lost(b) + ")");
			helper.assertTrue(a.hasEffect(MobEffects.MOVEMENT_SLOWDOWN) && b.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "and slowed");
			helper.assertTrue(cd(p, 3) > SymbioteWeapon.MACE.zCooldown - 20, "Z's tap entry cools for Ground Smash's 16 s");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void whipZLashesAndYanks(GameTestHelper helper) {
		ServerPlayer p = host(helper);
		IronGolem h = golem(helper, 3.5, 6.5); // tall enough for the level aim from a suited host's eyes
		formTo(p, SymbioteVitalsManager.FORM_WHIP);
		tap(p, AbilitySlot.SLOT_4);
		helper.runAfterDelay(SymbioteWeaponMoves.LASH_DELAY + 1, () -> {
			SymbioteAbilityManager.serverTick(p);
			helper.assertTrue(lost(h) >= SymbioteWeaponMoves.LASH_DAMAGE * 0.5f, "Lash hit the golem 5 blocks off (lost " + lost(h) + ")");
			helper.assertTrue(h.getDeltaMovement().z < -0.1, "and yanked it toward the host (vz " + h.getDeltaMovement().z + ")");
			helper.assertTrue(cd(p, 3) > SymbioteWeapon.WHIP.zCooldown - 20, "Z's tap entry cools for Lash's 10 s");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void swappingWeaponsNeverHandsOutAFreshZ(GameTestHelper helper) {
		ServerPlayer p = host(helper);
		formTo(p, SymbioteVitalsManager.FORM_AXE);
		tap(p, AbilitySlot.SLOT_4);
		long left = cd(p, 3);
		helper.assertTrue(left > 0, "Cleave cooling");
		formTo(p, SymbioteVitalsManager.FORM_MACE);
		tap(p, AbilitySlot.SLOT_4);
		helper.assertTrue(cd(p, 3) == left, "the Mace's Z is refused while Cleave cools (same entry, untouched)");
		formTo(p, SymbioteVitalsManager.FORM_NORMAL);
		tap(p, AbilitySlot.SLOT_4);
		helper.assertTrue(cd(p, 3) == left, "and so is Barrage");
		helper.succeed();
	}

	// ---------------- the wings ----------------

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 260)
	public void wingsGrowOnlyInTheAirFlySixSecondsThenGlide(GameTestHelper helper) {
		ServerPlayer p = host(helper);
		helper.assertTrue(!SymbioteWings.tryStart(p), "not from the ground");
		helper.assertTrue(SymbioteWings.phase(p) == SymbioteWings.Phase.NONE, "no wings on the ground");
		// up in the air
		p.moveTo(p.getX(), p.getY() + 4.0, p.getZ());
		p.setOnGround(false);
		SymbioteVitals before = SymbioteVitalsManager.vitals(p);
		float hp = before.hp;
		helper.assertTrue(SymbioteWings.tryStart(p), "double-tap in the air grows the wings");
		helper.assertTrue(SymbioteWings.phase(p) == SymbioteWings.Phase.FLYING, "flying");
		helper.assertTrue(Math.abs(SymbioteVitalsManager.vitals(p).hp - (hp - SymbioteWings.BIOMASS_COST)) < 0.01f, "costs 10 Biomass");
		helper.assertTrue(cd(p, SymbioteWings.CDI_WINGS) > SymbioteWings.COOLDOWN_TICKS - 3, "its own cooldown entry (15), 20 s");
		helper.assertTrue(cd(p, 3) == 0 && cd(p, AbilitySlot.SLOT_3.index()) == 0 && cd(p, SymbioteAbilityManager.CDI_DIVE) == 0,
				"no other move's cooldown touched");
		helper.assertTrue(!SymbioteWings.tryStart(p), "a second pair can't grow while these are out");
		// keep the mock player airborne and ticked through the flight
		for (int t = 1; t <= SymbioteWings.FLIGHT_TICKS + 4; t += 5) {
			final int at = t;
			helper.runAfterDelay(at, () -> {
				p.setOnGround(false);
				SymbioteAbilityManager.serverTick(p);
			});
		}
		helper.runAfterDelay(SymbioteWings.FLIGHT_TICKS - 10, () -> helper.assertTrue(SymbioteWings.phase(p) == SymbioteWings.Phase.FLYING,
				"still flying before 6 s"));
		helper.runAfterDelay(SymbioteWings.FLIGHT_TICKS + 6, () -> {
			helper.assertTrue(SymbioteWings.phase(p) == SymbioteWings.Phase.GLIDING, "after 6 s: gliding, not flying");
			helper.assertTrue(SymbioteWings.airborne(p), "still on the wings until landing");
			// land
			p.setOnGround(true);
			SymbioteAbilityManager.serverTick(p);
			helper.assertTrue(SymbioteWings.phase(p) == SymbioteWings.Phase.DISSOLVING, "landing dissolves them");
		});
		helper.runAfterDelay(SymbioteWings.FLIGHT_TICKS + 6 + SymbioteWings.DISSOLVE_TICKS + 2, () -> {
			SymbioteAbilityManager.serverTick(p);
			helper.assertTrue(SymbioteWings.phase(p) == SymbioteWings.Phase.NONE, "gone after the dissolve");
			p.setOnGround(false);
			helper.assertTrue(!SymbioteWings.tryStart(p) && cd(p, SymbioteWings.CDI_WINGS) > 0, "still cooling down: no new pair yet");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void wingsNeedTheSuitAndBiomassAndDryAir(GameTestHelper helper) {
		ServerPlayer p = host(helper);
		p.moveTo(p.getX(), p.getY() + 4.0, p.getZ());
		p.setOnGround(false);
		SymbioteVitals c = SymbioteVitalsManager.vitals(p).copy();
		c.hp = SymbioteWings.BIOMASS_COST - 1f;
		p.setAttached(ModAttachments.SYMBIOTE_VITALS, c);
		helper.assertTrue(!SymbioteWings.tryStart(p), "under 10 Biomass: no wings");
		c = SymbioteVitalsManager.vitals(p).copy();
		c.hp = SymbioteVitalsManager.MAX_HP;
		p.setAttached(ModAttachments.SYMBIOTE_VITALS, c);
		SymbioteState s = Symbiote.state(p).copy();
		s.abilityCooldowns.set(SymbioteWings.CDI_WINGS, p.level().getGameTime() + 100);
		p.setAttached(ModAttachments.SYMBIOTE_STATE, s);
		helper.assertTrue(!SymbioteWings.tryStart(p), "cooling down: no wings");
		s = Symbiote.state(p).copy();
		s.abilityCooldowns.set(SymbioteWings.CDI_WINGS, 0L);
		p.setAttached(ModAttachments.SYMBIOTE_STATE, s);
		helper.assertTrue(SymbioteWings.tryStart(p), "ready again: wings");
		// taking the suit off mid-flight lands them at once
		SymbioteState off = Symbiote.state(p).copy();
		off.active = false;
		p.setAttached(ModAttachments.SYMBIOTE_STATE, off);
		SymbioteWings.tick(p);
		helper.assertTrue(SymbioteWings.phase(p) == SymbioteWings.Phase.DISSOLVING, "suit off: the wings dissolve");
		helper.succeed();
	}
}
