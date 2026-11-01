import SwiftUI

struct MapScreen: View {
    let onOpenProfile: () -> Void
    @StateObject private var model = MapModel()
    @EnvironmentObject private var session: Session
    @Environment(\.colorScheme) private var scheme
    @Environment(\.scenePhase) private var phase
    @State private var sheetExpanded = false
    @GestureState private var drag: CGFloat = 0

    private var peek: CGFloat { model.spotsLoaded && !model.spotsFailed && model.sortedSpots.isEmpty ? 178 : 114 }

    var body: some View {
        GeometryReader { g in
            let bottom = g.safeAreaInsets.bottom
            let closed = peek + bottom
            let open = closed + (model.sortedSpots.isEmpty ? 0 : 360)
            let base = sheetExpanded ? open : closed
            let height = min(open + 20, max(closed, base + drag))
            ZStack {
                ParkMapView(model: model, dark: scheme == .dark, bottomInset: closed + 96).ignoresSafeArea()
                CenterPin(lifted: model.cameraMoving)
                if !model.mapRendered { BrandedMapLoader() }
                topArea
                if model.locating {
                    Group {
                        HStack(spacing: 10) {
                            ProgressView()
                            Text(L("map_locating_you")).font(PKFont.labelLarge).foregroundStyle(Color.pkText)
                        }
                        .padding(.horizontal, 18).padding(.vertical, 12)
                        .background(Color.pkSurface, in: Capsule()).shadow(radius: 4)
                    }
                    .frame(maxHeight: .infinity, alignment: model.deviceLocation == nil ? .center : .top)
                    .padding(.top, model.deviceLocation == nil ? 0 : 128)
                }
                VStack(spacing: 0) {
                    Spacer()
                    if let bar = model.snackbar {
                        SnackbarView(message: bar.message, actionLabel: bar.actionLabel, action: bar.action)
                            .padding(.bottom, 8).transition(.opacity)
                    }
                    HStack {
                        Spacer()
                        Button(action: model.locateButton) {
                            Image(systemName: "location.fill").font(.system(size: 18)).foregroundStyle(Color.pkPrimary)
                                .frame(width: 48, height: 48).background(Color.pkSurface, in: Circle()).shadow(color: .black.opacity(0.2), radius: 4, y: 2)
                        }
                        .accessibilityLabel(L("map_my_location_cd"))
                    }
                    .padding(.horizontal, 20).padding(.bottom, 12)
                    dock.padding(.horizontal, 12).padding(.bottom, 12)
                    NearbySheet(model: model, height: height, bottomInset: bottom, onSpot: { spot in
                        model.fly(to: model.coord(spot), zoom: 16.5)
                        model.sheet = .spot(spot)
                        sheetExpanded = false
                    }, dragGesture: DragGesture()
                        .updating($drag) { v, s, _ in s = -v.translation.height }
                        .onEnded { v in
                            withAnimation(.spring(duration: 0.3)) { sheetExpanded = -v.predictedEndTranslation.height + base > (open + closed) / 2 }
                        })
                }
                .ignoresSafeArea(edges: .bottom)
            }
        }
        .background(Color.pkBackground.ignoresSafeArea())
        .onAppear(perform: model.start)
        .onChange(of: phase) { _, new in
            if new == .active { model.becameActive() }
            if new == .background { model.wentToBackground() }
        }
        .onChange(of: session.driver) { _, _ in model.pushCar() }
        .alert(L("location_why_title"), isPresented: $model.showLocationWhy) {
            Button(L("location_why_continue"), action: model.locationWhyContinue)
            Button(L("location_why_not_now"), role: .cancel, action: model.locationWhyNotNow)
        } message: {
            Text(L("location_why_body"))
        }
        .alert(L("arrived_title"), isPresented: $model.askArrived) {
            Button(L("spot_i_took_it"), action: model.arrivedYes)
            Button(L("arrived_no"), role: .cancel, action: model.arrivedNo)
        } message: {
            Text(L("arrived_body"))
        }
        .sheet(item: $model.sheet) { sheet in
            Group {
                switch sheet {
                case .spot(let spot): SpotSheet(spot: spot, model: model)
                case .lot(let lot): LotSheet(lot: lot)
                case .report: ReportSheet(model: model)
                case .sign: SignReaderSheet(place: model.deviceLocation)
                case .car: CarSheet(model: model)
                case .explainer: CarExplainerSheet(model: model)
                case .parkedPay: ParkedPaySheet(model: model)
                case .payChooser: PayChooserSheet(model: model)
                case .stopPay(let app): StopPaySheet(app: app, model: model)
                case .levelUp(let level): LevelUpSheet(level: level)
                }
            }
            .parkItEnvironment(session)
        }
    }

