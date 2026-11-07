param(
    [switch]$CheckOnly
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$OutputEncoding = New-Object System.Text.UTF8Encoding($false)

$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$composeFile = Join-Path $projectRoot 'compose.yaml'
$configEnv = Join-Path $projectRoot 'config\.env'

$dockerCommand = Get-Command docker -ErrorAction SilentlyContinue
if ($dockerCommand) {
    $dockerExe = $dockerCommand.Source
} else {
    $dockerExe = @(
        (Join-Path $env:LOCALAPPDATA 'Programs\DockerDesktop\resources\bin\docker.exe'),
        (Join-Path $env:ProgramFiles 'Docker\Docker\resources\bin\docker.exe')
    ) | Where-Object { Test-Path -LiteralPath $_ } | Select-Object -First 1
}

if (-not $dockerExe) {
    throw '找不到 Docker 命令。请检查 Docker Desktop 是否已安装。'
}
if (-not (Test-Path -LiteralPath $configEnv)) {
    throw '缺少 config/.env，无法确认启动时使用的项目配置。'
}

& $dockerExe info --format '{{.ServerVersion}}' 2>$null | Out-Null
if ($LASTEXITCODE -ne 0) {
    Write-Host 'Docker Engine 未运行，项目容器当前也不会运行，无需关闭。'
    exit 0
}

$composeArgs = @('compose', '--env-file', $configEnv, '-f', $composeFile)
$services = @('frontend', 'backend', 'mysql', 'mongo', 'redis', 'pgvector')
$configured = @(& $dockerExe @composeArgs config --services)
if ($LASTEXITCODE -ne 0) {
    throw '无法读取本项目的 Docker Compose 配置，未执行关闭。'
}
foreach ($service in $services) {
    if ($configured -notcontains $service) {
        throw "Docker Compose 配置中缺少 $service，未执行关闭。"
    }
}

if ($CheckOnly) {
    Write-Host '检查通过：将停止本项目的前端、后端、MySQL、MongoDB、Redis 和 pgvector；数据库数据卷会保留。'
    exit 0
}

Write-Host '正在关闭本项目的前端、后端和数据库容器...'
& $dockerExe @composeArgs stop @services
if ($LASTEXITCODE -ne 0) {
    throw '部分容器关闭失败，请查看上方 Docker 输出。'
}

Write-Host '项目服务已关闭。数据库数据卷和容器仍保留；再次双击 start.cmd 即可启动。'
