#Requires -Version 7
# Story 12: 샘플 상품의 AI 설명을 실제 서버에서 받아 JSON으로 저장한다 (품질 측정용)
# 사용법: Gemini 모드로 서버를 띄운 뒤
#   .\scripts\collect-descriptions.ps1 -Output src/test/resources/eval/prompt-v1.json
# - API 키는 서버만 알고, 이 스크립트는 키를 다루지 않는다
# - 샘플 상품을 등록하고 설명을 받은 뒤, 이 스크립트가 만든 상품만 삭제한다 (기존 데이터는 건드리지 않음)
param(
    [Parameter(Mandatory)][string]$Output,
    [string]$BaseUrl = 'http://localhost:8080'
)

$ErrorActionPreference = 'Stop'

$samples = @(
    @{ name = 'Wireless Mouse'; price = 25000 },
    @{ name = '기계식 키보드'; price = 89000 },
    @{ name = '27인치 모니터'; price = 320000 },
    @{ name = '텀블러'; price = 15000 },
    @{ name = '무료 스티커'; price = 0 },
    @{ name = 'USB-C 케이블 2m'; price = 9900 },
    @{ name = 'Logi Pro Headset'; price = 129000 },
    @{ name = '무선마우스' * 51; price = 45000 }  # 이름 최대 길이 255자
)

# 응답에 charset이 없어도 한글이 깨지지 않도록 바이트를 UTF-8로 직접 읽는다
function Invoke-Api([string]$Method, [string]$Path, $Body) {
    $params = @{ Method = $Method; Uri = "$BaseUrl$Path"; SkipHttpErrorCheck = $true }
    if ($null -ne $Body) {
        $params.Body = [Text.Encoding]::UTF8.GetBytes(($Body | ConvertTo-Json -Compress))
        $params.ContentType = 'application/json; charset=utf-8'
    }
    $response = Invoke-WebRequest @params
    $text = [Text.Encoding]::UTF8.GetString($response.RawContentStream.ToArray())
    [pscustomobject]@{
        Status = [int]$response.StatusCode
        Json   = if ($text) { $text | ConvertFrom-Json } else { $null }
    }
}

$results = foreach ($sample in $samples) {
    $created = Invoke-Api POST '/products' $sample
    if ($created.Status -ne 201) {
        throw "상품 등록 실패: HTTP $($created.Status) ($($sample.name))"
    }
    $id = $created.Json.id
    try {
        $generated = Invoke-Api POST "/products/$id/generate-description" $null
        Write-Host "[$($generated.Status)] $($sample.name.Substring(0, [Math]::Min(20, $sample.name.Length)))"
        [ordered]@{
            name        = $sample.name
            price       = $sample.price
            status      = $generated.Status
            description = if ($generated.Status -eq 200) { $generated.Json.description } else { $null }
        }
    }
    finally {
        Invoke-Api DELETE "/products/$id" $null | Out-Null
    }
}

New-Item -ItemType Directory -Force (Split-Path $Output) | Out-Null
[IO.File]::WriteAllText(
    [IO.Path]::GetFullPath($Output),
    (ConvertTo-Json @($results) -Depth 3),
    [Text.UTF8Encoding]::new($false)
)
Write-Host "저장: $Output ($($results.Count)건)"
