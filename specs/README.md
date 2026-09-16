# 明视辅助设计资源包

## 文件说明

- `screens/`：六张高保真页面图片；按 1080 × 2400 Android 设计基准标注。
- `明视辅助_标注设计稿.png`：高保真界面总览与设计标注。
- `icons/`：可用于 Android Vector Drawable 转换的 SVG 图标。
- `design_tokens.json`：颜色、圆角、间距、字号与触控尺寸。

## 关键标注

| 项目 | 值 |
|---|---|
| 设计基准 | 360 × 800 dp（1080 × 2400 px @3x） |
| 页面左右边距 | 24 dp |
| 卡片间距 | 12 dp |
| 主区块间距 | 24 dp |
| 卡片圆角 | 16 dp |
| 最小点击区域 | 48 × 48 dp |
| 主按钮高度 | 56 dp |
| 默认阅读字号 | 48 sp |
| 深蓝背景 | `#0B2E59` |
| 主操作蓝 | `#2979FF` |
| 基础白 | `#FFFFFF` |

## Android 注意事项

- SVG 需要用 Android Studio 的 **Vector Asset** 导入，或转换为 `vector` XML。
- 避免将 OCR 结果仅渲染在图片上；应使用 TextView/Compose Text 重排，以支持字号、行距与 TalkBack。
- 高对比模式仅为阅读辅助，不应作为医疗治疗宣传。
