package com.projecthero.mod.saiyan.kit;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.projecthero.mod.ProjectHeroMod;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;

import net.minecraft.network.codec.ByteBufCodecs;

/**
 * v0.17.3: the Spirit Bomb's per-player state, synced to every client tracking the player (the pose, the held bomb and the
 * HUD cooldown all read it). Only {@code readyAt} matters across saves: the phase is reset on join.
 *
 * @param phase   {@link SaiyanSpiritBomb#NONE} .. {@link SaiyanSpiritBomb#THROW}
 * @param since   game time the phase began
 * @param radius  the held bomb's radius at {@code syncAt}
 * @param rate    radius growth per tick from {@code syncAt} (0 = stalled or full)
 * @param syncAt  game time {@code radius} was taken
 * @param readyAt game time the move is off cooldown
 */
public record SpiritBombState(int phase, long since, float radius, float rate, long syncAt, long readyAt) {
	public static final SpiritBombState NONE = new SpiritBombState(0, 0L, 0f, 0f, 0L, 0L);

	public static final Codec<SpiritBombState> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.fieldOf("phase").forGetter(SpiritBombState::phase),
			Codec.LONG.fieldOf("since").forGetter(SpiritBombState::since),
			Codec.FLOAT.fieldOf("radius").forGetter(SpiritBombState::radius),
			Codec.FLOAT.fieldOf("rate").forGetter(SpiritBombState::rate),
			Codec.LONG.fieldOf("sync_at").forGetter(SpiritBombState::syncAt),
			Codec.LONG.fieldOf("ready_at").forGetter(SpiritBombState::readyAt)).apply(i, SpiritBombState::new));

	public static final AttachmentType<SpiritBombState> TYPE = AttachmentRegistry.create(ProjectHeroMod.id("saiyan_spirit_bomb"),
			b -> b.persistent(CODEC).copyOnDeath().initializer(() -> NONE)
					.syncWith(ByteBufCodecs.fromCodec(CODEC), AttachmentSyncPredicate.all()));

	/** The held bomb's radius at game time {@code t} (the client passes a fractional time to interpolate). */
	public float radiusAt(double t) {
		return (float) Math.min(SaiyanSpiritBomb.RADIUS_MAX, radius + rate * Math.max(0.0, t - syncAt));
	}

	/** Forces the class (and so the attachment registration) to load. */
	static void initialize() {
	}
}
