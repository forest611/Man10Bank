using Man10BankServer.Data;
using Man10BankServer.Model;

namespace Man10BankServer.Common;

public static class Cheque
{
    private static readonly BankContext Context = new();
    private static readonly SemaphoreSlim Semaphore = new(1, 1);

    public static async Task<bool> TryUse(Player player,int id)
    {
        await Semaphore.WaitAsync();

        try
        {
            var record = Context.cheque_tbl.FirstOrDefault(r => r.id == id);
            if (record == null || record.used)
            {
                Semaphore.Release();
                return false;
            }

            record.used = true;
            record.use_date = DateTime.Now;
            record.use_player = player.Name;

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

        return true;
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