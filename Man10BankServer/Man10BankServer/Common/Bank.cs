using System.Collections.Concurrent;

namespace Man10BankServer.Common;

public class Bank
{

    private static readonly BlockingCollection<Action<Context>> BankQueue = new();
    
    public void StartMan10Bank()
    {
        Task.Run(BlockingQueue);
    }
    
    private static void BlockingQueue()
    {
        var context = new Context();
        
        while (true)
        {
            BankQueue.TryTake(out var job,-1);
            try
            {
                job?.Invoke(context);
            }
            catch (Exception e)
            {
                Console.WriteLine(e);
            }
            finally
            {
                context.Dispose();
            }
        }
        
    }
    

}