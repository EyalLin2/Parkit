import Foundation
import Security
import SwiftUI

/// The token lives in the Keychain. iOS keeps Keychain items across a reinstall,
/// so a fresh install clears it (see Session.init).
enum Keychain {
    private static let account = "session_token"
    private static let service = "com.parkit.israel"

    private static var base: [String: Any] {
        [kSecClass as String: kSecClassGenericPassword, kSecAttrService as String: service, kSecAttrAccount as String: account]
    }

    static func read() -> String? {
        var q = base
        q[kSecReturnData as String] = true
        var out: AnyObject?
        guard SecItemCopyMatching(q as CFDictionary, &out) == errSecSuccess, let d = out as? Data else { return nil }
        return String(data: d, encoding: .utf8)
    }

    static func write(_ value: String?) {
        SecItemDelete(base as CFDictionary)
        guard let value else { return }
        var q = base
        q[kSecValueData as String] = Data(value.utf8)
        q[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        SecItemAdd(q as CFDictionary, nil)
    }
}

/// What the map needs from the driver profile, cached so it applies on the first frame.
struct DriverPrefs: Codable, Equatable {
    var preferences = Preferences()
    var homeLat: Double?
    var homeLng: Double?
}

enum RootScreen: Equatable { case intro, login, details, onboarding, tips, main }

/// Session state (cleared on sign-out) and device state (kept), as SessionStore.kt.
@MainActor
final class Session: ObservableObject {
    static let shared = Session()
    private let d = UserDefaults.standard

    @Published var token: String?
    @Published var userId: String?
    @Published var displayName: String?
    @Published var driver = DriverPrefs()
    @Published var root: RootScreen = .login
    @Published var language: String = L10n.language
    @Published var themeMode: String?

    private enum K {
        static let installed = "installed"
        // Session keys
        static let userId = "s.user_id", displayName = "s.display_name", driver = "s.driver"
        static let needsProfile = "s.needs_profile", onboardingSeen = "s.onboarding_seen"
        static let lastLevel = "s.last_seen_level", parkedCar = "s.parked_car"
        static let leftAt = "s.left_app_at", bg = "s.profile_bg", special = "s.car_special", paint = "s.car_paint"
        static let claim = "s.active_claim", claimEnds = "s.claim_ends"
        // Device keys
        static let introSeen = "introSeen", tipsSeen = "tipsSeen", lastLat = "lastLat", lastLng = "lastLng"
        static let tipDay = "tipShownDay", carExplained = "carExplained", theme = "themeMode"
    }

    private init() {
        if !d.bool(forKey: K.installed) {
            // First launch of this install: anything in the Keychain is from an older one.
            Keychain.write(nil)
            d.set(true, forKey: K.installed)
        }
        token = Keychain.read()
        userId = d.string(forKey: K.userId)
        displayName = d.string(forKey: K.displayName)
        themeMode = d.string(forKey: K.theme)
        if let data = d.data(forKey: K.driver), let v = try? JSONDecoder().decode(DriverPrefs.self, from: data) { driver = v }
        L10n.applySystem()
        root = startScreen()
    }

    private func startScreen() -> RootScreen {
        if token != nil {
            if needsProfile { return .details }
            if !onboardingSeen { return .onboarding }
            return .main
        }
        return introSeen ? .login : .intro
    }

    func flag(_ key: String) -> Bool { d.bool(forKey: key) }

