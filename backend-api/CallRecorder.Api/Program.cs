using Microsoft.EntityFrameworkCore;
using StackExchange.Redis;
using CallRecorder.Api.Data;
using CallRecorder.Api.Services;

var builder = WebApplication.CreateBuilder(args);

// Controllers + Swagger
builder.Services.AddControllers();
builder.Services.AddEndpointsApiExplorer();
builder.Services.AddSwaggerGen();

// Oracle / EF Core
builder.Services.AddDbContext<AppDbContext>(options =>
    options.UseOracle(builder.Configuration.GetConnectionString("OracleDb")));

// Redis
builder.Services.AddSingleton<IConnectionMultiplexer>(sp =>
    ConnectionMultiplexer.Connect(builder.Configuration.GetConnectionString("Redis")!));
builder.Services.AddSingleton<IRedisCacheService, RedisCacheService>();

// Depolama ve FCM
builder.Services.AddSingleton<IRecordingStorageService, LocalDiskStorageService>();
builder.Services.AddSingleton<IFcmNotificationService, FcmNotificationService>();

// CORS (React Native uygulaması için)
builder.Services.AddCors(options =>
{
    options.AddPolicy("MobileApp", policy =>
    {
        policy.AllowAnyOrigin().AllowAnyMethod().AllowAnyHeader();
    });
});

var app = builder.Build();

if (app.Environment.IsDevelopment())
{
    app.UseSwagger();
    app.UseSwaggerUI();
}

app.UseHttpsRedirection();
app.UseCors("MobileApp");
app.UseAuthorization();
app.MapControllers();

app.Run();
