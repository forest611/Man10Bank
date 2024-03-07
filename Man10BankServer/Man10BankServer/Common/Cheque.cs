using Man10BankServer.Data;
using Man10BankServer.Model;

namespace Man10BankServer.Common;

public static class Cheque
{
    private static readonly BankContext Context = new();
    private static readonly SemaphoreSlim Semaphore = new(1, 1);

    public static async Task<Money> Use(Player player,int id)
    {
        await Semaphore.WaitAsync();
        var amount = new Money(0);
        
        try
        {

            var record = Context.cheque_tbl.FirstOrDefault(r => r.id == id);
            if (record == null || record.used)
            {
                return amount;
            }

            record.used = true;
            record.use_date = DateTime.Now;
            record.use_player = player.Name;
            amount = new Money(record.amount);
            await Context.SaveChangesAsync();
        }
        catch (Exception e)
        {
            Console.WriteLine(e);
        }
        finally
        {
            Semaphore.Release();
        }

        return amount;
    }

    public static async Task<int> Create(Player creator,Money amount, string note)
    {
        var record = new ChequeTable
        {
            amount = amount.Amount,
            note = note,
            player = creator.Name,
            uuid = creator.Uuid,
            used = false,
            date = DateTime.Now
        };

        await Semaphore.WaitAsync();

        try
        {
            Context.cheque_tbl.Add(record);
            await Context.SaveChangesAsync();
        }
        catch (Exception e)
        {
            Console.WriteLine(e);
        }
        finally
        {
            Semaphore.Release();
        }
        
        return record.id;
    }
    
}