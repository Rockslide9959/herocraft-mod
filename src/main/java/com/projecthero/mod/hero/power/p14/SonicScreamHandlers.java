package com.projecthero.mod.hero.power.p14;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.hero.AbilityContext;
import com.projecthero.mod.hero.AbilityHandler;
import com.projecthero.mod.hero.AbilityHandlers;
import com.projecthero.mod.hero.ExperimentalPowers;
import com.projecthero.mod.hero.Power;
import com.projecthero.mod.hero.Powers;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.hero.power.Handlers;
import com.projecthero.mod.hero.revamp.BatchCFx;
import com.projecthero.mod.hero.visual.MutationVisuals;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Power 14 -- Sonic Scream (v0.13.22 revamp).
 *
 * <h2>Signature: the Voice bar and visible sound</h2>
 * Every scream spends <b>Voice</b> ({@value #VOICE_MAX}); it comes back quickly once you have been quiet for
 * 1.5 s. Every scream is drawn as Warden-style rings of sound travelling along its path, so everyone can see where
 * the sound went.
 *
 * <p>Enhanced hearing (client-side: anything within 40 blocks flashes for the wielder the moment it moves) and the
 * Shift + right-click sonar ping stay as passives. C Echolocation keeps the same client glow (the synced
 * {@code echo_until} window read by {@code EntityGlowMixin}), now pinged automatically every two seconds while the
 * mode is on.
 */
public final class SonicScreamHandlers {
	public static final String KEY = "power_14_sonic_scream";

	public static final float VOICE_MAX = 115.0f;
	/** Voice regained per second once quiet. */
	private static final float VOICE_REGEN = 16.0f;
	private static final int QUIET_TICKS = 30;

	public static final float COST_BLAST = 16.0f;
	public static final float COST_FOCUSED = 24.0f;
	private static final float COST_JUMP = 12.0f;
	public static final float COST_SUPERSONIC = 55.0f;
	private static final float COST_RESONANCE = 18.0f;
	private static final float COST_PING = 4.0f;
	public static final float COST_BARRIER = 30.0f;
	public static final float COST_DISORIENT = 20.0f;

	public static final float BLAST_DAMAGE = 12.0f;
	public static final float FOCUSED_DAMAGE = 19.0f;
	private static final float SUPERSONIC_CONE = 60.0f;
	private static final float SUPERSONIC_RADIAL = 48.0f;
	private static final int SUPERSONIC_CHARGE = 85;

	/** Pale cyan: the colour of this power's sound rings. */
	static final int SOUND = 0x9FF6FF;

	/** A live Sound Barrier. */
	public static final class Barrier {
		final ServerLevel level;
		public final Vec3 centre;
		final Vec3 normal;
		final Vec3 side;
		final long expires;

		Barrier(ServerLevel level, Vec3 centre, Vec3 normal, long expires) {
			this.level = level;
			this.centre = centre;
			this.normal = normal;
			this.side = new Vec3(-normal.z, 0, normal.x);
			this.expires = expires;
		}

		/** Whether {@code pos} lies inside the wall slab (5 wide, 3.5 tall, 1.8 thick). */
		public boolean contains(Vec3 pos) {
			Vec3 rel = pos.subtract(centre);
			double n = rel.dot(normal);
			double s = rel.dot(side);
			return Math.abs(n) <= 0.9 && Math.abs(s) <= 2.6 && rel.y >= -1.4 && rel.y <= 2.4;
		}
	}

	private static final Map<UUID, Barrier> BARRIERS = new ConcurrentHashMap<>();
	/** entityId -> game time its Disorient wears off (the mob keeps losing its target until then). */
	private static final Map<Integer, Long> DISORIENTED = new ConcurrentHashMap<>();
	private static final Map<Integer, ServerLevel> DISORIENTED_LEVEL = new ConcurrentHashMap<>();

	private SonicScreamHandlers() {
	}

	private static Power power() {
		return Powers.byKey(KEY);
	}

	/** True while Echolocation (C) is on -- read from the synced attachment. */
	public static boolean echoActive(Player p) {
		var st = p.getAttachedOrElse(com.projecthero.mod.attachment.ModAttachments.EXPERIMENTAL_STATE, null);
		return st != null && st.ownedPowers.contains(KEY) && st.activeToggles.contains(KEY + "/echolocation");
	}

	/** Range multiplier while Echolocation is running: you hear exactly where everything is. */
	public static double sensesRange(Player p, double base) {
		return echoActive(p) ? base * 1.3 : base;
	}

	// ---- Voice ------------------------------------------------------------------------------

	private static void seed(AbilityContext ctx) {
		if (!ExperimentalPowers.state(ctx.player()).resources.containsKey(KEY + "/voice")) {
			ctx.setResource("voice", VOICE_MAX, VOICE_MAX);
		}
	}

	/** Spend Voice (and reset the quiet timer), or refuse with a message. */
	public static boolean spendVoice(AbilityContext ctx, float amount) {
		seed(ctx);
		if (ctx.resource("voice") < amount) {
			ctx.actionBar("message.projecthero.sonic.voice_low");
			return false;
		}
		ctx.addResource("voice", -amount, VOICE_MAX);
		ctx.setResource("quiet_at", ctx.player().level().getGameTime() + QUIET_TICKS, 1.0e12f);
		return true;
	}

	// ---- visuals ----------------------------------------------------------------------------

	private static ParticleOptions ringDust(float size) {
		return BatchCFx.dust(SOUND, size);
	}

	/** The Warden-style look of a scream: SONIC_BOOM pulses plus pale rings growing along the path. */
	private static void soundTrail(ServerLevel level, Vec3 origin, Vec3 dir, double length, double r0, double grow, double boomStep) {
		BatchCFx.ringTrail(level, origin, dir, length, 1.25, r0, grow, ringDust(0.9f));
		Vec3 d = dir.normalize();
		for (double s = boomStep; s <= length; s += boomStep) {
			Vec3 pt = origin.add(d.scale(s));
			level.sendParticles(ParticleTypes.SONIC_BOOM, pt.x, pt.y, pt.z, 1, 0, 0, 0, 0);
		}
	}

	private static Vec3 mouth(ServerPlayer p) {
		return p.getEyePosition().add(p.getLookAngle().scale(0.6)).add(0, -0.15, 0);
	}

	// ---- registration -----------------------------------------------------------------------

	public static void register() {
		// R -- Sonic Blast: a short cone of sound.
		AbilityHandlers.register(KEY, "sonic_blast", Handlers.instant(ctx -> {
			if (!spendVoice(ctx, COST_BLAST)) {
				return;
			}
			ServerPlayer p = ctx.player();
			double range = sensesRange(p, 5.0);
			for (LivingEntity e : AbilityHelpers.enemiesAround(p, p.getEyePosition().add(p.getLookAngle().scale(range * 0.6)),
					range * 0.7)) {
				if (inCone(p, e, 0.5, range + 1.0)) {
					AbilityHelpers.hurt(p, e, BLAST_DAMAGE);
					AbilityHelpers.knockbackFrom(e, p.position(), 1.5);
					staticStun(e);
				}
			}
			soundTrail(ctx.level(), mouth(p), p.getLookAngle(), range, 0.3, 0.35, 2.5);
			MutationVisuals.play(p, "p14.shout");
			AbilityHelpers.sound(p, SoundEvents.WARDEN_SONIC_BOOM, 0.7f, 1.6f);
			ctx.triggerCooldown();
		}));

		// G -- Focused Scream: a 30-block beam of sound.
		AbilityHandlers.register(KEY, "focused_scream", Handlers.instant(ctx -> {
			if (!spendVoice(ctx, COST_FOCUSED)) {
				return;
			}
			ServerPlayer p = ctx.player();
			double range = sensesRange(p, 30.0);
			LivingEntity t = AbilityHelpers.raycastEntity(p, range);
			Vec3 end = AbilityHelpers.aimPoint(p, range);
			double len = mouth(p).distanceTo(end);
			soundTrail(ctx.level(), mouth(p), end.subtract(mouth(p)), len, 0.35, 0.02, 1.5);
			if (t != null) {
				AbilityHelpers.hurt(p, t, FOCUSED_DAMAGE);
				AbilityHelpers.knockbackFrom(t, p.position(), 1.0);
				AbilityHelpers.applyControl(t, MobEffects.CONFUSION, 80, 0);
				staticStun(t);
			}
			MutationVisuals.play(p, "p14.focused");
			AbilityHelpers.sound(p, SoundEvents.WARDEN_SONIC_BOOM, 1.0f, 1.0f);
			ctx.triggerCooldown();
		}));

		// X -- Sonic Jump: blast the ground away beneath you. Also boosts an open elytra.
		AbilityHandlers.register(KEY, "sonic_jump", Handlers.instant(ctx -> {
			if (!spendVoice(ctx, COST_JUMP)) {
				return;
			}
			ServerPlayer p = ctx.player();
			ServerLevel level = ctx.level();
			if (p.isFallFlying()) {
				AbilityHelpers.addImpulse(p, p.getLookAngle().scale(1.7).add(0, 0.25, 0));
				BatchCFx.ring(level, p.position().add(0, 0.5, 0), p.getLookAngle(), 1.2, 18, ringDust(1.0f), 0.2);
			} else {
				AbilityHelpers.launchSelf(p, new Vec3(p.getDeltaMovement().x, 1.35, p.getDeltaMovement().z));
				for (LivingEntity e : AbilityHelpers.enemiesAround(p, p.position(), 3.0)) {
					AbilityHelpers.hurt(p, e, 4.0f);
					AbilityHelpers.knockbackFrom(e, p.position(), 1.2);
				}
				for (int i = 1; i <= 3; i++) {
					BatchCFx.flatRing(level, p.position().add(0, 0.1, 0), 0.6 * i, 12 + 6 * i, ringDust(1.0f), 0.15);
				}
			}
			level.sendParticles(ParticleTypes.SONIC_BOOM, p.getX(), p.getY(), p.getZ(), 1, 0, 0, 0, 0);
			// no fall damage from this launch -- protected until shortly after the next landing
			ctx.setResource("no_fall_until", p.level().getGameTime() + 200, 1.0e12f);
			MutationVisuals.play(p, "leap");
			AbilityHelpers.sound(p, SoundEvents.WARDEN_SONIC_BOOM, 0.8f, 1.9f);
			ctx.triggerCooldown();
		}));

		// Z -- Supersonic Scream: hold ~4 s, then the cone (or, sneaking when you started, all round you).
		AbilityHandlers.register(KEY, "supersonic_scream", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				if (ctx.resource("scream_start") > 0.5f) {
					return;
				}
				if (!ctx.cooldownReady()) {
					ctx.actionBar("message.projecthero.ability.on_cooldown", Component.translatable(ctx.ability().nameKey()),
							String.format(java.util.Locale.ROOT, "%.0f", Math.ceil(ctx.cooldownRemaining() / 20.0f)));
					return;
				}
				seed(ctx);
				if (ctx.resource("voice") < COST_SUPERSONIC) {
					ctx.actionBar("message.projecthero.sonic.voice_low");
					return;
				}
				ctx.setResource("scream_start", ctx.player().level().getGameTime(), 1.0e12f);
				ctx.setResource("scream_shift", ctx.player().isShiftKeyDown() ? 1 : 0, 1);
			}

			@Override
			public void onRelease(AbilityContext ctx) {
				float start = ctx.resource("scream_start");
				if (start <= 0.5f) {
					return;
				}
				long held = ctx.player().level().getGameTime() - (long) start;
				if (held >= SUPERSONIC_CHARGE) {
					fireSupersonic(ctx);
				} else {
					ctx.setResource("scream_start", 0, 1.0e12f);
					ctx.setResource("sonic_charge", 0, 100);
					MutationVisuals.stopIf(ctx.player(), "scream");
					AbilityHelpers.sound(ctx.player(), SoundEvents.FIRE_EXTINGUISH, 0.6f, 0.8f);
				}
			}

			@Override
			public void onServerTick(AbilityContext ctx) {
				float start = ctx.resource("scream_start");
				if (start <= 0.5f) {
					return;
				}
				ServerPlayer p = ctx.player();
				long held = p.level().getGameTime() - (long) start;
				if (held > SUPERSONIC_CHARGE + 40 || held < 0) {
					fireSupersonic(ctx);
					return;
				}
				MutationVisuals.ensure(p, "scream");
				double frac = Math.min(1.0, held / (double) SUPERSONIC_CHARGE);
				if (held % 4 == 0) {
					ctx.setResource("sonic_charge", (float) (frac * 100.0), 100);
					// a ring of sound tightening around the throat as it builds
					BatchCFx.flatRing(ctx.level(), p.getEyePosition().add(0, -0.3, 0), 1.6 - frac, 12, ringDust(0.7f), 0);
				}
				if (held % 8 == 0) {
					ctx.level().sendParticles(ParticleTypes.SONIC_BOOM, p.getX(), p.getY() + 1.4, p.getZ(),
							1, 0.15 + frac * 0.3, 0.15, 0.15 + frac * 0.3, 0.0);
					AbilityHelpers.sound(p, SoundEvents.WARDEN_SONIC_CHARGE, 0.7f, 0.6f + (float) frac);
				}
				if (held >= SUPERSONIC_CHARGE && held % 20 == 0) {
					p.displayClientMessage(Component.translatable("message.projecthero.sonic.scream_ready"), true);
				}
			}
		});

		// V -- Resonance: shatter glass / ice / leaves (terrain damage on) and stun whatever stands there.
		AbilityHandlers.register(KEY, "resonance", Handlers.instant(ctx -> {
			ServerPlayer p = ctx.player();
			ServerLevel level = ctx.level();
			double range = sensesRange(p, 14.0);
			var hit = AbilityHelpers.raycastBlock(p, range);
			LivingEntity aimed = AbilityHelpers.raycastEntity(p, range);
			Vec3 at = aimed != null ? aimed.position().add(0, aimed.getBbHeight() * 0.5, 0)
					: hit.getType() == HitResult.Type.BLOCK ? Vec3.atCenterOf(hit.getBlockPos())
					: p.getEyePosition().add(p.getLookAngle().scale(range));
			if (!spendVoice(ctx, COST_RESONANCE)) {
				return;
			}
			int shattered = 0;
			if (hit.getType() == HitResult.Type.BLOCK && AbilityHelpers.canGrief()) {
				for (BlockPos bp : BlockPos.betweenClosed(hit.getBlockPos().offset(-1, -1, -1), hit.getBlockPos().offset(1, 1, 1))) {
					if (isFragile(level, bp)) {
						level.destroyBlock(bp, true, p);
						shattered++;
					}
				}
			}
			for (LivingEntity e : AbilityHelpers.enemiesAround(p, at, 3.5)) {
				AbilityHelpers.hurt(p, e, 6.0f);
				AbilityHelpers.applyControl(e, MobEffects.MOVEMENT_SLOWDOWN, 40, 5);
				AbilityHelpers.applyControl(e, MobEffects.CONFUSION, 60, 0);
				if (e instanceof Mob mob) {
					mob.getNavigation().stop();
				}
			}
			for (int i = 1; i <= 3; i++) {
				BatchCFx.ring(level, at, p.getLookAngle(), 0.5 * i, 10 + 5 * i, ringDust(0.9f), 0.05);
			}
			level.sendParticles(ParticleTypes.NOTE, at.x, at.y, at.z, 10, 0.6, 0.6, 0.6, 1.0);
			AbilityHelpers.line(level, mouth(p), at, ringDust(0.5f), 1.0);
			MutationVisuals.play(p, "clap");
			AbilityHelpers.sound(p, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0f, 1.5f);
			if (shattered > 0) {
				level.playSound(null, BlockPos.containing(at), SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, 1.0f, 1.2f);
			}
			ctx.triggerCooldown();
		}));

		// C -- Echolocation: a sonar ping every 2 s while on. Everything within 20 blocks glows for you alone.
		AbilityHandlers.register(KEY, "echolocation", new AbilityHandler() {
			@Override
			public void onToggleOn(AbilityContext ctx) {
				seed(ctx);
				if (ctx.resource("voice") < COST_PING) {
					ctx.setToggled(false);
					ctx.actionBar("message.projecthero.sonic.voice_low");
					return;
				}
				ping(ctx);
				MutationVisuals.play(ctx.player(), "p14.listen");
			}

			@Override
			public void onToggleOff(AbilityContext ctx) {
				ctx.setResource("echo_until", 0, 1.0e12f);
			}

			@Override
			public void onToggleTick(AbilityContext ctx) {
				long now = ctx.player().level().getGameTime();
				if (ctx.resource("echo_until") - 10 > now) {
					return;
				}
				if (ctx.resource("voice") < COST_PING) {
					ctx.setToggled(false);
					ctx.setResource("echo_until", 0, 1.0e12f);
					ctx.actionBar("message.projecthero.sonic.voice_low");
					return;
				}
				ping(ctx);
			}
		});

		// H -- Sound Barrier: a 5-wide wall of sound 3 blocks ahead for 5 s. It shreds projectiles and shoves mobs.
		AbilityHandlers.register(KEY, "sound_barrier", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				if (!spendVoice(ctx, COST_BARRIER)) {
					return;
				}
				ServerPlayer p = ctx.player();
				Vec3 look = p.getLookAngle();
				Vec3 flat = new Vec3(look.x, 0, look.z);
				flat = flat.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : flat.normalize();
				Vec3 centre = p.position().add(flat.scale(3.0)).add(0, 1.2, 0);
				BARRIERS.put(p.getUUID(), new Barrier(ctx.level(), centre, flat, ctx.level().getGameTime() + 100));
				MutationVisuals.play(p, "cast_two_hand");
				AbilityHelpers.sound(p, SoundEvents.WARDEN_SONIC_CHARGE, 1.0f, 1.5f);
				ctx.triggerCooldown();
			}

			@Override
			public void onServerTick(AbilityContext ctx) {
				Barrier b = BARRIERS.get(ctx.player().getUUID());
				if (b != null) {
					barrierTick(ctx.player(), b);
				}
			}
		});

		// N -- Disorient: a warbling pulse. Nausea, slow, and every mob within 10 blocks forgets who it was fighting.
		AbilityHandlers.register(KEY, "disorient", Handlers.instant(ctx -> {
			if (!spendVoice(ctx, COST_DISORIENT)) {
				return;
			}
			ServerPlayer p = ctx.player();
			disorient(p, 10.0);
			MutationVisuals.play(p, "p14.shout");
			AbilityHelpers.sound(p, SoundEvents.WARDEN_SONIC_CHARGE, 1.0f, 2.0f);
			AbilityHelpers.sound(p, SoundEvents.NOTE_BLOCK_DIDGERIDOO.value(), 1.0f, 0.5f);
			ctx.triggerCooldown();
		}));

		// Shift + right-click a block: a single sonar ping (free, 4 s cooldown) -- the old passive, kept.
		net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register((player, level, hand, hitResult) -> {
			if (level.isClientSide || !(player instanceof ServerPlayer sp) || !sp.isShiftKeyDown()) {
				return net.minecraft.world.InteractionResult.PASS;
			}
			Power power = power();
			if (power == null || !ExperimentalPowers.owns(sp, power)) {
				return net.minecraft.world.InteractionResult.PASS;
			}
			long now = sp.level().getGameTime();
			if (ExperimentalPowers.getResource(sp, power, "echo_cd") > now) {
				return net.minecraft.world.InteractionResult.PASS;
			}
			ExperimentalPowers.setResource(sp, power, "echo_until", now + 3 * 20, 1.0e12f);
			ExperimentalPowers.setResource(sp, power, "echo_cd", now + 4 * 20, 1.0e12f);
			if (sp.level() instanceof ServerLevel sl) {
				sl.sendParticles(ParticleTypes.SONIC_BOOM, sp.getX(), sp.getY() + 1, sp.getZ(), 1, 0, 0, 0, 0);
			}
			AbilityHelpers.sound(sp, SoundEvents.WARDEN_SONIC_BOOM, 0.5f, 1.9f);
			return net.minecraft.world.InteractionResult.SUCCESS;
		});

		com.projecthero.mod.hero.PowerPassives.register(KEY, (player, owned) -> {
			if (!owned) {
				BARRIERS.remove(player.getUUID());
			}
		});
		com.projecthero.mod.hero.PowerPassives.registerTick(KEY, SonicScreamHandlers::passiveTick);
	}

	private static final Set<String> STALE = Set.of("senses");

	private static void passiveTick(ServerPlayer player) {
		Power power = power();
		if (power == null) {
			return;
		}
		if (player.tickCount % 100 == 0) {
			BatchCFx.purge(player, KEY, STALE, Set.of());
		}
		if (player.tickCount % 5 != 0) {
			return;
		}
		var res = ExperimentalPowers.state(player).resources;
		if (!res.containsKey(KEY + "/voice")) {
			ExperimentalPowers.setResource(player, power, "voice", VOICE_MAX, VOICE_MAX);
			return;
		}
		float voice = res.getOrDefault(KEY + "/voice", VOICE_MAX);
		float quietAt = res.getOrDefault(KEY + "/quiet_at", 0f);
		if (voice < VOICE_MAX && player.level().getGameTime() >= (long) quietAt) {
			ExperimentalPowers.addResource(player, power, "voice", VOICE_REGEN / 4.0f, VOICE_MAX);
		}
	}

	// ---- routines -----------------------------------------------------------------------------

	private static boolean inCone(ServerPlayer p, LivingEntity e, double angleDot, double maxDist) {
		Vec3 to = e.position().add(0, e.getBbHeight() * 0.5, 0).subtract(p.getEyePosition());
		return to.length() <= maxDist && to.normalize().dot(p.getLookAngle()) > angleDot;
	}

	/** One Echolocation ping: a ring of sound sweeping outward, and 2.5 s of glow for everything within 20 blocks. */
	private static void ping(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		long now = p.level().getGameTime();
		ctx.addResource("voice", -COST_PING, VOICE_MAX); // pings do not reset the quiet timer
		ctx.setResource("echo_until", now + 50, 1.0e12f);
		ServerLevel level = ctx.level();
		BatchCFx.flatRing(level, p.position().add(0, 1.0, 0), 1.0, 40, ringDust(1.1f), 0.9);
		BatchCFx.flatRing(level, p.position().add(0, 0.2, 0), 0.8, 24, ringDust(0.8f), 0.6);
		level.sendParticles(ParticleTypes.SONIC_BOOM, p.getX(), p.getY() + 1.0, p.getZ(), 1, 0, 0, 0, 0);
		AbilityHelpers.sound(p, SoundEvents.WARDEN_SONIC_BOOM, 0.35f, 1.9f);
	}

	private static void barrierTick(ServerPlayer owner, Barrier b) {
		ServerLevel level = b.level;
		long now = level.getGameTime();
		if (now >= b.expires || owner.level() != level) {
			BARRIERS.remove(owner.getUUID());
			return;
		}
		if (now % 3 == 0) {
			// the wall itself: a pale grid of sound with a SONIC_BOOM rolling across it
			for (double s = -2.5; s <= 2.5; s += 0.8) {
				for (double y = -1.2; y <= 2.2; y += 0.8) {
					Vec3 pt = b.centre.add(b.side.scale(s)).add(0, y, 0);
					level.sendParticles(ringDust(0.8f), pt.x, pt.y, pt.z, 1, 0.05, 0.05, 0.05, 0);
				}
			}
			double sweep = ((now / 3) % 7 - 3) * 0.8;
			Vec3 boom = b.centre.add(b.side.scale(sweep)).add(0, 0.5, 0);
			level.sendParticles(ParticleTypes.SONIC_BOOM, boom.x, boom.y, boom.z, 1, 0, 0, 0, 0);
		}
		AABB box = new AABB(b.centre, b.centre).inflate(3.5);
		for (Projectile proj : level.getEntitiesOfClass(Projectile.class, box)) {
			if (proj.getOwner() == owner || !b.contains(proj.position())) {
				continue;
			}
			level.sendParticles(ParticleTypes.SONIC_BOOM, proj.getX(), proj.getY(), proj.getZ(), 1, 0, 0, 0, 0);
			level.playSound(null, proj.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 0.4f, 2.0f);
			proj.discard();
		}
		if (now % 10 == 0) {
			for (LivingEntity e : AbilityHelpers.hostilesAround(owner, b.centre, 3.2)) {
				if (!b.contains(e.position().add(0, e.getBbHeight() * 0.5, 0))) {
					continue;
				}
				AbilityHelpers.hurt(owner, e, 3.0f);
				Vec3 away = e.position().subtract(b.centre).dot(b.normal) >= 0 ? b.normal : b.normal.reverse();
				AbilityHelpers.push(e, away.scale(1.1).add(0, 0.25, 0));
			}
		}
	}

	/** True while a Sound Barrier is up for {@code player} (for tests / other systems). */
	public static Barrier barrier(ServerPlayer player) {
		return BARRIERS.get(player.getUUID());
	}

	/** Disorient everything within {@code radius}: nausea, slow, and every mob drops its target for 3 s. */
	public static int disorient(ServerPlayer p, double radius) {
		ServerLevel level = (ServerLevel) p.level();
		long until = level.getGameTime() + 60;
		int n = 0;
		for (LivingEntity e : AbilityHelpers.enemiesAround(p, p.position(), radius)) {
			AbilityHelpers.hurt(p, e, 4.0f);
			AbilityHelpers.applyControl(e, MobEffects.CONFUSION, 120, 0);
			AbilityHelpers.applyControl(e, MobEffects.MOVEMENT_SLOWDOWN, 60, 0);
			if (e instanceof Mob mob) {
				loseTarget(mob);
				DISORIENTED.put(mob.getId(), until);
				DISORIENTED_LEVEL.put(mob.getId(), level);
			}
			level.sendParticles(ParticleTypes.NOTE, e.getX(), e.getY() + e.getBbHeight() + 0.3, e.getZ(), 3, 0.3, 0.1, 0.3, 1.0);
			n++;
		}
		for (int i = 1; i <= 3; i++) {
			BatchCFx.flatRing(level, p.position().add(0, 1.0, 0), i * 0.8, 16 + 8 * i, ringDust(1.0f), 0.3 + i * 0.2);
		}
		return n;
	}

	private static void loseTarget(Mob mob) {
		mob.setTarget(null);
		mob.setLastHurtByMob(null);
		mob.getNavigation().stop();
		mob.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
		mob.getBrain().eraseMemory(MemoryModuleType.ANGRY_AT);
	}

	/** True while {@code mob} is still disoriented. */
	public static boolean isDisoriented(Entity mob) {
		Long until = DISORIENTED.get(mob.getId());
		return until != null && until > mob.level().getGameTime();
	}

	/** Server tick (from RevampBatchC): disoriented mobs keep losing their target until it wears off. */
	public static void serverTick(MinecraftServer server) {
		if (DISORIENTED.isEmpty()) {
			return;
		}
		DISORIENTED.entrySet().removeIf(e -> {
			ServerLevel level = DISORIENTED_LEVEL.get(e.getKey());
			if (level == null || e.getValue() <= level.getGameTime()) {
				DISORIENTED_LEVEL.remove(e.getKey());
				return true;
			}
			if (level.getEntity(e.getKey()) instanceof Mob mob && mob.isAlive()) {
				if (mob.getTarget() != null) {
					loseTarget(mob);
				}
				return false;
			}
			DISORIENTED_LEVEL.remove(e.getKey());
			return true;
		});
	}

	public static void clearSessionState() {
		BARRIERS.clear();
		DISORIENTED.clear();
		DISORIENTED_LEVEL.clear();
	}

	/** A short, sharp confusion + slow -- the "stun" every offensive scream leaves behind. */
	private static void staticStun(LivingEntity e) {
		AbilityHelpers.applyControl(e, MobEffects.MOVEMENT_SLOWDOWN, 30, 3);
		AbilityHelpers.applyControl(e, MobEffects.CONFUSION, 30, 0);
	}

	private static void fireSupersonic(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		ServerLevel level = ctx.level();
		boolean shift = ctx.resource("scream_shift") > 0.5f;
		ctx.setResource("scream_shift", 0, 1);
		ctx.setResource("scream_start", 0, 1.0e12f);
		ctx.setResource("sonic_charge", 0, 100);
		if (!spendVoice(ctx, COST_SUPERSONIC)) {
			MutationVisuals.stopIf(p, "scream");
			return;
		}
		MutationVisuals.play(p, "p14.shout");
		if (shift) {
			double r = 20.0;
			for (LivingEntity e : AbilityHelpers.hostilesAround(p, p.position(), r)) {
				AbilityHelpers.hurtBurst(p, e, SUPERSONIC_RADIAL);
				AbilityHelpers.knockbackFrom(e, p.position(), 2.4);
				AbilityHelpers.applyControl(e, MobEffects.CONFUSION, 120, 0);
				staticStun(e);
			}
			for (int i = 1; i <= 5; i++) {
				BatchCFx.flatRing(level, p.position().add(0, 1.0, 0), i * 1.2, 20 + 6 * i, ringDust(1.3f), 0.5 + i * 0.25);
			}
			for (int i = 0; i < 16; i++) {
				double a = i * Math.PI / 8;
				level.sendParticles(ParticleTypes.SONIC_BOOM, p.getX() + Math.cos(a) * 6, p.getY() + 1.0,
						p.getZ() + Math.sin(a) * 6, 1, 0, 0, 0, 0);
			}
			level.playSound(null, p.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.8f, 0.5f);
			ctx.triggerCooldown(68 * 20);
			return;
		}
		double r = sensesRange(p, 20.0);
		Vec3 look = p.getLookAngle();
		for (LivingEntity e : AbilityHelpers.enemiesAround(p, p.getEyePosition().add(look.scale(r * 0.5)), r * 0.55)) {
			Vec3 to = e.position().add(0, e.getBbHeight() * 0.5, 0).subtract(p.getEyePosition());
			if (to.length() > r || to.normalize().dot(look) < 0.6) {
				continue;
			}
			AbilityHelpers.hurtBurst(p, e, SUPERSONIC_CONE);
			AbilityHelpers.knockbackFrom(e, p.position(), 3.0);
			AbilityHelpers.applyControl(e, MobEffects.CONFUSION, 120, 0);
			staticStun(e);
		}
		if (AbilityHelpers.canGrief()) {
			for (int i = 1; i <= (int) r; i++) {
				BlockPos step = BlockPos.containing(p.getEyePosition().add(look.scale(i)));
				for (BlockPos bp : BlockPos.betweenClosed(step.offset(-1, -1, -1), step.offset(1, 1, 1))) {
					if (isFragile(level, bp)) {
						level.destroyBlock(bp, false, p);
					}
				}
			}
		}
		soundTrail(level, mouth(p), look, r, 0.5, 0.3, 2.0);
		level.playSound(null, p.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.6f, 0.6f);
		ctx.triggerCooldown();
	}

	/** Glass, ice, leaves, glowstone and other near-weightless blocks. */
	public static boolean isFragile(ServerLevel level, BlockPos pos) {
		BlockState st = level.getBlockState(pos);
		if (st.isAir()) {
			return false;
		}
		if (st.is(Blocks.GLASS) || st.is(Blocks.GLASS_PANE) || st.is(Blocks.TINTED_GLASS)
				|| st.is(BlockTags.ICE) || st.is(Blocks.GLOWSTONE) || st.is(Blocks.SEA_LANTERN)
				|| st.is(BlockTags.IMPERMEABLE) || st.is(BlockTags.LEAVES)) {
			return true;
		}
		float hardness = st.getDestroySpeed(level, pos);
		return hardness >= 0.0f && hardness <= 0.35f && !st.is(Blocks.BEDROCK);
	}
}
