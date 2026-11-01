import SwiftUI

private let headerBottom = Color(hex: 0x1B3556)
private let onHeader = Color(hex: 0xC9D6E8)

/// The account screen: a quiet header, three big doors, progress, then a short list.
struct ProfileScreen: View {
    let onBack: () -> Void
    let open: (Route) -> Void
    @EnvironmentObject private var session: Session
    @State private var profile: ProfileOut?
    @State private var leaderboard: [LeaderboardEntry] = []
    @State private var cityBoard: [LeaderboardEntry] = []
    @State private var cityScope = Session.shared.driver.preferences.homeCity != nil
    @State private var error: String?
    @State private var page: String?
    @State private var pickAvatar = false
    @State private var pickPaint = false
    @State private var showAllReports = false

    private var homeCity: String? { session.driver.preferences.homeCity }

    var body: some View {
        ScrollView {
            VStack(spacing: 0) {
                if profile == nil || page == nil {
                    header
                } else {
                    ScreenTopBar(title: L(pageTitleKey)) { page = nil }
                }
                if let error { Text(error).font(PKFont.bodyMedium).foregroundStyle(Color.pkError).padding(20) }
                if let p = profile {
                    switch page {
                    case nil: overview(p)
                    case "reports": reports(p)
                    case "badges": BadgesCard(profile: p)
                    case "levels": LevelLadder(points: p.points, current: ParkItLevel.of(p.level))
                    default: league(p)
                    }
                } else if error == nil {
                    ProgressView().padding(48)
                }
                Spacer(minLength: 24)
            }
        }
        .ignoresSafeArea(edges: page == nil ? .top : [])
        .background(Color.pkBackground.ignoresSafeArea())
        .task { await load() }
        .task(id: homeCity) {
            if Features.league, let city = homeCity { cityBoard = (try? await API.shared.leaderboard(city: city)) ?? [] }
        }
        .sheet(isPresented: $pickAvatar) {
            AvatarPickerSheet(selected: profile?.avatar) { id in
                profile?.avatar = id
                Task { try? await API.shared.setAvatar(id) }
            }
            .parkItEnvironment(session)
        }
        .sheet(isPresented: $pickPaint) {
            paintPicker.parkItEnvironment(session)
        }
    }

    private func load() async {
        do {
            let p = try await API.shared.myProfile()
            profile = p
            session.syncDriver(p.preferences ?? Preferences())
            if Features.league { leaderboard = try await API.shared.leaderboard() }
        } catch {
            self.error = friendly(error)
        }
    }

    private var pageTitleKey: String {
        switch page {
        case "reports": "profile_tab_reports"
        case "league": "profile_tab_league"
        case "badges": "badges_title"
        default: "levels_title"
        }
    }

    private var paint: Color { myCarPaint(registry: profile?.preferences?.carPaint) }

    /// A dark band with your avatar, name, level and points, then your car on the road.
    private var header: some View {
        let car = profile?.preferences
        return VStack(alignment: .leading, spacing: 0) {
            HStack {
                Button(action: onBack) {
                    Image(systemName: "chevron.backward").font(.system(size: 18, weight: .semibold)).foregroundStyle(.white).frame(width: 48, height: 48).contentShape(Rectangle())
                }
                .accessibilityLabel(L("profile_back_cd"))
                Spacer()
                if profile != nil {
                    Button { open(.rewards(tab: 3)) } label: {
                        Image(systemName: "pencil").font(.system(size: 13)).foregroundStyle(.white)
                            .frame(width: 32, height: 32).background(.white.opacity(0.18), in: Circle())
                    }
                    .accessibilityLabel(L("profile_change_backdrop"))
                }
                Button { open(.settings) } label: {
                    Image(systemName: "gearshape.fill").font(.system(size: 18)).foregroundStyle(.white).frame(width: 48, height: 48).contentShape(Rectangle())
                }
                .accessibilityLabel(L("profile_settings_cd"))
            }
            .padding(.horizontal, 4).frame(height: 56)
            if let p = profile {
                let level = ParkItLevel.of(p.level)
                HStack(spacing: 14) {
                    Button { pickAvatar = true } label: {
                        ZStack {
                            Circle().stroke(.white.opacity(0.22), lineWidth: 3)
                            Circle().trim(from: 0, to: p.levelProgress).stroke(level.color, style: StrokeStyle(lineWidth: 3, lineCap: .round)).rotationEffect(.degrees(-90))
                            AvatarView(name: p.displayName, background: .pkPrimary, size: 62, textColor: .pkOnPrimary, avatarId: p.avatar)
                        }
                        .frame(width: 69, height: 69).frame(width: 72, height: 72)
                    }
                    VStack(alignment: .leading, spacing: 4) {
                        Text(isolate(p.displayName)).font(PKFont.headlineSmall).foregroundStyle(.white).lineLimit(1)
                        HStack(spacing: 8) {
                            HStack(spacing: 5) {
                                Circle().fill(level.color).frame(width: 8, height: 8)
                                Text(level.name).font(PKFont.labelMedium).foregroundStyle(.white)
                            }
                            .padding(.horizontal, 8).padding(.vertical, 2).background(.white.opacity(0.14), in: Capsule())
                            Text(L("profile_points_count", p.points)).font(PKFont.labelMedium).foregroundStyle(onHeader)
                        }
                    }
                }
                .padding(.horizontal, 20)
                Text(car?.carLabel.map(isolate) ?? L("car_art_add")).font(PKFont.labelLarge).foregroundStyle(onHeader).lineLimit(1)
                    .padding(.horizontal, 20).padding(.top, 12)
                ZStack(alignment: .top) {
                    CarOnRoad(size: car?.vehicleSize, paint: paint, special: session.carSpecial)
                        .contentShape(Rectangle()).onTapGesture(perform: carTapped)
                    Button(action: carTapped) {
                        Image(systemName: car?.vehicleSize == nil ? "plus" : "pencil").font(.system(size: 13, weight: .semibold)).foregroundStyle(headerBottom)
                            .frame(width: 30, height: 30).background(.white, in: Circle())
                    }
                    .accessibilityLabel(L(car?.vehicleSize == nil ? "car_art_add" : "car_art_edit"))
                    .offset(x: 74, y: 2)
                }
                .environment(\.layoutDirection, .leftToRight)
            } else {
                Spacer().frame(height: 96)
            }
        }
        .padding(.top, safeTop)
        .background(alignment: .top) { ProfileBackdrop(key: session.profileBackground) }
    }

