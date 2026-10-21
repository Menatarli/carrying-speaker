# 配置修改帮助

所有配置都放在游戏目录下的 `config/carrying-speaker/` 里。用 PCL 并且开了版本隔离的话，路径是 `.minecraft/versions/<版本名>/config/carrying-speaker/`。

```
config/carrying-speaker/
├─ config.json      基础设置
├─ events.json      事件表（什么时候放什么歌）
├─ music/           本地音乐目录，放 mp3 / wav
└─ .migrated        旧版配置迁移标记（不用管）
```

**改完之后在游戏里执行 `/ncm reload` 就会生效，不用重启。** 文件编码用 UTF-8。只想恢复默认的话，把对应文件删掉，重启游戏后会自动重新生成。

---

## 一、events.json（事件表）

```json
{
  "version": 1,
  "events": [
    {
      "eventType": "LOW_HEALTH",
      "enabled": true,
      "soundFile": "",
      "startOffsetSeconds": 0.0,
      "minHealth": 0.3,
      "exitHealth": 0.5,
      "minPlaySeconds": 10,
      "loop": true
    },
    {
      "eventType": "DEATH",
      "enabled": true,
      "soundFile": "",
      "startOffsetSeconds": 0.0,
      "minPlaySeconds": 120,
      "loop": false
    }
  ]
}
```

### 字段说明

| 字段 | 类型 | 默认 | 取值范围 | 说明 |
|---|---|---|---|---|
| `eventType` | 文本 | — | `LOW_HEALTH` / `DEATH` | 事件类型。每种只写一条，写了多条时只认第一条 |
| `enabled` | 开关 | `true` | `true` / `false` | 是否启用这个事件 |
| `soundFile` | 文本 | `""` | 空，或 `music/` 里的文件名 | 空表示从音乐目录随机挑一首。只写文件名，比如 `"副歌.mp3"`，不能带路径或盘符，扩展名只能是 `.mp3` / `.wav` |
| `startOffsetSeconds` | 数字 | `0` | 0–3600 | 从第几秒开始播，比如 `62.5` 就是从 1 分 02.5 秒起播。只对第一首生效，循环到下一首会从头放 |
| `minHealth` | 数字 | `0.3` | 大于 0、小于 1 | **只有 LOW_HEALTH 用**：血量占比 ≤ 这个值时开始播放（0.3 就是 30%，满血 20 时为 6 点） |
| `exitHealth` | 数字 | `0.5` | 大于 minHealth，≤ 1 | **只有 LOW_HEALTH 用**：血量回到 ≥ 这个值才算结束。两个值之间留一段距离，防止血量在线上来回跳时反复触发 |
| `minPlaySeconds` | 整数 | 低血量 `10` / 死亡 `120` | 0–600 | 至少播多少秒。回血或复活后也会放满这个时长再停。0 表示事件一结束就停 |
| `loop` | 开关 | 低血量 `true` / 死亡 `false` | `true` / `false` | `true`：一首放完接着随机放下一首，直到事件结束；`false`：只放这一首 |

### 规则

- **死亡优先**：低血量音乐正在放的时候死了，会马上切到死亡音乐；死亡音乐放着的时候，低血量不会打断它。
- 最短播放时长到了，并且事件已经结束（已经回血或复活），音乐才会停。
- 退出世界、执行 `/ncm local stop`、执行 `/ncm reload` 时，音乐会立刻停止。

### 填错了会怎样

- 整个文件格式坏了（比如少了逗号）：这次全部按默认值运行，**你的文件不会被覆盖**，日志里会有一行 warn。改好后执行 `/ncm reload` 就行。
- 某一条事件里有字段不合法（比如起播秒数写成负数）：只有这一条按默认值运行，其他事件不受影响。
- 写了不认识的 `eventType`：跳过这一条。
- 指定的 `soundFile` 不存在：事件触发时聊天栏会提示，不会改成随机播放。

### 常见改法

**死亡时固定从副歌开始放某首歌，至少放 2 分钟**
```json
{ "eventType": "DEATH", "enabled": true, "soundFile": "我的歌.mp3",
  "startOffsetSeconds": 58, "minPlaySeconds": 120, "loop": false }
```

**不想要低血量音乐**：把 LOW_HEALTH 的 `"enabled"` 改成 `false`。

**血量低于 20% 才放，回到 60% 再停**：`"minHealth": 0.2, "exitHealth": 0.6`。

**怎么找副歌的秒数**：在任意播放器里拖到副歌开头，看进度条上的时间，换算成秒填进去。

---

## 二、config.json（基础设置）

| 字段 | 默认 | 范围 | 说明 |
|---|---|---|---|
| `musicDir` | `"music"` | 文件夹名 | 本地音乐目录，相对于 `config/carrying-speaker/`。也可以改成别的名字，比如 `"bgm"` |
| `ncmBackground` | `true` | `true` / `false` | 网易云被唤起后要不要自动收回托盘、把焦点还给游戏。`false` 时什么窗口都不动 |
| `coldStartDelaySeconds` | `10` | 0–60 | 网易云没打开时，先启动它，等这么多秒再发播放指令。电脑比较慢、网易云启动后不出声的话，可以调大 |
| `lowHealthEnabled`、`enterRatio`、`exitRatio`、`minPlaySeconds` | — | — | **已经不起作用**，旧版留下来的字段，现在由 `events.json` 管。保留或删掉都可以 |

填错的值会自动回退到默认值，并在日志里记一行 warn。

---

## 三、music/（本地音乐目录）

- 支持 `.mp3` 和 `.wav`，扩展名大小写都行。只读这个目录的第一层，子文件夹不会读。
- 文件名可以用中文。
- 放进去或删掉文件后，下次触发事件就会用新的列表，不用 reload。

---

## 四、日志排查

日志在 `logs/latest.log`，搜 `carryingspeaker` 就能看到本模组的记录，比如：

- `已加载：音乐目录=…`：启动正常，后面跟着实际使用的音乐目录
- `读取事件表 … 失败，改用默认值`：events.json 格式有问题
- `网易云抢到前台，已收回托盘并把焦点还给 MC`：后台播放正常工作
- `检测到用户主动切到网易云，本轮不再接管窗口`：你自己切过去了，模组没有干预
