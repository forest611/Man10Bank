using System.Net;
using System.Text;
using System.Text.Json;

namespace Man10BankServer.Common;

public static class Score
{
    private static readonly HttpClient Client = new();
    private const string ConsoleIssuer = "CONSOLE";
    
    public static async Task<int> GetScore(string uuid)
    {
        using var response = await Client.GetAsync($"{Configuration.Man10SystemUrl}/Score/get?uuid={uuid}");
        var body = await response.Content.ReadAsStringAsync();
        return int.Parse(body);
    }

    /// <summary>
    /// スコアをひく
    /// </summary>
    /// <param name="uuid"></param>
    /// <param name="amount"></param>
    /// <param name="note"></param>
    /// <returns></returns>
    public static async Task TakeScore(string uuid, int amount, string note)
    {
        var data = new ScoreData
        {
            uuid = uuid,
            amount = amount,
            note = note,
            issuer = ConsoleIssuer
        };
        
        using var content = new StringContent(JsonSerializer.Serialize(data),Encoding.UTF8);
        await Client.PostAsync($"{Configuration.Man10SystemUrl}/Score/take",content);
    }



}
internal class ScoreData
{
    public string uuid { get; set; }
    public int amount { get; set; }
    public string note { get; set; }
    public string issuer { get; set; }
}