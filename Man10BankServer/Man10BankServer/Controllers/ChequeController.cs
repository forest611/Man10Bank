using Man10BankServer.Common;
using Man10BankServer.Data;
using Microsoft.AspNetCore.Mvc;

namespace Man10BankServer.Controllers;

[ApiController]
[Route("[controller]")]
public class ChequeController : ControllerBase
{
    
    [HttpGet("create")]
    public async Task<IActionResult> Create(string uuid,double amount,string note)
    {
        var p = await Player.GetFromUuid(uuid);
        if (p.IsEmpty())
        {
            return BadRequest();
        }
        var id = await Cheque.Create(p,new Money(amount),note);
        return id != 0 ? Ok(id) : BadRequest();
    }

    [HttpGet("use")]
    public async Task<IActionResult> Use(string uuid,int id)
    {
        var p = await Player.GetFromUuid(uuid);
        if (p.IsEmpty())
        {
            return NotFound();
        }
        var result = await Cheque.Use(p, id);
        var amount = result.Amount;
        return amount != 0 ? Ok(amount) : NoContent();
    }

    [HttpGet("amount")]
    public async Task<double> Amount(int id)
    {
        return (await Cheque.Amount(id)).Amount;
    }
}