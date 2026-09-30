package com.projecthero.mod.client;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.client.gui.AbilityHud;
import com.projecthero.mod.client.gui.PowerWheelScreen;
import com.projecthero.mod.client.gui.ThorHud;
import com.projecthero.mod.client.render.ModEntityRenderers;
import com.projecthero.mod.client.sound.ThorFlightSoundInstance;
import com.projecthero.mod.item.MjolnirTooltip;
import com.projecthero.mod.network.AbilityInputPayload;
import com.projecthero.mod.network.ThorActionPayload;
import com.projecthero.mod.power.ThorPowers;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

public class ProjectHeroModClient implements ClientModInitializer {
	private static final int DOUBLE_JUMP_WINDOW_TICKS = 7;

	/** Per-slot key-down edge state, for sending RELEASE on key-up (needed by HOLD/channel abilities). */
	private static final boolean[] slotWasDown = new boolean[6];

	private static boolean wasFlying = false;
	private static boolean jumpWasDown = false;
	private static int ticksSinceJumpPress = Integer.MAX_VALUE;
	private static boolean powerSelectWasDown = false;
	private static boolean powerInfoWasDown = false;
	private static boolean squadMenuWasDown = false;

	/** Green Lantern construct wheel: C (ability slot 6) held this many ticks so far, not shifted. */
	private static int glConstructHeldTicks = 0;
	private static boolean glWheelOpenedThisHold = false;
	/** v0.14.3: Shift+N went down as a Green Lantern -- the release must tell the server to stop taking the ring off. */
	private static boolean glRingRemoveHeld = false;

	/** Ability-1 (R) tap vs hold while a firearm is held: tap = reload, hold = open the Arsenal wheel. */
	private static int firearmAbilityOneHeld = -1;
	private static boolean firearmAbilityOneSentPress = false;
	private static final int FIREARM_RELOAD_TAP_TICKS = 8;

	/** Super Strength charged punch: attack key held this many ticks (not while aimed at a block). v0.14.5: 1 s. */
	public static final int CHARGED_PUNCH_HOLD_TICKS = com.projecthero.mod.hero.power.p01.SuperStrengthHandlers.CHARGED_HOLD_TICKS;
	private static int chargedPunchHold = 0;
	private static boolean chargedPunchWasCharging = false;

	/** Super Strength Power Leap: X held this many ticks = maximum charge. Mirrors the server. */
	public static final int LEAP_MAX_CHARGE_TICKS = 50;
	private static int leapChargeHold = 0;
	private static boolean leapWasCharging = false;

	/** 0..1 progress toward the charged punch being ready, for the ability HUD. */
	public static float chargedPunchProgress() {
		return Math.min(1.0f, chargedPunchHold / (float) CHARGED_PUNCH_HOLD_TICKS);
	}

	/** true once the charged punch has been held long enough to throw on release. */
	public static boolean chargedPunchReady() {
		return chargedPunchHold >= CHARGED_PUNCH_HOLD_TICKS;
	}

	/** 0..1 Power Leap charge, for the ability HUD (0 when X is not held). */
	public static float leapChargeProgress() {
		return Math.min(1.0f, leapChargeHold / (float) LEAP_MAX_CHARGE_TICKS);
	}

