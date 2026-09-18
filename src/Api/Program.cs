using System.Text;
using EventFlow.Api;
using EventFlow.Application;
using EventFlow.Infrastructure;
using Microsoft.AspNetCore.Authentication.JwtBearer;
using Microsoft.EntityFrameworkCore;
using Microsoft.IdentityModel.Tokens;

var builder = WebApplication.CreateBuilder(args);
builder.Configuration.AddEnvironmentVariables();
var connection = builder.Configuration.GetConnectionString("Default") ?? "Host=localhost;Port=5432;Database=eventflow;Username=eventflow;Password=eventflow";
builder.Services.AddDbContext<EventFlowDbContext>(o => o.UseNpgsql(connection));
builder.Services.AddScoped<IPasswordService, PasswordService>(); builder.Services.AddScoped<ITokenService, JwtTokenService>();
builder.Services.AddScoped<INotificationSender, DevelopmentNotificationSender>(); builder.Services.AddScoped<IFileStorage, LocalFileStorage>(); builder.Services.AddHttpContextAccessor(); builder.Services.AddScoped<ICurrentUser, CurrentUser>();
var jwtKey = builder.Configuration["Jwt:Key"] ?? "development-only-key-change-me-32-chars";
builder.Services.AddAuthentication(JwtBearerDefaults.AuthenticationScheme).AddJwtBearer(o => o.TokenValidationParameters = new TokenValidationParameters { ValidateIssuer=true,ValidateAudience=true,ValidateLifetime=true,ValidateIssuerSigningKey=true, ValidIssuer=builder.Configuration["Jwt:Issuer"]??"EventFlow",ValidAudience=builder.Configuration["Jwt:Audience"]??"EventFlowClients",IssuerSigningKey=new SymmetricSecurityKey(Encoding.UTF8.GetBytes(jwtKey)),ClockSkew=TimeSpan.FromSeconds(30) });
builder.Services.AddAuthorization(o => o.AddPolicy("Organizer", p => p.RequireRole("Organizer")));
builder.Services.AddControllers(); builder.Services.AddEndpointsApiExplorer(); builder.Services.AddSwaggerGen();
var allowedOrigins = builder.Configuration.GetSection("Cors:AllowedOrigins").Get<string[]>() ?? ["http://localhost:5173", "http://localhost:8081"];
builder.Services.AddCors(o=>o.AddDefaultPolicy(p=>p.AllowAnyHeader().AllowAnyMethod().WithOrigins(allowedOrigins)));
builder.Services.AddHealthChecks();
var app=builder.Build(); app.UseMiddleware<ErrorHandlingMiddleware>(); app.UseCors(); app.UseStaticFiles(); app.UseSwagger(); app.UseSwaggerUI(); app.UseAuthentication(); app.UseAuthorization(); app.MapControllers(); app.MapHealthChecks("/health");
if (app.Environment.IsDevelopment()) { using var scope=app.Services.CreateScope(); var db=scope.ServiceProvider.GetRequiredService<EventFlowDbContext>(); await db.Database.MigrateAsync(); await SeedData.EnsureSeededAsync(db); if (builder.Configuration.GetValue<bool>("SeedDemoData:Enabled")) await DemoSeedData.EnsureSeededAsync(db); }
app.Run();
public partial class Program { }