    private var safeTop: CGFloat {
        (UIApplication.shared.connectedScenes.first as? UIWindowScene)?.windows.first?.safeAreaInsets.top ?? 44
    }

    private func carTapped() {
        if profile?.preferences?.vehicleSize == nil { open(.driver) } else { pickPaint = true }
    }

    private var paintPicker: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text(L("car_art_pick_paint")).font(PKFont.titleLarge).foregroundStyle(Color.pkText)
            LazyVGrid(columns: Array(repeating: GridItem(.fixed(44)), count: 6), alignment: .leading, spacing: 12) {
                ForEach(carPaints.indices, id: \.self) { i in
                    Button {
                        session.carPaintIndex = i
                        session.objectWillChange.send()
                        pickPaint = false
                    } label: {
                        Circle().fill(carPaints[i]).frame(width: 40, height: 40)
                            .overlay(Circle().stroke(i == session.carPaintIndex ? Color.pkPrimary : Color.pkOutline, lineWidth: i == session.carPaintIndex ? 3 : 1))
                    }
                }
            }
            Button(L("car_art_edit")) {
                pickPaint = false
                open(.driver)
            }
            .font(PKFont.labelLarge).foregroundStyle(Color.pkPrimary)
        }
        .padding(.horizontal, 24).padding(.top, 28).padding(.bottom, 20)
        .fittedSheet()
    }

    @ViewBuilder private func overview(_ p: ProfileOut) -> some View {
        // Three big doors: rewards, my reports, league, each with a live hint.
        HStack(alignment: .top, spacing: 10) {
            ShortcutTile(icon: "gift.fill", tint: Color(hex: 0xB06000), bg: Color(hex: 0xFEF7E0), title: L("rewards_title"), hint: L("profile_rewards_hint", p.pointsBalance ?? 0)) { open(.rewards(tab: 1)) }
            ShortcutTile(icon: "mappin.circle.fill", tint: .pkBlue, bg: Color(hex: 0xE8F0FE), title: L("profile_tab_reports"), hint: LP("profile_reports_count", p.totalReports ?? 0)) { page = "reports" }
            if Features.league { ShortcutTile(icon: "trophy.fill", tint: .pkGreen, bg: Color(hex: 0xE6F4EA), title: L("profile_tab_league"), hint: homeCity ?? L("league_all_israel")) { page = "league" } }
        }
        .padding(.horizontal, 16).padding(.top, 16)
        nextLevel(p)
        HStack(spacing: 0) {
            stat("\(p.points)", L("hero_points"))
            Rectangle().fill(Color.pkOutline.opacity(0.5)).frame(width: 1, height: 32)
            stat("\(p.successfulReports)", L("hero_helped"))
            Rectangle().fill(Color.pkOutline.opacity(0.5)).frame(width: 1, height: 32)
            stat("\(p.weeklyPoints)", L("hero_rank"))
        }
        .padding(.vertical, 12)
        .background(Color.pkSurface, in: RoundedRectangle(cornerRadius: 18))
        .padding(.horizontal, 16).padding(.top, 12)
        let earned = Badge.allCases.filter { $0.have(p) >= $0.goal }.count
        VStack(spacing: 0) {
            if Features.badges {
                linkRow("rosette", L("badges_title"), L("badges_progress_short", earned, Badge.allCases.count)) { page = "badges" }
                Divider().padding(.leading, 64)
            }
            linkRow("star.fill", L("profile_levels_link"), nil) { page = "levels" }
            Divider().padding(.leading, 64)
            linkRow("person.badge.plus", L("map_empty_invite"), nil, action: invite)
        }
        .background(Color.pkSurface, in: RoundedRectangle(cornerRadius: 18))
        .padding(.horizontal, 16).padding(.top, 12)
    }

    private func stat(_ value: String, _ label: String) -> some View {
        VStack {
            Text(verbatim: value).font(PKFont.titleLarge).foregroundStyle(Color.pkText)
            Text(label).font(PKFont.labelSmall).foregroundStyle(Color.pkMuted).lineLimit(1)
        }
        .frame(maxWidth: .infinity)
    }

    private func linkRow(_ icon: String, _ title: String, _ end: String?, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 14) {
                Image(systemName: icon).font(.system(size: 15)).foregroundStyle(Color.pkPrimary)
                    .frame(width: 34, height: 34).background(Color.pkTint, in: RoundedRectangle(cornerRadius: 10))
                Text(title).font(PKFont.bodyLarge).foregroundStyle(Color.pkText)
                Spacer()
                if let end { Text(end).font(PKFont.bodySmall).foregroundStyle(Color.pkMuted) }
                Image(systemName: "chevron.forward").font(.system(size: 12, weight: .semibold)).foregroundStyle(Color.pkMuted)
            }
            .padding(.horizontal, 16).padding(.vertical, 14)
            .contentShape(Rectangle())
        }
    }

    /// How far to the next level, as one line and a bar.
    private func nextLevel(_ p: ProfileOut) -> some View {
        let level = ParkItLevel.of(p.level)
        return VStack(alignment: .leading, spacing: 10) {
            if let nextKey = p.nextLevel, let target = p.nextLevelPoints {
                let next = ParkItLevel.of(nextKey)
                HStack(alignment: .bottom) {
                    Text(L("level_to_next", target - p.points, next.name)).font(PKFont.titleSmall).foregroundStyle(Color.pkText)
                    Spacer()
                    Text(verbatim: "\(p.points.formatted()) / \(target.formatted())").font(PKFont.labelMedium).foregroundStyle(Color.pkMuted).environment(\.layoutDirection, .leftToRight)
                }
                GeometryReader { g in
                    ZStack(alignment: .leading) {
                        Capsule().fill(Color.pkOutline.opacity(0.25))
                        Capsule().fill(next.color).frame(width: g.size.width * p.levelProgress)
                    }
                }
                .frame(height: 6)
            } else {
                HStack(spacing: 12) {
                    LevelEmblem(level: level, size: 32)
                    Text(L("level_max")).font(PKFont.titleSmall).foregroundStyle(Color.pkText)
                }
            }
        }
        .padding(16)
        .background(Color.pkSurface, in: RoundedRectangle(cornerRadius: 18))
        .padding(.horizontal, 16).padding(.top, 12)
    }

    private func reports(_ p: ProfileOut) -> some View {
        SectionCard {
            VStack(spacing: 0) {
                if p.activity.isEmpty {
                    Text(L("profile_my_reports_empty")).font(PKFont.bodyMedium).foregroundStyle(Color.pkMuted).padding(20).frame(maxWidth: .infinity, alignment: .leading)
                } else {
                    ForEach(showAllReports ? p.activity : Array(p.activity.prefix(4))) { item in
                        ReportRow(item: item)
                        Divider().padding(.leading, 70)
                    }
                    if p.activity.count > 4 {
                        Button(L(showAllReports ? "profile_show_less" : "profile_see_all")) { showAllReports.toggle() }
                            .font(PKFont.labelLarge).foregroundStyle(Color.pkPrimary).frame(maxWidth: .infinity).padding(16)
                    }
                }
            }
        }
    }

    @ViewBuilder private func league(_ p: ProfileOut) -> some View {
        Text(L("league_subtitle")).font(PKFont.bodySmall).foregroundStyle(Color.pkMuted).frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 20).padding(.top, 4).padding(.bottom, 12)
        HStack(spacing: 0) {
            scopeButton(homeCity ?? L("league_my_city"), on: cityScope) { cityScope = true }
            scopeButton(L("league_all_israel"), on: !cityScope) { cityScope = false }
        }
        .clipShape(Capsule()).overlay(Capsule().stroke(Color.pkMuted.opacity(0.5), lineWidth: 1))
        .padding(.horizontal, 16).padding(.bottom, 12)
        if cityScope, homeCity == nil {
            // No preferred city yet: the city league needs one, one tap to set it.
            Button { open(.driver) } label: {
                SectionCard {
                    HStack(spacing: 14) {
                        Image(systemName: "building.2.fill").foregroundStyle(Color.pkPrimary).frame(width: 40, height: 40).background(Color.pkTint, in: RoundedRectangle(cornerRadius: 12))
                        VStack(alignment: .leading) {
                            Text(L("league_pick_city_title")).font(PKFont.titleSmall).foregroundStyle(Color.pkText)
                            Text(L("league_pick_city_body")).font(PKFont.bodySmall).foregroundStyle(Color.pkMuted).multilineTextAlignment(.leading)
                        }
                        Spacer()
                        Image(systemName: "chevron.forward").foregroundStyle(Color.pkMuted)
                    }
                    .padding(16)
                }
            }
        } else if cityScope {
            LeagueSection(entries: cityBoard, me: p, myRank: p.weeklyCityRank)
        } else {
            LeagueSection(entries: leaderboard, me: p, myRank: p.weeklyRank)
        }
    }

    private func scopeButton(_ title: String, on: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(title).font(PKFont.labelLarge).lineLimit(1).foregroundStyle(on ? Color.pkPrimary : Color.pkText)
                .frame(maxWidth: .infinity).frame(height: 40)
                .background(on ? Color.pkTint : .clear)
        }
    }
}

