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
            var context = new SystemContext();
            var userName = context.player_data.FirstOrDefault(r => r.uuid == uuid)?.mcid;
            context.Dispose();
            return userName ?? "";
        });
        
        return result;
    }

    /// <summary>
    /// UUIDを取得する
    /// </summary>
    /// <param name="uuid"></param>
    /// <returns></returns>
    public static async Task<string> GetUUID(string mcid)
    {
        var result = await Task.Run(() =>
        {
            var context = new SystemContext();
            var userName = context.player_data.FirstOrDefault(r => r.mcid == mcid)?.uuid;
            context.Dispose();
            return userName ?? "";
        });
        
        return result;
    }

    
    public static async Task<int?> GetScore(string uuid)
    {
        var result = await Task.Run(() =>
        {
            var context = new SystemContext();
            var score = context.player_data.FirstOrDefault(r=>r.uuid==uuid)?.score??null;
            context.Dispose();
            return score;
        });
        return result;
    }

    /// <summary>
    /// そのうちスコア用のAPI鯖を作る
    /// </summary>
    /// <param name="uuid"></param>
    /// <param name="amount"></param>
    /// <returns></returns>
    public static async Task<bool> TakeScore(string uuid, int amount)
    {
        var result = await Task.Run(() =>
        {
            var context = new SystemContext();
            var data = context.player_data.FirstOrDefault(r => r.uuid == uuid);
            if (data == null)
            {
                context.Dispose();
                return false;
            }

            data.score -= amount;
            context.SaveChanges();
            context.Dispose();
            
            return true;            
        });

        return result;
    }
}