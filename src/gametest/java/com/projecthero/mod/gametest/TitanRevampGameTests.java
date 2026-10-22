package com.projecthero.mod.gametest;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.projecthero.mod.hero.AbilityRouter;
import com.projecthero.mod.ironman.ui.IronManUiLayout;
import com.projecthero.mod.titanshifter.TitanMove;
import com.projecthero.mod.titanshifter.TitanPhase;
import com.projecthero.mod.titanshifter.TitanShifter;
import com.projecthero.mod.titanshifter.TitanShifterConfig;
import com.projecthero.mod.titanshifter.TitanType;
import com.projecthero.mod.titanshifter.TitanUi;
import com.projecthero.mod.titanshifter.data.TitanShifterState;
import com.projecthero.mod.titanshifter.entity.TitanCorpseEntity;
import com.projecthero.mod.titanshifter.entity.TitanFormEntity;
import com.projecthero.mod.titanshifter.item.TitanShifterItems;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.7 (user, Titan revamp gameplay): the Titan picker (type selection and re-forging), each type's size, the nape
 * (3x, cut out), severed limbs (sever, effects, regrow), the Steam refund by HP, Partial Shifting, and the UI strings'
 * widths against the HUD / picker layout ({@link TitanUi}, measured with the pessimistic {@link IronManUiLayout#approxWidth}).
 */
public class TitanRevampGameTests implements FabricGameTest {
	private static final int SETTLE = 80; // > transformTicks

	private static ServerPlayer mock(GameTestHelper helper) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 2.0, 2.5));
		p.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);
		p.setYHeadRot(0f);
		try {
			java.lang.reflect.Field f = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
			f.setAccessible(true);
			f.setInt(p, 0);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
		BlockPos base = BlockPos.containing(at);
		for (BlockPos pos : BlockPos.betweenClosed(base.offset(-9, 0, -9), base.offset(9, 18, 9))) {
			helper.getLevel().setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
		}
		return p;
	}

	private static ServerPlayer shifter(GameTestHelper helper, TitanType type) {
		ServerPlayer p = mock(helper);
		TitanShifter.grant(p, type);
		return p;
	}

	private static void pump(GameTestHelper helper, ServerPlayer p) {
		helper.onEachTick(() -> TitanShifter.tick(p));
	}

	private static Zombie dummy(GameTestHelper helper, Vec3 at) {
		Zombie z = EntityType.ZOMBIE.create(helper.getLevel());
		z.moveTo(at.x, at.y, at.z, 0f, 0f);
		z.setNoAi(true);
		z.setNoGravity(true);
		helper.getLevel().addFreshEntity(z);
		return z;
	}

	// ---------------- the picker ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void theSerumPicksATitanAndAnotherReforgesIt(GameTestHelper helper) {
		ServerPlayer p = mock(helper);
		helper.assertFalse(TitanShifter.choose(p, TitanType.FEMALE_TITAN), "no pick without a serum having been drunk");
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(TitanShifterItems.TITAN_SERUM, 2));
		p.getItemInHand(InteractionHand.MAIN_HAND).use(helper.getLevel(), p, InteractionHand.MAIN_HAND);
		helper.assertTrue(TitanShifter.pickPending(p), "drinking opens the picker");
		helper.assertFalse(TitanShifter.isShifter(p), "nothing granted before a pick");
		helper.assertTrue(p.getItemInHand(InteractionHand.MAIN_HAND).getCount() == 2, "the serum is not used up until a pick");
		helper.assertTrue(TitanShifter.choose(p, TitanType.FEMALE_TITAN), "picked the Female Titan");
		helper.assertTrue(TitanShifter.isShifter(p) && TitanShifter.selectedType(p) == TitanType.FEMALE_TITAN, "a Female Titan shifter");
		helper.assertTrue(p.getItemInHand(InteractionHand.MAIN_HAND).getCount() == 1, "one serum used");
		helper.assertFalse(TitanShifter.pickPending(p), "the offer is spent");
		// a second serum re-forges
		p.getItemInHand(InteractionHand.MAIN_HAND).use(helper.getLevel(), p, InteractionHand.MAIN_HAND);
		helper.assertTrue(TitanShifter.choose(p, TitanType.BEAST_TITAN), "re-forged");
		helper.assertTrue(TitanShifter.selectedType(p) == TitanType.BEAST_TITAN, "now the Beast Titan");
		helper.assertTrue(p.getItemInHand(InteractionHand.MAIN_HAND).isEmpty(), "the second serum used");
		// an old save / a plain grant is the Attack Titan
		ServerPlayer old = mock(helper);
		TitanShifter.grant(old);
		helper.assertTrue(TitanShifter.selectedType(old) == TitanType.GENERIC_TITAN, "existing shifters stay Attack Titans");
		helper.assertTrue(TitanType.byId("generic_titan") == TitanType.GENERIC_TITAN && TitanType.byId("nonsense") == TitanType.GENERIC_TITAN,
				"the saved id is kept");
		helper.succeed();
	}

	// ---------------- per-type size ----------------

	private static void sizeTest(GameTestHelper helper, TitanType type, float height, float hp) {
		ServerPlayer p = shifter(helper, type);
		helper.assertTrue(TitanShifter.transform(p), "transform as " + type.id);
		TitanFormEntity form = TitanShifter.formOf(p);
		helper.assertTrue(form != null && form.titanType() == type, "riding a " + type.id);
		helper.assertTrue(Math.abs(form.getBbHeight() - height) < 0.01f, type.id + " is " + height + " tall, got " + form.getBbHeight());
		helper.assertTrue(Math.abs(form.getMaxHealth() - hp) < 0.5f, type.id + " has " + hp + " HP, got " + form.getMaxHealth());
		helper.assertTrue(TitanMove.of(type, 3, true) == TitanMove.signature(type), "Shift+X is the signature");
		helper.assertTrue(TitanMove.of(type, 5, false) == TitanMove.ROAR, "V is the Roar");
		TitanShifter.forceEnd(p, false);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void theAttackTitanIs15Blocks(GameTestHelper helper) {
		sizeTest(helper, TitanType.GENERIC_TITAN, 15.0f, 500.0f);
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void theArmouredTitanIs15Blocks(GameTestHelper helper) {
		sizeTest(helper, TitanType.ARMORED_TITAN, 15.0f, 800.0f);
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void theFemaleTitanIs12Blocks(GameTestHelper helper) {
		sizeTest(helper, TitanType.FEMALE_TITAN, 12.0f, 450.0f);
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void theBeastTitanIs14Blocks(GameTestHelper helper) {
		sizeTest(helper, TitanType.BEAST_TITAN, 14.0f, 600.0f);
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void everyKeyHasATapAndAShiftMove(GameTestHelper helper) {
		for (TitanType t : TitanType.values()) {
			java.util.Set<TitanMove> seen = new java.util.HashSet<>();
			for (int slot = 1; slot <= 6; slot++) {
				for (boolean shift : new boolean[] { false, true }) {
					TitanMove m = TitanMove.of(t, slot, shift);
					helper.assertTrue(m != null, t.id + " key " + slot + (shift ? " (Shift)" : "") + " has a move");
					helper.assertTrue(seen.add(m), t.id + ": " + m + " is on two keys");
				}
			}
		}
		helper.succeed();
	}

	// ---------------- the nape ----------------

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 300)
	public void napeHitsTripleAndAHeavyOneCutsTheShifterOut(GameTestHelper helper) {
		ServerPlayer p = shifter(helper, TitanType.GENERIC_TITAN);
		pump(helper, p);
		TitanShifter.transform(p);
		helper.runAfterDelay(SETTLE, () -> {
			TitanFormEntity form = TitanShifter.formOf(p);
			helper.assertTrue(form != null && TitanShifter.inTitan(p), "in the Titan");
			Vec3 fwd = Vec3.directionFromRotation(0, form.getYRot());
			double h = form.getBbHeight();
			// in front at head height: an ordinary hit (a mob's hit ignores the armour, so 10 is 10)
			Zombie front = dummy(helper, form.position().add(fwd.scale(form.getBbWidth() * 0.5 + 1.5)).add(0, h * 0.85 - 1.74, 0));
			float hp0 = form.getHealth();
			form.hurt(helper.getLevel().damageSources().mobAttack(front), 10.0f);
			float lostFront = hp0 - form.getHealth();
			helper.assertTrue(Math.abs(lostFront - 10.0f) < 0.6f, "a front hit is plain damage, lost " + lostFront);
			// behind, at the nape: 3x
			Zombie back = dummy(helper, form.position().add(fwd.scale(-(form.getBbWidth() * 0.5 + 1.5))).add(0, h * 0.85 - 1.74, 0));
			helper.assertTrue(form.zoneOf(back.getEyePosition()) == TitanFormEntity.ZONE_NAPE, "the zombie behind is at the nape");
			float hp1 = form.getHealth();
			form.invulnerableTime = 0;
			form.hurt(helper.getLevel().damageSources().mobAttack(back), 10.0f);
			float lostNape = hp1 - form.getHealth();
			helper.assertTrue(Math.abs(lostNape - 30.0f) < 1.0f, "a nape hit deals 3x, lost " + lostNape);
			helper.assertTrue(TitanShifter.inTitan(p), "a light nape hit does not cut the shifter out");
			// a heavy one (>= 10% of max HP after the 3x) cuts them out
			form.invulnerableTime = 0;
			form.hurt(helper.getLevel().damageSources().mobAttack(back), 20.0f);
			helper.assertTrue(TitanShifter.phase(p) == TitanPhase.RECOVERING, "cut out -> RECOVERING, was " + TitanShifter.phase(p));
			helper.assertTrue(p.getVehicle() == null && form.isRemoved(), "ejected, the form gone");
			helper.assertTrue(TitanShifter.energy(p) < 1f, "Steam emptied");
			helper.assertTrue(p.getHealth() <= 8.0f, "4 hearts, was " + p.getHealth());
			helper.assertTrue(TitanShifter.transformCooldownRemaining(p) > 20 * 55, "shifting locked for 60 s");
			var corpses = helper.getLevel().getEntitiesOfClass(TitanCorpseEntity.class, new AABB(p.blockPosition()).inflate(24.0));
			helper.assertTrue(corpses.size() == 1 && corpses.get(0).cause() == TitanCorpseEntity.CAUSE_CUT_OUT, "a cut-out corpse left behind");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 300)
	public void napeGuardStopsTheMultiplierAndTheCutOut(GameTestHelper helper) {
		ServerPlayer p = shifter(helper, TitanType.GENERIC_TITAN);
		pump(helper, p);
		TitanShifter.transform(p);
		helper.runAfterDelay(SETTLE, () -> {
			TitanFormEntity form = TitanShifter.formOf(p);
			p.setShiftKeyDown(true);
			AbilityRouter.handleInput(p, 5, true); // Shift+V
			p.setShiftKeyDown(false);
			helper.assertTrue(form.isNapeGuarded(), "guarded");
			Vec3 fwd = Vec3.directionFromRotation(0, form.getYRot());
			Zombie back = dummy(helper, form.position().add(fwd.scale(-(form.getBbWidth() * 0.5 + 1.5))).add(0, form.getBbHeight() * 0.85 - 1.74, 0));
			float hp0 = form.getHealth();
			form.hurt(helper.getLevel().damageSources().mobAttack(back), 30.0f);
			helper.assertTrue(Math.abs((hp0 - form.getHealth()) - 30.0f) < 1.0f, "plain damage while guarded, lost " + (hp0 - form.getHealth()));
			helper.assertTrue(TitanShifter.inTitan(p), "and no cut-out");
			helper.succeed();
		});
	}

	// ---------------- severed limbs ----------------

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 500)
	public void aBigHitSeversALimbWhichRegrows(GameTestHelper helper) {
		ServerPlayer p = shifter(helper, TitanType.GENERIC_TITAN);
		pump(helper, p);
		TitanShifter.transform(p);
		helper.runAfterDelay(SETTLE, () -> {
			TitanFormEntity form = TitanShifter.formOf(p);
			Vec3 fwd = Vec3.directionFromRotation(0, form.getYRot());
			Vec3 right = Vec3.directionFromRotation(0, form.getYRot() + 90.0f);
			// low on the Titan's right, in front: the right leg
			Zombie z = dummy(helper, form.position().add(fwd.scale(form.getBbWidth() * 0.5 + 1.0)).add(right.scale(1.5)));
			helper.assertTrue(form.zoneOf(z.getEyePosition()) == TitanFormEntity.LIMB_R_LEG, "the zombie is at the right leg");
			form.hurt(helper.getLevel().damageSources().mobAttack(z), 40.0f);
			helper.assertTrue(form.hasLimb(TitanFormEntity.LIMB_R_LEG), "a small hit (8%) does not sever");
			form.invulnerableTime = 0;
			form.hurt(helper.getLevel().damageSources().mobAttack(z), 70.0f);
			helper.assertFalse(form.hasLimb(TitanFormEntity.LIMB_R_LEG), "a 14% hit severs the right leg");
			helper.assertTrue(form.severedLimbs() == TitanFormEntity.LIMB_R_LEG && form.isKneeling(), "synced bits, kneeling");
			AbilityRouter.handleInput(p, 3, true); // X = leap: refused on one leg
			helper.assertTrue(TitanShifter.cooldownRemaining(p, TitanMove.LEAP.id) == 0, "no leap on one leg");
			// both arms gone: no punches
			form.setSeveredLimbs(form.severedLimbs() | TitanFormEntity.LIMB_R_ARM | TitanFormEntity.LIMB_L_ARM);
			AbilityRouter.handleInput(p, 1, true);
			helper.assertTrue(TitanShifter.cooldownRemaining(p, TitanMove.PUNCH.id) == 0, "no punch without arms");
			float steam0 = TitanShifter.energy(p);
			helper.runAfterDelay(TitanShifterConfig.limbs().regrowTicks + 20, () -> {
				helper.assertTrue(form.severedLimbs() == 0, "every limb regrew, bits " + form.severedLimbs());
				helper.assertTrue(TitanShifter.energy(p) < steam0 - 10f, "regrowing spent Steam (5 a limb)");
				helper.succeed();
			});
		});
	}

	// ---------------- Steam refund ----------------

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400)
	public void leavingRefundsSteamByTheTitansHealth(GameTestHelper helper) {
		ServerPlayer p = shifter(helper, TitanType.FEMALE_TITAN);
		pump(helper, p);
		TitanShifter.transform(p);
		helper.runAfterDelay(SETTLE, () -> {
			TitanFormEntity form = TitanShifter.formOf(p);
			form.setHealth(form.getMaxHealth() * 0.5f);
			// no passive healing in the way: pretend a hit just landed
			form.markAttacked();
			TitanShifter.requestToggle(p);
			helper.runAfterDelay(TitanShifterConfig.transformation().revertTicks + 3, () -> {
				helper.assertTrue(TitanShifter.phase(p) == TitanPhase.HUMAN, "human again");
				float e = TitanShifter.energy(p);
				helper.assertTrue(e >= 48f && e <= 54f, "half HP refunds about half the bar, got " + e);
				helper.succeed();
			});
		});
	}

	// ---------------- Partial Shifting ----------------

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 300)
	public void partialShiftingMovesWorkInHumanForm(GameTestHelper helper) {
		ServerPlayer p = shifter(helper, TitanType.GENERIC_TITAN);
		pump(helper, p);
		helper.assertTrue(com.projecthero.mod.titanshifter.TitanShifterAbilityManager.hasHumanContext(p), "the human kit owns the keys");
		Zombie z = dummy(helper, p.position().add(0, 0, 3.0));
		float zhp = z.getHealth();
		float steam0 = TitanShifter.energy(p);
		AbilityRouter.handleInput(p, 1, true); // R = Titan Arm
		helper.assertTrue(TitanShifter.cooldownRemaining(p, TitanMove.TITAN_ARM.id) > 0, "Titan Arm recharging");
		helper.assertTrue(Math.abs(steam0 - TitanShifter.energy(p) - (float) TitanShifterConfig.human().titanArmSteam) < 0.01f, "it cost Steam");
		TitanShifterState s = TitanShifter.state(p);
		helper.assertTrue(s.lastAction == com.projecthero.mod.titanshifter.TitanHumanKit.POSE_BASE + TitanMove.TITAN_ARM.ordinal(), "the pose is synced");
		helper.runAfterDelay(TitanMove.TITAN_ARM.impactTick + 3, () -> {
			helper.assertTrue(z.getHealth() < zhp || !z.isAlive(), "the Titan Arm hit the zombie");
			// Shift+X: the cocoon -- nothing gets through
			p.setShiftKeyDown(true);
			AbilityRouter.handleInput(p, 3, true);
			helper.assertTrue(TitanShifter.cocooned(p), "cocooned");
			float hp = p.getHealth();
			p.invulnerableTime = 0;
			p.hurt(helper.getLevel().damageSources().mobAttack(z), 6.0f);
			helper.assertTrue(p.getHealth() >= hp, "untouchable in the cocoon");
			// Shift+G with a full bar: Bite Shift, a fast transformation
			TitanShifterState full = TitanShifter.state(p).copy();
			full.energy = 100f;
			full.abilityReadyAt.remove(TitanShifter.COCOON_KEY);
			p.setAttached(com.projecthero.mod.attachment.ModAttachments.TITAN_SHIFTER_STATE, full);
			AbilityRouter.handleInput(p, 2, true);
			p.setShiftKeyDown(false);
			helper.assertTrue(TitanShifter.phase(p) == TitanPhase.TRANSFORMING, "Bite Shift starts the shift");
			TitanFormEntity form = TitanShifter.formOf(p);
			helper.assertTrue(form != null && form.formStateTicks() == TitanShifterConfig.human().biteShiftTicks, "a fast transformation");
			helper.runAfterDelay(TitanShifterConfig.human().biteShiftTicks + 4, () -> {
				helper.assertTrue(TitanShifter.inTitan(p), "a Titan within a second");
				helper.succeed();
			});
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void steamBurstKnocksBackAndHumanHealingUsesItsOwnPool(GameTestHelper helper) {
		ServerPlayer p = shifter(helper, TitanType.GENERIC_TITAN);
		Zombie z = dummy(helper, p.position().add(0, 0, 2.0));
		z.setNoGravity(false);
		AbilityRouter.handleInput(p, 2, true); // G = Steam Burst
		helper.assertTrue(TitanShifter.cooldownRemaining(p, TitanMove.STEAM_BURST.id) > 0, "Steam Burst recharging");
		helper.assertTrue(z.getDeltaMovement().horizontalDistance() > 0.3, "the zombie was blown back");
		// the human healing spends the pool, never Steam
		p.setHealth(10.0f);
		helper.assertTrue(TitanShifter.baseRegenActive(p, TitanShifter.state(p)), "healing from the pool");
		float steam0 = TitanShifter.energy(p);
		for (int i = 0; i < 40; i++) {
			TitanShifter.tick(p);
		}
		helper.assertTrue(TitanShifter.energy(p) >= steam0, "Steam was not spent on healing");
		helper.succeed();
	}

	// ---------------- UI text fits ----------------

	private static JsonObject lang;

	private static String t(String key) {
		if (lang == null) {
			try (InputStream in = TitanRevampGameTests.class.getResourceAsStream("/assets/projecthero/lang/en_us.json")) {
				lang = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
			} catch (Exception e) {
				throw new IllegalStateException("cannot read en_us.json", e);
			}
		}
		if (!lang.has(key)) {
			throw new IllegalStateException("missing lang key " + key);
		}
		return lang.get(key).getAsString();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void titanHudAndPickerTextFits(GameTestHelper helper) {
		int w = TitanUi.HUD_W;
		for (TitanType type : TitanType.values()) {
			helper.assertTrue(IronManUiLayout.approxWidth(t(type.nameKey()), true) <= w, "title fits: " + type.id);
			for (int slot = 1; slot <= 6; slot++) {
				for (boolean shift : new boolean[] { false, true }) {
					TitanMove m = TitanMove.of(type, slot, shift);
					t(m.nameKey(type)); // every move is named
				}
			}
		}
		for (int slot = 1; slot <= 3; slot++) {
			t(TitanMove.of(null, slot, false).nameKey());
			t(TitanMove.of(null, slot, true).nameKey());
		}
		// the nape line sits right of the 20-px silhouette
		for (String k : new String[] { "nape_warning", "nape_guarded", "nape_crystal", "limbs_regrowing" }) {
			helper.assertTrue(IronManUiLayout.approxWidth(t("hud.projecthero.titan_shifter." + k), true) <= w - 20, "fits: " + k);
		}
		for (String k : new String[] { "energy_label", "pool_label", "hp" }) {
			helper.assertTrue(IronManUiLayout.approxWidth(t("hud.projecthero.titan_shifter." + k)) + 30 <= w, "label + % fits: " + k);
		}
		helper.assertTrue(IronManUiLayout.approxWidth(String.format(java.util.Locale.ROOT, t("hud.projecthero.titan_shifter.cooldown"), 60)) <= w,
				"the lock line fits");
		helper.assertTrue(IronManUiLayout.approxWidth(String.format(java.util.Locale.ROOT, t("hud.projecthero.titan_shifter.revert"), "H")) <= w,
				"the revert line fits");
		// the picker: each card's text wraps into its lines at the smallest screen, the name and size on one line
		for (int screenW : new int[] { 320, 426, 480, 960 }) {
			int tw = TitanUi.pickerTextWidth(screenW);
			for (TitanType type : TitanType.values()) {
				int lines = IronManUiLayout.wrapLines(t(type.descKey()), tw, IronManUiLayout::approxWidth);
				helper.assertTrue(lines <= TitanUi.PICKER_TEXT_LINES, type.id + " description wraps to " + lines + " lines at " + screenW);
				helper.assertTrue(IronManUiLayout.approxWidth(t(type.nameKey()), true) <= tw, type.id + " name fits the card");
				helper.assertTrue(IronManUiLayout.approxWidth(String.format(java.util.Locale.ROOT,
						t("screen.projecthero.titan_picker.size_current"), 15)) <= tw, "size line fits");
			}
			helper.assertTrue(IronManUiLayout.approxWidth(t("screen.projecthero.titan_picker.reforge"), true) <= TitanUi.pickerCardWidth(screenW) * 2,
					"title fits");
		}
		helper.succeed();
	}
}
