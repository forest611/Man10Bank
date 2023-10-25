using Man10BankServer.Common;

var builder = WebApplication.CreateBuilder(args);

// Add services to the container.

builder.Services.AddControllers();
// Learn more about configuring Swagger/OpenAPI at https://aka.ms/aspnetcore/swashbuckle
builder.Services.AddEndpointsApiExplorer();
builder.Services.AddSwaggerGen();

// CORS設定
builder.Services.AddCors(options =>
{
    options.AddPolicy("AllowOriginPolicy", b =>
    {
        b.AllowAnyOrigin() // すべてのオリジンからのリクエストを許可
            .AllowAnyMethod() // すべてのHTTPメソッドを許可 (GET、POST、PUT、DELETEなど)
            .AllowAnyHeader(); // すべてのヘッダーを許可
    });
});

var app = builder.Build();

Score.LoadConfig(builder.Configuration);
Startup.Setup();

app.UseSwagger();
app.UseSwaggerUI();

if (app.Environment.IsDevelopment())
{
    app.UseDeveloperExceptionPage();
}

app.UseRouting();

// CORSポリシーを有効にする
app.UseCors("AllowOriginPolicy");

app.UseAuthorization();

app.UseEndpoints(endpoints =>
{
    endpoints.MapControllers();
});

app.UseHttpsRedirection();

app.MapControllers();

app.Run();

