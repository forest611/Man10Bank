using Man10BankServer.Common;
using Microsoft.AspNetCore.Mvc;

namespace Man10BankServer.Controllers;

[ApiController]
[Route("[controller]")]
public class ServerLoanController : ControllerBase
{

    [HttpGet("is-loser")]
    public bool IsLoser(string uuid)
    {
        return false;
    }

    [HttpGet("get-info")]
    public ServerLoanData? GetInfo(string uuid)
    {
        return ServerLoan.GetBorrowingInfo(uuid).Result;
    }

    [HttpPost("set-info")]
    public int SetInfo([FromBody] ServerLoanData info)
    {
        return ServerLoan.SetBorrowingInfo(info).Result;
    }

    [HttpPost("try-borrow")]
    public string TryBorrow(string uuid, double amount)
    {
        return ServerLoan.Borrow(uuid, amount).Result;
    }
    
    [HttpPost("pay")]
    public bool Pay(string uuid, double amount)
    {
        return false;
    }
    
}

public class ServerLoanData
{
    public int OrderID { get; set; }
    public string UUID { get; set; }
    public DateTime BorrowDate { get; set; }
    public DateTime LastPayDate { get; set; }
    public double BorrowAmount { get; set; }
    public double PaymentAmount { get; set; }
    public int FailedPayment { get; set; }
    public bool StopInterest { get; set; }
}

