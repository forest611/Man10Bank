using Man10BankServer.Model;

namespace Man10BankServer.Common;

public static class History
{

    public static void AsyncServerEstateHistoryTask()
    {

        Task.Run(() =>
        {
            Console.WriteLine("サーバー全体資産の履歴をとるタスクを開始");

            while (true)
            {
                Thread.Sleep(1000*60);
                AddServerEstateHistory();
            }
        });
    }
    
    /// <summary>
    /// 鯖全体の資産履歴をとる
    /// </summary>
    private static void AddServerEstateHistory()
    {
        BankContext.AddDatabaseJob(context =>
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
                // shop = estate.Sum(r=>r.shop),
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
    }
    
    /// <summary>
    /// サーバーの資産情報を取得
    /// </summary>
    /// <returns></returns>
    public static async Task<ServerEstateHistory> GetServerEstate()
    {
        var result = await Task.Run(() =>
        {
            var context = new BankContext();
            var record = context.server_estate_history.OrderBy(r => r.date).FirstOrDefault() ?? new ServerEstateHistory();
            context.Dispose();
            return record;
        });
        
        return result;
    }

    public static async Task<ServerEstateHistory[]> GetServerEstateHistory(int day)
    {
        var result = await Task.Run(() =>
        {
            var date = DateTime.Now.AddDays(-day);
            var context = new BankContext();
            var array = context.server_estate_history.Where(r => r.date >= date).ToArray();
            context.Dispose();
            return array;
        });
        return result;
    }

    /// <summary>
    /// 新規資産レコード作成
    /// </summary>
    /// <param name="uuid"></param>
    private static void CreateEstateRecord(string uuid)
    {
        BankContext.AddDatabaseJob(context =>
        {
            if (context.estate_tbl.Any(r => r.uuid==uuid))
            {
                return;                
            }

            var record = new EstateTable
            {
                uuid = uuid,
                player = User.GetMinecraftId(uuid).Result
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
        BankContext.AddDatabaseJob(context =>
        {
            
            Console.WriteLine("1");
            var record = context.estate_tbl.FirstOrDefault(r => r.uuid == data.uuid);

            if (record == null)
            {
                Console.WriteLine("Created");
                CreateEstateRecord(data.uuid);
                return;
            }

            //銀行とローンはこっちで取得する
            data.bank = Bank.SyncGetBalance(data.uuid).Result;
            data.loan = ServerLoan.GetBorrowingInfo(data.uuid).Result?.borrow_amount ?? 0;

            var dataHasNotChanged = data.vault == record.vault &&
                                    data.bank == record.bank &&
                                    data.cash == record.cash &&
                                    data.loan == record.loan &&
                                    data.estate == record.estate;
                                     // data.shop == record.shop;

            if (dataHasNotChanged)
            {
                Console.WriteLine("NotChange");
                return; 
            }

            record.vault = data.vault;
            record.bank = data.bank;
            record.cash = data.cash;
            record.loan = data.loan;
            record.estate = data.estate;
            // record.shop = data.shop;
            // record.total = data.vault + data.bank + data.cash + data.estate + data.shop + data.crypto;
            record.total = data.vault + data.bank + data.cash + data.estate + data.crypto;
            record.date = DateTime.Now;
            
            //ヒストリーを追加
            var history = new EstateHistoryTable
            {
                vault = data.vault,
                bank = data.bank,
                cash = data.cash,
                loan = data.loan,
                estate = data.estate,
                // shop = data.shop,
                uuid = data.uuid,
                player = data.player,
                crypto = data.crypto,
                total = data.total
            };

            context.estate_history_tbl.Add(history);

            context.SaveChanges();
            Console.WriteLine("Changed");

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
            var context = new BankContext();

            var record = context.estate_tbl.FirstOrDefault(r => r.uuid==uuid) ?? new EstateTable();
            
            context.Dispose();
            
            return record;
        });

        return result;
    }

    public static async Task<EstateHistoryTable[]> GetUserEstateHistory(string uuid,int day)
    {
        var result = await Task.Run(() =>
        {
            var date = DateTime.Now.AddDays(-day);
            var context = new BankContext();
            var array = context.estate_history_tbl.Where(r =>r.uuid==uuid && r.date >= date ).ToArray();
            context.Dispose();
            return array;
        });
        return result;
    }

    /// <summary>
    /// 資産トップを取得
    /// </summary>
    /// <param name="record">何位まで取得するか</param>
    /// <param name="skip"></param>
    /// <returns></returns>
    public static async Task<EstateTable[]> GetBalanceTop(int record,int skip)
    {
        var result = await Task.Run(() =>
        {
            var context = new BankContext();

            var records = context.estate_tbl.OrderByDescending(r => r.total).Skip(skip).Take(record).ToArray();
            
            context.Dispose();
            
            return records;
        });

        return result;
    }

    /// <summary>
    /// 借金ランキング
    /// </summary>
    /// <param name="record">何位まで取得するか</param>
    /// <param name="skip"></param>
    /// <returns></returns>
    public static async Task<ServerLoanTable[]> GetLoanTop(int record,int skip)
    {
        var result = await Task.Run(() =>
        {
            var context = new BankContext();

            // var records = context.estate_tbl.OrderByDescending(r => r.total).Skip(skip).Take(record).ToArray();
            var records = context.server_loan_tbl.OrderByDescending(r => r.borrow_amount).Skip(skip).Take(record)
                .ToArray();
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
        BankContext.AddDatabaseJob(context =>
        {
            context.vault_log.Add(log);
            context.SaveChanges();
        });
    }

    /// <summary>
    /// ATMログ
    /// </summary>
    public static void AddAtmLog(ATMLog log)
    {
        BankContext.AddDatabaseJob(context =>
        {
            context.atm_log.Add(log);
            context.SaveChanges();
        });
    }

}