package com.projecthero.mod.titanshifter;

import com.projecthero.mod.combat.HeroTargets;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.titanshifter.data.TitanShifterState;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.7 (user, Titan revamp): Partial Shifting -- the human form's kit. Each move costs a little Steam (Bite Shift needs
 * the whole bar) and has its own recharge; each sets the synced pose ({@link #POSE_BASE} + the move's ordinal in
 * {@code TitanShifterState#lastAction}, its start in {@code lastActionTick}) the client poses the player's arms with.
 *
 * <pre>
 *   R        Titan Arm       a giant Titan arm bursts out of the shoulder and punches (16, 6 blocks, a huge shove)
 *   Shift+R  Crystal Blade   a hardened crystal blade along the forearm slashes in front (12)
 *   G        Steam Burst     a scalding cloud bursts off the body (4 and a strong knockback, 5 blocks)
 *   Shift+G  Bite Shift      bite the hand -- an instant (0.6 s) shift, needs the full bar
 *   X        Crystal Wall    a 4x4 wall of crystal two blocks ahead (25 s; the mod's conjured-terrain rules)
 *   Shift+X  Crystal Cocoon  encased for 5 s: untouchable, rooted, healing fast
 * </pre>
 */
public final class TitanHumanKit {
	/** lastAction values from here up are human-form poses (TitanMove ordinal added). */
	public static final int POSE_BASE = 1000;

	private TitanHumanKit() {
	}

	/** Delayed hits (the impact frame of each pose), per shifter; run from {@link #tick}. */
	private record Task(long due, Runnable run) {
	}

	private static final java.util.Map<java.util.UUID, java.util.List<Task>> TASKS = new java.util.HashMap<>();

	public static void clearSessionState() {
		TASKS.clear();
	}

	private static void later(ServerPlayer player, int ticks, Runnable run) {
		TASKS.computeIfAbsent(player.getUUID(), k -> new java.util.ArrayList<>())
				.add(new Task(player.level().getGameTime() + Math.max(0, ticks), run));
	}

	private static void runTasks(ServerPlayer player) {
		java.util.List<Task> list = TASKS.get(player.getUUID());
		if (list == null) {
			return;
		}
		long now = player.level().getGameTime();
		for (Task t : new java.util.ArrayList<>(list)) {
			if (now >= t.due()) {
				list.remove(t);
				if (now - t.due() <= 20) {
					t.run().run(); // a stale one (the shifter was away in a Titan) is just dropped
				}
			}
		}
		if (list.isEmpty()) {
			TASKS.remove(player.getUUID());
		}
	}

	public static void handle(ServerPlayer player, int slot, boolean shift) {
		TitanMove move = TitanMove.of(null, slot, shift);
		if (move == null) {
			return;
		}
		switch (move) {
			case TITAN_ARM -> titanArm(player);
			case CRYSTAL_BLADE -> crystalBlade(player);
			case STEAM_BURST -> steamBurst(player);
			case BITE_SHIFT -> biteShift(player);
			case CRYSTAL_WALL -> crystalWall(player);
			case CRYSTAL_COCOON -> cocoon(player);
			default -> {
			}
		}
	}

	/** Cooldown + Steam check and spend, and the pose. False (with feedback) when the move cannot fire. */
	private static boolean start(ServerPlayer player, TitanMove move, double steam) {
		if (!player.isAlive() || player.isSpectator() || TitanShifter.cocooned(player)) {
			return false;
		}
		int cd = TitanShifter.cooldownRemaining(player, move.id);
		if (cd > 0) {
			TitanShifter.say(player, "message.projecthero.titan_shifter.ability_cooldown", ChatFormatting.RED,
					TitanAbilities.Component_translatable(move.id), String.format(java.util.Locale.ROOT, "%.1f", cd / 20.0));
			return false;
		}
		if (steam > 0.0 && !TitanShifter.spendSteam(player, (float) steam)) {
			TitanShifter.say(player, "message.projecthero.titan_shifter.low_steam", ChatFormatting.RED, (int) Math.ceil(steam));
			return false;
		}
		TitanShifter.startCooldown(player, move.id, Math.max(0, move.cooldown()), POSE_BASE + move.ordinal());
		return true;
	}

	private static final DustParticleOptions FLESH = new DustParticleOptions(new org.joml.Vector3f(0.75f, 0.35f, 0.3f), 0.9f);
	private static final DustParticleOptions CRYSTAL = new DustParticleOptions(new org.joml.Vector3f(0.7f, 0.9f, 1.0f), 1.2f);

	/** R: Titan Arm -- the arm swells into a Titan's for one punch down the look. */
	public static void titanArm(ServerPlayer player) {
		var h = TitanShifterConfig.human();
		if (!start(player, TitanMove.TITAN_ARM, h.titanArmSteam)) {
			return;
		}
		ServerLevel level = (ServerLevel) player.level();
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.6f, 1.8f);
		Vec3 eye = player.getEyePosition();
		level.sendParticles(TitanShifter.YELLOW_SPARK, eye.x, eye.y - 0.4, eye.z, 20, 0.4, 0.4, 0.4, 0.2);
		later(player, TitanMove.TITAN_ARM.impactTick, () -> {
			if (!player.isAlive() || TitanShifter.phase(player) != TitanPhase.HUMAN) {
				return;
			}
			Vec3 from = player.getEyePosition().add(0, -0.3, 0);
			Vec3 to = from.add(player.getLookAngle().scale(h.titanArmReach));
			int n = 0;
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(from, to).inflate(1.6),
					e -> e != player && HeroTargets.canHarm(player, e))) {
				AABB grown = e.getBoundingBox().inflate(1.2);
				if (grown.contains(from) || grown.clip(from, to).isPresent()) {
					if (AbilityHelpers.hurtBurst(player, e, cap(e, (float) h.titanArmDamage))) {
						n++;
						if (!TitanCombat.isBoss(e)) {
							AbilityHelpers.knockbackFrom(e, player.position(), 2.0);
							AbilityHelpers.push(e, new Vec3(0, 0.35, 0));
						}
					}
				}
			}
			// the arm: a line of flesh and steam out to the fist, an impact at the end
			for (int i = 1; i <= 8; i++) {
				Vec3 p = from.lerp(to, i / 8.0);
				level.sendParticles(FLESH, p.x, p.y, p.z, 3, 0.25, 0.25, 0.25, 0.0);
				level.sendParticles(ParticleTypes.CLOUD, p.x, p.y, p.z, 1, 0.3, 0.3, 0.3, 0.02);
			}
			level.sendParticles(ParticleTypes.EXPLOSION, to.x, to.y, to.z, 1, 0, 0, 0, 0);
			level.playSound(null, to.x, to.y, to.z, SoundEvents.IRON_GOLEM_ATTACK, SoundSource.PLAYERS, 2.0f, n > 0 ? 0.5f : 0.7f);
			level.playSound(null, to.x, to.y, to.z, SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 1.0f, 0.6f);
		});
	}

	/** Shift+R: Crystal Blade -- a hardened forearm blade slashes the front arc. */
	public static void crystalBlade(ServerPlayer player) {
		var h = TitanShifterConfig.human();
		if (!start(player, TitanMove.CRYSTAL_BLADE, h.bladeSteam)) {
			return;
		}
		ServerLevel level = (ServerLevel) player.level();
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_CLUSTER_PLACE, SoundSource.PLAYERS, 1.5f, 1.2f);
		later(player, TitanMove.CRYSTAL_BLADE.impactTick, () -> {
			if (!player.isAlive()) {
				return;
			}
			Vec3 look = player.getLookAngle();
			Vec3 c = player.position().add(0, 1.0, 0);
			for (LivingEntity e : AbilityHelpers.living(level, c, h.bladeReach + 0.5, e -> e != player && HeroTargets.canHarm(player, e))) {
				Vec3 to = e.position().add(0, e.getBbHeight() * 0.5, 0).subtract(c);
				if (to.lengthSqr() < 1.0e-4 || to.normalize().dot(look) > 0.25) {
					if (AbilityHelpers.hurtBurst(player, e, cap(e, (float) h.bladeDamage)) && !TitanCombat.isBoss(e)) {
						AbilityHelpers.knockbackFrom(e, player.position(), 0.6);
					}
				}
			}
			Vec3 sweep = c.add(look.scale(1.8));
			level.sendParticles(ParticleTypes.SWEEP_ATTACK, sweep.x, sweep.y, sweep.z, 2, 0.4, 0.2, 0.4, 0.0);
			level.sendParticles(CRYSTAL, sweep.x, sweep.y, sweep.z, 16, 0.8, 0.3, 0.8, 0.0);
			level.playSound(null, sweep.x, sweep.y, sweep.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.2f, 1.4f);
		});
	}

	/** G: Steam Burst -- a scalding cloud knocks everything near away. */
	public static void steamBurst(ServerPlayer player) {
		var h = TitanShifterConfig.human();
		if (!start(player, TitanMove.STEAM_BURST, h.steamBurstSteam)) {
			return;
		}
		ServerLevel level = (ServerLevel) player.level();
		Vec3 c = player.position().add(0, 1.0, 0);
		level.sendParticles(ParticleTypes.CLOUD, c.x, c.y, c.z, 80, h.steamBurstRadius * 0.4, 0.6, h.steamBurstRadius * 0.4, 0.25);
		level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, c.x, c.y, c.z, 25, 1.0, 0.6, 1.0, 0.08);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 2.0f, 0.5f);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXTINGUISH_FIRE, SoundSource.PLAYERS, 1.5f, 0.6f);
		for (LivingEntity e : AbilityHelpers.living(level, c, h.steamBurstRadius, e -> e != player && HeroTargets.canHarm(player, e))) {
			AbilityHelpers.hurtBurst(player, e, cap(e, (float) h.steamBurstDamage));
			if (!TitanCombat.isBoss(e)) {
				AbilityHelpers.knockbackFrom(e, c, 1.6);
				AbilityHelpers.push(e, new Vec3(0, 0.3, 0));
			}
		}
	}

	/** Shift+G: Bite Shift -- the bite through the hand: an instant shift with the full bar (lightning and all). */
	public static void biteShift(ServerPlayer player) {
		TitanShifterState s = TitanShifter.state(player);
		if (s.energy + 1.0e-3f < TitanShifter.energyNeeded()) {
			TitanShifter.say(player, "message.projecthero.titan_shifter.bite_shift_needs_full", ChatFormatting.RED);
			return;
		}
		ServerLevel level = (ServerLevel) player.level();
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.2f, 1.4f);
		if (TitanShifter.transform(player, false, Math.max(1, TitanShifterConfig.human().biteShiftTicks))) {
			TitanShifter.startCooldown(player, TitanMove.BITE_SHIFT.id, 0, POSE_BASE + TitanMove.BITE_SHIFT.ordinal());
		}
	}

	/** X: Crystal Wall -- conjured crystal (the mod's conjured-structure rules: terrain damage must be on; mined it drops nothing). */
	public static void crystalWall(ServerPlayer player) {
		var h = TitanShifterConfig.human();
		if (!com.projecthero.mod.hero.HeroConfig.get().abilityTerrainDamage) {
			TitanShifter.say(player, "message.projecthero.titan_shifter.wall_off", ChatFormatting.GRAY);
			return;
		}
		if (!start(player, TitanMove.CRYSTAL_WALL, h.wallSteam)) {
			return;
		}
		ServerLevel level = (ServerLevel) player.level();
		if (!com.projecthero.mod.hero.power.ConjuredStructures.wall(player, level, Blocks.AMETHYST_BLOCK.defaultBlockState())) {
			TitanShifter.say(player, "message.projecthero.titan_shifter.wall_blocked", ChatFormatting.GRAY);
			return;
		}
		Vec3 front = player.position().add(Vec3.atLowerCornerOf(player.getDirection().getNormal()).scale(2.0)).add(0, 1.5, 0);
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.AMETHYST_BLOCK.defaultBlockState()), front.x, front.y, front.z,
				60, 1.5, 1.2, 1.5, 0.1);
		level.sendParticles(ParticleTypes.CLOUD, front.x, front.y - 1.0, front.z, 20, 1.5, 0.3, 1.5, 0.05);
		level.playSound(null, front.x, front.y, front.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 2.0f, 0.6f);
		level.playSound(null, front.x, front.y, front.z, SoundEvents.AMETHYST_CLUSTER_PLACE, SoundSource.PLAYERS, 2.0f, 0.5f);
	}

	/** Shift+X: Crystal Cocoon -- 5 s encased: untouchable (TitanShifterDamage), rooted, fast healing. */
	public static void cocoon(ServerPlayer player) {
		var h = TitanShifterConfig.human();
		if (!start(player, TitanMove.CRYSTAL_COCOON, h.cocoonSteam)) {
			return;
		}
		long until = player.level().getGameTime() + h.cocoonTicks;
		TitanShifterState n = TitanShifter.state(player).copy();
		n.abilityReadyAt.put(TitanShifter.COCOON_KEY, until);
		TitanShifter.save(player, n);
		player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, h.cocoonTicks, 3, false, false, true));
		player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, h.cocoonTicks, 9, false, false, false));
		player.setDeltaMovement(Vec3.ZERO);
		player.hurtMarked = true;
		ServerLevel level = (ServerLevel) player.level();
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 2.0f, 0.4f);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GLASS_PLACE, SoundSource.PLAYERS, 2.0f, 0.5f);
	}

	/** Per tick in human form: the cocoon's crystal shell, and its end. */
	static void tick(ServerPlayer player) {
		runTasks(player);
		TitanShifterState s = TitanShifter.state(player);
		Long until = s.abilityReadyAt.get(TitanShifter.COCOON_KEY);
		if (until == null) {
			return;
		}
		long now = player.level().getGameTime();
		ServerLevel level = (ServerLevel) player.level();
		if (now >= until) {
			TitanShifterState n = s.copy();
			n.abilityReadyAt.remove(TitanShifter.COCOON_KEY);
			TitanShifter.save(player, n);
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.AMETHYST_BLOCK.defaultBlockState()),
					player.getX(), player.getY() + 1.0, player.getZ(), 50, 0.6, 0.9, 0.6, 0.2);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.PLAYERS, 2.0f, 0.6f);
			return;
		}
		player.setDeltaMovement(0, Math.min(0, player.getDeltaMovement().y), 0);
		if (player.tickCount % 2 == 0) {
			// a shell of crystal round the body
			for (int i = 0; i < 10; i++) {
				double a = player.getRandom().nextDouble() * Math.PI * 2.0;
				double y = player.getRandom().nextDouble() * 2.1;
				level.sendParticles(CRYSTAL, player.getX() + Math.cos(a) * 0.75, player.getY() + y, player.getZ() + Math.sin(a) * 0.75, 1, 0, 0, 0, 0);
			}
			level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 1.0, player.getZ(), 1, 0.4, 0.6, 0.4, 0.01);
		}
	}

	/** Bosses lose at most the Titan cap per hit from these, too. */
	private static float cap(LivingEntity e, float dmg) {
		return TitanCombat.isBoss(e) ? Math.min(dmg, e.getMaxHealth() * (float) TitanShifterConfig.damage().bossMaxFractionPerHit) : dmg;
	}
}
