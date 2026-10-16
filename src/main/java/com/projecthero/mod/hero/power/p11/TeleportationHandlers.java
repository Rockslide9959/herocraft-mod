package com.projecthero.mod.hero.power.p11;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.projecthero.mod.combat.HeroTargets;
import com.projecthero.mod.hero.AbilityContext;
import com.projecthero.mod.hero.AbilityHandler;
import com.projecthero.mod.hero.AbilityHandlers;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.ExperimentalPowers;
import com.projecthero.mod.hero.HeroConfig;
import com.projecthero.mod.hero.Power;
import com.projecthero.mod.hero.Powers;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.hero.power.SafeTeleport;
import com.projecthero.mod.hero.revamp.batcha.BatchA;
import com.projecthero.mod.hero.revamp.d.BatchDFx;
import com.projecthero.mod.hero.visual.MutationVisuals;
import com.projecthero.mod.squad.Squads;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Power 11 -- Teleportation (v0.16.5 rework, docs/V0165_POWERS_PROPOSAL.md section 4): Nightcrawler-style combat
 * teleporting on a <b>Strain</b> meter instead of long cooldowns. Six keys, each with a Shift version; no H / N.
 *
 * <p>Strain (0-100): every jump adds strain, which drains at 10 a second. Reaching 100 overloads you: Nausea and 4 s
 * with no teleports. Only Rift (V) keeps a real cooldown (60 s).
 *
 * <p>R Blink (25 blocks, 15) / Shift+R Long Blink (hold to paint up to 75, release; 35). G Bamf Strike (chain of up to
 * 4, 40) / Shift+G Swap (20). X Escape Blink (10 back + Resistance III, 15) / Shift+X Sky Blink (15 up + slow-fall,
 * 15). Z Phase Step (walls up to 3 thick, 20) / Shift+Z Displace (target 15 up, no bosses, 30). V Rift (5 s charge,
 * coordinate screen, one linked gate pair; merges the old Portal + Portal Anchor) / Shift+V Mark / Recall (recall 25).
 * C Spatial Fold (v0.16.5 user: held -- a fold round you that sends projectiles back at their shooter and folds
 * enemies who come close back out through space; 10 to raise, 18 a second while held) / Shift+C Group Jump (the next Blink takes
 * squadmates within 5 blocks). Every jump leaves the purple puff + afterimage and 1 s of fall-damage immunity.
 *
 * <p>All six slots are HOLD so the handlers own their tap / Shift / release logic (the router gates nothing).
 * All destinations are validated by {@link SafeTeleport}.
 */
public final class TeleportationHandlers {
	public static final String KEY = "power_11_teleportation";

	// ---- Strain ----
	public static final float MAX_STRAIN = 100.0f;
	/** 10 strain a second. */
	public static final float STRAIN_DRAIN_PER_TICK = 10.0f / 20.0f;
	public static final int OVERLOAD_TICKS = 4 * 20;
	/** How long the overload's Nausea lasts (spec: "Nausea"; length decided here). */
	private static final int OVERLOAD_NAUSEA_TICKS = 5 * 20;
	/** No fall damage for the first second after any jump. */
	public static final int JUMP_GRACE_TICKS = 20;

	public static final float STRAIN_BLINK = 15.0f;
	public static final float STRAIN_LONG_BLINK = 35.0f;
	public static final float STRAIN_BAMF = 40.0f;
	public static final float STRAIN_SWAP = 20.0f;
	public static final float STRAIN_ESCAPE = 15.0f;
	public static final float STRAIN_SKY = 15.0f;
	public static final float STRAIN_PHASE = 20.0f;
	public static final float STRAIN_DISPLACE = 30.0f;
	public static final float STRAIN_RECALL = 25.0f;
	/** Spatial Fold (held): this much to raise it, then this much a second while it is held (strain drains 10). */
	public static final float STRAIN_FOLD = 10.0f;
	public static final float STRAIN_FOLD_PER_SECOND = 18.0f;
	/** Enemies who come this close are folded back out to {@link #FOLD_PUSH_TO} blocks. */
	public static final double FOLD_KEEP_OUT = 3.0;
	public static final double FOLD_PUSH_TO = 6.0;
	/** v0.16.5 (user): the first hit you land within this long after any teleport does double damage. */
	public static final int AMBUSH_TICKS = 20;
	public static final float AMBUSH_MULTIPLIER = 2.0f;

	// ---- ranges / timings ----
	public static final double BLINK_RANGE = 25.0;
	/** v0.16.5 (user): 150 blocks. */
	public static final double LONG_BLINK_RANGE = 150.0;
	public static final double ESCAPE_DISTANCE = 10.0;
	public static final double SKY_DISTANCE = 15.0;
	private static final int SKY_SLOW_FALL_TICKS = 6 * 20;
	/** Phase Step crosses walls up to 3 blocks thick: the wall must start within reach and the far side be clear. */
	private static final double PHASE_REACH = 4.5;
	public static final double DISPLACE_RANGE = 30.0;
	public static final double DISPLACE_HEIGHT = 15.0;
	public static final int FOLD_TICKS = 3 * 20;
	/** Group Jump stays armed this long waiting for the next Blink. */
	public static final int GROUP_JUMP_TICKS = 10 * 20;
	public static final double GROUP_JUMP_RADIUS = 5.0;

	/** Rift (V): hold this long to arm the destination picker. */
	public static final int RIFT_CHARGE_TICKS = 5 * 20;
	public static final int RIFT_CD = 60 * 20;
	/** Ticks for a rift gate to finish forming out of particles. */
	private static final int RIFT_FORM_TICKS = 60;

