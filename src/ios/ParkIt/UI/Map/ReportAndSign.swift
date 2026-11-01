import AVFoundation
import SwiftUI
import UIKit

/// The system camera. The photo comes back upright and at most `maxSide` px on the long side.
private struct CameraPicker: UIViewControllerRepresentable {
    let maxSide: CGFloat
    let onPhoto: (UIImage) -> Void
    @Environment(\.dismiss) private var dismiss

    func makeCoordinator() -> Coordinator { Coordinator(self) }

    func makeUIViewController(context: Context) -> UIImagePickerController {
        let picker = UIImagePickerController()
        picker.sourceType = .camera
        picker.delegate = context.coordinator
        return picker
    }

    func updateUIViewController(_ controller: UIImagePickerController, context: Context) {}

    final class Coordinator: NSObject, UIImagePickerControllerDelegate, UINavigationControllerDelegate {
        let parent: CameraPicker
        init(_ parent: CameraPicker) { self.parent = parent }

        func imagePickerController(_ picker: UIImagePickerController, didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey: Any]) {
            if let image = info[.originalImage] as? UIImage { parent.onPhoto(image.scaledUpright(maxSide: parent.maxSide)) }
            parent.dismiss()
        }

        func imagePickerControllerDidCancel(_ picker: UIImagePickerController) { parent.dismiss() }
    }
}

extension UIImage {
    func scaledUpright(maxSide: CGFloat) -> UIImage {
        let k = min(1, maxSide / max(size.width, size.height))
        let target = CGSize(width: size.width * k, height: size.height * k)
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        return UIGraphicsImageRenderer(size: target, format: format).image { _ in draw(in: CGRect(origin: .zero, size: target)) }
    }
}

/// A simulator has no camera, so there it stands in a drawn picture: this is what lets the
/// end-to-end test send a real photo through the server's blur and POST /spots.
enum SimulatorPhoto {
    static var available: Bool {
        #if targetEnvironment(simulator)
        true
        #else
        false
        #endif
    }

    static func make() -> UIImage {
        let size = CGSize(width: 1024, height: 768)
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        return UIGraphicsImageRenderer(size: size, format: format).image { r in
            // Dark to light across: e2e/seed.sql stores a sign that "looks like" this.
            let colors = [UIColor(white: 0.1, alpha: 1).cgColor, UIColor(white: 0.9, alpha: 1).cgColor] as CFArray
            let gradient = CGGradient(colorsSpace: CGColorSpaceCreateDeviceGray(), colors: colors, locations: [0, 1])!
            r.cgContext.drawLinearGradient(gradient, start: .zero, end: CGPoint(x: size.width, y: 0), options: [])
        }
    }
}

/// Camera access: the permission prompt comes from iOS the first time the camera opens.
private func cameraAvailable() -> Bool { UIImagePickerController.isSourceTypeAvailable(.camera) }

private func cameraAllowed() async -> Bool {
    switch AVCaptureDevice.authorizationStatus(for: .video) {
    case .authorized: true
    case .notDetermined: await AVCaptureDevice.requestAccess(for: .video)
    default: false
    }
}

/// Reporting stays the fewest possible decisions: photo (mandatory), regular or disabled,
/// car size, confirm. Every choice is visible up front, and the button says what's missing.
struct ReportSheet: View {
    @ObservedObject var model: MapModel
    @Environment(\.dismiss) private var dismiss
    @State private var preview: UIImage?
    @State private var stagingId: String?
    @State private var blurred: Int?
    @State private var uploading = false
    @State private var type = "street"
    @State private var size: String
    @State private var blueWhite: Bool? // no choice yet
    @State private var showDetails = false
    @State private var note = ""
    @State private var submitting = false
    @State private var error: String?
    @State private var camera = false
    private let lat: Double
    private let lng: Double
    private let street: String?
    private let noteMax = 120

    init(model: MapModel) {
        self.model = model
        lat = model.geocodeTarget.latitude
        lng = model.geocodeTarget.longitude
        street = model.resolvedStreet
        _size = State(initialValue: model.prefs.vehicleSize ?? "regular")
    }

