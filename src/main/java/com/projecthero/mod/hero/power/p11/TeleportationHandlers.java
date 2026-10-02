package com.projecthero.mod.hero.power.p11;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.projecthero.mod.hero.AbilityContext;
import com.projecthero.mod.hero.AbilityHandler;
import com.projecthero.mod.hero.AbilityHandlers;
import com.projecthero.mod.hero.ExperimentalPowers;
import com.projecthero.mod.hero.Power;
import com.projecthero.mod.hero.Powers;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.hero.power.Handlers;
import com.projecthero.mod.hero.power.SafeTeleport;
import com.projecthero.mod.hero.revamp.d.BatchDFx;
import com.projecthero.mod.hero.visual.MutationVisuals;
import com.projecthero.mod.squad.Squads;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Power 11 — Teleportation. All destinations validated by {@link SafeTeleport}. */
public final class TeleportationHandlers {
	private static final String KEY = "power_11_teleportation";
	private static final double BLINK_RANGE = 75.0;

	/** Portal (Z): hold this long to arm the destination picker. */
	private static final int PORTAL_CHARGE_TICKS = 5 * 20;
	private static final int PORTAL_CD = 51 * 20;
	/** Ticks for the portal to finish forming out of particles. */
	private static final int PORTAL_FORM_TICKS = 60;

	/** Per-player cooldown between portal-anchor warps, so you do not ping-pong. */
	private static final Map<UUID, Long> ANCHOR_WARP_READY = new HashMap<>();

	private TeleportationHandlers() {
	}

	private static void poof(ServerLevel level, Vec3 at) {
		level.sendParticles(ParticleTypes.PORTAL, at.x, at.y + 1, at.z, 30, 0.3, 0.6, 0.3, 0.4);
		level.sendParticles(ParticleTypes.REVERSE_PORTAL, at.x, at.y + 1, at.z, 20, 0.3, 0.6, 0.3, 0.1);
		BatchDFx.puff(level, at);
	}

	/** v0.13.22: the departure point gets the smoke puff plus a brief body-shaped afterimage of the teleporter. */
	private static void depart(ServerLevel level, Vec3 at, float yaw) {
		poof(level, at);
		BatchDFx.silhouette(level, at, yaw, BatchDFx.TELE_GLOW);
	}

