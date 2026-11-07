param([ValidateSet('historical60', 'candidate120')][string]$Dataset = 'historical60')

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$utf8 = New-Object System.Text.UTF8Encoding($false)
[Console]::OutputEncoding = $utf8
$OutputEncoding = $utf8

$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$configEnv = Join-Path $projectRoot 'config\.env'
$evaluationFile = if ($Dataset -eq 'candidate120') {
    Join-Path $projectRoot 'docs\benchmark\data\candidate120.json'
} else {
    Join-Path $projectRoot 'AI-Meeting-main\admin\src\main\resources\rag\evaluation.json'
}
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
$evaluationCases = ConvertFrom-Json -InputObject (Get-Content -LiteralPath $evaluationFile -Raw -Encoding UTF8)
$expectedCount = $evaluationCases.Count
if ($expectedCount -le 0) { throw 'RAG 评测集为空。' }
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
$hadEvaluationFile = Test-Path Env:RAG_EVALUATION_FILE
$previousEvaluationFile = if ($hadEvaluationFile) { $env:RAG_EVALUATION_FILE } else { $null }
$runStarted = [DateTimeOffset]::UtcNow.ToString('yyyy-MM-ddTHH:mm:ssZ')
$runLabel = Get-Date -Format 'yyyyMMdd-HHmmss'
$benchmarkOutputDir = if ([string]::IsNullOrWhiteSpace($env:BENCHMARK_RUN_DIR)) {
    Join-Path $projectRoot 'tmp'
} else {
    $env:BENCHMARK_RUN_DIR
}
$reportPath = Join-Path $benchmarkOutputDir "rag-eval-$runLabel.txt"
$attemptLogPath = Join-Path $benchmarkOutputDir "rag-eval-attempt-$runLabel.log"
$observationPath = Join-Path $benchmarkOutputDir "rag-observations-$runLabel.jsonl"
$evaluationAttempted = $false
$failure = $null
$restoreFailure = $null

try {
    $env:SPRING_PROFILES_ACTIVE = 'prod,rag-bootstrap,rag-eval'
    if ($Dataset -eq 'candidate120') {
        $env:RAG_EVALUATION_FILE = '/app/benchmark/candidate120.json'
    } else {
        Remove-Item Env:RAG_EVALUATION_FILE -ErrorAction SilentlyContinue
    }
    $evaluationAttempted = $true
    Write-Host '正在导入岗位知识并运行真实 RAG 评测...'
    & $startScript -NoBrowser

    $logs = @(& $dockerExe compose --env-file $configEnv -f $composeFile logs --since $runStarted --no-color backend 2>&1)
    if ($LASTEXITCODE -ne 0) {
        throw '读取后端评测日志失败。'
    }

    $logText = $logs -join "`n"
    $reportDir = Split-Path -Parent $reportPath
    New-Item -ItemType Directory -Force -Path $reportDir | Out-Null
    $logText | Set-Content -LiteralPath $attemptLogPath -Encoding UTF8
    $metricLines = @([regex]::Matches($logText, 'REAL RAG evaluation:[^\r\n]*') |
        ForEach-Object { $_.Value.Trim() })
    $observations = @([regex]::Matches($logText, 'REAL RAG observation:\s*(\{[^\r\n]*\})') |
        ForEach-Object { $_.Groups[1].Value })
    $observations | Set-Content -LiteralPath $observationPath -Encoding UTF8
    foreach ($mode in @('BM25', 'VECTOR', 'HYBRID', 'HYBRID_RERANK')) {
        if (-not @($metricLines | Where-Object { $_ -match "REAL RAG evaluation:\s*$mode,$expectedCount," }).Count) {
            throw "没有找到 $mode 的完整 $expectedCount 条查询评测结果。请检查后端日志。"
        }
    }
    if ($logText -match 'Vector search unavailable|BM25 search unavailable|Rerank failed') {
        throw '检索或重排期间发生降级，不能将本次指标作为完整实测结果。请检查后端日志。'
    }

    if ($observations.Count -ne (4 * $expectedCount)) {
        throw "逐查询原始结果不完整：预期 $($expectedCount * 4) 条，实际 $($observations.Count) 条。"
    }
    foreach ($row in $observations) { $null = $row | ConvertFrom-Json }
    $metricLines | Set-Content -LiteralPath $reportPath -Encoding UTF8
    Write-Host ''
    Write-Host '真实 RAG 评测结果：'
    $metricLines | ForEach-Object { Write-Host $_ }
    Write-Host "结果已保存：$reportPath"
} catch {
    $failure = $_.Exception.Message
    Write-Host "评测未完成：$failure" -ForegroundColor Red
    if (Test-Path -LiteralPath $attemptLogPath) {
        Write-Host "本次后端日志已保存：$attemptLogPath（含运行信息，分享前请检查敏感内容）"
    } else {
        Write-Host '查看后端日志可定位原因。'
    }
} finally {
    if ($hadProcessProfile) {
        $env:SPRING_PROFILES_ACTIVE = $previousProcessProfile
    } else {
        Remove-Item Env:SPRING_PROFILES_ACTIVE -ErrorAction SilentlyContinue
    }
    if ($hadEvaluationFile) {
        $env:RAG_EVALUATION_FILE = $previousEvaluationFile
    } else {
        Remove-Item Env:RAG_EVALUATION_FILE -ErrorAction SilentlyContinue
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
