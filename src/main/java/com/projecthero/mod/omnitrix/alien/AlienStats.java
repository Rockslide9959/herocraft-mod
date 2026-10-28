package com.projecthero.mod.omnitrix.alien;

/**
 * v0.17.1: an alien's body, applied as transient attribute modifiers while the wearer is transformed (the framework adds and
 * removes them: {@code OmnitrixBody}). Every field is the alien's own value or a change from a plain player:
 *
 * <ul>
 *   <li>{@code maxHealth}: the alien's max health (a player is 20). The current health converts by fraction both ways.</li>
 *   <li>{@code speed}: walk speed multiplier (1 = a player's).</li>
 *   <li>{@code scale}: body size (1 = a player; Grey Matter ~0.3, Four Arms 1.4). Vanilla's {@code scale} attribute: the
 *       hitbox, the eye height, the reach and the rendered model all follow.</li>
 *   <li>{@code armor}, {@code toughness}: armour points added (the worn armour still counts, but is hidden).</li>
 *   <li>{@code knockbackResist}: 0..1 added.</li>
 *   <li>{@code attack}: melee damage added to the fist (a player's is 1).</li>
 *   <li>{@code step}, {@code jump}, {@code safeFall}: step height (blocks), jump strength and safe fall distance added.</li>
 *   <li>{@code gravity}: gravity multiplier (1 = normal; Ghostfreak floats at ~0.3).</li>
 *   <li>{@code reach}: block and entity reach added on top of what the scale already gives.</li>
 * </ul>
 */
public record AlienStats(float maxHealth, float speed, float scale, float armor, float toughness, float knockbackResist, float attack,
		float step, float jump, float safeFall, float gravity, float reach) {

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private float maxHealth = 20f;
		private float speed = 1f;
		private float scale = 1f;
		private float armor;
		private float toughness;
		private float knockbackResist;
		private float attack;
		private float step;
		private float jump;
		private float safeFall;
		private float gravity = 1f;
		private float reach;

		public Builder health(float v) {
			maxHealth = v;
			return this;
		}

		public Builder speed(float v) {
			speed = v;
			return this;
		}

		public Builder scale(float v) {
			scale = v;
			return this;
		}

		public Builder armor(float v, float tough) {
			armor = v;
			toughness = tough;
			return this;
		}

		public Builder knockbackResist(float v) {
			knockbackResist = v;
			return this;
		}

		public Builder attack(float v) {
			attack = v;
			return this;
		}

		public Builder step(float v) {
			step = v;
			return this;
		}

		public Builder jump(float v) {
			jump = v;
			return this;
		}

		public Builder safeFall(float v) {
			safeFall = v;
			return this;
		}

		public Builder gravity(float v) {
			gravity = v;
			return this;
		}

		public Builder reach(float v) {
			reach = v;
			return this;
		}

		public AlienStats build() {
			return new AlienStats(maxHealth, speed, scale, armor, toughness, knockbackResist, attack, step, jump, safeFall, gravity, reach);
		}
	}
}
