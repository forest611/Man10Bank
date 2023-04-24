using Man10BankServer.Controllers;
using Microsoft.AspNetCore.Http;
using Xunit.Abstractions;

namespace Man10BankTest;

/// <summary>
/// BankControllerのテストコード
/// </summary>
public class TestBankController
{
    private readonly ITestOutputHelper _testOutputHelper;

    private static string UUID = "9c4161a9-0f5f-4317-835c-0bb196a7defa";
    private static string MCID = "forest611";

    public TestBankController(ITestOutputHelper testOutputHelper)
    {
        _testOutputHelper = testOutputHelper;
    }

    /// <summary>
    /// 接続テスト
    /// </summary>
    [Fact]
    public void TestTryConnect()
    {
        var controller = GetController();

        var ret = controller.TryConnect();
        
        Assert.Equal(0,ret);
    }

    /// <summary>
    /// UUIDの取得テスト
    /// </summary>
    [Fact]
    public void TestGetUUID()
    {
        var controller = GetController();

        controller.CreateBank(UUID, MCID);
        var ret = controller.GetUUID(MCID);
        
        Assert.Equal(UUID,ret);
    }

    /// <summary>
    /// スコアの取得テスト
    /// </summary>
    [Fact]
    public void TestGetScore()
    {
        var controller = GetController();

        var ret = controller.GetScore(UUID);
        
        Assert.NotNull(ret);
    }
    
    /// <summary>
    /// 所持金取得のテスト
    /// </summary>
    [Fact]
    public void TestGetBalance()
    {
        var controller = GetController();
        
        var ret = controller.GetBalance(UUID);
        
        Assert.True(ret>=0);
    }

    /// <summary>
    /// 所持金追加テスト
    /// </summary>
    [Fact]
    public void TestAddBalance()
    {
        var controller = GetController();
        const double amount = 1000.0;
        const int count = 100;
        const double first = 0.0;
        
        //所持金のリセットをする
        SetBalance(controller,first);

        var data = new TransactionData()
        {
            UUID = UUID,
            Amount = amount,
            Plugin = "Man10BankUnitTest",
            Note = "AddBalance",
            DisplayNote = "AddBalance"
        };
        
        for (var i = 0; i < count; i++)
        {
            controller.AddBalance(data);
        }

        var ret = controller.GetBalance(UUID);
        
        Assert.Equal(amount*count,ret);
    }

    /// <summary>
    /// 所持金の取り出しテスト
    /// </summary>
    [Fact]
    public void TestTakeBalance()
    {
        var controller = GetController();
        const double amount = 1000.0;
        const int count = 100;
        
        const double first = 1000000.0;
        
        SetBalance(controller,first);

        var data = new TransactionData()
        {
            UUID = UUID,
            Amount = amount,
            Plugin = "Man10BankUnitTest",
            Note = "AddBalance",
            DisplayNote = "AddBalance"
        };
        
        for (var i = 0; i < count; i++)
        {
            controller.TakeBalance(data);
        }

        var ret = controller.GetBalance(UUID);
        
        Assert.Equal(first-(amount*count),ret);
    }

    [Fact]
    public void TestAsyncAddBank()
    {
        var controller = GetController();
        var numbers = new[] { 1, 2, 3, 4, 5, 6 };
        var totalExe = 0;
        const double amount = 1000.0;
        const double first = 0.0;
        const int loop = 100;
        
        SetBalance(controller,first);
        
        var data = new TransactionData
        {
            UUID = UUID,
            Amount = amount,
            Plugin = "Man10BankUnitTest",
            Note = "AddBalance",
            DisplayNote = "AddBalance"
        };
        Parallel.ForEach(numbers,(_,_) =>
        {
            Interlocked.Increment(ref totalExe);

            var pController = GetController();

            for (var i = 0; i<loop; i++)
            {
                pController.AddBalance(data);
            }
        });
        _testOutputHelper.WriteLine($"Count:{totalExe}");
        _testOutputHelper.WriteLine($"AddAmount:{amount*loop*totalExe}");

        var ret = controller.GetBalance(UUID);

        Assert.Equal(amount*loop*totalExe,ret);
    }

    [Fact]
    public void TestAsyncTakeBank()
    {
        var controller = GetController();

        var numbers = new[] { 1, 2, 3, 4, 5, 6};
        var totalExe = 0;
        const double amount = 1000.0;
        const double first = 10000000.0;
        const int loop = 100;
        
        SetBalance(controller,first);
        
        var data = new TransactionData
        {
            UUID = UUID,
            Amount = amount,
            Plugin = "Man10BankUnitTest",
            Note = "AddBalance",
            DisplayNote = "AddBalance"
        };
        Parallel.ForEach(numbers,(_,_) =>
        {
            Interlocked.Increment(ref totalExe);

            var pController = GetController();

            for (var i = 0; i < loop; i++)
            {
                pController.TakeBalance(data);
            }
        });

        _testOutputHelper.WriteLine($"Count:{totalExe}");
        _testOutputHelper.WriteLine($"TakeAmount:{amount*loop*totalExe}");
        
        var ret = controller.GetBalance(UUID);

        Assert.Equal(first-(amount*loop*totalExe),ret);
    }

    
    private static BankController GetController()
    {
        var controller = new BankController
        {
            ControllerContext =
            {
                HttpContext = new DefaultHttpContext()
            }
        };
        return controller;
    }

    private static void SetBalance(BankController controller,double firstAmount)
    {
        
        TransactionData setBalance = new()
        {
            UUID = UUID,
            Amount = firstAmount,
            Plugin = "Man10BankUnitTest",
            Note = "ResetBalance",
            DisplayNote = "ResetBalance"
        };

        controller.SetBalance(setBalance);
    }
    
}