	@Override
	public void onInitializeClient() {
		ModKeyBindings.initialize();
		ModEntityRenderers.initialize();
		// v0.13.22: mutation move animations + per-batch client registration (poses, overlays, renderers)
		com.projecthero.mod.client.mutation.MutationPoseLibrary.init();
		com.projecthero.mod.client.mutation.RevampClientA.init();
		com.projecthero.mod.client.mutation.RevampClientB.init();
		com.projecthero.mod.client.mutation.RevampClientC.init();
		com.projecthero.mod.client.mutation.RevampClientD.init();
		com.projecthero.mod.client.mutation.RevampClientE.init();
		// v0.14.5 power reworks
		com.projecthero.mod.client.mutation.v0145.LaserVisionClientV0145.init();
		com.projecthero.mod.client.mutation.v0145.SuperStrengthClientV0145.init();
		com.projecthero.mod.client.mutation.v0145.SuperRegenerationClientV0145.init();
		com.projecthero.mod.client.mutation.v0145.SuperSpeedClientV0145.init();

		MjolnirTooltip.expandKeyHeld = Screen::hasShiftDown;
		com.projecthero.mod.hero.guide.HeroPackGuideItem.clientOpener =
				() -> Minecraft.getInstance().setScreen(new com.projecthero.mod.client.gui.HeroPackGuideScreen());

		HudRenderCallback.EVENT.register(ThorHud::render);
		HudRenderCallback.EVENT.register(AbilityHud::render);
		HudRenderCallback.EVENT.register(com.projecthero.mod.client.gui.LaserReticleHud::render);
		HudRenderCallback.EVENT.register(com.projecthero.mod.client.gui.IronManHud::render);
		HudRenderCallback.EVENT.register(com.projecthero.mod.client.gui.RaidHud::render);
		HudRenderCallback.EVENT.register(com.projecthero.mod.client.gui.SpiderHud::render);
		HudRenderCallback.EVENT.register(com.projecthero.mod.client.gui.MaxSteelHud::render);
		HudRenderCallback.EVENT.register(com.projecthero.mod.client.gui.FirearmHud::render);
		HudRenderCallback.EVENT.register(com.projecthero.mod.client.gui.ScopeOverlay::render);
		HudRenderCallback.EVENT.register(com.projecthero.mod.client.gui.PunisherHud::render);
		HudRenderCallback.EVENT.register(com.projecthero.mod.client.gui.WolverineSurgeOverlay::render);
		HudRenderCallback.EVENT.register(com.projecthero.mod.client.gui.WolverineHud::render);
		HudRenderCallback.EVENT.register(com.projecthero.mod.client.gui.TitanShifterHud::render);
		HudRenderCallback.EVENT.register(com.projecthero.mod.client.gui.AllMightHud::render);
		// v0.13.19: Moon Knight (Phase 1 -- lunar power, Vengeance, alter, resurrection, the six keys)
		HudRenderCallback.EVENT.register(com.projecthero.mod.client.gui.MoonKnightHud::render);
		// Moon Knight Phases 5 + 6: radial alter picker, Scholar's Sight outlines, Moonbeam / Eye of Khonshu / resurrection FX
		com.projecthero.mod.client.moonknight.MoonKnightAltersKhonshuClient.initialize();
		// v0.14.4: the Grapple Kick's lock-on preview (ring over the target + HUD name)
		com.projecthero.mod.client.moonknight.MoonKnightKickPreviewClient.initialize();
		// Moon Knight Phase 3-4: grappling line render, Cape Glide client movement, Truncheon staff model predicate
		com.projecthero.mod.client.moonknight.MoonKnightCombatClient.initialize();
		HudRenderCallback.EVENT.register(com.projecthero.mod.client.gui.HulkHud::render);
		// v0.14.8: Super Soldier -- mono HUD + the thrown shield renderer
		com.projecthero.mod.client.supersoldier.SuperSoldierClient.initialize();
		// v0.14.8: the Kryptonian -- HUD, heat-vision beams, the meteor renderer
		com.projecthero.mod.client.kryptonian.KryptonianClient.initialize();
		HudRenderCallback.EVENT.register(com.projecthero.mod.client.gui.SymbioteHud::render);
		HudRenderCallback.EVENT.register(com.projecthero.mod.client.gui.GreenLanternHud::render);
		HudRenderCallback.EVENT.register(com.projecthero.mod.client.gui.SquadLocatorBarHud::render);

		net.minecraft.client.gui.screens.MenuScreens.register(
				com.projecthero.mod.allmight.item.AllMightItems.LOCKER_MENU,
				com.projecthero.mod.client.gui.AllMightLockerScreen::new);
		net.minecraft.client.gui.screens.MenuScreens.register(
				com.projecthero.mod.ironman.IronManBlocks.STARK_FABRICATOR_MENU,
				com.projecthero.mod.client.gui.StarkFabricatorScreen::new);
		net.minecraft.client.gui.screens.MenuScreens.register(
				com.projecthero.mod.ironman.IronManBlocks.SUIT_PLATFORM_MENU,
				com.projecthero.mod.client.gui.IronManSuitPlatformScreen::new);
		net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry.register(
				com.projecthero.mod.ironman.IronManBlocks.SUIT_PLATFORM_BE,
				com.projecthero.mod.client.render.IronManSuitPlatformRenderer::new);
		// Moon Knight Phase 7: the Altar of Khonshu's scarab renderer + the ritual's white fade (receiver + overlay).
		com.projecthero.mod.client.moonknight.KhonshuTempleClient.register();
		com.projecthero.mod.client.render.IronManEntityRenderers.initialize();
		com.projecthero.mod.client.render.RaidEntityRenderers.initialize();
		com.projecthero.mod.client.render.TitanEntityRenderers.initialize();
		com.projecthero.mod.client.render.PunisherEntityRenderers.initialize();
		com.projecthero.mod.client.render.SuperheroFirstPersonArm.initialize();
		IronManBeamClient.register();
		com.projecthero.mod.client.spider.SpiderWebLineRenderer.initialize();
		com.projecthero.mod.client.thor.ThorLightningArcRenderer.initialize();
		com.projecthero.mod.client.thor.ThorFxRenderer.initialize(); // v0.14.4: shockwave rings, the Wrath charge, suit-up bolts
		com.projecthero.mod.client.firearm.BulletHoleRenderer.initialize();
		com.projecthero.mod.client.greenlantern.GreenLanternClient.initialize();
		com.projecthero.mod.client.grave.TrophyHeadClient.initialize(); // v0.14.4 trophy heads: cutout, power tint, glow
		com.projecthero.mod.client.firearm.GunMeshModels.initialize(); // v0.14.5 Punisher sniper drawn from the user's polygon mesh
		com.projecthero.mod.client.gui.TooltipWrap.initialize(); // v0.14.7: word-wrap every over-wide item tooltip line

		// GeckoLib armour: give every SuperheroArmorItem (Thor + the five Iron Man marks) a client-only
		// GeoRenderProvider so GeckoLib renders them with the shared crimson_vanguard model instead of
		// the vanilla armour model. See docs/ARMOR_MODELS.md.
		com.projecthero.mod.armor.SuperheroArmorItem.rendererFactory =
				consumer -> consumer.accept(new com.projecthero.mod.client.render.SuperheroArmorRenderProvider());

		// Iron Man call-armour picker (spec "changes 9"): server sends the list, we open the screen.
		ClientPlayNetworking.registerGlobalReceiver(com.projecthero.mod.network.IronManSuitListPayload.TYPE,
				(payload, context) -> context.client().execute(() -> context.client().setScreen(
						new com.projecthero.mod.client.gui.IronManSuitCallScreen(payload.options()))));

		// Cryokinesis ice-weapon wheel (v0.10.11): server tells us the 2 s Sneak+R hold completed.
		ClientPlayNetworking.registerGlobalReceiver(com.projecthero.mod.network.CryoWheelOpenPayload.TYPE,
				(payload, context) -> context.client().execute(() -> context.client().setScreen(
						new com.projecthero.mod.client.gui.CryoWeaponWheelScreen())));

		// Elasticity body-shape wheel (v0.10.13): Shift + C.
		ClientPlayNetworking.registerGlobalReceiver(com.projecthero.mod.network.ElasticFormWheelPayload.TYPE,
				(payload, context) -> context.client().execute(() -> context.client().setScreen(
						new com.projecthero.mod.client.gui.ElasticFormWheelScreen())));

		// Teleportation Portal picker (v0.10.13): the 5-second Z charge finished.
		ClientPlayNetworking.registerGlobalReceiver(com.projecthero.mod.network.PortalPickerPayload.TYPE,
				(payload, context) -> context.client().execute(() -> context.client().setScreen(
						new com.projecthero.mod.client.gui.PortalPickerScreen(payload.x(), payload.y(), payload.z()))));

		// Mark 7 weapon wheel ("changes 16"): server tells us to open it (empty ability string).
		ClientPlayNetworking.registerGlobalReceiver(com.projecthero.mod.network.IronManWeaponWheelPayload.TYPE,
				(payload, context) -> context.client().execute(() -> context.client().setScreen(
						new com.projecthero.mod.client.gui.IronManWeaponWheelScreen())));

		// Blank Blueprint picker ("changes 21"): server sends the mark list, we open the screen.
		ClientPlayNetworking.registerGlobalReceiver(com.projecthero.mod.network.IronManBlueprintPickerPayload.TYPE,
				(payload, context) -> context.client().execute(() -> context.client().setScreen(
						new com.projecthero.mod.client.gui.BlankBlueprintScreen(payload.entries()))));

		// v0.14.2: the Turbo Cannon arm cannon (the old tinted-elytra wings are gone -- Turbo Flight's form has its own)
		com.projecthero.mod.client.maxsteel.MaxSteelArmCannon.initialize();
		com.projecthero.mod.client.maxsteel.MaxSteelLockOnRenderer.initialize();
		com.projecthero.mod.client.render.MaxSteelFirstPersonArm.initialize();
		net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry.registerModelLayer(
				com.projecthero.mod.client.wolverine.WolverineClawsModel.LAYER,
				com.projecthero.mod.client.wolverine.WolverineClawsModel::createLayer);
		net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry.registerModelLayer(
				com.projecthero.mod.client.spider.ImpactWebModel.LAYER,
				com.projecthero.mod.client.spider.ImpactWebModel::createLayer);

		// Arc Reactor on the player's chest (Tony Stark power, no Iron Man chestplate) + Max Steel's
		// Turbo Blast charge orb / Turbo Cannon arm cannon (v0.14.2).
		// v0.14.4: white Symbiote eyes on infested / Symbiote Pet wolves, cats and cows
		com.projecthero.mod.client.symbiote.SymbioteSkin.registerLayers();
		net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback.EVENT.register(
				(entityType, entityRenderer, registrationHelper, context) -> {
					if (entityRenderer instanceof net.minecraft.client.renderer.entity.player.PlayerRenderer playerRenderer) {
						registrationHelper.register(new com.projecthero.mod.client.render.ArcReactorLayer(
								playerRenderer, context.getItemRenderer()));
						registrationHelper.register(new com.projecthero.mod.client.maxsteel.MaxSteelGearLayer(playerRenderer));
						registrationHelper.register(new com.projecthero.mod.client.render.PowerRingLayer(
								playerRenderer, context.getItemRenderer()));
						registrationHelper.register(new com.projecthero.mod.client.wolverine.WolverineClawsLayer(
								playerRenderer, context.getModelSet()));
						registrationHelper.register(new com.projecthero.mod.client.wolverine.WolverineFleshLayer(playerRenderer));
						registrationHelper.register(new com.projecthero.mod.client.wolverine.WolverineLegFleshLayer(playerRenderer));
						registrationHelper.register(new com.projecthero.mod.client.spider.SpiderHandTrackerLayer(playerRenderer));
						registrationHelper.register(new com.projecthero.mod.client.symbiote.SymbioteBladeRenderer.Layer(playerRenderer));
						registrationHelper.register(new com.projecthero.mod.client.moonknight.MoonKnightCapeLayer(playerRenderer));
						// v0.13.22: mutation overlays (stone skin, frost armour, glowing eyes, ...)
						registrationHelper.register(new com.projecthero.mod.client.mutation.MutationOverlayLayer(playerRenderer));
					}
				});

		// Zombie Raid: the dark-purple sky tint while a raid is happening around the player. The raid
		// bar itself is a vanilla boss bar and needs no packet.
		ClientPlayNetworking.registerGlobalReceiver(com.projecthero.mod.network.RaidSkyPayload.TYPE,
				(payload, context) -> context.client().execute(
						() -> com.projecthero.mod.client.gui.RaidSkyTint.accept(payload)));

		// Max Steel: Steel's directional threat warning.
		ClientPlayNetworking.registerGlobalReceiver(com.projecthero.mod.network.MaxSteelWarningPayload.TYPE,
				(payload, context) -> context.client().execute(
						() -> com.projecthero.mod.client.gui.MaxSteelHud.flashWarning(payload.yawToThreat())));

		// Spider-Sense: the pre-emptive directional danger warning.
		ClientPlayNetworking.registerGlobalReceiver(com.projecthero.mod.network.SpiderSenseWarningPayload.TYPE,
				(payload, context) -> context.client().execute(
						() -> com.projecthero.mod.client.gui.SpiderHud.flashWarning(
								payload.kind(), payload.yaw(), payload.vertical())));

		// Spider-Sense: the per-viewer red threat glow (v0.9.3). Replaces the client's whole threat set.
		ClientPlayNetworking.registerGlobalReceiver(com.projecthero.mod.network.SpiderSenseGlowPayload.TYPE,
				(payload, context) -> context.client().execute(() -> {
					long now = context.client().level != null ? context.client().level.getGameTime() : 0L;
					com.projecthero.mod.client.spider.SpiderSenseGlowClient.accept(payload.ids(), now);
				}));

		// Titan Shifter: footfall / impact tremors near any Titan (cosmetic, applied by TitanCameraMixin).
		ClientPlayNetworking.registerGlobalReceiver(com.projecthero.mod.network.TitanShakePayload.TYPE,
				(payload, context) -> context.client().execute(
						() -> com.projecthero.mod.client.titanshifter.TitanShakeClient.accept(payload.intensity(), payload.ticks())));

		// A brief camera zoom-in cue for a world event building up (currently: the Oathbreaker's summon).
		ClientPlayNetworking.registerGlobalReceiver(com.projecthero.mod.network.WorldEventZoomPayload.TYPE,
				(payload, context) -> context.client().execute(
						() -> com.projecthero.mod.client.oathbreaker.WorldEventZoomClient.accept(payload.amount(), payload.ticks())));

		// Titan Roar: everything within 50 blocks is outlined blue for the roaring shifter only (v0.12.43).
		ClientPlayNetworking.registerGlobalReceiver(com.projecthero.mod.network.TitanRoarSensePayload.TYPE,
				(payload, context) -> context.client().execute(() -> {
					long now = context.client().level != null ? context.client().level.getGameTime() : 0L;
					com.projecthero.mod.client.titanshifter.TitanRoarSenseClient.accept(payload.ids(), payload.ticks(), now);
				}));

		// Wolverine senses: the per-viewer orange hunter glow + the N-key sniff highlight.
		ClientPlayNetworking.registerGlobalReceiver(com.projecthero.mod.network.WolverineSensePayload.TYPE,
				(payload, context) -> context.client().execute(() -> {
					long now = context.client().level != null ? context.client().level.getGameTime() : 0L;
					com.projecthero.mod.client.wolverine.WolverineSenseClient.accept(payload.hunters(), payload.sniffed(),
							payload.sniffTicks(), now);
				}));

		// v0.13.4: Adamantium Execution's lock-on target -- glows red for this viewer only.
		ClientPlayNetworking.registerGlobalReceiver(com.projecthero.mod.network.WolverineExecutionTargetPayload.TYPE,
				(payload, context) -> context.client().execute(
						() -> com.projecthero.mod.client.wolverine.WolverineExecutionTargetClient.accept(payload.targetId())));

		// Green Lantern Ring Scan: the per-viewer hostile/passive glow (v0.11.10). Replaces the client's
		// whole scan set, same pattern as Spider-Sense above.
		ClientPlayNetworking.registerGlobalReceiver(com.projecthero.mod.network.GreenLanternRingScanPayload.TYPE,
				(payload, context) -> context.client().execute(() -> {
					long now = context.client().level != null ? context.client().level.getGameTime() : 0L;
					com.projecthero.mod.client.greenlantern.GreenLanternRingScanClient.accept(
							payload.hostileIds(), payload.passiveIds(), now);
				}));

		// Will Trial "Are you afraid?" prompt (v0.11.11): a vanilla yes/no confirmation. v0.11.14: the
		// screen carries only the question -- answer NO (not afraid) to earn the ring, YES (afraid) cancels
		// the trial so someone else can attempt it. The answer goes straight back to the server, which
		// re-checks it against the player's own active trial -- this screen is just the UI, nothing here
		// is trusted.
		ClientPlayNetworking.registerGlobalReceiver(com.projecthero.mod.network.GreenLanternTrialPromptPayload.TYPE,
				(payload, context) -> context.client().execute(() -> context.client().setScreen(new net.minecraft.client.gui.screens.ConfirmScreen(
						afraid -> {
							ClientPlayNetworking.send(new com.projecthero.mod.network.GreenLanternTrialAnswerPayload(afraid));
							context.client().setScreen(null);
						},
						net.minecraft.network.chat.Component.translatable("message.projecthero.green_lantern.trial_afraid.title"),
						net.minecraft.network.chat.Component.empty()))));

		// Symbiote bonding minigame (v0.12.25): the server sends the seed, the screen records press ticks.
		ClientPlayNetworking.registerGlobalReceiver(com.projecthero.mod.network.SymbioteBondGamePayload.TYPE,
				(payload, context) -> context.client().execute(() -> context.client().setScreen(
						new com.projecthero.mod.client.gui.SymbioteBondGameScreen(payload.seed()))));

		// Shift + Web Zip: the server tells the client to arm its adhesion grab intent so the zip lands
		// the player flush against the wall and the climb engine sticks with no double-tap (v0.6.21).
		ClientPlayNetworking.registerGlobalReceiver(com.projecthero.mod.network.SpiderClimbGrabPayload.TYPE,
				(payload, context) -> context.client().execute(() -> {
					net.minecraft.client.player.LocalPlayer p = context.client().player;
					if (p != null) {
						p.getAttachedOrCreate(com.projecthero.mod.attachment.ModAttachments.SPIDER_CLIMB_LOCAL)
								.setGrabIntent(true);
					}
				}));

		// v0.12.20: fading web strands (Web Zip, Combat Mode moves) from a player's hand to a target.
		ClientPlayNetworking.registerGlobalReceiver(com.projecthero.mod.network.SpiderWebStrandPayload.TYPE,
				(payload, context) -> context.client().execute(
						() -> com.projecthero.mod.client.spider.SpiderStrands.accept(payload)));

		// v0.13.4: crackling lightning arc segments (Lightning Beam, Chain Lightning).
		ClientPlayNetworking.registerGlobalReceiver(com.projecthero.mod.network.ThorLightningArcPayload.TYPE,
				(payload, context) -> context.client().execute(
						() -> com.projecthero.mod.client.thor.ThorLightningArcClient.accept(payload)));

		// Firearms: server-accepted shot -> recoil kick; headshot -> HUD flash.
		ClientPlayNetworking.registerGlobalReceiver(com.projecthero.mod.network.FirearmShotPayload.TYPE,
				(payload, context) -> context.client().execute(
						() -> com.projecthero.mod.client.firearm.FirearmClient.applyKick(payload.verticalKick())));
		ClientPlayNetworking.registerGlobalReceiver(com.projecthero.mod.network.FirearmHeadshotPayload.TYPE,
				(payload, context) -> context.client().execute(
						com.projecthero.mod.client.gui.FirearmHud::flashHeadshot));
		// Firearms: a shot hit a block -> drop a fading bullet-hole decal there.
		ClientPlayNetworking.registerGlobalReceiver(com.projecthero.mod.network.BulletHolePayload.TYPE,
				(payload, context) -> context.client().execute(() ->
						com.projecthero.mod.client.firearm.BulletHoleRenderer.add(
								payload.x(), payload.y(), payload.z(), payload.face())));
		// Punisher: server asked us to open the Arsenal wheel.
		ClientPlayNetworking.registerGlobalReceiver(com.projecthero.mod.network.PunisherArsenalOpenPayload.TYPE,
				(payload, context) -> context.client().execute(() -> {
					if (context.client().screen == null) {
						context.client().setScreen(new com.projecthero.mod.client.gui.ArsenalWheelScreen());
					}
				}));

		// The squad roster, pushed a few times a second while the player is in a squad.
		ClientPlayNetworking.registerGlobalReceiver(com.projecthero.mod.network.SquadInfoPayload.TYPE,
				(payload, context) -> context.client().execute(() ->
						com.projecthero.mod.client.squad.SquadClient.accept(payload)));

		ClientTickEvents.END_CLIENT_TICK.register(client ->
				com.projecthero.mod.client.firearm.FirearmClient.clientTick(client));
		ClientTickEvents.END_CLIENT_TICK.register(ProjectHeroModClient::handleKeyBinds);
		ClientTickEvents.END_CLIENT_TICK.register(MagneticSenseClient::clientTick);
		ClientTickEvents.END_CLIENT_TICK.register(SonicMotionClient::clientTick);
		ClientTickEvents.END_CLIENT_TICK.register(IronManFlightFxClient::clientTick);
		ClientTickEvents.END_CLIENT_TICK.register(com.projecthero.mod.client.hulk.HulkClient::tick);
		ClientTickEvents.END_CLIENT_TICK.register(MaxSteelFlightFxClient::clientTick);
		ClientTickEvents.END_CLIENT_TICK.register(com.projecthero.mod.client.spider.SpiderInputClient::clientTick);
		ClientTickEvents.END_CLIENT_TICK.register(com.projecthero.mod.client.symbiote.SymbioteFxClient::clientTick);
		ClientTickEvents.END_CLIENT_TICK.register(client -> com.projecthero.mod.client.titanshifter.TitanShakeClient.tick());
		ClientTickEvents.END_CLIENT_TICK.register(client -> com.projecthero.mod.client.oathbreaker.WorldEventZoomClient.tick());
	}

