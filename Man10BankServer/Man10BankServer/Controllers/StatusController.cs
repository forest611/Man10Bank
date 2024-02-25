using Man10BankServer.Common;
using Microsoft.AspNetCore.Mvc;

namespace Man10BankServer.Controllers;

[ApiController]
[Route("[controller]")]
public class StatusController : ControllerBase
{
    [HttpGet("get")]
    public Status GetStatus()
    {
        return Status.NowStatus;
    }

    [HttpPost("set")]
    public void SetStatus([FromBody] Status status)
    {
        Status.NowStatus = status;
    }
}