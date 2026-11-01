import CoreLocation
import Foundation
import SwiftUI
import UIKit
import UserNotifications

enum LegalLinks {
    static let privacy = URL(string: "https://eyallin2.github.io/Parkit/privacy.html")!
    static let terms = URL(string: "https://eyallin2.github.io/Parkit/terms.html")!
}

/// Turn-by-turn driving to a spot or car park: Waze first, then Apple Maps.
enum DriveTo {
    static func open(lat: Double, lng: Double) {
        if let waze = URL(string: "waze://?ll=\(lat),\(lng)&navigate=yes"), UIApplication.shared.canOpenURL(waze) {
            UIApplication.shared.open(waze)
        } else if let maps = URL(string: "http://maps.apple.com/?daddr=\(lat),\(lng)&dirflg=d") {
            UIApplication.shared.open(maps)
        }
    }

    /// Walking directions back to the car.
    static func walk(lat: Double, lng: Double) {
        if let maps = URL(string: "http://maps.apple.com/?daddr=\(lat),\(lng)&dirflg=w") { UIApplication.shared.open(maps) }
    }
}

func invite() {
    let vc = UIActivityViewController(activityItems: [L("profile_share_message")], applicationActivities: nil)
    let scene = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first
    var top = scene?.windows.first { $0.isKeyWindow }?.rootViewController
    while let next = top?.presentedViewController { top = next }
    top?.present(vc, animated: true)
}

/// Hand-off to the apps Israelis pay blue-white street parking with. No deal or API:
/// the App Store page opens the app if it's installed and offers it if it isn't.
/// (iOS has no public way to launch these two by bundle id.)
enum ParkingPay: String, CaseIterable, Identifiable {
    case pango = "Pango", cellopark = "Cellopark"

    var id: String { rawValue }
    var label: String { L(self == .pango ? "pay_pango" : "pay_cellopark") }
    var brand: Color { self == .pango ? Color(hex: 0x2E69E7) : Color(hex: 0x05A1AA) }
    private var storeID: String { self == .pango ? "434818173" : "499993633" }

    private static let d = UserDefaults.standard

    static var preferred: ParkingPay? {
        get { d.string(forKey: "pay_app").flatMap(ParkingPay.init(rawValue:)) }
        set {
            d.set(newValue?.rawValue, forKey: "pay_app")
            if newValue != nil { d.set(false, forKey: "pay_off") }
        }
    }

    static var isOff: Bool {
        get { d.bool(forKey: "pay_off") }
        set {
            d.set(newValue, forKey: "pay_off")
            if newValue { preferred = nil }
        }
    }

    static var session: ParkingPay? { d.string(forKey: "pay_session_app").flatMap(ParkingPay.init(rawValue:)) }
    static func clearSession() { d.removeObject(forKey: "pay_session_app") }

    func open() {
        Self.d.set(rawValue, forKey: "pay_session_app")
        if let url = URL(string: "https://apps.apple.com/il/app/id\(storeID)") { UIApplication.shared.open(url) }
    }
}

/// "Remind me 10 minutes before", from what the sign reader understood. One reminder
/// at a time, kept on the phone only (ParkingReminder.kt).
enum ParkingReminder {
    private static let d = UserDefaults.standard
    private static let israel = TimeZone(identifier: "Asia/Jerusalem")!
    private static let lead: TimeInterval = 10 * 60

    private static var calendar: Calendar {
        var c = Calendar(identifier: .gregorian)
        c.timeZone = israel
        return c
    }

    /// When today `hhmm` (Israel time) is: nil if unreadable or already past.
    static func changeTime(_ hhmm: String) -> Date? {
        let parts = hhmm.split(separator: ":").compactMap { Int($0) }
        guard parts.count >= 2, let at = calendar.date(bySettingHour: parts[0], minute: parts[1], second: 0, of: Date()), at > Date() else { return nil }
        return at
    }

    /// Asks for permission when first wanted. Returns when it will ring, or nil if refused.
    static func schedule(changeAt: Date, text: String) async -> Date? {
        let center = UNUserNotificationCenter.current()
        guard (try? await center.requestAuthorization(options: [.alert, .sound])) == true else { return nil }
        let ring = max(changeAt.addingTimeInterval(-lead), Date().addingTimeInterval(30))
        let content = UNMutableNotificationContent()
        content.title = L("reminder_title")
        content.body = text
        content.sound = .default
        let trigger = UNTimeIntervalNotificationTrigger(timeInterval: ring.timeIntervalSinceNow, repeats: false)
        center.removeAllPendingNotificationRequests()
        try? await center.add(UNNotificationRequest(identifier: "parking-reminder", content: content, trigger: trigger))
        d.set(changeAt, forKey: "reminder_change_at")
        return ring
    }

    static func cancel() {
        UNUserNotificationCenter.current().removeAllPendingNotificationRequests()
        d.removeObject(forKey: "reminder_change_at")
    }

    static var activeChangeAt: Date? {
        guard let at = d.object(forKey: "reminder_change_at") as? Date, at > Date() else { return nil }
        return at
    }

    static func clock(_ date: Date) -> String {
        let f = DateFormatter()
        f.timeZone = israel
        f.dateFormat = "HH:mm"
        return f.string(from: date)
    }
}