	private static void handleKeyBinds(Minecraft client) {
		FlightPoseHelper.clientTick(client.level);

		if (client.player == null) {
			// Leaving a world must not leave a stale raid-sky tint on the next one.
			com.projecthero.mod.client.gui.RaidSkyTint.reset();
			com.projecthero.mod.client.firearm.BulletHoleRenderer.clear();
			java.util.Arrays.fill(slotWasDown, false);
			wasFlying = false;
			jumpWasDown = false;
			ticksSinceJumpPress = Integer.MAX_VALUE;
			powerSelectWasDown = false;
			powerInfoWasDown = false;
			maxSteelTransformWasDown = false;
			titanSprintWasDown = false;
			squadMenuWasDown = false;
			glConstructHeldTicks = 0;
			glWheelOpenedThisHold = false;
			// Leaving a world must not carry the last server's squad roster into the next one.
			com.projecthero.mod.client.squad.SquadClient.clear();
			return;
		}

		handleGreenLanternConstructWheelHold(client);
		handleTitanSprint(client);

		boolean flyingNow = client.player.getAttachedOrElse(ModAttachments.FLYING, false);
		if (flyingNow && !wasFlying) {
			client.getSoundManager().play(new ThorFlightSoundInstance(client.player));
		}
		wasFlying = flyingNow;

		if (client.screen != null) {
			// A menu opened while a slot key was held -- release any that are "down" so a channelled
			// ability (Thor's beam, a flamethrower) doesn't stay stuck on server-side.
			for (int i = 0; i < 6; i++) {
				if (slotWasDown[i]) {
					slotWasDown[i] = false;
					// v0.11.2: slot 6 (C) opening the construct wheel is the one case where this cleanup
					// must NOT forward a release -- the press was already sent moments earlier, and
					// forwarding this release too would report a several-tick "hold" to the server that
					// (being under GreenLanternConfig.CONSTRUCT_WHEEL_HOLD_TICKS, since the wheel opens
					// the instant the client-side counter reaches that same threshold, one tick before the
					// server would ever see it) reads as a short tap and deploys the selected construct
					// out from under the player the moment the wheel opens. Leaving the server's
					// ABILITY6_PRESSED entry un-removed is harmless: the next real press simply overwrites
					// it (see GreenLanternAbilityManager#handleAbilitySix).
					if (i == 5 && glWheelOpenedThisHold) {
						continue;
					}
					ClientPlayNetworking.send(new AbilityInputPayload(i + 1, false));
				}
			}
			// v0.13.22: same for a mutation's H / N utility channel
			if (utility7Held) {
				utility7Held = false;
				ClientPlayNetworking.send(new AbilityInputPayload(7, false));
			}
			if (utility8Held) {
				utility8Held = false;
				ClientPlayNetworking.send(new AbilityInputPayload(8, false));
			}
			jumpWasDown = false;
			return;
		}

		handleDoubleJump(client, client.player);
		handlePowerSelect(client);
		handlePowerInfo(client);
		handleSquadMenu(client);
		handleMaxSteelTransform(client);
		handleWolverineOffHand(client);
		handleChargedPunch(client);

		boolean holdingFirearm = client.player.getMainHandItem().getItem()
				instanceof com.projecthero.mod.firearm.item.FirearmItem;

		for (int i = 0; i < 6; i++) {
			KeyMapping key = ModKeyBindings.ABILITY_SLOTS[i];
			int slot = i + 1;

			// Ability 1 (R) while holding a firearm: a quick tap reloads; a longer hold falls through
			// to the ability system (Arsenal wheel for a Punisher, nothing for anyone else).
			if (i == 0 && holdingFirearm) {
				handleFirearmAbilityOne(key);
				continue;
			}

			// PRESS edge: consumeClick() also catches a sub-tick tap that isDown() would miss.
			boolean pressedThisTick = false;
			while (key.consumeClick()) {
				ClientPlayNetworking.send(new AbilityInputPayload(slot, true));
				pressedThisTick = true;
			}

			boolean down = key.isDown();
			if (down && !slotWasDown[i] && !pressedThisTick) {
				ClientPlayNetworking.send(new AbilityInputPayload(slot, true));
			}
			if (!down && slotWasDown[i]) {
				ClientPlayNetworking.send(new AbilityInputPayload(slot, false));
			}
			slotWasDown[i] = down;
		}
	}

