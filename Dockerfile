# syntax=docker/dockerfile:1
FROM mcr.microsoft.com/dotnet/sdk:10.0 AS build
WORKDIR /src
COPY ["src/Api/EventFlow.Api.csproj", "src/Api/"]
COPY ["src/Application/EventFlow.Application.csproj", "src/Application/"]
COPY ["src/Domain/EventFlow.Domain.csproj", "src/Domain/"]
COPY ["src/Infrastructure/EventFlow.Infrastructure.csproj", "src/Infrastructure/"]
RUN dotnet restore "src/Api/EventFlow.Api.csproj"
COPY . .
RUN dotnet publish "src/Api/EventFlow.Api.csproj" --configuration Release --output /app/publish --no-restore /p:UseAppHost=false

FROM mcr.microsoft.com/dotnet/aspnet:10.0 AS runtime
WORKDIR /app
ENV ASPNETCORE_ENVIRONMENT=Production ASPNETCORE_URLS=http://0.0.0.0:8080 DOTNET_EnableDiagnostics=0
COPY --from=build /app/publish .
RUN mkdir -p /app/uploads && chown -R "$APP_UID:$APP_UID" /app
USER $APP_UID
EXPOSE 8080
ENTRYPOINT ["dotnet", "EventFlow.Api.dll"]
