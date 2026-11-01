import SwiftUI
import UIKit

/// Spot pins, drawn at runtime so each carries its own freshness tag (MarkerBitmaps.kt):
/// a coloured head with a bold "P", a short tail to the exact coordinate, and the age on
/// the head's shoulder.
enum Pins {
    private static let head: CGFloat = 34, tail: CGFloat = 9, border: CGFloat = 2.5, frameWidth: CGFloat = 3.5, pad: CGFloat = 4, tagH: CGFloat = 16

    /// Shadow padding under the tip, so the tip (not the image edge) sits on the coordinate.
    static let tipPadding = pad
    static let lotColor = "#4A5B78"

    private static func ui(_ hex: String) -> UIColor { UIColor(Color(hexString: hex) ?? .green) }

    static func pin(color: String, tag: String, disabled: Bool = false, hot: Bool = false, frame: String? = nil) -> UIImage {
        let r = head / 2
        let flame = hot ? r * 0.9 : 0
        let above = max(tagH * 0.35, flame)
        let tagFont = UIFont.systemFont(ofSize: 10, weight: .bold)
        let tagW = tag.isEmpty ? 0 : (tag as NSString).size(withAttributes: [.font: tagFont]).width + 10
        let halfW = max(hot ? r * 1.55 : r, r * 0.35 + tagW)
        let size = CGSize(width: ceil(2 * (pad + halfW)), height: ceil(pad + above + head + tail + pad))
        return UIGraphicsImageRenderer(size: size).image { renderer in
            let ctx = renderer.cgContext
            let cx = size.width / 2, cy = pad + above + r, tipY = cy + r + tail
            if hot { drawFlames(ctx, cx: cx, cy: cy, r: r, height: flame) }

            let outline = UIBezierPath(arcCenter: CGPoint(x: cx, y: cy), radius: r, startAngle: 0, endAngle: .pi * 2, clockwise: true)
            let taper = UIBezierPath()
            taper.move(to: CGPoint(x: cx - r * 0.45, y: cy + r * 0.7))
            taper.addLine(to: CGPoint(x: cx, y: tipY))
            taper.addLine(to: CGPoint(x: cx + r * 0.45, y: cy + r * 0.7))
            taper.close()
            ctx.saveGState()
            ctx.setShadow(offset: CGSize(width: 0, height: 1.5), blur: 3, color: UIColor.black.withAlphaComponent(0.35).cgColor)
            (frame.map(ui) ?? .white).setFill()
            outline.fill()
            taper.fill()
            ctx.restoreGState()

            ui(color).setFill()
            let inner = r - (frame != nil ? frameWidth : border)
            UIBezierPath(arcCenter: CGPoint(x: cx, y: cy), radius: inner, startAngle: 0, endAngle: .pi * 2, clockwise: true).fill()
            let innerTail = UIBezierPath()
            innerTail.move(to: CGPoint(x: cx - inner * 0.42, y: cy + inner * 0.72))
            innerTail.addLine(to: CGPoint(x: cx, y: tipY - border * 1.6))
            innerTail.addLine(to: CGPoint(x: cx + inner * 0.42, y: cy + inner * 0.72))
            innerTail.close()
            innerTail.fill()

            if disabled {
                let config = UIImage.SymbolConfiguration(pointSize: 16, weight: .bold)
                if let glyph = UIImage(systemName: "figure.roll", withConfiguration: config)?.withTintColor(.white, renderingMode: .alwaysOriginal) {
                    glyph.draw(at: CGPoint(x: cx - glyph.size.width / 2, y: cy - glyph.size.height / 2))
                }
            } else {
                draw("P", font: .systemFont(ofSize: 18, weight: .bold), color: .white, center: CGPoint(x: cx, y: cy))
            }

            if !tag.isEmpty {
                let rect = CGRect(x: cx + r * 0.35, y: cy - r - tagH * 0.35, width: tagW, height: tagH)
                ctx.saveGState()
                ctx.setShadow(offset: CGSize(width: 0, height: 1), blur: 2, color: UIColor.black.withAlphaComponent(0.28).cgColor)
                UIColor.white.setFill()
                UIBezierPath(roundedRect: rect, cornerRadius: tagH / 2).fill()
                ctx.restoreGState()
                draw(tag, font: tagFont, color: UIColor(red: 0x20 / 255, green: 0x21 / 255, blue: 0x24 / 255, alpha: 1), center: CGPoint(x: rect.midX, y: rect.midY))
            }
        }
    }

    private static func draw(_ text: String, font: UIFont, color: UIColor, center: CGPoint) {
        let attrs: [NSAttributedString.Key: Any] = [.font: font, .foregroundColor: color]
        let s = (text as NSString).size(withAttributes: attrs)
        (text as NSString).draw(at: CGPoint(x: center.x - s.width / 2, y: center.y - s.height / 2), withAttributes: attrs)
    }

