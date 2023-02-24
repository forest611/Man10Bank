using Man10BankServer.Model;

namespace Man10BankServer.Common;

public static class Cheque
{

    /// <summary>
    /// 新規小切手を発行
    /// </summary>
    /// <param name="uuid"></param>
    /// <param name="amount"></param>
    /// <param name="note"></param>
    /// <param name="isOp"></param>
    /// <returns>小切手のID</returns>
    public static async Task<int> Create(string uuid, double amount, string note, bool isOp)
    {
        var result = await Task.Run(() =>
        {
            var context = new Context();
            var record = new ChequeTable
            {
                amount = amount,
                date = DateTime.Now,
                note = note,
                player = Utility.GetMinecraftId(uuid).Result,
                uuid = uuid,
                used = false
            };

            context.cheque_tbl.Add(record);
            context.SaveChanges();

            var id = record.id;
            context.Dispose();
            
            return id;
        });

        return result;
    }

    /// <summary>
    /// 小切手を使用する
    /// </summary>
    /// <param name="id"></param>
    /// <returns>小切手の金額(使用不可能だった場合は-1)</returns>
    public static async Task<double> Use(int id)
    {
        var result = await Task.Run(() =>
        {

            var context = new Context();
            var record = context.cheque_tbl.FirstOrDefault(r => r.id == id);

            if (record==null || record.used)
            {
                context.Dispose();
                return -1;
            }
            var amount = record.amount;

            record.used = true;
            record.use_date = DateTime.Now;

            context.SaveChanges();
            context.Dispose();
            
            return amount;
        });

        return result;
    }
    
}