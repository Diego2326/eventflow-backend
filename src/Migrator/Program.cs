using EventFlow.Infrastructure;
using Microsoft.EntityFrameworkCore;

var connectionString = Environment.GetEnvironmentVariable("ConnectionStrings__Default");
if (string.IsNullOrWhiteSpace(connectionString))
{
    Console.Error.WriteLine("ConnectionStrings__Default is required.");
    return 1;
}

var options = new DbContextOptionsBuilder<EventFlowDbContext>()
    .UseNpgsql(connectionString)
    .Options;

await using var db = new EventFlowDbContext(options);
await db.Database.OpenConnectionAsync();

// Prevent two Cloud Run Job attempts from changing the schema concurrently.
await db.Database.ExecuteSqlRawAsync("SELECT pg_advisory_lock(7142026)");
try
{
    var pending = (await db.Database.GetPendingMigrationsAsync()).ToArray();
    Console.WriteLine(pending.Length == 0
        ? "Database schema is already current."
        : $"Applying {pending.Length} migration(s): {string.Join(", ", pending)}");

    await db.Database.MigrateAsync();
    await SeedData.EnsureSeededAsync(db);
    if (bool.TryParse(Environment.GetEnvironmentVariable("SeedDemoData__Enabled"), out var seedDemo) && seedDemo)
    {
        await DemoSeedData.EnsureSeededAsync(db);
        Console.WriteLine("Demo users completed.");
    }
    Console.WriteLine("Database migration and catalog seed completed.");
    return 0;
}
finally
{
    await db.Database.ExecuteSqlRawAsync("SELECT pg_advisory_unlock(7142026)");
}
