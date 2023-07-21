
using Man10BankServer.Model;

namespace Man10BankServer.Common;

public static class LocalLoan
{
    public static double MinimumInterest { get; set; }
    public static double MaximumInterest { get; set; }
    public static double Fee { get; set; }
    public static void LoadProperty()
    {
        MinimumInterest = double.Parse(Utility.Config?["LocalLoan:MinimumInterest"] ?? "0");
        MaximumInterest = double.Parse(Utility.Config?["LocalLoan:MaximumInterest"] ?? "0");
        Fee = double.Parse(Utility.Config?["LocalLoan:Fee"] ?? "0");
    }

    public static async Task<int> Create(LocalLoanTable data)
    {
        var result = await Task.Run(() =>
        {
            var context = new BankContext();
            context.loan_table.Add(data);
            context.SaveChanges();
            return data.id;
        });
        return result;
    }

    /// <summary>
    /// 借金の返済(手形の情報書き換えのみ)
    /// </summary>
    /// <param name="id"></param>
    /// <param name="amount"></param>
    /// <returns></returns>
    public static async Task<string> Pay(int id,double amount)
    {
        var result = await Task.Run(() =>
        {
            var context = new BankContext();
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
            var context = new BankContext();
            var record = context.loan_table.FirstOrDefault(r => r.id == id);
            
            context.Dispose();
            return record ?? new LocalLoanTable();
        });

        return result;
    }
}
