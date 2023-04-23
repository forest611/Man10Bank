using Man10BankServer.Controllers;
using Microsoft.AspNetCore.Http;

namespace Man10BankTest;

public class BankControllerTest
{
    [Fact]
    public void TestTryConnect()
    {
        var controller = new BankController
        {
            ControllerContext =
            {
                HttpContext = new DefaultHttpContext()
            }
        };

        var result = controller.TryConnect();

        Assert.Equal(0,result);
    }
}