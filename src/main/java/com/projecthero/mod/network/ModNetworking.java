package com.projecthero.mod.network;

import com.projecthero.mod.hero.AbilityRouter;
import com.projecthero.mod.hero.ExperimentalPowers;
import com.projecthero.mod.hero.Power;
import com.projecthero.mod.hero.Powers;
import com.projecthero.mod.power.ThorPowers;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.resources.ResourceLocation;

public final class ModNetworking {
	private ModNetworking() {
	}

	public static void initialize() {
		VersionCheck.initialize(); // v0.14.22: kick mismatched clients instead of desyncing block-state ids
		PayloadTypeRegistry.playC2S().register(ThorActionPayload.TYPE, ThorActionPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(AbilityInputPayload.TYPE, AbilityInputPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(PowerSelectPayload.TYPE, PowerSelectPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(IronManActionPayload.TYPE, IronManActionPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(IronManCallSuitPayload.TYPE, IronManCallSuitPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(StarkGantryActionPayload.TYPE, StarkGantryActionPayload.CODEC); // v0.15.4
		PayloadTypeRegistry.playS2C().register(StarkGantryMenuPayload.TYPE, StarkGantryMenuPayload.CODEC); // v0.15.4
		PayloadTypeRegistry.playC2S().register(IronManWeaponWheelPayload.TYPE, IronManWeaponWheelPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(IronManBlueprintChoicePayload.TYPE, IronManBlueprintChoicePayload.CODEC);
		PayloadTypeRegistry.playC2S().register(SpiderActionPayload.TYPE, SpiderActionPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(StrengthActionPayload.TYPE, StrengthActionPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(MaxSteelActionPayload.TYPE, MaxSteelActionPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(WolverineActionPayload.TYPE, WolverineActionPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(TitanShiftPayload.TYPE, TitanShiftPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(AllMightActionPayload.TYPE, AllMightActionPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(MoonKnightActionPayload.TYPE, MoonKnightActionPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(HulkActionPayload.TYPE, HulkActionPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(TitanShakePayload.TYPE, TitanShakePayload.CODEC);
		PayloadTypeRegistry.playS2C().register(WorldEventZoomPayload.TYPE, WorldEventZoomPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(FirearmFirePayload.TYPE, FirearmFirePayload.CODEC);
		PayloadTypeRegistry.playC2S().register(FirearmActionPayload.TYPE, FirearmActionPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(PunisherArsenalPayload.TYPE, PunisherArsenalPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(PunisherActionPayload.TYPE, PunisherActionPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(CryoWeaponPayload.TYPE, CryoWeaponPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(CryoWheelOpenPayload.TYPE, CryoWheelOpenPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(ElasticFormPayload.TYPE, ElasticFormPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(ElasticFormWheelPayload.TYPE, ElasticFormWheelPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(PortalCreatePayload.TYPE, PortalCreatePayload.CODEC);
		PayloadTypeRegistry.playS2C().register(PortalPickerPayload.TYPE, PortalPickerPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(BifrostScreenPayload.TYPE, BifrostScreenPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(BifrostActionPayload.TYPE, BifrostActionPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(ThorWeaponTogglePayload.TYPE, ThorWeaponTogglePayload.CODEC); // v0.15.3
		PayloadTypeRegistry.playS2C().register(IronManBeamPayload.TYPE, IronManBeamPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(IronManLockPayload.TYPE, IronManLockPayload.CODEC); // v0.14.26
		PayloadTypeRegistry.playS2C().register(IronManPosePayload.TYPE, IronManPosePayload.CODEC); // v0.14.26
		PayloadTypeRegistry.playS2C().register(IronManJarvisPayload.TYPE, IronManJarvisPayload.CODEC); // v0.14.29 agent F
		PayloadTypeRegistry.playS2C().register(LaserBeamPayload.TYPE, LaserBeamPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(IronManSuitListPayload.TYPE, IronManSuitListPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(IronManWeaponWheelPayload.TYPE, IronManWeaponWheelPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(IronManBlueprintPickerPayload.TYPE, IronManBlueprintPickerPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(RaidSkyPayload.TYPE, RaidSkyPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(MaxSteelWarningPayload.TYPE, MaxSteelWarningPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(SpiderSenseWarningPayload.TYPE, SpiderSenseWarningPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(SpiderSenseGlowPayload.TYPE, SpiderSenseGlowPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(WolverineSensePayload.TYPE, WolverineSensePayload.CODEC);
		PayloadTypeRegistry.playS2C().register(WolverineExecutionTargetPayload.TYPE, WolverineExecutionTargetPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(TitanRoarSensePayload.TYPE, TitanRoarSensePayload.CODEC);
		PayloadTypeRegistry.playS2C().register(SpiderClimbGrabPayload.TYPE, SpiderClimbGrabPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(SpiderWebStrandPayload.TYPE, SpiderWebStrandPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(FirearmShotPayload.TYPE, FirearmShotPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(FirearmHeadshotPayload.TYPE, FirearmHeadshotPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(BulletHolePayload.TYPE, BulletHolePayload.CODEC);
		PayloadTypeRegistry.playS2C().register(GunTracerPayload.TYPE, GunTracerPayload.CODEC); // v0.15.16
		PayloadTypeRegistry.playS2C().register(PunisherArsenalOpenPayload.TYPE, PunisherArsenalOpenPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(SquadInfoPayload.TYPE, SquadInfoPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(ThorLightningArcPayload.TYPE, ThorLightningArcPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(GreenLanternConstructSelectPayload.TYPE, GreenLanternConstructSelectPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(GreenLanternActionPayload.TYPE, GreenLanternActionPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(GreenLanternRingScanPayload.TYPE, GreenLanternRingScanPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(GreenLanternTrialPromptPayload.TYPE, GreenLanternTrialPromptPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(SymbioteBondGamePayload.TYPE, SymbioteBondGamePayload.CODEC);
		// Moon Knight Phase 7: the Khonshu ritual's fade to white (cosmetic).
		PayloadTypeRegistry.playS2C().register(com.projecthero.mod.moonknight.temple.MoonKnightRitualFadePayload.TYPE,
				com.projecthero.mod.moonknight.temple.MoonKnightRitualFadePayload.CODEC);
		PayloadTypeRegistry.playC2S().register(SymbioteBondResultPayload.TYPE, SymbioteBondResultPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(GreenLanternTrialAnswerPayload.TYPE, GreenLanternTrialAnswerPayload.CODEC);

		// Thor flight double-tap-jump (unchanged). The other Thor actions now arrive via the universal
		// slot router below, but the enum values are left intact for save/packet compatibility.
		ServerPlayNetworking.registerGlobalReceiver(ThorActionPayload.TYPE, (payload, context) -> {
			if (payload.action() == ThorActionPayload.Action.TOGGLE_FLIGHT) {
				ThorPowers.toggleFlight(context.player());
			} else if (payload.action() == ThorActionPayload.Action.TOGGLE_ARMOUR) {
				com.projecthero.mod.thorarmor.ThorArmor.toggle(context.player());
			}
		});

		// Green Lantern Ring Flight double-tap-jump (v0.11.5 -- moved off the X ability slot, which now
		// fires the "Green Lantern's Light!" Oath empowerment mode instead, v0.11.7).
		ServerPlayNetworking.registerGlobalReceiver(GreenLanternActionPayload.TYPE, (payload, context) -> {
			switch (payload.action()) {
				case TOGGLE_FLIGHT -> com.projecthero.mod.greenlantern.GreenLanternAbilityManager.toggleFlight(context.player());
				// v0.14.3: H / N / Shift + hold N
				case GIANT_HAND -> com.projecthero.mod.greenlantern.GreenLanternAbilityManager.giantHand(context.player());
				case CLEAR_CONSTRUCTS -> com.projecthero.mod.greenlantern.GreenLanternAbilityManager.clearConstructs(context.player());
				case RING_REMOVE_START -> com.projecthero.mod.greenlantern.GreenLanternAbilityManager.ringRemoveStart(context.player());
				case RING_REMOVE_STOP -> com.projecthero.mod.greenlantern.GreenLanternAbilityManager.ringRemoveStop(context.player());
				// v0.15.15: the N suit screen
				case SUIT_STYLE_DEFAULT, SUIT_STYLE_CORPS, SUIT_STYLE_STEWART, SUIT_STYLE_CLASSIC, SUIT_STYLE_MIDNIGHT, SUIT_STYLE_ARMORED ->
						com.projecthero.mod.greenlantern.GreenLanternSuit.selectStyle(context.player(), payload.action().suitStyle());
				case SUIT_TOGGLE -> com.projecthero.mod.greenlantern.GreenLanternAbilityManager.suitToggle(context.player());
				case REMOVE_RING -> com.projecthero.mod.greenlantern.GreenLanternAbilityManager.removeRingFromMenu(context.player());
			}
		});

		// The six universal HeroPack ability slots.
		ServerPlayNetworking.registerGlobalReceiver(AbilityInputPayload.TYPE, (payload, context) ->
				AbilityRouter.handleInput(context.player(), payload.slot(), payload.pressed()));

		// Iron Man double-tap-jump gestures: repulsor flight toggle, and suit summon.
		ServerPlayNetworking.registerGlobalReceiver(IronManActionPayload.TYPE, (payload, context) -> {
			switch (payload.action()) {
				// "changes 22": the same gesture drives bare Repulsor boots when there is no suit to fly.
				// The suit path gets first refusal; RepulsorBoots.toggle no-ops unless the boots are on.
				case TOGGLE_FLIGHT -> {
					if (!com.projecthero.mod.ironman.RepulsorBoots.worn(context.player())
							&& !com.projecthero.mod.ironman.RepulsorBoots.isFlying(context.player())) {
						com.projecthero.mod.ironman.IronManFlight.toggle(context.player());
					} else {
						com.projecthero.mod.ironman.RepulsorBoots.toggle(context.player());
					}
				}
				case SUMMON_SUIT -> com.projecthero.mod.ironman.suit.IronManSuitCall.callBest(context.player());
				case TOGGLE_FACEPLATE -> com.projecthero.mod.ironman.IronManFaceplate.toggle(context.player());
			}
		});

		// Spider-Man jump gestures. Server-validated: a request the player is not entitled to is
		// simply ignored, so neither of these can be abused into free height.
		ServerPlayNetworking.registerGlobalReceiver(SpiderActionPayload.TYPE, (payload, context) -> {
			switch (payload.action()) {
				case DOUBLE_JUMP -> com.projecthero.mod.spider.SpiderAbilities.doubleJump(context.player());
				case SURFACE_LEAP -> com.projecthero.mod.spider.SpiderClimbActions.leap(context.player());
				case CLIMB_GRAB -> com.projecthero.mod.spider.SpiderClimb.requestGrab(context.player());
				case CLIMB_RELEASE -> com.projecthero.mod.spider.SpiderClimb.requestRelease(context.player());
				case SUPER_JUMP -> com.projecthero.mod.spider.SpiderAbilities.superJump(context.player());
				case TOGGLE_MASK -> com.projecthero.mod.spider.SpiderMask.toggle(context.player());
				case TOGGLE_SYMBIOTE -> com.projecthero.mod.symbiote.Symbiote.toggle(context.player());
				case TOGGLE_MODE -> com.projecthero.mod.spider.SpiderCombat.toggleMode(context.player());
			}
		});

		// Super Strength: the charged punch and Power Leap are both wound up by a key hold and thrown on
		// release, tracked client-side. Timing comes from the client; the server owns power + cooldown.
		ServerPlayNetworking.registerGlobalReceiver(StrengthActionPayload.TYPE, (payload, context) -> {
			switch (payload.action()) {
				case PERFORM_CHARGED_PUNCH ->
						com.projecthero.mod.hero.power.p01.SuperStrengthHandlers.performChargedPunch(context.player());
				case PERFORM_POWER_LEAP ->
						com.projecthero.mod.hero.power.p01.SuperStrengthHandlers.performPowerLeap(context.player(), payload.value());
			}
		});

		// Max Steel: the dedicated transform key and the H-key helmet toggle.
		ServerPlayNetworking.registerGlobalReceiver(MaxSteelActionPayload.TYPE, (payload, context) -> {
			net.minecraft.server.level.ServerPlayer p = context.player();
			if (!com.projecthero.mod.maxsteel.MaxSteel.hasPower(p)) {
				return;
			}
			switch (payload.action()) {
				case TRANSFORM_TOGGLE -> com.projecthero.mod.maxsteel.MaxSteelTransform.toggle(p);
				case TOGGLE_HELMET -> com.projecthero.mod.maxsteel.MaxSteelFaceplate.toggle(p);
				case POWER_DOWN -> com.projecthero.mod.maxsteel.MaxSteelTransform.powerDown(p);
			}
		});

		// Titan Shifter: the Titan Shift key (H -- transform / revert). The server re-validates the unlock, phase,
		// Titan Energy, cooldown and room -- this is only a request.
		ServerPlayNetworking.registerGlobalReceiver(TitanShiftPayload.TYPE, (payload, context) -> {
			net.minecraft.server.level.ServerPlayer p = context.player();
			switch (payload.action()) {
				case TOGGLE_SHIFT -> com.projecthero.mod.titanshifter.TitanShifter.requestToggle(p);
				case GRAB_BITE -> com.projecthero.mod.titanshifter.TitanAbilities.grabOrBite(p);
				case LET_DOWN -> com.projecthero.mod.titanshifter.TitanAbilities.letDown(p);
				case SPRINT_ON -> com.projecthero.mod.titanshifter.TitanShifter.setSprintHeld(p, true);
				case SPRINT_OFF -> com.projecthero.mod.titanshifter.TitanShifter.setSprintHeld(p, false);
				case EMERGENCY_RELEASE -> com.projecthero.mod.titanshifter.TitanShifter.cancelEmergencyHold(p);
				case TOGGLE_REGEN -> com.projecthero.mod.titanshifter.TitanShifter.toggleRegen(p);
			}
		});

		// All Might: H (Base Form / Power Form). Server re-validates power, cooldown, OFA and state.
		ServerPlayNetworking.registerGlobalReceiver(AllMightActionPayload.TYPE, (payload, context) -> {
			net.minecraft.server.level.ServerPlayer p = context.player();
			if (!com.projecthero.mod.allmight.AllMight.hasPower(p)) {
				return;
			}
			switch (payload.action()) {
				case TOGGLE_FORM -> com.projecthero.mod.allmight.AllMight.toggleForm(p);
				case OPEN_LOCKER -> com.projecthero.mod.allmight.AllMightSuit.openLocker(p);
			}
		});

		// Moon Knight (v0.13.19): H suits up / down; the alter picker sends its choice. Server re-validates.
		ServerPlayNetworking.registerGlobalReceiver(MoonKnightActionPayload.TYPE, (payload, context) -> {
			net.minecraft.server.level.ServerPlayer p = context.player();
			if (!com.projecthero.mod.moonknight.MoonKnight.hasPower(p)) {
				return;
			}
			switch (payload.action()) {
				case TOGGLE_SUIT -> com.projecthero.mod.moonknight.MoonKnightTransform.toggle(p);
				case SELECT_ALTER -> {
					if (com.projecthero.mod.moonknight.MoonKnight.isTransformed(p)) {
						com.projecthero.mod.moonknight.ability.MoonKnightAlters.select(p, payload.arg());
					}
				}
				// v0.13.21: hold right click = Cape Block
				case CAPE_BLOCK_START -> com.projecthero.mod.moonknight.ability.MoonKnightCape.startBlock(p);
				case CAPE_BLOCK_STOP -> com.projecthero.mod.moonknight.ability.MoonKnightCape.stopBlock(p);
			}
		});

		// Hulk (v0.13.11): H lets the Hulk out at 75+ rage. Server re-validates the power, rage and state.
		ServerPlayNetworking.registerGlobalReceiver(HulkActionPayload.TYPE, (payload, context) -> {
			net.minecraft.server.level.ServerPlayer p = context.player();
			if (!com.projecthero.mod.hulk.Hulk.hasPower(p)) {
				return;
			}
			switch (payload.action()) {
				case TRANSFORM -> com.projecthero.mod.hulk.Hulk.tryTransform(p);
				case CALM_START -> com.projecthero.mod.hulk.HulkCalm.start(p);
				case CALM_REPORT -> com.projecthero.mod.hulk.HulkCalm.report(p, payload.a(), payload.b());
				case CALM_STOP -> com.projecthero.mod.hulk.HulkCalm.stop(p);
				case CONTROL -> com.projecthero.mod.hulk.HulkControl.answer(p, payload.a());
			}
		});

		// Wolverine: the H-key claw toggle, the N sniff and the right-click claw block. Server re-validates.
		ServerPlayNetworking.registerGlobalReceiver(WolverineActionPayload.TYPE, (payload, context) -> {
			if (payload.action() == WolverineActionPayload.Action.BLOCK_START) {
				com.projecthero.mod.wolverine.WolverineBlock.start(context.player());
			} else if (payload.action() == WolverineActionPayload.Action.BLOCK_STOP) {
				com.projecthero.mod.wolverine.WolverineBlock.stop(context.player());
			} else if (payload.action() == WolverineActionPayload.Action.SNIFF) {
				com.projecthero.mod.wolverine.WolverineSense.sniff(context.player());
			} else if (payload.action() == WolverineActionPayload.Action.TOGGLE_CLAWS) {
				com.projecthero.mod.wolverine.Wolverine.toggleClaws(context.player());
			}
		});

		// Firearms: the attack button (fire) and the reload / aim / scope gestures. Server-authoritative.
		ServerPlayNetworking.registerGlobalReceiver(FirearmFirePayload.TYPE, (payload, context) -> {
			// v0.13.11: the Hulk fights with his fists -- no guns (a release still goes through)
			if (payload.pressed() && com.projecthero.mod.hulk.Hulk.isHulk(context.player())) {
				return;
			}
			com.projecthero.mod.firearm.FirearmManager.onFireInput(context.player(), payload.pressed());
		});
		ServerPlayNetworking.registerGlobalReceiver(FirearmActionPayload.TYPE, (payload, context) -> {
			net.minecraft.server.level.ServerPlayer p = context.player();
			switch (payload.action()) {
				case RELOAD -> com.projecthero.mod.firearm.FirearmManager.onReloadInput(p);
				case AIM_START -> p.setAttached(com.projecthero.mod.attachment.ModAttachments.FIREARM_AIMING, true);
				case AIM_STOP -> p.setAttached(com.projecthero.mod.attachment.ModAttachments.FIREARM_AIMING, false);
				case CYCLE_ZOOM -> { /* scope zoom is resolved client-side; no server state to change */ }
			}
		});
		ServerPlayNetworking.registerGlobalReceiver(PunisherArsenalPayload.TYPE, (payload, context) ->
				com.projecthero.mod.punisher.ability.PunisherArsenal.equip(context.player(), payload.weaponId()));
		ServerPlayNetworking.registerGlobalReceiver(PunisherActionPayload.TYPE, (payload, context) -> {
			if (payload.action() == PunisherActionPayload.Action.ABANDON_TRAINING) {
				com.projecthero.mod.punisher.VigilanteTraining.abandon(context.player());
			}
		});

		// Cryokinesis ice-weapon wheel: the player picked which ice tool to shape.
		ServerPlayNetworking.registerGlobalReceiver(CryoWeaponPayload.TYPE, (payload, context) ->
				com.projecthero.mod.hero.power.p09.CryokinesisHandlers.giveIceToolChoice(context.player(), payload.weapon()));

		// Elasticity body-shape wheel: the player picked Elastic / Inflated / Compression.
		ServerPlayNetworking.registerGlobalReceiver(ElasticFormPayload.TYPE, (payload, context) ->
				com.projecthero.mod.hero.power.p17.ElasticityHandlers.chooseForm(context.player(), payload.form()));

		// Green Lantern construct wheel: the player picked a construct type (hold C).
		ServerPlayNetworking.registerGlobalReceiver(GreenLanternConstructSelectPayload.TYPE, (payload, context) ->
				com.projecthero.mod.greenlantern.GreenLanternAbilityManager.selectConstruct(context.player(), payload.ordinal()));

		// Symbiote bonding minigame result (v0.12.25): the server replays the press ticks against its own seed.
		ServerPlayNetworking.registerGlobalReceiver(SymbioteBondResultPayload.TYPE, (payload, context) ->
				context.server().execute(() -> com.projecthero.mod.symbiote.SymbioteBondGame.handleResult(context.player(), payload.presses())));

		// Will Trial "Are you afraid?" answer (v0.11.11).
		ServerPlayNetworking.registerGlobalReceiver(GreenLanternTrialAnswerPayload.TYPE, (payload, context) ->
				com.projecthero.mod.greenlantern.GreenLanternTrial.handleAnswer(context.player(), payload.yes()));

		// Teleportation Portal picker: the player chose a destination + dimension.
		ServerPlayNetworking.registerGlobalReceiver(PortalCreatePayload.TYPE, (payload, context) ->
				com.projecthero.mod.hero.power.p11.TeleportationHandlers.createDestinationPortal(
						context.player(), payload.x(), payload.y(), payload.z(), payload.dimension()));

		// Stormbreaker's Bifrost screen (v0.14.20): travel / save / clear -- all re-validated in Bifrost.handleAction.
		ServerPlayNetworking.registerGlobalReceiver(BifrostActionPayload.TYPE, (payload, context) ->
				context.server().execute(() -> com.projecthero.mod.stormbreaker.Bifrost.handleAction(context.player(), payload)));

		// v0.15.3: Thor's N weapon screen -- one weapon ACTIVE / INACTIVE for R, re-validated in ThorWeaponSelection.
		ServerPlayNetworking.registerGlobalReceiver(ThorWeaponTogglePayload.TYPE, (payload, context) ->
				context.server().execute(() -> com.projecthero.mod.hammer.ThorWeaponSelection.handleToggle(
						context.player(), payload.weapon(), payload.active())));

		// v0.15.4: the Stark Gantry -- H on the floor (menu request), a suit picked, or "Remove armour".
		ServerPlayNetworking.registerGlobalReceiver(StarkGantryActionPayload.TYPE, (payload, context) ->
				context.server().execute(() -> StarkGantryActionPayload.handleServer(context.player(), payload)));

		// Iron Man call-armour picker: the player chose a suit from the C-key screen.
		ServerPlayNetworking.registerGlobalReceiver(IronManCallSuitPayload.TYPE, (payload, context) ->
				com.projecthero.mod.ironman.suit.IronManSuitCall.execute(context.player(), payload.suitId(), payload.source()));

		// Mark 7 weapon wheel: the player picked which ability slot 3 (X) fires, or toggled entity glow.
		ServerPlayNetworking.registerGlobalReceiver(IronManWeaponWheelPayload.TYPE, (payload, context) ->
				IronManWeaponWheelPayload.handleServer(context.player(), payload.ability()));

		// Blank Blueprint picker ("changes 21"): the player chose a mark to stamp the blank into.
		ServerPlayNetworking.registerGlobalReceiver(IronManBlueprintChoicePayload.TYPE, (payload, context) ->
				com.projecthero.mod.ironman.TonyStark.stampBlueprint(context.player(), payload.suitId()));

		// Power-selection wheel: switch which owned experimental power occupies the six slots.
		ServerPlayNetworking.registerGlobalReceiver(PowerSelectPayload.TYPE, (payload, context) -> {
			if (payload.power().isEmpty()) {
				ExperimentalPowers.setActive(context.player(), null);
				return;
			}
			Power power = Powers.byKey(payload.power());
			if (power == null) {
				power = Powers.get(ResourceLocation.tryParse(payload.power()));
			}
			if (power != null && ExperimentalPowers.owns(context.player(), power)) {
				ExperimentalPowers.setActive(context.player(), power);
			}
		});
	}
}
