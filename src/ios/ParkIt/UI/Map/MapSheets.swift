import SwiftUI

private let hotColor = Color(hex: 0xE8480C)

/// The tapped-spot card, top to bottom: where, status, proof, what matters to you, one big
/// action ("On my way" claims and navigates), then "still free?" as a quiet two-part control.
struct SpotSheet: View {
    let spot: SpotOut
    @ObservedObject var model: MapModel
    @Environment(\.dismiss) private var dismiss
    @State private var busy = false
    @State private var error: String?
    @State private var address: String?
    @State private var photoFailed = false
    @State private var photoFull = false
    @State private var askReport = false

    private var isMine: Bool { spot.reporterId == Session.shared.userId }
    private var isClaimed: Bool { spot.status == "claimed" }
    private var canClaim: Bool { spot.status == "active" && !isMine }
    private var photoURL: URL? { spot.photoUrl.flatMap(ApiConfig.url(path:)) }
    private var fit: DriverFit { DriverFit(spot: spot, prefs: model.prefs) }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(alignment: .top) {
                Text(address ?? spot.localAddress ?? L("map_locating")).font(PKFont.titleLarge).foregroundStyle(Color.pkText).lineLimit(2)
                Spacer(minLength: 12)
                Button { dismiss() } label: {
                    Image(systemName: "xmark").font(.system(size: 13, weight: .semibold)).foregroundStyle(Color.pkText)
                        .frame(width: 36, height: 36).background(Color.pkText.opacity(0.06), in: Circle())
                }
                .accessibilityLabel(L("spot_close_cd"))
            }
            status.padding(.top, 8)
            photo.padding(.top, 14)
            notices
            Button(action: mainAction) {
                HStack(spacing: 8) {
                    if busy {
                        ProgressView().tint(Color.pkOnPrimary)
                    } else {
                        Image(systemName: "location.north.fill")
                        Text(L(canClaim ? "spot_go" : "spot_navigate")).lineLimit(1)
                    }
                }
            }
            .buttonStyle(PillButtonStyle())
            .disabled(busy)
            .padding(.top, 18)
            if let error { Text(error).font(PKFont.bodySmall).foregroundStyle(Color.pkError).padding(.top, 10) }

