using System;
using System.IO;
using Man10BankServer.Common;
using Microsoft.Extensions.Configuration;
using Xunit;

namespace BankTest;

[CollectionDefinition("Man10Bank")]
public class Init
{
    public static string Uuid = "9c4161a9-0f5f-4317-835c-0bb196a7defa";

    static Init()
    {
        LoadConfig();
    }
    
    private static void LoadConfig()
    {
        var config = new ConfigurationBuilder()
            .SetBasePath(Directory.GetCurrentDirectory())
            .AddJsonFile("appsettings.json")
            .Build();
        
        Configuration.LoadConfiguration(config);
        Console.WriteLine("LoadConfig");
    }   
}