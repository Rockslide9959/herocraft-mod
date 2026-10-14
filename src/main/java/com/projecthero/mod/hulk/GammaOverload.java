package com.projecthero.mod.hulk;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.power.PowerToggles;
import com.projecthero.mod.hulk.data.GammaAbsorb;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import org.joml.Vector3f;

/**
 * v0.13.21: how the Gamma power is gained. The Gamma Serum does not hand it over on its own -- it doses the drinker
 * ({@link ModAttachments#GAMMA_DOSED}). A dosed player who then right-clicks a Gamma Reactor overloads it.
 *
 * <p>v0.16.4 (user): "before the gamma reactor blows up it needs to create a big green flash which needs to last 10 seconds,
 * during that flash the player needs to fall to their knees and absorb the gamma radiation into their body ... after the
 * player absorbs the energy for 8 seconds then the gamma reactor blows up". The overload now runs:
 * <ol>
 *   <li>0 - {@link #FLASH_TICKS} (10 s): the reactor floods everything near it with a pulsing green flash (drawn by every
 *       client within {@code ~32} blocks from {@link ModAttachments#GAMMA_ABSORB}), green light bursting out of it and a
 *       rising hum;</li>
 *   <li>{@link #KNEEL_AT} (1.5 s): the player who started it drops to his knees -- pinned in place, free to look around;</li>
 *   <li>{@link #ABSORB_START} - {@link #FLASH_TICKS} (8 s): he soaks the radiation up -- streams of green energy and glowing
 *       wisps pour out of the flash into his body, which glows greener as it builds;</li>
 *   <li>{@link #FLASH_TICKS}: the reactor blows (the staged detonation: a centre blast, a ring of secondary blasts and a deep
 *       after-blast) -- and he is pulled out of the world into his own mind ({@link HulkMind}). When he comes back he
 *       changes slowly into the Hulk ({@link #awaken}).</li>
 * </ol>
 * The dose is only spent when the reactor goes off with him still absorbing; if he dies, logs out or wanders off during
 * the flash the reactor still blows, but he keeps the serum in his blood for another try. If the mind dimension is
 * missing he simply changes where he knelt (the pre-0.16.4 ending).
 *
 * <p>Blocks use {@link Level.ExplosionInteraction#BLOCK} (bed / respawn-anchor rules), so the crater respects the
 * {@code blockExplosionDropDecay} gamerule and does not rain thousands of item entities. The in-flight overloads and
 * blast shields are static world-object caches, cleared by {@code ServerStateReset} via {@link Hulk#clearSessionState}.
 */
public final class GammaOverload {
	/** v0.16.4 (user): the green flash before the blast (10 s; was a 3-second wind-up). */
	public static final int FLASH_TICKS = 200;
	/** v0.16.4: he drops to his knees 1.5 s into the flash... */
	public static final int KNEEL_AT = 30;
	/** ...and soaks the radiation up for the last 8 s of it. */
	public static final int ABSORB_START = FLASH_TICKS - 160;
	/** v0.16.4: further than this from the reactor and he is not absorbing any more (he cannot walk, but he can be moved). */
	public static final double ABSORB_RANGE = 24.0;
	/** v0.16.4: how far the green flash reaches on screen (clients fade it out with distance). */
	public static final double FLASH_RANGE = 32.0;
	/** Tick (after detonation) of each secondary ring blast, and of the deep after-blast. */
	private static final int RING_START = 4;
	private static final int RING_STEP = 2;
	private static final int RING_COUNT = 8;
	private static final int AFTER_BLAST = RING_START + RING_STEP * RING_COUNT + 4;
	public static final float CORE_POWER = 18.0f;
	public static final float RING_POWER = 9.0f;
	public static final double RING_RADIUS = 14.0;
	public static final float AFTER_POWER = 12.0f;
	/** How long the new Hulk shrugs off explosions and falls (10 s) -- v0.16.4: from the moment he is back from his mind. */
	public static final int SHIELD_TICKS = 200;

