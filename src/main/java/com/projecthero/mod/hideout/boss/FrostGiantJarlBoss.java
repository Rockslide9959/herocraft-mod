package com.projecthero.mod.hideout.boss;

import com.projecthero.mod.frostgiant.entity.FrostGiantWarlordEntity;
import com.projecthero.mod.hideout.HideoutBoss;
import com.projecthero.mod.hideout.HideoutBossLink;
import com.projecthero.mod.hideout.HideoutFaction;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;

/**
 * v0.17.5: the <b>Frost Giant Jarl</b> -- the Frost Giant Hall mini-boss: the Frost Giant Warlord's entity and model at
 * a hall-sized 6.5 blocks (the renderer fits the model to the hit-box), 650 health, 12 armour. Its moves are the
 * Warlord's: <b>Cleave</b> (a wide telegraphed arc), <b>Sword Plant</b> (a ring of ice spikes running outward -- jump
 * it) and <b>Rally</b> (nearby giants hit harder for a while); it drops to one knee at 30%. It ignores Rune Braziers
 * (there are none in the hall). The Warlord's own threat table is the shared one; the hideout link reuses it and
 * draws the distance-limited hideout bar instead of the Warlord's.
 */
public class FrostGiantJarlBoss extends FrostGiantWarlordEntity implements HideoutBoss {
	private final HideoutBossLink link = new HideoutBossLink(this, HideoutFaction.FROST, BossEvent.BossBarColor.BLUE, threat());
	/** v0.17.7: the second phase (see HideoutPhase). */
	final HideoutPhase phase = new HideoutPhase(this, HideoutFaction.FROST, HideoutPhase.Kind.ICE_SPIKES, 0.5f, 140);

	public FrostGiantJarlBoss(EntityType<? extends FrostGiantJarlBoss> type, Level level) {
		super(type, level);
		this.xpReward = 120;
		getAttribute(Attributes.MAX_HEALTH).setBaseValue(650.0);
		setHealth(650.0f);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return FrostGiantWarlordEntity.createAttributes()
				.add(Attributes.MAX_HEALTH, 650.0)
				.add(Attributes.ARMOR, 12.0)
				.add(Attributes.FOLLOW_RANGE, 40.0)
				.add(Attributes.STEP_HEIGHT, 1.5);
	}

	@Override
	public HideoutBossLink hideoutLink() {
		return link;
	}

	/** v0.17.7: the second phase (test hook). */
	public HideoutPhase phase() {
		return phase;
	}

	@Override
	protected boolean huntsBraziers() {
		return false;
	}

	@Override
	protected boolean showsOwnBossBar() {
		return false;
	}

	@Override
	public void configure(int fighters) {
		// a hideout boss has fixed health (the Warlord scales with the raid's fighters)
	}

	@Override
	public void aiStep() {
		super.aiStep();
		if (level() instanceof ServerLevel server && isAlive()) {
			link.tick(server);
			phase.tick(server, busy());
		}
	}

	@Override
	public boolean hurt(DamageSource source, float amount) {
		boolean hurt = super.hurt(source, amount);
		if (hurt && level() instanceof ServerLevel server) {
			phase.hurtCheck(server);
		}
		return hurt;
	}

	@Override
	public void die(DamageSource source) {
		super.die(source);
		if (level() instanceof ServerLevel server) {
			link.onDeath(server);
		}
	}

	@Override
	public void stopSeenByPlayer(net.minecraft.server.level.ServerPlayer player) {
		super.stopSeenByPlayer(player);
		link.stopSeen(player);
	}

	@Override
	public void remove(Entity.RemovalReason reason) {
		link.detach();
		super.remove(reason);
	}

	@Override
	public void addAdditionalSaveData(CompoundTag tag) {
		super.addAdditionalSaveData(tag);
		link.save(tag);
		phase.save(tag);
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		link.load(tag);
		phase.load(tag);
	}
}
