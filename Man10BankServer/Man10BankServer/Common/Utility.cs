using Man10BankServer.Model;

namespace Man10BankServer.Common;

public static class Utility
{
    
    public static IConfiguration? Config { get; set; }

    public static void LoadConfig(IConfiguration config) => Config = config;
    
    /// <summary>
    /// MinecraftIDを取得する
    /// </summary>
    /// <param name="uuid"></param>
    /// <returns></returns>
    public static async Task<string> GetMinecraftId(string uuid)
    {
        var result = await Task.Run(() =>
        {
            return "";
        });
        
        return result;
    }

    /// <summary>
    /// UUIDを取得する
    /// </summary>
    /// <param name="uuid"></param>
    /// <returns></returns>
    public static async Task<string> GetUUID(string mcid)
    {
        var result = await Task.Run(() =>
        {

            return ""; 
        });
        
        return result;
    }

    
    public static async Task<int?> GetScore(string uuid)
    {
        var result = await Task.Run(() =>
        {
            return 0;
        });
        return result;
    }

    /// <summary>
    /// そのうちスコア用のAPI鯖を作る
    /// </summary>
    /// <param name="uuid"></param>
    /// <param name="amount"></param>
    /// <returns></returns>
    public static async Task<bool> TakeScore(string uuid, int amount)
    {
        var result = await Task.Run(() =>
        {
            return true;
        });

        return result;
    }
}