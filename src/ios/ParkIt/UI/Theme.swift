import SwiftUI
import UIKit

extension Color {
    init(hex: UInt32, alpha: Double = 1) {
        self.init(.sRGB, red: Double(hex >> 16 & 0xFF) / 255, green: Double(hex >> 8 & 0xFF) / 255, blue: Double(hex & 0xFF) / 255, opacity: alpha)
    }

    /// "#RRGGBB" from the server, or nil.
    init?(hexString: String?) {
        guard let s = hexString?.replacingOccurrences(of: "#", with: ""), s.count == 6, let v = UInt32(s, radix: 16) else { return nil }
        self.init(hex: v)
    }

    private static func dynamic(_ light: UInt32, _ dark: UInt32) -> Color {
        func ui(_ v: UInt32) -> UIColor {
            UIColor(red: CGFloat(v >> 16 & 0xFF) / 255, green: CGFloat(v >> 8 & 0xFF) / 255, blue: CGFloat(v & 0xFF) / 255, alpha: 1)
        }
        return Color(uiColor: UIColor { $0.userInterfaceStyle == .dark ? ui(dark) : ui(light) })
    }

    // ui/theme/Color.kt
    static let pkPrimary = dynamic(0x1B4F91, 0x6AA3E0)
    static let pkOnPrimary = dynamic(0xFFFFFF, 0x12161D)
    static let pkTint = dynamic(0xEAF1FA, 0x16283B)
    static let pkBackground = dynamic(0xF3F4F7, 0x12161D)
    static let pkSurface = dynamic(0xFFFFFF, 0x1A2029)
    static let pkSurfaceVariant = dynamic(0xE7E9EE, 0x2A313D)
    static let pkText = dynamic(0x1A1F29, 0xE8EAEE)
    static let pkMuted = dynamic(0x5B6472, 0x97A1AF)
    static let pkOutline = dynamic(0xDEE2E8, 0x2A313D)
    static let pkError = dynamic(0xB3261E, 0xF2B8B5)

    static let pkGreen = Color(hex: 0x188038)
    static let pkOrange = Color(hex: 0xE8710A)
    static let pkAmber = Color(hex: 0xE37400)
    static let pkBlue = Color(hex: 0x1B4F91)
}

/// Android's type scale (ui/theme/Type.kt). All Heebo, from the one variable font
/// (its weight axis is set directly so every cut is the real one, never faked).
enum PKFont {
    private static var cache: [String: UIFont] = [:]

    static func heebo(_ size: CGFloat, _ weight: Int) -> Font {
        Font(ui(size, weight))
    }

    static func ui(_ size: CGFloat, _ weight: Int) -> UIFont {
        let key = "\(size)-\(weight)"
        if let hit = cache[key] { return hit }
        let wght = 0x77676874 // 'wght'
        let descriptor = UIFontDescriptor(name: "Heebo-Regular", size: size).addingAttributes([
            UIFontDescriptor.AttributeName(rawValue: kCTFontVariationAttribute as String): [wght: weight],
        ])
        let font = UIFont(descriptor: descriptor, size: size)
        cache[key] = font
        return font
    }

    static let displaySmall = heebo(36, 800)
    static let headlineLarge = heebo(32, 800)
    static let headlineMedium = heebo(28, 800)
    static let headlineSmall = heebo(24, 700)
    static let titleLarge = heebo(20, 700)
    static let titleMedium = heebo(16, 700)
    static let titleSmall = heebo(15, 500)
    static let bodyLarge = heebo(16, 400)
    static let bodyMedium = heebo(14, 400)
    static let bodySmall = heebo(12, 400)
    static let labelLarge = heebo(14, 700)
    static let labelMedium = heebo(12, 500)
    static let labelSmall = heebo(11, 500)

    static func wordmark(_ size: CGFloat) -> Font { .custom("SuezOne-Regular", size: size) }
}

// MARK: Shared pieces

struct PillButtonStyle: ButtonStyle {
    var color: Color = .pkPrimary
    var foreground: Color = .pkOnPrimary
    @Environment(\.isEnabled) private var enabled

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(PKFont.titleMedium)
            .foregroundStyle(enabled ? foreground : Color.pkMuted)
            .frame(maxWidth: .infinity)
            .frame(height: 52)
            .background(enabled ? color : Color.pkText.opacity(0.12), in: Capsule())
            .opacity(configuration.isPressed ? 0.85 : 1)
    }
}

/// Flat card with a hairline border (SectionCard).
struct SectionCard<Content: View>: View {
    @ViewBuilder var content: Content
    var body: some View {
        content
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Color.pkSurface, in: RoundedRectangle(cornerRadius: 20))
            .overlay(RoundedRectangle(cornerRadius: 20).stroke(Color.pkOutline.opacity(0.7), lineWidth: 1))
            .padding(.horizontal, 16)
    }
}

struct SectionTitle: View {
    let text: String
    init(_ text: String) { self.text = text }
    var body: some View {
        Text(text).font(PKFont.titleMedium).foregroundStyle(Color.pkText)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 20).padding(.top, 24).padding(.bottom, 10)
    }
}

struct ScreenTopBar<Action: View>: View {
    let title: String
    let onBack: () -> Void
    @ViewBuilder var action: Action

    var body: some View {
        HStack(spacing: 4) {
            Button(action: onBack) {
                Image(systemName: "chevron.backward").font(.system(size: 18, weight: .semibold))
                    .foregroundStyle(Color.pkText).frame(width: 48, height: 48).contentShape(Rectangle())
            }
            .accessibilityLabel(L("profile_back_cd"))
            Text(title).font(PKFont.titleLarge).foregroundStyle(Color.pkText)
            Spacer()
            action
        }
        .frame(height: 56).padding(.horizontal, 4)
    }
}

