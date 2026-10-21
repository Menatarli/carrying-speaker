package com.menatarli.ncmplayer;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** {@link NcmStealDetector} 的纯逻辑测试。 */
class NcmStealDetectorTest {
	private static final NcmStealDetector.Fg MC = NcmStealDetector.Fg.MC;
	private static final NcmStealDetector.Fg CM = NcmStealDetector.Fg.CLOUDMUSIC;
	private static final NcmStealDetector.Fg OTHER = NcmStealDetector.Fg.OTHER;

	@Test
	void mcToCloudMusicIsSteal() {
		NcmStealDetector detector = new NcmStealDetector(MC);
		assertEquals(NcmStealDetector.Action.ACT, detector.feed(CM));
	}

	@Test
	void otherThenCloudMusicIsUserSwitch() {
		NcmStealDetector detector = new NcmStealDetector(OTHER);
		assertEquals(NcmStealDetector.Action.STOP, detector.feed(CM));
	}

	@Test
	void actAgainAfterReturningToMc() {
		NcmStealDetector detector = new NcmStealDetector(MC);
		assertEquals(NcmStealDetector.Action.ACT, detector.feed(CM));
		assertEquals(NcmStealDetector.Action.NONE, detector.feed(MC));
		assertEquals(NcmStealDetector.Action.ACT, detector.feed(CM));
	}

	@Test
	void initialOtherDirectToCloudMusicIsStop() {
		NcmStealDetector detector = new NcmStealDetector(OTHER);
		assertEquals(NcmStealDetector.Action.STOP, detector.feed(CM));
	}

	@Test
	void stayingOnMcIsNone() {
		NcmStealDetector detector = new NcmStealDetector(MC);
		assertEquals(NcmStealDetector.Action.NONE, detector.feed(MC));
		assertEquals(NcmStealDetector.Action.NONE, detector.feed(MC));
	}

	@Test
	void cloudMusicToOtherIsNone() {
		NcmStealDetector detector = new NcmStealDetector(MC);
		assertEquals(NcmStealDetector.Action.ACT, detector.feed(CM));
		assertEquals(NcmStealDetector.Action.NONE, detector.feed(OTHER));
	}
}
