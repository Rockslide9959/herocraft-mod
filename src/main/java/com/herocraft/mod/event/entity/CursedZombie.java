package com.herocraft.mod.event.entity;

import com.herocraft.mod.grave.CurseSource;
import com.herocraft.mod.grave.GraveboundCurse;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * The rare naturally spawning carrier of the Gravebound Curse -- the second entry point into the
 * Zombie Raid, for players who have not found a Graveyard (spec sections 2-10).
 *
 * <h2>Deliberately not a miniboss</h2>
 * Stronger than a zombie, nowhere near a Juggernaut: modestly more health, damage and speed, and a
 * much longer follow range with persistent pursuit. Its threat is the curse it carries, not its
 * damage output, so a prepared player can fight it and an unprepared one can run from it -- which is
 * the whole point of making it visually obvious.
 *
 * <h2>Being seen coming</h2>
 * It is permanently glowing (a purple outline through walls at close range), trails a small number of
 * soul particles, and calls out with low, distorted ambient sounds. The particle budget is
 * deliberately tiny -- a handful every half second, from the server, only while a player is close
 * enough to be sent them -- because "do not create excessive particles" is an explicit requirement and
 * these mobs spawn in the open world rather than inside a managed event.
 *
 * <h2>Left on vanilla despawn rules, on purpose</h2>
 * It is tempting to make a rare encounter persistent so it cannot evaporate while the player runs.
 * That would be wrong here: a Cursed Zombie spawns from the ordinary natural-spawn path anywhere in
 * the Overworld, and a persistent one is never cleaned up by anything -- so over a long-lived world,
 * every one that ever spawned and was not killed would still be sitting in its chunk. At a fraction
 * of a percent of all zombie spawns that accumulates slowly and invisibly, which is exactly how a
 * world gets heavier the longer it is played.
 *
 * <p>Vanilla's rules are also simply correct for this mob: it will not despawn while anyone is near
 * enough to be threatened by it, and running far enough away that it does despawn <em>is</em>
 * escaping it.
 *
 * <h2>The curse hit</h2>
 * {@link #doHurtTarget} applies the curse only when the vanilla attack actually landed damage. A hit
 * that was blocked, missed, cancelled or otherwise did not damage the player returns false from
 * {@code super}, and the curse is not applied (spec section 6). Killing this mob afterwards does not
 * lift the curse -- nothing here holds it, {@link GraveboundCurse} does.
 */
public class CursedZombie extends RaidUndead {
	/** How often the ambient soul trail is emitted, in ticks. */
	private static final int PARTICLE_INTERVAL = 10;
	/** How often it may make its cursed call, in ticks. */
	private static final int VOICE_INTERVAL = 140;

	public CursedZombie(EntityType<? extends CursedZombie> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Zombie.createAttributes()
				.add(Attributes.MAX_HEALTH, 30.0)
				.add(Attributes.ATTACK_DAMAGE, 4.5)
				.add(Attributes.MOVEMENT_SPEED, 0.26)
				.add(Attributes.FOLLOW_RANGE, 48.0)
				.add(Attributes.ARMOR, 2.0);
	}

	@Override
	protected void addBehaviourGoals() {
		super.addBehaviourGoals();
		// Persistent pursuit: keeps chasing after losing line of sight, which is what makes it feel
		// like it is hunting the player rather than merely noticing them.
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false, null));
	}

	@Override
	public void aiStep() {
		super.aiStep();
		if (!(level() instanceof ServerLevel server)) {
			return;
		}
		if (!hasGlowingTag()) {
			setGlowingTag(true);
		}
		if (tickCount % PARTICLE_INTERVAL == 0) {
			server.sendParticles(ParticleTypes.SOUL, getX(), getY() + getBbHeight() * 0.6, getZ(),
					2, 0.25, 0.35, 0.25, 0.005);
			server.sendParticles(ParticleTypes.SMOKE, getX(), getY() + getBbHeight() * 0.3, getZ(),
					1, 0.2, 0.2, 0.2, 0.0);
		}
		if (tickCount % VOICE_INTERVAL == 0 && getRandom().nextFloat() < 0.4f) {
			server.playSound(null, getX(), getY(), getZ(), SoundEvents.SOUL_ESCAPE,
					SoundSource.HOSTILE, 0.7f, 0.5f);
		}
	}

	/**
	 * Vanilla's {@code doHurtTarget} returns true only when the attack actually dealt damage, so
	 * gating on it is exactly the "successful damaging hit" rule the design asks for -- a blocked or
	 * cancelled swing never reaches the curse.
	 */
	@Override
	public boolean doHurtTarget(Entity target) {
		boolean hit = super.doHurtTarget(target);
		if (hit && target instanceof ServerPlayer player) {
			// apply() is a no-op if this player is already cursed, so repeated hits (and hits from a
			// second Cursed Zombie) can never reset, extend or duplicate the timer.
			GraveboundCurse.apply(player, CurseSource.CURSED_ZOMBIE);
		}
		return hit;
	}

	/**
	 * The glow outline colour. Vanilla's {@link Entity#getTeamColor()} returns white (0xFFFFFF) for any
	 * entity with no scoreboard team, which is what the permanent glow tag would otherwise render as.
	 * A Cursed Zombie is described everywhere as trailing a <em>purple</em> soul-light, so its outline
	 * is forced to match -- this is a common (server + client) method, so every viewer sees it.
	 */
	@Override
	public int getTeamColor() {
		return 0xAA33FF;
	}

	@Override
	protected net.minecraft.sounds.SoundEvent getAmbientSound() {
		return SoundEvents.ZOMBIE_AMBIENT;
	}

	@Override
	public float getVoicePitch() {
		return super.getVoicePitch() * 0.6f;
	}
}
