using Man10BankServer.Data;
using Man10BankServer.Model;

namespace Man10BankServer.Common;

public class ServerLoan
{

    private static readonly BankContext Context = new();
    private static readonly SemaphoreSlim Semaphore = new(1, 1);
    
    private static DateTime _lastTaskDate = DateTime.Now;
    private static double _baseParameter = 3.55;
    private static int _standardScore = 300;
    private static int _stopInterestScore; 
    private static int _penaltyScore = 50;

    public static double DailyInterest { get; private set; } = 0.001;
    public static int PaymentInterval { get; private set; } = 3;
    public static double MinimumAmount { get; private set; } = 100000;
    public static double MaximumAmount { get; private set; } = 10000000;
    
    private Player Player { get; }
    public ServerLoan(Player player)
    {
        Player = player;
    }

    /// <summary>
    /// リボの借入　入金まで行う
    /// </summary>
    /// <param name="amount"></param>
    /// <returns></returns>
    public async Task<BorrowResult> Borrow(Money amount)
    {
        await Semaphore.WaitAsync();

        try
        {
            var record = Context.server_loan_tbl.FirstOrDefault(r => r.uuid == Player.Uuid);
            var borrowable = GetBorrowableAmount();
            var nowLoan = new Money(record?.borrow_amount ?? 0);
            //貸出上限が超えている
            if (borrowable.Amount<amount.Plus(nowLoan).Amount)
            {
                return BorrowResult.Failed;
            }

            //ここからは借入成功
            RecordAction(ServerLoanAction.Borrow,amount);
            var bank = await Bank.GetBank(Player);
            await bank.Add(amount,"Man10Bank","BorrowFromMan10Revolving","リボの借金");

            //初回
            if (record == null)
            {
                var newLoanRecord = new ServerLoanTable
                {
                    borrow_amount = amount.Amount,
                    borrow_date = DateTime.Now,
                    last_pay_date = DateTime.Now,
                    payment_amount = amount.Amount * DailyInterest * 2,
                    uuid = Player.Uuid,
                    player = Player.Name
                };

                Context.server_loan_tbl.Add(newLoanRecord);
                return BorrowResult.FirstSuccess;
            }

            //2回目以降
            record.borrow_amount += amount.Amount;
            record.payment_amount = record.borrow_amount * DailyInterest * 2;
            record.borrow_date = DateTime.Now;
            return BorrowResult.Success;
        }
        catch (Exception e)
        {
            Console.WriteLine(e);
        }
        finally
        {
            await Context.SaveChangesAsync();
            Semaphore.Release();
        }

        return BorrowResult.Failed;
    }

    /// <summary>
    /// スコア0未満で支払い回数に失敗がある人をLoserとする
    /// </summary>
    /// <returns></returns>
    public async Task<bool> IsLoser()
    {
        await Semaphore.WaitAsync();
        try
        {
            var score = await Score.GetScore(Player.Uuid);
            var failedCount = Context.server_loan_tbl.FirstOrDefault(r => r.uuid == Player.Uuid)?.failed_payment ?? 0;

            if (score < 0 && failedCount > 0)
            {
                return true;
            }
        }
        catch (Exception e)
        {
            Console.WriteLine(e);
        }
        finally
        {
            Semaphore.Release();
        }

        return false;
    }