            if !isMine {
                Text(L("spot_still_free_question")).font(PKFont.labelLarge).foregroundStyle(Color.pkMuted).padding(.top, 18).padding(.bottom, 8)
                HStack(spacing: 0) {
                    half(L("spot_i_took_it"), "checkmark.circle.fill", .pkGreen) {
                        try await API.shared.submitFeedback(spot.id, "confirmed_taken")
                        model.parkedOnSpot(spot, address: spot.localAddress ?? address)
                    }
                    Rectangle().fill(Color.pkOutline).frame(width: 1, height: 22)
                    half(L("spot_flag_taken"), "minus.circle.fill", .pkOrange) {
                        try await API.shared.submitFeedback(spot.id, "flagged_false")
                        model.sheet = nil
                    }
                }
                .frame(height: 46)
                .background(Color.pkText.opacity(0.05), in: Capsule())

                Button { askReport = true } label: {
                    Label(L("content_report_action"), systemImage: "flag.fill").font(PKFont.labelLarge).foregroundStyle(Color.pkMuted).frame(maxWidth: .infinity).frame(height: 40)
                }
                .disabled(busy)
                .padding(.top, 8)
            }
            if isMine, spot.status == "active" {
                Button(L("spot_cancel_report")) {
                    run {
                        try await API.shared.cancelSpot(spot.id)
                        model.sheet = nil
                    }
                }
                .font(PKFont.labelLarge).foregroundStyle(Color.pkError).frame(maxWidth: .infinity).frame(height: 40)
                .disabled(busy).padding(.top, 8)
            }
        }
        .padding(.horizontal, 20).padding(.top, 24).padding(.bottom, 20)
        .fittedSheet()
        .task {
            address = spot.localAddress ?? Geocoder.spot(spot.id)
            if address == nil { address = await Geocoder.resolve(spot: spot.id, lat: spot.lat, lng: spot.lng) }
        }
        .confirmationDialog(L("content_report_title"), isPresented: $askReport, titleVisibility: .visible) {
            if photoURL != nil { reportButton("photo", "content_report_photo") }
            if spot.note != nil { reportButton("note", "content_report_note") }
            reportButton("user", "content_report_user")
            reportButton("other", "content_report_other")
            Button(L("settings_cancel"), role: .cancel) {}
        }
        .fullScreenCover(isPresented: $photoFull) {
            if let photoURL { PhotoViewer(url: photoURL) }
        }
    }

    private func reportButton(_ reason: String, _ key: String) -> some View {
        Button(L(key)) {
            run {
                try await API.shared.reportContent(spot.id, reason)
                model.sheet = nil
                model.show(L("content_report_sent"))
            }
        }
    }

    private var status: some View {
        let short = relativeShort(spot.reportedAt)
        let age = short == L("time_now_short") ? L("time_now_long") : L("spot_age_short", short)
        let text = isClaimed ? L("spot_someone_on_the_way") : isMine ? L("map_spot_yours") : L("spot_status_free")
        let minutes = ageMinutes(spot.reportedAt)
        let color: Color = isClaimed ? .pkOrange : isMine ? .pkBlue : minutes < 10 ? .pkGreen : minutes < 30 ? .pkOrange : Color(hex: 0x80868B)
        return HStack(spacing: 10) {
            HStack(spacing: 6) {
                Circle().fill(color).frame(width: 8, height: 8)
                Text(verbatim: "\(text) · \(age)").font(PKFont.labelLarge).foregroundStyle(color).lineLimit(1)
            }
            .padding(.horizontal, 10).padding(.vertical, 5)
            .background(color.opacity(0.12), in: Capsule())
            Text(L("spot_distance_away", model.distanceLabel(spot))).font(PKFont.bodyMedium).foregroundStyle(Color.pkMuted).lineLimit(1)
        }
    }

    @ViewBuilder private var photo: some View {
        if let photoURL, !photoFailed {
            AsyncImage(url: photoURL) { phase in
                switch phase {
                case .success(let image): image.resizable().scaledToFill()
                case .failure: Color.pkTint.onAppear { photoFailed = true }
                default: Color.pkTint.overlay(ProgressView())
                }
            }
            .frame(maxWidth: .infinity).frame(height: 170)
            .clipShape(RoundedRectangle(cornerRadius: 16))
            .overlay(alignment: .bottomLeading) {
                Label(L("spot_photo_taken", relativeLong(spot.reportedAt)), systemImage: "camera.fill").font(PKFont.labelMedium).foregroundStyle(.white)
                    .padding(.horizontal, 10).padding(.vertical, 5).background(.black.opacity(0.55), in: Capsule()).padding(10)
            }
            .overlay(alignment: .bottomTrailing) {
                Image(systemName: "arrow.up.left.and.arrow.down.right").font(.system(size: 13)).foregroundStyle(.white)
                    .frame(width: 32, height: 32).background(.black.opacity(0.55), in: Circle()).padding(10)
                    .accessibilityLabel(L("spot_photo_expand_cd"))
            }
            .contentShape(Rectangle())
            .onTapGesture { photoFull = true }
            .accessibilityLabel(L("spot_photo_cd"))
        } else {
            HStack(spacing: 8) {
                Image(systemName: "camera.metering.none").font(.system(size: 15)).foregroundStyle(Color.pkMuted.opacity(0.7))
                Text(L("spot_no_photo_cd")).font(PKFont.bodySmall).foregroundStyle(Color.pkMuted)
                Spacer()
            }
            .padding(.horizontal, 12).frame(height: 44)
            .background(Color.pkText.opacity(0.04), in: RoundedRectangle(cornerRadius: 12))
        }
    }

    @ViewBuilder private var notices: some View {
        let lines = noticeLines
        if !lines.isEmpty {
            VStack(alignment: .leading, spacing: 10) {
                ForEach(lines.indices, id: \.self) { i in
                    let (icon, text, color) = lines[i]
                    HStack(alignment: .top, spacing: 10) {
                        Image(systemName: icon).font(.system(size: 14)).foregroundStyle(color ?? Color.pkMuted).frame(width: 18)
                        Text(text).font(PKFont.bodyMedium).foregroundStyle(color != nil ? Color.pkText : Color.pkMuted)
                    }
                }
            }
            .padding(.horizontal, 14).padding(.vertical, 12)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Color.pkText.opacity(0.04), in: RoundedRectangle(cornerRadius: 14))
            .padding(.top, 12)
        }
    }

    private var noticeLines: [(String, String, Color?)] {
        var out: [(String, String, Color?)] = []
        if let hot = spot.hotMinutes { out.append(("flame.fill", LP("hot_long", hot), hotColor)) }
        if fit.needsDisabledPermit { out.append(("figure.roll", L("fit_needs_disabled_permit_long"), .pkError)) }
        if fit.tooSmall { out.append(("car.fill", L("fit_too_small_long"), .pkOrange)) }
        if let note = spot.note { out.append(("info.circle.fill", L("spot_note", note), .pkAmber)) }
        if let size = spot.vehicleSize {
            let key = size == "compact" ? "report_size_compact" : size == "large" ? "report_size_large" : "report_size_regular"
            out.append(("car.fill", L("spot_fits", L(key).lowercased()), nil))
        }
        if spot.spotType == "disabled" { out.append(("figure.roll", L("map_spot_kind_disabled"), nil)) }
        if spot.payment == "paid" { out.append(("creditcard.fill", L("spot_blue_white"), nil)) }
        let level = ParkItLevel.of(spot.reporterLevel)
        if level != .newbie { out.append((level.icon, L("spot_reported_by_level", level.name), level.color)) }
        return out
    }

    private func half(_ label: String, _ icon: String, _ color: Color, action: @escaping () async throws -> Void) -> some View {
        Button { run(action) } label: {
            HStack(spacing: 6) {
                Image(systemName: icon).font(.system(size: 15)).foregroundStyle(color)
                Text(label).font(PKFont.labelLarge).foregroundStyle(Color.pkText).lineLimit(1)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .contentShape(Rectangle())
        }
        .disabled(busy)
    }

    private func run(_ action: @escaping () async throws -> Void) {
        busy = true
        error = nil
        Task {
            defer { busy = false }
            do {
                try await action()
                model.refreshSpotsNow()
            } catch {
                self.error = friendly(error)
            }
        }
    }

    /// Claim and navigate are one tap: saying "on my way" is why you open navigation.
    private func mainAction() {
        guard canClaim else {
            DriveTo.open(lat: spot.lat, lng: spot.lng)
            return
        }
        busy = true
        error = nil
        Task {
            defer { busy = false }
            do {
                var claimed = try await API.shared.claimSpot(spot.id)
                claimed.address = spot.localAddress ?? address
                claimed.reporterLevel = spot.reporterLevel
                model.claimed(claimed)
                DriveTo.open(lat: spot.lat, lng: spot.lng)
            } catch {
                self.error = friendly(error)
            }
        }
    }
}

