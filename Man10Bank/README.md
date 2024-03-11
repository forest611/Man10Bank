# コマンドリスト

## /bankstatus
- /bankstatus : プラグインの稼働状況をみる
- /bankstatus set <status> <true/false> : 各機能のon/off切り替え
- /bankstatus reload : システムリロード
## /atm
- /atm : ATMメニュを開く
- /atm register <金額> : 手に持ってるアイテムを通貨に設定する

## /bal /balance /bank /money
- /bal : 資産の表示
- /bal help : ユーザー向けヘルプの表示
- /bal log : 銀行の入出金ログ
- /bal bs : バランスシート
### OP
- /bal user <player> : 指定ユーザーの資産表示
- /bal logop : 指定ユーザーのログ
- /bal give <player> <amount> : 入金
- /bal take <player> <amount> : 出金
- /bal set <player> <amount> : 設定
## DEAL
- /deposit <amount/all> : vault->bank
- /withdraw <amount/all> : bank->vault
## /pay /mpay
- /pay <player> <amount> : 電子マネーを送る(オフラインなら銀行に振り込む)
- /mpay <player> <amount> : 銀行の残高を送る
## top
- /mbaltop : 所持金とっぷ
- /mloantop : 借キング
- /estateinfo : サーバーの資産状況
## /cheque
- /mcheque(op) <金額> <めも> : 小切手の発行(op付きは運営用)
## /mlend
- /mlend <player> <amount> <span> <interest> : 指定日数お金を貸す 金利は日利
- /mlend property : パラメータの確認
## /mrevo
- /mrevo check : 借りれる額を確認
- /mrevo borrow <amount> : お金を借りる
- /mrevo payment <amount> : 支払額を指定値に変更
- /mrevo pay <amount> : 指定額支払い(銀行)
- /mrevo payall : 一括返済
- /mrevo addtime <day>: 指定日数支払日を遅らせる