    public async Task<PaymentResult> Pay(Money amount,bool isSelf)
    {

        await Semaphore.WaitAsync();
        try
        {
            var record = Context.server_loan_tbl.FirstOrDefault(r => r.uuid == Player.Uuid);

            //借金がない
            if (record == null)
            {
                return PaymentResult.NotLoan;
            }

            var paymentAmount = isSelf ? amount : new Money(record.payment_amount);
            var bank = await Bank.GetBank(Player);
            var successWithdraw = await bank.Take(paymentAmount, "Man10Bank", "Man10Revolving", "Man10リボの支払い");
            
            switch (successWithdraw)
            {
                //支払い失敗、手動支払いの場合は何もしない
                case false when isSelf:
                {
                    return PaymentResult.NotEnoughMoney;
                }
                //自動支払いで失敗した場合
                case false:
                {
                    var score = await Score.GetScore(Player.Uuid);
                    record.failed_payment++;
                     
                    //ヒストリー追記
                    RecordAction(ServerLoanAction.FailedPayment,amount);

                    //基準スコア以上なら半減
                    var penalty = score > _standardScore ? score / 2 : _penaltyScore;
                    await Score.TakeScore(record.uuid, penalty,"まんじゅうリボの未払い");
                    return PaymentResult.NotEnoughMoney;
                }
                //成功した場合
                case true:
                {
                    //最終支払日の更新は、自動引き落としの場合のみ
                    if (!isSelf)
                    {
                        record.last_pay_date = DateTime.Now;
                    }
                    
                    var diff = paymentAmount.Amount - record.borrow_amount;
                    record.borrow_amount -= paymentAmount.Amount;
                    if (record.borrow_amount <= 0.0)
                    {
                        record.borrow_amount = 0;
                        record.failed_payment = 0;
                        await bank.Add(new Money(diff), "Man10Bank", "PaybackDifference", "差額の返金");

                    }
                    
                    RecordAction(isSelf ? ServerLoanAction.SelfPayment : ServerLoanAction.SuccessPayment,amount);
                    
                    return PaymentResult.Success;
                }
            }
        }
        catch (Exception e)
        {
            Console.WriteLine(e);
        }
        finally
        {
            await Context.SaveChangesAsync();
            Semaphore.Release();
        }
        return PaymentResult.NotLoan;
    }

    private void RecordAction(ServerLoanAction action,Money amount)
    {
        Task.Run(() =>
        {
            Semaphore.Wait();
            try
            {
                Context.server_loan_history.Add(new ServerLoanHistory
                {
                    player = Player.Name,
                    uuid = Player.Uuid,
                    type = action.ToString(),
                    amount = amount.Amount,
                    date = DateTime.Now
                });
                Context.SaveChanges();
            }
            catch (Exception e)
            {
                Console.WriteLine(e);
            }
            finally
            {
                Semaphore.Release();
            }
        });
    }

    public async Task<ServerLoanTable?> GetInfo()
    {
        await Semaphore.WaitAsync();
        try
        {
            var record = Context.server_loan_tbl.FirstOrDefault(r => r.uuid == Player.Uuid);
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

    public void SetPaymentAmount(Money amount)
    {
        Task.Run(() =>
        {
            Semaphore.Wait();

            try
            {
                var data = Context.server_loan_tbl.FirstOrDefault(r => r.uuid == Player.Uuid);
                if (data == null)
                {
                    return;
                }
                data.payment_amount = amount.Amount;
                Context.SaveChanges();
            }
            catch (Exception e)
            {
                Console.WriteLine(e);
            }
            finally
            {
                Semaphore.Release();
            }
        });
    }

    //仮で最小額だけ借りれるように
    public Money GetBorrowableAmount()
    {
        return new Money(MinimumAmount);
    }

    static ServerLoan()
    {
        Task.Run(PaymentTask);
    }
    
    public static void LoadConfig(IConfiguration config)
    {
     
     DailyInterest = config.GetValue<double>("ServerLoan:DailyInterest");
     PaymentInterval = config.GetValue<int>("ServerLoan:PaymentInterval");
     _baseParameter = config.GetValue<double>("ServerLoan:BaseParameter");
     _standardScore = config.GetValue<int>("ServerLoan:StandardScore");
     MinimumAmount = config.GetValue<double>("ServerLoan:MinimumAmount");
     MaximumAmount = config.GetValue<double>("ServerLoan:MaximumAmount");
     _stopInterestScore = config.GetValue<int>("ServerLoan:StopInterestScore");
     _penaltyScore = config.GetValue<int>("ServerLoan:PenaltyScore");
    }
     
     /// <summary>
     /// 一日一回支払い処理を動かすタスク
     /// </summary>
     private static async void PaymentTask()
     {
         Console.WriteLine("リボのタスク起動しました");
         
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

                 var score = await Score.GetScore(data.uuid);

                 //最低スコアを超えていたら、金利が発生する
                 if (score>_stopInterestScore)
                 {
                     data.borrow_amount += data.borrow_amount * DailyInterest;
                 }
                 await context.SaveChangesAsync();
                 
                 //支払日じゃなければコンティニュ
                 var diff = now - data.last_pay_date;
                 if (diff.Days < PaymentInterval) continue;

                 var payment = new Money(data.payment_amount);
                 var player = await Player.GetFromUuid(data.uuid);
                 var loan = new ServerLoan(player);
                 //支払い処理
                 await loan.Pay(payment, false);

             }
             
             Console.WriteLine("リボの処理終了");
         }
     }
     
