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

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.5 Symbiote moveset v3 ({@code SYMBIOTE_REFERENCE.md} "Moveset v3"): the new cooldowns (R / G 1 s, Shift+R 4 s, every Z 8 s,
 * hold Z 8 s, Spike Nova 8 s, Spike Surge 10 s on its own entry), the Thorns aura (a husk is hit, a squadmate is not), Spike Surge's
 * longer reach, Spike Nova hitting a ring of mobs, the grab without a time limit (crush, gentle set-down, throw, squadmates), and V: a tap
 * still cycles the Form, a hold is the wheel (no cycle), a wheel pick obeys the Form rules. Mock players are not reliably ticked, so the
 * tests call {@link SymbioteAbilityManager#serverTick} themselves. Damage is asserted as "took some" (the world may be on Hard).
 */
public class SymbioteMovesetV3GameTests implements FabricGameTest {

	/** A survival host, suit on, at the middle of a cleared 6 x 6 stone floor, facing +Z. */
	private static ServerPlayer host(GameTestHelper helper) {
		return host(helper, 4.0);
	}

	/** The same, at relative z {@code z} (the grab tests stand near the back wall: a held target is 3.9 blocks out in front). */
	private static ServerPlayer host(GameTestHelper helper, double z) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		for (BlockPos pos : BlockPos.betweenClosed(helper.absolutePos(new BlockPos(1, 2, 1)), helper.absolutePos(new BlockPos(6, 6, 6)))) {
			helper.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
		}
		for (BlockPos pos : BlockPos.betweenClosed(helper.absolutePos(new BlockPos(1, 1, 1)), helper.absolutePos(new BlockPos(6, 1, 6)))) {
			helper.getLevel().setBlock(pos, Blocks.STONE.defaultBlockState(), 2);
		}
		Vec3 at = helper.absoluteVec(new Vec3(4.0, 2.0, z));
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

	private static void press(ServerPlayer p, AbilitySlot slot, boolean down) {
		SymbioteAbilityManager.handle(p, slot, down);
	}

	private static void tap(ServerPlayer p, AbilitySlot slot) {
		press(p, slot, true);
		press(p, slot, false);
	}

	private static void shiftTap(ServerPlayer p, AbilitySlot slot) {
		p.setShiftKeyDown(true);
		tap(p, slot);
		p.setShiftKeyDown(false);
	}

	private static long cd(ServerPlayer p, int index) {
		return Math.max(0L, Symbiote.state(p).abilityCooldowns.get(index) - p.level().getGameTime());
	}

	private static void clearCooldowns(ServerPlayer p) {
		SymbioteState c = Symbiote.state(p).copy();
		for (int i = 0; i < c.abilityCooldowns.size(); i++) {
			c.abilityCooldowns.set(i, 0L);
		}
		p.setAttached(ModAttachments.SYMBIOTE_STATE, c);
		SymbioteVitals v = SymbioteVitalsManager.vitals(p).copy();
		v.spikeConeReadyAt = 0L;
		p.setAttached(ModAttachments.SYMBIOTE_VITALS, v);
	}

	private static Husk husk(GameTestHelper helper, Vec3 absolute) {
		Husk h = helper.spawn(EntityType.HUSK, helper.relativeVec(absolute));
		h.setPersistenceRequired();
		h.setNoAi(true);
		h.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 20 * 60, 0, false, false));
		return h;
	}

	private static float lost(LivingEntity e) {
		return e.getMaxHealth() - e.getHealth();
	}

	private static void thorns(GameTestHelper helper, ServerPlayer p) {
		helper.assertTrue(SymbioteVitalsManager.selectForm(p, SymbioteVitalsManager.FORM_THORNS), "picked Thorns");
		helper.assertTrue(SymbioteVitalsManager.thornsMode(p), "Thorns form is up");
	}

	/** Toward the room's far corner (the longest free line from the host in the middle of the 8 x 8 structure). */
	private static final Vec3 DIAG = new Vec3(-1, 0, -1).normalize();

	/** A husk with its near side about {@code gap} blocks from the host's side (measured flat, centre to centre minus both half widths). */
	private static Husk huskAtGap(GameTestHelper helper, ServerPlayer p, double gap) {
		double centre = p.getBbWidth() * 0.5 + 0.3 + gap;
		return husk(helper, p.position().add(DIAG.scale(centre)));
	}

	// ---------------- cooldowns ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void theNewCooldownNumbers(GameTestHelper helper) {
		ServerPlayer p = host(helper);
		helper.assertTrue(SymbioteState.COOLDOWN_SLOTS == 18 && Symbiote.state(p).abilityCooldowns.size() == 18, "18 cooldown entries");
		tap(p, AbilitySlot.SLOT_1);
		helper.assertTrue(cd(p, 0) > 15 && cd(p, 0) <= 20, "R Tendril Strike: 1 s (" + cd(p, 0) + ")");
		tap(p, AbilitySlot.SLOT_2);
		helper.assertTrue(cd(p, 1) > 15 && cd(p, 1) <= 20, "G Spike Shot: 1 s (" + cd(p, 1) + ")");
		shiftTap(p, AbilitySlot.SLOT_1);
		helper.assertTrue(cd(p, SymbioteAbilityManager.CDI_SWEEP) > 70 && cd(p, SymbioteAbilityManager.CDI_SWEEP) <= 80, "Shift+R Tendril Sweep: 4 s");
		tap(p, AbilitySlot.SLOT_4);
		helper.assertTrue(cd(p, 3) > 150 && cd(p, 3) <= 160, "Z Tendril Barrage: 8 s (" + cd(p, 3) + ")");
		long now = p.level().getGameTime();
		shiftTap(p, AbilitySlot.SLOT_2);
		long nova = SymbioteVitalsManager.vitals(p).spikeConeReadyAt - now;
		helper.assertTrue(nova > 150 && nova <= 160, "Shift+G Spike Nova: 8 s on its own entry (" + nova + ")");
		for (SymbioteWeapon w : SymbioteWeapon.values()) {
			helper.assertTrue(w.zCooldown == 160, "weapon Z 8 s: " + w);
		}
		helper.assertTrue(SymbioteAbilityManager.CD_ERUPTION == 160, "hold Z Ground Eruption: 8 s");
		// Spike Surge (Z in Thorns) on its own entry, not Z's tap entry
		clearCooldowns(p);
		thorns(helper, p);
		tap(p, AbilitySlot.SLOT_4);
		helper.assertTrue(cd(p, SymbioteAbilityManager.CDI_SURGE) > 190 && cd(p, SymbioteAbilityManager.CDI_SURGE) <= 200, "Spike Surge: 10 s");
		helper.assertTrue(cd(p, 3) == 0L, "and Z's tap entry untouched (no Barrage)");
		helper.assertTrue(SymbioteAbilityManager.surging(p, p.level().getGameTime()), "surging");
		// the Shift variants never block the plain key
		clearCooldowns(p);
		shiftTap(p, AbilitySlot.SLOT_1);
		tap(p, AbilitySlot.SLOT_1);
		helper.assertTrue(cd(p, 0) > 0L, "R fires while Shift+R cools");
		shiftTap(p, AbilitySlot.SLOT_2);
		tap(p, AbilitySlot.SLOT_2);
		helper.assertTrue(cd(p, 1) > 0L, "G fires while Spike Nova cools");
		helper.succeed();
	}

	// ---------------- Thorns aura / Spike Surge ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void theThornsAuraHurtsAHuskButNotASquadmate(GameTestHelper helper) {
		ServerPlayer p = host(helper);
		ServerPlayer mate = helper.makeMockServerPlayerInLevel();
		mate.setGameMode(GameType.SURVIVAL);
		var squads = com.projecthero.mod.squad.SquadManager.get(helper.getLevel().getServer());
		var squad = squads.create("SymV3Squad" + helper.getLevel().getGameTime(), p.getUUID());
		squads.addMember(squad, mate.getUUID());
		try {
			Husk h = huskAtGap(helper, p, 0.6);
			Vec3 m = p.position().add(DIAG.scale(-(p.getBbWidth() * 0.5 + 0.3 + 0.5)));
			mate.moveTo(m.x, m.y, m.z);
			SymbioteAbilityManager.serverTick(p);
			helper.assertTrue(lost(h) == 0.0f, "Normal form: no aura");
			thorns(helper, p);
			SymbioteAbilityManager.serverTick(p);
			helper.assertTrue(lost(h) > 0.0f && h.hasEffect(MobEffects.WITHER), "Thorns: the husk 0.6 blocks off is stabbed and withered");
			helper.assertTrue(!mate.hasEffect(MobEffects.WITHER), "the squadmate is not");
			float after = h.getHealth();
			SymbioteAbilityManager.serverTick(p);
			helper.assertTrue(h.getHealth() == after, "nothing again inside the 15-tick interval");
		} finally {
			squads.disband(squad);
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void spikeSurgeReachesFurther(GameTestHelper helper) {
		ServerPlayer p = host(helper);
		Husk h = huskAtGap(helper, p, 2.6);
		thorns(helper, p);
		SymbioteAbilityManager.serverTick(p);
		helper.assertTrue(lost(h) == 0.0f, "2.6 blocks off is out of the 1.5-block aura");
		tap(p, AbilitySlot.SLOT_4);
		helper.assertTrue(SymbioteAbilityManager.surging(p, p.level().getGameTime()), "Z in Thorns: Spike Surge");
		SymbioteAbilityManager.serverTick(p);
		helper.assertTrue(lost(h) > 0.0f, "surging, the aura reaches 3.5 blocks: the husk is hit");
		helper.assertTrue(lost(h) >= SymbioteAbilityManager.SURGE_DAMAGE * 0.4f, "for the surge's 5");
		helper.succeed();
	}

	// ---------------- Spike Nova ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void spikeNovaHitsARingOfMobs(GameTestHelper helper) {
		ServerPlayer p = host(helper);
		java.util.List<Husk> ring = new java.util.ArrayList<>();
		for (int i = 0; i < 6; i++) {
			double a = Math.PI * 2 * i / 6;
			ring.add(husk(helper, p.position().add(Math.cos(a) * 2.4, 0, Math.sin(a) * 2.4)));
		}
		shiftTap(p, AbilitySlot.SLOT_2);
		helper.assertTrue(p.getAttachedOrCreate(ModAttachments.SYMBIOTE_VITALS).animId == SymbioteAnim.SPIKE_NOVA, "the Nova pose");
		for (Husk h : ring) {
			helper.assertTrue(lost(h) > 0.0f, "every husk in the ring is hit");
			MobEffectInstance w = h.getEffect(MobEffects.WITHER);
			helper.assertTrue(w != null && w.getAmplifier() == 1, "Wither II");
		}
		helper.succeed();
	}

	// ---------------- the grab ----------------

	private static void grab(GameTestHelper helper, ServerPlayer p, LivingEntity target) {
		p.lookAt(EntityAnchorArgument.Anchor.EYES, target.position().add(0, target.getBbHeight() * 0.5, 0));
		p.setShiftKeyDown(true);
		press(p, AbilitySlot.SLOT_6, true);
		press(p, AbilitySlot.SLOT_6, false);
		p.setShiftKeyDown(false);
		helper.assertTrue(SymbioteAbilityManager.holding(p) && Symbiote.state(p).tendrilGrabTargetId == target.getId(), "Shift+C grabbed it");
		p.setXRot(0.0f); // look level again: the target is held at eye height, clear of the floor
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void theGrabHasNoTimeLimitAndCrushes(GameTestHelper helper) {
		ServerPlayer p = host(helper, 1.8);
		Husk h = husk(helper, p.position().add(0, 0, 2.5));
		grab(helper, p, h);
		helper.runAfterDelay(100, () -> {
			SymbioteAbilityManager.serverTick(p);
			helper.assertTrue(SymbioteAbilityManager.holding(p), "still held after 5 s (the old limit was 3.5 s)");
			helper.assertTrue(h.isNoGravity() && lost(h) == 0.0f, "held up, unhurt");
			helper.assertTrue(SymbioteAbilityManager.crush(p), "a left click crushes");
			float one = lost(h);
			helper.assertTrue(one > 0.0f, "the squeeze hurt it");
			helper.assertTrue(!SymbioteAbilityManager.crush(p), "a second click in the same half second does nothing");
			helper.assertTrue(lost(h) == one, "no second squeeze");
			helper.assertTrue(cd(p, AbilitySlot.SLOT_6.index()) == 0L && SymbioteAbilityManager.holding(p), "crushing does not let go");
			helper.runAfterDelay(SymbioteAbilityManager.CRUSH_INTERVAL + 1, () -> {
				helper.assertTrue(SymbioteAbilityManager.crush(p) && lost(h) > one, "half a second later: another squeeze");
				// C throws
				tap(p, AbilitySlot.SLOT_6);
				helper.assertTrue(!SymbioteAbilityManager.holding(p) && !h.isNoGravity(), "C threw it");
				helper.assertTrue(cd(p, AbilitySlot.SLOT_6.index()) > 0L && cd(p, SymbioteAbilityManager.CDI_MAW) == 0L, "grab cooldown, no bite");
				helper.succeed();
			});
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void shiftCSetsItDownGently(GameTestHelper helper) {
		ServerPlayer p = host(helper, 1.8);
		Husk h = husk(helper, p.position().add(0, 0, 2.5));
		grab(helper, p, h);
		SymbioteAbilityManager.serverTick(p);
		helper.assertTrue(h.getY() > p.getY() + 0.5, "held up in front of the host");
		shiftTap(p, AbilitySlot.SLOT_6);
		helper.assertTrue(!SymbioteAbilityManager.holding(p), "Shift+C let go");
		helper.assertTrue(!h.isNoGravity() && lost(h) == 0.0f, "gravity back, no damage");
		helper.assertTrue(Math.abs(h.getY() - p.getY()) < 0.6, "put down on the ground (" + h.getY() + " vs " + p.getY() + ")");
		helper.assertTrue(h.position().subtract(p.position()).horizontalDistance() < 3.0, "just in front of the host");
		helper.assertTrue(SymbioteAbilityManager.safeLanding(h), "and spared fall damage");
		helper.assertFalse(net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.ALLOW_DAMAGE.invoker()
				.allowDamage(h, h.damageSources().fall(), 8.0f), "a fall right after is negated");
		helper.assertTrue(cd(p, SymbioteAbilityManager.CDI_MAW) == 0L, "no Maw Bite on the way");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void aSquadmateCanBeGrabbedButNotHurt(GameTestHelper helper) {
		ServerPlayer p = host(helper, 1.8);
		ServerPlayer mate = helper.makeMockServerPlayerInLevel();
		mate.setGameMode(GameType.SURVIVAL);
		var squads = com.projecthero.mod.squad.SquadManager.get(helper.getLevel().getServer());
		var squad = squads.create("SymV3Grab" + helper.getLevel().getGameTime(), p.getUUID());
		squads.addMember(squad, mate.getUUID());
		try {
			helper.assertTrue(SymbioteAbilityManager.grabbable(p, mate), "a squadmate can be grabbed");
			mate.moveTo(p.getX(), p.getY(), p.getZ() + 2.5);
			grab(helper, p, mate);
			float hp = mate.getHealth();
			helper.assertTrue(SymbioteAbilityManager.crush(p), "squeezed");
			helper.assertTrue(mate.getHealth() == hp, "but not hurt");
			tap(p, AbilitySlot.SLOT_6);
			helper.assertTrue(!SymbioteAbilityManager.holding(p) && SymbioteAbilityManager.safeLanding(mate), "thrown, no fall damage after");
		} finally {
			squads.disband(squad);
		}
		helper.succeed();
	}

	// ---------------- V ----------------

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void tapVStillCyclesAndHoldVIsTheWheel(GameTestHelper helper) {
		ServerPlayer p = host(helper);
		tap(p, AbilitySlot.SLOT_5);
		helper.assertTrue(SymbioteVitalsManager.form(p) == SymbioteVitalsManager.FORM_BLADE, "a tap of V: Normal -> Blade");
		tap(p, AbilitySlot.SLOT_5);
		helper.assertTrue(SymbioteVitalsManager.form(p) == SymbioteVitalsManager.FORM_AXE, "-> Axe");
		// a wheel pick: straight to Thorns, then Normal
		SymbioteAbilityManager.pickForm(p, SymbioteVitalsManager.FORM_THORNS);
		helper.assertTrue(SymbioteVitalsManager.form(p) == SymbioteVitalsManager.FORM_THORNS && !SymbioteVitalsManager.bladeActive(p), "wheel: Thorns");
		SymbioteAbilityManager.pickForm(p, SymbioteVitalsManager.FORM_WHIP);
		helper.assertTrue(SymbioteVitalsManager.form(p) == SymbioteVitalsManager.FORM_WHIP && !SymbioteVitalsManager.thornsMode(p), "wheel: Thorns -> Whip");
		// a weapon is refused with something in the hand (no fallback)
		SymbioteAbilityManager.pickForm(p, SymbioteVitalsManager.FORM_NORMAL);
		p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
		SymbioteAbilityManager.pickForm(p, SymbioteVitalsManager.FORM_MACE);
		helper.assertTrue(SymbioteVitalsManager.form(p) == SymbioteVitalsManager.FORM_NORMAL, "full hand: no Mace from the wheel");
		p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		// a hold of V (the wheel opens on the client): no cycle when it lapses or is let go
		press(p, AbilitySlot.SLOT_5, true);
		helper.assertTrue(SymbioteAbilityManager.pending(p, AbilitySlot.SLOT_5), "the V press waits");
		helper.runAfterDelay(SymbioteAbilityManager.FORM_WHEEL_TICKS + 1, () -> {
			SymbioteAbilityManager.serverTick(p);
			press(p, AbilitySlot.SLOT_5, false);
			helper.assertTrue(SymbioteVitalsManager.form(p) == SymbioteVitalsManager.FORM_NORMAL, "a held V did not cycle");
			// and the wheel's own message lets a pending press lapse at once
			press(p, AbilitySlot.SLOT_5, true);
			SymbioteAbilityManager.formWheelOpened(p);
			press(p, AbilitySlot.SLOT_5, false);
			helper.assertTrue(SymbioteVitalsManager.form(p) == SymbioteVitalsManager.FORM_NORMAL, "wheel opened: no tap");
			// Shift+V is still the shield
			p.setShiftKeyDown(true);
			press(p, AbilitySlot.SLOT_5, true);
			helper.assertTrue(SymbioteAbilityManager.shieldActive(p), "Shift+V: the Shield");
			helper.succeed();
		});
	}
}
