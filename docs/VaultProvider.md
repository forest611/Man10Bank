# VaultProvider 設計書

Man10Bank を **Vault(Economy) の Provider** 化し、プレイヤーの電子マネーを
`Man10BankService`（C# / MySQL）の `user_vault` で一元管理する。

本設計では、外部ショップなどが使う既存 Vault API の **同期制約** と、
Man10BankService を唯一の真実（source of truth）にする **非同期・権威更新** を分離して扱う。

- 対象プラグイン: [`src/main/java/red/man10/man10bank`](../src/main/java/red/man10/man10bank)
- 対象サービス: [`man10bankservice/Man10BankService`](../../man10bankservice/Man10BankService)
- 関連: [`BankAPI.md`](./BankAPI.md)（既存の Bank=銀行残高 API 仕様）

> 用語注: Bukkit の経済連携基盤は「Vault(Economy) / Vault API」と表記する。
> プレイヤーが直接使える残高は「電子マネー」、DB/コード上の識別子は `vault`
> （例: `user_vault`）で統一する。

> 本書は設計の合意形成用ドラフト。データモデルと名称は維持し、アーキテクチャと整合性モデルを
> 本文の内容へ置き換える。

---

## 目次

- [1. 方針](#1-方針)
- [2. 用語](#2-用語)
- [3. 全体アーキテクチャ](#3-全体アーキテクチャ)
- [4. 取引経路](#4-取引経路)
- [5. 整合性モデル](#5-整合性モデル)
- [6. Provider キャッシュ](#6-provider-キャッシュ)
- [7. VaultService](#7-vaultservice)
- [8. Man10BankService API](#8-man10bankservice-api)
- [9. データモデル](#9-データモデル)
- [10. プラグイン側コンポーネント](#10-プラグイン側コンポーネント)
- [11. 既存処理への影響](#11-既存処理への影響)
- [12. 金額・型の扱い](#12-金額型の扱い)
- [13. 障害・エッジケース](#13-障害エッジケース)
- [14. セキュリティ](#14-セキュリティ)
- [15. 既知のリスク](#15-既知のリスク)

---

## 1. 方針

現状の Man10Bank は Vault の Consumer として外部 Economy Provider を呼んでいる。
本設計では向きを反転し、Man10Bank が `net.milkbowl.vault.economy.Economy` を実装して
Vault Provider になる。

### 確定方針

| 論点 | 方針 |
|---|---|
| 電子マネーの実体 | `Man10BankService` の `user_vault` を確定残高の唯一の真実（source of truth）にする。在席サーバーのローカル Vault 台帳は、受付済みで未確定の操作（減算予約）についてだけ権威を持つ。 |
| Vault Provider の制約 | Milkbowl/Vault 経由の Economy 操作は同期 API。メインスレッド呼び出しはその場で処理し、off-main 呼び出しはメインスレッドへ同期ディスパッチする。Provider 内で HTTP を待たない。 |
| 外部ショップ経路 | 外部ショップなど既製 Vault API しか使えないプラグインだけが `Man10BankProvider` を通る。同期応答は Provider キャッシュで成立させる。 |
| 内製経路 | `/pay`、`/deposit`、`/withdraw`、ATM、Man10 系内製プラグインは Vault API を直接呼ばず、`Man10BankAPI -> VaultService -> Man10BankService` の非同期経路を使う。 |
| 送金コマンド | `/pay` は同一 Paper 上でオンラインのプレイヤー間の電子マネー送金、`/mpay` は Bank 残高間の送金に固定する。両者の資産種別を混在させない。 |
| 操作の発行元 | すべての vault 書き込みは、対象プレイヤーが在席する Paper サーバーだけが発行する（単一書き込み者）。別 Paper 在席・完全オフラインの対象への操作は入金を含め拒否し、代替には既存 Bank 機能を使う。 |
| 収束責務 | `Man10BankProvider` は同期互換用のキャッシュを持つ。書き込み者が在席サーバーだけになるため、`VaultService` は session claim 時のロード、確定応答、定期再同期（自己修復）で Provider キャッシュを真実へ収束させる。他サーバー発の変更を伝える残高 push は持たない。 |
| 対応 API 範囲 | 旧 Vault Economy（単一通貨・`double`）のみ。VaultUnlocked / 多通貨は対象外。 |
| 金額規則 | 小数金額は整数円へ切り捨てる。残高上限は Man10BankService の設定値を権威とし、既定値は 1 兆円。 |
| 既存電子マネー移行 | 別タスク。`user_vault` は初期値 0 を許容する。 |

---

## 2. 用語

| 用語 | 意味 |
|---|---|
| 電子マネー | プレイヤーが直接使える残高。Vault(Economy) の `getBalance` が返す値。DB 上は `user_vault`。 |
| 銀行残高 | `user_bank.Balance`。`/deposit` `/withdraw` で電子マネーと相互移動する既存の銀行残高。 |
| Man10BankProvider | `Economy` 実装。外部 Vault Consumer から同期で呼ばれる互換レイヤ。 |
| VaultService | プラグイン側の非同期サービス。Man10BankService への全 vault 書き込み、送信待ちキュー、再同期、Provider キャッシュ収束を担当する。 |
| Man10BankAPI | 内製プラグイン向けの公開 API。Vault API ではなく VaultService を呼ぶ。 |
| 在席サーバー | 対象プレイヤーが現在ログインしている Paper サーバー。Man10BankService の session claim で管理する。 |
| 単一書き込み者 | 対象プレイヤーの vault 書き込みを発行できる唯一の主体。有効な session claim を持つ在席サーバーだけがこれになる。 |
| ローカル Vault 台帳 | VaultService が管理するオンラインプレイヤーのローカル残高台帳。外部 Provider 経路と内製 API 経路の未確定差分を同じ場所に予約する。 |
| Provider キャッシュ | Man10BankProvider が同期応答に使うローカルの参照用残高。実体はローカル Vault 台帳で、VaultService が更新・収束させる。 |
| `availableBalance` | ローカル Vault 台帳上で今使ってよい残高。`confirmedBalance + pendingDelta` で計算する。`pendingDelta` は `operationId` ごとの未確定減算予約から計算する 0 以下の値とし、DB 未確定の入金は含めない。 |
| 送信待ちキュー | Provider が同期成功させた操作を、後で Man10BankService へ送るために一時保存するキュー。各操作は二重適用を防ぐための `operationId` を持つ。 |
| 唯一の真実 | `Man10BankService` が参照する DB の `user_vault`（確定残高の真実）。Provider キャッシュは真実ではなく従属する参照用データ。ただし受付済みで未確定の減算予約は、在席サーバーのローカル Vault 台帳だけが知っている。 |

---

## 3. 全体アーキテクチャ

```
外部ショップ等
  |
  | Vault API（同期 / メインスレッド）
  v
Vault
  |
  v
Man10BankProvider
  |  1. Provider キャッシュで同期判定
  |  2. 成功分を送信待ちキューへ登録
  v
VaultService（プラグイン側 / 非同期）
  |  - 送信待ちキューの処理
  |  - Man10BankService への REST
  |  - session claim / 定期再同期
  |  - Provider キャッシュ収束
  v
Man10BankService（C# / Web API）
  |
  v
MySQL user_vault（唯一の真実）
```

内製プラグインと Man10Bank のコマンドは Provider を迂回する。

```
/pay, /deposit, /withdraw, ATM, 内製プラグイン
  |
  v
Man10BankAPI（非同期 API）
  |
  v
VaultService（プラグイン側）
  |
  v
Man10BankService
  |
  v
user_vault / user_bank
```

### 責務境界

| コンポーネント | 責務 | やらないこと |
|---|---|---|
| Man10BankProvider | Vault 互換の同期応答、Provider キャッシュの読み書き、送信待ちキューへ登録できるかの同期判定 | HTTP 待ち、DB 確定待ち、内製コマンド処理 |
| VaultService | Man10BankService との通信、送信待ちキューの処理、内製 API 操作のローカル予約、確定応答処理、session claim / 定期再同期、Provider キャッシュ収束 | Vault API 互換の同期契約そのもの |
| Man10BankAPI | 内製プラグイン向けの非同期 vault API | Vault の同期 API 互換 |
| Man10BankService | `user_vault` の原子的更新、`vault_log`、冪等制御、`user_bank` との 1 Tx 移動 | Paper メインスレッド都合の吸収 |

---

## 4. 取引経路

### 4.1 外部ショップなど既製 Vault API 経路

対象: ChestShop など、既存の Vault Economy API しか呼べない外部プラグイン。

```
外部ショップ
  -> Vault
  -> Man10BankProvider（同期）
  -> VaultService（非同期）
  -> Man10BankService
```

処理:

1. 外部プラグインが `withdrawPlayer` / `depositPlayer` / `getBalance` / `has` を同期で呼ぶ。
2. Man10BankProvider は Provider キャッシュだけを見て同期的に結果を返す。
3. `withdrawPlayer` / `depositPlayer` が成功した場合、Provider は操作を送信待ちキューに登録する。
4. VaultService が送信待ちキューの操作を順番に処理し、Man10BankService へ冪等キー付きで送信する。
5. Man10BankService の確定応答（自サーバー発リクエストの結果）により VaultService が Provider キャッシュを収束させる。

この経路の同期成功は「Provider キャッシュ上で取引が成立し、送信待ちキューに登録された」ことを意味する。
「Man10BankService でコミット済み」を意味しない。DB 確定は後段の VaultService が担う。
`depositPlayer` が `SUCCESS` を返しても、DB 確定前の入金額は Provider キャッシュ、`visibleBalance`、
`availableBalance` のいずれにも加算しない。Man10BankService の更新完了後に初めて確定残高として反映する。

### 4.2 内製 API 経路

対象:

- `/pay`
- `/deposit`
- `/withdraw`
- ATM
- Man10 系内製プラグイン
- 今後追加する Man10Bank 連携機能

```
プラグイン / コマンド
  -> Man10BankAPI
  -> VaultService
  -> Man10BankService
```

処理の入口:

1. 呼び出し側は非同期 API として VaultService に取引を依頼する。
2. VaultService は、対象 UUID が自サーバーに在席しているかを確認する。
3. 対象の在席状態に応じて、次のいずれかの経路に分岐する。

ここでいう「残高を減らす」「残高を増やす」は、電子マネーである `user_vault` の増減を基準にする。
Man10Bank コマンド名では、`/deposit` は `user_vault -> user_bank` なので電子マネーを減らす操作、
`/withdraw` は `user_bank -> user_vault` なので電子マネーを増やす操作として扱う。
複数プレイヤーを扱う `/pay` はこの単独 UUID の分岐とは別に、送金元と送金先の両方が
同一の自サーバー session 上に在席していることを確認できた場合だけ実行する。

#### 対象が自サーバーに在席している場合

1. 自サーバーの VaultService が対象 UUID のローカル Vault 台帳をロックする。
2. 残高を減らす操作なら、Man10BankService へ送る前に未確定差分として予約する。
3. 予約により `availableBalance` が減るため、同時に来た外部 Vault 経路も同じ減算後の残高を見る。
4. VaultService は Man10BankService の確定応答を待つ。
5. 成功時のみ呼び出し側へ成功を返す。
6. 成功時は確定残高で Provider キャッシュを更新し、失敗時は予約を取り消して必要なら権威残高で再同期する。

#### 対象が別 Paper に在席している場合

増額・減額を問わず拒否する。在席サーバーのローカル Vault 台帳を経由しない書き込みは
単一書き込み者の原則を壊し、在席サーバーのキャッシュと DB の乖離（と、それを収束させる
残高 push の仕組み）を要求するため、経路として持たない。必要な付与は既存 Bank 機能で行う。

#### 対象がオフラインの場合

1. 外部 Vault Provider 経路は対象未ロードとして `FAILURE`。
2. 内製 API 経路も、増額（`deposit`）を含むすべての vault 操作を拒否する。
3. オフラインプレイヤーへの付与・回収・補償は、既存実装済みの Bank 機能（銀行残高）で行う。
   電子マネーは「オンライン中のプレイヤーだけが持つ財布」、銀行残高は「オフラインでも操作できる口座」として役割を分離する。

この経路は Man10BankService のコミット結果を待てるため、Provider の同期成功扱いは使わない。
ただし外部 Vault 経路との二重引き落としを防ぐため、残高を減らす内製操作は必ずローカル Vault 台帳へ先に予約する。
内製コードから `Vault.getProvider(Economy)` を取得して自分自身の Provider を叩く実装は禁止する。

### 4.3 経路選択ルール

| ケース | 経路 |
|---|---|
| 外部ショップなど、Vault API しか使えない既製プラグイン | Vault -> Man10BankProvider |
| Man10Bank の `/pay` | 同一 Paper 上でオンラインのプレイヤー間だけ、Man10BankAPI -> VaultService。電子マネー -> 電子マネー |
| Man10Bank の `/mpay` | 既存 BankService。Bank -> Bank。VaultService の対象外 |
| Man10Bank の `/deposit` `/withdraw` | Man10BankAPI -> VaultService -> Man10BankService の move API |
| ATM の現金 <-> 電子マネー | Man10BankAPI / VaultService。Vault API は使わない |
| 内製プラグインの電子マネー操作 | Man10BankAPI。Vault API は使わない |
| 管理者の set/edit | Man10BankAPI -> VaultService -> Man10BankService。対象が自サーバーに在席する場合のみ |
| オフライン・別 Paper 在席プレイヤーへの操作 | 拒否。既存 Bank API（銀行残高）を使う |

---

## 5. 整合性モデル

### 5.1 2 種類の整合性

Vault の同期制約があるため、すべての経路で同じ整合性は提供しない。

| 経路 | 整合性 | 成功の意味 |
|---|---|---|
| 内製 API 経路 | 権威同期（authoritative async） | Man10BankService が `user_vault` をコミットした。 |
| 外部 Vault API 経路 | 同期互換のローカルコミット + 最終収束 | Provider キャッシュで成立し、送信待ちキューに登録された。DB には VaultService が後送する。 |

外部 Vault API 経路では、メインスレッドで HTTP を待てないため、完全な DB 同期コミットは提供しない。
代わりに、Provider キャッシュを「同期取引用の一時台帳」とし、VaultService が唯一の真実へ収束させる。

### 5.2 基本不変条件

1. `user_vault` が確定残高の最終的な唯一の真実。在席サーバーのローカル Vault 台帳は、受付済みで未確定の減算予約についてだけ権威を持つ。
2. Provider は HTTP / DB を同期的に待たない。
3. Provider が `SUCCESS` を返した操作は、必ず冪等キー付きで送信待ちキューに載せる。
4. VaultService だけが Man10BankService の vault 書き込み API を呼ぶ。
5. すべての vault 書き込みは、対象が自サーバーに在席している（有効な session claim を保持している）場合だけ許可する（単一書き込み者）。
6. 電子マネーを減らす操作は、ローカル Vault 台帳へ未確定差分を予約してから Man10BankService へ送る。
7. 別 Paper に在席しているオンラインプレイヤーへの操作は、増額・減額を問わず拒否する。
8. 完全オフラインプレイヤーへの vault 操作は、増額を含めすべて拒否する。オフラインの資産操作は既存 Bank 機能を使う。
9. 同一 UUID のローカル予約、Provider 書き込み、確定反映、予約取消は在席サーバーの VaultService が直列化する。
10. Provider キャッシュが未ロード、古い、競合中、または送信待ちキューが不健康な場合、Provider は新規書き込みを拒否する。
11. VaultService は session claim 時のロード、Man10BankService の確定残高、定期再同期を使って Provider キャッシュを収束させる。
12. 正の未確定差分は Provider キャッシュへ反映しない。入金は Man10BankService の DB 更新完了後にだけ `confirmedBalance`、`visibleBalance`、`availableBalance` を増やす。
13. `/pay` は送金元と送金先が同一 Paper 上でオンラインの場合だけ許可し、電子マネー以外の資産へは移動しない。Bank 間送金は `/mpay` が担う。

### 5.3 ローカル予約による二重引き落とし防止

外部 Vault 経路と内製 API 経路は、どちらも同じローカル Vault 台帳の `availableBalance` を見る。
`availableBalance` は次の計算値であり、独立して保存する値ではない。

```text
availableBalance = confirmedBalance + pendingDelta
```

`pendingDelta` は `operationId` ごとの未確定減算予約を合計した 0 以下の計算値とする。
Provider の `depositPlayer` や内製 API の入金は、送信中であっても正の `pendingDelta` を作らない。
したがって DB 未確定の入金を出金、`/pay`、`user_vault -> user_bank` に再利用できない。

そのため、電子マネーを減らす処理は次の順序を必須にする。

1. 対象 UUID のローカル Vault 台帳をロックする。
2. `availableBalance` を確認する。
3. 足りる場合だけ `operationId` ごとの未確定減算予約を追加し、`availableBalance` を即座に減らす。
4. 外部 Vault 経路はこの時点で `SUCCESS` を返し、送信待ちキューへ登録する。
5. 内製 API 経路はこの予約を保持したまま Man10BankService へ送信し、確定応答を待つ。
6. 成功時は確定残高で予約を消し込む。失敗時は予約を取り消し、必要なら権威残高で再同期する。

例: 残高 100,000 円のプレイヤーが、同時に `/pay 70,000` と外部ショップ購入 70,000 円を行う場合。

- `/pay` が先にローカル予約した場合、`availableBalance` は 30,000 円になる。外部ショップの `withdrawPlayer(70,000)` はキャッシュ不足で `FAILURE`。
- 外部ショップが先にローカル予約した場合、`availableBalance` は 30,000 円になる。`/pay` は VaultService の予約段階で不足として失敗。

この設計では、両方が同時に成功して合計 140,000 円を消費する状態をローカル側で作らない。

### 5.4 別 Paper 在席・オフラインプレイヤーへの操作

在席サーバー以外から `user_vault` を書き込む経路は持たない。減算は stale-high による過払いを
生むため論外として、増額も技術的には直接権威更新できるが、在席サーバーのキャッシュが stale になる
期間と、それを収束させる残高 push・不整合許容の複雑さを引き換えに要求する。本設計では
単一書き込み者の原則を優先し、増額側の経路も削除する。

| 操作 | 方針 |
|---|---|
| 別 Paper 在席プレイヤーへの増額・減額 | 拒否。 |
| 完全オフラインプレイヤーへの増額・減額・絶対値設定 | 拒否。 |
| 管理者 `set` / `give` / `take` | 対象が自サーバーに在席する場合だけ許可。 |
| オフライン・別 Paper 在席への付与・回収・補償 | 既存 Bank 機能（銀行残高）で行う。 |

この規則により、オンライン中の Provider キャッシュを在席サーバー以外が古くする書き込みは存在しなくなり、
在席サーバーのメモリと DB の乖離は「自サーバーが送信中の操作」だけに限定される。

### 5.5 外部 Vault API 経路の同期保証

Provider は以下を満たす場合だけ書き込み成功を返す。

- 対象プレイヤーの Provider キャッシュが `READY`。
- 対象プレイヤーがこのサーバー上で取引可能な状態。
- 金額が正の整数へ正規化できる。
- `withdraw` の場合、Provider キャッシュ上の `availableBalance >= amount`。
- 送信待ちキューが操作を受理できる。
- VaultService の書き込み健全性が `WRITE_READY`。
- Man10BankService との疎通が正常、または最後の成功確認から許容時間内。
- 未処理件数が閾値以下。

いずれかを満たさない場合は `EconomyResponse` を `FAILURE` にする。
外部ショップへ不確かな成功を返すより、取引を拒否することを優先する。

### 5.6 Man10BankService ダウン時の Provider 挙動

Man10BankProvider は同期メソッド内で Man10BankService へ疎通確認しない。
代わりに VaultService が非同期に health check / WebSocket heartbeat / 直近の送信結果を監視し、
Provider はそのスナップショットだけを見て同期的に成功可否を決める。

書き込み健全性:

| 状態 | Provider 書き込み | 説明 |
|---|---|---|
| `WRITE_READY` | 許可 | Man10BankService への疎通が正常、送信待ちキューが正常、未処理件数が閾値以下。 |
| `DEGRADED` | 原則拒否 | heartbeat 遅延、直近送信失敗、未処理件数増加など。外部 Vault 経路は安全側に倒して `FAILURE`。 |
| `DOWN` | 拒否 | Man10BankService 到達不能。`depositPlayer` / `withdrawPlayer` は即 `FAILURE`。 |
| `DRAINING` | 拒否 | サーバー移動 / shutdown / 復旧処理中。新規 Provider 書き込みは受けない。 |

つまり、Man10BankService が落ちていることを VaultService が検知済みなら、
外部ショップからの `withdrawPlayer` / `depositPlayer` は Provider キャッシュ残高に関係なく `FAILURE` になる。
`getBalance` はキャッシュ値を返してよいが、`has` は書き込み健全性が `WRITE_READY` でない場合 `false` に寄せる。
この拒否は `isEnabled()` の返値に依存させず、各取引メソッドが必ず書き込み健全性を検査して実施する。
`isEnabled()` は Provider が登録済みかつ `vault.providerEnabled = true` であることだけを表し、
`DEGRADED` / `DOWN` / `DRAINING` の一時状態では `true` を維持する。これにより、外部プラグインが
一時障害を恒久的な Provider 無効化と判断して接続を破棄することを避ける。復旧後は再登録なしで取引を再開する。

管理者設定またはプラグイン停止により Provider 自体を無効化する場合だけ `isEnabled() = false` とし、
ServicesManager から登録解除する。登録解除前の参照を保持する外部プラグインに備え、この状態では
`getBalance` は `0`、`has` / `hasAccount` / `createPlayerAccount` は `false`、入出金は `FAILURE` を返し、新規操作を受理しない。

通常時の Provider 取引は、同期処理内でメモリ上の送信待ちキューへ登録できれば `SUCCESS` を返す。
この時点で `operationId` は必ず発行するが、毎回ディスクへ永続保存することはしない。

Man10BankService の不調を検知した場合は、次の折衷案で扱う。

- ダウン検知後の新規 Provider 書き込みは `FAILURE`。
- すでに外部プラグインへ `SUCCESS` を返した未送信操作だけ、ローカル永続キューへ退避する。
- 保存先は Paper プラグインのデータフォルダ配下に置く（例: SQLite、または追記型ログファイル）。
- 復旧後、永続キューを確認し、同じ `operationId` で送信を再開する。
- Man10BankService で処理済みになった操作は、永続キューから削除するか `COMPLETED` にして後で掃除する。
- 通信失敗なら削除せず再送待ちにする。
- 業務失敗なら削除せず `CONFLICT` / `FAILED` として管理者確認対象にする。
- 長時間復旧しない場合、Provider キャッシュを `STALE` / `DISABLED` にして書き込みを止め、運用アラートを出す。
- 永続キューへの退避失敗、キューの `CONFLICT` / `FAILED` 化、正常 shutdown 時の未退避操作など、検知可能な異常は `severe` ログへ構造化して出力する。
- Paper の強制終了では消失したメモリ上の操作自体を再起動後に特定できないため、Provider 有効化時にこの許容リスクを `warning` で出力する。

このため、「ダウン検知済みの新規 Provider 取引」は `FAILURE` にできる。
一方で「`SUCCESS` 返却後にダウンした既存取引」は失敗へ変更できないため、永続キューへ退避して後送する。
ただし、Provider が `SUCCESS` を返した後、VaultService が永続キューへ退避する前に Paper プロセスがクラッシュした場合、
その取引は失われ得る。この折衷案は BankService ダウン対策であり、Paper クラッシュまで含めた完全保証ではない。
内製 API 経路は Man10BankService の確定応答を待つため、サービス到達不能なら呼び出し元へ失敗を返し、
この Provider 用のローカル永続キューには入れない。

### 5.7 成功後に DB 側で失敗した場合

通常は発生させない前提だが、次のような原因で起こり得る。

- Provider キャッシュが真実より高い状態で外部ショップが `withdraw` した。
- 旧 session からの書き込みと競合した。
- サーバー移動時の presence / lease が破綻した。
- バグまたは DB 手動変更があった。

外部 Vault API 経路は、外部ショップが既にアイテムを渡した後に失敗を知る可能性がある。
このため、単純なローカル巻き戻しだけでは補償にならない。

方針:

1. VaultService は対象アカウントの Provider 書き込みを `CONFLICT` にして一時停止する。
2. Man10BankService から権威残高を再取得し、Provider キャッシュを上書きする。
3. `severe` ログに `operationId`、uuid、amount、外部経路、失敗理由を構造化して残す。
4. 必要に応じて対象プレイヤー、またはサーバー全体の Vault Provider 書き込みを停止する。
5. 自動で外部ショップの成果物を取り消す処理は行わない。外部プラグイン固有であり汎用補償不能なため。

この状態を運用上の重大不整合として扱う。

### 5.8 サーバー移動と単一アクティブ Provider

同一プレイヤーに対して、同期 Vault 取引を受け付ける Provider キャッシュは同時に 1 つだけにする。

- join 時に VaultService が Man10BankService へ presence / session claim を送る。
- claim 成功後に `user_vault` をロードし、Provider キャッシュを `READY` にする。
- quit / kick / transfer 検知時は新規 Provider 書き込みを止め、送信待ちキューを処理してからキャッシュを破棄する。
- 別サーバーで同じ UUID の claim が来た場合、Man10BankService は後勝ちにして旧 session を失効させる。
- 旧 session からの後続書き込みを Man10BankService 側で拒否できるよう、すべての vault 書き込みに `sessionId` を含める。

presence / session は単一書き込み者を強制する要の仕組みであり、削れない。確定残高の唯一の真実は引き続き `user_vault`。

---

## 6. Provider キャッシュ

Provider キャッシュは Man10BankProvider が同期応答に使うローカル Vault 台帳の読み取り面。
所有と更新は VaultService 側に寄せ、外部 Provider 経路と内製 API 経路の未確定差分を同じ場所で管理する。

### 6.1 エントリ

```kotlin
data class VaultCacheEntry(
    val uuid: UUID,
    val confirmedBalance: Long,
    val confirmedVersion: Long,
    val pendingOperations: Map<String, PendingVaultOperation>, // operationId -> 未確定の減算予約
    val status: Status,
    val sessionId: String,
    val lastSyncedAtMillis: Long,
)

data class PendingVaultOperation(
    val operationId: String,
    val amount: Long, // 予約額（正の値）
    val source: PendingSource, // PROVIDER / MAN10_API
    val createdAtMillis: Long,
)
```

概念:

| 値 | 意味 |
|---|---|
| `confirmedBalance` | Man10BankService で確認済みの残高。 |
| `confirmedVersion` | `user_vault.Version`。古い再同期結果を捨てるために使う。 |
| `pendingOperations` | Provider 経路または内製 API 経路で予約済みだが、まだ Man10BankService で確定していない減算予約。`operationId` ごとに保持する。未確定の入金は含めない。 |
| `pendingDelta` | `pendingOperations` の予約額合計の符号反転（`-Σamount`）で都度計算する値。保存フィールドにはしない。常に 0 以下。 |
| `visibleBalance` | `confirmedBalance + pendingDelta`。`getBalance` が返す値。DB 未確定の入金は表示へ加えない。 |
| `availableBalance` | `confirmedBalance + pendingDelta`。外部 Provider 経路と内製 API 経路の `withdraw` / `/pay` / `user_vault -> user_bank` 可否判定に使う計算値。DB 未確定の入金は利用可能額へ加えない。 |
| `status` | `LOADING`（読み込み中）/ `READY`（取引可能）/ `STALE`（古い可能性あり）/ `DRAINING`（キュー処理中）/ `CONFLICT`（競合停止中）/ `DISABLED`（停止中）。 |

残高は内部では `Long`（円）で保持し、Vault 境界でだけ `Double` に変換する。
未確定の減算予約は必ず `operationId` ごとの `pendingOperations` として保持し、`pendingDelta` はその合計から
都度計算する。これにより複数の未確定操作の一部だけが成功・失敗した場合でも、該当する `operationId` だけを消し込める。
Provider 書き込みを許可するかどうかは、各エントリの `status` に加えて VaultService 全体の書き込み健全性
（`WRITE_READY` / `DEGRADED` / `DOWN` / `DRAINING`）も見る。
残高上限は session claim で Man10BankService から受け取った権威設定値を VaultService 全体で保持し、
Provider の同期判定に使う。上限値が未取得の間は書き込みを `FAILURE` にする。

### 6.2 同期 API の挙動

以下の処理はすべてメインスレッド上で実行する。外部プラグインが off-main から呼んだ場合、
Man10BankProvider は処理を Bukkit メインスレッドへ同期ディスパッチし、呼び出し元スレッドは結果が返るまで待つ。
HTTP や DB の完了を待つのではなく、Provider キャッシュと送信待ちキューの同期処理だけを待つ。

| Vault(Economy) メソッド | Provider の挙動 |
|---|---|
| `getBalance(player)` | `READY` なら `visibleBalance` を返す。未ロード時は `0` を返し、非同期ロードを要求する。 |
| `has(player, amount)` | 金額を整数円へ切り捨てて正規化し、`READY` かつ書き込み健全性が `WRITE_READY` かつ `availableBalance >= normalizedAmount`。正規化失敗、未ロード、古い可能性がある状態、サービス不健康時は `false`。 |
| `withdrawPlayer(player, amount)` | `READY`、書き込み健全性 `WRITE_READY`、金額正規化成功、残高十分、送信待ちキューへの登録成功なら `pendingDelta -= normalizedAmount` して `SUCCESS`。それ以外は `FAILURE`。 |
| `depositPlayer(player, amount)` | `READY`、書き込み健全性 `WRITE_READY`、金額正規化成功、送信待ちキューへの登録成功なら正規化後の整数額で `SUCCESS`。DB 確定までは Provider キャッシュ残高を増やさず、確定応答後に `confirmedBalance` を更新する。それ以外は `FAILURE`。 |
| `hasAccount(player)` | 対象が同一サーバーでオンラインかつ Provider キャッシュが `LOADING` または `READY` なら `true`。オフライン、解決不能、キャッシュなしは `false`。 |
| `createPlayerAccount(player)` | 同一サーバーのオンライン対象について VaultService の ensure / load 要求を受理できれば `true`。オフライン、解決不能、要求登録失敗は `false`。`true` は DB コミット済みを意味せず、`READY` になるまで金銭操作は失敗する。 |
| `isEnabled()` | Provider が ServicesManager に登録済みかつ `vault.providerEnabled = true` なら `true`。Service の `DEGRADED` / `DOWN` / `DRAINING` では `true` のままにし、取引メソッド側で書き込みを拒否する。 |
| `format(amount)` | 有限値の小数部を 0 方向へ切り捨て、3 桁カンマと `円` を付ける。例: `1234.9 -> "1,234円"`、`-1234.9 -> "-1,234円"`。NaN / Infinity は `"0円"`。 |
| bank 系 API | `hasBankSupport() = false`、bank 系は `NOT_IMPLEMENTED`。 |

### 6.3 overload / メタデータ

- 電子マネーは全 world 共通の単一通貨とする。world 引数付き overload は world 名を無視し、対応する通常版へ委譲する。
- `String` プレイヤー指定は Minecraft ID として扱い、同一サーバーで現在オンラインのプレイヤーだけを解決する。任意の名前からオフライン UUID を生成しない。
- `OfflinePlayer` 指定も、対象が同一サーバーで現在オンラインの場合だけ UUID を使って処理する。オフライン時は未ロード時の規則に従う。
- `currencyNameSingular()` と `currencyNamePlural()` はどちらも `"円"` を返す。
- `getName()` は `"Man10Bank"`、`fractionalDigits()` は `0`、`hasBankSupport()` は `false`。
- bank 系メソッドは `EconomyResponse.ResponseType.NOT_IMPLEMENTED`、`amount = 0`、`balance = 0` を返し、`getBanks()` は空リストを返す。

### 6.4 送信待ちキューの操作

Provider 書き込み成功時に作る操作:

```kotlin
data class VaultQueuedOperation(
    val operationId: String,
    val sessionId: String,
    val uuid: UUID,
    val type: Type, // PROVIDER_DEPOSIT / PROVIDER_WITHDRAW
    val amount: Long,
    val pluginName: String?,
    val reason: String?,
    val createdAtMillis: Long,
)
```

要件:

- `operationId` は UUID などの一意な冪等キー。同じ操作を二重適用しないために使う。
- Man10BankService 側で `operationId` を UNIQUE にし、重複送信を同じ結果として扱う。
- Provider は送信待ちキューへの登録に失敗した操作を `SUCCESS` にしてはならない。
- 通常時はメモリ上の送信待ちキューへ登録できれば `SUCCESS` を返す。
- 永続キューの対象は、Provider が外部プラグインへ `SUCCESS` を返した後、まだ Man10BankService へ確定送信できていない外部 Vault 取引のみ。
- Man10BankService の不調を検知したら、未送信のメモリキューをプラグインデータフォルダ配下の SQLite または追記型ログファイルへ退避する。
- 復旧後、永続キューを同じ `operationId` で処理し、成功した操作は削除または `COMPLETED` 化する。
- 送信待ちキューが詰まった場合、Provider キャッシュを `STALE` または `DISABLED` にして新規書き込みを止める。
- Paper クラッシュ時にメモリキューから永続キューへ退避できていない操作は失われ得る。これは本折衷案の既知リスクとして扱う。

### 6.5 収束

VaultService は次のイベントで Provider キャッシュを更新する。

| イベント | 更新内容 |
|---|---|
| 初回ロード / join claim 成功 | `confirmedBalance` / `confirmedVersion` をセットし、`READY` にする。 |
| キュー操作の確定成功 | 対応する Provider 未確定操作を外し、Man10BankService の返した確定残高・version を反映する。未確定の減算予約が残る場合は `pendingDelta` を再計算する。入金による増額もこの時点で初めて反映する。 |
| 内製 API 操作の確定成功 | 対応する内製 API の予約を外し、Man10BankService の返した確定残高・version を反映する。 |
| 内製 API 操作の失敗 | 対応する予約を取り消し、必要なら Man10BankService から権威残高を再取得する。 |
| 定期再同期（自己修復） | 権威残高を再取得し、`version` が新しければ `confirmedBalance` / `confirmedVersion` を更新して未確定の減算予約だけを再適用する。単一書き込み者の下では通常差分は出ないため、差分を検知したら乖離として `warning` ログを残す。 |
| 競合検知 | `CONFLICT` にして新規 Provider 書き込みを止め、手動確認できるログを残す。 |

古い再同期結果は `version` で捨てる。
未確定操作は `operationId` で管理し、HTTP タイムアウトや WebSocket 切断後も二重適用しない。

---

## 7. VaultService

プラグイン側の VaultService は電子マネー操作の単一窓口。
外部 Vault 互換経路と内製 API 経路の両方を受けるが、同期応答を返すのは Provider だけ。

### 7.1 主な責務

1. Provider 送信待ちキューの処理。
2. Man10BankService の `/api/Vault/*` 呼び出し。
3. `Man10BankAPI` からの非同期取引を実行し、確定結果を返す。
4. 在席していない対象（別 Paper 在席・完全オフライン）への vault API を、増額を含め拒否する。
5. join / quit / session claim。
6. 定期再同期による自己修復。
7. health check / WebSocket heartbeat / 直近送信結果による書き込み健全性の管理。
8. Provider キャッシュの `READY` / `STALE` / `CONFLICT` 管理。
9. Provider 書き込みを受け付けてよいかの健全性判定。

### 7.2 非同期 API

Man10BankAPI / コマンド / 内製プラグインは以下のような非同期メソッドだけを使う。

| メソッド | 用途 |
|---|---|
| `getBalance(uuid)` | Man10BankService から権威残高を取得。自サーバー在席なら Provider キャッシュも更新。 |
| `deposit(uuid, amount, reason)` | 電子マネーを権威入金。対象が自サーバーに在席する場合だけ許可。 |
| `withdraw(uuid, amount, reason)` | 電子マネーを権威出金。対象が自サーバーに在席する場合だけ許可し、先にローカル予約する。 |
| `transfer(from, to, amount, reason)` | `/pay`。送金元と送金先が同一の自サーバー上に在席している場合だけ実行する。電子マネー -> 電子マネー以外には使わない。 |
| `moveVaultToBank(uuid, amount, reason)` | `/deposit`。対象が自サーバーに在席する場合だけ、vault 側を先にローカル予約し `user_vault -> user_bank` を 1 Tx で移動。 |
| `moveBankToVault(uuid, amount, reason)` | `/withdraw`。対象が自サーバーに在席する場合だけ、`user_bank -> user_vault` を 1 Tx で移動。 |
| `setBalance(uuid, amount, reason)` | 管理者操作。対象が自サーバーに在席する場合だけ許可（増額・減額・絶対値とも）。 |

これらは Man10BankService の確定応答を待つ。
成功後、VaultService は確定残高+version で Provider キャッシュを補正する。

すべての操作は、対象が自サーバーに在席している（有効な session claim を保持している）ことを前提条件にする。
別 Paper 在席・完全オフラインの対象への操作は、増額を含めすべて拒否し、必要なら既存 Bank 機能を使う。
残高を減らす操作（`withdraw`、`transfer` の送金元、`moveVaultToBank`）は、Man10BankService へ送る前に
必ずローカル Vault 台帳で予約する。予約できない場合は、その時点で不足として失敗させる。
正の未確定差分はローカル Vault 台帳へ反映せず、Man10BankService の DB 更新完了後に確定残高を反映する。

### 7.3 Provider 送信待ちキューの処理

処理の流れ:

1. Provider が送信待ちキューに操作を追加する。
2. VaultService が登録順に操作を取得する。
3. Man10BankService へ `operationId` / `sessionId` / `uuid` / `amount` を送る。
4. 成功なら未確定操作から外し、返却された `balance` / `version` を Provider キャッシュへ反映する。
5. タイムアウトなら同じ `operationId` で再送する。
6. Man10BankService 不調を検知したら、未送信操作を永続キューへ退避し、新規 Provider 書き込みを止める。
7. 復旧後、永続キューを同じ `operationId` で再送し、成功した操作を削除または `COMPLETED` 化する。
8. 業務失敗なら `CONFLICT` として扱う。

POST は通常リトライしない方針だが、Provider のキュー操作は冪等キーが必須なので、
同一 `operationId` に限って再送できる。

### 7.4 同期チャネルと再同期

WebSocket は残高 push には使わず、session / presence チャネルとして使う。

- presence / session claim の維持と heartbeat。
- Man10BankService からの session 失効通知（別サーバーの後勝ち claim を受けた場合）。

残高の収束は「claim 時のロード」と「自サーバー発リクエストへの確定応答」だけで行う。
単一書き込み者の下では他サーバー発の変更が存在しないため、残高 push は不要になる。

安全網として低頻度の定期再同期（権威残高の再取得）を持ち、想定外の乖離（手動 DB 変更、バグ、
旧 session との競合）を検知・自己修復する。差分を検知した場合は `warning` ログを残す。
WebSocket 切断中は session heartbeat が途絶するため、書き込み健全性を落として fail-closed にする。
再接続時はオンラインプレイヤー全員を claim し直し、全件再同期する。

---

## 8. Man10BankService API

### 8.1 VaultController

`/api/Vault` を追加する。書き込みは `RequireWriteScope`。

| メソッド | 概要 | 返却 |
|---|---|---|
| `GET {uuid}/balance` | 電子マネー残高取得 | `{ balance, version }` |
| `GET {uuid}/logs` | 電子マネー取引ログ | `VaultLog[]` |
| `POST deposit` | 権威入金。内製 API と Provider 送信待ちキューの両方で使う。 | `{ balance, version }` |
| `POST withdraw` | 権威出金。不足時は `409`。 | `{ balance, version }` |
| `POST transfer` | 電子マネー -> 電子マネー。送金元・送金先が同一 Paper session 上でオンラインの `/pay` 用。 | from/to の残高・version |
| `POST move` | `user_vault` と `user_bank` の相互移動。`/deposit` `/withdraw` 用。ATM には使わない。 | vault/bank の残高 |
| `POST set` | 管理者用の絶対値設定。 | `{ balance, version }` |
| `POST session/claim` | Paper サーバーの player session claim。残高上限の権威設定も配布する。 | `{ sessionId, balance, version, maxBalance }` |
| `POST session/release` | session release。 | `204` |
| `GET session/{uuid}` | 対象 UUID の在席サーバー / session 状態を取得する。 | `{ server, sessionId, online }` |
| `GET ws` | presence / session 失効通知（残高 push は行わない）。 | WebSocket |

### 8.2 書き込み共通要件

- すべての書き込みは DB トランザクション内で行う。
- `user_vault` の対象行をロックし、残高不足や負数をサービス側で拒否する。
- 成功時に `version++`。
- `vault_log` に記録する。
- `operationId` が指定された場合は UNIQUE とし、同じ `operationId` の再送には同じ結果を返す。
- `sessionId` が指定された Provider キュー操作は、現在の claim と一致しなければ拒否する。
- 正規化後の操作金額が設定済みの `Vault:MaxBalance` を超える場合は拒否する。残高を増やす操作と絶対値設定は更新後残高も検証し、上限を超える場合は拒否する。
- すべての vault 書き込みに `sessionId` を必須とし、対象 UUID の現在の claim（在席サーバー・sessionId）と一致しなければ、増額・減額を問わず拒否する。
- 有効な claim が存在しない（完全オフラインの）対象への vault 書き込みは、この規則によりすべて拒否される。
- `transfer` は送金元・送金先の両方の claim が要求元サーバーと一致することを検証し、一致しなければ拒否する。
- オフラインプレイヤーの資産操作は既存 Bank API の責務であり、VaultController では扱わない。

---

## 9. データモデル

### 9.1 `user_vault`

名称と基本形は維持する。`user_bank` とは分離する。

```csharp
public class UserVault
{
    public int Id { get; set; }
    [StringLength(16)] public required string Player { get; set; }
    [StringLength(36)] public required string Uuid { get; set; }   // UNIQUE
    public decimal Balance { get; set; }
    public long Version { get; set; }
}
```

```sql
create table user_vault (
    id      int auto_increment primary key,
    player  varchar(16)   not null,
    uuid    varchar(36)   not null,
    balance decimal(20)   not null default 0,
    version bigint        not null default 0,
    unique key uq_user_vault_uuid (uuid)
);
```

### 9.2 `vault_log`

電子マネー専用ログ。銀行の `money_log` とは混ぜない。

追加で持つべき項目:

| 項目 | 用途 |
|---|---|
| `operation_id` | Provider 送信待ちキュー / Man10BankAPI の冪等キー。nullable でもよいが、指定時は UNIQUE。 |
| `source` | `PROVIDER` / `MAN10_API` / `ADMIN` / `SYSTEM`。 |
| `server` | 操作元 Paper サーバー名。 |
| `session_id` | Provider キュー操作の場合の session。 |
| `balance_after` | 操作後残高。監査と重複応答用。 |

### 9.3 session / presence

Provider キャッシュの単一アクティブ性を守るため、Man10BankService は短命の session を管理する。
DB 永続テーブルにするか、プロセス内メモリ + 再接続時の全件再同期にするかは実装時に選ぶ。

最低限必要な情報:

| 項目 | 用途 |
|---|---|
| `uuid` | 対象プレイヤー。 |
| `server` | claim した Paper サーバー。 |
| `session_id` | Provider キュー操作に付与する識別子。 |
| `expires_at` | ハートビート切れで失効させるための期限。 |

---

## 10. プラグイン側コンポーネント

| コンポーネント | 役割 |
|---|---|
| `economy/Man10BankProvider.kt` | `Economy` 実装。外部 Vault Consumer から呼ばれる同期互換レイヤ。 |
| `service/vault/VaultService.kt` | プラグイン側の非同期 vault サービス。Man10BankService への唯一の書き込み窓口。 |
| `service/vault/VaultProviderCache.kt` | Provider が読む同期キャッシュ。VaultService が収束更新する。 |
| `service/vault/VaultWriteQueue.kt` | Provider 成功操作の送信待ちキュー。冪等キーを管理する。 |
| `service/vault/VaultSyncClient.kt` | WebSocket session / presence / 失効通知 / 定期再同期。 |
| `api/VaultApiClient.kt` | `/api/Vault/*` REST クライアント。 |
| `Man10BankAPI.kt` | 内製プラグイン向けの非同期公開 API。 |
| `listener/VaultLifecycleListener.kt` | join/quit で claim、load、キュー処理、release を行う。 |

### 10.1 Economy 登録

```kotlin
server.servicesManager.register(
    Economy::class.java,
    man10BankProvider,
    this,
    ServicePriority.High
)
```

要件:

- `plugin.yml` は Vault より後にロードされるよう `softdepend: [Vault]` を維持する。
- 登録後に `ServicesManager.getRegistration(Economy)` の実効 Provider が自分自身か確認する。
- 競合 Provider が実効になった場合は `severe` ログを出し、Vault Provider 機能を停止する。
- 段階導入用に `vault.providerEnabled` で登録を切り替え可能にする。

### 10.2 スレッドモデル

| 処理 | スレッド |
|---|---|
| Man10BankProvider の全 `Economy` メソッド | メインスレッド同期。off-main から呼ばれた場合はメインスレッドへ同期ディスパッチして結果を返す。HTTP 待ちはしない。 |
| Provider キャッシュ操作 | メインスレッドへ直列化する。VaultService の確定応答、再同期による更新もメインスレッドへディスパッチして適用する。 |
| 送信待ちキューの処理 | `Dispatchers.IO`。 |
| Man10BankService REST | `Dispatchers.IO`。 |
| WebSocket session / 再同期 | `Dispatchers.IO`。 |
| コマンド結果のプレイヤー通知 | 必要に応じてメインスレッドへ戻す。 |

---

## 11. 既存処理への影響

### 11.1 `VaultManager` の置換

既存の [`VaultManager`](../src/main/java/red/man10/man10bank/service/VaultManager.kt) は外部 Economy Provider を取得する Consumer。
新設計では Man10Bank 自身が Provider になるため、`VaultManager` は外部 Economy Consumer ではなく
`VaultService` のファサードに作り替える。

方針:

- `VaultManager` 名は残し、既存の呼び出し側改修を最小化する。
- `hook()` / `provider()` のような外部 Economy Provider 取得前提の API は廃止または互換用 no-op にする。
- `getBalance` / `deposit` / `withdraw` / `isAvailable` は `VaultService` へ委譲する。
- 内製の `/deposit` `/withdraw` `/pay` / ATM は、最終的には `VaultService` / `Man10BankAPI` の非同期 API を使う。
- `VaultManager` から `ServicesManager.getRegistration(Economy)` で自分自身の Provider を取得して呼ぶ実装は禁止する。

### 11.2 `/deposit` `/withdraw`

現在は「Vault から引き落とし -> Bank API -> 失敗時補償」の Saga になっている。
新設計では `Man10BankService` の `POST /api/Vault/move` へ委譲し、
`user_vault` と `user_bank` を 1 DB トランザクションで更新する。

- `/deposit`: `user_vault -> user_bank`
- `/withdraw`: `user_bank -> user_vault`

対象プレイヤーが完全オフラインの場合、`/deposit` `/withdraw` はどちらも不可にする。
オフラインプレイヤーの資産操作は既存 Bank 機能を使う。

クライアント側の補償ロジックは削除する。

### 11.3 `/pay` / `/mpay`

電子マネー送金 `/pay` は `Man10BankAPI -> VaultService -> Man10BankService transfer`。
Provider は経由しない。
ただし送金元の電子マネーは、Man10BankService へ送る前に在席サーバーのローカル Vault 台帳で予約する。
これにより同時に外部ショップ購入が来ても、送金元残高を二重に使わない。

`/pay` は送金元と送金先が同一 Paper 上でオンラインの場合だけ成功させる。
送金先を UUID だけで解決して別 Paper 在席またはオフラインへ送る実装は禁止する。
送金する資産は送金元・送金先ともに電子マネー (`user_vault`) であり、Bank 残高との相互移動には使わない。
受取人の Provider キャッシュは Man10BankService の DB 更新完了後に増やす。

`/mpay` は既存の Bank -> Bank 送金コマンドとして維持し、VaultService / `user_vault` を経由させない。
コマンド名、対象資産、経路はこの区分で固定する。

### 11.4 ATM

ATM の現金 <-> 電子マネー変換は Vault API を呼ばず、VaultService の非同期 API を使う。
同一プレイヤーの ATM 操作は 1 件ずつ直列化し、確定待ちの間は対象 UI の再実行と対象アイテムの移動を禁止する。
Bukkit インベントリ操作はメインスレッド、VaultService と Man10BankService の呼び出しは非同期で行い、
結果を待ってからメインスレッドへ戻して次のアイテム操作を行う。

現金 -> 電子マネーは次の順序に固定する。

1. メインスレッドで対象現金アイテム、合計額、所有数を再検証する。
2. 現金アイテムを先に消費し、消費できたことを確定する。
3. VaultService の権威入金を呼び、Man10BankService の DB コミット結果を待つ。
4. DB 成功後に Provider キャッシュを増やし、ATM 操作を成功完了する。
5. DB が未コミットと確定できた失敗だけアイテム返却を試みる。タイムアウトなどコミット有無が不明な場合は自動返却せず、再照会対象として `severe` ログを残す。

電子マネー -> 現金は次の順序に固定する。

1. メインスレッドで現金アイテムを生成可能か、インベントリへ収容可能かを事前検証する。この時点では付与しない。
2. VaultService で電子マネーを予約して権威出金し、Man10BankService の DB コミット結果を待つ。
3. DB 成功後に Provider キャッシュへ確定残高を反映し、メインスレッドで現金アイテムを 1 回だけ付与する。
4. 付与できなかったことを確定できる全量または残量だけ、冪等キー付きの権威入金で返金を試みる。付与結果が不明な場合は再付与も自動返金も行わず、`severe` ログを残す。

クラッシュや結果不明時に消失と増殖のどちらかしか避けられない場合は、消失側へ倒す。
現金付与後の出金、DB 結果不明時のアイテム返却、付与結果不明時の再付与・自動返金は禁止する。

### 11.5 残高表示

`BalanceRegistry` の `id = "vault"` は維持する。
表示値は原則 Provider キャッシュ由来とし、キャッシュがない場合は VaultService に非同期ロードを依頼する。

---

## 12. 金額・型の扱い

- DB / サービス内部 / Provider キャッシュは整数円を `Long` または `decimal(20)` で扱う。
- Vault(Economy) の境界のみ `Double`。
- `fractionalDigits() = 0`。
- Vault API と内製 API の入力金額は、有限かつ正数であることを確認してから小数部を切り捨て、整数円へ正規化する。
- `0 < amount < 1` のように切り捨て後が 0 円になる金額、0 以下、NaN、Infinity は拒否する。
- 応答、`vault_log`、`operationId` に紐づく冪等結果には、要求時の小数値ではなく正規化後の整数額を使用する。
- 残高上限は Man10BankService の `Vault:MaxBalance` で設定可能とし、既定値を `1_000_000_000_000` 円（1 兆円）にする。
- Man10BankService を上限値の唯一の権威とし、session claim の `maxBalance` でオンライン Paper の VaultService / Provider へ配布する。Provider は上限未取得中の書き込みを拒否する。
- 正規化後の操作金額が上限を超える場合は、Provider と Man10BankService の双方で拒否する。
- deposit、transfer の受取人、Bank -> Vault の move、増額 set の更新後残高が上限を超える場合は拒否する。設定変更時点ですでに上限を超えている残高でも、残高を減らす操作は許可する。
- 設定値は 1 以上かつ IEEE 754 `Double` で整数を正確に表現できる `9_007_199_254_740_991` 以下に限定する。不正な設定では Man10BankService の Vault API を fail-closed で無効化し、`severe` ログを出す。
- `double` 同士の累積演算で残高を保持しない。

---

## 13. 障害・エッジケース

| ケース | 方針 |
|---|---|
| Provider キャッシュ未ロード | 書き込みは `FAILURE`。読みは `0` / `false` を返し、非同期ロードを要求する。 |
| Vault API の off-main 呼び出し | Bukkit メインスレッドへ同期ディスパッチし、Provider キャッシュと送信待ちキューの処理結果を呼び出し元へ返す。HTTP / DB は待たない。 |
| 小数金額 | 小数部を切り捨てて整数円として扱う。切り捨て後が 0 円なら拒否する。 |
| 残高上限 | Man10BankService の `Vault:MaxBalance` を権威とし、既定 1 兆円。操作金額、増額後残高、絶対値設定が超える場合は拒否する。既存の上限超過残高を減らす操作は許可する。 |
| 外部ショップ購入と内製 `/pay` が同時に残高を減らす | 両方が同じローカル Vault 台帳へ先に予約する。先に予約した方が `availableBalance` を減らすため、合計額が残高を超える場合は後続がローカル不足で失敗する。 |
| 別 Paper からオンライン中プレイヤーの残高を増減する | 増額・減額とも拒否する。vault の書き込み者は在席サーバーだけ（単一書き込み者）。 |
| DB 未確定の入金 | Provider キャッシュ、`visibleBalance`、`availableBalance` に加えない。Man10BankService の DB 更新完了後にだけ反映する。 |
| 完全オフラインプレイヤーへの内製 Vault 操作 | 増額を含めすべて拒否する。付与・回収・補償は既存 Bank 機能（銀行残高）を使う。 |
| Man10BankService 到達不能 | `isEnabled()` は `true` のまま、書き込み健全性を `DOWN` / `DEGRADED` にする。Provider の `depositPlayer` / `withdrawPlayer` / `has` は新規 `FAILURE` / `false`、`getBalance` はキャッシュ値。成功返却済みで未送信の Provider 操作は永続キューへ退避し、復旧後に同じ `operationId` で再送する。 |
| 送信待ちキューの未処理件数過多 | Provider 書き込みを止める。外部ショップには `FAILURE` を返す。 |
| Provider 成功後の service 失敗 | `CONFLICT`。自動補償せず、権威残高で収束し、重大ログを残す。 |
| HTTP タイムアウト | Provider キュー操作は同一 `operationId` で再送。内製 API は結果不明として呼び出し元へ失敗または再確認を返す。 |
| WebSocket 切断 | session heartbeat が途絶するため書き込み健全性を落とし fail-closed にする。再接続後にオンライン全員を claim し直し全件再同期。 |
| サーバークラッシュ | `user_vault` が真実。永続キューへ退避済みの操作は再起動後に同じ `operationId` で再送する。メモリキューにしか無かった Provider 成功返却済み操作は失われ得るため、既知リスクとして運用ログ・監視対象にする。 |
| ATM の現金 -> 電子マネー | 現金アイテムを確定消費してから DB 入金する。DB 結果不明時はアイテムを自動返却しない。 |
| ATM の電子マネー -> 現金 | DB 出金を確定してから現金アイテムを 1 回だけ付与する。付与結果不明時は再付与・自動返金をしない。 |
| 二重 Provider 登録 | 実効 Provider が自分でなければ Provider 機能を停止し、severe ログ。 |
| session mismatch | Man10BankService が Provider キュー操作を拒否し、VaultService は `CONFLICT` にする。 |
| 管理者 set / give / take | 対象が在席するサーバーで実行し、確定応答で Provider キャッシュへ反映する。オフライン・別 Paper 在席の対象は拒否（Bank で代替）。 |

---

## 14. セキュリティ

- REST 書き込みは既存の `RequireWriteScope` を使う。
- WebSocket / session claim も Bearer 認証を必須にする。
- Paper -> Man10BankService の API キーは `config.yml` / 環境変数管理とし、コミットしない。
- `serverName` / `sessionId` はクライアント自己申告だけを信用せず、認証情報またはサーバー登録情報と紐づける。
- `operationId`、`sessionId`、`serverName`、`source` は監査ログに残す。

---

## 15. 既知のリスク

### 15.1 許容する既知のリスク

- 外部 Vault API 経路は `SUCCESS` を返した時点では DB コミット済みではない。外部ショップが商品を渡した後に Man10BankService 側で失敗した場合、自動補償は汎用的にできない。このリスクは許容し、検知時は `operationId`、uuid、amount、呼び出し元、失敗理由を `severe` ログへ出力する。
- Provider が `SUCCESS` を返した後、未送信操作を永続キューへ退避する前に Paper プロセスがクラッシュすると、その取引は失われ得る。このリスクは許容する。退避失敗や正常 shutdown 時の未退避操作など検知可能な事象は `severe` ログへ出力し、Provider 有効化時にもこの動作モードを `warning` で通知する。強制終了で失われた操作そのものは事後に特定できない場合がある。

### 15.2 確定した設計判断

- vault の書き込み者は対象プレイヤーの在席サーバーだけとする（単一書き込み者）。別 Paper 在席・完全オフラインの対象への操作は増額を含め拒否し、オフライン・他サーバー在席への付与・回収・補償は既存 Bank 機能（銀行残高）で行う。
- 真実の階層は「DB `user_vault` = 確定残高の真実、在席サーバーのローカル Vault 台帳 = 受付済みで未確定の操作（減算予約）の権威」とする。
- 残高 push は持たない。Provider キャッシュの収束は claim 時ロード・確定応答・低頻度の定期再同期（自己修復）で行い、WebSocket は presence / session / 失効通知に使う。
- DB 未確定の正の差分は `availableBalance` と `visibleBalance` に含めず、Provider キャッシュも増やさない。Man10BankService の DB 更新完了後に確定残高として反映する。
- 既存電子マネーの移行は別タスクとし、現時点の Provider 設計では移行完了フラグや起動時 fail-closed を扱わない。
- `/pay` は同一 Paper 上でオンラインのプレイヤー間の電子マネー -> 電子マネー送金、`/mpay` は Bank -> Bank 送金に固定する。`/pay` による電子マネーと Bank 間の移動は実装しない。
- ATM は 11.4 の順序で Man10BankService の確定結果を待つ。結果不明時に消失と増殖の一方しか避けられない場合は消失を許容し、再付与・自動返金による増殖を防ぐ。
- 小数金額は整数円へ切り捨てて統一する。切り捨て後が 0 円になる入力は拒否する。
- Vault 残高上限は Man10BankService の `Vault:MaxBalance` で設定可能とし、既定値は 1 兆円にする。Service の設定値を権威として session claim で Provider へ配布する。
- 外部プラグインから Vault API が off-main で呼ばれた場合は、Bukkit メインスレッドへ同期ディスパッチする。Provider キャッシュの全更新もメインスレッドへ直列化する。
- Vault Economy の world 引数付き overload は world 名を無視し、全 world で同じ電子マネー残高を扱う。
- String 指定は同一サーバーでオンラインの Minecraft ID だけを解決し、任意のオフライン UUID は生成しない。
- `hasAccount` はオンラインかつキャッシュが `LOADING` / `READY` なら `true`。`createPlayerAccount` はオンライン対象の ensure / load 要求を受理できれば `true` とし、金銭操作は `READY` まで拒否する。
- `currencyNameSingular()` / `currencyNamePlural()` はどちらも `"円"` とする。
- `format(double)` は小数部を切り捨てて 3 桁カンマと `円` を付け、`1234.9` は `"1,234円"` と表示する。
- `isEnabled()` は Provider が登録済みかつ設定上有効なら `true` とし、一時的な Service 障害では `false` にしない。障害中の `has` は `false`、入出金は `FAILURE` とし、復旧後は再登録なしで再開する。

### 15.3 保留中・未解決のリスク

`*` が付いた項目は検討中として保留する。その他の項目も別途仕様判断が必要。

- *`operationId` を `vault_log` の UNIQUE だけで扱うと、`transfer` や `move` のような複数ログ・複数残高を返す操作の冪等応答が曖昧になる。必要ならログとは別に idempotency テーブルを用意する。
- *session / presence の保存先、lease 期限、heartbeat 間隔、Man10BankService 再起動時の扱いが未確定。単一アクティブ Provider の正しさに直結するため、実装前に固定する。
- *サーバー移動・kick・transfer・クラッシュ時に旧 session のキュー操作が拒否されると、外部プラグインには成功済みだが DB では失敗する状態になり得る。旧 session の drain 方針を明確にする必要がある。
- **現行の `user_bank` は UUID UNIQUE ではない。`user_vault` と `user_bank` を 1 Tx で扱う `move` を安全に実装するには、既存重複データの整理と bank 側の一意性・ロック戦略を確認する必要がある。
- **`VaultService` の `move` が `user_bank` を更新する場合、既存 `BankService.RunExclusiveAsync` と別経路で bank 更新を行うと競合し得る。既存 BankService の直列化キューに統合するか、行ロック順序を統一する必要がある。
- `serverName` / `sessionId` を自己申告だけで信用しない方針に対して、サーバー別 API key や登録済み server identity との紐づけ方式が未確定。認証と監査の設計に反映する必要がある。
