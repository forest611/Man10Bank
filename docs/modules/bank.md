# Bank モジュール実装記録

## 日付
- 2025-08-23

## 目的
- 残高管理（取得・設定・加減算）、入出金ログ記録、将来的な送金/ATM/ランキング基盤の整備。

## 対象スキーマ
- `user_bank(id, player, uuid, balance)`
- `money_log(id, player, uuid, plugin_name, amount, note, display_note, server, deposit, date)`

## 設計・方針
- 軽量ORM Ktorm（DSL）を使用。依存は最小限（Paper同梱のMySQLドライバ利用）。
- DB I/O はメインスレッド禁止。非同期で実行（後続でサービス層に集約）。
- 残高更新は当面Tx内の read-modify-write。将来 `UPDATE ... SET balance = balance + ?` の原子的更新に最適化可。
- 金額表示は `StringFormat.money(Number)` に統一（3桁カンマ＋整数のみ）。

## 実装・変更点（現状）
- `DatabaseProvider`: `config.yml` の `mysql` から接続初期化。
- テーブルDSL: `UserBank`, `MoneyLog` を定義。他テーブルもDSL化済み（今は未使用）。
- `BankRepository`:
  - `getBalanceByUuid(uuid)` / `setBalance(uuid, player, amount)`
  - `addBalance(uuid, player, delta)`（Txで加算/減算）
  - `logMoney(uuid, player, amount, deposit, pluginName?, note?, displayNote?, server?)`
- プラグイン起動で DB を初期化。

## 公開API（案）
- `deposit(uuid, player, amount)` / `withdraw(uuid, player, amount)`（残高 >= 0 を保証）
- `transfer(fromUuid, toUuid, amount)`（二相更新＋ログ）
- 例外は日本語メッセージで返す。失敗時はログ記録。

## テスト
- 単体: リポジトリの入出力境界をスタブ化し、サービス層を中心にテスト。
- 統合: 需要が出たら軽量の実DB/モックを検討（最小構成を維持）。

## TODO
- サービス層（BankService）実装と非同期ユーティリティ。
- `/deposit`, `/withdraw`, `/mpay` のコマンド実装。
- 残高更新の競合対策（原子的更新・リトライ戦略）。
- 入出金ログのバッチ/非同期投入最適化。
