import SwiftUI

// MARK: Avatars

func avatarId(_ index: Int) -> String { String(format: "avatar_%02d", index + 1) }

/// Tel Aviv types (ids avatar_01..16) and the legends unlocked in the shop.
func avatarImage(_ id: String?) -> UIImage? {
    guard let id else { return nil }
    return bundleImage(id)
}

struct AvatarView: View {
    let name: String?
    var background: Color = .pkPrimary
    var size: CGFloat = 40
    var textColor: Color = .white
    var avatarId: String?

    var body: some View {
        if let image = avatarImage(avatarId) {
            Image(uiImage: image).resizable().scaledToFill().frame(width: size, height: size).clipShape(Circle())
        } else {
            Text(name?.trimmed.first.map { String($0).uppercased() } ?? "?")
                .font(size >= 56 ? PKFont.headlineMedium : PKFont.titleMedium)
                .foregroundStyle(textColor)
                .frame(width: size, height: size)
                .background(background, in: Circle())
        }
    }
}

/// A 4x4 grid of avatars; the chosen one is ringed in the brand colour.
struct AvatarGrid: View {
    let selected: String?
    let onPick: (String) -> Void
    private let columns = Array(repeating: GridItem(.flexible(), spacing: 10, alignment: .top), count: 4)

    var body: some View {
        LazyVGrid(columns: columns, spacing: 10) {
            ForEach(0..<16, id: \.self) { i in
                let id = avatarId(i)
                let chosen = id == selected
                Button { onPick(id) } label: {
                    VStack(spacing: 4) {
                        if let image = avatarImage(id) {
                            Image(uiImage: image).resizable().scaledToFill().aspectRatio(1, contentMode: .fit).clipShape(Circle())
                                .overlay(Circle().stroke(Color.pkPrimary, lineWidth: chosen ? 3 : 0))
                        }
                        Text(L("avatar_names#\(i)")).font(PKFont.labelSmall)
                            .foregroundStyle(chosen ? Color.pkPrimary : Color.pkMuted)
                            .multilineTextAlignment(.center).lineLimit(2, reservesSpace: true)
                    }
                }
            }
        }
    }
}

struct AvatarPickerSheet: View {
    let selected: String?
    let onPick: (String) -> Void
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(L("avatar_pick_title")).font(PKFont.titleLarge).foregroundStyle(Color.pkText)
            ScrollView {
                AvatarGrid(selected: selected) {
                    onPick($0)
                    dismiss()
                }
            }
            Button(L("driver_close")) { dismiss() }.font(PKFont.labelLarge).foregroundStyle(Color.pkPrimary).frame(maxWidth: .infinity)
        }
        .padding(20)
        .presentationDetents([.large])
        .presentationBackground(Color.pkSurface)
    }
}

// MARK: Levels

/// The reporter ladder, mirrored from the backend's levels.py. Each level has its own
/// colour so it reads the same everywhere: hero, ladder, league, spot card, pin.
enum ParkItLevel: String, CaseIterable {
    case newbie, bronze, silver, gold, legend

    static func of(_ key: String?) -> ParkItLevel { ParkItLevel(rawValue: key ?? "") ?? .newbie }

    var minPoints: Int {
        switch self {
        case .newbie: 0
        case .bronze: 100
        case .silver: 400
        case .gold: 1200
        case .legend: 3000
        }
    }

    var color: Color {
        switch self {
        case .newbie: Color(hex: 0x7C8794)
        case .bronze: Color(hex: 0xB0703C)
        case .silver: Color(hex: 0x7D8DA3)
        case .gold: Color(hex: 0xC99A0E)
        case .legend: Color(hex: 0x7B3FE4)
        }
    }

    var icon: String {
        switch self {
        case .newbie: "car.fill"
        case .bronze: "medal.fill"
        case .silver: "rosette"
        case .gold: "trophy.fill"
        case .legend: "diamond.fill"
        }
    }

    var name: String { L("level_\(rawValue)") }
    var perk: String { L("perk_\(rawValue)") }

    var pinFrame: String? {
        switch self {
        case .bronze: "#B0703C"
        case .silver: "#9AA7B8"
        case .gold: "#D4A017"
        default: nil
        }
    }
}

struct LevelEmblem: View {
    let level: ParkItLevel
    let size: CGFloat
    var body: some View {
        ZStack {
            Circle().fill(level.color.opacity(0.16))
            Circle().fill(level.color).padding(size * 0.12)
            Image(systemName: level.icon).font(.system(size: size * 0.36)).foregroundStyle(.white)
        }
        .frame(width: size, height: size)
    }
}

