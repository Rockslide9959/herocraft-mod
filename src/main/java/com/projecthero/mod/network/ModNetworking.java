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
		PayloadTypeRegistry.playC2S().register(ThorActionPayload.TYPE, ThorActionPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(AbilityInputPayload.TYPE, AbilityInputPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(PowerSelectPayload.TYPE, PowerSelectPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(IronManActionPayload.TYPE, IronManActionPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(IronManCallSuitPayload.TYPE, IronManCallSuitPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(IronManWeaponWheelPayload.TYPE, IronManWeaponWheelPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(IronManBlueprintChoicePayload.TYPE, IronManBlueprintChoicePayload.CODEC);
		PayloadTypeRegistry.playC2S().register(SpiderActionPayload.TYPE, SpiderActionPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(StrengthActionPayload.TYPE, StrengthActionPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(MaxSteelActionPayload.TYPE, MaxSteelActionPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(WolverineActionPayload.TYPE, WolverineActionPayload.CODEC);
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
		PayloadTypeRegistry.playS2C().register(IronManBeamPayload.TYPE, IronManBeamPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(IronManSuitListPayload.TYPE, IronManSuitListPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(IronManWeaponWheelPayload.TYPE, IronManWeaponWheelPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(IronManBlueprintPickerPayload.TYPE, IronManBlueprintPickerPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(RaidSkyPayload.TYPE, RaidSkyPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(MaxSteelWarningPayload.TYPE, MaxSteelWarningPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(SpiderSenseWarningPayload.TYPE, SpiderSenseWarningPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(SpiderSenseGlowPayload.TYPE, SpiderSenseGlowPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(WolverineSensePayload.TYPE, WolverineSensePayload.CODEC);
		PayloadTypeRegistry.playS2C().register(SpiderClimbGrabPayload.TYPE, SpiderClimbGrabPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(FirearmShotPayload.TYPE, FirearmShotPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(FirearmHeadshotPayload.TYPE, FirearmHeadshotPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(BulletHolePayload.TYPE, BulletHolePayload.CODEC);
		PayloadTypeRegistry.playS2C().register(PunisherArsenalOpenPayload.TYPE, PunisherArsenalOpenPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(SquadInfoPayload.TYPE, SquadInfoPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(GreenLanternConstructSelectPayload.TYPE, GreenLanternConstructSelectPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(GreenLanternActionPayload.TYPE, GreenLanternActionPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(GreenLanternRingScanPayload.TYPE, GreenLanternRingScanPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(GreenLanternTrialPromptPayload.TYPE, GreenLanternTrialPromptPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(GreenLanternTrialAnswerPayload.TYPE, GreenLanternTrialAnswerPayload.CODEC);

		// Thor flight double-tap-jump (unchanged). The other Thor actions now arrive via the universal
		// slot router below, but the enum values are left intact for save/packet compatibility.
		ServerPlayNetworking.registerGlobalReceiver(ThorActionPayload.TYPE, (payload, context) -> {
			if (payload.action() == ThorActionPayload.Action.TOGGLE_FLIGHT) {
				ThorPowers.toggleFlight(context.player());
			}
		});

		// Green Lantern Ring Flight double-tap-jump (v0.11.5 -- moved off the X ability slot, which now
		// fires the "Green Lantern's Light!" Oath empowerment mode instead, v0.11.7).
		ServerPlayNetworking.registerGlobalReceiver(GreenLanternActionPayload.TYPE, (payload, context) -> {
			if (payload.action() == GreenLanternActionPayload.Action.TOGGLE_FLIGHT) {
				com.projecthero.mod.greenlantern.GreenLanternAbilityManager.toggleFlight(context.player());
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
				case TRANSFORM_TOGGLE -> com.projecthero.mod.maxsteel.MaxSteelTransform.goTurbo(p);
				case TOGGLE_HELMET -> com.projecthero.mod.maxsteel.MaxSteelFaceplate.toggle(p);
				case POWER_DOWN -> com.projecthero.mod.maxsteel.MaxSteelTransform.powerDown(p);
			}
		});

		// Wolverine: the H-key claw toggle. Server re-validates power ownership + the spam guard.
		ServerPlayNetworking.registerGlobalReceiver(WolverineActionPayload.TYPE, (payload, context) -> {
			if (payload.action() == WolverineActionPayload.Action.CLAW_STRIKE) {
				com.projecthero.mod.wolverine.WolverineSense.offHandStrike(context.player());
			} else if (payload.action() == WolverineActionPayload.Action.SNIFF) {
				com.projecthero.mod.wolverine.WolverineSense.sniff(context.player());
			} else if (payload.action() == WolverineActionPayload.Action.TOGGLE_CLAWS) {
				com.projecthero.mod.wolverine.Wolverine.toggleClaws(context.player());
			}
		});

		// Firearms: the attack button (fire) and the reload / aim / scope gestures. Server-authoritative.
		ServerPlayNetworking.registerGlobalReceiver(FirearmFirePayload.TYPE, (payload, context) ->
				com.projecthero.mod.firearm.FirearmManager.onFireInput(context.player(), payload.pressed()));
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

		// Will Trial "Are you afraid?" answer (v0.11.11).
		ServerPlayNetworking.registerGlobalReceiver(GreenLanternTrialAnswerPayload.TYPE, (payload, context) ->
				com.projecthero.mod.greenlantern.GreenLanternTrial.handleAnswer(context.player(), payload.yes()));

		// Teleportation Portal picker: the player chose a destination + dimension.
		ServerPlayNetworking.registerGlobalReceiver(PortalCreatePayload.TYPE, (payload, context) ->
				com.projecthero.mod.hero.power.p11.TeleportationHandlers.createDestinationPortal(
						context.player(), payload.x(), payload.y(), payload.z(), payload.dimension()));

		// Iron Man call-armour picker: the player chose a suit from the C-key screen.
		ServerPlayNetworking.registerGlobalReceiver(IronManCallSuitPayload.TYPE, (payload, context) ->
				com.projecthero.mod.ironman.suit.IronManSuitCall.execute(context.player(), payload.suitId(), payload.source()));

		// Mark 7 weapon wheel: the player picked which ability slot 3 (X) fires, or toggled entity glow.
		ServerPlayNetworking.registerGlobalReceiver(IronManWeaponWheelPayload.TYPE, (payload, context) -> {
			if (com.projecthero.mod.ironman.ability.IronManAbilities.ENTITY_GLOW_TOGGLE.equals(payload.ability())) {
				com.projecthero.mod.ironman.ability.IronManAbilities.toggleEntityGlowFromWheel(context.player());
				return;
			}
			for (String option : com.projecthero.mod.ironman.ability.IronManAbilities.WEAPON_WHEEL_OPTIONS) {
				if (option.equals(payload.ability())) {
					com.projecthero.mod.ironman.TonyStark.setWeaponWheelChoice(context.player(), option);
					return;
				}
			}
		});

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
