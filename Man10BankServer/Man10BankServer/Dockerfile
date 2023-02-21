FROM mcr.microsoft.com/dotnet/aspnet:6.0 AS base
WORKDIR /app
EXPOSE 80
EXPOSE 443

FROM mcr.microsoft.com/dotnet/sdk:6.0 AS build
WORKDIR /src
COPY ["Man10BankServer/Man10BankServer.csproj", "Man10BankServer/"]
RUN dotnet restore "Man10BankServer/Man10BankServer.csproj"
COPY . .
WORKDIR "/src/Man10BankServer"
RUN dotnet build "Man10BankServer.csproj" -c Release -o /app/build

FROM build AS publish
RUN dotnet publish "Man10BankServer.csproj" -c Release -o /app/publish

FROM base AS final
WORKDIR /app
COPY --from=publish /app/publish .
ENTRYPOINT ["dotnet", "Man10BankServer.dll"]
