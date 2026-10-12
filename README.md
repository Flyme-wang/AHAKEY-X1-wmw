# AhaKey X1 语音键改绑豆包输入法 · 可复刻手册

> 把 AhaKey X1 的语音键（Key1）从出厂映射（F18 / "Record"）改为 **右 Alt**，
> 在任意窗口按住该键即可唤起 **豆包输入法** 的语音录入，说完松开即上屏。

- 适用设备：AhaKey X1（4 键 + 拨杆 + OLED 屏）
- 软件环境：Windows 11 · AhaKey Studio 完整版 1.0.0 · 豆包输入法（Windows 版）· JDK 17
- 实测日期：2026-10-12
- 官方手册：<https://ahakey.com/download/windows/guide>

---

## 0. 结论速览（TL;DR）

**可行。** X1 的语音键本质是一个可编程按键：

- 出厂：Key1 = `F18`（HID `0x6D`），描述 `Record`
- 改后：Key1 = **右 Alt**（HID `0xE6`），描述 `Doubao`，全部 4 个模式统一更新

豆包输入法 Windows 版默认**长按右 Alt 唤起语音录入**，因此把 X1 语音键映射成右 Alt 后，
按住 X1 语音键 = 按住右 Alt = 直接说话。映射由键盘固件完成，**不依赖任何常驻软件做键位转换**。

---

## 1. 需求与官方依据

### 1.1 问题

> AhaKey X1 能不能使用豆包的语音输入法？

### 1.2 官方手册依据

官方操作手册（<https://ahakey.com/download/windows/guide>）明确了两点：

1. 语音键支持替换为第三方语音工具，并给出参考快捷键：「常用语音输入法快捷键：Typeless：Right Alt。微信输入法：Fn」（手册原文）。
2. 蓝牙模式下需保持 `BLE_tcp_driver.exe` 运行；AI 联动功能需保持 `AhaKeyStudio.exe` 运行。

### 1.3 三个关键事实（构成方案的支点）

1. X1 语音键 = 可编程键：出厂 HID `0x6D`（109 / F18）、描述 `Record`。
2. 豆包输入法 Windows 版的语音快捷键默认是**长按右 Alt**（设置 → 语音，可改）。
3. 「X1 语音键 → 右 Alt」即最简、最稳的对接方式；官方手册给出的 Typeless 参考值（Right Alt）与此一致。

---

## 2. 方案设计与对比

| 方案 | 做法 | 评价 |
| --- | --- | --- |
| **A. 映射为右 Alt（采用）** | Key1 → HID `0xE6` | ✅ 与豆包默认快捷键一致；键盘固件直接发 HID，不依赖常驻软件；换电脑/关掉 Studio 也生效 |
| B. 映射为 Win+H | Key1 → Win+H 组合键 | ✗ 走 Windows 自带语音：需开启"在线语音识别"，且官方要求外接麦克风（笔记本内置麦不可用），链路更长 |
| C. 用按键宏模拟 | 宏序列（按下/延时/释放） | ✗ 适合组合键（如 Ctrl+C）；单键场景多此一举，且有宏延时 |

**决策：方案 A** —— 在 Studio 中把全部 4 个模式的 Key1 统一改为右 Alt。

---

## 3. 执行方式（两种，任选其一）

> 目标一致：把 **全部 4 个模式** 的 **Key1** 改为 **右 Alt**、描述改为 **Doubao**，并写入设备。

### 方式一：AhaKey Studio GUI（零代码）

1. 设备开机，确认 `BLE_tcp_driver.exe` 已连接；打开 `AhaKeyStudio.exe` → **连接设备**。
2. 点 **编辑配置** → 点选一个模式（如 Mode1）→ 在 4 键示意图里点击 **Key1**。
3. 按键类型选 **快捷键**；键码下拉框选 **Right Alt** → 点 **添加**（单键快捷键只需这一个条目）。
4. **按键描述**填 `Doubao`（会显示在键盘屏幕上；建议英文、数字或下划线）。
5. 点 **应用按键到设备**。
6. 对其余 3 个模式重复第 2–5 步，最后 **保存配置**。

> ⚠️ 手工方式最容易踩的坑：漏改某个模式、或键码选错。4 个模式全部改完才算完成。

### 方式二：自动化脚本（本仓库提供，推荐批量/复刻）

不依赖界面，直接调用 Studio 自带类库完成"改键 → 写设备 → 保存草稿"，一次跑完 4 个模式。

**原理**

