package com.projecthero.mod.hulk;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.attachment.ModAttachments;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import org.joml.Vector3f;

/**
 * v0.13.21: how the Gamma power is gained now. The Gamma Serum no longer hands it over on its own -- it doses the
 * drinker ({@link ModAttachments#GAMMA_DOSED}). A dosed player who then right-clicks a Gamma Reactor overloads it:
 * {@link #CHARGE_TICKS} of rising hum and gathering light, then a staged detonation far bigger than TNT (a centre
 * blast, a ring of secondary blasts and a deep after-blast). The Gamma already in his blood is what lets him walk out
 * of it -- he is granted the power at the moment of detonation, shielded from the blast (and the fall it throws him
 * into) for {@link #SHIELD_TICKS}, and the Hulk comes out of the smoke.
 *
 * <p>Blocks use {@link Level.ExplosionInteraction#BLOCK} (bed / respawn-anchor rules), so the crater respects the
 * {@code blockExplosionDropDecay} gamerule and does not rain thousands of item entities. The in-flight overloads are
 * a static world-object cache, cleared by {@code ServerStateReset} via {@link Hulk#clearSessionState}.
 */
public final class GammaOverload {
	/** The reactor's wind-up before it goes (3 s). */
	public static final int CHARGE_TICKS = 60;
	/** Tick (after detonation) of each secondary ring blast, and of the deep after-blast. */
	private static final int RING_START = 4;
	private static final int RING_STEP = 2;
	private static final int RING_COUNT = 8;
	private static final int AFTER_BLAST = RING_START + RING_STEP * RING_COUNT + 4;
	public static final float CORE_POWER = 18.0f;
	public static final float RING_POWER = 9.0f;
	public static final double RING_RADIUS = 14.0;
	public static final float AFTER_POWER = 12.0f;
	/** How long the new Hulk shrugs off explosions and falls after the core blast (10 s -- the blast can throw him far). */
	public static final int SHIELD_TICKS = 200;

	private static final DustParticleOptions GAMMA = new DustParticleOptions(new Vector3f(0.35f, 1.0f, 0.25f), 2.0f);

	private record Overload(UUID player, ResourceKey<Level> dimension, BlockPos reactor, long startedAt) {
	}

	/** Keyed by the reactor's position (one overload per reactor); the dimension is checked on each tick. */
	private static final Map<BlockPos, Overload> ACTIVE = new ConcurrentHashMap<>();
	/** Player -> game time his blast shield ends. */
	private static final Map<UUID, Long> SHIELDED = new ConcurrentHashMap<>();

	private GammaOverload() {
	}

	public static void clearSessionState() {
		ACTIVE.clear();
		SHIELDED.clear();
	}

