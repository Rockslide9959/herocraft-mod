package com.projecthero.mod.ultron.contagion;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import com.projecthero.mod.ultron.contagion.entity.UltronOmegaEntity;
import com.projecthero.mod.ultron.contagion.item.ContagionItems;
import com.projecthero.mod.ultron.item.UltronItems;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.1: the <b>Mind Stone Beam</b> -- a thick golden beam, two ways:
 * <ul>
 *   <li><b>The Vision moment</b>: while Ultron Omega kneels at 0 health, any fighter holding a Mind Stone (either hand)
 *       within {@link #VISION_RANGE} holds right-click for 3 s ({@link ContagionConfig.Omega#visionBeamTicks}); the beam
 *       fires into him and he disintegrates.</li>
 *   <li><b>The Empowered Mind Stone</b> (first Omega win): Sneak + hold right-click fires a 2 s beam
 *       ({@link ContagionConfig.MindBeam}): 6 damage every 5 ticks to what it hits, 60 s cooldown.</li>
 * </ul>
 * The client reports "holding right-click with a Mind Stone" ({@link ContagionNet.MindHold}); everything else is decided
 * here. Whenever the beam applies, the stone's own right-click charm is suppressed. Per-player state is static and
 * cleared in {@code ServerStateReset}.
 */
public final class MindStoneBeam {
	public static final double VISION_RANGE = 32.0;
	public static final int GOLD = 0xFFD050;

	private static final class Hold {
		boolean holding;
		int visionTicks;
		int beamTicks = -1;
		long cooldownUntil;
	}

	private static final Map<UUID, Hold> HOLDS = new HashMap<>();

	private MindStoneBeam() {
	}

	public static void initialize() {
		ServerTickEvents.END_SERVER_TICK.register(MindStoneBeam::tick);
		UseItemCallback.EVENT.register((player, level, hand) -> {
			ItemStack stack = player.getItemInHand(hand);
			if (level.isClientSide() || UltronItems.MIND_STONE == null || !stack.is(UltronItems.MIND_STONE) || !(player instanceof ServerPlayer sp)) {
				return InteractionResultHolder.pass(stack);
			}
			if (visionTarget(sp) != null || ContagionItems.empowered(stack) && sp.isShiftKeyDown()) {
				return InteractionResultHolder.consume(stack); // the beam, not the charm
			}
			return InteractionResultHolder.pass(stack);
		});
	}

	public static void clearSessionState() {
		HOLDS.clear();
	}

	public static void setHolding(ServerPlayer p, boolean holding) {
		Hold h = HOLDS.computeIfAbsent(p.getUUID(), k -> new Hold());
		h.holding = holding;
		if (!holding) {
			h.visionTicks = 0;
		}
	}

	/** 0..1 through the 3 s Vision hold (for the HUD), or -1. */
	public static float visionHold(ServerPlayer p) {
		Hold h = HOLDS.get(p.getUUID());
		if (h == null || h.visionTicks <= 0) {
			return 0f;
		}
		return Math.min(1f, h.visionTicks / (float) Math.max(1, ContagionConfig.omega().visionBeamTicks));
	}

	/** The kneeling Omega {@code p} can strike, or null. */
	public static UltronOmegaEntity visionTarget(ServerPlayer p) {
		if (!Contagion.holdsMindStoneAny(p)) {
			return null;
		}
		ServerLevel level = p.serverLevel();
		SokoviaFinale f = ContagionState.get(level).finale;
		UltronOmegaEntity o = f == null ? null : f.omega(level);
		if (o == null) {
			for (UltronOmegaEntity e : level.getEntitiesOfClass(UltronOmegaEntity.class, p.getBoundingBox().inflate(VISION_RANGE))) {
				if (e.inVision()) {
					o = e;
				}
			}
		}
		return o != null && o.inVision() && o.distanceToSqr(p) <= VISION_RANGE * VISION_RANGE ? o : null;
	}

	private static void tick(MinecraftServer server) {
		Iterator<Map.Entry<UUID, Hold>> it = HOLDS.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, Hold> e = it.next();
			ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
			if (p == null) {
				it.remove();
				continue;
			}
			Hold h = e.getValue();
			if (h.beamTicks >= 0) {
				tickEmpoweredBeam(p, h);
				continue;
			}
			if (!h.holding) {
				continue;
			}
			UltronOmegaEntity o = visionTarget(p);
			if (o != null) {
				tickVision(p, h, o);
				continue;
			}
			h.visionTicks = 0;
			ItemStack held = p.getMainHandItem().is(UltronItems.MIND_STONE) ? p.getMainHandItem() : p.getOffhandItem();
			if (held.is(UltronItems.MIND_STONE) && ContagionItems.empowered(held) && p.isShiftKeyDown()) {
				long now = p.level().getGameTime();
				if (now < h.cooldownUntil) {
					if (now % 20 == 0) {
						p.displayClientMessage(Component.translatable("message.projecthero.contagion.mind_beam_cooldown",
								(h.cooldownUntil - now + 19) / 20).withStyle(ChatFormatting.GOLD), true);
					}
					continue;
				}
				h.beamTicks = 0;
				p.serverLevel().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.5f, 1.6f);
			}
		}
	}

	private static void tickVision(ServerPlayer p, Hold h, UltronOmegaEntity o) {
		ServerLevel level = p.serverLevel();
		h.visionTicks++;
		Vec3 from = p.getEyePosition().add(p.getLookAngle().scale(0.5)).add(0, -0.3, 0);
		Vec3 to = o.position().add(0, o.getBbHeight() * 0.6, 0);
		int need = ContagionConfig.omega().visionBeamTicks;
		if (h.visionTicks % 2 == 0) {
			float w = 0.06f + 0.25f * Math.min(1f, h.visionTicks / (float) need);
			ContagionNet.beam(level, from, to, GOLD, w, 3);
		}
		if (h.visionTicks % 10 == 0) {
			level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0f,
					0.6f + h.visionTicks / (float) need);
		}
		if (h.visionTicks >= need) {
			h.visionTicks = 0;
			h.holding = false;
			if (o.visionStruck(level, p)) {
				ContagionNet.beam(level, from, to, GOLD, 0.9f, 40);
				level.sendParticles(ParticleTypes.END_ROD, to.x, to.y, to.z, 60, 0.6, 1.2, 0.6, 0.15);
				level.playSound(null, o.getX(), o.getY(), o.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 3.0f, 1.8f);
				for (ServerPlayer q : level.players()) {
					if (q.distanceToSqr(o) < 96 * 96) {
						q.sendSystemMessage(Component.translatable("message.projecthero.contagion.vision_line", p.getDisplayName())
								.withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC));
					}
				}
			}
		}
	}

	private static void tickEmpoweredBeam(ServerPlayer p, Hold h) {
		ContagionConfig.MindBeam cfg = ContagionConfig.mindBeam();
		ServerLevel level = p.serverLevel();
		boolean still = h.holding && Contagion.holdsMindStoneAny(p) && p.isShiftKeyDown();
		if (!still || h.beamTicks >= cfg.beamTicks) {
			h.beamTicks = -1;
			h.cooldownUntil = level.getGameTime() + cfg.cooldownTicks;
			return;
		}
		h.beamTicks++;
		Vec3 eye = p.getEyePosition();
		Vec3 end = eye.add(p.getLookAngle().scale(cfg.range));
		HitResult block = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		if (block.getType() != HitResult.Type.MISS) {
			end = block.getLocation();
		}
		EntityHitResult hit = ProjectileUtil.getEntityHitResult(p, eye, end, new AABB(eye, end).inflate(1.0),
				e -> e instanceof LivingEntity le && le.isAlive() && !e.isSpectator() && !(e instanceof Player), eye.distanceToSqr(end));
		Vec3 stop = hit != null ? hit.getLocation() : end;
		Vec3 from = eye.add(p.getLookAngle().scale(0.5)).add(0, -0.3, 0);
		if (h.beamTicks % 2 == 0) {
			ContagionNet.beam(level, from, stop, GOLD, 0.35f, 3);
		}
		if (hit != null && h.beamTicks % Math.max(1, cfg.damageInterval) == 0 && hit.getEntity() instanceof LivingEntity target) {
			target.invulnerableTime = 0;
			target.hurt(level.damageSources().indirectMagic(p, p), cfg.damage);
			level.sendParticles(ParticleTypes.END_ROD, stop.x, stop.y, stop.z, 6, 0.2, 0.2, 0.2, 0.05);
		}
	}

	/** Test hook: is a Vision hold / beam in progress for {@code p}? */
	public static int visionTicks(ServerPlayer p) {
		Hold h = HOLDS.get(p.getUUID());
		return h == null ? 0 : h.visionTicks;
	}
}
