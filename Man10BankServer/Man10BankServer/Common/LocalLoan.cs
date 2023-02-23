using Man10BankServer.Controllers;

namespace Man10BankServer.Common;

public static class LocalLoan
{

    public static async Task<int> Create(LocalLoanData data)
    {
        var result = await Task.Run(() =>
        {
            var record = new LocalLoanTable
            {
                amount = data.Amount,
                borrow_uuid = data.BorrowUUID,
                borrow_player = Bank.GetMinecraftId(data.BorrowUUID) ?? "Null",
                lend_uuid = data.LendUUID,
                lend_player = Bank.GetMinecraftId(data.LendUUID) ?? "Null",
                payback_date = data.PayDate
            };
            var context = new Context();
            context.loan_table.Add(record);
            context.SaveChanges();

            return record.id;
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

    public static async Task<LocalLoanData> GetInfo(int id)
    {
        var result = await Task.Run(() =>
        {
            var context = new Context();
            var data = context.loan_table.FirstOrDefault(r => r.id == id);

            var ret = new LocalLoanData
            {
                BorrowUUID = data?.borrow_uuid ?? "",
                LendUUID = data?.lend_uuid ?? "",
                Amount = data?.amount ?? 0.0,
                OrderID = data?.id ?? -1,
                PayDate = data?.payback_date ?? DateTime.Now
            };
            context.Dispose();
            return ret;
        });

        return result;
    }
}
