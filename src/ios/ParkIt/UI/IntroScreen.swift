import SwiftUI

private struct Slide {
    let image: String
    let title: String
    let body: String
    let tint: Color
}

/// A bundled image, whatever its file type (the intro shots are WebP).
func bundleImage(_ name: String, _ ext: String = "png") -> UIImage? {
    Bundle.main.path(forResource: name, ofType: ext).flatMap(UIImage.init(contentsOfFile:))
}

/// A horizontal pager that follows the language direction.
private struct Pager<Page: View>: View {
    @Binding var page: Int
    let count: Int
    @ViewBuilder let content: (Int) -> Page

    var body: some View {
        TabView(selection: $page) {
            ForEach(0..<count, id: \.self) { i in content(i).tag(i) }
        }
        .tabViewStyle(.page(indexDisplayMode: .never))
    }
}

struct PageDots: View {
    let count: Int
    let current: Int
    var body: some View {
        HStack(spacing: 8) {
            ForEach(0..<count, id: \.self) { i in
                Capsule().fill(i == current ? Color.pkPrimary : Color.pkOutline)
                    .frame(width: i == current ? 22 : 8, height: 8)
            }
        }
        .padding(.vertical, 14)
    }
}

/// Once per install, before the first sign-in: pick a language, then four slides
/// that show the real app in the chosen language. Skippable.
struct IntroScreen: View {
    let onDone: () -> Void
    @EnvironmentObject private var session: Session
    @State private var page = 0

    private var slides: [Slide] {
        let suffix = L10n.isHebrew ? "he" : "en"
        return [
            Slide(image: "intro_map_\(suffix)", title: L("intro_find_title"), body: L("intro_find_body"), tint: .pkGreen),
            Slide(image: "intro_card_\(suffix)", title: L("intro_go_title"), body: L("intro_go_body"), tint: Color(hex: 0xE8480C)),
            Slide(image: "intro_report_\(suffix)", title: L("intro_report_title"), body: L("intro_report_body"), tint: .pkBlue),
            Slide(image: "intro_profile_\(suffix)", title: L("intro_climb_title"), body: L("intro_climb_body"), tint: Color(hex: 0x7B3FE4)),
        ]
    }

    var body: some View {
        let pages = slides.count + 1
        let last = page == pages - 1
        VStack(spacing: 0) {
            HStack {
                Spacer()
                if page > 0 {
                    Button(L("driver_skip"), action: onDone).font(PKFont.labelLarge).foregroundStyle(Color.pkPrimary).padding(.horizontal, 16)
                }
            }
            .frame(height: 48)
            Pager(page: $page, count: pages) { i in
                if i == 0 { welcome } else { slideView(slides[i - 1]) }
            }
            PageDots(count: pages, current: page)
            Button(page == 0 ? L("intro_how_it_works") : last ? L("intro_start") : L("intro_next")) {
                if last { onDone() } else { withAnimation { page += 1 } }
            }
            .buttonStyle(PillButtonStyle())
            .padding(.horizontal, 24).padding(.bottom, 12)
        }
        .background(Color.pkBackground.ignoresSafeArea())
    }

