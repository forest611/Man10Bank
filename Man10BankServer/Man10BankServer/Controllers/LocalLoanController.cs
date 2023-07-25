using Man10BankServer.Common;
using Man10BankServer.Model;
using Microsoft.AspNetCore.Mvc;

namespace Man10BankServer.Controllers;

[ApiController]
[Route("[controller]")]
public class LocalLoanController : ControllerBase
{
    
    [HttpPost("create")]
    public IActionResult CreateLoan([FromBody] LocalLoanTable data)
    {
        var result = LocalLoan.Create(data);
        return Ok(result.Result);
    }

    [HttpGet("pay")]
    public string Pay(int id, double amount)
    {
        var result = LocalLoan.Pay(id, amount);
        return result.Result;
    }

    [HttpGet("get-info")]
    public LocalLoanTable GetInfo(int id)
    {
        var result = LocalLoan.GetInfo(id);
        return result.Result;
    }

    [HttpGet("property")]
    public LocalLoanProperty Property()
    {
        return new LocalLoanProperty();
    }

    [HttpGet("total-loan")]
    public double GetTotalLoan(string uuid)
    {
        return LocalLoan.GetTotalLoan(uuid).Result;
    }
}

public class LocalLoanProperty
{
    public double MinimumInterest => LocalLoan.MinimumInterest;
    public double MaximumInterest => LocalLoan.MaximumInterest;
    public double Fee => LocalLoan.Fee;
}