	/**
	 * Ability-1 (R) while a firearm is held. A tap under {@link #FIREARM_RELOAD_TAP_TICKS} sends a
	 * reload request; holding past that threshold sends the ordinary ability-1 press (so a Punisher
	 * still opens the Arsenal wheel by holding R). Keeps {@code slotWasDown[0]} consistent so the
	 * generic loop is not confused the tick a non-firearm item comes back into hand.
	 */
	private static void handleFirearmAbilityOne(KeyMapping key) {
		while (key.consumeClick()) {
			// count a sub-tick tap as a press
			if (firearmAbilityOneHeld < 0) {
				firearmAbilityOneHeld = 0;
			}
		}
		boolean down = key.isDown();

		if (down && !slotWasDown[0]) {
			firearmAbilityOneHeld = 0;
			firearmAbilityOneSentPress = false;
		} else if (down) {
			firearmAbilityOneHeld++;
			if (firearmAbilityOneHeld >= FIREARM_RELOAD_TAP_TICKS && !firearmAbilityOneSentPress) {
				ClientPlayNetworking.send(new AbilityInputPayload(1, true));
				firearmAbilityOneSentPress = true;
			}
		} else if (slotWasDown[0]) {
			if (firearmAbilityOneSentPress) {
				ClientPlayNetworking.send(new AbilityInputPayload(1, false));
			} else if (firearmAbilityOneHeld >= 0 && firearmAbilityOneHeld < FIREARM_RELOAD_TAP_TICKS) {
				ClientPlayNetworking.send(new com.projecthero.mod.network.FirearmActionPayload(
						com.projecthero.mod.network.FirearmActionPayload.Action.RELOAD));
			}
			firearmAbilityOneHeld = -1;
			firearmAbilityOneSentPress = false;
		}
		slotWasDown[0] = down;
	}

