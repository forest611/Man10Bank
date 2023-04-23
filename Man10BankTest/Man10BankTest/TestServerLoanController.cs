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

        _testOutputHelper.WriteLine($"借入可能額:{ret}");
        
        Assert.True(ret>=0);
    }

    [Fact]
    public void TestGetInfo()
    {
        var controller = GetController();
        var info = controller.GetInfo(UUID);
        if (info == null)
        {
            _testOutputHelper.WriteLine("情報取得に失敗");
            Assert.True(false);
        }
        
        _testOutputHelper.WriteLine($"借入額:{info!.borrow_amount}");
        _testOutputHelper.WriteLine($"支払額:{info.payment_amount}");
        _testOutputHelper.WriteLine($"借入日:{info.borrow_date:yyyy/MM/dd HH:mm:ss}");
        _testOutputHelper.WriteLine($"最終支払日:{info.last_pay_date:yyyy/MM/dd HH:mm:ss}");
        
        Assert.True(true);
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

        if (!ret)
        {
            _testOutputHelper.WriteLine("返済失敗");
            Assert.True(false);
        }

        var last = controller.GetInfo(UUID)?.borrow_amount ?? null;
        
        Assert.Equal(0.0,last);
    }

    [Fact]
    public void TestBorrow()
    {
        var controller = GetController();

        const double amount = 100000;

        var result = controller.TryBorrow(UUID, amount);

        _testOutputHelper.WriteLine(result);
        
        if (result == "Failed")
        {
            _testOutputHelper.WriteLine("借入可能額が少ない");
            Assert.True(false);
        }
        
        Assert.True(true);
    }
    
    [Fact]
    public void TestPay()
    {
        var controller = GetController();

        const double amount = 10000;

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