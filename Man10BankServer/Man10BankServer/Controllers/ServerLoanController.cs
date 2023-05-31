using Man10BankServer.Common;
using Man10BankServer.Model;
using Microsoft.AspNetCore.Mvc;

namespace Man10BankServer.Controllers;

[ApiController]
[Route("[controller]")]
public class ServerLoanController : ControllerBase
{

    [HttpGet("borrowable-amount")]
    public double BorrowableAmount(string uuid)
    {
        return ServerLoan.CalculateLoanAmount(uuid).Result;
    }

    [HttpGet("is-loser")]
    public bool IsLoser(string uuid)
    {
        return ServerLoan.IsLoser(uuid).Result;
    }

    [HttpGet("get-info")]
    public ServerLoanTable? GetInfo(string uuid)
    {
        return ServerLoan.GetBorrowingInfo(uuid).Result;
    }

    [HttpGet("set-info")]
    public string SetInfo([FromBody] ServerLoanTable info)
    {
        return ServerLoan.SetBorrowingInfo(info).Result;
    }

    [HttpGet("try-borrow")]
    public string TryBorrow(string uuid, double amount)
    {
        return ServerLoan.Borrow(uuid, amount).Result;
    }
    
    [HttpGet("pay")]
    public bool Pay(string uuid, double amount)
    {
        return ServerLoan.Pay(uuid,amount).Result;
    }

    [HttpGet("next-pay")]
    public long NextPay(string uuid)
    {
        return ServerLoan.GetNextPayDate(uuid).Result?.Ticks ?? -1;
    }

    [HttpGet("property")]
    public ServerLoanProperty Property()
    {
        return new ServerLoanProperty();
    }
}

public class ServerLoanProperty
{
    public double DailyInterest => ServerLoan.DailyInterest;
    public int PaymentInterval => ServerLoan.PaymentInterval;
    public double MinimumAmount => ServerLoan.MinimumAmount;
    public double MaximumAmount => ServerLoan.MaximumAmount;
}

