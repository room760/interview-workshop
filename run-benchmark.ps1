param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('rag', 'singleflight', 'multiagent', 'recovery')]
    [string]$Suite,
    [switch]$CheckOnly,
    [switch]$OfflineOnly
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$utf8 = New-Object System.Text.UTF8Encoding($false)
[Console]::OutputEncoding = $utf8
$OutputEncoding = $utf8
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$inner = Join-Path $root 'AI-Meeting-main'
$raw = Join-Path $root 'docs\benchmark\raw'
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$runDir = Join-Path $root "tmp\benchmark-runs\$Suite-$stamp"

function Get-Sha256Hex([string]$path) {
    $stream = [System.IO.File]::OpenRead($path)
    $algorithm = [System.Security.Cryptography.SHA256]::Create()
    try {
        $hash = $algorithm.ComputeHash($stream)
        return ([System.BitConverter]::ToString($hash)).Replace('-', '').ToLowerInvariant()
    } finally {
        $algorithm.Dispose()
        $stream.Dispose()
    }
}

$jdkCandidates = @('E:\Java\jdk-17', $env:JAVA_HOME) | Where-Object {
    -not [string]::IsNullOrWhiteSpace($_) -and (Test-Path -LiteralPath (Join-Path $_ 'bin\java.exe'))
}
$jdk = $jdkCandidates | Where-Object {
    (Get-Item -LiteralPath (Join-Path $_ 'bin\java.exe')).VersionInfo.ProductMajorPart -eq 17
} | Select-Object -First 1
if (-not $jdk) { throw '需要 Java 17。请安装后设置 JAVA_HOME。' }

$mavenCandidates = @('E:\Java\maven\apache-maven-3.8.9\bin\mvn.cmd')
$mavenCommand = Get-Command mvn.cmd -ErrorAction SilentlyContinue
if ($mavenCommand) { $mavenCandidates += $mavenCommand.Source }
$maven = $mavenCandidates | Where-Object { Test-Path -LiteralPath $_ } | Select-Object -First 1
if (-not $maven) { throw '找不到 Maven。请安装 Maven 并加入 PATH。' }

$docker = $null
if ($Suite -eq 'singleflight' -or $Suite -eq 'rag' -or $Suite -eq 'recovery') {
    $dockerCommand = Get-Command docker -ErrorAction SilentlyContinue
    if ($dockerCommand) { $docker = $dockerCommand.Source }
    if (-not $docker) {
        $docker = @(
            (Join-Path $env:LOCALAPPDATA 'Programs\DockerDesktop\resources\bin\docker.exe'),
            (Join-Path $env:ProgramFiles 'Docker\Docker\resources\bin\docker.exe')
        ) | Where-Object { Test-Path -LiteralPath $_ } | Select-Object -First 1
    }
    if (-not $docker) { throw '找不到 Docker。请先启动 Docker Desktop。' }
}

if ($Suite -eq 'rag') {
    $candidate = Join-Path $root 'docs\benchmark\data\candidate120.json'
    $cases = ConvertFrom-Json -InputObject (Get-Content -LiteralPath $candidate -Raw -Encoding UTF8)
    if ($cases.Count -ne 120) { throw "RAG 候选集应有 120 条，实际 $($cases.Count) 条。" }
}

if ($CheckOnly) {
    Write-Host "检查通过：$Suite；Java 17、Maven 与数据文件可用。"
    if ($Suite -eq 'rag') { Write-Host 'RAG 正式运行会调用讯飞 Embedding，并重启后端；测试集为 120 条 AI 候选查询。' }
    if ($Suite -eq 'singleflight') { Write-Host 'Single-flight 正式运行会启动临时 Redis 容器，模型任务为 Mock。' }
    if ($Suite -eq 'multiagent') { Write-Host 'Multi-Agent 正式运行会执行 100 轮 Mock 对照和一次 30 秒超时测试。' }
    if ($Suite -eq 'recovery') { Write-Host '恢复测试会使用 Docker 中的真实 Redis/Mongo，运行 10 项集成测试与 5 项 Mockito 测试；当前终态二次访问有已知失败。' }
    exit 0
}

