package com.projecthero.mod.ghostrider;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.ghostrider.data.GhostRiderState;
import com.projecthero.mod.hero.HeroTiers;
import com.projecthero.mod.hero.power.PowerToggles;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.WitherSkeleton;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import org.joml.Vector3f;

/**
 * The single server-side API for Ghost Rider, the v0.16.5 Hero-Tier power. Nothing else touches
 * {@link GhostRiderState}; every change re-saves through {@link ServerPlayer#setAttached}. The moves live in
 * {@link GhostRiderAbilities}, the damage rules and guilt in {@link GhostRiderDamage}, the numbers in
 * {@link GhostRiderConfig}.
 *
 * <h2>How it works</h2>
 * Mephisto's Contract (rare Nether fortress / bastion loot) is signed in blood: the Spirit of Vengeance bonds with the
 * signer, who pays 5 hearts of max health until the first transformation. <b>H</b> sets the head ablaze any time: the
 * flames climb the body and the head burns down to a flaming skull. Transformed: immune to fire and lava, +6 max
 * health, never drowns, undead fear him, and the six keys work. Human: fire can't hurt him, nothing else.
 *
 * <h2>Hellfire and Vengeance</h2>
 * Hellfire (0..100) pays for the moves and refills faster at night and in the Nether. Every creature that hurts him, a
 * villager or a player is <i>guilty</i>; hitting the guilty fills Vengeance (0..100), which the Penance Stare burns.
 */
public final class GhostRider {
	public static final String KEY = "ghost_rider";

	private static final ResourceLocation BLOOD_PRICE_ID = PowerToggles.id("ghost_rider_blood_price");
	private static final ResourceLocation HEALTH_ID = PowerToggles.id("ghost_rider_health");

	/** Hellfire orange and an ember red. */
	public static final DustParticleOptions HELLFIRE = new DustParticleOptions(new Vector3f(1.0f, 0.45f, 0.05f), 1.4f);
	public static final DustParticleOptions HELLFIRE_BIG = new DustParticleOptions(new Vector3f(1.0f, 0.55f, 0.1f), 2.4f);
	public static final DustParticleOptions EMBER = new DustParticleOptions(new Vector3f(0.75f, 0.08f, 0.02f), 1.1f);

	private static final Map<UUID, Long> LAST_MESSAGE = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> LAST_TICK = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> LAST_TOGGLE = new ConcurrentHashMap<>();
	/** Game time a guilty creature was last hit by each Ghost Rider (Vengeance only decays after 10 s without). */
	private static final Map<UUID, Long> LAST_GUILTY_HIT = new ConcurrentHashMap<>();

	private GhostRider() {
	}

	public static void clearSessionState() {
		LAST_MESSAGE.clear();
		LAST_TICK.clear();
		LAST_TOGGLE.clear();
		LAST_GUILTY_HIT.clear();
		GhostRiderAbilities.clearSessionState();
		GhostRiderAbilityManager.clearSessionState();
		GhostRiderDamage.clearSessionState();
	}

	// ---------------------------------------------------------------- state

	public static GhostRiderState state(ServerPlayer player) {
		return player.getAttachedOrCreate(ModAttachments.GHOST_RIDER_STATE);
	}

	static void save(ServerPlayer player, GhostRiderState state) {
		player.setAttached(ModAttachments.GHOST_RIDER_STATE, state);
	}

	private static GhostRiderState peek(Player player) {
		return player.getAttachedOrElse(ModAttachments.GHOST_RIDER_STATE, null);
	}

	/** Client-safe (the attachment is synced). */
	public static boolean hasPower(Player player) {
		GhostRiderState s = peek(player);
		return s != null && s.hasPower;
	}

	/** Has the power and the Spirit is out. Client-safe. */
	public static boolean transformed(Player player) {
		GhostRiderState s = peek(player);
		return s != null && s.hasPower && s.transformed;
	}

	public static float hellfire(Player player) {
		GhostRiderState s = peek(player);
		return s == null ? 0f : s.hellfire;
	}