extension ScreenTopBar where Action == EmptyView {
    init(title: String, onBack: @escaping () -> Void) {
        self.init(title: title, onBack: onBack) { EmptyView() }
    }
}

/// An outlined text field with a floating-less label above, close to Android's fields.
struct LabeledField: View {
    let label: String
    @Binding var text: String
    var placeholder: String = ""
    var keyboard: UIKeyboardType = .default
    var capitalization: TextInputAutocapitalization = .sentences
    var disabled = false
    var isError = false
    var content: UITextContentType?
    var identifier: String?
    var onSubmit: () -> Void = {}

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(label).font(PKFont.labelMedium).foregroundStyle(isError ? Color.pkError : Color.pkMuted)
            TextField(placeholder.isEmpty ? label : placeholder, text: $text)
                .font(PKFont.bodyLarge)
                .foregroundStyle(disabled ? Color.pkMuted : Color.pkText)
                .keyboardType(keyboard)
                .textInputAutocapitalization(capitalization)
                .autocorrectionDisabled()
                .textContentType(content)
                .disabled(disabled)
                .submitLabel(.done)
                .onSubmit(onSubmit)
                .accessibilityIdentifier(identifier ?? label)
                .padding(.horizontal, 14).frame(height: 52)
                .background(disabled ? Color.pkText.opacity(0.04) : Color.clear, in: RoundedRectangle(cornerRadius: 10))
                .overlay(RoundedRectangle(cornerRadius: 10).stroke(isError ? Color.pkError : Color.pkMuted.opacity(0.6), lineWidth: 1))
        }
    }
}

/// ParkIt's logo (res/drawable/ic_logo.xml).
struct LogoMark: View {
    var size: CGFloat
    var body: some View {
        Canvas { ctx, s in
            let k = s.width / 100
            ctx.fill(Path(roundedRect: CGRect(x: 0, y: 0, width: s.width, height: s.width), cornerRadius: 22 * k), with: .color(.pkBlue))
            var pin = Path()
            pin.move(to: CGPoint(x: 50 * k, y: 20 * k))
            pin.addCurve(to: CGPoint(x: 27 * k, y: 42 * k), control1: CGPoint(x: 37 * k, y: 20 * k), control2: CGPoint(x: 27 * k, y: 30 * k))
            pin.addCurve(to: CGPoint(x: 50 * k, y: 80 * k), control1: CGPoint(x: 27 * k, y: 60 * k), control2: CGPoint(x: 50 * k, y: 80 * k))
            pin.addCurve(to: CGPoint(x: 73 * k, y: 42 * k), control1: CGPoint(x: 50 * k, y: 80 * k), control2: CGPoint(x: 73 * k, y: 60 * k))
            pin.addCurve(to: CGPoint(x: 50 * k, y: 20 * k), control1: CGPoint(x: 73 * k, y: 30 * k), control2: CGPoint(x: 63 * k, y: 20 * k))
            ctx.fill(pin, with: .color(.white))
            ctx.fill(Path(ellipseIn: CGRect(x: 40 * k, y: 31 * k, width: 20 * k, height: 20 * k)), with: .color(.pkBlue))
        }
        .frame(width: size, height: size)
        .accessibilityLabel(L("logo_cd"))
    }
}

/// Sizes a sheet to its content, as Android's modal bottom sheets are.
struct FittedSheet: ViewModifier {
    @State private var height: CGFloat = 300
    func body(content: Content) -> some View {
        ScrollView {
            content.background(GeometryReader { g in
                Color.clear.preference(key: SheetHeightKey.self, value: g.size.height)
            })
        }
        .scrollBounceBehavior(.basedOnSize)
        .onPreferenceChange(SheetHeightKey.self) { value in
            MainActor.assumeIsolated { if value > 0 { height = value + 20 } }
        }
        .presentationDetents([.height(height)])
        .presentationDragIndicator(.visible)
        .presentationBackground(Color.pkSurface)
        .presentationCornerRadius(24)
    }
}

private struct SheetHeightKey: PreferenceKey {
    static let defaultValue: CGFloat = 0
    static func reduce(value: inout CGFloat, nextValue: () -> CGFloat) { value = max(value, nextValue()) }
}

extension View {
    func fittedSheet() -> some View { modifier(FittedSheet()) }

    /// The direction, language and colours every presented sheet must re-apply.
    func parkItEnvironment(_ session: Session) -> some View {
        environment(\.layoutDirection, L10n.layoutDirection)
            .environment(\.locale, L10n.locale)
            .preferredColorScheme(session.colorScheme)
    }
}

/// A short message at the bottom of the map, with an optional action (a snackbar).
struct SnackbarView: View {
    let message: String
    var actionLabel: String?
    var action: (() -> Void)?
    var body: some View {
        HStack(spacing: 12) {
            Text(message).font(PKFont.bodyMedium).foregroundStyle(.white).frame(maxWidth: .infinity, alignment: .leading)
            if let actionLabel, let action {
                Button(actionLabel, action: action).font(PKFont.labelLarge).foregroundStyle(Color(hex: 0xA8C8F0))
            }
        }
        .padding(.horizontal, 16).padding(.vertical, 12)
        .background(Color(hex: 0x2B2F36), in: RoundedRectangle(cornerRadius: 10))
        .shadow(radius: 6)
        .padding(.horizontal, 12)
    }
}
