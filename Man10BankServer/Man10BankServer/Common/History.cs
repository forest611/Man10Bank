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

    public static void AddUserEstateHistory(string uuid)
    {
        Context.AddDatabaseJob(context =>
        {
            
            
            
        });
    }

}