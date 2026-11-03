param()

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$utf8 = New-Object System.Text.UTF8Encoding($false)
[Console]::OutputEncoding = $utf8
$OutputEncoding = $utf8

$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$configEnv = Join-Path $projectRoot 'config\.env'
$composeFile = Join-Path $projectRoot 'compose.yaml'
$startScript = Join-Path $projectRoot 'start.ps1'

if (-not (Test-Path -LiteralPath $configEnv)) {
    throw '缺少 config/.env。请先运行 start.cmd 创建配置文件。'
}

$config = @{}
Get-Content -LiteralPath $configEnv -Encoding UTF8 | ForEach-Object {
    if ($_ -match '^\s*([A-Za-z_][A-Za-z0-9_]*)=(.*)$') {
        $config[$Matches[1]] = $Matches[2].Trim()
    }
}
foreach ($name in @('XUNFEI_EMBEDDING_API_KEY', 'XUNFEI_EMBEDDING_MODEL_ID')) {
    if (-not $config.ContainsKey($name) -or [string]::IsNullOrWhiteSpace($config[$name])) {
        throw "请先在 config/.env 填写 $name。"
    }
}
$dimensions = 0
if (-not $config.ContainsKey('PGVECTOR_DIMENSIONS') -or
    -not [int]::TryParse($config['PGVECTOR_DIMENSIONS'], [ref]$dimensions) -or
    $dimensions -le 0) {
    throw '请先在 config/.env 填写正确的 PGVECTOR_DIMENSIONS。'
}
if ($config.ContainsKey('SPRING_PROFILES_ACTIVE') -and
    $config['SPRING_PROFILES_ACTIVE'] -match '(^|,)rag-(bootstrap|eval)(,|$)') {
    throw 'config/.env 已启用评测 profile。请先恢复日常 profile，再运行此脚本。'
}

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

$hadProcessProfile = Test-Path Env:SPRING_PROFILES_ACTIVE
$previousProcessProfile = if ($hadProcessProfile) { $env:SPRING_PROFILES_ACTIVE } else { $null }
$runStarted = [DateTimeOffset]::UtcNow.ToString('yyyy-MM-ddTHH:mm:ssZ')
$runLabel = Get-Date -Format 'yyyyMMdd-HHmmss'
$reportPath = Join-Path $projectRoot "tmp\rag-eval-$runLabel.txt"
$evaluationAttempted = $false
$failure = $null
$restoreFailure = $null

try {
    $env:SPRING_PROFILES_ACTIVE = 'prod,rag-bootstrap,rag-eval'
    $evaluationAttempted = $true
    Write-Host '正在导入岗位知识并运行真实 RAG 评测...'
    & $startScript -NoBrowser

    $logs = @(& $dockerExe compose --env-file $configEnv -f $composeFile logs --since $runStarted --no-color backend 2>&1)
    if ($LASTEXITCODE -ne 0) {
        throw '读取后端评测日志失败。'
    }

    $logText = $logs -join "`n"
    $metricLines = @([regex]::Matches($logText, 'REAL RAG evaluation:[^\r\n]*') |
        ForEach-Object { $_.Value.Trim() })
    foreach ($mode in @('VECTOR', 'HYBRID', 'HYBRID_RERANK')) {
        if (-not @($metricLines | Where-Object { $_ -match "REAL RAG evaluation:\s*$mode,24," }).Count) {
            throw "没有找到 $mode 的完整 24 条查询评测结果。请检查后端日志。"
        }
    }
    if ($logText -match 'Vector search unavailable|BM25 search unavailable|Rerank failed') {
        throw '检索或重排期间发生降级，不能将本次指标作为完整实测结果。请检查后端日志。'
    }

    $reportDir = Split-Path -Parent $reportPath
    New-Item -ItemType Directory -Force -Path $reportDir | Out-Null
    $metricLines | Set-Content -LiteralPath $reportPath -Encoding UTF8
    Write-Host ''
    Write-Host '真实 RAG 评测结果：'
    $metricLines | ForEach-Object { Write-Host $_ }
    Write-Host "结果已保存：$reportPath"
} catch {
    $failure = $_.Exception.Message
    Write-Host "评测未完成：$failure" -ForegroundColor Red
    Write-Host '查看后端日志可定位原因。'
} finally {
    if ($hadProcessProfile) {
        $env:SPRING_PROFILES_ACTIVE = $previousProcessProfile
    } else {
        Remove-Item Env:SPRING_PROFILES_ACTIVE -ErrorAction SilentlyContinue
    }
    if ($evaluationAttempted) {
        try {
            Write-Host '正在恢复日常运行配置...'
            & $startScript -NoBrowser -NoBuild
        } catch {
            $restoreFailure = $_.Exception.Message
            Write-Host "恢复失败：$restoreFailure" -ForegroundColor Red
        }
    }
}

if ($failure -or $restoreFailure) {
    exit 1
}
Write-Host '评测完成，服务已恢复。'
