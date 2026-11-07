package com.projecthero.mod.omnitrix.alien.waybig;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;

import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.power.PowerToggles;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.OmnitrixSfx;
import com.projecthero.mod.omnitrix.OmnitrixSounds;
import com.projecthero.mod.omnitrix.alien.AlienKit;
import com.projecthero.mod.omnitrix.alien.AlienMove;
import com.projecthero.mod.omnitrix.alien.AlienStats;
import com.projecthero.mod.omnitrix.alien.Aliens;
import com.projecthero.mod.omnitrix.data.OmnitrixState;
import com.projecthero.mod.quirk.fx.QuirkCamera;
import com.projecthero.mod.quirk.fx.QuirkFx;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityAttachment;
import net.minecraft.world.entity.EntityAttachments;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.2 (user: "add waybig too please ... Do research on his abilities and powers in the show and make him a really good power. Make
 * him 100 blocks tall please. Does not get slowed down by water or lava and can walk safely through both"). Way Big, a To'kustar --
 * an ultra-rare giant born in cosmic storms. In the show he towers over skyscrapers, throws buildings, fires the Cosmic Ray from
 * his crossed arms (a beam strong enough to knock back other To'kustars), shakes the ground with every stomp, spins into a
 * cyclone, survives in space and shrugs off almost anything -- the head fin is his one weak spot. Spec:
 * {@code docs/OMNITRIX_REVAMP_WAYBIG.md}.
 *
 * <h2>The size</h2>
 * Vanilla's scale attribute stops at 16 (a 29-block player), and a hitbox 100 blocks tall could never move through the world. So
 * his HITBOX stays a giant's feet and shins ({@link #body}: 3 x 6 blocks) while he is DRAWN 100 blocks tall
 * ({@code WayBigClient.renderScale}); his eyes sit at the top ({@link #COLOSSAL_EYE}), the camera pulls back to see him, the culling
 * box is the drawn body ({@link #drawnBody}), and he steps 40 blocks up at a stride (hills are knee-high). Every move reaches from his
 * eyes to where he looks, so he fights from the sky. V shrinks him to a 15-block {@code Compact} To'kustar for tight places (the
 * show: To'kustars can alter their size) and back.
 *
 * <h2>The body</h2>
 * Water and lava never slow, push or float him ({@link #ignoresFluids}) -- he wades through oceans and lava lakes on the bottom --
 * and fire, lava, drowning, suffocation and falls never hurt him. A blow that comes from above his shoulders hits the head fin:
 * double damage.
 */
public final class WayBigKit extends AlienKit {
	public static final String ID = "way_big";
	public static final int COLOUR = 0xC23D36, WHITE = 0xECEAE0, COSMIC = 0x7FE4FF;

	// ---------------------------------------------------------------- the size
	/** v0.17.2 (user: "Make him 100 blocks tall please"). */
	public static final float COLOSSAL_HEIGHT = 100f, COMPACT_HEIGHT = 15f;
	/** The player model is 1.8 blocks tall: drawn this many times over. */
	public static final float COLOSSAL_SCALE = COLOSSAL_HEIGHT / 1.8f, COMPACT_SCALE = COMPACT_HEIGHT / 1.8f;
	/** His eyes: the model's eye line (1.62 / 1.8 of its height). */
	public static final float COLOSSAL_EYE = COLOSSAL_HEIGHT * 0.9f, COMPACT_EYE = COMPACT_HEIGHT * 0.9f;
	/** The hitbox: his feet and shins (small enough to move through the world, solid enough to be fought). */
	public static final float COLOSSAL_BOX_W = 3.0f, COLOSSAL_BOX_H = 6.0f, COMPACT_BOX_W = 1.2f, COMPACT_BOX_H = 4.5f;
	/** Step height (hills and houses are knee-high to him) and the walk-speed multiplier, per size. */
	public static final float COLOSSAL_STEP = 40f, COMPACT_STEP = 6f, COLOSSAL_SPEED = 3.5f, COMPACT_SPEED = 1.6f;

	// ---------------------------------------------------------------- synced flags
	/** Compact (V): 15 blocks tall instead of 100. */
	public static final int F_COMPACT = 1;
	/** The Cosmic Ray is firing. */
	public static final int F_RAY = 2;
	/** The To'kustar Cyclone is spinning. */
	public static final int F_CYCLONE = 4;
	/** In the air from the Giant Stride (the landing quakes). */
	public static final int F_STRIDE = 8;

	// ---------------------------------------------------------------- poses
	public static final int ANIM_FIST = 1, ANIM_CHOP = 2, ANIM_RAY = 3, ANIM_STOMP = 4, ANIM_CLAP = 5, ANIM_STRIDE = 6, ANIM_SHIFT = 7, ANIM_ROAR = 8,
			ANIM_RAY_END = 9, ANIM_LAND = 10, ANIM_CYCLONE = 11, ANIM_CYCLONE_END = 12, ANIM_FIN = 13;

	// ---------------------------------------------------------------- moves (lang projecthero.omnitrix.way_big.<id>)
	/** The kit timer holding the tick the cyclone started (the client's spin reads it). */
	public static final String CYCLONE_START = "cyclone_start";
	public static final String FIST = "titan_fist", CHOP = "colossal_chop", RAY = "cosmic_ray", STRIDE = "giant_stride", STOMP = "quake_stomp",
			CLAP = "thunder_clap", CYCLONE = "tokustar_cyclone", SHIFT = "size_shift", BODY = "tokustar_body", FIN = "head_fin";

	// ---------------------------------------------------------------- numbers
	/**
	 * v0.17.2 (user: "way big is a giant a really powerful alien, make his moves stronger since hes huge and really powerful please"):
	 * far past the aliens' normal scale, every blow about the size of another alien's ultimate. Was 42 / 36 / 9 + 30 / 30 / 34 / 26 / 5 + 22.
	 */
	public static final float FIST_DAMAGE = 100f, CHOP_DAMAGE = 90f, RAY_DAMAGE = 20f, RAY_BLAST = 100f, STRIDE_DAMAGE = 70f, STOMP_DAMAGE = 85f,
			CLAP_DAMAGE = 65f, CYCLONE_DAMAGE = 15f, CYCLONE_FLING = 100f;
	/** Radii and reaches at 100 blocks tall (Compact: scaled by {@link #reachScale}). */
	public static final double FIST_RADIUS = 6, CHOP_LENGTH = 48, CHOP_WIDTH = 4, RAY_RANGE = 220, RAY_RADIUS = 2.6, STRIDE_RADIUS = 10,
			STOMP_RADIUS = 18, CLAP_RANGE = 40, CYCLONE_RADIUS = 28, LOOK_RANGE = 200;
	/** Each blow lands on the tick its pose lands ({@code WayBigClient}: the fist, the chop, the foot and the clap all meet then). */
	public static final int FIST_DELAY = 6, CHOP_DELAY = 9, STOMP_DELAY = 8, CLAP_DELAY = 8, RAY_TAP_TICKS = 24, RAY_MAX_TICKS = 80, RAY_EVERY = 4,
			CYCLONE_TICKS = 100, STRIDE_TICKS = 60;
	/** The head fin: from this share of his height up (his shoulders), and a shot falling at least this steeply (sine: 60 degrees). */
	public static final double FIN_LINE = 0.82, FIN_STEEP = 0.866;

	private static final ResourceLocation SIZE_SPEED = PowerToggles.id("omnitrix_way_big_speed");

	// ---------------------------------------------------------------- sounds
	public static final OmnitrixSfx.Cue SND_QUAKE = OmnitrixSfx.cue("way_big_quake")
			.layer(SoundEvents.GENERIC_EXPLODE.value(), 0.7f, 0.5f)
			.layer(SoundEvents.WARDEN_SONIC_BOOM, 0.25f, 0.5f, 1);
	public static final OmnitrixSfx.Cue SND_SWING = OmnitrixSfx.cue("way_big_swing")
			.layer(SoundEvents.PLAYER_ATTACK_SWEEP, 0.7f, 0.5f)
			.layer(SoundEvents.ELYTRA_FLYING, 0.3f, 0.5f);
	public static final OmnitrixSfx.Cue SND_RAY = OmnitrixSfx.cue("way_big_cosmic_ray")
			.layer(SoundEvents.BEACON_ACTIVATE, 0.7f, 0.6f)
			.layer(SoundEvents.WARDEN_SONIC_CHARGE, 0.6f, 1.2f);
	public static final OmnitrixSfx.Cue SND_RAY_HUM = OmnitrixSfx.cue("way_big_ray_hum")
			.layer(SoundEvents.BEACON_AMBIENT, 0.6f, 1.4f)
			.gap(20);
	public static final OmnitrixSfx.Cue SND_ROAR = OmnitrixSfx.cue("way_big_roar")
			.layer(SoundEvents.RAVAGER_ROAR, 0.7f, 0.5f)
			.layer(SoundEvents.WARDEN_ROAR, 0.4f, 0.6f, 2);
	public static final OmnitrixSfx.Cue SND_WIND = OmnitrixSfx.cue("way_big_cyclone")
			.layer(SoundEvents.ELYTRA_FLYING, 0.6f, 0.6f)
			.gap(30);

	// ---------------------------------------------------------------- server-only move state
	/** A blow on its way down (the Titan Fist, the Colossal Chop): where, and the tick it lands. */
	private record Pending(String move, Vec3 at, Vec3 dir, long when) {
	}

	private static final Map<UUID, List<Pending>> PENDING = new ConcurrentHashMap<>();
	/** The Cosmic Ray: the tick it ends. */
	private static final Map<UUID, Long> RAY_END = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> CYCLONE_END = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> STRIDE_START = new ConcurrentHashMap<>();

	private WayBigKit() {
	}

	public static void register() {
		Aliens.register(new WayBigKit());
	}

	public static void clearSessionState() {
		PENDING.clear();
		RAY_END.clear();
		CYCLONE_END.clear();
		STRIDE_START.clear();
	}

	/** Client-safe: {@code p} is Way Big right now. */
	public static boolean is(Player p) {
		return p != null && Omnitrix.hasPower(p) && ID.equals(Omnitrix.state(p).active);
	}

	/** Client-safe: Way Big's height right now (100, or 15 Compact). */
	public static float height(Player p) {
		return (Omnitrix.state(p).kitFlags & F_COMPACT) != 0 ? COMPACT_HEIGHT : COLOSSAL_HEIGHT;
	}

	/** Reaches and radii shrink with him (1 at 100 blocks, 0.35 Compact -- a 15-block giant still hits like a giant). */
	public static double reachScale(Player p) {
		return (Omnitrix.state(p).kitFlags & F_COMPACT) != 0 ? 0.35 : 1.0;
	}

	@Override
	public String id() {
		return ID;
	}

	@Override
	public AlienStats stats() {
		// the toughest body the Omnitrix has: armour maxed, never knocked back, no fall damage (the size's speed and step are
		// per form, see applySize)
		return AlienStats.builder().health(300f).speed(1f).scale(1f).armor(30f, 20f).knockbackResist(1f).attack(20f).safeFall(1024f).build();
	}

	@Override
	protected List<AlienMove> declareMoves() {
		return List.of(
				AlienMove.tap(FIST, AbilitySlot.SLOT_1, 50), AlienMove.shift(CHOP, AbilitySlot.SLOT_1, 180),
				AlienMove.tap(RAY, AbilitySlot.SLOT_2, 400),
				AlienMove.tap(STRIDE, AbilitySlot.SLOT_3, 160),
				AlienMove.tap(STOMP, AbilitySlot.SLOT_4, 200), AlienMove.shift(CLAP, AbilitySlot.SLOT_4, 240),
				AlienMove.tap(SHIFT, AbilitySlot.SLOT_5, 60),
				AlienMove.tap(CYCLONE, AbilitySlot.SLOT_6, 900),
				AlienMove.vanilla(BODY, "Passive", 0), AlienMove.vanilla(FIN, "Weakness", 0));
	}

	@Override
	public boolean holdable(AbilitySlot slot) {
		return slot == AbilitySlot.SLOT_2; // the Cosmic Ray: tap for a burst, hold to keep firing
	}

	@Override
	public boolean keyActive(Player p, AbilitySlot slot) {
		return switch (slot) {
			case SLOT_2 -> flag(p, F_RAY);
			case SLOT_5 -> flag(p, F_COMPACT);
			case SLOT_6 -> flag(p, F_CYCLONE);
			default -> false;
		};
	}

	// ---------------------------------------------------------------- the body

	@Override
	@Nullable
	public EntityDimensions body(Player p, Pose pose) {
		return flag(p, F_COMPACT) ? COMPACT_BODY : COLOSSAL_BODY;
	}

	private static final EntityDimensions COLOSSAL_BODY = giant(COLOSSAL_BOX_W, COLOSSAL_BOX_H, COLOSSAL_HEIGHT, COLOSSAL_EYE);
	private static final EntityDimensions COMPACT_BODY = giant(COMPACT_BOX_W, COMPACT_BOX_H, COMPACT_HEIGHT, COMPACT_EYE);

	/** A giant's feet-and-shins hitbox, his eyes at the top of the drawn body and the nametag over his head. */
	private static EntityDimensions giant(float w, float h, float drawn, float eye) {
		return EntityDimensions.scalable(w, h).withEyeHeight(eye).withAttachments(EntityAttachments.builder()
				.attach(EntityAttachment.NAME_TAG, 0f, drawn + 1f, 0f)
				.attach(EntityAttachment.VEHICLE, 0f, h * 0.9f, 0f));
	}

	@Override
	@Nullable
	public float[] drawnBody(Player p) {
		float h = height(p);
		return new float[] { h + 2f, h * 0.6f };
	}

	@Override
	public boolean ignoresFluids(Player p) {
		return true; // v0.17.2 (user): water and lava are puddles to him
	}

	@Override
	public float stepHeight(Player p) {
		return flag(p, F_COMPACT) ? COMPACT_STEP : COLOSSAL_STEP; // hills and houses are knee-high to him
	}

	@Override
	public boolean pawsOnly(Player p) {
		return true; // hands the size of houses: no tools or blocks (he can still eat)
	}

	@Override
	public boolean allowDamage(ServerPlayer p, DamageSource source, float amount) {
		// the To'kustar body: born in a cosmic storm, he walks through lava and the deep sea and survives in space
		return !(source.is(DamageTypeTags.IS_FIRE) || source.is(DamageTypeTags.IS_DROWNING) || source.is(DamageTypeTags.IS_FALL)
				|| source.is(net.minecraft.world.damagesource.DamageTypes.IN_WALL) || source.is(net.minecraft.world.damagesource.DamageTypes.CRAMMING)
				|| source.is(net.minecraft.world.damagesource.DamageTypes.FLY_INTO_WALL));
	}

	@Override
	public float incomingDamage(ServerPlayer p, DamageSource source, float amount) {
		// the head fin: the one weak spot -- a blow from above his shoulders hurts twice as much
		if (!finStruck(p, source)) {
			return amount;
		}
		float h = height(p);
		level(p).sendParticles(ParticleTypes.CRIT, p.getX(), p.getY() + h * 0.97, p.getZ(), 40, h * 0.04, h * 0.04, h * 0.04, 0.4);
		// the show's paralysing pain: he clutches his head, unless something bigger is going on
		OmnitrixState s = Omnitrix.state(p);
		if (!flag(p, F_RAY) && !flag(p, F_CYCLONE) && !flag(p, F_STRIDE) && p.level().getGameTime() - s.animStart > 40) {
			anim(p, ANIM_FIN);
		}
		return amount * 2f;
	}

	/**
	 * v0.17.2 (audit: the fin almost never fired): the check used only {@link DamageSource#getSourcePosition()}, and for a shot that is
	 * where the arrow IS when it strikes -- his shins, inside his 6-block hitbox -- so nothing short of a melee hit from 82 blocks up
	 * counted. A blow is on the fin when it comes from at or above his shoulders: the source's own position, or whoever threw it, however
	 * low the shot lands (his head is drawn far above his hitbox, so every hit has to land on the hitbox), or anything falling on him
	 * steeply from overhead (a shot, a dropped anvil), which came down past his head to reach him.
	 */
	public static boolean finStruck(Player p, DamageSource source) {
		double line = p.getY() + height(p) * FIN_LINE;
		Vec3 from = source.getSourcePosition();
		if (from != null && from.y > line) {
			return true;
		}
		net.minecraft.world.entity.Entity who = source.getEntity();
		if (who != null && who != p && who.getEyeY() > line) {
			return true;
		}
		net.minecraft.world.entity.Entity direct = source.getDirectEntity();
		if (direct == null || direct == p || direct instanceof LivingEntity) {
			return false;
		}
		Vec3 v = direct.getDeltaMovement();
		return v.lengthSqr() > 0.01 && -v.y / v.length() >= FIN_STEEP;
	}

	// ---------------------------------------------------------------- lifecycle

	@Override
	public void onTransform(ServerPlayer p) {
		setFlag(p, F_COMPACT, false);
		applySize(p);
		ServerLevel l = level(p);
		anim(p, ANIM_ROAR);
		OmnitrixSounds.play(l, p.position(), SND_ROAR, 3.0f, 0.9f);
		OmnitrixSounds.play(l, p.position(), SND_QUAKE, 3.0f, 0.8f);
		QuirkFx.ring(l, p.position().add(0, 0.1, 0)).colours(WHITE, COLOUR).size(24f).thickness(2.5f).life(26).ground().spawn();
		QuirkCamera.shakeAround(l, p.position(), 120, 0.5f, 20);
		dust(l, p.position(), 18, 80);
	}

	@Override
	public void onRevert(ServerPlayer p) {
		for (int f : new int[] { F_COMPACT, F_RAY, F_CYCLONE, F_STRIDE }) {
			setFlag(p, f, false);
		}
		setTimer(p, CYCLONE_START, 0L);
		PowerToggles.clearModifier(p, Attributes.MOVEMENT_SPEED, SIZE_SPEED);
		UUID id = p.getUUID();
		PENDING.remove(id);
		RAY_END.remove(id);
		CYCLONE_END.remove(id);
		STRIDE_START.remove(id);
	}

	/** The walk speed and step height of the size he is now. */
	private void applySize(ServerPlayer p) {
		boolean compact = flag(p, F_COMPACT);
		PowerToggles.modifier(p, Attributes.MOVEMENT_SPEED, SIZE_SPEED, (compact ? COMPACT_SPEED : COLOSSAL_SPEED) - 1f,
				AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
	}

	@Override
	public void tick(ServerPlayer p, OmnitrixState s, long now) {
		ServerLevel l = level(p);
		UUID id = p.getUUID();
		// lava and fire never take hold of him
		if (p.isOnFire()) {
			p.clearFire();
		}
		p.setAirSupply(p.getMaxAirSupply());
		// every stride shakes the ground a little (Colossal)
		if (!flag(p, F_COMPACT) && p.onGround() && p.getDeltaMovement().horizontalDistanceSqr() > 0.04 && now % 14 == 0) {
			OmnitrixSounds.play(l, p.position(), SND_QUAKE, 1.2f, 1.3f);
			dust(l, p.position(), 4, 14);
		}
		// blows on their way down
		List<Pending> list = PENDING.get(id);
		if (list != null) {
			for (Pending pd : new ArrayList<>(list)) {
				if (now >= pd.when()) {
					list.remove(pd);
					land(p, pd);
				}
			}
			if (list.isEmpty()) {
				PENDING.remove(id);
			}
		}
		Long rayEnd = RAY_END.get(id);
		if (rayEnd != null) {
			if (now >= rayEnd) {
				endRay(p);
			} else {
				tickRay(p, now);
			}
		}
		Long cyc = CYCLONE_END.get(id);
		if (cyc != null) {
			if (now >= cyc) {
				endCyclone(p);
			} else {
				tickCyclone(p, now);
			}
		}
		Long stride = STRIDE_START.get(id);
		if (stride != null && now - stride > 3 && (p.onGround() || now - stride > STRIDE_TICKS)) {
			STRIDE_START.remove(id);
			setFlag(p, F_STRIDE, false);
			anim(p, ANIM_LAND);
			quake(p, p.position(), STRIDE_RADIUS * reachScale(p), STRIDE_DAMAGE, 1.2, 0.6);
		}
	}

	// ---------------------------------------------------------------- keys

	@Override
	public void press(ServerPlayer p, AbilitySlot slot, boolean shift) {
		switch (slot) {
			case SLOT_1 -> {
				if (shift) {
					chop(p);
				} else {
					fist(p);
				}
			}
			case SLOT_2 -> {
				if (!shift) {
					startRay(p, RAY_TAP_TICKS);
				}
			}
			case SLOT_3 -> {
				if (!shift) {
					stride(p);
				}
			}
			case SLOT_4 -> {
				if (shift) {
					clap(p);
				} else {
					stomp(p);
				}
			}
			case SLOT_5 -> {
				if (!shift) {
					sizeShift(p);
				}
			}
			case SLOT_6 -> {
				if (!shift) {
					cyclone(p);
				}
			}
			default -> {
			}
		}
	}

	@Override
	public void holdStart(ServerPlayer p, AbilitySlot slot) {
		if (slot == AbilitySlot.SLOT_2) {
			startRay(p, RAY_MAX_TICKS);
		}
	}

	@Override
	public void holdEnd(ServerPlayer p, AbilitySlot slot, int heldTicks) {
		if (slot == AbilitySlot.SLOT_2 && RAY_END.containsKey(p.getUUID())) {
			endRay(p);
		}
	}

	// ---------------------------------------------------------------- aiming from the sky

	/** Where his look lands (blocks only), up to {@link #LOOK_RANGE}; the far end of the look if it meets nothing. */
	public static Vec3 lookPoint(ServerPlayer p, double range) {
		Vec3 eye = p.getEyePosition();
		Vec3 end = eye.add(p.getLookAngle().scale(range));
		HitResult hit = p.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		return hit.getType() == HitResult.Type.MISS ? end : hit.getLocation();
	}

	/** The ground under a point (the first solid block below it, within 120 blocks), or the point itself. */
	private static Vec3 ground(ServerLevel l, Vec3 at) {
		BlockPos.MutableBlockPos m = BlockPos.containing(at).mutable();
		for (int i = 0; i < 120 && m.getY() > l.getMinBuildHeight(); i++) {
			if (!l.getBlockState(m).getCollisionShape(l, m).isEmpty()) {
				return new Vec3(at.x, m.getY() + 1, at.z);
			}
			m.move(0, -1, 0);
		}
		return at;
	}

	private static Vec3 flat(ServerPlayer p) {
		Vec3 f = p.getLookAngle().multiply(1, 0, 1);
		return f.lengthSqr() < 1.0e-4 ? Vec3.directionFromRotation(0, p.getYRot()) : f.normalize();
	}

	// ---------------------------------------------------------------- R: Titan Fist / Shift+R: Colossal Chop

	/** R: a fist the size of a house comes down where he looks -- {@link #FIST_DAMAGE} in {@link #FIST_RADIUS}, flung away. */
	public boolean fist(ServerPlayer p) {
		if (!use(p, FIST)) {
			return false;
		}
		anim(p, ANIM_FIST);
		OmnitrixSounds.play(level(p), p.getEyePosition(), SND_SWING, 2.0f, 1.0f);
		queue(p, new Pending(FIST, ground(level(p), lookPoint(p, LOOK_RANGE * reachScale(p) + 20)), flat(p), p.level().getGameTime() + FIST_DELAY));
		return true;
	}

	/** Shift+R: both hands brought down edge-first -- {@link #CHOP_DAMAGE} along a {@link #CHOP_LENGTH}-block line ahead. */
	public boolean chop(ServerPlayer p) {
		if (!use(p, CHOP)) {
			return false;
		}
		anim(p, ANIM_CHOP);
		OmnitrixSounds.play(level(p), p.getEyePosition(), SND_SWING, 2.0f, 0.7f);
		queue(p, new Pending(CHOP, p.position(), flat(p), p.level().getGameTime() + CHOP_DELAY));
		return true;
	}

	private static void queue(ServerPlayer p, Pending pd) {
		PENDING.computeIfAbsent(p.getUUID(), k -> new ArrayList<>()).add(pd);
	}

	private void land(ServerPlayer p, Pending pd) {
		ServerLevel l = level(p);
		double k = reachScale(p);
		if (FIST.equals(pd.move())) {
			Vec3 at = pd.at();
			for (LivingEntity e : around(p, at, FIST_RADIUS * k)) {
				e.invulnerableTime = 0;
				hurt(p, e, FIST_DAMAGE, false);
				shove(e, at, 2.2, 0.9);
			}
			OmnitrixSounds.play(l, at, SND_QUAKE, 3.0f, 0.7f);
			QuirkFx.ring(l, at.add(0, 0.1, 0)).colours(WHITE, COLOUR).size((float) (FIST_RADIUS * k * 2)).thickness(1.4f).life(18).ground().spawn();
			QuirkFx.blast(l, at.add(0, 1, 0)).colours(WHITE, 0xB0A090).size((float) (FIST_RADIUS * k)).life(16).spawn();
			QuirkFx.decal(l, at.add(0, 0.05, 0)).style(QuirkFx.DECAL_CRACK).colours(0x2A2622, 0x4A4238).size((float) (FIST_RADIUS * k * 1.6)).life(80).spawn();
			QuirkCamera.shakeAround(l, at, 60, 0.45f, 12);
			dust(l, at, (int) (FIST_RADIUS * k), 60);
		} else if (STOMP.equals(pd.move())) {
			quake(p, pd.at(), STOMP_RADIUS * k, STOMP_DAMAGE, 0.6, 1.3);
		} else if (CLAP.equals(pd.move())) {
			clapLands(p);
		} else {
			Vec3 dir = pd.dir();
			double len = CHOP_LENGTH * k, width = CHOP_WIDTH * k;
			Vec3 start = p.position().add(dir.scale(p.getBbWidth()));
			Set<LivingEntity> hit = new HashSet<>();
			for (double d = 0; d <= len; d += width) {
				Vec3 at = ground(l, start.add(dir.scale(d)).add(0, 20 * k, 0));
				for (LivingEntity e : around(p, at.add(0, 1, 0), width)) {
					if (hit.add(e)) {
						e.invulnerableTime = 0;
						hurt(p, e, CHOP_DAMAGE, false);
						shove(e, at, 1.0, 1.1);
					}
				}
				if (((int) (d / width)) % 2 == 0) {
					dust(l, at, (int) Math.max(2, width), 10);
				}
			}
			Vec3 mid = ground(l, start.add(dir.scale(len * 0.5)).add(0, 20 * k, 0));
			OmnitrixSounds.play(l, mid, SND_QUAKE, 3.0f, 0.6f);
			QuirkFx.cone(l, start.add(0, 0.5, 0), dir).colours(WHITE, COLOUR).size((float) width).length((float) len).life(14).spawn();
			QuirkCamera.shakeAround(l, mid, 60, 0.4f, 10);
		}
	}

	// ---------------------------------------------------------------- G: Cosmic Ray

	/**
	 * G: arms crossed in a plus, the Cosmic Ray -- tap for a {@link #RAY_TAP_TICKS}-tick burst, hold to keep it on for up to
	 * {@link #RAY_MAX_TICKS}. Every {@link #RAY_EVERY} ticks {@link #RAY_DAMAGE} (burning) to everything in the beam; where it
	 * strikes, a {@link #RAY_BLAST} blast as it ends. The cooldown starts when it stops.
	 */
	public boolean startRay(ServerPlayer p, int ticks) {
		UUID id = p.getUUID();
		long now = p.level().getGameTime();
		if (RAY_END.containsKey(id)) {
			RAY_END.put(id, Math.max(RAY_END.get(id), now + ticks)); // the hold took over the tap
			return true;
		}
		if (cooldownLeft(p, RAY) > 0) {
			use(p, RAY); // the "not ready" line
			return false;
		}
		RAY_END.put(id, now + ticks);
		setFlag(p, F_RAY, true);
		anim(p, ANIM_RAY);
		OmnitrixSounds.play(level(p), p.getEyePosition(), SND_RAY, 3.0f, 0.8f);
		return true;
	}

	/** Where the crossed arms are: in front of his chest (about 70% of his height). */
	public static Vec3 rayOrigin(Player p) {
		Vec3 f = Vec3.directionFromRotation(0, p.getYRot());
		float h = height(p);
		return p.position().add(0, h * 0.7, 0).add(f.scale(h * 0.09));
	}

	private void tickRay(ServerPlayer p, long now) {
		ServerLevel l = level(p);
		Vec3 from = rayOrigin(p);
		Vec3 to = lookPoint(p, RAY_RANGE);
		// the beam runs from the arms to where the eyes look
		Vec3 dir = to.subtract(from);
		double len = dir.length();
		if (len < 1.0e-3) {
			return;
		}
		dir = dir.scale(1 / len);
		double r = RAY_RADIUS * Math.max(0.5, reachScale(p));
		com.projecthero.mod.beamclash.BeamClash.report(p, from, dir, len, 3f, 0x7CE0FF); // v0.17.3
		// the beam itself is drawn by the client every frame (WayBigClient.renderWorld), following his look
		OmnitrixSounds.play(l, from, SND_RAY_HUM, 2.0f, 1.0f);
		if (now % RAY_EVERY == 0) {
			Set<LivingEntity> hit = new HashSet<>();
			for (double d = 2; d < len + r; d += r) {
				for (LivingEntity e : around(p, from.add(dir.scale(d)), r)) {
					if (hit.add(e)) {
						e.invulnerableTime = 0;
						hurt(p, e, RAY_DAMAGE, true);
						e.igniteForSeconds(4);
						e.push(dir.x * 0.3, 0.05, dir.z * 0.3);
						e.hurtMarked = true;
					}
				}
			}
			l.sendParticles(ParticleTypes.END_ROD, to.x, to.y, to.z, 14, r, r, r, 0.15);
			l.sendParticles(ParticleTypes.FLASH, to.x, to.y, to.z, 1, 0, 0, 0, 0);
		}
	}

	private void endRay(ServerPlayer p) {
		UUID id = p.getUUID();
		if (RAY_END.remove(id) == null) {
			return;
		}
		setFlag(p, F_RAY, false);
		ServerLevel l = level(p);
		Vec3 to = lookPoint(p, RAY_RANGE);
		double r = 6 * Math.max(0.5, reachScale(p));
		for (LivingEntity e : around(p, to, r)) {
			e.invulnerableTime = 0;
			hurt(p, e, RAY_BLAST, true);
			shove(e, to, 1.6, 0.8);
		}
		QuirkFx.blast(l, to).colours(0xFFFFFF, COSMIC).size((float) r).life(18).spawn();
		OmnitrixSounds.play(l, to, SND_QUAKE, 2.5f, 1.1f);
		cooldown(p, RAY, -1);
		anim(p, ANIM_RAY_END);
	}

	// ---------------------------------------------------------------- X: Giant Stride

	/** X: a leap that covers a valley -- far forward and up, the landing quakes ({@link #STRIDE_DAMAGE} in {@link #STRIDE_RADIUS}). */
	public boolean stride(ServerPlayer p) {
		if (!use(p, STRIDE)) {
			return false;
		}
		anim(p, ANIM_STRIDE);
		double k = reachScale(p);
		Vec3 f = flat(p);
		p.setDeltaMovement(f.x * 4.2 * k + f.x * 0.6, 1.6 * Math.sqrt(k), f.z * 4.2 * k + f.z * 0.6);
		p.hurtMarked = true;
		p.resetFallDistance();
		setFlag(p, F_STRIDE, true);
		STRIDE_START.put(p.getUUID(), p.level().getGameTime());
		OmnitrixSounds.play(level(p), p.position(), SND_QUAKE, 2.0f, 1.2f);
		dust(level(p), p.position(), 6, 30);
		return true;
	}

	// ---------------------------------------------------------------- Z: Quake Stomp / Shift+Z: Thunder Clap

	/** Z: one stomp -- a shockwave ring out to {@link #STOMP_RADIUS}: {@link #STOMP_DAMAGE}, thrown up, slowed. */
	public boolean stomp(ServerPlayer p) {
		if (!use(p, STOMP)) {
			return false;
		}
		anim(p, ANIM_STOMP);
		OmnitrixSounds.play(level(p), p.getEyePosition(), SND_SWING, 2.0f, 0.6f);
		queue(p, new Pending(STOMP, p.position().add(flat(p).scale(p.getBbWidth())), flat(p), p.level().getGameTime() + STOMP_DELAY));
		return true;
	}

	private void quake(ServerPlayer p, Vec3 at, double radius, float damage, double push, double up) {
		ServerLevel l = level(p);
		for (LivingEntity e : around(p, at, radius)) {
			if (e.getY() > at.y + radius * 0.6) {
				continue; // a ground quake: not the bird overhead
			}
			e.invulnerableTime = 0;
			hurt(p, e, damage, false);
			shove(e, at, push, up);
			e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 2));
		}
		OmnitrixSounds.play(l, at, SND_QUAKE, 3.0f, 0.6f);
		QuirkFx.ring(l, at.add(0, 0.1, 0)).colours(WHITE, 0xB0A090).size((float) radius * 2f).thickness((float) Math.max(1, radius * 0.12)).life(20)
				.ground().spawn();
		QuirkCamera.shakeAround(l, at, radius * 4, 0.5f, 14);
		dust(l, at, (int) radius, 70);
	}

	/** Shift+Z: hands clapped together -- a thunderclap down a {@link #CLAP_RANGE}-block cone ahead: {@link #CLAP_DAMAGE}, blown away. */
	public boolean clap(ServerPlayer p) {
		if (!use(p, CLAP)) {
			return false;
		}
		anim(p, ANIM_CLAP);
		OmnitrixSounds.play(level(p), p.getEyePosition(), SND_SWING, 2.0f, 1.2f);
		queue(p, new Pending(CLAP, p.position(), flat(p), p.level().getGameTime() + CLAP_DELAY));
		return true;
	}

	/** The hands meet: the thunderclap goes out where he looks now. */
	private void clapLands(ServerPlayer p) {
		ServerLevel l = level(p);
		double range = CLAP_RANGE * reachScale(p);
		Vec3 from = p.position().add(0, Math.min(height(p) * 0.2, 12), 0);
		Vec3 look = p.getLookAngle();
		Vec3 dir = new Vec3(look.x, Mth.clamp(look.y, -0.6, 0.2), look.z).normalize();
		for (LivingEntity e : around(p, from.add(dir.scale(range * 0.5)), range * 0.6)) {
			Vec3 to = e.position().add(0, e.getBbHeight() * 0.5, 0).subtract(from);
			if (to.length() > range || to.normalize().dot(dir) < 0.6) {
				continue;
			}
			e.invulnerableTime = 0;
			hurt(p, e, CLAP_DAMAGE, false);
			e.push(dir.x * 3.0, 0.7, dir.z * 3.0);
			e.hurtMarked = true;
			e.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 80, 0));
		}
		OmnitrixSounds.play(l, p.getEyePosition(), SND_QUAKE, 3.0f, 1.5f);
		OmnitrixSounds.play(l, from, SND_QUAKE, 2.0f, 1.0f);
		QuirkFx.cone(l, from, dir).colours(0xFFFFFF, COSMIC).size((float) (range * 0.5)).length((float) range).life(14).spawn();
		QuirkCamera.shakeAround(l, from, range * 2, 0.35f, 10);
	}

	// ---------------------------------------------------------------- V: Size Shift

	/** V: Colossal (100 blocks) to Compact (15 blocks) and back -- the To'kustar size alteration. */
	public boolean sizeShift(ServerPlayer p) {
		if (!use(p, SHIFT)) {
			return false;
		}
		boolean compact = !flag(p, F_COMPACT);
		setFlag(p, F_COMPACT, compact);
		applySize(p);
		p.refreshDimensions();
		anim(p, ANIM_SHIFT);
		ServerLevel l = level(p);
		OmnitrixSounds.play(l, p.position(), SND_ROAR, compact ? 1.5f : 3.0f, compact ? 1.3f : 0.9f);
		QuirkFx.ring(l, p.position().add(0, 0.1, 0)).colours(WHITE, COSMIC).size(compact ? 8f : 24f).thickness(1.5f).life(16).ground().spawn();
		dust(l, p.position(), compact ? 4 : 14, 40);
		return true;
	}

	// ---------------------------------------------------------------- C: To'kustar Cyclone

	/**
	 * C: he spins into a cyclone for {@link #CYCLONE_TICKS} -- everything within {@link #CYCLONE_RADIUS} is dragged in and torn at
	 * ({@link #CYCLONE_DAMAGE} every half second), then flung out ({@link #CYCLONE_FLING}).
	 */
	public boolean cyclone(ServerPlayer p) {
		if (!use(p, CYCLONE)) {
			return false;
		}
		anim(p, ANIM_CYCLONE);
		setTimer(p, CYCLONE_START, p.level().getGameTime());
		setFlag(p, F_CYCLONE, true);
		CYCLONE_END.put(p.getUUID(), p.level().getGameTime() + CYCLONE_TICKS);
		OmnitrixSounds.play(level(p), p.position(), SND_ROAR, 2.0f, 1.1f);
		return true;
	}

	private void tickCyclone(ServerPlayer p, long now) {
		ServerLevel l = level(p);
		double r = CYCLONE_RADIUS * reachScale(p);
		Vec3 c = p.position();
		for (LivingEntity e : around(p, c.add(0, r * 0.3, 0), r)) {
			Vec3 to = c.subtract(e.position());
			Vec3 h = new Vec3(to.x, 0, to.z);
			double d = h.length();
			if (d < 1.0e-3) {
				continue;
			}
			Vec3 in = h.scale(1 / d);
			Vec3 round = new Vec3(-in.z, 0, in.x);
			e.push(in.x * 0.12 + round.x * 0.35, 0.09, in.z * 0.12 + round.z * 0.35);
			e.hurtMarked = true;
			e.resetFallDistance();
			if (now % 10 == 0) {
				e.invulnerableTime = 0;
				hurt(p, e, CYCLONE_DAMAGE, false);
			}
		}
		OmnitrixSounds.play(l, c, SND_WIND, 2.0f, 0.9f);
		if (now % 2 == 0) {
			for (int i = 0; i < 6; i++) {
				double a = (now * 0.6 + i * Math.PI / 3);
				double y = (i / 6.0) * height(p) * 0.6;
				double rr = r * (0.3 + 0.7 * i / 6.0);
				l.sendParticles(ParticleTypes.CLOUD, c.x + Math.cos(a) * rr, c.y + y, c.z + Math.sin(a) * rr, 2, 0.5, 0.5, 0.5, 0.02);
			}
		}
	}

	private void endCyclone(ServerPlayer p) {
		if (CYCLONE_END.remove(p.getUUID()) == null) {
			return;
		}
		setFlag(p, F_CYCLONE, false);
		anim(p, ANIM_CYCLONE_END);
		double r = CYCLONE_RADIUS * reachScale(p);
		for (LivingEntity e : around(p, p.position().add(0, r * 0.3, 0), r)) {
			e.invulnerableTime = 0;
			hurt(p, e, CYCLONE_FLING, false);
			shove(e, p.position(), 2.8, 1.2);
		}
		OmnitrixSounds.play(level(p), p.position(), SND_QUAKE, 2.0f, 1.3f);
	}

	// ---------------------------------------------------------------- effects

	/** Dust and block bits kicked up round {@code at}, out to {@code radius}. */
	private static void dust(ServerLevel l, Vec3 at, int radius, int count) {
		BlockState ground = l.getBlockState(BlockPos.containing(at).below());
		if (ground.isAir()) {
			ground = net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState();
		}
		l.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), at.x, at.y + 0.5, at.z, count, radius * 0.5, 0.6, radius * 0.5, 0.3);
		l.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, at.x, at.y + 0.5, at.z, Math.max(2, count / 6), radius * 0.4, 0.4, radius * 0.4, 0.02);
	}
}