/// The reporter's photo, full screen: pinch to zoom, tap the X to close.
private struct PhotoViewer: View {
    let url: URL
    @Environment(\.dismiss) private var dismiss
    @State private var scale: CGFloat = 1

    var body: some View {
        ZStack(alignment: .topTrailing) {
            Color.black.ignoresSafeArea()
            AsyncImage(url: url) { image in
                image.resizable().scaledToFit().scaleEffect(scale)
                    .gesture(MagnifyGesture().onChanged { scale = min(5, max(1, $0.magnification)) })
            } placeholder: {
                ProgressView().tint(.white)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            Button { dismiss() } label: {
                Image(systemName: "xmark").foregroundStyle(.white).frame(width: 40, height: 40).background(.black.opacity(0.5), in: Circle())
            }
            .padding(12)
            .accessibilityLabel(L("spot_close_cd"))
        }
    }
}

/// A car park's card: every line optional, and never a guessed price or opening hours.
struct LotSheet: View {
    let lot: ParkingLotOut

    var body: some View {
        let name = (L10n.isHebrew ? lot.name : lot.nameEn ?? lot.name) ?? L("lot_unnamed")
        let kind: String? = switch lot.kind {
        case "underground": L("lot_kind_underground")
        case "multi-storey": L("lot_kind_multistorey")
        case "rooftop": L("lot_kind_rooftop")
        case "surface": L("lot_kind_surface")
        default: nil
        }
        let details = [kind, lot.capacity.map { L("lot_capacity", $0) }, L("spot_distance_away", formatDistance(lot.distanceM))].compactMap { $0 }
        let date = lot.dataUpdatedAt?.prefix(10).split(separator: "-")
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 14) {
                Text(verbatim: "P").font(PKFont.titleLarge).foregroundStyle(.white).frame(width: 48, height: 48)
                    .background(Color(hexString: Pins.lotColor) ?? .gray, in: RoundedRectangle(cornerRadius: 12))
                VStack(alignment: .leading) {
                    Text(name).font(PKFont.titleLarge).foregroundStyle(Color.pkText)
                    if let op = lot.operator { Text(op).font(PKFont.bodyMedium).foregroundStyle(Color.pkMuted) }
                }
            }
            Text(details.joined(separator: " · ")).font(PKFont.bodyMedium).foregroundStyle(Color.pkMuted).padding(.top, 12)
            Text(lot.charge.map { L("lot_charge", $0) } ?? L("lot_charge_unknown")).font(PKFont.bodyMedium).foregroundStyle(Color.pkText).padding(.top, 8)
            if let hours = lot.openingHours {
                Text(L("lot_hours", hours == "24/7" ? L("lot_hours_always") : hours)).font(PKFont.bodyMedium).foregroundStyle(Color.pkText).padding(.top, 4)
            }
            Button { DriveTo.open(lat: lot.lat, lng: lot.lng) } label: {
                HStack(spacing: 8) {
                    Image(systemName: "location.north.fill")
                    Text(L("spot_navigate"))
                }
            }
            .buttonStyle(PillButtonStyle())
            .padding(.top, 18)
            Group {
                if let date, date.count == 3 {
                    Text(L("lot_data_note", "\(date[2]).\(date[1]).\(date[0])"))
                } else {
                    Text(L("lot_source_note"))
                }
            }
            .font(PKFont.bodySmall).foregroundStyle(Color.pkMuted).padding(.top, 10)
        }
        .padding(.horizontal, 20).padding(.top, 24).padding(.bottom, 16)
        .fittedSheet()
    }
}