	/**
	 * v0.11.2: holding ability slot 6 (C) for {@code GreenLanternConfig.CONSTRUCT_WHEEL_HOLD_TICKS}
	 * opens the construct wheel. Deliberately does NOT touch {@link #slotWasDown} or send any
	 * {@link AbilityInputPayload} itself -- the generic per-slot loop below keeps forwarding C's
	 * press/release exactly as it does for every other slot (so a quick tap still deploys, and Shift+C
	 * still dismisses via the existing server-side path), and opening a {@link Screen} makes the
	 * existing "screen opened while a slot key was held" cleanup (this same method's caller) release
	 * slot 6 on the client's own next tick, same as it would for any other ability key.
	 */
	private static void handleGreenLanternConstructWheelHold(Minecraft client) {
		boolean eligible = client.screen == null && !Screen.hasShiftDown()
				&& ModKeyBindings.ABILITY_6.isDown() && greenLanternHasWheelContext(client.player);
		if (!eligible) {
			glConstructHeldTicks = 0;
			glWheelOpenedThisHold = false;
			return;
		}
		glConstructHeldTicks++;
		if (!glWheelOpenedThisHold
				&& glConstructHeldTicks >= com.projecthero.mod.greenlantern.GreenLanternConfig.CONSTRUCT_WHEEL_HOLD_TICKS) {
			glWheelOpenedThisHold = true;
			client.setScreen(new com.projecthero.mod.client.gui.GreenLanternConstructWheelScreen());
		}
	}

	/** Mirrors the server's {@code GreenLanternAbilityManager.hasContext}: bonded, no mutation active. */
	private static boolean greenLanternHasWheelContext(LocalPlayer player) {
		if (player == null) {
			return false;
		}
		com.projecthero.mod.greenlantern.data.GreenLanternState state =
				player.getAttachedOrElse(ModAttachments.GREEN_LANTERN_STATE, null);
		if (state == null || !state.hasPower) {
			return false;
		}
		com.projecthero.mod.hero.data.ExperimentalState experimental =
				player.getAttachedOrElse(ModAttachments.EXPERIMENTAL_STATE, null);
		return experimental == null || experimental.activePower.isEmpty();
	}

	/** v0.13.22: whether H / N are currently held down on behalf of a mutation's utility slot (7 / 8). */
	private static boolean utility7Held;
	private static boolean utility8Held;

	/** Left or right Alt held -- Alt+H / Alt+N always reach the mutation's utility slots, whatever else owns H / N. */
	private static boolean altDown(Minecraft client) {
		long w = client.getWindow().getWindow();
		return org.lwjgl.glfw.GLFW.glfwGetKey(w, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_ALT) == org.lwjgl.glfw.GLFW.GLFW_PRESS
				|| org.lwjgl.glfw.GLFW.glfwGetKey(w, org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_ALT) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
	}

	/**
	 * Whether the player's selected mutation defines utility slot {@code slot} (7 = H, 8 = N) and Mjolnir is not in
	 * hand. v0.14.7: checked per key -- Super Speed has N (Speed Carry) but no H, so its plain H stays the power wheel.
	 */
	private static boolean mutationHasUtility(Minecraft client, int slot) {
		LocalPlayer p = client.player;
		if (p == null || client.screen != null || ThorPowers.isHoldingMjolnir(p)) {
			return false;
		}
		com.projecthero.mod.hero.data.ExperimentalState st = p.getAttachedOrElse(ModAttachments.EXPERIMENTAL_STATE, null);
		if (st == null || st.activePower.isEmpty()) {
			return false;
		}
		com.projecthero.mod.hero.Power power = com.projecthero.mod.hero.Powers.byKey(st.activePower);
		return power != null && power.hasSlot(com.projecthero.mod.hero.AbilitySlot.byNumber(slot));
	}

	/** v0.14.5: whether a mutation is selected at all (and Mjolnir is not in hand) -- Sneak+N combos need no N slot. */
	private static boolean mutationSelected(Minecraft client) {
		LocalPlayer p = client.player;
		if (p == null || client.screen != null || ThorPowers.isHoldingMjolnir(p)) {
			return false;
		}
		com.projecthero.mod.hero.data.ExperimentalState st = p.getAttachedOrElse(ModAttachments.EXPERIMENTAL_STATE, null);
		return st != null && !st.activePower.isEmpty() && com.projecthero.mod.hero.Powers.byKey(st.activePower) != null;
	}

	/**
	 * Sends a utility-slot press / release. Returns true if this tick's H or N edge was consumed by the mutation
	 * (Alt held, or the key being released after a mutation press), so the Hero-Tier chain must not see it.
	 */
	private static boolean handleUtilityEdge(Minecraft client, boolean down, boolean wasDown, int slot) {
		boolean held = slot == 7 ? utility7Held : utility8Held;
		if (held && (!down || client.screen != null)) {
			ClientPlayNetworking.send(new AbilityInputPayload(slot, false));
			if (slot == 7) {
				utility7Held = false;
			} else {
				utility8Held = false;
			}
			return true;
		}
		if (down && !wasDown && altDown(client) && mutationHasUtility(client, slot)) {
			pressUtility(slot);
			return true;
		}
		return held;
	}

	private static void pressUtility(int slot) {
		ClientPlayNetworking.send(new AbilityInputPayload(slot, true));
		if (slot == 7) {
			utility7Held = true;
		} else {
			utility8Held = true;
		}
	}

