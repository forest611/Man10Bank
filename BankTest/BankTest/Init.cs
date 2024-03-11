using System;
using System.IO;
using System.Net;
using System.Net.Http;
using System.Threading;
using System.Threading.Tasks;
using Man10BankServer.Common;
using Man10BankServer.Data;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Configuration;
using Moq;
using Moq.Protected;
using Xunit;

namespace BankTest;

[CollectionDefinition("Man10Bank")]
public class Init
{
    public const string Uuid = "9c4161a9-0f5f-4317-835c-0bb196a7defa";
    public const string Name = "forest611";

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

    public static HttpClient SetupMockWebServer(string expect,string arg)
    {
        var uri = Configuration.Man10SystemUrl;
        var mock = new Mock<HttpMessageHandler>();

        mock
            .Protected()
            .Setup<Task<HttpResponseMessage>>(
                "SendAsync",
                ItExpr.Is<HttpRequestMessage>(req =>
                    req.RequestUri != null && req.Method == HttpMethod.Get && req.RequestUri.ToString() == $"{uri}/{arg}"
                ),
                ItExpr.IsAny<CancellationToken>()
            )
            .ReturnsAsync(new HttpResponseMessage
            {
                StatusCode = HttpStatusCode.OK,
                Content = new StringContent(expect)
            })
            .Verifiable();
        
        var httpClient = new HttpClient(mock.Object)
        {
            BaseAddress = new Uri(uri)
        };
        
        return httpClient;
    }
    
}