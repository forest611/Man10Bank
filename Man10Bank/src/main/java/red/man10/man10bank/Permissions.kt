package red.man10.man10bank

object Permissions {


    //小切手
    const val USE_CHEQUE = "man10bank.cheque.use"
    const val ISSUE_CHEQUE = "man10bank.cheque.issue"
    const val ISSUE_CHEQUE_OP = "man10bank.cheque.issue_op"

    //銀行
    const val BANK_OP_COMMAND = "man10bank.bank.op"
    const val BANK_USER_COMMAND = "man10bank.bank.user"

    //リボ
    const val SERVER_LOAN_USER = "man10bank.server_loan.user"
    const val SERVER_LOAN_OP = "man10bank.server_loan.op"
}