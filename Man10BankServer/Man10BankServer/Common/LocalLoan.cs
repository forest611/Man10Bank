
using Man10BankServer.Model;

namespace Man10BankServer.Common;

public static class LocalLoan
{
    public static double MinimumInterest { get; private set; }
    public static double MaximumInterest { get; private set; }
    public static double Fee { get; private set; }
    
    /// <summary>
    /// 個人間借金の設定を読み込む
    /// </summary>
    public static void Load()
    {
        MinimumInterest = double.Parse(Score.Config?["LocalLoan:MinimumInterest"] ?? "0");
        MaximumInterest = double.Parse(Score.Config?["LocalLoan:MaximumInterest"] ?? "0");
        Fee = double.Parse(Score.Config?["LocalLoan:Fee"] ?? "0");
    }

    /// <summary>
    /// 借金の作成
    /// </summary>
    /// <param name="data"></param>
    /// <returns></returns>
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

            //返済日になってなかった場合
            if (data.payback_date>=DateTime.Now)
            {
                return "DateError";
            }

            //すでに全額返済していた場合
            if (data.amount<=0)
            {
                return "Already";
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

    public static async Task<double> GetTotalLoan(string uuid)
    {
        var result = await Task.Run(() =>
        {
            var context = new BankContext();
            var total = context.loan_table.Where(r => r.borrow_uuid == uuid).Sum(r => r.amount);
            return total;
        });
        return result;
    }
}