	/** Per-player cooldown between rift warps, so you do not ping-pong. */
	private static final Map<UUID, Long> RIFT_WARP_READY = new HashMap<>();

	private TeleportationHandlers() {
	}

	// ================================================================ shared helpers

	private static float res(ServerPlayer p, String name) {
		return BatchA.res(p, KEY, name);
	}

	private static void set(ServerPlayer p, String name, float v) {
		BatchA.set(p, KEY, name, v);
	}

	private static Power power() {
		return Powers.byKey(KEY);
	}

	public static float strain(ServerPlayer p) {
		return res(p, "strain");
	}

	public static boolean overloaded(ServerPlayer p) {
		return res(p, "overload") > 0.5f;
	}

	/** True for the first second after any jump (fall damage is ignored). */
	public static boolean inJumpGrace(ServerPlayer p) {
		return res(p, "jump_grace") > 0.5f;
	}

	public static boolean foldActive(ServerPlayer p) {
		return res(p, "fold") > 0.5f;
	}

	public static boolean groupJumpArmed(ServerPlayer p) {
		return res(p, "group_jump") > 0.5f;
	}

	/** Refuses (with the overload message) while overloaded. */
	private static boolean canJump(AbilityContext ctx) {
		if (overloaded(ctx.player())) {
			ctx.actionBar("message.projecthero.teleport.overloaded");
			return false;
		}
		return true;
	}

	/**
	 * A jump happened: add its strain and start the 1 s fall-damage grace. The jump that fills the meter still
	 * lands; then you overload.
	 */
	public static void addStrain(ServerPlayer p, float amount) {
		set(p, "jump_grace", JUMP_GRACE_TICKS);
		set(p, "ambush", AMBUSH_TICKS); // v0.16.5: the first hit within 1 s of a teleport does double damage
		strainOnly(p, amount);
	}

	/** Strain without a jump (no fall grace): the held Spatial Fold. */
	private static void strainOnly(ServerPlayer p, float amount) {
		float s = Math.min(MAX_STRAIN, strain(p) + amount);
		set(p, "strain", s);
		if (s >= MAX_STRAIN - 0.01f && !overloaded(p)) {
			set(p, "overload", OVERLOAD_TICKS);
			set(p, "aiming", 0);
			MutationVisuals.stopIf(p, "channel_right");
			p.addEffect(new MobEffectInstance(MobEffects.CONFUSION, OVERLOAD_NAUSEA_TICKS, 0, false, false, true));
			p.displayClientMessage(Component.translatable("message.projecthero.teleport.overloaded"), true);
			AbilityHelpers.sound(p, SoundEvents.ENDERMAN_HURT, 0.9f, 0.6f);
		}
	}

	private static void poof(ServerLevel level, Vec3 at) {
		level.sendParticles(ParticleTypes.PORTAL, at.x, at.y + 1, at.z, 30, 0.3, 0.6, 0.3, 0.4);
		level.sendParticles(ParticleTypes.REVERSE_PORTAL, at.x, at.y + 1, at.z, 20, 0.3, 0.6, 0.3, 0.1);
		BatchDFx.puff(level, at);
	}

	/** The departure point gets the smoke puff plus a brief body-shaped afterimage of the teleporter. */
	private static void depart(ServerLevel level, Vec3 at, float yaw) {
		poof(level, at);
		BatchDFx.silhouette(level, at, yaw, BatchDFx.TELE_GLOW);
	}

	/** Both ends of a successful self-jump: smoke + afterimage behind, smoke on arrival, a pose and the bamf. */
	private static void jumped(ServerPlayer p, ServerLevel from, Vec3 fromPos, float yaw, String anim, float pitch) {
		depart(from, fromPos, yaw);
		poof((ServerLevel) p.level(), p.position());
		MutationVisuals.play(p, anim);
		AbilityHelpers.sound(p, SoundEvents.ENDERMAN_TELEPORT, 1.0f, pitch);
	}