private struct ShortcutTile: View {
    let icon: String
    let tint: Color
    let bg: Color
    let title: String
    let hint: String
    let action: () -> Void
    var body: some View {
        Button(action: action) {
            VStack(spacing: 0) {
                Image(systemName: icon).font(.system(size: 18)).foregroundStyle(tint).frame(width: 42, height: 42).background(bg, in: Circle())
                Text(title).font(PKFont.labelLarge).foregroundStyle(Color.pkText).lineLimit(1).padding(.top, 8)
                Text(hint).font(PKFont.labelSmall).foregroundStyle(Color.pkMuted).lineLimit(1)
            }
            .padding(.vertical, 14).padding(.horizontal, 4)
            .frame(maxWidth: .infinity)
            .background(Color.pkSurface, in: RoundedRectangle(cornerRadius: 18))
        }
    }
}

private struct ReportRow: View {
    let item: ActivityItem
    var body: some View {
        let (key, color): (String, Color) = {
            if item.status == "active" { return ("report_status_active", .pkGreen) }
            if item.status == "claimed" { return ("report_status_claimed", .pkOrange) }
            if item.removedReason == "taken_confirmed" { return ("report_status_confirmed_taken", .pkPrimary) }
            if item.removedReason == "flagged_false" { return ("report_status_flagged_false", .pkError) }
            return ("report_status_expired", .pkMuted)
        }()
        let disabled = item.spotType == "disabled"
        let type = L(disabled ? "spot_type_disabled" : "spot_type_regular")
        HStack(spacing: 14) {
            Image(systemName: disabled ? "figure.roll" : "parkingsign").foregroundStyle(Color.pkPrimary).frame(width: 40, height: 40).background(Color.pkTint, in: RoundedRectangle(cornerRadius: 12))
            VStack(alignment: .leading) {
                Text(item.address ?? type).font(PKFont.titleSmall).foregroundStyle(Color.pkText).lineLimit(1)
                Text((item.address != nil ? "\(type) · " : "") + relativeLong(item.reportedAt)).font(PKFont.bodySmall).foregroundStyle(Color.pkMuted)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            Text(L(key)).font(PKFont.labelMedium).foregroundStyle(color).padding(.horizontal, 10).padding(.vertical, 4).background(color.opacity(0.1), in: Capsule())
        }
        .padding(.horizontal, 16).padding(.vertical, 12)
    }
}

// MARK: Rewards shop

/// Points buy car finishes and benefits from partners. Buying never lowers the level.
struct RewardsScreen: View {
    let initialTab: Int
    let onBack: () -> Void
    @EnvironmentObject private var session: Session
    @State private var shop: ShopOut?
    @State private var error: String?
    @State private var confirm: RewardOut?
    @State private var newCode: String?
    @State private var note: String?
    @State private var avatar: String?
    @State private var tab = 1

    private var car: Preferences { session.driver.preferences }

