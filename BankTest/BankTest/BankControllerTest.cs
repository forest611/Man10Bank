using System.Threading.Tasks;
using Man10BankServer.Common;
using Man10BankServer.Controllers;
using Man10BankServer.Data;
using Microsoft.AspNetCore.Mvc;
using Xunit;

namespace BankTest;

public class BankControllerTest
{
    [Fact]
    public async Task GetBalanceTest()
    {
        //UserServerのMock
        var client = Init.SetupMockWebServer(Init.Name,$"player/mcid?uuid={Init.Uuid}");
        Player.SetTestHttpClient(client);
        
        var controller = new BankController();
        var result = await controller.GetBalance(Init.Uuid);
        var actionResult = Assert.IsType<OkObjectResult>(result);
        var amount = Assert.IsType<double>(actionResult.Value);

        var player = await Player.GetFromUuid(Init.Uuid);
        var bank = await Bank.GetBank(player);
        var trueBalance = await bank.GetBalance();
        Assert.Equal(trueBalance.Amount,amount);

    }
}