	private static void handlePowerSelect(Minecraft client) {
		boolean down = ModKeyBindings.POWER_SELECT.isDown();
		if (handleUtilityEdge(client, down, powerSelectWasDown, 7)) {
			powerSelectWasDown = down;
			return;
		}
		if (down && !powerSelectWasDown) {
			// "changes 19": H while wearing any Iron Man armour opens / closes the helmet faceplate.
			// v0.6.16: H while transformed as Max Steel does the same for its helmet. Otherwise H opens
			// the experimental power wheel as before.
			if (client.player != null && com.projecthero.mod.titanshifter.TitanShifter.isShifter(client.player)
					&& (com.projecthero.mod.titanshifter.TitanShifter.phase(client.player).insideForm() || !Screen.hasShiftDown())) {
				// v0.12.32: H is Titan Shift -- transform / detransform (the J key is gone). Inside the Titan it always
				// reverts; outside, Shift+H still falls through to the power wheel / Thor armour below.
				ClientPlayNetworking.send(new com.projecthero.mod.network.TitanShiftPayload(
						com.projecthero.mod.network.TitanShiftPayload.Action.TOGGLE_SHIFT));
			} else if (client.player != null && com.projecthero.mod.allmight.AllMight.hasPower(client.player) && !Screen.hasShiftDown()) {
				// v0.12.33: H as All Might transforms / changes back (Shift+H still opens the power wheel).
				ClientPlayNetworking.send(new com.projecthero.mod.network.AllMightActionPayload(
						com.projecthero.mod.network.AllMightActionPayload.Action.TOGGLE_FORM));
			} else if (client.player != null && com.projecthero.mod.hulk.Hulk.hasPower(client.player) && !Screen.hasShiftDown()) {
				// v0.13.11: H with the Gamma power lets the Hulk out (75+ rage; Shift+H still opens the power wheel).
				ClientPlayNetworking.send(new com.projecthero.mod.network.HulkActionPayload(
						com.projecthero.mod.network.HulkActionPayload.Action.TRANSFORM));
			} else if (client.player != null && com.projecthero.mod.moonknight.MoonKnight.hasPower(client.player) && !Screen.hasShiftDown()) {
				// v0.13.19: H as Moon Knight -- the suit on / off (Shift+H still opens the power wheel).
				ClientPlayNetworking.send(new com.projecthero.mod.network.MoonKnightActionPayload(
						com.projecthero.mod.network.MoonKnightActionPayload.Action.TOGGLE_SUIT, 0));
			} else if (client.player != null && wearingAnyIronMan(client.player)) {
				ClientPlayNetworking.send(new com.projecthero.mod.network.IronManActionPayload(
						com.projecthero.mod.network.IronManActionPayload.Action.TOGGLE_FACEPLATE));
			} else if (client.player != null && com.projecthero.mod.maxsteel.MaxSteel.hasPower(client.player)
					&& (!Screen.hasShiftDown() || com.projecthero.mod.maxsteel.MaxSteel.isTransformed(client.player))) {
				// v0.14.2: plain H is THE Max Steel key -- Go Turbo / power down (N no longer does it). Shift+H while
				// suited opens / seals the helmet; Shift+H unsuited still falls through to the power wheel.
				ClientPlayNetworking.send(new com.projecthero.mod.network.MaxSteelActionPayload(
						Screen.hasShiftDown()
								? com.projecthero.mod.network.MaxSteelActionPayload.Action.TOGGLE_HELMET
								: com.projecthero.mod.network.MaxSteelActionPayload.Action.TRANSFORM_TOGGLE));
			} else if (client.player != null
					&& com.projecthero.mod.symbiote.Symbiote.canToggle(client.player)) {
				// v0.9.10: a Spider-Man who has bonded with the Symbiote -- plain H engages / retracts the
				// black suit. Shift+H still toggles the costume mask when a hood is worn, so nothing about
				// the old H behaviour is lost.
				if (Screen.hasShiftDown()
						&& com.projecthero.mod.spider.SpiderMask.wearingHood(client.player)) {
					ClientPlayNetworking.send(new com.projecthero.mod.network.SpiderActionPayload(
							com.projecthero.mod.network.SpiderActionPayload.Action.TOGGLE_MASK));
				} else {
					ClientPlayNetworking.send(new com.projecthero.mod.network.SpiderActionPayload(
							com.projecthero.mod.network.SpiderActionPayload.Action.TOGGLE_SYMBIOTE));
				}
			} else if (client.player != null && com.projecthero.mod.spider.SpiderMask.wearingHood(client.player)) {
				// v0.6.20: H while wearing the Spider-Man costume pulls the mask off / on.
				ClientPlayNetworking.send(new com.projecthero.mod.network.SpiderActionPayload(
						com.projecthero.mod.network.SpiderActionPayload.Action.TOGGLE_MASK));
			} else if (client.player != null && com.projecthero.mod.wolverine.Wolverine.hasPower(client.player)
					&& !Screen.hasShiftDown()) {
				// v0.12.1: H as Wolverine deploys / retracts the claws (Shift+H still opens the power wheel).
				ClientPlayNetworking.send(new com.projecthero.mod.network.WolverineActionPayload(
						com.projecthero.mod.network.WolverineActionPayload.Action.TOGGLE_CLAWS));
			} else if (client.player != null
					&& client.player.getAttachedOrElse(ModAttachments.BOUND_HAMMER_ID, null) != null
					&& (!Screen.hasShiftDown() || com.projecthero.mod.titanshifter.TitanShifter.isShifter(client.player)
							|| com.projecthero.mod.wolverine.Wolverine.hasPower(client.player)
							|| com.projecthero.mod.allmight.AllMight.hasPower(client.player)
							|| com.projecthero.mod.hulk.Hulk.hasPower(client.player)
							|| com.projecthero.mod.moonknight.MoonKnight.hasPower(client.player))) {
				// v0.12.32: H as a Thor bound to Mjolnir -- lightning strikes down and Thor's Armour forms (H again
				// dismisses it). Shift+H still opens the power wheel, unless another power already owns plain H,
				// in which case Shift+H is the armour.
				ClientPlayNetworking.send(new com.projecthero.mod.network.ThorActionPayload(
						com.projecthero.mod.network.ThorActionPayload.Action.TOGGLE_ARMOUR));
			} else if (client.player != null && !Screen.hasShiftDown() && greenLanternHasWheelContext(client.player)) {
				// v0.14.3: H as Green Lantern -- the Giant Hand: grab, then H again to hurl (Shift+H still opens the power wheel).
				ClientPlayNetworking.send(new com.projecthero.mod.network.GreenLanternActionPayload(
						com.projecthero.mod.network.GreenLanternActionPayload.Action.GIANT_HAND));
			} else if (!Screen.hasShiftDown() && mutationHasUtility(client, 7)) {
				// v0.13.22: a mutation with H / N abilities -- plain H is its Utility 1; Shift+H opens the power wheel.
				pressUtility(7);
			} else {
				client.setScreen(new PowerWheelScreen());
			}
		}
		if (!down && powerSelectWasDown && client.player != null && com.projecthero.mod.titanshifter.TitanShifter.isShifter(client.player)
				&& !com.projecthero.mod.titanshifter.TitanShifter.phase(client.player).insideForm()) {
			// v0.12.39: letting go of H ends an emergency-shift hold that has not finished (the server ignores it otherwise)
			ClientPlayNetworking.send(new com.projecthero.mod.network.TitanShiftPayload(
					com.projecthero.mod.network.TitanShiftPayload.Action.EMERGENCY_RELEASE));
		}
		powerSelectWasDown = down;
	}

	/**
	 * Super Strength client gestures that vanilla never reports on its own:
	 * <ul>
	 *   <li><b>Charged Punch</b> -- hold the attack key 1 s (charge does not build while the
	 *       crosshair is on a minable block, so ordinary mining never winds it up), then release to
	 *       throw it ({@code PERFORM_CHARGED_PUNCH}).</li>
	 *   <li><b>Power Leap charge bar</b> -- how long X has been held, so the HUD can show the tier.</li>
	 * </ul>
	 * The server re-validates power, cooldown and timing.
	 */
	private static void handleChargedPunch(Minecraft client) {
		LocalPlayer p = client.player;
		com.projecthero.mod.hero.data.ExperimentalState st = p == null ? null
				: p.getAttachedOrElse(ModAttachments.EXPERIMENTAL_STATE, null);
		boolean strengthKit = st != null && st.ownedPowers.contains("power_01_super_strength")
				&& "power_01_super_strength".equals(st.activePower);
		boolean holdingFirearm = p != null && p.getMainHandItem().getItem()
				instanceof com.projecthero.mod.firearm.item.FirearmItem;
		boolean usable = strengthKit && !holdingFirearm && client.screen == null;

		// ---- charged punch ----
		// The wind-up cannot even begin while the punch's own cooldown is running (the server tracks
		// it as the synced `charged_cd` resource, ticks not seconds).
		boolean punchOnCd = st != null
				&& st.resources.getOrDefault("power_01_super_strength/charged_cd", 0.0f) > 0.5f;
		boolean attackDown = usable && !punchOnCd && client.options.keyAttack.isDown();
		boolean lookingAtBlock = client.hitResult != null
				&& client.hitResult.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK;
		if (attackDown && !lookingAtBlock) {
			chargedPunchHold++;
			chargedPunchWasCharging = true;
		} else if (attackDown) {
			// holding, but aimed at a block -- freeze the charge, do not build it and do not throw
		} else {
			// Only a genuine key release throws the punch (not opening a screen mid-charge).
			if (chargedPunchWasCharging && chargedPunchHold >= CHARGED_PUNCH_HOLD_TICKS
					&& !client.options.keyAttack.isDown()) {
				ClientPlayNetworking.send(new com.projecthero.mod.network.StrengthActionPayload(
						com.projecthero.mod.network.StrengthActionPayload.Action.PERFORM_CHARGED_PUNCH, 0));
			}
			chargedPunchHold = 0;
			chargedPunchWasCharging = false;
		}

		// ---- Power Leap: the client owns the timing. Charge while X is held, launch on release with
		// the exact tick count so the distance is deterministic. ----
		boolean leapOnCd = false;
		if (st != null && client.level != null) {
			Long ready = st.abilityReadyAt.get("power_01_super_strength/power_leap");
			leapOnCd = ready != null && ready > client.level.getGameTime();
		}
		boolean leapDown = usable && !leapOnCd && ModKeyBindings.ABILITY_3.isDown();
		if (leapDown) {
			leapChargeHold = Math.min(LEAP_MAX_CHARGE_TICKS + 10, leapChargeHold + 1);
			leapWasCharging = true;
		} else {
			if (leapWasCharging && leapChargeHold >= 1 && strengthKit && !holdingFirearm && client.screen == null) {
				ClientPlayNetworking.send(new com.projecthero.mod.network.StrengthActionPayload(
						com.projecthero.mod.network.StrengthActionPayload.Action.PERFORM_POWER_LEAP, leapChargeHold));
			}
			leapChargeHold = 0;
			leapWasCharging = false;
		}
	}

