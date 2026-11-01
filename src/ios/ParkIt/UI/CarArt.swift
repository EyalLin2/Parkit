import SwiftUI

/// Paint colours for "your car", picked in the profile and kept on the device.
let carPaints: [Color] = [0x1B4F91, 0xD93025, 0xF2F2F2, 0x9AA0A6, 0x2B2F36, 0x188038, 0xF9AB00, 0x7B3FC4].map { Color(hex: UInt32($0)) }

/// The paint for "your car": a colour picked by hand wins, then the registry's, then ParkIt blue.
@MainActor func myCarPaint(registry: String?) -> Color {
    let manual = Session.shared.carPaintIndex
    if carPaints.indices.contains(manual) { return carPaints[manual] }
    return Color(hexString: registry) ?? carPaints[0]
}

/// A side view of the driver's car, from its size (CarArt.kt): compact = short hatchback,
/// regular = sedan, large = tall SUV. No car set: a dashed outline inviting one. Faces right.
struct CarArt: View {
    let size: String?
    let paint: Color
    var special: String?
    var animate = false

    var body: some View {
        TimelineView(.animation(minimumInterval: 1 / 30, paused: !animate || size == nil)) { t in
            Canvas { ctx, s in
                let phase = animate ? CGFloat(t.date.timeIntervalSinceReferenceDate.truncatingRemainder(dividingBy: 0.9) / 0.9) : 0
                if let size {
                    CarPainter.drawRoad(&ctx, size: s, phase: phase)
                    var c = ctx
                    c.translateBy(x: 0, y: sin(phase * .pi * 4) * s.height * 0.008)
                    CarPainter.drawCar(&c, size: s, kind: size, paint: paint, phase: phase, special: special)
                } else {
                    CarPainter.drawGhost(&ctx, size: s)
                }
            }
        }
        .aspectRatio(1.7, contentMode: .fit)
    }
}

/// The profile header strip: a full-width road with the car driving in the middle.
struct CarOnRoad: View {
    let size: String?
    let paint: Color
    var special: String?
    var height: CGFloat = 78

    var body: some View {
        TimelineView(.animation(minimumInterval: 1 / 30, paused: size == nil)) { t in
            Canvas { ctx, s in
                let phase = CGFloat(t.date.timeIntervalSinceReferenceDate.truncatingRemainder(dividingBy: 0.9) / 0.9)
                let line = CarPainter.carH - 0.09
                let roadTop = s.height * line, roadH = s.height - roadTop
                ctx.fill(Path(CGRect(x: 0, y: roadTop - 1.5, width: s.width, height: 1.5)), with: .color(Color(hex: 0x8A94A3)))
                ctx.fill(Path(CGRect(x: 0, y: roadTop, width: s.width, height: roadH)), with: .color(Color(hex: 0x2B2F36)))
                let dash = roadH * 1.1, step = dash * 1.9
                var x = -phase * step
                while x < s.width {
                    ctx.fill(Path(CGRect(x: x, y: roadTop + roadH * 0.5, width: dash, height: roadH * 0.14)), with: .color(Color(hex: 0xE8EAED)))
                    x += step
                }
                guard let size else { return }
                let k: CGFloat = size == "compact" ? 0.8 : size == "large" ? 1 : 0.9
                let carH = s.height * k, carW = carH * 1.7
                var c = ctx
                c.translateBy(x: (s.width - carW) / 2, y: s.height - carH - s.height * (1 - line) * (1 - k) + sin(phase * .pi * 4) * carH * 0.008)
                CarPainter.drawCar(&c, size: CGSize(width: carW, height: carH), kind: size, paint: paint, phase: phase, special: special)
            }
        }
        .frame(height: height)
    }
}

enum CarPainter {
    static let carH: CGFloat = 0.86

    private struct Shape {
        let left, right, belt, roof, rearRoof, frontRoof, rearGlass, windscreen: CGFloat
        let trunk: CGFloat?
        let wheelR, rearWheel, frontWheel, sill: CGFloat
    }

