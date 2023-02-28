package red.man10.man10bank.api

import java.util.Date

object Model {

    data class MoneyLog(
        var id : Int,
        var player : String,
        var uuid : String,
        var plugin_name : String,
        var amount : Double,
        var note : String,
        var display_note : String,
        var server : String,
        var deposit : Boolean,
        var date : Date
    )

}