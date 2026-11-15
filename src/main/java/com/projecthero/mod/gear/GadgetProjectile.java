package com.projecthero.mod.gear;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** v0.17.5: a thrown Smoke Bomb / Flashbang. Goes off where it hits (block or entity); what it does depends on its item. */
public class GadgetProjectile extends ThrowableItemProjectile {
	public GadgetProjectile(EntityType<? extends GadgetProjectile> type, Level level) {
		super(type, level);
	}

	public GadgetProjectile(Level level, LivingEntity owner) {
		super(GearItems.THROWN_GADGET, owner, level);
	}

	@Override
	protected Item getDefaultItem() {
		return GearItems.SMOKE_BOMB;
	}

	@Override
	protected void onHit(HitResult result) {
		super.onHit(result);
		if (level().isClientSide) {
			return;
		}
		Vec3 at = result.getLocation();
		if (getItem().getItem() instanceof ThrownGadgetItem g) {
			LivingEntity owner = getOwner() instanceof LivingEntity l ? l : null;
			if (g.kind() == GadgetCooldowns.Kind.FLASH) {
				Flashbang.detonate(level(), at, owner, g.upgraded());
			} else {
				SmokeCloudEntity.spawn(level(), at, owner, g.upgraded());
			}
		}
		discard();
	}
}
