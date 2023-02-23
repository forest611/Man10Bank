using System.Diagnostics;
using Man10BankServer.Controllers;

namespace Man10BankServer.Common;

public static class ServerLoan
{
    
    public static DateTime LastTaskDate = DateTime.Now;
    public static double DailyInterest = 0.001;
    public static int PaymentInterval = 3;

    //（無条件で借りられる額）+〔(一カ月の残高中央値-スコアによる天引き)×3.55］＝貸出可能金額
    public static async Task<double> CalculateLoanAmount(string uuid)
    {

        var result = await Task.Run(() =>
        {
            
            return 0.0;
        });

        return result;
    }

    public static async Task<string> Borrow(string uuid, double amount)
    {

        var result = await Task.Run(() =>
        {
            var context = new Context();
            var record = context.server_loan_tbl.FirstOrDefault(r => r.uuid == uuid);

            //借金がこれ以上できない場合
            if (amount + (record?.borrow_amount ?? 0.0) > CalculateLoanAmount(uuid).Result)
            {
                return "Failed";
            }

            var hasBorrowed = record != null;
            string ret;


            if (hasBorrowed)
            {
                ret = "Success";
                Debug.Assert(record != null, nameof(record) + " != null");
                record.borrow_amount += amount;
                record.payment_amount = record.borrow_amount * DailyInterest * 2;
                record.borrow_date = DateTime.Now;
            }
            else
            {
                ret = "FirstSuccess";

                var insert = new ServerLoanTable
                {
                    borrow_amount = amount,
                    borrow_date = DateTime.Now,
                    last_pay_date = DateTime.Now,
                    payment_amount = amount * DailyInterest * 2,
                    uuid = uuid,
                    player = Bank.GetMinecraftId(uuid) ?? ""
                };

                context.server_loan_tbl.Add(insert);
                context.SaveChanges();
            }

            context.SaveChanges();
            
            return ret;
        });

        return result;

    }

    /// <summary>
    /// 借金情報を取得する
    /// </summary>
    /// <param name="uuid"></param>
    /// <returns></returns>
    public static async Task<ServerLoanData?> GetBorrowingInfo(string uuid)
    {
        var result = await Task.Run(() =>
        {
            var context = new Context();
            var record = context.server_loan_tbl.FirstOrDefault(r => r.uuid == uuid);

            var ret = new ServerLoanData
            {
                OrderID = record?.id ?? -1,
                UUID = record?.uuid ?? "",
                BorrowAmount = record?.borrow_amount ?? 0,
                PaymentAmount = record?.payment_amount ?? 0,
                BorrowDate = record?.borrow_date ?? DateTime.Now,
                FailedPayment = record?.failed_payment ?? 0,
                LastPayDate = record?.last_pay_date ?? DateTime.Now,
                StopInterest = record?.stop_interest ?? false
            };
            context.Dispose();
            
            return ret;
        });
        
        return result;
    }

    /// <summary>
    /// 借金情報をセットする
    /// </summary>
    /// <param name="data"></param>
    /// <returns></returns>
    public static async Task<int> SetBorrowingInfo(ServerLoanData data)
    {

        var result = await Task.Run(() =>
        {
            var context = new Context();
            var record = new ServerLoanTable
            {
                player = Bank.GetMinecraftId(data.UUID) ?? "",
                uuid = data.UUID,
                borrow_amount = data.BorrowAmount,
                borrow_date = data.BorrowDate,
                last_pay_date = data.LastPayDate,
                payment_amount = data.PaymentAmount
            };

            context.server_loan_tbl.Add(record);
            
            context.Dispose();
            
            return 0;
        });

        return result;
    }

    public static void StartPaymentTask(IConfiguration config)
    {
        LastTaskDate = config.GetValue<DateTime>("ServerLoan:LastTaskDate");
        DailyInterest = config.GetValue<double>("ServerLoan:DailyInterest");
        PaymentInterval = config.GetValue<int>("ServerLoan:PaymentInterval");
        
        Task.Run(PaymentTask);
    }

    /// <summary>
    /// 一日一回支払い処理を動かすタスク
    /// </summary>
    private static void PaymentTask()
    {
        var context = new Context();
        
        while (true)
        {
            Thread.Sleep(1000*60);
            
            var now = DateTime.Now;

            if (now.Day==LastTaskDate.Day)
            {
                continue;
            }
            
            var result = context.server_loan_tbl.Where(r => r.borrow_amount > 0);

            foreach (var data in result)
            {
                data.borrow_amount += data.borrow_amount * DailyInterest;

                if (data.last_pay_date.Day + PaymentInterval <= now.Day) continue;

                var payment = data.payment_amount;

                if (Bank.AsyncTakeBalance(data.uuid,payment,"Man10Bank","Man10Revolving","Man10リボの支払い").Result == 0)
                {
                    data.last_pay_date = now;
                    data.borrow_amount -= data.payment_amount;
                    if (data.borrow_amount < 0.0)
                    {
                        data.borrow_amount = 0;
                    }
                }
                else
                {
                    data.failed_payment++;
                }

                //TODO:スコアの処理
                
            }

            context.SaveChanges();

        }
        
    }
}