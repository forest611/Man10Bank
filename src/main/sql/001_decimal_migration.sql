-- Man10Bank DECIMAL(20,0) migration
-- Generated at 2025-08-23T18:37:42+09:00
-- Strategy: add *_new DECIMAL(20,0) columns, copy with TRUNCATE(), drop old DOUBLE columns, rename *_new to original names.
-- Note: If NULLs are not desired, the optional lines fill NULL with 0.

START TRANSACTION;
SET FOREIGN_KEY_CHECKS = 0;

-- user_bank.balance
ALTER TABLE user_bank ADD COLUMN balance_new DECIMAL(20,0) NULL;
UPDATE user_bank SET balance_new = TRUNCATE(balance, 0);
UPDATE user_bank SET balance_new = 0 WHERE balance_new IS NULL; -- optional
ALTER TABLE user_bank DROP COLUMN balance;
ALTER TABLE user_bank CHANGE COLUMN balance_new balance DECIMAL(20,0) NOT NULL;

-- money_log.amount
ALTER TABLE money_log ADD COLUMN amount_new DECIMAL(20,0) NULL;
UPDATE money_log SET amount_new = TRUNCATE(amount, 0);
UPDATE money_log SET amount_new = 0 WHERE amount_new IS NULL; -- optional
ALTER TABLE money_log DROP COLUMN amount;
ALTER TABLE money_log CHANGE COLUMN amount_new amount DECIMAL(20,0) NOT NULL;

-- atm_log.amount
ALTER TABLE atm_log ADD COLUMN amount_new DECIMAL(20,0) NULL;
UPDATE atm_log SET amount_new = TRUNCATE(amount, 0);
UPDATE atm_log SET amount_new = 0 WHERE amount_new IS NULL; -- optional
ALTER TABLE atm_log DROP COLUMN amount;
ALTER TABLE atm_log CHANGE COLUMN amount_new amount DECIMAL(20,0) NOT NULL;

-- cheque_tbl.amount
ALTER TABLE cheque_tbl ADD COLUMN amount_new DECIMAL(20,0) NULL;
UPDATE cheque_tbl SET amount_new = TRUNCATE(amount, 0);
UPDATE cheque_tbl SET amount_new = 0 WHERE amount_new IS NULL; -- optional
ALTER TABLE cheque_tbl DROP COLUMN amount;
ALTER TABLE cheque_tbl CHANGE COLUMN amount_new amount DECIMAL(20,0) NOT NULL;

-- server_loan_tbl.borrow_amount
ALTER TABLE server_loan_tbl ADD COLUMN borrow_amount_new DECIMAL(20,0) NULL;
UPDATE server_loan_tbl SET borrow_amount_new = TRUNCATE(borrow_amount, 0);
UPDATE server_loan_tbl SET borrow_amount_new = 0 WHERE borrow_amount_new IS NULL; -- optional
ALTER TABLE server_loan_tbl DROP COLUMN borrow_amount;
ALTER TABLE server_loan_tbl CHANGE COLUMN borrow_amount_new borrow_amount DECIMAL(20,0) NOT NULL;

-- server_loan_tbl.payment_amount
ALTER TABLE server_loan_tbl ADD COLUMN payment_amount_new DECIMAL(20,0) NULL;
UPDATE server_loan_tbl SET payment_amount_new = TRUNCATE(payment_amount, 0);
UPDATE server_loan_tbl SET payment_amount_new = 0 WHERE payment_amount_new IS NULL; -- optional
ALTER TABLE server_loan_tbl DROP COLUMN payment_amount;
ALTER TABLE server_loan_tbl CHANGE COLUMN payment_amount_new payment_amount DECIMAL(20,0) NOT NULL;

-- loan_table.amount
ALTER TABLE loan_table ADD COLUMN amount_new DECIMAL(20,0) NULL;
UPDATE loan_table SET amount_new = TRUNCATE(amount, 0);
UPDATE loan_table SET amount_new = 0 WHERE amount_new IS NULL; -- optional
ALTER TABLE loan_table DROP COLUMN amount;
ALTER TABLE loan_table CHANGE COLUMN amount_new amount DECIMAL(20,0) NOT NULL;

