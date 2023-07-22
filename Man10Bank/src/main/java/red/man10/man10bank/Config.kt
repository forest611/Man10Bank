package red.man10.man10bank

object Config {

    private const val API_URL = "API.URL"
    private const val DEBUG_MODE = "DebugMode"

    var debugMode = false
    var url = ""

    //      Configの読み込み
    fun load(){

        Man10Bank.instance.saveDefaultConfig()
        Man10Bank.instance.reloadConfig()

        debugMode = Man10Bank.instance.config.getBoolean(DEBUG_MODE)
        url = Man10Bank.instance.config.getString(API_URL)?:""
    }

}