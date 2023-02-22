using Microsoft.AspNetCore.Mvc;

namespace Man10BankServer.Controllers;

[ApiController]
[Route("[controller]")]
public class LocalLoanController : ControllerBase
{
    
    [HttpPost("create")]
    public bool CreateLoan()
    {
        return false;
    }
    
}