package com.projecthero.mod.symbiote.entity;

import java.util.List;
import java.util.UUID;

import com.projecthero.mod.symbiote.SymbioteBonding;
import com.projecthero.mod.symbiote.SymbioteHost;
import com.projecthero.mod.symbiote.SymbioteSounds;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A free Symbiote -- the living black organism that bonds with a player to grant the Symbiote power.
 * Found:
 * <ul>
 *   <li>inside a {@link com.projecthero.mod.symbiote.block.SymbioteMeteoriteBlock Symbiote Meteorite}
 *       at the heart of a {@code symbiote_meteor} crater -- break the rock and it crawls out;</li>
 *   <li>inside the containment cell of a {@code symbiote_lab} (<b>confined</b>: it crawls around its
 *       chamber but can never leave it);</li>
 *   <li>where a {@link SymbioteHost Symbiote Host} mob died (it tears free, looking for a new one).</li>
 * </ul>
 *
 * <h2>A creature that is just trying to survive</h2>
 * v0.13.19 turned the old hovering particle cloud into a puddle of living black goo (rendered by
 * {@code SymbioteEntityRenderer}) with its own small brain, all server-side and all cheap:
 * <ul>
 *   <li><b>It crawls</b> with gravity and real block collision, oozes up 1-block steps and creeps up
 *       walls when something is in its way, and wanders with pauses when it has nothing to do.</li>
 *   <li><b>It hunts for a host</b>: every {@value #HOST_SCAN_INTERVAL} ticks it looks (one bounded AABB
 *       query) for a hostile-capable mob within {@value #SEEK_RANGE} blocks -- anything with an attack
 *       damage attribute, monsters preferred; never bosses, the mod's event/raid mobs, tamed or owned
 *       mobs, no-AI display mobs or an existing Symbiote Host -- crawls to it and, on contact,
 *       <b>takes control</b>: the mob becomes a Symbiote Host ({@link SymbioteHost#mark}) and this entity
 *       is discarded. When that host dies, {@link SymbioteHost#onDeath} frees a Symbiote again.</li>
 *   <li><b>It fears fire and sound</b>: every {@value #THREAT_SCAN_INTERVAL} ticks it checks for burning
 *       entities, a player holding a fire source (torch, lantern, campfire, flint and steel, fire charge,
 *       lava bucket...), fire / lava / lit campfires within a few blocks, and anyone sounding a goat horn
 *       nearby -- and flees. Touching fire or lava makes it recoil and leap clear.</li>
 *   <li><b>It holds still</b> while a player is interacting with it (the bonding minigame's
 *       {@link #claimBond} soft lock, or a few seconds after any right-click).</li>
 * </ul>
 *
 * <h2>Confined (lab) vs free</h2>
 * A confined Symbiote has a home point and a small leash radius: every goal is clamped into it, it only
 * ever considers a host standing inside it, and a hard leash snaps it back if anything (a flowing
 * current, a knock) carries it out. A free one roams anywhere.
 *
 * <h2>Indestructible until it bonds</h2>
 * Ignores every damage source, does not burn, drown, take fall damage or despawn. It only leaves the
 * world when it bonds (with a player via {@link SymbioteBonding}, or with a mob as above), is bottled
 * with a Symbiote Vial, or an operator removes it.
 *
 * <h2>Save compatibility</h2>
 * Pre-0.13.19 saves carry only {@code HoverY}. They load as a free Symbiote that simply drops to the
 * floor -- except one sitting on a lab's lodestone pedestal inside tinted glass, which re-adopts that
 * cell as its confined home on its first tick ({@link #adoptLegacyHome}).
 */
public class SymbioteEntity extends Entity {
	/** What the goo is doing, for the renderer's animation. */
	public static final byte MOOD_IDLE = 0;
	public static final byte MOOD_HUNT = 1;
	public static final byte MOOD_FLEE = 2;
	public static final byte MOOD_HELD = 3;

	private static final EntityDataAccessor<Byte> DATA_MOOD =
			SynchedEntityData.defineId(SymbioteEntity.class, EntityDataSerializers.BYTE);
	private static final EntityDataAccessor<Boolean> DATA_RECOIL =
			SynchedEntityData.defineId(SymbioteEntity.class, EntityDataSerializers.BOOLEAN);

	static final int HOST_SCAN_INTERVAL = 15;
	static final int THREAT_SCAN_INTERVAL = 10;
	static final double SEEK_RANGE = 24.0;
	private static final double FIRE_FEAR_RANGE = 6.0;
	private static final double HORN_FEAR_RANGE = 16.0;
	private static final double WANDER_SPEED = 0.045;
	private static final double HUNT_SPEED = 0.12;
	private static final double FLEE_SPEED = 0.16;
	private static final double CLIMB_SPEED = 0.14;
	private static final double GRAVITY = 0.06;
	/** Default leash for a lab Symbiote: the 3x3 interior of the containment cell. */
	public static final double LAB_HOME_RADIUS = 1.0;

	/** Small black-violet droplets: the goo's ambient drip (squid ink reads as big black squares up close). */
	private static final net.minecraft.core.particles.DustParticleOptions ICHOR =
			new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(0.06f, 0.03f, 0.09f), 0.7f);

	// ---- persisted ----
	private Vec3 home;
	private boolean confined;
	private double homeRadius = LAB_HOME_RADIUS;
	private int huntDelay;
	/** Set when loading a pre-0.13.19 save (HoverY only): {@link #adoptLegacyHome} runs on the first tick. */
	private boolean legacy;

	// ---- transient brain ----
	private UUID bondingPlayer;
	private long bondingExpiresAt;
	private final java.util.Map<UUID, Long> interactCooldown = new java.util.HashMap<>();
	private int recoilTicks;
	private int holdTicks;
	private int fleeTicks;
	private Vec3 fleeFrom;
	private UUID targetId;
	private double targetBestDist = Double.MAX_VALUE;
	private int targetStallTicks;
	private UUID ignoredTarget;
	private long ignoreUntil;
	private Vec3 wanderGoal;
	private int wanderTimer;
	private int restTicks;

	// ---- client-side animation state (read by the renderer) ----
	private int lerpSteps;
	private double lerpX;
	private double lerpY;
	private double lerpZ;
	private float lerpYRot;
	/** 0..1 how fast it is crawling, smoothed; {@link #crawlO} is last tick's value for partial ticks. */
	public float crawl;
	public float crawlO;
	/** 0..1 eased recoil / bristle amount. */
	public float bristle;
	public float bristleO;
	/** -1..1 eased posture: +1 rearing up attentively (held), -1 flattened and shrinking (fleeing). */
	public float alert;
	public float alertO;
	/** 0..1 eased "reaching for a host" amount (hunting). */
	public float hunt;
	public float huntO;

	public SymbioteEntity(EntityType<? extends SymbioteEntity> type, Level level) {
		super(type, level);
		this.noPhysics = false;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(DATA_MOOD, MOOD_IDLE);
		builder.define(DATA_RECOIL, false);
	}

	@Override
	protected void readAdditionalSaveData(CompoundTag tag) {
		if (tag.contains("HomeX")) {
			home = new Vec3(tag.getDouble("HomeX"), tag.getDouble("HomeY"), tag.getDouble("HomeZ"));
		}
		confined = tag.getBoolean("Confined");
		if (tag.contains("HomeRadius")) {
			homeRadius = tag.getDouble("HomeRadius");
		}
		huntDelay = tag.getInt("HuntDelay");
		// Pre-0.13.19 save: only the hover height was stored. Work out what it was on the first tick.
		legacy = tag.contains("HoverY") && !tag.contains("HomeX");
	}

	@Override
	protected void addAdditionalSaveData(CompoundTag tag) {
		if (home != null) {
			tag.putDouble("HomeX", home.x);
			tag.putDouble("HomeY", home.y);
			tag.putDouble("HomeZ", home.z);
		}
		tag.putBoolean("Confined", confined);
		tag.putDouble("HomeRadius", homeRadius);
		if (huntDelay > 0) {
			tag.putInt("HuntDelay", huntDelay);
		}
	}

	// ---------------- entity rules ----------------

	@Override
	public boolean isPickable() {
		return true;
	}

	@Override
	public boolean isInvulnerableTo(DamageSource source) {
		return true;
	}

	@Override
	public boolean hurt(DamageSource source, float amount) {
		return false;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean canBeCollidedWith() {
		return false;
	}

	@Override
	public boolean fireImmune() {
		return true;
	}

	@Override
	public boolean displayFireAnimation() {
		return false;
	}

	@Override
	public boolean shouldBeSaved() {
		return true;
	}

	@Override
	public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
		return false;
	}

	@Override
	public float maxUpStep() {
		// oozes over slabs, carpets and paths without "climbing"; full blocks are climbed (see moveGoo)
		return 0.6f;
	}

	@Override
	public boolean canChangeDimensions(Level from, Level to) {
		return false;
	}

	// ---------------- tick ----------------

	@Override
	public void tick() {
		super.tick();
		if (level().isClientSide) {
			clientTick();
			return;
		}
		serverTick((ServerLevel) level());
	}

	private void serverTick(ServerLevel server) {
		if (bondingPlayer != null) {
			ServerPlayer claimer = server.getServer().getPlayerList().getPlayer(bondingPlayer);
			if (claimer == null || claimer.isRemoved() || server.getGameTime() > bondingExpiresAt
					|| claimer.distanceToSqr(this) > 64.0) {
				bondingPlayer = null;
			}
		}
		if (legacy) {
			legacy = false;
			adoptLegacyHome(server);
		}
		if (home == null) {
			home = position();
		}
		if (recoilTicks > 0) {
			recoilTicks--;
		}
		if (holdTicks > 0) {
			holdTicks--;
		}
		if (huntDelay > 0) {
			huntDelay--;
		}

		boolean held = bondingPlayer != null || holdTicks > 0;
		touchHazards(server);
		if (!held && (tickCount + getId()) % THREAT_SCAN_INTERVAL == 0) {
			scanThreats(server);
		}

		Vec3 goal = null;
		double speed = 0.0;
		byte mood = MOOD_IDLE;
		if (held) {
			mood = MOOD_HELD;
		} else if (fleeTicks > 0) {
			fleeTicks--;
			mood = MOOD_FLEE;
			speed = FLEE_SPEED;
			Vec3 away = position().subtract(fleeFrom == null ? position() : fleeFrom).multiply(1.0, 0.0, 1.0);
			if (away.lengthSqr() < 1.0e-4) {
				away = Vec3.directionFromRotation(0.0f, getYRot());
			}
			goal = position().add(away.normalize().scale(6.0));
		} else {
			Mob target = currentTarget(server);
			if (target == null && huntDelay <= 0 && (tickCount + getId()) % HOST_SCAN_INTERVAL == 0) {
				target = findHost(server);
				if (target != null) {
					targetId = target.getUUID();
					targetBestDist = Double.MAX_VALUE;
					targetStallTicks = 0;
				}
			}
			if (target != null) {
				if (canReach(target)) {
					takeOver(server, target);
					return;
				}
				mood = MOOD_HUNT;
				speed = HUNT_SPEED;
				goal = target.position();
				trackProgress(server, target);
			} else {
				goal = wanderGoal();
				speed = goal == null ? 0.0 : WANDER_SPEED;
			}
		}

		if (confined && goal != null) {
			goal = clampToHome(goal);
		}
		moveGoo(goal, speed);
		if (confined) {
			enforceLeash();
		}

		entityData.set(DATA_MOOD, mood);
		entityData.set(DATA_RECOIL, recoilTicks > 0);
		ambience(server, speed);
	}

	// ---------------- movement ----------------

	private void moveGoo(Vec3 goal, double speed) {
		Vec3 v = getDeltaMovement();
		double vx = v.x;
		double vy = v.y;
		double vz = v.z;
		boolean pushing = false;
		if (goal != null && speed > 0.0) {
			double dx = goal.x - getX();
			double dz = goal.z - getZ();
			double d = Math.sqrt(dx * dx + dz * dz);
			if (d > 0.2) {
				pushing = true;
				vx += (dx / d * speed - vx) * 0.35;
				vz += (dz / d * speed - vz) * 0.35;
				float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0f;
				setYRot(Mth.approachDegrees(getYRot(), yaw, 14.0f));
			} else {
				vx *= 0.5;
				vz *= 0.5;
			}
		} else {
			vx *= 0.5;
			vz *= 0.5;
		}

		if (pushing && horizontalCollision) {
			vy = CLIMB_SPEED; // goo creeps straight up whatever is in its way
		} else if (isInLava()) {
			vy = 0.14;
		} else if (isInWater()) {
			vy = vy * 0.8 + 0.015;
		} else {
			vy = Math.max(-1.2, (vy - GRAVITY) * 0.98);
		}
		setDeltaMovement(vx, vy, vz);
		move(MoverType.SELF, getDeltaMovement());
		if (onGround()) {
			resetFallDistance();
		}
	}

	private Vec3 clampToHome(Vec3 goal) {
		double dx = goal.x - home.x;
		double dz = goal.z - home.z;
		double d = Math.sqrt(dx * dx + dz * dz);
		if (d <= homeRadius) {
			return goal;
		}
		double k = homeRadius / d;
		return new Vec3(home.x + dx * k, goal.y, home.z + dz * k);
	}

	/** Hard leash: a confined Symbiote is never, for any reason, further than its radius from home. */
	private void enforceLeash() {
		double dx = getX() - home.x;
		double dz = getZ() - home.z;
		double d = Math.sqrt(dx * dx + dz * dz);
		double y = Mth.clamp(getY(), home.y - 1.5, home.y + 3.0);
		if (d > homeRadius || y != getY()) {
			double k = d > homeRadius ? homeRadius / d : 1.0;
			setPos(home.x + dx * k, y, home.z + dz * k);
			Vec3 v = getDeltaMovement();
			setDeltaMovement(v.x * -0.3, Math.min(v.y, 0.0), v.z * -0.3);
		}
	}

	private Vec3 wanderGoal() {
		if (wanderGoal != null) {
			double dx = wanderGoal.x - getX();
			double dz = wanderGoal.z - getZ();
			if (dx * dx + dz * dz < 0.3 || --wanderTimer <= 0) {
				wanderGoal = null;
				restTicks = 20 + random.nextInt(70);
			}
			return wanderGoal;
		}
		if (restTicks > 0) {
			restTicks--;
			return null;
		}
		Vec3 origin = confined ? home : position();
		double reach = confined ? homeRadius : 3.0 + random.nextDouble() * 6.0;
		double angle = random.nextDouble() * Math.PI * 2.0;
		double dist = confined ? random.nextDouble() * reach : reach;
		wanderGoal = new Vec3(origin.x + Math.cos(angle) * dist, getY(), origin.z + Math.sin(angle) * dist);
		wanderTimer = 60 + random.nextInt(100);
		return wanderGoal;
	}

	// ---------------- fear ----------------

	/** Actually touching fire or lava: recoil hard and leap clear. */
	private void touchHazards(ServerLevel server) {
		BlockState in = server.getBlockState(blockPosition());
		boolean burning = isInLava() || in.is(BlockTags.FIRE) || CampfireBlock.isLitCampfire(in)
				|| isOnFire();
		if (!burning) {
			return;
		}
		clearFire();
		fleeFrom = Vec3.atCenterOf(blockPosition()).add(random.nextGaussian() * 0.3, 0.0, random.nextGaussian() * 0.3);
		fleeTicks = Math.max(fleeTicks, 40);
		if (recoilTicks <= 10) {
			recoil();
			Vec3 away = Vec3.directionFromRotation(0.0f, random.nextFloat() * 360.0f).scale(0.25);
			setDeltaMovement(away.x, 0.45, away.z);
			server.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 0.3, getZ(), 6, 0.3, 0.2, 0.3, 0.02);
			server.playSound(null, getX(), getY(), getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.HOSTILE, 0.8f, 0.7f);
			SymbioteSounds.organic(server, getX(), getY(), getZ(), 0.9f, 1.3f);
		}
	}

	/** Bounded look-around for anything it is afraid of. Sets a flee vector if it finds one. */
	private void scanThreats(ServerLevel server) {
		Vec3 me = position();
		Vec3 threat = null;
		double best = Double.MAX_VALUE;

		AABB near = getBoundingBox().inflate(FIRE_FEAR_RANGE, 3.0, FIRE_FEAR_RANGE);
		for (Entity e : server.getEntities(this, near, e -> e.isAlive() && !(e instanceof SymbioteEntity))) {
			boolean scary = e.isOnFire() && !e.fireImmune() || e instanceof Player p && holdsFire(p);
			double d = e.distanceToSqr(this);
			if (scary && d < best && d <= FIRE_FEAR_RANGE * FIRE_FEAR_RANGE) {
				best = d;
				threat = e.position();
			}
		}
		if (threat == null) {
			AABB horn = getBoundingBox().inflate(HORN_FEAR_RANGE, 6.0, HORN_FEAR_RANGE);
			for (Player p : server.getEntitiesOfClass(Player.class, horn,
					p -> p.isUsingItem() && p.getUseItem().is(Items.GOAT_HORN))) {
				threat = p.position();
				break;
			}
		}
		if (threat == null) {
			BlockPos c = blockPosition();
			BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
			for (int dx = -3; dx <= 3; dx++) {
				for (int dz = -3; dz <= 3; dz++) {
					for (int dy = -1; dy <= 2; dy++) {
						m.set(c.getX() + dx, c.getY() + dy, c.getZ() + dz);
						BlockState s = server.getBlockState(m);
						if (s.is(BlockTags.FIRE) || s.getFluidState().is(FluidTags.LAVA) || CampfireBlock.isLitCampfire(s)) {
							double d = m.distToCenterSqr(me);
							if (d < best) {
								best = d;
								threat = Vec3.atCenterOf(m);
							}
						}
					}
				}
			}
		}
		if (threat != null) {
			boolean wasCalm = fleeTicks <= 0;
			fleeFrom = threat;
			fleeTicks = 40;
			targetId = null; // survival first
			if (wasCalm) {
				SymbioteSounds.organic(server, getX(), getY(), getZ(), 0.5f, 1.5f);
			}
		}
	}

	/** Is this player holding something that burns? */
	public static boolean holdsFire(Player player) {
		return isFireItem(player.getMainHandItem()) || isFireItem(player.getOffhandItem());
	}

	public static boolean isFireItem(ItemStack stack) {
		return !stack.isEmpty() && (stack.is(Items.TORCH) || stack.is(Items.SOUL_TORCH) || stack.is(Items.LANTERN)
				|| stack.is(Items.SOUL_LANTERN) || stack.is(Items.FLINT_AND_STEEL) || stack.is(Items.FIRE_CHARGE)
				|| stack.is(Items.LAVA_BUCKET) || stack.is(Items.CAMPFIRE) || stack.is(Items.SOUL_CAMPFIRE)
				|| stack.is(Items.BLAZE_ROD) || stack.is(Items.BLAZE_POWDER) || stack.is(Items.MAGMA_BLOCK));
	}

	// ---------------- hosts ----------------

	private Mob currentTarget(ServerLevel server) {
		if (targetId == null) {
			return null;
		}
		if (server.getEntity(targetId) instanceof Mob mob && isValidHost(mob) && mob.distanceToSqr(this) <= (SEEK_RANGE + 8) * (SEEK_RANGE + 8)
				&& (!confined || withinHome(mob.position(), 0.8))) {
			return mob;
		}
		targetId = null;
		return null;
	}

	private Mob findHost(ServerLevel server) {
		AABB box = confined
				? new AABB(home, home).inflate(homeRadius + 0.8, 2.0, homeRadius + 0.8)
				: getBoundingBox().inflate(SEEK_RANGE, 8.0, SEEK_RANGE);
		long now = server.getGameTime();
		Mob best = null;
		double bestScore = Double.MAX_VALUE;
		for (Mob mob : server.getEntitiesOfClass(Mob.class, box, SymbioteEntity::isValidHost)) {
			if (mob.getUUID().equals(ignoredTarget) && now < ignoreUntil) {
				continue;
			}
			if (confined && !withinHome(mob.position(), 0.8)) {
				continue;
			}
			double score = mob.distanceToSqr(this) - (mob instanceof Enemy ? 96.0 : 0.0);
			if (score < bestScore) {
				bestScore = score;
				best = mob;
			}
		}
		return best;
	}

	private boolean withinHome(Vec3 p, double slack) {
		double dx = p.x - home.x;
		double dz = p.z - home.z;
		return dx * dx + dz * dz <= (homeRadius + slack) * (homeRadius + slack) && Math.abs(p.y - home.y) < 3.0;
	}

	/**
	 * A mob this organism may take over: alive, able to fight (has an attack-damage attribute), not a
	 * boss of any kind, not a mod event/raid mob, not tamed or owned, not a no-AI display mob, and not
	 * already a Symbiote Host.
	 */
	public static boolean isValidHost(Mob mob) {
		if (!mob.isAlive() || mob.isRemoved() || mob.isNoAi() || mob.isSpectator()) {
			return false;
		}
		if (SymbioteHost.is(mob) || mob.getAttribute(Attributes.ATTACK_DAMAGE) == null) {
			return false;
		}
		if (mob instanceof OwnableEntity owned && owned.getOwnerUUID() != null) {
			return false;
		}
		if (mob.getTags().contains(com.projecthero.mod.event.EventInstance.EVENT_TAG)) {
			return false;
		}
		return !isBoss(mob);
	}

	private static boolean isBoss(Mob mob) {
		return mob.getType().is(net.fabricmc.fabric.api.tag.convention.v2.ConventionalEntityTypeTags.BOSSES)
				|| com.projecthero.mod.titanshifter.TitanCombat.isBoss(mob)
				|| com.projecthero.mod.spider.SpiderWebs.isBoss(mob)
				|| com.projecthero.mod.allmight.AllMightShockwave.isBoss(mob)
				|| mob instanceof com.projecthero.mod.titan.entity.TitanEntity
				|| mob instanceof com.projecthero.mod.titan.entity.DisguisedTitanEntity
				|| mob instanceof com.projecthero.mod.behemoth.entity.AbyssalBehemothEntity
				|| mob instanceof com.projecthero.mod.oathbreaker.entity.OathbreakerEntity
				|| mob instanceof com.projecthero.mod.darkseid.entity.DarkseidEntity;
	}

	private boolean canReach(Mob mob) {
		return getBoundingBox().inflate(0.3, 0.2, 0.3).intersects(mob.getBoundingBox());
	}

	/** Give up on a host it cannot get to (behind glass, up a cliff it keeps slipping off...). */
	private void trackProgress(ServerLevel server, Mob target) {
		double d = target.distanceToSqr(this);
		if (d < targetBestDist - 0.25) {
			targetBestDist = d;
			targetStallTicks = 0;
			return;
		}
		if (++targetStallTicks > 100) {
			ignoredTarget = target.getUUID();
			ignoreUntil = server.getGameTime() + 400L;
			targetId = null;
			targetStallTicks = 0;
		}
	}

	/** Pour into the mob and take control of it. This entity is consumed. */
	public void takeOver(ServerLevel server, Mob mob) {
		Component victim = mob.getDisplayName();
		SymbioteHost.mark(mob);
		double cx = mob.getX();
		double cy = mob.getY() + mob.getBbHeight() * 0.5;
		double cz = mob.getZ();
		double w = mob.getBbWidth() * 0.5;
		double h = mob.getBbHeight() * 0.5;
		server.sendParticles(ParticleTypes.SQUID_INK, cx, cy, cz, 30, w, h, w, 0.06);
		server.sendParticles(ICHOR, cx, cy, cz, 40, w, h, w, 0.0);
		server.sendParticles(ICHOR, getX(), getY() + 0.3, getZ(), 20, 0.4, 0.2, 0.4, 0.0);
		server.sendParticles(ParticleTypes.SCULK_SOUL, cx, cy, cz, 6, w, h, w, 0.02);
		server.sendParticles(ParticleTypes.LARGE_SMOKE, cx, cy, cz, 10, w, h, w, 0.02);
		SymbioteSounds.organic(server, cx, cy, cz, 1.4f, 0.4f);
		server.playSound(null, cx, cy, cz, SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, 1.2f, 0.7f);
		server.playSound(null, cx, cy, cz, SoundEvents.SCULK_CATALYST_BLOOM, SoundSource.HOSTILE, 1.0f, 0.6f);
		for (Player p : server.getEntitiesOfClass(Player.class, mob.getBoundingBox().inflate(24.0))) {
			p.displayClientMessage(Component.translatable("message.projecthero.symbiote.took_host", victim)
					.withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC), false);
		}
		discard();
	}

	// ---------------- fx ----------------

	private void ambience(ServerLevel server, double speed) {
		if (recoilTicks > 0) {
			if (recoilTicks % 3 == 0) {
				server.sendParticles(ICHOR, getX(), getY() + 0.3, getZ(), 4, 0.35, 0.2, 0.35, 0.0);
			}
		} else if (tickCount % 7 == 0) {
			// a light ooze accent -- the model is the visual now
			server.sendParticles(ICHOR, getX(), getY() + 0.15, getZ(), 1, 0.3, 0.05, 0.3, 0.0);
		}
		if (speed > 0.0 && tickCount % 23 == 0) {
			server.sendParticles(ParticleTypes.DRIPPING_OBSIDIAN_TEAR, getX(), getY() + 0.35, getZ(), 1, 0.2, 0.05, 0.2, 0.0);
		}
		if ((tickCount + getId()) % 160 == 0 && random.nextInt(2) == 0) {
			SymbioteSounds.organic(server, getX(), getY(), getZ(), 0.3f, 0.9f + random.nextFloat() * 0.4f);
		}
	}

	// ---------------- client: smooth motion + animation state ----------------

	@Override
	public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
		this.lerpX = x;
		this.lerpY = y;
		this.lerpZ = z;
		this.lerpYRot = yRot;
		this.lerpSteps = Math.max(1, steps);
	}

	@Override
	public double lerpTargetX() {
		return lerpSteps > 0 ? lerpX : getX();
	}

	@Override
	public double lerpTargetY() {
		return lerpSteps > 0 ? lerpY : getY();
	}

	@Override
	public double lerpTargetZ() {
		return lerpSteps > 0 ? lerpZ : getZ();
	}

	@Override
	public float lerpTargetYRot() {
		return lerpSteps > 0 ? lerpYRot : getYRot();
	}

	private void clientTick() {
		if (lerpSteps > 0) {
			lerpPositionAndRotationStep(lerpSteps, lerpX, lerpY, lerpZ, lerpYRot, getXRot());
			lerpSteps--;
		}
		crawlO = crawl;
		bristleO = bristle;
		double dx = getX() - xo;
		double dz = getZ() - zo;
		float speed = (float) Math.sqrt(dx * dx + dz * dz);
		crawl += (Mth.clamp(speed * 9.0f, 0.0f, 1.0f) - crawl) * 0.2f;
		bristle += ((entityData.get(DATA_RECOIL) ? 1.0f : 0.0f) - bristle) * 0.25f;
		alertO = alert;
		huntO = hunt;
		byte mood = mood();
		float alertTarget = mood == MOOD_HELD ? 1.0f : mood == MOOD_FLEE ? -1.0f : 0.0f;
		alert += (alertTarget - alert) * 0.12f;
		hunt += ((mood == MOOD_HUNT ? 1.0f : 0.0f) - hunt) * 0.1f;
	}

	public byte mood() {
		return entityData.get(DATA_MOOD);
	}

	// ---------------- interaction ----------------

	@Override
	public InteractionResult interact(Player player, InteractionHand hand) {
		if (hand != InteractionHand.MAIN_HAND) {
			return InteractionResult.PASS;
		}
		if (!(player instanceof ServerPlayer sp)) {
			return InteractionResult.sidedSuccess(level().isClientSide);
		}
		long now = level().getGameTime();
		Long readyAt = interactCooldown.get(sp.getUUID());
		if (readyAt != null && now < readyAt) {
			return InteractionResult.CONSUME;
		}
		interactCooldown.put(sp.getUUID(), now + 20L);
		holdTicks = Math.max(holdTicks, 60); // it goes still, tasting the air, when someone reaches for it

		if (bondingPlayer != null && !bondingPlayer.equals(sp.getUUID())) {
			sp.displayClientMessage(Component.translatable("message.projecthero.symbiote.entity_busy"), true);
			return InteractionResult.CONSUME;
		}

		// v0.11.15: an empty Symbiote Vial in hand bottles the organism instead of bonding with it.
		if (player.getMainHandItem().is(com.projecthero.mod.symbiote.item.SymbioteHostItems.SYMBIOTE_VIAL)) {
			com.projecthero.mod.symbiote.item.SymbioteVialItem.capture(sp, this);
			return InteractionResult.CONSUME;
		}

		SymbioteBonding.attempt(sp, this);
		return InteractionResult.CONSUME;
	}

	// ---------------- bond lock ----------------

	public boolean claimBond(ServerPlayer player, int durationTicks) {
		if (bondingPlayer != null && !bondingPlayer.equals(player.getUUID())) {
			return false;
		}
		bondingPlayer = player.getUUID();
		bondingExpiresAt = level().getGameTime() + durationTicks;
		return true;
	}

	public void releaseBond() {
		bondingPlayer = null;
	}

	public boolean isHeld() {
		return bondingPlayer != null || holdTicks > 0;
	}

	/** Play the "recoil" bristle after refusing an unworthy host or touching fire. */
	public void recoil() {
		recoilTicks = 30;
	}

	// ---------------- state accessors ----------------

	/** Make this a lab Symbiote: it can never leave {@code radius} blocks (horizontally) of {@code center}. */
	public void setConfined(Vec3 center, double radius) {
		this.home = center;
		this.homeRadius = radius;
		this.confined = true;
	}

	public boolean isConfined() {
		return confined;
	}

	public Vec3 home() {
		return home;
	}

	public double homeRadius() {
		return homeRadius;
	}

	/** Ticks before it starts looking for a host (fresh from a meteorite or a dead host). */
	public void setHuntDelay(int ticks) {
		this.huntDelay = ticks;
	}

	/**
	 * A pre-0.13.19 Symbiote has no home. If it is sitting in a lab containment cell (a lodestone pedestal
	 * below it, tinted glass all around), adopt that cell as its confined home; otherwise it is free.
	 */
	private void adoptLegacyHome(ServerLevel server) {
		BlockPos base = blockPosition();
		BlockPos pedestal = null;
		for (int dy = 0; dy >= -3 && pedestal == null; dy--) {
			if (server.getBlockState(base.offset(0, dy, 0)).is(Blocks.LODESTONE)) {
				pedestal = base.offset(0, dy, 0);
			}
		}
		if (pedestal == null) {
			return;
		}
		int glass = 0;
		for (BlockPos p : BlockPos.betweenClosed(pedestal.offset(-2, 0, -2), pedestal.offset(2, 3, 2))) {
			if (server.getBlockState(p).is(Blocks.TINTED_GLASS)) {
				glass++;
			}
		}
		if (glass >= 6) {
			setConfined(Vec3.atBottomCenterOf(pedestal.above()), LAB_HOME_RADIUS);
		}
	}

	// ---------------- helpers ----------------

	/** A free Symbiote (meteorite, dead host, command). */
	public static SymbioteEntity spawn(ServerLevel level, double x, double y, double z) {
		SymbioteEntity e = SymbioteEntityTypes.SYMBIOTE.create(level);
		if (e == null) {
			return null;
		}
		e.moveTo(x, y, z, level.random.nextFloat() * 360f, 0f);
		e.home = new Vec3(x, y, z);
		level.addFreshEntity(e);
		return e;
	}

	/** A confined lab Symbiote that can never leave {@code radius} blocks of where it spawned. */
	public static SymbioteEntity spawnConfined(ServerLevel level, double x, double y, double z, double radius) {
		SymbioteEntity e = SymbioteEntityTypes.SYMBIOTE.create(level);
		if (e == null) {
			return null;
		}
		e.moveTo(x, y, z, level.random.nextFloat() * 360f, 0f);
		e.setConfined(new Vec3(x, y, z), radius);
		level.addFreshEntity(e);
		return e;
	}

	public static List<SymbioteEntity> near(ServerLevel level, double x, double y, double z, double r) {
		return level.getEntitiesOfClass(SymbioteEntity.class, new AABB(x - r, y - r, z - r, x + r, y + r, z + r));
	}
}
