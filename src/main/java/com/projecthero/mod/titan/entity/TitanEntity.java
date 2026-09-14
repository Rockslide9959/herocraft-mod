package com.projecthero.mod.titan.entity;

import java.util.UUID;

import com.projecthero.mod.event.EventBossBar;
import com.projecthero.mod.event.entity.RaidUndead;
import com.projecthero.mod.titan.TitanConfig;
import com.projecthero.mod.titan.TitanTerrain;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

/**
 * The true Titan -- an ~18-block giant zombie-style boss (spec: temporary placeholder model/texture,
 * kept easy to swap later; see {@code TitanRenderer}). Spawned only via
 * {@link DisguisedTitanEntity#completeTransformation}, never naturally.
 *
 * <h2>Attack state machine</h2>
 * Tick-driven, matching this codebase's established boss-AI convention (e.g.
 * {@code JuggernautZombie}'s own charge state machine, {@code BossPowerController}) rather than a
 * stack of vanilla {@code Goal}s: {@link #tickCombat} runs every tick, resolving an in-progress
 * attack's wind-up/active/recovery phases or, if idle and off the global cooldown, picking a new one
 * via {@link #chooseAttack}.
 */
public class TitanEntity extends RaidUndead {
	/** Height 18 / vanilla zombie height 1.95 -- see {@code TitanConfig.Stats#heightBlocks}'s javadoc
	 *  for why this is a compile-time constant rather than a live-reconfigurable one. */
	public static final float SCALE = 18.0f / 1.95f;

	private enum Attack { NONE, PUNCH, SWEEP, STOMP, SLAM, SHOCKWAVE, GRAB, BOULDER, CHARGE }

	/**
	 * v0.10.11: every telegraphed attack now winds up for a uniform 2 seconds, with its own distinct
	 * charge-up particle signature (see {@link #emitTelegraph}), so a player can read which move is
	 * coming and answer it. CHARGE keeps this as its launch delay; the rest resolve when it hits 0.
	 */
	private static final int WINDUP_TICKS = 40;

	private Attack activeAttack = Attack.NONE;
	private int attackTicks;
	private boolean attackResolved;
	private int globalAttackCooldown;
	private final int[] attackCooldowns = new int[Attack.values().length];

	private Vec3 chargeDirection = Vec3.ZERO;
	private double chargeDistanceLeft;

	private UUID grabbedPlayer;
	private int grabTicksLeft;

	private double lastDistanceToTarget = -1;
	private int fleeSignal;
	private int meleeCooldown;

	private Vec3 lastUnstickPos = Vec3.ZERO;
	private int stuckTicks;

	private EventBossBar bossBar;

	public TitanEntity(EntityType<? extends TitanEntity> type, Level level) {
		super(type, level);
		this.xpReward = 200;
	}

	/**
	 * "changes 23": strip vanilla {@code Zombie}'s own {@code ZombieAttackGoal} (a {@code MeleeAttackGoal}
	 * on a plain ~1s cadence, completely outside {@link #globalAttackCooldown}). Left in place, it fired
	 * a fast, untelegraphed vanilla punch on top of the deliberately slower, telegraphed
	 * {@link #tickCombat} state machine below -- since the Titan has no bespoke animations (it renders as
	 * a scaled-up vanilla zombie model, see {@code TitanEntityRenderers}), that extra attack was
	 * indistinguishable from -- and drowned out -- the intended PUNCH/STOMP/SLAM moves, which is what
	 * read as "the Titan spams its smash attack constantly." {@link #tickApproach} replaces the
	 * chase-into-range half of what that goal used to do; {@link DisguisedTitanEntity} is deliberately
	 * NOT touched here, since it must go on looking and acting like an ordinary zombie until it
	 * transforms.
	 */
	@Override
	protected void registerGoals() {
		super.registerGoals();
		// v0.10.10 (performance): strip every vanilla goal that runs an A* search. Path-finding cost
		// scales with the entity's own footprint -- the node evaluator samples a
		// ceil(width) x ceil(height) x ceil(width) box of block states for EVERY node it visits -- so on
		// an 18-block frame a single search is thousands of times more expensive than it is for an
		// ordinary zombie, and vanilla Zombie/Monster ships four goals that start one on a timer whether
		// or not anything is happening ("he lags out the world just by existing"). The Titan does not
		// need any of them: it hunts through {@link #tickApproach}'s direct steering, and terrain in the
		// way is handled by Step Assist plus {@link #tickUnstick}'s corridor carving.
		this.goalSelector.removeAllGoals(goal ->
				goal instanceof net.minecraft.world.entity.ai.goal.MeleeAttackGoal
						|| goal instanceof net.minecraft.world.entity.ai.goal.MoveThroughVillageGoal
						|| goal instanceof net.minecraft.world.entity.ai.goal.RandomStrollGoal
						|| goal instanceof net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal
						|| goal instanceof net.minecraft.world.entity.ai.goal.RemoveBlockGoal
						|| goal instanceof net.minecraft.world.entity.ai.goal.BreakDoorGoal);
		// Target scans are cheap, but only the player scan is wanted: a world boss chasing a wandering
		// villager or a turtle across the map is both wrong and (via the chase) expensive.
		this.targetSelector.removeAllGoals(goal ->
				goal instanceof net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal);
	}

	public static AttributeSupplier.Builder createAttributes() {
		var stats = TitanConfig.stats();
		var atk = TitanConfig.attacks();
		return Zombie.createAttributes()
				.add(Attributes.MAX_HEALTH, stats.health)
				.add(Attributes.ATTACK_DAMAGE, atk.punchDamage)
				.add(Attributes.MOVEMENT_SPEED, stats.normalSpeed)
				.add(Attributes.FOLLOW_RANGE, stats.followRange)
				.add(Attributes.KNOCKBACK_RESISTANCE, stats.knockbackResistance)
				.add(Attributes.ARMOR, 10.0)
				.add(Attributes.ARMOR_TOUGHNESS, 8.0)
				.add(Attributes.STEP_HEIGHT, stats.stepHeight)
				.add(Attributes.ATTACK_KNOCKBACK, 2.0);
	}

