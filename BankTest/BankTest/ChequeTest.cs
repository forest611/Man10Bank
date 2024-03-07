using System;
using System.Threading.Tasks;
using Man10BankServer.Common;
using Man10BankServer.Data;
using Xunit;

namespace BankTest;

public class ChequeTest
{
    [Fact]
    public async Task ChequeAllTest()
    {
        //UserServerのMock
        var client = Init.SetupMockWebServer(Init.Name,$"player/mcid?uuid={Init.Uuid}");
        Player.SetTestHttpClient(client);

        var player = await Player.GetFromUuid(Init.Uuid);
        var amount = new Money(new Random().Next(100000));

        var id = await Cheque.Create(player, amount, "UnitTest");

        var firstResult = await Cheque.Use(player, id);
        
        Assert.Equal(firstResult.Amount,amount.Amount);

        var secondResult = await Cheque.Use(player, id);
        Assert.NotEqual(amount.Amount,secondResult.Amount);
    }
}