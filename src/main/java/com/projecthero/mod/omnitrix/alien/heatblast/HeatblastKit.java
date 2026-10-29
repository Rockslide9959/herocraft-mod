package com.projecthero.mod.omnitrix.alien.heatblast;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.omnitrix.OmnitrixSounds;
import com.projecthero.mod.omnitrix.alien.AlienKit;
import com.projecthero.mod.omnitrix.alien.AlienMove;
import com.projecthero.mod.omnitrix.alien.AlienStats;
import com.projecthero.mod.omnitrix.alien.Aliens;
import com.projecthero.mod.omnitrix.data.OmnitrixState;
import com.projecthero.mod.omnitrix.entity.AlienProjectileEntity;
import com.projecthero.mod.quirk.fx.QuirkCamera;
import com.projecthero.mod.quirk.fx.QuirkFx;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1: Heatblast, the Pyronite -- a magma-rock body with a flame for a head. Immune to fire and lava; water puts his fire
 * moves out.
 *
 * <pre>
 *   R        Fire Blast      a fast fire bolt (6 + 4 s alight)          Shift+R  Fire Stream     a 2.5 s flamethrower cone
 *   G        Pyro Bomb       a lobbed fireball, 3.5-block blast          Shift+G  Heat Shield     5 s: 60% less damage, burns attackers, melts arrows
 *   X        Fire Jet        hover / fly on jets of flame (toggle)       Shift+X  Jet Dash        a burst forward, scorching what it passes
 *   Z        Flame Ring      a ring of fire on the ground for 4 s       Shift+Z  Flare           a blinding white-hot flash
 *   V        Heat Aura       toggle: a searing aura -- creatures near catch fire, snow and ice melt, dropped items smelt
 *   C        Fireball Nova   a burst of fire all around (10)            Shift+C  Supernova       a 1.25 s charge, then a huge blast (18)
 *   Attack   a flaming punch (sets alight)
 *   Use      empty hand: a small fire bolt; empty hand on a block: melts it (sand to glass, ore to ingots, ice to water...);
 *            Shift + an item: smelts up to 8 of it in your hand
 * </pre>
 */
public final class HeatblastKit extends AlienKit {
	public static final String ID = "heatblast";
	public static final int COLOUR = 0xFF7A1A;

	public static final int ANIM_BLAST = 1;
	public static final int ANIM_STREAM = 2;
	public static final int ANIM_BOMB = 3;
	public static final int ANIM_SHIELD = 4;
	public static final int ANIM_DASH = 5;
	public static final int ANIM_RING = 6;
	public static final int ANIM_FLARE = 7;
	public static final int ANIM_NOVA = 8;
	public static final int ANIM_CHARGE = 9;
	public static final int ANIM_SUPERNOVA = 10;
	public static final int ANIM_PUNCH = 11;
	public static final int ANIM_SMELT = 12;

	public static final int F_FLYING = 1;
	public static final int F_SHIELD = 2;
	public static final int F_STREAM = 4;
	public static final int F_CHARGING = 8;

	public static final String FIRE_BLAST = "fire_blast";
	public static final String FIRE_STREAM = "fire_stream";
	public static final String PYRO_BOMB = "pyro_bomb";
	public static final String HEAT_SHIELD = "heat_shield";
	public static final String FIRE_JET = "fire_jet";
	public static final String JET_DASH = "jet_dash";
	public static final String FLAME_RING = "flame_ring";
	public static final String FLARE = "flare";
	public static final String NOVA = "fireball_nova";
	public static final String SUPERNOVA = "supernova";
	public static final String FIRE_BOLT = "fire_bolt";
	public static final String SMELT = "smelt";

	public static final float BLAST_DAMAGE = 6f;
	public static final float STREAM_DAMAGE = 2f;
	public static final int STREAM_TICKS = 50;
	public static final float BOMB_DAMAGE = 8f;
	public static final int SHIELD_TICKS = 100;
	public static final float SHIELD_FACTOR = 0.4f;
	public static final int RING_TICKS = 80;
	public static final float RING_RADIUS = 4f;
	public static final float RING_DAMAGE = 2f;
	public static final float NOVA_RADIUS = 6f;
	public static final float NOVA_DAMAGE = 10f;
	public static final float SUPERNOVA_RADIUS = 9f;
	public static final float SUPERNOVA_DAMAGE = 18f;
	public static final int SUPERNOVA_CHARGE = 25;
	public static final float DASH_DAMAGE = 4f;
	public static final float PUNCH_FIRE = 2f;
	public static final float FLY_SPEED = 0.07f;

	/** player -> where the Flame Ring burns (and in which dimension). */
	private static final Map<UUID, Ring> RINGS = new ConcurrentHashMap<>();
	/** player -> creatures the current Jet Dash already scorched. */
	private static final Map<UUID, java.util.Set<Integer>> DASH_HITS = new ConcurrentHashMap<>();

	private record Ring(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dim, Vec3 at) {
	}

	private HeatblastKit() {
	}

