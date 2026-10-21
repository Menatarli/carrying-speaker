package com.menatarli.ncmplayer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

/** {@link NcmPendingRequests} 的纯逻辑测试。 */
class NcmPendingRequestsTest {

	@Test
	void addAndTake() {
		NcmPendingRequests requests = new NcmPendingRequests();
		String token = requests.add(NcmPlayer.TYPE_SONG, "123", "Menatarli", 0L);
		Optional<NcmPendingRequests.Request> taken = requests.take(token, 1_000L);
		assertTrue(taken.isPresent());
		assertEquals(NcmPlayer.TYPE_SONG, taken.get().type());
		assertEquals("123", taken.get().id());
		assertEquals("Menatarli", taken.get().sender());
	}

	@Test
	void secondTakeReturnsEmpty() {
		NcmPendingRequests requests = new NcmPendingRequests();
		String token = requests.add(NcmPlayer.TYPE_SONG, "123", "Menatarli", 0L);
		assertTrue(requests.take(token, 1_000L).isPresent());
		assertFalse(requests.take(token, 2_000L).isPresent(), "同一个 token 只能取一次");
	}

	@Test
	void expiredRequestIsGone() {
		NcmPendingRequests requests = new NcmPendingRequests();
		String token = requests.add(NcmPlayer.TYPE_SONG, "123", "Menatarli", 0L);
		assertFalse(requests.take(token, NcmPendingRequests.TTL_MILLIS + 1).isPresent(), "超过 60 秒应当取不到");
	}

	@Test
	void unknownTokenIsEmpty() {
		NcmPendingRequests requests = new NcmPendingRequests();
		assertFalse(requests.take("deadbeef", 0L).isPresent());
		assertFalse(requests.take(null, 0L).isPresent());
	}

	@Test
	void evictsOldestWhenFull() {
		NcmPendingRequests requests = new NcmPendingRequests();
		String first = requests.add(NcmPlayer.TYPE_SONG, "1", "A", 0L);
		for (int i = 0; i < NcmPendingRequests.MAX_SIZE; i++) {
			requests.add(NcmPlayer.TYPE_SONG, String.valueOf(i + 2), "A", 0L);
		}
		assertEquals(NcmPendingRequests.MAX_SIZE, requests.size());
		assertFalse(requests.take(first, 1L).isPresent(), "最旧的一条应当被淘汰");
	}

	@Test
	void tokenHasExpectedFormat() {
		NcmPendingRequests requests = new NcmPendingRequests();
		for (int i = 0; i < 50; i++) {
			String token = requests.add(NcmPlayer.TYPE_SONG, "1", "A", 0L);
			assertEquals(NcmPendingRequests.TOKEN_LENGTH, token.length());
			assertTrue(token.matches("[a-z0-9]{8}"), "token 必须是 8 位 [a-z0-9]：" + token);
		}
	}
}
