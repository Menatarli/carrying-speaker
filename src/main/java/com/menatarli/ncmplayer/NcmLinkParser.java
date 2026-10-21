package com.menatarli.ncmplayer;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从网易云分享链接里解析出播放类型和 id。
 *
 * <p>纯逻辑：不引用 Minecraft 类、不联网、不做任何系统调用。用户给的链接只用来提取 type 和 id，
 * 永远不会被原样打开。
 *
 * <p>支持的形状：{@code /song?id=}、{@code /playlist?id=}，路径可以带 {@code /m} 前缀，
 * 也可以放在 fragment 里（{@code #/song?id=}）。其他路径（album / artist / djradio / 短链）一律不支持。
 */
public final class NcmLinkParser {
	/** 解析结果。type 取 {@link NcmPlayer#TYPE_SONG} 或 {@link NcmPlayer#TYPE_PLAYLIST}。 */
	public record Link(String type, String id) {
	}

	/** 分享文案里可能夹带其他文字，只取第一个 http(s) 链接。 */
	private static final Pattern URL_IN_TEXT = Pattern.compile("https?://\\S+", Pattern.CASE_INSENSITIVE);

	private static final String HOST_MAIN = "music.163.com";
	private static final String HOST_YMUSIC = "y.music.163.com";

	private NcmLinkParser() {
	}

	/** 解析失败一律返回空，不往外抛异常。 */
	public static Optional<Link> parse(String input) {
		try {
			if (input == null) {
				return Optional.empty();
			}
			String trimmed = input.trim();
			if (trimmed.isEmpty()) {
				return Optional.empty();
			}

			Matcher matcher = URL_IN_TEXT.matcher(trimmed);
			String url = matcher.find() ? matcher.group() : trimmed;

			URI uri = new URI(url);
			String scheme = uri.getScheme();
			if (scheme == null) {
				return Optional.empty();
			}
			scheme = scheme.toLowerCase(Locale.ROOT);
			if (!scheme.equals("http") && !scheme.equals("https")) {
				return Optional.empty();
			}

			String host = uri.getHost();
			if (host == null) {
				return Optional.empty();
			}
			host = host.toLowerCase(Locale.ROOT);
			if (!host.equals(HOST_MAIN) && !host.equals(HOST_YMUSIC)) {
				return Optional.empty();
			}

			String path = uri.getRawPath();
			String query = uri.getRawQuery();

			String fragment = uri.getRawFragment();
			if (fragment != null && !fragment.isEmpty()) {
				// fragment 形如 /song?id=...，把它拆成路径和查询串。
				int question = fragment.indexOf('?');
				if (question >= 0) {
					path = fragment.substring(0, question);
					query = fragment.substring(question + 1);
				} else {
					path = fragment;
					query = null;
				}
			}

			if (path == null) {
				return Optional.empty();
			}
			String normalized = stripMobilePrefix(path);
			final String type;
			if (normalized.equals("/song")) {
				type = NcmPlayer.TYPE_SONG;
			} else if (normalized.equals("/playlist")) {
				type = NcmPlayer.TYPE_PLAYLIST;
			} else {
				return Optional.empty();
			}

			String id = queryParam(query, "id");
			if (id == null || !NcmPlayer.isValidId(id)) {
				return Optional.empty();
			}
			return Optional.of(new Link(type, id));
		} catch (Exception e) {
			return Optional.empty();
		}
	}

	/** 去掉移动端路径前缀 /m：/m/song → /song。 */
	private static String stripMobilePrefix(String path) {
		if (path.startsWith("/m/")) {
			return path.substring(2);
		}
		return path;
	}

	/** 从查询串里取一个参数，返回解码后的值；没有返回 null。其余参数一律忽略。 */
	private static String queryParam(String query, String name) {
		if (query == null || query.isEmpty()) {
			return null;
		}
		for (String pair : query.split("&")) {
			int equals = pair.indexOf('=');
			if (equals <= 0) {
				continue;
			}
			if (!pair.substring(0, equals).equals(name)) {
				continue;
			}
			String raw = pair.substring(equals + 1);
			try {
				return URLDecoder.decode(raw, StandardCharsets.UTF_8);
			} catch (Exception e) {
				return raw;
			}
		}
		return null;
	}
}
