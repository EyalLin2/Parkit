import CoreLocation
import Foundation
import UIKit

enum ApiConfig {
    #if DEBUG
    static let baseURL = "http://localhost:8000/"
    #else
    static let baseURL = "https://129-159-134-170.sslip.io/"
    #endif

    static func url(path: String) -> URL? {
        URL(string: baseURL.trimmingCharacters(in: CharacterSet(charactersIn: "/")) + path)
    }

    static var appVersion: String { Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "" }
}

struct APIError: Error {
    let status: Int
    let detail: String?
}

/// Every error a driver can see has a short code, the same table as Android
/// (ApiErrors.kt, docs/error-codes.md).
enum ParkItError {
    case network, timeout, badRequest, sessionExpired, blocked, notFound, conflict, photoTooLarge, invalid, tooMany, server, unavailable, unexpected

    init(_ error: Error) {
        if let e = error as? APIError {
            switch e.status {
            case 400: self = .badRequest
            case 401: self = .sessionExpired
            case 403: self = .blocked
            case 404: self = .notFound
            case 409: self = .conflict
            case 413: self = .photoTooLarge
            case 422: self = .invalid
            case 429: self = .tooMany
            case 503: self = .unavailable
            default: self = e.status >= 500 ? .server : .badRequest
            }
        } else if let e = error as? URLError {
            self = e.code == .timedOut ? .timeout : .network
        } else {
            self = .unexpected
        }
    }

    var code: String {
        switch self {
        case .network: "PK-100"
        case .timeout: "PK-101"
        case .badRequest: "PK-400"
        case .sessionExpired: "PK-401"
        case .blocked: "PK-403"
        case .notFound: "PK-404"
        case .conflict: "PK-409"
        case .photoTooLarge: "PK-413"
        case .invalid: "PK-422"
        case .tooMany: "PK-429"
        case .server: "PK-500"
        case .unavailable: "PK-503"
        case .unexpected: "PK-900"
        }
    }

    var messageKey: String {
        switch self {
        case .network: "error_network"
        case .timeout: "error_timeout"
        case .badRequest, .unexpected: "error_generic"
        case .sessionExpired: "error_session"
        case .blocked: "error_blocked"
        case .notFound: "error_not_found"
        case .conflict: "error_conflict"
        case .photoTooLarge: "error_photo_large"
        case .invalid: "error_invalid"
        case .tooMany: "error_too_many"
        case .server: "error_server"
        case .unavailable: "error_unavailable"
        }
    }
}

/// The last few errors this session, attached to feedback.
@MainActor enum RecentErrors {
    private static var items: [String] = []
    static func record(_ code: String) {
        let f = DateFormatter()
        f.dateFormat = "HH:mm:ss"
        items.append("\(f.string(from: Date())) \(code)")
        if items.count > 10 { items.removeFirst() }
    }
    static var summary: String { items.joined(separator: ", ") }
}

/// Plain-language message plus the code. The server's own explanations are in
/// English, so a Hebrew screen gets the matching translated line; the 150 m rule
/// always gets its own wording rather than a generic "invalid".
@MainActor func friendly(_ error: Error, aboutSpot: Bool = true) -> String {
    let kind = ParkItError(error)
    RecentErrors.record(kind.code)
    var message = L(kind == .notFound && !aboutSpot ? "error_generic" : kind.messageKey)
    if let e = error as? APIError, let detail = e.detail {
        if detail.contains("near where you are") {
            message = L("report_too_far")
        } else if !L10n.isHebrew, e.status != 422, e.status < 500 || e.status == 503 {
            message = detail
        }
    }
    return L("error_with_code", message, kind.code)
}

@MainActor
final class API {
    static let shared = API()

    private let session: URLSession = {
        let c = URLSessionConfiguration.default
        // Sending mail, blurring a photo and reading a sign can take a while on the server.
        c.timeoutIntervalForRequest = 45
        c.timeoutIntervalForResource = 90
        c.waitsForConnectivity = false
        return URLSession(configuration: c)
    }()

    private let decoder: JSONDecoder = {
        let d = JSONDecoder()
        d.keyDecodingStrategy = .convertFromSnakeCase
        return d
    }()

    private let encoder: JSONEncoder = {
        let e = JSONEncoder()
        e.keyEncodingStrategy = .convertToSnakeCase
        return e
    }()

    private func query(_ path: String, _ items: [(String, String)]) -> String {
        var c = URLComponents()
        c.queryItems = items.map { URLQueryItem(name: $0.0, value: $0.1) }
        return path + (c.percentEncodedQuery.map { "?\($0)" } ?? "")
    }

