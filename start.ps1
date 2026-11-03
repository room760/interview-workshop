param(
    [switch]$NoBrowser,
    [switch]$NoBuild
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$composeFile = Join-Path $projectRoot 'compose.yaml'
$configDir = Join-Path $projectRoot 'config'
$configEnv = Join-Path $configDir '.env'
$aiConfig = Join-Path $configDir 'ai-credentials.sql'

$dockerCommand = Get-Command docker -ErrorAction SilentlyContinue
if ($dockerCommand) {
    $dockerExe = $dockerCommand.Source
} else {
    $dockerCandidates = @(
        (Join-Path $env:LOCALAPPDATA 'Programs\DockerDesktop\resources\bin\docker.exe'),
        (Join-Path $env:ProgramFiles 'Docker\Docker\resources\bin\docker.exe')
    )
    $dockerExe = $dockerCandidates | Where-Object { Test-Path -LiteralPath $_ } | Select-Object -First 1
}

if (-not $dockerExe) {
    throw '找不到 Docker 命令。请先安装并启动 Docker Desktop。'
}

& $dockerExe info --format '{{.ServerVersion}}' 2>$null | Out-Null
if ($LASTEXITCODE -ne 0) {
    throw 'Docker Engine 尚未就绪。请启动 Docker Desktop 后重试。'
}

if (-not (Test-Path -LiteralPath $configEnv)) {
    Copy-Item -LiteralPath (Join-Path $configDir '.env.example') -Destination $configEnv
    Write-Host '已创建 config/.env；如需真实 AI 调用，请填写自己的密钥。'
}

$OutputEncoding = New-Object System.Text.UTF8Encoding($false)
Set-Location -LiteralPath $projectRoot

Write-Host '正在启动数据库和缓存...'
& $dockerExe compose --env-file $configEnv -f $composeFile up -d --wait --wait-timeout 180 mysql mongo redis pgvector
if ($LASTEXITCODE -ne 0) {
    throw '基础服务启动失败。可运行 docker compose -f compose.yaml ps 查看状态。'
}

if (Test-Path -LiteralPath $aiConfig) {
    Write-Host '正在载入 config/ai-credentials.sql 中的本机 AI 配置...'
    Get-Content -LiteralPath $aiConfig -Raw -Encoding UTF8 |
        & $dockerExe compose --env-file $configEnv -f $composeFile exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot "$MYSQL_DATABASE"' 2>$null | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw 'config/ai-credentials.sql 导入失败。'
    }
} else {
    Write-Host '当前没有本机私有 AI 配置；网页和后端可启动，真实 AI 调用需补充自己的密钥。'
}

Write-Host '正在启动后端和前端...'
$upArgs = @('compose', '--env-file', $configEnv, '-f', $composeFile, 'up', '-d', '--wait', '--wait-timeout', '300')
if (-not $NoBuild) {
    $upArgs += '--build'
}
$upArgs += @('backend', 'frontend')
& $dockerExe @upArgs
if ($LASTEXITCODE -ne 0) {
    throw '前后端启动失败。可运行 docker compose -f compose.yaml ps 查看状态。'
}

Write-Host ''
Write-Host '启动完成：http://localhost:8080/'
Write-Host '后端健康检查：http://localhost:8002/actuator/health'
if (-not $NoBrowser) {
    Start-Process 'http://localhost:8080/'
}
