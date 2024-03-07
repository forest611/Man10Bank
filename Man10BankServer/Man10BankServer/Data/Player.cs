using System.Collections.Concurrent;
using System.Net;
using Man10BankServer.Common;

namespace Man10BankServer.Data;

public class Player
{
    public string Name { get; }
    public string Uuid { get; }

    private static HttpClient _client = new() {BaseAddress = new Uri(Configuration.Man10SystemUrl)};
    private static readonly HashSet<Player> PlayerSet = new();

    private Player(string name, string uuid)
    {
        Name = name;
        Uuid = uuid;
    }
    
    public static void SetTestHttpClient(HttpClient client)
    {
        _client = client;
    }

    public static async Task<Player> GetFromUuid(string uuid)
    {
        var cachePlayer = PlayerSet.FirstOrDefault(p => p.Uuid == uuid);
        if (cachePlayer!= null)
        {
            return cachePlayer;
        }
        using var response = await _client.GetAsync($"player/mcid?uuid={uuid}");
        if (response.StatusCode == HttpStatusCode.NotFound)
        {
            throw new Exception("プレイヤーが存在しません");
        }
        var body = await response.Content.ReadAsStringAsync();
        var newPlayer = new Player(body, uuid);
        PlayerSet.Add(newPlayer);
        return newPlayer;
    } 
    public static async Task<Player> GetFromName(string name)
    {
        var cachePlayer = PlayerSet.FirstOrDefault(p => p.Name == name);
        if (cachePlayer!= null)
        {
            return cachePlayer;
        }
        using var response = await _client.GetAsync($"player/uuid?minecraftId={name}");
        if (response.StatusCode == HttpStatusCode.NotFound)
        {
            throw new Exception("プレイヤーが存在しません");
        }
        var body = await response.Content.ReadAsStringAsync();
        var newPlayer = new Player(name,body);
        PlayerSet.Add(newPlayer);
        return newPlayer;
    }
}