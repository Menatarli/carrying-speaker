package com.menatarli.ncmplayer;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 音效事件表：{@code events.json} 的读取、容错与默认模板生成。
 *
 * <p>容错原则：文件不存在就写一份默认模板；读不了或解析失败**绝不覆盖用户文件**，只用默认值；
 * 单个事件字段非法只回退那一条；同一类型出现多次取第一个；未知类型跳过。
 * 任何异常都在内部吞掉，不往外抛。
 */
public final class SoundEventTable {
	/** 文件名。 */
	public static final String FILE_NAME = "events.json";
	/** 顶层版本号。 */
	public static final int VERSION = 1;

	private static final Logger LOGGER = LoggerFactory.getLogger("carryingspeaker");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private final Map<SoundEventConfig.EventType, SoundEventConfig> events;

	private SoundEventTable(Map<SoundEventConfig.EventType, SoundEventConfig> events) {
		this.events = events;
	}

	/** 全部事件的默认值。 */
	public static SoundEventTable defaults() {
		Map<SoundEventConfig.EventType, SoundEventConfig> map = new EnumMap<>(SoundEventConfig.EventType.class);
		for (SoundEventConfig.EventType type : SoundEventConfig.EventType.values()) {
			map.put(type, SoundEventConfig.defaults(type));
		}
		return new SoundEventTable(map);
	}

	/** 取某个事件的配置。 */
	public Optional<SoundEventConfig> get(SoundEventConfig.EventType type) {
		return Optional.ofNullable(events.get(type));
	}

	/**
	 * 从文件加载。文件不存在会写默认模板；任何失败都回退默认表，绝不抛异常。
	 *
	 * @param file events.json 路径，可为 null
	 */
	public static SoundEventTable load(Path file) {
		if (file == null) {
			return defaults();
		}
		try {
			if (Files.notExists(file)) {
				writeTemplate(file);
				return defaults();
			}
			return parse(Files.readString(file, StandardCharsets.UTF_8));
		} catch (Exception e) {
			LOGGER.warn("读取事件表 {} 失败，改用默认值", file, e);
			return defaults();
		}
	}

	/** 解析 JSON 文本。包内可见，方便单测直接喂字符串。 */
	static SoundEventTable parse(String text) {
		Map<SoundEventConfig.EventType, SoundEventConfig> map =
				new EnumMap<>(SoundEventConfig.EventType.class);
		try {
			JsonObject root = JsonParser.parseString(text).getAsJsonObject();
			JsonArray array = root.has("events") ? root.getAsJsonArray("events") : null;
			if (array == null) {
				LOGGER.warn("事件表里没有 events 数组，改用默认值");
				return defaults();
			}
			for (JsonElement element : array) {
				if (!element.isJsonObject()) {
					continue;
				}
				JsonObject json = element.getAsJsonObject();
				String typeName = json.has("eventType") ? json.get("eventType").getAsString() : "";
				final SoundEventConfig.EventType type;
				try {
					type = SoundEventConfig.EventType.valueOf(typeName.toUpperCase(Locale.ROOT));
				} catch (Exception e) {
					LOGGER.warn("事件表里有未知事件类型「{}」，已跳过", typeName);
					continue;
				}
				if (map.containsKey(type)) {
					LOGGER.warn("事件表里「{}」出现了多次，取第一个", type);
					continue;
				}
				Optional<SoundEventConfig> parsed = SoundEventConfig.parse(type, json);
				if (parsed.isPresent()) {
					map.put(type, parsed.get());
				} else {
					LOGGER.warn("事件表里「{}」的字段不合法，回退到该类型的默认值", type);
					map.put(type, SoundEventConfig.defaults(type));
				}
			}
		} catch (Exception e) {
			LOGGER.warn("解析事件表失败，改用默认值", e);
			return defaults();
		}
		// 文件里没提到的事件类型补默认值。
		for (SoundEventConfig.EventType type : SoundEventConfig.EventType.values()) {
			map.putIfAbsent(type, SoundEventConfig.defaults(type));
		}
		return new SoundEventTable(map);
	}

	/** 默认模板的 JSON 文本。 */
	public static String defaultJson() {
		JsonObject root = new JsonObject();
		root.addProperty("version", VERSION);
		JsonArray array = new JsonArray();
		array.add(SoundEventConfig.defaults(SoundEventConfig.EventType.LOW_HEALTH).toJson());
		array.add(SoundEventConfig.defaults(SoundEventConfig.EventType.DEATH).toJson());
		root.add("events", array);
		return GSON.toJson(root) + System.lineSeparator();
	}

	/** 先写临时文件再 move 替换，避免写一半留下坏文件。 */
	private static void writeTemplate(Path file) {
		try {
			Path parent = file.getParent();
			if (parent != null) {
				Files.createDirectories(parent);
			}
			Path temp = file.resolveSibling(file.getFileName() + ".tmp");
			Files.writeString(temp, defaultJson(), StandardCharsets.UTF_8);
			Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
			LOGGER.info("事件表不存在，已生成默认模板：{}", file.toAbsolutePath());
		} catch (Exception e) {
			LOGGER.warn("生成事件表模板 {} 失败", file, e);
		}
	}
}
