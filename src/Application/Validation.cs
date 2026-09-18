namespace EventFlow.Application;

public static class InputValidation
{
    public static bool ValidEmail(string email) => System.Net.Mail.MailAddress.TryCreate(email, out _);
    public static bool ValidPassword(string value) => value.Length >= 10 && value.Any(char.IsUpper) && value.Any(char.IsLower) && value.Any(char.IsDigit) && value.Any(c => !char.IsLetterOrDigit(c));
}

public sealed class AppException(int statusCode, string code, string message) : Exception(message)
{
    public int StatusCode { get; } = statusCode; public string Code { get; } = code;
}