	private static final DustParticleOptions GAMMA = new DustParticleOptions(new Vector3f(0.35f, 1.0f, 0.25f), 2.0f);
	private static final DustParticleOptions GAMMA_SMALL = new DustParticleOptions(new Vector3f(0.45f, 1.0f, 0.35f), 1.1f);
	/** v0.16.4: pinned to his knees while he absorbs (and while the Hulk grabs him in his mind). */
	private static final ResourceLocation LOCK_ID = PowerToggles.id("gamma_absorb_lock");
	private static final ResourceLocation JUMP_LOCK_ID = PowerToggles.id("gamma_absorb_jump_lock");

	private static final class Overload {
		/** The absorbing player; null once he is gone (dead, logged out, moved away) -- the reactor still blows. */
		UUID absorber;
		final ResourceKey<Level> dimension;
		final BlockPos reactor;
		final long startedAt;
		/** False only for gametests: everything happens except the explosions themselves. */
		final boolean blast;

		Overload(UUID absorber, ResourceKey<Level> dimension, BlockPos reactor, long startedAt, boolean blast) {
			this.absorber = absorber;
			this.dimension = dimension;
			this.reactor = reactor;
			this.startedAt = startedAt;
			this.blast = blast;
		}
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
		HulkMind.initialize();
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

	/** v0.16.4: raises his blast shield for {@link #SHIELD_TICKS} from now. */
	static void shield(ServerPlayer player) {
		SHIELDED.put(player.getUUID(), player.level().getGameTime() + SHIELD_TICKS);
	}

	public static boolean overloading(BlockPos reactor) {
		return ACTIVE.containsKey(reactor);
	}

	/** v0.16.4: is this player the one absorbing an overloading reactor right now? */
	public static boolean absorbing(ServerPlayer player) {
		for (Overload o : ACTIVE.values()) {
			if (player.getUUID().equals(o.absorber)) {
				return true;
			}
		}
		return false;
	}

	/** Stop an overload before it goes off (tests -- a real blast would flatten the neighbouring gametests). */
	public static void cancel(BlockPos reactor) {
		ACTIVE.remove(reactor);
	}

	/** v0.16.4: {@link #cancel(BlockPos)}, and the absorbing player (if still around) is let go too. */
	public static void cancel(ServerLevel level, BlockPos reactor) {
		Overload o = ACTIVE.remove(reactor);
		ServerPlayer p = o == null || o.absorber == null ? null : level.getServer().getPlayerList().getPlayer(o.absorber);
		if (p != null) {
			release(p);
		}
	}

	/** Client-safe: the absorb state of {@code player}, or null. */
	public static GammaAbsorb absorbState(net.minecraft.world.entity.player.Player player) {
		return player.getAttachedOrElse(ModAttachments.GAMMA_ABSORB, null);
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
		// v0.16.4: one overload per reactor and per player, and never while he is (or is still coming back from) his mind
		if (ACTIVE.containsKey(pos.immutable()) || absorbing(player) || HulkMind.inSession(player)
				|| player.level().dimension() == HulkMind.DIMENSION) {
			return;
		}
		// v0.16.1: projectheroUniquePowers -- someone else is the Hulk: the reactor stays quiet and the dose is kept
		if (com.projecthero.mod.hero.UniquePowers.refuse(player, Hulk.KEY)) {
			return;
		}
		start(player, pos);
	}

	/** Begin the overload (also the test hook). */
	public static void start(ServerPlayer player, BlockPos pos) {
		start(player, pos, true);
	}