	private static boolean maxSteelTransformWasDown = false;

	/** v0.13.9: true while this client has told the server its claw guard is up (see {@link #handleWolverineOffHand}). */
	public static boolean wolverineGuardSent;

	/**
	 * Wolverine, claws out, empty hands: HOLDING right click raises the claw guard (v0.13.9 -- it used to be an
	 * off-hand strike; left click now picks a random hand instead, see {@code WolverineAttackMixin}). Sends the
	 * press/release edges; the server re-validates and syncs the flag everyone poses from.
	 */
	private static void handleWolverineOffHand(Minecraft client) {
		boolean want = client.player != null && client.screen == null && client.options.keyUse.isDown()
				&& com.projecthero.mod.wolverine.WolverineBlock.canBlock(client.player);
		if (want != wolverineGuardSent) {
			wolverineGuardSent = want;
			if (client.getConnection() == null) {
				return; // left the world with the guard up: nothing to tell (it isn't persisted)
			}
			ClientPlayNetworking.send(new com.projecthero.mod.network.WolverineActionPayload(want
					? com.projecthero.mod.network.WolverineActionPayload.Action.BLOCK_START
					: com.projecthero.mod.network.WolverineActionPayload.Action.BLOCK_STOP));
		}
	}

	private static boolean titanSprintWasDown;

	/** v0.12.34: a rider never reports sprinting, so the Titan is told whether Sprint is held (a run starts after 3 s of it while walking). */
	private static void handleTitanSprint(Minecraft client) {
		boolean now = client.player != null && client.screen == null && client.options.keySprint.isDown()
				&& com.projecthero.mod.titanshifter.TitanShifter.inTitan(client.player);
		if (now != titanSprintWasDown) {
			titanSprintWasDown = now;
			ClientPlayNetworking.send(new com.projecthero.mod.network.TitanShiftPayload(now
					? com.projecthero.mod.network.TitanShiftPayload.Action.SPRINT_ON
					: com.projecthero.mod.network.TitanShiftPayload.Action.SPRINT_OFF));
		}
	}