    private static func shape(_ kind: String) -> Shape {
        switch kind {
        case "compact": Shape(left: 0.14, right: 0.86, belt: 0.53, roof: 0.25, rearRoof: 0.24, frontRoof: 0.56, rearGlass: 0.15, windscreen: 0.70, trunk: nil, wheelR: 0.135, rearWheel: 0.29, frontWheel: 0.71, sill: 0.79)
        case "large": Shape(left: 0.06, right: 0.97, belt: 0.40, roof: 0.04, rearRoof: 0.12, frontRoof: 0.70, rearGlass: 0.09, windscreen: 0.82, trunk: nil, wheelR: 0.19, rearWheel: 0.24, frontWheel: 0.79, sill: 0.72)
        default: Shape(left: 0.03, right: 0.97, belt: 0.52, roof: 0.20, rearRoof: 0.33, frontRoof: 0.62, rearGlass: 0.24, windscreen: 0.76, trunk: 0.47, wheelR: 0.15, rearWheel: 0.22, frontWheel: 0.79, sill: 0.78)
        }
    }

    private static func body(_ kind: String, _ size: CGSize) -> Path {
        let w = size.width, h = size.height * carH, k = shape(kind)
        var p = Path()
        p.move(to: CGPoint(x: w * k.left, y: h * k.sill))
        if let t = k.trunk {
            p.addLine(to: CGPoint(x: w * k.left, y: h * (t + 0.05)))
            p.addQuadCurve(to: CGPoint(x: w * (k.left + 0.05), y: h * t), control: CGPoint(x: w * k.left, y: h * t))
            p.addLine(to: CGPoint(x: w * k.rearGlass, y: h * t))
        } else {
            p.addLine(to: CGPoint(x: w * k.left, y: h * (k.roof + 0.10)))
            p.addQuadCurve(to: CGPoint(x: w * (k.left + 0.06), y: h * k.roof), control: CGPoint(x: w * k.left, y: h * k.roof))
        }
        p.addLine(to: CGPoint(x: w * k.rearRoof, y: h * k.roof))
        p.addLine(to: CGPoint(x: w * k.frontRoof, y: h * k.roof))
        p.addLine(to: CGPoint(x: w * k.windscreen, y: h * k.belt))
        p.addLine(to: CGPoint(x: w * (k.right - 0.06), y: h * (k.belt + 0.03)))
        p.addQuadCurve(to: CGPoint(x: w * k.right, y: h * (k.belt + 0.14)), control: CGPoint(x: w * k.right, y: h * (k.belt + 0.05)))
        p.addLine(to: CGPoint(x: w * k.right, y: h * k.sill))
        p.closeSubpath()
        return p
    }

    private static func vertical(_ hex: [UInt32], _ size: CGSize) -> GraphicsContext.Shading {
        .linearGradient(Gradient(colors: hex.map { Color(hex: $0) }), startPoint: CGPoint(x: 0, y: 0), endPoint: CGPoint(x: 0, y: size.height * carH))
    }