New-Item -ItemType Directory -Force -Path $runDir | Out-Null
$datasetPath = Join-Path $root 'docs\benchmark\data\candidate120.json'
$gitHead = $null
$gitCommand = Get-Command git -ErrorAction SilentlyContinue
if ($gitCommand) {
    $safeRoot = $root.Replace('\', '/')
    $gitHead = (& $gitCommand.Source -c "safe.directory=$safeRoot" -C $root rev-parse HEAD 2>$null | Select-Object -First 1)
}
$runInfo = [ordered]@{
    suite = $Suite
    startedAtLocal = (Get-Date).ToString('o')
    gitHead = $gitHead
    javaHome = $jdk
    mavenExecutable = $maven
    candidate120Sha256 = Get-Sha256Hex $datasetPath
    realEmbeddingRequested = ($Suite -eq 'rag' -and -not $OfflineOnly)
    mockModelTasks = ($Suite -eq 'singleflight' -or $Suite -eq 'multiagent')
    realRedisMongoRequested = ($Suite -eq 'recovery')
}
$runInfo | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $runDir 'run-info.json') -Encoding UTF8
$oldJavaHome = $env:JAVA_HOME
$oldPath = $env:Path
$hadRunDir = Test-Path Env:BENCHMARK_RUN_DIR
$oldRunDir = if ($hadRunDir) { $env:BENCHMARK_RUN_DIR } else { $null }
$containerName = "interview-workshop-benchmark-redis-$PID"
$containerStarted = $false
$status = 0

function Invoke-MavenBenchmark([string]$testSpec, [string]$logName, [string[]]$extraArgs) {
    $logPath = Join-Path $runDir $logName
    Push-Location -LiteralPath $inner
    try {
        $previousPreference = $ErrorActionPreference
        try {
            $ErrorActionPreference = 'Continue'
            & $maven -o -pl admin -am "-Dtest=$testSpec" @extraArgs '-Dsurefire.failIfNoSpecifiedTests=false' test 2>&1 |
                ForEach-Object { $_.ToString() } |
                Tee-Object -FilePath $logPath
            $code = $LASTEXITCODE
        } finally {
            $ErrorActionPreference = $previousPreference
        }
        if ($code -ne 0) { throw "JUnit 未通过，日志：$logPath" }
    } finally {
        Pop-Location
    }
}

function Copy-Raw([string[]]$names) {
    foreach ($name in $names) {
        $source = Join-Path $raw $name
        if (-not (Test-Path -LiteralPath $source)) { throw "缺少原始结果：$source" }
        Copy-Item -LiteralPath $source -Destination (Join-Path $runDir $name)
    }
}