	public static void register() {
		Aliens.register(new HeatblastKit());
		AlienProjectileEntity.onHit("heatblast_bomb", (proj, hit) -> {
			if (proj.level() instanceof ServerLevel l) {
				Vec3 at = hit.getLocation();
				QuirkFx.blast(l, at).colours(0xFFF0B0, COLOUR).size(3.2f).life(18).spawn();
				QuirkFx.decal(l, at.add(0, 0.05, 0)).style(QuirkFx.DECAL_SCORCH).colour(0x1A120C).size(3f).life(200).spawn();
				OmnitrixSounds.play(l, at, OmnitrixSounds.HEATBLAST_NOVA, 1.2f, 1.2f);
				QuirkCamera.shakeAround(l, at, 12, 0.4f, 8);
			}
		});
		AlienProjectileEntity.onHit("heatblast_blast", (proj, hit) -> {
			if (proj.level() instanceof ServerLevel l) {
				l.sendParticles(ParticleTypes.FLAME, hit.getLocation().x, hit.getLocation().y, hit.getLocation().z, 12, 0.2, 0.2, 0.2, 0.06);
				l.sendParticles(ParticleTypes.LAVA, hit.getLocation().x, hit.getLocation().y, hit.getLocation().z, 2, 0.1, 0.1, 0.1, 0);
			}
		});
	}

	public static void clearSessionState() {
		RINGS.clear();
		DASH_HITS.clear();
	}

	@Override
	public String id() {
		return ID;
	}

	@Override
	public AlienStats stats() {
		return AlienStats.builder().health(26f).speed(1.05f).armor(4f, 0f).attack(2f).knockbackResist(0.1f).safeFall(4f).build();
	}

	@Override
	protected List<AlienMove> declareMoves() {
		return List.of(
				AlienMove.tap(FIRE_BLAST, AbilitySlot.SLOT_1, 12), AlienMove.shift(FIRE_STREAM, AbilitySlot.SLOT_1, 120),
				AlienMove.tap(PYRO_BOMB, AbilitySlot.SLOT_2, 80), AlienMove.shift(HEAT_SHIELD, AbilitySlot.SLOT_2, 300),
				AlienMove.tap(FIRE_JET, AbilitySlot.SLOT_3, 10), AlienMove.shift(JET_DASH, AbilitySlot.SLOT_3, 60),
				AlienMove.tap(FLAME_RING, AbilitySlot.SLOT_4, 280), AlienMove.shift(FLARE, AbilitySlot.SLOT_4, 240),
				AlienMove.tap(HEAT_AURA, AbilitySlot.SLOT_5, 60),
				AlienMove.tap(NOVA, AbilitySlot.SLOT_6, 400), AlienMove.shift(SUPERNOVA, AbilitySlot.SLOT_6, 900),
				// the vanilla keys' moves (no box on the HUD)
				AlienMove.vanilla("flaming_punch", "Attack", 10), AlienMove.vanilla(FIRE_BOLT, "Use", 10),
				AlienMove.vanilla(SMELT, "Use on a block / Shift+Use with an item", 20));
	}

	// ---------------------------------------------------------------- lifecycle

	@Override
	public void onTransform(ServerPlayer p) {
		ServerLevel l = level(p);
		OmnitrixSounds.play(l, p.position(), OmnitrixSounds.HEATBLAST_IGNITE, 1.0f, 1.0f);
		QuirkFx.ring(l, p.position().add(0, 0.05, 0)).colours(0xFFF0B0, COLOUR).size(3.5f).thickness(0.6f).life(16).ground().spawn();
		p.clearFire();
	}

	@Override
	public void onRevert(ServerPlayer p) {
		revertV(p);
		stopFlying(p);
		RINGS.remove(p.getUUID());
		DASH_HITS.remove(p.getUUID());
	}

	private static boolean wet(ServerPlayer p) {
		return p.isInWaterOrBubble() || p.isInWaterRainOrBubble() && p.level().isRainingAt(p.blockPosition().above());
	}