- AhaKey Studio 是 Java 应用（`app\ahakey-studio-1.0.0.jar` + `app\lib\*.jar`），按键配置与设备同步逻辑都在类库里；
- 脚本复用其类库：`StudioStore`（读写草稿）→ `StudioState`（状态对象）→ `KeyConfig.setHidCode`（改键码）→ `DeviceSyncService.commandsForModes / writeSequentially`（生成并顺序下发命令）→ `StudioStore.save`（保存草稿）；
- 设备通道：`BleManager` 优先 USB，回退 **BLE 桥**（`BLE_tcp_driver.exe` 监听的 `127.0.0.1:9000`）。

**前置条件**

- AhaKey Studio 完整版已能正常连接设备（先用 GUI 连成功过一次）；
- 豆包输入法已安装并运行，语音快捷键保持默认 **右 Alt**；
- JDK 17+（`javac` / `java` 可用，或用完整路径）。

**步骤（PowerShell 逐行复制执行）**

```powershell
# ① 关闭 AhaKey Studio（避免其占用键盘通信通道）；
#    然后确认 BLE_tcp_driver.exe 仍在运行（任务管理器查看）；
#    若已退出，到 D:\Program Files\AhaKeyStudio\app\ 下手动启动它。

# ② 备份当前草稿（可回滚）
copy "$env:USERPROFILE\.ahakey\studio-draft.json" "$env:USERPROFILE\.ahakey\studio-draft.json.pre-doubao"

# ③ 编译脚本（在源码目录内执行；javac 不在 PATH 时改用完整路径，如 "C:\Program Files\Java\jdk-17\bin\javac.exe"）
javac -encoding UTF-8 -cp "D:\Program Files\AhaKeyStudio\app\ahakey-studio-1.0.0.jar;D:\Program Files\AhaKeyStudio\app\lib\*" -d . ApplyDoubaoVoiceKey.java

# ④ 运行（约 2–3 秒完成写入；看到 RESULT=OK 即成功）
java -cp ".;D:\Program Files\AhaKeyStudio\app\ahakey-studio-1.0.0.jar;D:\Program Files\AhaKeyStudio\app\lib\*" ApplyDoubaoVoiceKey

# ⑤ 重新打开 AhaKey Studio → 连接设备（恢复 AI 联动与 BLE 桥常驻）
#    验证：编辑配置中 Key1 显示 Right Alt；键盘 OLED 屏显示 Doubao。
```

> 路径说明：`D:\Program Files\AhaKeyStudio` 为 Studio 安装目录（按实际修改）；cmd 用户把 `$env:USERPROFILE` 换成 `%USERPROFILE%`；Git Bash 用户把双引号换成单引号，其余相同。

**脚本做了什么（写入协议）**

一次完整写入共 **38 条命令**顺序下发（发一条、等一条响应，实测约 2.6 秒）：

| 内容 | 条数 |
| --- | --- |
| 每个模式：Key1 键码、Key1 描述、Key2 键码、Key2 描述、Key3 宏、Key3 描述、Key4 键码、Key4 描述、AI 灯效 | 9 × 4 模式 = 36 |
| 灯光亮度 | 1 |
| saveConfig（`0x04`，保存全部配置到设备） | 1 |
| **合计** | **38** |

帧格式示例（实测抓包）：

```text
AA BB 73 73 00 00 E6 CC DD              ← Mode1·Key1 键码 = 0xE6（右 Alt）
AA BB 73 75 00 00 44 6F 75 62 61 6F CC DD   ← Mode1·Key1 描述 = "Doubao"（ASCII）
AA BB 04 CC DD                          ← saveConfig：保存全部配置
```

（`73 73`=键码、`73 75`=描述、`73 74`=宏、`84`=AI 灯效、`85`=亮度；`AA BB` 帧头、`CC DD` 帧尾）

> 脚本按"当前草稿整机同步"：只改了 Key1，其余键位/灯效按草稿原值重写一遍，不会丢配置；脚本可重复执行。

---

## 4. 使用方法

1. 豆包输入法保持运行；其语音快捷键保持 **长按右 Alt**（设置 → 语音；不要改成"右 Alt + 空格"等组合）。
2. 按住 X1 的语音键说话 → 松开 → 文字上屏。
3. 不想一直按着？豆包语音设置里切 **免提模式**（按一下开始、再按一下结束）。
4. 想在任意窗口都能触发（不依赖输入框焦点）→ 打开豆包的 **全局语音快捷键** 开关。
5. 麦克风在豆包设置里选择（建议耳机/外接麦；笔记本内置麦识别效果一般）。

