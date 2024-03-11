using System.Net;
using System.Text;
using System.Text.Json;

namespace Man10BankServer.Common;

public static class Score
{
    private static HttpClient _client = new() {BaseAddress = new Uri(Configuration.Man10SystemUrl)};
    private const string ConsoleIssuer = "CONSOLE";

    /// <summary>
    /// テスト用にHttpClientを変えるコード
    /// </summary>
    /// <param name="client"></param>
    public static void SetTestHttpClient(HttpClient client)
    {
        _client = client;
    }
    
    public static async Task<int> GetScore(string uuid)
    {
        try
        {
            using var response = await _client.GetAsync($"Score/get?uuid={uuid}");
            var body = await response.Content.ReadAsStringAsync();
            return int.Parse(body);

        }
        catch (Exception)
        {
            Status.NowStatus.EnableAccessUserServer = false;
            return 0;
        }
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

        try
        {
            using var content = new StringContent(JsonSerializer.Serialize(data),Encoding.UTF8);
            await _client.PostAsync("Score/take",content);
        }
        catch (Exception)
        {
            Status.NowStatus.EnableAccessUserServer = false;
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