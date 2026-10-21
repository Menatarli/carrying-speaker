package com.menatarli.ncmplayer;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** {@link DeathTrigger} 的纯逻辑测试。 */
class DeathTriggerTest {

	@Test
	void firstObservationProducesNothing() {
		DeathTrigger trigger = new DeathTrigger();
		assertEquals(DeathTrigger.Event.NONE, trigger.update(false), "第一次观察死亡状态不该触发");
	}

	@Test
	void aliveToDeadIsDied() {
		DeathTrigger trigger = new DeathTrigger();
		trigger.update(true);
		assertEquals(DeathTrigger.Event.DIED, trigger.update(false));
	}

	@Test
	void deadToAliveIsRespawned() {
		DeathTrigger trigger = new DeathTrigger();
		trigger.update(false);
		assertEquals(DeathTrigger.Event.RESPAWNED, trigger.update(true));
	}

	@Test
	void stayingAliveIsNone() {
		DeathTrigger trigger = new DeathTrigger();
		trigger.update(true);
		assertEquals(DeathTrigger.Event.NONE, trigger.update(true));
		assertEquals(DeathTrigger.Event.NONE, trigger.update(true));
	}

	@Test
	void resetReturnsToFirstObservation() {
		DeathTrigger trigger = new DeathTrigger();
		trigger.update(true);
		trigger.reset();
		assertEquals(DeathTrigger.Event.NONE, trigger.update(false), "reset 之后应重新算首次观察");
	}
}