-- estate_tbl.*
ALTER TABLE estate_tbl ADD COLUMN vault_new DECIMAL(20,0) NULL,
  ADD COLUMN bank_new DECIMAL(20,0) NULL,
  ADD COLUMN cash_new DECIMAL(20,0) NULL,
  ADD COLUMN estate_new DECIMAL(20,0) NULL,
  ADD COLUMN loan_new DECIMAL(20,0) NULL,
  ADD COLUMN shop_new DECIMAL(20,0) NULL,
  ADD COLUMN crypto_new DECIMAL(20,0) NULL,
  ADD COLUMN total_new DECIMAL(20,0) NULL;
UPDATE estate_tbl SET
  vault_new = TRUNCATE(vault, 0),
  bank_new = TRUNCATE(bank, 0),
  cash_new = TRUNCATE(cash, 0),
  estate_new = TRUNCATE(estate, 0),
  loan_new = TRUNCATE(loan, 0),
  shop_new = TRUNCATE(shop, 0),
  crypto_new = TRUNCATE(crypto, 0),
  total_new = TRUNCATE(total, 0);
UPDATE estate_tbl SET
  vault_new = IFNULL(vault_new, 0),
  bank_new = IFNULL(bank_new, 0),
  cash_new = IFNULL(cash_new, 0),
  estate_new = IFNULL(estate_new, 0),
  loan_new = IFNULL(loan_new, 0),
  shop_new = IFNULL(shop_new, 0),
  crypto_new = IFNULL(crypto_new, 0),
  total_new = IFNULL(total_new, 0);
ALTER TABLE estate_tbl
  DROP COLUMN vault,
  DROP COLUMN bank,
  DROP COLUMN cash,
  DROP COLUMN estate,
  DROP COLUMN loan,
  DROP COLUMN shop,
  DROP COLUMN crypto,
  DROP COLUMN total;
ALTER TABLE estate_tbl
  CHANGE COLUMN vault_new vault DECIMAL(20,0) NOT NULL,
  CHANGE COLUMN bank_new bank DECIMAL(20,0) NOT NULL,
  CHANGE COLUMN cash_new cash DECIMAL(20,0) NOT NULL,
  CHANGE COLUMN estate_new estate DECIMAL(20,0) NOT NULL,
  CHANGE COLUMN loan_new loan DECIMAL(20,0) NOT NULL,
  CHANGE COLUMN shop_new shop DECIMAL(20,0) NOT NULL,
  CHANGE COLUMN crypto_new crypto DECIMAL(20,0) NOT NULL,
  CHANGE COLUMN total_new total DECIMAL(20,0) NOT NULL;

-- estate_history_tbl.*
ALTER TABLE estate_history_tbl ADD COLUMN vault_new DECIMAL(20,0) NULL,
  ADD COLUMN bank_new DECIMAL(20,0) NULL,
  ADD COLUMN cash_new DECIMAL(20,0) NULL,
  ADD COLUMN estate_new DECIMAL(20,0) NULL,
  ADD COLUMN loan_new DECIMAL(20,0) NULL,
  ADD COLUMN shop_new DECIMAL(20,0) NULL,
  ADD COLUMN crypto_new DECIMAL(20,0) NULL,
  ADD COLUMN total_new DECIMAL(20,0) NULL;
UPDATE estate_history_tbl SET
  vault_new = TRUNCATE(vault, 0),
  bank_new = TRUNCATE(bank, 0),
  cash_new = TRUNCATE(cash, 0),
  estate_new = TRUNCATE(estate, 0),
  loan_new = TRUNCATE(loan, 0),
  shop_new = TRUNCATE(shop, 0),
  crypto_new = TRUNCATE(crypto, 0),
  total_new = TRUNCATE(total, 0);
