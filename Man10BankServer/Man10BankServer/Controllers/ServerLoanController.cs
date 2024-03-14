using Man10BankServer.Common;
using Man10BankServer.Data;
using Man10BankServer.Model;
using Microsoft.AspNetCore.Mvc;

namespace Man10BankServer.Controllers;

[ApiController]
[Route("[controller]")]
public class ServerLoanController : ControllerBase
{

    [HttpGet("borrowable-amount")]
    public async Task<IActionResult> BorrowableAmount(string uuid)
    {
        var player = await Player.GetFromUuid(uuid);
        if (player.IsEmpty())
        {
            return NotFound();
        }
        var loan = new ServerLoan(player);
        return Ok(loan.GetBorrowableAmount().Amount);
    }

    [HttpGet("is-loser")]
    public async Task<IActionResult> IsLoser(string uuid)
    {
        var player = await Player.GetFromUuid(uuid);
        if (player.IsEmpty())
        {
            return NotFound();
        }
        var loan = new ServerLoan(player);
        var result = await loan.IsLoser();
        return Ok(result);
    }

    [HttpGet("info")]
    public async Task<IActionResult> GetInfo(string uuid)
    {
        var player = await Player.GetFromUuid(uuid);
        if (player.IsEmpty())
        {
            return NotFound();
        }
        var loan = new ServerLoan(player);
        var result = await loan.GetInfo();
        return result != null ? Ok(result) : NotFound();
    }

    [HttpGet("borrow")]
    public async Task<IActionResult> Borrow(string uuid, double amount)
    {
        var player = await Player.GetFromUuid(uuid);
        if (player.IsEmpty())
        {
            return NotFound();
        }
        var loan = new ServerLoan(player);
        var result = await loan.Borrow(new Money(amount));
        return Ok(result.ToString());
    }
    
    [HttpGet("pay")]
    public async Task<IActionResult> Pay(string uuid, double amount)
    {
        var player = await Player.GetFromUuid(uuid);
        if (player.IsEmpty())
        {
            return NotFound();
        }
        var loan = new ServerLoan(player);
        var result = await loan.Pay(new Money(amount),true);
        return Ok(result.ToString());
    }

    [HttpPost("set-payment")]
    public async void SetPaymentAmount(string uuid, double amount)
    {
        var player = await Player.GetFromUuid(uuid);
        if (player.IsEmpty())
        {
            return;
        }
        var loan = new ServerLoan(player);
        loan.SetPaymentAmount(new Money(amount));
    }

    [HttpPost("add-payment-day")]
    public void AddPaymentDay(int day)
    {
        ServerLoan.AddPaymentDay(day);
    }

    [HttpGet("next-pay")]
    public async Task<IActionResult> NextPay(string uuid)
    {
        var player = await Player.GetFromUuid(uuid);
        if (player.IsEmpty())
        {
            return NotFound();
        }
        var loan = new ServerLoan(player);
        var data = await loan.GetInfo();
        if (data == null)
        {
            return NotFound();
        }
        var nextPayDate = data.last_pay_date.AddDays(ServerLoan.PaymentInterval);
        return Ok(nextPayDate);
    }

    [HttpGet("property")]
    public ServerLoanProperty Property()
    {
        return new ServerLoanProperty();
    }
}

public class ServerLoanProperty
{
    public double DailyInterest => ServerLoan.DailyInterest;
    public int PaymentInterval => ServerLoan.PaymentInterval;
    public double MinimumAmount => ServerLoan.MinimumAmount;
    public double MaximumAmount => ServerLoan.MaximumAmount;
}