	/** Turns the player's camera toward {@code at} (a server-side lookAt alone never reaches the client). */
	private static void face(ServerPlayer p, Vec3 at) {
		p.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, at);
		p.connection.teleport(p.getX(), p.getY(), p.getZ(), p.getYRot(), p.getXRot());
	}

	private static Power power() {
		return Powers.byKey(KEY);
	}

	/** Where a blink aimed right now would land: on top of the block being looked at, else max range. */
	private static Vec3 blinkDest(ServerPlayer p) {
		BlockHitResult bhr = AbilityHelpers.raycastBlock(p, BLINK_RANGE);
		if (bhr.getType() == HitResult.Type.BLOCK) {
			BlockPos face = bhr.getBlockPos().relative(bhr.getDirection());
			for (int i = 0; i < 4; i++) {
				BlockPos below = face.below(i + 1);
				if (!p.level().getBlockState(below).getCollisionShape(p.level(), below).isEmpty()) {
					return Vec3.atBottomCenterOf(face.below(i));
				}
			}
			return Vec3.atBottomCenterOf(face);
		}
		return p.position().add(p.getLookAngle().scale(BLINK_RANGE));
	}

	public static void register() {
		// R -- Blink (hold to paint the destination, release to teleport). Shift + R is a Phase Jump
		// through a thin wall instead.
		AbilityHandlers.register(KEY, "blink", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				ServerPlayer p = ctx.player();
				if (p.isShiftKeyDown()) {
					if (!ctx.cooldownReady()) {
						return;
					}
					Vec3 from = p.position();
					if (SafeTeleport.phaseThrough(p, p.getLookAngle(), 4.5)) {
						depart(ctx.level(), from, p.getYRot());
						poof(ctx.level(), p.position());
						MutationVisuals.play(p, "dash_forward");
						AbilityHelpers.sound(p, SoundEvents.ENDERMAN_TELEPORT, 0.8f, 1.4f);
						ctx.triggerCooldown();
					} else {
						ctx.actionBar("message.projecthero.teleport.no_room");
					}
					ctx.setResource("phase_jumped", 1, 1);
					return;
				}
				if (ctx.cooldownReady()) {
					ctx.setResource("aiming", 1, 1);
					MutationVisuals.play(p, "channel_right");
				}
			}

			@Override
			public void onServerTick(AbilityContext ctx) {
				if (ctx.resource("aiming") < 0.5f) {
					return;
				}
				ServerPlayer p = ctx.player();
				MutationVisuals.ensure(p, "channel_right");
				Vec3 d = blinkDest(p);
				if (p.tickCount % 2 == 0) {
					ctx.level().sendParticles(ParticleTypes.PORTAL, d.x, d.y + 0.1, d.z, 14, 0.35, 0.1, 0.35, 0.05);
					ctx.level().sendParticles(ParticleTypes.END_ROD, d.x, d.y + 0.9, d.z, 3, 0.08, 0.45, 0.08, 0.0);
				}
			}

			@Override
			public void onRelease(AbilityContext ctx) {
				if (ctx.resource("phase_jumped") > 0.5f) {
					ctx.setResource("phase_jumped", 0, 1);
					return;
				}
				if (ctx.resource("aiming") < 0.5f) {
					return;
				}
				ctx.setResource("aiming", 0, 1);
				ServerPlayer p = ctx.player();
				MutationVisuals.stopIf(p, "channel_right");
				Vec3 from = p.position();
				float yaw = p.getYRot();
				Vec3 dest = blinkDest(p);
				if (SafeTeleport.tryTeleport(p, dest) || SafeTeleport.blink(p, p.getLookAngle(), BLINK_RANGE)) {
					depart(ctx.level(), from, yaw);
					poof(ctx.level(), p.position());
					MutationVisuals.play(p, "dash_forward");
					AbilityHelpers.sound(p, SoundEvents.ENDERMAN_TELEPORT, 1.0f, 1.2f);
					ctx.triggerCooldown();
				}
			}
		});

		AbilityHandlers.register(KEY, "target_teleport", Handlers.instant(ctx -> {
			ServerPlayer p = ctx.player();
			LivingEntity t = AbilityHelpers.raycastEntity(p, 50.0);
			if (t == null) {
				return;
			}
			Vec3 from = p.position();
			float yaw = p.getYRot();
			if (teleportBehind(p, t)) {
				depart(ctx.level(), from, yaw);
				poof(ctx.level(), p.position());
				MutationVisuals.play(p, "hero_landing");
				AbilityHelpers.sound(p, SoundEvents.ENDERMAN_TELEPORT, 1.0f, 1.0f);
				ctx.triggerCooldown();
			}
		}));

		AbilityHandlers.register(KEY, "escape_blink", Handlers.instant(ctx -> {
			ServerPlayer p = ctx.player();
			Vec3 from = p.position();
			float yaw = p.getYRot();
			Vec3 back = p.getLookAngle().reverse();
			if (SafeTeleport.blink(p, back, 9.5)) {
				p.addEffect(new net.minecraft.world.effect.MobEffectInstance(
						net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE, 24, 2, false, false, false));
				depart(ctx.level(), from, yaw);
				poof(ctx.level(), p.position());
				MutationVisuals.play(p, "leap");
				AbilityHelpers.sound(p, SoundEvents.ENDERMAN_TELEPORT, 1.0f, 1.6f);
				ctx.triggerCooldown();
			}
		}));

		// H -- Bamf Strike: vanish behind the looked-at target, hit, and chain to up to 4 targets.
		AbilityHandlers.register(KEY, "bamf_strike", Handlers.instantTicking(
				TeleportationHandlers::bamfStart, TeleportationHandlers::bamfTick));

		// N -- Swap: trade places with the looked-at creature.
		AbilityHandlers.register(KEY, "swap", Handlers.instant(TeleportationHandlers::swap));

		// Z -- Portal. Hold 5 s to arm, then pick an exact destination (coords + dimension) in a screen.
		// A gateway forms slowly out of particles where you stand and a matching one at the destination;
		// step into either to travel to the other. The pair stays open until you sneak + right-click a
		// gate to close it, or you lose the power. 60 s cooldown to open a pair.
		AbilityHandlers.register(KEY, "portal", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				if (ExperimentalPowers.getMarker(ctx.player(), ctx.power(), "portal_a") != null) {
					ctx.actionBar("message.projecthero.teleport.portal_exists");
					return;
				}
				if (!ctx.cooldownReady()) {
					ctx.actionBar("message.projecthero.ability.on_cooldown",
							net.minecraft.network.chat.Component.translatable(ctx.ability().nameKey()),
							String.format(java.util.Locale.ROOT, "%.0f", Math.ceil(ctx.cooldownRemaining() / 20.0f)));
					return;
				}
				ctx.setResource("portal_charge_start", ctx.player().level().getGameTime(), 1.0e12f);
				MutationVisuals.play(ctx.player(), "channel_two_hand");
			}

			@Override
			public void onServerTick(AbilityContext ctx) {
				portalChargeTick(ctx);
				portalPairTick(ctx);
			}

			@Override
			public void onRelease(AbilityContext ctx) {
				float start = ctx.resource("portal_charge_start");
				if (start <= 0.5f) {
					return;
				}
				long held = ctx.player().level().getGameTime() - (long) start;
				ctx.setResource("portal_charge_start", 0, 1.0e12f);
				ctx.setResource("portal_charge", 0, 100);
				MutationVisuals.stopIf(ctx.player(), "channel_two_hand");
				if (held >= PORTAL_CHARGE_TICKS) {
					MutationVisuals.play(ctx.player(), "cast_raise_both");
					sendPortalPicker(ctx);
				} else {
					AbilityHelpers.sound(ctx.player(), SoundEvents.AMETHYST_BLOCK_BREAK, 0.6f, 0.8f);
				}
			}
		});

		// C -- Portal Anchor. Press to drop anchor A, press again for anchor B; the two link into a
		// permanent portal (works across dimensions). Sneak + right-click either end to remove the pair.
		AbilityHandlers.register(KEY, "portal_anchor", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				ServerPlayer p = ctx.player();
				BlockPos a = ExperimentalPowers.getMarker(p, ctx.power(), "anchor_a");
				BlockPos b = ExperimentalPowers.getMarker(p, ctx.power(), "anchor_b");
				if (a != null && b != null) {
					ctx.actionBar("message.projecthero.teleport.anchor_full");
					return;
				}
				BlockHitResult hit = AbilityHelpers.raycastBlock(p, 40.0);
				BlockPos target = hit.getType() == HitResult.Type.BLOCK
						? hit.getBlockPos().relative(hit.getDirection())
						: p.blockPosition();
				if (a == null) {
					ExperimentalPowers.setMarker(p, ctx.power(), "anchor_a", target);
					ctx.actionBar("message.projecthero.teleport.anchor_a");
				} else {
					ExperimentalPowers.setMarker(p, ctx.power(), "anchor_b", target);
					ctx.actionBar("message.projecthero.teleport.anchor_linked");
				}
				AbilityHelpers.burst(ctx.level(), Vec3.atCenterOf(target), ParticleTypes.REVERSE_PORTAL, 40, 0.4);
				BatchDFx.puff(ctx.level(), Vec3.atBottomCenterOf(target));
				MutationVisuals.play(p, "point_right");
				AbilityHelpers.sound(p, SoundEvents.ENDERMAN_TELEPORT, 1.0f, 0.7f);
				ctx.triggerCooldown();
			}

			@Override
			public void onServerTick(AbilityContext ctx) {
				anchorTick(ctx);
			}
		});

		AbilityHandlers.register(KEY, "teleport_mark", Handlers.instant(ctx -> {
			ServerPlayer p = ctx.player();
			BlockPos mark = ExperimentalPowers.getMarker(p, ctx.power(), "return");
			if (mark != null) {
				var dimKey = ExperimentalPowers.getMarkerDimension(p, ctx.power(), "return");
				ServerLevel target = (dimKey == null || p.getServer() == null)
						? ctx.level() : p.getServer().getLevel(dimKey);
				if (target == null) {
					target = ctx.level();
				}
				ServerLevel originLevel = ctx.level();
				Vec3 from = p.position();
				float yaw = p.getYRot();
				if (SafeTeleport.tryTeleport(p, target, Vec3.atBottomCenterOf(mark))) {
					depart(originLevel, from, yaw);
					poof((ServerLevel) p.level(), p.position());
					MutationVisuals.play(p, "flex");
					AbilityHelpers.sound(p, SoundEvents.ENDERMAN_TELEPORT, 1.0f, 0.9f);
					ExperimentalPowers.clearMarker(p, ctx.power(), "return");
					ctx.triggerCooldown();
				} else {
					ctx.actionBar("message.projecthero.teleport.mark_blocked");
					ExperimentalPowers.clearMarker(p, ctx.power(), "return");
				}
			} else {
				ExperimentalPowers.setMarker(p, ctx.power(), "return", p.blockPosition());
				ctx.actionBar("message.projecthero.teleport.mark_placed");
				MutationVisuals.play(p, "summon_ground");
				BatchDFx.ring(ctx.level(), p.position().add(0, 0.1, 0), 0.8, BatchDFx.TELE_GLOW, 12, 0);
				AbilityHelpers.burst(ctx.level(), p.position(), ParticleTypes.REVERSE_PORTAL, 20, 0.3);
			}
		}));

		// Sneak + right-click a portal anchor to tear the pair down.
		UseBlockCallback.EVENT.register((player, level, hand, hitResult) -> {
			if (level.isClientSide || !(player instanceof ServerPlayer sp) || !player.isShiftKeyDown()) {
				return InteractionResult.PASS;
			}
			Power power = power();
			if (power == null || !ExperimentalPowers.owns(sp, power)) {
				return InteractionResult.PASS;
			}
			BlockPos clicked = hitResult.getBlockPos();
			BlockPos a = ExperimentalPowers.getMarker(sp, power, "anchor_a");
			BlockPos b = ExperimentalPowers.getMarker(sp, power, "anchor_b");
			if ((a != null && a.distSqr(clicked) <= 9.0) || (b != null && b.distSqr(clicked) <= 9.0)) {
				ExperimentalPowers.clearMarker(sp, power, "anchor_a");
				ExperimentalPowers.clearMarker(sp, power, "anchor_b");
				sp.displayClientMessage(net.minecraft.network.chat.Component.translatable(
						"message.projecthero.teleport.anchor_removed"), true);
				AbilityHelpers.sound(sp, SoundEvents.ENDERMAN_DEATH, 0.8f, 1.2f);
				return InteractionResult.SUCCESS;
			}
			BlockPos pa = ExperimentalPowers.getMarker(sp, power, "portal_a");
			BlockPos pb = ExperimentalPowers.getMarker(sp, power, "portal_b");
			if ((pa != null && pa.distSqr(clicked) <= 9.0) || (pb != null && pb.distSqr(clicked) <= 9.0)) {
				closePortalPair(sp, power);
				sp.displayClientMessage(net.minecraft.network.chat.Component.translatable(
						"message.projecthero.teleport.portal_closed"), true);
				AbilityHelpers.sound(sp, SoundEvents.ENDERMAN_DEATH, 0.8f, 1.2f);
				return InteractionResult.SUCCESS;
			}
			return InteractionResult.PASS;
		});
	}

	// ---- G / H helpers -------------------------------------------------------------------------

	/** Teleports {@code p} to a safe spot behind {@code t} (else beside it), facing it. */
	private static boolean teleportBehind(ServerPlayer p, LivingEntity t) {
		Vec3 back = t.getLookAngle().multiply(1, 0, 1);
		if (back.lengthSqr() < 1.0e-4) {
			back = t.position().subtract(p.position()).multiply(1, 0, 1);
		}
		back = back.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : back.normalize();
		double dist = 1.2 + t.getBbWidth() * 0.5;
		Vec3 side = new Vec3(-back.z, 0, back.x);
		for (Vec3 off : new Vec3[] { back.scale(-dist), side.scale(dist), side.scale(-dist), back.scale(dist) }) {
			if (SafeTeleport.tryTeleport(p, t.position().add(off))) {
				face(p, t.getEyePosition());
				return true;
			}
		}
		return false;
	}

	// ---- H: Bamf Strike ------------------------------------------------------------------------

	private static final int BAMF_MAX_TARGETS = 4;
	private static final float BAMF_DAMAGE = 11.0f;
	private static final double BAMF_RANGE = 30.0;
	private static final double BAMF_CHAIN_RANGE = 10.0;
	private static final int BAMF_HOP_TICKS = 6;
	/** caster -> ids already struck in the running chain. */
	private static final Map<UUID, java.util.List<Integer>> BAMF_HIT = new HashMap<>();

	private static boolean bamfTarget(ServerPlayer p, LivingEntity t) {
		// v0.14.20: the shared rule 1 (the aimed first hop); the chain's later hops only pick hostiles (rule 2)
		return t != null && com.projecthero.mod.combat.HeroTargets.canHarm(p, t) && !Squads.areAllies(p, t)
				&& !(t instanceof com.projecthero.mod.hero.revamp.d.MirrorImageEntity);
	}

	private static void bamfStart(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		LivingEntity t = AbilityHelpers.raycastEntity(p, BAMF_RANGE);
		if (!bamfTarget(p, t)) {
			ctx.actionBar("message.projecthero.teleport.no_target");
			return;
		}
		java.util.List<Integer> hit = new java.util.ArrayList<>();
		if (!bamfHop(ctx.level(), p, t, 0)) {
			ctx.actionBar("message.projecthero.teleport.no_room");
			return;
		}
		hit.add(t.getId());
		BAMF_HIT.put(p.getUUID(), hit);
		ctx.setResource("bamf_timer", BAMF_HOP_TICKS, BAMF_HOP_TICKS);
		ctx.triggerCooldown();
	}

	/** One hop of the chain: vanish, reappear behind {@code t}, strike. */
	private static boolean bamfHop(ServerLevel level, ServerPlayer p, LivingEntity t, int index) {
		Vec3 from = p.position();
		float yaw = p.getYRot();
		if (!teleportBehind(p, t)) {
			return false;
		}
		depart(level, from, yaw);
		BatchDFx.puff(level, p.position());
		AbilityHelpers.hurtBurst(p, t, BAMF_DAMAGE);
		AbilityHelpers.knockbackFrom(t, p.position(), 0.5);
		Vec3 c = t.position().add(0, t.getBbHeight() * 0.5, 0);
		level.sendParticles(ParticleTypes.SWEEP_ATTACK, c.x, c.y, c.z, 1, 0, 0, 0, 0);
		level.sendParticles(BatchDFx.TELE_SMOKE, c.x, c.y, c.z, 8, 0.3, 0.4, 0.3, 0.0);
		String[] strikes = { "slash_right", "punch_left", "punch_right", "haymaker" };
		MutationVisuals.play(p, strikes[Math.min(index, strikes.length - 1)]);
		AbilityHelpers.sound(p, SoundEvents.ENDERMAN_TELEPORT, 0.9f, 1.3f + index * 0.1f);
		AbilityHelpers.sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, 0.9f, 1.0f);
		return true;
	}

	private static void bamfTick(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		java.util.List<Integer> hit = BAMF_HIT.get(p.getUUID());
		if (hit == null) {
			return;
		}
		float timer = ctx.resource("bamf_timer") - 1;
		if (timer > 0) {
			ctx.setResource("bamf_timer", timer, BAMF_HOP_TICKS);
			return;
		}
		if (hit.size() >= BAMF_MAX_TARGETS || !p.isAlive()) {
			bamfEnd(ctx);
			return;
		}
		LivingEntity next = null;
		double best = BAMF_CHAIN_RANGE * BAMF_CHAIN_RANGE;
		for (LivingEntity e : AbilityHelpers.hostilesAround(p, p.position(), BAMF_CHAIN_RANGE)) {
			double d = e.distanceToSqr(p);
			if (d < best && !hit.contains(e.getId()) && bamfTarget(p, e)) {
				best = d;
				next = e;
			}
		}
		if (next == null || !bamfHop(ctx.level(), p, next, hit.size())) {
			bamfEnd(ctx);
			return;
		}
		hit.add(next.getId());
		ctx.setResource("bamf_timer", BAMF_HOP_TICKS, BAMF_HOP_TICKS);
	}

	private static void bamfEnd(AbilityContext ctx) {
		BAMF_HIT.remove(ctx.player().getUUID());
		ctx.setResource("bamf_timer", 0, BAMF_HOP_TICKS);
	}

	/** Test hook: how many targets the running chain has struck (0 when none is running). */
	public static int bamfChainLength(ServerPlayer p) {
		java.util.List<Integer> hit = BAMF_HIT.get(p.getUUID());
		return hit == null ? 0 : hit.size();
	}

	// ---- N: Swap -------------------------------------------------------------------------------

	private static final double SWAP_RANGE = 40.0;

	private static void swap(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		ServerLevel level = ctx.level();
		LivingEntity t = AbilityHelpers.raycastEntity(p, SWAP_RANGE);
		if (t == null || !(AbilityHelpers.isValidGrabTarget(t, p) || Squads.areAllies(p, t))) {
			ctx.actionBar("message.projecthero.teleport.no_target");
			return;
		}
		Vec3 mine = p.position();
		Vec3 theirs = t.position();
		float yaw = p.getYRot();
		// both ends must be clear for both bodies -- the player's usual safety check, plus the target's hitbox
		if (!SafeTeleport.isSafe(level, p, theirs)
				|| !level.noCollision(t, t.getBoundingBox().move(mine.subtract(theirs)))) {
			ctx.actionBar("message.projecthero.teleport.no_room");
			return;
		}
		if (t instanceof ServerPlayer tp) {
			tp.teleportTo(mine.x, mine.y, mine.z);
			tp.connection.resetPosition();
		} else {
			t.teleportTo(mine.x, mine.y, mine.z);
		}
		t.resetFallDistance();
		if (!SafeTeleport.tryTeleport(p, theirs)) {
			// cannot happen after the check above, but never leave the target stranded in our spot
			t.teleportTo(theirs.x, theirs.y, theirs.z);
			return;
		}
		if (t instanceof net.minecraft.world.entity.Mob mob && !Squads.areAllies(p, t)) {
			AbilityHelpers.applyControl(mob, net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 30, 1);
			mob.getNavigation().stop();
		}
		depart(level, mine, yaw);
		BatchDFx.silhouette(level, theirs, t.getYRot(), BatchDFx.TELE_GLOW);
		poof(level, p.position());
		BatchDFx.tether(level, mine.add(0, 1, 0), theirs.add(0, 1, 0), BatchDFx.TELE_GLOW, 24);
		MutationVisuals.play(p, "clap");
		AbilityHelpers.sound(p, SoundEvents.ENDERMAN_TELEPORT, 1.0f, 0.8f);
		AbilityHelpers.sound(p, SoundEvents.CHORUS_FRUIT_TELEPORT, 1.0f, 1.2f);
		ctx.triggerCooldown();
	}

	// ---- Z: Portal ---------------------------------------------------------------------------

	private static void portalChargeTick(AbilityContext ctx) {
		float start = ctx.resource("portal_charge_start");
		if (start <= 0.5f) {
			return;
		}
		ServerPlayer p = ctx.player();
		long held = p.level().getGameTime() - (long) start;
		if (held < 0 || held > PORTAL_CHARGE_TICKS + 60) {
			ctx.setResource("portal_charge_start", 0, 1.0e12f);
			ctx.setResource("portal_charge", 0, 100);
			return;
		}
		double frac = Math.min(1.0, held / (double) PORTAL_CHARGE_TICKS);
		ctx.setResource("portal_charge", (float) (frac * 100.0), 100);
		MutationVisuals.ensure(p, "channel_two_hand");
		if (p.tickCount % 2 == 0) {
			BatchDFx.ring(ctx.level(), p.position().add(0, 0.1, 0), 0.6 + frac, BatchDFx.TELE_GLOW, 10, held * 0.2);
		}
		ctx.level().sendParticles(ParticleTypes.PORTAL, p.getX(), p.getY() + 1.0, p.getZ(),
				4 + (int) (frac * 20), 0.5 + frac, 0.9, 0.5 + frac, 0.05);
		if (held % 10 == 0) {
			AbilityHelpers.sound(p, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.7f, 0.5f + (float) frac);
		}
		if (held >= PORTAL_CHARGE_TICKS && held % 20 == 0) {
			p.displayClientMessage(net.minecraft.network.chat.Component.translatable(
					"message.projecthero.teleport.portal_ready"), true);
		}
	}

	private static void sendPortalPicker(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(p,
				new com.projecthero.mod.network.PortalPickerPayload(p.getX(), p.getY(), p.getZ()));
		AbilityHelpers.sound(p, SoundEvents.PORTAL_TRIGGER, 0.7f, 1.4f);
	}

	/**
	 * Server side of the picker screen: stand up a linked pair of gateways -- one where the caster
	 * stands ({@code portal_a}) and one at the chosen coordinates ({@code portal_b}). The pair persists
	 * until a gate is sneak + right-clicked ({@link #closePortalPair}) or the power is lost (markers are
	 * auto-cleared on revoke).
	 */
	public static void createDestinationPortal(ServerPlayer p, int x, int y, int z, String dimension) {
		Power power = power();
		if (power == null || !ExperimentalPowers.owns(p, power)) {
			return;
		}
		if (ExperimentalPowers.getMarker(p, power, "portal_a") != null) {
			return; // a pair is already open
		}
		if (!ExperimentalPowers.cooldownReady(p, power, power.ability(com.projecthero.mod.hero.AbilitySlot.SLOT_4))) {
			return;
		}
		ResourceLocation dim = ResourceLocation.tryParse(dimension);
		if (dim == null) {
			dim = p.level().dimension().location();
		}
		ExperimentalPowers.setMarker(p, power, "portal_a", p.blockPosition(), p.level().dimension().location());
		ExperimentalPowers.setMarker(p, power, "portal_b", new BlockPos(x, y, z), dim);
		ExperimentalPowers.setResource(p, power, "portal_form", 0, PORTAL_FORM_TICKS);
		ExperimentalPowers.triggerCooldown(p, power, power.ability(com.projecthero.mod.hero.AbilitySlot.SLOT_4), PORTAL_CD);
		AbilityHelpers.sound(p, SoundEvents.PORTAL_TRAVEL, 0.6f, 1.2f);
		p.displayClientMessage(net.minecraft.network.chat.Component.translatable(
				"message.projecthero.teleport.portal_open"), true);
	}

	/** Tick both gateways of the caster's open portal pair: form them, draw them, and travel players. */
	private static void portalPairTick(AbilityContext ctx) {
		ServerPlayer owner = ctx.player();
		Power power = ctx.power();
		BlockPos a = ExperimentalPowers.getMarker(owner, power, "portal_a");
		BlockPos b = ExperimentalPowers.getMarker(owner, power, "portal_b");
		if (a == null || b == null || owner.getServer() == null) {
			return;
		}
		ResourceKey<Level> dimA = orDefault(ExperimentalPowers.getMarkerDimension(owner, power, "portal_a"), owner);
		ResourceKey<Level> dimB = orDefault(ExperimentalPowers.getMarkerDimension(owner, power, "portal_b"), owner);
		ServerLevel levelA = owner.getServer().getLevel(dimA);
		ServerLevel levelB = owner.getServer().getLevel(dimB);
		if (levelA == null || levelB == null) {
			return;
		}
		keepLoaded(levelA, a);
		keepLoaded(levelB, b);
		float form = Math.min(PORTAL_FORM_TICKS, ctx.resource("portal_form") + 1.0f);
		ctx.setResource("portal_form", form, PORTAL_FORM_TICKS);
		double frac = form / PORTAL_FORM_TICKS;
		renderPortalRing(levelA, a, frac);
		renderPortalRing(levelB, b, frac);
		if (frac >= 0.99) {
			warpPlayers(levelA, a, levelB, b);
			if (!(a.equals(b) && dimA == dimB)) {
				warpPlayers(levelB, b, levelA, a);
			}
		}
	}

	private static void renderPortalRing(ServerLevel level, BlockPos at, double frac) {
		Vec3 c = Vec3.atBottomCenterOf(at).add(0, 1.0, 0);
		int ring = 4 + (int) (frac * 26);
		for (int i = 0; i < ring; i++) {
			double a = i / (double) ring * Math.PI * 2;
			double rad = 0.9 * frac;
			level.sendParticles(ParticleTypes.PORTAL, c.x + Math.cos(a) * rad, c.y + (i % 3) * 0.5 * frac,
					c.z + Math.sin(a) * rad, 1, 0.02, 0.15, 0.02, 0.01);
		}
		if (frac >= 0.99) {
			level.sendParticles(ParticleTypes.REVERSE_PORTAL, c.x, c.y, c.z, 4, 0.4, 0.7, 0.4, 0.02);
		}
	}

	private static void closePortalPair(ServerPlayer p, Power power) {
		ExperimentalPowers.clearMarker(p, power, "portal_a");
		ExperimentalPowers.clearMarker(p, power, "portal_b");
		ExperimentalPowers.setResource(p, power, "portal_form", 0, PORTAL_FORM_TICKS);
	}

	// ---- C: Portal Anchor ------------------------------------------------------------------------

	private static void anchorTick(AbilityContext ctx) {
		ServerPlayer owner = ctx.player();
		Power power = ctx.power();
		BlockPos a = ExperimentalPowers.getMarker(owner, power, "anchor_a");
		BlockPos b = ExperimentalPowers.getMarker(owner, power, "anchor_b");
		if (a == null || b == null || owner.getServer() == null) {
			return;
		}
		ResourceKey<Level> dimA = orDefault(ExperimentalPowers.getMarkerDimension(owner, power, "anchor_a"), owner);
		ResourceKey<Level> dimB = orDefault(ExperimentalPowers.getMarkerDimension(owner, power, "anchor_b"), owner);
		ServerLevel levelA = owner.getServer().getLevel(dimA);
		ServerLevel levelB = owner.getServer().getLevel(dimB);
		if (levelA == null || levelB == null) {
			return;
		}
		keepLoaded(levelA, a);
		keepLoaded(levelB, b);
		pillar(levelA, a);
		pillar(levelB, b);
		warpPlayers(levelA, a, levelB, b);
		if (!(a.equals(b) && dimA == dimB)) {
			warpPlayers(levelB, b, levelA, a);
		}
	}

	private static void pillar(ServerLevel level, BlockPos at) {
		Vec3 c = Vec3.atBottomCenterOf(at);
		for (int i = 0; i < 3; i++) {
			level.sendParticles(ParticleTypes.PORTAL, c.x, c.y + i * 0.7 + 0.2, c.z, 3, 0.3, 0.2, 0.3, 0.02);
		}
		level.sendParticles(ParticleTypes.REVERSE_PORTAL, c.x, c.y + 0.9, c.z, 2, 0.35, 0.5, 0.35, 0.01);
	}

	private static void warpPlayers(ServerLevel fromLevel, BlockPos fromPos, ServerLevel toLevel, BlockPos toPos) {
		Vec3 centre = Vec3.atBottomCenterOf(fromPos).add(0, 0.9, 0);
		long now = fromLevel.getGameTime();
		for (ServerPlayer pl : fromLevel.players()) {
			if (pl.position().distanceToSqr(centre) > 1.4 * 1.4) {
				continue;
			}
			Long ready = ANCHOR_WARP_READY.get(pl.getUUID());
			if (ready != null && ready > now) {
				continue;
			}
			Vec3 from = pl.position();
			if (SafeTeleport.tryTeleport(pl, toLevel, Vec3.atBottomCenterOf(toPos).add(0, 0.1, 0))) {
				ANCHOR_WARP_READY.put(pl.getUUID(), toLevel.getGameTime() + 40);
				poof(fromLevel, from);
				poof(toLevel, pl.position());
				AbilityHelpers.sound(pl, SoundEvents.PORTAL_TRAVEL, 0.7f, 1.1f);
			}
		}
	}

	private static ResourceKey<Level> orDefault(ResourceKey<Level> key, ServerPlayer p) {
		return key != null ? key : p.level().dimension();
	}

	/**
	 * Cross-dimension portals and anchors were forming fine but never actually delivering anyone: the
	 * far endpoint's chunk was force-loaded once at creation to read/place it, but nothing kept it
	 * loaded afterwards. Once that one-off load expired the destination chunk unloaded again (nobody
	 * was standing there to keep it resident), so every later {@code hasChunkAt} check on it -- the
	 * first thing {@link SafeTeleport#isSafe} tests -- came back false and the gate simply never fired
	 * for that side. This is the same {@link TicketType#PORTAL} vanilla nether portals refresh on every
	 * use to keep the far side alive; re-adding it every tick both endpoints exist keeps them loaded
	 * for as long as the pair does, dimension travel included, and it quietly expires on its own
	 * (300 ticks) once we stop refreshing it.
	 */
	private static void keepLoaded(ServerLevel level, BlockPos pos) {
		level.getChunkSource().addRegionTicket(TicketType.PORTAL, new ChunkPos(pos), 3, pos);
	}

	/** Drop the tiny per-player portal-anchor warp cooldown map when a server stops. */
	public static void clearSessionState() {
		ANCHOR_WARP_READY.clear();
		BAMF_HIT.clear();
	}
}