    var body: some View {
        VStack(spacing: 0) {
            ScreenTopBar(title: L("rewards_title"), onBack: onBack)
            if let s = shop {
                VStack(alignment: .leading, spacing: 0) {
                    HStack(alignment: .lastTextBaseline, spacing: 8) {
                        Text(verbatim: "\(s.balance)").font(PKFont.displaySmall).foregroundStyle(Color.pkText)
                        Text(L("rewards_balance_line")).font(PKFont.titleMedium).foregroundStyle(Color.pkMuted)
                    }
                    Text(L("rewards_level_safe")).font(PKFont.bodySmall).foregroundStyle(Color.pkMuted)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, 20)
                tabs
                if let note { Text(note).font(PKFont.bodySmall).foregroundStyle(Color.pkAmber).padding(.horizontal, 20).padding(.bottom, 6) }
                if let error { Text(error).font(PKFont.bodySmall).foregroundStyle(Color.pkError).padding(.horizontal, 20).padding(.bottom, 6) }
                ScrollView {
                    if tab == 0 { partners(s) } else { grid(s) }
                }
            } else {
                Group {
                    if let error { Text(error).foregroundStyle(Color.pkError) } else { ProgressView() }
                }
                .font(PKFont.bodyMedium).padding(48)
                Spacer()
            }
        }
        .background(Color.pkBackground.ignoresSafeArea())
        .onAppear { tab = initialTab == 0 && !Features.partners ? 1 : initialTab }
        .task { await load() }
        .alert(confirm?.title ?? "", isPresented: Binding(get: { confirm != nil }, set: { if !$0 { confirm = nil } })) {
            Button(L("rewards_buy")) { if let r = confirm { buy(r) } }
            Button(L("settings_cancel"), role: .cancel) {}
        } message: {
            Text(L("rewards_confirm_body", confirm?.cost ?? 0))
        }
        .alert(L("rewards_code_title"), isPresented: Binding(get: { newCode != nil }, set: { if !$0 { newCode = nil } })) {
            Button(L("level_up_ok")) {}
        } message: {
            Text(verbatim: "\(newCode ?? "")\n\n\(L("rewards_code_body"))")
        }
    }

    private func load() async {
        do {
            let s = try await API.shared.rewards()
            shop = s
            // Only wear what this account owns.
            let owned = s.rewards.filter(\.isOwned).compactMap(\.key)
            if let special = session.carSpecial, !owned.contains(special) { session.carSpecial = nil }
            avatar = (try? await API.shared.myProfile())?.avatar
        } catch {
            self.error = friendly(error, aboutSpot: false)
        }
    }

    private var tabs: some View {
        HStack(spacing: 0) {
            ForEach(Array(["rewards_tab_partners", "rewards_tab_legends", "rewards_tab_car", "rewards_tab_backdrops"].enumerated()), id: \.offset) { i, key in
                // The index is the tab's id, so hiding one doesn't renumber the rest.
                if i != 0 || Features.partners {
                    let on = i == tab
                    Button { tab = i } label: {
                        Text(L(key)).font(PKFont.labelLarge).foregroundStyle(on ? Color.pkText : Color.pkMuted).lineLimit(1).minimumScaleFactor(0.8)
                            .frame(maxWidth: .infinity).padding(.vertical, 8)
                            .background(on ? Color.pkSurface : .clear, in: Capsule())
                    }
                }
            }
        }
        .padding(3).background(Color.pkSurfaceVariant, in: Capsule())
        .padding(.horizontal, 16).padding(.vertical, 14)
    }

