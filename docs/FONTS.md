# 内嵌中文字体（PRD 5.2）

UI 已内嵌子集化中文字体，不再依赖系统默认字体
（ColorOS 默认字体和 AOSP 不同，占位字体曾让不同机器上的观感不一致）。

## 已打包的字重

字体文件在 `core/designsystem/src/main/res/font/`，
由 `Type.kt` 的 `SerifFamily` / `SansFamily` 引用。

| 字体 | 用途 | 字重 | 文件 | 大小 |
|---|---|---|---|---|
| Noto Serif SC | 标题、金额、倒数天数 | 400 / 500 | `noto_serif_sc_regular.ttf` / `noto_serif_sc_medium.ttf` | 各 ~3.36MB |
| MiSans | 正文、UI、按钮、标签 | 400 / 500 / 600 / 700 | `misans_regular.ttf` / `misans_medium.ttf` / `misans_semibold.ttf` / `misans_bold.ttf` | 各 ~1.65MB |

700（Bold）是 Markdown 加粗在用的字重；其余字重与 `Type.kt` 样式一一对应。

## 来源与授权

- **Noto Serif SC**：Google Fonts，SIL OFL 1.1，免费商用。
  从本机 `C:\Windows\Fonts\NotoSerifSC-VF.ttf`（可变字体）实例化出静态字重。
- **MiSans**：小米官方免费商用。
  从 GitHub 镜像 `boyan01/mi_sans_font`（`lib/fonts/MiSans-*.ttf`）下载。

## 子集化

- 工具：`tools/subset_fonts.py`（fontTools 4.60）。
- 字符集：GB2312 全表 + ASCII 可打印 + 常用中文标点 +
  App 源码（kt/xml/kts）实际出现的所有字符。
- 缺字会回退到系统字体，不会显示豆腐块，所以 GB2312 之外的生僻字不影响使用。
- 重新生成：

```powershell
python tools/subset_fonts.py `
  --noto-serif-vf "C:\Windows\Fonts\NotoSerifSC-VF.ttf" `
  --misans-dir <MiSans TTF 所在目录> `
  --out core/designsystem/src/main/res/font
```

## 两个已知取舍

1. **Noto Serif SC 单字重 3.36MB，略超 PRD 的 3MB 目标。**
   保留 GB2312 二级字库（3008 个次常用字，含大量人名用字如 鑫/垚/婳/珩），
   避免笔记/纪念日里这些字回退到系统字体造成视觉割裂。
   对自用 App，APK 多几百 KB 完全可接受。
2. **Noto Serif SC 没有 `tnum` 特性**：倒数天数上 `TabularNumbers` 静默失效
   （MiSans 有 `tnum`，正文/金额等场景不受影响）。与接入前行为一致，不算回归。
