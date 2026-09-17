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

	/** Ability-1 (R) tap vs hold while a firearm is held: tap = reload, hold = open the Arsenal wheel. */
	private static int firearmAbilityOneHeld = -1;
	private static boolean firearmAbilityOneSentPress = false;
	private static final int FIREARM_RELOAD_TAP_TICKS = 8;

	/** Super Strength charged punch: attack key held this many ticks (not while aimed at a block). */
	public static final int CHARGED_PUNCH_HOLD_TICKS = 40;
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
		HudRenderCallback.EVENT.register(com.projecthero.mod.client.gui.SymbioteHud::render);
		HudRenderCallback.EVENT.register(com.projecthero.mod.client.gui.GreenLanternHud::render);

		net.minecraft.client.gui.screens.MenuScreens.register(
				com.projecthero.mod.ironman.IronManBlocks.STARK_FABRICATOR_MENU,
				com.projecthero.mod.client.gui.StarkFabricatorScreen::new);
		net.minecraft.client.gui.screens.MenuScreens.register(
				com.projecthero.mod.ironman.IronManBlocks.SUIT_PLATFORM_MENU,
				com.projecthero.mod.client.gui.IronManSuitPlatformScreen::new);
		net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry.register(
				com.projecthero.mod.ironman.IronManBlocks.SUIT_PLATFORM_BE,
				com.projecthero.mod.client.render.IronManSuitPlatformRenderer::new);
		com.projecthero.mod.client.render.IronManEntityRenderers.initialize();
		com.projecthero.mod.client.render.RaidEntityRenderers.initialize();
		com.projecthero.mod.client.render.TitanEntityRenderers.initialize();
		com.projecthero.mod.client.render.PunisherEntityRenderers.initialize();
		com.projecthero.mod.client.render.SuperheroFirstPersonArm.initialize();
		IronManBeamClient.register();
		com.projecthero.mod.client.spider.SpiderWebLineRenderer.initialize();
		com.projecthero.mod.client.firearm.BulletHoleRenderer.initialize();
		com.projecthero.mod.client.greenlantern.GreenLanternShieldRenderer.initialize();

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

		net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry.registerModelLayer(
				com.projecthero.mod.client.maxsteel.MaxSteelWingsModel.LAYER,
				com.projecthero.mod.client.maxsteel.MaxSteelWingsModel::createLayer);

		// Arc Reactor on the player's chest (Tony Stark power, no Iron Man chestplate) + Max Steel's
		// blue Turbo Flight wings.
		net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback.EVENT.register(
				(entityType, entityRenderer, registrationHelper, context) -> {
					if (entityRenderer instanceof net.minecraft.client.renderer.entity.player.PlayerRenderer playerRenderer) {
						registrationHelper.register(new com.projecthero.mod.client.render.ArcReactorLayer(
								playerRenderer, context.getItemRenderer()));
						registrationHelper.register(new com.projecthero.mod.client.maxsteel.MaxSteelWingsLayer(
								playerRenderer, context.getModelSet()));
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
		ClientTickEvents.END_CLIENT_TICK.register(MaxSteelFlightFxClient::clientTick);
		ClientTickEvents.END_CLIENT_TICK.register(com.projecthero.mod.client.spider.SpiderInputClient::clientTick);
		ClientTickEvents.END_CLIENT_TICK.register(com.projecthero.mod.client.symbiote.SymbioteFxClient::clientTick);
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
			squadMenuWasDown = false;
			glConstructHeldTicks = 0;
			glWheelOpenedThisHold = false;
			// Leaving a world must not carry the last server's squad roster into the next one.
			com.projecthero.mod.client.squad.SquadClient.clear();
			return;
		}

		handleGreenLanternConstructWheelHold(client);

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
			jumpWasDown = false;
			return;
		}

		handleDoubleJump(client, client.player);
		handlePowerSelect(client);
		handlePowerInfo(client);
		handleSquadMenu(client);
		handleMaxSteelTransform(client);
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

	private static void handlePowerSelect(Minecraft client) {
		boolean down = ModKeyBindings.POWER_SELECT.isDown();
		if (down && !powerSelectWasDown) {
			// "changes 19": H while wearing any Iron Man armour opens / closes the helmet faceplate.
			// v0.6.16: H while transformed as Max Steel does the same for its helmet. Otherwise H opens
			// the experimental power wheel as before.
			if (client.player != null && wearingAnyIronMan(client.player)) {
				ClientPlayNetworking.send(new com.projecthero.mod.network.IronManActionPayload(
						com.projecthero.mod.network.IronManActionPayload.Action.TOGGLE_FACEPLATE));
			} else if (client.player != null && com.projecthero.mod.maxsteel.MaxSteel.isTransformed(client.player)) {
				// v0.6.17: Shift+H powers down; plain H toggles the helmet.
				ClientPlayNetworking.send(new com.projecthero.mod.network.MaxSteelActionPayload(
						Screen.hasShiftDown()
								? com.projecthero.mod.network.MaxSteelActionPayload.Action.POWER_DOWN
								: com.projecthero.mod.network.MaxSteelActionPayload.Action.TOGGLE_HELMET));
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
			} else {
				client.setScreen(new PowerWheelScreen());
			}
		}
		powerSelectWasDown = down;
	}

	/**
	 * Super Strength client gestures that vanilla never reports on its own:
	 * <ul>
	 *   <li><b>Charged Punch</b> -- hold the attack key ~2 s (charge does not build while the
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

	private static void handleMaxSteelTransform(Minecraft client) {
		boolean down = ModKeyBindings.MAX_STEEL_TRANSFORM.isDown();
		if (down && !maxSteelTransformWasDown && client.player != null
				&& com.projecthero.mod.maxsteel.MaxSteel.hasPower(client.player)) {
			ClientPlayNetworking.send(new com.projecthero.mod.network.MaxSteelActionPayload(
					com.projecthero.mod.network.MaxSteelActionPayload.Action.TRANSFORM_TOGGLE));
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
		if (stark == null || !stark.hasPower) {
			return;
		}
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