UPDATE estate_history_tbl SET
  vault_new = IFNULL(vault_new, 0),
  bank_new = IFNULL(bank_new, 0),
  cash_new = IFNULL(cash_new, 0),
  estate_new = IFNULL(estate_new, 0),
  loan_new = IFNULL(loan_new, 0),
  shop_new = IFNULL(shop_new, 0),
  crypto_new = IFNULL(crypto_new, 0),
  total_new = IFNULL(total_new, 0);
ALTER TABLE estate_history_tbl
  DROP COLUMN vault,
  DROP COLUMN bank,
  DROP COLUMN cash,
  DROP COLUMN estate,
  DROP COLUMN loan,
  DROP COLUMN shop,
  DROP COLUMN crypto,
  DROP COLUMN total;
ALTER TABLE estate_history_tbl
  CHANGE COLUMN vault_new vault DECIMAL(20,0) NOT NULL,
  CHANGE COLUMN bank_new bank DECIMAL(20,0) NOT NULL,
  CHANGE COLUMN cash_new cash DECIMAL(20,0) NOT NULL,
  CHANGE COLUMN estate_new estate DECIMAL(20,0) NOT NULL,
  CHANGE COLUMN loan_new loan DECIMAL(20,0) NOT NULL,
  CHANGE COLUMN shop_new shop DECIMAL(20,0) NOT NULL,
  CHANGE COLUMN crypto_new crypto DECIMAL(20,0) NOT NULL,
  CHANGE COLUMN total_new total DECIMAL(20,0) NOT NULL;

-- server_estate_history.*
ALTER TABLE server_estate_history ADD COLUMN vault_new DECIMAL(20,0) NULL,
  ADD COLUMN bank_new DECIMAL(20,0) NULL,
  ADD COLUMN cash_new DECIMAL(20,0) NULL,
  ADD COLUMN estate_new DECIMAL(20,0) NULL,
  ADD COLUMN loan_new DECIMAL(20,0) NULL,
  ADD COLUMN shop_new DECIMAL(20,0) NULL,
  ADD COLUMN crypto_new DECIMAL(20,0) NULL,
  ADD COLUMN total_new DECIMAL(20,0) NULL;
UPDATE server_estate_history SET
  vault_new = TRUNCATE(vault, 0),
  bank_new = TRUNCATE(bank, 0),
  cash_new = TRUNCATE(cash, 0),
  estate_new = TRUNCATE(estate, 0),
  loan_new = TRUNCATE(loan, 0),
  shop_new = TRUNCATE(shop, 0),
  crypto_new = TRUNCATE(crypto, 0),
  total_new = TRUNCATE(total, 0);
UPDATE server_estate_history SET
  vault_new = IFNULL(vault_new, 0),
  bank_new = IFNULL(bank_new, 0),
  cash_new = IFNULL(cash_new, 0),
  estate_new = IFNULL(estate_new, 0),
  loan_new = IFNULL(loan_new, 0),
  shop_new = IFNULL(shop_new, 0),
  crypto_new = IFNULL(crypto_new, 0),
  total_new = IFNULL(total_new, 0);
ALTER TABLE server_estate_history
  DROP COLUMN vault,
  DROP COLUMN bank,
  DROP COLUMN cash,
  DROP COLUMN estate,
  DROP COLUMN loan,
  DROP COLUMN shop,
  DROP COLUMN crypto,
  DROP COLUMN total;
ALTER TABLE server_estate_history
  CHANGE COLUMN vault_new vault DECIMAL(20,0) NOT NULL,
  CHANGE COLUMN bank_new bank DECIMAL(20,0) NOT NULL,
  CHANGE COLUMN cash_new cash DECIMAL(20,0) NOT NULL,
  CHANGE COLUMN estate_new estate DECIMAL(20,0) NOT NULL,
  CHANGE COLUMN loan_new loan DECIMAL(20,0) NOT NULL,
  CHANGE COLUMN shop_new shop DECIMAL(20,0) NOT NULL,
  CHANGE COLUMN crypto_new crypto DECIMAL(20,0) NOT NULL,
  CHANGE COLUMN total_new total DECIMAL(20,0) NOT NULL;

SET FOREIGN_KEY_CHECKS = 1;
COMMIT;
