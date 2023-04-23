using Man10BankServer.Controllers;
using Microsoft.AspNetCore.Http;
using Xunit.Abstractions;

namespace Man10BankTest;

public class TestServerLoanController
{
    private readonly ITestOutputHelper _testOutputHelper;
    private static string UUID = "9c4161a9-0f5f-4317-835c-0bb196a7defa";
    private static string MCID = "forest611";

    public TestServerLoanController(ITestOutputHelper testOutputHelper)
    {
        _testOutputHelper = testOutputHelper;
    }

    [Fact]
    public void TestGetBorrowableAmount()
    {

        var controller = GetController();

        var ret = controller.BorrowableAmount(UUID);

        Assert.True(ret>=0);
    }

    [Fact]
    public void TestBorrowAndPay()
    {
        var controller = GetController();

        var amount = controller.BorrowableAmount(UUID);

        var retBorrow = controller.TryBorrow(UUID, amount);

        if (retBorrow == "Failed")
        {
            _testOutputHelper.WriteLine("借入可能額が少ない");
            Assert.True(false);
        }
        
        var ret = controller.Pay(UUID, amount);
        
        Assert.True(ret);
    }
    
    private static ServerLoanController GetController()
    {
        var controller = new ServerLoanController()
        {
            ControllerContext =
            {
                HttpContext = new DefaultHttpContext()
            }
        };
        return controller;
    }
}