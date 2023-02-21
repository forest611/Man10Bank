using Man10BankServer.Common;
using Microsoft.AspNetCore.Mvc;

namespace Man10BankServer.Controllers;

[ApiController]
[Route("[controller]")]
public class BankController : ControllerBase
{

    [HttpGet("get-balance")]
    public double GetBalance(string uuid)
    {


        return 0.0;
    }

    [HttpPost("add-balance")]
    public bool AddBalance(string uuid, double amount)
    {
        Bank.AddBalance(uuid,amount,"Test","Test","Test");
        return false;
    }

    [HttpPost("take-balance")]
    public bool TakeBalance(string uuid, double amount)
    {
        Bank.TakeBalance(uuid,amount,"Test","Test","Test");
        return false;
    }
    
}