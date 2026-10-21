package com.menatarli.ncmplayer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** {@link SoundEventArbiter} 的纯逻辑测试。 */
class SoundEventArbiterTest {
	private static final long SEC = 1_000_000_000L;
	private static final float MAX = 20.0F;
	private static final float LOW = 4.0F;      // 0.2 < 0.3
	private static final float HIGH = 14.0F;    // 0.7 > 0.5

	private static final String DEFAULT_JSON = "{\"events\":["
			+ "{\"eventType\":\"LOW_HEALTH\",\"minHealth\":0.3,\"exitHealth\":0.5,\"minPlaySeconds\":10},"
			+ "{\"eventType\":\"DEATH\",\"soundFile\":\"d.mp3\",\"minPlaySeconds\":120}"
			+ "]}";

	private static SoundEventArbiter arbiter() {
		return new SoundEventArbiter(SoundEventTable.parse(DEFAULT_JSON));
	}

	@Test
	void lowHealthStartsThenPendsUntilMinPlay() {
		SoundEventArbiter arbiter = arbiter();
		arbiter.update(true, MAX, MAX, 0L);

		SoundEventArbiter.Decision start = arbiter.update(true, LOW, MAX, 1 * SEC);
		assertNotNull(start.start());
		assertEquals(SoundEventConfig.EventType.LOW_HEALTH, start.start().eventType());
		assertFalse(start.stop());

		SoundEventArbiter.Decision pending = arbiter.update(true, HIGH, MAX, 6 * SEC);
		assertFalse(pending.stop(), "未满 10 秒应当挂起");
		assertEquals(SoundEventConfig.EventType.LOW_HEALTH, arbiter.activeType());

		SoundEventArbiter.Decision done = arbiter.update(true, HIGH, MAX, 11 * SEC);
		assertTrue(done.stop(), "到 10 秒应当停");
		assertNull(arbiter.activeType());
	}

	@Test
	void deathPreemptsLowHealth() {
		SoundEventArbiter arbiter = arbiter();
		arbiter.update(true, MAX, MAX, 0L);
		arbiter.update(true, LOW, MAX, 1 * SEC);

		SoundEventArbiter.Decision decision = arbiter.update(false, LOW, MAX, 2 * SEC);
		assertTrue(decision.stop(), "死亡要抢占，先停");
		assertNotNull(decision.start());
		assertEquals(SoundEventConfig.EventType.DEATH, decision.start().eventType());
		assertEquals(SoundEventConfig.EventType.DEATH, arbiter.activeType());
	}

	@Test
	void respawnPendsUntilDeathMinPlay() {
		SoundEventArbiter arbiter = arbiter();
		arbiter.update(true, MAX, MAX, 0L);
		arbiter.update(false, MAX, MAX, 1 * SEC);

		SoundEventArbiter.Decision pending = arbiter.update(true, MAX, MAX, 2 * SEC);
		assertFalse(pending.stop(), "未满 120 秒应当挂起");
		assertEquals(SoundEventConfig.EventType.DEATH, arbiter.activeType());

		SoundEventArbiter.Decision done = arbiter.update(true, MAX, MAX, 122 * SEC);
		assertTrue(done.stop());
		assertNull(arbiter.activeType());
	}

	@Test
	void lowHealthEnterIgnoredWhileDeathPlaying() {
		SoundEventArbiter arbiter = arbiter();
		arbiter.update(true, MAX, MAX, 0L);
		arbiter.update(false, MAX, MAX, 1 * SEC);

		SoundEventArbiter.Decision decision = arbiter.update(false, LOW, MAX, 2 * SEC);
		assertNull(decision.start(), "死亡播放期间的低血量进入应当被忽略");
		assertEquals(SoundEventConfig.EventType.DEATH, arbiter.activeType());
	}

	@Test
	void disabledDeathDoesNotTrigger() {
		String json = "{\"events\":["
				+ "{\"eventType\":\"LOW_HEALTH\"},"
				+ "{\"eventType\":\"DEATH\",\"enabled\":false}]}";
		SoundEventArbiter arbiter = new SoundEventArbiter(SoundEventTable.parse(json));
		arbiter.update(true, MAX, MAX, 0L);

		SoundEventArbiter.Decision decision = arbiter.update(false, MAX, MAX, 1 * SEC);
		assertNull(decision.start());
		assertNull(arbiter.activeType());
	}

	@Test
	void onStartFailedClearsActive() {
		SoundEventArbiter arbiter = arbiter();
		arbiter.update(true, MAX, MAX, 0L);
		arbiter.update(true, LOW, MAX, 1 * SEC);
		assertEquals(SoundEventConfig.EventType.LOW_HEALTH, arbiter.activeType());

		arbiter.onStartFailed(SoundEventConfig.EventType.LOW_HEALTH);
		assertNull(arbiter.activeType(), "启动失败应当清掉状态");
	}

	@Test
	void resetReportsWhetherSomethingWasPlaying() {
		SoundEventArbiter arbiter = arbiter();
		arbiter.update(true, MAX, MAX, 0L);
		arbiter.update(true, LOW, MAX, 1 * SEC);

		assertTrue(arbiter.reset(), "之前有事件在播，应当返回 true");
		assertFalse(arbiter.reset(), "再 reset 一次应当返回 false");
	}

	@Test
	void firstObservationInDeathStateDoesNotTrigger() {
		SoundEventArbiter arbiter = arbiter();
		SoundEventArbiter.Decision decision = arbiter.update(false, MAX, MAX, 0L);
		assertNull(decision.start(), "首次观察就是死亡状态，不该触发");
		assertNull(arbiter.activeType());
	}
}