    private func grid(_ s: ShopOut) -> some View {
        let items: [RewardOut] = switch tab {
        case 1: s.rewards.filter { $0.key?.hasPrefix("avatar_legend_") == true }
        case 2: s.rewards.filter { $0.kind == "cosmetic" && $0.key?.hasPrefix("paint_") == true }
        default: s.rewards.filter { $0.key?.hasPrefix("bg_") == true }
        }
        return LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 10, alignment: .top), count: tab == 1 ? 3 : 2), spacing: 10) {
            ForEach(items) { reward in
                Button { tap(reward, balance: s.balance) } label: {
                    VStack(spacing: 8) {
                        picture(reward)
                        Text(reward.title).font(PKFont.labelLarge).foregroundStyle(Color.pkText).multilineTextAlignment(.center).lineLimit(2, reservesSpace: tab == 1)
                        PriceTag(reward: reward, balance: s.balance, inUse: inUse(reward))
                    }
                    .padding(10).frame(maxWidth: .infinity)
                    .background(Color.pkSurface, in: RoundedRectangle(cornerRadius: 16))
                    .overlay(RoundedRectangle(cornerRadius: 16).stroke(inUse(reward) ? Color.pkPrimary : Color.pkOutline.opacity(0.5), lineWidth: inUse(reward) ? 2 : 1))
                }
            }
        }
        .padding(.horizontal, 16).padding(.top, 4).padding(.bottom, 32)
    }

    @ViewBuilder private func picture(_ reward: RewardOut) -> some View {
        switch tab {
        case 1:
            if let image = avatarImage(reward.key) {
                Image(uiImage: image).resizable().scaledToFill().frame(width: 64, height: 64).clipShape(Circle())
            }
        case 2:
            CarArt(size: car.vehicleSize ?? "regular", paint: myCarPaint(registry: car.carPaint), special: reward.key)
        default:
            ProfileBackdrop(key: reward.key).frame(height: 72).clipShape(RoundedRectangle(cornerRadius: 12))
        }
    }

    private func inUse(_ r: RewardOut) -> Bool {
        guard let key = r.key else { return false }
        if key.hasPrefix("avatar_legend_") { return avatar == key }
        if key.hasPrefix("bg_") { return session.profileBackground == key }
        return session.carSpecial == key
    }

    @ViewBuilder private func partners(_ s: ShopOut) -> some View {
        let partners = s.rewards.filter { $0.kind == "partner" }
        VStack(spacing: 8) {
            if partners.isEmpty {
                SectionCard {
                    HStack(spacing: 14) {
                        Image(systemName: "gift.fill").foregroundStyle(Color(hex: 0xB06000)).frame(width: 40, height: 40).background(Color(hex: 0xFEF7E0), in: Circle())
                        VStack(alignment: .leading) {
                            Text(L("rewards_partners_soon_title")).font(PKFont.titleSmall).foregroundStyle(Color.pkText)
                            Text(L("rewards_partners_soon_body")).font(PKFont.bodySmall).foregroundStyle(Color.pkMuted)
                        }
                    }
                    .padding(16)
                }
            }
            ForEach(partners) { reward in
                Button { tap(reward, balance: s.balance) } label: {
                    SectionCard {
                        HStack(spacing: 14) {
                            Image(systemName: "gift.fill").font(.system(size: 26)).foregroundStyle(Color.pkPrimary)
                            VStack(alignment: .leading) {
                                Text(reward.title).font(PKFont.titleSmall).foregroundStyle(Color.pkText)
                                if let partner = reward.partner { Text(partner).font(PKFont.bodySmall).foregroundStyle(Color.pkPrimary) }
                                if let details = reward.details { Text(details).font(PKFont.bodySmall).foregroundStyle(Color.pkMuted).multilineTextAlignment(.leading) }
                            }
                            .frame(maxWidth: .infinity, alignment: .leading)
                            PriceTag(reward: reward, balance: s.balance, inUse: false)
                        }
                        .padding(16)
                    }
                }
            }
            if !s.redemptions.isEmpty {
                SectionTitle(L("rewards_my_codes"))
                ForEach(s.redemptions) { r in
                    SectionCard {
                        HStack {
                            VStack(alignment: .leading) {
                                Text(r.title).font(PKFont.titleSmall).foregroundStyle(Color.pkText)
                                if let partner = r.partner { Text(partner).font(PKFont.bodySmall).foregroundStyle(Color.pkMuted) }
                            }
                            Spacer()
                            Text(verbatim: r.code).font(PKFont.titleLarge).foregroundStyle(Color.pkPrimary)
                        }
                        .padding(16)
                    }
                }
            }
        }
        .padding(.bottom, 32)
    }

    private func tap(_ reward: RewardOut, balance: Int) {
        note = nil
        if reward.isOwned {
            wear(reward)
        } else if balance >= reward.cost {
            confirm = reward
        } else {
            note = L("rewards_missing", reward.cost - balance)
        }
    }

    private func wear(_ reward: RewardOut) {
        guard let key = reward.key else { return }
        if key.hasPrefix("avatar_legend_") {
            Task {
                if (try? await API.shared.setAvatar(key)) != nil { avatar = key }
            }
        } else if key.hasPrefix("bg_") {
            session.profileBackground = session.profileBackground == key ? nil : key
            session.objectWillChange.send()
        } else {
            session.carSpecial = session.carSpecial == key ? nil : key
            session.objectWillChange.send()
        }
    }

    private func buy(_ reward: RewardOut) {
        Task {
            do {
                let updated = try await API.shared.redeemReward(reward.id)
                shop = updated
                if reward.kind == "cosmetic" {
                    // Put it on right away: that's the fun part.
                    if let key = reward.key, key.hasPrefix("avatar_legend_") {
                        try? await API.shared.setAvatar(key)
                        avatar = key
                    } else if let key = reward.key, key.hasPrefix("bg_") {
                        session.profileBackground = key
                    } else {
                        session.carSpecial = reward.key
                    }
                    session.objectWillChange.send()
                } else {
                    newCode = updated.redemptions.first { $0.rewardId == reward.id }?.code
                }
            } catch {
                self.error = friendly(error)
            }
        }
    }
}

private struct PriceTag: View {
    let reward: RewardOut
    let balance: Int
    let inUse: Bool
    var body: some View {
        let affordable = balance >= reward.cost
        let bg: Color = reward.isOwned ? .pkTint : affordable ? Color(hex: 0xFEF7E0) : .pkSurfaceVariant
        let fg: Color = reward.isOwned ? .pkPrimary : affordable ? Color(hex: 0x8A5300) : .pkMuted
        HStack(spacing: 4) {
            Image(systemName: reward.isOwned ? "checkmark.circle.fill" : affordable ? "star.fill" : "lock.fill").font(.system(size: 11))
            Text(inUse ? L("rewards_in_use") : reward.isOwned ? L("rewards_owned") : "\(reward.cost)").font(PKFont.heebo(12, 700)).lineLimit(1)
        }
        .foregroundStyle(fg).padding(.horizontal, 10).padding(.vertical, 3).background(bg, in: Capsule())
    }
}

// MARK: My car & preferences

/// "My car & preferences". Shown once after login (skippable, and skipped by itself if the
/// account already answered) and from the personal area. Everything is optional.
struct DriverProfileScreen: View {
    let onboarding: Bool
    let onDone: () -> Void
    @EnvironmentObject private var session: Session
    @State private var loading = true
    @State private var vehicleSize: String?
    @State private var carLabel: String?
    @State private var carPaint: String?
    @State private var disabledPermit = false
    @State private var city: String?
    @State private var cityCentre: (Double, Double)?
    @State private var cities: [CityOut] = []
    @State private var plate = ""
    @State private var lookingUp = false
    @State private var lookupError: String?
    @State private var saving = false
    @State private var error: String?
    @State private var pickCity = false

    var body: some View {
        VStack(spacing: 0) {
            if onboarding {
                HStack {
                    Spacer()
                    Button(L("driver_skip"), action: finish).font(PKFont.labelLarge).foregroundStyle(Color.pkPrimary).padding(.horizontal, 16).frame(height: 48)
                }
            } else {
                ScreenTopBar(title: L("driver_title"), onBack: onDone)
            }
            if loading {
                Spacer()
                ProgressView()
                Spacer()
            } else {
                form
                Button(action: save) {
                    if saving { ProgressView().tint(Color.pkOnPrimary) } else { Text(L(onboarding ? "driver_save_and_continue" : "driver_save")) }
                }
                .buttonStyle(PillButtonStyle())
                .disabled(saving)
                .padding(.horizontal, 16).padding(.vertical, 12)
            }
        }
        .background(Color.pkBackground.ignoresSafeArea())
        .task { await load() }
        .sheet(isPresented: $pickCity) {
            CityPicker(cities: cities, selected: city) { picked in
                city = picked?.name
                cityCentre = picked.map { ($0.lat, $0.lng) }
                pickCity = false
            }
            .parkItEnvironment(session)
        }
    }

