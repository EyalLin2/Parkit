import Foundation

// Same shapes as the Android app's ApiModels.kt. JSON keys are snake_case on the
// wire; the API client converts them, so only the camelCase names appear here.

struct TokenOut: Codable {
    let accessToken: String
    let userId: String
    var needsProfile: Bool?
}

struct EmailOtpRequest: Codable { let email: String }

struct EmailOtpRequested: Codable {
    let email: String
    var resendAfterSeconds: Int?
    var devCode: String?
}

struct EmailOtpVerify: Codable { let email: String; let code: String }

struct DetailsUpdate: Codable { let firstName: String; let lastName: String; let email: String }

struct AccountOut: Codable {
    var phone: String?
    var firstName: String?
    var lastName: String?
    var email: String?
    var emailVerified: Bool?
}

struct SpotCreate: Codable {
    let lat: Double
    let lng: Double
    let spotType: String
    let payment: String
    var vehicleSize: String?
    var photoStagingId: String?
    var address: String?
    var note: String?
    var reporterLat: Double?
    var reporterLng: Double?
    var reporterAccuracyM: Double?
    var handoff: Bool = false
}

struct SpotOut: Codable, Identifiable, Equatable {
    let id: String
    let reporterId: String
    let lat: Double
    let lng: Double
    let spotType: String
    let payment: String
    var vehicleSize: String?
    var photoUrl: String?
    var address: String?
    var note: String?
    let status: String
    let reportedAt: String
    var claimedBy: String?
    var reporterLevel: String?
    var hotMinutes: Int?

    /// The server stores a spot's street in Hebrew; the English UI looks it up again.
    var localAddress: String? { L10n.isHebrew ? address : nil }
}

struct FeedbackCreate: Codable { let type: String }
struct ContentReport: Codable { let reason: String; var note: String? }

struct StagedPhotoOut: Codable {
    let stagingId: String
    let facesBlurred: Int
    var platesBlurred: Int?
    let previewBase64: String
}

struct ActivityItem: Codable, Identifiable {
    let spotId: String
    let spotType: String
    let payment: String
    var address: String?
    let reportedAt: String
    let status: String
    var removedReason: String?
    var id: String { spotId }
}

struct ProfileOut: Codable {
    let userId: String
    let displayName: String
    let points: Int
    let weeklyPoints: Int
    var pointsBalance: Int?
    var avatar: String?
    var weeklyRank: Int?
    var weeklyCityRank: Int?
    let successfulReports: Int
    var totalReports: Int?
    var level: String?
    var levelMinPoints: Int?
    var nextLevel: String?
    var nextLevelPoints: Int?
    let activity: [ActivityItem]
    var preferences: Preferences?
    var account: AccountOut?

    /// 0...1 toward the next level (1 at the top of the ladder).
    var levelProgress: Double {
        guard let next = nextLevelPoints else { return 1 }
        let min = levelMinPoints ?? 0
        return Swift.min(1, Swift.max(0, Double(points - min) / Double(Swift.max(next - min, 1))))
    }
}

/// Sent whole: an explicit null clears a value on the server.
struct Preferences: Codable, Equatable {
    var vehicleSize: String?
    var carLabel: String?
    var carPaint: String?
    var hasDisabledPermit: Bool = false
    var homeCity: String?

    var isEmpty: Bool { vehicleSize == nil && !hasDisabledPermit && homeCity == nil }

    init(vehicleSize: String? = nil, carLabel: String? = nil, carPaint: String? = nil, hasDisabledPermit: Bool = false, homeCity: String? = nil) {
        self.vehicleSize = vehicleSize
        self.carLabel = carLabel
        self.carPaint = carPaint
        self.hasDisabledPermit = hasDisabledPermit
        self.homeCity = homeCity
    }

