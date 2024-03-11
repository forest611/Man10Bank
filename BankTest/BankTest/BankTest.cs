using System;
using System.Collections.Generic;
using System.Linq;
using System.Threading;
using System.Threading.Tasks;
using Man10BankServer.Common;
using Man10BankServer.Data;
using Xunit;
using Xunit.Abstractions;

namespace BankTest;

public class BankTest
{
    private readonly ITestOutputHelper _testOutputHelper;
    private const string PluginName = "UnitTest";
    private const string Note = "UnitTest";
    private static readonly SemaphoreSlim Semaphore = new(1, 1);

    public BankTest(ITestOutputHelper testOutputHelper)
    {
        _testOutputHelper = testOutputHelper;
    }

    [Fact]
    public async Task AddBalanceTest()
    {
        //UserServerのMock
        var client = Init.SetupMockWebServer(Init.Name,$"player/mcid?uuid={Init.Uuid}");
        Player.SetTestHttpClient(client);

        var player = await Player.GetFromUuid(Init.Uuid);
        var bank = await Bank.GetBank(player);
        var firstAmount = await bank.GetBalance();

        await Semaphore.WaitAsync();
        var addAmount = new Money(new Random().Next(1000000));

        try
        {
            await bank.Add(addAmount, PluginName, Note, $"{Note} AddBalanceTest");
            var now = await bank.GetBalance();
            Assert.Equal(firstAmount.Plus(addAmount).Amount,now.Amount);

        }
        catch (Exception)
        {
            Assert.True(false);
            // ignored
        }
        finally
        {
            Semaphore.Release();
        }
    }
    
    [Fact]
    public async Task TakeBalanceTest()
    {
        //UserServerのMock
        var client = Init.SetupMockWebServer(Init.Name,$"player/mcid?uuid={Init.Uuid}");
        Player.SetTestHttpClient(client);

        var player = await Player.GetFromUuid(Init.Uuid);
        var bank = await Bank.GetBank(player);
        var firstAmount = await bank.GetBalance();

        await Semaphore.WaitAsync();
        var takeAmount = new Money(new Random().Next(1000000));

        try
        {
            var result = await bank.Take(takeAmount, PluginName, Note, $"{Note} TakeBalanceTest");
            if (!result && firstAmount.Amount<takeAmount.Amount)
            {
                Assert.True(true);
                return;
            }
            var now = await bank.GetBalance();
            Assert.Equal(firstAmount.Minus(takeAmount).Amount,now.Amount);

        }
        catch (Exception)
        {
            Assert.True(false);
            // ignored
        }
        finally
        {
            Semaphore.Release();
        }
    }

    [Fact]
    public async Task RandomTest()
    {
        //UserServerのMock
        var client = Init.SetupMockWebServer(Init.Name,$"player/mcid?uuid={Init.Uuid}");
        Player.SetTestHttpClient(client);

        var player = await Player.GetFromUuid(Init.Uuid);
        var bank = await Bank.GetBank(player);
        var expectAmount = (await bank.GetBalance()).Amount;
        
        await Semaphore.WaitAsync();
        
        try
        {
            var random = new Random();
            var amountRandom = new Random();
            var tasks = new List<Task>();
            Parallel.ForEach(Enumerable.Range(0, 1000), i =>
            {
                var amount = new Money(amountRandom.Next(1000000));
                var isAdd = random.Next(2) == 1;
                
                if (isAdd)
                {
                    tasks.Add(Task.Run(async () =>
                    {
                        var result = await bank.Add(amount, PluginName, Note, $"{Note} RandomTest {i}");
                        if (result)
                        {
                            expectAmount += amount.Amount;
                        }
                    }));
                }
                else
                {
                    tasks.Add(Task.Run(async () =>
                    {
                        var result = await bank.Take(amount, PluginName, Note, $"{Note} RandomTest {i}");
                        if (result)
                        {
                            expectAmount -= amount.Amount;
                        }
                    }));
                }

            });

            await Task.WhenAll(tasks);
        }
        catch (Exception e)
        {
            Assert.True(false,e.StackTrace ?? "");
            // ignored
        }
        finally
        {
            Semaphore.Release();
        }        
        var now = await bank.GetBalance();
        Assert.Equal(expectAmount,now.Amount);
    }
    
    
}