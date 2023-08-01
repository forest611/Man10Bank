namespace Man10BankServer.Common;

public abstract class Startup
{
    
    public static void Setup()
    {
        //ブロッキングキューの起動
        Task.Run(Bank.BlockingQueue);
        ServerLoan.Async(Score.Config!);
        History.AsyncServerEstateHistoryTask();
    }
    
}