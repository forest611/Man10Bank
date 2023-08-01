using Man10BankServer.Common;
using Man10BankServer.Model;

var builder = WebApplication.CreateBuilder(args);

// Add services to the container.

builder.Services.AddControllers();
// Learn more about configuring Swagger/OpenAPI at https://aka.ms/aspnetcore/swashbuckle
builder.Services.AddEndpointsApiExplorer();
builder.Services.AddSwaggerGen();
Bank.ConfigureServices(builder.Services);

var app = builder.Build();

Score.LoadConfig(builder.Configuration);
Bank.Configure(app,app.Environment);
Bank.Setup();
// Configure the HTTP request pipeline.
app.UseSwagger();
app.UseSwaggerUI();

app.UseHttpsRedirection();

app.UseAuthorization();

app.MapControllers();

app.Run();