    /// Logo, one line of what ParkIt is, and the language choice.
    private var welcome: some View {
        VStack(spacing: 0) {
            ZStack {
                Circle().fill(RadialGradient(colors: [Color.pkPrimary.opacity(0.18), .clear], center: .center, startRadius: 0, endRadius: 66))
                    .frame(width: 132, height: 132)
                LogoMark(size: 92)
            }
            Text(verbatim: "ParkIt").font(PKFont.wordmark(36)).foregroundStyle(Color.pkText).padding(.top, 12)
            Text(L("intro_welcome_body")).font(PKFont.bodyLarge).foregroundStyle(Color.pkMuted).multilineTextAlignment(.center).padding(.top, 8)
            Text(L("intro_choose_language")).font(PKFont.labelLarge).foregroundStyle(Color.pkMuted).padding(.top, 36).padding(.bottom, 10)
            HStack(spacing: 12) {
                languageCard("עברית", code: "he")
                languageCard("English", code: "en")
            }
        }
        .padding(.horizontal, 32)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    private func languageCard(_ label: String, code: String) -> some View {
        let selected = L10n.language == code
        return Button {
            if !selected { session.setLanguage(code) }
        } label: {
            HStack(spacing: 8) {
                Text(verbatim: label).font(PKFont.titleMedium).foregroundStyle(Color.pkText)
                if selected {
                    Image(systemName: "checkmark.circle.fill").font(.system(size: 16)).foregroundStyle(Color.pkPrimary)
                }
            }
            .frame(maxWidth: .infinity).frame(height: 64)
            .background(selected ? Color.pkPrimary.opacity(0.08) : Color.pkSurface, in: RoundedRectangle(cornerRadius: 18))
            .overlay(RoundedRectangle(cornerRadius: 18).stroke(selected ? Color.pkPrimary : Color.pkOutline, lineWidth: selected ? 2 : 1))
        }
    }

    /// A real screenshot in a phone frame on a soft colour glow, then what it shows.
    private func slideView(_ slide: Slide) -> some View {
        VStack(spacing: 0) {
            GeometryReader { g in
                let h = g.size.height * 0.92
                let w = h * 540 / 1122
                ZStack {
                    RadialGradient(colors: [slide.tint.opacity(0.2), .clear], center: .center, startRadius: 0, endRadius: g.size.width / 2)
                    Group {
                        if let image = bundleImage(slide.image, "webp") {
                            Image(uiImage: image).resizable().scaledToFill()
                                .frame(width: w - 12, height: h - 12, alignment: .top)
                                .clipShape(RoundedRectangle(cornerRadius: 25))
                        }
                    }
                    .frame(width: w, height: h)
                    .background(Color(hex: 0x1B1F27), in: RoundedRectangle(cornerRadius: 30))
                    .shadow(radius: 12)
                }
            }
            Text(slide.title).font(PKFont.headlineSmall).foregroundStyle(Color.pkText).multilineTextAlignment(.center).padding(.top, 18)
            Text(slide.body).font(PKFont.bodyMedium).foregroundStyle(Color.pkMuted).multilineTextAlignment(.center).padding(.top, 6)
        }
        .padding(.horizontal, 28)
    }
}

/// "How to use ParkIt": five short tips shown once after sign-up (and from Settings).
/// Each draws the real thing it explains, with the same pins the map uses.
struct TipsScreen: View {
    let onDone: () -> Void
    @State private var page = 0

    var body: some View {
        let count = 5
        let last = page == count - 1
        VStack(spacing: 0) {
            HStack {
                Text(L("tips_header")).font(PKFont.titleMedium).foregroundStyle(Color.pkMuted)
                Spacer()
                if !last { Button(L("driver_skip"), action: onDone).font(PKFont.labelLarge).foregroundStyle(Color.pkPrimary) }
            }
            .frame(height: 48).padding(.horizontal, 20)
            Pager(page: $page, count: count) { i in tip(i) }
            PageDots(count: count, current: page)
            Button(last ? L("tips_done") : L("intro_next")) {
                if last { onDone() } else { withAnimation { page += 1 } }
            }
            .buttonStyle(PillButtonStyle())
            .padding(.horizontal, 24).padding(.bottom, 12)
        }
        .background(Color.pkBackground.ignoresSafeArea())
    }

    private func tip(_ i: Int) -> some View {
        let (title, body, tint): (String, String, Color) = switch i {
        case 0: ("tip_map_title", "tip_map_body", .pkGreen)
        case 1: ("tip_hot_title", "tip_hot_body", Color(hex: 0xE8480C))
        case 2: ("tip_go_title", "tip_go_body", .pkBlue)
        case 3: ("tip_report_title", "tip_report_body", .pkBlue)
        default: ("tip_close_title", "tip_close_body", Color(hex: 0x7B3FE4))
        }
        return VStack(spacing: 0) {
            ZStack {
                RadialGradient(colors: [tint.opacity(0.16), .clear], center: .center, startRadius: 0, endRadius: 170)
                art(i)
            }
            Text(L(title)).font(PKFont.headlineSmall).foregroundStyle(Color.pkText).multilineTextAlignment(.center)
            Text(L(body)).font(PKFont.bodyLarge).foregroundStyle(Color.pkMuted).multilineTextAlignment(.center).padding(.vertical, 8)
        }
        .padding(.horizontal, 28)
    }

