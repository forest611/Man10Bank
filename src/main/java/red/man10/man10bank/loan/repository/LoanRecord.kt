package red.man10.man10bank.loan.repository

import java.util.Date
import java.util.UUID

data class LoanRecord(
    val uuid: UUID,
    val borrowAmount: Double,
    val paymentAmount: Double,
    val lastPayDate: Date,
    val failedPayment: Int
)
