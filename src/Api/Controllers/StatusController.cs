using System.Reflection;
using Microsoft.AspNetCore.Mvc;

namespace EventFlow.Api.Controllers;

[ApiController]
[Route("status")]
public sealed class StatusController(IHostEnvironment environment) : ControllerBase
{
    private static readonly DateTimeOffset StartedAt = DateTimeOffset.UtcNow;

    [HttpGet]
    [Produces("text/html")]
    public ContentResult Page()
    {
        var apiVersion = Assembly.GetEntryAssembly()?.GetName().Version?.ToString() ?? "unknown";
        var environmentName = System.Net.WebUtility.HtmlEncode(environment.EnvironmentName);
        var version = System.Net.WebUtility.HtmlEncode(apiVersion);
        var started = StartedAt.ToString("u");
        var html = $$"""
            <!doctype html>
            <html lang="es">
            <head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
            <title>EventFlow · Estado de la API</title>
            <style>
              :root { color-scheme: dark; font-family: Inter, system-ui, sans-serif; background:#0c1b2e; color:#f7f4ed; }
              body { max-width:900px; margin:0 auto; padding:48px 24px; }
              .brand { color:#ff6847; letter-spacing:.08em; text-transform:uppercase; font-weight:700; }
              h1 { font-size:clamp(2rem,5vw,3.5rem); margin:.4rem 0 2rem; }
              .card { background:#132945; border:1px solid #284361; border-radius:18px; padding:24px; margin:16px 0; }
              .ok { color:#66e3ae; font-weight:700; }
              .grid { display:grid; grid-template-columns:repeat(auto-fit,minmax(180px,1fr)); gap:12px; }
              .label { color:#9fb1c7; font-size:.8rem; text-transform:uppercase; letter-spacing:.08em; }
              a { color:#ff957b; } code { color:#c7d7e8; }
            </style></head>
            <body><div class="brand">EventFlow / Operations</div><h1>API en línea <span class="ok">●</span></h1>
            <div class="card grid">
              <div><div class="label">Entorno</div><strong>{{environmentName}}</strong></div>
              <div><div class="label">Versión</div><strong>{{version}}</strong></div>
              <div><div class="label">Inicio del proceso</div><strong>{{started}} UTC</strong></div>
            </div>
            <div class="card"><strong>Enlaces operativos</strong><p><a href="/swagger">Swagger UI</a> · <a href="/health">Health completo</a> · <a href="/health/live">Liveness</a> · <a href="/health/ready">Readiness</a></p><p>Los logs estructurados se emiten a stdout/stderr para Cloud Run y aparecen en <a href="https://console.cloud.google.com/logs">Cloud Logging</a>.</p></div>
            </body></html>
            """;
        return Content(html, "text/html; charset=utf-8");
    }

    [HttpGet("json")]
    public IActionResult Json() => Ok(new
    {
        status = "ok",
        service = "eventflow-api",
        environment = environment.EnvironmentName,
        version = Assembly.GetEntryAssembly()?.GetName().Version?.ToString() ?? "unknown",
        startedAt = StartedAt,
        uptimeSeconds = Math.Round((DateTimeOffset.UtcNow - StartedAt).TotalSeconds, 1),
        links = new { swagger = "/swagger", health = "/health", readiness = "/health/ready", liveness = "/health/live" }
    });
}