    var onboardingSeen: Bool { get { d.bool(forKey: K.onboardingSeen) } set { d.set(newValue, forKey: K.onboardingSeen) } }
    var needsProfile: Bool { get { d.bool(forKey: K.needsProfile) } set { d.set(newValue, forKey: K.needsProfile) } }
    var introSeen: Bool { get { d.bool(forKey: K.introSeen) } set { d.set(newValue, forKey: K.introSeen) } }
    var tipsSeen: Bool { get { d.bool(forKey: K.tipsSeen) } set { d.set(newValue, forKey: K.tipsSeen) } }
    var carExplained: Bool { get { d.bool(forKey: K.carExplained) } set { d.set(newValue, forKey: K.carExplained) } }
    var lastSeenLevel: String? { get { d.string(forKey: K.lastLevel) } set { d.set(newValue, forKey: K.lastLevel) } }
    var profileBackground: String? { get { d.string(forKey: K.bg) } set { d.set(newValue, forKey: K.bg) } }
    var carSpecial: String? { get { d.string(forKey: K.special) } set { d.set(newValue, forKey: K.special) } }
    var tipShownDay: String? { get { d.string(forKey: K.tipDay) } set { d.set(newValue, forKey: K.tipDay) } }

    /// Index into CarPaints picked by hand; -1 = the colour from the registry.
    var carPaintIndex: Int {
        get { d.object(forKey: K.paint) as? Int ?? -1 }
        set { d.set(newValue, forKey: K.paint) }
    }

    var leftAppAt: Date? { get { d.object(forKey: K.leftAt) as? Date } set { d.set(newValue, forKey: K.leftAt) } }

    var lastFix: (lat: Double, lng: Double)? {
        get {
            guard d.object(forKey: K.lastLat) != nil else { return nil }
            return (d.double(forKey: K.lastLat), d.double(forKey: K.lastLng))
        }
        set {
            d.set(newValue?.lat, forKey: K.lastLat)
            d.set(newValue?.lng, forKey: K.lastLng)
        }
    }

    var parkedCar: ParkedCar? {
        get { d.data(forKey: K.parkedCar).flatMap { try? JSONDecoder().decode(ParkedCar.self, from: $0) } }
        set { d.set(newValue.flatMap { try? JSONEncoder().encode($0) }, forKey: K.parkedCar) }
    }

    /// The spot this driver said "on my way" to, so it survives a trip to the navigation app.
    var activeClaim: SpotOut? {
        get { d.data(forKey: K.claim).flatMap { try? JSONDecoder().decode(SpotOut.self, from: $0) } }
        set { d.set(newValue.flatMap { try? JSONEncoder().encode($0) }, forKey: K.claim) }
    }

    var claimEndsAt: Date? { get { d.object(forKey: K.claimEnds) as? Date } set { d.set(newValue, forKey: K.claimEnds) } }

    func saveDriver(_ value: DriverPrefs) {
        driver = value
        d.set(try? JSONEncoder().encode(value), forKey: K.driver)
    }

    /// Server copy wins for the preferences; the city centre is kept only while the city is unchanged.
    func syncDriver(_ server: Preferences) {
        let keep = driver.preferences.homeCity == server.homeCity
        saveDriver(DriverPrefs(preferences: server, homeLat: keep ? driver.homeLat : nil, homeLng: keep ? driver.homeLng : nil))
    }

    func save(token: String, userId: String, displayName: String) {
        Keychain.write(token)
        self.token = token
        self.userId = userId
        self.displayName = displayName
        d.set(userId, forKey: K.userId)
        d.set(displayName, forKey: K.displayName)
    }

    func signOut() {
        Keychain.write(nil)
        for key in d.dictionaryRepresentation().keys where key.hasPrefix("s.") { d.removeObject(forKey: key) }
        token = nil
        userId = nil
        displayName = nil
        driver = DriverPrefs()
        root = .login
    }

    func loggedIn(needsProfile: Bool) {
        self.needsProfile = needsProfile
        if needsProfile { root = .details } else { root = onboardingSeen ? .main : .onboarding }
    }

    func setLanguage(_ code: String) {
        L10n.set(code)
        language = L10n.language
    }

    func setTheme(_ mode: String?) {
        themeMode = mode
        d.set(mode, forKey: K.theme)
    }

    var colorScheme: ColorScheme? {
        switch themeMode {
        case "light": .light
        case "dark": .dark
        default: nil
        }
    }
}
