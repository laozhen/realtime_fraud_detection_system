# Chaos Engineering Script - Random Pod Deletion
# Randomly deletes fraud-detection pods every 30 seconds for 5 minutes

# Configuration
$duration = 300  # 5 minutes in seconds
$interval = 30   # 30 seconds between deletions
$namespace = "fraud-detection"  # Kubernetes namespace
$podNamePattern = "fraud-detection"  # Pattern to match pod names

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Starting Chaos Test - Pod Deletion" -ForegroundColor Cyan
Write-Host "Duration: $duration seconds (5 minutes)" -ForegroundColor Cyan
Write-Host "Interval: $interval seconds" -ForegroundColor Cyan
Write-Host "Namespace: $namespace" -ForegroundColor Cyan
Write-Host "Target: $podNamePattern pods" -ForegroundColor Cyan
Write-Host "========================================`n" -ForegroundColor Cyan

# Calculate end time
$startTime = Get-Date
$endTime = $startTime.AddSeconds($duration)
$iteration = 0

Write-Host "Start Time: $($startTime.ToString('yyyy-MM-dd HH:mm:ss'))" -ForegroundColor Green
Write-Host "End Time:   $($endTime.ToString('yyyy-MM-dd HH:mm:ss'))`n" -ForegroundColor Green

# Main chaos loop
while ((Get-Date) -lt $endTime) {
    $iteration++
    $currentTime = Get-Date
    $elapsed = [math]::Round(($currentTime - $startTime).TotalSeconds, 0)
    $remaining = [math]::Round(($endTime - $currentTime).TotalSeconds, 0)
    
    Write-Host "[$($currentTime.ToString('HH:mm:ss'))] Iteration $iteration | Elapsed: ${elapsed}s | Remaining: ${remaining}s" -ForegroundColor Yellow
    
    # Get all fraud-detection pods
    Write-Host "  Fetching pods matching pattern: $podNamePattern..." -ForegroundColor Gray
    
    try {
        $pods = kubectl get pods -n $namespace -o json | ConvertFrom-Json
        $fraudPods = $pods.items | Where-Object { $_.metadata.name -like "*$podNamePattern*" -and $_.status.phase -eq "Running" }
        
        if ($fraudPods.Count -eq 0) {
            Write-Host "  WARNING: No running $podNamePattern pods found in namespace $namespace!" -ForegroundColor Red
        }
        else {
            Write-Host "  Found $($fraudPods.Count) running pod(s)" -ForegroundColor Gray
            
            # Randomly select one pod
            $randomIndex = Get-Random -Minimum 0 -Maximum $fraudPods.Count
            $targetPod = $fraudPods[$randomIndex]
            $podName = $targetPod.metadata.name
            
            Write-Host "  Target pod: $podName" -ForegroundColor Magenta
            Write-Host "  Deleting pod..." -ForegroundColor Red
            
            # Delete the pod
            $deleteResult = kubectl delete pod $podName -n $namespace 2>&1
            
            if ($LASTEXITCODE -eq 0) {
                Write-Host "  SUCCESS: Pod $podName deleted successfully in namespace $namespace!" -ForegroundColor Green
            }
            else {
                Write-Host "  ERROR: Failed to delete pod $podName in namespace $namespace" -ForegroundColor Red
                Write-Host "  Error: $deleteResult" -ForegroundColor Red
            }
        }
    }
    catch {
        Write-Host "  ERROR: Failed to get pods - $($_.Exception.Message)" -ForegroundColor Red
    }
    
    # Wait for the next iteration (only if not the last iteration)
    $timeLeft = ($endTime - (Get-Date)).TotalSeconds
    if ($timeLeft -gt 0) {
        $sleepTime = [math]::Min($interval, $timeLeft)
        Write-Host "  Waiting $sleepTime seconds until next deletion...`n" -ForegroundColor Gray
        Start-Sleep -Seconds $sleepTime
    }
}

# Summary
$finalTime = Get-Date
$totalDuration = [math]::Round(($finalTime - $startTime).TotalSeconds, 0)

Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host "Chaos Test Completed!" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Total Iterations: $iteration" -ForegroundColor Green
Write-Host "Total Duration: $totalDuration seconds" -ForegroundColor Green
Write-Host "End Time: $($finalTime.ToString('yyyy-MM-dd HH:mm:ss'))" -ForegroundColor Green
Write-Host "========================================`n" -ForegroundColor Cyan