    enum CodingKeys: String, CodingKey { case vehicleSize, carLabel, carPaint, hasDisabledPermit, homeCity }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        vehicleSize = try c.decodeIfPresent(String.self, forKey: .vehicleSize)
        carLabel = try c.decodeIfPresent(String.self, forKey: .carLabel)
        carPaint = try c.decodeIfPresent(String.self, forKey: .carPaint)
        hasDisabledPermit = try c.decodeIfPresent(Bool.self, forKey: .hasDisabledPermit) ?? false
        homeCity = try c.decodeIfPresent(String.self, forKey: .homeCity)
    }

    func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        try c.encode(vehicleSize, forKey: .vehicleSize)
        try c.encode(carLabel, forKey: .carLabel)
        try c.encode(carPaint, forKey: .carPaint)
        try c.encode(hasDisabledPermit, forKey: .hasDisabledPermit)
        try c.encode(homeCity, forKey: .homeCity)
    }
}

struct VehicleLookupOut: Codable {
    let make: String
    let model: String
    var year: Int?
    var body: String?
    let size: String
    let label: String
    var paint: String?
}

struct ParkingLotOut: Codable, Identifiable, Equatable {
    let id: Int
    let lat: Double
    let lng: Double
    var name: String?
    var nameEn: String?
    var kind: String?
    var fee: String?
    var capacity: Int?
    var `operator`: String?
    var charge: String?
    var openingHours: String?
    let distanceM: Double
    var dataUpdatedAt: String?
}

struct CityOut: Codable, Identifiable {
    let name: String
    var nameEn: String?
    let lat: Double
    let lng: Double
    var id: String { name }
    var displayName: String { L10n.isHebrew ? name : (nameEn ?? name) }
}

struct LeaderboardEntry: Codable, Identifiable {
    let rank: Int
    let userId: String
    let displayName: String
    let weeklyPoints: Int
    var level: String?
    var avatar: String?
    var id: String { userId }
}

struct AvatarUpdate: Encodable {
    let avatar: String?
    enum CodingKeys: String, CodingKey { case avatar }
    func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        try c.encode(avatar, forKey: .avatar)
    }
}

struct FeedbackIn: Codable {
    let kind: String
    let message: String
    var appVersion: String?
    var device: String?
}

struct RewardOut: Codable, Identifiable {
    let id: Int
    let kind: String
    var key: String?
    let titleHe: String
    let titleEn: String
    var partner: String?
    var descriptionHe: String?
    var descriptionEn: String?
    let cost: Int
    var owned: Bool?
    var isOwned: Bool { owned == true }
    var title: String { L10n.isHebrew ? titleHe : titleEn }
    var details: String? { L10n.isHebrew ? descriptionHe : descriptionEn }
}

struct RedemptionOut: Codable, Identifiable {
    let rewardId: Int
    let titleHe: String
    let titleEn: String
    var partner: String?
    let code: String
    let createdAt: String
    var id: String { code }
    var title: String { L10n.isHebrew ? titleHe : titleEn }
}

struct ShopOut: Codable {
    let balance: Int
    let rewards: [RewardOut]
    let redemptions: [RedemptionOut]
}

struct SignReading: Codable {
    let verdict: String
    var until: String?
    var changesAt: String?
    var changesTo: String?
    var paid: Bool?
    let summaryHe: String
    let summaryEn: String
}

struct AreaForecast: Codable { let everyMinutes: Int; let reports: Int; let days: Int; var scope: String? }
struct BestHour: Codable { let hour: Int; let reports: Int; var scope: String? }
struct ReverseGeocodeOut: Codable { var label: String?; var street: String? }

/// How a spot relates to this driver's car and permits. The map never hides a
/// report because of these: it orders and labels them (DriverFit.kt).
struct DriverFit {
    let needsDisabledPermit: Bool
    let tooSmall: Bool
    var unusable: Bool { needsDisabledPermit || tooSmall }

    init(spot: SpotOut, prefs: Preferences) {
        func rank(_ s: String?) -> Int? { ["compact": 0, "regular": 1, "large": 2][s ?? ""] }
        needsDisabledPermit = spot.spotType == "disabled" && !prefs.hasDisabledPermit
        if let a = rank(spot.vehicleSize), let b = rank(prefs.vehicleSize) { tooSmall = a < b } else { tooSmall = false }
    }
}

/// Where the driver left the car.
struct ParkedCar: Codable, Equatable {
    var lat: Double
    var lng: Double
    var at: Date
    var address: String?
    var spotType: String = "street"
}