     private enum ServerLoanAction
     {
         Borrow,
         SelfPayment,
         SuccessPayment,
         FailedPayment
     }
     
     public enum BorrowResult
     {
         Failed,
         Success,
         FirstSuccess
     }
     
     public enum PaymentResult
     {
         Success,
         NotEnoughMoney,
         NotLoan
     }

}


// public static class ServerLoan
// {
//     private static DateTime _lastTaskDate = DateTime.Now;
//     private static double _baseParameter = 3.55;
//     private static int _standardScore = 300;
//     private static int _stopInterestScore; 
//     private static int _penaltyScore = 50;
//     
//     public static double DailyInterest { get; private set; } = 0.001;
//     public static int PaymentInterval { get; private set; } = 3;
//     public static double MinimumAmount { get; private set; } = 100000;
//     public static double MaximumAmount { get; private set; } = 10000000;
//
//     //（無条件で借りられる額）+〔(一カ月の残高中央値*(スコア/基準スコア))×3.55］＝貸出可能金額
//     // 12/5 スコア0未満はリボの使用不可に変更。とりあえず一律10万に
//     public static async Task<double> CalculateLoanAmount(string uuid)
//     {
//         return MinimumAmount;
//         
//         
//         var result = await Task.Run(() =>
//         {
//             var score = Score.GetScore(uuid).Result ?? 0.0;
//
//             //スコアがマイナスなら借入不可
//             return score < 0 ? 0.0 : MinimumAmount;
//
//             // var score = Score.GetScore(uuid).Result ?? 0.0;
//             //
//             // var lastMonth = DateTime.Now.AddMonths(-1);
//             // var context = new BankContext();
//             // var history = context.estate_history_tbl
//             //     .Where(r => r.uuid == uuid && r.date > lastMonth).OrderBy(r => r.total).ToList();
//             //
//             // context.Dispose();
//             //
//             // if (history.Count == 0)
//             // {
//             //     return 0.0;
//             // }
//             //
//             // double median;
//             //
//             // if (history.Count % 2 == 0)
//             // {
//             //     var i = history[(history.Count - 1) / 2].total;
//             //     var j = history[history.Count / 2].total;
//             //
//             //     median = (i + j) / 2;
//             // }
//             // else
//             // {
//             //     median = history[history.Count / 2].total;
//             // }
//             //
//             // var scoreParam = score / _standardScore;
//             //
//             // if (scoreParam>1) { scoreParam = 1; }
//             //
//             // var borrowableAmount = median * scoreParam * _baseParameter + MinimumAmount;
//             //
//             // return borrowableAmount>MaximumAmount ? MaximumAmount : borrowableAmount;
//         });
//
//         return result;
//     }
//
//     public static async Task<string> Borrow(string uuid, double amount)
//     {
//
//         var result = await Task.Run(() =>
//         {
//             var context = new BankContext();
//             var record = context.server_loan_tbl.FirstOrDefault(r => r.uuid == uuid);
//             var player = User.GetMinecraftId(uuid).Result;
//             
//             //借金がこれ以上できない場合
//             if (amount + (record?.borrow_amount ?? 0.0) > CalculateLoanAmount(uuid).Result)
//             {
//                 return "Failed";
//             }
//             string ret;
//             
//             //レコードがnullなら初借金とする
//             if (record != null)
//             {
//                 ret = "Successful";
//                 record.borrow_amount += amount;
//                 record.payment_amount = record.borrow_amount * DailyInterest * 2;
//                 record.borrow_date = DateTime.Now;
//             }
//             else
//             {
//                 ret = "FirstSuccessful";
//
//                 var insert = new ServerLoanTable
//                 {
//                     borrow_amount = amount,
//                     borrow_date = DateTime.Now,
//                     last_pay_date = DateTime.Now,
//                     payment_amount = amount * DailyInterest * 2,
//                     uuid = uuid,
//                     player = player
//                 };
//
//                 context.server_loan_tbl.Add(insert);
//                 // context.SaveChanges();
//             }
//
//             //リボの借入成功したパターン
//             if (ret.Contains("Successful"))
//             {
//                 _ = Bank.SyncAddBalance(uuid, amount,"Man10Bank","BorrowFromMan10Revolving","リボの借金");
//                 context.server_loan_history.Add(new ServerLoanHistory
//                 {
//                     player = player,
//                     uuid = uuid,
//                     type = ServerLoanType.BORROW.ToString(),
//                     amount = amount,
//                     date = DateTime.Now
//                 });
//             }     
//
//             context.SaveChanges();
//             context.Dispose();
//             return ret;
//         });
//
//         return result;
//     }
//
//     public static async Task<bool> Pay(string uuid, double amount)
//     {
//         var result = await Task.Run(() =>
//         {
//             var context = new BankContext();
//             var data = context.server_loan_tbl.FirstOrDefault(r=>r.uuid == uuid);
//
//             if (data==null)
//             {
//                 return false;
//             }
//             
//             //支払い処理
//             if (Bank.SyncTakeBalance(data.uuid, amount, "Man10Bank", "Man10Revolving", "Man10リボの支払い").Result 
//                 != 200) return false;
//             //支払い成功した場合
//             // data.last_pay_date = now;
//             data.borrow_amount -= amount;
//             if (data.borrow_amount < 0.0)
//             {
//                 data.borrow_amount = 0;
//                 data.failed_payment = 0;
//             }
//             
//             //ヒストリー追記
//             context.server_loan_history.Add(new ServerLoanHistory
//             {
//                 player = data.player,
//                 uuid = data.uuid,
//                 type = ServerLoanType.SELF_PAYMENT.ToString(),
//                 amount = amount,
//                 date = DateTime.Now
//             });
//
//             context.SaveChanges();
//             context.Dispose();
//             return true;
//         });
//
//         return result;
//     }
//
//     /// <summary>
//     /// 借金情報を取得する
//     /// </summary>
//     /// <param name="uuid"></param>
//     /// <returns></returns>
//     public static async Task<ServerLoanTable?> GetBorrowingInfo(string uuid)
//     {
//         var result = await Task.Run(() =>
//         {
//             var context = new BankContext();
//             var record = context.server_loan_tbl.FirstOrDefault(r => r.uuid == uuid);
//             context.Dispose();
//             return record;
//         });
//         
//         return result;
//     }
//
//     /// <summary>
//     /// 借金情報をセットする
//     /// </summary>
//     /// <param name="data"></param>
//     /// <returns></returns>
//     public static async Task<int> SetBorrowingInfo(ServerLoanTable data)
//     {
//
//         var result = await Task.Run(() =>
//         {
//             var context = new BankContext();
//
//             int ret;
//
//             if (!context.server_loan_tbl.Any(r=>r.id==data.id))
//             {
//                 context.Dispose();
//                 ret = 550;
//                 return ret;
//             }
//
//             //支払額>貸出額の場合、=になおす
//             if (data.payment_amount>data.borrow_amount)
//             {
//                 data.payment_amount = data.borrow_amount;
//             }
//             
//             context.server_loan_tbl.Update(data);
//             context.SaveChanges();
//             ret = 200;
//             context.Dispose();
//             
//             return ret;
//         });
//
//         return result;
//     }
//
//     public static async Task<DateTime?> GetNextPayDate(string uuid)
//     {
//         var result = await Task.Run<DateTime?>(() =>
//         {
//             var context = new BankContext();
//             var record = context.server_loan_tbl.FirstOrDefault(r => r.uuid == uuid);
//
//             if (record==null)
//             {
//                 return null;
//             }
//
//             var ret = record.last_pay_date.AddDays(PaymentInterval);
//             
//             context.Dispose();
//             
//             return ret;
//         });
//
//         return result;
//     }
//
//     public static async Task<bool> IsLoser(string uuid)
//     {
//         var result = await Task.Run(() =>
//         {
//
//             var score = Score.GetScore(uuid).Result;
//             var context = new BankContext();
//             var record = context.server_loan_tbl.FirstOrDefault(r => r.uuid == uuid);
//
//             if (record == null) { return false; }
//
//             var failed = record.failed_payment;
//             
//             context.Dispose();
//             
//             return score < 0 && failed > 0;
//         });
//
//         return result;
//     }
//
//     /// <summary>
//     /// リボの設定読み込み
//     /// </summary>
//     public static void Load()
//     {
//
//         var config = Score.Config;
//         
//         // _lastTaskDate = config.GetValue<DateTime>("ServerLoan:LastTaskDate");
//
//         DailyInterest = config.GetValue<double>("ServerLoan:DailyInterest");
//         PaymentInterval = config.GetValue<int>("ServerLoan:PaymentInterval");
//         _baseParameter = config.GetValue<double>("ServerLoan:BaseParameter");
//         _standardScore = config.GetValue<int>("ServerLoan:StandardScore");
//         MinimumAmount = config.GetValue<double>("ServerLoan:MinimumAmount");
//         MaximumAmount = config.GetValue<double>("ServerLoan:MaximumAmount");
//         _stopInterestScore = config.GetValue<int>("ServerLoan:StopInterestScore");
//         _penaltyScore = config.GetValue<int>("ServerLoan:PenaltyScore");
//         
//         Task.Run(PaymentTask);
//     }
//
//     /// <summary>
//     /// 一日一回支払い処理を動かすタスク
//     /// </summary>
//     private static void PaymentTask()
//     {
//         Console.WriteLine("リボのタスク起動しました");
//         
//         var context = new BankContext();
//         
//         while (true)
//         {
//             Thread.Sleep(1000*60);
//             
//             var now = DateTime.Now;
//
//             if (now.Day==_lastTaskDate.Day)
//             {
//                 continue;
//             }
//
//             _lastTaskDate = now;
//             
//             Console.WriteLine("リボの処理を開始");
//             
//             var result = context.server_loan_tbl.Where(r => r.borrow_amount > 0);
//
//             foreach (var data in result)
//             {
//
//                 var score = Score.GetScore(data.uuid).Result;
//
//                 //スコアが指定値以上なら金利追加
//                 if (score>_stopInterestScore)
//                 {
//                     data.borrow_amount += data.borrow_amount * DailyInterest;
//                 }
//                 
//                 //支払日じゃなければコンティニュ
//                 var diff = now - data.last_pay_date;
//                 if (diff.Days < PaymentInterval) continue;
//
//                 var payment = data.payment_amount;
//                 var failedFlag = false;
//                 
//                 //支払い処理
//                 if (Bank.SyncTakeBalance(data.uuid,payment,"Man10Bank","Man10Revolving","Man10リボの支払い").Result == 200)
//                 {
//                      //支払い成功した場合
//                     data.last_pay_date = now;
//                     data.borrow_amount -= data.payment_amount;
//                     if (data.borrow_amount < 0.0)
//                     {
//                         data.borrow_amount = 0;
//                         data.failed_payment = 0;
//                     }
//                     
//                     //ヒストリー追記
//                     context.server_loan_history.Add(new ServerLoanHistory
//                     {
//                         player = data.player,
//                         uuid = data.uuid,
//                         type = ServerLoanType.SUCCESS_PAYMENT.ToString(),
//                         amount = payment,
//                         date = DateTime.Now
//                     });
//
//                 }
//                 else//支払い失敗
//                 {
//                     data.failed_payment++;
//                     failedFlag = true;
//                     
//                     //ヒストリー追記
//                     context.server_loan_history.Add(new ServerLoanHistory
//                     {
//                         player = data.player,
//                         uuid = data.uuid,
//                         type = ServerLoanType.FAILED_PAYMENT.ToString(),
//                         amount = payment,
//                         date = DateTime.Now
//                     });
//                     //基準スコア以上なら半減
//                     var penalty = score > _standardScore ? (int)score / 2 : _penaltyScore;
//                     _ = Score.TakeScore(data.uuid, penalty,"まんじゅうリボの未払い");
//
//                 }
//
//                 // //支払い成功時はここで終了
//                 // if (!failedFlag) continue;
//
//             }
//             context.SaveChanges();
//             
//             Console.WriteLine("リボの処理終了");
//         }
//     }
//
// }