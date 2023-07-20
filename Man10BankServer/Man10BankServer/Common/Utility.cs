using System.Text;
using System.Text.Json;

namespace Man10BankServer.Common;

public static class Utility
{
    
    public static IConfiguration? Config { get; set; }
    private static string SystemUrl { get; set; } = "";
    private static readonly HttpClient Client = new();
    
    
    public static void LoadConfig(IConfiguration config)
    {
        Config = config;
        SystemUrl = Config["Man10SystemUrl"]!;
        TryConnect();
    }

    /// <summary>
    /// MinecraftIDを取得する
    /// </summary>
    /// <param name="uuid"></param>
    /// <returns></returns>
    public static async Task<string> GetMinecraftId(string uuid)
    {
        var response = await Client.GetAsync($"{SystemUrl}/Player/mcid?uuid={uuid}");
        var body = await response.Content.ReadAsStringAsync();
        response.Dispose();
        return body;
    }

    /// <summary>
    /// UUIDを取得する
    /// </summary>
    /// <param name="uuid"></param>
    /// <returns></returns>
    public static async Task<string> GetUUID(string mcid)
    {
        var response = await Client.GetAsync($"{SystemUrl}/Player/uuid?minecraftId={mcid}");
        var body = await response.Content.ReadAsStringAsync();
        response.Dispose();
        return body;
    }

    
    public static async Task<int?> GetScore(string uuid)
    {
        var response = await Client.GetAsync($"{SystemUrl}/Score/get?uuid={uuid}");
        var body = await response.Content.ReadAsStringAsync();
        response.Dispose();
        return int.Parse(body);
    }

    /// <summary>
    /// スコアをひく
    /// </summary>
    /// <param name="uuid"></param>
    /// <param name="amount"></param>
    /// <param name="note"></param>
    /// <returns></returns>
    public static async Task<bool> TakeScore(string uuid, int amount,string note)
    {
        var data = new ScoreData
        {
            uuid = uuid,
            amount = amount,
            note = note,
            issuer = "CONSOLE"
        };
        
        var content = new StringContent(JsonSerializer.Serialize(data),Encoding.UTF8);
        var response = await Client.PostAsync($"{SystemUrl}/Score/take",content);
        response.Dispose();
        return (int)response.StatusCode == 200;
    }

    private static async void TryConnect()
    {
        try
        {
            var result = await Client.GetAsync($"{SystemUrl}/score/try");
            result.Dispose();
            Console.WriteLine((int)result.StatusCode == 200 ? "Man10System接続成功" : "Man10System接続失敗");
        }
        catch (Exception e)
        {
            Console.WriteLine($"Man10System接続失敗:{e}");
        }
    }

}
internal class ScoreData
{
    public string uuid { get; set; }
    public int amount { get; set; }
    public string note { get; set; }
    public string issuer { get; set; }
}