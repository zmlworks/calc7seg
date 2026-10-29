# 電卓 (calc7seg) — Android

市販の卓上電卓風、10 桁 7 セグ液晶の軽量電卓。パッケージ `com.zmlworks.calc7seg`。
Plain Java + framework View のみ（ライブラリなし）。

## キー配列（4 列）

```
MC   MR   M-   M+
func func CA   CE
BS   √    +/-  ÷
1    2    3    ×
4    5    6    −
7    8    9    +
0    00   .    =
```

`func` ×2 は未割当（`MainActivity.onFunc(int)` / `Calculator.Key.FUNC1/FUNC2` に後で割り当て）。

## 計算仕様（`Calculator.java`、BigDecimal）

- 即時実行の連鎖: `2 + 3 × 4 =` → 20
- 定数計算: `=` を続けて押すと最後の演算を第 2 オペランドで繰り返す（`2+3===` → 5, 8, 11 / `2×3==` → 6, 18）。`5 × =` → 25（二乗）
- 結果は 10 桁に切り捨て、末尾の 0 は除去
- |結果| ≥ 10^10 → E（表示は ×10^-10 の値。実機と同じ）、0 除算・負の √ → E。E 中は CA / CE のみ受付
- CA: 全クリア（メモリは保持）、CE: 入力中の数値をクリア、BS: 入力中の最終桁を削除
- M+ / M-: 保留中の演算を完了してから表示値を加減算。メモリ ≠ 0 で M 表示
- 状態は回転・アプリ再起動をまたいで保持（SharedPreferences）。画面は縦固定

## ビルド

```sh
. /workspace/android/env.sh
cd /workspace/android/calc7seg
./gradlew :app:testReleaseUnitTest :app:assembleRelease
```

署名は `/workspace/android/keys/keystore.properties`（リポジトリ外）。
成果物: `app/build/outputs/apk/release/` → 配布用コピー `/workspace/android/out/calc7seg-0.1.0-release.apk`

液晶のプレビュー PNG（端末不要、JDK のみ）: `tools/preview/render.sh <出力先>`
