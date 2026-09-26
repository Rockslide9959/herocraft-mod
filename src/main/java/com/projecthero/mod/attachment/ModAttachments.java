package com.projecthero.mod.attachment;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.grave.GraveboundState;
import com.projecthero.mod.hero.data.ExperimentalState;
import com.projecthero.mod.ironman.data.TonyStarkState;

import java.util.UUID;

import com.mojang.serialization.Codec;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;

/**
 * Persistent/synced per-player state used by the Thor powers. Kept as plain attachments (rather
 * than a bespoke SavedData object) since none of this needs to be queried outside of the owning
 * player.
 */
public final class ModAttachments {
	/**
	 * Comma-separated keys of the non-experimental Primary powers a player holds, oldest first. Drives the
	 * "hold two Primary powers, the oldest is replaced" rule in {@link com.projecthero.mod.hero.HeroTiers}.
	 */
	public static final AttachmentType<String> PRIMARY_ORDER = AttachmentRegistry.create(
			ProjectHeroMod.id("primary_order"),
			builder -> builder.persistent(Codec.STRING).copyOnDeath().initializer(() -> ""));

	/**
	 * Hidden worthiness score. See {@link com.projecthero.mod.worthiness.Worthiness}. {@code copyOnDeath()}
	 * is required here -- a player death/respawn creates a new player entity instance, and without it
	 * this (like any Fabric attachment) silently resets to its initializer instead of carrying over.
	 * Worthiness must survive death: the whole cross-dimension summon design hinges on a player who
	 * dies holding Mjolnir still being worthy enough to call it back after respawning.
	 */
	public static final AttachmentType<Integer> WORTHINESS = AttachmentRegistry.create(
			ProjectHeroMod.id("worthiness"),
			builder -> builder.persistent(Codec.INT).copyOnDeath().initializer(() -> 0)
					// Synced to the owning client so it can tell whether the player is Thor (e.g. the
					// "Your Power" info screen). targetOnly: no other player needs a worthiness score.
					.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.targetOnly()));

	/** Storm Energy resource bar value, synced only to the owning player for HUD rendering. */
	public static final AttachmentType<Float> STORM_ENERGY = AttachmentRegistry.create(
			ProjectHeroMod.id("storm_energy"),
			builder -> builder.persistent(Codec.FLOAT)
					.initializer(() -> 100.0f)
					.syncWith(ByteBufCodecs.FLOAT, AttachmentSyncPredicate.targetOnly()));

	/**
	 * Whether the player currently has Thor flight toggled on. Not persisted across restarts, but
	 * synced to everyone (not just the owning player) so third-person viewers can render the flight
	 * lean/arm pose and so the owning client can drive its own looping flight ambience sound.
	 */
	public static final AttachmentType<Boolean> FLYING = AttachmentRegistry.create(
			ProjectHeroMod.id("thor_flying"),
			builder -> builder.initializer(() -> false)
					.syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.all()));

	/** Per-ability cooldowns (Lightning Strike, Thunderclap, Storm Call, Chain Lightning, ...). Not persisted. */
	public static final AttachmentType<CooldownState> COOLDOWNS = AttachmentRegistry.create(
			ProjectHeroMod.id("thor_cooldowns"),
			builder -> builder.initializer(CooldownState::new)
					.syncWith(CooldownState.STREAM_CODEC, AttachmentSyncPredicate.targetOnly()));

	/** Whether Lightning Laser is currently being fired (held-key, continuous-drain ability). */
	public static final AttachmentType<Boolean> LASER_ACTIVE = AttachmentRegistry.create(
			ProjectHeroMod.id("thor_laser_active"),
			builder -> builder.initializer(() -> false));

	/**
	 * Ticks the player has been charging God of Thunder's Wrath (hold Z). Zero = not charging;
	 * {@code >= 100} fires the ultimate. Not persisted; synced to the owning client so the HUD can
	 * draw the 5-second buildup bar without a round trip.
	 */
	public static final AttachmentType<Integer> THOR_WRATH_CHARGE = AttachmentRegistry.create(
			ProjectHeroMod.id("thor_wrath_charge"),
			builder -> builder.initializer(() -> 0)
					.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.targetOnly()));

	/** Active Storm Call state, if any. Not persisted. */
	public static final AttachmentType<StormCallState> STORM_CALL_STATE = AttachmentRegistry.create(
			ProjectHeroMod.id("thor_storm_call_state"),
			builder -> builder.initializer(StormCallState::new));

	/**
	 * Ticks remaining of "hammerless flight" grace -- see {@link com.projecthero.mod.power.ThorPowers}'s
	 * throw/flight handling. Zero means no grace is active. Counts down in whole ticks rather than
	 * storing an expiry game-time so it never depends on anything client-side, per the explicit
	 * "ticks, not client FPS" requirement.
	 *
	 * <p>Deliberately not persisted and not {@code copyOnDeath()}: a fresh player instance (death,
	 * respawn, or relogin) starts at the initializer value of 0, which is exactly the "clear it"
	 * behavior a temporary flight grace needs on both of those -- see {@link ThorPowers}'
	 * "lifecycle safety nets" section. Synced to the owning client only, so the HUD can show a
	 * countdown without a round trip.
	 */
	public static final AttachmentType<Integer> HAMMERLESS_FLIGHT_TICKS = AttachmentRegistry.create(
			ProjectHeroMod.id("thor_hammerless_flight_ticks"),
			builder -> builder.initializer(() -> 0)
					.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.targetOnly()));

	/**
	 * The {@link com.projecthero.mod.item.ModDataComponents#HAMMER_ID} of the Mjolnir this player has
	 * bound, or absent. This -- not what is in their hands -- is the single source of truth for
	 * whether they have the Power of Thor (see {@link com.projecthero.mod.power.ThorPassives}) and for
	 * which hammer their call key summons.
	 *
	 * <p>Persistent <em>and</em> {@code copyOnDeath()}: dying must not cost you your hammer, since
	 * the headline scenario for the whole persistence system is "lose it, die, respawn elsewhere,
	 * call it back". Synced to the owning client so the tooltip and HUD can tell whether this is
	 * <em>your</em> hammer without a round trip.
	 */
	public static final AttachmentType<UUID> BOUND_HAMMER_ID = AttachmentRegistry.create(
			ProjectHeroMod.id("thor_bound_hammer"),
			builder -> builder.persistent(UUIDUtil.CODEC)
					.copyOnDeath()
					.syncWith(UUIDUtil.STREAM_CODEC, AttachmentSyncPredicate.targetOnly()));

	/**
	 * v0.12.16: true once a Thor has deliberately released their hold on the hammer (unbound it) and has
	 * not bound one since. If they then gain another power they lose their worthiness
	 * ({@code HeroTiers.unworthyIfHammerReleased}) so they cannot lift Mjolnir back up and stack it on top.
	 */
	public static final AttachmentType<Boolean> HAMMER_RELEASED = AttachmentRegistry.create(
			ProjectHeroMod.id("thor_hammer_released"),
			builder -> builder.persistent(com.mojang.serialization.Codec.BOOL)
					.copyOnDeath()
					.initializer(() -> false));

	/**
	 * Game time of the last damage that got through, used only to gate the very mild Power of Thor
	 * regeneration. Not persisted -- a fresh login is as good as being out of combat.
	 */
	public static final AttachmentType<Long> LAST_HURT_TICK = AttachmentRegistry.create(
			ProjectHeroMod.id("thor_last_hurt_tick"),
			builder -> builder.initializer(() -> 0L));

	/**
	 * Whether the player currently has <em>experimental</em> flight engaged (Flight power, Wind Flight,
	 * Psychic Flight, Magnetic Flight, ...). Completely independent of Thor's {@link #FLYING}: separate
	 * key, separate lifecycle, and hero flight refuses to engage while Thor flight is active. Not
	 * persisted (a relog drops you safely); synced to everyone so third-person pose/HUD can react.
	 */
	public static final AttachmentType<Boolean> HERO_FLYING = AttachmentRegistry.create(
			ProjectHeroMod.id("hero_flying"),
			builder -> builder.initializer(() -> false)
					.syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.all()));

	/**
	 * All experimental-mutation state (owned powers, active power, ability cooldowns, toggle/cycle
	 * states, power resources, research progression) in one isolated namespaced key. Persistent and
	 * {@code copyOnDeath()} -- permanently-unlocked powers and long cooldowns must survive death and
	 * relog (spec section 20). Synced to the owning client only, for the six-slot HUD and the
	 * power-selection wheel. Completely separate from every Thor attachment above.
	 */
	public static final AttachmentType<ExperimentalState> EXPERIMENTAL_STATE = AttachmentRegistry.create(
			ProjectHeroMod.id("experimental_state"),
			builder -> builder.persistent(ExperimentalState.CODEC)
					.copyOnDeath()
					.initializer(ExperimentalState::new)
					.syncWith(ByteBufCodecs.fromCodec(ExperimentalState.CODEC), AttachmentSyncPredicate.targetOnly()));

	/**
	 * Whether the player currently has Iron Man suit flight engaged (repulsor flight). Independent of
	 * Thor's {@link #FLYING} and experimental {@link #HERO_FLYING}: separate key, separate lifecycle.
	 * Not persisted (a relog drops you safely); synced to everyone so third-person pose/HUD can react.
	 */
	public static final AttachmentType<Boolean> IRON_MAN_FLYING = AttachmentRegistry.create(
			ProjectHeroMod.id("iron_man_flying"),
			builder -> builder.initializer(() -> false)
					.syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.all()));

	/**
	 * "changes 22": whether the player is flying on a bare {@link com.projecthero.mod.ironman.item.RepulsorItem}
	 * worn in the boots slot. Deliberately a <em>separate</em> flag from {@link #IRON_MAN_FLYING}:
	 * repulsor boots are not a suit, have none of a suit's energy / integrity / altitude rules, and
	 * must not make {@code IronManFlight}'s per-tick suit checks fire. Not persisted; synced to
	 * everyone so the thruster pose and particles read the same as suit flight.
	 */
	public static final AttachmentType<Boolean> REPULSOR_BOOTS_FLYING = AttachmentRegistry.create(
			ProjectHeroMod.id("repulsor_boots_flying"),
			builder -> builder.initializer(() -> false)
					.syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.all()));

	/**
	 * "changes 19": whether the worn Iron Man helmet's faceplate is currently open (revealing the
	 * pilot's face). Toggled with the H key while wearing any Iron Man armour. Not persisted (a relog
	 * closes it); synced to everyone so every viewer sees the open visor.
	 */
	public static final AttachmentType<Boolean> IRON_MAN_FACEPLATE_OPEN = AttachmentRegistry.create(
			ProjectHeroMod.id("iron_man_faceplate_open"),
			builder -> builder.initializer(() -> false)
					.syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.all()));

	/**
	 * "changes 19": whether the Mark 5's gauntlet blades are currently extended (its slot-3 toggle).
	 * While set the wearer gets +4 melee and cannot place blocks. Not persisted (a relog retracts
	 * them); synced to everyone for the blade FX.
	 */
	public static final AttachmentType<Boolean> IRON_MAN_BLADES = AttachmentRegistry.create(
			ProjectHeroMod.id("iron_man_blades"),
			builder -> builder.initializer(() -> false)
					.syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.all()));

	/**
	 * The entire Iron Man / Tony Stark progression (permanent power flag, technology level, per-suit
	 * energy/integrity, active suit, Iron Man ability cooldowns) in one isolated namespaced key.
	 * Persistent and {@code copyOnDeath()} -- the Tony Stark Hero-Tier power and everything gated
	 * behind it must survive death and relog exactly like the Power of Thor and permanently-unlocked
	 * mutations. Synced to <em>everyone</em> (not just the owner) so other players' clients can render
	 * the chest Arc Reactor and suit state on a Tony Stark player they can see ("changes 10": the
	 * reactor was invisible to other players in multiplayer because this used to be target-only). The
	 * server stays authoritative -- every gameplay check runs against the server-side attachment, so a
	 * modified client seeing this data cannot fake the power. Completely separate from every Thor
	 * attachment and from {@link #EXPERIMENTAL_STATE}.
	 */
	public static final AttachmentType<TonyStarkState> TONY_STARK_STATE = AttachmentRegistry.create(
			ProjectHeroMod.id("tony_stark_state"),
			builder -> builder.persistent(TonyStarkState.CODEC)
					.copyOnDeath()
					.initializer(TonyStarkState::new)
					.syncWith(ByteBufCodecs.fromCodec(TonyStarkState.CODEC), AttachmentSyncPredicate.all()));

	/**
	 * The whole Zombie Raid progression for one player: the Gravebound Curse timer and its source,
	 * completed-raid count, whether the first-clear Heart of the Grave has been handed over, and
	 * Experimental Power research from Powered Zombie Boss kills. See {@link GraveboundState} for why
	 * the curse is attachment data rather than a status effect (milk, death and relog must not clear
	 * it). Persistent and {@code copyOnDeath()} -- "death must not remove the curse" is an explicit
	 * requirement. Synced to the owning client only, for the curse HUD. Completely separate from every
	 * Thor attachment, from {@link #EXPERIMENTAL_STATE} and from {@link #TONY_STARK_STATE}.
	 */
	public static final AttachmentType<GraveboundState> GRAVEBOUND_STATE = AttachmentRegistry.create(
			ProjectHeroMod.id("gravebound_state"),
			builder -> builder.persistent(GraveboundState.CODEC)
					.copyOnDeath()
					.initializer(GraveboundState::new)
					.syncWith(ByteBufCodecs.fromCodec(GraveboundState.CODEC), AttachmentSyncPredicate.targetOnly()));

	/**
	 * The whole Spider-Man Hero-Class power: the permanent power flag, the organic Web Reserve, the
	 * double-jump and web-ability cooldowns, and the live traversal state (which surface is being
	 * crawled, where a web line is anchored). Persistent and {@code copyOnDeath()} for the same reason
	 * the Tony Stark and experimental stores are -- a permanently-earned Hero Class must survive death
	 * and relog. Completely separate from every other attachment here.
	 *
	 * <p>Synced to <b>everyone</b>, not target-only: other players' clients need the climb state to
	 * draw a ceiling-crawling Spider-Man the right way up, and the anchor to draw his web line. The
	 * server stays authoritative -- every gameplay decision (reserve, cooldowns, dodges, anchors) is
	 * made against the server-side copy, so a client that can see this data still cannot fake any of
	 * it. The mutators in {@code SpiderMan} only re-save on a change a viewer could notice, so this
	 * does not put a packet on the wire every tick.
	 */
	public static final AttachmentType<com.projecthero.mod.spider.data.SpiderManState> SPIDER_MAN_STATE =
			AttachmentRegistry.create(ProjectHeroMod.id("spider_man_state"),
					builder -> builder.persistent(com.projecthero.mod.spider.data.SpiderManState.CODEC)
							.copyOnDeath()
							.initializer(com.projecthero.mod.spider.data.SpiderManState::new)
							.syncWith(ByteBufCodecs.fromCodec(com.projecthero.mod.spider.data.SpiderManState.CODEC),
									AttachmentSyncPredicate.all()));

	/**
	 * Per-tick scratch state for the surface-adhesion engine (which face is held, how long a hold has
	 * survived with nothing in reach, whether a transition just happened). Neither persisted nor
	 * synced: it is rewritten every tick and is worth neither disk nor bandwidth. Kept as an
	 * attachment rather than a {@code static} map precisely so it belongs to the player entity and
	 * cannot outlive a world -- see {@code ServerStateReset} for why that distinction matters here.
	 * Each side keeps its own copy: the owning client's drives movement, the server's drives the
	 * synced climb state above.
	 */
	public static final AttachmentType<com.projecthero.mod.spider.data.SpiderClimbLocal> SPIDER_CLIMB_LOCAL =
			AttachmentRegistry.create(ProjectHeroMod.id("spider_climb_local"),
					builder -> builder.initializer(com.projecthero.mod.spider.data.SpiderClimbLocal::new));

	/**
	 * Ticks the player has been charging Web Blossom (sneak + hold V). Zero = not charging;
	 * {@code >= 60} fires it. Not persisted; synced to the owning client so the HUD can draw the
	 * 3-second buildup bar.
	 */
	public static final AttachmentType<Integer> SPIDER_BLOSSOM_CHARGE = AttachmentRegistry.create(
			ProjectHeroMod.id("spider_blossom_charge"),
			builder -> builder.initializer(() -> 0)
					.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.targetOnly()));

	/**
	 * The entire Max Steel Hero-Tier power for one player: the permanent power flag, T.U.R.B.O. Energy,
	 * the transformed state + which Turbo Mode is active + the suit-up animation clock, and the ability
	 * cooldowns. Persistent and {@code copyOnDeath()} for the same reason the Tony Stark / Spider-Man
	 * stores are -- a permanently-bonded Hero Tier power must survive death and relog. The active suit
	 * state itself is torn down on death by {@code MaxSteel.onPlayerRespawn}.
	 *
	 * <p>Synced to <b>everyone</b>, not target-only: other players' clients need the transformed state,
	 * the mode and the suit-up start tick to render a transformed Max Steel and its pixel-by-pixel
	 * suit-up. The server stays authoritative -- every gameplay check runs against the server-side copy.
	 * Completely separate from every other attachment here.
	 */
	public static final AttachmentType<com.projecthero.mod.maxsteel.data.MaxSteelState> MAX_STEEL_STATE =
			AttachmentRegistry.create(ProjectHeroMod.id("max_steel_state"),
					builder -> builder.persistent(com.projecthero.mod.maxsteel.data.MaxSteelState.CODEC)
							.copyOnDeath()
							.initializer(com.projecthero.mod.maxsteel.data.MaxSteelState::new)
							.syncWith(ByteBufCodecs.fromCodec(com.projecthero.mod.maxsteel.data.MaxSteelState.CODEC),
									AttachmentSyncPredicate.all()));

	/**
	 * Whether the player currently has Turbo Flight engaged. Independent of every other flight flag
	 * (Thor {@link #FLYING}, {@link #HERO_FLYING}, {@link #IRON_MAN_FLYING}, {@link #REPULSOR_BOOTS_FLYING}):
	 * separate key, separate lifecycle. Not persisted (a relog drops you safely); synced to everyone so
	 * the thruster pose/particles read on other clients.
	 */
	public static final AttachmentType<Boolean> MAX_STEEL_FLYING = AttachmentRegistry.create(
			ProjectHeroMod.id("max_steel_flying"),
			builder -> builder.initializer(() -> false)
					.syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.all()));

	/**
	 * v0.9.3: how many ticks the player has been charging the Turbo Cannon (0 = not charging). Drives the
	 * bottom-right HUD charge bar so the pilot can see how much of the 5-second charge window is left.
	 * Target-only (only the charging player's own HUD reads it) and not persisted (a relog cancels the
	 * charge). Written each tick by {@code MaxSteelCannon}.
	 */
	public static final AttachmentType<Integer> MAX_STEEL_CANNON_CHARGE = AttachmentRegistry.create(
			ProjectHeroMod.id("max_steel_cannon_charge"),
			builder -> builder.initializer(() -> 0)
					.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.targetOnly()));

	/**
	 * Whether the Max Steel helmet is retracted (H key), revealing the pilot's face -- the direct
	 * parallel to {@link #IRON_MAN_FACEPLATE_OPEN}. Synced to everyone, not persisted (a relog / a
	 * suit-down closes it). Read client-side by {@code SuperheroArmorRenderer.setHelmetHidden} and
	 * {@code PlayerModelMixin}.
	 */
	public static final AttachmentType<Boolean> MAX_STEEL_FACEPLATE_OPEN = AttachmentRegistry.create(
			ProjectHeroMod.id("max_steel_faceplate_open"),
			builder -> builder.initializer(() -> false)
					.syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.all()));

	/**
	 * v0.6.20: whether the Spider-Man costume's mask is pulled off (revealing the wearer's face).
	 * Toggled with the H key while wearing the Spider-Man Suit head piece -- the direct parallel to
	 * {@link #IRON_MAN_FACEPLATE_OPEN} / {@link #MAX_STEEL_FACEPLATE_OPEN}, and like them synced to
	 * everyone and not persisted (a relog puts the mask back on). Read client-side by
	 * {@code SuperheroArmorRenderer} and {@code PlayerModelMixin}.
	 */
	public static final AttachmentType<Boolean> SPIDER_MAN_MASK_OPEN = AttachmentRegistry.create(
			ProjectHeroMod.id("spider_man_mask_open"),
			builder -> builder.initializer(() -> false)
					.syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.all()));

	/**
	 * The whole Punisher Hero-Tier power for one player: the permanent power flag, the unlocked
	 * Arsenal weapons, ability cooldowns, the Adrenaline / Suppressive Fire / Tactical Roll timers,
	 * and Vigilante Training progress. Persistent + {@code copyOnDeath()} like the Tony Stark /
	 * Spider-Man / Max Steel stores. Synced to everyone (the ∞-reserve HUD + ability HUD read it on
	 * other clients); server stays authoritative. Isolated from every other attachment here.
	 */
	public static final AttachmentType<com.projecthero.mod.punisher.data.PunisherState> PUNISHER_STATE =
			AttachmentRegistry.create(ProjectHeroMod.id("punisher_state"),
					builder -> builder.persistent(com.projecthero.mod.punisher.data.PunisherState.CODEC)
							.copyOnDeath()
							.initializer(com.projecthero.mod.punisher.data.PunisherState::new)
							.syncWith(ByteBufCodecs.fromCodec(com.projecthero.mod.punisher.data.PunisherState.CODEC),
									AttachmentSyncPredicate.all()));

	/**
	 * The whole Wolverine Hero-Tier power for one player (v0.12.1): the permanent power flag, claw
	 * deployment, Berserker Rage / Claw Dash / emergency-heal timers, the last-action stamp the client
	 * animates from, and ability cooldowns. Persistent + {@code copyOnDeath()} and synced to everyone
	 * (other clients render the claws and the rage look from it); the server stays authoritative.
	 */
	public static final AttachmentType<com.projecthero.mod.wolverine.data.WolverineState> WOLVERINE_STATE =
			AttachmentRegistry.create(ProjectHeroMod.id("wolverine_state"),
					builder -> builder.persistent(com.projecthero.mod.wolverine.data.WolverineState.CODEC)
							.copyOnDeath()
							.initializer(com.projecthero.mod.wolverine.data.WolverineState::new)
							.syncWith(ByteBufCodecs.fromCodec(com.projecthero.mod.wolverine.data.WolverineState.CODEC),
									AttachmentSyncPredicate.all()));

	/**
	 * The whole Titan Shifter Hero-Tier power for one player (v0.12.31): the unlock, the phase state machine,
	 * transformation cooldown, ability cooldowns and the HUD's Titan-health mirror. Persistent +
	 * {@code copyOnDeath()}; synced to the owner only (the Titan itself is a real entity every client sees).
	 */
	public static final AttachmentType<com.projecthero.mod.titanshifter.data.TitanShifterState> TITAN_SHIFTER_STATE =
			AttachmentRegistry.create(ProjectHeroMod.id("titan_shifter_state"),
					builder -> builder.persistent(com.projecthero.mod.titanshifter.data.TitanShifterState.CODEC)
							.copyOnDeath()
							.initializer(com.projecthero.mod.titanshifter.data.TitanShifterState::new)
							.syncWith(ByteBufCodecs.fromCodec(com.projecthero.mod.titanshifter.data.TitanShifterState.CODEC),
									AttachmentSyncPredicate.targetOnly()));

	/**
	 * The whole All Might / One For All power for one player (v0.12.33): the power, the chosen form, OFA Power, Full Cowl
	 * and transformation clocks, ability cooldowns and the current pose animation. Persistent + {@code copyOnDeath()}; synced
	 * to every client (other players render the form and the poses from it); the server stays authoritative.
	 */
	/** v0.12.36: the two costume pieces waiting in the All Might costume locker (N in the Power Form). Persistent, kept through death, not synced. */
	/** v0.12.36: this player has already been shown the "craft the Guidebook" welcome message in this world. */
	public static final AttachmentType<Boolean> GUIDEBOOK_HINT_SEEN = AttachmentRegistry.create(
			ProjectHeroMod.id("guidebook_hint_seen"),
			builder -> builder.persistent(com.mojang.serialization.Codec.BOOL).copyOnDeath().initializer(() -> false));

	/** Transient countdown (ticks) for repeating that message on the action bar. */
	public static final AttachmentType<Integer> GUIDEBOOK_HINT_LEFT = AttachmentRegistry.create(
			ProjectHeroMod.id("guidebook_hint_left"));

	public static final AttachmentType<net.minecraft.world.item.component.ItemContainerContents> ALL_MIGHT_LOCKER =
			AttachmentRegistry.create(ProjectHeroMod.id("all_might_locker"),
					builder -> builder.persistent(net.minecraft.world.item.component.ItemContainerContents.CODEC)
							.copyOnDeath()
							.initializer(() -> net.minecraft.world.item.component.ItemContainerContents.EMPTY));

	public static final AttachmentType<com.projecthero.mod.allmight.data.AllMightState> ALL_MIGHT_STATE =
			AttachmentRegistry.create(ProjectHeroMod.id("all_might_state"),
					builder -> builder.persistent(com.projecthero.mod.allmight.data.AllMightState.CODEC)
							.copyOnDeath()
							.initializer(com.projecthero.mod.allmight.data.AllMightState::new)
							.syncWith(ByteBufCodecs.fromCodec(com.projecthero.mod.allmight.data.AllMightState.CODEC),
									AttachmentSyncPredicate.all()));

	/**
	 * The Spider-Man Symbiote upgrade: {@code hasSymbiote} (has the player bonded with a Symbiote yet),
	 * {@code active} (is the black suit on, or in the middle of coming on / going off), a tiny anti-spam
	 * toggle cooldown, the suit-up/suit-down animation clock ({@code transformDir}/
	 * {@code transformStartTick}/{@code transformDurationTicks} -- exactly the {@code MaxSteelState}
	 * pattern, read by {@code SymbioteTransform} on both sides), and {@code stowedArmor} (whatever real
	 * armour the suit displaced, so it can be handed back on retraction). Only ever meaningful for a
	 * player who <em>also</em> has the Spider-Man Hero Class -- it is not a standalone power.
	 *
	 * <p>Persistent + {@code copyOnDeath()} so the unlock survives death and relog, exactly like a Hero
	 * Class does. {@code active} and the animation clock are deliberately torn down on death / relog /
	 * dimension change / power loss by {@link com.projecthero.mod.symbiote.Symbiote} (there must be
	 * no stuck suit or lingering buff), so their persisted values are only ever a transient convenience.
	 * Synced to <b>everyone</b> so other players' clients can tell a symbiote Spider-Man apart, see the
	 * same suit-up reveal, and so the owning client knows whether H toggles the symbiote or the costume
	 * mask. Server stays authoritative for every check.
	 */
	public static final AttachmentType<com.projecthero.mod.symbiote.SymbioteState> SYMBIOTE_STATE =
			AttachmentRegistry.create(ProjectHeroMod.id("symbiote_state"),
					builder -> builder.persistent(com.projecthero.mod.symbiote.SymbioteState.CODEC)
							.copyOnDeath()
							.initializer(com.projecthero.mod.symbiote.SymbioteState::new)
							.syncWith(ByteBufCodecs.fromCodec(com.projecthero.mod.symbiote.SymbioteState.CODEC),
									AttachmentSyncPredicate.all()));

	/**
	 * The Symbiote's own "life" (v0.9.23) -- {@code hp} (the health bar half of every hit is routed
	 * onto), {@code broken} (bar spent, abilities locked), and the Symbiote Blade / Symbiote Spikes
	 * toggles. A second attachment purely because {@link com.projecthero.mod.symbiote.SymbioteState} is
	 * already at the codec's 16-field ceiling. Persistent (the bar must not silently refill on relog),
	 * synced to everyone (HUD, the black-arm render, another player's view). Reset to a healthy default
	 * on death / relog / dimension change by {@link com.projecthero.mod.symbiote.SymbioteVitalsManager}.
	 */
	public static final AttachmentType<com.projecthero.mod.symbiote.SymbioteVitals> SYMBIOTE_VITALS =
			AttachmentRegistry.create(ProjectHeroMod.id("symbiote_vitals"),
					builder -> builder.persistent(com.projecthero.mod.symbiote.SymbioteVitals.CODEC)
							.copyOnDeath()
							.initializer(com.projecthero.mod.symbiote.SymbioteVitals::new)
							.syncWith(ByteBufCodecs.fromCodec(com.projecthero.mod.symbiote.SymbioteVitals.CODEC),
									AttachmentSyncPredicate.all()));

	/**
	 * Marks a mob as a rare <b>Symbiote Host</b> (v0.9.10). Persistent so the buff/aura survive a chunk
	 * reload; not synced (the aura is server-spawned particles). Set once at spawn by
	 * {@code SymbioteHostSpawns}; read every tick by {@code SymbioteHost} off a cheap null-check.
	 */
	public static final AttachmentType<Boolean> SYMBIOTE_HOST = AttachmentRegistry.create(
			ProjectHeroMod.id("symbiote_host"),
			builder -> builder.persistent(Codec.BOOL).initializer(() -> false));

	/**
	 * Whether the player is currently aiming a firearm down its sights / through its scope (holding
	 * right-click). Drives server-side spread reduction and client-side FOV zoom, scope overlay and
	 * reduced sensitivity. Not persisted (a relog lowers the weapon); synced to everyone so third-
	 * person viewers can see a shouldered stance.
	 */
	public static final AttachmentType<Boolean> FIREARM_AIMING = AttachmentRegistry.create(
			ProjectHeroMod.id("firearm_aiming"),
			builder -> builder.initializer(() -> false)
					.syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.all()));

	/**
	 * The entire Green Lantern Hero-Tier power for one player -- Ring Charge, suit state, selected
	 * construct, Mastery progress, cumulative counters and ability cooldowns. Persistent + copyOnDeath
	 * (the power and Mastery must survive death/relog), synced to everyone (other clients render the
	 * suit). Server stays authoritative. Isolated from every other attachment here.
	 */
	public static final AttachmentType<com.projecthero.mod.greenlantern.data.GreenLanternState> GREEN_LANTERN_STATE =
			AttachmentRegistry.create(ProjectHeroMod.id("green_lantern_state"),
					builder -> builder.persistent(com.projecthero.mod.greenlantern.data.GreenLanternState.CODEC)
							.copyOnDeath()
							.initializer(com.projecthero.mod.greenlantern.data.GreenLanternState::new)
							.syncWith(ByteBufCodecs.fromCodec(com.projecthero.mod.greenlantern.data.GreenLanternState.CODEC),
									AttachmentSyncPredicate.all()));

	/**
	 * Whether Ring Flight is currently engaged. Independent of every other flight flag, exactly like
	 * {@link #MAX_STEEL_FLYING}. Not persisted (a relog drops you safely); synced to everyone so
	 * thruster particles/pose read on other clients.
	 */
	public static final AttachmentType<Boolean> GREEN_LANTERN_FLYING = AttachmentRegistry.create(
			ProjectHeroMod.id("green_lantern_flying"),
			builder -> builder.initializer(() -> false)
					.syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.all()));

	/** Whether Ring Flight's boost is currently held. Purely a render/HUD flag. */
	public static final AttachmentType<Boolean> GREEN_LANTERN_BOOSTING = AttachmentRegistry.create(
			ProjectHeroMod.id("green_lantern_boosting"),
			builder -> builder.initializer(() -> false)
					.syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.all()));

	/**
	 * Current HP of the player's active Directional Shield/Protective Dome, or 0 when neither is up.
	 * Deliberately not part of {@link #GREEN_LANTERN_STATE} -- combat state that must never survive a
	 * relog. Synced to everyone so the barrier's translucency/HP can be inferred by anyone nearby.
	 */
	public static final AttachmentType<Float> GREEN_LANTERN_BARRIER_HP = AttachmentRegistry.create(
			ProjectHeroMod.id("green_lantern_barrier_hp"),
			builder -> builder.initializer(() -> 0f)
					.syncWith(ByteBufCodecs.FLOAT, AttachmentSyncPredicate.all()));

	/** True while the active barrier is the Protective Dome (Shift+Z) rather than the Directional Shield. */
	public static final AttachmentType<Boolean> GREEN_LANTERN_BARRIER_IS_DOME = AttachmentRegistry.create(
			ProjectHeroMod.id("green_lantern_barrier_is_dome"),
			builder -> builder.initializer(() -> false)
					.syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.all()));

	/**
	 * v0.11.8: the Directional Shield/Protective Dome's shared uptime meter, 1.0 = fully charged, 0.0 =
	 * exhausted -- drains over {@link com.projecthero.mod.greenlantern.GreenLanternConfig#BARRIER_METER_MAX_TICKS}
	 * of active use, refills at the same rate while neither is up. Not persisted (same "combat state must
	 * never survive a relog" reasoning as {@link #GREEN_LANTERN_BARRIER_HP}); synced so the HUD bar reads
	 * correctly for everyone.
	 */
	public static final AttachmentType<Float> GREEN_LANTERN_BARRIER_METER = AttachmentRegistry.create(
			ProjectHeroMod.id("green_lantern_barrier_meter"),
			builder -> builder.initializer(() -> 1f)
					.syncWith(ByteBufCodecs.FLOAT, AttachmentSyncPredicate.all()));

	/**
	 * Absolute game-time the X ability's "Green Lantern's Light!" Oath empowerment mode expires, or 0
	 * while inactive (v0.11.7). Not persisted (a relog drops it safely); synced so the HUD can show a
	 * remaining-time countdown on the X key.
	 */
	public static final AttachmentType<Long> GREEN_LANTERN_OATH_UNTIL = AttachmentRegistry.create(
			ProjectHeroMod.id("green_lantern_oath_until"),
			builder -> builder.initializer(() -> 0L)
					.syncWith(ByteBufCodecs.VAR_LONG, AttachmentSyncPredicate.all()));

	/** Absolute game-time the player started reciting the X ability's Oath, or 0 while not reciting. */
	public static final AttachmentType<Long> GREEN_LANTERN_OATH_RECITING_SINCE = AttachmentRegistry.create(
			ProjectHeroMod.id("green_lantern_oath_reciting_since"),
			builder -> builder.initializer(() -> 0L)
					.syncWith(ByteBufCodecs.VAR_LONG, AttachmentSyncPredicate.all()));

	/**
	 * v0.11.10: whether the automatic underwater air tank ({@code GreenLanternAirTank}) is currently
	 * active for this player. Not persisted (a relog resurfaces you safely); synced to everyone so the
	 * tank's own particle rig reads correctly on other clients too.
	 */
	public static final AttachmentType<Boolean> GREEN_LANTERN_AIR_TANK_ACTIVE = AttachmentRegistry.create(
			ProjectHeroMod.id("green_lantern_air_tank_active"),
			builder -> builder.initializer(() -> false)
					.syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.all()));

	private ModAttachments() {
	}

	public static void initialize() {
		// Classes are loaded (and their attachments registered) simply by referencing this class;
		// this method exists so ProjectHeroMod has an explicit, readable init call.
	}
}
