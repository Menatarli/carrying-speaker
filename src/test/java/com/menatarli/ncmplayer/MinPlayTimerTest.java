package com.menatarli.ncmplayer;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** {@link MinPlayTimer} 的纯逻辑测试，不依赖 Minecraft。 */
class MinPlayTimerTest {
	private static final long SEC = 1_000_000_000L;

	@Test
	void zeroSecondsStopsImmediately() {
		MinPlayTimer timer = new MinPlayTimer(0);
		timer.onStart(100L);
		assertTrue(timer.onStopRequested(100L), "N=0 时应当立即停止");
		assertFalse(timer.isPending(), "N=0 不应进入待停止");
	}

	@Test
	void stopsWhenMinPlayReached() {
		MinPlayTimer timer = new MinPlayTimer(20);
		timer.onStart(0L);
		assertTrue(timer.onStopRequested(20 * SEC), "播满 20 秒应当立即停止");
		assertFalse(timer.isPending());
	}

	@Test
	void keepsPlayingWhenBelowMinPlay() {
		MinPlayTimer timer = new MinPlayTimer(20);
		timer.onStart(0L);
		assertFalse(timer.onStopRequested(5 * SEC), "未满 20 秒应当挂起待停");
		assertTrue(timer.isPending());
		assertFalse(timer.tick(19 * SEC), "还没到 20 秒，不该停");
		assertTrue(timer.tick(20 * SEC), "到 20 秒应当停");
		assertFalse(timer.isPending());
		assertFalse(timer.isActive());
	}

	@Test
	void enterWhilePendingCancelsStop() {
		MinPlayTimer timer = new MinPlayTimer(20);
		timer.onStart(0L);
		assertFalse(timer.onStopRequested(3 * SEC), "未满，先挂起");
		assertTrue(timer.onEnterWhilePending(), "待停期间再次进入低血量，应当取消待停");
		assertFalse(timer.isPending());
		assertFalse(timer.tick(25 * SEC), "取消待停后不该自己停");
		assertTrue(timer.isActive(), "取消待停后继续播，计时仍然有效");
	}

	@Test
	void manualPlaybackIsNotAffected() {
		MinPlayTimer timer = new MinPlayTimer(20);
		// 手动播放不会调用 onStart，所以回血时直接停，不挂起。
		assertTrue(timer.onStopRequested(0L), "没有低血量计时，应当立即停止");
		assertFalse(timer.isPending());
		assertFalse(timer.tick(30 * SEC), "没有计时，tick 不该要求停止");
	}
}
