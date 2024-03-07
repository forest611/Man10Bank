using System.Threading.Tasks;
using Man10BankServer.Data;
using Xunit;

namespace BankTest.System;

public class PlayerTest
{
    
    
    [Fact]
    public async Task GetPlayerNameTest()
    {
        var client = Init.SetupMockWebServer(Init.Name,$"player/mcid?uuid={Init.Uuid}");
        Player.SetTestHttpClient(client);
        var player = await Player.GetFromUuid(Init.Uuid);
        Assert.Equal(player.Name,Init.Name);
    }
    
}