    private var topArea: some View {
        VStack(spacing: 10) {
            LocationBar(model: model, onOpenProfile: onOpenProfile).padding(.horizontal, 12)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 6) {
                    FilterChip(label: L("map_filter_all"), icon: "parkingsign", selected: model.typeFilter == nil) { model.typeFilter = nil }
                    // Only drivers with a disabled permit need this filter.
                    if model.prefs.hasDisabledPermit || model.typeFilter == "disabled" {
                        FilterChip(label: L("map_filter_disabled"), icon: "figure.roll", selected: model.typeFilter == "disabled") { model.typeFilter = "disabled" }
                    }
                    LotsChip(checked: model.showLots) { model.showLots.toggle() }
                }
                .padding(.horizontal, 12).padding(.vertical, 4)
            }
            if model.activeClaim == nil, let car = model.parkedCar {
                CarLine(car: car, model: model).padding(.horizontal, 12)
            }
            if let claim = model.activeClaim, let end = model.claimEndsAt {
                ClaimTripCard(spot: claim, endsAt: end, model: model).padding(.horizontal, 12)
            }
            Spacer()
        }
        .padding(.top, 8)
    }

    private var dock: some View {
        let parked = model.parkedCar != nil
        let (label, color): (String, Color) = switch model.mainAction {
        case .report: (L("dock_report"), .pkPrimary)
        case .leave: (L("dock_leave"), .pkGreen)
        case .reminder(let m): (L("dock_reminder", m), .pkAmber)
        }
        return HStack(spacing: 0) {
            Button {
                if parked { model.sheet = .car } else { model.parkHere() }
            } label: {
                VStack(spacing: 2) {
                    CarArt(size: model.prefs.vehicleSize ?? "regular", paint: myCarPaint(registry: model.prefs.carPaint), special: session.carSpecial).frame(width: 40)
                    Text(L(parked ? "dock_my_car" : "dock_parked")).font(PKFont.labelSmall).foregroundStyle(Color.pkText).lineLimit(1)
                }
                .frame(maxWidth: .infinity)
            }
            Button(action: model.onMain) {
                HStack(spacing: 4) {
                    if model.mainAction == .report { Image(systemName: "plus").font(.system(size: 16, weight: .bold)) }
                    Text(label).font(PKFont.titleSmall).lineLimit(1)
                }
                .foregroundStyle(Color.pkOnPrimary)
                .padding(.horizontal, 14)
                .frame(maxWidth: .infinity).frame(height: 48)
                .background(color, in: Capsule())
            }
            .layoutPriority(1)
            .frame(minWidth: 140)
            Button {
                model.sheet = .sign
            } label: {
                VStack(spacing: 2) {
                    ZStack(alignment: .topTrailing) {
                        SignScanIcon(size: 30)
                        AIBadge(small: true).offset(x: 6, y: -4)
                    }
                    Text(L("dock_sign")).font(PKFont.labelSmall).foregroundStyle(Color.pkText).lineLimit(1)
                }
                .frame(maxWidth: .infinity)
            }
        }
        .padding(.horizontal, 6)
        .frame(height: 64)
        .background(Color.pkSurface, in: RoundedRectangle(cornerRadius: 32))
        .shadow(color: .black.opacity(0.18), radius: 6, y: 2)
    }
}

// MARK: Top of the map

private struct LocationBar: View {
    @ObservedObject var model: MapModel
    let onOpenProfile: () -> Void
    @EnvironmentObject private var session: Session
    @State private var pulse = false

