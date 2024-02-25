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
        var response = await Client.GetAsync($"{Configuration.Man10SystemUrl}/player/mcid?uuid={uuid}");
        var body = await response.Content.ReadAsStringAsync();
        return new Player(body, uuid);
    } 
    public static async Task<Player> GetFromName(string name)
    {
        var response = await Client.GetAsync($"{Configuration.Man10SystemUrl}/player/uuid?minecraftId={name}");
        var body = await response.Content.ReadAsStringAsync();
        return new Player(name, body);
    }
}