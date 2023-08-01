using Man10BankServer.Model;
using Microsoft.AspNetCore.Mvc;

namespace Man10BankServer.Common;

public static class User
{
    /// <summary>
    /// MinecraftIDを取得する
    /// </summary>
    /// <param name="uuid"></param>
    /// <returns></returns>
    public static async Task<string> GetMinecraftId(string uuid)
    {
        var response = await Score.Client.GetAsync($"{Score.SystemUrl}/player/mcid?uuid={uuid}");
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
        var response = await Score.Client.GetAsync($"{Score.SystemUrl}/player/uuid?minecraftId={mcid}");
        var body = await response.Content.ReadAsStringAsync();
        response.Dispose();
        return body;
    }

    public static async Task<string[]> GetIdSuggest(string mcid)
    {
        var result = await Task.Run(() =>
        {
            var context = new BankContext();
            var list = context.user_bank.Where(r => r.player.StartsWith(mcid)).Select(r => r.player).ToArray();
            context.Dispose();
            return list;
        });

        return result;
    }
}