    private func send(_ method: String, _ path: String, body: Data? = nil, contentType: String? = nil) async throws -> Data {
        guard let url = URL(string: ApiConfig.baseURL + path) else { throw URLError(.badURL) }
        var r = URLRequest(url: url)
        r.httpMethod = method
        r.httpBody = body
        if let contentType { r.setValue(contentType, forHTTPHeaderField: "Content-Type") }
        if let token = Session.shared.token { r.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization") }
        let (data, response) = try await session.data(for: r)
        let status = (response as? HTTPURLResponse)?.statusCode ?? 0
        guard (200..<300).contains(status) else {
            if status == 401, Session.shared.token != nil { Session.shared.signOut() }
            let detail = (try? JSONSerialization.jsonObject(with: data) as? [String: Any])?["detail"] as? String
            throw APIError(status: status, detail: detail)
        }
        return data
    }

    private func get<T: Decodable>(_ path: String, _ items: [(String, String)] = []) async throws -> T {
        try decoder.decode(T.self, from: try await send("GET", query(path, items)))
    }

    private func post<T: Decodable, B: Encodable>(_ path: String, _ body: B, method: String = "POST") async throws -> T {
        try decoder.decode(T.self, from: try await send(method, path, body: try encoder.encode(body), contentType: "application/json"))
    }

    private func postNoReply<B: Encodable>(_ path: String, _ body: B, method: String = "POST") async throws {
        _ = try await send(method, path, body: try encoder.encode(body), contentType: "application/json")
    }

    private func upload<T: Decodable>(_ path: String, field: String, filename: String, jpeg: Data) async throws -> T {
        let boundary = "parkit-\(UUID().uuidString)"
        var body = Data()
        body.append(Data("--\(boundary)\r\nContent-Disposition: form-data; name=\"\(field)\"; filename=\"\(filename)\"\r\nContent-Type: image/jpeg\r\n\r\n".utf8))
        body.append(jpeg)
        body.append(Data("\r\n--\(boundary)--\r\n".utf8))
        return try decoder.decode(T.self, from: try await send("POST", path, body: body, contentType: "multipart/form-data; boundary=\(boundary)"))
    }

    // MARK: Auth

    func requestEmailOtp(_ email: String) async throws -> EmailOtpRequested { try await post("auth/email/request", EmailOtpRequest(email: email)) }
    func verifyEmailOtp(_ email: String, _ code: String) async throws -> TokenOut { try await post("auth/email/verify", EmailOtpVerify(email: email, code: code)) }
    func refreshToken() async throws -> TokenOut { try decoder.decode(TokenOut.self, from: try await send("POST", "auth/refresh")) }
    func updateDetails(_ d: DetailsUpdate) async throws -> AccountOut { try await post("users/me/details", d, method: "PATCH") }
    func deleteAccount() async throws { _ = try await send("DELETE", "users/me") }
    func setAvatar(_ id: String?) async throws { try await postNoReply("users/me/avatar", AvatarUpdate(avatar: id), method: "PUT") }
    func myProfile() async throws -> ProfileOut { try await get("users/me") }
    func updatePreferences(_ p: Preferences) async throws -> Preferences { try await post("users/me/preferences", p, method: "PATCH") }

    // MARK: Spots

    /// `me` is the phone's real position: free spots are only for drivers close by.
    func nearbySpots(lat: Double, lng: Double, radiusM: Int, me: CLLocationCoordinate2D) async throws -> [SpotOut] {
        try await get("spots", [("lat", "\(lat)"), ("lng", "\(lng)"), ("radius_m", "\(radiusM)"), ("me_lat", "\(me.latitude)"), ("me_lng", "\(me.longitude)")])
    }
    func reportSpot(_ s: SpotCreate) async throws -> SpotOut { try await post("spots", s) }
    func claimSpot(_ id: String, me: CLLocationCoordinate2D?) async throws -> SpotOut {
        let items = me.map { [("me_lat", "\($0.latitude)"), ("me_lng", "\($0.longitude)")] } ?? []
        return try decoder.decode(SpotOut.self, from: try await send("POST", query("spots/\(id)/claim", items)))
    }
    func submitFeedback(_ id: String, _ type: String) async throws { try await postNoReply("spots/\(id)/feedback", FeedbackCreate(type: type)) }
    func reportContent(_ id: String, _ reason: String) async throws { try await postNoReply("spots/\(id)/report-content", ContentReport(reason: reason)) }
    func cancelSpot(_ id: String) async throws { _ = try await send("DELETE", "spots/\(id)") }
    func stagePhoto(_ jpeg: Data) async throws -> StagedPhotoOut { try await upload("photos/stage", field: "file", filename: "photo.jpg", jpeg: jpeg) }
    func readSign(_ jpeg: Data) async throws -> SignReading { try await upload("signs/read", field: "photo", filename: "sign.jpg", jpeg: jpeg) }

    // MARK: Everything else

    func rewards() async throws -> ShopOut { try await get("rewards") }
    func redeemReward(_ id: Int) async throws -> ShopOut { try decoder.decode(ShopOut.self, from: try await send("POST", "rewards/\(id)/redeem")) }
    func lookupVehicle(_ plate: String) async throws -> VehicleLookupOut { try await get("vehicles/lookup", [("plate", plate)]) }
    func areaForecast(lat: Double, lng: Double) async throws -> AreaForecast? { try await get("insights/area", [("lat", "\(lat)"), ("lng", "\(lng)")]) }
    func bestHour(lat: Double, lng: Double) async throws -> BestHour? { try await get("insights/best-hour", [("lat", "\(lat)"), ("lng", "\(lng)")]) }
    func parkingLots(lat: Double, lng: Double, radiusM: Int) async throws -> [ParkingLotOut] {
        try await get("parking-lots", [("lat", "\(lat)"), ("lng", "\(lng)"), ("radius_m", "\(radiusM)")])
    }
    func cities() async throws -> [CityOut] { try await get("places/cities") }
    func leaderboard(limit: Int = 10, city: String? = nil) async throws -> [LeaderboardEntry] {
        var items = [("limit", "\(limit)")]
        if let city { items.append(("city", city)) }
        return try await get("leaderboard", items)
    }
    func sendFeedback(_ text: String) async throws {
        let recent = RecentErrors.summary
        try await postNoReply("feedback", FeedbackIn(
            kind: "feedback",
            message: recent.isEmpty ? text : "\(text)\n\n[recent errors: \(recent)]",
            appVersion: ApiConfig.appVersion,
            device: "iPhone \(UIDevice.current.systemVersion)"
        ))
    }
    func reverseGeocode(lat: Double, lng: Double) async throws -> ReverseGeocodeOut {
        try await get("geocode/reverse", [("lat", "\(lat)"), ("lng", "\(lng)"), ("lang", L10n.isHebrew ? "he" : "en")])
    }
}

/// Address lookups through ParkIt's own backend, cached on a ~11 m grid with a few
/// retries for flaky mobile networks (Android's Geocoder).
@MainActor enum Geocoder {
    private static var cache: [String: ReverseGeocodeOut] = [:]
    private static var spotCache: [String: String] = [:]

