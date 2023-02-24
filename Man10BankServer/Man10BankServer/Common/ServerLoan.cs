using System.Diagnostics;

namespace Man10BankServer.Common;

public static class ServerLoan
{
    private static DateTime _lastTaskDate = DateTime.Now;
    private static double _dailyInterest = 0.001;
    private static int _paymentInterval = 3;
    private static double _baseParameter = 3.55;
    private static double _standardScore = 200;
    private static double _minimumAmount = 100000;

    //（無条件で借りられる額）+〔(一カ月の残高中央値*(スコア/基準スコア))×3.55］＝貸出可能金額
    public static async Task<double> CalculateLoanAmount(string uuid)
    {
        var result = await Task.Run(() =>
        {
            var score = Utility.GetScore(uuid).Result;
            var lastMonth = DateTime.Now.AddMonths(-1);
            var context = new Context();
            var history = context.estate_history_tbl
                .Where(r => r.uuid == uuid && r.date > lastMonth).OrderBy(r => r.total).ToList();

            context.Dispose();
            
            if (history.Count == 0)
            {
                return 0.0;
            }
            
            double median;
            
            if (history.Count % 2 == 0)
            {
                var i = history[(history.Count - 1) / 2].total;
                var j = history[history.Count / 2].total;

                median = (i + j) / 2;
            }
            else
            {
                median = history[history.Count / 2].total;
            }
            
            var scoreParam = score / _standardScore;

            if (scoreParam>1) { scoreParam = 1; }

            var borrowableAmount = median * scoreParam * _baseParameter + _minimumAmount;
            
            return borrowableAmount;
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
                ret = "Successful";
                Debug.Assert(record != null, nameof(record) + " != null");
                record.borrow_amount += amount;
                record.payment_amount = record.borrow_amount * _dailyInterest * 2;
                record.borrow_date = DateTime.Now;
            }
            else
            {
                ret = "FirstSuccessful";

                var insert = new ServerLoanTable
                {
                    borrow_amount = amount,
                    borrow_date = DateTime.Now,
                    last_pay_date = DateTime.Now,
                    payment_amount = amount * _dailyInterest * 2,
                    uuid = uuid,
                    player = Utility.GetMinecraftId(uuid).Result
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
    public static async Task<ServerLoanTable?> GetBorrowingInfo(string uuid)
    {
        var result = await Task.Run(() =>
        {
            var context = new Context();
            var record = context.server_loan_tbl.FirstOrDefault(r => r.uuid == uuid);
            context.Dispose();
            return record;
        });
        
        return result;
    }

    /// <summary>
    /// 借金情報をセットする
    /// </summary>
    /// <param name="data"></param>
    /// <returns></returns>
    public static async Task<int> SetBorrowingInfo(ServerLoanTable data)
    {

        var result = await Task.Run(() =>
        {
            var context = new Context();
            //TODO:上書き処理を書く
            
            context.server_loan_tbl.Add(data);
            context.server_loan_tbl.Update(data);
            
            context.Dispose();
            
            return 0;
        });

        return result;
    }

    public static async Task<bool> IsLoser(string uuid)
    {
        var result = await Task.Run(() =>
        {

            return false;
        });

        return result;
    }
    

    public static void StartPaymentTask(IConfiguration config)
    {
        _lastTaskDate = config.GetValue<DateTime>("ServerLoan:LastTaskDate");
        _dailyInterest = config.GetValue<double>("ServerLoan:DailyInterest");
        _paymentInterval = config.GetValue<int>("ServerLoan:PaymentInterval");
        
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

            if (now.Day==_lastTaskDate.Day)
            {
                continue;
            }
            
            var result = context.server_loan_tbl.Where(r => r.borrow_amount > 0);

            foreach (var data in result)
            {
                data.borrow_amount += data.borrow_amount * _dailyInterest;

                if (data.last_pay_date.Day + _paymentInterval <= now.Day) continue;

                var payment = data.payment_amount;

                if (Bank.AsyncTakeBalance(data.uuid,payment,"Man10Bank","Man10Revolving","Man10リボの支払い").Result == "Successful")
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