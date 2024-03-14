using System.Collections.Concurrent;
using Man10BankServer.Data;
using Man10BankServer.Model;

namespace Man10BankServer.Common;

/// <summary>
/// Bankクラス
/// </summary>
public class Bank
{
    private Player Player { get; }

    private static readonly BankContext Context = new();
    private static readonly ConcurrentDictionary<Player,Bank> BankDictionary = new ();
    private static readonly BlockingCollection<Action> BlockingCollection = new();

    private const string ServerName = "paper";
    
    private Bank(Player player)
    {
        Player = player;
    }

    /// <summary>
    /// キューのスレッドを起動
    /// </summary>
    static Bank()
    {
        RunQueueTasks();
    }
    
    public static async Task<Bank> GetBank(Player player)
    {
        if (BankDictionary.TryGetValue(player, out var bank)) return bank;
        bank = new Bank(player);
        await bank.GetBalance();
        BankDictionary[player] = bank;
        return bank;
    }


    private void CreateBank()
    {
        if (Context.user_bank.Any(r => r.uuid == Player.Uuid))
        {
            throw new Exception("すでに口座がある");
        }
        
        var bank = new UserBank
        {
            balance = 0,
            uuid = Player.Uuid,
            player = Player.Name
        };
        Context.user_bank.Add(bank);
        Context.SaveChanges();
        AddLog(new Money(0),true,"Man10Bank","CreateAccount","口座を作成");
    }
    
    public async Task<Money> GetBalance()
    {
        var tcs = new TaskCompletionSource<Money>();
        
        BlockingCollection.Add(() =>
        {
            var record = Context.user_bank.FirstOrDefault(r => r.uuid == Player.Uuid);
            if (record == null)
            {
                CreateBank();
                tcs.SetResult(new Money(0));
                return;
            }
            var amount = record.balance;
            tcs.SetResult(new Money(amount));
        });
        return await tcs.Task;
    }

    public async Task<bool> Take(Money takeAmount,string plugin, string note, string displayNote)
    {
        var tcs = new TaskCompletionSource<bool>();
        
        BlockingCollection.Add(() =>
        {
            var record = Context.user_bank.First(r => r.uuid == Player.Uuid);
            var nowAmount = record.balance;
            if (nowAmount < takeAmount.Amount)
            {
                tcs.SetResult(false);
                return;
            }
            record.balance -= takeAmount.Amount;
            Context.SaveChanges();
            AddLog(takeAmount,false,plugin,note,displayNote);
            tcs.SetResult(true);
        });
        return await tcs.Task;
    }

    public async Task<bool> Add(Money addAmount,string plugin, string note, string displayNote)
    {
        var tcs = new TaskCompletionSource<bool>();
        
        BlockingCollection.Add(() =>
        {
            var record = Context.user_bank.First(r => r.uuid == Player.Uuid);
            record.balance += addAmount.Amount;
            Context.SaveChanges();
            AddLog(addAmount,true,plugin,note,displayNote);
            tcs.SetResult(true);            
        });
        return await tcs.Task;
    }

    public void Set(Money amount, string plugin, string note, string displayNote)
    {
        BlockingCollection.Add(() =>
        {
            var record = Context.user_bank.First(r => r.uuid == Player.Uuid);
            record.balance = amount.Amount;
            Context.SaveChanges();
            AddLog(amount,true,plugin,note,displayNote);
        });
    }

    private void AddLog(Money amount, bool isDeposit, string plugin, string note, string displayNote)
    {
        var fixedNote = note.Length >= 60 ? note[..60] : note;
        var fixedDisplayNote = displayNote.Length >= 60 ? displayNote[..60] : displayNote;
        
        var log = new MoneyLog
        {
            uuid = Player.Uuid,
            player = Player.Name,
            amount = amount.Amount,
            balance = 0,
            deposit = isDeposit,
            plugin_name = plugin,
            server = ServerName,
            note = $"[2.0]{fixedNote}",
            display_note = fixedDisplayNote,
            date = DateTime.Now
        };

        Context.money_log.Add(log);
        Context.SaveChanges();
    }

    public async Task<MoneyLog[]> GetLog(int recordCount, int skip)
    {
        var tcs = new TaskCompletionSource<MoneyLog[]>();
        BlockingCollection.Add(() =>
        {
            var logArray = Context.money_log
                .Where(r => r.uuid == Player.Uuid)
                .OrderByDescending(r => r.date)
                .Skip(skip)
                .Take(recordCount)
                .ToArray();
            tcs.SetResult(logArray);
        });
        
        return await tcs.Task;
    }
    
    private static void RunQueueTasks()
    {
        Console.WriteLine("取引キューの起動");

        Task.Run(() =>
        {
            while (BlockingCollection.TryTake(out var job,Timeout.Infinite))
            {
                try
                {
                    job?.Invoke();
                }
                catch (ThreadInterruptedException)
                {
                    Console.WriteLine("終了");
                    return;
                }
                catch (Exception e)
                {
                    Console.WriteLine(e);
                }
            }
        });

    }
    
}