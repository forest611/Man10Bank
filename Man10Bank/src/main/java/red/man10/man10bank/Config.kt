package red.man10.man10bank

object Config {

    private const val API_URL = "API.URL"
    private const val DEBUG_MODE = "DebugMode"
    private const val STATUS_CHECK_SECONDS = "StatusCheckSeconds"

    var debugMode = false
    var url = ""
    var statusCheckSeconds = 0

    //      Configの読み込み
    fun load(){

        Man10Bank.instance.saveDefaultConfig()
        Man10Bank.instance.reloadConfig()

        debugMode = Man10Bank.instance.config.getBoolean(DEBUG_MODE)
        url = Man10Bank.instance.config.getString(API_URL)?:""
        statusCheckSeconds = Man10Bank.instance.config.getInt(STATUS_CHECK_SECONDS)
    }

}