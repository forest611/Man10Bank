using Man10BankServer.Common;
using Microsoft.AspNetCore.Mvc;

namespace Man10BankServer.Controllers;

[ApiController]
[Route("[controller]")]
public class LocalLoanController : ControllerBase
{
    
    [HttpPost("create")]
    public int CreateLoan([FromBody] LocalLoanData data)
    {
        var result = LocalLoan.Create(data);
        return result.Result;
    }

    [HttpPost("pay")]
    public string Pay(int id, double amount)
    {
        var result = LocalLoan.Pay(id, amount);
        return result.Result;
    }

    [HttpGet("get-info")]
    public LocalLoanTable? GetInfo(int id)
    {
        var result = LocalLoan.GetInfo(id);
        return result.Result;
    }
    
}