    private var form: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                if onboarding {
                    Text(L("driver_onboarding_title")).font(PKFont.headlineSmall).foregroundStyle(Color.pkText).padding(.horizontal, 20)
                    Text(L("driver_onboarding_subtitle")).font(PKFont.bodyMedium).foregroundStyle(Color.pkMuted).padding(.horizontal, 20).padding(.top, 6)
                }
                SectionTitle(L("driver_section_car"))
                SectionCard { carCard }
                SectionTitle(L("driver_section_permit"))
                SectionCard {
                    VStack(alignment: .leading, spacing: 10) {
                        Toggle(isOn: $disabledPermit) {
                            HStack(spacing: 12) {
                                iconBadge("figure.roll")
                                Text(L("driver_disabled_permit")).font(PKFont.titleSmall).foregroundStyle(Color.pkText)
                            }
                        }
                        .tint(Color.pkPrimary)
                        Text(L("driver_disabled_permit_note")).font(PKFont.bodySmall).foregroundStyle(Color.pkMuted)
                    }
                    .padding(16)
                }
                SectionTitle(L("driver_section_area"))
                SectionCard {
                    Button { pickCity = true } label: {
                        HStack(spacing: 12) {
                            iconBadge("building.2.fill")
                            VStack(alignment: .leading, spacing: 2) {
                                Text(L("driver_home_city")).font(PKFont.labelMedium).foregroundStyle(Color.pkMuted)
                                Text(cityName).font(PKFont.titleSmall).foregroundStyle(Color.pkText)
                                Text(L("driver_home_city_hint")).font(PKFont.bodySmall).foregroundStyle(Color.pkMuted).multilineTextAlignment(.leading)
                            }
                            .frame(maxWidth: .infinity, alignment: .leading)
                            Image(systemName: "chevron.down").foregroundStyle(Color.pkMuted)
                        }
                        .padding(16)
                    }
                }
                if let error { Text(error).font(PKFont.bodyMedium).foregroundStyle(Color.pkError).padding(20) }
            }
            .padding(.bottom, 16)
        }
        .scrollDismissesKeyboard(.interactively)
        .clipped()
    }

    private var cityName: String {
        guard let city else { return L("driver_not_set") }
        return cities.first { $0.name == city }?.displayName ?? city
    }

    private func iconBadge(_ icon: String) -> some View {
        Image(systemName: icon).font(.system(size: 18)).foregroundStyle(Color.pkPrimary)
            .frame(width: 40, height: 40).background(Color.pkTint, in: RoundedRectangle(cornerRadius: 12))
    }

    private var carCard: some View {
        VStack(alignment: .leading, spacing: 0) {
            if let carLabel {
                HStack(spacing: 12) {
                    iconBadge("car.fill")
                    VStack(alignment: .leading) {
                        Text(carLabel).font(PKFont.titleSmall).foregroundStyle(Color.pkText)
                        Text(L("driver_car_from_registry")).font(PKFont.bodySmall).foregroundStyle(Color.pkMuted)
                    }
                }
                .padding(.bottom, 14)
            }
            HStack(alignment: .bottom, spacing: 10) {
                LabeledField(label: L(carLabel == nil ? "driver_plate_label" : "driver_plate_change"), text: $plate, keyboard: .numberPad, capitalization: .never, isError: lookupError != nil, onSubmit: lookup)
                    .onChange(of: plate) { _, v in
                        let clean = String(v.filter { $0.isNumber || $0 == "-" }.prefix(10))
                        if clean != v { plate = clean }
                    }
                Button(action: lookup) {
                    Group {
                        if lookingUp { ProgressView().tint(Color.pkOnPrimary) } else { Image(systemName: "magnifyingglass").foregroundStyle(Color.pkOnPrimary) }
                    }
                    .frame(width: 56, height: 52)
                    .background(plate.isEmpty ? Color.pkText.opacity(0.2) : Color.pkPrimary, in: Capsule())
                }
                .disabled(lookingUp || plate.isEmpty)
                .accessibilityLabel(L("driver_plate_lookup"))
            }
            Text(lookupError ?? L("driver_plate_privacy")).font(PKFont.bodySmall).foregroundStyle(lookupError != nil ? Color.pkError : Color.pkMuted).padding(.top, 6)
            Text(L("driver_size_label")).font(PKFont.labelLarge).foregroundStyle(Color.pkText).padding(.top, 18).padding(.bottom, 8)
            SizeSegments(selection: Binding(get: { vehicleSize }, set: { new in
                // A manual change means the looked-up model no longer describes the car.
                if vehicleSize != new { carLabel = nil }
                vehicleSize = new
            }))
            Text(L("driver_size_hint")).font(PKFont.bodySmall).foregroundStyle(Color.pkMuted).padding(.top, 6)
        }
        .padding(16)
    }

    private func load() async {
        let cached = session.driver
        vehicleSize = cached.preferences.vehicleSize
        carLabel = cached.preferences.carLabel
        carPaint = cached.preferences.carPaint
        disabledPermit = cached.preferences.hasDisabledPermit
        city = cached.preferences.homeCity
        if let lat = cached.homeLat, let lng = cached.homeLng { cityCentre = (lat, lng) }
        do {
            let server = try await API.shared.myProfile().preferences ?? Preferences()
            // Onboarding is for new drivers; an account that already answered goes straight on.
            if onboarding, !server.isEmpty {
                session.syncDriver(server)
                finish()
                return
            }
            vehicleSize = server.vehicleSize
            carLabel = server.carLabel
            carPaint = server.carPaint
            disabledPermit = server.hasDisabledPermit
            if server.homeCity != city { cityCentre = nil }
            city = server.homeCity
            loading = false
            cities = try await API.shared.cities()
            if let city, let hit = cities.first(where: { $0.name == city }) { cityCentre = (hit.lat, hit.lng) }
        } catch {
            loading = false
            self.error = friendly(error)
        }
    }

    private func lookup() {
        let digits = plate.filter(\.isNumber)
        guard (5...8).contains(digits.count) else {
            lookupError = L("driver_plate_invalid")
            return
        }
        lookingUp = true
        lookupError = nil
        Task {
            defer { lookingUp = false }
            do {
                let car = try await API.shared.lookupVehicle(digits)
                carLabel = car.label
                carPaint = car.paint
                session.carPaintIndex = -1 // a new car shows its own colour again
                vehicleSize = car.size
                plate = ""
            } catch {
                lookupError = (error as? APIError)?.status == 404 ? L("driver_plate_not_found") : L("driver_plate_lookup_failed")
            }
        }
    }

    private func save() {
        saving = true
        error = nil
        Task {
            defer { saving = false }
            do {
                let saved = try await API.shared.updatePreferences(Preferences(vehicleSize: vehicleSize, carLabel: carLabel, carPaint: carPaint, hasDisabledPermit: disabledPermit, homeCity: city))
                session.saveDriver(DriverPrefs(preferences: saved, homeLat: cityCentre?.0, homeLng: cityCentre?.1))
                finish()
            } catch {
                self.error = friendly(error)
            }
        }
    }

    private func finish() {
        session.onboardingSeen = true
        onDone()
    }
}

