namespace Man10BankServer.Common;

public abstract class Startup
{
    
    public static void Setup()
    {
        //ブロッキングキューの起動
        Task.Run(Bank.BlockingQueue);
        History.AsyncServerEstateHistoryTask();
        ServerLoan.Load();
        LocalLoan.Load();
    }
    
}