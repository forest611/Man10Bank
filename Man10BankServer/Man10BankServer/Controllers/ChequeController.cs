using Man10BankServer.Common;
using Man10BankServer.Data;
using Microsoft.AspNetCore.Mvc;

namespace Man10BankServer.Controllers;

[ApiController]
[Route("[controller]")]
public class ChequeController : ControllerBase
{
    
    [HttpGet("create")]
    public async Task<int> Create(string uuid,double amount,string note)
    {
        var p = await Player.GetFromUuid(uuid);
        if (p.IsEmpty())
        {
            return 0;
        }
        return await Cheque.Create(p,new Money(amount),note);
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
}