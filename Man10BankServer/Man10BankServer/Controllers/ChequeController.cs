using Microsoft.AspNetCore.Mvc;

namespace Man10BankServer.Controllers;

[ApiController]
[Route("[controller]")]
public class ChequeController : ControllerBase
{
    [HttpPost("create")]
    public bool Create(string uuid,double amount,string note)
    {
        return false;
    }

    [HttpPost("try-use")]
    public bool TryUse(int id)
    {
        return false;
    }
}