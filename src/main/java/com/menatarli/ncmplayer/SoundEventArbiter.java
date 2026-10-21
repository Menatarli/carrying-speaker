package com.menatarli.ncmplayer;

/**
 * 事件仲裁器：决定「现在该放哪个事件的声音」以及「该不该停」。纯逻辑，不引用 MC 类。
 *
 * <p>DEATH 优先：死亡会无条件抢占低血量（不管低血量的最短时长还没到）。
 * 每个事件有自己的 {@link MinPlayTimer}，按各自的 minPlaySeconds 结算最短播放时长。
 *
 * <p>行为变化（已知并接受）：手动 {@code /ncm local play} 开始的播放不再因为回血而停——
 * 因为现在只有事件仲裁器会要求 stop。
 */
public final class SoundEventArbiter {
	/** 一次 tick 的决策：先 stop，再 start（两者可以同时出现）。 */
	public record Decision(boolean stop, SoundEventConfig start) {
	}

	private final SoundEventTable table;
	private final LowHealthTrigger lowHealth;
	private final DeathTrigger death = new DeathTrigger();

	/** 当前正在播的事件类型；null 表示没有事件在播。 */
	private SoundEventConfig.EventType activeType;
	private MinPlayTimer activeTimer;

	public SoundEventArbiter(SoundEventTable table) {
		this.table = table == null ? SoundEventTable.defaults() : table;
		SoundEventConfig low = configOf(SoundEventConfig.EventType.LOW_HEALTH);
		this.lowHealth = new LowHealthTrigger((float) low.minHealth(), (float) low.exitHealth());
	}

	/**
	 * 喂一次世界状态。调用方负责在主线程执行，并按决策先 stop 再 start。
	 */
	public Decision update(boolean alive, float health, float maxHealth, long nowNanos) {
		boolean stop = false;
		SoundEventConfig start = null;

		// a. 最短播放时长到点。
		if (activeTimer != null && activeType != null && activeTimer.tick(nowNanos)) {
			stop = true;
			clearActive();
		}

		// b. 死亡优先。
		DeathTrigger.Event deathEvent = death.update(alive);
		if (deathEvent == DeathTrigger.Event.DIED) {
			SoundEventConfig deathConfig = configOf(SoundEventConfig.EventType.DEATH);
			if (deathConfig.enabled() && activeType != SoundEventConfig.EventType.DEATH) {
				if (activeType != null) {
					// 抢占：不管低血量的最短时长。
					stop = true;
					clearActive();
				}
				start = deathConfig;
				beginActive(SoundEventConfig.EventType.DEATH, deathConfig, nowNanos);
			}
		} else if (deathEvent == DeathTrigger.Event.RESPAWNED
				&& activeType == SoundEventConfig.EventType.DEATH) {
			if (activeTimer != null && activeTimer.onStopRequested(nowNanos)) {
				stop = true;
				clearActive();
			}
		}

		// c. 低血量。
		LowHealthTrigger.Event healthEvent = lowHealth.update(health, maxHealth, alive);
		if (healthEvent == LowHealthTrigger.Event.ENTER) {
			if (activeType == SoundEventConfig.EventType.DEATH) {
				// 死亡优先，忽略。
			} else if (activeType == SoundEventConfig.EventType.LOW_HEALTH) {
				// 待停期间又进入低血量：继续播当前这首，不重新开始计时。
				if (activeTimer != null) {
					activeTimer.onEnterWhilePending();
				}
			} else if (activeType == null) {
				SoundEventConfig lowConfig = configOf(SoundEventConfig.EventType.LOW_HEALTH);
				if (lowConfig.enabled()) {
					start = lowConfig;
					beginActive(SoundEventConfig.EventType.LOW_HEALTH, lowConfig, nowNanos);
				}
			}
		} else if (healthEvent == LowHealthTrigger.Event.EXIT) {
			if (activeType == SoundEventConfig.EventType.LOW_HEALTH && activeTimer != null) {
				if (activeTimer.onStopRequested(nowNanos)) {
					stop = true;
					clearActive();
				}
			}
		}

		return new Decision(stop, start);
	}

	/** 播放启动失败：如果失败的就是当前事件，把状态清掉。 */
	public void onStartFailed(SoundEventConfig.EventType type) {
		if (activeType == type) {
			clearActive();
		}
	}

	/**
	 * 清空所有状态。
	 *
	 * @return true = 之前确实有事件在播
	 */
	public boolean reset() {
		boolean wasActive = activeType != null;
		clearActive();
		death.reset();
		lowHealth.reset();
		return wasActive;
	}

	/** 当前正在播的事件类型，测试和日志用。 */
	public SoundEventConfig.EventType activeType() {
		return activeType;
	}

	private SoundEventConfig configOf(SoundEventConfig.EventType type) {
		return table.get(type).orElse(SoundEventConfig.defaults(type));
	}

	private void beginActive(SoundEventConfig.EventType type, SoundEventConfig config, long nowNanos) {
		activeType = type;
		activeTimer = new MinPlayTimer(config.minPlaySeconds());
		activeTimer.onStart(nowNanos);
	}

	private void clearActive() {
		activeType = null;
		activeTimer = null;
	}
}
