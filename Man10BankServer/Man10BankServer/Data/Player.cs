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
    private static Player Empty => new("", "");

    private Player(string name, string uuid)
    {
        Name = name;
        Uuid = uuid;
    }

    public bool IsEmpty() => Name == "" && Uuid == "";
    
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

        try
        {
            using var response = await _client.GetAsync($"player/mcid?uuid={uuid}");
            if (response.StatusCode == HttpStatusCode.NotFound)
            {
                Console.WriteLine("存在しないプレイヤー");
                return Empty;
            }
        
            var name = await response.Content.ReadAsStringAsync();
            var newPlayer = new Player(name, uuid);
            PlayerSet.Add(newPlayer);
            return newPlayer;
        }
        //UserServerへの接続失敗
        catch (Exception)
        {
            Status.NowStatus.EnableAccessUserServer = false;
            return Empty;
        }
    } 
    public static async Task<Player> GetFromName(string name)
    {
        var cachePlayer = PlayerSet.FirstOrDefault(p => p.Name == name);
        if (cachePlayer!= null)
        {
            return cachePlayer;
        }

        try
        {
            using var response = await _client.GetAsync($"player/uuid?minecraftId={name}");
            if (response.StatusCode == HttpStatusCode.NotFound)
            {
                return Empty;
            }
            var uuid = await response.Content.ReadAsStringAsync();
            var newPlayer = new Player(name,uuid);
            PlayerSet.Add(newPlayer);
            return newPlayer;

        }
        catch (Exception)
        {
            Status.NowStatus.EnableAccessUserServer = false;
            return Empty;
        }
    }
}