	/**
	 * v0.9.22: a navigator tuned for a huge boss that must run players down across long distances and
	 * broken terrain. Floats over water rather than stalling in it, walks over fences, and searches
	 * many more nodes than a normal mob so it can actually find a route to a target up to
	 * {@link TitanConfig.Stats#followRange} blocks away. Getting physically wedged is handled
	 * separately by {@link #tickUnstick}.
	 */
	@Override
	protected PathNavigation createNavigation(Level level) {
		GroundPathNavigation nav = new GroundPathNavigation(this, level);
		nav.setCanFloat(true);
		nav.setCanWalkOverFences(true);
		// v0.10.10: back down to vanilla's default. Nothing routinely paths any more (see
		// registerGoals / tickApproach); this only bounds the cost if some other system ever asks.
		nav.setMaxVisitedNodesMultiplier(1.0f);
		return nav;
	}

	/**
	 * v0.9.22: the pathfinder may route the Titan down tall slopes and cliffs -- it takes no fall
	 * damage ({@link #causeFallDamage}), so a big "comfortable fall" keeps it from freezing at the top
	 * of every downhill instead of following the player down.
	 */
	@Override
	public int getMaxFallDistance() {
		return 32;
	}

	@Override
	public EntityDimensions getDefaultDimensions(Pose pose) {
		return super.getDefaultDimensions(pose).scale(SCALE);
	}

	@Override
	public boolean isBaby() {
		return false;
	}