/// Where the car is, a walk back to it, and "I'm leaving", which hands the spot on.
struct CarSheet: View {
    @ObservedObject var model: MapModel
    @State private var askHandOff = false

    var body: some View {
        if let car = model.parkedCar {
            let minutes = Int(Date().timeIntervalSince(car.at) / 60)
            let since = minutes < 1 ? L("car_just_now") : relativeLong(minutes: minutes)
            VStack(alignment: .leading, spacing: 0) {
                HStack(spacing: 12) {
                    Image(systemName: "car.fill").foregroundStyle(Color.pkPrimary).frame(width: 40, height: 40)
                        .background(Color.pkPrimary.opacity(0.12), in: RoundedRectangle(cornerRadius: 12))
                    VStack(alignment: .leading) {
                        Text(L("car_card_title", since)).font(PKFont.labelMedium).foregroundStyle(Color.pkPrimary)
                        Text(car.address ?? L("map_locating")).font(PKFont.titleSmall).foregroundStyle(Color.pkText).lineLimit(1)
                        if let at = ParkingReminder.activeChangeAt {
                            Text(L("reminder_countdown", reminderClock(at))).font(PKFont.labelMedium).foregroundStyle(Color.pkAmber).padding(.top, 2)
                        }
                    }
                    Spacer()
                }
                HStack(spacing: 8) {
                    TripAction(label: L("car_navigate"), icon: "figure.walk", color: .pkPrimary) { DriveTo.walk(lat: car.lat, lng: car.lng) }
                    // Blue-white curb? Pay without hunting for the app.
                    TripAction(label: L("car_pay_short"), icon: "creditcard.fill", color: Color(hex: 0x1565C0)) {
                        if let app = ParkingPay.preferred {
                            app.open()
                        } else {
                            model.sheet = .payChooser
                        }
                    }
                }
                .padding(.top, 12)
                // One way to finish: by the car it offers to hand the spot on; away, it just ends.
                Button {
                    if model.nearMyCar { askHandOff = true } else { model.forgetCar() }
                } label: {
                    Label(L("car_end_parking"), systemImage: "rectangle.portrait.and.arrow.right").font(PKFont.titleSmall)
                        .foregroundStyle(.white).frame(maxWidth: .infinity).frame(height: 48)
                        .background(Color.pkGreen, in: RoundedRectangle(cornerRadius: 14))
                }
                .padding(.top, 8)
            }
            .padding(.horizontal, 20).padding(.top, 24).padding(.bottom, 24)
            .fittedSheet()
            .alert(L("car_handoff_title"), isPresented: $askHandOff) {
                Button(L("car_handoff_yes")) {
                    model.sheet = nil
                    model.leave(car)
                }
                Button(L("car_handoff_no")) { model.forgetCar() }
            } message: {
                Text(L("car_handoff_body"))
            }
        }
    }
}

