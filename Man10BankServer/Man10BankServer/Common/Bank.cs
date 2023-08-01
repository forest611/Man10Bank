using System.Collections.Concurrent;
using Man10BankServer.Model;

namespace Man10BankServer.Common;

public static class Bank
{

    private static readonly BlockingCollection<Action<BankContext>> BankQueue = new();
    
    public static void Setup()
    {
        //ブロッキングキューの起動
        Task.Run(BlockingQueue);
        ServerLoan.Async(Utility.Config!);
        History.AsyncServerEstateHistoryTask();
    }
    
    public static void ConfigureServices(IServiceCollection services)
    {
        services.AddControllers();

        // CORS設定
        services.AddCors(options =>
        {
            options.AddPolicy("AllowOriginPolicy", builder =>
            {
                builder.AllowAnyOrigin() // すべてのオリジンからのリクエストを許可
                    .AllowAnyMethod() // すべてのHTTPメソッドを許可 (GET、POST、PUT、DELETEなど)
                    .AllowAnyHeader(); // すべてのヘッダーを許可
            });
        });
    }

    public static void Configure(IApplicationBuilder app, IWebHostEnvironment env)
    {
        if (env.IsDevelopment())
        {
            app.UseDeveloperExceptionPage();
        }

        app.UseRouting();

        // CORSポリシーを有効にする
        app.UseCors("AllowOriginPolicy");

        app.UseAuthorization();

        app.UseEndpoints(endpoints =>
        {
            endpoints.MapControllers();
        });
    }

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
    /// 非同期のため、厳密な金額ではない可能性がある
    /// 口座がない場合は-1を返す
    /// </summary>
    /// <param name="uuid"></param>
    /// <returns></returns>
    public static async Task<double> SyncGetBalance(string uuid)
    {

        var result = await Task.Run(() =>
        {
            using var context = new BankContext();
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
    public static async Task<int> SyncAddBalance(string uuid, double amount,string plugin,string note,string displayNote)
    {
        var result = await Task.Run(() =>
        {
            var ret = 550;
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
    public static async Task<int> SyncTakeBalance(string uuid, double amount,string plugin,string note,string displayNote)
    {
        var result = await Task.Run(() =>
        {
            var ret = 550;
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
            
            PushBankLog(uuid,0,true,"Man10Bank","CreateAccount","口座を作成");
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
            
            PushBankLog(uuid,Math.Floor(amount),true,plugin,note,displayNote);
            
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
            
            PushBankLog(uuid,Math.Floor(amount),false,plugin,note,displayNote);
            
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
    public static void SetBalance(string uuid, double amount,string plugin,string note,string displayNote)
    {
        BankQueue.TryAdd(context =>
        {
            var result = context.user_bank.FirstOrDefault(r => r.uuid == uuid);
            if (result == null)
            {
                return;
            }
            result.balance = Math.Floor(amount);
            context.SaveChanges();
            PushBankLog(uuid,Math.Floor(amount),false,plugin,$"[Set]{note}",$"[Set]{displayNote}");
        });
    }

    /// <summary>
    /// 口座残高の変更があったらログに追記する
    /// </summary>
    /// <param name="uuid"></param>
    /// <param name="amount"></param>
    /// <param name="isDeposit"></param>
    /// <param name="plugin"></param>
    /// <param name="note"></param>
    /// <param name="displayNote"></param>
    private static void PushBankLog(string uuid,double amount,bool isDeposit, string plugin, string note, string displayNote)
    {

        BankContext.AddDatabaseJob(context =>
        {
            var userName = User.GetMinecraftId(uuid).Result;

            var log = new MoneyLog
            {
                uuid = uuid,
                player = userName,
                amount = amount,
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
    private static void BlockingQueue()
    {
        Console.WriteLine("Man10Bankキューを起動");

        var context = new BankContext();
        
        while (BankQueue.TryTake(out var job,-1))
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
