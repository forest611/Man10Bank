using System.Collections.Concurrent;
using Man10BankServer.Model;

namespace Man10BankServer.Common;

public static class Bank
{

    private static readonly BlockingCollection<Action<BankContext>> BankQueue = new();
    
    /// <summary>
    /// 接続確認処理
    /// </summary>
    /// <returns></returns>
    public static async Task<bool> SyncCheckConnect()
    {
        var result = await Task.Run(() =>
        {
            using var context = new BankContext();
            var ret = context.Database.CanConnect();

            return ret;
        });

        return result;
    }

    /// <summary>
    /// ユーザーの所持金を取得する
    /// 口座がない場合は-1を返す
    /// </summary>
    /// <param name="uuid"></param>
    /// <returns></returns>
    public static async Task<double> SyncGetBalance(string uuid)
    {
        var tcs = new TaskCompletionSource<double>();
        
        GetBalance(uuid, r =>
        {
            tcs.SetResult(r);
        });

        return await tcs.Task;
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
    public static async Task<int> SyncAddBalance(string uuid, double amount,string plugin,string note,string displayNote)
    {
        var tcs = new TaskCompletionSource<int>();

        AddBalance(uuid, amount, plugin, note, displayNote, r =>
        {
            tcs.SetResult(r);
        });

        return await tcs.Task;
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
    public static async Task<int> SyncTakeBalance(string uuid, double amount,string plugin,string note,string displayNote)
    {
        var tcs = new TaskCompletionSource<int>();

        TakeBalance(uuid, amount, plugin, note, displayNote, r => {
            tcs.SetResult(r);
        });

        return await tcs.Task;
    }
    
    public static async Task<int> SyncSetBalance(string uuid, double amount,string plugin,string note,string displayNote)
    {
        var tcs = new TaskCompletionSource<int>();

        SetBalance(uuid, amount, plugin, note, displayNote, r => {
            tcs.SetResult(r);
        });

        return await tcs.Task;
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
            
            PushBankLog(uuid,0,bank.balance,true,"Man10Bank","CreateAccount","口座を作成");
        });
    }

    /// <summary>
    /// 残高取得(他のトランザクションから取得しないように)
    /// </summary>
    /// <param name="uuid"></param>
    /// <param name="callback"></param>
    private static void GetBalance(string uuid,Action<double>? callback = null)
    {
        BankQueue.TryAdd(context =>
        {
            var result = context.user_bank.FirstOrDefault(r => r.uuid == uuid);
            //口座がない場合は-1を返す
            if (result == null)
            {
                // Console.WriteLine("口座がありません");
                callback?.Invoke(-1);
                return;
            }

            var log = context.money_log.LastOrDefault(r => r.uuid == uuid);

            if (log!= null &&  Math.Abs(log.balance - result.balance) > 1)
            {
                // ログの値とのズレがあった場合
                callback?.Invoke(-2);
                return;
            }
            
            callback?.Invoke(result.balance);
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
    /// <param name="callback">
    /// 200:成功
    /// 550:口座なし
    /// </param>
    private static void AddBalance(string uuid, double amount,string plugin,string note,string displayNote,Action<int>? callback = null)
    {
        BankQueue.TryAdd(context =>
        {
            var result = context.user_bank.FirstOrDefault(r => r.uuid == uuid);
            if (result == null)
            {
                // Console.WriteLine("口座がありません");
                callback?.Invoke(550);
                return;
            }
            result.balance = Math.Floor(result.balance+amount);
            context.SaveChanges();
            
            PushBankLog(uuid,Math.Floor(amount),result.balance,true,plugin,note,displayNote);
            
            callback?.Invoke(200);
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
    /// <param name="callback">
    /// 200:成功
    /// 550:口座なし
    /// 551:残高不足
    /// </param>
    private static void TakeBalance(string uuid, double amount,string plugin,string note,string displayNote,Action<int>? callback = null)
    {
        BankQueue.TryAdd(context =>
        {
            var result = context.user_bank.FirstOrDefault(r => r.uuid == uuid);
            if (result == null)
            {
                // Console.WriteLine("口座がありません");
                callback?.Invoke(550);
                return;
            }

            if (result.balance<amount)
            {
                // Console.WriteLine("残高不足");
                callback?.Invoke(551);
                return;
            }
            
            result.balance = Math.Floor(result.balance-amount);
            context.SaveChanges();
            
            PushBankLog(uuid,Math.Floor(amount),result.balance,false,plugin,note,displayNote);
            
            callback?.Invoke(200);
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
    /// <param name="callback">
    /// 200:成功
    /// 550:口座なし
    /// </param>
    private static void SetBalance(string uuid, double amount,string plugin,string note,string displayNote,Action<int>? callback = null)
    {
        BankQueue.TryAdd(context =>
        {
            var result = context.user_bank.FirstOrDefault(r => r.uuid == uuid);
            if (result == null)
            {
                callback?.Invoke(550);
                return;
            }
            result.balance = Math.Floor(amount);
            context.SaveChanges();
            PushBankLog(uuid,Math.Floor(amount),result.balance,false,plugin,$"[Set]{note}",$"[Set]{displayNote}");
            callback?.Invoke(200);
        });
    }

    /// <summary>
    /// 口座残高の変更があったらログに追記する
    /// </summary>
    /// <param name="uuid"></param>
    /// <param name="amount"></param>
    /// <param name="balance"></param>
    /// <param name="isDeposit"></param>
    /// <param name="plugin"></param>
    /// <param name="note"></param>
    /// <param name="displayNote"></param>
    private static void PushBankLog(string uuid,double amount,double balance,bool isDeposit, string plugin, string note, string displayNote)
    {

        BankContext.AddDatabaseJob(context =>
        {
            var userName = User.GetMinecraftId(uuid).Result;
            if (note.Length >= 60)
            {
                note = note[..60];
            }

            var log = new MoneyLog
            {
                uuid = uuid,
                player = userName,
                amount = amount,
                balance = balance,
                deposit = isDeposit,
                plugin_name = plugin,
                server = "paper",
                note = note,
                display_note = displayNote,
                date = DateTime.Now
            };

            context.money_log.Add(log);
            context.SaveChanges();
        });
    }

    /// <summary>
    /// 銀行の取引履歴を取得
    /// </summary>
    /// <param name="uuid"></param>
    /// <param name="record">レコード数</param>
    /// <param name="skip">何件飛ばすか</param>
    /// <returns></returns>
    public static async Task<MoneyLog[]> GetLog(string uuid,int record,int skip)
    {
        var result = await Task.Run(() =>
        {
            using var context = new BankContext();
            var ret = context.money_log
                .Where(r => r.uuid == uuid)
                .OrderByDescending(r => r.date)
                .Skip(skip)
                .Take(record)
                .ToArray();
            return ret;
        });

        return result;
    }
    

    #region キュー
    
    /// <summary>
    /// バンクのトランザクションを処理するキュー
    /// </summary>
    public static void BlockingQueue()
    {
        Console.WriteLine("Man10Bankキューを起動");

        var context = new BankContext();
        
        while (BankQueue.TryTake(out var job,Timeout.Infinite))
        {
            try
            {
                // Console.WriteLine(job?.Method.Name);
                job?.Invoke(context);
            }
            catch (Exception e)
            {
                Console.WriteLine(e);
            }
        }
        Console.WriteLine("Man10Bankキューを終了");
    }
    
    #endregion
}