/// First tap on "I just parked": short and visual, your own car and one button.
struct CarExplainerSheet: View {
    @ObservedObject var model: MapModel
    @EnvironmentObject private var session: Session

    var body: some View {
        VStack(spacing: 6) {
            CarArt(size: model.prefs.vehicleSize ?? "regular", paint: myCarPaint(registry: model.prefs.carPaint), special: session.carSpecial, animate: true).frame(width: 150)
            Text(L("car_explain_title")).font(PKFont.titleLarge).foregroundStyle(Color.pkText).padding(.top, 6)
            Text(L("car_explain_body")).font(PKFont.bodyMedium).foregroundStyle(Color.pkMuted).multilineTextAlignment(.center)
            Button(L("car_explain_ok"), action: model.confirmParked).buttonStyle(PillButtonStyle()).padding(.top, 12)
        }
        .padding(.horizontal, 24).padding(.top, 24).padding(.bottom, 24)
        .fittedSheet()
    }
}

/// Big tappable tiles, one per payment app.
struct PayAppTiles: View {
    let selected: ParkingPay?
    let onPick: (ParkingPay) -> Void

    var body: some View {
        HStack(spacing: 12) {
            ForEach(ParkingPay.allCases) { app in
                Button { onPick(app) } label: {
                    VStack(spacing: 8) {
                        Text(app.label).font(PKFont.titleMedium).foregroundStyle(.white)
                        BlueWhiteCurb().frame(width: 80, height: 7)
                    }
                    .frame(maxWidth: .infinity).padding(.vertical, 22)
                    .background(LinearGradient(colors: [app.brand.opacity(0.82), app.brand], startPoint: .topLeading, endPoint: .bottomTrailing), in: RoundedRectangle(cornerRadius: 18))
                    .overlay(RoundedRectangle(cornerRadius: 18).stroke(Color.pkText, lineWidth: app == selected ? 3 : 0))
                    .shadow(color: .black.opacity(0.15), radius: 4, y: 2)
                }
            }
        }
    }
}

