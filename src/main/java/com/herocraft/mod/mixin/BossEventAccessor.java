package com.herocraft.mod.mixin;

import java.util.UUID;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.world.BossEvent;

/**
 * Lets the mod overwrite a {@link BossEvent}'s otherwise-{@code private final} id.
 *
 * <p>Why this is needed: {@code ServerBossEvent}'s only constructor generates a fresh random id, so a
 * boss bar tied to an entity gets a <em>new</em> id every time that entity's Java object is
 * reconstructed -- on chunk reload, on world reload, on server restart. A client that still shows the
 * pre-reload bar (because it never received a REMOVE for it -- it was out of range when the entity
 * unloaded) then ends up with a second, undismissable copy. Pinning the bar's id to the entity's own
 * (persistent) UUID means a reloaded boss reuses the same bar, so the client updates it in place
 * instead of stacking a ghost.
 */
@Mixin(BossEvent.class)
public interface BossEventAccessor {
	@Mutable
	@Accessor("id")
	void herocraft$setId(UUID id);
}
