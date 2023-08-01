using System.Text;
using System.Text.Json;

namespace Man10BankServer.Common;

public static class Score
{
    
    public static IConfiguration? Config { get; set; }
    public static string SystemUrl { get; set; } = "";
    public static readonly HttpClient Client = new();
    
    
    public static void LoadConfig(IConfiguration config)
    {
        Config = config;
        SystemUrl = Config["Man10SystemUrl"]!;
        Thread.Sleep(1000);
        TryConnect();
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
            Console.WriteLine($"Man10System接続失敗");
            Console.WriteLine(e);
        }
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



}
internal class ScoreData
{
    public string uuid { get; set; }
    public int amount { get; set; }
    public string note { get; set; }
    public string issuer { get; set; }
}