using System.Collections.Concurrent;
using System.Timers;
using Man10BankServer.Data;
using Man10BankServer.Model;
using Timer = System.Timers.Timer;

namespace Man10BankServer.Common;

public static class History
{

    static History()
    {
        RunDatabaseTask();
        RunServerEstateHistoryTask();
    }
    
    private static void RunServerEstateHistoryTask()
    {
        Console.WriteLine("サーバー全体資産の履歴をとるタスクを開始");
        
        var timerTask = new Timer(1000 * 60 * 5);

        timerTask.Elapsed += (sender, args) =>
        {
            Thread.Sleep(1000*60*5);
            AddServerEstateHistory();
        };

        timerTask.AutoReset = true;
        timerTask.Start();
    }
    
    /// <summary>
    /// 鯖全体の資産履歴をとる
    /// </summary>
    private static void AddServerEstateHistory()
    {
        DbQueue.Add(context =>
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
        var context = new BankContext();
        var record = context.server_estate_history.OrderByDescending(r => r.date).FirstOrDefault() ?? new ServerEstateHistory();
        await context.DisposeAsync();

        return record;
    }

    public static async Task<ServerEstateHistory[]> GetServerEstateHistory(int day)
    {
        var date = DateTime.Now.AddDays(-day);
        var context = new BankContext();
        var array = context.server_estate_history.Where(r => r.date >= date).ToArray();
        await context.DisposeAsync();

        return array;
    }

    /// <summary>
    /// 新規資産レコード作成
    /// </summary>
    private static void CreateEstateRecord(Player player)
    {
        DbQueue.Add(context =>
        {
            if (context.estate_tbl.Any(r => r.uuid==player.Uuid))
            {
                return;                
            }

            var record = new EstateTable
            {
                uuid = player.Uuid,
                player = player.Name
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
        DbQueue.Add(context =>
        {
            
            //最新の資産情報の更新
            var estateRecord = context.estate_tbl.FirstOrDefault(r => r.uuid == data.uuid);
            var player = Player.GetFromUuid(data.uuid).Result;

            if (estateRecord == null)
            {
                Console.WriteLine($"Created Estate Record : {data.player}");
                CreateEstateRecord(player);
                return;
            }

            //銀行とローンはこっちで取得する
            var bank = Bank.GetBank(player).Result;
            var loan = new ServerLoan(player);
            data.bank = bank.GetBalance().Result.Amount;
            data.loan = loan.GetInfo().Result?.borrow_amount ?? 0;

            var dataHasNotChanged = data.vault == estateRecord.vault &&
                                    data.bank == estateRecord.bank &&
                                    data.cash == estateRecord.cash &&
                                    data.loan == estateRecord.loan &&
                                    data.estate == estateRecord.estate;
                                     // data.shop == record.shop;

            if (dataHasNotChanged)
            {
                return; 
            }

            estateRecord.vault = data.vault;
            estateRecord.bank = data.bank;
            estateRecord.cash = data.cash;
            estateRecord.loan = data.loan;
            estateRecord.estate = data.estate;
            estateRecord.date = DateTime.Now;
            // record.shop = data.shop;
            // record.total = data.vault + data.bank + data.cash + data.estate + data.shop + data.crypto;
            estateRecord.total = data.vault + data.bank + data.cash + data.estate + data.crypto;
            
            ///////////////////
            
            //ヒストリーを追加
            var history = new EstateHistoryTable
            {
                vault = data.vault,
                bank = data.bank,
                cash = data.cash,
                loan = data.loan,
                estate = data.estate,
                // shop = data.shop,
                date = DateTime.Now,
                uuid = data.uuid,
                player = data.player,
                crypto = data.crypto,
                total = data.total,
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
        var context = new BankContext();
        var record = context.estate_tbl.FirstOrDefault(r => r.uuid==uuid) ?? new EstateTable();         
        await context.DisposeAsync();
        return record;
    }

    public static async Task<EstateHistoryTable[]> GetUserEstateHistory(string uuid,int day)
    {
        var date = DateTime.Now.AddDays(-day);
        var context = new BankContext();
        var array = context.estate_history_tbl.Where(r =>r.uuid==uuid && r.date >= date ).ToArray();
        await context.DisposeAsync();
        return array;
    }

    /// <summary>
    /// 資産トップを取得
    /// </summary>
    /// <param name="record">何位まで取得するか</param>
    /// <param name="skip"></param>
    /// <returns></returns>
    public static async Task<EstateTable[]> GetBalanceTop(int record,int skip)
    {
        var context = new BankContext();
        var records = context.estate_tbl.OrderByDescending(r => r.total).Skip(skip).Take(record).ToArray();
        await context.DisposeAsync();
        return records;
    }

    /// <summary>
    /// 借金ランキング
    /// </summary>
    /// <param name="record">何位まで取得するか</param>
    /// <param name="skip"></param>
    /// <returns></returns>
    public static async Task<ServerLoanTable[]> GetLoanTop(int record,int skip)
    {
        var context = new BankContext();
        var records = context.server_loan_tbl.OrderByDescending(r => r.borrow_amount).Skip(skip).Take(record)
            .ToArray();
        await context.DisposeAsync();
        return records;
    }

    /// <summary>
    /// 電子マネーのログをとる関数
    /// </summary>
    public static void AddVaultLog(VaultLog log)
    {
        DbQueue.Add(context =>
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
        DbQueue.Add(context =>
        {
            context.atm_log.Add(log);
            context.SaveChanges();
        });
    }
    
    private static readonly BlockingCollection<Action<BankContext>> DbQueue = new();

    private static void RunDatabaseTask()
    {
        Task.Run(() =>
        {
            var context = new BankContext();
            Console.WriteLine("データベースキューを起動");
            while (DbQueue.TryTake(out var job,Timeout.Infinite))
            {
                try
                {
                    job?.Invoke(context);
                }
                catch (Exception e)
                {
                    Console.WriteLine(e.Message);
                }
            }
        });
    }


}