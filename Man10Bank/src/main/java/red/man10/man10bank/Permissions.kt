package red.man10.man10bank

object Permissions {


    //小切手
    const val USE_CHEQUE = "man10bank.cheque.use"
    const val ISSUE_CHEQUE = "man10bank.cheque.issue"
    const val ISSUE_CHEQUE_OP = "man10bank.cheque.issue_op"

    //システム
    const val RELOAD_SYSTEM = "man10bank.system.reload"

    //銀行
    const val BANK_OP_COMMAND = "man10bank.bank.op"
    const val BANK_USER_COMMAND = "man10bank.bank.user"
}