	public static void initialize() {
		ServerTickEvents.END_SERVER_TICK.register(GammaOverload::tick);
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (!(entity instanceof ServerPlayer player)) {
				return true;
			}
			Long until = SHIELDED.get(player.getUUID());
			if (until == null) {
				return true;
			}
			if (player.level().getGameTime() > until) {
				SHIELDED.remove(player.getUUID());
				return true;
			}
			// /kill and the void still work
			if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
				return true;
			}
			return !(source.is(DamageTypeTags.IS_EXPLOSION) || source.is(DamageTypeTags.IS_FALL)
					|| source.is(DamageTypeTags.IS_FIRE) || source.is(net.minecraft.world.damagesource.DamageTypes.IN_WALL));
		});
	}

	public static boolean isDosed(ServerPlayer player) {
		return Boolean.TRUE.equals(player.getAttachedOrElse(ModAttachments.GAMMA_DOSED, false));
	}

	public static void setDosed(ServerPlayer player, boolean dosed) {
		player.setAttached(ModAttachments.GAMMA_DOSED, dosed);
	}

	/** True while this player's blast shield is up (tests). */
	public static boolean shielded(ServerPlayer player) {
		Long until = SHIELDED.get(player.getUUID());
		return until != null && player.level().getGameTime() <= until;
	}

	public static boolean overloading(BlockPos reactor) {
		return ACTIVE.containsKey(reactor);
	}

	/** Stop an overload before it goes off (tests -- a real blast would flatten the neighbouring gametests). */
	public static void cancel(BlockPos reactor) {
		ACTIVE.remove(reactor);
	}

	/**
	 * A player right-clicked a Gamma Reactor. Starts the overload if he has the serum in his blood; otherwise he gets
	 * a warning (and a Gamma player just feels it hum).
	 */
	public static void onReactorUsed(ServerPlayer player, BlockPos pos) {
		if (Hulk.hasPower(player)) {
			player.displayClientMessage(Component.translatable("message.projecthero.gamma_reactor.hulk")
					.withStyle(ChatFormatting.GREEN), true);
			return;
		}
		if (!isDosed(player)) {
			player.displayClientMessage(Component.translatable("message.projecthero.gamma_reactor.not_dosed")
					.withStyle(ChatFormatting.GRAY), true);
			return;
		}
		if (ACTIVE.containsKey(pos.immutable())) {
			return;
		}
		start(player, pos);
	}

	/** Begin the overload (also the test hook). */
	public static void start(ServerPlayer player, BlockPos pos) {
		ServerLevel level = (ServerLevel) player.level();
		ACTIVE.put(pos.immutable(), new Overload(player.getUUID(), level.dimension(), pos.immutable(), level.getGameTime()));
		// the dose is spent the moment the reactor takes it
		setDosed(player, false);
		Vec3 c = Vec3.atCenterOf(pos);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.BLOCKS, 3.0f, 0.5f);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 3.0f, 0.5f);
		player.displayClientMessage(Component.translatable("message.projecthero.gamma_reactor.overload")
				.withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD), true);
	}

	private static void tick(MinecraftServer server) {
		if (ACTIVE.isEmpty()) {
			return;
		}
		for (Overload o : ACTIVE.values()) {
			ServerLevel level = server.getLevel(o.dimension());
			if (level == null) {
				ACTIVE.remove(o.reactor());
				continue;
			}
			long t = level.getGameTime() - o.startedAt();
			Vec3 c = Vec3.atCenterOf(o.reactor());
			if (t < CHARGE_TICKS) {
				charge(level, c, t);
			} else if (t == CHARGE_TICKS) {
				detonate(server, level, o, c);
			} else {
				long after = t - CHARGE_TICKS;
				if (after >= RING_START && after < RING_START + (long) RING_STEP * RING_COUNT && (after - RING_START) % RING_STEP == 0) {
					int i = (int) ((after - RING_START) / RING_STEP);
					double a = Math.PI * 2.0 * i / RING_COUNT;
					level.explode(null, c.x + Math.cos(a) * RING_RADIUS, c.y, c.z + Math.sin(a) * RING_RADIUS,
							RING_POWER, false, Level.ExplosionInteraction.BLOCK);
					level.sendParticles(GAMMA, c.x + Math.cos(a) * RING_RADIUS, c.y + 2.0, c.z + Math.sin(a) * RING_RADIUS,
							60, 3.0, 3.0, 3.0, 0.2);
				} else if (after == AFTER_BLAST) {
					level.explode(null, c.x, c.y - 4.0, c.z, AFTER_POWER, false, Level.ExplosionInteraction.BLOCK);
					level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y, c.z, 6, 6.0, 3.0, 6.0, 0.0);
					ACTIVE.remove(o.reactor());
				}
			}
		}
	}

	/** The wind-up: light pulled into the core, a climbing whine, the ground shaking. */
	private static void charge(ServerLevel level, Vec3 c, long t) {
		float f = t / (float) CHARGE_TICKS;
		int n = 6 + (int) (f * 30);
		for (int i = 0; i < n; i++) {
			double r = 6.0 * (1.0 - f) + 1.5;
			double a = level.random.nextDouble() * Math.PI * 2.0;
			double y = (level.random.nextDouble() - 0.3) * 4.0;
			double x = c.x + Math.cos(a) * r;
			double z = c.z + Math.sin(a) * r;
			level.sendParticles(GAMMA, x, c.y + y, z, 0, c.x - x, -y * 0.5, c.z - z, 0.08);
		}
		if (t % 10 == 0) {
			level.playSound(null, c.x, c.y, c.z, SoundEvents.BEACON_AMBIENT, SoundSource.BLOCKS, 3.0f, 0.5f + f * 1.5f);
			level.playSound(null, c.x, c.y, c.z, SoundEvents.WARDEN_HEARTBEAT, SoundSource.BLOCKS, 3.0f, 0.6f + f);
		}
		if (t > CHARGE_TICKS - 20 && t % 4 == 0) {
			level.sendParticles(ParticleTypes.END_ROD, c.x, c.y + 0.6, c.z, 12, 0.3, 0.6, 0.3, 0.25);
		}
	}

	private static void detonate(MinecraftServer server, ServerLevel level, Overload o, Vec3 c) {
		ServerPlayer player = server.getPlayerList().getPlayer(o.player());
		long now = level.getGameTime();
		// he gets the power -- and the shield -- a heartbeat before the blast reaches him
		if (player != null && player.level() == level) {
			SHIELDED.put(player.getUUID(), now + SHIELD_TICKS);
			Hulk.grant(player);
		}
		level.setBlock(o.reactor(), Blocks.AIR.defaultBlockState(), 3);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 12.0f, 0.5f);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.BLOCKS, 12.0f, 0.5f);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 12.0f, 0.6f);
		level.explode(null, c.x, c.y, c.z, CORE_POWER, false, Level.ExplosionInteraction.BLOCK);
		level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y + 1.0, c.z, 10, 5.0, 4.0, 5.0, 0.0);
		level.sendParticles(ParticleTypes.FLASH, c.x, c.y + 1.0, c.z, 3, 0.0, 0.0, 0.0, 0.0);
		// a green shock sphere
		for (int i = 0; i < 400; i++) {
			double u = level.random.nextDouble() * 2.0 - 1.0;
			double a = level.random.nextDouble() * Math.PI * 2.0;
			double s = Math.sqrt(1.0 - u * u);
			Vec3 dir = new Vec3(s * Math.cos(a), u, s * Math.sin(a));
			level.sendParticles(GAMMA, c.x + dir.x * 3.0, c.y + dir.y * 3.0, c.z + dir.z * 3.0, 0, dir.x, dir.y, dir.z, 1.6);
		}
		for (ServerPlayer near : level.players()) {
			if (near.distanceToSqr(c) < 80.0 * 80.0) {
				near.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 40, 0, false, false));
			}
		}
		if (player != null && player.level() == level && Hulk.hasPower(player)) {
			player.displayClientMessage(Component.translatable("message.projecthero.gamma_reactor.survived")
					.withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD), false);
			// the Hulk walks out of the smoke
			Hulk.transform(player, false);
			Hulk.setRage(player, HulkConfig.RAGE_MAX);
		}
	}
}
