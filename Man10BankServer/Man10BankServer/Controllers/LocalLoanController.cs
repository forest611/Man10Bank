using Man10BankServer.Common;
using Man10BankServer.Data;
using Man10BankServer.Model;
using Microsoft.AspNetCore.Mvc;

namespace Man10BankServer.Controllers;

[ApiController]
[Route("[controller]")]
public class LocalLoanController : ControllerBase
{
    
    [HttpPost("create")]
    public async Task<IActionResult> CreateLoan([FromBody] LocalLoanTable data)
    {
        var result = await LocalLoan.Create(data);
        return result != 0 ? Ok(result) : StatusCode(500,"Server Error");
    }

    [HttpGet("pay")]
    public async Task<IActionResult> Pay(int id, double amount)
    {
        var result = await LocalLoan.Pay(id, new Money(amount));
        return Ok(result);
    }

    [HttpGet("get-info")]
    public async Task<IActionResult> GetInfo(int id)
    {
        var result = await LocalLoan.GetInfo(id);
        return result != null ? Ok(result) : NotFound();
    }

    [HttpGet("property")]
    public LocalLoanProperty Property()
    {
        return new LocalLoanProperty();
    }

    [HttpGet("total-loan")]
    public async Task<double> GetTotalLoan(string uuid)
    {
        var result = await LocalLoan.GetTotalLoan(uuid);
        return result.Amount;
    }
}

public class LocalLoanProperty
{
    public double MinimumInterest => LocalLoan.MinimumInterest;
    public double MaximumInterest => LocalLoan.MaximumInterest;
    public double Fee => LocalLoan.Fee;
}

