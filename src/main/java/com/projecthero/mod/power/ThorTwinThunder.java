package com.projecthero.mod.power;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.network.TitanShakePayload;
import com.projecthero.mod.titanshifter.TitanCombat;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.1: "Twin Thunder Strike", the Mjolnir + Stormbreaker dual-wield combo's finisher ({@link WeaponCombo}). The
 * 4th hit, landed inside the window, queues it ({@link #queue}) -- only while wielding both and off its cooldown
 * ({@link ThorConfig.TwinThunder#cooldownTicks}, 20 s). Once the leaping slam has come down ({@link #DELAY} ticks
 * later) it plays out on the server clock, synced to every viewer as {@link ThorFx#ANIM_TWIN_THUNDER}:
 * <ol>
 *   <li>tick 0: both weapons raised high;</li>
 *   <li>tick {@link #BOLT_TICK}: a bolt from the sky onto each of them (visual-only: Thor is immune and nothing burns),
 *       lightning arcing between the two (client);</li>
 *   <li>tick {@link #SLAM_TICK}: slammed together into the ground -- {@link ThorConfig.TwinThunder#damage} (20) to every
 *       enemy within {@link ThorConfig.TwinThunder#radius} (6) blocks, knocked up and out (bosses aren't moved), a
 *       ground-crack ring and a screen shake for players nearby;</li>
 *   <li>tick {@link #RING_TICK}: a ring of {@link ThorConfig.TwinThunder#ringBolts} bolts round him at
 *       {@link ThorConfig.TwinThunder#ringRadius}, each hurting the threats near it (rule 2: hostile only).</li>
 * </ol>
 * Never the wielder, his pets, squad allies or their pets ({@link WeaponCombo#friendly}); the storm buff applies through
 * the usual hook. Pending strikes are static, keyed by player UUID, ticked once per server tick and cleared by
 * {@code ServerStateReset}.
 */
public final class ThorTwinThunder {
	/** Ticks from the 4th hit to the strike (the hop comes down). */
	public static final int DELAY = 12;
	public static final int BOLT_TICK = 8;
	public static final int SLAM_TICK = 16;
	public static final int RING_TICK = 19;
	public static final int END_TICK = 30;

	private static final class Pending {
		final long start;
		final Vec3 center;

		Pending(long start, Vec3 center) {
			this.start = start;
			this.center = center;
		}
	}

	private static final Map<UUID, Pending> PENDING = new HashMap<>();

	private ThorTwinThunder() {
	}

	public static void initialize() {
		ServerTickEvents.END_SERVER_TICK.register(ThorTwinThunder::tick);
	}

	/** The 4th dual-wield hit landed in the window: queue the strike if he can. */
	public static void queue(ServerPlayer player) {
		if (!WeaponCombo.isDualWielding(player) || PENDING.containsKey(player.getUUID())) {
			return;
		}
		long now = player.level().getGameTime();
		var cds = player.getAttachedOrCreate(ModAttachments.COOLDOWNS);
		if (!cds.isReady(ThorAbility.TWIN_THUNDER, now)) {
			int secs = Mth.ceil(cds.remainingTicks(ThorAbility.TWIN_THUNDER, now) / 20.0f);
			player.displayClientMessage(Component.translatable("projecthero.thor.twin_thunder.recharging", secs), true);
			return;
		}
		ThorPowers.triggerCooldown(player, ThorAbility.TWIN_THUNDER, ThorConfig.get().twinThunder.cooldownTicks);
		PENDING.put(player.getUUID(), new Pending(now + DELAY, null));
	}

	/** Is a Twin Thunder Strike queued or playing for {@code player}? For tests / HUD. */
	public static boolean isActive(ServerPlayer player) {
		return PENDING.containsKey(player.getUUID());
	}

	private static void tick(MinecraftServer server) {
		if (PENDING.isEmpty()) {
			return;
		}
		Map<UUID, Pending> updates = new HashMap<>();
		Iterator<Map.Entry<UUID, Pending>> it = PENDING.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, Pending> e = it.next();
			ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
			Pending p = e.getValue();
			if (player == null || !player.isAlive() || player.isRemoved() || player.isSpectator()) {
				it.remove();
				continue;
			}
			long t = player.level().getGameTime() - p.start;
			if (t < 0) {
				continue;
			}
			if (t == 0) {
				if (!WeaponCombo.isDualWielding(player)) { // put a weapon away mid-hop: no strike (the cooldown stands)
					it.remove();
					continue;
				}
				Vec3 c = player.position();
				updates.put(e.getKey(), new Pending(p.start, c));
				raise(player);
				continue;
			}
			Vec3 center = p.center != null ? p.center : player.position();
			if (t == BOLT_TICK) {
				boltsOnWeapons(player);
			} else if (t == SLAM_TICK) {
				// slam where he is now (he may have stepped), and remember it for the ring
				center = player.position();
				updates.put(e.getKey(), new Pending(p.start, center));
				slam(player, center);
			} else if (t == RING_TICK) {
				ring(player, center);
			} else if (t >= END_TICK) {
				it.remove();
			}
		}
		updates.forEach((k, v) -> {
			if (PENDING.containsKey(k)) {
				PENDING.put(k, v);
			}
		});
	}

	private static void raise(ServerPlayer player) {
		ThorVisuals.anim(player, ThorFx.ANIM_TWIN_THUNDER, player.position());
		ServerLevel level = player.serverLevel();
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.LIGHTNING_BOLT_THUNDER,
				SoundSource.PLAYERS, 0.6f, 0.6f);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_POWER_SELECT,
				SoundSource.PLAYERS, 0.6f, 1.4f);
	}

	/** Where the two raised weapon heads are (both sides of the head, high), from the body's facing. */
	public static Vec3[] weaponHeads(ServerPlayer player) {
		float yaw = player.yBodyRot * Mth.DEG_TO_RAD;
		Vec3 right = new Vec3(-Mth.cos(yaw), 0.0, -Mth.sin(yaw));
		Vec3 top = player.position().add(0.0, player.getBbHeight() + 0.75, 0.0);
		return new Vec3[] { top.add(right.scale(0.55)), top.add(right.scale(-0.55)) };
	}

	private static void boltsOnWeapons(ServerPlayer player) {
		ServerLevel level = player.serverLevel();
		for (Vec3 head : weaponHeads(player)) {
			visualBolt(level, player, head);
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, head.x, head.y, head.z, 24, 0.2, 0.2, 0.2, 0.25);
			level.sendParticles(ParticleTypes.FLASH, head.x, head.y, head.z, 1, 0.0, 0.0, 0.0, 0.0);
		}
	}

	private static void slam(ServerPlayer player, Vec3 center) {
		ServerLevel level = player.serverLevel();
		ThorConfig.TwinThunder cfg = ThorConfig.get().twinThunder;
		for (LivingEntity e : AbilityHelpers.enemiesAround(player, center.add(0, 0.5, 0), cfg.radius)) {
			if (WeaponCombo.friendly(player, e)) {
				continue;
			}
			if (AbilityHelpers.hurt(player, e, (float) cfg.damage)) {
				knock(e, center, cfg.knockback, cfg.knockUp);
			}
		}
		// the crack: rings of debris from the block underfoot, sparks and a flash
		BlockPos below = BlockPos.containing(center.x, center.y - 0.2, center.z);
		BlockState ground = level.getBlockState(below);
		if (ground.isAir()) {
			ground = net.minecraft.world.level.block.Blocks.STONE.defaultBlockState();
		}
		BlockParticleOption debris = new BlockParticleOption(ParticleTypes.BLOCK, ground);
		for (int r = 1; r <= (int) cfg.radius; r++) {
			int points = 10 + r * 6;
			for (int i = 0; i < points; i++) {
				double a = Math.PI * 2 * i / points;
				level.sendParticles(debris, center.x + Math.cos(a) * r, center.y + 0.1, center.z + Math.sin(a) * r, 2,
						0.15, 0.05, 0.15, 0.15);
			}
		}
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, center.x, center.y + 0.3, center.z, 60, cfg.radius * 0.35, 0.2,
				cfg.radius * 0.35, 0.3);
		level.sendParticles(ParticleTypes.EXPLOSION, center.x, center.y + 0.4, center.z, 3, 0.8, 0.1, 0.8, 0.0);
		level.sendParticles(ParticleTypes.FLASH, center.x, center.y + 0.5, center.z, 1, 0.0, 0.0, 0.0, 0.0);
		level.playSound(null, center.x, center.y, center.z, SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS,
				1.2f, 0.7f);
		level.playSound(null, center.x, center.y, center.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 1.2f,
				0.8f);
		level.playSound(null, center.x, center.y, center.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.5f,
				1.3f);
		shake(level, center, 1.4f, 14, 24.0);
	}

	private static void ring(ServerPlayer player, Vec3 center) {
		ServerLevel level = player.serverLevel();
		ThorConfig.TwinThunder cfg = ThorConfig.get().twinThunder;
		int n = Math.max(0, cfg.ringBolts);
		double phase = (player.getYRot() * Mth.DEG_TO_RAD);
		java.util.Set<LivingEntity> hit = new java.util.HashSet<>();
		for (int i = 0; i < n; i++) {
			double a = phase + Math.PI * 2 * i / n;
			Vec3 at = center.add(Math.cos(a) * cfg.ringRadius, 0.0, Math.sin(a) * cfg.ringRadius);
			at = groundAt(level, at);
			visualBolt(level, player, at);
			for (LivingEntity e : ThorTargets.hostilesInRadius(level, player, at, cfg.ringSplash)) {
				if (!WeaponCombo.friendly(player, e) && hit.add(e) && AbilityHelpers.hurt(player, e, (float) cfg.ringDamage)) {
					knock(e, at, 0.4, 0.3);
				}
			}
		}
	}

	/** The top of the ground at (x, z) near {@code at} (up to 4 blocks either way), else {@code at}. */
	static Vec3 groundAt(ServerLevel level, Vec3 at) {
		BlockPos.MutableBlockPos m = BlockPos.containing(at.x, at.y + 4, at.z).mutable();
		for (int i = 0; i < 9; i++) {
			BlockPos below = m.below();
			if (!level.getBlockState(below).getCollisionShape(level, below).isEmpty()
					&& level.getBlockState(m).getCollisionShape(level, m).isEmpty()) {
				return new Vec3(at.x, m.getY(), at.z);
			}
			m.move(0, -1, 0);
		}
		return at;
	}

	static void visualBolt(ServerLevel level, ServerPlayer cause, Vec3 at) {
		LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
		if (bolt != null) {
			bolt.moveTo(at.x, at.y, at.z);
			bolt.setVisualOnly(true);
			bolt.setCause(cause);
			level.addFreshEntity(bolt);
		}
	}

	private static void knock(LivingEntity target, Vec3 origin, double strength, double lift) {
		if (TitanCombat.isBoss(target) || !target.isAlive()) {
			return;
		}
		AbilityHelpers.knockbackFrom(target, origin, strength);
		if (lift > 0.0) {
			AbilityHelpers.push(target, new Vec3(0.0, lift, 0.0));
		}
	}

	/** A screen shake (the Titan / Hulk one) for every player within {@code radius}, fading with distance. */
	static void shake(ServerLevel level, Vec3 at, float intensity, int ticks, double radius) {
		double r2 = radius * radius;
		for (ServerPlayer p : level.players()) {
			double d2 = p.distanceToSqr(at);
			if (d2 <= r2 && ServerPlayNetworking.canSend(p, TitanShakePayload.TYPE)) {
				ServerPlayNetworking.send(p, new TitanShakePayload(intensity * (float) (1.0 - Math.sqrt(d2 / r2) * 0.8), ticks));
			}
		}
	}

	/** World unload: drop every queued strike. */
	public static void clearSessionState() {
		PENDING.clear();
	}
}