    /// Three flame tongues behind the pin's head: yellow at the root, a red tip, a warm glow.
    private static func drawFlames(_ ctx: CGContext, cx: CGFloat, cy: CGFloat, r: CGFloat, height: CGFloat) {
        let space = CGColorSpaceCreateDeviceRGB()
        let glow = CGGradient(colorsSpace: space, colors: [UIColor(red: 1, green: 0.55, blue: 0, alpha: 0.55).cgColor, UIColor(red: 1, green: 0.55, blue: 0, alpha: 0).cgColor] as CFArray, locations: [0.5, 1])!
        let glowC = CGPoint(x: cx, y: cy - r * 0.2)
        ctx.drawRadialGradient(glow, startCenter: glowC, startRadius: 0, endCenter: glowC, endRadius: r * 1.5, options: [])
        let flame = CGGradient(colorsSpace: space, colors: ["#FFD54F", "#FF9800", "#F4511E", "#D84315"].map { ui($0).cgColor } as CFArray, locations: [0, 0.35, 0.75, 1])!
        for (lean, tall, wide) in [(CGFloat(-30), CGFloat(0.72), CGFloat(0.78)), (30, 0.72, 0.78), (0, 1, 1)] {
            let baseY = cy - r * 0.35, tipY = cy - r - height * tall, half = r * 0.62 * wide
            ctx.saveGState()
            ctx.translateBy(x: cx, y: cy)
            ctx.rotate(by: lean * .pi / 180)
            ctx.translateBy(x: -cx, y: -cy)
            let p = UIBezierPath()
            p.move(to: CGPoint(x: cx - half, y: baseY))
            p.addCurve(to: CGPoint(x: cx + half * 0.12, y: tipY), controlPoint1: CGPoint(x: cx - half * 1.15, y: baseY - (baseY - tipY) * 0.55), controlPoint2: CGPoint(x: cx - half * 0.1, y: tipY + (baseY - tipY) * 0.25))
            p.addCurve(to: CGPoint(x: cx + half, y: baseY), controlPoint1: CGPoint(x: cx + half * 0.2, y: tipY + (baseY - tipY) * 0.35), controlPoint2: CGPoint(x: cx + half * 1.15, y: baseY - (baseY - tipY) * 0.4))
            p.close()
            ctx.saveGState()
            p.addClip()
            ctx.drawLinearGradient(flame, start: CGPoint(x: cx, y: baseY), end: CGPoint(x: cx, y: tipY), options: [])
            ctx.restoreGState()
            ctx.restoreGState()
        }
    }

    /// Paid car park: a rounded slate square, so it never reads as a free spot.
    static func lotPin() -> UIImage {
        let side: CGFloat = 26, total = side + 2 * pad
        return UIGraphicsImageRenderer(size: CGSize(width: total, height: total)).image { renderer in
            let ctx = renderer.cgContext
            let rect = CGRect(x: pad, y: pad, width: side, height: side)
            ctx.saveGState()
            ctx.setShadow(offset: CGSize(width: 0, height: 1), blur: 2.5, color: UIColor.black.withAlphaComponent(0.35).cgColor)
            UIColor.white.setFill()
            UIBezierPath(roundedRect: rect, cornerRadius: 7).fill()
            ctx.restoreGState()
            ui(lotColor).setFill()
            UIBezierPath(roundedRect: rect.insetBy(dx: 2, dy: 2), cornerRadius: 5.5).fill()
            draw("P", font: .systemFont(ofSize: 15, weight: .bold), color: .white, center: CGPoint(x: rect.midX, y: rect.midY))
        }
    }

    /// The driver's own car as a map marker: the profile's art in a white bubble with a pointer.
    @MainActor static func carMarker(kind: String?, paint: Color, special: String?) -> UIImage {
        let w: CGFloat = 46, h: CGFloat = 38, bubble = h * 0.82
        let view = Canvas { ctx, size in
            let box = CGRect(x: 0, y: 0, width: w - 2, height: bubble - 3)
            ctx.fill(Path(roundedRect: box.offsetBy(dx: 1, dy: 1.5), cornerRadius: bubble * 0.3), with: .color(.black.opacity(0.18)))
            ctx.fill(Path(roundedRect: box, cornerRadius: bubble * 0.3), with: .color(.white))
            var tail = Path()
            tail.move(to: CGPoint(x: w / 2 - 7, y: bubble - 4))
            tail.addLine(to: CGPoint(x: w / 2, y: h))
            tail.addLine(to: CGPoint(x: w / 2 + 7, y: bubble - 4))
            tail.closeSubpath()
            ctx.fill(tail, with: .color(.white))
            var inner = ctx
            inner.translateBy(x: 3.5, y: 3.5)
            CarPainter.drawCar(&inner, size: CGSize(width: w - 9, height: bubble - 10), kind: kind ?? "regular", paint: paint, phase: 0, special: special)
        }
        .frame(width: w, height: h)
        let r = ImageRenderer(content: view)
        r.scale = UIScreen.main.scale
        return r.uiImage ?? UIImage()
    }
}

/// A pin as a view, for the tips and the level ladder.
struct PinImage: View {
    let color: String
    let tag: String
    var hot = false
    var frame: String?
    var scale: CGFloat = 1

    var body: some View {
        let image = Pins.pin(color: color, tag: tag, hot: hot, frame: frame)
        Image(uiImage: image).resizable().scaledToFit().frame(width: image.size.width * scale, height: image.size.height * scale)
    }
}
