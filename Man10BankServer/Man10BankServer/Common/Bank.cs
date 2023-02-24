using System.Collections.Concurrent;
using Man10BankServer.Model;

namespace Man10BankServer.Common;

public static class Bank
{

    private static readonly BlockingCollection<Action<Context>> BankQueue = new();

    public static void StartMan10Bank()
    {
        Task.Run(BlockingQueue);
    }

    /// <summary>
    /// ユーザーの所持金を取得する
    /// 非同期のため、厳密な金額ではない可能性がある
    /// 口座がない場合は-1を返す
    /// </summary>
    /// <param name="uuid"></param>
    /// <returns></returns>
    public static async Task<double> AsyncGetBalance(string uuid)
    {

        var result = await Task.Run(() =>
        {
            using var context = new Context();
            var balance = context.user_bank.FirstOrDefault(r => r.uuid == uuid)?.balance;
            return balance;
        });

        return result ?? -1;
    }

    /// <summary>
    /// 残高を追加する
    /// </summary>
    /// <param name="uuid"></param>
    /// <param name="amount"></param>
    /// <param name="plugin"></param>
    /// <param name="note"></param>
    /// <param name="displayNote"></param>
    /// <returns></returns>
    public static async Task<string> AsyncAddBalance(string uuid, double amount,string plugin,string note,string displayNote)
    {
        var result = await Task.Run(() =>
        {
            var ret = "Successful";
            var lockObj = new object();

            Monitor.Enter(lockObj);

            AddBalance(uuid, amount, plugin, note, displayNote, r =>
            {
                Monitor.Enter(lockObj);
                ret = r;
                Monitor.PulseAll(lockObj);
                Monitor.Exit(lockObj);
            });

            Monitor.Wait(lockObj);
            Monitor.Exit(lockObj);
            
            return ret;
        });

        return result;
    }
    
    /// <summary>
    /// 残高を減らす
    /// </summary>
    /// <param name="uuid"></param>
    /// <param name="amount"></param>
    /// <param name="plugin"></param>
    /// <param name="note"></param>
    /// <param name="displayNote"></param>
    /// <returns></returns>
    public static async Task<string> AsyncTakeBalance(string uuid, double amount,string plugin,string note,string displayNote)
    {
        var result = await Task.Run(() =>
        {
            var ret = "Successful";
            var lockObj = new object();
            
            Monitor.Enter(lockObj);
            
            TakeBalance(uuid, amount, plugin, note, displayNote, r => {
                Monitor.Enter(lockObj);
                ret = r;
                Monitor.PulseAll(lockObj);
                Monitor.Exit(lockObj);
            });

            Monitor.Wait(lockObj);
            Monitor.Exit(lockObj);
            return ret;
        });

        return result;
    }

    /// <summary>
    /// 口座を作る
    /// </summary>
    /// <param name="uuid"></param>
    /// <param name="userName"></param>
    public static void CreateBank(string uuid,string userName)
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
                player = userName
            };
            context.user_bank.Add(bank);
            context.SaveChanges();
            
            BankLog(uuid,0,true,"Man10Bank","CreateAccount","口座を作成");
        });
    }

    /// <summary>
    /// 銀行残高を増やす
    /// </summary>
    /// <param name="uuid"></param>
    /// <param name="amount"></param>
    /// <param name="plugin"></param>
    /// <param name="note"></param>
    /// <param name="displayNote"></param>
    /// <param name="callback"></param>
    private static void AddBalance(string uuid, double amount,string plugin,string note,string displayNote,Action<string>? callback = null)
    {
        BankQueue.TryAdd(context =>
        {
            var result = context.user_bank.FirstOrDefault(r => r.uuid == uuid);
            if (result == null)
            {
                Console.WriteLine("口座がありません");
                callback?.Invoke("NotFoundBank");
                return;
            }
            result.balance = Math.Floor(result.balance+amount);
            context.SaveChanges();
            
            BankLog(uuid,Math.Floor(amount),true,plugin,note,displayNote);
            
            callback?.Invoke("Successful");
        });
    }

    /// <summary>
    /// 銀行残高を減らす
    /// </summary>
    /// <param name="uuid"></param>
    /// <param name="amount"></param>
    /// <param name="plugin"></param>
    /// <param name="note"></param>
    /// <param name="displayNote"></param>
    /// <param name="callback"></param>
    private static void TakeBalance(string uuid, double amount,string plugin,string note,string displayNote,Action<string>? callback = null)
    {
        BankQueue.TryAdd(context =>
        {
            var result = context.user_bank.FirstOrDefault(r => r.uuid == uuid);
            if (result == null)
            {
                Console.WriteLine("口座がありません");
                callback?.Invoke("NotFoundBank");
                return;
            }

            if (result.balance<amount)
            {
                Console.WriteLine("残高不足");
                callback?.Invoke("NotEnoughMoney");
                return;
            }
            
            result.balance = Math.Floor(result.balance-amount);
            context.SaveChanges();
            
            BankLog(uuid,Math.Floor(amount),false,plugin,note,displayNote);
            
            callback?.Invoke("Successful");
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

    private static void BankLog(string uuid,double amount,bool isDeposit, string plugin, string note, string displayNote)
    {

        Context.AddDatabaseJob(context =>
        {
            var userName = Utility.GetMinecraftId(uuid).Result;
            // var context = new Context();

            var log = new MoneyLog
            {
                uuid = uuid,
                player = userName,
                amount = amount,
                deposit = isDeposit,
                plugin_name = plugin,
                server = "paper",
                note = note,
                display_note = displayNote
            };

            context.money_log.Add(log);
            context.SaveChanges();
            // context.Dispose();
        
        });
    }

    public static async Task<MoneyLog[]> GetLog(string uuid)
    {
        var result = await Task.Run(() =>
        {
            using var context = new Context();
            var ret = context.money_log
                .Where(r => r.uuid == uuid)
                .OrderBy(r => r.date)
                .Take(10)
                .ToArray();
            return ret;
        });

        return result;
    }
    

    #region キュー
    
    /// <summary>
    /// バンクのトランザクションを処理するキュー
    /// </summary>
    private static void BlockingQueue()
    {
        Console.WriteLine("Man10Bankキューを起動");

        var context = new Context();
        
        while (true)
        {
            BankQueue.TryTake(out var job);
            try
            {
                job?.Invoke(context);
                // context.Dispose();
            }
            catch (Exception e)
            {
                Console.WriteLine(e);
            }
        }
    }
    
    #endregion
}