	@Override
	public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
		return false; // no normal fall damage -- its own attacks are the only source of terrain interaction
	}

	@Override
	public boolean removeWhenFarAway(double distanceSq) {
		return false;
	}

	/** Called once, right after {@link DisguisedTitanEntity} spawns this entity. */
	public void onTransformed() {
		// setHealth(), not heal() -- heal() only applies while current health is already > 0, which is
		// not a safe assumption to make about a just-constructed entity.
		setHealth(getMaxHealth());
		if (level() instanceof ServerLevel server) {
			server.playSound(null, blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 4.0f, 0.5f);
			server.sendParticles(ParticleTypes.EXPLOSION, getX(), getY() + getBbHeight() * 0.5, getZ(), 1, 0, 0, 0, 0);
		}
		bossBar().update((ServerLevel) level(), blockPosition(), TitanConfig.stats().detectionRange,
				Component.translatable("entity.projecthero.titan"), 1.0f);
	}

	private EventBossBar bossBar() {
		if (bossBar == null) {
			bossBar = new EventBossBar(getUUID(), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS, true);
		}
		return bossBar;
	}

	@Override
	public void aiStep() {
		super.aiStep();
		if (level().isClientSide()) {
			return;
		}
		ServerLevel server = (ServerLevel) level();
		footsteps(server);
		if (TitanConfig.world().passiveWalkingDestructionEnabled) {
			passiveDestruction(server);
		}
		acquireTarget(server);
		tickGrab(server);
		tickApproach();
		tickUnstick(server);
		tickMeleeSwipe(server);
		tickCombat(server);
		updateBossBar(server);
		yellowAura(server);
	}

	/**
	 * v0.10.19: a steady drift of yellow embers off the Titan's body -- makes the boss read as
	 * charged-up/powered even while it is just walking, on top of the yellow already layered into its
	 * attack bursts below.
	 */
	private void yellowAura(ServerLevel server) {
		if (tickCount % 5 != 0) {
			return;
		}
		double w = getBbWidth();
		double h = getBbHeight();
		for (int i = 0; i < 3; i++) {
			double ox = (server.random.nextDouble() - 0.5) * w;
			double oy = server.random.nextDouble() * h;
			double oz = (server.random.nextDouble() - 0.5) * w;
			server.sendParticles(new net.minecraft.core.particles.DustParticleOptions(
							new org.joml.Vector3f(1.0f, 0.82f, 0.15f), 2.2f),
					getX() + ox, getY() + oy, getZ() + oz, 1, 0.0, 0.02, 0.0, 0.0);
		}
	}

	/**
	 * "changes 25": the Titan actively hunts. If it currently has no target, lock onto the nearest
	 * non-creative player inside its detection range straight away rather than waiting on vanilla's
	 * periodic target scan -- a world boss should never lose interest or wander off while a player is
	 * anywhere nearby.
	 */
	private void acquireTarget(ServerLevel server) {
		LivingEntity current = getTarget();
		if (current != null && current.isAlive() && !((current instanceof Player p) && (p.isCreative() || p.isSpectator()))) {
			return;
		}
		Player nearest = server.getNearestPlayer(this, TitanConfig.stats().detectionRange);
		if (nearest != null && nearest.isAlive() && !nearest.isCreative() && !nearest.isSpectator()) {
			setTarget(nearest);
		}
	}

	/**
	 * "changes 25": a plain, reliable melee. The telegraphed PUNCH/STOMP/SLAM moves are the Titan's
	 * showpiece attacks, but between them a player standing right at its feet used to be able to just
	 * hug the leg and whittle it down untouched (vanilla's own melee goal having been stripped in
	 * {@link #registerGoals}). This is a short-cooldown swipe -- big damage, hard knockback -- that lands
	 * whenever a player is within arm's reach, independent of the state machine's global cooldown.
	 */
	private void tickMeleeSwipe(ServerLevel server) {
		if (meleeCooldown > 0) {
			meleeCooldown--;
		}
		if (meleeCooldown > 0 || activeAttack != Attack.NONE || grabbedPlayer != null) {
			return;
		}
		LivingEntity target = getTarget();
		if (target == null || !target.isAlive() || !(target instanceof Player)) {
			return;
		}
		double reach = TitanConfig.attacks().meleeRange + getBbWidth() * 0.5;
		double dx = target.getX() - getX();
		double dz = target.getZ() - getZ();
		double dy = target.getY() - getY();
		if (dx * dx + dz * dz > reach * reach || dy < -3.0 || dy > getBbHeight() + 2.0) {
			return;
		}
		getLookControl().setLookAt(target, 60.0f, 60.0f);
		target.hurt(damageSources().mobAttack(this), scaleForPlayer(target, TitanConfig.attacks().meleeDamage));
		Vec3 push = target.position().subtract(position()).normalize();
		target.setDeltaMovement(target.getDeltaMovement().add(push.x * 1.4, 0.42, push.z * 1.4));
		target.hurtMarked = true;
		meleeCooldown = TitanConfig.attacks().meleeCooldownTicks;
		server.playSound(null, blockPosition(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.HOSTILE, 2.0f, 0.6f);
		server.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY() + 1, target.getZ(), 12, 0.3, 0.3, 0.3, 0.1);
	}

	private static final net.minecraft.resources.ResourceLocation CHASE_SPEED_ID =
			com.projecthero.mod.ProjectHeroMod.id("titan_chase_speed");
	private boolean chaseSpeedActive;

	/**
	 * Replaces the chase-into-range half of vanilla's {@code ZombieAttackGoal} (removed in
	 * {@link #registerGoals}) now that the goal supplying it is gone: keep walking toward the current
	 * target and facing it, but never apply damage here -- damage only ever comes from
	 * {@link #tickCombat}'s own attacks. Paused mid-attack ({@code beginAttack} already parks the
	 * navigator) and while holding a grabbed player, so it does not fight the grab-hold teleport.
	 *
	 * <p>"changes 24": also applies {@link TitanConfig.Stats#aggressiveSpeed} while a target is being
	 * chased -- that field existed in the config from the start but nothing ever read it, so the Titan
	 * always walked at its idle {@code normalSpeed} (0.225, i.e. an ordinary zombie's own pace) even
	 * mid-fight, which reads as sluggish on an 18-block frame with a correspondingly huge stride. A
	 * transient {@link net.minecraft.world.entity.ai.attributes.AttributeModifier} is added the moment a
	 * target appears and removed the moment it is lost, rather than baking the boost into the base
	 * attribute, so the Titan is genuinely calmer (and this boost genuinely readable as "it noticed you")
	 * outside of combat.
	 */
	private void tickApproach() {
		LivingEntity target = getTarget();
		boolean hasTarget = target != null && target.isAlive();
		if (hasTarget != chaseSpeedActive) {
			var speedAttr = getAttribute(Attributes.MOVEMENT_SPEED);
			if (speedAttr != null) {
				speedAttr.removeModifier(CHASE_SPEED_ID);
				if (hasTarget) {
					double boost = TitanConfig.stats().aggressiveSpeed - TitanConfig.stats().normalSpeed;
					speedAttr.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(
							CHASE_SPEED_ID, boost, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE));
				}
			}
			chaseSpeedActive = hasTarget;
		}
		if (!hasTarget || activeAttack != Attack.NONE || grabbedPlayer != null) {
			return;
		}
		getLookControl().setLookAt(target, 30.0f, 30.0f);
		// v0.10.10 (performance): steer straight at the target instead of running an A* search at it.
		// MoveControl just points the body at a position and applies the movement-speed attribute -- no
		// graph search, no node sampling, so it costs effectively nothing however big the Titan is. The
		// old repath-every-10-ticks call was the single largest source of the "he lags out the world"
		// report: each search sampled an ~19x19x19 block box per visited node.
		//
		// Losing the pathfinder loses obstacle avoidance, which for this boss is the point -- terrain is
		// an inconvenience, not a barrier. Step Assist (STEP_HEIGHT 10) walks it up anything shorter
		// than itself and tickUnstick punches a corridor through whatever is left.
		getMoveControl().setWantedPosition(target.getX(), target.getY(), target.getZ(), 1.0);
	}

	/**
	 * v0.9.22: "the Titan can never be hard-stopped by terrain." If it is chasing a target, not
	 * mid-attack, and has made almost no horizontal progress for a full second despite wanting to
	 * close the distance, punch a Titan-sized corridor through whatever is in the way toward the
	 * target and give itself a shove. Step Assist (STEP_HEIGHT 10) covers ordinary ledges; this only
	 * ever fires when something genuinely walled it in.
	 */
	private void tickUnstick(ServerLevel server) {
		LivingEntity target = getTarget();
		if (target == null || !target.isAlive() || activeAttack != Attack.NONE || grabbedPlayer != null) {
			stuckTicks = 0;
			return;
		}
		if (distanceTo(target) <= TitanConfig.attacks().meleeRange) {
			stuckTicks = 0;
			return;
		}
		Vec3 pos = position();
		double dx = pos.x - lastUnstickPos.x;
		double dz = pos.z - lastUnstickPos.z;
		lastUnstickPos = pos;
		if (dx * dx + dz * dz < 0.02 * 0.02) {
			stuckTicks++;
		} else {
			stuckTicks = Math.max(0, stuckTicks - 3);
		}
		// v0.10.10: half a second, not a full one -- with the pathfinder gone (see tickApproach) this is
		// the only thing that gets the Titan past a wall, so it should not dither in front of one.
		if (stuckTicks < 10) {
			return;
		}
		stuckTicks = 0;
		Vec3 dir = new Vec3(target.getX() - getX(), 0, target.getZ() - getZ());
		if (dir.lengthSqr() < 1.0e-4) {
			return;
		}
		dir = dir.normalize();
		int width = (int) Math.ceil(getBbWidth()) + 2;
		Vec3 from = position().add(0, 0.5, 0);
		Vec3 to = from.add(dir.scale(width + 3.0));
		TitanTerrain.carveCorridor(server, from, to, width, Math.min(12, (int) Math.ceil(getBbHeight())));
		setDeltaMovement(getDeltaMovement().add(dir.x * 0.4, 0.05, dir.z * 0.4));
		// v0.10.10: steer, don't path -- see tickApproach for why no A* search runs here any more.
		getMoveControl().setWantedPosition(target.getX(), target.getY(), target.getZ(), 1.0);
	}

	private void updateBossBar(ServerLevel server) {
		if (isRemoved() || isDeadOrDying()) {
			return;
		}
		// v0.10.10: five times a second is plenty for a health bar, and each refresh walks the level's
		// whole player list to recompute the audience.
		if (tickCount % 4 != 0) {
			return;
		}
		float progress = Math.max(0.0f, getHealth() / getMaxHealth());
		bossBar().update(server, blockPosition(), TitanConfig.stats().followRange,
				Component.translatable("entity.projecthero.titan"), progress);
	}

	// ---------------- footsteps / terrain ----------------

	/**
	 * "changes 24": gated on actually walking, not just standing on the ground -- it used to fire every
	 * 8 ticks purely off {@code onGround()}, so an idle Titan just standing there (which is most of a
	 * fight: it stops moving for every attack windup, and now also whenever it has no target) kept
	 * booming a heavy impact sound nonstop for no visible reason. {@code getDeltaMovement()} is the
	 * server's own authoritative velocity, so this reads real movement rather than guessing from the
	 * navigator's state.
	 */
	private void footsteps(ServerLevel server) {
		if (tickCount % 8 != 0 || !onGround()) {
			return;
		}
		Vec3 delta = getDeltaMovement();
		if (delta.x * delta.x + delta.z * delta.z < 0.003) {
			return;
		}
		server.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 0.1, getZ(), 8, getBbWidth() * 0.3, 0.05,
				getBbWidth() * 0.3, 0.02);
		server.playSound(null, blockPosition(), SoundEvents.GENERIC_BIG_FALL, SoundSource.HOSTILE, 2.5f, 0.5f);
	}

	/** Only genuinely fragile blocks the huge body intersects -- see {@link TitanTerrain#breakFragileAt}. */
	/**
	 * v0.10.10 (performance): this used to walk the Titan's ENTIRE bounding box -- ~10 x 18 x 10, near
	 * enough two thousand {@code getBlockState} lookups -- every 10 ticks, standing still or not. That
	 * is a per-tick average of a couple of hundred block reads spent almost entirely on air, for a
	 * cosmetic effect (brushing leaves and glass aside).
	 *
	 * <p>Now it only runs while the Titan is actually moving, and only scans the slab it is walking
	 * <em>through</em> -- from its feet to a little over head height on a player's scale -- rather than
	 * the full column up to its shoulders, which is where nothing it walks into ever is. That is roughly
	 * a twentieth of the work, and none of it while it is standing in a wind-up.
	 */
	private void passiveDestruction(ServerLevel server) {
		if (tickCount % 10 != 0 || !TitanConfig.world().blockDestructionEnabled) {
			return;
		}
		Vec3 delta = getDeltaMovement();
		if (delta.x * delta.x + delta.z * delta.z < 0.003) {
			return;
		}
		var box = getBoundingBox();
		int minY = Mth.floor(box.minY);
		int maxY = Math.min(Mth.floor(box.maxY), minY + 3);
		for (BlockPos p : BlockPos.betweenClosed(
				BlockPos.containing(box.minX, minY, box.minZ), BlockPos.containing(box.maxX, maxY, box.maxZ))) {
			TitanTerrain.breakFragileAt(server, p);
		}
	}

	// ---------------- attack state machine ----------------

	private void tickCombat(ServerLevel server) {
		for (int i = 0; i < attackCooldowns.length; i++) {
			if (attackCooldowns[i] > 0) {
				attackCooldowns[i]--;
			}
		}
		if (globalAttackCooldown > 0) {
			globalAttackCooldown--;
		}

		LivingEntity target = getTarget();
		if (activeAttack != Attack.NONE) {
			resolveActiveAttack(server, target);
			return;
		}
		if (target == null || !target.isAlive() || globalAttackCooldown > 0 || grabbedPlayer != null) {
			return;
		}
		trackFleeSignal(target);
		Attack chosen = chooseAttack(target);
		if (chosen != Attack.NONE) {
			beginAttack(server, chosen, target);
		}
	}

	private void trackFleeSignal(LivingEntity target) {
		double d = distanceTo(target);
		if (lastDistanceToTarget >= 0) {
			fleeSignal = d > lastDistanceToTarget + 0.05 ? Math.min(20, fleeSignal + 1) : Math.max(0, fleeSignal - 1);
		}
		lastDistanceToTarget = d;
	}

	private Attack chooseAttack(LivingEntity target) {
		double dist = distanceTo(target);
		double dy = target.getY() - getY();
		boolean underfoot = dist < 5.0 && dy < -1.5;
		boolean veryClose = dist < 4.0;
		boolean fleeing = fleeSignal >= 8;
		boolean airborne = !target.onGround() && target.getY() - getY() > 3.0;

		java.util.List<Attack> pool = new java.util.ArrayList<>();
		if (dist <= TitanConfig.attacks().punchRange && ready(Attack.PUNCH)) {
			addWeighted(pool, Attack.PUNCH, 3);
		}
		// v0.10.11: a wide backhand sweep -- a mid-range alternative to the single-target punch that
		// catches a whole group standing in front of the Titan and hurls them back.
		if (dist <= 12.0 && ready(Attack.SWEEP)) {
			addWeighted(pool, Attack.SWEEP, veryClose ? 4 : 3);
		}
		// v0.10.11: a ground shockwave -- a big radial pulse that reaches much further than Stomp/Slam
		// and knocks everyone in the ring straight up.
		if (dist <= TitanConfig.attacks().shockwaveRadius && ready(Attack.SHOCKWAVE)) {
			addWeighted(pool, Attack.SHOCKWAVE, fleeing || dist > 8.0 ? 4 : 2);
		}
		if (underfoot && ready(Attack.STOMP)) {
			addWeighted(pool, Attack.STOMP, 6);
		} else if (dist <= 6.0 && ready(Attack.STOMP)) {
			addWeighted(pool, Attack.STOMP, 1);
		}
		// "changes 24": Ground Slam's actual damage is an AoE of only TitanConfig.attacks().slamRadius
		// (6.0 by default) centred on the Titan itself once the 18-tick windup ends -- and beginAttack()
		// stops the navigator for the whole windup, so the Titan does not close distance during it. The
		// old `dist <= 20.0` selection let it pick Slam from up to 20 blocks away, which is a guaranteed
		// total whiff against a target nowhere near the blast by the time it lands -- this is what read
		// as "his moves don't seem to do AoE damage like they should." Tightened to the radius plus a
		// small margin for the target's own movement during the windup, matching Stomp's existing
		// radius-plus-a-little-slack fallback range just above.
		if (dist <= TitanConfig.attacks().slamRadius + 3.0 && ready(Attack.SLAM)) {
			addWeighted(pool, Attack.SLAM, veryClose ? 4 : 2);
		}
		if (dist <= 6.0 && !airborne && ready(Attack.GRAB) && target instanceof ServerPlayer) {
			addWeighted(pool, Attack.GRAB, veryClose ? 4 : 2);
		}
		// v0.9.22: capped -- with a 150-block detection range the Titan would otherwise lob boulders
		// from across the map. Beyond this it just closes the distance on foot.
		if (dist > 6.0 && dist <= 48.0 && ready(Attack.BOULDER)) {
			addWeighted(pool, Attack.BOULDER, fleeing || airborne ? 5 : 2);
		}
		if (dist > 6.0 && dist < TitanConfig.attacks().chargeMaxDistance && hasLineOfSight(target) && ready(Attack.CHARGE)) {
			addWeighted(pool, Attack.CHARGE, fleeing ? 5 : 2);
		}
		if (pool.isEmpty()) {
			return Attack.NONE;
		}
		return pool.get(getRandom().nextInt(pool.size()));
	}

	private static void addWeighted(java.util.List<Attack> pool, Attack a, int weight) {
		for (int i = 0; i < weight; i++) {
			pool.add(a);
		}
	}

	private boolean ready(Attack a) {
		return attackCooldowns[a.ordinal()] <= 0;
	}

	private void beginAttack(ServerLevel server, Attack attack, LivingEntity target) {
		activeAttack = attack;
		attackResolved = false;
		getNavigation().stop();
		// v0.10.10: the mover is what actually drives it now, so parking the (unused) navigator is not
		// enough on its own to hold it still for an attack wind-up.
		getMoveControl().setWantedPosition(getX(), getY(), getZ(), 0.0);
		// v0.10.11: uniform 2 s wind-up for every attack.
		attackTicks = WINDUP_TICKS;
		if (attack == Attack.CHARGE) {
			Vec3 dir = new Vec3(target.getX() - getX(), 0, target.getZ() - getZ());
			chargeDirection = dir.lengthSqr() < 1.0e-4 ? Vec3.directionFromRotation(0, getYRot()) : dir.normalize();
			chargeDistanceLeft = TitanConfig.attacks().chargeMaxDistance;
		}
		server.playSound(null, blockPosition(), telegraphSound(attack), SoundSource.HOSTILE, 2.6f, 0.5f);
	}

	private static net.minecraft.sounds.SoundEvent telegraphSound(Attack a) {
		return switch (a) {
			case CHARGE -> SoundEvents.RAVAGER_ROAR;
			case SLAM, SHOCKWAVE -> SoundEvents.WARDEN_SONIC_CHARGE;
			case BOULDER -> SoundEvents.RAVAGER_STUNNED;
			case GRAB -> SoundEvents.WARDEN_TENDRIL_CLICKS;
			case SWEEP -> SoundEvents.RAVAGER_ATTACK;
			default -> SoundEvents.RAVAGER_CELEBRATE;
		};
	}

	private void resolveActiveAttack(ServerLevel server, LivingEntity target) {
		attackTicks--;
		// The 2-second charge-up: a per-attack particle tell every tick until it resolves.
		if (attackTicks > 0) {
			emitTelegraph(server, activeAttack, attackTicks, target);
			if (activeAttack == Attack.CHARGE) {
				return; // still winding up
			}
		}
		switch (activeAttack) {
			case PUNCH -> {
				if (attackTicks == 0 && !attackResolved) {
					doPunch(server, target);
				}
			}
			case SWEEP -> {
				if (attackTicks == 0 && !attackResolved) {
					doSweep(server);
				}
			}
			case STOMP -> {
				if (attackTicks == 0 && !attackResolved) {
					doStomp(server);
				}
			}
			case SLAM -> {
				if (attackTicks == 0 && !attackResolved) {
					doSlam(server);
				}
			}
			case SHOCKWAVE -> {
				if (attackTicks == 0 && !attackResolved) {
					doShockwave(server);
				}
			}
			case GRAB -> {
				if (attackTicks == 0 && !attackResolved) {
					doGrabAttempt(server, target);
				}
			}
			case BOULDER -> {
				if (attackTicks == 0 && !attackResolved) {
					doBoulder(server, target);
				}
			}
			case CHARGE -> {
				tickCharge(server);
				return;
			}
			default -> {
			}
		}
		if (attackTicks <= 0) {
			endAttack(activeAttack);
		}
	}

	/**
	 * The charge-up particle signature for {@code attack}, drawn every tick of its 2-second wind-up.
	 * Each move looks different so the telegraph is readable: a cocked fist, a sweeping arc at chest
	 * height, dust gathering under a raised foot, a crackling column overhead, a contracting ground
	 * ring, a reaching hand, a boulder forming in the palm, or the ground being pawed before a charge.
	 */
	private void emitTelegraph(ServerLevel server, Attack a, int remaining, LivingEntity target) {
		double cx = getX();
		double cy = getY();
		double cz = getZ();
		double h = getBbHeight();
		double w = getBbWidth();
		double progress = 1.0 - remaining / (double) WINDUP_TICKS;
		Vec3 aim = target != null && target.isAlive()
				? new Vec3(target.getX() - cx, 0, target.getZ() - cz)
				: Vec3.directionFromRotation(0, getYRot());
		aim = aim.lengthSqr() < 1.0e-4 ? Vec3.directionFromRotation(0, getYRot()) : aim.normalize();
		Vec3 side = new Vec3(-aim.z, 0, aim.x);
		switch (a) {
			case PUNCH -> {
				Vec3 fist = new Vec3(cx, cy + h * 0.78, cz).subtract(aim.scale(w * 0.45));
				server.sendParticles(ParticleTypes.CRIT, fist.x, fist.y, fist.z, 5, 0.35, 0.35, 0.35, 0.03);
			}
			case SWEEP -> {
				double ang = (remaining / (double) WINDUP_TICKS) * Math.PI - Math.PI / 2.0;
				Vec3 p = new Vec3(cx, cy + h * 0.55, cz)
						.add(side.scale(Math.cos(ang) * w * 0.9)).add(aim.scale(w * 0.6));
				server.sendParticles(ParticleTypes.SWEEP_ATTACK, p.x, p.y, p.z, 1, 0, 0, 0, 0);
				server.sendParticles(ParticleTypes.CLOUD, p.x, p.y, p.z, 2, 0.15, 0.15, 0.15, 0.0);
			}
			case STOMP -> {
				server.sendParticles(ParticleTypes.CLOUD, cx, cy + 0.1, cz, 4, w * 0.35, 0.05, w * 0.35, 0.01);
				server.sendParticles(ParticleTypes.POOF, cx, cy + 0.1, cz, 2, w * 0.3, 0.02, w * 0.3, 0.0);
			}
			case SLAM -> {
				server.sendParticles(ParticleTypes.ELECTRIC_SPARK, cx, cy + h + 0.6, cz,
						6, w * 0.4, 0.3, w * 0.4, 0.06);
				server.sendParticles(ParticleTypes.CRIT, cx, cy + h * (0.6 + 0.45 * progress), cz,
						4, w * 0.3, 0.4, w * 0.3, 0.02);
			}
			case SHOCKWAVE -> {
				double rad = 1.5 + 6.0 * (remaining / (double) WINDUP_TICKS);
				for (int i = 0; i < 8; i++) {
					double aa = server.random.nextDouble() * Math.PI * 2;
					server.sendParticles(ParticleTypes.SCULK_SOUL,
							cx + Math.cos(aa) * rad, cy + 0.35, cz + Math.sin(aa) * rad, 1, 0, 0, 0, 0);
				}
			}
			case GRAB -> {
				Vec3 hand = new Vec3(cx, cy + h * 0.6, cz).add(aim.scale(w * 0.7));
				server.sendParticles(ParticleTypes.PORTAL, hand.x, hand.y, hand.z, 6, 0.3, 0.4, 0.3, 0.05);
				server.sendParticles(ParticleTypes.ENCHANT, hand.x, hand.y + 1.0, hand.z, 4, 0.2, 0.2, 0.2, 0.1);
			}
			case BOULDER -> {
				Vec3 hand = new Vec3(cx, cy + h * 0.66, cz).add(aim.scale(w * 0.35));
				server.sendParticles(ParticleTypes.LARGE_SMOKE, hand.x, hand.y, hand.z,
						3, 0.35 + 0.3 * progress, 0.35, 0.35 + 0.3 * progress, 0.0);
				server.sendParticles(ParticleTypes.LAVA, hand.x, hand.y, hand.z, 1, 0.2, 0.2, 0.2, 0.0);
			}
			case CHARGE -> {
				Vec3 back = aim.scale(-1);
				server.sendParticles(ParticleTypes.ANGRY_VILLAGER, cx, cy + h * 0.85, cz, 1,
						w * 0.3, 0.2, w * 0.3, 0.0);
				server.sendParticles(ParticleTypes.CLOUD,
						cx + back.x * w * 0.5, cy + 0.1, cz + back.z * w * 0.5,
						4, w * 0.2, 0.05, w * 0.2, 0.03);
			}
			default -> {
			}
		}
	}

	private void endAttack(Attack a) {
		attackCooldowns[a.ordinal()] = cooldownFor(a);
		globalAttackCooldown = TitanConfig.cooldowns().globalAttackDelay;
		activeAttack = Attack.NONE;
		attackResolved = false;
	}

	private int cooldownFor(Attack a) {
		var cd = TitanConfig.cooldowns();
		return switch (a) {
			case PUNCH -> cd.punch;
			case SWEEP -> cd.sweep;
			case STOMP -> cd.stomp;
			case SLAM -> cd.groundSlam;
			case SHOCKWAVE -> cd.shockwave;
			case GRAB -> cd.grab;
			case BOULDER -> cd.boulder;
			case CHARGE -> cd.charge;
			default -> 20;
		};
	}

	// ---------------- individual attacks ----------------

	/**
	 * v0.10.12: the Titan must not one-shot a healthy player. Every hit it lands on a player is
	 * capped at this fraction of their max health <em>before</em> armour is applied, so a full-diamond
	 * player takes a solid but survivable blow, and even an unarmoured one is left with a sliver
	 * rather than instantly dead. Non-players (its own thrown boulders, mobs) are unaffected.
	 */
	private static final double PLAYER_DAMAGE_CAP_FRACTION = 0.8;

	public static float scaleForPlayer(LivingEntity target, double amount) {
		if (target instanceof net.minecraft.world.entity.player.Player pl) {
			return (float) Math.min(amount, pl.getMaxHealth() * PLAYER_DAMAGE_CAP_FRACTION);
		}
		return (float) amount;
	}

	private void doPunch(ServerLevel server, LivingEntity target) {
		attackResolved = true;
		if (target == null || distanceTo(target) > TitanConfig.attacks().punchRange + 1.0) {
			return;
		}
		target.hurt(damageSources().mobAttack(this), scaleForPlayer(target, TitanConfig.attacks().punchDamage));
		Vec3 push = target.position().subtract(position()).normalize();
		target.setDeltaMovement(target.getDeltaMovement().add(push.x * 1.6, 0.4, push.z * 1.6));
		target.hurtMarked = true;
		server.sendParticles(ParticleTypes.CLOUD, target.getX(), target.getY() + 1, target.getZ(), 10, 0.3, 0.3, 0.3, 0.05);
		server.playSound(null, blockPosition(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.HOSTILE, 2.0f, 0.5f);
	}

	/** Wide backhand: a ~180-degree forward arc, big damage-free-ish knockback, catches a whole group. */
	private void doSweep(ServerLevel server) {
		attackResolved = true;
		Vec3 facing = Vec3.directionFromRotation(0, getYRot());
		Vec3 center = position();
		double reach = TitanConfig.attacks().sweepRange + getBbWidth() * 0.5;
		for (LivingEntity le : nearbyLiving(reach)) {
			Vec3 flat = new Vec3(le.getX() - center.x, 0, le.getZ() - center.z);
			if (flat.lengthSqr() > 1.0e-4 && flat.normalize().dot(facing) < 0.0) {
				continue; // behind the swing
			}
			le.hurt(damageSources().mobAttack(this), scaleForPlayer(le, TitanConfig.attacks().sweepDamage));
			Vec3 push = flat.lengthSqr() < 1.0e-4 ? facing : flat.normalize();
			le.setDeltaMovement(push.x * 2.6, 0.55, push.z * 2.6);
			le.hurtMarked = true;
		}
		server.sendParticles(ParticleTypes.SWEEP_ATTACK, getX() + facing.x * getBbWidth() * 0.6,
				getY() + getBbHeight() * 0.5, getZ() + facing.z * getBbWidth() * 0.6,
				8, getBbWidth() * 0.6, 0.4, getBbWidth() * 0.6, 0.0);
		server.playSound(null, blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 2.6f, 0.4f);
	}

	/** Ground shockwave: a big radial pulse that reaches far past Stomp/Slam and knocks everyone up. */
	private void doShockwave(ServerLevel server) {
		attackResolved = true;
		double r = TitanConfig.attacks().shockwaveRadius;
		Vec3 center = position();
		for (LivingEntity le : nearbyLiving(r)) {
			double d = le.position().distanceTo(center);
			float dmg = (float) (TitanConfig.attacks().shockwaveDamage * (1.0 - Math.min(0.6, d / r)));
			le.hurt(damageSources().mobAttack(this), scaleForPlayer(le, dmg));
			Vec3 push = le.position().subtract(center);
			push = push.lengthSqr() < 1.0e-4 ? Vec3.directionFromRotation(0, getYRot()) : push.normalize();
			le.setDeltaMovement(push.x * 1.3, 0.95, push.z * 1.3);
			le.hurtMarked = true;
		}
		for (int step = 1; step <= (int) r; step += 2) {
			for (int i = 0; i < 24; i++) {
				double a = i / 24.0 * Math.PI * 2;
				server.sendParticles(ParticleTypes.CLOUD, center.x + Math.cos(a) * step, center.y + 0.25,
						center.z + Math.sin(a) * step, 1, 0.0, 0.0, 0.0, 0.0);
			}
		}
		TitanTerrain.breakCluster(server, blockPosition(), Math.min(6.0, r * 0.4), false);
		server.sendParticles(ParticleTypes.EXPLOSION_EMITTER, getX(), getY(), getZ(), 1, 0, 0, 0, 0);
		server.sendParticles(new net.minecraft.core.particles.DustParticleOptions(
						new org.joml.Vector3f(1.0f, 0.82f, 0.15f), 2.6f),
				getX(), getY() + 1.0, getZ(), 40, getBbWidth() * 0.5, 0.6, getBbWidth() * 0.5, 0.1);
		server.playSound(null, blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 3.0f, 0.5f);
	}

	private void doStomp(ServerLevel server) {
		attackResolved = true;
		Vec3 center = position();
		for (LivingEntity le : nearbyLiving(TitanConfig.attacks().stompRadius)) {
			le.hurt(damageSources().mobAttack(this), scaleForPlayer(le, TitanConfig.attacks().stompDamage));
			Vec3 push = le.position().subtract(center).normalize();
			le.setDeltaMovement(push.x * 1.4, 0.6, push.z * 1.4);
			le.hurtMarked = true;
		}
		TitanTerrain.breakCluster(server, blockPosition(), TitanConfig.attacks().stompRadius, false);
		server.sendParticles(ParticleTypes.EXPLOSION, getX(), getY(), getZ(), 1, 0, 0, 0, 0);
		server.sendParticles(new net.minecraft.core.particles.DustParticleOptions(
						new org.joml.Vector3f(1.0f, 0.82f, 0.15f), 2.6f),
				getX(), getY() + 0.5, getZ(), 30, getBbWidth() * 0.5, 0.4, getBbWidth() * 0.5, 0.08);
		server.playSound(null, blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 2.5f, 0.6f);
	}

	private void doSlam(ServerLevel server) {
		attackResolved = true;
		Vec3 center = position();
		for (LivingEntity le : nearbyLiving(TitanConfig.attacks().slamRadius)) {
			le.hurt(damageSources().mobAttack(this), scaleForPlayer(le, TitanConfig.attacks().slamDamage));
			Vec3 push = le.position().subtract(center).normalize();
			le.setDeltaMovement(push.x * 1.2, 0.5, push.z * 1.2);
			le.hurtMarked = true;
		}
		TitanTerrain.breakCluster(server, blockPosition(), TitanConfig.attacks().slamRadius, false);
		server.sendParticles(ParticleTypes.EXPLOSION_EMITTER, getX(), getY(), getZ(), 1, 0, 0, 0, 0);
		server.sendParticles(new net.minecraft.core.particles.DustParticleOptions(
						new org.joml.Vector3f(1.0f, 0.82f, 0.15f), 2.6f),
				getX(), getY() + 0.5, getZ(), 40, getBbWidth() * 0.5, 0.5, getBbWidth() * 0.5, 0.1);
		server.playSound(null, blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 3.0f, 0.4f);
	}

	private void doGrabAttempt(ServerLevel server, LivingEntity target) {
		attackResolved = true;
		if (!(target instanceof ServerPlayer player) || distanceTo(player) > 7.0) {
			return;
		}
		player.hurt(damageSources().mobAttack(this), scaleForPlayer(player, TitanConfig.attacks().grabDamage));
		grabbedPlayer = player.getUUID();
		grabTicksLeft = 30; // 1.5s hold
		server.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 1, player.getZ(), 10, 0.3, 0.3, 0.3, 0.02);
		server.playSound(null, blockPosition(), SoundEvents.PLAYER_HURT, SoundSource.HOSTILE, 2.0f, 0.4f);
	}

	private void tickGrab(ServerLevel server) {
		if (grabbedPlayer == null) {
			return;
		}
		ServerPlayer player = server.getServer().getPlayerList().getPlayer(grabbedPlayer);
		boolean stillValid = player != null && player.isAlive() && !player.isRemoved()
				&& player.level() == level() && distanceTo(player) < 16.0 && this.isAlive();
		if (!stillValid) {
			releaseGrab();
			return;
		}
		// Hold near the Titan's "hand" -- shoulder height, one body-width off to the side.
		Vec3 look = Vec3.directionFromRotation(0, getYRot());
		Vec3 side = new Vec3(-look.z, 0, look.x);
		Vec3 hold = position().add(0, getBbHeight() * 0.55, 0).add(side.scale(getBbWidth() * 0.4));
		player.teleportTo(hold.x, hold.y, hold.z);
		player.setDeltaMovement(Vec3.ZERO);
		player.fallDistance = 0;
		player.hurtMarked = true;

		grabTicksLeft--;
		if (grabTicksLeft <= 0) {
			player.hurt(damageSources().mobAttack(this), scaleForPlayer(player, TitanConfig.attacks().holdDamage));
			throwPlayer(server, player);
			releaseGrab();
		}
	}

	private void throwPlayer(ServerLevel server, ServerPlayer player) {
		double dist = TitanConfig.attacks().throwHorizontalMin
				+ getRandom().nextDouble() * (TitanConfig.attacks().throwHorizontalMax - TitanConfig.attacks().throwHorizontalMin);
		double up = TitanConfig.attacks().throwVerticalMin
				+ getRandom().nextDouble() * (TitanConfig.attacks().throwVerticalMax - TitanConfig.attacks().throwVerticalMin);
		Vec3 dir = player.position().subtract(position());
		Vec3 flat = new Vec3(dir.x, 0, dir.z);
		flat = flat.lengthSqr() < 1.0e-4 ? Vec3.directionFromRotation(0, getYRot()) : flat.normalize();
		// Convert the desired horizontal distance into a launch speed under gravity (v^2 = d*g / sin(2*45)).
		double speed = Math.sqrt(Math.max(1.0, dist * 0.07 * 2));
		player.setDeltaMovement(flat.x * speed * 0.35, up * 0.11, flat.z * speed * 0.35);
		player.hurtMarked = true;
		player.hasImpulse = true;
		player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(player));
		player.hurt(damageSources().mobAttack(this), scaleForPlayer(player, TitanConfig.attacks().throwDamage));
		server.playSound(null, blockPosition(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.HOSTILE, 2.5f, 0.4f);
	}

	/** Release paths: end-of-hold throw, Titan death, player death, disconnect, dimension change. */
	public void releaseGrab() {
		grabbedPlayer = null;
		grabTicksLeft = 0;
	}

	public boolean isGrabbing(UUID playerId) {
		return playerId.equals(grabbedPlayer);
	}

	private void doBoulder(ServerLevel server, LivingEntity target) {
		attackResolved = true;
		if (target == null) {
			return;
		}
		var ground = server.getBlockState(blockPosition().below());
		TitanBoulderEntity boulder = new TitanBoulderEntity(server, this, TitanBoulderEntity.itemForGround(ground));
		Vec3 from = position().add(0, getBbHeight() * 0.6, 0);
		// Lead the target's current velocity a little -- a simple, dodgeable prediction.
		Vec3 aim = target.position().add(target.getDeltaMovement().scale(8)).subtract(from);
		double dist = aim.length();
		boulder.setPos(from.x, from.y, from.z);
		float velocity = (float) Math.min(2.2, 0.9 + dist * 0.03);
		boulder.shoot(aim.x, aim.y + dist * 0.08, aim.z, velocity, 2.0f);
		server.addFreshEntity(boulder);
		server.sendParticles(ParticleTypes.POOF, from.x, from.y, from.z, 20, 0.4, 0.2, 0.4, 0.05);
		server.playSound(null, blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.5f, 1.2f);
	}

	private void tickCharge(ServerLevel server) {
		// First tick of the run (the 20-tick wind-up has just finished): re-lock the charge line onto
		// where the target actually is NOW, so a player who side-stepped during the wind-up is still
		// chased rather than the Titan barrelling at their old position. `attackResolved` doubles as the
		// "charge has launched" flag here (CHARGE never uses it for anything else).
		if (!attackResolved) {
			attackResolved = true;
			LivingEntity t = getTarget();
			if (t != null && t.isAlive()) {
				Vec3 d = new Vec3(t.getX() - getX(), 0, t.getZ() - getZ());
				if (d.lengthSqr() > 1.0e-4) {
					chargeDirection = d.normalize();
				}
			}
			server.playSound(null, blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 3.5f, 0.4f);
		}
		// chargeSpeed is already in blocks/tick -- apply it straight to the velocity. (The old code
		// multiplied it by 20, launching the Titan ~7 blocks/tick: it crossed the whole charge distance
		// in a couple of ticks, usually clipping a wall and ending instantly -- "the charge is broken".)
		double speed = TitanConfig.stats().chargeSpeed;
		setDeltaMovement(chargeDirection.x * speed, getDeltaMovement().y, chargeDirection.z * speed);
		hasImpulse = true;
		chargeDistanceLeft -= speed;
		TitanTerrain.breakAlongPath(server, position(), position().add(chargeDirection.scale(2)), getBbWidth());
		if (server.getGameTime() % 3 == 0) {
			server.sendParticles(ParticleTypes.CLOUD, getX(), getY(), getZ(), 3, getBbWidth() * 0.3, 0.1, getBbWidth() * 0.3, 0.0);
		}

		boolean hitSomething = false;
		for (LivingEntity le : nearbyLiving(getBbWidth() * 0.6 + 1.0)) {
			le.hurt(damageSources().mobAttack(this), scaleForPlayer(le, TitanConfig.attacks().chargeDamage));
			double throwDist = TitanConfig.attacks().chargeThrowMin
					+ getRandom().nextDouble() * (TitanConfig.attacks().chargeThrowMax - TitanConfig.attacks().chargeThrowMin);
			Vec3 push = chargeDirection.scale(throwDist * 0.12).add(0, 0.5, 0);
			le.setDeltaMovement(push);
			le.hurtMarked = true;
			hitSomething = true;
		}
		if (hitSomething || chargeDistanceLeft <= 0 || horizontalCollision) {
			setDeltaMovement(0, getDeltaMovement().y, 0);
			endAttack(Attack.CHARGE);
		}
	}

	private java.util.List<LivingEntity> nearbyLiving(double radius) {
		return level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(radius),
				le -> le != this && le.isAlive() && (le instanceof Player));
	}

	// ---------------- death ----------------

	private int deathTicks = -1;

	@Override
	public void die(DamageSource source) {
		if (deathTicks >= 0) {
			return;
		}
		releaseGrab();
		activeAttack = Attack.NONE;
		setDeltaMovement(Vec3.ZERO);
		setNoAi(true);
		deathTicks = 30; // 1.5s stagger/collapse before removal
		if (level() instanceof ServerLevel server) {
			server.playSound(null, blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 3.0f, 0.3f);
			server.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + getBbHeight() * 0.5, getZ(),
					40, getBbWidth() * 0.4, getBbHeight() * 0.3, getBbWidth() * 0.4, 0.03);
			dropLoot(server, source);
		}
	}

	private void dropLoot(ServerLevel server, DamageSource source) {
		var lootTable = server.getServer().reloadableRegistries().getLootTable(
				net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.LOOT_TABLE,
						com.projecthero.mod.ProjectHeroMod.id("entities/titan")));
		LootParams.Builder params = new LootParams.Builder(server)
				.withParameter(LootContextParams.THIS_ENTITY, this)
				.withParameter(LootContextParams.ORIGIN, position())
				.withParameter(LootContextParams.DAMAGE_SOURCE, source)
				.withOptionalParameter(LootContextParams.ATTACKING_ENTITY, source.getEntity())
				.withOptionalParameter(LootContextParams.DIRECT_ATTACKING_ENTITY, source.getDirectEntity())
				.withLuck(0);
		lootTable.getRandomItems(params.create(LootContextParamSets.ENTITY), this.getLootTableSeed(), stack -> spawnAtLocation(stack));
	}

	@Override
	public void tick() {
		super.tick();
		if (deathTicks < 0) {
			return;
		}
		if (level() instanceof ServerLevel server && deathTicks % 5 == 0) {
			server.sendParticles(ParticleTypes.POOF, getX(), getY() + getBbHeight() * 0.3, getZ(),
					15, getBbWidth() * 0.3, 0.3, getBbWidth() * 0.3, 0.02);
		}
		deathTicks--;
		if (deathTicks == 0) {
			bossBar.clear((ServerLevel) level());
			remove(Entity.RemovalReason.KILLED);
		}
	}

	@Override
	public void remove(Entity.RemovalReason reason) {
		releaseGrab();
		if (bossBar != null && level() instanceof ServerLevel server) {
			bossBar.clear(server);
		}
		super.remove(reason);
	}

	@Override
	public void addAdditionalSaveData(CompoundTag tag) {
		super.addAdditionalSaveData(tag);
	}
}
