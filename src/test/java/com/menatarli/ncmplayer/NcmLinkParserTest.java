package com.menatarli.ncmplayer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

/** {@link NcmLinkParser} 的纯逻辑测试，不依赖 Minecraft。 */
class NcmLinkParserTest {

	@Test
	void parsesShareLinkWithExtraParams() {
		Optional<NcmLinkParser.Link> result = NcmLinkParser.parse(
				"https://music.163.com/song?id=3413072220&uct2=U2FsdGVkX191/aF4veA91lSVbvhbKUCegZA2mk1OBOs=");
		assertTrue(result.isPresent(), "带 uct2 分享参数的单曲链接应当能解析");
		assertEquals(NcmPlayer.TYPE_SONG, result.get().type());
		assertEquals("3413072220", result.get().id());
	}

	@Test
	void parsesPlaylistLink() {
		Optional<NcmLinkParser.Link> result = NcmLinkParser.parse("https://music.163.com/playlist?id=7589303710&userid=1");
		assertTrue(result.isPresent());
		assertEquals(NcmPlayer.TYPE_PLAYLIST, result.get().type());
		assertEquals("7589303710", result.get().id());
	}

	@Test
	void parsesFragmentSong() {
		Optional<NcmLinkParser.Link> result = NcmLinkParser.parse("https://music.163.com/#/song?id=347230");
		assertTrue(result.isPresent(), "fragment 形式的单曲链接应当能解析");
		assertEquals(NcmPlayer.TYPE_SONG, result.get().type());
		assertEquals("347230", result.get().id());
	}

	@Test
	void parsesFragmentPlaylist() {
		Optional<NcmLinkParser.Link> result = NcmLinkParser.parse("https://music.163.com/#/playlist?id=3778678");
		assertTrue(result.isPresent());
		assertEquals(NcmPlayer.TYPE_PLAYLIST, result.get().type());
		assertEquals("3778678", result.get().id());
	}

	@Test
	void parsesMobilePath() {
		Optional<NcmLinkParser.Link> result = NcmLinkParser.parse("https://y.music.163.com/m/song?id=347230");
		assertTrue(result.isPresent(), "带 /m 前缀的移动端路径应当能解析");
		assertEquals(NcmPlayer.TYPE_SONG, result.get().type());
		assertEquals("347230", result.get().id());
	}

	@Test
	void parsesLinkEmbeddedInShareText() {
		Optional<NcmLinkParser.Link> result = NcmLinkParser.parse(
				"分享ChampionAsh5357的单曲《Creeper》: https://music.163.com/song?id=347230&uct2=abc （来自网易云音乐）");
		assertTrue(result.isPresent(), "夹在分享文案里的链接应当能取出来");
		assertEquals(NcmPlayer.TYPE_SONG, result.get().type());
		assertEquals("347230", result.get().id());
	}

	@Test
	void acceptsUppercaseHostAndHttp() {
		Optional<NcmLinkParser.Link> result = NcmLinkParser.parse("HTTP://MUSIC.163.COM/song?id=347230");
		assertTrue(result.isPresent(), "大写 host 和 http 协议都应当识别");
		assertEquals(NcmPlayer.TYPE_SONG, result.get().type());
		assertEquals("347230", result.get().id());
	}

	@Test
	void rejectsAlbum() {
		assertTrue(NcmLinkParser.parse("https://music.163.com/album?id=1").isEmpty());
	}

	@Test
	void rejectsIdWithLetters() {
		assertTrue(NcmLinkParser.parse("https://music.163.com/song?id=abc123").isEmpty());
	}

	@Test
	void rejectsMissingId() {
		assertTrue(NcmLinkParser.parse("https://music.163.com/song").isEmpty());
	}

	@Test
	void rejectsTooLongId() {
		assertTrue(NcmLinkParser.parse("https://music.163.com/song?id=123456789012345678901").isEmpty());
	}

	@Test
	void rejectsSpoofedHost() {
		assertTrue(NcmLinkParser.parse("https://music.163.com.evil.com/song?id=1").isEmpty());
		assertTrue(NcmLinkParser.parse("https://evil.com/song?id=1").isEmpty());
	}

	@Test
	void rejectsJavascriptScheme() {
		assertTrue(NcmLinkParser.parse("javascript:alert(1)").isEmpty());
	}

	@Test
	void rejectsEmptyInput() {
		assertTrue(NcmLinkParser.parse("").isEmpty());
		assertTrue(NcmLinkParser.parse("   ").isEmpty());
		assertTrue(NcmLinkParser.parse(null).isEmpty());
	}
}
