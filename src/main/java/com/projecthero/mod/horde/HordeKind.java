package com.projecthero.mod.horde;

import org.joml.Vector3f;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.BossEvent;

/** v0.14.12: the three Hordes -- what each one looks, sounds and pays like. Harder hordes pay better. */
public enum HordeKind {
	ZOMBIE("zombie_horde", ChatFormatting.DARK_GREEN, 0x2E4A1E, 0x4F8A2B, BossEvent.BossBarColor.GREEN, 0.7f),
	SKELETON("skeleton_horde", ChatFormatting.GRAY, 0x4A4A5A, 0xE6E2D3, BossEvent.BossBarColor.WHITE, 1.0f),
	SPIDER("spider_horde", ChatFormatting.DARK_RED, 0x3A0F1A, 0xC0182A, BossEvent.BossBarColor.RED, 1.4f);

	private final String typeId;
	private final ChatFormatting color;
	private final int skyColor;
	private final int borderColor;
	private final BossEvent.BossBarColor barColor;
	private final float bossPitch;

	HordeKind(String typeId, ChatFormatting color, int skyColor, int borderColor, BossEvent.BossBarColor barColor, float bossPitch) {
		this.typeId = typeId;
		this.color = color;
		this.skyColor = skyColor;
		this.borderColor = borderColor;
		this.barColor = barColor;
		this.bossPitch = bossPitch;
	}

	public String typeId() {
		return typeId;
	}

	public ChatFormatting color() {
		return color;
	}

	public int skyColor() {
		return skyColor;
	}

	public Vector3f borderColor() {
		return HordeRaid.rgb(borderColor);
	}

	public BossEvent.BossBarColor barColor() {
		return barColor;
	}

	public float bossPitch() {
		return bossPitch;
	}

	public HordeBlock block() {
		return switch (this) {
			case ZOMBIE -> HordeBlocks.ZOMBIE_HORDE;
			case SKELETON -> HordeBlocks.SKELETON_HORDE;
			case SPIDER -> HordeBlocks.SPIDER_HORDE;
		};
	}

	public SoundEvent startSound() {
		return switch (this) {
			case ZOMBIE -> SoundEvents.ZOMBIE_VILLAGER_CURE;
			case SKELETON -> SoundEvents.SKELETON_HORSE_AMBIENT;
			case SPIDER -> SoundEvents.SPIDER_AMBIENT;
		};
	}

	public SoundEvent spawnSound() {
		return switch (this) {
			case ZOMBIE -> SoundEvents.ZOMBIE_AMBIENT;
			case SKELETON -> SoundEvents.SKELETON_AMBIENT;
			case SPIDER -> SoundEvents.SPIDER_STEP;
		};
	}

	public ParticleOptions burstParticle() {
		return switch (this) {
			case ZOMBIE -> ParticleTypes.ITEM_SLIME;
			case SKELETON -> ParticleTypes.WHITE_ASH;
			case SPIDER -> ParticleTypes.SQUID_INK;
		};
	}

	public static HordeKind byTypeId(String id) {
		for (HordeKind k : values()) {
			if (k.typeId.equals(id)) {
				return k;
			}
		}
		return null;
	}
}
