using System.Diagnostics;
using Man10BankServer.Model;

namespace Man10BankServer.Common;

public static class ServerLoan
{
    private static DateTime _lastTaskDate = DateTime.Now;
    private static double _dailyInterest = 0.001;
    private static int _paymentInterval = 3;
    private static double _baseParameter = 3.55;
    private static int _standardScore = 300;
    private static double _minimumAmount = 100000;
    private static int _stopInterestScore; 
    private static int _penaltyScore = 50; 

    //（無条件で借りられる額）+〔(一カ月の残高中央値*(スコア/基準スコア))×3.55］＝貸出可能金額
    public static async Task<double> CalculateLoanAmount(string uuid)
    {
        var result = await Task.Run(() =>
        {
            var score = Utility.GetScore(uuid).Result;
            var lastMonth = DateTime.Now.AddMonths(-1);
            var context = new BankContext();
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
            var context = new BankContext();
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
                // context.SaveChanges();
            }

            if (ret=="Successful")
            {
                _ = Bank.AsyncAddBalance(uuid, amount,"Man10Bank","BorrowFromMan10Revolving","リボの借金");
            }     

            context.SaveChanges();
            context.Dispose();
            return ret;
        });

        return result;
    }

    public static async Task<bool> Pay(string uuid, double amount)
    {
        var result = await Task.Run(() =>
        {
            var context = new BankContext();
            var data = context.server_loan_tbl.FirstOrDefault(r=>r.uuid == uuid);

            if (data==null)
            {
                return false;
            }
            
            //支払い処理
            if (Bank.AsyncTakeBalance(data.uuid, amount, "Man10Bank", "Man10Revolving", "Man10リボの支払い").Result 
                != "Successful") return false;
            //支払い成功した場合
            // data.last_pay_date = now;
            data.borrow_amount -= data.payment_amount;
            if (data.borrow_amount < 0.0)
            {
                data.borrow_amount = 0;
                data.failed_payment = 0;
            }

            context.SaveChanges();
            context.Dispose();
            return true;
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
            var context = new BankContext();
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
    public static async Task<string> SetBorrowingInfo(ServerLoanTable data)
    {

        var result = await Task.Run(() =>
        {
            var context = new BankContext();

            string ret;

            if (!context.server_loan_tbl.Any(r=>r.id==data.id))
            {
                context.Dispose();
                ret = "NotFound";            
                return ret;
            }
            
            context.server_loan_tbl.Update(data);
            ret = "Update";
            context.Dispose();
            
            return ret;
        });

        return result;
    }

    public static async Task<DateTime?> NextPayDate(string uuid)
    {
        var result = await Task.Run<DateTime?>(() =>
        {
            var context = new BankContext();
            var record = context.server_loan_tbl.FirstOrDefault(r => r.uuid == uuid);

            if (record==null)
            {
                return null;
            }

            var ret = record.last_pay_date.AddDays(_paymentInterval);
            
            context.Dispose();
            
            return ret;
        });

        return result;
    }

    public static async Task<bool> IsLoser(string uuid)
    {
        var result = await Task.Run(() =>
        {

            var score = Utility.GetScore(uuid).Result;
            var context = new BankContext();
            var record = context.server_loan_tbl.FirstOrDefault(r => r.uuid == uuid);

            if (record == null) { return false; }

            var failed = record.failed_payment;
            
            context.Dispose();
            
            return score < 0 && failed > 0;
        });

        return result;
    }

    public static void StartPaymentTask(IConfiguration config)
    {
        _lastTaskDate = config.GetValue<DateTime>("ServerLoan:LastTaskDate");
        _dailyInterest = config.GetValue<double>("ServerLoan:DailyInterest");
        _paymentInterval = config.GetValue<int>("ServerLoan:PaymentInterval");

        _baseParameter = config.GetValue<double>("ServerLoan:BaseParameter");
        _standardScore = config.GetValue<int>("ServerLoan:StandardScore");
        _minimumAmount = config.GetValue<Double>("ServerLoan:MinimumAmount");
        _stopInterestScore = config.GetValue<int>("ServerLoan:StopInterestScore");
        _penaltyScore = config.GetValue<int>("ServerLoan:PenaltyScore");
        
        Task.Run(PaymentTask);
    }

    /// <summary>
    /// 一日一回支払い処理を動かすタスク
    /// </summary>
    private static void PaymentTask()
    {
        Console.WriteLine("リボのタスク起動");
        
        var context = new BankContext();
        
        while (true)
        {
            Thread.Sleep(1000*60);
            
            var now = DateTime.Now;

            if (now.Day==_lastTaskDate.Day)
            {
                continue;
            }

            _lastTaskDate = now;
            
            Console.WriteLine("リボの処理を開始");
            
            var result = context.server_loan_tbl.Where(r => r.borrow_amount > 0);

            foreach (var data in result)
            {

                var score = Utility.GetScore(data.uuid).Result;

                //スコアが指定値以上なら金利追加
                if (score>_stopInterestScore)
                {
                    data.borrow_amount += data.borrow_amount * _dailyInterest;
                }
                
                //支払日じゃなければコンティニュ
                if (data.last_pay_date.Day + _paymentInterval <= now.Day) continue;

                var payment = data.payment_amount;
                var failedFlag = false;
                
                //支払い処理
                if (Bank.AsyncTakeBalance(data.uuid,payment,"Man10Bank","Man10Revolving","Man10リボの支払い").Result == "Successful")
                {
                     //支払い成功した場合
                    data.last_pay_date = now;
                    data.borrow_amount -= data.payment_amount;
                    if (data.borrow_amount < 0.0)
                    {
                        data.borrow_amount = 0;
                        data.failed_payment = 0;
                    }
                }
                else
                {
                    data.failed_payment++;
                    failedFlag = true;
                }

                if (!failedFlag) continue;
                //基準スコア以上なら半減
                if (score>_standardScore)
                {
                    var _ = Utility.TakeScore(data.uuid, Convert.ToInt32(score) / 2);
                }
                else
                {
                    var _ = Utility.TakeScore(data.uuid, _penaltyScore);
                }
            }
            context.SaveChanges();
        }
    }
}