    private static func key(_ lat: Double, _ lng: Double) -> String {
        String(format: "%.4f,%.4f,%@", lat, lng, L10n.language)
    }

    static func cached(lat: Double, lng: Double) -> ReverseGeocodeOut? { cache[key(lat, lng)] }

    /// nil only after every retry failed: callers show a message, never coordinates.
    static func reverse(lat: Double, lng: Double, attempts: Int = 3) async -> ReverseGeocodeOut? {
        if let hit = cached(lat: lat, lng: lng) { return hit }
        for attempt in 0..<attempts {
            if Task.isCancelled { return nil }
            if let r = try? await API.shared.reverseGeocode(lat: lat, lng: lng) {
                cache[key(lat, lng)] = r
                return r
            }
            if attempt < attempts - 1 { try? await Task.sleep(for: .milliseconds(600 * (attempt + 1))) }
        }
        return nil
    }

    static func spot(_ id: String) -> String? { spotCache["\(L10n.language)|\(id)"] }

    static func resolve(spot id: String, lat: Double, lng: Double) async -> String? {
        if let hit = spot(id) { return hit }
        guard let label = await reverse(lat: lat, lng: lng, attempts: 1)?.street else { return nil }
        spotCache["\(L10n.language)|\(id)"] = label
        return label
    }
}

/// The server's timestamps, with or without fractional seconds or an offset.
func parseISO(_ iso: String) -> Date? {
    var s = iso.replacingOccurrences(of: #"\.\d+"#, with: "", options: .regularExpression)
    if s.range(of: #"(Z|[+-]\d\d:?\d\d)$"#, options: .regularExpression) == nil { s += "Z" }
    return ISO8601DateFormatter().date(from: s)
}

func ageMinutes(_ iso: String) -> Int {
    guard let d = parseISO(iso) else { return 0 }
    return max(0, Int(Date().timeIntervalSince(d) / 60))
}

func relativeShort(_ iso: String) -> String {
    let m = ageMinutes(iso)
    if m < 1 { return L("time_now_short") }
    if m < 60 { return L("time_minutes_short", m) }
    if m < 1440 { return L("time_hours_short", m / 60) }
    return L("time_days_short", min(m / 1440, 99))
}

func relativeLong(_ iso: String) -> String {
    relativeLong(minutes: ageMinutes(iso))
}

func relativeLong(minutes m: Int) -> String {
    if m < 1 { return L("time_now_long") }
    if m < 60 { return LP("time_minutes_long", m) }
    if m < 1440 { return LP("time_hours_long", m / 60) }
    return LP("time_days_long", m / 1440)
}

func formatDistance(_ meters: Double) -> String {
    if meters < 1000 { return L("distance_m", Int((meters / 10).rounded()) * 10) }
    return L("distance_km", meters / 1000)
}
