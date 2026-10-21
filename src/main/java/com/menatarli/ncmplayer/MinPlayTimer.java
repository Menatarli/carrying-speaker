package com.menatarli.ncmplayer;

/**
 * 低血量触发的本地音乐「最短播放时长」计时器。纯逻辑，不引用任何 Minecraft 类，方便单测。
 *
 * <p>时间由调用方以纳秒传入，内部不读时钟，所以单测可以完全控制。
 * 只有低血量 ENTER 真正开始播放后才调用 {@link #onStart(long)}；手动播放不参与计时。
 */
public final class MinPlayTimer {
	private final long minPlayNanos;
	/** 低血量触发的这次播放是否还在计时中。 */
	private boolean active;
	/** 已经回血（或死亡），但还没到最短时长。 */
	private boolean pendingStop;
	private long startNanos;

	public MinPlayTimer(int minPlaySeconds) {
		this.minPlayNanos = Math.max(0, minPlaySeconds) * 1_000_000_000L;
	}

	/** 低血量播放真正开始：开始计时。 */
	public void onStart(long nowNanos) {
		active = true;
		pendingStop = false;
		startNanos = nowNanos;
	}

	/**
	 * 回血或死亡时调用。
	 *
	 * @return true = 已经播满最短时长，调用方应立即停止；false = 未满，调用方挂起待停止
	 */
	public boolean onStopRequested(long nowNanos) {
		if (!active) {
			// 不是低血量触发的播放（手动播放），不受最短时长约束。
			return true;
		}
		if (elapsed(nowNanos) >= minPlayNanos) {
			// 已经播够了：直接结束计时，不进入待停状态。
			active = false;
			pendingStop = false;
			return true;
		}
		pendingStop = true;
		return false;
	}

	/**
	 * 每 tick 调用：待停止且已经播满最短时长时返回 true，调用方应立即停止。
	 */
	public boolean tick(long nowNanos) {
		if (!active || !pendingStop || elapsed(nowNanos) < minPlayNanos) {
			return false;
		}
		active = false;
		pendingStop = false;
		return true;
	}

	/**
	 * 待停止期间又进入低血量：取消待停止，当前这首继续播，开始时间不重置。
	 *
	 * @return true = 确实取消了待停止，调用方不要重新 start
	 */
	public boolean onEnterWhilePending() {
		if (!active || !pendingStop) {
			return false;
		}
		pendingStop = false;
		return true;
	}

	/** 是否处于「待停止」状态。 */
	public boolean isPending() {
		return pendingStop;
	}

	/** 低血量触发的计时是否还在进行。 */
	public boolean isActive() {
		return active;
	}

	/** 立即停止并清掉所有状态。 */
	public void reset() {
		active = false;
		pendingStop = false;
		startNanos = 0L;
	}

	private long elapsed(long nowNanos) {
		return nowNanos - startNanos;
	}
}