    var body: some View {
        let profile = model.myProfile
        let level = profile.map { ParkItLevel.of($0.level) }
        HStack(spacing: 12) {
            LogoMark(size: 24)
            VStack(alignment: .leading, spacing: 0) {
                Text(L("map_pin_label")).font(PKFont.labelSmall).foregroundStyle(Color.pkMuted)
                if !model.addressLoading, let address = model.resolvedAddress {
                    Text(address).font(PKFont.bodyLarge).foregroundStyle(Color.pkText).lineLimit(1)
                } else if model.addressFailed, !model.addressLoading {
                    Text(L("map_address_unavailable")).font(PKFont.bodyLarge).foregroundStyle(Color.pkMuted).lineLimit(1)
                } else {
                    HStack(spacing: 8) {
                        RoundedRectangle(cornerRadius: 7).fill(Color.pkText.opacity(pulse ? 0.1 : 0.04)).frame(maxWidth: 150).frame(height: 14)
                        Text(L("map_locating")).font(PKFont.labelSmall).foregroundStyle(Color.pkMuted.opacity(pulse ? 0.8 : 0.35))
                    }
                    .frame(height: 24)
                    .onAppear { withAnimation(.easeInOut(duration: 0.7).repeatForever()) { pulse = true } }
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            // Avatar in a ring that fills toward the next level, in the level's colour.
            Button(action: onOpenProfile) {
                ZStack {
                    Circle().stroke(Color.pkOutline.opacity(0.6), lineWidth: 3)
                    Circle().trim(from: 0, to: profile?.levelProgress ?? 0)
                        .stroke(level?.color ?? .pkPrimary, style: StrokeStyle(lineWidth: 3, lineCap: .round))
                        .rotationEffect(.degrees(-90))
                    AvatarView(name: session.displayName, background: .pkPrimary, size: 38, textColor: .pkOnPrimary, avatarId: profile?.avatar)
                }
                .frame(width: 45, height: 45)
                .frame(width: 48, height: 48)
            }
            .accessibilityLabel(L("map_open_profile_cd"))
        }
        .padding(.leading, 16).padding(.trailing, 8)
        .frame(height: 56)
        .background(Color.pkSurface, in: RoundedRectangle(cornerRadius: 28))
        .shadow(color: .black.opacity(0.18), radius: 6, y: 2)
    }
}

private struct FilterChip: View {
    let label: String
    let icon: String
    let selected: Bool
    let action: () -> Void
    var body: some View {
        Button(action: action) {
            HStack(spacing: 6) {
                Image(systemName: icon).font(.system(size: 14))
                Text(label).font(PKFont.labelLarge)
            }
            .foregroundStyle(selected ? Color.pkOnPrimary : Color.pkText)
            .padding(.horizontal, 11).frame(height: 36)
            .background(selected ? Color.pkPrimary : Color.pkSurface, in: Capsule())
            .shadow(color: .black.opacity(0.15), radius: 3, y: 1)
        }
    }
}

private struct LotsChip: View {
    let checked: Bool
    let action: () -> Void
    var body: some View {
        let slate = Color(hexString: Pins.lotColor) ?? .gray
        Button(action: action) {
            HStack(spacing: 6) {
                Text(verbatim: "₪").font(PKFont.titleMedium).foregroundStyle(checked ? .white : slate)
                Text(L("map_filter_lots")).font(PKFont.labelLarge).foregroundStyle(checked ? .white : Color.pkText)
            }
            .padding(.horizontal, 11).frame(height: 36)
            .background(checked ? slate : Color.pkSurface, in: Capsule())
            .shadow(color: .black.opacity(0.15), radius: 3, y: 1)
        }
    }
}

/// A drop pin that lifts while the map moves; the needle tip is the exact screen centre.
private struct CenterPin: View {
    let lifted: Bool
    var body: some View {
        ZStack {
            Ellipse().fill(.black.opacity(0.25)).frame(width: 10, height: 4).scaleEffect(lifted ? 0.6 : 1)
            VStack(spacing: 0) {
                ZStack {
                    Circle().fill(Color.pkPrimary).frame(width: 30, height: 30).shadow(radius: 3)
                    Circle().fill(.white).frame(width: 10, height: 10)
                }
                Rectangle().fill(Color.pkPrimary).frame(width: 3, height: 16)
                Color.clear.frame(height: 46)
            }
            .offset(y: lifted ? -12 : 0)
            .animation(.spring(response: 0.3, dampingFraction: 0.5), value: lifted)
        }
        .allowsHitTesting(false)
    }
}

private struct BrandedMapLoader: View {
    @State private var spin = false
    var body: some View {
        ZStack {
            Circle().fill(Color.pkSurface).frame(width: 64, height: 64).shadow(radius: 6)
            Circle().trim(from: 0.05, to: 0.85)
                .stroke(AngularGradient(colors: [Color.pkPrimary.opacity(0), .pkPrimary], center: .center), style: StrokeStyle(lineWidth: 4, lineCap: .round))
                .frame(width: 50, height: 50)
                .rotationEffect(.degrees(spin ? 360 : 0))
                .animation(.linear(duration: 1).repeatForever(autoreverses: false), value: spin)
            LogoMark(size: 26)
        }
        .onAppear { spin = true }
    }
}

// MARK: The sheet with the nearby list

private struct NearbySheet<G: Gesture>: View {
    @ObservedObject var model: MapModel
    let height: CGFloat
    let bottomInset: CGFloat
    let onSpot: (SpotOut) -> Void
    let dragGesture: G

    var body: some View {
        let spots = model.sortedSpots
        VStack(spacing: 0) {
            VStack(spacing: 0) {
                Capsule().fill(Color.pkMuted.opacity(0.4)).frame(width: 36, height: 4).padding(.vertical, 12)
                if model.spotsFailed {
                    failed
                } else {
                    header(spots)
                }
            }
            .contentShape(Rectangle())
            .gesture(dragGesture)
            if !model.spotsFailed {
                if model.spotsLoaded, spots.isEmpty { emptyActions }
                if !spots.isEmpty {
                    Divider().overlay(Color.pkOutline.opacity(0.5))
                    ScrollView {
                        LazyVStack(spacing: 0) {
                            ForEach(Array(spots.enumerated()), id: \.element.id) { i, spot in
                                // In English, the stored Hebrew street beats no street at all.
                                SpotRow(spot: spot, model: model, address: spot.localAddress ?? model.spotAddresses[spot.id] ?? spot.address) { onSpot(spot) }
                                    .accessibilityIdentifier("spot_row_\(i)")
                            }
                        }
                    }
                }
            }
            Spacer(minLength: 0)
        }
        .frame(maxWidth: .infinity)
        .frame(height: height, alignment: .top)
        .background(Color.pkSurface, in: UnevenRoundedRectangle(topLeadingRadius: 24, topTrailingRadius: 24))
        .shadow(color: .black.opacity(0.15), radius: 8, y: -2)
    }

    private var failed: some View {
        // Offline or server down: a clear state with a way out, never a fake "no spots".
        HStack(spacing: 14) {
            Image(systemName: "icloud.slash").font(.system(size: 24)).foregroundStyle(Color.pkMuted)
            VStack(alignment: .leading) {
                Text(L("map_offline_title")).font(PKFont.titleMedium).foregroundStyle(Color.pkText)
                Text(L("map_offline_body")).font(PKFont.bodySmall).foregroundStyle(Color.pkMuted)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            Button(L("map_offline_retry"), action: model.retrySpots)
                .font(PKFont.labelLarge).foregroundStyle(Color.pkPrimary).padding(.horizontal, 14).padding(.vertical, 8)
                .background(Color.pkTint, in: Capsule())
        }
        .padding(.horizontal, 20).padding(.bottom, 16)
    }

    private func header(_ spots: [SpotOut]) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(!model.spotsLoaded ? L("map_nearby_searching") : spots.isEmpty ? L(model.tooFar ? "map_get_closer_title" : "map_nearby_none") : LP("map_nearby_count", spots.count))
                .font(PKFont.titleLarge).foregroundStyle(Color.pkText)
            if model.forecast != nil || model.forecastNoData { forecastLine }
            if let tip = model.dailyTip {
                HStack(spacing: 6) {
                    Image(systemName: "lightbulb.fill").font(.system(size: 12)).foregroundStyle(Color.pkAmber)
                    Text(L("tip_best_hour", "\(tip.hour):00", "\((tip.hour + 1) % 24):00")).font(PKFont.bodySmall).foregroundStyle(Color.pkMuted).lineLimit(1)
                    Button { model.dailyTip = nil } label: {
                        Image(systemName: "xmark").font(.system(size: 10)).foregroundStyle(Color.pkMuted).frame(width: 20, height: 20)
                    }
                    .accessibilityLabel(L("ad_close"))
                }
            }
            Text(hint(spots)).font(PKFont.bodyMedium).foregroundStyle(Color.pkMuted).lineLimit(1)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 20).padding(.bottom, 16)
    }

    private var forecastLine: some View {
        HStack(spacing: 6) {
            Text(L("forecast_tag")).font(PKFont.labelSmall).foregroundStyle(.white).padding(.horizontal, 6)
                .background(LinearGradient(colors: [Color(hex: 0x7B3FC4), Color(hex: 0x2E7BD6)], startPoint: .leading, endPoint: .trailing), in: Capsule())
            Text(forecastText).font(PKFont.bodySmall).foregroundStyle(Color.pkMuted).lineLimit(2)
        }
    }

    private var forecastText: String {
        guard let f = model.forecast else { return L("forecast_no_data") }
        let every = f.everyMinutes > 90 ? L("forecast_rare") : LP("forecast_every", f.everyMinutes)
        switch f.scope {
        case "area": return every + " · " + L("forecast_scope_area")
        case "city": return every + " · " + L("forecast_scope_city")
        default: return every
        }
    }

    private func hint(_ spots: [SpotOut]) -> String {
        if !model.spotsLoaded { return L("map_nearby_searching_hint") }
        let usable = spots.filter { !DriverFit(spot: $0, prefs: model.prefs).unusable }
        guard let nearest = (usable.isEmpty ? spots : usable).min(by: { model.coord($0).distance(to: model.reference) < model.coord($1).distance(to: model.reference) }) else {
            return L(model.tooFar ? "map_get_closer_hint" : "map_nearby_empty_hint")
        }
        return L("map_nearest", model.distanceLabel(nearest), relativeLong(nearest.reportedAt))
    }

    /// An empty map is where a new driver gives up: offer the car parks, and more drivers.
    private var emptyActions: some View {
        HStack(spacing: 8) {
            if !model.showLots {
                Button { model.showLots = true } label: {
                    Label(L("map_empty_show_lots"), systemImage: "parkingsign").font(PKFont.labelLarge).lineLimit(1)
                        .foregroundStyle(Color.pkPrimary).frame(maxWidth: .infinity).frame(height: 40).background(Color.pkTint, in: Capsule())
                }
            }
            Button(action: invite) {
                Label(L("map_empty_invite"), systemImage: "person.badge.plus").font(PKFont.labelLarge).lineLimit(1)
                    .foregroundStyle(Color.pkOnPrimary).frame(maxWidth: .infinity).frame(height: 40).background(Color.pkPrimary, in: Capsule())
            }
        }
        .padding(.horizontal, 20).padding(.bottom, 16)
    }
}

private struct SpotRow: View {
    let spot: SpotOut
    @ObservedObject var model: MapModel
    let address: String?
    let onTap: () -> Void

