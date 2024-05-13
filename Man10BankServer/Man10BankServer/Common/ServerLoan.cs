using Man10BankServer.Data;
using Man10BankServer.Model;
using Timer = System.Timers.Timer;

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
    public async Task<BorrowResult> BorrowAndSendMoney(Money amount)
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

    /// <summary>
    /// 支払い処理　出金もここで行う
    /// </summary>
    /// <param name="amount"></param>
    /// <param name="isSelf"></param>
    /// <returns></returns>
    public async Task<PaymentResult> PayFromBank(Money amount,bool isSelf)
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

    //トラブルなどで支払日を伸ばす必要がある場合の処理
    public static void AddPaymentDay(int day)
    {
        Task.Run(() =>
        {
            Semaphore.Wait();

            try
            {
                var record = Context.server_loan_tbl.Where(r => r.borrow_amount!= 0);
                foreach (var data in record)
                {
                    data.last_pay_date = data.last_pay_date.AddDays(day);
                }
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
        PaymentTask();
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
     private static void PaymentTask()
     {
         Console.WriteLine("リボのタスク起動しました");
         
         var context = new BankContext();

         var timerTask = new Timer(1000 * 60);

         timerTask.Elapsed += async (_, _) =>
         {
             var now = DateTime.Now;

             if (now.Day==_lastTaskDate.Day)
             {
                 return;
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
                 
                 //支払日じゃなければコンティニュ
                 var diff = now - data.last_pay_date;
                 if (diff.Days < PaymentInterval) continue;

                 var payment = new Money(data.payment_amount);
                 var player = await Player.GetFromUuid(data.uuid);
                 var loan = new ServerLoan(player);
                 //支払い処理
                 await loan.PayFromBank(payment, false);

             }
             
             await context.SaveChangesAsync();
             Console.WriteLine("リボの処理終了");
         };

         timerTask.AutoReset = true;
         
         timerTask.Start();
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


