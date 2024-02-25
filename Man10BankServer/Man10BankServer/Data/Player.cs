using System.Net;
using Man10BankServer.Common;

namespace Man10BankServer.Data;

public class Player
{
    public string Name { get; }
    public string Uuid { get; }

    private static readonly HttpClient Client = new();

    private Player(string name, string uuid)
    {
        Name = name;
        Uuid = uuid;
    }

    public static async Task<Player> GetFromUuid(string uuid)
    {
        using var response = await Client.GetAsync($"{Configuration.Man10SystemUrl}/player/mcid?uuid={uuid}");
        if (response.StatusCode == HttpStatusCode.NotFound)
        {
            throw new Exception("プレイヤーが存在しません");
        }
        var body = await response.Content.ReadAsStringAsync();
        return new Player(body, uuid);
    } 
    public static async Task<Player> GetFromName(string name)
    {
        using var response = await Client.GetAsync($"{Configuration.Man10SystemUrl}/player/uuid?minecraftId={name}");

        if (response.StatusCode == HttpStatusCode.NotFound)
        {
            throw new Exception("プレイヤーが存在しません");
        }
        var body = await response.Content.ReadAsStringAsync();
        return new Player(name, body);
    }
}