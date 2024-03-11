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

Configuration.LoadConfiguration(builder.Configuration);

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

app.Use(async (context, next) =>
{
    //Http認証
    if (!Authentication.CheckAuthentication(context))
    {
        return;
    }
    
    //UserServerに繋がらなかったら、ショートして500を返す
    if (!Status.NowStatus.EnableAccessUserServer)
    {
        context.Response.StatusCode = StatusCodes.Status500InternalServerError;
        return;
    }
    await next.Invoke();
});

app.UseEndpoints(endpoints =>
{
    endpoints.MapControllers();
});

app.UseHttpsRedirection();

app.MapControllers();

app.Run();