/// A strip of blue-white painted curb: Israel's sign for paid street parking.
struct BlueWhiteCurb: View {
    var body: some View {
        Canvas { ctx, s in
            let seg = s.height * 2.2
            var x: CGFloat = 0, blue = true
            while x < s.width {
                ctx.fill(Path(CGRect(x: x, y: 0, width: seg, height: s.height)), with: .color(blue ? Color(hex: 0x0D3C9E) : .white))
                x += seg
                blue.toggle()
            }
        }
        .clipShape(RoundedRectangle(cornerRadius: 2))
    }
}

/// Right after parking: "blue-white? pay" with the apps as one-tap buttons.
struct ParkedPaySheet: View {
    @ObservedObject var model: MapModel

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 8) {
                Image(systemName: "checkmark.circle.fill").font(.system(size: 22)).foregroundStyle(Color.pkGreen)
                Text(L("parked_pay_title")).font(PKFont.titleLarge).foregroundStyle(Color.pkText)
            }
            Text(L("parked_pay_body")).font(PKFont.bodyMedium).foregroundStyle(Color.pkMuted).padding(.top, 4).padding(.bottom, 16)
            PayAppTiles(selected: ParkingPay.preferred) { app in
                ParkingPay.preferred = app
                model.sheet = nil
                app.open()
            }
            Button(L("parked_pay_free")) { model.sheet = nil }
                .font(PKFont.labelLarge).foregroundStyle(Color.pkPrimary).frame(maxWidth: .infinity).frame(height: 44).padding(.top, 8)
        }
        .padding(.horizontal, 24).padding(.top, 24).padding(.bottom, 20)
        .fittedSheet()
    }
}

struct PayChooserSheet: View {
    @ObservedObject var model: MapModel

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(L("pay_chooser_title")).font(PKFont.titleLarge).foregroundStyle(Color.pkText)
            Text(L("pay_chooser_body")).font(PKFont.bodyMedium).foregroundStyle(Color.pkMuted).padding(.top, 4).padding(.bottom, 16)
            PayAppTiles(selected: nil) { app in
                ParkingPay.preferred = app // remembered; changeable in Settings
                model.sheet = nil
                app.open()
            }
            Text(L("pay_chooser_remember")).font(PKFont.bodySmall).foregroundStyle(Color.pkMuted).padding(.top, 14)
        }
        .padding(.horizontal, 24).padding(.top, 24).padding(.bottom, 24)
        .fittedSheet()
    }
}

/// Ending a parking that was paid for: remind to stop it in the app.
struct StopPaySheet: View {
    let app: ParkingPay
    @ObservedObject var model: MapModel

    var body: some View {
        VStack(spacing: 0) {
            Image(systemName: "stop.circle.fill").font(.system(size: 40)).foregroundStyle(app.brand)
            Text(L("stop_pay_title", app.label)).font(PKFont.titleLarge).foregroundStyle(Color.pkText).multilineTextAlignment(.center).padding(.top, 6)
            Text(L("stop_pay_body")).font(PKFont.bodyMedium).foregroundStyle(Color.pkMuted).multilineTextAlignment(.center).padding(.top, 4).padding(.bottom, 16)
            Button(L("stop_pay_open", app.label)) {
                model.sheet = nil
                app.open()
                ParkingPay.clearSession() // opening it isn't a new session
            }
            .buttonStyle(PillButtonStyle(color: app.brand, foreground: .white))
            Button(L("stop_pay_done")) { model.sheet = nil }.font(PKFont.labelLarge).foregroundStyle(Color.pkPrimary).frame(height: 44).padding(.top, 6)
        }
        .padding(.horizontal, 24).padding(.top, 24).padding(.bottom, 20)
        .fittedSheet()
    }
}