    static func drawCar(_ ctx: inout GraphicsContext, size: CGSize, kind: String, paint: Color, phase: CGFloat, special: String?) {
        let w = size.width, h = size.height * carH, k = shape(kind)
        let resolved = UIColor(paint)
        var red: CGFloat = 0, green: CGFloat = 0, blue: CGFloat = 0, alpha: CGFloat = 0
        resolved.getRed(&red, green: &green, blue: &blue, alpha: &alpha)
        let light = 0.299 * red + 0.587 * green + 0.114 * blue > 0.7
        func mix(_ other: CGFloat, _ t: CGFloat) -> Color {
            Color(red: red + (other - red) * t, green: green + (other - green) * t, blue: blue + (other - blue) * t)
        }

        ctx.fill(Path(ellipseIn: CGRect(x: w * (k.left + 0.02), y: h * 0.85, width: w * (k.right - k.left - 0.04), height: h * 0.09)), with: .color(.black.opacity(0.16)))
        let carBody = body(kind, size)
        switch special {
        case "paint_gold":
            ctx.fill(carBody, with: vertical([0xFFF1B8, 0xE2B33C, 0x9C7418, 0xE9C25A], size))
        case "paint_chrome":
            ctx.fill(carBody, with: vertical([0xFFFFFF, 0xB8BEC6, 0x5F6670, 0xD9DEE3], size))
            ctx.stroke(carBody, with: .color(Color(hex: 0x8A9097)), lineWidth: w * 0.01)
        case "paint_galaxy":
            ctx.fill(carBody, with: .linearGradient(Gradient(colors: [Color(hex: 0x1A0B3D), Color(hex: 0x4A1F8C), Color(hex: 0x0E2A6B)]), startPoint: .zero, endPoint: CGPoint(x: w, y: h)))
            var c = ctx
            c.clip(to: carBody)
            var seed: UInt64 = 7
            func next() -> CGFloat {
                seed = seed &* 6364136223846793005 &+ 1442695040888963407
                return CGFloat(seed >> 40) / CGFloat(1 << 24)
            }
            for _ in 0..<26 {
                let a = 0.55 + next() * 0.45, r = w * (0.004 + next() * 0.006), x = w * next(), y = h * next()
                c.fill(Path(ellipseIn: CGRect(x: x - r, y: y - r, width: r * 2, height: r * 2)), with: .color(.white.opacity(a)))
            }
        case "paint_flag":
            ctx.fill(carBody, with: .color(Color(hex: 0xF7F9FC)))
            var c = ctx
            c.clip(to: carBody)
            let band = h * 0.055
            c.fill(Path(CGRect(x: 0, y: h * (k.belt + 0.04), width: w, height: band)), with: .color(Color(hex: 0x0038B8)))
            c.fill(Path(CGRect(x: 0, y: h * (k.sill - 0.10), width: w, height: band)), with: .color(Color(hex: 0x0038B8)))
            ctx.stroke(carBody, with: .color(Color(hex: 0xB0B8C4)), lineWidth: w * 0.01)
        case "paint_neon":
            ctx.stroke(carBody, with: .color(Color(hex: 0x39FF14).opacity(0.35)), lineWidth: w * 0.05)
            ctx.fill(carBody, with: vertical([0xB6FF9E, 0x39FF14, 0x1FA80A], size))
        default:
            ctx.fill(carBody, with: .linearGradient(Gradient(colors: [mix(1, 0.25), paint, mix(0, 0.25)]), startPoint: .zero, endPoint: CGPoint(x: 0, y: h)))
            if light { ctx.stroke(carBody, with: .color(Color(hex: 0x9AA0A6)), lineWidth: w * 0.012) }
            if special == "paint_flames" { drawFlames(&ctx, size: size, kind: kind) }
        }

        // Glass: follows the roof, one pillar in the middle.
        let inset: CGFloat = 0.03, glassTop = h * (k.roof + 0.04), glassBottom = h * (k.belt - 0.03)
        var glass = Path()
        glass.move(to: CGPoint(x: w * (k.rearGlass + inset), y: glassBottom))
        glass.addLine(to: CGPoint(x: w * (k.rearRoof + inset * 0.5), y: glassTop))
        glass.addLine(to: CGPoint(x: w * (k.frontRoof - inset * 0.5), y: glassTop))
        glass.addLine(to: CGPoint(x: w * (k.windscreen - inset * 1.5), y: glassBottom))
        glass.closeSubpath()
        ctx.fill(glass, with: .linearGradient(Gradient(colors: [Color(hex: 0xBFD9F2), Color(hex: 0x6E8FB3)]), startPoint: CGPoint(x: 0, y: glassTop), endPoint: CGPoint(x: w, y: glassBottom)))

        let pillar = w * ((k.rearRoof + k.frontRoof) / 2 + 0.02)
        let trim: Color = switch special {
        case "paint_gold": Color(hex: 0x93741F)
        case "paint_chrome": Color(hex: 0x646869)
        case "paint_neon": Color(hex: 0x25A60D)
        case "paint_galaxy": Color(hex: 0x30145B)
        case "paint_flag": Color(hex: 0x002477)
        default: mix(0, 0.35)
        }
        func line(_ a: CGPoint, _ b: CGPoint, _ color: Color, _ width: CGFloat) {
            var p = Path()
            p.move(to: a)
            p.addLine(to: b)
            ctx.stroke(p, with: .color(color), lineWidth: width)
        }
        line(CGPoint(x: pillar, y: glassTop), CGPoint(x: pillar, y: glassBottom), trim, w * 0.022)
        line(CGPoint(x: pillar, y: glassBottom), CGPoint(x: pillar, y: h * (k.sill - 0.05)), trim, w * 0.006)
        if kind == "large" {
            ctx.fill(Path(roundedRect: CGRect(x: 0, y: h * 0.30, width: w * 0.075, height: h * 0.30), cornerRadius: w * 0.03), with: .color(Color(hex: 0x202124)))
            ctx.fill(Path(roundedRect: CGRect(x: w * 0.018, y: h * 0.36, width: w * 0.04, height: h * 0.18), cornerRadius: w * 0.02), with: .color(Color(hex: 0x5F6368)))
            line(CGPoint(x: w * (k.rearRoof + 0.02), y: h * (k.roof - 0.025)), CGPoint(x: w * (k.frontRoof - 0.04), y: h * (k.roof - 0.025)), Color(hex: 0x3C4043), w * 0.014)
        }
        ctx.fill(Path(roundedRect: CGRect(x: w * (k.right - 0.06), y: h * (k.belt + 0.08), width: w * 0.05, height: h * 0.06), cornerRadius: w * 0.01), with: .color(Color(hex: 0xFFE082)))
        ctx.fill(Path(roundedRect: CGRect(x: w * k.left, y: h * ((k.trunk ?? k.belt) + 0.07), width: w * 0.03, height: h * 0.07), cornerRadius: w * 0.01), with: .color(Color(hex: 0xE53935)))

        let r = h * k.wheelR
        for cx in [w * k.rearWheel, w * k.frontWheel] {
            let c = CGPoint(x: cx, y: h * k.sill)
            func disc(_ radius: CGFloat, _ hex: UInt32) {
                ctx.fill(Path(ellipseIn: CGRect(x: c.x - radius, y: c.y - radius, width: radius * 2, height: radius * 2)), with: .color(Color(hex: hex)))
            }
            disc(r * 1.02, 0x202124)
            disc(r * 0.52, 0xBDC1C6)
            for a in 0..<3 {
                let angle = phase * .pi * 2 + CGFloat(a) * .pi / 3
                let dx = cos(angle) * r * 0.48, dy = sin(angle) * r * 0.48
                line(CGPoint(x: c.x - dx, y: c.y - dy), CGPoint(x: c.x + dx, y: c.y + dy), Color(hex: 0x80868B), r * 0.14)
            }
            disc(r * 0.18, 0x5F6368)
        }
    }

