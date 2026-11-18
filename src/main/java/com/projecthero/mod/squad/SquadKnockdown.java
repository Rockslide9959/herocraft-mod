package com.projecthero.mod.squad;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.projecthero.mod.ProjectHeroMod;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.BossEvent;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.5 (user): squad knockdowns. A player in a squad who takes a killing blow is <b>knocked down</b> instead of dying --
 * as long as someone in their squad is still standing to pick them up -- and a squadmate brings them back by holding
 * right-click on them for 5 seconds. The full rule set (and the improvements on the user's idea) is in
 * {@code docs/SQUAD_KNOCKDOWN_REFERENCE.md}; in short:
 * <ul>
 *   <li>The knockdown is the <em>last</em> death check: every power's death save and a held Totem of Undying go first, and
 *       nothing that bypasses invulnerability ({@code /kill}, the void, a serum rejection) is ever turned into one.</li>
 *   <li>Knocked: 1 health, no damage taken or dealt, invisible to every mob's and boss's targeting (and to {@code BossThreat}),
 *       crawling (vanilla's 0.6-block swim/crawl pose, at vanilla's crawl speed), no jumping, sprinting, attacking, using,
 *       placing, breaking or opening anything, and no power keys (their packets are dropped, see
 *       {@code SquadKnockdownPayloadMixin}). Hunger and air are frozen.</li>
 *   <li>75 s on the clock, paused while being revived; a quick re-knock (within 60 s of being revived) gets 45 s, then 25 s.</li>
 *   <li>Nobody left standing for 5 s (everyone else knocked, dead, offline or in another dimension) = everyone knocked bleeds
 *       out. Holding jump for 3 s gives up. Logging out while knocked counts as bleeding out, except for the single-player /
 *       LAN host and a server shutting down: those resume the knockdown on the next join.</li>
 *   <li>Revived: stands up with 6 hearts, Resistance II and Regeneration I for 5 s, and 2 s in which nothing can hurt them.</li>
 * </ul>
 *
 * <p>State: the server's live table is {@link #KNOCKED_PLAYERS} (concurrent, because the payload filter asks it from the
 * network thread); every client learns who is knocked from the synced {@link #KNOCKED} attachment, which is what the crawl
 * pose, the input lock and the squad-only glow read on that side. All static tables are dropped by
 * {@link #clearSessionState} via {@code ServerStateReset}.
 */
public final class SquadKnockdown {
	// ---------------------------------------------------------------- tuning

	/** The knockdown's countdown: 75 s (user). */
	public static final int KNOCK_TICKS = 75 * 20;
	/** A re-knock within {@link #CHAIN_WINDOW_TICKS} of being revived shortens the countdown: 75 s, then 45 s, then 25 s. */
	public static final int[] CHAIN_TICKS = {KNOCK_TICKS, 45 * 20, 25 * 20};
	public static final int CHAIN_WINDOW_TICKS = 60 * 20;
	/** Hold right-click for 5 s straight (user). */
	public static final int REVIVE_TICKS = 5 * 20;
	/**
	 * Holding right-click on an entity makes the vanilla client send an interact packet every 4 ticks, so a hold counts as
	 * unbroken while the last one is at most this old. Letting go or looking away stops the packets and resets the revive.
	 */
	public static final int HOLD_GRACE_TICKS = 6;
	/** How far the reviver's eyes may be from the knocked player's body. */
	public static final double REVIVE_REACH = 3.5;
	/** Hold jump this long to give up. */
	public static final int GIVE_UP_TICKS = 3 * 20;
	/** Nobody left who could revive for this long = bleed out (5 s, so a squadmate stepping through a portal is not fatal). */
	public static final int NO_REVIVER_GRACE_TICKS = 5 * 20;
	/** After a resume (the host reloading the world), friends get 30 s to join before the "nobody left" rule applies. */
	public static final int RESUME_NO_REVIVER_GRACE_TICKS = 30 * 20;
	public static final float KNOCKED_HEALTH = 1.0f;
	/** Revived with 6 hearts. */
	public static final float REVIVE_HEALTH = 12.0f;
	public static final int REVIVE_BUFF_TICKS = 5 * 20;
	/** 2 s in which nothing can hurt a revived player. */
	public static final int REVIVE_SHIELD_TICKS = 2 * 20;
	/** How far round a knocked player mobs are told to forget them. */
	private static final double FORGET_RADIUS = 64.0;

	public static final ResourceKey<DamageType> BLED_OUT = ResourceKey.create(Registries.DAMAGE_TYPE, ProjectHeroMod.id("bled_out"));

	private static final ResourceLocation NO_JUMP = ProjectHeroMod.id("squad_knocked_no_jump");
	private static final ResourceLocation EARLY = ProjectHeroMod.id("squad_knockdown_early");
	private static final ResourceLocation LATE = ProjectHeroMod.id("squad_knockdown_late");

	/** Synced to every client (self and trackers): is this player knocked down? Not saved -- see {@link #SAVED}. */
	public static final AttachmentType<Boolean> KNOCKED = AttachmentRegistry.create(ProjectHeroMod.id("squad_knocked"),
			builder -> builder.initializer(() -> false).syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.all()));

	/** A knockdown the player left mid-way without it counting as a death (host leaving, server stopping): resumed on join. */
	public record Saved(int ticksLeft, int total) {
		public static final Codec<Saved> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.INT.fieldOf("ticks_left").forGetter(Saved::ticksLeft),
				Codec.INT.fieldOf("total").forGetter(Saved::total)
		).apply(i, Saved::new));
	}

	public static final AttachmentType<Saved> SAVED = AttachmentRegistry.create(ProjectHeroMod.id("squad_knocked_saved"),
			builder -> builder.persistent(Saved.CODEC));

	// ---------------------------------------------------------------- state

	/** One live knockdown. */
	static final class Knock {
		final UUID id;
		final int total;
		int ticksLeft;
		/** The squadmate holding right-click, or null. */
		UUID reviver;
		int progress;
		int lastHold;
		int noReviverTicks;
		int noReviverGrace = NO_REVIVER_GRACE_TICKS;
		final ServerBossEvent bar;
		boolean barPaused;

		Knock(UUID id, int total, int ticksLeft) {
			this.id = id;
			this.total = total;
			this.ticksLeft = ticksLeft;
			this.bar = new ServerBossEvent(Component.translatable("projecthero.knockdown.bar"), BossEvent.BossBarColor.RED,
					BossEvent.BossBarOverlay.PROGRESS);
			this.bar.setProgress(1.0f);
		}
	}

	/** Knocked player id -> their knockdown. Read from the network thread too (the payload filter). */
	private static final Map<UUID, Knock> KNOCKED_PLAYERS = new ConcurrentHashMap<>();
	/** Player id -> {server tick they were last revived, chain step of that knockdown}: shortens a quick re-knock. */
	private static final Map<UUID, long[]> RECENT = new HashMap<>();
	/** Player id -> server tick their post-revive protection ends. */
	private static final Map<UUID, Integer> SHIELD_UNTIL = new HashMap<>();

	private SquadKnockdown() {
	}

	// ---------------------------------------------------------------- setup

	public static void initialize() {
		PayloadTypeRegistry.playS2C().register(SquadKnockdownHudPayload.TYPE, SquadKnockdownHudPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(SquadKnockdownGiveUpPayload.TYPE, SquadKnockdownGiveUpPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(SquadKnockdownGiveUpPayload.TYPE,
				(payload, context) -> giveUp(context.player()));

		// The knockdown is the last word before a death: after every death save (DEFAULT phase), before the orbit grave.
		ServerLivingEntityEvents.ALLOW_DEATH.addPhaseOrdering(Event.DEFAULT_PHASE, LATE);
		ServerLivingEntityEvents.ALLOW_DEATH.addPhaseOrdering(LATE, com.projecthero.mod.galactus.orbit.GalactusOrbit.GRAVE_PHASE);
		ServerLivingEntityEvents.ALLOW_DEATH.register(LATE, SquadKnockdown::allowDeath);
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer p) {
				Knock k = KNOCKED_PLAYERS.get(p.getUUID());
				if (k != null) {
					end(p, k);
				}
				RECENT.remove(p.getUUID());
				SHIELD_UNTIL.remove(p.getUUID());
			}
		});

		// Knocked players take no damage and deal none; nothing else gets to react to a hit that will not land.
		ServerLivingEntityEvents.ALLOW_DAMAGE.addPhaseOrdering(EARLY, Event.DEFAULT_PHASE);
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(EARLY, SquadKnockdown::allowDamage);
		// A reviver who gets hurt loses their grip.
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, taken, blocked) -> {
			if (entity instanceof ServerPlayer p && taken > 0.0f) {
				for (Knock k : KNOCKED_PLAYERS.values()) {
					if (p.getUUID().equals(k.reviver)) {
						cancelRevive(p.server, k);
					}
				}
			}
		});

		// Hands off: knocked players cannot attack, use, place, break or open anything (both sides, so nothing flickers).
		AttackEntityCallback.EVENT.addPhaseOrdering(EARLY, Event.DEFAULT_PHASE);
		AttackEntityCallback.EVENT.register(EARLY, (player, level, hand, entity, hit) ->
				isKnocked(player) || (!level.isClientSide() && isReviving(player)) ? InteractionResult.FAIL : InteractionResult.PASS);
		AttackBlockCallback.EVENT.addPhaseOrdering(EARLY, Event.DEFAULT_PHASE);
		AttackBlockCallback.EVENT.register(EARLY, (player, level, hand, pos, dir) ->
				isKnocked(player) ? InteractionResult.FAIL : InteractionResult.PASS);
		UseBlockCallback.EVENT.addPhaseOrdering(EARLY, Event.DEFAULT_PHASE);
		UseBlockCallback.EVENT.register(EARLY, (player, level, hand, hit) ->
				isKnocked(player) ? InteractionResult.FAIL : InteractionResult.PASS);
		UseItemCallback.EVENT.addPhaseOrdering(EARLY, Event.DEFAULT_PHASE);
		UseItemCallback.EVENT.register(EARLY, (player, level, hand) -> isKnocked(player)
				? InteractionResultHolder.fail(player.getItemInHand(hand)) : InteractionResultHolder.pass(player.getItemInHand(hand)));
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, be) -> !isKnocked(player));
		// Right-click on a knocked squadmate: the revive hold. Early, so no other "use a player" action (piggyback seats,
		// heals, ...) takes the click first.
		UseEntityCallback.EVENT.addPhaseOrdering(EARLY, Event.DEFAULT_PHASE);
		UseEntityCallback.EVENT.register(EARLY, (player, level, hand, entity, hit) -> {
			if (isKnocked(player)) {
				return InteractionResult.FAIL;
			}
			if (!(entity instanceof Player target) || !isKnocked(target)) {
				return InteractionResult.PASS;
			}
			if (level.isClientSide()) {
				return InteractionResult.CONSUME; // the server decides; CONSUME sends the packet without swinging the arm
			}
			return player instanceof ServerPlayer sp && target instanceof ServerPlayer st ? holdRevive(sp, st) : InteractionResult.PASS;
		});

		ServerTickEvents.END_SERVER_TICK.register(SquadKnockdown::tick);

		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> onDisconnect(handler.getPlayer(), server));
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> server.execute(() -> onJoin(handler.getPlayer())));
	}

	/** {@code ServerStateReset}: the live tables hold boss bars that hold players. */
	public static void clearSessionState() {
		for (Knock k : KNOCKED_PLAYERS.values()) {
			k.bar.removeAllPlayers();
		}
		KNOCKED_PLAYERS.clear();
		RECENT.clear();
		SHIELD_UNTIL.clear();
	}

	// ---------------------------------------------------------------- queries

	/** Side-aware: the server's table on the server, the synced attachment on a client. */
	public static boolean isKnocked(Entity entity) {
		if (!(entity instanceof Player player)) {
			return false;
		}
		if (player.level() != null && player.level().isClientSide()) {
			return player.getAttachedOrElse(KNOCKED, false);
		}
		return KNOCKED_PLAYERS.containsKey(player.getUUID());
	}

	/** Server: ticks left on {@code player}'s countdown, 0 when not knocked. */
	public static int ticksLeft(Player player) {
		Knock k = KNOCKED_PLAYERS.get(player.getUUID());
		return k == null ? 0 : k.ticksLeft;
	}

	/** Server: the full length of {@code player}'s current countdown, 0 when not knocked. */
	public static int totalTicks(Player player) {
		Knock k = KNOCKED_PLAYERS.get(player.getUUID());
		return k == null ? 0 : k.total;
	}

	/** Server: revive ticks held on {@code player} so far. */
	public static int reviveProgress(Player player) {
		Knock k = KNOCKED_PLAYERS.get(player.getUUID());
		return k == null ? 0 : k.progress;
	}

	/** Server: is {@code player} holding right-click on a knocked squadmate right now? */
	public static boolean isReviving(Player player) {
		if (player.getServer() == null) {
			return false;
		}
		int now = player.getServer().getTickCount();
		for (Knock k : KNOCKED_PLAYERS.values()) {
			if (player.getUUID().equals(k.reviver) && now - k.lastHold <= HOLD_GRACE_TICKS) {
				return true;
			}
		}
		return false;
	}

	/** Server: is the post-revive protection still up? */
	public static boolean shielded(Player player) {
		Integer until = SHIELD_UNTIL.get(player.getUUID());
		return until != null && player.getServer() != null && player.getServer().getTickCount() < until;
	}

	/**
	 * The packet filter (network thread): a knocked player's power-key and ability packets are dropped. Only this mod's own
	 * packets are touched, and the squad screen's friendly-fire switch and the give-up hold still get through.
	 */
	public static boolean blocksPayload(ServerPlayer player, ResourceLocation id) {
		if (player == null || id == null || !ProjectHeroMod.MOD_ID.equals(id.getNamespace())
				|| !KNOCKED_PLAYERS.containsKey(player.getUUID())) {
			return false;
		}
		return !id.getPath().startsWith("squad_");
	}

	/**
	 * Could {@code mate} pick {@code downed} up at all (not counting reach): a different player in the same squad, alive,
	 * not a spectator, not knocked themselves, in the same dimension.
	 */
	public static boolean standingFor(ServerPlayer mate, ServerPlayer downed) {
		return mate != null && mate != downed && !mate.getUUID().equals(downed.getUUID()) && mate.isAlive() && !mate.isDeadOrDying()
				&& !mate.isSpectator() && !mate.hasDisconnected() && !KNOCKED_PLAYERS.containsKey(mate.getUUID())
				&& mate.level() == downed.level() && SquadManager.get(downed.server).sameSquad(mate.getUUID(), downed.getUUID());
	}

	/** Is anyone in {@code player}'s squad still standing to revive them? */
	public static boolean hasStandingSquadmate(ServerPlayer player) {
		Squad squad = SquadManager.get(player.server).squadOf(player.getUUID());
		if (squad == null) {
			return false;
		}
		for (UUID id : squad.members()) {
			if (!id.equals(player.getUUID()) && standingFor(player.server.getPlayerList().getPlayer(id), player)) {
				return true;
			}
		}
		return false;
	}

	// ---------------------------------------------------------------- hooks

	/** {@code ALLOW_DEATH}, last phase: a killing blow on a squad member with a squadmate standing knocks them down instead. */
	private static boolean allowDeath(LivingEntity entity, DamageSource source, float amount) {
		if (!(entity instanceof ServerPlayer p) || p.isSpectator() || KNOCKED_PLAYERS.containsKey(p.getUUID())) {
			return true; // a knocked player dying (/kill, the void, bleeding out) just dies
		}
		if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || source.is(BLED_OUT)) {
			return true; // /kill, the void, serum rejection: nothing to crawl away from
		}
		if (com.projecthero.mod.galactus.orbit.GalactusOrbit.totemWillSave(p, source)) {
			return true; // vanilla fires the held totem right after this hook
		}
		if (!hasStandingSquadmate(p)) {
			return true; // nobody to pick you up
		}
		knock(p);
		return false;
	}

	private static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
		if (source.is(DamageTypes.GENERIC_KILL) || source.is(DamageTypes.FELL_OUT_OF_WORLD) || source.is(BLED_OUT)) {
			return true;
		}
		if (entity instanceof Player p && (KNOCKED_PLAYERS.containsKey(p.getUUID()) || shielded(p))) {
			return false;
		}
		return !(source.getEntity() instanceof Player dealer && KNOCKED_PLAYERS.containsKey(dealer.getUUID()));
	}

	private static void onDisconnect(ServerPlayer p, MinecraftServer server) {
		Knock k = KNOCKED_PLAYERS.get(p.getUUID());
		if (k == null) {
			return;
		}
		if (!server.isRunning() || server.isSingleplayerOwner(p.getGameProfile())) {
			// the world is closing under them: not a death -- the knockdown carries on when they are back
			p.setAttached(SAVED, new Saved(Math.max(1, k.ticksLeft), k.total));
			end(p, k);
			return;
		}
		bleedOut(p); // logging out while knocked counts as bleeding out
	}

	private static void onJoin(ServerPlayer p) {
		Saved saved = p.getAttached(SAVED);
		if (saved == null) {
			return;
		}
		p.removeAttached(SAVED);
		if (!p.isAlive() || p.isDeadOrDying()) {
			return;
		}
		Knock k = new Knock(p.getUUID(), Math.max(1, saved.total()), Math.max(1, saved.ticksLeft()));
		k.noReviverGrace = RESUME_NO_REVIVER_GRACE_TICKS;
		start(p, k, false);
	}

	// ---------------------------------------------------------------- knock / revive / bleed out

	/** Knock {@code p} down now (the death hook, and the tests). The countdown is shortened for a quick re-knock. */
	public static void knock(ServerPlayer p) {
		if (KNOCKED_PLAYERS.containsKey(p.getUUID())) {
			return;
		}
		long now = p.server.getTickCount();
		long[] rec = RECENT.get(p.getUUID());
		int step = 0;
		if (rec != null && now - rec[0] <= CHAIN_WINDOW_TICKS) {
			step = (int) Math.min(rec[1] + 1, CHAIN_TICKS.length - 1);
		}
		RECENT.put(p.getUUID(), new long[] {Long.MIN_VALUE / 2, step});
		int total = CHAIN_TICKS[step];
		start(p, new Knock(p.getUUID(), total, total), true);
	}

	private static void start(ServerPlayer p, Knock k, boolean fresh) {
		KNOCKED_PLAYERS.put(p.getUUID(), k);
		p.setAttached(KNOCKED, true);
		p.setHealth(KNOCKED_HEALTH);
		p.setAbsorptionAmount(0.0f);
		p.clearFire();
		p.setSprinting(false);
		p.stopUsingItem();
		p.stopFallFlying();
		p.stopRiding();
		p.ejectPassengers();
		p.fallDistance = 0.0f;
		if (p.getAbilities().flying && !p.isCreative()) {
			p.getAbilities().flying = false;
			p.onUpdateAbilities();
		}
		// the Void cannot keep a body that is down
		if (com.projecthero.mod.sentry.Sentry.inTakeover(p)) {
			com.projecthero.mod.sentry.VoidTakeover.end(p, com.projecthero.mod.sentry.VoidTakeover.End.DIED);
		}
		AttributeInstance jump = p.getAttribute(Attributes.JUMP_STRENGTH);
		if (jump != null) {
			jump.addOrUpdateTransientModifier(new AttributeModifier(NO_JUMP, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		}
		k.bar.setProgress(Math.max(0.0f, Math.min(1.0f, k.ticksLeft / (float) k.total)));
		k.bar.addPlayer(p);
		forgetAsTarget(p);
		if (fresh) {
			p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.PLAYER_BIG_FALL, SoundSource.PLAYERS, 0.9f, 0.7f);
			tellSquad(p, Component.translatable("projecthero.knockdown.down", p.getGameProfile().getName())
					.withStyle(ChatFormatting.RED), false);
		}
	}

	/** Every mob that was after {@code p} lets go (bosses drop them through {@code BossTargets}). */
	private static void forgetAsTarget(ServerPlayer p) {
		AABB box = p.getBoundingBox().inflate(FORGET_RADIUS);
		for (Mob mob : p.serverLevel().getEntitiesOfClass(Mob.class, box, m -> true)) {
			if (mob.getTarget() == p) {
				mob.setTarget(null);
			}
			Brain<?> brain = mob.getBrain();
			if (brain.hasMemoryValue(MemoryModuleType.ATTACK_TARGET) && brain.getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null) == p) {
				brain.eraseMemory(MemoryModuleType.ATTACK_TARGET);
			}
			if (brain.hasMemoryValue(MemoryModuleType.ANGRY_AT) && p.getUUID().equals(brain.getMemory(MemoryModuleType.ANGRY_AT).orElse(null))) {
				brain.eraseMemory(MemoryModuleType.ANGRY_AT);
			}
		}
	}

	/** The revive hold, called for every interact packet (every 4 ticks while right-click is held on the knocked player). */
	public static InteractionResult holdRevive(ServerPlayer reviver, ServerPlayer target) {
		Knock k = KNOCKED_PLAYERS.get(target.getUUID());
		if (k == null || !SquadManager.get(target.server).sameSquad(reviver.getUUID(), target.getUUID())) {
			return InteractionResult.PASS; // only squadmates can revive
		}
		if (!standingFor(reviver, target) || !inReach(reviver, target)) {
			return InteractionResult.CONSUME;
		}
		int now = reviver.server.getTickCount();
		if (k.reviver != null && !k.reviver.equals(reviver.getUUID()) && now - k.lastHold <= HOLD_GRACE_TICKS) {
			reviver.displayClientMessage(Component.translatable("projecthero.knockdown.busy", target.getGameProfile().getName())
					.withStyle(ChatFormatting.GRAY), true);
			return InteractionResult.CONSUME; // one reviver at a time
		}
		if (!reviver.getUUID().equals(k.reviver)) {
			// one revive at a time per reviver, too
			for (Knock other : KNOCKED_PLAYERS.values()) {
				if (other != k && reviver.getUUID().equals(other.reviver)) {
					cancelRevive(reviver.server, other);
				}
			}
			k.reviver = reviver.getUUID();
			k.progress = 0;
		}
		k.lastHold = now;
		return InteractionResult.CONSUME;
	}

	private static boolean inReach(ServerPlayer reviver, ServerPlayer target) {
		Vec3 eye = reviver.getEyePosition();
		AABB b = target.getBoundingBox();
		double dx = Math.max(Math.max(b.minX - eye.x, 0.0), eye.x - b.maxX);
		double dy = Math.max(Math.max(b.minY - eye.y, 0.0), eye.y - b.maxY);
		double dz = Math.max(Math.max(b.minZ - eye.z, 0.0), eye.z - b.maxZ);
		return dx * dx + dy * dy + dz * dz <= REVIVE_REACH * REVIVE_REACH;
	}

	private static void cancelRevive(MinecraftServer server, Knock k) {
		if (k.reviver == null) {
			return;
		}
		ServerPlayer r = server.getPlayerList().getPlayer(k.reviver);
		k.reviver = null;
		k.progress = 0;
		if (r != null) {
			sendHud(r, SquadKnockdownHudPayload.none());
		}
	}

	/** The give-up hold finished (client), re-checked here: still knocked and nobody mid-revive. */
	public static boolean giveUp(ServerPlayer p) {
		Knock k = KNOCKED_PLAYERS.get(p.getUUID());
		if (k == null || (k.reviver != null && k.progress > 0)) {
			return false;
		}
		bleedOut(p);
		return true;
	}

	/** Stand {@code p} up: 6 hearts, Resistance II and Regeneration I for 5 s, 2 s untouchable. */
	public static void revive(ServerPlayer p, ServerPlayer reviver) {
		Knock k = KNOCKED_PLAYERS.get(p.getUUID());
		if (k == null) {
			return;
		}
		end(p, k);
		int now = p.server.getTickCount();
		long[] rec = RECENT.get(p.getUUID());
		RECENT.put(p.getUUID(), new long[] {now, rec == null ? 0 : rec[1]});
		SHIELD_UNTIL.put(p.getUUID(), now + REVIVE_SHIELD_TICKS);
		p.setHealth(Math.min(p.getMaxHealth(), REVIVE_HEALTH));
		p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, REVIVE_BUFF_TICKS, 1));
		p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, REVIVE_BUFF_TICKS, 0));
		p.serverLevel().sendParticles(ParticleTypes.TOTEM_OF_UNDYING, p.getX(), p.getY() + 0.6, p.getZ(), 24, 0.35, 0.45, 0.35, 0.22);
		p.serverLevel().sendParticles(ParticleTypes.HEART, p.getX(), p.getY() + 1.2, p.getZ(), 4, 0.4, 0.3, 0.4, 0.0);
		p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.3f, 1.6f);
		p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.35f, 1.4f);
		if (reviver != null) {
			tellSquad(p, Component.translatable("projecthero.knockdown.revived", reviver.getGameProfile().getName(),
					p.getGameProfile().getName()).withStyle(ChatFormatting.GREEN), true);
		}
	}

	/** The countdown ran out, they gave up, nobody was left, or they logged out: a real death ("bled out"). */
	public static void bleedOut(ServerPlayer p) {
		Knock k = KNOCKED_PLAYERS.get(p.getUUID());
		if (k != null) {
			end(p, k);
		}
		RECENT.remove(p.getUUID());
		DamageSource source = new DamageSource(p.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(BLED_OUT));
		boolean landed = p.hurt(source, 1.0e6f);
		if (!landed && p.isAlive() && !p.isDeadOrDying()) {
			// something vetoed the damage itself (an immunity window): the knockdown is over all the same
			p.setHealth(0.0f);
			p.die(source);
		}
	}

	/** Lift the knockdown (revive, death, logout): table, attachment, jump lock, bar, HUDs. */
	private static void end(ServerPlayer p, Knock k) {
		KNOCKED_PLAYERS.remove(k.id);
		p.setAttached(KNOCKED, false);
		AttributeInstance jump = p.getAttribute(Attributes.JUMP_STRENGTH);
		if (jump != null) {
			jump.removeModifier(NO_JUMP);
		}
		k.bar.removeAllPlayers();
		sendHud(p, SquadKnockdownHudPayload.none());
		if (k.reviver != null) {
			ServerPlayer r = p.server.getPlayerList().getPlayer(k.reviver);
			if (r != null) {
				sendHud(r, SquadKnockdownHudPayload.none());
			}
		}
	}

	/** Test/command hook: drop a knockdown with no death and no revive. */
	public static void clear(ServerPlayer p) {
		Knock k = KNOCKED_PLAYERS.get(p.getUUID());
		if (k != null) {
			end(p, k);
		}
		RECENT.remove(p.getUUID());
		SHIELD_UNTIL.remove(p.getUUID());
	}

	/** Test hook: set the countdown. */
	public static void setTicksLeft(ServerPlayer p, int ticks) {
		Knock k = KNOCKED_PLAYERS.get(p.getUUID());
		if (k != null) {
			k.ticksLeft = ticks;
		}
	}

	// ---------------------------------------------------------------- tick

	private static void tick(MinecraftServer server) {
		if (KNOCKED_PLAYERS.isEmpty()) {
			if (!SHIELD_UNTIL.isEmpty() && server.getTickCount() % 200 == 0) {
				int now = server.getTickCount();
				SHIELD_UNTIL.values().removeIf(until -> until <= now);
			}
			return;
		}
		int now = server.getTickCount();
		for (Knock k : new ArrayList<>(KNOCKED_PLAYERS.values())) {
			ServerPlayer p = server.getPlayerList().getPlayer(k.id);
			if (p == null) {
				KNOCKED_PLAYERS.remove(k.id);
				k.bar.removeAllPlayers();
				continue;
			}
			tickOne(server, p, k, now);
		}
	}

	private static void tickOne(MinecraftServer server, ServerPlayer p, Knock k, int now) {
		if (!p.isAlive() || p.isDeadOrDying()) {
			end(p, k);
			return;
		}
		// the body holds still at a sliver: no regen, no hunger, no drowning, no fire, no fall
		if (p.getHealth() != KNOCKED_HEALTH) {
			p.setHealth(KNOCKED_HEALTH);
		}
		p.setAirSupply(p.getMaxAirSupply());
		p.getFoodData().setExhaustion(0.0f);
		if (p.isOnFire()) {
			p.clearFire();
		}
		if (p.isSprinting()) {
			p.setSprinting(false);
		}
		p.fallDistance = 0.0f;

		ServerPlayer reviver = null;
		if (k.reviver != null) {
			reviver = server.getPlayerList().getPlayer(k.reviver);
			if (reviver == null || now - k.lastHold > HOLD_GRACE_TICKS || !standingFor(reviver, p) || !inReach(reviver, p)) {
				cancelRevive(server, k);
				reviver = null;
			}
		}
		if (reviver != null) {
			k.progress++;
			k.noReviverTicks = 0;
			if (k.progress >= REVIVE_TICKS) {
				revive(p, reviver);
				return;
			}
		} else {
			if (hasStandingSquadmate(p)) {
				k.noReviverTicks = 0;
			} else if (++k.noReviverTicks >= k.noReviverGrace) {
				bleedOut(p); // the whole squad is down (or gone): nobody is coming
				return;
			}
			k.ticksLeft--;
			if (k.ticksLeft <= 0) {
				bleedOut(p);
				return;
			}
		}

		// the top bar: drains with the countdown, turns white while it is paused
		k.bar.setProgress(Math.max(0.0f, Math.min(1.0f, k.ticksLeft / (float) k.total)));
		boolean paused = reviver != null;
		if (paused != k.barPaused) {
			k.barPaused = paused;
			k.bar.setColor(paused ? BossEvent.BossBarColor.WHITE : BossEvent.BossBarColor.RED);
		}
		sendHud(p, new SquadKnockdownHudPayload(SquadKnockdownHudPayload.MODE_KNOCKED, k.ticksLeft, k.total, k.progress, REVIVE_TICKS,
				reviver == null ? "" : reviver.getGameProfile().getName()));
		if (reviver != null) {
			sendHud(reviver, new SquadKnockdownHudPayload(SquadKnockdownHudPayload.MODE_REVIVING, k.ticksLeft, k.total, k.progress,
					REVIVE_TICKS, p.getGameProfile().getName()));
		}
	}

	// ---------------------------------------------------------------- messaging

	private static void sendHud(ServerPlayer p, SquadKnockdownHudPayload payload) {
		if (ServerPlayNetworking.canSend(p, SquadKnockdownHudPayload.TYPE)) {
			ServerPlayNetworking.send(p, payload);
		}
	}

	/** Chat line to the squad; the knocked player themselves only when {@code includeSelf} (their screen shows just the bar). */
	private static void tellSquad(ServerPlayer p, Component message, boolean includeSelf) {
		Squad squad = SquadManager.get(p.server).squadOf(p.getUUID());
		if (squad == null) {
			return;
		}
		for (UUID id : squad.members()) {
			if (!includeSelf && id.equals(p.getUUID())) {
				continue;
			}
			ServerPlayer member = p.server.getPlayerList().getPlayer(id);
			if (member != null) {
				member.sendSystemMessage(message);
			}
		}
	}
}