	/**
	 * v0.16.4: {@code blast} false (gametests only) runs the whole sequence -- flash, absorbing, the reactor going, the trip
	 * into his mind -- without the explosions.
	 */
	public static void start(ServerPlayer player, BlockPos pos, boolean blast) {
		ServerLevel level = (ServerLevel) player.level();
		long now = level.getGameTime();
		ACTIVE.put(pos.immutable(), new Overload(player.getUUID(), level.dimension(), pos.immutable(), now, blast));
		// v0.16.4: the flash and his absorbing are drawn by every client from this
		player.setAttached(ModAttachments.GAMMA_ABSORB, new GammaAbsorb(pos.immutable(), now));
		Vec3 c = Vec3.atCenterOf(pos);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.BLOCKS, 3.0f, 0.5f);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 3.0f, 0.5f);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 3.0f, 0.5f);
		level.sendParticles(ParticleTypes.FLASH, c.x, c.y + 0.5, c.z, 2, 0.0, 0.0, 0.0, 0.0);
		player.displayClientMessage(Component.translatable("message.projecthero.gamma_reactor.overload")
				.withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD), true);
	}

	/** v0.16.4: pins him in place (attributes sync, so his client stops too) -- or lets him go. */
	static void lockMovement(ServerPlayer player, boolean on) {
		if (on) {
			PowerToggles.modifier(player, Attributes.MOVEMENT_SPEED, LOCK_ID, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
			PowerToggles.modifier(player, Attributes.JUMP_STRENGTH, JUMP_LOCK_ID, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
			player.setSprinting(false);
			if (player.onGround()) {
				Vec3 v = player.getDeltaMovement();
				if (v.x * v.x + v.z * v.z > 1.0e-4) {
					player.setDeltaMovement(0.0, v.y, 0.0);
					player.hurtMarked = true;
				}
			}
		} else {
			PowerToggles.clearModifier(player, Attributes.MOVEMENT_SPEED, LOCK_ID);
			PowerToggles.clearModifier(player, Attributes.JUMP_STRENGTH, JUMP_LOCK_ID);
		}
	}

	/** v0.16.4: he is no longer absorbing (the flash state goes, he can move again). */
	static void release(ServerPlayer player) {
		player.removeAttached(ModAttachments.GAMMA_ABSORB);
		lockMovement(player, false);
	}

	private static void tick(MinecraftServer server) {
		if (ACTIVE.isEmpty()) {
			return;
		}
		for (Overload o : ACTIVE.values()) {
			ServerLevel level = server.getLevel(o.dimension);
			if (level == null) {
				ACTIVE.remove(o.reactor);
				continue;
			}
			long t = level.getGameTime() - o.startedAt;
			Vec3 c = Vec3.atCenterOf(o.reactor);
			if (t < FLASH_TICKS) {
				flash(level, c, t);
				absorb(server, level, o, c, t);
			} else if (t == FLASH_TICKS) {
				detonate(server, level, o, c);
			} else {
				long after = t - FLASH_TICKS;
				if (after >= RING_START && after < RING_START + (long) RING_STEP * RING_COUNT && (after - RING_START) % RING_STEP == 0) {
					int i = (int) ((after - RING_START) / RING_STEP);
					double a = Math.PI * 2.0 * i / RING_COUNT;
					if (o.blast) {
						level.explode(null, c.x + Math.cos(a) * RING_RADIUS, c.y, c.z + Math.sin(a) * RING_RADIUS,
								RING_POWER, false, Level.ExplosionInteraction.BLOCK);
					}
					level.sendParticles(GAMMA, c.x + Math.cos(a) * RING_RADIUS, c.y + 2.0, c.z + Math.sin(a) * RING_RADIUS,
							60, 3.0, 3.0, 3.0, 0.2);
				} else if (after >= AFTER_BLAST) {
					if (o.blast) {
						level.explode(null, c.x, c.y - 4.0, c.z, AFTER_POWER, false, Level.ExplosionInteraction.BLOCK);
						level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y, c.z, 6, 6.0, 3.0, 6.0, 0.0);
					}
					ACTIVE.remove(o.reactor);
				}
			}
		}
	}

	/**
	 * v0.16.4: the 10-second green flash -- green light bursting out of the core in pulses, a haze that thickens, a climbing
	 * whine and heartbeat. (The screen tint and the glow round the core are drawn client-side.)
	 */
	private static void flash(ServerLevel level, Vec3 c, long t) {
		float f = t / (float) FLASH_TICKS;
		// bursts of light flung out of the core
		int n = 4 + (int) (f * 10);
		for (int i = 0; i < n; i++) {
			double u = level.random.nextDouble() * 2.0 - 1.0;
			double a = level.random.nextDouble() * Math.PI * 2.0;
			double s = Math.sqrt(1.0 - u * u);
			level.sendParticles(i % 3 == 0 ? GAMMA : GAMMA_SMALL, c.x, c.y + 0.3, c.z, 0, s * Math.cos(a), Math.abs(u) * 0.6 + 0.1,
					s * Math.sin(a), 0.35 + f * 0.25);
		}
		// a pulse every second: a ring of light racing out along the ground
		if (t % 20 == 0) {
			for (int i = 0; i < 36; i++) {
				double a = Math.PI * 2.0 * i / 36.0;
				level.sendParticles(GAMMA_SMALL, c.x, c.y - 0.3, c.z, 0, Math.cos(a), 0.02, Math.sin(a), 0.6 + f * 0.4);
			}
			level.sendParticles(ParticleTypes.FLASH, c.x, c.y + 0.5, c.z, 1, 0.0, 0.0, 0.0, 0.0);
			level.playSound(null, c.x, c.y, c.z, SoundEvents.WARDEN_HEARTBEAT, SoundSource.BLOCKS, 3.0f, 0.6f + f);
		}
		if (t % 10 == 0) {
			level.playSound(null, c.x, c.y, c.z, SoundEvents.BEACON_AMBIENT, SoundSource.BLOCKS, 3.0f, 0.5f + f * 1.5f);
		}
		if (t == FLASH_TICKS - 34) {
			level.playSound(null, c.x, c.y, c.z, SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.BLOCKS, 4.0f, 0.6f);
		}
		if (t % 3 == 0) {
			level.sendParticles(ParticleTypes.GLOW, c.x, c.y + 0.5, c.z, 2 + (int) (f * 4), 1.2 + f * 2.0, 1.0 + f, 1.2 + f * 2.0, 0.02);
		}
		if (t > FLASH_TICKS - 20 && t % 4 == 0) {
			level.sendParticles(ParticleTypes.END_ROD, c.x, c.y + 0.6, c.z, 12, 0.3, 0.6, 0.3, 0.25);
		}
	}

	/** v0.16.4: the player who started it -- on his knees, the radiation pouring into him. */
	private static void absorb(MinecraftServer server, ServerLevel level, Overload o, Vec3 c, long t) {
		if (o.absorber == null) {
			return;
		}
		ServerPlayer p = server.getPlayerList().getPlayer(o.absorber);
		if (p == null || !p.isAlive() || p.level() != level || p.distanceToSqr(c) > ABSORB_RANGE * ABSORB_RANGE
				|| absorbStateLost(p, o)) {
			// gone (logged out, dead, carried off): the reactor still blows, but nobody takes the radiation in
			if (p != null) {
				release(p);
			}
			o.absorber = null;
			return;
		}
		if (t < KNEEL_AT) {
			return;
		}
		lockMovement(p, true);
		double h = p.getBbHeight();
		if (t == KNEEL_AT) {
			level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.PLAYER_HURT, SoundSource.PLAYERS, 0.9f, 0.6f);
			level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.PLAYER_BREATH, SoundSource.PLAYERS, 1.2f, 0.5f);
			level.sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 0.1, p.getZ(), 10, 0.5, 0.05, 0.5, 0.02);
			p.displayClientMessage(Component.translatable("message.projecthero.gamma_reactor.absorb")
					.withStyle(ChatFormatting.GREEN), true);
		}
		if (t < ABSORB_START) {
			return;
		}
		float f = (t - ABSORB_START) / (float) (FLASH_TICKS - ABSORB_START);
		Vec3 chest = p.position().add(0, h * 0.45, 0);
		// streams of radiation drawn out of the flash into his body
		int n = 3 + Math.round(f * 6);
		for (int i = 0; i < n; i++) {
			double a = level.random.nextDouble() * Math.PI * 2.0;
			double r = 0.4 + level.random.nextDouble() * 1.2;
			Vec3 from = c.add(Math.cos(a) * r, level.random.nextDouble() * 2.0 - 0.5, Math.sin(a) * r);
			Vec3 d = chest.subtract(from);
			// a dust mote coasts about 25x its starting speed (friction 0.96), so this one ends up at his chest
			// v0.16.4 (screenshot check): not to the absorber himself -- in first person every mote converged on his camera
			// and buried the screen; everyone else still sees the streams pour into him
			for (ServerPlayer viewer : level.players()) {
				if (viewer != p) {
					level.sendParticles(viewer, i % 2 == 0 ? GAMMA : GAMMA_SMALL, false, from.x, from.y, from.z, 0, d.x, d.y, d.z, 0.042);
				}
			}
		}
		// the radiation building up in him
		for (ServerPlayer viewer : level.players()) {
			if (viewer != p) { // v0.16.4 (screenshot check): not in his own face (his body glow shows it to him)
				level.sendParticles(viewer, GAMMA_SMALL, false, p.getX(), p.getY() + h * 0.5, p.getZ(), 1 + Math.round(f * 4), 0.3, h * 0.3, 0.3, 0.01);
			}
		}
		if (t % 6 == 0) {
			level.sendParticles(ParticleTypes.HAPPY_VILLAGER, p.getX(), p.getY() + h * 0.55, p.getZ(), 1 + Math.round(f * 3), 0.35, h * 0.3,
					0.35, 0.0);
		}
		long beat = Math.max(8L, 24L - Math.round(f * 16.0f)); // the heart speeds up
		if ((t - ABSORB_START) % beat == 0) {
			level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.2f + f, 0.7f + f * 0.4f);
		}
		if ((t - ABSORB_START) % 40 == 20) {
			level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.PLAYER_BREATH, SoundSource.PLAYERS, 1.0f, 0.5f + f * 0.3f);
		}
	}

	/** His absorb state was replaced (another reactor) or dropped (a respawned player has none). */
	private static boolean absorbStateLost(ServerPlayer p, Overload o) {
		GammaAbsorb s = p.getAttachedOrElse(ModAttachments.GAMMA_ABSORB, null);
		return s == null || !s.reactor().equals(o.reactor);
	}

	private static void detonate(MinecraftServer server, ServerLevel level, Overload o, Vec3 c) {
		ServerPlayer player = o.absorber == null ? null : server.getPlayerList().getPlayer(o.absorber);
		if (player != null && player.level() == level && player.isAlive()) {
			release(player);
			// v0.16.4 (user): the reactor blows and he is pulled into his own mind -- his body stays here, kneeling. He comes
			// back (and changes) once the Hulk in the mirror has him. No mind dimension: he just changes here.
			if (!HulkMind.enter(player)) {
				shield(player);
				awaken(player);
			}
		}
		level.setBlock(o.reactor, Blocks.AIR.defaultBlockState(), 3);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 12.0f, 0.5f);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.BLOCKS, 12.0f, 0.5f);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 12.0f, 0.6f);
		if (o.blast) {
			level.explode(null, c.x, c.y, c.z, CORE_POWER, false, Level.ExplosionInteraction.BLOCK);
			level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y + 1.0, c.z, 10, 5.0, 4.0, 5.0, 0.0);
		}
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
	}

	/**
	 * v0.16.4 (user): "the player slowly transforms into the hulk" -- the end of the origin. The dose is spent, the Gamma
	 * power granted (it becomes his Primary power, as before) and the slow, unwilling change begins: on his knees, the Hulk
	 * phasing onto him, the roar as he stands ({@link Hulk#transform} forced). His rage is left just under 50, so he is
	 * tamed -- the player's own Hulk -- the moment he is on his feet. If the grant is refused (projectheroUniquePowers:
	 * someone else became the Hulk meanwhile) the dose goes back into his blood.
	 */
	static void awaken(ServerPlayer player) {
		setDosed(player, false);
		if (!Hulk.grant(player) && !Hulk.hasPower(player)) {
			setDosed(player, true);
			return;
		}
		player.displayClientMessage(Component.translatable("message.projecthero.gamma_reactor.survived")
				.withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD), false);
		Hulk.transform(player, true);
		Hulk.setRage(player, HulkConfig.MANUAL_TRANSFORM_RAGE - 1.0f);
	}
}