    static func drawRoad(_ ctx: inout GraphicsContext, size: CGSize, phase: CGFloat) {
        let top = size.height * (carH - 0.09), roadH = size.height - top
        ctx.fill(Path(roundedRect: CGRect(x: 0, y: top, width: size.width, height: roadH), cornerRadius: roadH / 2), with: .color(Color(hex: 0x3C4043)))
        let dash = size.width * 0.09, step = dash * 1.9
        var c = ctx
        c.clip(to: Path(CGRect(x: size.width * 0.05, y: 0, width: size.width * 0.9, height: size.height)))
        var x = size.width * 0.05 - phase * step
        while x < size.width {
            c.fill(Path(CGRect(x: x, y: top + roadH * 0.62, width: dash, height: roadH * 0.12)), with: .color(Color(hex: 0xF1F3F4)))
            x += step
        }
    }

    private static func drawFlames(_ ctx: inout GraphicsContext, size: CGSize, kind: String) {
        let w = size.width, h = size.height * carH, k = shape(kind)
        let base = h * (k.sill - 0.04), top = h * (k.belt + 0.06), start = w * (k.right - 0.10)
        let tongues = 4, span = (start - w * (k.left + 0.12)) / CGFloat(tongues)
        for (hex, shrink) in [(UInt32(0xFF5722), CGFloat(0)), (0xFFC107, 0.35)] {
            var p = Path()
            p.move(to: CGPoint(x: start, y: base))
            p.addLine(to: CGPoint(x: start, y: top + (base - top) * shrink))
            for i in 0..<tongues {
                let x0 = start - span * CGFloat(i), tip = top + (base - top) * (shrink + 0.15 * CGFloat(i))
                p.addQuadCurve(to: CGPoint(x: x0 - span, y: tip + (base - top) * 0.35), control: CGPoint(x: x0 - span * 0.7, y: tip - h * 0.02))
            }
            p.addLine(to: CGPoint(x: start - span * CGFloat(tongues), y: base))
            p.closeSubpath()
            ctx.fill(p, with: .color(Color(hex: hex)))
        }
    }