struct LevelPinPreview: View {
    let level: ParkItLevel
    let size: CGFloat
    var dimmed = false
    var body: some View {
        Image(uiImage: Pins.pin(color: level == .legend ? "#7B3FE4" : "#188038", tag: "", frame: level.pinFrame))
            .resizable().scaledToFit().frame(width: size, height: size).opacity(dimmed ? 0.4 : 1)
    }
}

/// All five levels: reached ones ticked, yours highlighted, the rest locked but visible.
struct LevelLadder: View {
    let points: Int
    let current: ParkItLevel

    var body: some View {
        SectionCard {
            VStack(spacing: 2) {
                ForEach(ParkItLevel.allCases, id: \.self) { level in
                    let reached = points >= level.minPoints
                    let isCurrent = level == current
                    HStack(spacing: 12) {
                        if reached {
                            LevelEmblem(level: level, size: 38)
                        } else {
                            Image(systemName: level.icon).font(.system(size: 16)).foregroundStyle(level.color.opacity(0.45))
                                .frame(width: 38, height: 38).background(Color.pkText.opacity(0.06), in: Circle())
                        }
                        VStack(alignment: .leading, spacing: 1) {
                            HStack(spacing: 8) {
                                Text(level.name).font(PKFont.titleSmall).foregroundStyle(reached ? Color.pkText : Color.pkMuted)
                                Text(L("level_threshold", level.minPoints)).font(PKFont.labelSmall).foregroundStyle(Color.pkMuted)
                                if isCurrent {
                                    Text(L("level_yours")).font(PKFont.labelSmall).foregroundStyle(.white)
                                        .padding(.horizontal, 8).padding(.vertical, 2).background(level.color, in: Capsule())
                                }
                            }
                            Text(level.perk).font(PKFont.bodySmall).foregroundStyle(Color.pkMuted)
                        }
                        Spacer(minLength: 4)
                        ZStack(alignment: .bottomTrailing) {
                            LevelPinPreview(level: level, size: 34, dimmed: !reached)
                            if !reached { Image(systemName: "lock.fill").font(.system(size: 11)).foregroundStyle(Color.pkMuted) }
                        }
                    }
                    .padding(.horizontal, 10).padding(.vertical, 8)
                    .background(isCurrent ? level.color.opacity(0.07) : .clear, in: RoundedRectangle(cornerRadius: 16))
                    .overlay(RoundedRectangle(cornerRadius: 16).stroke(isCurrent ? level.color : .clear, lineWidth: 1.5))
                    .padding(.horizontal, 8)
                }
                Text(L("level_earn_hint")).font(PKFont.labelSmall).foregroundStyle(Color.pkMuted).multilineTextAlignment(.center).padding(10)
            }
            .padding(.vertical, 6)
        }
    }
}

/// Shown once when the driver reaches a new level.
struct LevelUpSheet: View {
    let level: ParkItLevel
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        VStack(spacing: 8) {
            ZStack {
                Circle().fill(RadialGradient(colors: [level.color.opacity(0.35), .clear], center: .center, startRadius: 0, endRadius: 66)).frame(width: 132, height: 132)
                LevelEmblem(level: level, size: 96)
            }
            Text(L("level_up_title", level.name)).font(PKFont.headlineSmall).foregroundStyle(Color.pkText).multilineTextAlignment(.center)
            HStack(spacing: 10) {
                LevelPinPreview(level: level, size: 44)
                Text(level.perk).font(PKFont.bodyMedium).foregroundStyle(Color.pkMuted)
            }
            .padding(.top, 6)
            Button(L("level_up_ok")) { dismiss() }.buttonStyle(PillButtonStyle()).padding(.top, 12)
        }
        .padding(24)
        .fittedSheet()
    }
}

// MARK: Badges

enum Badge: CaseIterable {
    case first, reports10, reports50, reports100, helped10, helped50

    var goal: Int {
        switch self {
        case .first: 1
        case .reports10, .helped10: 10
        case .reports50, .helped50: 50
        case .reports100: 100
        }
    }
    var helped: Bool { self == .helped10 || self == .helped50 }
    var color: Color {
        switch self {
        case .first: Color(hex: 0x12A594)
        case .reports10: Color(hex: 0xB0703C)
        case .reports50: Color(hex: 0x7D8DA3)
        case .reports100: Color(hex: 0xC99A0E)
        case .helped10: Color(hex: 0xE5484D)
        case .helped50: Color(hex: 0x7B3FE4)
        }
    }
    var icon: String {
        switch self {
        case .first: "flag.fill"
        case .reports10: "parkingsign"
        case .reports50: "star.circle.fill"
        case .reports100: "trophy.fill"
        case .helped10: "hands.sparkles.fill"
        case .helped50: "bolt.fill"
        }
    }
    var name: String {
        switch self {
        case .first: L("badge_first")
        case .reports10: L("badge_reports_10")
        case .reports50: L("badge_reports_50")
        case .reports100: L("badge_reports_100")
        case .helped10: L("badge_helped_10")
        case .helped50: L("badge_helped_50")
        }
    }
    func have(_ p: ProfileOut) -> Int { helped ? p.successfulReports : (p.totalReports ?? 0) }
}

