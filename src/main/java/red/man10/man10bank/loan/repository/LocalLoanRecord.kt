package red.man10.man10bank.loan.repository

import java.util.Date
import java.util.UUID

data class LocalLoanRecord(
    val id: Int,
    val lendUUID: UUID,
    val borrowUUID: UUID,
    val paybackDate: Date,
    var amount: Double
)