    static func drawGhost(_ ctx: inout GraphicsContext, size: CGSize) {
        let style = StrokeStyle(lineWidth: size.width * 0.014, dash: [size.width * 0.03, size.width * 0.02])
        ctx.stroke(body("regular", size), with: .color(.pkOutline), style: style)
        let r = size.height * 0.15
        for cx in [size.width * 0.23, size.width * 0.78] {
            ctx.stroke(Path(ellipseIn: CGRect(x: cx - r, y: size.height * 0.78 - r, width: r * 2, height: r * 2)), with: .color(.pkOutline), style: style)
        }
    }
}

/// Profile header backgrounds bought in the shop ("bg_*"), drawn in code. null = default navy.
struct ProfileBackdrop: View {
    let key: String?

    var body: some View {
        Canvas { ctx, s in
            let w = s.width, h = s.height
            func sky(_ hex: [UInt32]) {
                ctx.fill(Path(CGRect(origin: .zero, size: s)), with: .linearGradient(Gradient(colors: hex.map { Color(hex: $0) }), startPoint: .zero, endPoint: CGPoint(x: 0, y: h)))
            }
            func disc(_ hex: UInt32, _ alpha: Double, _ r: CGFloat, _ x: CGFloat, _ y: CGFloat) {
                ctx.fill(Path(ellipseIn: CGRect(x: x - r, y: y - r, width: r * 2, height: r * 2)), with: .color(Color(hex: hex).opacity(alpha)))
            }
            func wave(_ y: CGFloat, _ hex: UInt32, _ alpha: Double, _ amp: CGFloat, _ shift: CGFloat) {
                var p = Path()
                p.move(to: CGPoint(x: 0, y: y))
                var x = -shift
                let step = w / 6
                while x < w + step {
                    p.addQuadCurve(to: CGPoint(x: x + step / 2, y: y), control: CGPoint(x: x + step / 4, y: y - amp))
                    p.addQuadCurve(to: CGPoint(x: x + step, y: y), control: CGPoint(x: x + step * 3 / 4, y: y + amp))
                    x += step
                }
                p.addLine(to: CGPoint(x: w, y: h))
                p.addLine(to: CGPoint(x: 0, y: h))
                p.closeSubpath()
                ctx.fill(p, with: .color(Color(hex: hex).opacity(alpha)))
            }
            func dune(_ y: CGFloat, _ hex: UInt32, _ alpha: Double, _ peak: CGFloat) {
                var p = Path()
                p.move(to: CGPoint(x: 0, y: y + h * 0.06))
                p.addQuadCurve(to: CGPoint(x: w, y: y + h * 0.04), control: CGPoint(x: peak, y: y - h * 0.1))
                p.addLine(to: CGPoint(x: w, y: h))
                p.addLine(to: CGPoint(x: 0, y: h))
                p.closeSubpath()
                ctx.fill(p, with: .color(Color(hex: hex).opacity(alpha)))
            }
            func skyline(_ hex: UInt32, _ alpha: Double, windows: UInt32?) {
                let base = h * 0.8, tw = w * 0.06
                let towers: [(CGFloat, CGFloat)] = [(0, 0.30), (0.06, 0.42), (0.13, 0.26), (0.19, 0.5), (0.25, 0.34), (0.62, 0.38), (0.69, 0.55), (0.76, 0.3), (0.83, 0.46), (0.9, 0.36), (0.96, 0.28)]
                for (x, frac) in towers {
                    let top = base - h * frac * 0.75
                    ctx.fill(Path(CGRect(x: w * x, y: top, width: tw, height: base - top)), with: .color(Color(hex: hex).opacity(alpha)))
                    guard let windows else { continue }
                    var wy = top + h * 0.04, row = 0
                    while wy < base - h * 0.04 {
                        if (row + Int(x * 100)) % 3 != 0 {
                            ctx.fill(Path(CGRect(x: w * x + tw * 0.25, y: wy, width: tw * 0.18, height: h * 0.018)), with: .color(Color(hex: windows).opacity(0.55)))
                            ctx.fill(Path(CGRect(x: w * x + tw * 0.6, y: wy, width: tw * 0.18, height: h * 0.018)), with: .color(Color(hex: windows).opacity(0.35)))
                        }
                        wy += h * 0.05
                        row += 1
                    }
                }
            }
            switch key {
            case "bg_sea":
                sky([0x0B3D63, 0x1C6E9C])
                disc(0xFFE08A, 0.85, h * 0.07, w * 0.16, h * 0.24)
                wave(h * 0.62, 0x2A86B8, 0.55, h * 0.025, 0)
                wave(h * 0.70, 0x3B9CCB, 0.55, h * 0.02, w * 0.1)
                wave(h * 0.78, 0x5DB4DC, 0.5, h * 0.018, w * 0.2)
            case "bg_sunset":
                sky([0x2B1846, 0x7A2E5C, 0xC75A3A])
                disc(0xFFB45C, 0.9, h * 0.11, w * 0.22, h * 0.62)
                skyline(0x2A1430, 0.92, windows: nil)
            case "bg_desert":
                sky([0x4A2414, 0x8E4A24, 0xC07A3A])
                disc(0xFFD27A, 0.8, h * 0.08, w * 0.8, h * 0.22)
                dune(h * 0.66, 0x6E3518, 0.8, w * 0.35)
                dune(h * 0.74, 0x5A2A12, 0.9, w * 0.75)
            case "bg_night":
                sky([0x070B1E, 0x16213E])
                for (x, y) in [(0.08, 0.12), (0.2, 0.3), (0.33, 0.08), (0.47, 0.22), (0.6, 0.1), (0.71, 0.28), (0.86, 0.14), (0.93, 0.34), (0.4, 0.4), (0.14, 0.46)] as [(CGFloat, CGFloat)] {
                    disc(0xFFFFFF, 0.75, h * 0.007, w * x, h * y)
                }
                disc(0xF1F3F4, 1, h * 0.06, w * 0.82, h * 0.2)
                disc(0x070B1E, 1, h * 0.055, w * 0.845, h * 0.185)
                skyline(0x0E1530, 1, windows: 0xFFD27A)
            default:
                sky([0x14243A, 0x1B3556])
            }
        }
    }
}