	/** Turns the player's camera toward {@code at} (a server-side lookAt alone never reaches the client). */
	private static void face(ServerPlayer p, Vec3 at) {
		p.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, at);
		p.connection.teleport(p.getX(), p.getY(), p.getZ(), p.getYRot(), p.getXRot());
	}

	/** Where a blink aimed right now would land: on top of the block being looked at, else max range. */
	private static Vec3 blinkDest(ServerPlayer p, double range) {
		BlockHitResult bhr = AbilityHelpers.raycastBlock(p, range);
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
		return p.position().add(p.getLookAngle().scale(range));
	}

	// ================================================================ registration

	public static void register() {
		// R -- Blink (tap) / Long Blink (Shift: hold to paint, release to jump). Also runs the power-wide upkeep.
		AbilityHandlers.register(KEY, "blink", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				ServerPlayer p = ctx.player();
				if (!canJump(ctx)) {
					return;
				}
				if (p.isShiftKeyDown()) {
					set(p, "aiming", 1);
					MutationVisuals.play(p, "channel_right");
					return;
				}
				// v0.16.5 (user): walked up to a wall and aiming at it -- Blink goes through it (up to 3 blocks thick)
				if (wallInFront(p, ctx.level()) && phaseThrough(ctx, STRAIN_BLINK) == 0) {
					return;
				}
				blinkTo(ctx, blinkDest(p, BLINK_RANGE), BLINK_RANGE, STRAIN_BLINK);
			}

			@Override
			public void onServerTick(AbilityContext ctx) {
				upkeep(ctx);
				ServerPlayer p = ctx.player();
				if (res(p, "aiming") < 0.5f) {
					return;
				}
				MutationVisuals.ensure(p, "channel_right");
				Vec3 d = blinkDest(p, LONG_BLINK_RANGE);
				if (p.tickCount % 2 == 0) {
					ctx.level().sendParticles(ParticleTypes.PORTAL, d.x, d.y + 0.1, d.z, 14, 0.35, 0.1, 0.35, 0.05);
					ctx.level().sendParticles(ParticleTypes.END_ROD, d.x, d.y + 0.9, d.z, 3, 0.08, 0.45, 0.08, 0.0);
				}
			}

			@Override
			public void onRelease(AbilityContext ctx) {
				ServerPlayer p = ctx.player();
				if (res(p, "aiming") < 0.5f) {
					return;
				}
				set(p, "aiming", 0);
				MutationVisuals.stopIf(p, "channel_right");
				if (canJump(ctx)) {
					blinkTo(ctx, blinkDest(p, LONG_BLINK_RANGE), LONG_BLINK_RANGE, STRAIN_LONG_BLINK);
				}
			}
		});

		// G -- Bamf Strike (tap) / Swap (Shift)
		AbilityHandlers.register(KEY, "bamf_strike", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				if (!canJump(ctx)) {
					return;
				}
				if (ctx.player().isShiftKeyDown()) {
					swap(ctx);
				} else {
					bamfStart(ctx);
				}
			}

			@Override
			public void onServerTick(AbilityContext ctx) {
				bamfTick(ctx);
			}
		});

		// X -- Escape Blink (tap) / Sky Blink (Shift)
		AbilityHandlers.register(KEY, "escape_blink", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				if (!canJump(ctx)) {
					return;
				}
				ServerPlayer p = ctx.player();
				ServerLevel level = ctx.level();
				Vec3 from = p.position();
				float yaw = p.getYRot();
				if (p.isShiftKeyDown()) {
					if (SafeTeleport.blink(p, new Vec3(0, 1, 0), SKY_DISTANCE)) {
						p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, SKY_SLOW_FALL_TICKS, 0, false, false, true));
						jumped(p, level, from, yaw, "leap", 1.5f);
						addStrain(p, STRAIN_SKY);
					} else {
						ctx.actionBar("message.projecthero.teleport.no_room");
					}
					return;
				}
				Vec3 back = p.getLookAngle().multiply(1, 0, 1);
				back = back.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : back.normalize().reverse();
				if (SafeTeleport.blink(p, back, ESCAPE_DISTANCE)) {
					p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 20, 2, false, false, false));
					jumped(p, level, from, yaw, "leap", 1.6f);
					addStrain(p, STRAIN_ESCAPE);
				} else {
					ctx.actionBar("message.projecthero.teleport.no_room");
				}
			}
		});

		// Z -- Phase Step (tap) / Displace (Shift)
		AbilityHandlers.register(KEY, "phase_step", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				if (!canJump(ctx)) {
					return;
				}
				if (ctx.player().isShiftKeyDown()) {
					displace(ctx);
				} else {
					phaseStep(ctx);
				}
			}
		});

		// V -- Rift (tap: 5 s charge, then the coordinate screen; tap again while a pair is open to close it) /
		// Mark / Recall (Shift)
		AbilityHandlers.register(KEY, "rift", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				ServerPlayer p = ctx.player();
				if (p.isShiftKeyDown()) {
					markRecall(ctx);
					return;
				}
				if (ExperimentalPowers.getMarker(p, ctx.power(), "rift_a") != null) {
					closeRift(p, ctx.power());
					ctx.actionBar("message.projecthero.teleport.rift_closed");
					AbilityHelpers.sound(p, SoundEvents.ENDERMAN_DEATH, 0.8f, 1.2f);
					return;
				}
				if (!ctx.cooldownReady()) {
					ctx.actionBar("message.projecthero.ability.on_cooldown", Component.translatable(ctx.ability().nameKey()),
							String.format(java.util.Locale.ROOT, "%.0f", Math.ceil(ctx.cooldownRemaining() / 20.0f)));
					return;
				}
				set(p, "rift_charge_start", p.level().getGameTime());
				MutationVisuals.play(p, "channel_two_hand");
			}

			@Override
			public void onServerTick(AbilityContext ctx) {
				riftChargeTick(ctx);
				riftPairTick(ctx);
			}

			@Override
			public void onRelease(AbilityContext ctx) {
				ServerPlayer p = ctx.player();
				float start = res(p, "rift_charge_start");
				if (start <= 0.5f) {
					return;
				}
				long held = p.level().getGameTime() - (long) start;
				set(p, "rift_charge_start", 0);
				set(p, "rift_charge", 0);
				MutationVisuals.stopIf(p, "channel_two_hand");
				if (held >= RIFT_CHARGE_TICKS) {
					MutationVisuals.play(p, "cast_raise_both");
					net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(p,
							new com.projecthero.mod.network.PortalPickerPayload(p.getX(), p.getY(), p.getZ()));
					AbilityHelpers.sound(p, SoundEvents.PORTAL_TRIGGER, 0.7f, 1.4f);
				} else {
					AbilityHelpers.sound(p, SoundEvents.AMETHYST_BLOCK_BREAK, 0.6f, 0.8f);
				}
			}
		});

		// C -- Spatial Fold (tap) / Group Jump (Shift)
		AbilityHandlers.register(KEY, "spatial_fold", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				ServerPlayer p = ctx.player();
				if (p.isShiftKeyDown()) {
					set(p, "group_jump", GROUP_JUMP_TICKS);
					ctx.actionBar("message.projecthero.teleport.group_jump");
					MutationVisuals.play(p, "point_right");
					BatchDFx.ring(ctx.level(), p.position().add(0, 0.1, 0), GROUP_JUMP_RADIUS, BatchDFx.TELE_GLOW, 28, 0);
					AbilityHelpers.sound(p, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0f, 0.8f);
					return;
				}
				if (!canJump(ctx) || res(p, "fold_held") > 0.5f) {
					return;
				}
				// v0.16.5 (user): held -- it stays up while C is held, building strain
				set(p, "fold_held", 1);
				set(p, "fold", 2);
				strainOnly(p, STRAIN_FOLD);
				MutationVisuals.play(p, "cast_raise_both");
				AbilityHelpers.sound(p, SoundEvents.ENDERMAN_TELEPORT, 0.8f, 0.6f);
				AbilityHelpers.sound(p, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0f, 1.2f);
			}

			@Override
			public void onRelease(AbilityContext ctx) {
				dropFold(ctx.player());
			}
		});

		// v0.16.5: the ambush -- double damage on the first hit within 1 s of a teleport
		net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.ALLOW_DAMAGE.register(TeleportationHandlers::ambush);

		// Sneak + right-click a rift gate to close the pair.
		UseBlockCallback.EVENT.register((player, level, hand, hitResult) -> {
			if (level.isClientSide || !(player instanceof ServerPlayer sp) || !player.isShiftKeyDown()) {
				return InteractionResult.PASS;
			}
			Power power = power();
			if (power == null || !ExperimentalPowers.owns(sp, power)) {
				return InteractionResult.PASS;
			}
			BlockPos clicked = hitResult.getBlockPos();
			BlockPos a = ExperimentalPowers.getMarker(sp, power, "rift_a");
			BlockPos b = ExperimentalPowers.getMarker(sp, power, "rift_b");
			if ((a != null && a.distSqr(clicked) <= 9.0) || (b != null && b.distSqr(clicked) <= 9.0)) {
				closeRift(sp, power);
				sp.displayClientMessage(Component.translatable("message.projecthero.teleport.rift_closed"), true);
				AbilityHelpers.sound(sp, SoundEvents.ENDERMAN_DEATH, 0.8f, 1.2f);
				return InteractionResult.SUCCESS;
			}
			return InteractionResult.PASS;
		});
	}

	// ================================================================ upkeep (runs every tick for an owner)

	private static void upkeep(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		float s = strain(p);
		if (s > 0) {
			set(p, "strain", Math.max(0, s - STRAIN_DRAIN_PER_TICK));
		}
		for (String timer : new String[] { "overload", "jump_grace", "group_jump", "ambush" }) {
			float v = res(p, timer);
			if (v > 0) {
				set(p, timer, v - 1);
			}
		}
		if (res(p, "fold_held") > 0.5f) {
			if (overloaded(p) || !p.isAlive()) {
				dropFold(p); // overloaded: the fold collapses
			} else {
				set(p, "fold", 2);
				strainOnly(p, STRAIN_FOLD_PER_SECOND / 20.0f);
				MutationVisuals.ensure(p, "cast_raise_both");
			}
		}
		float fold = res(p, "fold");
		if (fold > 0) {
			set(p, "fold", fold - 1);
			foldTick(p, ctx.level());
		}
		if (p.tickCount % 100 == 23) {
			dropLegacyState(p, ctx.power());
		}
	}

	/** Pre-v0.16.5 portal / anchor markers are inert now -- clear them out of old saves. */
	private static void dropLegacyState(ServerPlayer p, Power power) {
		for (String m : new String[] { "anchor_a", "anchor_b", "portal_a", "portal_b" }) {
			if (ExperimentalPowers.getMarker(p, power, m) != null) {
				ExperimentalPowers.clearMarker(p, power, m);
			}
		}
	}

	// ================================================================ R: Blink / Long Blink (+ Group Jump)

	private static void blinkTo(AbilityContext ctx, Vec3 dest, double range, float strain) {
		ServerPlayer p = ctx.player();
		ServerLevel level = ctx.level();
		Vec3 from = p.position();
		float yaw = p.getYRot();
		List<ServerPlayer> group = groupJumpArmed(p) ? groupMates(p) : List.of();
		if (SafeTeleport.tryTeleport(p, dest) || SafeTeleport.blink(p, p.getLookAngle(), range)) {
			jumped(p, level, from, yaw, "dash_forward", 1.2f);
			addStrain(p, strain);
			if (groupJumpArmed(p)) {
				set(p, "group_jump", 0);
				bringGroup(p, level, from, group);
			}
		} else {
			ctx.actionBar("message.projecthero.teleport.no_room");
		}
	}

	private static List<ServerPlayer> groupMates(ServerPlayer p) {
		List<ServerPlayer> out = new java.util.ArrayList<>();
		for (ServerPlayer other : ((ServerLevel) p.level()).players()) {
			if (other != p && other.isAlive() && !other.isSpectator() && Squads.areAllies(p, other)
					&& other.distanceToSqr(p) <= GROUP_JUMP_RADIUS * GROUP_JUMP_RADIUS) {
				out.add(other);
			}
		}
		return out;
	}

	/** Squadmates keep their offset from you where it is safe; otherwise they land at your side. */
	private static void bringGroup(ServerPlayer p, ServerLevel level, Vec3 origin, List<ServerPlayer> group) {
		Vec3 here = p.position();
		for (ServerPlayer mate : group) {
			Vec3 mateFrom = mate.position();
			float yaw = mate.getYRot();
			Vec3 offset = mateFrom.subtract(origin);
			Vec3[] tries = { here.add(offset), here.add(1.2, 0, 0), here.add(-1.2, 0, 0), here.add(0, 0, 1.2),
					here.add(0, 0, -1.2), here };
			for (Vec3 t : tries) {
				if (SafeTeleport.tryTeleport(mate, t)) {
					depart(level, mateFrom, yaw);
					poof(level, mate.position());
					break;
				}
			}
		}
	}

	// ================================================================ G: Bamf Strike / Swap

	private static final int BAMF_MAX_TARGETS = 4;
	private static final float BAMF_DAMAGE = 11.0f;
	private static final double BAMF_RANGE = 30.0;
	private static final double BAMF_CHAIN_RANGE = 10.0;
	private static final int BAMF_HOP_TICKS = 6;
	/** caster -> ids already struck in the running chain. */
	private static final Map<UUID, List<Integer>> BAMF_HIT = new HashMap<>();

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

	private static boolean bamfTarget(ServerPlayer p, LivingEntity t) {
		return t != null && HeroTargets.canHarm(p, t) && !Squads.areAllies(p, t)
				&& !(t instanceof com.projecthero.mod.hero.revamp.d.MirrorImageEntity);
	}

	private static void bamfStart(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		if (BAMF_HIT.containsKey(p.getUUID())) {
			return; // a chain is already running
		}
		LivingEntity t = AbilityHelpers.raycastEntity(p, BAMF_RANGE);
		if (!bamfTarget(p, t)) {
			ctx.actionBar("message.projecthero.teleport.no_target");
			return;
		}
		if (!bamfHop(ctx.level(), p, t, 0)) {
			ctx.actionBar("message.projecthero.teleport.no_room");
			return;
		}
		List<Integer> hit = new java.util.ArrayList<>();
		hit.add(t.getId());
		BAMF_HIT.put(p.getUUID(), hit);
		set(p, "bamf_timer", BAMF_HOP_TICKS);
		addStrain(p, STRAIN_BAMF);
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
		set(p, "jump_grace", JUMP_GRACE_TICKS);
		return true;
	}

	private static void bamfTick(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		List<Integer> hit = BAMF_HIT.get(p.getUUID());
		if (hit == null) {
			return;
		}
		float timer = res(p, "bamf_timer") - 1;
		if (timer > 0) {
			set(p, "bamf_timer", timer);
			return;
		}
		if (hit.size() >= BAMF_MAX_TARGETS || !p.isAlive()) {
			bamfEnd(p);
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
			bamfEnd(p);
			return;
		}
		hit.add(next.getId());
		set(p, "bamf_timer", BAMF_HOP_TICKS);
	}

	private static void bamfEnd(ServerPlayer p) {
		BAMF_HIT.remove(p.getUUID());
		set(p, "bamf_timer", 0);
	}

	/** Test hook: how many targets the running chain has struck (0 when none is running). */
	public static int bamfChainLength(ServerPlayer p) {
		List<Integer> hit = BAMF_HIT.get(p.getUUID());
		return hit == null ? 0 : hit.size();
	}

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
			AbilityHelpers.applyControl(mob, MobEffects.MOVEMENT_SLOWDOWN, 30, 1);
			mob.getNavigation().stop();
		}
		depart(level, mine, yaw);
		BatchDFx.silhouette(level, theirs, t.getYRot(), BatchDFx.TELE_GLOW);
		poof(level, p.position());
		BatchDFx.tether(level, mine.add(0, 1, 0), theirs.add(0, 1, 0), BatchDFx.TELE_GLOW, 24);
		MutationVisuals.play(p, "clap");
		AbilityHelpers.sound(p, SoundEvents.ENDERMAN_TELEPORT, 1.0f, 0.8f);
		AbilityHelpers.sound(p, SoundEvents.CHORUS_FRUIT_TELEPORT, 1.0f, 1.2f);
		addStrain(p, STRAIN_SWAP);
	}

	// ================================================================ Z: Phase Step / Displace

	/**
	 * Steps through the wall in front of you (horizontal facing). The wall has to start within 1.5 blocks and the
	 * first clear spot behind it must be within {@link #PHASE_REACH} -- i.e. walls up to 3 blocks thick.
	 */
	private static void phaseStep(AbilityContext ctx) {
		int r = phaseThrough(ctx, STRAIN_PHASE);
		if (r != 0) {
			ctx.actionBar(r == 2 ? "message.projecthero.teleport.no_room" : "message.projecthero.teleport.no_wall");
		}
	}

	/**
	 * Through the wall in front of you: 0 = went through, 1 = no wall within 1.5 blocks, 2 = a wall but no room behind it
	 * within {@link #PHASE_REACH} (walls up to 3 blocks thick). Phase Step and a Blink aimed into a wall both use it.
	 */
	private static int phaseThrough(AbilityContext ctx, float strain) {
		ServerPlayer p = ctx.player();
		ServerLevel level = ctx.level();
		Vec3 dir = p.getLookAngle().multiply(1, 0, 1);
		if (dir.lengthSqr() < 1.0e-4) {
			return 1;
		}
		dir = dir.normalize();
		Vec3 origin = p.position();
		boolean inWall = false;
		for (double d = 0.5; d <= PHASE_REACH; d += 0.25) {
			Vec3 c = origin.add(dir.scale(d));
			if (!SafeTeleport.isSafe(level, p, c)) {
				inWall = true;
			} else if (inWall) {
				float yaw = p.getYRot();
				if (SafeTeleport.tryTeleport(p, c)) {
					jumped(p, level, origin, yaw, "dash_forward", 1.4f);
					addStrain(p, strain);
					return 0;
				}
				return 2;
			} else if (d > 1.5) {
				break;
			}
		}
		return inWall ? 2 : 1;
	}

	/**
	 * A wall you have walked up to (its face within about a block, roughly level look) that you are aiming at -- a wall
	 * further off still stops a Blink short, as it always has.
	 */
	private static boolean wallInFront(ServerPlayer p, ServerLevel level) {
		if (Math.abs(p.getXRot()) > 50f) {
			return false;
		}
		BlockHitResult bhr = AbilityHelpers.raycastBlock(p, 2.0);
		if (bhr == null || bhr.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK || bhr.getDirection().getAxis().isVertical()) {
			return false;
		}
		Vec3 dir = p.getLookAngle().multiply(1, 0, 1);
		if (dir.lengthSqr() < 1.0e-4) {
			return false;
		}
		dir = dir.normalize();
		return !SafeTeleport.isSafe(level, p, p.position().add(dir.scale(0.9)));
	}

	/** Gametests: whether the ambush window (double damage on the next hit) is open. */
	public static boolean ambushReady(ServerPlayer p) {
		return res(p, "ambush") > 0.5f;
	}

	private static final ThreadLocal<Boolean> AMBUSH_REENTRANT = ThreadLocal.withInitial(() -> false);

	/**
	 * v0.16.5 (user: "right after teleporting the first hit you deal after 1 seconds deals double damage"): within 1 s
	 * of any teleport, the first hit a teleporter lands is re-issued at double damage (and the window closes).
	 */
	private static boolean ambush(LivingEntity victim, net.minecraft.world.damagesource.DamageSource source, float amount) {
		if (AMBUSH_REENTRANT.get() || amount <= 0f || !(source.getEntity() instanceof ServerPlayer p) || victim == p) {
			return true;
		}
		Power power = power();
		if (power == null || !ExperimentalPowers.owns(p, power) || res(p, "ambush") < 0.5f) {
			return true;
		}
		set(p, "ambush", 0);
		AMBUSH_REENTRANT.set(true);
		try {
			victim.hurt(source, amount * AMBUSH_MULTIPLIER);
		} finally {
			AMBUSH_REENTRANT.set(false);
		}
		if (victim.level() instanceof ServerLevel sl) {
			sl.sendParticles(ParticleTypes.CRIT, victim.getX(), victim.getY() + victim.getBbHeight() * 0.6, victim.getZ(), 16, 0.3, 0.3, 0.3, 0.3);
			sl.sendParticles(ParticleTypes.REVERSE_PORTAL, victim.getX(), victim.getY() + victim.getBbHeight() * 0.6, victim.getZ(), 12, 0.3, 0.3,
					0.3, 0.1);
		}
		AbilityHelpers.sound(p, SoundEvents.PLAYER_ATTACK_CRIT, 1.0f, 0.8f);
		return false;
	}

	/** Whether Displace may move {@code t}: a harmable non-ally that is not a boss. */
	public static boolean canDisplace(ServerPlayer p, LivingEntity t) {
		return t != null && HeroTargets.canHarm(p, t) && !Squads.areAllies(p, t) && AbilityHelpers.isValidGrabTarget(t, p)
				&& !com.projecthero.mod.titanshifter.TitanCombat.isBoss(t);
	}

	private static void displace(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		ServerLevel level = ctx.level();
		LivingEntity t = AbilityHelpers.raycastEntity(p, DISPLACE_RANGE);
		if (!canDisplace(p, t)) {
			ctx.actionBar("message.projecthero.teleport.no_target");
			return;
		}
		Vec3 from = t.position();
		Vec3 dest = null;
		for (double h = DISPLACE_HEIGHT; h >= 3.0; h -= 1.0) {
			Vec3 c = from.add(0, h, 0);
			if (c.y < level.getMaxBuildHeight() - 2 && level.noCollision(t, t.getBoundingBox().move(0, h, 0))) {
				dest = c;
				break;
			}
		}
		if (dest == null) {
			ctx.actionBar("message.projecthero.teleport.no_room");
			return;
		}
		if (t instanceof ServerPlayer tp) {
			tp.teleportTo(dest.x, dest.y, dest.z);
			tp.connection.resetPosition();
		} else {
			t.teleportTo(dest.x, dest.y, dest.z);
		}
		t.setDeltaMovement(Vec3.ZERO);
		t.hurtMarked = true;
		BatchDFx.silhouette(level, from, t.getYRot(), BatchDFx.TELE_GLOW);
		poof(level, from);
		poof(level, dest);
		MutationVisuals.play(p, "point_right");
		AbilityHelpers.sound(p, SoundEvents.ENDERMAN_TELEPORT, 1.0f, 0.7f);
		addStrain(p, STRAIN_DISPLACE);
	}

	// ================================================================ C: Spatial Fold

	/**
	 * While the fold is up, any projectile about to hit you (not your own) is teleported back at whoever fired it:
	 * it reappears two blocks in front of the shooter flying straight at them, and becomes yours. A projectile with
	 * no living shooter simply vanishes.
	 */
	/** C released (or the fold collapsed): it drops. */
	public static void dropFold(ServerPlayer p) {
		if (res(p, "fold_held") < 0.5f) {
			return;
		}
		set(p, "fold_held", 0);
		set(p, "fold", 0);
		MutationVisuals.stopIf(p, "cast_raise_both");
		AbilityHelpers.sound(p, SoundEvents.ENDERMAN_TELEPORT, 0.5f, 1.4f);
	}

	/** Whether the held Spatial Fold is up (gametests). */
	public static boolean foldHeld(ServerPlayer p) {
		return res(p, "fold_held") > 0.5f;
	}

	private static void foldTick(ServerPlayer p, ServerLevel level) {
		// v0.16.5 (user): "stop enemies from getting close to you" -- anyone hostile who comes inside the fold is folded
		// back out through space (a boss, or one with nowhere to land, is thrown back instead)
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(FOLD_KEEP_OUT),
				e -> e != p && e.isAlive() && com.projecthero.mod.combat.HeroTargets.canHarm(p, e))) {
			if (e.distanceTo(p) > FOLD_KEEP_OUT + e.getBbWidth() * 0.5) {
				continue;
			}
			Vec3 away = new Vec3(e.getX() - p.getX(), 0, e.getZ() - p.getZ());
			away = away.lengthSqr() < 1.0e-4 ? p.getLookAngle().multiply(1, 0, 1) : away;
			away = away.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : away.normalize();
			Vec3 from = e.position();
			Vec3 to = p.position().add(away.scale(FOLD_PUSH_TO)).add(0, e.getY() - p.getY(), 0);
			boolean boss = e.getMaxHealth() >= 300f;
			if (!boss && level.noCollision(e, e.getBoundingBox().move(to.subtract(from)))) {
				e.teleportTo(to.x, to.y, to.z);
				e.setDeltaMovement(away.scale(0.3));
				e.hurtMarked = true;
				poof(level, from);
				poof(level, to);
			} else {
				e.setDeltaMovement(away.scale(1.4).add(0, 0.3, 0));
				e.hurtMarked = true;
				level.sendParticles(ParticleTypes.REVERSE_PORTAL, e.getX(), e.getY() + 1, e.getZ(), 16, 0.3, 0.5, 0.3, 0.1);
			}
			if (e instanceof net.minecraft.world.entity.Mob m && m.getTarget() == p) {
				m.getNavigation().stop();
			}
		}
		if (p.tickCount % 3 == 0) {
			BatchDFx.ring(level, p.position().add(0, 1.0, 0), 1.3, BatchDFx.TELE_GLOW, 14, p.tickCount * 0.3);
		}
		AABB box = p.getBoundingBox().inflate(3.5);
		for (Projectile proj : level.getEntitiesOfClass(Projectile.class, box, e -> e.isAlive() && e.getOwner() != p)) {
			Vec3 v = proj.getDeltaMovement();
			Vec3 toMe = p.getBoundingBox().getCenter().subtract(proj.position());
			if (v.lengthSqr() < 1.0e-3 || v.dot(toMe) <= 0) {
				continue; // stuck in the ground, or flying away from you
			}
			foldBack(p, level, proj);
		}
	}

	/** Sends one projectile back at its shooter. Public for the damage-rule fallback. */
	public static void foldBack(ServerPlayer p, ServerLevel level, Projectile proj) {
		Entity shooter = proj.getOwner();
		Vec3 at = proj.position();
		poof(level, at.subtract(0, 1, 0));
		if (!(shooter instanceof LivingEntity ls) || !ls.isAlive() || ls.level() != level || ls == p) {
			proj.discard();
			return;
		}
		Vec3 target = ls.position().add(0, ls.getBbHeight() * 0.6, 0);
		Vec3 dir = target.subtract(p.position().add(0, 1, 0));
		dir = dir.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : dir.normalize();
		Vec3 start = target.subtract(dir.scale(2.0));
		double speed = Math.max(1.0, proj.getDeltaMovement().length());
		proj.setOwner(p);
		proj.moveTo(start.x, start.y, start.z, proj.getYRot(), proj.getXRot());
		proj.setDeltaMovement(dir.scale(speed));
		proj.hurtMarked = true;
		level.sendParticles(ParticleTypes.REVERSE_PORTAL, start.x, start.y, start.z, 16, 0.2, 0.2, 0.2, 0.05);
		AbilityHelpers.sound(p, SoundEvents.CHORUS_FRUIT_TELEPORT, 0.8f, 1.5f);
	}

	// ================================================================ V: Rift / Mark & Recall

	private static void markRecall(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		BlockPos mark = ExperimentalPowers.getMarker(p, ctx.power(), "return");
		if (mark == null) {
			ExperimentalPowers.setMarker(p, ctx.power(), "return", p.blockPosition());
			ctx.actionBar("message.projecthero.teleport.mark_placed");
			MutationVisuals.play(p, "summon_ground");
			BatchDFx.ring(ctx.level(), p.position().add(0, 0.1, 0), 0.8, BatchDFx.TELE_GLOW, 12, 0);
			AbilityHelpers.burst(ctx.level(), p.position(), ParticleTypes.REVERSE_PORTAL, 20, 0.3);
			return;
		}
		if (!canJump(ctx)) {
			return;
		}
		var dimKey = ExperimentalPowers.getMarkerDimension(p, ctx.power(), "return");
		ServerLevel target = (dimKey == null || p.getServer() == null) ? ctx.level() : p.getServer().getLevel(dimKey);
		if (target == null) {
			target = ctx.level();
		}
		ServerLevel originLevel = ctx.level();
		Vec3 from = p.position();
		float yaw = p.getYRot();
		ExperimentalPowers.clearMarker(p, ctx.power(), "return");
		if (SafeTeleport.tryTeleport(p, target, Vec3.atBottomCenterOf(mark))) {
			jumped(p, originLevel, from, yaw, "flex", 0.9f);
			addStrain(p, STRAIN_RECALL);
		} else {
			ctx.actionBar("message.projecthero.teleport.mark_blocked");
		}
	}

	private static void riftChargeTick(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		float start = res(p, "rift_charge_start");
		if (start <= 0.5f) {
			return;
		}
		long held = p.level().getGameTime() - (long) start;
		if (held < 0 || held > RIFT_CHARGE_TICKS + 60) {
			set(p, "rift_charge_start", 0);
			set(p, "rift_charge", 0);
			return;
		}
		double frac = Math.min(1.0, held / (double) RIFT_CHARGE_TICKS);
		set(p, "rift_charge", (float) (frac * 100.0));
		MutationVisuals.ensure(p, "channel_two_hand");
		if (p.tickCount % 2 == 0) {
			BatchDFx.ring(ctx.level(), p.position().add(0, 0.1, 0), 0.6 + frac, BatchDFx.TELE_GLOW, 10, held * 0.2);
		}
		ctx.level().sendParticles(ParticleTypes.PORTAL, p.getX(), p.getY() + 1.0, p.getZ(),
				4 + (int) (frac * 20), 0.5 + frac, 0.9, 0.5 + frac, 0.05);
		if (held % 10 == 0) {
			AbilityHelpers.sound(p, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.7f, 0.5f + (float) frac);
		}
		if (held >= RIFT_CHARGE_TICKS && held % 20 == 0) {
			p.displayClientMessage(Component.translatable("message.projecthero.teleport.rift_ready"), true);
		}
	}

	/**
	 * Server side of the picker screen: open the linked gate pair -- one where the caster stands ({@code rift_a}) and
	 * one at the chosen coordinates ({@code rift_b}), in any dimension. One pair at a time; it stays until V is tapped
	 * again, a gate is sneak + right-clicked ({@link #closeRift}) or the power is lost (markers clear on revoke).
	 */
	public static void createDestinationPortal(ServerPlayer p, int x, int y, int z, String dimension) {
		Power power = power();
		if (power == null || !ExperimentalPowers.owns(p, power)) {
			return;
		}
		if (ExperimentalPowers.getMarker(p, power, "rift_a") != null) {
			return; // a pair is already open
		}
		if (!ExperimentalPowers.cooldownReady(p, power, power.ability(AbilitySlot.SLOT_5))) {
			return;
		}
		ResourceLocation dim = ResourceLocation.tryParse(dimension);
		if (dim == null) {
			dim = p.level().dimension().location();
		}
		ExperimentalPowers.setMarker(p, power, "rift_a", p.blockPosition(), p.level().dimension().location());
		ExperimentalPowers.setMarker(p, power, "rift_b", new BlockPos(x, y, z), dim);
		set(p, "rift_form", 0);
		ExperimentalPowers.triggerCooldown(p, power, power.ability(AbilitySlot.SLOT_5),
				HeroConfig.get().scaledCooldown(RIFT_CD));
		AbilityHelpers.sound(p, SoundEvents.PORTAL_TRAVEL, 0.6f, 1.2f);
		p.displayClientMessage(Component.translatable("message.projecthero.teleport.rift_open"), true);
	}

	/** Tick both gates of the caster's open rift: form them, draw them, and carry players through. */
	private static void riftPairTick(AbilityContext ctx) {
		ServerPlayer owner = ctx.player();
		Power power = ctx.power();
		BlockPos a = ExperimentalPowers.getMarker(owner, power, "rift_a");
		BlockPos b = ExperimentalPowers.getMarker(owner, power, "rift_b");
		if (a == null || b == null || owner.getServer() == null) {
			return;
		}
		ResourceKey<Level> dimA = orDefault(ExperimentalPowers.getMarkerDimension(owner, power, "rift_a"), owner);
		ResourceKey<Level> dimB = orDefault(ExperimentalPowers.getMarkerDimension(owner, power, "rift_b"), owner);
		ServerLevel levelA = owner.getServer().getLevel(dimA);
		ServerLevel levelB = owner.getServer().getLevel(dimB);
		if (levelA == null || levelB == null) {
			return;
		}
		keepLoaded(levelA, a);
		keepLoaded(levelB, b);
		float form = Math.min(RIFT_FORM_TICKS, res(owner, "rift_form") + 1.0f);
		set(owner, "rift_form", form);
		double frac = form / RIFT_FORM_TICKS;
		renderGate(levelA, a, frac);
		renderGate(levelB, b, frac);
		if (frac >= 0.99) {
			warpPlayers(levelA, a, levelB, b);
			if (!(a.equals(b) && dimA == dimB)) {
				warpPlayers(levelB, b, levelA, a);
			}
		}
	}

	private static void renderGate(ServerLevel level, BlockPos at, double frac) {
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

	public static void closeRift(ServerPlayer p, Power power) {
		ExperimentalPowers.clearMarker(p, power, "rift_a");
		ExperimentalPowers.clearMarker(p, power, "rift_b");
		set(p, "rift_form", 0);
	}

	private static void warpPlayers(ServerLevel fromLevel, BlockPos fromPos, ServerLevel toLevel, BlockPos toPos) {
		Vec3 centre = Vec3.atBottomCenterOf(fromPos).add(0, 0.9, 0);
		long now = fromLevel.getGameTime();
		for (ServerPlayer pl : List.copyOf(fromLevel.players())) {
			if (pl.position().distanceToSqr(centre) > 1.4 * 1.4) {
				continue;
			}
			Long ready = RIFT_WARP_READY.get(pl.getUUID());
			if (ready != null && ready > now) {
				continue;
			}
			Vec3 from = pl.position();
			if (SafeTeleport.tryTeleport(pl, toLevel, Vec3.atBottomCenterOf(toPos).add(0, 0.1, 0))) {
				RIFT_WARP_READY.put(pl.getUUID(), toLevel.getGameTime() + 40);
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
	 * Keeps both gates' chunks loaded while the pair exists, so cross-dimension rifts keep delivering: the same
	 * {@link TicketType#PORTAL} vanilla nether portals refresh on use; it expires on its own once we stop.
	 */
	private static void keepLoaded(ServerLevel level, BlockPos pos) {
		level.getChunkSource().addRegionTicket(TicketType.PORTAL, new ChunkPos(pos), 3, pos);
	}

	/** Drop the per-player rift warp cooldowns and running Bamf chains when a server stops. */
	public static void clearSessionState() {
		RIFT_WARP_READY.clear();
		BAMF_HIT.clear();
	}
}
