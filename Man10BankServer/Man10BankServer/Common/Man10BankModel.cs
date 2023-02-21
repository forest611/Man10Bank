using System.ComponentModel.DataAnnotations;
using Microsoft.EntityFrameworkCore;

namespace Man10BankServer.Common;

/// <summary>
/// UserBankテーブル
/// </summary>
public class UserBank
{
    [Key]
    public int id { get; set; }
    public string player { get; set; }
    public string uuid { get; set; }
    public double balance { get; set; }

}

public class Context : DbContext
{
    
    public DbSet<UserBank> user_Bank { get; set; }
    
    private static string Host { get; }
    private static string Port { get; }
    private static string Pass { get; }
    private static string User { get; }
    private static string DatabaseName { get; }
    /// <summary>
    /// DBの接続設定を読み込む
    /// </summary>
    static Context()
    {
        Host = "localhost";
        Port = "3306";
        Pass = "rDcrmPRLJvu@ex/E,>K";
        User = "forest";
        DatabaseName = "man10_bank";
    }
    
    protected override void OnConfiguring(DbContextOptionsBuilder optionsBuilder)
    {
        var connectionString = $"server='{Host}';port='{Port}';user='{User}';password='{Pass}';Database='{DatabaseName}'";
        var serverVersion = new MySqlServerVersion(new Version(8, 0, 30));
        optionsBuilder.UseMySql(connectionString, serverVersion);
    }
}
