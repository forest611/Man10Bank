namespace Man10BankServer.Data;

public class Money
{
    public double Amount { get; }

    public Money(double amount)
    {
        if (amount < 0)
        {
            throw new ArgumentException("マイナスの金額は設定不可", nameof(amount));
        }

        if (!IsInteger(amount))
        {
            throw new ArgumentException("整数値のみが設定可能", nameof(amount));
        }
        
        Amount = amount;
    }

    public Money Plus(double amount) => new(Amount + amount);
    public Money Plus(Money money) => new(Amount + money.Amount);
    public Money Minus(double amount) => new(Amount - amount);
    public Money Minus(Money money) => new(Amount - money.Amount);
    
    private static bool IsInteger(double value)
    {
        return Math.Abs(value - Math.Floor(value)) <= 0;
    }
}