	private static void handleMaxSteelTransform(Minecraft client) {
		boolean down = ModKeyBindings.MAX_STEEL_TRANSFORM.isDown();
		if (handleUtilityEdge(client, down, maxSteelTransformWasDown, 8)) {
			maxSteelTransformWasDown = down;
			return;
		}
		boolean humanShifter = client.player != null && com.projecthero.mod.titanshifter.TitanShifter.isShifter(client.player)
				&& com.projecthero.mod.titanshifter.TitanShifter.phase(client.player) == com.projecthero.mod.titanshifter.TitanPhase.HUMAN;
		if (down && !maxSteelTransformWasDown && humanShifter && Screen.hasShiftDown()) {
			// v0.12.43: Shift+N as a base-form Titan Shifter toggles the passive regeneration (always available, even if another power owns plain N).
			ClientPlayNetworking.send(new com.projecthero.mod.network.TitanShiftPayload(
					com.projecthero.mod.network.TitanShiftPayload.Action.TOGGLE_REGEN));
		} else if (down && !maxSteelTransformWasDown && client.player != null
				&& com.projecthero.mod.titanshifter.TitanShifter.inTitan(client.player)) {
			// v0.12.34: N inside the Titan grabs the mob you look at / bites it; Shift+N sets it down gently.
			ClientPlayNetworking.send(new com.projecthero.mod.network.TitanShiftPayload(Screen.hasShiftDown()
					? com.projecthero.mod.network.TitanShiftPayload.Action.LET_DOWN
					: com.projecthero.mod.network.TitanShiftPayload.Action.GRAB_BITE));
		} else if (down && !maxSteelTransformWasDown && client.player != null
				&& com.projecthero.mod.spider.SpiderMan.hasPower(client.player)) {
			// v0.12.20: Spider-Man -- N swaps Traversal Mode and Combat Mode.
			ClientPlayNetworking.send(new com.projecthero.mod.network.SpiderActionPayload(
					com.projecthero.mod.network.SpiderActionPayload.Action.TOGGLE_MODE));
		} else if (down && !maxSteelTransformWasDown && client.player != null
				&& com.projecthero.mod.allmight.AllMight.isFullPower(client.player)) {
			// v0.12.36: All Might -- N (Power Form) opens the costume locker.
			ClientPlayNetworking.send(new com.projecthero.mod.network.AllMightActionPayload(
					com.projecthero.mod.network.AllMightActionPayload.Action.OPEN_LOCKER));
		} else if (down && !maxSteelTransformWasDown && client.player != null
				&& com.projecthero.mod.wolverine.Wolverine.hasPower(client.player)) {
			// Wolverine: Utility 2 (N) sniffs -- highlights everything within 40 blocks for 20 s.
			ClientPlayNetworking.send(new com.projecthero.mod.network.WolverineActionPayload(
					com.projecthero.mod.network.WolverineActionPayload.Action.SNIFF));
		} else if (down && !maxSteelTransformWasDown && client.player != null
				&& com.projecthero.mod.symbiote.Symbiote.isNormalHost(client.player)) {
			// v0.11.16: Utility 2 (N) as a Symbiote host toggles Predator Vision (the glow outline).
			boolean on = com.projecthero.mod.client.symbiote.SymbioteFxClient.togglePredatorVision();
			client.player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
					on ? "projecthero.symbiote.predator_vision.on" : "projecthero.symbiote.predator_vision.off"), true);
		} else if (down && !maxSteelTransformWasDown && client.player != null && greenLanternHasWheelContext(client.player)) {
			// v0.14.3: Green Lantern -- N dismisses every construct (was Shift+C); Shift + hold N for 5 s takes the ring off.
			if (Screen.hasShiftDown()) {
				glRingRemoveHeld = true;
				ClientPlayNetworking.send(new com.projecthero.mod.network.GreenLanternActionPayload(
						com.projecthero.mod.network.GreenLanternActionPayload.Action.RING_REMOVE_START));
			} else {
				ClientPlayNetworking.send(new com.projecthero.mod.network.GreenLanternActionPayload(
						com.projecthero.mod.network.GreenLanternActionPayload.Action.CLEAR_CONSTRUCTS));
			}
		} else if (down && !maxSteelTransformWasDown && humanShifter) {
			// v0.12.43: plain N as a base-form Titan Shifter (no other power claiming N) toggles the passive regeneration.
			ClientPlayNetworking.send(new com.projecthero.mod.network.TitanShiftPayload(
					com.projecthero.mod.network.TitanShiftPayload.Action.TOGGLE_REGEN));
		} else if (down && !maxSteelTransformWasDown && (mutationHasUtility(client, 8)
				|| (Screen.hasShiftDown() && mutationSelected(client)))) {
			// v0.13.22: nothing else owns N -- it is the selected mutation's Utility 2.
			// v0.14.5: a six-key mutation (no H / N, e.g. Super Strength) still sends Sneak+N so power combos fire.
			pressUtility(8);
		}
		if (!down && glRingRemoveHeld) {
			glRingRemoveHeld = false;
			ClientPlayNetworking.send(new com.projecthero.mod.network.GreenLanternActionPayload(
					com.projecthero.mod.network.GreenLanternActionPayload.Action.RING_REMOVE_STOP));
		}
		maxSteelTransformWasDown = down;
	}

	private static boolean wearingAnyIronMan(net.minecraft.world.entity.player.Player player) {
		for (net.minecraft.world.entity.EquipmentSlot slot : new net.minecraft.world.entity.EquipmentSlot[] {
				net.minecraft.world.entity.EquipmentSlot.HEAD, net.minecraft.world.entity.EquipmentSlot.CHEST,
				net.minecraft.world.entity.EquipmentSlot.LEGS, net.minecraft.world.entity.EquipmentSlot.FEET }) {
			if (player.getItemBySlot(slot).getItem() instanceof com.projecthero.mod.ironman.item.IronManArmorItem) {
				return true;
			}
		}
		return false;
	}

	/** P: the squad screen. Opens for everyone -- it explains how to start a squad if you have none. */
	private static void handleSquadMenu(Minecraft client) {
		boolean down = ModKeyBindings.SQUAD_MENU.isDown();
		if (down && !squadMenuWasDown && client.screen == null) {
			client.setScreen(new com.projecthero.mod.client.gui.SquadScreen());
		}
		squadMenuWasDown = down;
	}

	private static void handlePowerInfo(Minecraft client) {
		boolean down = ModKeyBindings.POWER_INFO.isDown();
		if (down && !powerInfoWasDown) {
			client.setScreen(new com.projecthero.mod.client.gui.PowerInfoScreen());
		}
		powerInfoWasDown = down;
	}

	/**
	 * Thor flight -- unchanged: double-tap the vanilla jump key while holding Mjolnir. See the long
	 * note this method carried before the universal-input refactor; nothing about it changed.
	 */
	private static void handleDoubleJump(Minecraft client, LocalPlayer player) {
		if (ticksSinceJumpPress != Integer.MAX_VALUE) {
			ticksSinceJumpPress++;
		}

		boolean jumpDown = client.options.keyJump.isDown();
		boolean pressed = jumpDown && !jumpWasDown;
		jumpWasDown = jumpDown;

		if (!pressed) {
			return;
		}

		boolean doubleTap = ticksSinceJumpPress <= DOUBLE_JUMP_WINDOW_TICKS;
		ticksSinceJumpPress = 0;

		if (!doubleTap) {
			return;
		}

		// Thor: double-tap jump while airborne + holding Mjolnir.
		if (!player.onGround() && ThorPowers.isHoldingMjolnir(player)) {
			ticksSinceJumpPress = Integer.MAX_VALUE;
			ClientPlayNetworking.send(new ThorActionPayload(ThorActionPayload.Action.TOGGLE_FLIGHT));
			return;
		}

		// v0.14.8: the Kryptonian -- double-tap jump in the air takes off / drops out of flight (server re-validates).
		if (!player.onGround() && com.projecthero.mod.kryptonian.Kryptonian.hasPower(player)) {
			ticksSinceJumpPress = Integer.MAX_VALUE;
			ClientPlayNetworking.send(new com.projecthero.mod.kryptonian.network.KryptonianActionPayload(
					com.projecthero.mod.kryptonian.network.KryptonianActionPayload.Action.TOGGLE_FLIGHT));
			return;
		}

		// "changes 22": bare Repulsor boots. Anyone can wear one -- no Tony Stark power, no suit -- so
		// this is checked before the power gate below. Airborne double-tap toggles the boots' flight,
		// exactly like the suit's; the boots land themselves on ground contact.
		if (!player.onGround() && player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.FEET)
				.is(com.projecthero.mod.ironman.item.IronManItems.REPULSOR)) {
			ticksSinceJumpPress = Integer.MAX_VALUE;
			ClientPlayNetworking.send(new com.projecthero.mod.network.IronManActionPayload(
					com.projecthero.mod.network.IronManActionPayload.Action.TOGGLE_FLIGHT));
			return;
		}

		// Iron Man: same gesture. Airborne + wearing a suit -> repulsor flight; grounded + not wearing
		// but has developed a suit -> summon it. Server re-validates power / suit / energy.
		com.projecthero.mod.ironman.data.TonyStarkState stark =
				player.getAttachedOrElse(com.projecthero.mod.attachment.ModAttachments.TONY_STARK_STATE, null);
		// Guards the Iron Man gesture rather than early-returning -- an early return here used to skip
		// every check below it (including Green Lantern's, further down), so any player without Tony
		// Stark power could never double-tap-jump into Ring Flight at all.
		if (stark != null && stark.hasPower) {
			boolean wearingIronMan = isWearingIronMan(player);
			if (!player.onGround() && wearingIronMan) {
				ticksSinceJumpPress = Integer.MAX_VALUE;
				ClientPlayNetworking.send(new com.projecthero.mod.network.IronManActionPayload(
						com.projecthero.mod.network.IronManActionPayload.Action.TOGGLE_FLIGHT));
			} else if (player.onGround() && player.isShiftKeyDown() && !wearingIronMan
					&& stark.builtSuits.stream().anyMatch(id -> id.indexOf('/') < 0)) {
				// "changes 22": the ground summon gesture now needs SNEAK + double-tap jump.
				//
				// The airborne half of this method is safe because ordinary movement never puts two jump
				// presses in the air inside the 7-tick window. The grounded half was not: two hops in a
				// third of a second is just... running. Any Tony Stark player who bunny-hopped, or who got
				// knocked back and mashed jump to get moving again, silently called their armour in -- which
				// is exactly the "an armour gets called to me when I get hit" report. Worse, a rapid string
				// of jumps re-fires it, because a double-tap that does nothing still leaves the timer at 0
				// and so makes the NEXT tap a double-tap too.
				//
				// Sneaking is never part of a jump you did not mean, so gating on it removes the accident
				// without removing the gesture. `C` remains the ordinary way to call the armour.
				ticksSinceJumpPress = Integer.MAX_VALUE;
				ClientPlayNetworking.send(new com.projecthero.mod.network.IronManActionPayload(
						com.projecthero.mod.network.IronManActionPayload.Action.SUMMON_SUIT));
				return;
			}
		}

		// Green Lantern (v0.11.5): Ring Flight moved off the X ability slot to this same double-tap-jump
		// gesture (X now fires Ring Grapple instead) -- airborne only, toggles on or off either way.
		// Server re-validates power/context/energy in GreenLanternAbilityManager#toggleFlight.
		com.projecthero.mod.greenlantern.data.GreenLanternState lantern =
				player.getAttachedOrElse(com.projecthero.mod.attachment.ModAttachments.GREEN_LANTERN_STATE, null);
		if (lantern != null && lantern.hasPower && !player.onGround()) {
			ticksSinceJumpPress = Integer.MAX_VALUE;
			ClientPlayNetworking.send(new com.projecthero.mod.network.GreenLanternActionPayload(
					com.projecthero.mod.network.GreenLanternActionPayload.Action.TOGGLE_FLIGHT));
		}
	}

	private static boolean isWearingIronMan(LocalPlayer player) {
		for (net.minecraft.world.entity.EquipmentSlot slot : new net.minecraft.world.entity.EquipmentSlot[] {
				net.minecraft.world.entity.EquipmentSlot.HEAD, net.minecraft.world.entity.EquipmentSlot.CHEST,
				net.minecraft.world.entity.EquipmentSlot.LEGS, net.minecraft.world.entity.EquipmentSlot.FEET }) {
			if (player.getItemBySlot(slot).getItem() instanceof com.projecthero.mod.ironman.item.IronManArmorItem) {
				return true;
			}
		}
		return false;
	}
}
