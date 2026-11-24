$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$project = Split-Path -Parent $MyInvocation.MyCommand.Path
$root = Split-Path -Parent $project
$out = Join-Path $root 'res\mipmap'
New-Item -ItemType Directory -Force -Path $out | Out-Null

function New-IconBitmap([bool]$round) {
    $bmp = New-Object System.Drawing.Bitmap 512, 512
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
    $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality

    # Charcoal field with a soft inward glow.
    $g.Clear([System.Drawing.Color]::FromArgb(255, 16, 19, 28))
    $soft = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255, 25, 30, 45))
    if ($round) { $g.FillEllipse($soft, 24, 24, 464, 464) }
    else { $g.FillEllipse($soft, 56, 56, 400, 400) }

    # Faint vinyl-like rim.
    $ring = New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(70, 200, 169, 106))
    $ring.Width = 3
    $g.DrawEllipse($ring, 62, 62, 388, 388)

    # Three echoes, tightening until they become the play wedge.
    $outer = New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(156, 200, 169, 106))
    $outer.Width = 14
    $outer.StartCap = [System.Drawing.Drawing2D.LineCap]::Round
    $outer.EndCap = [System.Drawing.Drawing2D.LineCap]::Round
    $g.DrawBezier($outer, 190, 148, 74, 208, 74, 304, 190, 364)

    $mid = New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(214, 200, 169, 106))
    $mid.Width = 11
    $mid.StartCap = [System.Drawing.Drawing2D.LineCap]::Round
    $mid.EndCap = [System.Drawing.Drawing2D.LineCap]::Round
    $g.DrawBezier($mid, 230, 184, 140, 224, 140, 288, 230, 328)

    $inner = New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(242, 241, 227, 198))
    $inner.Width = 8
    $inner.StartCap = [System.Drawing.Drawing2D.LineCap]::Round
    $inner.EndCap = [System.Drawing.Drawing2D.LineCap]::Round
    $g.DrawBezier($inner, 266, 214, 206, 236, 206, 276, 266, 298)

    $triBrush = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255, 246, 232, 201))
    $points = [System.Drawing.PointF[]]@(
        (New-Object System.Drawing.PointF 302, 176),
        (New-Object System.Drawing.PointF 302, 336),
        (New-Object System.Drawing.PointF 414, 256)
    )
    $g.FillPolygon($triBrush, $points)

    $g.Dispose()
    return $bmp
}

$square = New-IconBitmap $false
$square.Save((Join-Path $out 'ic_launcher.png'), [System.Drawing.Imaging.ImageFormat]::Png)
$square.Dispose()

$circle = New-IconBitmap $true
$circle.Save((Join-Path $out 'ic_launcher_round.png'), [System.Drawing.Imaging.ImageFormat]::Png)
$circle.Dispose()

Write-Host "Wrote launcher icons to $out"
