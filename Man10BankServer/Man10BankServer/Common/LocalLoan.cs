using Man10BankServer.Controllers;

namespace Man10BankServer.Common;

public static class LocalLoan
{

    public static async Task<int> Create(LocalLoanData data)
    {
        var result = await Task.Run(() =>
        {
            var insertData = new LocalLoanTable
            {
                amount = data.Amount,
                borrow_uuid = data.BorrowUUID,
                borrow_player = Bank.GetMinecraftId(data.BorrowUUID) ?? "Null",
                lend_uuid = data.LendUUID,
                lend_player = Bank.GetMinecraftId(data.LendUUID) ?? "Null",
                payback_date = data.PayDate
            };
            var context = new Context();
            context.loan_table.Add(insertData);
            context.SaveChanges();

            return insertData.id;
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

    public static async Task<LocalLoanTable?> GetInfo(int id)
    {
        var result = await Task.Run(() =>
        {
            var context = new Context();
            var data = context.loan_table.FirstOrDefault(r => r.id == id);
            return data;
        });

        return result;
    }
}