    private var hasPhoto: Bool { stagingId != nil }
    // Whether the next driver pays is the one thing every report must say.
    private var curbKnown: Bool { type != "street" || blueWhite != nil }
    private var ready: Bool { hasPhoto && curbKnown }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                HStack {
                    Text(L("report_title")).font(PKFont.headlineSmall).foregroundStyle(Color.pkText)
                    Spacer()
                    // What you get for it, right where you decide.
                    HStack(spacing: 4) {
                        Image(systemName: "star.fill").font(.system(size: 12))
                        Text(L("report_points_reward")).font(PKFont.labelLarge)
                    }
                    .foregroundStyle(Color(hex: 0xC9971C)).padding(.horizontal, 10).padding(.vertical, 4)
                    .background(Color(hex: 0xC9971C).opacity(0.14), in: Capsule())
                }
                HStack(spacing: 4) {
                    Image(systemName: "mappin.circle.fill").font(.system(size: 14)).foregroundStyle(Color.pkPrimary)
                    Text(model.addressLoading ? L("map_locating") : model.resolvedAddress ?? L("map_address_unavailable")).font(PKFont.bodyMedium).foregroundStyle(Color.pkMuted).lineLimit(1)
                }
                .padding(.top, 6)

                if model.parkedCar == nil {
                    // A saved car has its own one-tap "I'm leaving" next to the car.
                    Button {
                        model.sheet = nil
                        model.leaveHereNow()
                    } label: {
                        Text(L("report_leaving_now")).font(PKFont.labelLarge).foregroundStyle(Color.pkPrimary)
                            .multilineTextAlignment(.center)
                            .padding(.horizontal, 12).padding(.vertical, 10)
                            .frame(maxWidth: .infinity)
                            .overlay(Capsule().stroke(Color.pkOutline, lineWidth: 1))
                    }
                    .padding(.top, 14)
                }

                photoArea.padding(.top, 18)
                HStack(alignment: .top, spacing: 6) {
                    Image(systemName: "eye.slash").font(.system(size: 12)).foregroundStyle(Color.pkMuted)
                    Text(L("report_privacy_note")).font(PKFont.bodySmall).foregroundStyle(Color.pkMuted)
                }
                .padding(.top, 8).padding(.horizontal, 4)

                if type == "street" {
                    label(L("report_curb_question"))
                    HStack(spacing: 10) {
                        curbOption(L("report_curb_blue_white"), "creditcard.fill", true)
                        curbOption(L("report_curb_gray"), "banknote", false)
                    }
                }

                // The rest is for whoever wants to add it.
                Button(L(showDetails ? "report_less" : "report_more")) { showDetails.toggle() }
                    .font(PKFont.labelLarge).foregroundStyle(Color.pkPrimary).padding(.top, 14)
                if showDetails {
                    label(L("report_type_question"))
                    HStack(spacing: 10) {
                        typeOption(L("map_spot_kind_regular"), "car.fill", "street")
                        typeOption(L("map_spot_kind_disabled"), "figure.roll", "disabled")
                    }
                    label(L("report_size_question"))
                    SizeSegments(selection: Binding(get: { size }, set: { size = $0 ?? "regular" }))
                    label(L("report_note_label"))
                    TextField(L("report_note_hint"), text: $note)
                        .font(PKFont.bodyLarge)
                        .padding(.horizontal, 14).frame(height: 52)
                        .overlay(RoundedRectangle(cornerRadius: 14).stroke(Color.pkMuted.opacity(0.6), lineWidth: 1))
                        .accessibilityIdentifier("report_note")
                        .onChange(of: note) { _, v in if v.count > noteMax { note = String(v.prefix(noteMax)) } }
                    Text(verbatim: "\(note.count)/\(noteMax)").font(PKFont.bodySmall).foregroundStyle(Color.pkMuted).padding(.top, 4).padding(.horizontal, 14)
                }

                if let error { Text(error).font(PKFont.bodySmall).foregroundStyle(Color.pkError).padding(.top, 12) }

