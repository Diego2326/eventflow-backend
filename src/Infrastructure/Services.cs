using System.Security.Cryptography;
using System.Text;
using EventFlow.Application;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.Logging;
using Microsoft.EntityFrameworkCore;

namespace EventFlow.Infrastructure;

public sealed class PasswordService : IPasswordService
{
    private const int Iterations = 210_000;
    public string Hash(string password) { var salt = RandomNumberGenerator.GetBytes(16); var key = Rfc2898DeriveBytes.Pbkdf2(password, salt, Iterations, HashAlgorithmName.SHA256, 32); return $"pbkdf2-sha256${Iterations}${Convert.ToBase64String(salt)}${Convert.ToBase64String(key)}"; }
    public bool Verify(string hash, string password) { try { var p = hash.Split('$'); var salt = Convert.FromBase64String(p[2]); var expected = Convert.FromBase64String(p[3]); var actual = Rfc2898DeriveBytes.Pbkdf2(password, salt, int.Parse(p[1]), HashAlgorithmName.SHA256, expected.Length); return CryptographicOperations.FixedTimeEquals(expected, actual); } catch { return false; } }
}

public sealed class DevelopmentNotificationSender(ILogger<DevelopmentNotificationSender> logger, IConfiguration configuration) : INotificationSender
{
    public Task SendVerificationAsync(string email, string token, CancellationToken ct) { logger.LogInformation("DEV verification for {Email}: {Url}/verify?token={Token}", email, configuration["App:PublicUrl"], token); return Task.CompletedTask; }
    public Task SendPasswordResetAsync(string email, string token, CancellationToken ct) { logger.LogInformation("DEV password reset for {Email}: {Url}/reset-password?token={Token}", email, configuration["App:PublicUrl"], token); return Task.CompletedTask; }
}

public sealed class LocalFileStorage(IConfiguration configuration) : IFileStorage
{
    public async Task<string> SaveAsync(Stream stream, string fileName, string contentType, CancellationToken ct)
    {
        if (!contentType.StartsWith("image/", StringComparison.OrdinalIgnoreCase)) throw new AppException(400, "invalid_file", "Solo se permiten imágenes.");
        var extension = Path.GetExtension(fileName).ToLowerInvariant(); if (extension is not (".jpg" or ".jpeg" or ".png" or ".webp")) throw new AppException(400, "invalid_file", "Formato de imagen no permitido.");
        var root = configuration["Storage:LocalPath"] ?? "uploads"; Directory.CreateDirectory(root); var name = $"{Guid.NewGuid():N}{extension}";
        await using var output = File.Create(Path.Combine(root, name)); await stream.CopyToAsync(output, ct); return $"/uploads/{name}";
    }
}

