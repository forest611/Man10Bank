
using Man10BankServer.Model;

namespace Man10BankServer.Common;

public static class LocalLoan
{

    public static async Task<int> Create(LocalLoanTable data)
    {
        var result = await Task.Run(() =>
        {
            var context = new Context();
            context.loan_table.Add(data);
            context.SaveChanges();
            return data.id;
        });
        return result;
    }

    public static async Task<string> Pay(int id,double amount)
    {
        var result = await Task.Run(() =>
        {
            var context = new Context();
            var data = context.loan_table.FirstOrDefault(r => r.id == id);

            if (data == null)
            {
                return "DataNotFound";
            }

            data.amount -= amount;

            if (data.amount < 0)
            {
                data.amount = 0;
                context.SaveChanges();
                return "AllPaid";
            }

            context.SaveChanges();

            return "Paid";
        });

        return result;
    }

    public static async Task<LocalLoanTable> GetInfo(int id)
    {
        var result = await Task.Run(() =>
        {
            var context = new Context();
            var record = context.loan_table.FirstOrDefault(r => r.id == id);
            
            context.Dispose();
            return record ?? new LocalLoanTable();
        });

        return result;
    }
}