private struct CityPicker: View {
    let cities: [CityOut]
    let selected: String?
    let onPick: (CityOut?) -> Void
    @State private var query = ""
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        let q = query.trimmed.lowercased()
        let shown = cities
            .filter { q.isEmpty || $0.name.lowercased().contains(q) || ($0.nameEn ?? "").lowercased().contains(q) }
            .sorted { $0.displayName < $1.displayName }
        VStack(alignment: .leading, spacing: 8) {
            Text(L("driver_home_city")).font(PKFont.titleLarge).foregroundStyle(Color.pkText)
            HStack(spacing: 8) {
                Image(systemName: "magnifyingglass").foregroundStyle(Color.pkMuted)
                TextField(L("driver_search_city"), text: $query).font(PKFont.bodyLarge).autocorrectionDisabled()
            }
            .padding(.horizontal, 12).frame(height: 48)
            .overlay(RoundedRectangle(cornerRadius: 10).stroke(Color.pkMuted.opacity(0.6), lineWidth: 1))
            List {
                if q.isEmpty { row(L("driver_not_set"), checked: selected == nil) { onPick(nil) } }
                ForEach(shown) { city in row(city.displayName, checked: city.name == selected) { onPick(city) } }
            }
            .listStyle(.plain)
            Button(L("driver_close")) { dismiss() }.font(PKFont.labelLarge).foregroundStyle(Color.pkPrimary).frame(maxWidth: .infinity).frame(height: 44)
        }
        .padding(20)
        .presentationDetents([.large])
        .presentationBackground(Color.pkSurface)
    }

    private func row(_ label: String, checked: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack {
                Text(label).font(PKFont.bodyLarge).foregroundStyle(Color.pkText)
                Spacer()
                if checked { Image(systemName: "checkmark").foregroundStyle(Color.pkPrimary) }
            }
        }
        .listRowBackground(Color.clear)
    }
}

// MARK: Settings

/// Grouped rows, the current value on the trailing edge, a picker on tap.
struct SettingsScreen: View {
    let onBack: () -> Void
    let open: (Route) -> Void
    @EnvironmentObject private var session: Session
    @Environment(\.openURL) private var openURL
    @State private var dialog: String?
    @State private var deleting = false
    @State private var deleteError: String?
    @State private var payLabelTick = 0

    private var payValue: String {
        _ = payLabelTick
        if ParkingPay.isOff { return L("settings_pay_none") }
        return ParkingPay.preferred?.label ?? L("settings_pay_ask")
    }

    private var themeValue: String {
        switch session.themeMode {
        case "light": L("profile_theme_light")
        case "dark": L("profile_theme_dark")
        default: L("profile_theme_system")
        }
    }

    var body: some View {
        ScrollView {
            VStack(spacing: 0) {
                ScreenTopBar(title: L("settings_title"), onBack: onBack)
                SectionTitle(L("settings_section_account"))
                SectionCard {
                    VStack(spacing: 0) {
                        row("person.fill", L("details_title"), subtitle: L("settings_details_sub")) { open(.detailsEdit) }
                        divider
                        row("car.fill", L("driver_title"), subtitle: session.driver.preferences.carLabel ?? L("driver_summary_empty_body")) { open(.driver) }
                    }
                }
                SectionTitle(L("settings_section_preferences"))
                SectionCard {
                    VStack(spacing: 0) {
                        row("globe", L("profile_language_label"), value: L(L10n.isHebrew ? "profile_language_hebrew" : "profile_language_english")) { dialog = "language" }
                        divider
                        row("moon.fill", L("settings_appearance"), value: themeValue) { dialog = "theme" }
                        divider
                        row("creditcard.fill", L("settings_pay_app"), value: payValue) { dialog = "pay" }
                    }
                }
                SectionTitle(L("settings_section_community"))
                SectionCard {
                    VStack(spacing: 0) {
                        row("lightbulb.fill", L("settings_tips")) { open(.tips) }
                        divider
                        row("bubble.left.fill", L("feedback_title"), subtitle: L("feedback_subtitle")) { dialog = "feedback" }
                        divider
                        row("person.badge.plus", L("profile_share_button"), subtitle: L("settings_invite_subtitle"), action: invite)
                    }
                }
                SectionTitle(L("settings_section_about"))
                SectionCard {
                    VStack(spacing: 0) {
                        row("info.circle.fill", L("settings_version"), value: "\u{2066}\(ApiConfig.appVersion)\u{2069}")
                        divider
                        row("map.fill", L("settings_map_data"), value: L("settings_map_data_value"))
                        divider
                        row("face.smiling.inverse", L("settings_avatars"), subtitle: L("settings_avatars_value"))
                        divider
                        row("lock.shield.fill", L("legal_privacy")) { openURL(LegalLinks.privacy) }
                        divider
                        row("building.columns.fill", L("legal_terms")) { openURL(LegalLinks.terms) }
                    }
                }
                Spacer().frame(height: 24)
                SectionCard {
                    VStack(spacing: 0) {
                        row("rectangle.portrait.and.arrow.right", L("map_logout_cd"), tint: .pkError, chevron: false) { dialog = "logout" }
                        divider
                        // Required by the App Store for any app with accounts.
                        row("trash.fill", L("settings_delete_account"), tint: .pkError, chevron: false) { dialog = "delete" }
                    }
                }
                Spacer().frame(height: 24)
            }
        }
        .clipped()
        .background(Color.pkBackground.ignoresSafeArea())
        .confirmationDialog(L("profile_language_label"), isPresented: dialogBinding("language"), titleVisibility: .visible) {
            Button(L("profile_language_hebrew")) { session.setLanguage("he") }
            Button(L("profile_language_english")) { session.setLanguage("en") }
            Button(L("settings_cancel"), role: .cancel) {}
        }
        .confirmationDialog(L("settings_appearance"), isPresented: dialogBinding("theme"), titleVisibility: .visible) {
            Button(L("profile_theme_system")) { session.setTheme(nil) }
            Button(L("profile_theme_light")) { session.setTheme("light") }
            Button(L("profile_theme_dark")) { session.setTheme("dark") }
            Button(L("settings_cancel"), role: .cancel) {}
        }
        .alert(L("settings_logout_confirm_title"), isPresented: dialogBinding("logout")) {
            Button(L("map_logout_cd"), role: .destructive) { session.signOut() }
            Button(L("settings_cancel"), role: .cancel) {}
        } message: {
            Text(L("settings_logout_confirm_body"))
        }
        .alert(L("settings_delete_title"), isPresented: dialogBinding("delete")) {
            Button(L("settings_delete_confirm"), role: .destructive, action: deleteAccount)
            Button(L("settings_cancel"), role: .cancel) {}
        } message: {
            Text(deleteError.map { "\(L("settings_delete_body"))\n\n\($0)" } ?? L("settings_delete_body"))
        }
        .sheet(isPresented: dialogBinding("pay")) {
            payDialog.parkItEnvironment(session)
        }
        .sheet(isPresented: dialogBinding("feedback")) {
            FeedbackSheet().parkItEnvironment(session)
        }
    }

