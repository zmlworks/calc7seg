# 電卓 (calc7seg) — Android

市販の卓上電卓風、10 桁 7 セグ液晶の軽量電卓。パッケージ `com.zmlworks.calc7seg`。
Plain Java + framework View のみ（ライブラリなし）。

## キー配列（4 列）

```
MC   MR   M-   M+
1/x  Ans. CA   CE
BS   √    +/-  ÷
1    2    3    ×
4    5    6    −
7    8    9    +
0    00   .    =
```

## 液晶

```
[■+] M E                 [メモリ表示 小 10 桁]
[-]  8.8.8.8.8.8.8.8.8.8.   ← メイン 10 桁（太め・縦長）
```

- 左上: 保留中の四則演算子（+ − × ÷）を黒地に白の反転表示。演算子を押してから = / CA / E まで点灯。消灯時は薄いゴースト
- M / E インジケータ
- 右上: メモリの値（小さい 7 セグ）。メモリ 0 のときは消灯（ゴーストのみ）

## 計算仕様（`Calculator.java`、BigDecimal）

- 即時実行の連鎖: `2 + 3 × 4 =` → 20
- 定数計算: `=` を続けて押すと最後の演算を第 2 オペランドで繰り返す（`2+3===` → 5, 8, 11 / `2×3==` → 6, 18）。`5 × =` → 25（二乗）
- 結果は 10 桁に切り捨て、末尾の 0 は除去
- |結果| ≥ 10^10 → E（表示は ×10^-10 の値。実機と同じ）、0 除算・負の √ → E。E 中は CA / CE のみ受付
- 1/x: 表示値（入力中・結果どちらでも）を逆数に。10 桁に切り捨て、1/0 は E。√ と同様に第 2 オペランドにも使える（`5 + 1/x =` → 5.2）
- Ans.: 直近の `=` の結果を呼び出す（MR と同じく新しいオペランドになる）。`=` の連打（定数計算）と、保留中演算を完了させる M+/M- でも更新。連鎖計算の途中結果は入らない。CA では消えない。初期値 0、状態保存に含む
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
