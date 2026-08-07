#!/usr/bin/env python3
"""MyAPP 内嵌中文字体子集化脚本（PRD 5.2）。

来源：
- Noto Serif SC：本机 C:\\Windows\\Fonts\\NotoSerifSC-VF.ttf（Google Fonts 可变字体，
  用 fontTools 实例化为静态字重 400/500 后再裁剪）
- MiSans：GitHub 镜像（boyan01/mi_sans_font）的静态 TTF，
  裁剪字重 400/500/600/700（700 是 Markdown 加粗在用）

裁剪字符集 = GB2312 全部字符 + ASCII 可打印 + 常用中文标点/符号
             + App 源码（kt/xml/kts）里实际出现的所有字符。
缺字会回退到系统字体，不会显示豆腐块，所以 GB2312 之外的生僻字不影响使用。

用法：
    python tools/subset_fonts.py ^
        --noto-serif-vf "C:\\Windows\\Fonts\\NotoSerifSC-VF.ttf" ^
        --misans-dir .tmp_fonts ^
        --out core/designsystem/src/main/res/font

生成的字体文件提交到仓库，换机器不漂移。
"""

from __future__ import annotations

import argparse
import os

from fontTools import subset
from fontTools.ttLib import TTFont
from fontTools.varLib.instancer import instantiateVariableFont


REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def collect_chars() -> set[str]:
    """GB2312 全部字符 + ASCII 可打印 + 常用中文标点/符号。"""
    chars: set[str] = set()

    # ASCII 可打印（拉丁字母、数字、常见符号——中英混排必需）
    chars.update(chr(c) for c in range(0x20, 0x7F))

    # GB2312 全表：双字节区位码，lead 0xA1-0xF7、trail 0xA1-0xFE
    for lead in range(0xA1, 0xF8):
        for trail in range(0xA1, 0xFF):
            try:
                chars.update(bytes([lead, trail]).decode("gb2312"))
            except UnicodeDecodeError:
                pass

    # 常用标点/符号区段（只保留真正常用且 GB2312 未覆盖的；
    # 箭头/制表符等零散字符若源码用到，会通过 collect_source_chars() 带进来）
    ranges = [
        (0x00A0, 0x00FF),   # Latin-1 补充（含 ±×÷ 等）
        (0x2000, 0x206F),   # 通用标点
        (0x3000, 0x303F),   # CJK 标点
        (0xFF00, 0xFFEF),   # 全角形式
    ]
    for start, end in ranges:
        chars.update(chr(c) for c in range(start, end + 1))
    return chars


def collect_source_chars() -> set[str]:
    """App 源码里实际出现过的字符，保证 UI 文案永远不缺字。"""
    chars: set[str] = set()
    skip_parts = {"build", ".git", ".gradle", ".idea", ".tmp_fonts", ".kotlin"}
    for dirpath, dirnames, filenames in os.walk(REPO_ROOT):
        dirnames[:] = [d for d in dirnames if d not in skip_parts]
        for name in filenames:
            if not name.endswith((".kt", ".kts", ".xml")):
                continue
            path = os.path.join(dirpath, name)
            try:
                with open(path, encoding="utf-8", errors="ignore") as f:
                    chars.update(f.read())
            except OSError:
                pass
    return chars


def make_subsetter(chars: set[str]) -> subset.Subsetter:
    opts = subset.Options()
    opts.layout_features = ["*"]      # 保留 tnum/kern/liga 等排版特性
    opts.name_IDs = ["*"]             # 保留完整 name 表（含许可证信息）
    opts.name_legacy = True
    opts.name_languages = ["*"]
    opts.drop_tables += ["DSIG", "FFTM"]
    opts.notdef_outline = True
    opts.recalc_bounds = True
    opts.recalc_timestamp = True
    sub = subset.Subsetter(opts)
    sub.populate(text="".join(sorted(chars)))
    return sub


def subset_path(src: str, dst: str, chars: set[str]) -> int:
    sub = make_subsetter(chars)
    font = TTFont(src)
    sub.subset(font)
    font.save(dst)
    return os.path.getsize(dst)


def instance_noto_serif(src: str, dst: str, wght: int, chars: set[str]) -> int:
    font = TTFont(src)
    font = instantiateVariableFont(font, {"wght": wght})
    # 轴已钉死，去掉可变字体残留表，得到纯静态 TTF
    for table in ("fvar", "gvar", "HVAR", "MVAR", "avar", "STAT", "cvar"):
        if table in font:
            del font[table]
    sub = make_subsetter(chars)
    sub.subset(font)
    font.save(dst)
    return os.path.getsize(dst)


def main() -> None:
    parser = argparse.ArgumentParser(description="MyAPP 中文字体子集化")
    parser.add_argument("--noto-serif-vf", required=True, help="Noto Serif SC 可变字体路径")
    parser.add_argument("--misans-dir", required=True, help="MiSans 静态 TTF 所在目录")
    parser.add_argument("--out", required=True, help="输出目录（res/font）")
    args = parser.parse_args()

    chars = collect_chars() | collect_source_chars()
    print(f"字符集大小: {len(chars)}")
    os.makedirs(args.out, exist_ok=True)

    # Noto Serif SC：标题/倒数天数（400/500）
    for wght, name in (
        (400, "noto_serif_sc_regular"),
        (500, "noto_serif_sc_medium"),
    ):
        dst = os.path.join(args.out, f"{name}.ttf")
        size = instance_noto_serif(args.noto_serif_vf, dst, wght, chars)
        print(f"{os.path.basename(dst):28s} {size / 1024:.0f} KB")

    # MiSans：正文/UI/按钮（400/500/600/700）
    for src_name, out_name in (
        ("MiSans-Regular.ttf", "misans_regular"),
        ("MiSans-Medium.ttf", "misans_medium"),
        ("MiSans-Semibold.ttf", "misans_semibold"),
        ("MiSans-Bold.ttf", "misans_bold"),
    ):
        src = os.path.join(args.misans_dir, src_name)
        dst = os.path.join(args.out, f"{out_name}.ttf")
        size = subset_path(src, dst, chars)
        print(f"{os.path.basename(dst):28s} {size / 1024:.0f} KB")


if __name__ == "__main__":
    main()
