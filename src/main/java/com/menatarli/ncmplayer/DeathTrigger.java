package com.menatarli.ncmplayer;

/**
 * 死亡状态检测。纯逻辑，不引用任何 Minecraft 类。
 *
 * <p>第一次调用只记录状态、不产生事件——这样进入世界时如果正好处于死亡状态，也不会误触发。
 */
public final class DeathTrigger {
	/** 状态迁移事件。 */
	public enum Event {
		/** 刚刚死亡。 */
		DIED,
		/** 刚刚复活。 */
		RESPAWNED,
		/** 状态没变，或这是第一次观察。 */
		NONE
	}

	/** null 表示还没观察过。 */
	private Boolean lastAlive;

	/**
	 * 喂一次存活状态。
	 *
	 * @param alive 当前是否活着
	 */
	public Event update(boolean alive) {
		Boolean previous = lastAlive;
		lastAlive = alive;
		if (previous == null) {
			// 第一次观察：只记录，不产生事件。
			return Event.NONE;
		}
		if (previous && !alive) {
			return Event.DIED;
		}
		if (!previous && alive) {
			return Event.RESPAWNED;
		}
		return Event.NONE;
	}

	/** 回到「未观察」状态。 */
	public void reset() {
		lastAlive = null;
	}
}
