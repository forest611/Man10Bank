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

    public static async Task<ServerLoanData?> GetBorrowingInfo(string uuid)
    {
        var result = await Task.Run(() =>
        {
            
            
            return new ServerLoanData();
        });
        
        return result;
    }

    public static async Task<int> SetBorrowingInfo(ServerLoanData data)
    {

        var result = await Task.Run(() =>
        {

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