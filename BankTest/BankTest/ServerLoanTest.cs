using System;
using System.Threading;
using System.Threading.Tasks;
using Man10BankServer.Common;
using Man10BankServer.Data;
using Xunit;

namespace BankTest;

public class ServerLoanTest
{

    private static readonly SemaphoreSlim Semaphore = new(1, 1);
    
    [Fact]
    public async Task BorrowTest()
    {
        
        //UserServerのMock
        var client = Init.SetupMockWebServer(Init.Name,$"player/mcid?uuid={Init.Uuid}");
        Player.SetTestHttpClient(client);
        
        await Semaphore.WaitAsync();

        try
        {
            var player = await Player.GetFromUuid(Init.Uuid);
            var loan = new ServerLoan(player);
            var bank = await Bank.GetBank(player);
            var firstBalance = await bank.GetBalance();

            var nowLoan = (await loan.GetInfo())?.borrow_amount ?? 0.0;

            await loan.Pay(new Money(nowLoan), true);
        
            Assert.Equal(0.0,(await loan.GetInfo())?.borrow_amount ?? 0.0);
        
            var overAmount = new Money(1000).Plus(loan.GetBorrowableAmount());
            var overResult = await loan.Borrow(overAmount);
            Assert.Equal(ServerLoan.BorrowResult.Failed,overResult);

            var successAmount = loan.GetBorrowableAmount();
            var successResult = await loan.Borrow(successAmount);
            Assert.NotEqual(ServerLoan.BorrowResult.Failed,successResult);

            var lastBalance = await bank.GetBalance();
        
            Assert.Equal(firstBalance.Plus(successAmount).Amount,lastBalance.Amount);

        }
        catch (Exception)
        {
            // ignored
        }
        finally
        {
            Semaphore.Release();
        }
    }

    [Fact]
    public async Task PayTest()
    {
        //UserServerのMock
        var client = Init.SetupMockWebServer(Init.Name,$"player/mcid?uuid={Init.Uuid}");
        Player.SetTestHttpClient(client);

        await Semaphore.WaitAsync();

        try
        {
            var player = await Player.GetFromUuid(Init.Uuid);
            var loan = new ServerLoan(player);
            var bank = await Bank.GetBank(player);

            var nowLoan = (await loan.GetInfo())?.borrow_amount ?? 0.0;

            await loan.Pay(new Money(nowLoan), true);
        
            Assert.Equal(0.0,(await loan.GetInfo())?.borrow_amount ?? 0.0);
        
            var successAmount = loan.GetBorrowableAmount();
            var successResult = await loan.Borrow(successAmount);
            Assert.NotEqual(ServerLoan.BorrowResult.Failed,successResult);

            var payResult = await loan.Pay(successAmount,true);

            var balance = await bank.GetBalance();

            Assert.Equal(
                balance.Amount < successAmount.Amount
                    ? ServerLoan.PaymentResult.NotEnoughMoney
                    : ServerLoan.PaymentResult.Success, payResult);

        }
        catch (Exception)
        {
            // ignored
        }
        finally
        {
            Semaphore.Release();
        }
    }
    
    
}