try {
    $env:JAVA_HOME = $jdk
    $env:Path = "$(Join-Path $jdk 'bin');$oldPath"
    Write-Host "本次结果目录：$runDir"
    switch ($Suite) {
        'rag' {
            Write-Host '第一步：运行 120 条 BM25 离线评测。'
            Invoke-MavenBenchmark 'RagBm25BenchmarkTest' 'maven-bm25.log' @()
            Copy-Raw @('bm25-queries.jsonl', 'bm25-summary.json')
            if ($OfflineOnly) {
                Write-Host '离线检查模式：未调用讯飞，也未运行向量/混合/重排。'
            } else {
                Write-Host '第二步：运行 120 条真实 BM25/向量/混合/规则重排评测；此步骤调用讯飞 Embedding。'
                $env:BENCHMARK_RUN_DIR = $runDir
                $previousPreference = $ErrorActionPreference
                try {
                    $ErrorActionPreference = 'Continue'
                    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $root 'eval-rag.ps1') -Dataset candidate120 2>&1 |
                        ForEach-Object { $_.ToString() } |
                        Tee-Object -FilePath (Join-Path $runDir 'rag-console.log')
                    $code = $LASTEXITCODE
                } finally {
                    $ErrorActionPreference = $previousPreference
                }
                if ($code -ne 0) { throw '真实 RAG 评测失败；请查看本次结果目录中的日志。' }
            }
        }
        'singleflight' {
            & $docker image inspect 'redis:7.2' *> $null
            if ($LASTEXITCODE -ne 0) { throw '本机缺少 redis:7.2 镜像；请先运行 start.cmd 下载基础镜像。' }
            & $docker run -d --rm --name $containerName -p '127.0.0.1:16379:6379' 'redis:7.2' | Out-Null
            if ($LASTEXITCODE -ne 0) { throw '临时 Redis 启动失败；请检查 16379 端口。' }
            $containerStarted = $true
            $ready = $false
            for ($i = 0; $i -lt 20; $i++) {
                $reply = & $docker exec $containerName redis-cli ping 2>$null
                if ($LASTEXITCODE -eq 0 -and $reply -match 'PONG') { $ready = $true; break }
                Start-Sleep -Seconds 1
            }
            if (-not $ready) { throw '临时 Redis 未就绪。' }
            Write-Host '正在运行本地去重与真实 Redis Lua 并发测试，模型任务为 Mock。'
            Invoke-MavenBenchmark 'LocalSingleFlightBenchmarkTest,RedisFlightCoordinatorBenchmarkTest' 'maven-singleflight.log' @('-Dbenchmark.redis=true', '-Dbenchmark.redis.port=16379')
            Copy-Raw @('local-singleflight.jsonl', 'redis-coordinator.jsonl', 'redis-takeover.json')
        }
        'multiagent' {
            Write-Host '正在运行 100 轮 Mock 并行对照、异常传播及 30 秒超时测试。'
            Invoke-MavenBenchmark 'InterviewAnswerParallelBenchmarkTest' 'maven-multiagent.log' @()
            Copy-Raw @('multi-agent.jsonl', 'multi-agent-timeout.json')
        }
        'recovery' {
            Write-Host '正在检查真实 Redis、MongoDB 容器。'
            Push-Location -LiteralPath $inner
            try {
                & $docker compose -f docker-compose.yml up -d mongo redis
                if ($LASTEXITCODE -ne 0) { throw '无法启动 Redis/MongoDB；请检查 Docker Desktop。' }
            } finally {
                Pop-Location
            }
            $ready = $false
            for ($i = 0; $i -lt 30; $i++) {
                $redisHealth = & $docker inspect --format '{{.State.Health.Status}}' interview-workshop-redis 2>$null
                $mongoHealth = & $docker inspect --format '{{.State.Health.Status}}' interview-workshop-mongo 2>$null
                if ($redisHealth -eq 'healthy' -and $mongoHealth -eq 'healthy') { $ready = $true; break }
                Start-Sleep -Seconds 2
            }
            if (-not $ready) { throw 'Redis/MongoDB 未在 60 秒内达到 healthy；未运行测试。' }
            Write-Host '正在运行真实 Redis/Mongo 集成测试及原有 Mockito 测试，约需 2 分钟。'
            $env:BENCHMARK_RUN_DIR = $runDir
            $runStarted = Get-Date
            $mavenFailure = $null
            try {
                Invoke-MavenBenchmark 'InterviewSessionRuntimeIntegrationTest,InterviewSessionRuntimeRehydrateServiceTest' 'maven-recovery.log' @('-Dbenchmark.recovery.integration=true')
            } catch {
                $mavenFailure = $_.Exception.Message
            }
            $resultPath = Join-Path $runDir 'recovery-integration.jsonl'
            $rows = @()
            if ((Test-Path -LiteralPath $resultPath) -and (Get-Item -LiteralPath $resultPath).LastWriteTime -ge $runStarted) {
                $rows = @(Get-Content -LiteralPath $resultPath -Encoding UTF8 | Where-Object { $_.Trim() } | ForEach-Object { $_ | ConvertFrom-Json })
            }
            $integrationReportPath = Join-Path $inner 'admin\target\surefire-reports\TEST-com.cqr.interviewworkshop.interview.application.runtime.InterviewSessionRuntimeIntegrationTest.xml'
            $unitReportPath = Join-Path $inner 'admin\target\surefire-reports\TEST-com.cqr.interviewworkshop.interview.application.runtime.InterviewSessionRuntimeRehydrateServiceTest.xml'
            $integrationReport = $null
            $unitReport = $null
            if ((Test-Path -LiteralPath $integrationReportPath) -and (Get-Item -LiteralPath $integrationReportPath).LastWriteTime -ge $runStarted) {
                $integrationReport = ([xml](Get-Content -LiteralPath $integrationReportPath -Raw -Encoding UTF8)).testsuite
            }
            if ((Test-Path -LiteralPath $unitReportPath) -and (Get-Item -LiteralPath $unitReportPath).LastWriteTime -ge $runStarted) {
                $unitReport = ([xml](Get-Content -LiteralPath $unitReportPath -Raw -Encoding UTF8)).testsuite
            }
            $recoverable = @($rows | Where-Object { $_.expectedRecoverable })
            $recovered = @($recoverable | Where-Object { $_.canWrite -and $_.backfillComplete })
            $repeats = @($rows | Where-Object { $_.scenario -like 'repeat_*' } | ForEach-Object { [double]$_.latencyMs } | Sort-Object)
            $p50 = if ($repeats.Count -gt 0) { $repeats[[Math]::Ceiling($repeats.Count * 0.5) - 1] } else { $null }
            $p95 = if ($repeats.Count -gt 0) { $repeats[[Math]::Ceiling($repeats.Count * 0.95) - 1] } else { $null }
            $summary = [ordered]@{
                realRedisMongo = ($null -ne $integrationReport -and $rows.Count -gt 0)
                integrationTests = if ($integrationReport) { [int]$integrationReport.tests } else { $null }
                integrationFailures = if ($integrationReport) { [int]$integrationReport.failures } else { $null }
                mockitoTests = if ($unitReport) { [int]$unitReport.tests } else { $null }
                mockitoFailures = if ($unitReport) { [int]$unitReport.failures } else { $null }
                expectedRecoverableObservations = $recoverable.Count
                successfulRecoveries = $recovered.Count
                repeatedLatencySamples = $repeats.Count
                recoveryP50Ms = $p50
                recoveryP95Ms = $p95
                note = 'Failure in terminal session second cache hit is a known business assertion failure; see recovery-integration-report.md.'
            }
            $summary | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $runDir 'recovery-summary.json') -Encoding UTF8
            Write-Host "真实集成测试：$($summary.integrationTests) 项，失败 $($summary.integrationFailures) 项；原有单元测试：$($summary.mockitoTests) 项，失败 $($summary.mockitoFailures) 项。"
            Write-Host "预期可恢复观察 $($summary.successfulRecoveries)/$($summary.expectedRecoverableObservations)；100 次重复采样 P50/P95：$p50/$p95 ms。"
            Write-Host "原始结果和汇总：$runDir"
            if ($mavenFailure) {
                throw "测试发现失败断言，已保留完整结果。$mavenFailure"
            }
        }
    }
    Write-Host "评测完成。结果目录：$runDir"
} catch {
    $status = 1
    Write-Host "评测失败：$($_.Exception.Message)" -ForegroundColor Red
    Write-Host "已产生的日志保存在：$runDir"
} finally {
    if ($containerStarted) { & $docker stop $containerName *> $null }
    $env:JAVA_HOME = $oldJavaHome
    $env:Path = $oldPath
    if ($hadRunDir) { $env:BENCHMARK_RUN_DIR = $oldRunDir }
    else { Remove-Item Env:BENCHMARK_RUN_DIR -ErrorAction SilentlyContinue }
}
exit $status
