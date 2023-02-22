using Man10BankServer.Common;
using Microsoft.AspNetCore.Mvc;

namespace Man10BankServer.Controllers;

[ApiController]
[Route("[controller]")]
public class ServerLoanController : ControllerBase
{
    [HttpGet("get")]
    public double GetLoan(string uuid)
    {
        return -1;
    }

    [HttpGet("is-loser")]
    public bool IsLoser(string uuid)
    {
        return false;
    }

    [HttpGet("get-info")]
    public ServerLoanTable GetInfo(string uuid)
    {
        return new ServerLoanTable();
    }

    [HttpPost("set-info")]
    public bool SetInfo([FromBody] ServerLoanTable info)
    {
        return false;
    }

    [HttpPost("try-borrow")]
    public bool TryBorrow(string uuid, double amount)
    {
        return false;
    }
    
    [HttpPost("pay")]
    public bool Pay(string uuid, double amount)
    {
        return false;
    }
    
}