    @ViewBuilder private func art(_ i: Int) -> some View {
        switch i {
        case 0:
            HStack(alignment: .bottom, spacing: 0) {
                artPin("#0B5E2B", L("tip_tag_fresh"), L("tip_cap_fresh"))
                artPin("#7EC791", L("tip_tag_old"), L("tip_cap_old"))
                artPin("#E8710A", L("tip_tag_fresh"), L("tip_cap_claimed"))
            }
        case 1:
            PinImage(color: "#188038", tag: L("tip_tag_fresh"), hot: true, scale: 2.2)
        case 2:
            VStack(spacing: 20) {
                PinImage(color: "#188038", tag: L("tip_tag_fresh"), scale: 1.6)
                HStack(spacing: 8) {
                    Image(systemName: "location.north.fill").foregroundStyle(Color.pkOnPrimary)
                    Text(L("spot_go")).font(PKFont.titleMedium).foregroundStyle(Color.pkOnPrimary)
                }
                .padding(.horizontal, 22).padding(.vertical, 14)
                .background(Color.pkPrimary, in: Capsule()).shadow(radius: 4)
            }
        case 3:
            VStack(spacing: 18) {
                HStack(spacing: 10) {
                    Image(systemName: "plus").foregroundStyle(Color.pkPrimary).frame(width: 32, height: 32).background(.white, in: Circle())
                    Text(L("map_report_here")).font(PKFont.titleSmall).foregroundStyle(Color.pkOnPrimary)
                }
                .padding(.leading, 6).padding(.trailing, 18).padding(.vertical, 6)
                .background(Color.pkPrimary, in: Capsule()).shadow(radius: 4)
                HStack(spacing: 10) {
                    Image(systemName: "camera.fill").foregroundStyle(Color.pkPrimary).frame(width: 44, height: 44).background(Color.pkTint, in: Circle())
                    Text(L("tip_photo_points")).font(PKFont.labelLarge).foregroundStyle(Color.pkPrimary)
                }
            }
        default:
            VStack(spacing: 22) {
                HStack(spacing: 0) {
                    HStack(spacing: 6) {
                        Image(systemName: "checkmark.circle.fill").foregroundStyle(Color.pkGreen)
                        Text(L("spot_i_took_it")).font(PKFont.labelLarge).foregroundStyle(Color.pkText)
                    }
                    .frame(maxWidth: .infinity)
                    Rectangle().fill(Color.pkOutline).frame(width: 1, height: 22)
                    HStack(spacing: 6) {
                        Image(systemName: "minus.circle.fill").foregroundStyle(Color.pkOrange)
                        Text(L("spot_flag_taken")).font(PKFont.labelLarge).foregroundStyle(Color.pkText)
                    }
                    .frame(maxWidth: .infinity)
                }
                .frame(height: 48).background(Color.pkSurface, in: Capsule())
                HStack(alignment: .bottom, spacing: 10) {
                    PinImage(color: "#188038", tag: "", frame: "#B0703C", scale: 1.3)
                    PinImage(color: "#188038", tag: "", frame: "#9AA7B8", scale: 1.3)
                    PinImage(color: "#188038", tag: "", frame: "#D4A017", scale: 1.3)
                    PinImage(color: "#7B3FE4", tag: "", scale: 1.3)
                }
            }
            .padding(.horizontal, 8)
        }
    }

    private func artPin(_ color: String, _ tag: String, _ caption: String) -> some View {
        VStack(spacing: 6) {
            PinImage(color: color, tag: tag, scale: 1.3)
            Text(caption).font(PKFont.labelMedium).foregroundStyle(Color.pkMuted).multilineTextAlignment(.center).frame(width: 96)
        }
        .frame(maxWidth: .infinity)
    }
}
