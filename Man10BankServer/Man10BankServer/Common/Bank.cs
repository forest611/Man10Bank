using System.Collections.Concurrent;
using Microsoft.OpenApi.Any;

namespace Man10BankServer.Common;

public class Bank
{

    private static readonly BlockingCollection<Action<Context>> BankQueue = new();
    
    public void StartMan10Bank()
    {
        Task.Run(BlockingQueue);
    }

    public static void GetBalance(string uuid,Action<double?> callback)
    {
        BankQueue.TryAdd(context =>
        {
            var balance = context.user_bank.FirstOrDefault(r => r.uuid == uuid)?.balance;
            callback.Invoke(balance);
        });
    }

    public static async Task<double?> AsyncGetBalance(string uuid)
    {

        double? result = null;
        
        GetBalance(uuid, balance =>
        {
            //ここの処理が終わるまで待機したい
            result = balance;
        });

        return result;
    }

    /// <summary>
    /// MinecraftIDを取得する
    /// </summary>
    /// <param name="uuid"></param>
    /// <returns></returns>
    public static string? GetMinecraftID(string uuid)
    {
        var context = new Context();
        var mcid = context.user_bank.FirstOrDefault(r => r.uuid == uuid)?.player;
        return mcid;
    }

    /// <summary>
    /// 口座を作る
    /// </summary>
    /// <param name="uuid"></param>
    public static void CreateBank(string uuid,string mcid)
    {
        BankQueue.TryAdd(context =>
        {
            //すでに口座が存在したらリターン
            if (context.user_bank.Any(r => r.uuid == uuid))
            {
                return;
            }
            
            var bank = new UserBank
            {
                balance = 0,
                uuid = uuid,
                player = mcid
            };
            context.user_bank.Add(bank);
            context.SaveChanges();
        });
    }
    
    /// <summary>
    /// 銀行残高を増やす
    /// </summary>
    /// <param name="uuid"></param>
    /// <param name="amount"></param>
    public static void AddBalance(string uuid, double amount,string plugin,string note,string displayNote)
    {
        BankQueue.TryAdd(context =>
        {
            var result = context.user_bank.FirstOrDefault(r => r.uuid == uuid);
            if (result == null)
            {
                return;
            }
            result.balance += amount;
            context.SaveChanges();
        });
    }

    /// <summary>
    /// 銀行残高を減らす
    /// </summary>
    /// <param name="uuid"></param>
    /// <param name="amount"></param>
    public static void TakeBalance(string uuid, double amount,string plugin,string note,string displayNote)
    {
        BankQueue.TryAdd(context =>
        {
            var result = context.user_bank.FirstOrDefault(r => r.uuid == uuid);
            if (result == null)
            {
                return;
            }
            result.balance -= amount;
            context.SaveChanges();
        });
    }

    /// <summary>
    /// 銀行残高を設定する
    /// </summary>
    /// <param name="uuid"></param>
    /// <param name="amount"></param>
    /// <param name="plugin"></param>
    /// <param name="note"></param>
    /// <param name="displayNote"></param>
    public static void SetBalance(string uuid, double amount,string plugin,string note,string displayNote)
    {
        BankQueue.TryAdd(context =>
        {
            var result = context.user_bank.FirstOrDefault(r => r.uuid == uuid);
            if (result == null)
            {
                return;
            }
            result.balance = amount;
            context.SaveChanges();
        });
    }

    public static void BankLog(string uuid,double amount,bool isDeposit, string plugin, string note, string displayNote)
    {

        var mcid = GetMinecraftID(uuid);
        var context = new Context();

        var log = new MoneyLog
        {
            uuid = uuid,
            player = mcid,
            amount = amount,
            deposit = isDeposit,
            plugin_name = plugin,
            server = "paper",
            note = note,
            display_note = displayNote
        };

        context.money_log.Add(log);
        context.SaveChanges();
    }
    

    #region キュー
    
    /// <summary>
    /// バンクのトランザクションを処理するキュー
    /// </summary>
    private static void BlockingQueue()
    {
        var context = new Context();
        
        while (true)
        {
            BankQueue.TryTake(out var job,-1);
            try
            {
                job?.Invoke(context);
            }
            catch (Exception e)
            {
                Console.WriteLine(e);
            }
            finally
            {
                context.Dispose();
            }
        }
        
    }
    
    #endregion

}