struct BadgesCard: View {
    let profile: ProfileOut
    private let columns = Array(repeating: GridItem(.flexible(), alignment: .top), count: 3)

    var body: some View {
        let earned = Badge.allCases.filter { $0.have(profile) >= $0.goal }.count
        SectionCard {
            VStack(alignment: .leading, spacing: 14) {
                Text(L("badges_progress", earned, Badge.allCases.count)).font(PKFont.labelLarge).foregroundStyle(Color.pkMuted).padding(.horizontal, 8)
                LazyVGrid(columns: columns, spacing: 14) {
                    ForEach(Badge.allCases, id: \.self) { badge in
                        let have = badge.have(profile)
                        let unlocked = have >= badge.goal
                        VStack(spacing: 6) {
                            BadgeArt(color: badge.color, icon: badge.icon, unlocked: unlocked, size: 72)
                            Text(badge.name).font(PKFont.labelMedium).foregroundStyle(unlocked ? Color.pkText : Color.pkMuted).multilineTextAlignment(.center).lineLimit(2)
                            if !unlocked {
                                Text(verbatim: "\(min(have, badge.goal))/\(badge.goal)").font(PKFont.labelSmall).foregroundStyle(Color.pkMuted)
                            }
                        }
                    }
                }
            }
            .padding(.vertical, 16).padding(.horizontal, 8)
        }
    }
}

/// The medal: ribbon tails, a hexagon with a gradient, a bevel, a shine and the icon.
private struct BadgeArt: View {
    let color: Color
    let icon: String
    let unlocked: Bool
    let size: CGFloat

    var body: some View {
        let base = unlocked ? color : Color(hex: 0xB8BEC8)
        ZStack(alignment: .topTrailing) {
            ZStack {
                Canvas { ctx, s in
                    let w = s.width, c = CGPoint(x: w / 2, y: w * 0.44), r = w * 0.40
                    for side in [CGFloat(-1), 1] {
                        var tail = Path()
                        tail.move(to: CGPoint(x: c.x + side * r * 0.15, y: c.y + r * 0.4))
                        tail.addLine(to: CGPoint(x: c.x + side * r * 0.75, y: c.y + r * 1.25))
                        tail.addLine(to: CGPoint(x: c.x + side * r * 0.5, y: c.y + r * 1.12))
                        tail.addLine(to: CGPoint(x: c.x + side * r * 0.35, y: c.y + r * 1.32))
                        tail.addLine(to: CGPoint(x: c.x - side * r * 0.15, y: c.y + r * 0.5))
                        tail.closeSubpath()
                        ctx.fill(tail, with: .color(base.opacity(0.7)))
                    }
                    func hexagon(_ radius: CGFloat) -> Path {
                        var p = Path()
                        for i in 0..<6 {
                            let a = (60 * CGFloat(i) - 90) * .pi / 180
                            let pt = CGPoint(x: c.x + radius * cos(a), y: c.y + radius * sin(a))
                            if i == 0 { p.move(to: pt) } else { p.addLine(to: pt) }
                        }
                        p.closeSubpath()
                        return p
                    }
                    ctx.fill(hexagon(r), with: .linearGradient(Gradient(colors: [base.opacity(0.75), base]), startPoint: CGPoint(x: c.x - r, y: c.y - r), endPoint: CGPoint(x: c.x + r, y: c.y + r)))
                    ctx.stroke(hexagon(r * 0.8), with: .color(.white.opacity(0.45)), lineWidth: w * 0.025)
                }
                Image(systemName: icon).font(.system(size: size * 0.3)).foregroundStyle(.white.opacity(unlocked ? 1 : 0.85)).padding(.bottom, size * 0.12)
            }
            if !unlocked {
                Image(systemName: "lock.fill").font(.system(size: size * 0.13)).foregroundStyle(.white)
                    .frame(width: size * 0.28, height: size * 0.28).background(Color(hex: 0x8A93A3), in: Circle())
                    .overlay(Circle().stroke(Color.pkSurface, lineWidth: 2))
            }
        }
        .frame(width: size, height: size)
    }
}

