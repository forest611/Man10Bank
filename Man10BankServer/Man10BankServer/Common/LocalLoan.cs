
using Man10BankServer.Data;
using Man10BankServer.Model;

namespace Man10BankServer.Common;

public static class LocalLoan
{
    public static double MinimumInterest { get; private set; }
    public static double MaximumInterest { get; private set; }
    public static double Fee { get; private set; }

    private static readonly BankContext Context = new();
    private static readonly SemaphoreSlim Semaphore = new(1, 1);
    
    /// <summary>
    /// 個人間借金の設定を読み込む
    /// </summary>
    public static void LoadConfig(IConfiguration config)
    {
        MinimumInterest = double.Parse(config["LocalLoan:MinimumInterest"] ?? "0");
        MaximumInterest = double.Parse(config["LocalLoan:MaximumInterest"] ?? "0");
        Fee = double.Parse(config["LocalLoan:Fee"] ?? "0");
    }

    /// <summary>
    /// 借金の作成
    /// </summary>
    /// <param name="data"></param>
    /// <returns></returns>
    public static async Task<int> Create(LocalLoanTable data)
    {
        await Semaphore.WaitAsync();
        try
        {
            Context.loan_table.Add(data);
            await Context.SaveChangesAsync();
            return data.id;
        }
        catch (Exception e)
        {
            Console.WriteLine(e);
        }
        finally
        {
            Semaphore.Release();
        }

        return 0;
    }

    /// <summary>
    /// 借金の返済(手形の情報書き換えのみ)
    /// </summary>
    /// <param name="id"></param>
    /// <param name="amount"></param>
    /// <returns></returns>
    public static async Task<string> Pay(int id,Money amount)
    {
        await Semaphore.WaitAsync();
        try
        {
            var data = Context.loan_table.FirstOrDefault(r => r.id == id);

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

            data.amount -= amount.Amount;
            
            if (data.amount < 0)
            {
                data.amount = 0;
                await Context.SaveChangesAsync();
                return "AllPaid";
            }

            await Context.SaveChangesAsync();

            return "Paid";

        }
        catch (Exception e)
        {
            Console.WriteLine(e);
        }
        finally
        {
            Semaphore.Release();
        }

        return "DataNotFound";
    }

    public static async Task<LocalLoanTable?> GetInfo(int id)
    {
        await Semaphore.WaitAsync();
        try
        {
            var record = Context.loan_table.FirstOrDefault(r => r.id == id);
            return record;
        }
        catch (Exception e)
        {
            Console.WriteLine(e);
        }
        finally
        {
            Semaphore.Release();
        }
        return null;
    }

    public static async Task<Money> GetTotalLoan(string uuid)
    {
        await Semaphore.WaitAsync();
        try
        {
            var total = Context.loan_table.Where(r => r.borrow_uuid == uuid).Sum(r => r.amount);
            return new Money(total);
        }
        catch (Exception e)
        {
            Console.WriteLine(e);
        }
        finally
        {
            Semaphore.Release();
        }

        return new Money(0);
    }
}