    private func dialogBinding(_ name: String) -> Binding<Bool> {
        Binding(get: { dialog == name }, set: { if !$0 { dialog = nil } })
    }

    private var divider: some View { Divider().padding(.leading, 68) }

    @ViewBuilder
    private func row(_ icon: String, _ title: String, subtitle: String? = nil, value: String? = nil, tint: Color = .pkPrimary, chevron: Bool = true, action: (() -> Void)? = nil) -> some View {
        let content = HStack(spacing: 16) {
            Image(systemName: icon).font(.system(size: 16)).foregroundStyle(tint)
                .frame(width: 36, height: 36).background(tint.opacity(0.12), in: RoundedRectangle(cornerRadius: 10))
            VStack(alignment: .leading, spacing: 1) {
                Text(title).font(PKFont.titleSmall).foregroundStyle(tint == .pkError ? tint : Color.pkText)
                if let subtitle { Text(subtitle).font(PKFont.bodySmall).foregroundStyle(Color.pkMuted).multilineTextAlignment(.leading) }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            if let value { Text(value).font(PKFont.bodyMedium).foregroundStyle(Color.pkMuted) }
            if chevron, action != nil { Image(systemName: "chevron.forward").font(.system(size: 12, weight: .semibold)).foregroundStyle(Color.pkMuted) }
        }
        .padding(.horizontal, 16).padding(.vertical, 14)
        .contentShape(Rectangle())
        if let action { Button(action: action) { content } } else { content }
    }

    private var payDialog: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text(L("settings_pay_app")).font(PKFont.titleLarge).foregroundStyle(Color.pkText)
            PayAppTiles(selected: ParkingPay.preferred) { app in
                ParkingPay.preferred = app
                payLabelTick += 1
                dialog = nil
            }
            HStack {
                Button(L("settings_pay_none")) {
                    ParkingPay.isOff = true
                    payLabelTick += 1
                    dialog = nil
                }
                Spacer()
                Button(L("settings_pay_ask")) {
                    ParkingPay.isOff = false
                    ParkingPay.preferred = nil
                    payLabelTick += 1
                    dialog = nil
                }
            }
            .font(PKFont.labelLarge).foregroundStyle(Color.pkPrimary)
        }
        .padding(.horizontal, 24).padding(.top, 28).padding(.bottom, 20)
        .fittedSheet()
    }

    private func deleteAccount() {
        guard !deleting else { return }
        deleting = true
        deleteError = nil
        Task {
            defer { deleting = false }
            do {
                try await API.shared.deleteAccount()
                session.signOut()
            } catch {
                deleteError = friendly(error)
                dialog = "delete" // keep the question up, now with what went wrong
            }
        }
    }
}

/// Free-text feedback. The app version, device and last error codes are attached.
private struct FeedbackSheet: View {
    @Environment(\.dismiss) private var dismiss
    @State private var text = ""
    @State private var sending = false
    @State private var sent = false
    @State private var error: String?

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(L(sent ? "feedback_thanks_title" : "feedback_title")).font(PKFont.titleLarge).foregroundStyle(Color.pkText)
            if sent {
                Text(L("feedback_thanks_body")).font(PKFont.bodyMedium).foregroundStyle(Color.pkText)
                Button(L("driver_close")) { dismiss() }.buttonStyle(PillButtonStyle())
            } else {
                TextField(L("feedback_hint"), text: $text, axis: .vertical).font(PKFont.bodyLarge).lineLimit(4...8)
                    .padding(12).overlay(RoundedRectangle(cornerRadius: 10).stroke(Color.pkMuted.opacity(0.6), lineWidth: 1))
                    .onChange(of: text) { _, v in if v.count > 4000 { text = String(v.prefix(4000)) } }
                Text(L("feedback_attached")).font(PKFont.bodySmall).foregroundStyle(Color.pkMuted)
                if let error { Text(error).font(PKFont.bodyMedium).foregroundStyle(Color.pkError) }
                Button(action: send) {
                    if sending { ProgressView().tint(Color.pkOnPrimary) } else { Text(L("feedback_send")) }
                }
                .buttonStyle(PillButtonStyle())
                .disabled(text.trimmed.isEmpty || sending)
                Button(L("settings_cancel")) { dismiss() }.font(PKFont.labelLarge).foregroundStyle(Color.pkPrimary).frame(maxWidth: .infinity)
            }
        }
        .padding(.horizontal, 20).padding(.top, 28).padding(.bottom, 20)
        .presentationDetents([.medium, .large])
        .presentationBackground(Color.pkSurface)
    }

    private func send() {
        sending = true
        error = nil
        Task {
            defer { sending = false }
            do {
                try await API.shared.sendFeedback(text.trimmed)
                sent = true
            } catch {
                self.error = friendly(error)
            }
        }
    }
}
