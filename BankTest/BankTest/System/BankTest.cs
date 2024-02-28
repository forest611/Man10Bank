using System;
using System.Threading.Tasks;
using Man10BankServer.Common;
using Man10BankServer.Data;
using Xunit;

namespace BankTest.System;

public class BankTest
{
    private const string PluginName = "UnitTest";
    private const string Note = "UnitTest";
    [Fact]
    public async Task AddBalanceTest()
    {
        var player = await Player.GetFromUuid(Init.Uuid);
        var bank = await Bank.GetBank(player);
        var firstAmount = await bank.GetBalance();
        var addAmount = new Money(new Random().NextDouble()*1000000.0);
        var result = await bank.Add(addAmount, PluginName, Note, Note);
        Assert.True(result);

        var now = await bank.GetBalance();
        
        Assert.Equal(firstAmount.Plus(addAmount).Amount,now.Amount);
    }
}