                Button(action: submit) {
                    if submitting { ProgressView().tint(Color.pkOnPrimary) } else { Text(L(!hasPhoto ? "report_needs_photo" : !curbKnown ? "report_needs_curb" : "report_confirm")).multilineTextAlignment(.center) }
                }
                .buttonStyle(PillButtonStyle())
                .disabled(!ready || submitting || uploading)
                .padding(.top, 22)
            }
            .padding(.horizontal, 20).padding(.top, 24).padding(.bottom, 24)
        }
        .scrollDismissesKeyboard(.interactively)
        .background(Color.pkSurface)
        .presentationDetents([.large])
        .presentationDragIndicator(.visible)
        .presentationBackground(Color.pkSurface)
        .fullScreenCover(isPresented: $camera) {
            CameraPicker(maxSide: 1600) { image in upload(image) }.ignoresSafeArea()
        }
    }

    private func label(_ text: String) -> some View {
        Text(text).font(PKFont.titleSmall).foregroundStyle(Color.pkText).padding(.top, 20).padding(.bottom, 10)
    }

    private func curbOption(_ title: String, _ icon: String, _ value: Bool) -> some View {
        let selected = blueWhite == value
        return Button { blueWhite = value } label: {
            HStack(spacing: 8) {
                Image(systemName: icon).font(.system(size: 18)).foregroundStyle(selected ? Color.pkPrimary : Color.pkMuted)
                Text(title).font(PKFont.labelLarge).foregroundStyle(selected ? Color.pkPrimary : Color.pkText).lineLimit(1).minimumScaleFactor(0.8)
            }
            .padding(.horizontal, 6)
            .frame(maxWidth: .infinity).frame(height: 56)
            .background(selected ? Color.pkPrimary.opacity(0.1) : .clear, in: RoundedRectangle(cornerRadius: 16))
            .overlay(RoundedRectangle(cornerRadius: 16).stroke(selected ? Color.pkPrimary : Color.pkOutline, lineWidth: selected ? 2 : 1))
        }
    }

    private func typeOption(_ title: String, _ icon: String, _ value: String) -> some View {
        let selected = type == value
        return Button { type = value } label: {
            HStack(spacing: 8) {
                Image(systemName: icon).font(.system(size: 18)).foregroundStyle(selected ? Color.pkPrimary : Color.pkMuted)
                Text(title).font(PKFont.labelLarge).foregroundStyle(selected ? Color.pkPrimary : Color.pkText)
            }
            .frame(maxWidth: .infinity).frame(height: 56)
            .background(selected ? Color.pkPrimary.opacity(0.1) : .clear, in: RoundedRectangle(cornerRadius: 16))
            .overlay(RoundedRectangle(cornerRadius: 16).stroke(selected ? Color.pkPrimary : Color.pkOutline, lineWidth: selected ? 2 : 1))
        }
    }

    @ViewBuilder private var photoArea: some View {
        if let preview {
            Image(uiImage: preview).resizable().scaledToFill()
                .frame(maxWidth: .infinity).frame(height: 190)
                .clipShape(RoundedRectangle(cornerRadius: 20))
                .accessibilityLabel(L("report_preview_cd"))
                .overlay(alignment: .bottomLeading) {
                    HStack(spacing: 6) {
                        Image(systemName: "eye.slash").font(.system(size: 12))
                        Text((blurred ?? 0) > 0 ? LP("report_items_blurred", blurred ?? 0) : L("report_privacy_checked")).font(PKFont.labelMedium)
                    }
                    .foregroundStyle(.white).padding(.horizontal, 10).padding(.vertical, 5)
                    .background(.black.opacity(0.55), in: Capsule()).padding(10)
                }
                .overlay(alignment: .topTrailing) {
                    Button(action: takePhoto) {
                        HStack(spacing: 4) {
                            Image(systemName: "arrow.clockwise").font(.system(size: 13))
                            Text(L("report_retake")).font(PKFont.labelLarge)
                        }
                        .foregroundStyle(Color(hex: 0x202124)).padding(.horizontal, 12).padding(.vertical, 6)
                        .background(.white, in: Capsule())
                    }
                    .padding(10)
                }
        } else {
            Button(action: takePhoto) {
                VStack(spacing: 0) {
                    if uploading {
                        ProgressView()
                        Text(L("report_blurring")).font(PKFont.bodySmall).foregroundStyle(Color.pkMuted).padding(.top, 10)
                    } else {
                        Image(systemName: "camera.fill").font(.system(size: 22)).foregroundStyle(Color.pkOnPrimary)
                            .frame(width: 56, height: 56).background(Color.pkPrimary, in: Circle())
                        Text(L("report_take_photo")).font(PKFont.titleSmall).foregroundStyle(Color.pkText).padding(.top, 10)
                        Text(L("report_photo_required")).font(PKFont.bodySmall).foregroundStyle(Color.pkMuted)
                    }
                }
                .frame(maxWidth: .infinity).frame(height: 150)
                .background(Color.pkTint, in: RoundedRectangle(cornerRadius: 20))
                .overlay(RoundedRectangle(cornerRadius: 20).stroke(Color.pkPrimary.opacity(0.35), lineWidth: 1.5))
            }
            .disabled(uploading)
        }
    }

    private func takePhoto() {
        if cameraAvailable() {
            Task {
                if await cameraAllowed() { camera = true } else { error = L("permission_camera_needed") }
            }
        } else if SimulatorPhoto.available {
            upload(SimulatorPhoto.make())
        } else {
            error = L("permission_camera_needed")
        }
    }

    private func upload(_ image: UIImage) {
        uploading = true
        error = nil
        Task {
            defer { uploading = false }
            do {
                guard let jpeg = image.jpegData(compressionQuality: 0.85) else { return }
                let staged = try await API.shared.stagePhoto(jpeg)
                stagingId = staged.stagingId
                blurred = staged.facesBlurred + (staged.platesBlurred ?? 0)
                preview = Data(base64Encoded: staged.previewBase64, options: .ignoreUnknownCharacters).flatMap(UIImage.init(data:))
            } catch {
                self.error = L("report_photo_upload_failed", friendly(error))
            }
        }
    }

    private func submit() {
        submitting = true
        error = nil
        Task {
            defer { submitting = false }
            do {
                let fix = model.reportFields()
                _ = try await API.shared.reportSpot(SpotCreate(
                    lat: lat, lng: lng, spotType: type,
                    payment: blueWhite == true && type == "street" ? "paid" : "free",
                    vehicleSize: size, photoStagingId: stagingId, address: street,
                    note: note.trimmed.isEmpty ? nil : note.trimmed,
                    reporterLat: fix.lat, reporterLng: fix.lng, reporterAccuracyM: fix.accuracy
                ))
                model.sheet = nil
                model.show(L("map_reported_snackbar"))
                model.refreshSpotsNow()
                await model.refreshMyProfile()
            } catch {
                self.error = error.isOutsideServiceArea ? L("map_tel_aviv_only") : friendly(error)
            }
        }
    }
}