	public static float vengeance(Player player) {
		GhostRiderState s = peek(player);
		return s == null ? 0f : s.vengeance;
	}

	public static boolean shieldUp(Player player) {
		GhostRiderState s = peek(player);
		return s != null && s.transformed && s.shieldUntil > player.level().getGameTime();
	}

	public static int cooldownRemaining(Player player, String abilityId) {
		GhostRiderState s = peek(player);
		if (s == null) {
			return 0;
		}
		Long ready = s.abilityReadyAt.get(abilityId);
		return ready == null ? 0 : (int) Math.max(0L, ready - player.level().getGameTime());
	}

	/** Hellfire per second right now: night and the Nether feed it. Client-safe. */
	public static float regenPerSecond(Player player) {
		GhostRiderState s = peek(player);
		if (s == null || !s.hasPower) {
			return 0f;
		}
		if (!s.transformed) {
			return GhostRiderConfig.HELLFIRE_REGEN_HUMAN;
		}
		Level level = player.level();
		if (level.dimension() == Level.NETHER) {
			return GhostRiderConfig.HELLFIRE_REGEN_NETHER;
		}
		return isNight(level) ? GhostRiderConfig.HELLFIRE_REGEN_NIGHT : GhostRiderConfig.HELLFIRE_REGEN_DAY;
	}

	private static boolean isNight(Level level) {
		long t = level.getDayTime() % 24000L;
		return t >= 13000L && t <= 23000L;
	}

	static void say(ServerPlayer player, String key, ChatFormatting colour, Object... args) {
		long now = player.level().getGameTime();
		Long last = LAST_MESSAGE.get(player.getUUID());
		if (last != null && now - last < 10 && now >= last) {
			return;
		}
		LAST_MESSAGE.put(player.getUUID(), now);
		player.displayClientMessage(Component.translatable(key, args).withStyle(colour), true);
	}

	// ---------------------------------------------------------------- resources

	public static boolean spendHellfire(ServerPlayer player, float cost) {
		if (cost <= 0f) {
			return true;
		}
		GhostRiderState s = state(player);
		if (s.hellfire + 1.0e-3f < cost) {
			return false;
		}
		GhostRiderState n = s.copy();
		n.hellfire = Math.max(0f, Math.min(GhostRiderConfig.HELLFIRE_MAX, s.hellfire) - cost);
		save(player, n);
		return true;
	}

	public static void setHellfire(ServerPlayer player, float value) {
		GhostRiderState n = state(player).copy();
		n.hellfire = Math.max(0f, Math.min(GhostRiderConfig.HELLFIRE_MAX, value));
		save(player, n);
	}

	public static void setVengeance(ServerPlayer player, float value) {
		GhostRiderState n = state(player).copy();
		n.vengeance = Math.max(0f, Math.min(GhostRiderConfig.VENGEANCE_MAX, value));
		save(player, n);
	}

	/** A hit on a guilty creature: Vengeance grows with the damage. */
	static void feedVengeance(ServerPlayer player, float damage) {
		LAST_GUILTY_HIT.put(player.getUUID(), player.level().getGameTime());
		GhostRiderState s = state(player);
		float next = Math.min(GhostRiderConfig.VENGEANCE_MAX, s.vengeance + damage * GhostRiderConfig.VENGEANCE_PER_DAMAGE);
		if (next != s.vengeance) {
			GhostRiderState n = s.copy();
			n.vengeance = next;
			save(player, n);
		}
	}

	// ---------------------------------------------------------------- grant / revoke