public static class SeedData
{
    private static readonly (string Code,string Name,string Category)[] Catalog =
    [
        ("INV","Invitaciones","Acceso"),("GST","Gestión de invitados","Acceso"),("CAL","Agenda","Organización"),("MAP","Mapas","Organización"),("BKG","Reservas internas","Organización"),
        ("AST","Asistencia","Atención"),("ORD","Pedidos","Atención"),("QUE","Colas virtuales","Atención"),("INT","Interacción","Participación"),("GAM","Gamificación","Participación"),
        ("GAL","Galería","Social"),("NET","Networking","Social"),("MSG","Mensajería","Social"),("TRN","Transporte","Logística"),("LNF","Objetos perdidos","Logística"),("AFO","Aforo","Logística"),
        ("RSC","Recursos","Contenido"),("SES","Sesiones","Contenido"),("EXH","Expositores","Contenido"),("SPT","Torneos y resultados","Competencia"),("NOT","Notificaciones","Comunicación")
    ];
    private static readonly Dictionary<string,string[]> Templates = new()
    {
        ["WEDDING"]=["INV","GST","CAL","MAP","ORD","AST","GAL","INT","TRN","NOT"], ["BIRTHDAY"]=["INV","GST","CAL","MAP","GAL","INT","GAM","ORD","NOT"],
        ["GRADUATION"]=["INV","GST","CAL","MAP","GAL","RSC","NOT"], ["CONFERENCE"]=["INV","GST","CAL","MAP","SES","INT","NET","RSC","QUE","NOT"],
        ["CORPORATE"]=["INV","GST","CAL","MAP","NET","INT","RSC","NOT"], ["EXPO"]=["INV","GST","MAP","EXH","GAM","QUE","NET","RSC","NOT"],
        ["JOB_FAIR"]=["INV","GST","MAP","EXH","NET","QUE","RSC","NOT"], ["FESTIVAL"]=["INV","GST","CAL","MAP","QUE","AFO","TRN","LNF","NOT"],
        ["TOURNAMENT"]=["INV","GST","CAL","MAP","SPT","INT","AFO","NOT"], ["HACKATHON"]=["INV","GST","CAL","MAP","NET","INT","RSC","BKG","NOT"],
        ["WORKSHOP"]=["INV","GST","CAL","SES","INT","RSC","NOT"], ["RETREAT"]=["INV","GST","CAL","MAP","TRN","AST","AFO","NOT"],
        ["TRIP"]=["INV","GST","CAL","MAP","TRN","NOT"], ["GALA"]=["INV","GST","CAL","MAP","ORD","INT","GAL","NOT"], ["CUSTOM"]=[]
    };
    public static async Task EnsureSeededAsync(EventFlowDbContext db, CancellationToken ct = default)
    {
        if (await db.Modules.AnyAsync(ct)) return;
        var modules = Catalog.Select(x => new Domain.Module { Code=x.Code, Name=x.Name, Category=x.Category, Description=$"Módulo {x.Name.ToLowerInvariant()} para la experiencia del evento.", Icon=x.Code.ToLowerInvariant() }).ToList();
        db.Modules.AddRange(modules);
        foreach (var pair in Templates) { var t = new Domain.ModuleTemplate { Code=pair.Key, Name=TemplateName(pair.Key) }; for(var i=0;i<pair.Value.Length;i++) t.Items.Add(new Domain.ModuleTemplateItem { Module=modules.Single(m=>m.Code==pair.Value[i]), DisplayOrder=i }); db.ModuleTemplates.Add(t); }
        void Depends(string code,string required) => db.ModuleDependencies.Add(new Domain.ModuleDependency { Module=modules.Single(m=>m.Code==code), RequiredModule=modules.Single(m=>m.Code==required) });
        Depends("SES","CAL"); Depends("QUE","GST"); Depends("AFO","GST"); Depends("SPT","CAL"); Depends("BKG","CAL");
        var permissions = new[] { "events.read", "events.manage", "modules.manage", "profile.manage" }.Select(x=>new Domain.Permission{Code=x,Description=x}).ToList();
        var organizer = new Domain.Role { Name="Organizer" }; organizer.RolePermissions=permissions.Select(x=>new Domain.RolePermission{Role=organizer,Permission=x}).ToList(); db.Roles.Add(organizer);
        await db.SaveChangesAsync(ct);
    }
    private static string TemplateName(string code) => code switch { "WEDDING"=>"Boda", "BIRTHDAY"=>"XV años / Cumpleaños", "GRADUATION"=>"Graduación", "CONFERENCE"=>"Congreso / Conferencia", "CORPORATE"=>"Evento empresarial", "EXPO"=>"Feria / Exposición", "JOB_FAIR"=>"Feria de empleo", "FESTIVAL"=>"Concierto / Festival", "TOURNAMENT"=>"Torneo deportivo / gaming", "HACKATHON"=>"Hackathon", "WORKSHOP"=>"Taller / Capacitación", "RETREAT"=>"Retiro / Campamento", "TRIP"=>"Excursión / Viaje grupal", "GALA"=>"Cena / Gala / Premiación", _=>"Personalizado" };
}

public static class DemoSeedData
{
    private static readonly (string Name, string Email, string Phone, string Password)[] DemoUsers =
    [
        ("Ana Organizadora", "ana.organizadora@eventflow.demo", "+502 5555 0101", "EventFlowDemo1!"),
        ("Carlos Organizador", "carlos.organizador@eventflow.demo", "+502 5555 0102", "EventFlowDemo2!"),
    ];

    public static async Task EnsureSeededAsync(EventFlowDbContext db, CancellationToken ct = default)
    {
        var organizer = await db.Roles.SingleAsync(role => role.Name == "Organizer", ct);
        var passwordService = new PasswordService();

        foreach (var demo in DemoUsers)
        {
            var normalizedEmail = demo.Email.ToUpperInvariant();
            if (await db.Users.AnyAsync(user => user.NormalizedEmail == normalizedEmail, ct)) continue;

            var user = new Domain.User
            {
                Name = demo.Name,
                Email = demo.Email,
                NormalizedEmail = normalizedEmail,
                Phone = demo.Phone,
                NormalizedPhone = new string(demo.Phone.Where(char.IsDigit).ToArray()),
                PasswordHash = passwordService.Hash(demo.Password),
                Status = Domain.AccountStatus.Active,
            };
            user.Preference.User = user;
            user.UserRoles.Add(new Domain.UserRole { User = user, Role = organizer });
            db.Users.Add(user);
        }

        await db.SaveChangesAsync(ct);
    }
}