    var body: some View {
        let fit = DriverFit(spot: spot, prefs: model.prefs)
        let color = Color(hexString: pinColor(spot)) ?? .pkGreen
        let disabled = spot.spotType == "disabled"
        Button(action: onTap) {
            HStack(spacing: 14) {
                SpotThumb(url: spot.photoUrl.flatMap(ApiConfig.url(path:)), disabled: disabled, color: color)
                VStack(alignment: .leading, spacing: 1) {
                    Text(address ?? L("map_locating")).font(PKFont.titleSmall).foregroundStyle(address == nil ? Color.pkMuted : Color.pkText).lineLimit(1)
                    Text(details(disabled: disabled)).font(PKFont.bodySmall).foregroundStyle(Color.pkMuted).lineLimit(1)
                    if fit.needsDisabledPermit {
                        Text(L("fit_needs_disabled_permit")).font(PKFont.labelMedium).foregroundStyle(Color.pkMuted).lineLimit(1)
                    } else if fit.tooSmall {
                        Text(L("fit_too_small")).font(PKFont.labelMedium).foregroundStyle(Color.pkMuted).lineLimit(1)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                Text(model.distanceLabel(spot)).font(PKFont.labelLarge).foregroundStyle(Color.pkText)
            }
            .padding(.horizontal, 20).padding(.vertical, 12)
            .opacity(fit.unusable ? 0.55 : 1)
            .contentShape(Rectangle())
        }
    }

    private func details(disabled: Bool) -> String {
        var parts = spot.left ? [L("spot_source_left")] : []
        parts.append(L(disabled ? "map_spot_kind_disabled" : "map_spot_kind_regular"))
        if spot.payment == "paid" { parts.append(L("spot_blue_white_short")) }
        parts.append(relativeLong(spot.reportedAt))
        if spot.hotMinutes != nil { parts.append(L("hot_short")) }
        if spot.status == "claimed" {
            parts.append(L("report_status_claimed"))
        } else if spot.mine {
            parts.append(L("map_spot_yours"))
        }
        return parts.joined(separator: " · ")
    }
}

private struct SpotThumb: View {
    let url: URL?
    let disabled: Bool
    let color: Color
    var body: some View {
        ZStack {
            RoundedRectangle(cornerRadius: 12).fill(color.opacity(0.12))
            AsyncImage(url: url) { phase in
                if let image = phase.image {
                    image.resizable().scaledToFill()
                } else {
                    Image(systemName: disabled ? "figure.roll" : "parkingsign").foregroundStyle(color)
                }
            }
        }
        .frame(width: 48, height: 48)
        .clipShape(RoundedRectangle(cornerRadius: 12))
    }
}

// MARK: Trip and car

/// Stays on screen after "On my way" so the driver can close the loop from the map. No
/// ticking countdown: a timer on screen while driving pushes people to hurry.
private struct ClaimTripCard: View {
    let spot: SpotOut
    let endsAt: Date
    @ObservedObject var model: MapModel

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 12) {
                Image(systemName: "car.fill").foregroundStyle(Color.pkOrange).frame(width: 40, height: 40)
                    .background(Color.pkOrange.opacity(0.14), in: RoundedRectangle(cornerRadius: 12))
                VStack(alignment: .leading) {
                    Text(L("claim_trip_title")).font(PKFont.labelMedium).foregroundStyle(Color.pkOrange)
                    Text(spot.address ?? L("map_locating")).font(PKFont.titleSmall).foregroundStyle(Color.pkText).lineLimit(1)
                }
                Spacer()
            }
            Text(L("claim_drive_safely")).font(PKFont.bodySmall).foregroundStyle(Color.pkMuted).padding(.top, 8)
            HStack(spacing: 8) {
                TripAction(label: L("spot_navigate"), icon: "location.north.fill", color: .pkPrimary) { DriveTo.open(lat: spot.lat, lng: spot.lng) }
                TripAction(label: L("spot_i_took_it"), icon: "checkmark.circle.fill", color: .pkGreen) { model.tripFeedback("confirmed_taken") }
                TripAction(label: L("spot_flag_taken"), icon: "minus.circle.fill", color: .pkOrange) { model.tripFeedback("flagged_false") }
            }
            .padding(.top, 12)
            Button(L("claim_release"), action: model.releaseClaim)
                .font(PKFont.labelLarge).foregroundStyle(Color.pkPrimary)
                .frame(maxWidth: .infinity).frame(height: 40)
        }
        .padding(14)
        .background(Color.pkSurface, in: RoundedRectangle(cornerRadius: 20))
        .shadow(color: .black.opacity(0.18), radius: 6, y: 2)
        .task(id: endsAt) {
            try? await Task.sleep(for: .seconds(max(0, endsAt.timeIntervalSinceNow)))
            if !Task.isCancelled { model.claimExpired() }
        }
    }
}

