using Man10BankServer.Model;

namespace Man10BankServer.Common;

public static class Utility
{
    /// <summary>
    /// MinecraftIDを取得する
    /// </summary>
    /// <param name="uuid"></param>
    /// <returns></returns>
    public static async Task<string> GetMinecraftId(string uuid)
    {
        var result = await Task.Run(() =>
        {
            var context = new Context();
            var userName = context.user_bank.FirstOrDefault(r => r.uuid == uuid)?.player;
            context.Dispose();
            return userName ?? "";
        });
        
        return result;
    }


    public static async Task<int> GetScore(string uuid)
    {
        var result = await Task.Run(() =>
        {
            var score = 0;

            return score;
        });
        return result;
    }

    public static async Task<bool> TakeScore(string uuid, double amount)
    {
        var result = await Task.Run(() =>
        {
            
            return true;            
        });

        return result;
    }
}