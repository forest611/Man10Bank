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
    public ServerLoanTable GetInfo(string uuid)
    {
        return ServerLoan.GetBorrowingInfo(uuid).Result;
    }

    [HttpPost("set-info")]
    public int SetInfo([FromBody] ServerLoanTable info)
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

