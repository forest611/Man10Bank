using Man10BankServer.Model;

namespace Man10BankServer.Common;

public static class History
{
    public static void AddServerEstateHistory()
    {
        Context.AddDatabaseJob(context =>
        {
            var year = DateTime.Now.Year;
            var month = DateTime.Now.Month;
            var day = DateTime.Now.Day;
            var hour = DateTime.Now.Hour;


            var hasData = context.server_estate_history.Any(r =>
                r.year == year && r.month == month && r.day == day && r.hour == hour);

            if (hasData)
            {
                context.Dispose();
                return;
            }
        
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
    public static void CreateEstateRecord(string uuid)
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
        });
    }
    
    public static void AddUserEstateHistory(string uuid)
    {
        Context.AddDatabaseJob(context =>
        {
            var record = context.estate_tbl.FirstOrDefault(r => r.uuid == uuid);

            if (record == null)
            {
                CreateEstateRecord(uuid);
                return;
            }
            
            //TODO:
            var vault = record.vault;
            var bank = record.bank;
            var cash = record.cash;
            var loan = record.loan;
            var estate = record.estate;
            var shop = record.shop;
            
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

            var record = context.estate_tbl.FirstOrDefault(r => r.uuid==uuid);
            
            context.Dispose();
            
            return record ?? new EstateTable();
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

            var records = context.estate_tbl.OrderByDescending(r => r.total).Take(size);
            
            context.Dispose();
            
            return records.ToArray();
        });

        return result;
    }


}