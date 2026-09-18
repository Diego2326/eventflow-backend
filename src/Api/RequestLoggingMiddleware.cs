using System.Diagnostics;

namespace EventFlow.Api;

public sealed class RequestLoggingMiddleware(RequestDelegate next, ILogger<RequestLoggingMiddleware> logger)
{
    public async Task Invoke(HttpContext context)
    {
        var traceId = Activity.Current?.TraceId.ToString() ?? context.TraceIdentifier;
        context.Response.Headers["X-Trace-Id"] = traceId;
        var stopwatch = Stopwatch.StartNew();
        try
        {
            await next(context);
        }
        finally
        {
            stopwatch.Stop();
            logger.LogInformation("HTTP {Method} {Path} responded {StatusCode} in {ElapsedMs} ms trace={TraceId}",
                context.Request.Method, context.Request.Path, context.Response.StatusCode,
                stopwatch.Elapsed.TotalMilliseconds, traceId);
        }
    }
}
