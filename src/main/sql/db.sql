create table user_bank
(
	id int auto_increment,
	player varchar(16) not null,
	uuid varchar(36) not null,
	balance double default 0.0 not null,
	constraint user_bank_pk
		primary key (id)
);

create index user_bank_id_uuid_player_index
	on user_bank (id, uuid, player);

create table money_log
(
	id int auto_increment,
	player varchar(16) not null,
	uuid varchar(36) not null,
	plugin_name varchar(16) null,
	amount double default 0 not null,
	note varchar(64) null,
	server varchar(16) null,
	deposit boolean default true null,
	date datetime default now() not null,
	constraint money_log_pk
		primary key (id)
);

create index money_log_id_uuid_player_index
	on money_log (id, uuid, player);

create table loan_table
(
	id int auto_increment,
	lend_player varchar(16) null,
	lend_uuid varchar(36) null,
	borrow_player varchar(16) null,
	borrow_uuid varchar(36) null,
	borrow_date datetime default now() null,
	payback_date datetime null,
	amount double default 0.0 not null,
	constraint loan_table_pk
		primary key (id)
);

create index loan_table_player_uuid_index
	on loan_table (borrow_player, borrow_uuid);

create table atm_log
(
	id int auto_increment,
	player varchar(16) null,
	uuid varchar(36) null,
	amount double null,
	deposit boolean null,
	date datetime default now() null,
	constraint atm_log_pk
		primary key (id)
);

create table estate_tbl
(
    id int auto_increment,
    uuid varchar(36) not null,
    date datetime null,
    player varchar(16) null,
    vault double default 0 null,
    bank double default 0 null,
    estate double default 0 null,
    crypto double default 0 null,
    total double default 0 null,
    constraint estate_tbl_pk
        primary key (id)
)
comment '現在の個人の資産テーブル';

create index estate_tbl_uuid_index
    on estate_tbl (uuid);


create table estate_history_tbl
(
    id int auto_increment,
    uuid varchar(36) null,
    date datetime default now() null,
    player varchar(16) null,
    vault double default 0 null,
    bank double default 0 null,
    estate double default 0 null,
    crypto double default 0 null,
    total double default 0 null,
    constraint estate_history_tbl_pk
        primary key (id)
);


create table server_estate_history
(
	id int auto_increment,
	vault double null,
	bank double default 0 null,
	estate double default 0 null,
	crypto double default 0 null,
	total double default 0 null,
	year int null,
	month int null,
	day int null,
	hour int null,
	date datetime default now() null,
	constraint server_estate_history_pk
		primary key (id)
);

create index server_estate_history_year_month_day_hour_index
	on server_estate_history (year, month, day, hour);

