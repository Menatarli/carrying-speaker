package com.menatarli.ncmplayer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** LowHealthTrigger 的纯逻辑测试：enter 0.3 / exit 0.5，maxHealth 20。 */
class LowHealthTriggerTest {
	private static final float ENTER = 0.3F;
	private static final float EXIT = 0.5F;
	private static final float MAX = 20.0F;

	private LowHealthTrigger newTrigger() {
		return new LowHealthTrigger(ENTER, EXIT);
	}

	@Test
	void fullHealthReturnsNone() {
		assertEquals(LowHealthTrigger.Event.NONE, newTrigger().update(20.0F, MAX, true));
	}

	@Test
	void droppingToThirtyPercentEnters() {
		LowHealthTrigger trigger = newTrigger();
		assertEquals(LowHealthTrigger.Event.ENTER, trigger.update(6.0F, MAX, true));
		assertTrue(trigger.isActive());
	}

	@Test
	void staysBetweenHysteresisBand() {
		LowHealthTrigger trigger = newTrigger();
		assertEquals(LowHealthTrigger.Event.ENTER, trigger.update(6.0F, MAX, true));
		// 30% 与 50% 之间来回：不再触发，也不退出
		assertEquals(LowHealthTrigger.Event.NONE, trigger.update(8.0F, MAX, true));   // 40%
		assertEquals(LowHealthTrigger.Event.NONE, trigger.update(6.5F, MAX, true));   // 32.5%
		assertEquals(LowHealthTrigger.Event.NONE, trigger.update(9.9F, MAX, true));   // 49.5%
		assertEquals(LowHealthTrigger.Event.NONE, trigger.update(6.0F, MAX, true));   // 又回到 30%，不重复 ENTER
	}

	@Test
	void backToFiftyPercentExits() {
		LowHealthTrigger trigger = newTrigger();
		assertEquals(LowHealthTrigger.Event.ENTER, trigger.update(6.0F, MAX, true));
		assertEquals(LowHealthTrigger.Event.EXIT, trigger.update(10.0F, MAX, true));
		assertFalse(trigger.isActive());
		// 退出后再降到 40% 不该再进（要跌到 30% 才进）
		assertEquals(LowHealthTrigger.Event.NONE, trigger.update(8.0F, MAX, true));
	}

	@Test
	void deathWhileActiveExits() {
		LowHealthTrigger trigger = newTrigger();
		assertEquals(LowHealthTrigger.Event.ENTER, trigger.update(6.0F, MAX, true));
		assertEquals(LowHealthTrigger.Event.EXIT, trigger.update(6.0F, MAX, false));
		assertFalse(trigger.isActive());
	}

	@Test
	void zeroMaxHealthNeverTriggers() {
		LowHealthTrigger trigger = newTrigger();
		assertEquals(LowHealthTrigger.Event.NONE, trigger.update(0.0F, 0.0F, true));
		assertEquals(LowHealthTrigger.Event.NONE, trigger.update(20.0F, 0.0F, true));
		assertFalse(trigger.isActive());
	}

	@Test
	void deadAndZeroHealthNeverTriggers() {
		assertEquals(LowHealthTrigger.Event.NONE, newTrigger().update(0.0F, MAX, false));
	}

	@Test
	void resetAllowsEnteringAgain() {
		LowHealthTrigger trigger = newTrigger();
		assertEquals(LowHealthTrigger.Event.ENTER, trigger.update(6.0F, MAX, true));
		trigger.reset();
		assertFalse(trigger.isActive());
		assertEquals(LowHealthTrigger.Event.ENTER, trigger.update(6.0F, MAX, true));
		assertTrue(trigger.isActive());
	}
}
