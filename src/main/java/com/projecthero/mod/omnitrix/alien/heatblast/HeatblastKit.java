package com.projecthero.mod.omnitrix.alien.heatblast;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.joml.Vector3f;

import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.power.p08.PyroFxEntity;
import com.projecthero.mod.hero.power.p08.PyrokinesisHandlers;
import com.projecthero.mod.omnitrix.OmnitrixSfx;
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
import net.minecraft.core.particles.DustParticleOptions;
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
 *   R        Fire Blast      a fast fire bolt (12 + 4 s alight)         Shift+R  Fire Stream     a 2.5 s flamethrower cone (4 a pulse)
 *   G        Pyro Bomb       a lobbed fireball (6), 3.5-block blast (12) Shift+G Heat Shield     5 s: 60% less damage, burns attackers, melts arrows
 *   X        Fire Jet        hover / fly on jets of flame (toggle)       Shift+X  Jet Dash        a burst forward, scorching what it passes (8)
 *   Z        Fireball Nova   a burst of fire all around (18)            Shift+Z  Supernova       a 1.25 s charge, then a huge blast (30; ultimate)
 *   V        Heat Aura       toggle: a searing aura -- creatures near catch fire, snow and ice melt, dropped items smelt
 *   C        Flame Ring      a ring of fire on the ground for 4 s (3)   Shift+C  Flare           a blinding white-hot flash (6)
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
	/** v0.17.2: Use's small fire bolt (a quick flick, apart from Fire Blast's full throw) and the Fire Jet's lift-off. */
	public static final int ANIM_BOLT = 14;
	public static final int ANIM_LIFTOFF = 15;

	public static final int F_FLYING = 1;

	/** v0.17.1: Heatblast up on his Fire Jet now (any side: the synced flags), for the shared flight lean. */
	public static boolean flying(net.minecraft.world.entity.player.Player p) {
		if (!com.projecthero.mod.omnitrix.Omnitrix.hasPower(p) || p.onGround()) {
			return false;
		}
		var s = com.projecthero.mod.omnitrix.Omnitrix.state(p);
		return ID.equals(s.active) && (s.kitFlags & F_FLYING) != 0;
	}
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

	// v0.17.2 (user: "change the damage on the ben 10 aliens to be on par with the elemental power abilities"): was 6
	public static final float BLAST_DAMAGE = 12f;
	public static final float STREAM_DAMAGE = 4f; // v0.17.2: was 2 a pulse
	public static final int STREAM_TICKS = 50;
	public static final float BOMB_DAMAGE = 12f; // v0.17.2: was 8 (the direct hit is half of it: was 4, now 6)
	public static final int SHIELD_TICKS = 100;
	public static final float SHIELD_FACTOR = 0.4f;
	public static final int RING_TICKS = 80;
	public static final float RING_RADIUS = 4f;
	public static final float RING_DAMAGE = 3f; // v0.17.2: was 2 a pulse
	public static final float NOVA_RADIUS = 6f;
	public static final float NOVA_DAMAGE = 18f; // v0.17.2: was 10
	public static final float NOVA_EDGE_DAMAGE = 12f; // v0.17.2: was 6 (60% of the centre's)
	public static final float SUPERNOVA_RADIUS = 9f;
	public static final float SUPERNOVA_DAMAGE = 30f; // v0.17.2: was 18 (10.8 at the edge; now 18)
	public static final int SUPERNOVA_CHARGE = 25;
	public static final float DASH_DAMAGE = 8f; // v0.17.2: was 4
	/** v0.17.2: the Flare's singe (was 3) and Use's small fire bolt (was 3); both were inline numbers. */
	public static final float FLARE_DAMAGE = 6f;
	public static final float BOLT_DAMAGE = 5f;
	public static final float PUNCH_FIRE = 2f;
	public static final float FLY_SPEED = 0.07f;
	/** v0.17.2: how far the Fire Stream's drawn flamethrower cone reaches (or the first block in the way). Look only. */
	public static final double STREAM_FX_RANGE = 7.0;
	/** v0.17.2: how far the Flare's Solar-Flare sun and screen flash reach (its blinding range is 8). Look only. */
	private static final double FLARE_FX_RADIUS = 8.0;

	/** player -> where the Flame Ring burns (and in which dimension). */
	private static final Map<UUID, Ring> RINGS = new ConcurrentHashMap<>();
	/** player -> creatures the current Jet Dash already scorched. */
	private static final Map<UUID, java.util.Set<Integer>> DASH_HITS = new ConcurrentHashMap<>();

	/** v0.17.2: {@code fx} is the Ring of Fire drawn for it (null if none could be spawned). */
	private record Ring(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dim, Vec3 at, PyroFxEntity fx) {
	}

	/** Puts out {@code p}'s Flame Ring (its fire with it). */
	private static void removeRing(ServerPlayer p) {
		Ring r = RINGS.remove(p.getUUID());
		if (r != null && r.fx() != null && r.fx().isAlive()) {
			r.fx().discard();
		}
	}

	private HeatblastKit() {
	}

	public static void register() {
		Aliens.register(new HeatblastKit());
		AlienProjectileEntity.onHit("heatblast_bomb", (proj, hit) -> {
			if (proj.level() instanceof ServerLevel l) {
				Vec3 at = hit.getLocation();
				// v0.17.2 (user: "change his ability vfx to be similar to the pyrokinesis power"): it bursts like Pyrokinesis'
				// Meteor -- the swelling fireball blast with its shock ring and crown of flames, explosions, flames and lava
				PyrokinesisHandlers.meteorBlastFx(l, proj.getOwner() instanceof ServerPlayer sp ? sp : null, at, BOMB_FX_RADIUS);
				OmnitrixSounds.play(l, at, OmnitrixSounds.HEATBLAST_NOVA, 1.2f, 1.2f);
				QuirkCamera.shakeAround(l, at, 12, 0.4f, 8);
			}
		});
		AlienProjectileEntity.onHit("heatblast_blast", (proj, hit) -> {
			if (proj.level() instanceof ServerLevel l) {
				// v0.17.2 (user: "change his ability vfx to be similar to the pyrokinesis power"): Pyrokinesis' Fireball burst
				PyrokinesisHandlers.fireballImpactFx(l, hit.getLocation(), 1.0f);
			}
		});
		AlienProjectileEntity.onHit("heatblast_bolt", (proj, hit) -> {
			if (proj.level() instanceof ServerLevel l) {
				// v0.17.2: the same Fireball burst, half size
				PyrokinesisHandlers.fireballImpactFx(l, hit.getLocation(), 0.5f);
			}
		});
		// v0.17.2: the bomb trails lava like Pyrokinesis' Meteor (the shots are drawn as Pyrokinesis' fireballs by the client --
		// HeatblastClient -- and trail its flame; the earlier ember / spark trails are gone)
		AlienProjectileEntity.trail("heatblast_bomb", ParticleTypes.LAVA);
	}

	/** v0.17.2: how big the Pyro Bomb's Meteor-blast looks (its damage radius, 3.5, is set on the projectile). */
	private static final double BOMB_FX_RADIUS = 3.5;

	// ---------------------------------------------------------------- v0.17.2 (user: "upgrade the vfx on all of heatblasts abilities"): VFX helpers

	/** A glowing orange ember and a paler, smaller spark (dust: they hang and drift instead of rising like flames). */
	static final DustParticleOptions EMBER = new DustParticleOptions(new Vector3f(1.0f, 0.48f, 0.08f), 1.0f);
	static final DustParticleOptions SPARK = new DustParticleOptions(new Vector3f(1.0f, 0.86f, 0.42f), 0.6f);

	/** A pop of embers: orange dust, sparks, small flames and (big enough) a lava spit. One packet per particle type. */
	static void embers(ServerLevel l, Vec3 at, int n, double spread, double speed) {
		l.sendParticles(EMBER, at.x, at.y, at.z, n, spread, spread * 0.6, spread, speed);
		l.sendParticles(SPARK, at.x, at.y, at.z, Math.max(1, n / 2), spread, spread * 0.6, spread, speed);
		l.sendParticles(ParticleTypes.SMALL_FLAME, at.x, at.y, at.z, Math.max(1, n / 2), spread, spread * 0.6, spread, speed * 0.5);
		if (n >= 6) {
			l.sendParticles(ParticleTypes.LAVA, at.x, at.y, at.z, Math.max(1, n / 6), spread * 0.5, 0.1, spread * 0.5, 0);
		}
	}

	// v0.17.2 (user: "change his ability vfx to be similar to the pyrokinesis power"): the moves now use Pyrokinesis' FX
	// (PyrokinesisHandlers' shared fire FX, PyroFxEntity, and the client's Pyro renderers); the muzzle rings and outward flame
	// bursts went with the old look. The embers stay for the Fire Jet's touchdown only.

	/**
	 * v0.17.2 (user: "change heatblasts landing sound effect, it sounds weird, make it slightly softer too please"): the Fire
	 * Jet touchdown. It was the shared synthesised impact thud (0.7-1.2) with a fire charge pitched down under it (0.6) -- now
	 * a soft, muffled flame whoosh (a blaze shot pitched low) over a light thud of his rock body, the flames crackling out
	 * after; no layer above 0.3, played at 0.7-1.0.
	 */
	public static final OmnitrixSfx.Cue LAND = OmnitrixSfx.cue("heatblast_land")
			.layer(SoundEvents.BLAZE_SHOOT, 0.22f, 0.55f)
			.layer(SoundEvents.BASALT_FALL, 0.3f, 0.75f)
			.layer(SoundEvents.PLAYER_ATTACK_KNOCKBACK, 0.12f, 0.6f)
			.layer(SoundEvents.FURNACE_FIRE_CRACKLE, 0.18f, 1.1f, 3)
			.jitter(0.05f).gap(6);

	/** The superhero landing out of the Fire Jet ({@code HeroLandingFx}, which draws the shared shockwave): his sound and embers. */
	public static void heroLanding(ServerPlayer p, ServerLevel l, float s) {
		OmnitrixSounds.play(l, p.position(), LAND, 0.7f + 0.3f * s, 1.0f);
		embers(l, p.position().add(0, 0.2, 0), 6 + (int) (6 * s), 0.6, 0.08);
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
				// v0.17.2 (user: "change the damage on the ben 10 aliens to be on par with the elemental power abilities"): the Nova
				// and Supernova moved from C to Z (cooldowns 400 -> 240 and 900 -> 600, the ultimate's 30 s); the Flame Ring and Flare
				// from Z to C
				AlienMove.tap(NOVA, AbilitySlot.SLOT_4, 240), AlienMove.shift(SUPERNOVA, AbilitySlot.SLOT_4, 600),
				AlienMove.tap(HEAT_AURA, AbilitySlot.SLOT_5, 60),
				AlienMove.tap(FLAME_RING, AbilitySlot.SLOT_6, 280), AlienMove.shift(FLARE, AbilitySlot.SLOT_6, 240),
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
		removeRing(p);
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
				// v0.17.2: Pyrokinesis' Jet Flight exhaust (its flames and smoke; the jets themselves are drawn on every client)
				l.sendParticles(ParticleTypes.FLAME, p.getX(), p.getY() - 0.1, p.getZ(), 3, 0.15, 0.05, 0.15, 0.03);
				l.sendParticles(ParticleTypes.SMOKE, p.getX(), p.getY() - 0.3, p.getZ(), 1, 0.1, 0.05, 0.1, 0.01);
				if (p.tickCount % 20 == 0) {
					OmnitrixSounds.play(l, p.position(), OmnitrixSounds.HEATBLAST_JET_LOOP, 1.0f, 1.0f);
				}
			}
			if (p.isInWaterOrBubble()) {
				stopFlying(p);
				fizzles(p);
			} else if (p.onGround() && now - timer(p, "fly_at") > 10) {
				// v0.17.1 (user: "the flight should be deactivated when the player touches the ground"): landing ends the Fire Jet
				stopFlying(p);
			}
		}
		// Fire Stream
		long streamEnd = timer(p, "stream_end");
		if (streamEnd <= 0 && flag(p, F_STREAM)) {
			// v0.17.2: a stream flag whose timer is gone (cleared timers) never outlives it -- the cone would be drawn for ever
			setFlag(p, F_STREAM, false);
		}
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
					// v0.17.2 (user: "change his ability vfx to be similar to the pyrokinesis power"): it burns up the way a
					// shot does in Pyrokinesis' Flame Wall -- a puff of smoke and flame
					l.sendParticles(ParticleTypes.LARGE_SMOKE, pr.getX(), pr.getY(), pr.getZ(), 6, 0.1, 0.1, 0.1, 0.02);
					l.sendParticles(ParticleTypes.FLAME, pr.getX(), pr.getY(), pr.getZ(), 6, 0.1, 0.1, 0.1, 0.04);
					l.playSound(null, pr.getX(), pr.getY(), pr.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.25f, 1.8f);
					pr.discard(); // arrows and fireballs burn up in the shield
				}
				if (now % 3 == 0) {
					// v0.17.2: Pyrokinesis' Flame Body -- the living-fire figure is drawn on every client; this is its flicker
					l.sendParticles(ParticleTypes.FLAME, p.getX(), p.getY() + 1.0, p.getZ(), 1, 0.3, 0.7, 0.3, 0.02);
				}
			}
		}
		// Flame Ring
		Ring ring = RINGS.get(p.getUUID());
		long ringEnd = timer(p, "ring_end");
		if (ring != null) {
			if (now >= ringEnd || ring.dim() != l.dimension()) {
				removeRing(p);
				setTimer(p, "ring_end", 0);
			} else {
				// v0.17.2 (user: "change his ability vfx to be similar to the pyrokinesis power"): the fire itself is Pyrokinesis'
				// Ring of Fire (a look-only PyroFxEntity: its fire sheets, ember bed and flames); this only burns
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
				// v0.17.2 (user: "change his ability vfx to be similar to the pyrokinesis power"): Pyrokinesis' Flame Dash wake
				// (the body ablaze and streaming flame is drawn on every client)
				l.sendParticles(ParticleTypes.FLAME, p.getX(), p.getY() + 0.8, p.getZ(), 10, 0.25, 0.5, 0.25, 0.02);
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
				// v0.17.2 (user: "change his ability vfx to be similar to the pyrokinesis power"): like Pyrokinesis' Meteor charging
				// -- flames boiling off the swelling fireball (drawn between his hands on every client), more as it grows
				if (now % 3 == 0) {
					float f = 1f - (nova - now) / (float) SUPERNOVA_CHARGE;
					double yaw = Math.toRadians(p.yBodyRot);
					Vec3 core = p.position().add(-Math.sin(yaw) * 0.6, 1.05, Math.cos(yaw) * 0.6);
					l.sendParticles(ParticleTypes.FLAME, core.x, core.y, core.z, 2 + (int) (f * 6), 0.2 + f * 0.5, 0.2 + f * 0.5, 0.2 + f * 0.5, 0.01);
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
			case SLOT_4 -> { // v0.17.2: the Nova / Supernova (were C)
				if (shift) {
					supernova(p);
				} else {
					nova(p);
				}
			}
			case SLOT_5 -> vKey(p, shift); // v0.17.1 (user: "add abilities to the V key for every alien")
			case SLOT_6 -> { // v0.17.2: the Flame Ring / Flare (were Z)
				if (shift) {
					flare(p);
				} else {
					flameRing(p);
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
		// v0.17.2 (user: "change his ability vfx to be similar to the pyrokinesis power"): no muzzle ring -- like Pyrokinesis'
		// Fireball, the shot is a ball of fire (HeatblastClient draws it) and the palm flares as it leaves
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
		// v0.17.2 (user: "change his ability vfx to be similar to the pyrokinesis power"): the stream is Pyrokinesis'
		// Flamethrower -- its roaring cone is drawn on every client (HeatblastClient); this is the same spray of flame round it
		Vec3 low = eye.add(0, -0.3, 0);
		for (int i = 0; i < 6; i++) { // stream() runs every 3rd tick; Pyrokinesis sprays 3 a tick
			double d = 1.0 + l.random.nextDouble() * (STREAM_FX_RANGE - 1.0);
			Vec3 pt = low.add(look.scale(d));
			l.sendParticles(ParticleTypes.FLAME, pt.x, pt.y, pt.z, 1, 0.08 * d, 0.08 * d, 0.08 * d, 0.02);
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
		// v0.17.2 (user: "change his ability vfx to be similar to the pyrokinesis power"): no muzzle ring -- the bomb is a
		// Pyrokinesis Meteor in flight (HeatblastClient draws it), swelling in his hand before the throw
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
		// v0.17.2 (user: "change his ability vfx to be similar to the pyrokinesis power"): it takes like Pyrokinesis' Flame
		// Body -- a rush of fire up his whole body (the living-fire figure is drawn on every client while it lasts)
		l.sendParticles(ParticleTypes.FLAME, p.getX(), p.getY() + 1.0, p.getZ(), 60, 0.4, 0.9, 0.4, 0.05);
		return true;
	}

	@Override
	public boolean keyActive(net.minecraft.world.entity.player.Player p, AbilitySlot slot) {
		return slot == AbilitySlot.SLOT_3 && flag(p, F_FLYING);
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
		setTimer(p, "fly_at", p.level().getGameTime());
		p.getAbilities().mayfly = true;
		p.getAbilities().flying = true;
		p.getAbilities().setFlyingSpeed(FLY_SPEED);
		p.onUpdateAbilities();
		p.push(0, 0.45, 0);
		p.hurtMarked = true;
		ServerLevel l = level(p);
		OmnitrixSounds.play(l, p.position(), OmnitrixSounds.HEATBLAST_JET, 1.0f, 1.0f);
		// v0.17.2: the push-off pose; v0.17.2 (user: "change his ability vfx to be similar to the pyrokinesis power"): lift-off
		// like Pyrokinesis' Jet Flight -- a blast of flame off the ground (no rings), then its jets out of his palms and soles
		anim(p, ANIM_LIFTOFF);
		l.sendParticles(ParticleTypes.FLAME, p.getX(), p.getY() + 0.1, p.getZ(), 40, 0.4, 0.1, 0.4, 0.1);
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
		OmnitrixSounds.play(l, p.position(), OmnitrixSounds.HEATBLAST_JET, 1.0f, 1.3f);
		// v0.17.2 (user: "change his ability vfx to be similar to the pyrokinesis power"): Pyrokinesis' Flame Dash launch -- a
		// burst of flame off him (no ribbon, rings or cone); his body bursts into streaming fire on every client
		l.sendParticles(ParticleTypes.FLAME, p.getX(), p.getY() + 0.9, p.getZ(), 30, 0.3, 0.5, 0.3, 0.12);
		return true;
	}

	public boolean flameRing(ServerPlayer p) {
		if (fizzles(p) || !use(p, FLAME_RING)) {
			return false;
		}
		anim(p, ANIM_RING);
		ServerLevel l = level(p);
		// v0.17.2 (user: "change his ability vfx to be similar to the pyrokinesis power"): Pyrokinesis' Ring of Fire -- the same
		// look-only fire entity (two rings of fire sheets over a glowing ember bed) and its cast flames; no QuirkFx rings / decal
		removeRing(p);
		PyroFxEntity fx = PyroFxEntity.spawn(l, p, PyroFxEntity.RING, p.position(), p.getYRot(), RING_RADIUS, RING_TICKS).visualOnly();
		RINGS.put(p.getUUID(), new Ring(p.level().dimension(), p.position(), fx));
		setTimer(p, "ring_end", p.level().getGameTime() + RING_TICKS);
		PyrokinesisHandlers.ringCastFx(l, p.position(), RING_RADIUS);
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
		// v0.17.2 (user: "change his ability vfx to be similar to the pyrokinesis power"): Pyrokinesis' Solar Flare -- the sun
		// swelling out of him with its corona and shock ring, and the white flash on the screens round him (softer on his own);
		// the vanilla flash discs, QuirkFx rings and spark dust are gone
		PyrokinesisHandlers.solarFlareFx(l, p, p.position().add(0, 1.2, 0), FLARE_FX_RADIUS, false);
		for (LivingEntity e : around(p, c, 8.0)) {
			e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0));
			e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1));
			hurt(p, e, FLARE_DAMAGE, true); // v0.17.2: was 3
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
		burst(p, NOVA_RADIUS, NOVA_DAMAGE, NOVA_EDGE_DAMAGE, 1.2, 6, 1.0f);
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
		burst(p, SUPERNOVA_RADIUS, SUPERNOVA_DAMAGE, SUPERNOVA_DAMAGE * 0.6f, 1.8, 8, 1.8f);
	}

	/** v0.17.2: {@code edgeDamage} is what reaches the rim (it was always 60% of {@code damage}; the Nova's is now 2/3). */
	private void burst(ServerPlayer p, float radius, float damage, float edgeDamage, double push, int fireSecs, float scale) {
		ServerLevel l = level(p);
		Vec3 c = p.position().add(0, 1.0, 0);
		OmnitrixSounds.play(l, c, OmnitrixSounds.HEATBLAST_NOVA, 1.4f, 1.0f / scale + 0.2f);
		QuirkCamera.shakeAround(l, c, radius * 2.5, 0.35f * scale, 12);
		// v0.17.2 (user: "change his ability vfx to be similar to the pyrokinesis power"): the Fireball Nova is Pyrokinesis'
		// Inferno (the ring of flame sheets rolling out over the ground) and the Supernova its Phoenix Dive landing (that ring
		// under a swelling fireball blast); the QuirkFx blasts, rings, scorch, pillar, ember dust and lava are gone
		if (scale > 1.0f) {
			// (the fireball blast held to a Meteor's size: at the full 9 its core would swallow his own camera)
			PyrokinesisHandlers.phoenixImpactFx(l, p, p.position(), radius, PyrokinesisHandlers.METEOR_RADIUS);
		} else {
			PyrokinesisHandlers.infernoFx(l, p, p.position(), radius);
		}
		for (LivingEntity e : around(p, c, radius)) {
			double d = Math.sqrt(e.distanceToSqr(c));
			hurt(p, e, (float) (damage - (damage - edgeDamage) * Math.min(1.0, d / radius)), true);
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
				// v0.17.2 (user: "change his ability vfx to be similar to the pyrokinesis power"): Pyrokinesis' burning punch --
				// flames off the hit (the QuirkFx pop and ember dust are gone)
				level(p).sendParticles(ParticleTypes.FLAME, le.getX(), le.getY() + le.getBbHeight() * 0.6, le.getZ(), 6, 0.2, 0.3, 0.2, 0.02);
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
			anim(p, ANIM_BOLT); // v0.17.2: its own quick flick
			AlienProjectileEntity.of(p, "heatblast_bolt").colour(0xFFA040).size(0.28f).damage(BOLT_DAMAGE).fire(2).speed(1.6).life(30).spawn();
			OmnitrixSounds.play(level(p), p.position(), OmnitrixSounds.HEATBLAST_BLAST, 0.5f, 1.4f);
			p.swing(hand, true); // v0.17.2: a small Pyrokinesis fireball (drawn by HeatblastClient); no muzzle ring
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
		l.sendParticles(ParticleTypes.FLAME, p.getX(), p.getY() + 1.0, p.getZ(), 10, 0.3, 0.3, 0.3, 0.03);
		// v0.17.2: a tight swirl of flame between the cupped hands, smoke off the work, embers
		Vec3 cup = p.getEyePosition().add(p.getLookAngle().scale(0.6)).add(0, -0.45, 0);
		for (int k = 0; k < 8; k++) {
			double a = k * Math.PI / 4;
			l.sendParticles(ParticleTypes.FLAME, cup.x + Math.cos(a) * 0.25, cup.y, cup.z + Math.sin(a) * 0.25, 0, -Math.sin(a) * 0.5, 0.6, Math.cos(a) * 0.5,
					0.06);
		}
		l.sendParticles(ParticleTypes.SMOKE, cup.x, cup.y + 0.3, cup.z, 4, 0.1, 0.1, 0.1, 0.02);
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
		// v0.17.2: the block slumps -- lava dripping and a smoke plume (v0.17.2 Pyrokinesis-style pass: no white flash right in
		// front of the camera, no ember dust)
		Vec3 mid = Vec3.atCenterOf(pos);
		l.sendParticles(ParticleTypes.DRIPPING_LAVA, mid.x, mid.y - 0.3, mid.z, 4, 0.3, 0.1, 0.3, 0);
		l.sendParticles(ParticleTypes.LARGE_SMOKE, mid.x, mid.y + 0.6, mid.z, 3, 0.25, 0.2, 0.25, 0.02);
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
				// v0.17.2: the shield bites back in a flare of fire on them (Pyrokinesis' burning-touch flames)
				level(p).sendParticles(ParticleTypes.FLAME, attacker.getX(), attacker.getY() + attacker.getBbHeight() * 0.6, attacker.getZ(), 6, 0.2, 0.3, 0.2,
						0.02);
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
			OmnitrixSounds.play(l, p.position(), OmnitrixSounds.HEATBLAST_IGNITE, 1.0f, 0.8f);
			// v0.17.2 (user: "change his ability vfx to be similar to the pyrokinesis power"): he goes white-hot like a
			// Pyrokinesis user at full Heat -- a rush of fire up him (the glowing hands, ember shell and the hot ground are drawn on
			// every client); no QuirkFx ring / flash, no ember dust
			l.sendParticles(ParticleTypes.FLAME, p.getX(), p.getY() + 1.0, p.getZ(), 40, 0.4, 0.9, 0.4, 0.05);
		} else {
			l.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.6f, 1.4f);
			l.sendParticles(ParticleTypes.SMOKE, p.getX(), p.getY() + 1.0, p.getZ(), 10, 0.4, 0.5, 0.4, 0.02); // v0.17.2: cooling off
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
		// v0.17.2 (user: "change his ability vfx to be similar to the pyrokinesis power"): Pyrokinesis' heat shimmer (its
		// high-Heat small flames) and its Flame Body flicker, spread out over the aura; the orbiting flames and ember dust are gone
		if (now % 4 == 0) {
			l.sendParticles(ParticleTypes.SMALL_FLAME, p.getX(), p.getY() + 1.0, p.getZ(), 1, 0.3, 0.6, 0.3, 0.01);
		}
		if (now % 3 == 0) {
			l.sendParticles(ParticleTypes.FLAME, p.getX(), p.getY() + 0.3, p.getZ(), 1, AURA_RADIUS * 0.4, 0.2, AURA_RADIUS * 0.4, 0.02);
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
