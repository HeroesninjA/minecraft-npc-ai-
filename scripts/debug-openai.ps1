[CmdletBinding()]
param(
    [string]$ConfigPath = "",
    [string]$BaseUrl = "",
    [string]$Model = "",
    [string]$ApiKey = "",
    [string]$OutputDir = "",
    [int]$TimeoutSeconds = 15,
    [switch]$IncludeResponseTest
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.Net.Http

$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = (Resolve-Path (Join-Path $scriptRoot "..")).Path

if ([string]::IsNullOrWhiteSpace($ConfigPath)) {
    $ConfigPath = Join-Path $repoRoot "src\src\main\resources\config.yml"
}

if ([string]::IsNullOrWhiteSpace($OutputDir)) {
    $OutputDir = Join-Path $repoRoot "debug-logs"
}

function New-DirectoryIfMissing {
    param([string]$Path)

    if (-not [string]::IsNullOrWhiteSpace($Path) -and -not (Test-Path -LiteralPath $Path)) {
        New-Item -ItemType Directory -Path $Path | Out-Null
    }
}

function Write-Log {
    param(
        [string]$Level,
        [string]$Message
    )

    $line = "[{0}] [{1}] {2}" -f (Get-Date -Format "yyyy-MM-dd HH:mm:ss"), $Level.ToUpperInvariant(), $Message
    $line | Tee-Object -FilePath $script:LogPath -Append
}

function Write-Section {
    param([string]$Title)
    Write-Log "info" ("=" * 20 + " " + $Title + " " + "=" * 20)
}

function Get-ScalarFromYamlSection {
    param(
        [string[]]$Lines,
        [string[]]$PathSegments
    )

    if (-not $Lines -or -not $PathSegments -or $PathSegments.Count -eq 0) {
        return $null
    }

    $sectionStack = @()

    foreach ($rawLine in $Lines) {
        if ($null -eq $rawLine) {
            continue
        }

        $line = $rawLine -replace "`t", "    "
        if ($line -match '^\s*#' -or $line.Trim().Length -eq 0) {
            continue
        }

        if ($line -notmatch '^(\s*)([^:#]+):(.*)$') {
            continue
        }

        $indent = $matches[1].Length
        $key = $matches[2].Trim()
        $rest = $matches[3].Trim()

        while ($sectionStack.Count -gt 0 -and $sectionStack[-1].Indent -ge $indent) {
            $sectionStack = if ($sectionStack.Count -eq 1) { @() } else { $sectionStack[0..($sectionStack.Count - 2)] }
        }

        $currentPath = @($sectionStack | ForEach-Object { $_.Key })
        $candidatePath = @($currentPath + $key)

        if ([string]::IsNullOrWhiteSpace($rest)) {
            $sectionStack += [pscustomobject]@{
                Key = $key
                Indent = $indent
            }
            continue
        }

        if (($candidatePath -join ".") -eq ($PathSegments -join ".")) {
            $value = $rest
            if (($value.StartsWith('"') -and $value.EndsWith('"')) -or ($value.StartsWith("'") -and $value.EndsWith("'"))) {
                return $value.Substring(1, $value.Length - 2)
            }
            return $value
        }
    }

    return $null
}

function Resolve-ConfigValues {
    param([string]$Path)

    $result = [ordered]@{
        BaseUrl = $null
        Model = $null
        ApiKey = $null
    }

    if (-not (Test-Path -LiteralPath $Path)) {
        return $result
    }

    $lines = Get-Content -LiteralPath $Path
    $result.BaseUrl = Get-ScalarFromYamlSection -Lines $lines -PathSegments @("openai", "base_url")
    $result.Model = Get-ScalarFromYamlSection -Lines $lines -PathSegments @("openai", "model")
    $result.ApiKey = Get-ScalarFromYamlSection -Lines $lines -PathSegments @("openai", "api_key")
    return $result
}

function Mask-Secret {
    param([string]$Value)

    if ([string]::IsNullOrWhiteSpace($Value)) {
        return "<neconfigurat>"
    }

    if ($Value.Length -le 8) {
        return ("*" * $Value.Length)
    }

    return $Value.Substring(0, 7) + "..." + $Value.Substring($Value.Length - 4)
}

function Get-JsonPreview {
    param([string]$Text)

    if ([string]::IsNullOrWhiteSpace($Text)) {
        return "<gol>"
    }

    $normalized = $Text.Replace("`r", " ").Replace("`n", " ").Trim()
    if ($normalized.Length -le 300) {
        return $normalized
    }

    return $normalized.Substring(0, 297) + "..."
}

function Invoke-HttpProbe {
    param(
        [string]$Method,
        [string]$Url,
        [object]$Body = $null,
        [string]$BearerToken = ""
    )

    $handler = $null
    $client = $null

    try {
        $handler = [System.Net.Http.HttpClientHandler]::new()
        $client = [System.Net.Http.HttpClient]::new($handler)
        $client.Timeout = [TimeSpan]::FromSeconds($TimeoutSeconds)
        $client.DefaultRequestHeaders.Accept.Clear()
        [void]$client.DefaultRequestHeaders.Accept.Add([System.Net.Http.Headers.MediaTypeWithQualityHeaderValue]::new("application/json"))

        if (-not [string]::IsNullOrWhiteSpace($BearerToken)) {
            $client.DefaultRequestHeaders.Authorization = [System.Net.Http.Headers.AuthenticationHeaderValue]::new("Bearer", $BearerToken)
        }

        if ($Method -ieq "GET") {
            $response = $client.GetAsync($Url).GetAwaiter().GetResult()
        } elseif ($Method -ieq "POST") {
            $jsonBody = if ($null -ne $Body) { $Body | ConvertTo-Json -Depth 8 -Compress } else { "" }
            $content = [System.Net.Http.StringContent]::new($jsonBody, [System.Text.Encoding]::UTF8, "application/json")
            $response = $client.PostAsync($Url, $content).GetAwaiter().GetResult()
            $content.Dispose()
        } else {
            throw "Metoda HTTP nesuportata: $Method"
        }

        $responseBody = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
        return [pscustomobject]@{
            Success = $response.IsSuccessStatusCode
            StatusCode = [int]$response.StatusCode
            Body = $responseBody
            Error = if ($response.IsSuccessStatusCode) { $null } else { $response.ReasonPhrase }
        }
    } catch {
        $exception = $_.Exception
        return [pscustomobject]@{
            Success = $false
            StatusCode = $null
            Body = $null
            Error = ("{0}: {1}" -f $exception.GetType().FullName, $exception.Message)
        }
    } finally {
        if ($null -ne $client) {
            $client.Dispose()
        }
        if ($null -ne $handler) {
            $handler.Dispose()
        }
    }
}

New-DirectoryIfMissing -Path $OutputDir
$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$script:LogPath = Join-Path $OutputDir "openai-debug-$timestamp.log"

Write-Log "info" "Pornesc diagnosticare OpenAI."
Write-Log "info" "Log file: $LogPath"

$configValues = Resolve-ConfigValues -Path $ConfigPath
if ([string]::IsNullOrWhiteSpace($BaseUrl)) {
    $BaseUrl = $configValues.BaseUrl
}
if ([string]::IsNullOrWhiteSpace($Model)) {
    $Model = $configValues.Model
}
if ([string]::IsNullOrWhiteSpace($ApiKey)) {
    $ApiKey = $configValues.ApiKey
}

if ([string]::IsNullOrWhiteSpace($BaseUrl)) {
    $BaseUrl = if ($env:OPENAI_BASE_URL) { $env:OPENAI_BASE_URL } else { "https://api.openai.com/v1" }
}
if ([string]::IsNullOrWhiteSpace($Model)) {
    $Model = "gpt-5.4-nano"
}
if ([string]::IsNullOrWhiteSpace($ApiKey)) {
    $ApiKey = $env:OPENAI_API_KEY
}
if (-not ($BaseUrl -match '^\w+://')) {
    $BaseUrl = "https://$BaseUrl"
}
$BaseUrl = $BaseUrl.TrimEnd("/")

Write-Section "Configuratie"
Write-Log "info" "ConfigPath: $ConfigPath"
Write-Log "info" "BaseUrl: $BaseUrl"
Write-Log "info" "Model: $Model"
Write-Log "info" "TimeoutSeconds: $TimeoutSeconds"
Write-Log "info" "OPENAI_BASE_URL: $(if ($env:OPENAI_BASE_URL) { $env:OPENAI_BASE_URL } else { '<neconfigurat>' })"
Write-Log "info" "ApiKey: $(Mask-Secret -Value $ApiKey)"

if ([string]::IsNullOrWhiteSpace($ApiKey)) {
    Write-Log "warn" "Cheia API lipseste. Seteaza openai.api_key sau OPENAI_API_KEY."
}

$encodedModel = [System.Uri]::EscapeDataString($Model)

Write-Section "Probe HTTP /models/{model}"
$modelProbe = Invoke-HttpProbe -Method "GET" -Url "$BaseUrl/models/$encodedModel" -BearerToken $ApiKey
if ($modelProbe.Success) {
    Write-Log "info" "Status: $($modelProbe.StatusCode)"
    Write-Log "info" ("Body: {0}" -f (Get-JsonPreview -Text $modelProbe.Body))
} else {
    Write-Log "error" "Request esuat: $($modelProbe.Error)"
    if ($modelProbe.StatusCode) {
        Write-Log "error" "Status: $($modelProbe.StatusCode)"
    }
    if ($modelProbe.Body) {
        Write-Log "error" ("Body: {0}" -f (Get-JsonPreview -Text $modelProbe.Body))
    }
}

if ($IncludeResponseTest) {
    Write-Section "Probe HTTP /responses"
    $body = @{
        model = $Model
        input = "Raspunde cu exact textul OK."
        max_output_tokens = 16
        temperature = 0
        store = $false
    }

    $responseProbe = Invoke-HttpProbe -Method "POST" -Url "$BaseUrl/responses" -Body $body -BearerToken $ApiKey
    if ($responseProbe.Success) {
        Write-Log "info" "Status: $($responseProbe.StatusCode)"
        Write-Log "info" ("Body: {0}" -f (Get-JsonPreview -Text $responseProbe.Body))
    } else {
        Write-Log "error" "Request esuat: $($responseProbe.Error)"
        if ($responseProbe.StatusCode) {
            Write-Log "error" "Status: $($responseProbe.StatusCode)"
        }
        if ($responseProbe.Body) {
            Write-Log "error" ("Body: {0}" -f (Get-JsonPreview -Text $responseProbe.Body))
        }
    }
}

Write-Section "Rezultat"
Write-Log "info" "Diagnosticare terminata."
Write-Log "info" "Log final: $LogPath"
