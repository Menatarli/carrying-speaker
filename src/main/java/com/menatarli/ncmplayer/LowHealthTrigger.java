package com.menatarli.ncmplayer;

/**
 * 低血量进入 / 离开的滞回判断。
 *
 * <p>纯逻辑，不引用任何 Minecraft 类，方便单测。血量比例落在 enterRatio 与 exitRatio 之间时保持当前状态，
 * 不会来回抖动。
 */
public final class LowHealthTrigger {
	/** update 的返回事件。 */
	public enum Event {
		/** 血量刚刚跌到 enterRatio 以下，进入低血量。 */
		ENTER,
		/** 血量刚刚回到 exitRatio 以上，或者玩家死了，离开低血量。 */
		EXIT,
		/** 状态没变。 */
		NONE
	}

	private final float enterRatio;
	private final float exitRatio;
	private boolean active;

	public LowHealthTrigger(float enterRatio, float exitRatio) {
		this.enterRatio = enterRatio;
		this.exitRatio = exitRatio;
	}

	/**
	 * 喂一次血量快照。
	 *
	 * @param health 当前血量
	 * @param maxHealth 最大血量
	 * @param alive 是否还活着
	 * @return 状态变化事件；比例在 enter 与 exit 之间时返回 {@link Event#NONE}
	 */
	public Event update(float health, float maxHealth, boolean alive) {
		if (!active) {
			// 未激活：只有活着、maxHealth 有效、且比例 <= enterRatio 才进入。
			if (alive && maxHealth > 0.0F && health / maxHealth <= enterRatio) {
				active = true;
				return Event.ENTER;
			}
			return Event.NONE;
		}

		// 已激活：死了，或者比例回到 exitRatio 以上就离开。
		// maxHealth 为 0 时 Java 的浮点除法会给 Infinity / NaN，比较结果分别是 true / false，与规则一致，不会抛异常。
		if (!alive || health / maxHealth >= exitRatio) {
			active = false;
			return Event.EXIT;
		}
		return Event.NONE;
	}

	/** 回到未激活状态，不返回事件。 */
	public void reset() {
		active = false;
	}

	/** 当前是否处于低血量激活状态。 */
	public boolean isActive() {
		return active;
	}

	public float getEnterRatio() {
		return enterRatio;
	}

	public float getExitRatio() {
		return exitRatio;
	}
}