---

## 5. 排障

| 现象 | 排查 |
| --- | --- |
| 按语音键无反应 | ① 豆包输入法是否在运行；② 豆包快捷键是否被改过；③ 键盘是否连到这台电脑 |
| 长按没开始录音 | 豆包快捷键被改成组合键了；恢复为"右 Alt"，或按豆包实际快捷键重新映射 X1 |
| 蓝牙偶发断连 | 保持 `BLE_tcp_driver.exe` 常驻；上传达 GIF/大图建议有线连接（官方手册） |
| AI 灯效不联动 | 保持 `AhaKeyStudio.exe` 运行；重新"连接设备" |
| 脚本报连接失败 | 先关闭 Studio 再跑；确认 `BLE_tcp_driver.exe` 在运行、`127.0.0.1:9000` 可连 |
| 写入超时 | 检查键盘距离/蓝牙质量；重跑一次（脚本可重复执行，幂等） |

---

## 6. 实测记录

**环境**：Windows 11 · AhaKey Studio 完整版 1.0.0 · JDK 17 · 豆包输入法 Windows 版 · 键盘 BLE 名 `AhaKey 665E` · 2026-10-12

**执行结果（脚本输出摘录）**

```text
=== 2. KEY1 before ===
  MODE0: hid=0x6D desc=Record preset=CUSTOM   ← 出厂 F18
  MODE1: hid=0x6D desc=Record preset=CUSTOM
  MODE2: hid=0x6D desc=Record preset=CUSTOM
  MODE3: hid=0x0  desc=N/A    preset=CUSTOM

=== 3. set KEY1 -> RIGHT_ALT (0xe6) ===
  MODE0: hid=0xE6 desc=Doubao preset=CUSTOM
  MODE1: hid=0xE6 desc=Doubao preset=CUSTOM
  MODE2: hid=0xE6 desc=Doubao preset=CUSTOM
  MODE3: hid=0xE6 desc=Doubao preset=CUSTOM

=== 5. write commands to device ===
  total commands: 38
  [38] 保存中 (38/38) 保存全部配置到设备
  write success
  draft saved
RESULT=OK
```

**效果**

- 4 个模式的语音键统一为 **右 Alt / Doubao**，已写入键盘（`saveConfig` 掉电保存）；
- 键盘 OLED 屏显示 `Doubao`；
- 按住 X1 语音键说话 → 豆包语音悬浮窗弹出 → 松开后文字上屏。

---

## 7. 回滚（恢复出厂键位）

任选其一：

1. **脚本回滚**：把 `src/ApplyDoubaoVoiceKey.java` 中
   `kc.setHidCode(HIDUsage.RIGHT_ALT);` 改回 `kc.setHidCode(0x6D);`（F18）、
   `kc.setDescription("Doubao");` 改回 `kc.setDescription("Record");`，重跑脚本；
2. **草稿回滚**：把备份 `studio-draft.json.pre-doubao` 覆盖回 `%USERPROFILE%\.ahakey\studio-draft.json`，重启 Studio → 连接设备 → 应用；
3. **GUI 回滚**：Studio 中把各模式 Key1 键码从 `Right Alt` 改回 `F18`（描述 `Record`），应用并保存。

---

## 8. 举一反三

同一套方法可平移到其它语音工具 / 其它按键：

| 目标 | X1 侧映射 | 备注 |
| --- | --- | --- |
| 豆包输入法（本手册） | 右 Alt | 豆包默认长按右 Alt |
| Typeless | 右 Alt | 官方手册参考值 |
| 微信输入法 | Fn | 官方手册参考值；HID 无标准 Fn，视工具是否可选其它键 |
| 其它任意键 | 任意 HID 键码 | 把脚本里 `HIDUsage.RIGHT_ALT` 换成目标键即可 |

---

## 9. 安全与说明

- 本仓库 **不包含** 任何账号、口令、密钥；
- `%USERPROFILE%\.ahakey\` 目录中可能有第三方工具的配置文件**含明文账号/口令**，请勿整目录外传或上传网盘；
- 本方案仅修改**自有设备**的按键映射，不修改任何软件文件；Studio 升级后包名/类名若变化，脚本需同步微调（见 `src/` 源码）；
- 本手册为个人实践记录，与 AhaKey 官方无关联。

---

*本手册由本次实际执行过程整理；脚本与命令均在本机验证通过（RESULT=OK）。*
