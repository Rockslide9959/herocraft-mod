package com.projecthero.mod.client.mixin;

import java.util.Map;
import java.util.UUID;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.client.gui.components.LerpingBossEvent;

/** v0.17.1: the boss bars on screen, so the Flash Suit visor can keep its heading strip under them. */
@Mixin(BossHealthOverlay.class)
public interface BossHealthOverlayAccessor {
	@Accessor("events")
	Map<UUID, LerpingBossEvent> projecthero$events();
}