	/** Mephisto's Contract / the command. False if the player already carries the Spirit. */
	public static boolean grant(ServerPlayer player) {
		if (state(player).hasPower) {
			return false;
		}
		if (com.projecthero.mod.hero.UniquePowers.refuse(player, KEY)) {
			return false;
		}
		HeroTiers.claimPrimary(player, KEY);
		GhostRiderState s = new GhostRiderState();
		s.hasPower = true;
		s.hellfire = GhostRiderConfig.HELLFIRE_MAX;
		save(player, s);
		reconcile(player);
		ServerLevel level = (ServerLevel) player.level();
		Vec3 c = player.position().add(0, 1.0, 0);
		level.sendParticles(ParticleTypes.FLAME, c.x, c.y, c.z, 80, 0.5, 1.0, 0.5, 0.05);
		level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, c.x, c.y, c.z, 30, 0.4, 0.8, 0.4, 0.03);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, c.x, c.y, c.z, 30, 0.5, 1.0, 0.5, 0.02);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 0.6f, 1.4f);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1.2f, 0.6f);
		player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(
				Component.translatable("message.projecthero.ghost_rider.title").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)));
		player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(
				Component.translatable("message.projecthero.ghost_rider.subtitle").withStyle(ChatFormatting.RED)));
		player.displayClientMessage(Component.translatable("message.projecthero.ghost_rider.acquired")
				.withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
		player.displayClientMessage(Component.translatable("message.projecthero.ghost_rider.acquired_hint")
				.withStyle(ChatFormatting.YELLOW), false);
		return true;
	}

	public static void revoke(ServerPlayer player) {
		GhostRiderAbilities.clear(player);
		GhostRiderAbilities.dismissCycle(player);
		save(player, new GhostRiderState());
		reconcile(player);
		forget(player.getUUID());
	}

	private static void forget(UUID id) {
		LAST_MESSAGE.remove(id);
		LAST_TICK.remove(id);
		LAST_TOGGLE.remove(id);
		LAST_GUILTY_HIT.remove(id);
		GhostRiderAbilityManager.forget(id);
	}

	// ---------------------------------------------------------------- the change (H)

	/** H: the Spirit of Vengeance out or back (debounced). */
	public static void toggleForm(ServerPlayer player) {
		GhostRiderState s = state(player);
		if (!s.hasPower || !player.isAlive() || player.isSpectator()) {
			return;
		}
		long now = player.level().getGameTime();
		Long last = LAST_TOGGLE.get(player.getUUID());
		if (last != null && now - last < GhostRiderConfig.TOGGLE_COOLDOWN && now >= last) {
			return;
		}
		LAST_TOGGLE.put(player.getUUID(), now);
		if (s.transformed) {
			revert(player);
		} else {
			transform(player);
		}
	}

	public static void transform(ServerPlayer player) {
		GhostRiderState s = state(player);
		if (!s.hasPower || s.transformed) {
			return;
		}
		long now = player.level().getGameTime();
		GhostRiderState n = s.copy();
		n.transformed = true;
		n.formChangeAt = now;
		n.bonded = true; // the blood price is paid with the first change
		n.animId = GhostRiderState.ANIM_TRANSFORM;
		n.animStart = now;
		save(player, n);
		reconcile(player);
		player.clearFire();
		GhostRiderAbilities.beginTransformFx(player);
	}

	public static void revert(ServerPlayer player) {
		GhostRiderState s = state(player);
		if (!s.transformed) {
			return;
		}
		GhostRiderAbilities.clear(player);
		GhostRiderAbilities.dismissCycle(player);
		long now = player.level().getGameTime();
		GhostRiderState n = state(player).copy();
		n.transformed = false;
		n.formChangeAt = now;
		n.blasting = false;
		n.chainUntil = 0L;
		n.chainTarget = java.util.List.of();
		n.spinUntil = 0L;
		n.shieldUntil = 0L;
		n.stareTarget = -1;
		n.rushing = false;
		n.animId = GhostRiderState.ANIM_REVERT;
		n.animStart = now;
		save(player, n);
		reconcile(player);
		ServerLevel level = (ServerLevel) player.level();
		Vec3 c = player.position().add(0, 1.6, 0);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, c.x, c.y, c.z, 30, 0.3, 0.5, 0.3, 0.02);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 1.0f, 0.7f);
	}

	// ---------------------------------------------------------------- stats

	/** Max-health modifiers: the blood price until bonded, +6 while transformed. */
	public static void reconcile(ServerPlayer player) {
		GhostRiderState s = peek(player);
		boolean owes = s != null && s.hasPower && !s.bonded;
		boolean out = s != null && s.hasPower && s.transformed;
		if (owes) {
			PowerToggles.modifier(player, Attributes.MAX_HEALTH, BLOOD_PRICE_ID, -GhostRiderConfig.BLOOD_PRICE, AttributeModifier.Operation.ADD_VALUE);
		} else {
			PowerToggles.clearModifier(player, Attributes.MAX_HEALTH, BLOOD_PRICE_ID);
		}
		if (out) {
			PowerToggles.modifier(player, Attributes.MAX_HEALTH, HEALTH_ID, GhostRiderConfig.MAX_HEALTH_BONUS, AttributeModifier.Operation.ADD_VALUE);
		} else {
			PowerToggles.clearModifier(player, Attributes.MAX_HEALTH, HEALTH_ID);
		}
		if (player.getHealth() > player.getMaxHealth()) {
			player.setHealth(player.getMaxHealth());
		}
	}

	/** Whether he may use a move right now; says why not. */
	static boolean canAct(ServerPlayer player) {
		GhostRiderState s = state(player);
		if (!s.hasPower || !player.isAlive() || player.isSpectator()) {
			return false;
		}
		if (!s.transformed) {
			say(player, "message.projecthero.ghost_rider.not_transformed", ChatFormatting.GRAY);
			return false;
		}
		if (player.level().getGameTime() - s.formChangeAt < GhostRiderConfig.TRANSFORM_TICKS - 10) {
			return false; // still burning in
		}
		return true;
	}

	// ---------------------------------------------------------------- tick

	public static void tick(ServerPlayer player) {
		GhostRiderState s = player.getAttachedOrElse(ModAttachments.GHOST_RIDER_STATE, null);
		if (s == null || !s.hasPower) {
			return;
		}
		long now = player.level().getGameTime();
		Long prev = LAST_TICK.put(player.getUUID(), now);
		if (prev != null && prev == now) {
			return;
		}
		if (player.tickCount % 20 == 0) {
			reconcile(player);
		}
		if (now % 5 == 0) {
			tickResources(player, s, now);
		}
		if (player.isOnFire()) {
			player.clearFire(); // hellfire can't burn its own host
		}
		if (s.transformed) {
			if (player.getAirSupply() < player.getMaxAirSupply()) {
				player.setAirSupply(player.getMaxAirSupply());
			}
			if (now % 20 == 0) {
				tickUndeadFear(player);
			}
			if (now % 4 == 0 && player.isAlive()) {
				// embers rising off the skull for everyone else (the owner sees the client-side flames)
				ServerLevel level = (ServerLevel) player.level();
				level.sendParticles(ParticleTypes.SMALL_FLAME, player.getX(), player.getEyeY() + 0.45, player.getZ(), 1, 0.12, 0.1, 0.12, 0.01);
			}
		}
		GhostRiderAbilities.tick(player);
	}

	private static void tickResources(ServerPlayer player, GhostRiderState s, long now) {
		float hf = Float.isNaN(s.hellfire) ? 0f : s.hellfire;
		float nextHf = Math.min(GhostRiderConfig.HELLFIRE_MAX, hf + regenPerSecond(player) / 4f);
		float v = Float.isNaN(s.vengeance) ? 0f : s.vengeance;
		Long lastHit = LAST_GUILTY_HIT.get(player.getUUID());
		float nextV = v;
		if (v > 0f && (lastHit == null || now - lastHit > 200L || now < lastHit)) {
			nextV = Math.max(0f, v - GhostRiderConfig.VENGEANCE_DECAY / 4f);
		}
		if (nextHf != s.hellfire || nextV != s.vengeance) {
			GhostRiderState n = s.copy();
			n.hellfire = nextHf;
			n.vengeance = nextV;
			save(player, n);
		}
	}

	/** Undead that came for him lose their nerve: their target drops and they back away (wither skeletons flee). */
	private static void tickUndeadFear(ServerPlayer player) {
		double r = GhostRiderConfig.UNDEAD_FEAR_RANGE;
		AABB box = player.getBoundingBox().inflate(r);
		for (Mob mob : player.level().getEntitiesOfClass(Mob.class, box,
				m -> m.getType().is(EntityTypeTags.UNDEAD) && m.isAlive() && !GhostRiderCombat.isBoss(m))) {
			boolean flee = mob instanceof WitherSkeleton || mob.getTarget() == player;
			if (mob.getTarget() == player) {
				mob.setTarget(null);
			}
			if (flee) {
				Vec3 away = mob.position().subtract(player.position());
				away = away.horizontalDistanceSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : new Vec3(away.x, 0, away.z).normalize();
				Vec3 to = mob.position().add(away.scale(10.0));
				mob.getNavigation().moveTo(to.x, to.y, to.z, 1.4);
			}
		}
	}

	// ---------------------------------------------------------------- lifecycle

	public static void onPlayerJoin(ServerPlayer player) {
		GhostRiderState s = state(player);
		if (!s.hasPower) {
			return;
		}
		long now = player.level().getGameTime();
		GhostRiderState n = s.copy();
		n.blasting = false;
		n.rushing = false;
		n.chainUntil = 0L;
		n.chainTarget = java.util.List.of();
		n.spinUntil = 0L;
		n.shieldUntil = 0L;
		n.stareTarget = -1;
		n.animId = GhostRiderState.ANIM_NONE;
		n.formChangeAt = 0L;
		n.hellfire = Float.isNaN(n.hellfire) ? 0f : Math.max(0f, Math.min(GhostRiderConfig.HELLFIRE_MAX, n.hellfire));
		n.vengeance = Float.isNaN(n.vengeance) ? 0f : Math.max(0f, Math.min(GhostRiderConfig.VENGEANCE_MAX, n.vengeance));
		n.abilityReadyAt.entrySet().removeIf(e -> e.getValue() > now + GhostRiderConfig.WRATH_COOLDOWN + 20L);
		save(player, n);
		reconcile(player);
	}

	public static void onPlayerRespawn(ServerPlayer player) {
		GhostRiderState s = state(player);
		if (!s.hasPower) {
			return;
		}
		GhostRiderState n = s.copy();
		n.transformed = false;
		n.formChangeAt = 0L;
		n.blasting = false;
		n.rushing = false;
		n.chainUntil = 0L;
		n.chainTarget = java.util.List.of();
		n.spinUntil = 0L;
		n.shieldUntil = 0L;
		n.stareTarget = -1;
		n.animId = GhostRiderState.ANIM_NONE;
		n.hellfire = Math.max(n.hellfire, GhostRiderConfig.HELLFIRE_MAX * 0.5f);
		n.vengeance = 0f;
		n.abilityReadyAt.clear();
		save(player, n);
		reconcile(player);
	}

	/** Death / logout / dimension change: drop everything transient (the power and the form stay). */
	public static void clearTransient(ServerPlayer player) {
		GhostRiderAbilities.clear(player);
		GhostRiderAbilities.dismissCycle(player);
		forget(player.getUUID());
		GhostRiderState s = player.getAttachedOrElse(ModAttachments.GHOST_RIDER_STATE, null);
		if (s != null && s.hasPower && (s.blasting || s.rushing || s.chainUntil != 0L || s.spinUntil != 0L || s.shieldUntil != 0L
				|| s.stareTarget != -1)) {
			GhostRiderState n = s.copy();
			n.blasting = false;
			n.rushing = false;
			n.chainUntil = 0L;
			n.chainTarget = java.util.List.of();
			n.spinUntil = 0L;
			n.shieldUntil = 0L;
			n.stareTarget = -1;
			save(player, n);
		}
	}

	static void setAnim(ServerPlayer player, int animId) {
		GhostRiderState n = state(player).copy();
		n.animId = animId;
		n.animStart = player.level().getGameTime();
		save(player, n);
	}
}