struct TripAction: View {
    let label: String
    let icon: String
    let color: Color
    let action: () -> Void
    var body: some View {
        Button(action: action) {
            HStack(spacing: 4) {
                Image(systemName: icon).font(.system(size: 13))
                Text(label).font(PKFont.labelMedium).lineLimit(1).minimumScaleFactor(0.8)
            }
            .foregroundStyle(color)
            .frame(maxWidth: .infinity).frame(height: 44)
            .background(color.opacity(0.1), in: RoundedRectangle(cornerRadius: 14))
        }
    }
}

/// The parked car as one thin line: tap for walk, leaving and pay.
private struct CarLine: View {
    let car: ParkedCar
    @ObservedObject var model: MapModel
    @EnvironmentObject private var session: Session

    var body: some View {
        let here = model.deviceLocation.map { $0.distance(to: Coord(latitude: car.lat, longitude: car.lng)) }
        Button { model.sheet = .car } label: {
            HStack(spacing: 10) {
                CarArt(size: model.prefs.vehicleSize ?? "regular", paint: myCarPaint(registry: model.prefs.carPaint), special: session.carSpecial).frame(width: 34)
                VStack(alignment: .leading, spacing: 0) {
                    Text(title(here)).font(PKFont.labelLarge).foregroundStyle(Color.pkText).lineLimit(1)
                    Text(L("car_line_hint")).font(PKFont.labelSmall).foregroundStyle(Color.pkPrimary).lineLimit(1)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                if let at = ParkingReminder.activeChangeAt {
                    HStack(spacing: 2) {
                        Image(systemName: "alarm.fill").font(.system(size: 11))
                        Text(verbatim: reminderClock(at)).font(PKFont.labelMedium)
                    }
                    .foregroundStyle(Color.pkAmber)
                }
                Image(systemName: "chevron.forward").font(.system(size: 12, weight: .semibold)).foregroundStyle(Color.pkMuted)
                Button { DriveTo.walk(lat: car.lat, lng: car.lng) } label: {
                    HStack(spacing: 4) {
                        Image(systemName: "figure.walk").font(.system(size: 13))
                        Text(L("car_navigate")).font(PKFont.labelMedium)
                    }
                    .foregroundStyle(Color.pkOnPrimary).padding(.horizontal, 10).padding(.vertical, 5)
                    .background(Color.pkPrimary, in: Capsule())
                }
            }
            .padding(.leading, 14).padding(.trailing, 8).padding(.vertical, 8)
            .background(Color.pkSurface, in: RoundedRectangle(cornerRadius: 22))
            .shadow(color: .black.opacity(0.18), radius: 6, y: 2)
        }
        .buttonStyle(.plain)
    }

    private func title(_ here: Double?) -> String {
        var parts: [String] = []
        if let street = car.address?.split(separator: ",").first { parts.append(street.trimmingCharacters(in: .whitespaces)) }
        if let here { parts.append(here < MapConst.nearMeM ? L("car_next_to_you") : L("car_from_you", formatDistance(here))) }
        return L("car_line_title") + " " + parts.joined(separator: " · ")
    }
}

func reminderClock(_ at: Date) -> String {
    let minutes = max(0, Int(at.timeIntervalSinceNow / 60))
    return String(format: "%d:%02d", minutes / 60, minutes % 60)
}

// MARK: Sign icon, also used by the sign reader

/// "AI": marks what a model decided, so nobody mistakes it for a rule.
struct AIBadge: View {
    var small = false
    var body: some View {
        let ai = LinearGradient(colors: [Color(hex: 0x7B3FC4), Color(hex: 0x2E7BD6), Color(hex: 0x00B4D8)], startPoint: .topLeading, endPoint: .bottomTrailing)
        Text(verbatim: "AI").font(.system(size: small ? 8 : 11, weight: .black)).foregroundStyle(ai)
            .environment(\.layoutDirection, .leftToRight)
            .padding(.horizontal, small ? 3 : 6).padding(.vertical, 1)
            .background(.white, in: RoundedRectangle(cornerRadius: small ? 5 : 7))
            .overlay(RoundedRectangle(cornerRadius: small ? 5 : 7).stroke(ai, lineWidth: 1.5))
    }
}

/// A blue "P" sign on its pole with a small camera badge: "photograph the parking sign".
struct SignScanIcon: View {
    let size: CGFloat
    var body: some View {
        ZStack(alignment: .bottomTrailing) {
            Canvas { ctx, s in
                let w = s.width, left = w * 0.18, top = w * 0.04, side = w * 0.6
                ctx.fill(Path(roundedRect: CGRect(x: w * 0.44, y: w * 0.55, width: w * 0.08, height: w * 0.43), cornerRadius: w * 0.04), with: .color(Color(hex: 0x9AA0A6)))
                ctx.fill(Path(roundedRect: CGRect(x: left - w * 0.03, y: top - w * 0.03, width: side + w * 0.06, height: side + w * 0.06), cornerRadius: side * 0.2), with: .color(.white))
                ctx.fill(Path(roundedRect: CGRect(x: left, y: top, width: side, height: side), cornerRadius: side * 0.16), with: .color(Color(hex: 0x1565C0)))
                ctx.draw(Text(verbatim: "P").font(.system(size: w * 0.42, weight: .black)).foregroundColor(.white), at: CGPoint(x: left + side / 2, y: top + side / 2))
            }
            Image(systemName: "camera.fill").font(.system(size: size * 0.2)).foregroundStyle(.white)
                .frame(width: size * 0.38, height: size * 0.38).background(Color.pkBlue, in: Circle())
                .overlay(Circle().stroke(.white, lineWidth: size * 0.035))
        }
        .frame(width: size, height: size)
        .environment(\.layoutDirection, .leftToRight)
    }
}
