using Man10BankServer.Model;

namespace Man10BankServer.Common;

public static class History
{

    static History()
    {
        Task.Run(ServerEstateHistoryTask);
    }

    private static void ServerEstateHistoryTask()
    {
        Console.WriteLine("サーバー全体資産の履歴をとるタスクを開始");

        while (true)
        {
            Thread.Sleep(1000*60);
            AddServerEstateHistory();
        }
    }
    
    /// <summary>
    /// 鯖全体の資産履歴をとる
    /// </summary>
    private static void AddServerEstateHistory()
    {
        Context.AddDatabaseJob(context =>
        {
            var year = DateTime.Now.Year;
            var month = DateTime.Now.Month;
            var day = DateTime.Now.Day;
            var hour = DateTime.Now.Hour;


            var hasData = context.server_estate_history.Any(r =>
                r.year == year && r.month == month && r.day == day && r.hour == hour);

            if (hasData) { return; }
        
            var estate = context.estate_tbl;

            var record = new ServerEstateHistory
            {
                vault = estate.Sum(r => r.vault),
                bank = estate.Sum(r => r.bank),
                cash = estate.Sum(r => r.cash),
                estate = estate.Sum(r => r.estate),
                loan = estate.Sum(r => r.estate),
                shop = estate.Sum(r=>r.shop),
                crypto = 0,
                date = DateTime.Now,
                year = year,
                month = month,
                day = day,
                hour = hour,
                total = estate.Sum(r => r.total)
            };

            context.server_estate_history.Add(record);

            context.SaveChanges();
        });
        // var context = new Context();
        
        // context.Dispose();
    }
    
    /// <summary>
    /// サーバーの資産情報を取得
    /// </summary>
    /// <returns></returns>
    public static async Task<ServerEstateHistory> GetServerEstate()
    {
        var result = await Task.Run(() =>
        {
            var context = new Context();
            var record = context.server_estate_history.OrderBy(r => r.date).FirstOrDefault() ?? new ServerEstateHistory();
            context.Dispose();
            return record;
        });
        
        return result;
    }

    /// <summary>
    /// 新規資産レコード作成(口座作成時に呼ぶ)
    /// </summary>
    /// <param name="uuid"></param>
    private static void CreateEstateRecord(string uuid)
    {
        Context.AddDatabaseJob(context =>
        {
            if (context.estate_tbl.Any(r => r.uuid==uuid))
            {
                return;                
            }

            var record = new EstateTable
            {
                uuid = uuid,
                player = Utility.GetMinecraftId(uuid).Result
            };

            context.estate_tbl.Add(record);

            context.SaveChanges();
        });
    }
    
    /// <summary>
    /// ユーザーの資産を記録
    /// </summary>
    /// <param name="data"></param>
    public static void AddUserEstateHistory(EstateTable data)
    {
        Context.AddDatabaseJob(context =>
        {
            var record = context.estate_tbl.FirstOrDefault(r => r.uuid == data.uuid);

            if (record == null)
            {
                CreateEstateRecord(data.uuid);
                return;
            }

            //古いデータ
            var lastVault = record.vault;
            var lastBank = record.bank;
            var lastCash = record.cash;
            var lastLoan = record.loan;
            var lastEstate = record.estate;
            var lastShop = record.shop;

            if (data.vault == lastVault && data.bank == lastBank && data.cash == lastCash && data.loan == lastLoan
                && data.estate == lastEstate && data.shop == lastShop)
            {
                return;
            }

            context.estate_tbl.Add(data);

            var history = new EstateHistoryTable
            {
                vault = data.vault,
                bank = data.bank,
                cash = data.cash,
                loan = data.loan,
                estate = data.estate,
                shop = data.shop,
                uuid = data.uuid,
                player = data.player
            };

            context.estate_history_tbl.Add(history);

            context.SaveChanges();
        });
    }

    /// <summary>
    /// 指定ユーザーの資産情報を取得
    /// </summary>
    /// <param name="uuid"></param>
    /// <returns></returns>
    public static async Task<EstateTable> GetUserEstate(string uuid)
    {
        var result = await Task.Run(() =>
        {
            var context = new Context();

            var record = context.estate_tbl.FirstOrDefault(r => r.uuid==uuid) ?? new EstateTable();
            
            context.Dispose();
            
            return record;
        });

        return result;
    }

    /// <summary>
    /// 資産トップを取得
    /// </summary>
    /// <param name="size">何位まで取得するか</param>
    /// <returns></returns>
    public static async Task<EstateTable[]> GetBalanceTop(int size)
    {
        var result = await Task.Run(() =>
        {
            var context = new Context();

            var records = context.estate_tbl.OrderByDescending(r => r.total).Take(size).ToArray();
            
            context.Dispose();
            
            return records;
        });

        return result;
    }

    /// <summary>
    /// 電子マネーのログをとる関数
    /// </summary>
    public static void AddVaultLog(VaultLog log)
    {
        Context.AddDatabaseJob(context =>
        {
            context.vault_log.Add(log);
            context.SaveChanges();
        });
    }

    /// <summary>
    /// ATMログ
    /// </summary>
    public static void AddATMLog(ATMLog log)
    {
        Context.AddDatabaseJob(context =>
        {
            context.atm_log.Add(log);
            context.SaveChanges();
        });
    }

}