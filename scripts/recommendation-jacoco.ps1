param(
    [switch]$SkipMaven
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$csvPath = Join-Path $repoRoot "target\site\jacoco\jacoco.csv"

$testClasses = @(
    "RecommendationScorerTest",
    "RecommendationServiceTest",
    "RecommendationSignalServiceTest",
    "ContentLessonLinkServiceTest",
    "FeedServiceTest",
    "FeedControllerMockIntegrationTest",
    "FeedControllerTest",
    "AdminContentLessonLinkTest"
) -join ","

# Keep the warning scoped to the recommendation feature so unrelated packages do not skew the signal.
$recommendationClasses = @(
    @{ Package = "com.rotiprata.api.feed.controller"; Class = "FeedController" },
    @{ Package = "com.rotiprata.api.feed.service"; Class = "FeedService" },
    @{ Package = "com.rotiprata.api.feed.service"; Class = "RecommendationService" },
    @{ Package = "com.rotiprata.api.feed.service"; Class = "RecommendationService.RecommendationCursor" },
    @{ Package = "com.rotiprata.api.feed.service"; Class = "RecommendationSignalService" },
    @{ Package = "com.rotiprata.api.feed.service"; Class = "RecommendationScorer" },
    @{ Package = "com.rotiprata.api.feed.service"; Class = "RecommendationScorer.ScoredRecommendation" },
    @{ Package = "com.rotiprata.api.feed.service"; Class = "RecommendationSignals" },
    @{ Package = "com.rotiprata.api.feed.service"; Class = "RecommendationSignals.LessonProgressSignal" },
    @{ Package = "com.rotiprata.api.feed.service"; Class = "RecommendationSurface" },
    @{ Package = "com.rotiprata.api.feed.service"; Class = "ContentLessonLinkService" },
    @{ Package = "com.rotiprata.api.feed.service"; Class = "ContentLessonLinkService.LinkedLesson" },
    @{ Package = "com.rotiprata.api.feed.service"; Class = "ContentLessonLinkService.LinkSource" },
    @{ Package = "com.rotiprata.api.zdto"; Class = "RecommendationResponse" }
)

if (-not $SkipMaven) {
    & mvn "-Dtest=$testClasses" verify
    if ($LASTEXITCODE -ne 0) {
        exit $LASTEXITCODE
    }
}

if (-not (Test-Path $csvPath)) {
    throw "JaCoCo report not found at '$csvPath'. Run the recommendation Maven workflow first."
}

$rows = Import-Csv $csvPath
$scopedRows = foreach ($target in $recommendationClasses) {
    $rows | Where-Object {
        $_.PACKAGE -eq $target.Package -and $_.CLASS -eq $target.Class
    }
}

if (-not $scopedRows) {
    throw "No recommendation coverage rows were found in '$csvPath'."
}

$lineMissed = ($scopedRows | Measure-Object -Property LINE_MISSED -Sum).Sum
$lineCovered = ($scopedRows | Measure-Object -Property LINE_COVERED -Sum).Sum
$lineTotal = $lineMissed + $lineCovered

if ($lineTotal -le 0) {
    throw "Recommendation coverage rows did not contain any executable lines."
}

$coverage = [math]::Round(($lineCovered / $lineTotal) * 100, 2)

Write-Host ("Recommendation JaCoCo line coverage: {0}% ({1}/{2} lines)" -f $coverage, $lineCovered, $lineTotal)

if ($coverage -lt 50) {
    Write-Warning ("Recommendation coverage is below the 50% warning floor: {0}%" -f $coverage)
} elseif ($coverage -lt 70) {
    Write-Host ("Recommendation coverage cleared the 50% warning floor but is still below the 70% target.")
} else {
    Write-Host ("Recommendation coverage reached the 70% target.")
}