/// Compact / regular / large, as one segmented row.
struct SizeSegments: View {
    @Binding var selection: String?

    var body: some View {
        HStack(spacing: 0) {
            ForEach(Array(["compact", "regular", "large"].enumerated()), id: \.offset) { i, value in
                let on = selection == value
                Button { selection = value } label: {
                    Text(L("report_size_\(value)")).font(PKFont.labelLarge).foregroundStyle(on ? Color.pkOnPrimary : Color.pkText)
                        .frame(maxWidth: .infinity).frame(height: 44)
                        .background(on ? Color.pkPrimary : .clear)
                }
                if i < 2 { Rectangle().fill(Color.pkMuted.opacity(0.5)).frame(width: 1, height: 44) }
            }
        }
        .clipShape(Capsule())
        .overlay(Capsule().stroke(Color.pkMuted.opacity(0.5), lineWidth: 1))
    }
}

/// "Can I park here?": photograph a parking sign, get a plain answer for a regular driver
/// right now. The answer is advice, so the sheet always says to check the sign itself.
struct SignReaderSheet: View {
    let place: Coord?
    @State private var lastPhoto: UIImage?
    @State private var reading: SignReading?
    @State private var busy = false
    @State private var error: String?
    @State private var reminderSet: String?
    @State private var camera = false

    var body: some View {
        VStack(spacing: 0) {
            if busy {
                ProgressView().padding(24)
                Text(L("sign_reading")).font(PKFont.bodyMedium).foregroundStyle(Color.pkText)
            } else if let r = reading {
                result(r)
            } else {
                SignScanIcon(size: 72)
                HStack(spacing: 8) {
                    Text(L("sign_title")).font(PKFont.titleLarge).foregroundStyle(Color.pkText)
                    AIBadge()
                }
                .padding(.top, 10)
                Text(L("sign_intro")).font(PKFont.bodyMedium).foregroundStyle(Color.pkMuted).multilineTextAlignment(.center).padding(.top, 6)
            }
            if let error { Text(error).font(PKFont.bodyMedium).foregroundStyle(Color.pkError).multilineTextAlignment(.center).padding(.top, 10) }
            if !busy {
                Button(L(reading == nil ? "sign_shoot" : "sign_again"), action: shoot).buttonStyle(PillButtonStyle()).padding(.top, 18)
            }
            Text(L("sign_disclaimer")).font(PKFont.bodySmall).foregroundStyle(Color.pkMuted).multilineTextAlignment(.center).padding(.top, 12)
        }
        .padding(.horizontal, 24).padding(.top, 28).padding(.bottom, 24)
        .fittedSheet()
        .fullScreenCover(isPresented: $camera) {
            CameraPicker(maxSide: 1280) { image in send(image) }.ignoresSafeArea()
        }
    }