// MARK: League

/// Weekly league: a podium of the top three, slim rows for the rest, and always your own place.
struct LeagueSection: View {
    let entries: [LeaderboardEntry]
    let me: ProfileOut
    let myRank: Int?

    var body: some View {
        VStack(spacing: 10) {
            if entries.isEmpty {
                SectionCard {
                    Text(L("profile_leaderboard_empty")).font(PKFont.bodyMedium).foregroundStyle(Color.pkMuted).padding(20)
                }
            } else {
                SectionCard {
                    HStack(alignment: .bottom, spacing: 0) {
                        podium(1, medal: Color(hex: 0x9AA5B4), first: false)
                        podium(0, medal: Color(hex: 0xD4A017), first: true)
                        podium(2, medal: Color(hex: 0xB0703C), first: false)
                    }
                    .padding(.horizontal, 8).padding(.vertical, 16)
                }
                if entries.count > 3 {
                    SectionCard {
                        VStack(spacing: 0) {
                            ForEach(entries.dropFirst(3)) { e in
                                row(rank: e.rank, name: e.displayName, level: ParkItLevel.of(e.level), points: e.weeklyPoints, isMe: e.userId == me.userId, avatar: e.avatar)
                            }
                        }
                    }
                }
            }
            if !entries.contains(where: { $0.userId == me.userId }) {
                let level = ParkItLevel.of(me.level)
                Group {
                    if let myRank {
                        row(rank: myRank, name: me.displayName, level: level, points: me.weeklyPoints, isMe: true, avatar: me.avatar, highlight: false)
                    } else {
                        Text(L("league_not_ranked")).font(PKFont.bodyMedium).foregroundStyle(Color.pkText).padding(16)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(level.color.opacity(0.08), in: RoundedRectangle(cornerRadius: 20))
                .overlay(RoundedRectangle(cornerRadius: 20).stroke(level.color.opacity(0.5), lineWidth: 1))
                .padding(.horizontal, 16)
            }
        }
    }

    @ViewBuilder private func podium(_ index: Int, medal: Color, first: Bool) -> some View {
        Group {
            if entries.indices.contains(index) {
                let e = entries[index]
                let level = ParkItLevel.of(e.level)
                let ring: CGFloat = first ? 64 : 52
                VStack(spacing: 0) {
                    ZStack(alignment: .bottomTrailing) {
                        AvatarView(name: e.displayName, background: level.color, size: ring - 8, avatarId: e.avatar)
                            .frame(width: ring, height: ring)
                            .overlay(Circle().stroke(level.color, lineWidth: 2.5))
                        Text(verbatim: "\(e.rank)").font(PKFont.labelSmall).foregroundStyle(.white)
                            .frame(width: 20, height: 20).background(medal, in: Circle())
                            .overlay(Circle().stroke(Color.pkSurface, lineWidth: 2))
                    }
                    Text(e.userId == me.userId ? L("profile_you") : isolate(e.displayName)).font(PKFont.labelLarge).foregroundStyle(Color.pkText).lineLimit(1).padding(.top, 8)
                    Text(L("profile_points_suffix", e.weeklyPoints)).font(first ? PKFont.titleMedium : PKFont.titleSmall).foregroundStyle(first ? medal : Color.pkText)
                }
                .padding(.horizontal, 4)
            }
        }
        .frame(maxWidth: .infinity)
    }

    private func row(rank: Int, name: String, level: ParkItLevel, points: Int, isMe: Bool, avatar: String?, highlight: Bool = true) -> some View {
        HStack(spacing: 0) {
            Text(verbatim: "\(rank)").font(PKFont.titleSmall).foregroundStyle(Color.pkMuted).frame(width: 28)
            AvatarView(name: name, background: level.color, size: 34, avatarId: avatar)
                .frame(width: 40, height: 40).overlay(Circle().stroke(level.color, lineWidth: 2)).padding(.leading, 8)
            Text(isMe ? "\(isolate(name)) · \(L("profile_you"))" : isolate(name)).font(PKFont.bodyLarge).foregroundStyle(Color.pkText).lineLimit(1).padding(.leading, 12)
            Image(systemName: level.icon).font(.system(size: 13)).foregroundStyle(level.color).padding(.leading, 6).accessibilityLabel(level.name)
            Spacer(minLength: 8)
            Text(L("profile_points_suffix", points)).font(PKFont.titleSmall).foregroundStyle(Color.pkText)
        }
        .padding(.horizontal, 16).padding(.vertical, 10)
        .background(isMe && highlight ? level.color.opacity(0.08) : .clear)
    }
}
