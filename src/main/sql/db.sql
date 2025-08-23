create table atm_log
(
    id      int auto_increment
        primary key,
    player  varchar(16)                        null,
    uuid    varchar(36)                        null,
    amount  double                             null,
    deposit tinyint(1)                         null,
    date    datetime default CURRENT_TIMESTAMP null
);

create table cheque_tbl
(
    id         int auto_increment
        primary key,
    player     varchar(16)       null,
    uuid       varchar(36)       null,
    amount     double            null,
    note       varchar(128)      null,
    date       datetime          null,
    use_date   datetime          null,
    use_player varchar(16)       null,
    used       tinyint default 0 null
);

create index cheque_tbl_used_index
    on cheque_tbl (used);

create table estate_history_tbl
(
    id     int auto_increment
        primary key,
    uuid   varchar(36)                        null,
    date   datetime default CURRENT_TIMESTAMP null,
    player varchar(16)                        null,
    vault  double   default 0                 null,
    bank   double   default 0                 null,
    cash   double   default 0                 null,
    estate double   default 0                 null,
    loan   double   default 0                 null,
    shop   double   default 0                 null,
    crypto double   default 0                 null,
    total  double   default 0                 null
);

create index estate_history_tbl_uuid_index
    on estate_history_tbl (uuid);

create table estate_tbl
(
    id     int auto_increment
        primary key,
    uuid   varchar(36)      not null,
    date   datetime         null,
    player varchar(16)      null,
    vault  double default 0 null,
    bank   double default 0 null,
    cash   double default 0 null,
    estate double default 0 null,
    loan   double default 0 null,
    shop   double default 0 null,
    crypto double default 0 null,
    total  double default 0 null
)
    comment '現在の個人の資産テーブル';

create index estate_tbl_uuid_index
    on estate_tbl (uuid);

create table loan_table
(
    id              int auto_increment
        primary key,
    lend_player     varchar(16)                        null,
    lend_uuid       varchar(36)                        null,
    borrow_player   varchar(16)                        null,
    borrow_uuid     varchar(36)                        null,
    borrow_date     datetime default CURRENT_TIMESTAMP null,
    payback_date    datetime                           null,
    amount          double   default 0                 not null,
    collateral_item text                               null
);

create index loan_table_player_uuid_index
    on loan_table (borrow_player, borrow_uuid);

create table money_log
(
    id           int auto_increment
        primary key,
    player       varchar(16)                          not null,
    uuid         varchar(36)                          not null,
    plugin_name  varchar(16)                          null,
    amount       double     default 0                 not null,
    note         varchar(64)                          null,
    display_note varchar(64)                          null,
    server       varchar(16)                          null,
    deposit      tinyint(1) default 1                 null,
    date         datetime   default CURRENT_TIMESTAMP not null
);

create index money_log_id_uuid_player_index
    on money_log (id, uuid, player);

create table server_estate_history
(
    id     int auto_increment
        primary key,
    vault  double                             null,
    bank   double   default 0                 null,
    cash   double   default 0                 null,
    estate double   default 0                 null,
    loan   double   default 0                 null,
    crypto double   default 0                 null,
    shop   double   default 0                 null,
    total  double   default 0                 null,
    year   int                                null,
    month  int                                null,
    day    int                                null,
    hour   int                                null,
    date   datetime default CURRENT_TIMESTAMP null
);

create index server_estate_history_year_month_day_hour_index
    on server_estate_history (year, month, day, hour);

create table server_loan_tbl
(
    id             int auto_increment
        primary key,
    player         varchar(16)                        null comment '借りたプレイヤー',
    uuid           varchar(36)                        null,
    borrow_date    datetime default CURRENT_TIMESTAMP null comment '借りた日
',
    last_pay_date  datetime default CURRENT_TIMESTAMP null comment '最後に支払った日
',
    borrow_amount  double                             null comment '借りた金額の合計',
    payment_amount double                             null comment '週ごとの支払額',
    failed_payment int      default 0                 null comment '支払いに失敗した回数',
    stop_interest  tinyint  default 0                 null comment '利息をたすかどうか'
);

create index server_loan_tbl_uuid_borrow_amount_index
    on server_loan_tbl (uuid, borrow_amount);

create table user_bank
(
    id      int auto_increment
        primary key,
    player  varchar(16)      not null,
    uuid    varchar(36)      not null,
    balance double default 0 not null
);

create index user_bank_id_uuid_player_index
    on user_bank (id, uuid, player);