    @ViewBuilder private func result(_ r: SignReading) -> some View {
        let (color, icon, title): (Color, String, String) = switch r.verdict {
        case "allowed": (.pkGreen, "checkmark.circle.fill", L("sign_allowed"))
        case "not_allowed": (Color(hex: 0xD93025), "minus.circle.fill", L("sign_not_allowed"))
        default: (.pkAmber, "questionmark.circle.fill", L("sign_unclear"))
        }
        Image(systemName: icon).font(.system(size: 40)).foregroundStyle(color).frame(width: 72, height: 72).background(color.opacity(0.14), in: Circle())
        Text(title).font(PKFont.headlineSmall).foregroundStyle(color).padding(.top, 12)
        if let until = r.until { Text(L("sign_until", until)).font(PKFont.titleMedium).foregroundStyle(Color.pkText).padding(.top, 4) }
        if r.paid == true { Text(L("sign_paid")).font(PKFont.bodyMedium).foregroundStyle(Color(hex: 0x1565C0)).padding(.top, 4) }
        Text(L10n.isHebrew ? r.summaryHe : r.summaryEn).font(PKFont.bodyMedium).foregroundStyle(Color.pkText).multilineTextAlignment(.center).padding(.top, 10)
        if r.fromCache == true {
            Text(L("sign_from_cache")).font(PKFont.bodySmall).foregroundStyle(Color.pkMuted).multilineTextAlignment(.center).padding(.top, 6)
            Button(L("sign_read_again")) {
                if let lastPhoto { send(lastPhoto, fresh: true) }
            }
            .font(PKFont.labelLarge).foregroundStyle(Color.pkPrimary).frame(minHeight: 40)
        }
        // The useful part: don't get a ticket when the rules change.
        if let at = r.changesAt ?? r.until, ParkingReminder.changeTime(at) != nil {
            if let reminderSet {
                Text(L("reminder_set", reminderSet)).font(PKFont.labelLarge).foregroundStyle(Color.pkGreen).padding(.top, 14)
            } else {
                Button { remind(r, at: at) } label: {
                    Label(L("reminder_button", at), systemImage: "alarm").font(PKFont.labelLarge).foregroundStyle(Color.pkPrimary)
                        .padding(.horizontal, 16).frame(height: 40).overlay(Capsule().stroke(Color.pkOutline, lineWidth: 1))
                }
                .padding(.top, 14)
            }
        }
    }

    private func remind(_ r: SignReading, at: String) {
        guard let change = ParkingReminder.changeTime(at) else { return }
        let key = r.changesTo == "paid" ? "reminder_text_paid" : r.changesTo == "allowed" ? "reminder_text_allowed" : "reminder_text_forbidden"
        Task {
            if let ring = await ParkingReminder.schedule(changeAt: change, text: L(key, at)) {
                reminderSet = ParkingReminder.clock(ring)
            } else {
                error = L("permission_notifications_needed")
            }
        }
    }

    private func shoot() {
        if cameraAvailable() {
            Task {
                if await cameraAllowed() { camera = true } else { error = L("permission_camera_needed") }
            }
        } else if SimulatorPhoto.available {
            send(SimulatorPhoto.make())
        } else {
            error = L("permission_camera_needed")
        }
    }

    private func send(_ image: UIImage, fresh: Bool = false) {
        lastPhoto = image
        busy = true
        error = nil
        Task {
            defer { busy = false }
            do {
                guard let jpeg = image.jpegData(compressionQuality: 0.85) else { return }
                reminderSet = nil // a new sign: its own reminder
                reading = try await API.shared.readSign(jpeg, at: place, fresh: fresh)
            } catch {
                self.error = friendly(error, aboutSpot: false)
            }
        }
    }
}