	/** Water puts Heatblast's fire out: a fire move refuses (steam and a hiss instead). */
	private boolean fizzles(ServerPlayer p) {
		if (!p.isInWaterOrBubble()) {
			return false;
		}
		ServerLevel l = level(p);
		l.sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 1.2, p.getZ(), 12, 0.3, 0.4, 0.3, 0.03);
		l.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.8f, 1.2f);
		p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.heatblast.wet").withStyle(ChatFormatting.AQUA), true);
		return true;
	}

	@Override
	public void tick(ServerPlayer p, OmnitrixState s, long now) {
		ServerLevel l = level(p);
		tickV(p, l, now);
		p.clearFire();
		if (p.isInLava()) {
			p.resetFallDistance();
		}
		// the flame head and the embers (others see the client-side flames; this is the server's share)
		if (p.tickCount % 6 == 0) {
			l.sendParticles(wet(p) ? ParticleTypes.CLOUD : ParticleTypes.FLAME, p.getX(), p.getY() + p.getBbHeight() + 0.1, p.getZ(), 1, 0.12, 0.05,
					0.12, 0.01);
		}
		// Fire Jet: flight
		if (flag(p, F_FLYING)) {
			if (!p.getAbilities().mayfly) {
				p.getAbilities().mayfly = true;
				p.onUpdateAbilities();
			}
			if (p.getAbilities().flying && p.tickCount % 2 == 0) {
				l.sendParticles(ParticleTypes.FLAME, p.getX(), p.getY() - 0.1, p.getZ(), 3, 0.12, 0.05, 0.12, 0.02);
				if (p.tickCount % 20 == 0) {
					OmnitrixSounds.play(l, p.position(), OmnitrixSounds.HEATBLAST_JET_LOOP, 1.0f, 1.0f);
				}
			}
			if (p.isInWaterOrBubble()) {
				stopFlying(p);
				fizzles(p);
			}
		}
		// Fire Stream
		long streamEnd = timer(p, "stream_end");
		if (streamEnd > 0) {
			if (now >= streamEnd || p.isInWaterOrBubble()) {
				setTimer(p, "stream_end", 0);
				setFlag(p, F_STREAM, false);
			} else if (now % 3 == 0) {
				stream(p, now);
			}
		}
		// Heat Shield
		long shieldEnd = timer(p, "shield_end");
		if (shieldEnd > 0) {
			if (now >= shieldEnd) {
				setTimer(p, "shield_end", 0);
				setFlag(p, F_SHIELD, false);
			} else {
				for (Projectile pr : l.getEntitiesOfClass(Projectile.class, p.getBoundingBox().inflate(2.5),
						pr -> pr.getOwner() != p && !(pr instanceof AlienProjectileEntity))) {
					l.sendParticles(ParticleTypes.FLAME, pr.getX(), pr.getY(), pr.getZ(), 8, 0.1, 0.1, 0.1, 0.04);
					pr.discard(); // arrows and fireballs burn up in the shield
				}
				if (now % 4 == 0) {
					for (int k = 0; k < 6; k++) {
						double a = (now * 0.4) + k * Math.PI / 3;
						l.sendParticles(ParticleTypes.FLAME, p.getX() + Math.cos(a) * 1.1, p.getY() + 0.2 + (k % 3) * 0.6, p.getZ() + Math.sin(a) * 1.1,
								1, 0, 0.05, 0, 0.01);
					}
				}
			}
		}
		// Flame Ring
		Ring ring = RINGS.get(p.getUUID());
		long ringEnd = timer(p, "ring_end");
		if (ring != null) {
			if (now >= ringEnd || ring.dim() != l.dimension()) {
				RINGS.remove(p.getUUID());
				setTimer(p, "ring_end", 0);
			} else {
				if (now % 3 == 0) {
					for (int k = 0; k < 10; k++) {
						double a = k * Math.PI * 2 / 10 + now * 0.05;
						l.sendParticles(ParticleTypes.FLAME, ring.at().x + Math.cos(a) * RING_RADIUS, ring.at().y + 0.1, ring.at().z + Math.sin(a) * RING_RADIUS,
								2, 0.1, 0.2, 0.1, 0.01);
					}
				}
				if (now % 10 == 0) {
					for (LivingEntity e : around(p, ring.at(), RING_RADIUS + 0.5)) {
						double d = Math.sqrt(e.distanceToSqr(ring.at().x, e.getY(), ring.at().z));
						if (Math.abs(d - RING_RADIUS) < 1.6 || d < RING_RADIUS) {
							hurt(p, e, RING_DAMAGE, true);
							e.igniteForSeconds(3);
						}
					}
				}
			}
		}
		// Jet Dash: scorch what it passes
		long dashEnd = timer(p, "dash_end");
		if (dashEnd > 0) {
			if (now >= dashEnd) {
				setTimer(p, "dash_end", 0);
				DASH_HITS.remove(p.getUUID());
			} else {
				java.util.Set<Integer> hits = DASH_HITS.computeIfAbsent(p.getUUID(), k -> new java.util.HashSet<>());
				for (LivingEntity e : around(p, p.position().add(0, 1, 0), 1.8)) {
					if (hits.add(e.getId())) {
						hurt(p, e, DASH_DAMAGE, true);
						e.igniteForSeconds(4);
					}
				}
				l.sendParticles(ParticleTypes.FLAME, p.getX(), p.getY() + 1.0, p.getZ(), 8, 0.3, 0.4, 0.3, 0.02);
			}
		}
		// Supernova's charge
		long nova = timer(p, "supernova_at");
		if (nova > 0) {
			if (now >= nova) {
				setTimer(p, "supernova_at", 0);
				setFlag(p, F_CHARGING, false);
				supernovaBlast(p);
			} else {
				p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 4, 3, false, false, false));
				if (now % 2 == 0) {
					double r = 2.5 * (nova - now) / (double) SUPERNOVA_CHARGE;
					for (int k = 0; k < 8; k++) {
						double a = k * Math.PI / 4 + now * 0.3;
						l.sendParticles(ParticleTypes.FLAME, p.getX() + Math.cos(a) * r, p.getY() + 1.0, p.getZ() + Math.sin(a) * r, 1, 0, 0, 0, 0);
					}
				}
			}
		}
	}

	// ---------------------------------------------------------------- keys

	@Override
	public void press(ServerPlayer p, AbilitySlot slot, boolean shift) {
		switch (slot) {
			case SLOT_1 -> {
				if (shift) {
					fireStream(p);
				} else {
					fireBlast(p);
				}
			}
			case SLOT_2 -> {
				if (shift) {
					heatShield(p);
				} else {
					pyroBomb(p);
				}
			}
			case SLOT_3 -> {
				if (shift) {
					jetDash(p);
				} else {
					fireJet(p);
				}
			}
			case SLOT_4 -> {
				if (shift) {
					flare(p);
				} else {
					flameRing(p);
				}
			}
			case SLOT_5 -> vKey(p, shift); // v0.17.1 (user: "add abilities to the V key for every alien")
			case SLOT_6 -> {
				if (shift) {
					supernova(p);
				} else {
					nova(p);
				}
			}
			default -> {
			}
		}
	}

	public boolean fireBlast(ServerPlayer p) {
		if (fizzles(p) || !use(p, FIRE_BLAST)) {
			return false;
		}
		anim(p, ANIM_BLAST);
		AlienProjectileEntity.of(p, "heatblast_blast").colour(COLOUR).size(0.45f).damage(BLAST_DAMAGE).fire(4).speed(1.8).life(50).spawn();
		OmnitrixSounds.play(level(p), p.position(), OmnitrixSounds.HEATBLAST_BLAST, 0.9f, 0.9f + p.getRandom().nextFloat() * 0.2f);
		return true;
	}

	public boolean fireStream(ServerPlayer p) {
		if (fizzles(p) || !use(p, FIRE_STREAM)) {
			return false;
		}
		anim(p, ANIM_STREAM);
		setTimer(p, "stream_end", p.level().getGameTime() + STREAM_TICKS);
		setFlag(p, F_STREAM, true);
		OmnitrixSounds.play(level(p), p.position(), OmnitrixSounds.HEATBLAST_STREAM, 1.0f, 1.0f);
		return true;
	}

	private void stream(ServerPlayer p, long now) {
		ServerLevel l = level(p);
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getLookAngle();
		Vec3 palm = eye.add(look.scale(0.7)).add(0, -0.3, 0);
		if (now % 6 == 0) {
			QuirkFx.cone(l, palm, look).colours(0xFFF4C0, 0xFF4A10).size(1.6f).length(7f).life(10).spawn();
		}
		if (now % 12 == 0) {
			OmnitrixSounds.play(l, palm, OmnitrixSounds.HEATBLAST_STREAM, 0.6f, 1.1f);
		}
		for (LivingEntity e : around(p, eye.add(look.scale(3.5)), 5.0)) {
			Vec3 to = e.position().add(0, e.getBbHeight() * 0.5, 0).subtract(eye);
			double dist = to.length();
			if (dist <= 7.5 && to.normalize().dot(look) > Math.cos(Math.toRadians(30))) {
				hurt(p, e, STREAM_DAMAGE, true);
				e.igniteForSeconds(3);
			}
		}
		// the stream lights the ground it plays over
		var hit = l.clip(new net.minecraft.world.level.ClipContext(eye, eye.add(look.scale(7)), net.minecraft.world.level.ClipContext.Block.COLLIDER,
				net.minecraft.world.level.ClipContext.Fluid.NONE, p));
		if (hit.getType() == HitResult.Type.BLOCK && now % 9 == 0) {
			BlockPos above = hit.getBlockPos().relative(hit.getDirection());
			if (l.getBlockState(above).isAir() && Blocks.FIRE.defaultBlockState().canSurvive(l, above)
					&& l.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_MOBGRIEFING)) {
				l.setBlock(above, Blocks.FIRE.defaultBlockState(), 11);
			}
		}
	}

	public boolean pyroBomb(ServerPlayer p) {
		if (fizzles(p) || !use(p, PYRO_BOMB)) {
			return false;
		}
		anim(p, ANIM_BOMB);
		AlienProjectileEntity.of(p, "heatblast_bomb").colour(0xFF5A10).size(0.75f).damage(BOMB_DAMAGE * 0.5f).blast(3.5f, BOMB_DAMAGE).fire(5)
				.gravity(0.045).speed(1.15).life(80).spawn();
		OmnitrixSounds.play(level(p), p.position(), OmnitrixSounds.HEATBLAST_BLAST, 1.0f, 0.7f);
		return true;
	}

	public boolean heatShield(ServerPlayer p) {
		if (fizzles(p) || !use(p, HEAT_SHIELD)) {
			return false;
		}
		anim(p, ANIM_SHIELD);
		setTimer(p, "shield_end", p.level().getGameTime() + SHIELD_TICKS);
		setFlag(p, F_SHIELD, true);
		ServerLevel l = level(p);
		OmnitrixSounds.play(l, p.position(), OmnitrixSounds.HEATBLAST_SHIELD, 1.0f, 1.0f);
		QuirkFx.ring(l, p.position().add(0, 1.0, 0)).colours(0xFFF0B0, COLOUR).size(2.2f).thickness(0.5f).life(14).spawn();
		return true;
	}

	public boolean fireJet(ServerPlayer p) {
		if (!use(p, FIRE_JET)) {
			return false;
		}
		if (flag(p, F_FLYING)) {
			stopFlying(p);
			return true;
		}
		if (fizzles(p)) {
			return false;
		}
		setFlag(p, F_FLYING, true);
		p.getAbilities().mayfly = true;
		p.getAbilities().flying = true;
		p.getAbilities().setFlyingSpeed(FLY_SPEED);
		p.onUpdateAbilities();
		p.push(0, 0.45, 0);
		p.hurtMarked = true;
		ServerLevel l = level(p);
		OmnitrixSounds.play(l, p.position(), OmnitrixSounds.HEATBLAST_JET, 1.0f, 1.0f);
		QuirkFx.ring(l, p.position().add(0, 0.05, 0)).colours(0xFFF0B0, COLOUR).size(2.0f).thickness(0.4f).life(10).ground().spawn();
		return true;
	}

	private void stopFlying(ServerPlayer p) {
		if (!flag(p, F_FLYING)) {
			return;
		}
		setFlag(p, F_FLYING, false);
		if (!p.isCreative() && !p.isSpectator()) {
			p.getAbilities().mayfly = false;
			p.getAbilities().flying = false;
		}
		p.getAbilities().setFlyingSpeed(0.05f);
		p.onUpdateAbilities();
		p.resetFallDistance();
	}

	public boolean jetDash(ServerPlayer p) {
		if (fizzles(p) || !use(p, JET_DASH)) {
			return false;
		}
		anim(p, ANIM_DASH);
		Vec3 look = p.getLookAngle();
		p.setDeltaMovement(look.scale(1.8).add(0, 0.15, 0));
		p.hurtMarked = true;
		p.resetFallDistance();
		setTimer(p, "dash_end", p.level().getGameTime() + 8);
		DASH_HITS.put(p.getUUID(), new java.util.HashSet<>());
		ServerLevel l = level(p);
		QuirkFx.trail(l, p).colours(0xFFF0B0, COLOUR).size(0.5f).size2(10f).life(20).vec(new Vec3(0, 1.0, 0)).style(QuirkFx.RIBBON_FLAME).spawn();
		OmnitrixSounds.play(l, p.position(), OmnitrixSounds.HEATBLAST_JET, 1.0f, 1.3f);
		return true;
	}

	public boolean flameRing(ServerPlayer p) {
		if (fizzles(p) || !use(p, FLAME_RING)) {
			return false;
		}
		anim(p, ANIM_RING);
		RINGS.put(p.getUUID(), new Ring(p.level().dimension(), p.position()));
		setTimer(p, "ring_end", p.level().getGameTime() + RING_TICKS);
		ServerLevel l = level(p);
		QuirkFx.ring(l, p.position().add(0, 0.05, 0)).colours(0xFFF0B0, 0xFF3A08).size(RING_RADIUS).thickness(0.7f).life(RING_TICKS).ground().spawn();
		QuirkFx.decal(l, p.position().add(0, 0.05, 0)).style(QuirkFx.DECAL_SCORCH).colour(0x1A120C).size(RING_RADIUS + 1f).life(RING_TICKS + 100).spawn();
		OmnitrixSounds.play(l, p.position(), OmnitrixSounds.HEATBLAST_NOVA, 0.8f, 1.4f);
		return true;
	}

	public boolean flare(ServerPlayer p) {
		if (fizzles(p) || !use(p, FLARE)) {
			return false;
		}
		anim(p, ANIM_FLARE);
		ServerLevel l = level(p);
		Vec3 c = p.position().add(0, 1.4, 0);
		l.sendParticles(ParticleTypes.FLASH, c.x, c.y, c.z, 2, 0.2, 0.2, 0.2, 0);
		QuirkFx.blast(l, c).colours(0xFFFFFF, 0xFFE070).size(2.0f).life(10).flags(QuirkFx.F_NO_SMOKE | QuirkFx.F_NO_RING).spawn();
		for (LivingEntity e : around(p, c, 8.0)) {
			e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0));
			e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1));
			hurt(p, e, 3f, true);
			e.igniteForSeconds(2);
		}
		OmnitrixSounds.play(l, c, OmnitrixSounds.HEATBLAST_IGNITE, 1.0f, 1.6f);
		return true;
	}

	public boolean nova(ServerPlayer p) {
		if (fizzles(p) || !use(p, NOVA)) {
			return false;
		}
		anim(p, ANIM_NOVA);
		burst(p, NOVA_RADIUS, NOVA_DAMAGE, 1.2, 6, 1.0f);
		return true;
	}

	public boolean supernova(ServerPlayer p) {
		if (fizzles(p) || !use(p, SUPERNOVA)) {
			return false;
		}
		anim(p, ANIM_CHARGE);
		setFlag(p, F_CHARGING, true);
		setTimer(p, "supernova_at", p.level().getGameTime() + SUPERNOVA_CHARGE);
		OmnitrixSounds.play(level(p), p.position(), OmnitrixSounds.HEATBLAST_SHIELD, 1.0f, 0.6f);
		return true;
	}

	private void supernovaBlast(ServerPlayer p) {
		anim(p, ANIM_SUPERNOVA);
		burst(p, SUPERNOVA_RADIUS, SUPERNOVA_DAMAGE, 1.8, 8, 1.8f);
	}

	private void burst(ServerPlayer p, float radius, float damage, double push, int fireSecs, float scale) {
		ServerLevel l = level(p);
		Vec3 c = p.position().add(0, 1.0, 0);
		QuirkFx.blast(l, c).colours(0xFFF0B0, COLOUR).size(radius * 0.7f).life(22).spawn();
		QuirkFx.ring(l, p.position().add(0, 0.05, 0)).colours(0xFFFFFF, COLOUR).size(radius).thickness(0.8f).life(16).ground().spawn();
		QuirkFx.decal(l, p.position().add(0, 0.05, 0)).style(QuirkFx.DECAL_SCORCH).colour(0x1A120C).size(radius * 0.8f).life(300).spawn();
		OmnitrixSounds.play(l, c, OmnitrixSounds.HEATBLAST_NOVA, 1.4f, 1.0f / scale + 0.2f);
		QuirkCamera.shakeAround(l, c, radius * 2.5, 0.35f * scale, 12);
		for (LivingEntity e : around(p, c, radius)) {
			double d = Math.sqrt(e.distanceToSqr(c));
			hurt(p, e, (float) (damage * (1.0 - 0.4 * Math.min(1.0, d / radius))), true);
			e.igniteForSeconds(fireSecs);
			shove(e, c, push, 0.45);
		}
	}

	// ---------------------------------------------------------------- vanilla keys

	@Override
	public boolean onAttack(ServerPlayer p, Entity target) {
		if (target instanceof LivingEntity le && !fizzlesQuiet(p)) {
			anim(p, ANIM_PUNCH);
			if (ready(p, "flaming_punch")) {
				cooldown(p, "flaming_punch", 10);
				le.igniteForSeconds(4);
				le.invulnerableTime = 0;
				hurt(p, le, PUNCH_FIRE, true);
				level(p).sendParticles(ParticleTypes.FLAME, le.getX(), le.getY() + le.getBbHeight() * 0.6, le.getZ(), 10, 0.2, 0.3, 0.2, 0.04);
				le.invulnerableTime = 0; // the fist itself still lands this tick
			}
		}
		return false;
	}

	private static boolean fizzlesQuiet(ServerPlayer p) {
		return p.isInWaterOrBubble();
	}

	@Override
	public InteractionResult onUse(ServerPlayer p, InteractionHand hand) {
		ItemStack held = p.getItemInHand(hand);
		if (held.isEmpty() && hand == InteractionHand.MAIN_HAND) {
			if (fizzles(p) || !use(p, FIRE_BOLT)) {
				return InteractionResult.FAIL;
			}
			anim(p, ANIM_BLAST);
			AlienProjectileEntity.of(p, "heatblast_bolt").colour(0xFFA040).size(0.28f).damage(3f).fire(2).speed(1.6).life(30).spawn();
			OmnitrixSounds.play(level(p), p.position(), OmnitrixSounds.HEATBLAST_BLAST, 0.5f, 1.4f);
			p.swing(hand, true);
			return InteractionResult.SUCCESS;
		}
		if (p.isShiftKeyDown() && !held.isEmpty()) {
			return smelt(p, hand) ? InteractionResult.SUCCESS : InteractionResult.PASS;
		}
		return InteractionResult.PASS;
	}

	/** Shift + use with an item: smelts up to 8 of the held stack, like a furnace would. */
	public boolean smelt(ServerPlayer p, InteractionHand hand) {
		ItemStack held = p.getItemInHand(hand);
		ServerLevel l = level(p);
		Optional<RecipeHolder<SmeltingRecipe>> recipe = l.getRecipeManager().getRecipeFor(RecipeType.SMELTING, new SingleRecipeInput(held), l);
		if (recipe.isEmpty()) {
			return false;
		}
		if (fizzles(p) || !use(p, SMELT)) {
			return true;
		}
		anim(p, ANIM_SMELT);
		int n = Math.min(8, held.getCount());
		ItemStack out = recipe.get().value().getResultItem(l.registryAccess()).copy();
		out.setCount(out.getCount() * n);
		held.shrink(n);
		if (!p.getInventory().add(out)) {
			p.drop(out, false);
		}
		p.giveExperiencePoints(Math.max(1, Math.round(recipe.get().value().getExperience() * n)));
		l.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.FURNACE_FIRE_CRACKLE, SoundSource.PLAYERS, 1.0f, 1.0f);
		l.sendParticles(ParticleTypes.FLAME, p.getX(), p.getY() + 1.0, p.getZ(), 16, 0.3, 0.3, 0.3, 0.03);
		p.swing(hand, true);
		return true;
	}

	@Override
	public InteractionResult onUseBlock(ServerPlayer p, InteractionHand hand, BlockHitResult hit) {
		if (hand != InteractionHand.MAIN_HAND || !p.getMainHandItem().isEmpty()) {
			return InteractionResult.PASS;
		}
		return melt(p, hit.getBlockPos()) ? InteractionResult.SUCCESS : InteractionResult.PASS;
	}

	/** What a meltable block becomes (null: not meltable). Ores drop their smelted ingot. */
	public static BlockState meltsInto(BlockState s) {
		if (s.is(Blocks.SAND) || s.is(Blocks.RED_SAND)) {
			return Blocks.GLASS.defaultBlockState();
		}
		if (s.is(Blocks.ICE) || s.is(Blocks.PACKED_ICE) || s.is(Blocks.BLUE_ICE) || s.is(Blocks.FROSTED_ICE)) {
			return Blocks.WATER.defaultBlockState();
		}
		if (s.is(Blocks.SNOW_BLOCK) || s.is(Blocks.SNOW) || s.is(Blocks.POWDER_SNOW)) {
			return Blocks.AIR.defaultBlockState();
		}
		if (s.is(Blocks.COBBLESTONE)) {
			return Blocks.STONE.defaultBlockState();
		}
		if (s.is(Blocks.STONE) || s.is(Blocks.DEEPSLATE) || s.is(Blocks.COBBLED_DEEPSLATE)) {
			return Blocks.MAGMA_BLOCK.defaultBlockState();
		}
		if (s.is(Blocks.MAGMA_BLOCK) || s.is(Blocks.OBSIDIAN)) {
			return Blocks.LAVA.defaultBlockState();
		}
		if (s.is(Blocks.CLAY)) {
			return Blocks.TERRACOTTA.defaultBlockState();
		}
		if (s.is(Blocks.WET_SPONGE)) {
			return Blocks.SPONGE.defaultBlockState();
		}
		return null;
	}

	private static ItemStack oreDrop(BlockState s) {
		if (s.is(Blocks.IRON_ORE) || s.is(Blocks.DEEPSLATE_IRON_ORE)) {
			return new ItemStack(Items.IRON_INGOT);
		}
		if (s.is(Blocks.GOLD_ORE) || s.is(Blocks.DEEPSLATE_GOLD_ORE) || s.is(Blocks.NETHER_GOLD_ORE)) {
			return new ItemStack(Items.GOLD_INGOT);
		}
		if (s.is(Blocks.COPPER_ORE) || s.is(Blocks.DEEPSLATE_COPPER_ORE)) {
			return new ItemStack(Items.COPPER_INGOT, 2);
		}
		if (s.is(Blocks.ANCIENT_DEBRIS)) {
			return new ItemStack(Items.NETHERITE_SCRAP);
		}
		return ItemStack.EMPTY;
	}

	/** Use on a block (empty hand): melts it. True if anything happened. */
	public boolean melt(ServerPlayer p, BlockPos pos) {
		ServerLevel l = level(p);
		BlockState s = l.getBlockState(pos);
		BlockState into = meltsInto(s);
		ItemStack drop = into == null ? oreDrop(s) : ItemStack.EMPTY;
		if (into == null && drop.isEmpty()) {
			return false;
		}
		if (fizzles(p) || !use(p, SMELT)) {
			return true;
		}
		if (!p.mayInteract(l, pos) || !p.mayBuild()) {
			return true;
		}
		anim(p, ANIM_SMELT);
		if (into != null) {
			l.setBlock(pos, into, Block.UPDATE_ALL);
		} else {
			l.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
			ItemEntity it = new ItemEntity(l, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, drop);
			l.addFreshEntity(it);
		}
		l.playSound(null, pos, SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS, 0.8f, 1.2f);
		l.sendParticles(ParticleTypes.LAVA, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 4, 0.3, 0.1, 0.3, 0);
		l.sendParticles(ParticleTypes.FLAME, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 12, 0.4, 0.4, 0.4, 0.02);
		p.swing(InteractionHand.MAIN_HAND, true);
		return true;
	}

	// ---------------------------------------------------------------- damage

	/** Fire, lava, magma floors, burning: nothing. */
	@Override
	public boolean allowDamage(ServerPlayer p, DamageSource source, float amount) {
		if (source.is(DamageTypeTags.IS_FIRE) || source.is(DamageTypes.HOT_FLOOR) || source.is(DamageTypes.LAVA)) {
			p.clearFire();
			return false;
		}
		return true;
	}

	@Override
	public float incomingDamage(ServerPlayer p, DamageSource source, float amount) {
		if (flag(p, F_SHIELD)) {
			if (source.getDirectEntity() instanceof LivingEntity attacker && attacker != p && source.getDirectEntity() == source.getEntity()) {
				attacker.igniteForSeconds(3);
				hurt(p, attacker, 2f, true);
			}
			return amount * SHIELD_FACTOR;
		}
		return amount;
	}

	// ---------------------------------------------------------------- v0.17.1 (user: "add abilities to the V key for every alien"): V Heat Aura

	public static final String HEAT_AURA = "heat_aura";
	public static final int ANIM_AURA = 13;
	public static final int F_AURA = 16;
	public static final double AURA_RADIUS = 3.5;
	public static final float AURA_DAMAGE = 1f;
	/** An item the aura already smelted (so cobblestone stops at stone). */
	private static final String AURA_TAG = "projecthero_heat_aura";

	private void vKey(ServerPlayer p, boolean shift) {
		heatAura(p);
	}

	/**
	 * V: Heat Aura on / off. On, he burns white-hot: every creature within 3.5 blocks catches fire (and takes 1 a second), snow
	 * and ice round him melt, and items dropped near him come out smelted, like a walking furnace. Water puts it out.
	 */
	public boolean heatAura(ServerPlayer p) {
		boolean on = !flag(p, F_AURA);
		if (on && fizzles(p)) {
			return false;
		}
		if (!use(p, HEAT_AURA)) {
			return false;
		}
		setFlag(p, F_AURA, on);
		anim(p, ANIM_AURA);
		ServerLevel l = level(p);
		if (on) {
			QuirkFx.ring(l, p.position().add(0, 0.05, 0)).colours(0xFFF0B0, COLOUR).size((float) AURA_RADIUS * 2).thickness(0.5f).life(14).ground().spawn();
			OmnitrixSounds.play(l, p.position(), OmnitrixSounds.HEATBLAST_IGNITE, 1.0f, 0.8f);
		} else {
			l.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.6f, 1.4f);
		}
		p.displayClientMessage(Component.translatable(on ? "message.projecthero.omnitrix.heatblast.aura_on" : "message.projecthero.omnitrix.heatblast.aura_off")
				.withStyle(on ? ChatFormatting.GOLD : ChatFormatting.GRAY), true);
		return true;
	}

	private void revertV(ServerPlayer p) {
		setFlag(p, F_AURA, false);
	}

	private void tickV(ServerPlayer p, ServerLevel l, long now) {
		if (!flag(p, F_AURA)) {
			return;
		}
		if (p.isInWaterOrBubble()) {
			setFlag(p, F_AURA, false);
			fizzles(p);
			return;
		}
		if (now % 2 == 0) {
			double a = now * 0.35;
			for (int k = 0; k < 3; k++) {
				double b = a + k * Math.PI * 2 / 3;
				l.sendParticles(ParticleTypes.FLAME, p.getX() + Math.cos(b) * 1.2, p.getY() + 0.2 + (now % 8) * 0.2, p.getZ() + Math.sin(b) * 1.2, 1, 0, 0.03, 0, 0.01);
			}
			l.sendParticles(ParticleTypes.SMALL_FLAME, p.getX(), p.getY() + 1.0, p.getZ(), 2, AURA_RADIUS * 0.4, 0.4, AURA_RADIUS * 0.4, 0.0);
		}
		if (now % 10 == 0) {
			for (LivingEntity e : around(p, p.position().add(0, 1, 0), AURA_RADIUS)) {
				e.igniteForSeconds(3);
				if (now % 20 == 0) {
					hurt(p, e, AURA_DAMAGE, true);
				}
			}
			for (net.minecraft.world.entity.item.ItemEntity it : l.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
					p.getBoundingBox().inflate(AURA_RADIUS), it -> it.isAlive() && !it.getTags().contains(AURA_TAG))) {
				ItemStack held = it.getItem();
				Optional<RecipeHolder<SmeltingRecipe>> recipe = l.getRecipeManager().getRecipeFor(RecipeType.SMELTING, new SingleRecipeInput(held), l);
				if (recipe.isEmpty()) {
					continue;
				}
				ItemStack out = recipe.get().value().getResultItem(l.registryAccess()).copy();
				if (out.isEmpty()) {
					continue;
				}
				out.setCount(Math.min(out.getMaxStackSize(), out.getCount() * held.getCount()));
				it.setItem(out);
				it.addTag(AURA_TAG);
				l.sendParticles(ParticleTypes.FLAME, it.getX(), it.getY() + 0.2, it.getZ(), 6, 0.1, 0.1, 0.1, 0.02);
				l.sendParticles(ParticleTypes.SMOKE, it.getX(), it.getY() + 0.3, it.getZ(), 3, 0.1, 0.1, 0.1, 0.01);
			}
		}
		if (now % 20 == 5 && p.mayBuild()) {
			BlockPos c = p.blockPosition();
			BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
			for (int dx = -3; dx <= 3; dx++) {
				for (int dy = -1; dy <= 2; dy++) {
					for (int dz = -3; dz <= 3; dz++) {
						m.set(c.getX() + dx, c.getY() + dy, c.getZ() + dz);
						net.minecraft.world.level.block.state.BlockState st = l.getBlockState(m);
						if (st.is(net.minecraft.world.level.block.Blocks.SNOW) || st.is(net.minecraft.world.level.block.Blocks.SNOW_BLOCK)
								|| st.is(net.minecraft.world.level.block.Blocks.POWDER_SNOW)) {
							l.setBlockAndUpdate(m, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
						} else if (st.is(net.minecraft.world.level.block.Blocks.ICE) || st.is(net.minecraft.world.level.block.Blocks.FROSTED_ICE)) {
							l.setBlockAndUpdate(m, l.dimensionType().ultraWarm() ? net.minecraft.world.level.block.Blocks.AIR.defaultBlockState()
									: net.minecraft.world.level.block.Blocks.WATER.defaultBlockState());
						} else {
							continue;
						}
						l.sendParticles(ParticleTypes.CLOUD, m.getX() + 0.5, m.getY() + 0.6, m.getZ() + 0.5, 2, 0.2, 0.1, 0.2, 0.01);
					}
				}
			}
		}
		if (now % 40 == 0) {
			l.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.FIRE_AMBIENT, SoundSource.PLAYERS, 0.6f, 0.8f);
		}
	}
}
